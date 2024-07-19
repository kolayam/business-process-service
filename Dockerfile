FROM maven
WORKDIR /usr/src
RUN git clone https://github.com/kolayam/common.git
WORKDIR /usr/src/common
RUN mvn clean install -Dmaven.test.skip=true
WORKDIR /usr/src/app
COPY . .
RUN mvn clean install -Dmaven.test.skip=true
FROM openjdk:8
COPY --from=0 /usr/src/app/target/business-process-service.jar ./
ENV PORT=8085
EXPOSE $PORT
RUN env
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom", "-jar", "/business-process-service.jar"]