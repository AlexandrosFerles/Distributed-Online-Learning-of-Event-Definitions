package oled.whole_caviar_data

import utils.DataUtils.{DataAsIntervals, Interval}
import scala.util.Random


/**
  * Created by ferles on 16/5/2017.
  */
object MovingDistributedIntervals {

  val movePos1 = Interval("moving",2520,5800)       //82
  val movePos2 = Interval("moving",24440,27360)     //73
  val movePos3 = Interval("moving",460640,464200)   //89
  val movePos4 = Interval("moving",547400,559280)   //297
  val movePos5 = Interval("moving",565800,568120)   //58
  val movePos6 = Interval("moving",786240,791880)   //141
  val movePos7 = Interval("moving",797120,800440)   //83
  val movePos8 = Interval("moving",814880,829480)   //365
  val movePos9 = Interval("moving",843560,849440)   //147
  val movePos10 = Interval("moving",872000,884560)  //314
  val movePos11 = Interval("moving",885480,892520)  //176


  val moveNeg1 = Interval("moving",680,2560)        //48
  val moveNeg2 = Interval("moving",5760,24480)      //469
  val moveNeg3 = Interval("moving",27320,67280)     //1000
  val moveNeg4 = Interval("moving",67320,107280)    //1000
  val moveNeg5 = Interval("moving",107320,147280)   //1000
  val moveNeg6 = Interval("moving",147320,187280)   //1000
  val moveNeg7 = Interval("moving",187320,227280)   //1000
  val moveNeg8 = Interval("moving",227320,267280)   //1000
  val moveNeg9 = Interval("moving",267320,307280)   //1000
  val moveNeg10 = Interval("moving",307320,347280)  //1000
  val moveNeg11 = Interval("moving",347320,387280)  //1000
  val moveNeg12 = Interval("moving",387320,427280)  //1000
  val moveNeg13 = Interval("moving",427320,460640)  //834
  val moveNeg14 = Interval("moving",464160,504120)  //1000
  val moveNeg15 = Interval("moving",504160,544120)  //1000
  val moveNeg16 = Interval("moving",544160,547400)  //82
  val moveNeg17 = Interval("moving",559240,565840)  //166
  val moveNeg18 = Interval("moving",568080,608040)  //1000
  val moveNeg19 = Interval("moving",608080,648040)  //1000
  val moveNeg20 = Interval("moving",648080,688040)  //1000
  val moveNeg21 = Interval("moving",688080,728040)  //1000
  val moveNeg22 = Interval("moving",728080,768040)  //1000
  val moveNeg23 = Interval("moving",768080,786240)  //455
  val moveNeg24 = Interval("moving",791840,797160)  //134
  val moveNeg25 = Interval("moving",800400,814920)  //364
  val moveNeg26 = Interval("moving",829440,843600)  //355
  val moveNeg27 = Interval("moving",849400,872040)  //567
  val moveNeg28 = Interval("moving",884520,885520)  //26
  val moveNeg29 = Interval("moving",892480,932440)//1000
  val moveNeg30 = Interval("moving",932480,972440)//1000
  val moveNeg31 = Interval("moving",972480,1012440)//1000
  val moveNeg32 = Interval("moving",1012480,1052440)//1000
  val moveNeg33 = Interval("moving",1052480,1077640)//1000

  val allNegIntervals = List(moveNeg1,moveNeg2,moveNeg3,moveNeg4,moveNeg5,moveNeg6,moveNeg7,moveNeg8,moveNeg9,moveNeg10,moveNeg11,moveNeg12,moveNeg13,moveNeg14,
    moveNeg15,moveNeg16,moveNeg17,moveNeg18,moveNeg19,moveNeg20,moveNeg21,moveNeg22,moveNeg23,moveNeg24,moveNeg25,moveNeg26,moveNeg27,
    moveNeg28,moveNeg29,moveNeg30,moveNeg31,moveNeg32,moveNeg33)



  val allPosIntervals = List(movePos1,movePos2,movePos3,movePos4,movePos5,movePos6,movePos7,movePos8,movePos9,movePos10,movePos11)

