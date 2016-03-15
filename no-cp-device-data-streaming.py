from __future__ import division

import os
import sys
from pyspark import SparkContext, SparkConf
from pyspark.streaming import StreamingContext
from pyspark.sql import SQLContext, Row
from pyspark import SparkContext
from pyspark.streaming import StreamingContext
from pyspark.sql import HiveContext
import org.apache.spark.ml.classification.LogisticRegression
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.feature.StandardScaler
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.util.MLUtils
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.mllib.classification.{LogisticRegressionWithLBFGS, LogisticRegressionModel}
import org.apache.spark.mllib.evaluation.MulticlassMetrics
import org.apache.spark.ml.feature.{OneHotEncoder, StringIndexer}
import org.apache.spark.ml.feature.VectorIndexer
#import time
#import datetime
#from datetime import *
#import pytz
#from pytz import *
import json
import csv
#from pyspark.streaming.kafka import KafkaUtils
from pyspark.streaming.kafka import KafkaUtils
import StringIO
#from sqlalchemy import create_engine
#import pandas
#import MySQLdb

def getSqlContextInstance(sparkContext):
    if ('sqlContextSingletonInstance' not in globals()):
        globals()['sqlContextSingletonInstance'] = HiveContext(sparkContext)
    return globals()['sqlContextSingletonInstance']



def loadEnergy(input):
    """Parse a CSV line"""
    input = StringIO.StringIO(input)
    reader = csv.DictReader(input, fieldnames=["cid", "cname", "email", "gender", "age", "address","country", "register_date", "friend_count", "lifetime", "game1","game2","game3","game4","revenue","paid_customer"])
    return reader.next()




def process(rdd):
    try:
        sqlContext = getSqlContextInstance(rdd.context)
        wordsDataFrame = rdd.map(loadEnergy).toDF()
        #wordsDataFrame = sqlContext.jsonRDD(rdd)
	    wordsDataFrame.registerTempTable("ts")
        val df = sqlContext.sql("select cast(if(gender=='male',1,0) as double) as gender,\
        cast(age as double) as age,\
        country,cast(friend_count as double) as friend_count,\
        cast(lifetime as double) as lifetime,cast(game1 as double) as game1,\
        cast(game2 as double) as game2,\
        cast(game3 as double) as game3,\
        cast(game4 as double) as game4,\
        cast(if(paid_customer=='yes',1,0) as double) as paid_customer from ts")

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

val vector_indexer = new VectorIndexer()
  .setInputCol("countryVec")
  .setOutputCol("country_indexed")
  .setMaxCategories(7)

val indexerModel = vector_indexer.fit(encoded)

val categoricalFeatures: Set[Int] = indexerModel.categoryMaps.keys.toSet
println(s"Chose ${categoricalFeatures.size} categorical features: " +
  categoricalFeatures.mkString(", "))

val indexedData = indexerModel.transform(encoded)





        #cnt = rdd.count()
        #cur_rate = cnt/30
        #pti_text = sqlContext.sql("select text,created_at from ts where text IS NOT NULL and lang=\'en\'")
        #2015-05-25 13:18:25
        #df = sqlContext.sql("select from_unixtime(timestamp,'yyyy-MM-dd HH:mm:00') as timestamp,uid,sum(consumption) as comsumption from ts group by timestamp,uid order by timestamp")
        #df = sqlContext.sql("select dev,sum(consumption) as comsumption from ts group by dev order by dev")
        #df = sqlContext.sql("select from_unixtime(unix_timestamp(regexp_replace(timestamp,'T||||||||||||Z',''),'yyyy-MM-ddHH:mm:ss.SSS'),'yyyy-MM-dd HH:mm') as timestamp,uid,sum(consumption) as comsumption from ts group by timestamp,uid order by timestamp desc")
	#print "Current Rate = %f records / second" % cur_rate
        df.show()
        #print cnt
    except Exception as e: print(e)



if __name__ == "__main__":
    #sc = SparkContext(appName="DeviceData_StreamingApp")   # new context
    ratebalance, maxRate, topic = sys.argv[1:]
    #maxRate = sys.argv[3]
    #ratebalance = sys.argv[2]
    #conf = SparkConf().setAppName("DDSApp-no-cp").set("spark.streaming.receiver.maxRate", maxRate)
    conf = SparkConf().setAppName("DeviceData_StreamingApp").set("spark.streaming.backpressure.enabled", ratebalance).set("spark.streaming.receiver.maxRate", maxRate)
    sc = SparkContext(conf=conf)
    #tdate = datetime.now(pytz.timezone('Asia/Karachi')).strftime("%y-%m-%d")
    ssc = StreamingContext(sc, 60)
    zkQuorum = "cdh-node-1.novalocal:2181,cdh-node-2.novalocal:2181,cdh-node-3.novalocal:2181"
    #brokers = "cdh-base.novalocal:9020,cdh-node-1.novalocal:9020,cdh-node-2.novalocal:9020,cdh-node-3.novalocal:9020"
    #brokers = "cdh-base.novalocal:9020"
    #topic = "energy-stream"
    #topic = "datagen2"
    kvs = KafkaUtils.createStream(ssc, zkQuorum, "device-dds", {topic:10} )
    #kvs = KafkaUtils.createDirectStream(ssc, [topic], {"metadata.broker.list": brokers})
    #input = ssc.textFileStream("/user/flume/tweet-stream/%s" % tdate)
    #brokers, topic = sys.argv[1:]
    #kvs = KafkaUtils.createDirectStream(ssc, [topic], {"metadata.broker.list": brokers})
    input =  kvs.map(lambda x: x[1])
    input.foreachRDD(process)
    ssc.start()
    ssc.awaitTermination()
