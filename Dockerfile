#STAGE1: MAVEN BUILD
FROM maven as build
WORKDIR /app
COPY . .
RUN mvn clean install
#STAGE2: RUN JAR
FROM adoptopenjdk/openjdk11:alpine-jre
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
