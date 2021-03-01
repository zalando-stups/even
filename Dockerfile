FROM registry.opensource.zalan.do/library/openjdk-8:latest

MAINTAINER Zalando SE

COPY target/even.jar /
COPY resources/api/even-api.yaml /zalando-apis/

EXPOSE 8080

VOLUME /tmp

CMD java $JAVA_OPTS -jar /even.jar
