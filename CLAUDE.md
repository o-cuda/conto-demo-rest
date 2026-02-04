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
- Jakarta Bean Validation (input validation annotations)
- JUnit 5 + Mockito (unit testing)
- Vert.x JUnit5 (async testing support)
- JaCoCo (test coverage reporting)

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

# Run unit tests
mvn test -Dtest="**/unit/**/*Test"

# Run integration tests (requires server running first)
mvn test -Dtest="**/integration/**/*Test"
```

**Important**: The default properties location is `/data/application.properties` for Kubernetes deployments. For local development, override with `applicationPropertiesPath` VM argument.

## Architecture

### Message Flow

1. HTTP server receives REST requests on port 8080
2. `HttpServerVerticle` generates UUID (requestId) and parses request parameters
3. `HttpServerVerticle` builds API URL using `ApiConstants` and sends JSON message to event bus:
   - GET /api/accounts/balance → `EventBusConstants.SALDO_BUS` → `SaldoVerticle`
   - GET /api/accounts/transactions → `EventBusConstants.LISTA_BUS` → `ListaTransazioniVerticle`
   - POST /api/accounts/payments/money-transfers → `EventBusConstants.BONIFICO_BUS` → `BonificoVerticle`
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

### Constants Management

All hardcoded strings have been centralized into constants classes:

**`ApiConstants.java`** - Fabrick API endpoint URLs
- `FABRICK_API_BASE` - Base URL for sandbox environment
- `BALANCE_URL_TEMPLATE` - Balance endpoint with {accountId} placeholder
- `TRANSACTIONS_URL_TEMPLATE` - Transactions endpoint with placeholders
- `MONEY_TRANSFER_URL_TEMPLATE` - Money transfer endpoint with placeholder
- `TRANSACTIONS_URL_FORMAT` - String format for validation enquiries
- `REST_API_BASE` - REST API base path
- `REST_BALANCE_ENDPOINT` - GET /api/accounts/balance
- `REST_TRANSACTIONS_ENDPOINT` - GET /api/accounts/transactions
- `REST_MONEY_TRANSFER_ENDPOINT` - POST /api/accounts/payments/money-transfers

**`EventBusConstants.java`** - Event bus addresses
- `SALDO_BUS` - "saldo_bus"
- `LISTA_BUS` - "lista_bus"
- `BONIFICO_BUS` - "bonifico_bus"
- `TRANSACTION_PERSISTENCE_BUS` - "transaction_persistence_bus"

**`StatusConstants.java`** - Status codes and messages
- `OK`, `ERROR` - Response status values
- `STATUS_PENDING`, `STATUS_EXECUTED`, `STATUS_CANCELED` - Transfer statuses
- Error message constants for validation failures
- `FIELD_REMITTANCE_INFORMATION` - Fabrick API field name

### API Endpoints

The Fabrick API endpoints (SVIL environment) are defined in `ApiConstants`:

- Balance: `ApiConstants.BALANCE_URL_TEMPLATE`
- Transactions: `ApiConstants.TRANSACTIONS_URL_TEMPLATE`
- Money Transfer: `ApiConstants.MONEY_TRANSFER_URL_TEMPLATE`

### Configuration Management

The application is designed for multi-environment Kubernetes deployments:
- Local: `config-map/local/application.properties`
- Kubernetes default: `/data/application.properties`

Override with `-DapplicationPropertiesPath` VM argument.

## Data Types and Validation

### Monetary Values

**All monetary fields use `BigDecimal` for precision:**
- `SaldoResponseDto.balance`, `SaldoResponseDto.availableBalance`
- `BalanceDto.Payload.balance`, `BalanceDto.Payload.availableBalance`
- `ListaTransactionDto.amount`
- `BonificoRequestDto.amount`
- `BonificoRestRequestDto.amount`

Never use `double` or `float` for monetary values.

### Input Validation

The application uses **Jakarta Bean Validation** annotations on DTOs:

**BonificoRestRequestDto validations:**
- `@NotNull` - creditor, amount
- `@NotBlank` - description, currency, creditor.name, creditor.account.accountCode
- `@DecimalMin("0.01")` - amount
- `@Pattern` - currency (ISO 4217), IBAN format, BIC format, string lengths

**HttpServerVerticle integration:**
- Injects `Validator` via constructor
- Validates requests before processing
- Returns all validation errors at once with descriptive messages

### Generics and Type Safety

All collection types use proper generics:
- Use `List<>` interface instead of `ArrayList<>` implementation
- Explicit type parameters: `new HashSet<String>()`, `new ArrayList<JsonArray>()`
- No raw types allowed

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
  - `BonificoVerticleTest.java`
- `unit/dto/DtoSerializationTest.java` - Jackson JSON serialization tests

**Key Test Dependencies:**
- `vertx-junit5` - Vert.x JUnit 5 extension for async testing
- `mockito-core` and `mockito-junit-jupiter` - Mocking framework
- `spring-boot-starter-validation` - Bean Validation support

### Integration Tests

Integration tests require the application to be running first:

```bash
# Start the application
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-DapplicationPropertiesPath=file:/path/to/config-map/local/application.properties"

