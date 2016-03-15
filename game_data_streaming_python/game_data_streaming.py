from __future__ import division

import os
import sys
from pyspark import SparkContext, SparkConf
from pyspark.streaming import StreamingContext
from pyspark.sql import SQLContext, Row
from pyspark import SparkContext
from pyspark.streaming import StreamingContext
from pyspark.sql import HiveContext
from pyspark.streaming.kafka import KafkaUtils
#import time
#import datetime
#from datetime import *
#import pytz
#from pytz import *
import json
import csv
#from pyspark.streaming.kafka import KafkaUtils
import StringIO
#from sqlalchemy import create_engine
#import pandas
#import MySQLdb
from pyspark.ml.feature import StringIndexer
from pyspark.ml.feature import OneHotEncoder
from pyspark.ml.feature import VectorIndexer
from pyspark.mllib.classification import SVMWithSGD, SVMModel
from pyspark.mllib.regression import LabeledPoint
from pyspark.mllib.linalg import SparseVector, DenseVector
from pyspark.mllib.linalg import Vectors
from pyspark.ml.feature import VectorAssembler
from pyspark.ml.feature import RFormula
from pyspark.ml import Pipeline
from pyspark.sql import Row


def getSqlContextInstance(sparkContext):
    if ('sqlContextSingletonInstance' not in globals()):
        globals()['sqlContextSingletonInstance'] = HiveContext(sparkContext)
    return globals()['sqlContextSingletonInstance']



def loadGaming(input):
    """Parse a CSV line"""
    input = StringIO.StringIO(input)
    reader = csv.DictReader(input, fieldnames=["cid", "cname", "email", "gender", "age", "address","country", "register_date", "friend_count", "lifetime", "game1","game2","game3","game4","revenue","paid_customer"])
    return reader.next()




def process(rdd):
    try:
        sqlContext = getSqlContextInstance(rdd.context)
        game_df = rdd.map(loadGaming).toDF()
        game_df.registerTempTable("gaming_stream")
        df = sqlContext.sql("select cast(if(gender=='male',1,0) as double) as gender,\
                            cast(age as double) as age,\
                            country,cast(friend_count as double) as friend_count,\
                            cast(lifetime as double) as lifetime,\
                            cast(game1 as double) as game1,\
                            cast(game2 as double) as game2,\
                            cast(game3 as double) as game3,\
                            cast(game4 as double) as game4,\
                            cast(if(paid_customer=='yes',1,0) as double) as paid_customer \
                             from gaming_stream")
        indexer = StringIndexer(inputCol="country", outputCol="countryIndex")
        indexed = indexer.fit(df).transform(df)
        encoder = OneHotEncoder(dropLast=False, inputCol="countryIndex", outputCol="countryVec")
        encoded = encoder.transform(indexed)
        #encoded.show()
        #formula = RFormula(
        #        formula="paid_customer ~ gender + age + country + lifetime + game1 + game2 + game3 + game4",
        #        featuresCol="features",
        #        labelCol="label")
        #encoded = formula.fit(df).transform(df)
        #encoded.show()
        test= encoded.map(lambda x: LabeledPoint( x[9], Vectors.dense(x[0],x[1],x[10],x[3],x[4],x[5],x[6],x[7],x[8])))
        #model = SVMModel.load(sc, "")
        model = SVMModel.load(rdd.context, "/user/gaming/SVM_Model_Gaming_test2")

        labelsAndPreds = test.map(lambda p: (p.label, model.predict(p.features)))
        trainErr = labelsAndPreds.filter(lambda (v, p): v != p).count() / float(test.count())
        print("Training Error = " + str(trainErr))
        labelsAndPreds.toDF("label","predicted").show()
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
    ssc = StreamingContext(sc, 30)
    zkQuorum = "cdh-node-2.novalocal:2181,cdh-node-6.novalocal:2181,cdh-node-9.novalocal:2181"
    #brokers = "cdh-base.novalocal:9020,cdh-node-1.novalocal:9020,cdh-node-2.novalocal:9020,cdh-node-3.novalocal:9020"
    #brokers = "cdh-base.novalocal:9020"
    #topic = "energy-stream"
    #topic = "datagen2"
    kvs = KafkaUtils.createStream(ssc, zkQuorum, "gaming-data", {topic:10} )
    #kvs = KafkaUtils.createDirectStream(ssc, [topic], {"metadata.broker.list": brokers})
    #input = ssc.textFileStream("/user/flume/tweet-stream/%s" % tdate)
    #brokers, topic = sys.argv[1:]
    #kvs = KafkaUtils.createDirectStream(ssc, [topic], {"metadata.broker.list": brokers})
    input =  kvs.map(lambda x: x[1])
    input.foreachRDD(process)
    ssc.start()
    ssc.awaitTermination()