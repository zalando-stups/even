FROM zalando/openjdk:8u40-b09-2
MAINTAINER Henning Jacobs <henning.jacobs@zalando.de>

ADD target/ssh-access-granting-service.jar .

EXPOSE 8080

CMD java -jar /ssh-access-granting-service.jar

