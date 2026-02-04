# Conto Demo Banking API

Microservizio reattivo Spring Boot + Vert.x che espone un'API REST per operazioni bancarie attraverso le API di Fabrick.

## Caratteristiche

- **Architettura reattiva** basata su Vert.x con event-driven communication
- **API REST** per operazioni bancarie (saldo, transazioni, bonifici)
- **Validazione input** con Jakarta Bean Validation
- **Logging contestualizzato** con reactiverse-contextual-logging per tracciamento richieste
- **Test automatizzati** con JUnit 5, Mockito, e Vert.x JUnit5
- **Docker ready** con build automatica dell'immagine

## Tecnologie

- Spring Boot 3.2.5
- Vert.x 4.5.11
- Java 21
- Jakarta Bean Validation
- Jackson per JSON
- Lombok
- JUnit 5 + Mockito
- H2 Database (in-memory)

## Porte

- **HTTP Server (Vert.x)**: 8080
- **Spring Boot Actuator**: 9091

## Avvio dell'Applicazione

L'applicazione richiede un file di configurazione:

```bash
# Sviluppo locale con properties personalizzate
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-DapplicationPropertiesPath=file:/path/to/conto-demo/config-map/local/application.properties"

# Build
mvn clean package

# Build immagine Docker
mvn clean package docker:build

# Run unit tests
mvn test -Dtest="**/unit/**/*Test"

# Run integration tests (richiede server in esecuzione)
mvn test -Dtest="**/integration/**/*Test"
```

**Nota**: La location di default per le properties è `/data/application.properties` per deploy Kubernetes. Per sviluppo locale, usare la VM argument `applicationPropertiesPath`.

## Architettura

L'applicazione usa Vert.x come framework reattivo. I vari Verticle comunicano attraverso l'event bus in pattern publisher/subscriber.

### Message Flow

1. Il server HTTP riceve richieste REST sulla porta 8080
2. `HttpServerVerticle` genera un UUID (requestId) e processa i parametri della richiesta
3. `HttpServerVerticle` costruisce l'URL API usando `ApiConstants` e invia un messaggio JSON all'event bus:
   - GET /api/accounts/balance → `EventBusConstants.SALDO_BUS` → `SaldoVerticle`
   - GET /api/accounts/transactions → `EventBusConstants.LISTA_BUS` → `ListaTransazioniVerticle`
   - POST /api/accounts/payments/money-transfers → `EventBusConstants.BONIFICO_BUS` → `BonificoVerticle`
4. I Verticle chiamano le API Fabrick via WebClient
5. Le risposte sono restituite come JSON

### Formato Risposta

Tutte le risposte sono in JSON:

Successo:
```json
{
  "status": "OK",
  "payload": { ... }
}
```

Errore:
```json
{
  "status": "ERROR",
  "requestId": "uuid",
  "message": "messaggio di errore"
}
```

### API Endpoints

- `GET /api/accounts/balance` - Saldo conto
- `GET /api/accounts/transactions?fromAccountingDate=X&toAccountingDate=Y` - Lista transazioni
- `POST /api/accounts/payments/money-transfers` - Esegui bonifico

### Gestione Costanti

Tutte le stringhe hardcoded sono state centralizzate in classi di costanti:

- **`ApiConstants.java`** - URL endpoint API Fabrick
- **`EventBusConstants.java`** - Indirizzi event bus
- **`StatusConstants.java`** - Codici di stato e messaggi di errore

### Logging e Tracciamento Richieste

L'applicazione usa `reactiverse-contextual-logging` per mantenere il contesto della richiesta attraverso l'event bus reattivo. In `ContoDemoApplication`, gli interceptor outbound/inbound propagano il `requestId` attraverso gli header dell'event bus, permettendo di correlare i log attraverso i Verticle.

Vedi: `ContoDemoApplication.java:73-89`

### Database H2

Il database H2 in-memory viene usato per la persistenza asincrona delle transazioni. Il verticle `TransactionPersistenceVerticle` gestisce l'inserimento delle transazioni evitando duplicati.

## Validazione Input

L'applicazione usa **Jakarta Bean Validation** con le seguenti annotazioni:

### BonificoRestRequestDto

- `@NotNull` - creditor, amount
- `@NotBlank` - description, currency, creditor.name, creditor.account.accountCode
- `@DecimalMin("0.01")` - amount (minimo 0.01)
- `@Pattern` - currency (codice ISO 4217 a 3 lettere maiuscole), formato IBAN, formato BIC/SWIFT

### Tipi di Dati

**Tutti i valori monetari usano `BigDecimal`** per la precisione:
- `balance`, `availableBalance` in SaldoResponseDto
- `amount` in ListaTransactionDto, BonificoRequestDto, BonificoRestRequestDto

**Non usare mai `double` o `float` per valori monetari.**

### Generics e Type Safety

Tutte le collection usano generics appropriati:
- Usare interfaccia `List<>` invece di `ArrayList<>`
- Parametri di tipo espliciti: `new HashSet<String>()`, `new ArrayList<JsonArray>()`
- Nessun raw type permesso

## Testing

### Unit Tests

```bash
# Run tutti gli unit tests
mvn test -Dtest="**/unit/**/*Test"
```

Located in `src/test/java/it/demo/fabrick/unit/`:
- `unit/verticle/` - Test di inizializzazione Verticle e subscription event bus
- `unit/dto/DtoSerializationTest.java` - Test serializzazione Jackson JSON

### Integration Tests

```bash
# Avviare l'applicazione prima
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-DapplicationPropertiesPath=file:/path/to/config-map/local/application.properties"

# Lanciare i test di integrazione (in un altro terminale)
mvn test -Dtest="**/integration/**/*Test"
```

Test class: `integration/RestIntegrationTest.java`

Testa gli endpoint REST usando `ApiConstants`.

### Test Coverage

**JaCoCo Maven Plugin (v0.8.12)** configurato per la copertura dei test:

```bash
# Genera report copertura
mvn jacoco:report

# Visualizza report
open target/site/jacoco/index.html
```

**Limitazione nota**: JaCoCo ha problemi di compatibilità con Java 21 + Vert.x bytecode instrumentation.

## Docker

L'immagine base usa Amazon Corretto OpenJDK versione 21.

```bash
# Build immagine
mvn clean package docker:build
```

## Configurazione Multi-Environment

L'applicazione è progettata per deployment Kubernetes multi-environment:
- Local: `config-map/local/application.properties`
- Kubernetes default: `/data/application.properties`

Override con `-DapplicationPropertiesPath` VM argument.

## Note Implementative

### Bonifici (BonificoVerticle)

- Validazione dell'amount con precisione BigDecimal
- `executionDate` impostato sempre a data odierna in `DtoMapper`
- Validation enquiry automatica in caso di HTTP 500/504 per verificare se il bonifico è stato eseguito
- Ricerca transazioni per conferma bonifico con tolleranza di 0.01 sull'amount

### Persistenza Transazioni

Il verticle `TransactionPersistenceVerticle` gestisce la persistenza asincrona:
- Fire-and-forget: non blocca la risposta REST
- Evita duplicati controllando i transactionId esistenti
- Batch insert per ottimizzazione performance

## Limitazioni Note

- La copertura dei test ha problemi di compatibilità con Java 21 + Vert.x (limitazione JaCoCo)
- Il campo `executionDate` per i bonifici è sempre impostato a data odierna (non configurabile dall'utente)
