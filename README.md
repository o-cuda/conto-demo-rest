### BREVE INTRODUZIONE

Applicazione di Test che si basa su alcune API di Fabrick.
Siccome tra i requisiti non era richiesta un FE di esposizione del dato, ho preso spunto da una applicazione fatta da me che veniva usata per gestire la comunicazione via messaggi in plain string, con i vari campi posizionali in input, attraverso un socket TCP, e restituisce sempre un messaggio di testo

L'applicazione si basa su SpringBoot + Vert-x rendeno l'applicazione reattiva

Sono presenti dei test automatici e sono presenti anche test di integrazione (in generale preferisco porre attenzione quando possibile a quelli di integrazione)

Per i test di integrazione occorre far partire l'applicazione:
    - mvn spring-boot:run -Dspring-boot.run.jvmArguments="-DapplicationPropertiesPath=file:/path/to/conto-demo/config-map/local/application.properties"
	- spring parte sulla 9091 -> questo semplicemente per la parte degli actuator di spring (interessante per gli health check )
	- il server socket di vertx parte sulla 9221
	- lancio manuale dei test di integrazione come "programma java", sono dei main semplici che scrivono il messaggio aprendo un client socket

Per quanto riguarda il DB, al momento  è presente ma non l'ho usato per la scrittura su DB dei movimenti, lo vorrei fare in un secondo step.
Il db per poterlo fare è presente, è un H2 che parte in memory, ed è usato per la lettura delle configurazioni dei messaggi che arrivano sul socket.
In pratica i primi 3 caratteri che arrivano sul messaggio identificano l'OPERAZIONE che si vuole effettuare e vanno a prendersi sul DB la configurazione da usare
- LIS: lista delle transazioni
- BON: effettuare il bonifico
- SAL: per la visualizzazione del SALDO

## DESCRIZIONE ARCHITETTURA

L'applicazione è un microservizio. All'interno della compilazione è anche presente la compilazione di una immagine docker (non l'ho provata direttamente al momento perchè non ho un docker instalato a portata di mano, ma dovrebbe funzionare correttamente, immagine base usata una OpenJDK Amazon Corretto versione 21)

L'applicazione usa vertx come framework reactive. I vari Verticle colloquiano tra loro attraverso l'event bus come fossero del publisher / subscriber

Il logging dell'applicazione è affidata a Logback, con in più la possibilità di loggare in maniera asicrona attraverso configurazione, ed anche in formato json se si vuole utilizzare uno stack elastic + kibana ad esempio
Siccome siamo un contesto reattivo vertx, il problema qua è che si perdeva la possibilità di mettere nell'MDC di Logback un ID per contrassegnare ed identificare i LOG di una richiesta univoca. Per ovviare a questo problema si è utilizzato reactiverse-contextual-logging: [link alla documentazione]( https://reactiverse.io/reactiverse-contextual-logging/)

Per quanto rigurada le letture/scritture su DB sono fatte senza l'utilizzo di un ORM ma utilizzando direttamente il JDBC client di vertx: [link alla documentazione](https://vertx.io/docs/vertx-jdbc-client/java/)
Questo perchè i JDBC normali bloccano i thread, mentre il client di vertx no

Il DB H2 è solo in memoria, quindi le eventuali scritture vanno per perse ogni volta si riavvia l'applicazione
Se si volesse mettere un DB vero, per quanto riguarda le configurazioni le si probbero semplicemente mettere in cache (il primo modo che mi viene in mente sarebbe usare la [Cache di Spring](https://spring.io/guides/gs/caching/))

L'application.properties utilizzato si trova solo config-map/local. 
Si trova li semplicemente perchè questa applicazione in teoria si potrebbe adattare ad un kubernetes multiambiente, dove gli application properties devono essere diversi perchè ogni ambiente ha il suo specifico.
Quindi nella classe Main ContoDemoApplication c'è una annotation PropertiesSource che prende come default /data/application.properties (o qualsiasi altro default utilizzato nel proprio cluster Kubernets) mentre per lo sviluppo locale bisogna andare a valorizzare la variabile applicationPropertiesPath come vmArgs, mettendo la propria location del file.
Esempio se si usa Windows: -DapplicationPropertiesPath="file:C:\Workspace\conto-demo\config-map\local\application.properties"


## COME FUNZIONA 

In questo caso l'applicazione start un server che apre sulla porta 9221 un server socket
I messaggi devono essere inviati come una string dove:
- i primi tre caratteri identificano l'applicazione
    - LIS per lista transazioni
    - BON per effettuare il bonifico
    - SAL per la visualizzazione del saldo
  
Il messagio recuperato dal socket e dato al verticle "GestisciRequestVerticle".
Il primo passo eseguito è legge la configurazione atraverso i primi tre caratteri passandoli al verticle "ConfigVerticle" che si occupa di verificare recuperare dall'in-memory DB la configurazione da leggere.
La confgigurazione si può trovare dentro al file data.sql che viene caricato all'avvio dell'applicazione (lo schema delle tabelle è schema.sql)
"GestisciRequestVerticle" recupera dalla configurazione su quale event bus deve mandare la request e viene invocato così l'ultimo verticle in maniera dinamica:
- LIS al lista_bus -> ListaTransazioniVerticle
- BON al bonifico_bus -> BonificoVerticle
- SAL al saldo_bus -> SaldoVerticle
 
Ognuno di questi verticle richiama le API Fabrick
N.B. Per BON:
- non è stato effettuato alcun controllo sui decimali sull'amount, ma al momento non so quanti decimali massimi si dovrebbero avere
- l'executionDate l'ho impostato sempre a data odierna
- alcuni campi nella request li ho messi come costanti, altri sono dinamici ed arrivano in input al server socket
 
 Le risposte sono sempre delle plain string ed iniziano sempre con 0 se è tutto corretto più l'eventuale messaggio, oppure con 1 se c'è stato un errore
 In caso di errore viene riportato in prima battuta l'UUID che identifica la richiesta, e poi il messaggio di errore (messaggio di errore ritornato dalle API di Fabrick o errore interno dell'applicazione).
 La decisione di mettere l'UUID è stata presa per visualizzare in maniera rapida i log 
  