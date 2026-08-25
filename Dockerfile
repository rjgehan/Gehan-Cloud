# syntax=docker/dockerfile:1

# --- Stage 1: build ----------------------------------------------------------
# Pinned to BUILDPLATFORM so Maven always runs natively. A Spring Boot jar is
# architecture-independent, so the same build output ships to every target arch
# and only the JRE base image below differs. Without this pin, an arm64 target
# would run the whole Maven build under QEMU emulation.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build

# Resolve dependencies in their own layer so code-only changes reuse the cache.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests \
 && mv target/gehan-cloud-*.jar target/app.jar

# Split the fat jar into layers that change at different rates, so a redeploy
# only ships the ~200KB application layer instead of 68 dependency jars again.
# The extracted app.jar is a thin jar whose Class-Path points at a sibling lib/,
# which is why all four layer directories flatten into the same /app.
WORKDIR /build/target
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# --- Stage 2: runtime --------------------------------------------------------
FROM eclipse-temurin:25-jre-noble AS runtime

LABEL org.opencontainers.image.title="Gehan Cloud" \
      org.opencontainers.image.description="Self-hosted personal cloud portal built with Spring Boot" \
      org.opencontainers.image.source="https://github.com/rjgehan/Gehan-Cloud" \
      org.opencontainers.image.licenses="MIT"

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 1000 --create-home --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build /build/target/extracted/dependencies/ ./
COPY --from=build /build/target/extracted/spring-boot-loader/ ./
COPY --from=build /build/target/extracted/snapshot-dependencies/ ./
COPY --from=build /build/target/extracted/application/ ./

# SQLite file and grocery.json live here; mount a volume to keep them across deploys.
RUN mkdir -p /data && chown -R app:app /data /app
VOLUME ["/data"]
ENV APP_DATA_DIR=/data

USER app
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fsS http://localhost:8080/login >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
