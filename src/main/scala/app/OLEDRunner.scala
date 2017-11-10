package app


import java.util.concurrent.CountDownLatch

import com.typesafe.scalalogging.LazyLogging
import akka.actor.{ActorSystem, Props}
import oled.MasterActor
import global.GlobalLocker
import monolithic.Monolither
import oled.whole_caviar_data.oled.whole_caviar_data.MeetingDistributedIntervals
import oled.whole_caviar_data.MovingDistributedIntervals
import oled.whole_caviar_data.{MeetingTrainingData1, MeetingTrainingData10, MeetingTrainingData2, MeetingTrainingData3, MeetingTrainingData4, MeetingTrainingData5, MeetingTrainingData6, MeetingTrainingData7, MeetingTrainingData8, MeetingTrainingData9}
import oled.whole_caviar_data.{MovingTrainingData1, MovingTrainingData10, MovingTrainingData2, MovingTrainingData3, MovingTrainingData4, MovingTrainingData5, MovingTrainingData6, MovingTrainingData7, MovingTrainingData8, MovingTrainingData9}
import utils.DataUtils.Interval
import utils.Database

object OLEDRunner extends App with LazyLogging{

  override def main(args: Array[String])={

    val delta = 0.00001//args(0).toDouble//0.00001
    val specializationDepth = 1//args(3).toInt//1

    val breakTiesThreshold = 0.05
    val repeatFor = if ( args.length <7) {
      1
    }
    else {
      args(6).split("repeatFor=")(1).toInt
    }
    val chunkSize = 50  // learn from chunks instead of pairs (to speed things up)
    val onlinePruning = true
    val withPostPruning = false
    val withInertia = false

    Globals.glvalues("specializationDepth") = specializationDepth.toString

    val system = ActorSystem("HoeffdingLearningSystem")
    val latch = new CountDownLatch(1)

    val inputPath = args(0)
    val HLE = args(1)
    val numberOfCores = args(2).toInt
    val mode = args(3)
    val datasetNumber = args(4).toInt

    val minSeenExmpls = if (args.length < 6) {
      numberOfCores match{
        case 2 =>   1000
        case 4 =>   1000
        case 6 =>   1000
        case 8 =>   1000
        case 10 =>  1000
        case 12 =>  1000
      }
    }
    else {
      args(5).split("minSeen=")(1).toInt
    }


    val entryPath = s"$inputPath/datasets/Caviar/$HLE"
    val fromDB = "caviar"
    val globals = new Globals(entryPath,fromDB)
    val DB = new Database(globals.fromDB,"examples")
    val HAND_CRAFTED = globals.HAND_CRAFTED_RULES+s"$HLE-hand-crafted.lp"

    val postPruningThreshold = HLE match{
      case "meeting" => 0.85
      case "moving"  => 0.75
    }

    val testingSet = HLE match{
      case "meeting" =>
        datasetNumber match{
          case 1 => List(MeetingTrainingData1.meetTrainingSet1)
          case 2 => List(MeetingTrainingData2.meetTrainingSet2)
          case 3 => List(MeetingTrainingData3.meetTrainingSet3)
          case 4 => List(MeetingTrainingData4.meetTrainingSet4)
          case 5 => List(MeetingTrainingData5.meetTrainingSet5)
          case 6 => List(MeetingTrainingData6.meetTrainingSet6)
          case 7 => List(MeetingTrainingData7.meetTrainingSet7)
          case 8 => List(MeetingTrainingData8.meetTrainingSet8)
          case 9 => List(MeetingTrainingData9.meetTrainingSet9)
          case 10 => List(MeetingTrainingData10.meetTrainingSet10)
        }
      case "moving"  =>
        datasetNumber match{
          case 1 => List(MovingTrainingData1.moveTrainingSet1)
          case 2 => List(MovingTrainingData2.moveTrainingSet2)
          case 3 => List(MovingTrainingData3.moveTrainingSet3)
          case 4 => List(MovingTrainingData4.moveTrainingSet4)
          case 5 => List(MovingTrainingData5.moveTrainingSet5)
          case 6 => List(MovingTrainingData6.moveTrainingSet6)
          case 7 => List(MovingTrainingData7.moveTrainingSet7)
          case 8 => List(MovingTrainingData8.moveTrainingSet8)
          case 9 => List(MovingTrainingData9.moveTrainingSet9)
          case 10 => List(MovingTrainingData10.moveTrainingSet10)
        }
    }
/*
    val trainingSets = if (mode=="monolithic") {
      datasetNumber match {
        case 1 => List (MeetingTrainingData1.meetTrainingSet1)
        case 2 => List (MeetingTrainingData1.meetTrainingSet2)
        case 3 => List (MeetingTrainingData1.meetTrainingSet3)
        case 4 => List (MeetingTrainingData1.meetTrainingSet4)
        case 5 => List (MeetingTrainingData1.meetTrainingSet5)
        case 6 => List (MeetingTrainingData1.meetTrainingSet6)
        case 7 => List (MeetingTrainingData1.meetTrainingSet7)
        case 8 => List (MeetingTrainingData1.meetTrainingSet8)
        case 9 => List (MeetingTrainingData1.meetTrainingSet9)
        case 10 => List (MeetingTrainingData1.meetTrainingSet10)
      }
    }
    else HLE match{
      case "meeting" =>
        numberOfCores match{
          case 2 =>
            datasetNumber match {
              case 1 =>
                List (
                  MeetingTrainingData1.meetTrainingSet21,
                  MeetingTrainingData1.meetTrainingSet22)
              case 2 =>
                List (
                  MeetingTrainingData2.meetTrainingSet21,
                  MeetingTrainingData2.meetTrainingSet22)
              case 3 =>
                List (
                  MeetingTrainingData3.meetTrainingSet21,
                  MeetingTrainingData3.meetTrainingSet22)
              case 4 =>
                List (
                  MeetingTrainingData4.meetTrainingSet21,
                  MeetingTrainingData4.meetTrainingSet22)
              case 5 =>
                List (
                  MeetingTrainingData5.meetTrainingSet21,
                  MeetingTrainingData5.meetTrainingSet22)
              case 6 =>
                List (
                  MeetingTrainingData6.meetTrainingSet21,
                  MeetingTrainingData6.meetTrainingSet22)
              case 7 =>
                List (
                  MeetingTrainingData7.meetTrainingSet21,
                  MeetingTrainingData7.meetTrainingSet22)
              case 8 =>
                List (
                  MeetingTrainingData8.meetTrainingSet21,
                  MeetingTrainingData8.meetTrainingSet22)
              case 9 =>
                List (
                  MeetingTrainingData9.meetTrainingSet21,
                  MeetingTrainingData9.meetTrainingSet22)
              case 10 =>
                List (
                  MeetingTrainingData10.meetTrainingSet21,
                  MeetingTrainingData10.meetTrainingSet22)
            }
          case 4 =>
            datasetNumber match{
              case 1 =>
                List(
                  MeetingTrainingData1.meetTrainingSet41,
                  MeetingTrainingData1.meetTrainingSet42,
                  MeetingTrainingData1.meetTrainingSet43,
                  MeetingTrainingData1.meetTrainingSet44)
              case 2 =>
                List(
                  MeetingTrainingData2.meetTrainingSet41,
                  MeetingTrainingData2.meetTrainingSet42,
                  MeetingTrainingData2.meetTrainingSet43,
                  MeetingTrainingData2.meetTrainingSet44)
              case 3 =>
                List(
                  MeetingTrainingData3.meetTrainingSet41,
                  MeetingTrainingData3.meetTrainingSet42,
                  MeetingTrainingData3.meetTrainingSet43,
                  MeetingTrainingData3.meetTrainingSet44)
              case 4 =>
                List(
                  MeetingTrainingData4.meetTrainingSet41,
                  MeetingTrainingData4.meetTrainingSet42,
                  MeetingTrainingData4.meetTrainingSet43,
                  MeetingTrainingData4.meetTrainingSet44)
              case 5 =>
                List(
                  MeetingTrainingData5.meetTrainingSet41,
                  MeetingTrainingData5.meetTrainingSet42,
                  MeetingTrainingData5.meetTrainingSet43,
                  MeetingTrainingData5.meetTrainingSet44)
              case 6 =>
                List(
                  MeetingTrainingData6.meetTrainingSet41,
                  MeetingTrainingData6.meetTrainingSet42,
                  MeetingTrainingData6.meetTrainingSet43,
                  MeetingTrainingData6.meetTrainingSet44)
              case 7 =>
                List(
                  MeetingTrainingData7.meetTrainingSet41,
                  MeetingTrainingData7.meetTrainingSet42,
                  MeetingTrainingData7.meetTrainingSet43,
                  MeetingTrainingData7.meetTrainingSet44)
              case 8 =>
                List(
                  MeetingTrainingData8.meetTrainingSet41,
                  MeetingTrainingData8.meetTrainingSet42,
                  MeetingTrainingData8.meetTrainingSet43,
                  MeetingTrainingData8.meetTrainingSet44)
              case 9=>
                List(
                  MeetingTrainingData9.meetTrainingSet41,
                  MeetingTrainingData9.meetTrainingSet42,
                  MeetingTrainingData9.meetTrainingSet43,
                  MeetingTrainingData9.meetTrainingSet44)
              case 10 =>
                List(
                  MeetingTrainingData10.meetTrainingSet41,
                  MeetingTrainingData10.meetTrainingSet42,
                  MeetingTrainingData10.meetTrainingSet43,
                  MeetingTrainingData10.meetTrainingSet44)
            }

          case 6 =>
            datasetNumber match{
              case 1 =>
                List(
                  MeetingTrainingData1.meetTrainingSet61,
                  MeetingTrainingData1.meetTrainingSet62,
                  MeetingTrainingData1.meetTrainingSet63,
                  MeetingTrainingData1.meetTrainingSet64,
                  MeetingTrainingData1.meetTrainingSet65,
                  MeetingTrainingData1.meetTrainingSet66
                )
              case 2 =>
                List(
                  MeetingTrainingData2.meetTrainingSet61,
                  MeetingTrainingData2.meetTrainingSet62,
                  MeetingTrainingData2.meetTrainingSet63,
                  MeetingTrainingData2.meetTrainingSet64,
                  MeetingTrainingData2.meetTrainingSet65,
                  MeetingTrainingData2.meetTrainingSet66
                )
              case 3 =>
                List(
                  MeetingTrainingData3.meetTrainingSet61,
                  MeetingTrainingData3.meetTrainingSet62,
                  MeetingTrainingData3.meetTrainingSet63,
                  MeetingTrainingData3.meetTrainingSet64,
                  MeetingTrainingData3.meetTrainingSet65,
                  MeetingTrainingData3.meetTrainingSet66
                )
              case 4 =>
                List(
                  MeetingTrainingData4.meetTrainingSet61,
                  MeetingTrainingData4.meetTrainingSet62,
                  MeetingTrainingData4.meetTrainingSet63,
                  MeetingTrainingData4.meetTrainingSet64,
                  MeetingTrainingData4.meetTrainingSet65,
                  MeetingTrainingData4.meetTrainingSet66
                )
              case 5 =>
                List(
                  MeetingTrainingData5.meetTrainingSet61,
                  MeetingTrainingData5.meetTrainingSet62,
                  MeetingTrainingData5.meetTrainingSet63,
                  MeetingTrainingData5.meetTrainingSet64,
                  MeetingTrainingData5.meetTrainingSet65,
                  MeetingTrainingData5.meetTrainingSet66
                )
              case 6 =>
                List(
                  MeetingTrainingData6.meetTrainingSet61,
                  MeetingTrainingData6.meetTrainingSet62,
                  MeetingTrainingData6.meetTrainingSet63,
                  MeetingTrainingData6.meetTrainingSet64,
                  MeetingTrainingData6.meetTrainingSet65,
                  MeetingTrainingData6.meetTrainingSet66
                )
              case 7 =>
                List(
                  MeetingTrainingData7.meetTrainingSet61,
                  MeetingTrainingData7.meetTrainingSet62,
                  MeetingTrainingData7.meetTrainingSet63,
                  MeetingTrainingData7.meetTrainingSet64,
                  MeetingTrainingData7.meetTrainingSet65,
                  MeetingTrainingData7.meetTrainingSet66
                )
              case 8 =>
                List(
                  MeetingTrainingData8.meetTrainingSet61,
                  MeetingTrainingData8.meetTrainingSet62,
                  MeetingTrainingData8.meetTrainingSet63,
                  MeetingTrainingData8.meetTrainingSet64,
                  MeetingTrainingData8.meetTrainingSet65,
                  MeetingTrainingData8.meetTrainingSet66
                )
              case 9 =>
                List(
                  MeetingTrainingData9.meetTrainingSet61,
                  MeetingTrainingData9.meetTrainingSet62,
                  MeetingTrainingData9.meetTrainingSet63,
                  MeetingTrainingData9.meetTrainingSet64,
                  MeetingTrainingData9.meetTrainingSet65,
                  MeetingTrainingData9.meetTrainingSet66
                )
              case 10 =>
                List(
                  MeetingTrainingData10.meetTrainingSet61,
                  MeetingTrainingData10.meetTrainingSet62,
                  MeetingTrainingData10.meetTrainingSet63,
                  MeetingTrainingData10.meetTrainingSet64,
                  MeetingTrainingData10.meetTrainingSet65,
                  MeetingTrainingData10.meetTrainingSet66
                )
            }

          case 8 =>
            datasetNumber match{
              case 1 =>
                List(
                  MeetingTrainingData1.meetTrainingSet81,
                  MeetingTrainingData1.meetTrainingSet82,
                  MeetingTrainingData1.meetTrainingSet83,
                  MeetingTrainingData1.meetTrainingSet84,
                  MeetingTrainingData1.meetTrainingSet85,
                  MeetingTrainingData1.meetTrainingSet86,
                  MeetingTrainingData1.meetTrainingSet87,
                  MeetingTrainingData1.meetTrainingSet88)
              case 2 =>
                List(
                  MeetingTrainingData2.meetTrainingSet81,
                  MeetingTrainingData2.meetTrainingSet82,
                  MeetingTrainingData2.meetTrainingSet83,
                  MeetingTrainingData2.meetTrainingSet84,
                  MeetingTrainingData2.meetTrainingSet85,
                  MeetingTrainingData2.meetTrainingSet86,
                  MeetingTrainingData2.meetTrainingSet87,
                  MeetingTrainingData2.meetTrainingSet88)
              case 3 =>
                List(
                  MeetingTrainingData3.meetTrainingSet81,
                  MeetingTrainingData3.meetTrainingSet82,
                  MeetingTrainingData3.meetTrainingSet83,
                  MeetingTrainingData3.meetTrainingSet84,
                  MeetingTrainingData3.meetTrainingSet85,
                  MeetingTrainingData3.meetTrainingSet86,
                  MeetingTrainingData3.meetTrainingSet87,
                  MeetingTrainingData3.meetTrainingSet88)
              case 4 =>
                List(
                  MeetingTrainingData4.meetTrainingSet81,
                  MeetingTrainingData4.meetTrainingSet82,
                  MeetingTrainingData4.meetTrainingSet83,
                  MeetingTrainingData4.meetTrainingSet84,
                  MeetingTrainingData4.meetTrainingSet85,
                  MeetingTrainingData4.meetTrainingSet86,
                  MeetingTrainingData4.meetTrainingSet87,
                  MeetingTrainingData4.meetTrainingSet88)
              case 5 =>
                List(
                  MeetingTrainingData5.meetTrainingSet81,
                  MeetingTrainingData5.meetTrainingSet82,
                  MeetingTrainingData5.meetTrainingSet83,
                  MeetingTrainingData5.meetTrainingSet84,
                  MeetingTrainingData5.meetTrainingSet85,
                  MeetingTrainingData5.meetTrainingSet86,
                  MeetingTrainingData5.meetTrainingSet87,
                  MeetingTrainingData5.meetTrainingSet88)
              case 6 =>
                List(
                  MeetingTrainingData6.meetTrainingSet81,
                  MeetingTrainingData6.meetTrainingSet82,
                  MeetingTrainingData6.meetTrainingSet83,
                  MeetingTrainingData6.meetTrainingSet84,
                  MeetingTrainingData6.meetTrainingSet85,
                  MeetingTrainingData6.meetTrainingSet86,
                  MeetingTrainingData6.meetTrainingSet87,
                  MeetingTrainingData6.meetTrainingSet88)
              case 7 =>
                List(
                  MeetingTrainingData7.meetTrainingSet81,
                  MeetingTrainingData7.meetTrainingSet82,
                  MeetingTrainingData7.meetTrainingSet83,
                  MeetingTrainingData7.meetTrainingSet84,
                  MeetingTrainingData7.meetTrainingSet85,
                  MeetingTrainingData7.meetTrainingSet86,
                  MeetingTrainingData7.meetTrainingSet87,
                  MeetingTrainingData7.meetTrainingSet88)
              case 8 =>
                List(
                  MeetingTrainingData8.meetTrainingSet81,
                  MeetingTrainingData8.meetTrainingSet82,
                  MeetingTrainingData8.meetTrainingSet83,
                  MeetingTrainingData8.meetTrainingSet84,
                  MeetingTrainingData8.meetTrainingSet85,
                  MeetingTrainingData8.meetTrainingSet86,
                  MeetingTrainingData8.meetTrainingSet87,
                  MeetingTrainingData8.meetTrainingSet88)
              case 9 =>
                List(
                  MeetingTrainingData9.meetTrainingSet81,
                  MeetingTrainingData9.meetTrainingSet82,
                  MeetingTrainingData9.meetTrainingSet83,
                  MeetingTrainingData9.meetTrainingSet84,
                  MeetingTrainingData9.meetTrainingSet85,
                  MeetingTrainingData9.meetTrainingSet86,
                  MeetingTrainingData9.meetTrainingSet87,
                  MeetingTrainingData9.meetTrainingSet88)
              case 10 =>
                List(
                  MeetingTrainingData10.meetTrainingSet81,
                  MeetingTrainingData10.meetTrainingSet82,
                  MeetingTrainingData10.meetTrainingSet83,
                  MeetingTrainingData10.meetTrainingSet84,
                  MeetingTrainingData10.meetTrainingSet85,
                  MeetingTrainingData10.meetTrainingSet86,
                  MeetingTrainingData10.meetTrainingSet87,
                  MeetingTrainingData10.meetTrainingSet88)
            }

          case 10 =>
            datasetNumber match{
              case 1 =>
                List(
                  MeetingTrainingData1.meetTrainingSet1001,
                  MeetingTrainingData1.meetTrainingSet1002,
                  MeetingTrainingData1.meetTrainingSet1003,
                  MeetingTrainingData1.meetTrainingSet1004,
                  MeetingTrainingData1.meetTrainingSet1005,
                  MeetingTrainingData1.meetTrainingSet1006,
                  MeetingTrainingData1.meetTrainingSet1007,
                  MeetingTrainingData1.meetTrainingSet1008,
                  MeetingTrainingData1.meetTrainingSet1009,
                  MeetingTrainingData1.meetTrainingSet1010)
              case 2 =>
                List(
                  MeetingTrainingData2.meetTrainingSet1001,
                  MeetingTrainingData2.meetTrainingSet1002,
                  MeetingTrainingData2.meetTrainingSet1003,
                  MeetingTrainingData2.meetTrainingSet1004,
                  MeetingTrainingData2.meetTrainingSet1005,
                  MeetingTrainingData2.meetTrainingSet1006,
                  MeetingTrainingData2.meetTrainingSet1007,
                  MeetingTrainingData2.meetTrainingSet1008,
                  MeetingTrainingData2.meetTrainingSet1009,
                  MeetingTrainingData2.meetTrainingSet1010)
              case 3 =>
                List(
                  MeetingTrainingData3.meetTrainingSet1001,
                  MeetingTrainingData3.meetTrainingSet1002,
                  MeetingTrainingData3.meetTrainingSet1003,
                  MeetingTrainingData3.meetTrainingSet1004,
                  MeetingTrainingData3.meetTrainingSet1005,
                  MeetingTrainingData3.meetTrainingSet1006,
                  MeetingTrainingData3.meetTrainingSet1007,
                  MeetingTrainingData3.meetTrainingSet1008,
                  MeetingTrainingData3.meetTrainingSet1009,
                  MeetingTrainingData3.meetTrainingSet1010)
              case 4 =>
                List(
                  MeetingTrainingData4.meetTrainingSet1001,
                  MeetingTrainingData4.meetTrainingSet1002,
                  MeetingTrainingData4.meetTrainingSet1003,
                  MeetingTrainingData4.meetTrainingSet1004,
                  MeetingTrainingData4.meetTrainingSet1005,
                  MeetingTrainingData4.meetTrainingSet1006,
                  MeetingTrainingData4.meetTrainingSet1007,
                  MeetingTrainingData4.meetTrainingSet1008,
                  MeetingTrainingData4.meetTrainingSet1009,
                  MeetingTrainingData4.meetTrainingSet1010)
              case 5 =>
                List(
                  MeetingTrainingData5.meetTrainingSet1001,
                  MeetingTrainingData5.meetTrainingSet1002,
                  MeetingTrainingData5.meetTrainingSet1003,
                  MeetingTrainingData5.meetTrainingSet1004,
                  MeetingTrainingData5.meetTrainingSet1005,
                  MeetingTrainingData5.meetTrainingSet1006,
                  MeetingTrainingData5.meetTrainingSet1007,
                  MeetingTrainingData5.meetTrainingSet1008,
                  MeetingTrainingData5.meetTrainingSet1009,
                  MeetingTrainingData5.meetTrainingSet1010)
              case 6 =>
                List(
                  MeetingTrainingData6.meetTrainingSet1001,
                  MeetingTrainingData6.meetTrainingSet1002,
                  MeetingTrainingData6.meetTrainingSet1003,
                  MeetingTrainingData6.meetTrainingSet1004,
                  MeetingTrainingData6.meetTrainingSet1005,
                  MeetingTrainingData6.meetTrainingSet1006,
                  MeetingTrainingData6.meetTrainingSet1007,
                  MeetingTrainingData6.meetTrainingSet1008,
                  MeetingTrainingData6.meetTrainingSet1009,
                  MeetingTrainingData6.meetTrainingSet1010)
              case 7 =>
                List(
                  MeetingTrainingData7.meetTrainingSet1001,
                  MeetingTrainingData7.meetTrainingSet1002,
                  MeetingTrainingData7.meetTrainingSet1003,
                  MeetingTrainingData7.meetTrainingSet1004,
                  MeetingTrainingData7.meetTrainingSet1005,
                  MeetingTrainingData7.meetTrainingSet1006,
                  MeetingTrainingData7.meetTrainingSet1007,
                  MeetingTrainingData7.meetTrainingSet1008,
                  MeetingTrainingData7.meetTrainingSet1009,
                  MeetingTrainingData7.meetTrainingSet1010)
              case 8 =>
                List(
                  MeetingTrainingData8.meetTrainingSet1001,
                  MeetingTrainingData8.meetTrainingSet1002,
                  MeetingTrainingData8.meetTrainingSet1003,
                  MeetingTrainingData8.meetTrainingSet1004,
                  MeetingTrainingData8.meetTrainingSet1005,
                  MeetingTrainingData8.meetTrainingSet1006,
                  MeetingTrainingData8.meetTrainingSet1007,
                  MeetingTrainingData8.meetTrainingSet1008,
                  MeetingTrainingData8.meetTrainingSet1009,
                  MeetingTrainingData8.meetTrainingSet1010)
              case 9 =>
                List(
                  MeetingTrainingData9.meetTrainingSet1001,
                  MeetingTrainingData9.meetTrainingSet1002,
                  MeetingTrainingData9.meetTrainingSet1003,
                  MeetingTrainingData9.meetTrainingSet1004,
                  MeetingTrainingData9.meetTrainingSet1005,
                  MeetingTrainingData9.meetTrainingSet1006,
                  MeetingTrainingData9.meetTrainingSet1007,
                  MeetingTrainingData9.meetTrainingSet1008,
                  MeetingTrainingData9.meetTrainingSet1009,
                  MeetingTrainingData9.meetTrainingSet1010)
              case 10 =>
                List(
                  MeetingTrainingData10.meetTrainingSet1001,
                  MeetingTrainingData10.meetTrainingSet1002,
                  MeetingTrainingData10.meetTrainingSet1003,
                  MeetingTrainingData10.meetTrainingSet1004,
                  MeetingTrainingData10.meetTrainingSet1005,
                  MeetingTrainingData10.meetTrainingSet1006,
                  MeetingTrainingData10.meetTrainingSet1007,
                  MeetingTrainingData10.meetTrainingSet1008,
                  MeetingTrainingData10.meetTrainingSet1009,
                  MeetingTrainingData10.meetTrainingSet1010)
            }
          case 12 =>
            datasetNumber match{
              case 1 =>
                List(
                  MeetingTrainingData1.meetTrainingSet1201,
                  MeetingTrainingData1.meetTrainingSet1202,
                  MeetingTrainingData1.meetTrainingSet1203,
                  MeetingTrainingData1.meetTrainingSet1204,
                  MeetingTrainingData1.meetTrainingSet1205,
                  MeetingTrainingData1.meetTrainingSet1206,
                  MeetingTrainingData1.meetTrainingSet1207,
                  MeetingTrainingData1.meetTrainingSet1208,
                  MeetingTrainingData1.meetTrainingSet1209,
                  MeetingTrainingData1.meetTrainingSet1210,
                  MeetingTrainingData1.meetTrainingSet1211,
                  MeetingTrainingData1.meetTrainingSet1212)
              case 2 =>
                List(
                  MeetingTrainingData2.meetTrainingSet1201,
                  MeetingTrainingData2.meetTrainingSet1202,
                  MeetingTrainingData2.meetTrainingSet1203,
                  MeetingTrainingData2.meetTrainingSet1204,
                  MeetingTrainingData2.meetTrainingSet1205,
                  MeetingTrainingData2.meetTrainingSet1206,
                  MeetingTrainingData2.meetTrainingSet1207,
                  MeetingTrainingData2.meetTrainingSet1208,
                  MeetingTrainingData2.meetTrainingSet1209,
                  MeetingTrainingData2.meetTrainingSet1210,
                  MeetingTrainingData2.meetTrainingSet1211,
                  MeetingTrainingData2.meetTrainingSet1212)
              case 3 =>
                List(
                  MeetingTrainingData3.meetTrainingSet1201,
                  MeetingTrainingData3.meetTrainingSet1202,
                  MeetingTrainingData3.meetTrainingSet1203,
                  MeetingTrainingData3.meetTrainingSet1204,
                  MeetingTrainingData3.meetTrainingSet1205,
                  MeetingTrainingData3.meetTrainingSet1206,
                  MeetingTrainingData3.meetTrainingSet1207,
                  MeetingTrainingData3.meetTrainingSet1208,
                  MeetingTrainingData3.meetTrainingSet1209,
                  MeetingTrainingData3.meetTrainingSet1210,
                  MeetingTrainingData3.meetTrainingSet1211,
                  MeetingTrainingData3.meetTrainingSet1212)
              case 4 =>
                List(
                  MeetingTrainingData4.meetTrainingSet1201,
                  MeetingTrainingData4.meetTrainingSet1202,
                  MeetingTrainingData4.meetTrainingSet1203,
                  MeetingTrainingData4.meetTrainingSet1204,
                  MeetingTrainingData4.meetTrainingSet1205,
                  MeetingTrainingData4.meetTrainingSet1206,
                  MeetingTrainingData4.meetTrainingSet1207,
                  MeetingTrainingData4.meetTrainingSet1208,
                  MeetingTrainingData4.meetTrainingSet1209,
                  MeetingTrainingData4.meetTrainingSet1210,
                  MeetingTrainingData4.meetTrainingSet1211,
                  MeetingTrainingData4.meetTrainingSet1212)
              case 5 =>
                List(
                  MeetingTrainingData5.meetTrainingSet1201,
                  MeetingTrainingData5.meetTrainingSet1202,
                  MeetingTrainingData5.meetTrainingSet1203,
                  MeetingTrainingData5.meetTrainingSet1204,
                  MeetingTrainingData5.meetTrainingSet1205,
                  MeetingTrainingData5.meetTrainingSet1206,
                  MeetingTrainingData5.meetTrainingSet1207,
                  MeetingTrainingData5.meetTrainingSet1208,
                  MeetingTrainingData5.meetTrainingSet1209,
                  MeetingTrainingData5.meetTrainingSet1210,
                  MeetingTrainingData5.meetTrainingSet1211,
                  MeetingTrainingData5.meetTrainingSet1212)
              case 6 =>
                List(
                  MeetingTrainingData6.meetTrainingSet1201,
                  MeetingTrainingData6.meetTrainingSet1202,
                  MeetingTrainingData6.meetTrainingSet1203,
                  MeetingTrainingData6.meetTrainingSet1204,
                  MeetingTrainingData6.meetTrainingSet1205,
                  MeetingTrainingData6.meetTrainingSet1206,
                  MeetingTrainingData6.meetTrainingSet1207,
                  MeetingTrainingData6.meetTrainingSet1208,
                  MeetingTrainingData6.meetTrainingSet1209,
                  MeetingTrainingData6.meetTrainingSet1210,
                  MeetingTrainingData6.meetTrainingSet1211,
                  MeetingTrainingData6.meetTrainingSet1212)
              case 7 =>
                List(
                  MeetingTrainingData7.meetTrainingSet1201,
                  MeetingTrainingData7.meetTrainingSet1202,
                  MeetingTrainingData7.meetTrainingSet1203,
                  MeetingTrainingData7.meetTrainingSet1204,
                  MeetingTrainingData7.meetTrainingSet1205,
                  MeetingTrainingData7.meetTrainingSet1206,
                  MeetingTrainingData7.meetTrainingSet1207,
                  MeetingTrainingData7.meetTrainingSet1208,
                  MeetingTrainingData7.meetTrainingSet1209,
                  MeetingTrainingData7.meetTrainingSet1210,
                  MeetingTrainingData7.meetTrainingSet1211,
                  MeetingTrainingData7.meetTrainingSet1212)
              case 8 =>
                List(
                  MeetingTrainingData8.meetTrainingSet1201,
                  MeetingTrainingData8.meetTrainingSet1202,
                  MeetingTrainingData8.meetTrainingSet1203,
                  MeetingTrainingData8.meetTrainingSet1204,
                  MeetingTrainingData8.meetTrainingSet1205,
                  MeetingTrainingData8.meetTrainingSet1206,
                  MeetingTrainingData8.meetTrainingSet1207,
                  MeetingTrainingData8.meetTrainingSet1208,
                  MeetingTrainingData8.meetTrainingSet1209,
                  MeetingTrainingData8.meetTrainingSet1210,
                  MeetingTrainingData8.meetTrainingSet1211,
                  MeetingTrainingData8.meetTrainingSet1212)
              case 9 =>
                List(
                  MeetingTrainingData9.meetTrainingSet1201,
                  MeetingTrainingData9.meetTrainingSet1202,
                  MeetingTrainingData9.meetTrainingSet1203,
                  MeetingTrainingData9.meetTrainingSet1204,
                  MeetingTrainingData9.meetTrainingSet1205,
                  MeetingTrainingData9.meetTrainingSet1206,
                  MeetingTrainingData9.meetTrainingSet1207,
                  MeetingTrainingData9.meetTrainingSet1208,
                  MeetingTrainingData9.meetTrainingSet1209,
                  MeetingTrainingData9.meetTrainingSet1210,
                  MeetingTrainingData9.meetTrainingSet1211,
                  MeetingTrainingData9.meetTrainingSet1212)
              case 10 =>
                List(
                  MeetingTrainingData10.meetTrainingSet1201,
                  MeetingTrainingData10.meetTrainingSet1202,
                  MeetingTrainingData10.meetTrainingSet1203,
                  MeetingTrainingData10.meetTrainingSet1204,
                  MeetingTrainingData10.meetTrainingSet1205,
                  MeetingTrainingData10.meetTrainingSet1206,
                  MeetingTrainingData10.meetTrainingSet1207,
                  MeetingTrainingData10.meetTrainingSet1208,
                  MeetingTrainingData10.meetTrainingSet1209,
                  MeetingTrainingData10.meetTrainingSet1210,
                  MeetingTrainingData10.meetTrainingSet1211,
                  MeetingTrainingData10.meetTrainingSet1212)
            }

        }
      case "moving" =>
        numberOfCores match{
          case 2 =>
            datasetNumber match {
              case 1 =>
                List (
                  MovingTrainingData1.moveTrainingSet21,
                  MovingTrainingData1.moveTrainingSet22)
              case 2 =>
                List (
                  MovingTrainingData2.moveTrainingSet21,
                  MovingTrainingData2.moveTrainingSet22)
              case 3 =>
                List (
                  MovingTrainingData3.moveTrainingSet21,
                  MovingTrainingData3.moveTrainingSet22)
              case 4 =>
                List (
                  MovingTrainingData4.moveTrainingSet21,
                  MovingTrainingData4.moveTrainingSet22)
              case 5 =>
                List (
                  MovingTrainingData5.moveTrainingSet21,
                  MovingTrainingData5.moveTrainingSet22)
              case 6 =>
                List (
                  MovingTrainingData6.moveTrainingSet21,
                  MovingTrainingData6.moveTrainingSet22)
              case 7 =>
                List (
                  MovingTrainingData7.moveTrainingSet21,
                  MovingTrainingData7.moveTrainingSet22)
              case 8 =>
                List (
                  MovingTrainingData8.moveTrainingSet21,
                  MovingTrainingData8.moveTrainingSet22)
              case 9 =>
                List (
                  MovingTrainingData9.moveTrainingSet21,
                  MovingTrainingData9.moveTrainingSet22)
              case 10 =>
                List (
                  MovingTrainingData10.moveTrainingSet21,
                  MovingTrainingData10.moveTrainingSet22)
            }
          case 4 =>
            datasetNumber match{
              case 1 =>
                List(
                  MovingTrainingData1.moveTrainingSet41,
                  MovingTrainingData1.moveTrainingSet42,
                  MovingTrainingData1.moveTrainingSet43,
                  MovingTrainingData1.moveTrainingSet44)
              case 2 =>
                List(
                  MovingTrainingData2.moveTrainingSet41,
                  MovingTrainingData2.moveTrainingSet42,
                  MovingTrainingData2.moveTrainingSet43,
                  MovingTrainingData2.moveTrainingSet44)
              case 3 =>
                List(
                  MovingTrainingData3.moveTrainingSet41,
                  MovingTrainingData3.moveTrainingSet42,
                  MovingTrainingData3.moveTrainingSet43,
                  MovingTrainingData3.moveTrainingSet44)
              case 4 =>
                List(
                  MovingTrainingData4.moveTrainingSet41,
                  MovingTrainingData4.moveTrainingSet42,
                  MovingTrainingData4.moveTrainingSet43,
                  MovingTrainingData4.moveTrainingSet44)
              case 5 =>
                List(
                  MovingTrainingData5.moveTrainingSet41,
                  MovingTrainingData5.moveTrainingSet42,
                  MovingTrainingData5.moveTrainingSet43,
                  MovingTrainingData5.moveTrainingSet44)
              case 6 =>
                List(
                  MovingTrainingData6.moveTrainingSet41,
                  MovingTrainingData6.moveTrainingSet42,
                  MovingTrainingData6.moveTrainingSet43,
                  MovingTrainingData6.moveTrainingSet44)
              case 7 =>
                List(
                  MovingTrainingData7.moveTrainingSet41,
                  MovingTrainingData7.moveTrainingSet42,
                  MovingTrainingData7.moveTrainingSet43,
                  MovingTrainingData7.moveTrainingSet44)
              case 8 =>
                List(
                  MovingTrainingData8.moveTrainingSet41,
                  MovingTrainingData8.moveTrainingSet42,
                  MovingTrainingData8.moveTrainingSet43,
                  MovingTrainingData8.moveTrainingSet44)
              case 9=>
                List(
                  MovingTrainingData9.moveTrainingSet41,
                  MovingTrainingData9.moveTrainingSet42,
                  MovingTrainingData9.moveTrainingSet43,
                  MovingTrainingData9.moveTrainingSet44)
              case 10 =>
                List(
                  MovingTrainingData10.moveTrainingSet41,
                  MovingTrainingData10.moveTrainingSet42,
                  MovingTrainingData10.moveTrainingSet43,
                  MovingTrainingData10.moveTrainingSet44)
            }

          case 6 =>
            datasetNumber match{
              case 1 =>
                List(
                  MovingTrainingData1.moveTrainingSet61,
                  MovingTrainingData1.moveTrainingSet62,
                  MovingTrainingData1.moveTrainingSet63,
                  MovingTrainingData1.moveTrainingSet64,
                  MovingTrainingData1.moveTrainingSet65,
                  MovingTrainingData1.moveTrainingSet66
                )
              case 2 =>
                List(
                  MovingTrainingData2.moveTrainingSet61,
                  MovingTrainingData2.moveTrainingSet62,
                  MovingTrainingData2.moveTrainingSet63,
                  MovingTrainingData2.moveTrainingSet64,
                  MovingTrainingData2.moveTrainingSet65,
                  MovingTrainingData2.moveTrainingSet66
                )
              case 3 =>
                List(
                  MovingTrainingData3.moveTrainingSet61,
                  MovingTrainingData3.moveTrainingSet62,
                  MovingTrainingData3.moveTrainingSet63,
                  MovingTrainingData3.moveTrainingSet64,
                  MovingTrainingData3.moveTrainingSet65,
                  MovingTrainingData3.moveTrainingSet66
                )
              case 4 =>
                List(
                  MovingTrainingData4.moveTrainingSet61,
                  MovingTrainingData4.moveTrainingSet62,
                  MovingTrainingData4.moveTrainingSet63,
                  MovingTrainingData4.moveTrainingSet64,
                  MovingTrainingData4.moveTrainingSet65,
                  MovingTrainingData4.moveTrainingSet66
                )
              case 5 =>
                List(
                  MovingTrainingData5.moveTrainingSet61,
                  MovingTrainingData5.moveTrainingSet62,
                  MovingTrainingData5.moveTrainingSet63,
                  MovingTrainingData5.moveTrainingSet64,
                  MovingTrainingData5.moveTrainingSet65,
                  MovingTrainingData5.moveTrainingSet66
                )
              case 6 =>
                List(
                  MovingTrainingData6.moveTrainingSet61,
                  MovingTrainingData6.moveTrainingSet62,
                  MovingTrainingData6.moveTrainingSet63,
                  MovingTrainingData6.moveTrainingSet64,
                  MovingTrainingData6.moveTrainingSet65,
                  MovingTrainingData6.moveTrainingSet66
                )
              case 7 =>
                List(
                  MovingTrainingData7.moveTrainingSet61,
                  MovingTrainingData7.moveTrainingSet62,
                  MovingTrainingData7.moveTrainingSet63,
                  MovingTrainingData7.moveTrainingSet64,
                  MovingTrainingData7.moveTrainingSet65,
                  MovingTrainingData7.moveTrainingSet66
                )
              case 8 =>
                List(
                  MovingTrainingData8.moveTrainingSet61,
                  MovingTrainingData8.moveTrainingSet62,
                  MovingTrainingData8.moveTrainingSet63,
                  MovingTrainingData8.moveTrainingSet64,
                  MovingTrainingData8.moveTrainingSet65,
                  MovingTrainingData8.moveTrainingSet66
                )
              case 9 =>
                List(
                  MovingTrainingData9.moveTrainingSet61,
                  MovingTrainingData9.moveTrainingSet62,
                  MovingTrainingData9.moveTrainingSet63,
                  MovingTrainingData9.moveTrainingSet64,
                  MovingTrainingData9.moveTrainingSet65,
                  MovingTrainingData9.moveTrainingSet66
                )
              case 10 =>
                List(
                  MovingTrainingData10.moveTrainingSet61,
                  MovingTrainingData10.moveTrainingSet62,
                  MovingTrainingData10.moveTrainingSet63,
                  MovingTrainingData10.moveTrainingSet64,
                  MovingTrainingData10.moveTrainingSet65,
                  MovingTrainingData10.moveTrainingSet66
                )
            }

          case 8 =>
            datasetNumber match{
              case 1 =>
                List(
                  MovingTrainingData1.moveTrainingSet81,
                  MovingTrainingData1.moveTrainingSet82,
                  MovingTrainingData1.moveTrainingSet83,
                  MovingTrainingData1.moveTrainingSet84,
                  MovingTrainingData1.moveTrainingSet85,
                  MovingTrainingData1.moveTrainingSet86,
                  MovingTrainingData1.moveTrainingSet87,
                  MovingTrainingData1.moveTrainingSet88)
              case 2 =>
                List(
                  MovingTrainingData2.moveTrainingSet81,
                  MovingTrainingData2.moveTrainingSet82,
                  MovingTrainingData2.moveTrainingSet83,
                  MovingTrainingData2.moveTrainingSet84,
                  MovingTrainingData2.moveTrainingSet85,
                  MovingTrainingData2.moveTrainingSet86,
                  MovingTrainingData2.moveTrainingSet87,
                  MovingTrainingData2.moveTrainingSet88)
              case 3 =>
                List(
                  MovingTrainingData3.moveTrainingSet81,
                  MovingTrainingData3.moveTrainingSet82,
                  MovingTrainingData3.moveTrainingSet83,
                  MovingTrainingData3.moveTrainingSet84,
                  MovingTrainingData3.moveTrainingSet85,
                  MovingTrainingData3.moveTrainingSet86,
                  MovingTrainingData3.moveTrainingSet87,
                  MovingTrainingData3.moveTrainingSet88)
              case 4 =>
                List(
                  MovingTrainingData4.moveTrainingSet81,
                  MovingTrainingData4.moveTrainingSet82,
                  MovingTrainingData4.moveTrainingSet83,
                  MovingTrainingData4.moveTrainingSet84,
                  MovingTrainingData4.moveTrainingSet85,
                  MovingTrainingData4.moveTrainingSet86,
                  MovingTrainingData4.moveTrainingSet87,
                  MovingTrainingData4.moveTrainingSet88)
              case 5 =>
                List(
                  MovingTrainingData5.moveTrainingSet81,
                  MovingTrainingData5.moveTrainingSet82,
                  MovingTrainingData5.moveTrainingSet83,
                  MovingTrainingData5.moveTrainingSet84,
                  MovingTrainingData5.moveTrainingSet85,
                  MovingTrainingData5.moveTrainingSet86,
                  MovingTrainingData5.moveTrainingSet87,
                  MovingTrainingData5.moveTrainingSet88)
              case 6 =>
                List(
                  MovingTrainingData6.moveTrainingSet81,
                  MovingTrainingData6.moveTrainingSet82,
                  MovingTrainingData6.moveTrainingSet83,
                  MovingTrainingData6.moveTrainingSet84,
                  MovingTrainingData6.moveTrainingSet85,
                  MovingTrainingData6.moveTrainingSet86,
                  MovingTrainingData6.moveTrainingSet87,
                  MovingTrainingData6.moveTrainingSet88)
              case 7 =>
                List(
                  MovingTrainingData7.moveTrainingSet81,
                  MovingTrainingData7.moveTrainingSet82,
                  MovingTrainingData7.moveTrainingSet83,
                  MovingTrainingData7.moveTrainingSet84,
                  MovingTrainingData7.moveTrainingSet85,
                  MovingTrainingData7.moveTrainingSet86,
                  MovingTrainingData7.moveTrainingSet87,
                  MovingTrainingData7.moveTrainingSet88)
              case 8 =>
                List(
                  MovingTrainingData8.moveTrainingSet81,
                  MovingTrainingData8.moveTrainingSet82,
                  MovingTrainingData8.moveTrainingSet83,
                  MovingTrainingData8.moveTrainingSet84,
                  MovingTrainingData8.moveTrainingSet85,
                  MovingTrainingData8.moveTrainingSet86,
                  MovingTrainingData8.moveTrainingSet87,
                  MovingTrainingData8.moveTrainingSet88)
              case 9 =>
                List(
                  MovingTrainingData9.moveTrainingSet81,
                  MovingTrainingData9.moveTrainingSet82,
                  MovingTrainingData9.moveTrainingSet83,
                  MovingTrainingData9.moveTrainingSet84,
                  MovingTrainingData9.moveTrainingSet85,
                  MovingTrainingData9.moveTrainingSet86,
                  MovingTrainingData9.moveTrainingSet87,
                  MovingTrainingData9.moveTrainingSet88)
              case 10 =>
                List(
                  MovingTrainingData10.moveTrainingSet81,
                  MovingTrainingData10.moveTrainingSet82,
                  MovingTrainingData10.moveTrainingSet83,
                  MovingTrainingData10.moveTrainingSet84,
                  MovingTrainingData10.moveTrainingSet85,
                  MovingTrainingData10.moveTrainingSet86,
                  MovingTrainingData10.moveTrainingSet87,
                  MovingTrainingData10.moveTrainingSet88)
            }

          case 10 =>
            datasetNumber match{
              case 1 =>
                List(
                  MovingTrainingData1.moveTrainingSet1001,
                  MovingTrainingData1.moveTrainingSet1002,
                  MovingTrainingData1.moveTrainingSet1003,
                  MovingTrainingData1.moveTrainingSet1004,
                  MovingTrainingData1.moveTrainingSet1005,
                  MovingTrainingData1.moveTrainingSet1006,
                  MovingTrainingData1.moveTrainingSet1007,
                  MovingTrainingData1.moveTrainingSet1008,
                  MovingTrainingData1.moveTrainingSet1009,
                  MovingTrainingData1.moveTrainingSet1010)
              case 2 =>
                List(
                  MovingTrainingData2.moveTrainingSet1001,
                  MovingTrainingData2.moveTrainingSet1002,
                  MovingTrainingData2.moveTrainingSet1003,
                  MovingTrainingData2.moveTrainingSet1004,
                  MovingTrainingData2.moveTrainingSet1005,
                  MovingTrainingData2.moveTrainingSet1006,
                  MovingTrainingData2.moveTrainingSet1007,
                  MovingTrainingData2.moveTrainingSet1008,
                  MovingTrainingData2.moveTrainingSet1009,
                  MovingTrainingData2.moveTrainingSet1010)
              case 3 =>
                List(
                  MovingTrainingData3.moveTrainingSet1001,
                  MovingTrainingData3.moveTrainingSet1002,
                  MovingTrainingData3.moveTrainingSet1003,
                  MovingTrainingData3.moveTrainingSet1004,
                  MovingTrainingData3.moveTrainingSet1005,
                  MovingTrainingData3.moveTrainingSet1006,
                  MovingTrainingData3.moveTrainingSet1007,
                  MovingTrainingData3.moveTrainingSet1008,
                  MovingTrainingData3.moveTrainingSet1009,
                  MovingTrainingData3.moveTrainingSet1010)
              case 4 =>
                List(
                  MovingTrainingData4.moveTrainingSet1001,
                  MovingTrainingData4.moveTrainingSet1002,
                  MovingTrainingData4.moveTrainingSet1003,
                  MovingTrainingData4.moveTrainingSet1004,
                  MovingTrainingData4.moveTrainingSet1005,
                  MovingTrainingData4.moveTrainingSet1006,
                  MovingTrainingData4.moveTrainingSet1007,
                  MovingTrainingData4.moveTrainingSet1008,
                  MovingTrainingData4.moveTrainingSet1009,
                  MovingTrainingData4.moveTrainingSet1010)
              case 5 =>
                List(
                  MovingTrainingData5.moveTrainingSet1001,
                  MovingTrainingData5.moveTrainingSet1002,
                  MovingTrainingData5.moveTrainingSet1003,
                  MovingTrainingData5.moveTrainingSet1004,
                  MovingTrainingData5.moveTrainingSet1005,
                  MovingTrainingData5.moveTrainingSet1006,
                  MovingTrainingData5.moveTrainingSet1007,
                  MovingTrainingData5.moveTrainingSet1008,
                  MovingTrainingData5.moveTrainingSet1009,
                  MovingTrainingData5.moveTrainingSet1010)
              case 6 =>
                List(
                  MovingTrainingData6.moveTrainingSet1001,
                  MovingTrainingData6.moveTrainingSet1002,
                  MovingTrainingData6.moveTrainingSet1003,
                  MovingTrainingData6.moveTrainingSet1004,
                  MovingTrainingData6.moveTrainingSet1005,
                  MovingTrainingData6.moveTrainingSet1006,
                  MovingTrainingData6.moveTrainingSet1007,
                  MovingTrainingData6.moveTrainingSet1008,
                  MovingTrainingData6.moveTrainingSet1009,
                  MovingTrainingData6.moveTrainingSet1010)
              case 7 =>
                List(
                  MovingTrainingData7.moveTrainingSet1001,
                  MovingTrainingData7.moveTrainingSet1002,
                  MovingTrainingData7.moveTrainingSet1003,
                  MovingTrainingData7.moveTrainingSet1004,
                  MovingTrainingData7.moveTrainingSet1005,
                  MovingTrainingData7.moveTrainingSet1006,
                  MovingTrainingData7.moveTrainingSet1007,
                  MovingTrainingData7.moveTrainingSet1008,
                  MovingTrainingData7.moveTrainingSet1009,
                  MovingTrainingData7.moveTrainingSet1010)
              case 8 =>
                List(
                  MovingTrainingData8.moveTrainingSet1001,
                  MovingTrainingData8.moveTrainingSet1002,
                  MovingTrainingData8.moveTrainingSet1003,
                  MovingTrainingData8.moveTrainingSet1004,
                  MovingTrainingData8.moveTrainingSet1005,
                  MovingTrainingData8.moveTrainingSet1006,
                  MovingTrainingData8.moveTrainingSet1007,
                  MovingTrainingData8.moveTrainingSet1008,
                  MovingTrainingData8.moveTrainingSet1009,
                  MovingTrainingData8.moveTrainingSet1010)
              case 9 =>
                List(
                  MovingTrainingData9.moveTrainingSet1001,
                  MovingTrainingData9.moveTrainingSet1002,
                  MovingTrainingData9.moveTrainingSet1003,
                  MovingTrainingData9.moveTrainingSet1004,
                  MovingTrainingData9.moveTrainingSet1005,
                  MovingTrainingData9.moveTrainingSet1006,
                  MovingTrainingData9.moveTrainingSet1007,
                  MovingTrainingData9.moveTrainingSet1008,
                  MovingTrainingData9.moveTrainingSet1009,
                  MovingTrainingData9.moveTrainingSet1010)
              case 10 =>
                List(
                  MovingTrainingData10.moveTrainingSet1001,
                  MovingTrainingData10.moveTrainingSet1002,
                  MovingTrainingData10.moveTrainingSet1003,
                  MovingTrainingData10.moveTrainingSet1004,
                  MovingTrainingData10.moveTrainingSet1005,
                  MovingTrainingData10.moveTrainingSet1006,
                  MovingTrainingData10.moveTrainingSet1007,
                  MovingTrainingData10.moveTrainingSet1008,
                  MovingTrainingData10.moveTrainingSet1009,
                  MovingTrainingData10.moveTrainingSet1010)
            }
          case 12 =>
            datasetNumber match{
              case 1 =>
                List(
                  MovingTrainingData1.moveTrainingSet1201,
                  MovingTrainingData1.moveTrainingSet1202,
                  MovingTrainingData1.moveTrainingSet1203,
                  MovingTrainingData1.moveTrainingSet1204,
                  MovingTrainingData1.moveTrainingSet1205,
                  MovingTrainingData1.moveTrainingSet1206,
                  MovingTrainingData1.moveTrainingSet1207,
                  MovingTrainingData1.moveTrainingSet1208,
                  MovingTrainingData1.moveTrainingSet1209,
                  MovingTrainingData1.moveTrainingSet1210,
                  MovingTrainingData1.moveTrainingSet1211,
                  MovingTrainingData1.moveTrainingSet1212)
              case 2 =>
                List(
                  MovingTrainingData2.moveTrainingSet1201,
                  MovingTrainingData2.moveTrainingSet1202,
                  MovingTrainingData2.moveTrainingSet1203,
                  MovingTrainingData2.moveTrainingSet1204,
                  MovingTrainingData2.moveTrainingSet1205,
                  MovingTrainingData2.moveTrainingSet1206,
                  MovingTrainingData2.moveTrainingSet1207,
                  MovingTrainingData2.moveTrainingSet1208,
                  MovingTrainingData2.moveTrainingSet1209,
                  MovingTrainingData2.moveTrainingSet1210,
                  MovingTrainingData2.moveTrainingSet1211,
                  MovingTrainingData2.moveTrainingSet1212)
              case 3 =>
                List(
                  MovingTrainingData3.moveTrainingSet1201,
                  MovingTrainingData3.moveTrainingSet1202,
                  MovingTrainingData3.moveTrainingSet1203,
                  MovingTrainingData3.moveTrainingSet1204,
                  MovingTrainingData3.moveTrainingSet1205,
                  MovingTrainingData3.moveTrainingSet1206,
                  MovingTrainingData3.moveTrainingSet1207,
                  MovingTrainingData3.moveTrainingSet1208,
                  MovingTrainingData3.moveTrainingSet1209,
                  MovingTrainingData3.moveTrainingSet1210,
                  MovingTrainingData3.moveTrainingSet1211,
                  MovingTrainingData3.moveTrainingSet1212)
              case 4 =>
                List(
                  MovingTrainingData4.moveTrainingSet1201,
                  MovingTrainingData4.moveTrainingSet1202,
                  MovingTrainingData4.moveTrainingSet1203,
                  MovingTrainingData4.moveTrainingSet1204,
                  MovingTrainingData4.moveTrainingSet1205,
                  MovingTrainingData4.moveTrainingSet1206,
                  MovingTrainingData4.moveTrainingSet1207,
                  MovingTrainingData4.moveTrainingSet1208,
                  MovingTrainingData4.moveTrainingSet1209,
                  MovingTrainingData4.moveTrainingSet1210,
                  MovingTrainingData4.moveTrainingSet1211,
                  MovingTrainingData4.moveTrainingSet1212)
              case 5 =>
                List(
                  MovingTrainingData5.moveTrainingSet1201,
                  MovingTrainingData5.moveTrainingSet1202,
                  MovingTrainingData5.moveTrainingSet1203,
                  MovingTrainingData5.moveTrainingSet1204,
                  MovingTrainingData5.moveTrainingSet1205,
                  MovingTrainingData5.moveTrainingSet1206,
                  MovingTrainingData5.moveTrainingSet1207,
                  MovingTrainingData5.moveTrainingSet1208,
                  MovingTrainingData5.moveTrainingSet1209,
                  MovingTrainingData5.moveTrainingSet1210,
                  MovingTrainingData5.moveTrainingSet1211,
                  MovingTrainingData5.moveTrainingSet1212)
              case 6 =>
                List(
                  MovingTrainingData6.moveTrainingSet1201,
                  MovingTrainingData6.moveTrainingSet1202,
                  MovingTrainingData6.moveTrainingSet1203,
                  MovingTrainingData6.moveTrainingSet1204,
                  MovingTrainingData6.moveTrainingSet1205,
                  MovingTrainingData6.moveTrainingSet1206,
                  MovingTrainingData6.moveTrainingSet1207,
                  MovingTrainingData6.moveTrainingSet1208,
                  MovingTrainingData6.moveTrainingSet1209,
                  MovingTrainingData6.moveTrainingSet1210,
                  MovingTrainingData6.moveTrainingSet1211,
                  MovingTrainingData6.moveTrainingSet1212)
              case 7 =>
                List(
                  MovingTrainingData7.moveTrainingSet1201,
                  MovingTrainingData7.moveTrainingSet1202,
                  MovingTrainingData7.moveTrainingSet1203,
                  MovingTrainingData7.moveTrainingSet1204,
                  MovingTrainingData7.moveTrainingSet1205,
                  MovingTrainingData7.moveTrainingSet1206,
                  MovingTrainingData7.moveTrainingSet1207,
                  MovingTrainingData7.moveTrainingSet1208,
                  MovingTrainingData7.moveTrainingSet1209,
                  MovingTrainingData7.moveTrainingSet1210,
                  MovingTrainingData7.moveTrainingSet1211,
                  MovingTrainingData7.moveTrainingSet1212)
              case 8 =>
                List(
                  MovingTrainingData8.moveTrainingSet1201,
                  MovingTrainingData8.moveTrainingSet1202,
                  MovingTrainingData8.moveTrainingSet1203,
                  MovingTrainingData8.moveTrainingSet1204,
                  MovingTrainingData8.moveTrainingSet1205,
                  MovingTrainingData8.moveTrainingSet1206,
                  MovingTrainingData8.moveTrainingSet1207,
                  MovingTrainingData8.moveTrainingSet1208,
                  MovingTrainingData8.moveTrainingSet1209,
                  MovingTrainingData8.moveTrainingSet1210,
                  MovingTrainingData8.moveTrainingSet1211,
                  MovingTrainingData8.moveTrainingSet1212)
              case 9 =>
                List(
                  MovingTrainingData9.moveTrainingSet1201,
                  MovingTrainingData9.moveTrainingSet1202,
                  MovingTrainingData9.moveTrainingSet1203,
                  MovingTrainingData9.moveTrainingSet1204,
                  MovingTrainingData9.moveTrainingSet1205,
                  MovingTrainingData9.moveTrainingSet1206,
                  MovingTrainingData9.moveTrainingSet1207,
                  MovingTrainingData9.moveTrainingSet1208,
                  MovingTrainingData9.moveTrainingSet1209,
                  MovingTrainingData9.moveTrainingSet1210,
                  MovingTrainingData9.moveTrainingSet1211,
                  MovingTrainingData9.moveTrainingSet1212)
              case 10 =>
                List(
                  MovingTrainingData10.moveTrainingSet1201,
                  MovingTrainingData10.moveTrainingSet1202,
                  MovingTrainingData10.moveTrainingSet1203,
                  MovingTrainingData10.moveTrainingSet1204,
                  MovingTrainingData10.moveTrainingSet1205,
                  MovingTrainingData10.moveTrainingSet1206,
                  MovingTrainingData10.moveTrainingSet1207,
                  MovingTrainingData10.moveTrainingSet1208,
                  MovingTrainingData10.moveTrainingSet1209,
                  MovingTrainingData10.moveTrainingSet1210,
                  MovingTrainingData10.moveTrainingSet1211,
                  MovingTrainingData10.moveTrainingSet1212)
            }

        }
    }
*/
    val trainingSets = if (mode=="monolithic") {
      datasetNumber match {
        case 1 => List (MeetingTrainingData1.meetTrainingSet1)
        case 2 => List (MeetingTrainingData1.meetTrainingSet2)
        case 3 => List (MeetingTrainingData1.meetTrainingSet3)
        case 4 => List (MeetingTrainingData1.meetTrainingSet4)
        case 5 => List (MeetingTrainingData1.meetTrainingSet5)
        case 6 => List (MeetingTrainingData1.meetTrainingSet6)
        case 7 => List (MeetingTrainingData1.meetTrainingSet7)
        case 8 => List (MeetingTrainingData1.meetTrainingSet8)
        case 9 => List (MeetingTrainingData1.meetTrainingSet9)
        case 10 => List (MeetingTrainingData1.meetTrainingSet10)
      }
    }
    else{
      HLE match{
        case "meeting" =>
          datasetNumber match{
            case 1 =>
              numberOfCores match{
                case 2 =>
                  MeetingDistributedIntervals.TwoFoldSplit.meetTrainingSet1
                case 6 =>
                  List( MeetingTrainingData1.meetTrainingSet61,
                    MeetingTrainingData1.meetTrainingSet62,
                    MeetingTrainingData1.meetTrainingSet63,
                    MeetingTrainingData1.meetTrainingSet64,
                    MeetingTrainingData1.meetTrainingSet65,
                    MeetingTrainingData1.meetTrainingSet66)
                case 4 =>
                  List( MeetingTrainingData1.meetTrainingSet41,
                    MeetingTrainingData1.meetTrainingSet42,
                    MeetingTrainingData1.meetTrainingSet43,
                    MeetingTrainingData1.meetTrainingSet44)
                case 8 =>
                  MeetingDistributedIntervals.EightFoldSplit.meetTrainingSet1
                case 10 =>
                  MeetingDistributedIntervals.TenFoldSplit.meetTrainingSet1
              }
            case 2 =>numberOfCores match{
              case 2 =>
                MeetingDistributedIntervals.TwoFoldSplit.meetTrainingSet2
              case 6 =>
                MeetingDistributedIntervals.SixFoldSplit.meetTrainingSet2
              case 4 =>
                MeetingDistributedIntervals.FourFoldSplit.meetTrainingSet2
              case 8 =>
                MeetingDistributedIntervals.EightFoldSplit.meetTrainingSet2
              case 10 =>
                MeetingDistributedIntervals.TenFoldSplit.meetTrainingSet2
            }
            case 3 =>numberOfCores match{
              case 2 =>
                MeetingDistributedIntervals.TwoFoldSplit.meetTrainingSet3
              case 6 =>
                MeetingDistributedIntervals.SixFoldSplit.meetTrainingSet3
              case 4 =>
                MeetingDistributedIntervals.FourFoldSplit.meetTrainingSet3
              case 8 =>
                MeetingDistributedIntervals.EightFoldSplit.meetTrainingSet3
              case 10 =>
                MeetingDistributedIntervals.TenFoldSplit.meetTrainingSet3
            }
            case 4 =>numberOfCores match{
              case 2 =>
                MeetingDistributedIntervals.TwoFoldSplit.meetTrainingSet4
              case 6 =>
                MeetingDistributedIntervals.SixFoldSplit.meetTrainingSet4
              case 4 =>
                MeetingDistributedIntervals.FourFoldSplit.meetTrainingSet4
              case 8 =>
                MeetingDistributedIntervals.EightFoldSplit.meetTrainingSet4
              case 10 =>
                MeetingDistributedIntervals.TenFoldSplit.meetTrainingSet4
            }
            case 5 =>numberOfCores match{
              case 2 =>
                MeetingDistributedIntervals.TwoFoldSplit.meetTrainingSet5
              case 6 =>
                MeetingDistributedIntervals.SixFoldSplit.meetTrainingSet5
              case 4 =>
                MeetingDistributedIntervals.FourFoldSplit.meetTrainingSet5
              case 8 =>
                MeetingDistributedIntervals.EightFoldSplit.meetTrainingSet5
              case 10 =>
                MeetingDistributedIntervals.TenFoldSplit.meetTrainingSet5
            }
            case 6 =>numberOfCores match{
              case 2 =>
                MeetingDistributedIntervals.TwoFoldSplit.meetTrainingSet6
              case 6 =>
                MeetingDistributedIntervals.SixFoldSplit.meetTrainingSet6
              case 4 =>
                MeetingDistributedIntervals.FourFoldSplit.meetTrainingSet6
              case 8 =>
                MeetingDistributedIntervals.EightFoldSplit.meetTrainingSet6
              case 10 =>
                MeetingDistributedIntervals.TenFoldSplit.meetTrainingSet6
            }
            case 7 =>numberOfCores match{
              case 2 =>
                MeetingDistributedIntervals.TwoFoldSplit.meetTrainingSet7
              case 6 =>
                MeetingDistributedIntervals.SixFoldSplit.meetTrainingSet7
              case 4 =>
                MeetingDistributedIntervals.FourFoldSplit.meetTrainingSet7
              case 8 =>
                MeetingDistributedIntervals.EightFoldSplit.meetTrainingSet7
              case 10 =>
                MeetingDistributedIntervals.TenFoldSplit.meetTrainingSet7
            }
            case 8 =>numberOfCores match{
              case 2 =>
                MeetingDistributedIntervals.TwoFoldSplit.meetTrainingSet8
              case 6 =>
                MeetingDistributedIntervals.SixFoldSplit.meetTrainingSet8
              case 4 =>
                MeetingDistributedIntervals.FourFoldSplit.meetTrainingSet8
              case 8 =>
                MeetingDistributedIntervals.EightFoldSplit.meetTrainingSet8
              case 10 =>
                MeetingDistributedIntervals.TenFoldSplit.meetTrainingSet8
            }
            case 9 =>numberOfCores match{
              case 2 =>
                MeetingDistributedIntervals.TwoFoldSplit.meetTrainingSet9
              case 6 =>
                MeetingDistributedIntervals.SixFoldSplit.meetTrainingSet9
              case 4 =>
                MeetingDistributedIntervals.FourFoldSplit.meetTrainingSet9
              case 8 =>
                MeetingDistributedIntervals.EightFoldSplit.meetTrainingSet9
              case 10 =>
                MeetingDistributedIntervals.TenFoldSplit.meetTrainingSet9
            }
            case 10 =>numberOfCores match{
              case 2 =>
                MeetingDistributedIntervals.TwoFoldSplit.meetTrainingSet10
              case 6 =>
                MeetingDistributedIntervals.SixFoldSplit.meetTrainingSet10
              case 4 =>
                MeetingDistributedIntervals.FourFoldSplit.meetTrainingSet10
              case 8 =>
                MeetingDistributedIntervals.EightFoldSplit.meetTrainingSet10
              case 10 =>
                MeetingDistributedIntervals.TenFoldSplit.meetTrainingSet10
            }
          }
        case "moving" =>
          datasetNumber match{
            case 1 =>
              numberOfCores match{
                case 2 =>
                  MovingDistributedIntervals.TwoFoldSplit.moveTrainingSet1
                case 6 =>
                  MovingDistributedIntervals.SixFoldSplit.moveTrainingSet1
                case 4 =>
                  MovingDistributedIntervals.FourFoldSplit.moveTrainingSet1
                case 8 =>
                  MovingDistributedIntervals.EightFoldSplit.moveTrainingSet1
                case 10 =>
                  MovingDistributedIntervals.TenFoldSplit.moveTrainingSet1
              }
            case 2 =>numberOfCores match{
              case 2 =>
                MovingDistributedIntervals.TwoFoldSplit.moveTrainingSet2
              case 6 =>
                MovingDistributedIntervals.SixFoldSplit.moveTrainingSet2
              case 4 =>
                MovingDistributedIntervals.FourFoldSplit.moveTrainingSet2
              case 8 =>
                MovingDistributedIntervals.EightFoldSplit.moveTrainingSet2
              case 10 =>
                MovingDistributedIntervals.TenFoldSplit.moveTrainingSet2
            }
            case 3 =>numberOfCores match{
              case 2 =>
                MovingDistributedIntervals.TwoFoldSplit.moveTrainingSet3
              case 6 =>
                MovingDistributedIntervals.SixFoldSplit.moveTrainingSet3
              case 4 =>
                MovingDistributedIntervals.FourFoldSplit.moveTrainingSet3
              case 8 =>
                MovingDistributedIntervals.EightFoldSplit.moveTrainingSet3
              case 10 =>
                MovingDistributedIntervals.TenFoldSplit.moveTrainingSet3
            }
            case 4 =>numberOfCores match{
              case 2 =>
                MovingDistributedIntervals.TwoFoldSplit.moveTrainingSet4
              case 6 =>
                MovingDistributedIntervals.SixFoldSplit.moveTrainingSet4
              case 4 =>
                MovingDistributedIntervals.FourFoldSplit.moveTrainingSet4
              case 8 =>
                MovingDistributedIntervals.EightFoldSplit.moveTrainingSet4
              case 10 =>
                MovingDistributedIntervals.TenFoldSplit.moveTrainingSet4
            }
            case 5 =>numberOfCores match{
              case 2 =>
                MovingDistributedIntervals.TwoFoldSplit.moveTrainingSet5
              case 6 =>
                MovingDistributedIntervals.SixFoldSplit.moveTrainingSet5
              case 4 =>
                MovingDistributedIntervals.FourFoldSplit.moveTrainingSet5
              case 8 =>
                MovingDistributedIntervals.EightFoldSplit.moveTrainingSet5
              case 10 =>
                MovingDistributedIntervals.TenFoldSplit.moveTrainingSet5
            }
            case 6 =>numberOfCores match{
              case 2 =>
                MovingDistributedIntervals.TwoFoldSplit.moveTrainingSet6
              case 6 =>
                MovingDistributedIntervals.SixFoldSplit.moveTrainingSet6
              case 4 =>
                MovingDistributedIntervals.FourFoldSplit.moveTrainingSet6
              case 8 =>
                MovingDistributedIntervals.EightFoldSplit.moveTrainingSet6
              case 10 =>
                MovingDistributedIntervals.TenFoldSplit.moveTrainingSet6
            }
            case 7 =>numberOfCores match{
              case 2 =>
                MovingDistributedIntervals.TwoFoldSplit.moveTrainingSet7
              case 6 =>
                MovingDistributedIntervals.SixFoldSplit.moveTrainingSet7
              case 4 =>
                MovingDistributedIntervals.FourFoldSplit.moveTrainingSet7
              case 8 =>
                MovingDistributedIntervals.EightFoldSplit.moveTrainingSet7
              case 10 =>
                MovingDistributedIntervals.TenFoldSplit.moveTrainingSet7
            }
            case 8 =>numberOfCores match{
              case 2 =>
                MovingDistributedIntervals.TwoFoldSplit.moveTrainingSet8
              case 6 =>
                MovingDistributedIntervals.SixFoldSplit.moveTrainingSet8
              case 4 =>
                MovingDistributedIntervals.FourFoldSplit.moveTrainingSet8
              case 8 =>
                MovingDistributedIntervals.EightFoldSplit.moveTrainingSet8
              case 10 =>
                MovingDistributedIntervals.TenFoldSplit.moveTrainingSet8
            }
            case 9 =>numberOfCores match{
              case 2 =>
                MovingDistributedIntervals.TwoFoldSplit.moveTrainingSet9
              case 6 =>
                MovingDistributedIntervals.SixFoldSplit.moveTrainingSet9
              case 4 =>
                MovingDistributedIntervals.FourFoldSplit.moveTrainingSet9
              case 8 =>
                MovingDistributedIntervals.EightFoldSplit.moveTrainingSet9
              case 10 =>
                MovingDistributedIntervals.TenFoldSplit.moveTrainingSet9
            }
            case 10 =>numberOfCores match{
              case 2 =>
                MovingDistributedIntervals.TwoFoldSplit.moveTrainingSet10
              case 6 =>
                MovingDistributedIntervals.SixFoldSplit.moveTrainingSet10
              case 4 =>
                MovingDistributedIntervals.FourFoldSplit.moveTrainingSet10
              case 8 =>
                MovingDistributedIntervals.EightFoldSplit.moveTrainingSet10
              case 10 =>
                MovingDistributedIntervals.TenFoldSplit.moveTrainingSet10
            }
          }
      }
      
    }

    trainingSets.foreach( dataset => println(dataset.trainingSet.toString() ) )

    logger.info(s"Distributed OLED, mode: $mode, Min Seen:$minSeenExmpls, repeat for:$repeatFor, delta:$delta, number of cores:$numberOfCores")
    mode match{
      case "monolithic" =>
        val actor =
          system.actorOf(Props(
            new Monolither(DB,
              delta, breakTiesThreshold, postPruningThreshold, minSeenExmpls, 1500, repeatFor, chunkSize,
              withInertia, withPostPruning, onlinePruning,
              testingSet, HLE, HAND_CRAFTED, globals)
          ), name = "Monolithic")
        actor ! "start"
      case "streaming" =>
        val actor =
          system.actorOf(Props(
            new MasterActor(trainingSets, DB,
              delta, breakTiesThreshold, postPruningThreshold, minSeenExmpls, repeatFor, chunkSize,
              withInertia, withPostPruning, onlinePruning,
              testingSet, HLE, HAND_CRAFTED, globals)
          ), name = "MasterActor")
        actor ! "start"
      case "single-lock" =>
        val actor =
          system.actorOf(Props(
            new MasterActor(trainingSets, DB,
              delta, breakTiesThreshold, postPruningThreshold, minSeenExmpls, repeatFor, chunkSize,
              withInertia, withPostPruning, onlinePruning,
              testingSet, HLE, HAND_CRAFTED, globals)
          ), name = "MasterActor")
        actor ! "wait"
      case "global" =>
        val actor =
          system.actorOf(Props(
            new GlobalLocker(trainingSets, DB,
              delta, breakTiesThreshold, postPruningThreshold, minSeenExmpls, repeatFor, chunkSize,
              withInertia, withPostPruning, onlinePruning,
              testingSet, HLE, HAND_CRAFTED, globals)
          ), name = "Waiter")
        actor ! "start"
    }
  }
}
