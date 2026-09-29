# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Single Maven pass. dependency:go-offline is deliberately avoided: it resolves
# test and plugin artifacts too and fails on some of them, which breaks deploys.
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# Render free tier gives 512MB total. Cap the heap well below that so the
# JVM, Metaspace and direct buffers all have room to spare.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:InitialRAMPercentage=50.0 -XX:+UseSerialGC -Xss512k -XX:MaxMetaspaceSize=128m -Djava.security.egd=file:/dev/./urandom"
ENV PORT=8080

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

# exec form so the JVM is PID 1 and receives SIGTERM for graceful shutdown
ENTRYPOINT ["java", "-jar", "app.jar"]
