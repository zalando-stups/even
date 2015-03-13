FROM zalando/openjdk:8u40-b09-2
MAINTAINER Henning Jacobs <henning.jacobs@zalando.de>

COPY target/even.jar /

EXPOSE 8080

CMD java $(java-dynamic-memory-opts) -jar /even.jar

