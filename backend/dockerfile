# Multi-stage build for the Spring Boot API.
#
#   docker build -t jobhunt-backend ./backend
#   docker run --rm -p 8080:8080 \
#     -e JWT_SECRET="$(openssl rand -base64 48)" \
#     -e DB_URL=jdbc:postgresql://host:5432/jobhunt \
#     -e DB_USERNAME=jobhunt -e DB_PASSWORD=... \
#     -v jobhunt-uploads:/app/data/uploads \
#     jobhunt-backend

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Dependencies first, so the layer is cached until the POM changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Never run as root.
RUN addgroup -S app && adduser -S app -G app

COPY --from=build /app/target/jobhunt-backend-*.jar app.jar

# Uploaded documents live here; mount a volume so they survive a redeploy.
RUN mkdir -p /app/data/uploads && chown -R app:app /app

USER app
EXPOSE 8080

# Production profile: no credential defaults, secure cookies, mandatory env vars.
ENV SPRING_PROFILES_ACTIVE=prod
ENV STORAGE_ROOT=/app/data/uploads

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
