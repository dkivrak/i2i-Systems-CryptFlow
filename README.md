# CryptFlow

CryptFlow, sanal USD bakiyesiyle BTC, ETH ve SOL alım-satımı yapılabilen eğitim amaçlı bir paper-trading uygulamasıdır. Spring Boot backend sentetik fiyatları üretir, Redis üzerinden sunar, PostgreSQL'de geçmişi saklar ve STOMP ile React arayüzüne yayınlar. Gemini sohbeti yalnızca kullanıcı isteği üzerine çalışır.


## Mimari

- **Frontend:** React, Vite, JavaScript, Tailwind CSS, `@stomp/stompjs`
- **Backend:** Java 21, Spring Boot 3 modular monolith
- **Kalıcı veri:** PostgreSQL + Flyway
- **Geçici veri:** Redis oturumları ve güncel fiyatlar
- **Canlı akış:** Native WebSocket + STOMP (`/ws`, `/topic/market/prices`)
- **AI:** Google Gemini REST API

Ticker Engine her 15 saniyede BTC, ETH ve SOL fiyatlarını değiştirir. Aynı zaman damgasına sahip snapshot'lar önce PostgreSQL'e yazılır, güncel liste Redis'te overwrite edilir ve ardından STOMP topic'ine yayınlanır. Trade işlemleri wallet ve asset satırlarını pessimistic-write kilidiyle alır ve tek transaction içinde tamamlanır.

## Yerel kurulum

Gereksinimler: Docker/Docker Compose, frontend geliştirme için Node.js 20+ ve npm.

```bash
cp .env.example .env
docker compose up --build
```

Bu komut backend, PostgreSQL ve Redis'i başlatır. Flyway şemayı otomatik oluşturur. Backend `http://localhost:8080`, Swagger UI `http://localhost:8080/swagger-ui.html` adresindedir.

Frontend için ikinci terminal:

```bash
cd frontend
npm install
npm run dev
```

Arayüz `http://localhost:5173` adresinde açılır.

Kimlik doğrulama rotaları `/login` ve `/dashboard` olarak ayrılmıştır. Başarılı login sonrasında UUID token `sessionStorage` içindeki `cryptflow_token` anahtarına yazılır ve kullanıcı `/dashboard` rotasına yönlendirilir. Aynı sekmede sayfa yenilendiğinde geçerli token korunur.

## Environment variable'lar

Kök `.env.example` dosyasını `.env` olarak kopyalayın. Gerçek `.env` Git tarafından dışlanır.

| Değişken | Açıklama |
|---|---|
| `POSTGRES_*` | PostgreSQL veritabanı, kullanıcı, parola ve host portu |
| `SPRING_DATASOURCE_*` | Backend JDBC bağlantısı |
| `SPRING_DATA_REDIS_*` | Redis bağlantısı |
| `SESSION_TTL_HOURS` | Redis session TTL; varsayılan 24 saat |
| `FRONTEND_ORIGINS` | CORS ve WebSocket için virgülle ayrılmış izinli origin listesi |
| `TICKER_*` | Scheduler aralığı, maksimum değişim ve başlangıç fiyatları |
| `GEMINI_API_KEY` | Gemini API anahtarı; boşsa yalnız chat devre dışıdır |
| `GEMINI_MODEL` | Kullanılacak Gemini model adı; varsayılan `gemini-3.1-flash-lite` |
| `GEMINI_TIMEOUT_SECONDS` | Gemini çağrı timeout'u; varsayılan 30 saniye |

`.env` içindeki bir backend değişkeni güncellendiğinde `docker compose restart backend`
eski container environment'ını korur. Yeni değeri yüklemek için backend'i
`docker compose up -d --build --force-recreate backend` ile yeniden oluşturun.

Frontend gerekirse `frontend/.env.local` içinde `VITE_API_BASE_URL=http://localhost:8080/api` ve `VITE_WS_URL=ws://localhost:8080/ws` tanımlayabilir. Örnek değerler [frontend/.env.example](frontend/.env.example) dosyasındadır.

## API özeti

Public:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/market/prices`

`Authorization: Bearer <token>` gerektiren endpoint'ler:

- `POST /api/auth/logout`
- `GET /api/me`
- `GET /api/portfolio`
- `POST /api/trades`
- `GET /api/trades?page=0&size=20`
- `POST /api/chat/query`

Swagger/OpenAPI: `/swagger-ui.html` ve `/v3/api-docs`.

## WebSocket akışı

Frontend ilk fiyatları `GET /api/market/prices` ile alır, sonra `ws://localhost:8080/ws` bağlantısını açıp `/topic/market/prices` kanalına abone olur. Bağlantı kesilirse istemci 5 saniye sonra otomatik yeniden bağlanır ve son bilinen fiyatları göstermeye devam eder.

## Test ve build

```bash
cd backend
mvn test

cd ../frontend
npm run build
```

PDF, ödev ekran görüntüleri ve gerçek secret'lar repository'ye eklenmemelidir.
