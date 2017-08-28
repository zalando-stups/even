FROM ubuntu:latest

RUN apt-get update && \
    apt-get install -y openssh-server && \
    mkdir /root/.ssh && \
    chmod 700 /root/.ssh && \
    mkdir /var/run/sshd
EXPOSE 22
ENTRYPOINT ["/usr/sbin/sshd", "-D", "-e", "-p", "22", "-o", "PasswordAuthentication=no"]
