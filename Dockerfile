# Imagen de produccion del servicio de notificaciones. Se construye desde la raiz del repositorio:
#   docker build -t energy-monitor-notification .
# La orquesta el repositorio infra, junto con el gateway, los servicios de seguridad,
# dispositivos, mediciones y hogares y energy-monitor-backend.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
USER app
EXPOSE 8087
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
