# en-monitor-notification-service

EnergyMonitor notification microservice: delivers the alerts and recommendations to the members of
a home by mail, Web Push and mobile push according to each member's preferences, mails the
verification and recovery codes of the security service, keeps the in-app notifications of the
administrators and the delivery log, and manages each user's notification channels. Spring Boot 4,
Java 21, hexagonal architecture.

It is being extracted from the `notification` module of `energy-monitor-backend`, following the
same steps as the security, device, measurement and home services.

## Requirements

- Java 21 (the repository brings `./mvnw`, no Maven install needed).
- MySQL from the `infra` repository: the schema is shared with `energy-monitor-backend` and the
  security, device, measurement and home services.

## Run locally

```bash
cp .env.example .env
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
# set the MYSQL_PASSWORD of infra/.env in both files
./mvnw spring-boot:run          # http://localhost:8087/actuator/health
```

On Windows PowerShell use `.\mvnw.cmd` instead of `./mvnw`.

## Tests

```bash
SPRING_DATASOURCE_PASSWORD=... ./mvnw test
```

The tests run under the `test` profile against the MySQL of `infra`.
