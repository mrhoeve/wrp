#
# Build stage
#
FROM maven:3.9.5-amazoncorretto-17 AS build
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml clean package -DskipTests

#
# Package stage
#
FROM amazoncorretto:17-alpine3.18
COPY --from=build /home/app/target/WebsiteregisterRijksoverheidParser-*.jar /usr/local/lib/WebsiteregisterRijksoverheidParser.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar", "-XX:+UseSerialGC", "-Xss512k","/usr/local/lib/WebsiteregisterRijksoverheidParser.jar"]

