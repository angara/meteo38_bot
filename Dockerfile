FROM eclipse-temurin:25-jdk

WORKDIR /app
COPY target/meteo38_bot.jar /app

CMD ["java", "-jar", "meteo38_bot.jar"]
