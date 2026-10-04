# ═══════════════════════════════════════
# STAGE 1: BUILD
# ═══════════════════════════════════════
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom.xml kwanza (kwa caching)
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build jar
RUN mvn clean package -DskipTests

# ═══════════════════════════════════════
# STAGE 2: RUN
# ═══════════════════════════════════════
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy jar kutoka build stage
COPY --from=build /app/target/V1-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Run app
ENTRYPOINT ["java", "-Dserver.port=${PORT:-8080}", "-jar", "app.jar"]