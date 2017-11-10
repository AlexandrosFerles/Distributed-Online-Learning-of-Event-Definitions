package oled

import java.lang

import app.Globals
import logic.{Clause, LogicUtils, Theory}
import utils.DataUtils.{DataAsExamples, DataAsIntervals, TrainingSet}
import utils.{CaviarUtils, Exmpl, Utils}
import jep.Jep
import utils.Database
import utils.Implicits._

import scala.language.postfixOps
import com.typesafe.scalalogging.LazyLogging
import akka.actor.Actor
import akka.actor._

import scala.collection.mutable.ListBuffer
import scala.language.postfixOps

/**
  * Created by nkatz on 27/2/2016.
  *
  */

/**
  *
  * @param DB The database of examples
  * @param delta Hoeffding delta
  * @param breakTiesThreshold
  * @param pruningThreshold All rules with score < this are pruned
  * @param minSeenExmpls Minimun number of examples on which a rule must be evaluated before ties may be broken
  * @param repeatFor Re-sees the data this-many times
  * @param chunkSize Instead of getting pairs from the DB, get chunks to speed things up
  * @param targetClass initiated or terminated
  * @param withInertia This is used for experiments with learning from weak/strong examples. If false, learning
  *                    is performed without inertia (for the initiated part of the theory). This parameter is
  *                    only used for getting the examples from the DB (see also CaviarUtils.getDataAsChunks)
  */


