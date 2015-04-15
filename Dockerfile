FROM zalando/openjdk:8u40-b09-4
MAINTAINER Henning Jacobs <henning.jacobs@zalando.de>

COPY target/even.jar /
COPY scm-source.json /

EXPOSE 8080

CMD java $(java-dynamic-memory-opts) -jar /even.jar

