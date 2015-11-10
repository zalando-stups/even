FROM zalando/openjdk:8u66-b17-1-2

MAINTAINER Zalando SE

COPY target/even.jar /
COPY target/scm-source.json /

EXPOSE 8080

CMD java $(java-dynamic-memory-opts) $(appdynamics-agent) -Dhystrix.command.default.execution.isolation.thread.timeoutInMilliseconds=15000 -jar /even.jar

