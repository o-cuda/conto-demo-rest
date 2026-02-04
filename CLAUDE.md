# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a reactive Spring Boot + Vert.x microservice that exposes a REST API for banking operations through the Fabrick API. The application uses an event-driven architecture where Verticles communicate via the Vert.x event bus in a publisher/subscriber pattern.

**Key Technologies:**
- Spring Boot 3.2.5 (for dependency injection, actuator, and infrastructure)
- Vert.x 4.5.11 (reactive framework)
- Vert.x Web (REST API server)
- Java 21 (compiled to Java 21 bytecode)
- Lombok (managed by Spring Boot, code generation for getters/setters/logging)
- JUnit 5 + Mockito (unit testing)
- Vert.x JUnit5 (async testing support)

**Ports:**
- Spring Boot/Actuator: 9091
- Vert.x HTTP Server: 8080

## Running the Application

The application requires a properties file path to be specified for local development:

```bash
# Local development with custom properties location
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-DapplicationPropertiesPath=file:/path/to/conto-demo/config-map/local/application.properties"

# Build
mvn clean package

# Build Docker image (automatically builds during package phase)
mvn clean package docker:build

# Run tests (integration tests - requires server to be running first)
mvn test
```

**Important**: The default properties location is `/data/application.properties` for Kubernetes deployments. For local development, override with `applicationPropertiesPath` VM argument.

## Architecture

### Message Flow

1. HTTP server receives REST requests on port 8080
2. `HttpServerVerticle` generates UUID (requestId) and parses request parameters
3. `HttpServerVerticle` builds API URL with hardcoded endpoints and sends JSON message to event bus:
   - GET /api/accounts/{accountId}/balance → `saldo_bus` → `SaldoVerticle`
   - GET /api/accounts/{accountId}/transactions → `lista_bus` → `ListaTransazioniVerticle`
   - POST /api/accounts/{accountId}/payments/money-transfers → `bonifico_bus` → `BonificoVerticle`
4. Operation verticles call Fabrick APIs via WebClient
5. Responses are returned as JSON

### Response Format

All responses are JSON:

Success response:
```json
{
  "status": "OK",
  "payload": { ... }
}
```

Error response:
```json
{
  "status": "ERROR",
  "requestId": "uuid",
  "message": "error message"
}
```

### Request ID Propagation

The application uses `reactiverse-contextual-logging` to maintain request context across the reactive event bus. In `ContoDemoApplication`, outbound/inbound interceptors propagate the `requestId` through event bus headers, allowing logs to be correlated across Verticles.

See: `ContoDemoApplication.java:73-89`

### API Endpoints

The API endpoints are hardcoded (SVIL environment):

- Balance: `https://sandbox.platfr.io/api/gbs/banking/v4.0/accounts/{accountId}/balance`
- Transactions: `https://sandbox.platfr.io/api/gbs/banking/v4.0/accounts/{accountId}/transactions?fromAccountingDate={fromDate}&toAccountingDate={toDate}`
- Money Transfer: `https://sandbox.platfr.io/api/gbs/banking/v4.0/accounts/{accountId}/payments/money-transfers`

### Configuration Management

The application is designed for multi-environment Kubernetes deployments:
- Local: `config-map/local/application.properties`
- Kubernetes default: `/data/application.properties`

Override with `-DapplicationPropertiesPath` VM argument.

## Testing

The project has two types of tests:

### Unit Tests

Unit tests are located in `src/test/java/it/demo/fabrick/unit/` and can be run independently without starting the server:

```bash
# Run all unit tests (fast, no external dependencies)
mvn test -Dtest="**/unit/**/*Test"

# Run specific unit test class
mvn test -Dtest="SaldoVerticleTest"
```

**Unit Test Structure:**
- `unit/verticle/` - Verticle initialization and event bus subscription tests
  - `SaldoVerticleTest.java`
  - `ListaTransazioniVerticleTest.java`
- `unit/dto/DtoSerializationTest.java` - Jackson JSON serialization tests

**Key Test Dependencies:**
- `vertx-junit5` - Vert.x JUnit 5 extension for async testing
- `mockito-core` and `mockito-junit-jupiter` - Mocking framework

### Integration Tests

Integration tests require the application to be running first:

```bash
# Start the application
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-DapplicationPropertiesPath=file:/path/to/config-map/local/application.properties"

# Run integration tests (in another terminal)
mvn test -Dtest="**/integration/**/*Test"
```

Test class: `integration/RestIntegrationTest.java`

Tests REST endpoints:
- GET /api/accounts/{accountId}/balance
- GET /api/accounts/{accountId}/transactions
- POST /api/accounts/{accountId}/payments/money-transfers

