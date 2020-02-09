#!/bin/sh
echo "Starting Spark history server"
/spark/sbin/start-history-server.sh
echo "Starting Spark master"
/spark/sbin/start-master.sh -h 0.0.0.0 -p 7077
echo "Starting Spark worker with max memory set to $SPARK_WORKER_MEMORY"
/spark/sbin/start-slave.sh spark://0.0.0.0:7077 -m $SPARK_WORKER_MEMORY
