import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.sql._
import org.apache.spark.sql.hive.HiveContext
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.mllib.util.MLUtils
import org.apache.spark.ml.feature.{OneHotEncoder, StringIndexer}
import org.apache.spark.ml.feature.VectorIndexer
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.feature.StandardScaler
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.util.MLUtils
import org.apache.spark.mllib.classification.{SVMModel, SVMWithSGD}
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import scala.math._
import math._
import org.apache.spark.mllib.optimization.L1Updater
import org.apache.spark.storage.StorageLevel._

object GameDataSVM{

  def main(args: Array[String]) {

    //creating the spark, spark-sql contexts
    val startt = System.currentTimeMillis

    val sparkConf = new SparkConf().setAppName("Gaming-Data-SVM")
    val sc = new SparkContext(sparkConf)
        //val sqlContext = new HiveContext(sc)
    val sqlContext = new org.apache.spark.sql.hive.HiveContext(sc)
    //import sqlContext._
    //import sqlContext.implicits
    val df = sqlContext.sql("select cast(if(gender=='male',1,0) as double) as gender,cast(age as double) as age,country,cast(friend_count as double) as friend_count ,cast(lifetime as double) as lifetime,cast(game1 as double) as game1,cast(game2 as double) as game2,cast(game3 as double) as game3,cast(game4 as double) as game4,cast(if(paid_customer=='yes',1,0) as double) as paid_customer from gaming").persist(MEMORY_AND_DISK_SER)
    //df.registerTempTable("gaming")
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

    val indexedData = indexerModel.transform(encoded)

    val parsedData = indexedData.map { row =>
      val features = Vectors.dense(row(0).asInstanceOf[Double], row(1).asInstanceOf[Double], row(10).asInstanceOf[Double], row(3).asInstanceOf[Double], row(4).asInstanceOf[Double], row(5).asInstanceOf[Double], row(6).asInstanceOf[Double], row(7).asInstanceOf[Double], row(8).asInstanceOf[Double])
      LabeledPoint(row(9).asInstanceOf[Double], features)
    }

    //val splits = parsedData.randomSplit(Array(0.9999, 0.0001), seed = 123)
    //val training_nn = splits(0).cache()
    //val test = splits(1)

    val scaler2 = new StandardScaler(withMean = true, withStd = true).fit(parsedData.map(x => x.features))
    val training = parsedData.map(x => LabeledPoint(x.label, scaler2.transform(Vectors.dense(x.features.toArray))))
    //val training = training_ling.map(x =>LabeledPoint())

    val numIterations = 5
    val model = SVMWithSGD.train(training, numIterations)
    //val svmAlg = new SVMWithSGD()
    //svmAlg.optimizer.
    //  setNumIterations(10).
    //  setRegParam(0.1).
    // setUpdater(new L1Updater)
    //val model = svmAlg.run(training)
    //model.save(sc, "/user/gaming/GameDataSVM_250GB_Model")


    // Clear the default threshold.
    //model.clearThreshold()

    // Compute raw scores on the test set.
    //val scoreAndLabels = test.map { point =>
     // val score = model.predict(point.features)
      //(score, point.label)
    //}

    // Get evaluation metrics.
    //val metrics = new BinaryClassificationMetrics(scoreAndLabels)
    //val auROC = metrics.areaUnderROC()

    //println("Area under ROC = " + auROC)
    val elapsed = ((System.currentTimeMillis - startt)/1000)
    println("Ellapsed time = " + elapsed + " seconds")

    sc.stop()

  }
}
