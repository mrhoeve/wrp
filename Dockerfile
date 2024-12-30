#
# Build stage
#
FROM maven:3.9.9-amazoncorretto-21 AS build
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml clean package -DskipTests

#
# Package stage
#
FROM amazoncorretto:21-alpine
ENV SERVICE_NAME="wrp"

RUN apk -U upgrade
RUN apk add --update curl && rm -rf /var/cache/apk/*
COPY --from=build /home/app/target/WebsiteregisterRijksoverheidParser-*.jar /app/WebsiteregisterRijksoverheidParser.jar


RUN addgroup --gid 1001 -S $SERVICE_NAME && \
    adduser -G $SERVICE_NAME --shell /bin/false --disabled-password -H --uid 1001 $SERVICE_NAME && \
    mkdir -p /var/log/$SERVICE_NAME && \
    chown $SERVICE_NAME:$SERVICE_NAME /var/log/$SERVICE_NAME
EXPOSE 8080
USER $SERVICE_NAME
ENTRYPOINT ["java","-jar", "-XX:+UseSerialGC", "-Xss512k","/app/WebsiteregisterRijksoverheidParser.jar"]