# Run integration tests (in another terminal)
mvn test -Dtest="**/integration/**/*Test"
```

Test class: `integration/RestIntegrationTest.java`

Tests REST endpoints using `ApiConstants`:
- GET `ApiConstants.REST_BALANCE_ENDPOINT`
- GET `ApiConstants.REST_TRANSACTIONS_ENDPOINT`
- POST `ApiConstants.REST_MONEY_TRANSFER_ENDPOINT`

### Test Coverage

**JaCoCo Maven Plugin (v0.8.12)** configured for test coverage:

```bash
# Generate coverage report
mvn jacoco:report

# View report
open target/site/jacoco/index.html
```

**Known Limitation:** JaCoCo has compatibility issues with Java 21 + Vert.x bytecode instrumentation. Coverage report generation may fail during test phase. For coverage reports, consider using external tools or running with Java 17.

## Code Conventions

- Italian is used for comments and some variable names
- Verticles are Spring components (autowired in `ContoDemoApplication`)
- REST API uses JSON request/response format
- Event bus communication uses `ContoDemoApplication.getDefaultDeliverOptions()` for 10-second timeout
- All API URLs managed through `ApiConstants` - never hardcoded
- All event bus addresses managed through `EventBusConstants`
- All status codes and messages managed through `StatusConstants`
- Use `BigDecimal` for all monetary values
- Use `List<>` interface instead of `ArrayList<>` implementation
- Add Bean Validation annotations to all REST request DTOs

## Known Limitations

- Test coverage reporting has compatibility issues with Java 21 + Vert.x (JaCoCo limitation)
- Money transfer `executionDate` is always set to today's date (not user-configurable)

## Version History

### BigDecimal, Constants, and Validation Refactoring (February 2025)

**Major Changes:**
- **BigDecimal migration**: All monetary fields converted from `double` to `BigDecimal`
- **Constants classes created**:
  - `ApiConstants.java` - Centralized API endpoint URLs
  - `EventBusConstants.java` - Centralized event bus addresses
  - `StatusConstants.java` - Centralized status codes and error messages
- **Bean Validation added**: `spring-boot-starter-validation` dependency added
- **Input validation enhanced**: Jakarta Bean Validation annotations added to DTOs
- **Generics fixed**: Replaced raw types with proper generic collections
- **Dependencies cleaned**: Removed unused `jsoniter` library (Jackson only requirement)
- **Test coverage configured**: JaCoCo Maven plugin added (v0.8.12)
- **OpenAPI updated**: Changed schema from `double` to `decimal` for monetary fields
- **Integration tests updated**: Now use `ApiConstants` instead of hardcoded paths

**Files Modified:**
- `dto/` - All monetary fields changed to `BigDecimal`, `ArrayList` → `List`
- `dto/rest/BonificoRestRequestDto.java` - Added Bean Validation annotations, removed `executionDate`
- `vertx/HttpServerVerticle.java` - Added `Validator` injection, uses constants
- `vertx/BonificoVerticle.java` - Uses constants for API URLs and event bus addresses
- `vertx/SaldoVerticle.java` - Uses `EventBusConstants`
- `vertx/ListaTransazioniVerticle.java` - Uses `EventBusConstants`
- `vertx/TransactionPersistenceVerticle.java` - Uses `EventBusConstants`
- `mapper/DtoMapper.java` - Uses `StatusConstants`
- `utils/` - Created 3 new constants classes
- `integration/RestIntegrationTest.java` - Uses `ApiConstants`
- `openapi.yaml` - Updated schema for `decimal` format, removed `executionDate`

**Validation Added:**
- `@NotNull`, `@NotBlank` for required fields
- `@DecimalMin("0.01")` for amount validation
- `@Pattern` for format validation (IBAN, BIC, ISO currency codes)
- `@Valid` for nested object validation

**Test Results:**
- All 14 unit tests passing
- 3 new constants classes created
- 9 files modified, 93 insertions, 25 deletions

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
