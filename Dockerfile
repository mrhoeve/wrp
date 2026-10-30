# syntax=docker/dockerfile:1

FROM maven:3.10.0-eclipse-temurin-21-alpine AS build

WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B clean verify

FROM eclipse-temurin:21-jre-alpine-3.24

WORKDIR /app

RUN apk upgrade --no-cache libcrypto3 libssl3 openssl \
    && addgroup --gid 1001 -S wrp \
    && adduser --uid 1001 -S -D -H -G wrp wrp

COPY --from=build --chown=0:0 --chmod=0444 /workspace/target/WebsiteregisterRijksoverheidParser-*.jar /app/wrp.jar

USER 1001:1001
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xss512k", "-jar", "/app/wrp.jar"]
