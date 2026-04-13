FROM docker.m.daocloud.io/maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /workspace
RUN printf '%s\n' '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">' '  <mirrors>' '    <mirror>' '      <id>central</id>' '      <mirrorOf>*</mirrorOf>' '      <name>Maven Central</name>' '      <url>https://repo.maven.apache.org/maven2</url>' '    </mirror>' '  </mirrors>' '</settings>' > /tmp/settings.xml
COPY . .
RUN mvn -s /tmp/settings.xml -q -DskipTests -pl medical-service/medical-appointment-service -am package

FROM docker.m.daocloud.io/eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/medical-service/medical-appointment-service/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
