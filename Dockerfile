FROM registry.opensource.zalan.do/library/openjdk-8:latest

MAINTAINER Zalando SE

COPY target/even.jar /
COPY resources/api/even-api.yaml /zalando-apis/

EXPOSE 8080

VOLUME /tmp

ENTRYPOINT ["java", \
            "-XX:InitialRAMPercentage=80.0", \
            "-XX:MinRAMPercentage=80.0", \
            "-XX:MaxRAMPercentage=80.0", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-jar", \
            "even.jar"]
