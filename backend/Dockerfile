# =====================================================================
# Suma Tracking backend (Java 21, Spring Boot) as a Docker image.
# Stage 1 builds the jar with the project's own Maven wrapper.
# Stage 2 runs it on a small Java 21 runtime, as a normal (non-root) user.
# No password is inside this image. It comes from SUMA_DB_PASSWORD when the container starts.
# =====================================================================
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Windows may save mvnw with Windows line endings; fix that so Linux can run it
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/suma-tracking-backend-*.jar /app/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system suma && useradd --system --gid suma --uid 10001 suma
COPY --from=build /app/app.jar /app/app.jar
USER suma
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
