FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

ARG APP_MODULE=user-service

COPY . .

RUN if [ -z "$APP_MODULE" ]; then echo "APP_MODULE build argument is required"; exit 2; fi \
    && mvn -B -Dstyle.color=never -DskipTests -pl "$APP_MODULE" -am package

RUN test -f "/workspace/${APP_MODULE}/pom.xml" || \
    (echo "Module not found: ${APP_MODULE}" && exit 1)

RUN find "/workspace/${APP_MODULE}/target" -maxdepth 1 -type f -name "*.jar" \
    ! -name "original-*.jar" -print

FROM eclipse-temurin:21-jre

WORKDIR /app

ARG APP_MODULE=user-service

COPY --from=build /workspace/${APP_MODULE}/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]