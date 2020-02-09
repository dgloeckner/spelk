FROM alpine:3.11.3

LABEL maintainer="Daniel Glöckner <gloeckner.daniel@gmail.com>"

ARG SPARK_VERSION=2.4.4
ENV SPARK_VERSION=${SPARK_VERSION}
ARG HADOOP_VERSION=2.7
ENV HADOOP_VERSION=${HADOOP_VERSION}
ARG HADOOP_MINOR_VERSION=2.7.3
ENV HADOOP_MINOR_VERSION=${HADOOP_MINOR_VERSION}
ENV SPARK_WORKER_MEMORY="5G"

# ENV variables are used with envsubst below
ARG SPARK_DRIVER_MEMORY=5g
ENV SPARK_DRIVER_MEMORY=${SPARK_DRIVER_MEMORY}
ENV SPARK_DRIVER_EXTRA_JAVA_OPTIONS=-XX:+PrintGCDetails

# Install base software, including Java
# /dev/urandom is used as random source, which is perfectly safe
# according to http://www.2uo.de/myths-about-urandom/
RUN apk add --no-cache \
    gettext procps coreutils bash bash-completion \
    openjdk8 \
    && echo "securerandom.source=file:/dev/urandom" >> /usr/lib/jvm/default-jvm/jre/lib/security/java.security

# Install Spark
RUN wget https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz \
      && tar -xvzf spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz \
      && mv spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION} spark \
      && rm spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz \
      && wget http://archive.apache.org/dist/hadoop/common/hadoop-${HADOOP_MINOR_VERSION}/hadoop-${HADOOP_MINOR_VERSION}.tar.gz \
      && tar -xvzf hadoop-${HADOOP_MINOR_VERSION}.tar.gz \
      && mv hadoop-${HADOOP_MINOR_VERSION} /hadoop \
      && rm hadoop-${HADOOP_MINOR_VERSION}.tar.gz

# Configure Spark
RUN mkdir /spark/eventlog
ADD spark-defaults.conf.template /spark/conf
RUN envsubst < /spark/conf/spark-defaults.conf.template > /spark/conf/spark-defaults.conf

# Add Spelk
RUN wget -P /spark/jars/ https://github.com/dgloeckner/spelk/releases/download/20200207213226-0e007e0/spelk.jar
ADD metrics.properties /spark/conf

# Dev tools
RUN apk add --no-cache \
    git git-bash-completion

# Customize environment
ADD .bashrc /root/
ADD startup.sh /root/

# Prepare volume for sharing code with host OS
RUN mkdir /code
# Can be mounted to a directory on the Host OS --volume=/local/code/dir:/code
VOLUME /code
WORKDIR /code

# Expose Spark ports
EXPOSE 18080 7077 8080 8081

# Run startup script and wait until we're shut down
ENTRYPOINT /root/startup.sh && tail -f /spark/logs/*