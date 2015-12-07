FROM zalando/openjdk:8u66-b17-1-3

MAINTAINER Zalando SE

COPY target/even.jar /
COPY target/scm-source.json /

EXPOSE 8080

VOLUME /tmp

CMD java $JAVA_OPTS $(java-dynamic-memory-opts) $(newrelic-agent) $(appdynamics-agent) -jar /even.jar

