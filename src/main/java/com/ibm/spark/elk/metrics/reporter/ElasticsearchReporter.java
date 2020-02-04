/*
 * This class was modified so the IBM copyright was removed.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.spark.elk.metrics.reporter;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkEnv;
import org.apache.spark.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")

public class ElasticsearchReporter extends ScheduledReporter {

	private final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchReporter.class);

	private final Clock clock;
	private final String timestampField;
	private final CloseableHttpClient httpClient;
	private final String indexNamePattern;
	private final String mainClass;
	private final String processId;
	private final SimpleDateFormat timestampFormat;
	private final String baseUrl;

	private String localhost;
	private String appName;
	private String appId;
	private String executorId;

	private final JsonFactory jsonFactory;

	private ElasticsearchReporter(MetricRegistry registry, MetricFilter filter, TimeUnit rateUnit,
			TimeUnit durationUnit, String host, String port, String path, String indexNamePattern,
			String timestampField) {
		super(registry, "elasticsearch-reporter", filter, rateUnit, durationUnit);
		this.clock = Clock.defaultClock();
		this.httpClient = HttpClients.createDefault();
		this.baseUrl = "http://" + host + ":" + port + "/" + (path.isEmpty() ? "" : path + "/");
		this.timestampField = timestampField;
		this.timestampFormat = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss.SSSZ");
		this.localhost = Utils.localCanonicalHostName();
		this.indexNamePattern = indexNamePattern;
		this.mainClass = getJavaMainClass();
		this.processId = ManagementFactory.getRuntimeMXBean().getName();
		this.jsonFactory = new JsonFactory();
	}

	@Override
	public void close() {
		super.close();
		try {
			this.httpClient.close();
		} catch (IOException e) {
			LOGGER.error("Error while closing http client", e);
		}
	}

	@Override
	public void report(SortedMap<String, Gauge> gauges, SortedMap<String, Counter> counters,
			SortedMap<String, Histogram> histograms, SortedMap<String, Meter> meters, SortedMap<String, Timer> timers) {
		try {
			final long timestamp = clock.getTime();
			Date now = new Date(timestamp);
			String timestampString = timestampFormat.format(now);
			String indexName =
					indexNamePattern.contains("%") ? String.format(indexNamePattern, now) : indexNamePattern;
			String connectionUrl = baseUrl + indexName + "/";
			initializeAppInfo();
			String json = produceJsonFromMetrics(indexName, gauges, counters, histograms, meters, timers,
					timestampString);
			performBulkInsert(connectionUrl, json);
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(json);
			}
		} catch (Exception ex) {
			LOGGER.error("Exception posting to Elasticsearch index", ex);
		}

	}

	private String produceJsonFromMetrics(String indexName, SortedMap<String, Gauge> gauges,
			SortedMap<String, Counter> counters, SortedMap<String, Histogram> histograms,
			SortedMap<String, Meter> meters, SortedMap<String, Timer> timers, String timestampString)
			throws IOException {
		StringWriter stringWriter = new StringWriter();
		JsonGenerator jsonGenerator = jsonFactory.createGenerator(stringWriter);
		ObjectMapper objectMapper = new ObjectMapper();
		jsonGenerator.setCodec(objectMapper);

		if (!gauges.isEmpty()) {
			for (Entry<String, Gauge> entry : gauges.entrySet()) {
				reportGauge(indexName, jsonGenerator, entry, timestampString);
			}
		}

		if (!counters.isEmpty()) {
			for (Entry<String, Counter> entry : counters.entrySet()) {
				reportCounter(indexName, jsonGenerator, entry, timestampString);
			}
		}

		if (!histograms.isEmpty()) {
			for (Entry<String, Histogram> entry : histograms.entrySet()) {
				reportHistogram(indexName, jsonGenerator, entry, timestampString);
			}
		}

		if (!meters.isEmpty()) {
			for (Entry<String, Meter> entry : meters.entrySet()) {
				reportMeter(indexName, jsonGenerator, entry, timestampString);
			}
		}

		if (!timers.isEmpty()) {
			for (Entry<String, Timer> entry : timers.entrySet()) {
				reportTimer(indexName, jsonGenerator, entry, timestampString);
			}
		}
		// Add final empty line to bulk request.
		stringWriter.write('\n');
		stringWriter.write('\n');

		return stringWriter.getBuffer().toString();
	}

	private void initializeAppInfo() {
		if (appName == null && SparkEnv.get() != null) {
			SparkConf conf = SparkEnv.get().conf();
			appName = conf.get("spark.app.name", "");
			appId = conf.getAppId();
			executorId = conf.get("spark.executor.id", null);
		}
	}

	private void performBulkInsert(String connectionUrl, String json) throws IOException {
		HttpPut put = new HttpPut(connectionUrl + "_bulk");
		put.setHeader("Accept", "application/json");
		put.setHeader("Content-type", "application/json");
		put.setEntity(new StringEntity(json, "UTF-8"));
		try (CloseableHttpResponse result = httpClient.execute(put)) {
			if (HttpStatus.SC_OK != result.getStatusLine().getStatusCode()) {
				LOGGER.warn("Failed to write metric to Elasticsearch to " + connectionUrl + " " + result
						.getStatusLine() + " " + result.getStatusLine().getReasonPhrase());
			} else {
				LOGGER.debug("Request was posted.");
			}
		}
	}

	private void reportGauge(String indexName, JsonGenerator jsonGenerator,
			Map.Entry<String, Gauge> entry, String timestampString) {
		try {

			writeStartMetric(entry.getKey(), indexName, jsonGenerator, timestampString);
			jsonGenerator.writeObject((entry.getValue().getValue()));
			writeEndMetric(jsonGenerator);

		} catch (IOException ioe) {
			LOGGER.error("Exception writing metrics to Elasticsearch index: " + ioe.toString());
		}

	}

	private void reportCounter(String indexName, JsonGenerator jsonGenerator,
			Entry<String, Counter> entry, String timestampString) {
		try {

			writeStartMetric(entry.getKey(), indexName, jsonGenerator, timestampString);
			jsonGenerator.writeNumber((entry.getValue().getCount()));
			writeEndMetric(jsonGenerator);

		} catch (IOException ioe) {
			LOGGER.error("Exception writing metrics to Elasticsearch index: " + ioe.toString());
		}

	}

	private void reportHistogram(String indexName, JsonGenerator jsonGenerator,
			Entry<String, Histogram> entry, String timestampString) {
		try {
			writeStartMetric(entry.getKey(), indexName, jsonGenerator, timestampString);
			jsonGenerator.writeStartObject();
			final Histogram histogram = entry.getValue();
			final Snapshot snapshot = histogram.getSnapshot();
			jsonGenerator.writeNumberField("count", histogram.getCount());
			jsonGenerator.writeNumberField("min", convertDuration(snapshot.getMin()));
			jsonGenerator.writeNumberField("max", convertDuration(snapshot.getMax()));
			jsonGenerator.writeNumberField("mean", convertDuration(snapshot.getMean()));
			jsonGenerator.writeNumberField("stddev", convertDuration(snapshot.getStdDev()));
			jsonGenerator.writeNumberField("median", convertDuration(snapshot.getMedian()));
			jsonGenerator.writeNumberField("75th_percentile", convertDuration(snapshot.get75thPercentile()));
			jsonGenerator.writeNumberField("95th_percentile", convertDuration(snapshot.get95thPercentile()));
			jsonGenerator.writeNumberField("98th_percentile", convertDuration(snapshot.get98thPercentile()));
			jsonGenerator.writeNumberField("99th_percentile", convertDuration(snapshot.get99thPercentile()));
			jsonGenerator.writeNumberField("999th_percentile", convertDuration(snapshot.get999thPercentile()));

			jsonGenerator.writeEndObject();
			writeEndMetric(jsonGenerator);

		} catch (IOException ioe) {
			LOGGER.error("Exception writing metrics to Elasticsearch index: " + ioe.toString());
		}

	}

	private void reportMeter(String indexName, JsonGenerator jsonGenerator,
			Entry<String, Meter> entry, String timestampString) {
		try {
			writeStartMetric(entry.getKey(), indexName, jsonGenerator, timestampString);
			jsonGenerator.writeStartObject();
			final Meter meter = entry.getValue();
			jsonGenerator.writeNumberField("count", meter.getCount());
			jsonGenerator.writeNumberField("mean_rate", convertRate(meter.getMeanRate()));
			jsonGenerator.writeNumberField("1-minute_rate", convertRate(meter.getOneMinuteRate()));
			jsonGenerator.writeNumberField("5-minute_rate", convertRate(meter.getFiveMinuteRate()));
			jsonGenerator.writeNumberField("15-minute_rate", convertRate(meter.getFifteenMinuteRate()));
			jsonGenerator.writeEndObject();
			writeEndMetric(jsonGenerator);

		} catch (IOException ioe) {
			LOGGER.error("Exception writing metrics to Elasticsearch index: " + ioe.toString());
		}
	}

	private void reportTimer(String indexName, JsonGenerator jsonGenerator,
			Entry<String, Timer> entry, String timestampString) {
		try {
			writeStartMetric(entry.getKey(), indexName, jsonGenerator, timestampString);
			jsonGenerator.writeStartObject();
			final Timer timer = entry.getValue();
			final Snapshot snapshot = timer.getSnapshot();
			jsonGenerator.writeNumberField("count", timer.getCount());
			jsonGenerator.writeNumberField("mean_rate", convertRate(timer.getMeanRate()));
			jsonGenerator.writeNumberField("1-minute_rate", convertRate(timer.getOneMinuteRate()));
			jsonGenerator.writeNumberField("5-minute_rate", convertRate(timer.getFiveMinuteRate()));
			jsonGenerator.writeNumberField("15-minute_rate", convertRate(timer.getFifteenMinuteRate()));
			jsonGenerator.writeNumberField("min", convertDuration(snapshot.getMin()));
			jsonGenerator.writeNumberField("max", convertDuration(snapshot.getMax()));
			jsonGenerator.writeNumberField("mean", convertDuration(snapshot.getMean()));
			jsonGenerator.writeNumberField("stddev", convertDuration(snapshot.getStdDev()));
			jsonGenerator.writeNumberField("median", convertDuration(snapshot.getMedian()));
			jsonGenerator.writeNumberField("75th_percentile", convertDuration(snapshot.get75thPercentile()));
			jsonGenerator.writeNumberField("95th_percentile", convertDuration(snapshot.get95thPercentile()));
			jsonGenerator.writeNumberField("98th_percentile", convertDuration(snapshot.get98thPercentile()));
			jsonGenerator.writeNumberField("99th_percentile", convertDuration(snapshot.get99thPercentile()));
			jsonGenerator.writeNumberField("999th_percentile", convertDuration(snapshot.get999thPercentile()));

			jsonGenerator.writeEndObject();
			writeEndMetric(jsonGenerator);

		} catch (IOException ioe) {
			LOGGER.error("Exception writing metrics to Elasticsearch index: " + ioe.toString());
		}

	}

	private String writeStartMetric(String key, String indexName, JsonGenerator jsonGenerator,
			String timestampString)
			throws IOException {
		// Strip appId from metric name.
		String metricName = appId != null ? key.replace(appId + ".", "") : key;
		// Remove executor numbers from metric name
		metricName = metricName.replaceAll("[0-9]+\\.", "");
		// Remove whitespace from metrics name
		metricName = metricName.replaceAll("[]\\s]+", "");

		// Write the Elasticsearch bulk header
		jsonGenerator.writeStartObject();
		jsonGenerator.writeObjectFieldStart("index");
		jsonGenerator.writeStringField("_index", indexName);
		jsonGenerator.writeEndObject();
		jsonGenerator.writeEndObject();
		jsonGenerator.flush();
		jsonGenerator.writeRaw('\n');

		// write standard fields
		jsonGenerator.writeStartObject();
		jsonGenerator.writeStringField(timestampField, timestampString);
		jsonGenerator.writeStringField("hostName", localhost);
		jsonGenerator.writeStringField("applicationName", appName);
		jsonGenerator.writeStringField("applicationId", appId);
		jsonGenerator.writeStringField("executorId", executorId);
		if(mainClass!=null && !mainClass.isEmpty()) {
			jsonGenerator.writeStringField("javaMainClass", mainClass);
		}
		jsonGenerator.writeStringField("processId", processId);
		jsonGenerator.writeFieldName(metricName);

		return metricName;
	}

	private void writeEndMetric(JsonGenerator jsonGenerator) throws IOException {

		jsonGenerator.writeEndObject();
		jsonGenerator.flush();
		jsonGenerator.writeRaw('\n');
	}

	private String getJavaMainClass() {
		// might not be portable between JVMs :(
		return System.getenv(System.getenv().keySet()
				.stream()
				.filter(k -> k.startsWith("JAVA_MAIN_CLASS")).findAny().orElse(""));
	}

	public static Builder forRegistry(MetricRegistry registry) {
		return new Builder(registry);
	}

	public static class Builder {
		private final MetricRegistry registry;
		private TimeUnit rateUnit;
		private TimeUnit durationUnit;
		private MetricFilter filter;

		private String elasticsearchHost = "localhost";
		private String elasticsearchPort = "9200";
		private String elasticsearchIndex = "spark";
		private String elasticsearchPath = "";
		private String elasticsearchTimestampField = "timestamp";

		private Builder(MetricRegistry registry) {
			this.registry = registry;
			this.rateUnit = TimeUnit.SECONDS;
			this.durationUnit = TimeUnit.MILLISECONDS;
			this.filter = MetricFilter.ALL;
		}

		public Builder host(String hostName) {
			this.elasticsearchHost = hostName;
			return this;
		}

		public Builder port(String port) {
			this.elasticsearchPort = port;
			return this;
		}

		public Builder index(String index) {
			this.elasticsearchIndex = index;
			return this;
		}

		public Builder path(String path) {
			this.elasticsearchPath = path;
			return this;
		}

		public Builder timestampField(String timestampFieldName) {
			this.elasticsearchTimestampField = timestampFieldName;
			return this;
		}

		public Builder convertRatesTo(TimeUnit rateUnit) {
			this.rateUnit = rateUnit;
			return this;
		}

		public Builder convertDurationsTo(TimeUnit durationUnit) {
			this.durationUnit = durationUnit;
			return this;
		}

		public ElasticsearchReporter build() {
			return new ElasticsearchReporter(registry, filter, rateUnit, durationUnit, elasticsearchHost,
					elasticsearchPort, elasticsearchPath, elasticsearchIndex, elasticsearchTimestampField);
		}
	}

}
