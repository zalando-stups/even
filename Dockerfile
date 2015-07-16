FROM zalando/openjdk:8u45-b14-5

MAINTAINER Zalando SE

COPY target/even.jar /
COPY target/scm-source.json /

EXPOSE 8080

CMD java $(java-dynamic-memory-opts) $(appdynamics-agent) -Dhystrix.command.default.execution.isolation.thread.timeoutInMilliseconds=15000 -jar /even.jar

