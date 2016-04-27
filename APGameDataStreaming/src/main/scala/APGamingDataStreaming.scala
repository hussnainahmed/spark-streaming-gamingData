//package main.scala

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.sql._
import org.apache.spark.sql.hive.HiveContext
import org.apache.spark.rdd.RDD
import java.util.HashMap
import org.apache.spark.util.IntParam
import org.apache.spark.sql.SQLContext
import java.io.StringReader
import au.com.bytecode.opencsv.CSVReader
import kafka.serializer.StringDecoder

import org.apache.spark.streaming._
import org.apache.spark.streaming.kafka._
import org.apache.spark.SparkConf

import org.apache.spark.ml.feature.{OneHotEncoder, StringIndexer}
import org.apache.spark.ml.feature.VectorIndexer
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.feature.StandardScaler
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.util.MLUtils
import org.apache.spark.mllib.classification.{SVMModel, SVMWithSGD}
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import scala.math._
import math._
import org.apache.spark.mllib.optimization.L1Updater


object SQLContextSingleton {

  @transient  private var instance: SQLContext = _

 def getInstance(sparkContext: SparkContext): SQLContext = {
    if (instance == null) {
      instance = new HiveContext(sparkContext)
    }
    instance
  }
}
case class GameData(cid: String, cname: String, email: String, gender: String, age: Int, address: String, country: String, register_date: String, friend_count: Int, lifetime: Int, game1: Int, game2: Int, game3: Int, game4: Int, revenue: Int, paid_customer: String)

object APGamingDataStreaming{

  def main(args: Array[String]) {

    if (args.length < 2) {
      System.err.println("Usage: GameDataStreaming <topics> <numThreads>")
      System.exit(1)
    }

//    case class GameData(cid: String, cname: String, email: String, gender: String, age: Int, address: String, country: String, register_date: String, friend_count: Int, lifetime: Int, game1: Int, game2: Int, game3: Int, game4: Int, revenue: Int, paid_customer: String)


    val Array(topics, ratebalance,maxRate, numThreads) = args

    val sparkConf = new SparkConf().setAppName("Game Data Streaming").set("spark.streaming.backpressure.enabled", ratebalance).set("spark.streaming.receiver.maxRate", maxRate)
    val sc = new SparkContext(sparkConf)

    val ssc = new StreamingContext(sc, Seconds(30))

    //val topicsSet = topics.split(",").toSet
    //val kafkaParams = Map[String, String]("metadata.broker.list" -> brokers)

    //val messages = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](
      //ssc, kafkaParams, topicsSet)

    val zkQuorum = "adp-base.novalocal,adp-node-1.novalocal,adp-node-2.novalocal"
    //val kafkaParams = Map[String, String]("metadata.broker.list" -> brokers)


    val topicMap = topics.split(",").map((_, numThreads.toInt)).toMap
    //val topicsSet = topics.split(",").toSet
    //val kafkaParams = Map[String, String]("metadata.broker.list" -> brokers)
    //val messages = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, kafkaParams, topicsSet)
    val lines = KafkaUtils.createStream(ssc, zkQuorum, "gaming-stream", topicMap).map(_._2)
    //val lines = messages.map(_._2)


    lines.foreachRDD((rdd: RDD[String]) => {
      val sqlContext = SQLContextSingleton.getInstance(rdd.sparkContext)
      import sqlContext.implicits._

      val game_df =  rdd.map(_.split(",")).map(x => GameData(x(0).toString, x(1).toString, x(2).toString, x(3).toString, x(4).toInt, x(5).toString, x(6).toString, x(7).toString, x(8).toInt, x(9).toInt, x(10).toInt, x(11).toInt, x(12).toInt, x(13).toInt, x(14).toInt, x(15).toString)).toDF().cache()
      // Register as table
      game_df.registerTempTable("gaming_stream")
      val rep_df = sqlContext.sql("select cast(if(gender=='male',1,0) as double) as gender,cast(age as double) as age,country,cast(friend_count as double) as friend_count ,cast(lifetime as double) as lifetime,cast(game1 as double) as game1,cast(game2 as double) as game2,cast(game3 as double) as game3,cast(game4 as double) as game4,cast(if(paid_customer=='yes',1,0) as double) as paid_customer from gaming_stream")
      rep_df.registerTempTable("gaming_rep")
      val df = sqlContext.sql("select * from gaming_rep group by gender,age,country,friend_count,lifetime,game1,game2,game3,game4,paid_customer")

      val indexer = new StringIndexer()
        .setInputCol("country")
        .setOutputCol("country_index")
        .fit(df)
      val indexed = indexer.transform(df)

      val encoder = new OneHotEncoder()
        .setInputCol("country_index")
        .setOutputCol("countryVec")
      val encoded = encoder.transform(indexed)
      //encoded.select("cid","cname","gender","age","countryVec","friend_count","game1","game2","game3","game4","paid_customer")

      val vec_indexer = new VectorIndexer()
        .setInputCol("countryVec")
        .setOutputCol("country_indexed")
        .setMaxCategories(7)

      val indexerModel = vec_indexer.fit(encoded)

      val categoricalFeatures: Set[Int] = indexerModel.categoryMaps.keys.toSet
      println(s"Chose ${categoricalFeatures.size} categorical features: " +
        categoricalFeatures.mkString(", "))

      // Create new column "indexed" with categorical values transformed to indices
      val indexedData = indexerModel.transform(encoded)

      val test = indexedData.map { row =>
        val features = Vectors.dense(row(0).asInstanceOf[Double], row(1).asInstanceOf[Double], row(10).asInstanceOf[Double], row(3).asInstanceOf[Double], row(4).asInstanceOf[Double], row(5).asInstanceOf[Double], row(6).asInstanceOf[Double], row(7).asInstanceOf[Double], row(8).asInstanceOf[Double])
        LabeledPoint(row(9).asInstanceOf[Double], features)
      }

      val model = SVMModel.load(rdd.sparkContext, "/user/gaming/SVM_Model_Gaming_L1_250GB")

      //model.clearThreshold()

      val scoreAndLabels = test.map { point =>
        val score = model.predict(point.features)
        (score, point.label)
      }

      // Get evaluation metrics.
      val metrics = new BinaryClassificationMetrics(scoreAndLabels)
      val auROC = metrics.areaUnderROC()

      println("Area under ROC = " + auROC)

      scoreAndLabels.toDF("score","label").show()

    })
    
    ssc.start()
    ssc.awaitTermination()

  }
}


