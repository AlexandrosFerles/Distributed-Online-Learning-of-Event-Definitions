package oled.whole_caviar_data

import utils.DataUtils.{DataAsIntervals, Interval}
import scala.util.Random
/**
  * Created by ferles on 25/5/2017.
  */
object MeetingFrames {

  def splitN[A](xs: List[A], n: Int) = {
    val (quot, rem) = (xs.size / n, xs.size % n)
    val (smaller, bigger) = xs.splitAt(xs.size - rem * (quot + 1))
    smaller.grouped(quot) ++ bigger.grouped(quot + 1)
  }

  def getData(testingPos: Interval, testingNegs: List[Interval], coresNum: Int) = {

    def isInTrainingSet(x: Interval) = x != testingPos

    var allowedPos = allPosIntervals.filter(x => x!= testingPos)
    
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

  val meeting = "meeting"

  val frame1 =Interval("meeting",680,24400) //593
  val frame2 =Interval("meeting",24440,66600) //1054
  val frame3 =Interval("meeting",68040,121760) //1343
  val frame4 =Interval("meeting",121800,163480) //1042
  val frame5 =Interval("meeting",163520,197840) //858
  val frame6 =Interval("meeting",197880,231760) //847
  val frame7 =Interval("meeting",231800,275360) //1089
  val frame8 =Interval("meeting",275720,307000) //782
  val frame9 =Interval("meeting",339840,382800) //1074
  val frame10 =Interval("meeting",382840,423080) //1006
  val frame11 =Interval("meeting",423120,459520) //910
  val frame12 =Interval("meeting",459840,507080) //1181
  val frame13 =Interval("meeting",507120,547360) //1006
  val frame14 =Interval("meeting",547400,604200) //1420
  val frame15 =Interval("meeting",604240,648800) //1114
  val frame16 =Interval("meeting",648840,691480) //1066
  val frame17 =Interval("meeting",691520,726000) //862
  val frame18 =Interval("meeting",726040,772080) //1151
  val frame19 =Interval("meeting",777600,800360) //569    //MEETING
  val frame20 =Interval("meeting",800400,833440) //826    //MEETING
  val frame21 =Interval("meeting",833480,858360) //622    //MEETING
  val frame22 =Interval("meeting",860400,895280) //872    //MEETING
  val frame23 =Interval("meeting",896560,910360) //345    //MEETING
  val frame24 =Interval("meeting",910400,932440) //551    //MEETING
  val frame25 =Interval("meeting",932480,954480) //550
  val frame26 =Interval("meeting",954520,976520) //550
  val frame27 =Interval("meeting",976560,1014880) //958
  val frame28 =Interval("meeting",1014920,1053240) //958
  val frame29 =Interval("meeting",1053280,1085360) //802
  val frame30 =Interval("meeting",1085400,1102600) //430
  
  val allIntervals = List(frame1 ,frame2 ,frame3 ,frame4 ,frame5 ,frame6 ,frame7 ,frame8 ,frame9 ,frame10 ,frame11 ,
    frame12 ,frame13 ,frame14 ,frame15 ,frame16 ,frame17 ,frame18 ,frame19 ,frame20 ,frame21 ,frame22 ,frame23 ,
    frame24 ,frame25 ,frame26 ,frame27 ,frame28 ,frame29)


  val allPosIntervals = List(frame19,frame20,frame21,frame22,frame23,frame24)
  val allNegIntervals = allIntervals.filter( x => !allPosIntervals.contains(x))


  val meetingFrames=List(frame1,frame2,frame3,frame4,frame5,frame6,frame7,frame8,frame9,frame10,frame11,frame12,
    frame13,frame14,frame15,frame16,frame17,frame18,frame19,frame20,frame21,frame22,
    frame23,frame24,frame25,frame26,frame27,frame28,frame29,frame30)

  //testingSets
  val testingFrame1 = List(frame19,frame1,frame2 )
  val testingFrame2 = List(frame20,frame3,frame4 )
  val testingFrame3 = List(frame21,frame5,frame6)
  val testingFrame4 = List(frame22,frame7,frame8 )
  val testingFrame5 = List(frame23,frame9,frame10 )
  val testingFrame6 = List(frame24,frame11,frame12 )
  
  val trainingFrame1= meetingFrames.filter( x => !testingFrame1.contains(x))
  val trainingFrame2= meetingFrames.filter( x => !testingFrame2.contains(x))
  val trainingFrame3= meetingFrames.filter( x => !testingFrame3.contains(x))
  val trainingFrame4= meetingFrames.filter( x => !testingFrame4.contains(x))
  val trainingFrame5= meetingFrames.filter( x => !testingFrame5.contains(x))
  val trainingFrame6= meetingFrames.filter( x => !testingFrame6.contains(x))

  val meetTrainingSet1 = {
    val training = trainingFrame1
    val testing = testingFrame1
    new DataAsIntervals(trainingSet = List(training.head) ++ Random.shuffle(training.tail), testingSet = testing)
  }
  val meetTrainingSet2 = {
    val training = trainingFrame2
    val testing = testingFrame2
    new DataAsIntervals(trainingSet = List(training.head) ++ Random.shuffle(training.tail), testingSet = testing)
  }
  val meetTrainingSet3 = {
    val training = trainingFrame3
    val testing = testingFrame3
    new DataAsIntervals(trainingSet = List(training.head) ++ Random.shuffle(training.tail), testingSet = testing)
  }
  val meetTrainingSet4 = {
    val training = trainingFrame4
    val testing = testingFrame4
    new DataAsIntervals(trainingSet = List(training.head) ++ Random.shuffle(training.tail), testingSet = testing)
  }
  val meetTrainingSet5 = {
    val training = trainingFrame5
    val testing = testingFrame5
    new DataAsIntervals(trainingSet = List(training.head) ++ Random.shuffle(training.tail), testingSet = testing)
  }
  val meetTrainingSet6 = {
    val training = trainingFrame6
    val testing = testingFrame6
    new DataAsIntervals(trainingSet = List(training.head) ++ Random.shuffle(training.tail), testingSet = testing)
  }

  def main(args: Array[String]) = {
    /*
    * Just for testing-debugging
    *
    * */
    ///*
    val eight = EightFoldSplit.meetTrainingSet1
    val four = FourFoldSplit.meetTrainingSet1
    val two = TwoFoldSplit.meetTrainingSet1

    println(eight)
    println("")
    println(four)
    println("")
    println(two)
    //*/
    //println(splitInterval(meetPos1, 4))
    //println(Interval("meeting",5720,24480) == meetPos1)
  }

  val testingNeg1 = allNegIntervals.filter( x => !meetTrainingSet1.trainingSet.contains(x))
  val testingNeg2= allNegIntervals.filter( x =>  !meetTrainingSet2.trainingSet.contains(x))
  val testingNeg3= allNegIntervals.filter( x =>  !meetTrainingSet3.trainingSet.contains(x))
  val testingNeg4= allNegIntervals.filter( x =>  !meetTrainingSet4.trainingSet.contains(x))
  val testingNeg5= allNegIntervals.filter( x =>  !meetTrainingSet5.trainingSet.contains(x))
  val testingNeg6= allNegIntervals.filter( x =>  !meetTrainingSet6.trainingSet.contains(x))






  object TwoFoldSplit {

    val cores = 2

    val meetTrainingSet1 = getData(frame19, testingNeg1, cores)
    val meetTrainingSet2 = getData(frame20, testingNeg2, cores)
    val meetTrainingSet3 = getData(frame21, testingNeg3, cores)
    val meetTrainingSet4 = getData(frame22, testingNeg4, cores)
    val meetTrainingSet5 = getData(frame23, testingNeg5, cores)
    val meetTrainingSet6 = getData(frame24, testingNeg6, cores)

  }



  object FourFoldSplit {

    val cores = 4

    val meetTrainingSet1 = getData(frame19, testingNeg1, cores)
    val meetTrainingSet2 = getData(frame20, testingNeg2, cores)
    val meetTrainingSet3 = getData(frame21, testingNeg3, cores)
    val meetTrainingSet4 = getData(frame22, testingNeg4, cores)
    val meetTrainingSet5 = getData(frame23, testingNeg5, cores)
    val meetTrainingSet6 = getData(frame24, testingNeg6, cores)
  }

  object SixFoldSplit {
    val cores = 6

    val meetTrainingSet1 = getData(frame19, testingNeg1, cores)
    val meetTrainingSet2 = getData(frame20, testingNeg2, cores)
    val meetTrainingSet3 = getData(frame21, testingNeg3, cores)
    val meetTrainingSet4 = getData(frame22, testingNeg4, cores)
    val meetTrainingSet5 = getData(frame23, testingNeg5, cores)
    val meetTrainingSet6 = getData(frame24, testingNeg6, cores)

  }

  object EightFoldSplit {

    val cores = 8

    val meetTrainingSet1 = getData(frame19, testingNeg1, cores)
    val meetTrainingSet2 = getData(frame20, testingNeg2, cores)
    val meetTrainingSet3 = getData(frame21, testingNeg3, cores)
    val meetTrainingSet4 = getData(frame22, testingNeg4, cores)
    val meetTrainingSet5 = getData(frame23, testingNeg5, cores)
    val meetTrainingSet6 = getData(frame24, testingNeg6, cores)
  }

  object TenFoldSplit {

    val cores = 10

    val meetTrainingSet1 = getData(frame19, testingNeg1, cores)
    val meetTrainingSet2 = getData(frame20, testingNeg2, cores)
    val meetTrainingSet3 = getData(frame21, testingNeg3, cores)
    val meetTrainingSet4 = getData(frame22, testingNeg4, cores)
    val meetTrainingSet5 = getData(frame23, testingNeg5, cores)
    val meetTrainingSet6 = getData(frame24, testingNeg6, cores)
  }

}
