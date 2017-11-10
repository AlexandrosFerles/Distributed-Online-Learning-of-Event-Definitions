package oled

import akka.actor.ActorRef
import logic.{Clause, Theory}

import scala.collection.mutable.ListBuffer

/**
  * Created by ferles on 8/3/2017.
  */

case class Brothers( brothers: List[ActorRef])
case class Wait( brothers: List[ActorRef])
//case class NewRule( val c : List[Clause])
case class NewRule( clause : Clause )
//case class IndicesPlease( uuid : Int , actorName : String )
case class IndicesPlease(  uuid : Int , requester: ActorRef )
case class Result (theory: Theory , time: Double)
//case class RefinedRule( parentRule : Clause , refinedRule : Clause )
case class RefinedRule( parentRuleId : Int, refinedRule : Clause )
case class IndicesArrived( tps: Int , fps: Int, fns: Int, examples: Int, clauseID: Int )
case class ClauseArrived( clause : Clause, id: Int , actorName : String )
case class Package ( clauses : ListBuffer[ ( Int,Clause) ] )
case class Proceed ( t1: List[Int], t2: List[Clause] , t3: List[(Int,Clause)], t4: List[(String, Int, Int)])
//case class Response ( name: String, id: Int, clause: Clause)
case class Response ( clause: Clause)
case class ClausesWanted ( clausesWanted: List[(String, Int, Int)] )
case class Finished(theory: Theory)
case class GiveMeTheTheory(theory: Theory)
case class DropClause(id: Int)
class HelperClasses {

}