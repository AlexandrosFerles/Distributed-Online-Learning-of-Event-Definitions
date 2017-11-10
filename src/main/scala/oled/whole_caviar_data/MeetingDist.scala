package oled.whole_caviar_data

/**
  * Created by ferles on 12/5/2017.
  */
package oled.whole_caviar_data

import utils.DataUtils.{Interval, DataAsIntervals}


import scala.util.Random

/**
  * Created by nkatz on 4/11/17.
  */

object MeetingDistributedIntervals {

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

    if (isInTrainingSet(meetPos1)) {
      allowedPos = allowedPos.filter(x => x!= meetPos1)
      // split meetPos1
      val subIntervals = splitInterval(meetPos1, 4)
      allowedPos = subIntervals ++ allowedPos
    }

    if (isInTrainingSet(meetPos2)) {
      allowedPos = allowedPos.filter(x => x!= meetPos2)
      // split meetPos1
      val subIntervals = splitInterval(meetPos2, 8)
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

  val meetPos1 = Interval("meeting",5720,24480)     //  469
  val meetPos2 = Interval("meeting",27280,61560)    //  857
  val meetPos3 = Interval("meeting",507120,509800)  //  67
  val meetPos4 = Interval("meeting",559200,564200)  //  125
  val meetPos5 = Interval("meeting",785240,786320)  //  27
  val meetPos6 = Interval("meeting",813080,814960)  //  47
  val meetPos7 = Interval("meeting",829400,835520)  //  153
  val meetPos8 = Interval("meeting",842680,843640)  //  24
  val meetPos9 = Interval("meeting",892440,894920)  //  62
  val meetPos10 = Interval("meeting",1009000,1009880) //22

  // To break large intervals in smaller of 1000 data points use this (40 is the step):
  // List.range(568080,786280,40).grouped(1000).map(x => (x.head,x.tail.reverse.head)) foreach println
  val meetNeg1 = Interval("meeting",680,5760)       // length: 128
  val meetNeg2 = Interval("meeting",24440,27320)    // length: 73
  val meetNeg3 = Interval("meeting",61520,101480)   // length: 1000
  val meetNeg4 = Interval("meeting",101520,141480)  // length: 1000
  val meetNeg5 = Interval("meeting",141520,181480)  // length: 1000
  val meetNeg6 = Interval("meeting",181520,221480)  // length: 1000
  val meetNeg7 = Interval("meeting",221520,261480)  // length: 1000
  val meetNeg8 = Interval("meeting",261520,301480)  // length: 1000
  val meetNeg9 = Interval("meeting",301520,341480)  // length: 1000
  val meetNeg10 = Interval("meeting",341520,381480) // length: 1000
  val meetNeg11 = Interval("meeting",381520,421480) // length: 1000
  val meetNeg12 = Interval("meeting",421520,461480) // length: 1000
  val meetNeg13 = Interval("meeting",461520,501480) // length: 1000
  val meetNeg14 = Interval("meeting",501520,507160) // length: 142
  val meetNeg15 = Interval("meeting",509760,559240) // length: 1238
  val meetNeg16 = Interval("meeting",564160,604120) // length: 1000
  val meetNeg17 = Interval("meeting",604160,644120) // length: 1000
  val meetNeg18 = Interval("meeting",644160,684120) // length: 1000
  val meetNeg19 = Interval("meeting",684160,724120) // length: 1000
  val meetNeg20 = Interval("meeting",724160,764120) // length: 1000
  val meetNeg21 = Interval("meeting",764160,785240) // length: 528
  val meetNeg22 = Interval("meeting",786280,813120) // length: 672
  val meetNeg23 = Interval("meeting",814920,829440) // length: 364
  val meetNeg24 = Interval("meeting",835480,842720) // length: 182
  val meetNeg25 = Interval("meeting",843600,892480) // length: 1223
  val meetNeg26 = Interval("meeting",894880,914840) // length: 500
  val meetNeg27 = Interval("meeting",914880,934840) // length: 500
  val meetNeg28 = Interval("meeting",934880,954840) // length: 500
  val meetNeg29 = Interval("meeting",954880,974840) // length: 500
  val meetNeg30 = Interval("meeting",974880,994840) // length: 500
  val meetNeg31 = Interval("meeting",994880,1005760)// length: 273
  val meetNeg32 = Interval("meeting",1005840,1009040)// length: 81
  val meetNeg33 = Interval("meeting",1009840,1077680)//// length: 1697


  val allNegIntervals = List(meetNeg1,meetNeg2,meetNeg3,meetNeg4,meetNeg5,meetNeg6,meetNeg7,meetNeg8,meetNeg9,meetNeg10,meetNeg11,meetNeg12,meetNeg13,meetNeg14,
    meetNeg15,meetNeg16,meetNeg17,meetNeg18,meetNeg19,meetNeg20,meetNeg21,meetNeg22,meetNeg23,meetNeg24,meetNeg25,meetNeg26,meetNeg27,
    meetNeg28,meetNeg29,meetNeg30,meetNeg31,meetNeg32,meetNeg33)

  val allPosIntervals = List(meetPos1,meetPos2,meetPos3,meetPos4,meetPos5,meetPos6,meetPos7,meetPos8,meetPos9,meetPos10)

  // Negative intervals for the testing sets
  val testingNeg1 = List(meetNeg1,meetNeg2,meetNeg3,meetNeg33)
  val testingNeg2 = List(meetNeg4,meetNeg5,meetNeg6)
  val testingNeg3 = List(meetNeg7,meetNeg8,meetNeg9)
  val testingNeg4 = List(meetNeg10,meetNeg11,meetNeg12)
  val testingNeg5 = List(meetNeg13,meetNeg14,meetNeg15)
  val testingNeg6 = List(meetNeg16,meetNeg17,meetNeg18)
  val testingNeg7 = List(meetNeg19,meetNeg20,meetNeg21)
  val testingNeg8 = List(meetNeg22,meetNeg23,meetNeg24)
  val testingNeg9 = List(meetNeg25,meetNeg26,meetNeg27)
  val testingNeg10 = List(meetNeg28,meetNeg29,meetNeg30,meetNeg31,meetNeg32)

  val allNegativeTestingSetIntervals =
    List(testingNeg1,testingNeg2,testingNeg3,testingNeg4,testingNeg5,testingNeg6,testingNeg7,testingNeg8,testingNeg9,testingNeg10)

  // Training set 1. All but meetPos1
  //----------------------------------
  val meetTrainingSet1 = {
    val training = allPosIntervals.filter(x => x!= meetPos1) ++ allNegIntervals.filter(z => !testingNeg1.contains(z))
    val testing = List(meetPos1) ++ testingNeg1
    new DataAsIntervals(trainingSet = List(training.head) ++ Random.shuffle(training.tail), testingSet = testing)
  }

  // Training set 2. All but meetPos2
  //----------------------------------
  val meetTrainingSet2 = {
    val training = allPosIntervals.filter(x => x!= meetPos2) ++ allNegIntervals.filter(z => !testingNeg2.contains(z))
    val testing = List(meetPos2) ++ testingNeg2
    new DataAsIntervals(trainingSet = List(training.head) ++ Random.shuffle(training.tail), testingSet = testing)
  }

  // Training set 3. All but meetPos3
  //----------------------------------
  val meetTrainingSet3 = {
    val training = allPosIntervals.filter(x => x!= meetPos3) ++ allNegIntervals.filter(z => !testingNeg3.contains(z))
    val testing = List(meetPos3) ++ testingNeg3
    new DataAsIntervals(trainingSet = List(training.head) ++ Random.shuffle(training.tail), testingSet = testing)
  }

  // Training set 4. All but meetPos4
  //----------------------------------
  val meetTrainingSet4 = {
    val training = allPosIntervals.filter(x => x!= meetPos4) ++ allNegIntervals.filter(z => !testingNeg4.contains(z))
    val testing = List(meetPos4) ++ testingNeg4
    new DataAsIntervals(trainingSet = List(training.head) ++ Random.shuffle(training.tail), testingSet = testing)
  }

  // Training set 5. All but meetPos5
  //----------------------------------
  val meetTrainingSet5 = {
    val training = allPosIntervals.filter(x => x!= meetPos5) ++ allNegIntervals.filter(z => !testingNeg5.contains(z))
    val testing = List(meetPos5) ++ testingNeg5
    new DataAsIntervals(trainingSet = List(training.head) ++ Random.shuffle(training.tail), testingSet = testing)
  }

  // Training set 6. All but meetPos6
  //----------------------------------
  val meetTrainingSet6 = {
    val training = allPosIntervals.filter(x => x!= meetPos6) ++ allNegIntervals.filter(z => !testingNeg6.contains(z))
    val testing = List(meetPos6) ++ testingNeg6
    new DataAsIntervals(trainingSet = List(training.head) ++ Random.shuffle(training.tail), testingSet = testing)
  }

  // Training set 7. All but meetPos7
  //----------------------------------
  val meetTrainingSet7 = {
    val training = allPosIntervals.filter(x => x!= meetPos7) ++ allNegIntervals.filter(z => !testingNeg7.contains(z))
    val testing = List(meetPos7) ++ testingNeg7
    new DataAsIntervals(trainingSet = List(training.head) ++ Random.shuffle(training.tail), testingSet = testing)
  }

  // Training set 8. All but meetPos8
  //----------------------------------
  val meetTrainingSet8 = {
    val training = allPosIntervals.filter(x => x!= meetPos8) ++ allNegIntervals.filter(z => !testingNeg8.contains(z))
    val testing = List(meetPos8) ++ testingNeg8
    new DataAsIntervals(trainingSet = List(training.head) ++ Random.shuffle(training.tail), testingSet = testing)
  }

  // Training set 9. All but meetPos9
  //----------------------------------
  val meetTrainingSet9 = {
    val training = allPosIntervals.filter(x => x!= meetPos9) ++ allNegIntervals.filter(z => !testingNeg9.contains(z))
    val testing = List(meetPos9) ++ testingNeg9
    new DataAsIntervals(trainingSet = List(training.head) ++ Random.shuffle(training.tail), testingSet = testing)
  }

  // Training set 10. All but meetPos10
  //----------------------------------
  val meetTrainingSet10 = {
    val training = allPosIntervals.filter(x => x!= meetPos10) ++ allNegIntervals.filter(z => !testingNeg10.contains(z))
    val testing = List(meetPos10) ++ testingNeg10
    new DataAsIntervals(trainingSet = List(training.head) ++ Random.shuffle(training.tail), testingSet = testing)
  }

  val allTrainingSets = List(meetTrainingSet1,meetTrainingSet2,meetTrainingSet3,meetTrainingSet4,meetTrainingSet5,meetTrainingSet6,meetTrainingSet7,meetTrainingSet8,
    meetTrainingSet9,meetTrainingSet10)

  val wholeCAVIARForManualRules = {
    new DataAsIntervals(trainingSet = List(), testingSet = allPosIntervals++allNegIntervals)
  }

  val wholeCAVIARForTraining = {
    new DataAsIntervals(trainingSet = allPosIntervals++allNegIntervals, testingSet = allPosIntervals++allNegIntervals)
  }


  /*
  test code to test the total size of each negative testing interval
    for (x <- allNegativeTestingSetIntervals) {
      val totalLength = x.foldLeft(0)((p,q) => p + q.length)
      println(totalLength)
    }
  */


  val wholeCAVIAR1 = {
    //new TrainingSet(trainingSet = List(allPosIntervals.head) ++ Random.shuffle(allPosIntervals++allNegIntervals), testingSet = allPosIntervals++allNegIntervals)
    new DataAsIntervals(trainingSet = List(allPosIntervals.head) ++ Random.shuffle(allPosIntervals++allNegIntervals), testingSet = List(Interval("meeting",680,1077680)))
    //Interval("meeting",24440,27320)
  }

  object TwoFoldSplit {

    val cores = 2

    val meetTrainingSet1 = getData(meetPos1, testingNeg1, cores)
    val meetTrainingSet2 = getData(meetPos2, testingNeg2, cores)
    val meetTrainingSet3 = getData(meetPos3, testingNeg3, cores)
    val meetTrainingSet4 = getData(meetPos4, testingNeg4, cores)
    val meetTrainingSet5 = getData(meetPos5, testingNeg5, cores)
    val meetTrainingSet6 = getData(meetPos6, testingNeg6, cores)
    val meetTrainingSet7 = getData(meetPos7, testingNeg7, cores)
    val meetTrainingSet8 = getData(meetPos8, testingNeg8, cores)
    val meetTrainingSet9 = getData(meetPos9, testingNeg9, cores)
    val meetTrainingSet10 = getData(meetPos10, testingNeg10, cores)

  }



  object FourFoldSplit {

    val cores = 4

    val meetTrainingSet1 = getData(meetPos1, testingNeg1, cores)
    val meetTrainingSet2 = getData(meetPos2, testingNeg2, cores)
    val meetTrainingSet3 = getData(meetPos3, testingNeg3, cores)
    val meetTrainingSet4 = getData(meetPos4, testingNeg4, cores)
    val meetTrainingSet5 = getData(meetPos5, testingNeg5, cores)
    val meetTrainingSet6 = getData(meetPos6, testingNeg6, cores)
    val meetTrainingSet7 = getData(meetPos7, testingNeg7, cores)
    val meetTrainingSet8 = getData(meetPos8, testingNeg8, cores)
    val meetTrainingSet9 = getData(meetPos9, testingNeg9, cores)
    val meetTrainingSet10 = getData(meetPos10, testingNeg10, cores)
  }

  object SixFoldSplit {
    val cores = 6

    val meetTrainingSet1 = getData(meetPos1, testingNeg1, cores)
    val meetTrainingSet2 = getData(meetPos2, testingNeg2, cores)
    val meetTrainingSet3 = getData(meetPos3, testingNeg3, cores)
    val meetTrainingSet4 = getData(meetPos4, testingNeg4, cores)
    val meetTrainingSet5 = getData(meetPos5, testingNeg5, cores)
    val meetTrainingSet6 = getData(meetPos6, testingNeg6, cores)
    val meetTrainingSet7 = getData(meetPos7, testingNeg7, cores)
    val meetTrainingSet8 = getData(meetPos8, testingNeg8, cores)
    val meetTrainingSet9 = getData(meetPos9, testingNeg9, cores)
    val meetTrainingSet10 = getData(meetPos10, testingNeg10, cores)

  }

  object EightFoldSplit {

    val cores = 8

    val meetTrainingSet1 = getData(meetPos1, testingNeg1, cores)
    val meetTrainingSet2 = getData(meetPos2, testingNeg2, cores)
    val meetTrainingSet3 = getData(meetPos3, testingNeg3, cores)
    val meetTrainingSet4 = getData(meetPos4, testingNeg4, cores)
    val meetTrainingSet5 = getData(meetPos5, testingNeg5, cores)
    val meetTrainingSet6 = getData(meetPos6, testingNeg6, cores)
    val meetTrainingSet7 = getData(meetPos7, testingNeg7, cores)
    val meetTrainingSet8 = getData(meetPos8, testingNeg8, cores)
    val meetTrainingSet9 = getData(meetPos9, testingNeg9, cores)
    val meetTrainingSet10 = getData(meetPos10, testingNeg10, cores)
  }

  object TenFoldSplit {

    val cores = 10

    val meetTrainingSet1 = getData(meetPos1, testingNeg1, cores)
    val meetTrainingSet2 = getData(meetPos2, testingNeg2, cores)
    val meetTrainingSet3 = getData(meetPos3, testingNeg3, cores)
    val meetTrainingSet4 = getData(meetPos4, testingNeg4, cores)
    val meetTrainingSet5 = getData(meetPos5, testingNeg5, cores)
    val meetTrainingSet6 = getData(meetPos6, testingNeg6, cores)
    val meetTrainingSet7 = getData(meetPos7, testingNeg7, cores)
    val meetTrainingSet8 = getData(meetPos8, testingNeg8, cores)
    val meetTrainingSet9 = getData(meetPos9, testingNeg9, cores)
    val meetTrainingSet10 = getData(meetPos10, testingNeg10, cores)
  }




}