class TheoryLearner(val DB: Database,
                    val delta: Double,
                    val breakTiesThreshold: Double,
                    val pruningThreshold: Double,
                    val minSeenExmpls: Double,
                    val repeatFor: Int,
                    val chunkSize: Int,
                    targetClass: String,
                    withInertia: Boolean,
                    withPostPruning: Boolean,
                    onlinePruning: Boolean,
                    data: TrainingSet,
                    reScoreData: TrainingSet,
                    val HLE: String,
                    val learningInitWithInertia: Boolean = false,
                    kernelSet: Theory = Theory(),
                    globals: Globals) extends Actor with LazyLogging {



  /**
    * utils and methods for learning an initiatedAt or terminatedAt theory (separately)
    */

  val oldClauses = ListBuffer[Clause]()

  def copyClause(c: Clause) = {

    def basicopy(clause: Clause) = {
      val copy_ = Clause(head = clause.head, body = clause.body, uuid = clause.uuid)
      copy_.tps = clause.tps
      copy_.fps = clause.fps
      copy_.fns = clause.fns
      copy_.seenExmplsNum = clause.seenExmplsNum
      copy_.parentClause = clause.parentClause
      copy_.supportSet = clause.supportSet
      copy_
    }
    val copy = basicopy(c)
    val refinementsCopy = c.refinements.map(ref => basicopy(ref))
    copy.refinements = refinementsCopy
    copy
  }




  var timeRunningMean = 0.0
  var groundRunningMean = 0.0
  var exmplCounter=0
  var firstGrow = true

  var firstScore = true
  var oldLength = 0

  val name = self.path.name
  val bottomClauses =
    if (kernelSet.clauses.nonEmpty) {
      if (this.targetClass == "initiated") {
        Theory(kernelSet.clauses.filter(x => x.head.tostring.contains("initiatedAt")))
      } else {
        Theory(kernelSet.clauses.filter(x => x.head.tostring.contains("terminatedAt")))
      }
    } else {
      kernelSet
    }

  var theory = Theory()

  var clausesToRefine = ListBuffer[Int]()
  var execTime : Double = 0

  var dataToUse = getTrainingData
  for ( i <-1 until repeatFor) dataToUse ++= getTrainingData
  val myData = dataToUse
  var rounds = 0
  var finished = false

  var brothers = List[ActorRef]()
  var clauseMapping = scala.collection.mutable.Map[Int, List[(Int,ActorRef)]]() //map of a clauseID towards clauses in each learners

  val myName = self.path.name
  val parent = context.parent.path.name

  var waiting = false
  var pendingClauses = 0

  var lastToFinish = false
  var finalTheorySent = false

  def update( clause: Clause, rule: Clause) : Unit={

    try {
      clause.updateStatistics(
        clause.tps + rule.tps - clause.clauseIndices(rule.uuid)._1 - rule.clauseIndices(clause.uuid)._1,
        clause.fps + rule.fps - clause.clauseIndices(rule.uuid)._2 - rule.clauseIndices(clause.uuid)._2,
        clause.fns + rule.fns - clause.clauseIndices(rule.uuid)._3 - rule.clauseIndices(clause.uuid)._3,
        clause.seenExmplsNum + rule.seenExmplsNum - clause.clauseIndices(rule.uuid)._4 - rule.clauseIndices(clause.uuid)._4
      )

      clause.clauseIndices(rule.uuid) = (
        rule.tps - rule.clauseIndices(clause.uuid)._1,
        rule.fps - rule.clauseIndices(clause.uuid)._2,
        rule.fns - rule.clauseIndices(clause.uuid)._3,
        rule.seenExmplsNum - rule.clauseIndices(clause.uuid)._4
        )
    }
    catch {
      case e: java.util.NoSuchElementException =>


        try {
          clause.updateStatistics(
            clause.tps + rule.tps - rule.clauseIndices(clause.uuid)._1,
            clause.fps + rule.fps - rule.clauseIndices(clause.uuid)._2,
            clause.fns + rule.fns - rule.clauseIndices(clause.uuid)._3,
            clause.seenExmplsNum + rule.seenExmplsNum - rule.clauseIndices(clause.uuid)._4
          )

          clause.clauseIndices(rule.uuid) = (
            rule.tps - rule.clauseIndices(clause.uuid)._1,
            rule.fps - rule.clauseIndices(clause.uuid)._2,
            rule.fns - rule.clauseIndices(clause.uuid)._3,
            rule.seenExmplsNum - rule.clauseIndices(clause.uuid)._4
            )
        }
        catch {
          case e: java.util.NoSuchElementException =>

            clause.updateStatistics(
              clause.tps + rule.tps,
              clause.fps + rule.fps,
              clause.fns + rule.fns,
              clause.seenExmplsNum + rule.seenExmplsNum
            )

            clause.clauseIndices(rule.uuid) = (clause.tps, clause.fps, clause.fns, clause.seenExmplsNum)
        }
    }

    val refinementPairs = clause.refinements zip rule.refinements
    refinementPairs.foreach(pair => {

      val clauseRef = pair._1
      val ruleRef = pair._2

      try {
        clauseRef.updateStatistics(
          clauseRef.tps + ruleRef.tps - clauseRef.clauseIndices(ruleRef.uuid)._1 - ruleRef.clauseIndices(clauseRef.uuid)._1,
          clauseRef.fps + ruleRef.fps - clauseRef.clauseIndices(ruleRef.uuid)._2 - ruleRef.clauseIndices(clauseRef.uuid)._2,
          clauseRef.fns + ruleRef.fns - clauseRef.clauseIndices(ruleRef.uuid)._3 - ruleRef.clauseIndices(clauseRef.uuid)._3,
          clauseRef.seenExmplsNum + ruleRef.seenExmplsNum - clauseRef.clauseIndices(ruleRef.uuid)._4 - ruleRef.clauseIndices(clauseRef.uuid)._4
        )

        clauseRef.clauseIndices(ruleRef.uuid) = (
          ruleRef.tps - ruleRef.clauseIndices(clauseRef.uuid)._1,
          ruleRef.fps - ruleRef.clauseIndices(clauseRef.uuid)._2,
          ruleRef.fns - ruleRef.clauseIndices(clauseRef.uuid)._3,
          ruleRef.seenExmplsNum - ruleRef.clauseIndices(clauseRef.uuid)._4
          )

      }
      catch {
        case e: java.util.NoSuchElementException =>


          try {
            clauseRef.updateStatistics(
              clauseRef.tps + ruleRef.tps - ruleRef.clauseIndices(clauseRef.uuid)._1,
              clauseRef.fps + ruleRef.fps - ruleRef.clauseIndices(clauseRef.uuid)._2,
              clauseRef.fns + ruleRef.fns - ruleRef.clauseIndices(clauseRef.uuid)._3,
              clauseRef.seenExmplsNum + ruleRef.seenExmplsNum - ruleRef.clauseIndices(clauseRef.uuid)._4
            )

            clauseRef.clauseIndices(ruleRef.uuid) = (
              ruleRef.tps - ruleRef.clauseIndices(clauseRef.uuid)._1,
              ruleRef.fps - ruleRef.clauseIndices(clauseRef.uuid)._2,
              ruleRef.fns - ruleRef.clauseIndices(clauseRef.uuid)._3,
              ruleRef.seenExmplsNum - ruleRef.clauseIndices(clauseRef.uuid)._4
              )

          }
          catch {
            case e: java.util.NoSuchElementException =>

              //logger.info(s"2 EXCEPTIONS")


              clauseRef.updateStatistics(
                clauseRef.tps + ruleRef.tps,
                clauseRef.fps + ruleRef.fps,
                clauseRef.fns + ruleRef.fns,
                clauseRef.seenExmplsNum + ruleRef.seenExmplsNum
              )

              clauseRef.clauseIndices(ruleRef.uuid) = (clauseRef.tps, clauseRef.fps, clauseRef.fns, clauseRef.seenExmplsNum)
          }
      }
    })

  }


  def receive = {

    case d: DropClause =>
      val buffer = ListBuffer[Clause]()
      this.theory.clauses.foreach( clause => if (clause.uuid==d.id) oldClauses+= clause else buffer += clause)
      this.theory=Theory(buffer.toList)


    case g: GiveMeTheTheory =>
      val completeTheory = pruneTheory(g.theory)
      sender ! Result(completeTheory,execTime)

    case "proceed" =>
      if (!finished) {
        run
        if ( (!finished) ) self ! "proceed"
      }

    case Brothers(actors) =>
      var temp = ListBuffer[ActorRef]()
      actors.foreach( actor => if (actor.path.name != name) temp+=actor)
      brothers = temp.toList
      //logger.info(s"starting learning $HLE theories for $targetClass from ${DB.name}")
      run
      self ! "proceed"

    case NewRule( clause ) =>
      this.theory = Theory( this.theory.clauses ++ List(clause))

    case IndicesPlease( id , requester) =>
      var found = false
      this.theory.clauses.foreach( clause => if (clause.uuid == id ) {
        found = true
        requester ! Response(clause)
      } )
      if (!found){
        this.oldClauses.foreach( clause => if (clause.uuid == id ) {
          found = true
          requester ! Response(clause)
        })
      }
      if (!found) self ! IndicesPlease(id, requester)



    case RefinedRule( idToDiscard , refinedRule ) =>
      val buffer = ListBuffer[Clause](refinedRule)
      this.theory.clauses.foreach( clause => if (clause.uuid != idToDiscard) buffer +=clause)
      this.theory = Theory(buffer.toList)

    case Response(rule) =>
      this.theory.clauses.foreach( clause =>{

        if (clause.uuid == rule.uuid) {
          update(clause, rule)
          clause.actorsResponded += 1
          if (clause.actorsResponded == brothers.length) {

            val (expand, newEpsilon, newObservedDiff, finalBest, finalSecondBest) = rightWay(clause)
            expand match {
              case true =>
                val finalTest =
                  if (finalSecondBest != clause) (finalBest.score > clause.score) && (finalBest.score - clause.score > newEpsilon)
                  else finalBest.score > clause.score
                finalTest match {
                  case true =>
                    val refinedRule = finalBest
                    refinedRule.supportSet = clause.supportSet
                    refinedRule.generateCandidateRefs
                    refinedRule.seenExmplsNum = 0

                    val tempTheory = ListBuffer[Clause](refinedRule)
                    theory.clauses.foreach(otherClause => if (otherClause.uuid != clause.uuid) tempTheory += otherClause else oldClauses+= otherClause)
                    theory = Theory(tempTheory.toList)

                    brothers.foreach( _ ! RefinedRule(clause.uuid, copyClause(refinedRule)) )

                    logger.info(showInfo(clause, finalBest, finalSecondBest, newEpsilon, newObservedDiff, clause.seenExmplsNum))

                  case false =>
                    clause.actorsResponded = 0
                }

              case false =>
                clause.actorsResponded = 0
            }
          }
        }
      })
  }

  val jep = new Jep()
  //jep.runScript(GlobalValues.ASPHandler)

  val specialBKfile = if(targetClass=="initiated") globals.BK_INITIATED_ONLY else globals.BK_TERMINATED_ONLY

  val initorterm = if(targetClass=="initiated") "initiatedAt" else "terminatedAt"

  def getData = utils.CaviarUtils.getDataAsChunks(DB, chunkSize, targetClass, withInertia).take(1500)

  def getTrainingData: Iterator[Exmpl] = {
    /*
    if (trainingData.asInstanceOf[TrainingSet].trainingSet == Nil) {
      trainingData.asInstanceOf[iled.core.noisyILED.experimentsMLNdata.MLNDataHandler.TrainingSet].trainingData.toIterator
    } else {
      if (this.trainingData == TrainingSet()) getData else CaviarUtils.getDataFromIntervals(DB,HLE,this.trainingData.trainingSet,chunkSize)
    }
    */

    data match {
      case x: DataAsIntervals => if (data.isEmpty) getData else CaviarUtils.getDataFromIntervals(DB, HLE, data.asInstanceOf[DataAsIntervals].trainingSet, chunkSize)
      case x: DataAsExamples => data.asInstanceOf[DataAsExamples].trainingSet.toIterator
      case _ => throw new RuntimeException(s"${data.getClass}: Don't know what to do with this data container!")
    }
  }

  def getReScoreData = utils.CaviarUtils.getDataAsChunks(DB, chunkSize, targetClass, withInertia).take(1500)
  def getReScoreTrainingData: Iterator[Exmpl] = {
    /*
    if (trainingData.asInstanceOf[TrainingSet].trainingSet == Nil) {
      trainingData.asInstanceOf[iled.core.noisyILED.experimentsMLNdata.MLNDataHandler.TrainingSet].trainingData.toIterator
    } else {
      if (this.trainingData == TrainingSet()) getData else CaviarUtils.getDataFromIntervals(DB,HLE,this.trainingData.trainingSet,chunkSize)
    }
    */

    data match {
      case x: DataAsIntervals => if (data.isEmpty) getReScoreData else CaviarUtils.getDataFromIntervals(DB, HLE, data.asInstanceOf[DataAsIntervals].trainingSet, chunkSize)
      case x: DataAsExamples => data.asInstanceOf[DataAsExamples].trainingSet.toIterator
      case _ => throw new RuntimeException(s"${data.getClass}: Don't know what to do with this data container!")
    }
  }

  def run: ( Theory , Double )= {

    def runOnce(inTheory: Theory): Theory = {
      //val dataChunks = scala.util.Random.shuffle(iled.utils.CaviarUtils.getDataAsChunks(DB, chunkSize)) // do not shuffle to see what happens
      // helper method to compute the running mean for various statistics
      def getRunningMean(what: String, newValue: Double) = {
        // The running average can be computed by
        // ((prevAvg*n) + newValue)/(n+1)
        // where n is the number of seen data points
        def runningMean(prevAvg: Double, newVal: Double, n: Int) = ((prevAvg * n) + newValue) / (n + 1)
        what match {
          case "time" => runningMean(this.timeRunningMean, newValue, this.exmplCounter)
          case "groundPrg" => runningMean(this.groundRunningMean, newValue, this.exmplCounter)
        }
      }

      if (myData.isEmpty){
        finished = true
        this.theory
      }
      else {
        val res = Utils.time {
          processExample(this.theory, myData.next, learningInitWithInertia)
        }
        res._1
      }
    }

    //val _finalTheory = Utils.time{ (1 to 1).foldLeft(this.theory)( (t,_) =>  runOnce(t)) }
    val _finalTheory = Utils.time{ runOnce(this.theory)}
    //logger.info(s" $parent examples finished!")
    val (finalTheory,time) = (_finalTheory._1,_finalTheory._2)
    this.execTime += time

    if (finished) {
      context.parent ! Finished(finalTheory)
      (finalTheory,execTime)
    }
    else{
      (finalTheory, time )
    }
  }

  def pruneTheory (theory: Theory): Theory ={

    logger.info(s"$targetClass learning completed in all learners, theory for $targetClass clauses for $HLE before pruning is:")
    logger.info(s"${theory.showWithStats}")

    val pruned = theory.clauses.filter(x => (x.score > pruningThreshold) && (x.tps+x.fps+x.fns > 70) )
    Theory(pruned)
  }

  def processExample(topTheory: Theory, e: Exmpl, learningInitWithInertia: Boolean): Theory = {


    var newTopTheory = topTheory
    if (this.bottomClauses.clauses.isEmpty){ // in the opposite case we've collected the BCs in a first pass over the data.
    //logger.info(s" theory of grow new rule test:")
    //newTopTheory.clauses.foreach( clause => logger.info( clause.tostring))
    //logger.info(s"got here!")
    val startNew = this.theory.growNewRuleTest(e,this.jep, initorterm, globals)
      if (startNew) {

        val newRules = generateNewRules(this.theory, e, learningInitWithInertia)
        //logger.info(s"${newRules.length} new rules from $name")
        //logger.info(s" ${self.path.name} created ${newRules.length} rules!")
        newTopTheory = topTheory.clauses ++ newRules
        this.theory = newTopTheory

        newRules.foreach(rule => {
          rule.generateCandidateRefs
          brothers.foreach( _ ! NewRule(copyClause(rule)) )
          })
      }
    }

    if (newTopTheory.clauses.nonEmpty) {
      if(firstScore){
        //logger.info(s"i am $name, scoring rules for theory:${topTheory.tostring} in round $rounds")
        firstScore = false
      }
      this.theory.scoreRules(e.exmplWithInertia, this.jep, globals)
      try {
        val expanded  = expandRules(this.theory,e.time)
        if (onlinePruning) {
          pruneRules(expanded)
        } else {
          this.theory = expanded
          this.theory
        }
      } catch {
        case z: IndexOutOfBoundsException =>
          println(s"top theory:\n ${topTheory.tostring}")
          println(e.id)
          Theory()
      }
    } else {
      if (this.bottomClauses.clauses.isEmpty) {
        this.theory
      } else {
        // generate a top theory from the already constructed bottom clauses
        val top = this.bottomClauses.clauses map { x =>
          val c = Clause(head=x.head, body = List())
          logger.debug(s"Started growing new rule: \n ${c.tostring} from bottom clause: \n ${x.tostring}")
          c.addToSupport(x)
          c
        }
        this.theory = Theory(top)
        this.theory
        //Theory(top)
      }
    }
  }

  def pruneRules(topTheory: Theory) = {
    val pruned = topTheory.clauses.foldLeft(List[Clause]()){ (keep, clause) =>
      val epsilon = Utils.hoeffding(delta, clause.seenExmplsNum)
      val meanPruningScore = clause.meanScorePruning(this.pruningThreshold)
      //if (this.pruningThreshold - meanScore > epsilon && clause.seenExmplsNum > minSeenExmpls) {
      if (meanPruningScore > epsilon && clause.seenExmplsNum > minSeenExmpls*10 && clause.score < this.pruningThreshold) {
        //logger.info(s"\nPruned clause:\n${clause.tostring}\nMean score" +
        // s" so far: ${clause.meanScorePruning(this.pruningThreshold)} | tps: ${clause.tps} fps: ${clause.fps}, fns: ${clause.fns}")
        oldClauses+=clause
        brothers.foreach( _ ! DropClause(clause.uuid))
        logger.info(s" $name, dropped ${clause.tostring}")
        keep
      } else {
        keep :+ clause
      }
    }
    Theory(pruned)
  }

  def generateNewRules(topTheory: Theory, e: Exmpl, learningInitWithInertia: Boolean = false) = {
    val terminatedOnly = if(initorterm=="terminatedAt") true else false
    val (_, varKernel) =
      LogicUtils.generateKernel(e.exmplWithInertia.toMapASP, jep = this.jep, learningTerminatedOnly = terminatedOnly,
        oledLearningInitWithInertia = learningInitWithInertia, bkFile = specialBKfile, globals=globals)
    val bottomTheory = topTheory.clauses flatMap(x => x.supportSet.clauses)
    val goodKernelRules = varKernel.filter(newBottomRule => !bottomTheory.exists(supportRule => newBottomRule.thetaSubsumes(supportRule)))
    ///*
    goodKernelRules map { x =>
      val c = Clause(head=x.head, body = List())
      logger.debug(s"Started growing new rule: \n ${c.tostring} from bottom clause: \n ${x.tostring}")
      c.addToSupport(x)
      c
    }
  }

  def wrongWay(parentRule: Clause) = {
    val best2 = parentRule.refinements.sortBy { x => - x.score }.take(2)
    val best = best2.head
    val secondBest = best2(1)
    val epsilon = Utils.hoeffding(delta, parentRule.seenExmplsNum)
    val observedDiff = best2.head.score - best2(1).score
    val passesTest = if (epsilon < observedDiff) true else false
    val tie = if (epsilon <= breakTiesThreshold && parentRule.seenExmplsNum >= minSeenExmpls) true else false
    val couldExpand = passesTest || tie
    (couldExpand,epsilon,observedDiff,best,secondBest)
  }

  def rightWay(parentRule: Clause) = {
    val (observedDiff,best,secondBest) = parentRule.meanDiff
    val epsilon = Utils.hoeffding(delta, parentRule.seenExmplsNum)
    val passesTest = if (epsilon < observedDiff) true else false
    //val tie = if (epsilon <= breakTiesThreshold && parentRule.seenExmplsNum >= minSeenExmpls) true else false
    val tie = if (observedDiff < epsilon  && epsilon < breakTiesThreshold && parentRule.seenExmplsNum >= minSeenExmpls) true else false

    val couldExpand = passesTest || tie
    (couldExpand,epsilon,observedDiff,best,secondBest)
  }

  def expandRules(topTheory: Theory, time:String): Theory  = {

    val out = topTheory.clauses flatMap { parentRule =>
      val (couldExpand,epsilon,observedDiff,best,secondBest) = rightWay(parentRule)
      //println(best.score, parentRule.score, epsilon)
      couldExpand match {
        case true =>
          val extraTest =
            if(secondBest != parentRule) (best.score > parentRule.score) && (best.score - parentRule.score > epsilon)
            else best.score > parentRule.score
          extraTest match {
            case true =>

              var alreadyRequested = false
              this.clausesToRefine.foreach( id => if (id == parentRule.uuid) alreadyRequested= true)
              if (!alreadyRequested) {
                this.clausesToRefine += parentRule.uuid
                brothers.foreach( _ ! IndicesPlease(parentRule.uuid, self) )
              }

              List(parentRule)
            case false => List(parentRule)
          }
        case false => List(parentRule)
      }
    }
    Theory(out)
  }

  //def debugExpandRules()
  /**
    *
    * Quick and dirty solution for post-pruning. Go through the training set again and score rules.
    * "what" is either initiated or terminated and it is used to specify which BK file to use
    *
    * @param DB
    * @param theory
    * @param chunkSize
    * @param jep
    * @param trainingSetSize
    * @param what
    *
    */
  def reScore(DB: Database, theory: Theory,
              //chunkSize: Int, jep: Jep,
              chunkSize: Int,
              trainingSetSize: Int, what: String, withInertia: Boolean) = {
    val dataChunks = getReScoreTrainingData
    theory.clauses foreach (p => p.clearStatistics) // zero all counters before re-scoring
    for (x <- dataChunks) {
      //println(x.id)
      theory.scoreRules(x.exmplWithInertia, jep, globals, postPruningMode = true)
    }
  }


  def showInfo(c: Clause, c1: Clause, c2: Clause, hoeffding: Double, observedDiff: Double, n: Int) = {

    /*
    val similarity = (x: Clause) => {
      if (this.targetClass=="initiated"){
        //println(Theory(GlobalValues.CurrentTheoryInitiated).tostring)
        Utils.similarity(x,GlobalValues.CurrentTheoryInitiated)
      } else {
        //println(Theory(GlobalValues.CurrentTheoryTerminated).tostring)
        Utils.similarity(x,GlobalValues.CurrentTheoryTerminated)
      }
    }
    */
    s"\n===========================================================\n" +
      s"\n$name: Clause (id: ${c.uuid} | score: ${c.score} | tps: ${c.tps} fps: ${c.fps} fns: ${c.fns})\n\n${c.tostring}\n\nwas refined to" +
      s" (new score: ${c1.score} | tps: ${c1.tps} fps: ${c1.fps} fns: ${c1.fns})\n\n${c1.tostring}\n\nε: $hoeffding, ΔG: $observedDiff, examples used: $n, round: $rounds" +
      //s"\nall refs: \n\n ${c.refinements.sortBy(z => -z.score).map(x => x.tostring+" "+" | score "+x.score+" | similarity "+similarity(x)).mkString("\n")}" +
      //s"\nall refs: \n\n ${c.refinements.sortBy(z => (-z.score,z.body.length+1)).map(x => x.tostring+" | score "+x.score+" (tps|fps|fns): "+(x.tps,x.fps,x.fns)).mkString("\n")}" +
      s"\n===========================================================\n"

    /*
    "\n========================================" +
      s"\nHoeffding bound : $hoeffding" +
      s"\nobserved mean difference: $observedDiff" +
      s"\nNumber of examples used: $n\n" +
      "------------------------------------------------\n" +
      "Current rule (" + c.score + "): \n" +
      c.tostring + "\n" +
      "------------------------------------------------\n" +
      "Best refinement (" + c1.score + "): \n" +
      c1.tostring + "\n" +
      "------------------------------------------------\n" +
      "second best refinement (" + c2.score + "): \n" +
      c2.tostring + "\n" +
      "============================================"
    */
  }

}