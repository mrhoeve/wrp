#
# Build stage
#
FROM maven:3.9.6-amazoncorretto-21 AS build
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml clean package -DskipTests

#
# Package stage
#
FROM amazoncorretto:21-alpine
COPY --from=build /home/app/target/WebsiteregisterRijksoverheidParser-*.jar /usr/local/lib/WebsiteregisterRijksoverheidParser.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar", "-XX:+UseSerialGC", "-Xss512k","/usr/local/lib/WebsiteregisterRijksoverheidParser.jar"]

