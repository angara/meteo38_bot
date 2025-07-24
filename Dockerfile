FROM openjdk:21-jdk-slim

WORKDIR /app
COPY target/meteo38_bot.jar /app

CMD ["java", "-jar", "meteo38_bot.jar"]
