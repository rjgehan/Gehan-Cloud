# syntax=docker/dockerfile:1

# --- Stage 1: build ----------------------------------------------------------
# Maven comes with the image. The bundled ./mvnw wrapper is deliberately not
# used here: it downloads and unzips Maven at build time, and a bare JDK image
# has neither curl nor unzip to do that with.
#
# Pinned to BUILDPLATFORM so Maven always runs natively. A Spring Boot jar is
# architecture-independent, so the same build output ships to every target arch
# and only the JRE base image below differs. Without this pin, an arm64 target
# would run the whole Maven build under QEMU emulation.
FROM --platform=$BUILDPLATFORM maven:3-eclipse-temurin-26 AS build
WORKDIR /build

# Resolve dependencies in their own layer so code-only changes reuse the cache.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src/ src/
RUN mvn -B -q clean package -DskipTests \
 && mv target/gehan-cloud-*.jar target/app.jar

# Split the fat jar into layers that change at different rates, so a redeploy
# only ships the ~200KB application layer instead of 68 dependency jars again.
# The extracted app.jar is a thin jar whose Class-Path points at a sibling lib/,
# which is why all four layer directories flatten into the same /app.
WORKDIR /build/target
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# --- Stage 2: runtime --------------------------------------------------------
FROM eclipse-temurin:21-jre-noble AS runtime

LABEL org.opencontainers.image.title="Gehan Cloud" \
      org.opencontainers.image.description="Self-hosted personal cloud portal built with Spring Boot" \
      org.opencontainers.image.source="https://github.com/rjgehan/Gehan-Cloud" \
      org.opencontainers.image.licenses="MIT"

# Ubuntu noble already ships a user at UID 1000, so the app user takes a UID
# outside that range. Pinned rather than auto-assigned, so a bind-mounted /data
# can be chowned to a known owner.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --create-home --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build /build/target/extracted/dependencies/ ./
COPY --from=build /build/target/extracted/spring-boot-loader/ ./
COPY --from=build /build/target/extracted/snapshot-dependencies/ ./
COPY --from=build /build/target/extracted/application/ ./

# The SQLite file lives here; mount a volume to keep it across deploys.
RUN mkdir -p /data && chown -R app:app /data /app
VOLUME ["/data"]
ENV APP_DATA_DIR=/data

USER app
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fsS http://localhost:8080/login >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
