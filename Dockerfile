FROM openjdk:11

RUN mkdir /app
COPY ./build/libs/pollywog-all.jar /app/pollywog.jar

WORKDIR /app

ENTRYPOINT ["java", "-jar", "/app/pollywog.jar"]