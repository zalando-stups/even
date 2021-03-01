FROM registry.opensource.zalan.do/library/openjdk-8:latest
label maintainer="Zalando SE"

# add AWS RDS CA bundle
RUN mkdir /tmp/rds-ca && \
    curl https://s3.amazonaws.com/rds-downloads/rds-combined-ca-bundle.pem > /tmp/rds-ca/aws-rds-ca-bundle.pem
# split the bundle into individual certs (prefixed with xx)
# see http://blog.swwomm.com/2015/02/importing-new-rds-ca-certificate-into.html
RUN cd /tmp/rds-ca && csplit -sz aws-rds-ca-bundle.pem '/-BEGIN CERTIFICATE-/' '{*}'
RUN for CERT in /tmp/rds-ca/xx*; do mv $CERT /usr/local/share/ca-certificates/aws-rds-ca-$(basename $CERT).crt; done

RUN update-ca-certificates

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
