# Stage 1: build the JAR with the Maven Wrapper (no local Maven/JDK build needed)
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Dependencies first, so they are cached when only the source code changes
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw package -DskipTests -B

# Stage 2: run the JAR on a smaller JRE image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
