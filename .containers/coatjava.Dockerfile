#syntax=docker/dockerfile:1
ARG REF_NAME=development 

FROM codecr.jlab.org/hallb/clas12/clas12-containers/java_base:main

LABEL name="coatjava"
LABEL maintainer="Whitney Armstrong <whit@jlab.org>"
LABEL architecture="amd64"

# build coatjava 
RUN java --version && cd /opt && \
    git clone https://code.jlab.org/hallb/alert/coatjava.git && cd coatjava && \
    git fetch origin && git checkout ${REF_NAME} && ./build-coatjava.sh --quiet && \
    ./install-clara /opt/clara
