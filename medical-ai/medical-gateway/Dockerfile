FROM docker.m.daocloud.io/maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -DskipTests -pl medical-gateway -am package

FROM docker.m.daocloud.io/eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/medical-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
