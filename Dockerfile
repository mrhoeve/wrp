# syntax=docker/dockerfile:1

FROM maven:3.9.16-eclipse-temurin-21-alpine AS build

WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B clean verify

FROM eclipse-temurin:21-jre-alpine-3.24

ENV SERVICE_NAME=wrp
WORKDIR /app

COPY --from=build /workspace/target/WebsiteregisterRijksoverheidParser-*.jar /app/wrp.jar

RUN addgroup --gid 1001 -S "$SERVICE_NAME" \
    && adduser --uid 1001 -S -D -H -G "$SERVICE_NAME" "$SERVICE_NAME" \
    && chown -R "$SERVICE_NAME:$SERVICE_NAME" /app

USER 1001:1001
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xss512k", "-jar", "/app/wrp.jar"]
