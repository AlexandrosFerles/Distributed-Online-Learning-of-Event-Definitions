clear
rm oled.jar
sbt assembly 
mv target/scala-2.11/oled.jar .
java -cp oled.jar:/lib/jep-3.6.0.jar -Djava.library.path=/home/ferles/setups/jep-master/lib/python/jep app.OLEDRunner "/home/ferles/dOLED_Tests/Meeting/Test1" meeting 6 old
