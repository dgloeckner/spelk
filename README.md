## Attribution and Motivation
This project is a modernized version of Spelk which was published by IBM on GitHub under
an Apache 2 license. The license permits derived work and as such this project was created
to make Spelk compatible with recent Elasticsearch versions.

The main issue with the IBM version of Spelk is that it only works with older Elasticsearch versions.
Elasticsearch has changed its way how types in indexes work (only one type per index). This
project provides Spelk working against an Elasticsearch 7 instance.

In addition, a few minor extensions have been implemented including capturing of fully qualified hostname,
process IDs and using rolling indexes (i.e. one index per day).

See: https://github.com/dgloeckner/spelk
See: https://github.com/ibmruntimes/spelk

# Spelk - reporting Apache Spark metrics to Elasticsearch

Spelk (spark-elk) is an add-on to Apache Spark (<http://spark.apache.org/>) to report metrics to an Elasticsearch server and allow visualizations using Kibana.

## Building Spelk

Spelk is built using [Apache Maven](http://maven.apache.org/). You can specify the Spark and Scala versions.

    mvn -Dspark.version=2.0.0 -Dscala.binary.version=2.11 clean package


## Configuration

To use Spelk you need to add the Spelk jar to the Spark driver/executor classpaths and enable the metrics sink.


**Add Spelk jar to classpaths**

Update [spark home]/conf/spark-defaults.conf eg.

	spark.driver.extraClassPath=<path to Spelk>/spelk-0.1.0.jar
	spark.executor.extraClassPath=<path to Spelk>/spelk-0.1.0.jar


 
**Enable the Elasticsearch sink**

Update metrics.properties in [spark home]/conf/metrics.properties eg.

	# org.apache.spark.elk.metrics.sink.ElasticsearchSink
	driver.sink.elk.class=org.apache.spark.elk.metrics.sink.ElasticsearchSink
	executor.sink.elk.class=org.apache.spark.elk.metrics.sink.ElasticsearchSink
	# You may enable the ElasticsearchSink for all components also:
	# *.sink.elk.class=org.apache.spark.elk.metrics.sink.ElasticsearchSink
	#   Name:     Default:      Description:
	#   host      none          Elasticsearch server host	
	#   port      none          Elasticsearch server port 
	#   index     spark         Elasticsearch index name
	#   period    10            polling period
	#   units     seconds       polling period units
	*.sink.elk.host=localhost
	*.sink.elk.port=9200
	# Index name supports a formatting pattern to format the current date.
	*.sink.elk.index=spark-%tF
	*.sink.elk.period=10
	*.sink.elk.unit=seconds
.
**Run Elasticsearch for testing**

`docker run -p 9200:9200 -p 9300:9300 -e "discovery.type=single-node" docker.elastic.co/elasticsearch/elasticsearch:7.4.1