  val testingNeg1 = List(moveNeg1,moveNeg2,moveNeg3,moveNeg31)
  val testingNeg2 = List(moveNeg4,moveNeg5,moveNeg6)
  val testingNeg3 = List(moveNeg7,moveNeg8,moveNeg9)
  val testingNeg4 = List(moveNeg10,moveNeg11,moveNeg12)
  val testingNeg5 = List(moveNeg13,moveNeg14,moveNeg15)
  val testingNeg6 = List(moveNeg16,moveNeg17,moveNeg18)
  val testingNeg7 = List(moveNeg19,moveNeg20,moveNeg21)
  val testingNeg8 = List(moveNeg21,moveNeg23,moveNeg24,moveNeg33)
  val testingNeg9 = List(moveNeg25,moveNeg26,moveNeg27,moveNeg32)
  val testingNeg10 = List(moveNeg28,moveNeg29,moveNeg30)

  def splitN[A](xs: List[A], n: Int) = {
    val (quot, rem) = (xs.size / n, xs.size % n)
    val (smaller, bigger) = xs.splitAt(xs.size - rem * (quot + 1))
    smaller.grouped(quot) ++ bigger.grouped(quot + 1)
  }

  /* Split large positive intervals into smaller ones to evenly distribute the data */
  def splitInterval(x: Interval, byN: Int) = {
    splitN((x.startPoint to x.endPoint by 40).toList, byN).toList.map(z => Interval(x.HLE, z.head, z.tail.reverse.head))
  }


  def getData(testingPos: Interval, testingNegs: List[Interval], coresNum: Int) = {
    /*
    * meetPos1 (size=470) and meetPos1 (size=858) are large positive intervals.
    * Therefore, if they are in the training set, we break them into smaller
    * intervals in order to evenly distribute the data across nodes.
    * */

    ///*
    def isInTrainingSet(x: Interval) = x != testingPos

    var allowedPos = allPosIntervals.filter(x => x!= testingPos)

    if (isInTrainingSet(movePos4)) {
      allowedPos = allowedPos.filter(x => x!= movePos4)
      // split movePos1
      val subIntervals = splitInterval(movePos4, 4)
      allowedPos = subIntervals ++ allowedPos
    }

    if (isInTrainingSet(movePos8)) {
      allowedPos = allowedPos.filter(x => x!= movePos8)
      // split movePos1
      val subIntervals = splitInterval(movePos8, 4)
      allowedPos = subIntervals ++ allowedPos
    }

    if (isInTrainingSet(movePos10)) {
      allowedPos = allowedPos.filter(x => x!= movePos8)
      // split movePos1
      val subIntervals = splitInterval(movePos8, 4)
      allowedPos = subIntervals ++ allowedPos
    }
    //*/
    //val allowedPos = allPosIntervals.filter(x => x!= testingPos)
    val allowedNegs = allNegIntervals.filter(z => !testingNegs.contains(z))

    val positives = splitN(allowedPos, coresNum).toList
    val negatives = splitN(allowedNegs, coresNum).toList

    if (positives.length != negatives.length) throw new RuntimeException("SOMETHING'S WRONG!")

    val zipped = positives zip negatives
    val testing = List(testingPos) ++ testingNegs
    val out = zipped.map(x => new DataAsIntervals(trainingSet = List(x._1.head) ++ Random.shuffle(x._1.tail ++ x._2), testingSet = testing) )
    out
  }

  def main(args: Array[String]) = {
    /*
    * Just for testing-debugging
    *
    * */
    ///*
    val eight = EightFoldSplit.moveTrainingSet1
    val four = FourFoldSplit.moveTrainingSet1
    val two = TwoFoldSplit.moveTrainingSet1

    println(eight)
    println("")
    println(four)
    println("")
    println(two)
    //*/
    //println(splitInterval(meetPos1, 4))
    //println(Interval("meeting",5720,24480) == meetPos1)
  }





  object TwoFoldSplit {

    val cores = 2

