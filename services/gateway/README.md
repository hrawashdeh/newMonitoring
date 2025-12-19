# Gateway Service

API Gateway for ETL Monitoring Platform

## Features
- Spring Cloud Gateway (reactive)
- Redis-backed rate limiting
- Circuit breakers (Resilience4j)
- Prometheus metrics

## Build
```bash
cd services/gateway
mvn clean package
```

## Run
```bash
mvn spring-boot:run
```

## Configuration
See `src/main/resources/application.yaml`

## Port
8888
