lazy val root = (project in file(".")).
  settings(
    name := "GamingDataStreaming",
    version := "1.0",
    scalaVersion := "2.10.1",
    mainClass in Compile := Some("GamingDataStreaming")        
  )

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "1.6.0" % "provided",
  "org.apache.spark" %% "spark-sql" % "1.6.0" % "provided",
  "org.apache.spark" %% "spark-hive" % "1.6.0" % "provided",
  "org.apache.spark" %% "spark-mllib" % "1.6.0" % "provided",
  "org.apache.spark" %% "spark-streaming" % "1.6.0" % "provided",
  "org.apache.spark" % "spark-streaming-kafka_2.10" % "1.6.0",
  "org.apache.kafka" % "kafka_2.10" % "0.8.2",
  "au.com.bytecode" % "opencsv" % "2.4" 
)

// META-INF discarding
mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
   {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case x => MergeStrategy.first
   }
}

assemblyJarName in assembly := "gamingdatastreaming.jar"
