FROM openjdk:8

RUN apt-get update \
    && apt-get install -y \
        curl \
        libxrender1 \
        libjpeg62-turbo \
        fontconfig \
        libxtst6 \
        xfonts-75dpi \
        xfonts-base \
        xz-utils

RUN curl "https://github.com/wkhtmltopdf/packaging/releases/download/0.12.6-1/wkhtmltox_0.12.6-1.buster_amd64.deb" -L -o "wkhtmltopdf.deb"
RUN dpkg -i wkhtmltopdf.deb

COPY cb-org-hierarchy-service-0.0.1-SNAPSHOT.jar /opt/
#HEALTHCHECK --interval=30s --timeout=30s CMD curl --fail http://localhost:7001/actuator/health || exit 1
CMD ["/bin/bash", "-c", "java -XX:+PrintFlagsFinal $JAVA_OPTIONS -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -jar /opt/cb-org-hierarchy-service-0.0.1-SNAPSHOT.jar"]

