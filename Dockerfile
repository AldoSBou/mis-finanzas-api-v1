# =============================================================
# Dockerfile multi-stage para mis-finanzas-api (Quarkus 3.21)
#
# Stage 1 (build): compila el proyecto con Maven y Java 21.
# Stage 2 (runtime): imagen minima solo con el JAR y un JRE.
# =============================================================

# ---------- Stage 1: BUILD ----------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

# Copiamos primero los archivos de Maven para aprovechar la cache de capas.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Codigo fuente
COPY src/ src/

# Build del JAR (tests se corren en CI aparte)
RUN ./mvnw package -DskipTests -B

# ---------- Stage 2: RUNTIME ----------
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# Usuario no-root por seguridad
RUN groupadd --system quarkus && useradd --system --gid quarkus quarkus

# Estructura fast-jar de Quarkus
COPY --from=build /build/target/quarkus-app/lib/      ./lib/
COPY --from=build /build/target/quarkus-app/*.jar     ./
COPY --from=build /build/target/quarkus-app/app/      ./app/
COPY --from=build /build/target/quarkus-app/quarkus/  ./quarkus/

# Script de arranque que prepara las llaves JWT desde variables de entorno
COPY --chown=quarkus:quarkus entrypoint.sh ./entrypoint.sh
RUN chmod +x ./entrypoint.sh

# Crear la carpeta de llaves y darle ownership al usuario quarkus
# (el entrypoint necesita escribir aquí, y corremos como no-root)
RUN mkdir -p /app/keys && chown -R quarkus:quarkus /app

EXPOSE 8080

USER quarkus

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC"

ENTRYPOINT ["./entrypoint.sh"]
