package oled

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import com.mongodb.casbah.commons.MongoDBObject
import com.typesafe.scalalogging.LazyLogging
import app.Globals
import logic.{Clause, Literal, LogicUtils, Theory}
import utils.DataUtils.{DataAsExamples, DataAsIntervals, ResultsContainer, TrainingSet}
import utils._
import utils.Implicits._
import jep.Jep

import scala.collection.mutable.ListBuffer


/**
  * Created by nkatz on 28/2/2016.
  */

class Dispatcher( val DB: Database,
                  trainingSets : List[TrainingSet],
                  val delta: Double,
                  val breakTiesThreshold: Double,
                  val postPruningThreshold: Double,
                  val minSeenExmpls: Double,
                  val repeatFor: Int,
                  val chunkSize: Int,
                  withInertia: Boolean,
                  withPostPruning: Boolean,
                  onlinePruning: Boolean,
                  reScoreData: TrainingSet,
                  val HLE: String,
                  handCraftedTheoryFile: String = "",
                  val kernelSet: Theory = Theory(),
                  globals: Globals
                ) extends Actor with LazyLogging {

  val WHOLE_DATA_SET_VALE = 1000000000
  var size = 2 // two theories are being expected, one for learning the initiatedAt part and one for the terminatedAt
  var theories = List[(Theory,Double)]()
  var merged = Theory()
  var time = 0.0

  val initLearners = ListBuffer[ActorRef]()
  val termLearners = ListBuffer[ActorRef]()

  var initTheory = Theory()
  var termTheory = Theory()

  var initLearnersCnt=0
  var termLearnersCnt=0

  trainingSets.foreach( trainingSet =>{

    val initLearner = context.actorOf(Props(
      new TheoryLearner(DB, delta, breakTiesThreshold, postPruningThreshold,
        minSeenExmpls, repeatFor, chunkSize, "initiated", withInertia = withInertia,
        withPostPruning = withPostPruning, onlinePruning = onlinePruning, trainingSet,reScoreData = reScoreData,HLE,
        kernelSet = kernelSet, globals = globals)
    ), name = s"initlearner$initLearnersCnt,$HLE")

    initLearners+=initLearner
    initLearnersCnt += 1

    val termLearner = context.actorOf(Props(
      new TheoryLearner(DB, delta, breakTiesThreshold, postPruningThreshold,
        minSeenExmpls, repeatFor, chunkSize, "terminated", withInertia = withInertia,
        withPostPruning = withPostPruning, onlinePruning = onlinePruning,
        trainingSet,reScoreData = reScoreData, HLE, kernelSet = kernelSet, globals = globals)
    ), name = s"termlearner$termLearnersCnt,$HLE")

    termLearners += termLearner
    termLearnersCnt +=1
  })



  def receive = {

    case "start" =>

      this.initLearners.foreach( learner => learner ! Brothers(initLearners.toList) )
      this.termLearners.foreach( learner => learner ! Brothers(termLearners.toList) )

    case "wait" =>

      this.initLearners.foreach( learner => learner ! Wait(initLearners.toList) )
      this.termLearners.foreach( learner => learner ! Wait(termLearners.toList) )



    case f : Finished =>
      if (sender.path.name contains "init"){

        
        val buffer = ListBuffer[Clause]()
        f.theory.clauses.foreach( clause => {
          var found = false
          initTheory.clauses.foreach( initClause => if (initClause.uuid == clause.uuid) {
            found = true
            initClause.updateStatistics(
              initClause.tps + clause.tps,
              initClause.fps + clause.fps,
              initClause.fns + clause.fns,
              initClause.seenExmplsNum + clause.seenExmplsNum
            )
          })
          if (!found) buffer+= clause
        })
        initTheory = Theory( initTheory.clauses ++ buffer.toList  )
        initLearnersCnt-=1
        if (initLearnersCnt==0) sender ! GiveMeTheTheory(initTheory)
      }
      else {
        val buffer = ListBuffer[Clause]()
        f.theory.clauses.foreach( clause => {
          var found = false
          termTheory.clauses.foreach( termClause => if (termClause.uuid == clause.uuid) {
            found = true
            termClause.updateStatistics(
              termClause.tps + clause.tps,
              termClause.fps + clause.fps,
              termClause.fns + clause.fns,
              termClause.seenExmplsNum + clause.seenExmplsNum
            )
          })
          if (!found) buffer+= clause
        })
        termTheory = Theory( termTheory.clauses ++ buffer.toList  )
        termLearnersCnt-=1
        if (termLearnersCnt==0) sender ! GiveMeTheTheory(termTheory)
      }

    case Result(theory,trainingTime) =>

      //logger.info(s"HERE")

      time = Math.max(time,trainingTime)
      size-= 1

      if (sender.path.name contains "init") {
        initLearners.foreach( learner => learner ! PoisonPill)
        initTheory = theory
      }
      else {
        termLearners.foreach( learner => learner ! PoisonPill)
        termTheory = theory
      }

      if(size == 0) {

        // merge the theories and do cross-validation
        merged = initTheory.clauses ++ termTheory.clauses
        val theorySize = merged.clauses.foldLeft(0)((x,y) => x + y.body.length + 1)

        logger.info(s"$HLE theory picked: ${merged.tostring}")
        logger.info("Performing cross-validation")

        //=============================================
        val crossValJep = new Jep()
        //crossValJep.runScript(GlobalValues.ASPHandler)
        //=============================================

        val (tps,fps,fns,precision,recall,fscore) =
          crossVal(merged ,DB, crossValJep, 1500*chunkSize, data=reScoreData, globals = globals)

        //logger.info(s"\ntps: $tps\nfps: $fps\nfns: $fns\nprecision: $precision\nrecall: $recall\nf-score: $fscore\ntraining time: " +
        //  s"$time\ntheory size: $theorySize")
        val merged_ = Theory(LogicUtils.compressTheory(merged.clauses))
        logger.info(s"\nDone.$HLE Theory found:\n ${merged_.showWithStats}")
        crossValJep.close()

        context.parent ! new ResultsContainer(tps.toFloat,fps.toFloat,fns.toFloat,precision,recall,fscore,theorySize.toFloat,time,merged)

        println(s"sent $HLE results, shutting down")
        //context.stop(self)
        //context.system.shutdown()
      }
  }



  /**
    *
    * Evaluate a hypothesis on the testing data
    *
    * @param t
    * @param DB
    * @param jep
    * @param testingSetSize this is used to drop the slice of the data on the which we didn't train
    *                       (so use them for testing). This works in conjunction with trainingSetSize
    *                       (see runWithDataChunks). E.g. if DB.size = 2000 and trainingSetSize = 1000
    *                       then runWithDataChunks will take the first 1000 examples for training. So,
    *                       here, we'll drop the first 1000 and use the rest for testing.
    */
  def crossVal(t: Theory, DB: Database, jep: Jep, testingSetSize: Int, data: TrainingSet, handCraftedTheoryFile: String = "",
               globals: Globals) = {

    data match {
      case x: DataAsExamples =>
        val dataIterator =
          if (testingSetSize < WHOLE_DATA_SET_VALE) DB.collection.find().sort(MongoDBObject("exampleId" -> 1)).drop(testingSetSize)
          else DB.collection.find().sort(MongoDBObject("exampleId" -> 1))
        while (dataIterator.hasNext) {
          val e = dataIterator.next()
          val ee = new Exmpl(e)
          println(ee.id)
          evaluateTheory(t, ee, withInertia = true, jep, handCraftedTheoryFile, globals)
        }
      case x: DataAsIntervals =>
        val data = getTestingData
        while (data.hasNext) {
          val e = data.next()
          println(e.time)
          evaluateTheory(t, e, withInertia = true, jep, handCraftedTheoryFile, globals)
        }
      case _ => throw new RuntimeException("This logic is not implemented yet")
    }
    val stats = t.stats
    (stats._1, stats._2, stats._3, stats._4, stats._5, stats._6)
  }

  def getTestingData: Iterator[Exmpl] = {
    reScoreData match {
      case x: DataAsExamples =>
        reScoreData.asInstanceOf[DataAsExamples].testingSet.toIterator
      case x: DataAsIntervals =>
        CaviarUtils.getDataFromIntervals(DB,HLE,reScoreData.asInstanceOf[DataAsIntervals].testingSet,chunkSize, withChunking = false)
      case _ => throw new RuntimeException("This logic is not implemented yet")
    }
  }

  def evaluateTheory(theory: Theory, e: Exmpl, withInertia: Boolean = true, jep: Jep, handCraftedTheoryFile: String = "", globals: Globals): Unit = {
    val varbedExmplPatterns = globals.EXAMPLE_PATTERNS_AS_STRINGS
    val coverageConstr = s"${globals.TPS_RULES}\n${globals.FPS_RULES}\n${globals.FNS_RULES}"
    val t =
      if(theory != Theory()) {
        theory.clauses.map(x => x.withTypePreds(globals).tostring).mkString("\n")
      } else {
        globals.INCLUDE_BK(handCraftedTheoryFile)
      }

    val show = globals.SHOW_TPS_ARITY_1 + globals.SHOW_FPS_ARITY_1 + globals.SHOW_FNS_ARITY_1
    val ex = if(withInertia) e.exmplWithInertia.tostring else e.exmplNoInertia.tostring
    val program = ex + globals.INCLUDE_BK(globals.BK_CROSSVAL) + t + coverageConstr + show
    val f = Utils.getTempFile("isConsistent",".lp",deleteOnExit = true)
    Utils.writeLine(program, f, "overwrite")
    //val answerSet = ASP.solve(task = Globals.INFERENCE, aspInputFile = f, jep=jep)
    val answerSet = ASP.solveWithJep(task = Globals.INFERENCE, aspInputFile = f, jep=jep)
    if (answerSet.nonEmpty) {
      val atoms = answerSet.head.atoms
      atoms.foreach { a=>
        val lit = Literal.toLiteral(a)
        val inner = lit.terms.head
        lit.functor match {
          case "tps" => theory.tps += inner.tostring
          case "fps" => theory.fps += inner.tostring
          case "fns" => theory.fns += inner.tostring
        }
      }
    }
  }
}
