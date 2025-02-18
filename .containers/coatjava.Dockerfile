#syntax=docker/dockerfile:1

FROM ubuntu:noble

LABEL name="coatjava"
LABEL maintainer="Whitney Armstrong <whit@jlab.org>"
LABEL architecture="amd64"

USER root


RUN apt update && apt upgrade -y
RUN apt install cmake vim maven groovy git ca-certificates wget curl python-is-python3 \
                openjdk-17-jdk openjdk-17-jre openjdk-17-jdk-headless openjdk-17-jre-headless \
                python3-sqlalchemy -y && update-ca-certificates

# CA certificates
ADD https://pki.jlab.org/JLabCA.crt /etc/ssl/certs/JLabCA.crt 
#RUN trust anchor --store /image/JLabCA.crt

ARG REF_NAME=development 
# build coatjava 
RUN java --version && cd /opt && \
    git clone https://code.jlab.org/hallb/alert/coatjava.git && cd coatjava && \
    git fetch origin && git checkout ${REF_NAME} && ./build-coatjava.sh 
