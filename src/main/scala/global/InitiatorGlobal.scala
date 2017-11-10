package global

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import app.Globals
import com.typesafe.scalalogging.LazyLogging
import logic.{Clause, Theory}
import oled.{Proceed, RefinedRule, Result}
import utils.DataUtils.{DataAsIntervals, TrainingSet}
import utils.{Database, Utils}

import scala.collection.mutable.{ListBuffer, Map}


/**
  * Created by ferles on 20/12/2016.
  */
class InitiatorGlobal (trainingSets : List[DataAsIntervals],
                       caviar: Database,
                       val delta: Double,
                       val breakTiesThreshold: Double,
                       val postPruningThreshold: Double,
                       val minSeenExmpls: Double,
                       val repeatFor: Int,
                       val chunkSize: Int,
                       withInertia: Boolean,
                       withPostPruning: Boolean,
                       onlinePruning: Boolean,
                       val reScoreData : TrainingSet,
                       val HLE: String,
                       kernelSet: Theory = Theory(),
                       globals: Globals
                      ) extends  Actor with LazyLogging {

  var pendingClauses = 0
  var pendingEvaluations = 0

  var learnerCnt=0
  val learners = ListBuffer[ActorRef]()

  var result = Theory()
  var time=0.0

  val mappings = ListBuffer[List[(Int,ActorRef)]]()
  val oldMappings = ListBuffer[List[(Int,ActorRef)]]()

  var clausesForRefinement = ListBuffer[(Int,String)]()

  val clausesToRemove =   Map[String, ListBuffer[Int] ]()
  var clausesToAdd =      Map[String, List[Clause] ]()
  val clausesForUpdate =  Map[String, ListBuffer[ (Int , List[Clause] ) ] ]()
  val clausesWanted =     Map[String, ListBuffer[ (Int , Int ) ] ]()

  var lock = false
  var finished = Map [String,Boolean]()

  val theoryOfLearner = Map[String, Theory]()
  var oldClauses = Map[String, List[Clause]]()

  var returnTo = scala.collection.mutable.Map[ Int, String]()
  var brotherClauses = scala.collection.mutable.Map[ Int , ListBuffer[Clause]]()
  var actorsResponded = scala.collection.mutable.Map[ Int , Int]()

  val idsRequested = ListBuffer[Int]()

  var initTheory = Theory ()
  var last = false

  val idsRemoved = ListBuffer[Int]()

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

  def receive={

    case "go" =>


      trainingSets.foreach(dataset =>{

        val learner = context.actorOf(Props(
          new GlobalLearner(caviar, delta, breakTiesThreshold, postPruningThreshold,
            minSeenExmpls, repeatFor, chunkSize, "initiated", withInertia = withInertia,
            withPostPruning = withPostPruning, onlinePruning = onlinePruning, dataset, reScoreData, HLE, kernelSet = kernelSet, globals = globals)
        ), name = s"initlearner$learnerCnt")
        learner ! "go"

        clausesToRemove(learner.path.name) = ListBuffer[Int]()
        clausesToAdd(learner.path.name) = List[Clause]()
        clausesForUpdate(learner.path.name) = ListBuffer[ (Int , List[Clause] ) ]()
        clausesWanted(learner.path.name) = ListBuffer[(Int , Int)]()
        finished(learner.path.name) = false

        theoryOfLearner(learner.path.name) = Theory()
        oldClauses(learner.path.name) = List[Clause]()

        learners += learner
        this.learnerCnt +=1
      })

    case r: RemoveRule =>
      var check = false
      idsRequested.foreach( id => if( id == r.id) check = true )
      if (!check){
        logger.info(s" Clause ${r.id} pruned!")
        this.learners.foreach( learner => clausesToRemove(learner.path.name) += r.id)
        idsRemoved+= r.id
      }

    case ProcessedBatchGlobal =>

      if ( pendingClauses == 0  ){
        val t1 = clausesToRemove(sender.path.name).toList
        val t2 = clausesToAdd(sender.path.name)

        sender ! ProceedGlobal(t1, t2)

        //clear all info
        clausesToRemove(sender.path.name).clear()
        clausesToAdd(sender.path.name) = List[Clause]()
      }

    case NewRuleGlobal(clauseList) =>

      this.learners.foreach(learner =>
        if (learner.path.name != sender.path.name) {
          val copies = clauseList.map( x => copyClause(x) )
          clausesToAdd(learner.path.name) = clausesToAdd(learner.path.name) ++ copies
        })


    case IndicesPleaseGlobal(id) =>

      var alreadyRequested = false
      idsRequested.foreach(uuid => if (uuid == id) alreadyRequested = true)
      idsRemoved.foreach(uuid => if (uuid == id) alreadyRequested = true)


      if (!alreadyRequested){
        idsRequested += id
        pendingClauses += 1
        lock = true
        returnTo(id) = sender.path.name
        brotherClauses(id) = ListBuffer[Clause]()
        actorsResponded(id) = 0

        this.learners.foreach( learner =>{
          if (learner.path.name != sender.path.name){
            val t1 = clausesToAdd(learner.path.name )
            learner ! ClausesWantedGlobal(t1, id )
            clausesToAdd(learner.path.name ) = List[Clause]()
          }
        })
      }

    case ResponseGlobal( id , rule) =>

      brotherClauses(id)+= rule
      actorsResponded( id ) += 1
      if (actorsResponded(id)==this.learners.length-1) {

        context.actorSelection(returnTo(id)) ! EvaluateExpansion( id , brotherClauses(id).toList)
      }
      var i =0

    case RefinedRuleGlobal( id , refinedRule) =>

      idsRequested -= id

      this.learners.foreach( learner =>
        if (learner.path.name != sender.path.name) {
          clausesToAdd(learner.path.name) = clausesToAdd(learner.path.name) ++ List(copyClause(refinedRule))
          clausesToRemove(learner.path.name) += id
        })

      pendingClauses -=1
      if (pendingClauses==0) {

        lock = false

        this.learners.foreach(learner => {

          if (!finished(learner.path.name)){

            val t1 = clausesToRemove(learner.path.name).toList
            val t2 = clausesToAdd(learner.path.name)

            learner ! ProceedGlobal(t1, t2)

            //clear all info
            clausesToRemove(learner.path.name).clear()
            clausesToAdd(learner.path.name) = List[Clause]()
          }
        })
      }


    /*
          var map = List[(Int,ActorRef)]()
          this.mappings.foreach( mapping => mapping.foreach(pair => if (pair._1 == id) map = mapping))

          map.foreach( pair => if (pair._1 != sender.path.name) clausesToRemove(pair._2.path.name) += pair._1)

          this.mappings-=map
          this.oldMappings+=map

          val mapping = ListBuffer[(Int,ActorRef)]( )
          this.learners.foreach(learner => {
            if (learner.path.name != sender.path.name) {
              val copy = Clause(head = refinedRule.head, body = refinedRule.body)
              copy.isCopy = true
              val refsCopy = refinedRule.refinements.map(x => Clause(x.head, x.body))
              // set them in copy:
              copy.refinements = refinedRule.refinements
              //copy.refinements = refsCopy
              clausesToAdd(learner.path.name) = clausesToAdd(learner.path.name) ++ List(copy)
              mapping += ((copy.uuid, learner))
              //lock(learner.path.name)-=1
            }
            else mapping += ((refinedRule.uuid, sender))
          })

          this.mappings += mapping.toList
          mapping.clear()

          //logger.info( s" Dropping down pending clauses to ${pendingClauses-1}")
    */



    case n: NoEvaluationDone =>
      idsRequested -= n.id
      pendingClauses -=1
      if (pendingClauses==0) {

        lock = false

        this.learners.foreach(learner => {

          if (!finished(learner.path.name)){

            val t1 = clausesToRemove(learner.path.name).toList
            val t2 = clausesToAdd(learner.path.name)

            learner ! ProceedGlobal(t1, t2)

            //clear all info
            clausesToRemove(learner.path.name).clear()
            clausesToAdd(learner.path.name) = List[Clause]()
          }
        })
      }

    case ResultGlobal(theory, oldies , tempTime) =>

      finished(sender.path.name) = true

      theoryOfLearner(sender.path.name) =
        if ( clausesToAdd(sender.path.name).length > 0) {
          val t1 = clausesToAdd(sender.path.name)
          clausesToAdd(sender.path.name) = List[Clause]()
          Theory(theory.clauses ++ t1)
        }
        else theory

      oldClauses(sender.path.name) = oldies
      this.time = Math.max( this.time,tempTime)


      this.learnerCnt -= 1
      if (this.learnerCnt ==1) last = true
      if (this.learnerCnt == 0) {
        initTheory = {
          val buffer = ListBuffer[Clause](theory.clauses.head)
          theory.clauses.tail.foreach( clause =>{
            var found = false
            buffer.foreach( rule => if (rule.tostring==clause.tostring) found = true)
            if (!found) buffer+=clause
          })
          Theory ( buffer.toList )
        }
        sender ! RescoreGlobal(initTheory)
      }

    case Result( theory , _) =>
      this.learners.foreach( _ ! PoisonPill)
      context.parent ! Result ( theory , this.time)
  }
}