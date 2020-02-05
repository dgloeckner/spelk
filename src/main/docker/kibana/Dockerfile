ARG ELK_VERSION=7.5.1

# https://github.com/elastic/kibana-docker
FROM docker.elastic.co/kibana/kibana:${ELK_VERSION}

# Add pre-configured Kibana dashboards
RUN mkdir /tmp/dashboards
COPY install-dashboards.sh dashboards /tmp/dashboards/

CMD /tmp/dashboards/install-dashboards.sh & /usr/local/bin/kibana-docker