## Code Conventions

- Italian is used for comments and some variable names
- Verticles are Spring components (autowired in `ContoDemoApplication`)
- REST API uses JSON request/response format
- Event bus communication uses `ContoDemoApplication.getDefaultDeliverOptions()` for 10-second timeout
- API endpoints are hardcoded (no database configuration needed)

## Known Limitations

- No decimal validation for transfer amounts

## Version History

### Spring Boot 3.2.5 + Java 21 + Vert.x 4.5.11 Upgrade (February 2025)

Upgraded from Spring Boot 2.7.2 / Java 8 / Vert.x 4.2.1 to modern versions:

**Major Changes:**
- **Spring Boot**: 2.7.2 → 3.2.5
- **Java**: 1.8 → 21 (with `release=21` compiler flag)
- **Vert.x**: 4.2.1 → 4.5.11
- **Jakarta EE**: Full migration from `javax.*` to `jakarta.*` namespace (required by Spring Boot 3)
- **Spring Security**: Rewritten `ActuatorSecurity` class to use `SecurityFilterChain` bean pattern (removed deprecated `WebSecurityConfigurerAdapter`)
- **Maven Compiler Plugin**: 3.8.1 → 3.13.0

**Breaking Changes Addressed:**
1. All `javax.*` imports replaced with `jakarta.*` (e.g., `javax.annotation.PostConstruct` → `jakarta.annotation.PostConstruct`)
2. Spring Security configuration migrated from `WebSecurityConfigurerAdapter` to `SecurityFilterChain` bean (required by Spring Security 6)
3. Actuator security matcher updated to use `securityMatcher()` with `authorizeHttpRequests()` for `/actuator` endpoints
4. Logger configuration updated from `javax.servlet` to `jakarta.servlet`

**Compatibility Notes:**
- Lombok version is now managed by Spring Boot 3 parent POM
- Application requires Java 21+ to compile and run
- All 29 unit tests pass successfully

**Files Modified During Upgrade:**
- `pom.xml` - Spring Boot parent, Java version, Vert.x version, compiler plugin
- `src/main/java/it/demo/fabrick/ContoDemoApplication.java` - Jakarta import
- `src/main/java/it/demo/fabrick/ActuatorSecurity.java` - Complete Spring Security 6 rewrite
- `src/main/resources/logging-extend.xml` - Jakarta namespace

### REST API Conversion (February 2025)

Converted from TCP socket server to REST API:

**Major Changes:**
- **SocketServerVerticle** → **HttpServerVerticle**: Replaced TCP socket server (port 9221) with HTTP server (port 8080)
- **GestisciRequestVerticle** → Removed: Positional message parsing replaced with JSON REST endpoints
- **ConfigVerticle** → Removed: Database configuration replaced with hardcoded API endpoints
- **MessageParserUtil** → Removed: No longer needed for JSON requests
- **SocketResponseFormatter** → Removed: No longer needed for JSON responses

**New Components:**
- `HttpServerVerticle.java` - Vert.x Web HTTP server exposing 3 REST endpoints
- `dto/rest/` package - REST request/response DTOs:
  - `SaldoRequestDto.java`, `SaldoResponseDto.java`
  - `TransazioniRequestDto.java`, `TransazioniResponseDto.java`
  - `BonificoRestRequestDto.java`, `BonificoRestResponseDto.java`
- `integration/RestIntegrationTest.java` - Integration tests for REST endpoints

**Modified Components:**
- `SaldoVerticle.java` - Returns JSON instead of formatted string
- `ListaTransazioniVerticle.java` - Returns JSON instead of formatted string
- `BonificoVerticle.java` - Accepts REST request DTO, returns JSON

**API Endpoints:**
- `GET /api/accounts/{accountId}/balance` - Get account balance
- `GET /api/accounts/{accountId}/transactions?fromAccountingDate=X&toAccountingDate=Y` - Get transactions
- `POST /api/accounts/{accountId}/payments/money-transfers` - Execute money transfer

**Removed Components:**
- `SocketServerVerticle.java` - TCP socket server (no longer @Component)
- `GestisciRequestVerticle.java` - Positional message router (no longer @Component)
- `ConfigVerticle.java` - Database configuration (no longer @Component)
- `MessageParserUtil.java` - Message parsing utility (deleted)
- `SocketResponseFormatter.java` - Response formatting (deleted)
- Related unit tests (deleted)

**Compatibility Notes:**
- Vert.x Web dependency already present in pom.xml
- All 8 remaining unit tests pass successfully