    val moveTrainingSet1 = getData(movePos1, testingNeg1, cores)
    val moveTrainingSet2 = getData(movePos2, testingNeg2, cores)
    val moveTrainingSet3 = getData(movePos3, testingNeg3, cores)
    val moveTrainingSet4 = getData(movePos4, testingNeg4, cores)
    val moveTrainingSet5 = getData(movePos5, testingNeg5, cores)
    val moveTrainingSet6 = getData(movePos6, testingNeg6, cores)
    val moveTrainingSet7 = getData(movePos7, testingNeg7, cores)
    val moveTrainingSet8 = getData(movePos8, testingNeg8, cores)
    val moveTrainingSet9 = getData(movePos9, testingNeg9, cores)
    val moveTrainingSet10 = getData(movePos10, testingNeg10, cores)

  }



  object FourFoldSplit {

    val cores = 4

    val moveTrainingSet1 = getData(movePos1, testingNeg1, cores)
    val moveTrainingSet2 = getData(movePos2, testingNeg2, cores)
    val moveTrainingSet3 = getData(movePos3, testingNeg3, cores)
    val moveTrainingSet4 = getData(movePos4, testingNeg4, cores)
    val moveTrainingSet5 = getData(movePos5, testingNeg5, cores)
    val moveTrainingSet6 = getData(movePos6, testingNeg6, cores)
    val moveTrainingSet7 = getData(movePos7, testingNeg7, cores)
    val moveTrainingSet8 = getData(movePos8, testingNeg8, cores)
    val moveTrainingSet9 = getData(movePos9, testingNeg9, cores)
    val moveTrainingSet10 = getData(movePos10, testingNeg10, cores)
  }

  object SixFoldSplit {
    val cores = 6

    val moveTrainingSet1 = getData(movePos1, testingNeg1, cores)
    val moveTrainingSet2 = getData(movePos2, testingNeg2, cores)
    val moveTrainingSet3 = getData(movePos3, testingNeg3, cores)
    val moveTrainingSet4 = getData(movePos4, testingNeg4, cores)
    val moveTrainingSet5 = getData(movePos5, testingNeg5, cores)
    val moveTrainingSet6 = getData(movePos6, testingNeg6, cores)
    val moveTrainingSet7 = getData(movePos7, testingNeg7, cores)
    val moveTrainingSet8 = getData(movePos8, testingNeg8, cores)
    val moveTrainingSet9 = getData(movePos9, testingNeg9, cores)
    val moveTrainingSet10 = getData(movePos10, testingNeg10, cores)

  }

  object EightFoldSplit {

    val cores = 8

    val moveTrainingSet1 = getData(movePos1, testingNeg1, cores)
    val moveTrainingSet2 = getData(movePos2, testingNeg2, cores)
    val moveTrainingSet3 = getData(movePos3, testingNeg3, cores)
    val moveTrainingSet4 = getData(movePos4, testingNeg4, cores)
    val moveTrainingSet5 = getData(movePos5, testingNeg5, cores)
    val moveTrainingSet6 = getData(movePos6, testingNeg6, cores)
    val moveTrainingSet7 = getData(movePos7, testingNeg7, cores)
    val moveTrainingSet8 = getData(movePos8, testingNeg8, cores)
    val moveTrainingSet9 = getData(movePos9, testingNeg9, cores)
    val moveTrainingSet10 = getData(movePos10, testingNeg10, cores)
  }

  object TenFoldSplit {

    val cores = 10

    val moveTrainingSet1 = getData(movePos1, testingNeg1, cores)
    val moveTrainingSet2 = getData(movePos2, testingNeg2, cores)
    val moveTrainingSet3 = getData(movePos3, testingNeg3, cores)
    val moveTrainingSet4 = getData(movePos4, testingNeg4, cores)
    val moveTrainingSet5 = getData(movePos5, testingNeg5, cores)
    val moveTrainingSet6 = getData(movePos6, testingNeg6, cores)
    val moveTrainingSet7 = getData(movePos7, testingNeg7, cores)
    val moveTrainingSet8 = getData(movePos8, testingNeg8, cores)
    val moveTrainingSet9 = getData(movePos9, testingNeg9, cores)
    val moveTrainingSet10 = getData(movePos10, testingNeg10, cores)
  }


}
