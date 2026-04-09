FROM docker.m.daocloud.io/maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -DskipTests -pl medical-service/medical-user-service -am package

FROM docker.m.daocloud.io/eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/medical-service/medical-user-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
