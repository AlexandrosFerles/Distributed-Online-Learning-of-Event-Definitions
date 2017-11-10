package global

/**
  * Created by ferles on 27/4/2017.
  */

import logic.{Clause, Theory}

case class ProcessedBatchGlobal()
case class PingGlobal()
case class ProceedGlobal ( t1: List[Int], t2: List[Clause] )
case class Lock ( t1: List[Int], t2: List[Clause] , t3: List[ (Int , List[Clause] ) ], t4: List[(Int,Int)])
case class Again()
case class NotFound( original : Int, ruleId: Int)

case class ResultGlobal (theory: Theory ,oldTheory: List[Clause], time: Double)
case class RescoreGlobal(theory : Theory)

case class NewRuleGlobal( val c : List[Clause])
case class RefinedRuleGlobal( parentRule : Int, refinedRule : Clause )
case class EvaluateExpansion( id : Int , copies: List[Clause] )
case class NoEvaluationDone( id : Int)

case class IndicesPleaseGlobal( clauseId: Int )
case class ClausesWantedGlobal (newClauses : List[Clause] , original : Int)
//case class ClausesWantedGlobal ( wantedID : Int )
case class ResponseGlobal ( id: Int, clause: Clause)

case class RemoveRule (id : Int)


class GlobalClasses {

}