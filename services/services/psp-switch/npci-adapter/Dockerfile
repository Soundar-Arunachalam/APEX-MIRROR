# ─────────────────────────────────────────────────────────────────────────────
# Stage 1: Build
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

# Copy Maven wrapper + POM first for layer caching (dependencies won't rebuild
# unless pom.xml changes)
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .

# Download dependencies
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build
COPY src/ src/
RUN ./mvnw package -DskipTests -B

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: Runtime
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

LABEL maintainer="PSP Engineering <platform@psp.demo>"
LABEL description="NPCI Adapter — bridges PSP Switch (Kafka/Redis) to NPCI UPI network (HTTP/XML)"

# Create non-root user for security
RUN addgroup -S npci && adduser -S npci -G npci

WORKDIR /app

# Copy the fat JAR from the build stage
COPY --from=build /app/target/npci-adapter-*.jar app.jar

# Own the working directory
RUN chown -R npci:npci /app

USER npci

# Adapter listens on 8081
EXPOSE 8081

# JVM tuning: container-aware GC + async logging
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.output.ansi.enabled=ALWAYS"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
