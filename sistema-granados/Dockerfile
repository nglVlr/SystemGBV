# Sistema Granados: imagen de la aplicacion (Spring Boot + Java 17).
# Construir y arrancar todo:  docker compose up -d --build

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system granados \
    && useradd --system --gid granados --home-dir /app --no-create-home granados \
    && mkdir -p /data/uploads /data/generados \
    && chown -R granados:granados /app /data

COPY --from=build --chown=granados:granados /src/target/sistema-granados-1.0.0.jar /app/app.jar

RUN printf '%s\n' \
    '#!/bin/sh' \
    'set -e' \
    'mkdir -p /data/uploads /data/generados' \
    'chown -R granados:granados /data 2>/dev/null || true' \
    'exec runuser -u granados -- java -jar /app/app.jar' \
    > /app/docker-entrypoint.sh \
    && chmod +x /app/docker-entrypoint.sh

ENV PORT=8085
EXPOSE ${PORT}

# DB URL is NOT baked into the image: SPRING_DATASOURCE_URL (env) overrides
# application-prod.properties. Local Docker sets it in docker-compose.yml;
# Render injects SPRING_DATASOURCE_HOST/PORT/DBNAME/USERNAME/PASSWORD.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    TZ=America/Guatemala \
    APP_STORAGE_UPLOADS=/data/uploads \
    APP_STORAGE_GENERADOS=/data/generados

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=20 \
    CMD curl -fsS http://127.0.0.1:${PORT}/login || exit 1

ENTRYPOINT ["/app/docker-entrypoint.sh"]
