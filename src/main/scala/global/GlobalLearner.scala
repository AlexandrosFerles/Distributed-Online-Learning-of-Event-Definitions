package global

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
import com.mongodb.DB

import scala.collection.mutable.ListBuffer
import scala.language.postfixOps
import oled.{Finished, Result}

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


class GlobalLearner(    val caviar: Database,
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
                        val reScoreData : TrainingSet,
                        val HLE: String,
                        kernelSet: Theory = Theory(),
                        globals: Globals) extends Actor with LazyLogging {

  var myRepeatFor = this.repeatFor-1
  var testData = getTrainingData

  var timeRunningMean = 0.0
  var groundRunningMean = 0.0
  var exmplCounter=0

  var myData = getTrainingData
  for ( i<- 1 until repeatFor) myData ++= getTrainingData

  var oldLength = 0
  val name = self.path.name

  var lastToFinish = false

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

  var rounds = 0
  var finished = false

  val ids = ListBuffer[Int]()

  val myName = self.path.name
  val parent = context.parent.path.name

  var trainingTime = 0.0

  val oldClauses = ListBuffer[Clause]()
  val clausesForRefinement = ListBuffer[Int]()

  //def a = scala.collection.mutable.Map[ Int , String]()


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

            //logger.info(s"ONE EXCEPTION")

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
  def decide ( clause : Clause) : ( Boolean, Clause) ={

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
            refinedRule.seenExmplsNum = 0
            refinedRule.generateCandidateRefs

            logger.info(showInfo(clause, finalBest, finalSecondBest, newEpsilon, newObservedDiff, clause.seenExmplsNum))

            (true,refinedRule)

          case false =>
            (false, Clause() )
        }

      case false =>
        logger.info(s"Not expanded to ${finalBest.tostring}")
        (false, Clause() )
    }
  }

  def copyClause(c: Clause) = {

    def basicopy(clause: Clause) = {
      val copy_ = Clause(head = clause.head, body = clause.body, uuid = clause.uuid)
      //copy_.uuid = clause.uuid
      copy_.tps = clause.tps
      copy_.fps = clause.fps
      copy_.fns = clause.fns
      copy_.seenExmplsNum = clause.seenExmplsNum
      //copy_.generatedAtNode = clause.generatedAtNode

      // don't copy these, there's no need (nothing changes in the parent clause or the support set) and copying
      // it makes it messy to retrieve ids in other nodes
      copy_.parentClause = clause.parentClause
      copy_.supportSet = clause.supportSet
      copy_
    }
    val copy = basicopy(c)
    val refinementsCopy = c.refinements.map(ref => basicopy(ref))
    copy.refinements = refinementsCopy
    copy
  }

  def decide2( clause : Clause, orders : Boolean) : (Boolean,Clause)= {

    val copy = copyClause(clause)
    //logger.info(s" copy indices before update: ${copy.tps}, ${copy.fps},${copy.fns}, ${copy.seenExmplsNum}")

    clause.brotherClauses.foreach(rule => {
      copy.updateStatistics(copy.tps + rule.tps, copy.fps + rule.fps, copy.fns + rule.fns, copy.seenExmplsNum + rule.seenExmplsNum)

      val pairs = copy.refinements zip rule.refinements
      pairs.foreach(pair => {
        val copyRefinement = pair._1
        val ruleRefinement = pair._2

        copyRefinement.updateStatistics(copyRefinement.tps + ruleRefinement.tps, copyRefinement.fps + ruleRefinement.fps, copyRefinement.fns + ruleRefinement.fns, copyRefinement.seenExmplsNum + ruleRefinement.seenExmplsNum)
      })
    })

    val (expand, newEpsilon, newObservedDiff, finalBest, finalSecondBest) = rightWay(copy)
    expand match {
      case true =>
        val finalTest =
          if (finalSecondBest != copy) (finalBest.score > copy.score) && (finalBest.score - copy.score > newEpsilon)
          else finalBest.score > copy.score
        finalTest match {
          case true =>

            if (orders) {
              val refinedRule = finalBest
              refinedRule.supportSet = copy.supportSet
              refinedRule.seenExmplsNum = 0
              refinedRule.generateCandidateRefs
              if ( refinedRule.refinements.length == 0) {
                logger.info(s"No refinements  made in CLAUSE UPDATE ++ isCopy:${clause.isCopy}")
              }

              logger.info(showInfo(copy, finalBest, finalSecondBest, newEpsilon, newObservedDiff, clause.seenExmplsNum))

              (true, refinedRule)
            }
            else {
              (true,Clause())
            }

          case false =>
            if ( orders) sender ! NoEvaluationDone(clause.uuid)
            (false, Clause() )
        }

      case false =>
        if (orders) {
          logger.info(s"Not expanded to ${finalBest.tostring}")
          sender ! NoEvaluationDone(clause.uuid)
        }
        (false, Clause() )
    }

  }


  def receive = {

    case "go" =>
      //logger.info(s"starting learning for $targetClass from ${caviar.name}")
      run
      sender ! ProcessedBatchGlobal

    case e: EvaluateExpansion =>

      val old = this.clausesForRefinement.length
      clausesForRefinement -= e.id
      if (this.clausesForRefinement.length == old) logger.info(s"THIS SHOULD NOT HAVE HAPPENED")
      if (e.copies.length == 0) logger.info(s"zero fucks given")

      val updatedClauses = ListBuffer[Clause]()
      val myIdsToRemove = ListBuffer[Int]()
      this.theory.clauses.foreach( clause =>{

        if (clause.uuid == e.id){

          clause.brotherClauses = e.copies
          val (expanded,newRule) = decide2(clause,orders = true)


          //e.copies.foreach( rule => update( clause , rule))
          //val (expanded,newRule) = decide(clause)
          if (expanded){
            updatedClauses += newRule
            myIdsToRemove += clause.uuid
            oldClauses += clause
            context.parent ! RefinedRuleGlobal( clause.uuid , newRule)
          }
          else {
            context.parent ! NoEvaluationDone
          }
        }
      })

      var debug = 0

      if (updatedClauses.length > 0) {
        val buffer=ListBuffer[Clause]()
        this.theory.clauses.foreach( clause =>{
          var found = false
          myIdsToRemove.foreach( id =>{
            if ( id == clause.uuid) found = true
          })
          if (found) oldClauses += clause
          else buffer +=clause
        })
        this.theory = Theory(updatedClauses.toList ++ buffer.toList)
      }

      debug = 0

    case RescoreGlobal(completeTheory) =>
      sender ! Result( pruneTheory(completeTheory) , 0.0 )


    case c : ClausesWantedGlobal =>

      if ( c.newClauses.length > 0) this.theory = Theory( this.theory.clauses ++ c.newClauses)

      var found = false
      this.theory.clauses.foreach( clause =>{
        if ( clause.uuid == c.original){
          sender ! ResponseGlobal(c.original , clause)
          found = true
        }
      })
      if (!found){
        oldClauses.foreach( clause =>{
          if ( clause.uuid == c.original){
            sender ! ResponseGlobal(c.original , clause)
            found = true
          }
        })
      }
      if (!found) {
        //for debugging
        logger.info(s",$name NOT FOUND")
        sender ! ResponseGlobal(c.original , new Clause() )
        //sender ! NotFound( c.original, c.copy)
      }

    case ProceedGlobal(idsToRemove, clausesToAdd) =>

      val buffer = ListBuffer[Clause]()

      if (idsToRemove.length>0) {
        this.theory.clauses.foreach(clause => {
          var found = false
          idsToRemove.foreach(id => {
            if (clause.uuid == id) found = true
          })
          if (!found) buffer+=clause
        })
      }

      if (clausesToAdd.length > 0 ) {
        debug =0
      }

      if ( clausesToAdd.length>0){
        if (buffer.length >0)
          this.theory = Theory( clausesToAdd ++ buffer.toList)
        else
          this.theory = Theory( clausesToAdd ++ this.theory.clauses)
      }
      else
      if (buffer.length>0)
        this.theory=Theory(buffer.toList)

      if (clausesToAdd.length > 0 ) {
        debug =0
      }

      rounds+=1
      run
      if (!finished) sender ! ProcessedBatchGlobal
  }

  var debug =0

  val jep = new Jep()
  //jep.runScript(GlobalValues.ASPHandler)


  val specialBKfile = if(targetClass=="initiated") globals.BK_INITIATED_ONLY else globals.BK_TERMINATED_ONLY

  val initorterm = if(targetClass=="initiated") "initiatedAt" else "terminatedAt"

  //TODO: below are all helper function definitions

  def pruneTheory (theory: Theory): Theory ={

    val temp = ListBuffer(theory.clauses.head)
    theory.clauses.tail.foreach( rule =>{
      var found = false
      temp.foreach(clause => if (clause.tostring == rule.tostring) found = true)
      if (!found) temp += rule
    })

    val finalTheory = Theory(temp.toList)
    reScore(caviar,finalTheory,chunkSize,1500,targetClass, withInertia)

    logger.info(s"$targetClass learning completed in all learners, theory for $targetClass clauses for $HLE before pruning is:")
    logger.info(s"${theory.showWithStats}")

    val pruned = finalTheory.clauses.filter(x => (x.score > pruningThreshold) )
    Theory(pruned)
  }

  def getData = utils.CaviarUtils.getDataAsChunks(caviar, chunkSize, targetClass, withInertia).take(1500)

  def getTrainingData: Iterator[Exmpl] = {
    /*
    if (trainingData.asInstanceOf[TrainingSet].trainingSet == Nil) {
      trainingData.asInstanceOf[iled.core.noisyILED.experimentsMLNdata.MLNDataHandler.TrainingSet].trainingData.toIterator
    } else {
      if (this.trainingData == TrainingSet()) getData else CaviarUtils.getDataFromIntervals(DB,HLE,this.trainingData.trainingSet,chunkSize)
    }
    */

    data match {
      case x: DataAsIntervals => if (data.isEmpty) getData else {
        CaviarUtils.getDataFromIntervals(caviar, HLE, data.asInstanceOf[DataAsIntervals].trainingSet, chunkSize)
      }
      case x: DataAsExamples => data.asInstanceOf[DataAsExamples].trainingSet.toIterator
      case _ => throw new RuntimeException(s"${data.getClass}: Don't know what to do with this data container!")
    }
  }
  def getReScoreData = utils.CaviarUtils.getDataAsChunks(caviar, chunkSize, targetClass, withInertia).take(1500)

  def getReScoreTrainingData: Iterator[Exmpl] = {

    data match {
      case x: DataAsIntervals => if (reScoreData.isEmpty) getReScoreData else CaviarUtils.getDataFromIntervals(caviar, HLE, reScoreData.asInstanceOf[DataAsIntervals].trainingSet, chunkSize)
      case x: DataAsExamples => reScoreData.asInstanceOf[DataAsExamples].trainingSet.toIterator
      case _ => throw new RuntimeException(s"${data.getClass}: Don't know what to do with this data container!")
    }
  }

  val startTime = System.currentTimeMillis()

  def run:  Theory = {

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
      /*
      if (testData.isEmpty){
        if (myRepeatFor == 0){
          this.execTime = ((System.currentTimeMillis() - startTime).toDouble/1000.0).toDouble
          this.finished = true
          this.theory
        }
        else{
          myRepeatFor-=1
          testData = getTrainingData
          processExample(this.theory, testData.next, withInertia)
        }
      }
      else processExample(this.theory, testData.next, withInertia)
      */

      if (myData.isEmpty){
        this.execTime = ((System.currentTimeMillis() - startTime).toDouble/1000.0).toDouble
        this.finished = true
        this.theory
      }
      else processExample(this.theory, myData.next, withInertia)

    }

    val _finalTheory = Utils.time{ runOnce(this.theory)}
    val (finalTheory,time) = (_finalTheory._1,_finalTheory._2)

    if (finished) {
      logger.info(s"$myName, finished!")
      context.parent ! ResultGlobal( this.theory, this.oldClauses.toList, this.execTime)
      finalTheory
    }
    else{
      finalTheory
    }
  }

  def processExample(topTheory: Theory, e: Exmpl, learningInitWithInertia: Boolean): Theory = {

    var newTopTheory = topTheory
    if (this.bottomClauses.clauses.isEmpty){ // in the opposite case we've collected the BCs in a first pass over the data.
    val startNew = this.theory.growNewRuleTest(e,this.jep, initorterm, globals)
      if (startNew) {

        val newRules = generateNewRules(this.theory, e, learningInitWithInertia)
        newTopTheory = topTheory.clauses ++ newRules
        this.theory = newTopTheory

        newRules.foreach(rule => {
          rule.generateCandidateRefs
          if ( rule.refinements.length == 0) logger.info(s"No refinements made in NEW RULES")
        })
        context.parent ! NewRuleGlobal(newRules)
      }
    }

    if (newTopTheory.clauses.nonEmpty) {
      this.theory.scoreRules(e.exmplWithInertia, this.jep, globals)
      try {
        val expanded  = expandRules(this.theory,e.time)
        if (onlinePruning) {
          pruneRules(expanded)
          this.theory
        } else {
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
    val pruned = this.theory.clauses.foldLeft(List[Clause]()){ (keep, clause) =>
      val epsilon = Utils.hoeffding(delta, clause.seenExmplsNum)
      val meanPruningScore = clause.meanScorePruning(this.pruningThreshold)
      if (meanPruningScore > epsilon && clause.seenExmplsNum > 20000) {
        logger.info(s"I want to prune clause:\n${clause.tostring}, ${clause.uuid}\nMean score" +
         s" so far: ${clause.meanScorePruning(this.pruningThreshold)} | tps: ${clause.tps} fps: ${clause.fps}, fns: ${clause.fns}, examples: ${clause.seenExmplsNum}")
        context.parent ! RemoveRule(clause.uuid)
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

    val out = this.theory
    topTheory.clauses.foreach( parentRule =>{
      val (whatToDo,_) = decide2(parentRule, orders = false)
      if (whatToDo){
        var alreadyRequested = false
        this.clausesForRefinement.foreach( id => if (id == parentRule.uuid) alreadyRequested=true)
        if (!alreadyRequested) {
          this.clausesForRefinement+= parentRule.uuid
          context.parent ! IndicesPleaseGlobal(parentRule.uuid )
        }
      }
    })
    /*
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
                  this.clausesForRefinement.foreach( id => if (id == parentRule.uuid) alreadyRequested=true)
                  if (!alreadyRequested) {
                    this.clausesForRefinement+= parentRule.uuid
                    context.parent ! IndicesPleaseGlobal(parentRule.uuid )
                  }
                  //if ( targetClass contains "term") logger.info(s"$name, i want to expand ${parentRule.uuid} to ${best.tostring}, round:$rounds")
                  List(parentRule)
                case false => List(parentRule)
              }
            case false => List(parentRule)
          }
        }
      */

    out
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
      s"\n$name: Clause (id: ${c.uuid} | score: ${c.score} | tps: ${c.tps} fps: ${c.fps} fns: ${c.fns} exmpls: ${c.seenExmplsNum})\n\n${c.tostring}\n\nwas refined to" +
      s" (new score: ${c1.score} | tps: ${c1.tps} fps: ${c1.fps} fns: ${c1.fns} exmpls: ${c1.seenExmplsNum})\n\n${c1.tostring}\n\nε: $hoeffding, ΔG: $observedDiff, examples used: $n, round: $rounds" +
      //s"\nall refs: \n\n ${c.refinements.sortBy(z => -z.score).map(x => x.tostring+" "+" | score "+x.score+" | similarity "+similarity(x)).mkString("\n")}" +
      //s"\nall refs: \n\n ${c.refinements.sortBy(z => (-z.score,z.body.length+1)).map(x => x.tostring+" | score "+x.score+" (tps|fps|fns): "+(x.tps,x.fps,x.fns)).mkString("\n")}" +
      s"\n===========================================================\n"

  }

}