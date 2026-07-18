# CryptFlow - Technical Architecture & System Design Document

This document provides a detailed breakdown of the system architecture, data flow paths, database layout, and decoupling interfaces for the CryptFlow platform.

---

## 1. Architectural Design Overview

CryptFlow is engineered as a decoupled modular monolith on the backend with a standalone Single-Page Application (SPA) on the frontend. The system manages high-frequency price data ingestion and executes simulated trades in an ACID-compliant environment.

```mermaid
graph TD
    %% Sources
    Binance[Binance WebSocket Stream] -- wss:// --> Ingestion[SupportedSymbolsService]
    
    %% Backend Modules
    subgraph "CryptFlow Core Monolith"
        Ingestion -- Implements --> Provider[ExternalPriceProvider Interface]
        Provider -- Write Prices --> Redis[(Redis In-Memory Cache)]
        Provider -- Broadcast Tickers --> WS[STOMP WebSocket Server]
        
        %% Database Sync
        Engine[TickerEngine Scheduler] -- Read Redis / Save Snapshots --> PostgreSQL[(PostgreSQL Database)]
        
        %% Business Modules
        Auth[Auth Service]
        Trade[Trade Service]
        Chat[Chat Service]
    end

    %% Client Layer
    subgraph "Frontend Client (React)"
        UI[React App] -- 1. REST API Requests --> Auth
        UI -- 2. wss:// Subscriptions --> WS
        UI -- 3. Place Order --> Trade
        UI -- 4. Portfolio AI Chat --> Chat
    end

    %% Storage Integration
    Trade -- ACID Transaction / Locks --> PostgreSQL
    Auth -- Cache User Sessions --> Redis
    Chat -- Context Gathering --> PostgreSQL
```

---

## 2. Ingestion & Decoupling Layer (Section 5.3)

To decouple price ingestion from the core trade processing engine, the system implements the `ExternalPriceProvider` interface. This allows the backend to switch between live APIs and simulated tickers without altering order routing or balance validation logic.

### Ingestion Interface Code Contract:
```java
package com.i2i.cryptflow.shared.model;

import java.math.BigDecimal;
import java.util.List;

public interface ExternalPriceProvider {
    List<String> getSymbols();
    boolean isSupported(String symbol);
    BigDecimal getInitialPrice(String symbol);
}
```

### Dependency Decoupling Diagram:
```mermaid
classDiagram
    class ExternalPriceProvider {
        <<interface>>
        +getSymbols() List~String~
        +isSupported(symbol) boolean
        +getInitialPrice(symbol) BigDecimal
    }
    class SupportedSymbolsService {
        +fetchLatestPrices() Map
    }
    class TradeService {
        -ExternalPriceProvider priceProvider
    }
    class MarketPriceService {
        -ExternalPriceProvider priceProvider
    }
    class TickerEngine {
        -ExternalPriceProvider priceProvider
    }

    ExternalPriceProvider <|.. SupportedSymbolsService : implements
    TradeService --> ExternalPriceProvider : depends on
    MarketPriceService --> ExternalPriceProvider : depends on
    TickerEngine --> ExternalPriceProvider : depends on
```

---

## 3. Core Modules & Business Logic

### 3.1. Authentication & Session Module (`com.i2i.cryptflow.auth`)
* **State Isolation:** User credentials and hashes are stored permanently in PostgreSQL, while active session tokens are cached in Redis with a 24-hour Time-to-Live (TTL).
* **Initial Provisioning:** Upon user registration, a randomized starting balance (between `$10,000` and `$20,000`) is credited using a cryptographically secure random generator (`SecureRandom`).

### 3.2. Order Execution Engine (`com.i2i.cryptflow.trade`)
To ensure transactional integrity and avoid double-spending or balance mismatch, all buy and sell actions are wrapped inside database transactions using pessimistic write locks.

```mermaid
sequenceDiagram
    autonumber
    actor User as React Client
    participant API as TradeController
    participant Service as TradeService
    participant DB as PostgreSQL (Pessimistic Lock)

    User->>API: POST /api/trades (BUY 0.5 BTC)
    API->>Service: execute(userId, symbol, BUY, qty)
    Service->>DB: Lock Wallet & Asset (SELECT FOR UPDATE)
    activate DB
    Note over DB: Locks held. No concurrent writes allowed.
    Service->>Service: Fetch Live BTC Price from Redis
    Service->>Service: Validate Balance & Order Value
    Service->>DB: Deduct USD Balance & Credit BTC Quantity
    Service->>DB: Save TradeTransaction Record
    deactivate DB
    Service-->>User: Return TradeResult (201 Created)
```

---

## 4. Storage Architecture & Schema Design

### 4.1. Cache Layer (Redis)
* **`session:<token>` (String):** Stores the active user session metadata.
* **`market:prices` (Hash):** Stores the latest ticker prices mapped to coin symbols. Key overwrites occur every 15 seconds.

### 4.2. Database Schema (PostgreSQL)
```mermaid
erDiagram
    USERS {
        uuid id PK
        varchar email UK
        varchar password_hash
        timestamp created_at
    }
    WALLETS {
        uuid id PK
        uuid user_id FK "Unique"
        numeric usd_balance
    }
    PORTFOLIO_ASSETS {
        uuid id PK
        uuid wallet_id FK
        varchar symbol
        numeric quantity
    }
    TRADE_TRANSACTIONS {
        uuid id PK
        uuid user_id FK
        uuid wallet_id FK
        varchar symbol
        varchar side
        numeric quantity
        numeric unit_price_usd
        numeric total_usd
        timestamp executed_at
    }
    PRICE_SNAPSHOTS {
        bigint id PK
        varchar symbol
        numeric price_usd
        timestamp recorded_at
    }

    USERS ||--|| WALLETS : owns
    USERS ||--o{ TRADE_TRANSACTIONS : executes
    WALLETS ||--o{ PORTFOLIO_ASSETS : contains
    WALLETS ||--o{ TRADE_TRANSACTIONS : logs
```

---

## 5. LLM Diagnostics Pipeline (`com.i2i.cryptflow.chat`)

The Gemini integration operates strictly on-demand. When a request is made, a context-rich prompt is assembled by combining permanent financial records with temporary caching data.

```mermaid
graph LR
    %% Context Sources
    Postgres[(PostgreSQL)] -- Historical Trades & Holdings --> Prompt[Prompt Orchestration]
    Redis[(Redis)] -- Live Tickers --> Prompt
    Input[User Query] --> Prompt
    
    %% Pipeline
    Prompt -- Context-Rich Payload --> Gemini[Gemini REST Client]
    Gemini -- Try/Catch Block --> Response[Structured Markdown Response]
    
    %% Error Fallback
    style Response fill:#1fc8a4,stroke:#fff,stroke-width:2px;
```

* **Exception Fallback:** In the event of network timeouts or API limits, the client returns a structured error message (`ApiException` -> `503 Service Unavailable`) rather than blocking connection threads.
