FROM registry.opensource.zalan.do/stups/openjdk:latest

MAINTAINER Zalando SE

COPY target/even.jar /
COPY resources/api/even-api.yaml /zalando-apis/

EXPOSE 8080

VOLUME /tmp

CMD java $JAVA_OPTS $(java-dynamic-memory-opts) -jar /even.jar

