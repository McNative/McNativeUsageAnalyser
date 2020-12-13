FROM maven:3.6.3-jdk-14 AS build
COPY src /usr/src/app/src
COPY pom.xml /usr/src/app
RUN mvn -f /usr/src/app/pom.xml clean package -Dmaven.test.skip=true

FROM openjdk:14-jdk-slim
COPY --from=build /usr/src/app/target/McNativeUsageAnalyser.jar McNativeUsageAnalyser.jar

ENTRYPOINT ["java", "-jar", "McNativeUsageAnalyser.jar"]