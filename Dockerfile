FROM eclipse-temurin:8-jdk
RUN useradd -ms /bin/bash appuser

RUN apt-get update && apt-get install -y \
        curl \
        libxrender1 \
        libjpeg-turbo8 \
        fontconfig \
        libxtst6 \
        xfonts-75dpi \
        xfonts-base \
        xz-utils \
    && rm -rf /var/lib/apt/lists/*

RUN curl -L -o wkhtmltopdf.deb \
    https://github.com/wkhtmltopdf/packaging/releases/download/0.12.6-1/wkhtmltox_0.12.6-1.buster_amd64.deb

RUN dpkg -i wkhtmltopdf.deb || apt-get -f install -y

COPY cb-org-hierarchy-service-0.0.1-SNAPSHOT.jar /opt/

RUN chown -R appuser:appuser /opt
USER appuser

CMD ["java", "-XX:+PrintFlagsFinal", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-Xmx512m", "-jar", "/opt/cb-org-hierarchy-service-0.0.1-SNAPSHOT.jar"]
