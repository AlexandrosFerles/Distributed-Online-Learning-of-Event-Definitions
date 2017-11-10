package global
//package oldWait

import akka.actor.{Actor, Props, PoisonPill}
import app.Globals
import com.typesafe.scalalogging.LazyLogging
import logic.Theory
import utils.DataUtils.{ResultsContainer, TrainingSet, DataAsIntervals}
import utils.{Database, Utils}


import scala.collection.mutable.ListBuffer

/**
  * Created by ferles on 27/4/2017.
  */
//class oldWaiter (trainingSets : List[ DataAsIntervals ],
class GlobalLocker (
                      trainingSets : List[ DataAsIntervals ],
                      DB: Database,
                      delta: Double,
                      breakTiesThreshold: Double,
                      postPruningThreshold: Double,
                      minSeenExmpls: Int,
                      repeatFor: Int,
                      chunkSize: Int,
                      withInertia: Boolean,
                      withPostPruning: Boolean,
                      onlinePruning: Boolean,
                      testingData: List[TrainingSet]=Nil,
                      HLE : String,
                      HAND_CRAFETD : String,
                      globals : Globals)
  extends Actor with LazyLogging {

  //val debugTrainingData = trainingData.take(4)
  //var remainingTasks =  trainingData.size //debugTrainingData.size //
  //var remainingTasks=2 //searching for both meeting and moving theory
  var remainingTasks = 0
  //searching for both meeting and moving theory
  //println(remainingTasks)
  val results = new ListBuffer[ResultsContainer]

  def g = (x: List[Double]) => {
    val m = Utils.mean(x)
    val d = Utils.deviation(x, m)
    (m, d)
  }

  def receive = {

    case "start" =>
      for (dataset <- testingData) {
        context.actorOf(Props(
          new GlobalDispatcher(DB, trainingSets ,delta, breakTiesThreshold, postPruningThreshold, minSeenExmpls,
            repeatFor, chunkSize, withInertia, withPostPruning, onlinePruning, dataset, HLE,HAND_CRAFETD, Theory(),globals = globals)
        ), name = s"Dispatcher") ! "start"
        remainingTasks += 1
      }

    case x: ResultsContainer =>
      results +=x

      remainingTasks -= 1
      sender ! PoisonPill

      logger.info(s"Remaining tasks: $remainingTasks")
      if (remainingTasks == 0) {

        logger.info(s"All done!")

        val (tps,fps,fns,precision,recall,fscore,theorySize,times) = {
          val z = results.foldLeft(List[Double](), List[Double](), List[Double](), List[Double](), List[Double](), List[Double](), List[Double](), List[Double]()) {
            (x, y) => (x._1:+y.tps, x._1:+y.fps, x._3:+y.fns, x._4:+y.precision, x._5:+y.recall, x._6:+y.fscore, x._7:+y.theorySize, x._8:+y.time)
          }
          (g(z._1),g(z._2),g(z._3),g(z._4),g(z._5),g(z._6),g(z._7),g(z._8))
        }
        val show =
          s"\ntps: ${tps._1} (+/- ${tps._2})" +
            s"\nfps: ${fps._1} (+/- ${fps._2})" +
            s"\nfns: ${fns._1} (+/- ${fns._2})" +
            s"\nprecision: ${precision._1} (+/- ${precision._2})" +
            s"\nrecall: ${recall._1} (+/- ${recall._2})" +
            s"\nf-score: ${fscore._1} (+/- ${fscore._2})" +
            s"\nsize: ${theorySize._1} (+/- ${theorySize._2})" +
            s"\ntime: ${times._1} (+/- ${times._2})"
        println(show)


        context.system.shutdown()
      }
  }

}

