# syntax=docker/dockerfile:1

# ---- Build stage: compile the Spring Boot fat jar with the Maven wrapper ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Resolve dependencies first so they cache independently of source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src ./src
RUN ./mvnw -B -q -DskipTests clean package

# ---- Runtime stage: slim JRE, non-root user ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 10001 spring
USER spring

COPY --from=build /app/target/app-0.0.1-SNAPSHOT.jar app.jar

# Render sets $PORT; Spring binds to it (see application.yaml).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
