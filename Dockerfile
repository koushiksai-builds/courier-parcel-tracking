FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY . .

RUN mvn -B -Dstyle.color=never -DskipTests -pl app -am package

RUN JAR_PATH="$(find /workspace/app/target -maxdepth 1 -type f -name "*.jar" -print -quit)" \
    && test -n "$JAR_PATH" \
    && cp "$JAR_PATH" /workspace/app.jar

FROM eclipse-temurin:21-jre

WORKDIR /app

ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx320m"

COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
