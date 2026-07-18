# CryptFlow - Profesyonel Paper Trading & Portfolio Workspace

CryptFlow, gerçek zamanlı Binance verileriyle entegre çalışan, kullanıcıların sanal bakiye (USD) ile risk almadan alım-satım (paper trading) yapabilmesini sağlayan, Gemini AI destekli modern bir portföy yönetim ve analiz web uygulamasıdır.

---

## 🌟 Temel Özellikler

* **Canlı Binance Entegrasyonu:** Sunucu, Binance WebSocket akışına (`!miniTicker@arr`) bağlanarak 620'den fazla USDT çiftini canlı olarak takip eder ve fiyatları milisaniyeler içinde günceller.
* **Canlı Fiyat Akışı (Real-time WebSockets):** React frontend uygulaması, sunucu ile kurduğu yerel WebSocket bağlantısı sayesinde fiyat güncellemelerini anlık olarak alır. Fiyat değişimleri arayüzde şık yeşil/kırmızı **flaş efektleriyle** görselleştirilir.
* **Akıllı Logo Desteği (Multi-Source Fallback):** Coin logoları öncelikle GitHub kripto kütüphanesinden çekilir. Logo bulunamazsa otomatik olarak **CoinCap API**'sine yönlenir, yine bulunamazsa dinamik gradyan arka plana sahip harf ikonu gösterilir.
* **Güvenli Alım-Satım Motoru (Trading Engine):** Bakiye ve varlık kontrolleri veritabanında **Pessimistic Write Lock** (yazma kilidi) kullanılarak tek bir transaction içinde ACID prensiplerine uygun olarak gerçekleştirilir.
* **Gemini AI Asistanı:** Kullanıcılar portföy dağılımlarını, risk analizlerini ve piyasa trendlerini portföy verileriyle beslenmiş Gemini AI asistanına sorabilir.
* **Kapsamlı İşlem Geçmişi (Transactions):** Yapılan tüm işlemler veritabanında arşivlenir ve 10.000 işleme kadar sayfalama desteğiyle arayüzde listelenir.

---

## 🏗 Mimari ve Veri Akışı (Data Flow)

Aşağıdaki şema, Binance'ten başlayıp veritabanlarına ve oradan da tarayıcıya (frontend) ulaşan canlı veri ve işlem akışını göstermektedir:

```mermaid
graph TD
    %% Veri Kaynakları
    Binance[Binance WebSocket Stream] -- Canlı Fiyat Paketleri --> Backend[Spring Boot Backend]
    
    %% Sunucu Katmanı
    subgraph "Sunucu (VPS / Localhost)"
        Backend -- 1. Fiyat Güncelleme --> Redis[(Redis Hash: market:prices)]
        Backend -- 2. Canlı Yayın --> WS[Native WebSocket Server]
        Backend -- 3. Periyodik Fiyat Snapshot --> DB[(PostgreSQL Database)]
        
        %% Servisler
        AuthService[Auth Service]
        TradeService[Trade Execution Engine]
        GeminiClient[Gemini AI Client]
    end

    %% İstemci Katmanı
    subgraph "İstemci (Browser / Frontend)"
        Frontend[React Vite App] -- 1. İlk Yükleme REST API --> Backend
        Frontend -- 2. Abone Ol wss:// --> WS
        Frontend -- 3. Risk Sorusu /chat --> GeminiClient
        Frontend -- 4. Alım-Satım /trades --> TradeService
    end

    %% Kalıcı Depolama
    TradeService -- ACID Transaction / Locks --> DB
    AuthService -- Session Yönetimi --> Redis
```

### 🔁 Veri Akışı Detayları:
1. **Fiyat Güncelleme Akışı:**
   * Backend, Binance WebSocket bağlantısı üzerinden tüm `*USDT` fiyat güncellemelerini yakalar.
   * Yakalanan güncel fiyatlar **Redis** (`market:prices`) hash tablosunda güncellenir.
   * Eş zamanlı olarak aktif WebSocket istemcilerine (React) yeni fiyat paketleri tekil yayınla anlık iletilir.
   * `TickerEngine` her 15 saniyede bir Redis'teki fiyatların anlık görüntüsünü (snapshot) **PostgreSQL** veritabanına periyodik analizler için yazar.
2. **Alım-Satım İşlem Akışı:**
   * Kullanıcı `/api/trades` üzerinden `BUY` veya `SELL` isteği gönderdiğinde, `TradeService` ilgili kullanıcının cüzdanını ve o coine ait varlık satırını veritabanında **PESSIMISTIC_WRITE** moduyla kilitler.
   * Bakiye yeterliliği ve varlık miktarı anlık canlı fiyatla kontrol edilir.
   * İşlem onaylanırsa, bakiye düşülür/artırılır, `trades` tablosuna log yazılır ve kilitler serbest bırakılır.

---

## 💾 Veritabanı Şeması (Database Schema)

Uygulamanın şeması **Flyway** yardımıyla otomatik olarak yönetilir. Temel tabloların yapısı şu şekildedir:

### 1. `users` (Kullanıcılar)
* `id` (UUID, Primary Key): Benzersiz kullanıcı kimliği.
* `first_name` (VARCHAR): Kullanıcının adı.
* `last_name` (VARCHAR): Kullanıcının soyadı.
* `email` (VARCHAR, Unique): Giriş e-posta adresi.
* `password` (VARCHAR): BCrypt ile şifrelenmiş parola.

### 2. `portfolio` (Cüzdan/Portföy Ana)
* `id` (UUID, Primary Key): Portföy ID'si.
* `user_id` (UUID, Foreign Key): Kullanıcı ilişkisi.
* `usd_balance` (NUMERIC): Kullanılabilir sanal USD bakiyesi (başlangıçta $10,000.00).

### 3. `portfolio_assets` (Sahip Olunan Kripto Varlıklar)
* `id` (UUID, Primary Key): Varlık ID'si.
* `portfolio_id` (UUID, Foreign Key): Portföy ilişkisi.
* `symbol` (VARCHAR): Kripto varlık sembolü (örn. `BTC`, `ETH`).
* `quantity` (NUMERIC): Sahip olunan miktar.
* `average_buy_price` (NUMERIC): Ortalama alış maliyeti.

### 4. `trades` (İşlem Geçmişi)
* `id` (UUID, Primary Key): İşlem ID'si.
* `user_id` (UUID, Foreign Key): Kullanıcı ilişkisi.
* `symbol` (VARCHAR): İşlem yapılan sembol.
* `side` (VARCHAR): İşlem yönü (`BUY` veya `SELL`).
* `quantity` (NUMERIC): İşlem adedi.
* `unit_price_usd` (NUMERIC): İşlemin gerçekleştiği birim fiyat.
* `total_usd` (NUMERIC): Toplam işlem tutarı (USD).
* `executed_at` (TIMESTAMP): İşlem zamanı.

### 5. `price_snapshots` (Fiyat Geçmişi)
* `id` (BIGINT, Primary Key): Snapshot ID'si.
* `symbol` (VARCHAR): Kripto para sembolü.
* `price_usd` (NUMERIC): O andaki USD değeri.
* `recorded_at` (TIMESTAMP): Kayıt zamanı.

---

## 🔌 API Dokümantasyonu (Endpoints)

Tüm isteklerin gövdesi JSON formatındadır. Kimlik doğrulaması gerektiren isteklerde `Authorization: Bearer <token>` başlığı (header) gönderilmelidir.

### 🔐 Kimlik Doğrulama (Auth)
| Metot | Rota | Açıklama | Yetki |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Yeni kullanıcı hesabı oluşturur. | Public |
| `POST` | `/api/auth/login` | Giriş yapar ve 24 saat geçerli UUID token döner. | Public |
| `POST` | `/api/auth/logout` | Aktif oturumu sonlandırır ve tokenı siler. | Bearer |

### 📈 Market Verisi & İşlemler
| Metot | Rota | Açıklama | Yetki |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/market/prices` | Desteklenen tüm coinlerin anlık fiyat listesini döner. | Public |
| `GET` | `/api/me` | Giriş yapmış kullanıcının profil bilgilerini getirir. | Bearer |
| `GET` | `/api/portfolio` | Kullanıcının USD bakiyesini ve sahip olduğu varlıkları listeler. | Bearer |
| `POST` | `/api/trades` | Sanal bakiye ile alım (`BUY`) veya satım (`SELL`) emri çalıştırır. | Bearer |
| `GET` | `/api/trades` | Kullanıcının işlem geçmişini döner (Sayfa boyutu: 10,000). | Bearer |
| `POST` | `/api/chat/query` | Gemini AI asistanına portföy analizi sorusu yönlendirir. | Bearer |

---

## 💻 Yerel Geliştirme Kurulumu (Local Setup)

### Gereksinimler:
* Docker ve Docker Compose
* Node.js v20+ ve npm (Frontend derleme/geliştirme için)

### Adım 1: Environment Tanımları
Kök dizindeki `.env.example` dosyasını `.env` adıyla kopyalayın ve içerisindeki `GEMINI_API_KEY` alanına kendi Gemini API anahtarınızı girin:
```bash
cp .env.example .env
```

### Adım 2: Docker Servislerinin Başlatılması (Database & Backend)
Proje kök dizininde aşağıdaki komutla PostgreSQL, Redis ve Spring Boot uygulamasını başlatın:
```bash
docker compose up --build -d
```
* **Backend:** `http://localhost:8080` adresinde çalışacaktır.
* **Swagger/OpenAPI:** API dokümanlarına `http://localhost:8080/swagger-ui.html` adresinden erişebilirsiniz.

### Adım 3: Frontend (Arayüz) Sunucusunun Başlatılması
Yeni bir terminal açıp `frontend` klasörüne gidin, bağımlılıkları kurun ve geliştirici sunucusunu başlatın:
```bash
cd frontend
npm install
npm run dev
```
* **Frontend Arayüzü:** `http://localhost:5173` adresinde tarayıcınızda açılacaktır.

---

## 🚀 Canlı Sunucu (VPS) Deploy Rehberi

Projeyi canlı sunucunuza (Ubuntu VPS) diğer çalışan projeleri etkilemeyecek şekilde kurmak için aşağıdaki adımları uygulayabilirsiniz:

### 1. Kodları Sunucuya Çekme:
```bash
cd /var/www
git clone https://github.com/dkivrak/i2i-Systems-CryptFlow.git cryptflow
cd cryptflow
```

### 2. Docker Konteynerlerini Derleme ve Başlatma (Database & Backend):
```bash
# Projeye özel .env dosyasını oluşturun ve Gemini API key'i yazın
cp .env.example .env

# Sadece CryptFlow servislerini izole derleyip arka planda başlatın
docker compose build --no-cache
docker compose up -d
```

### 3. Frontend Dağıtımı (PM2 & Nginx):
Arayüzü sunucuda barındırmak için önce derleyip, ardından arka planda kesintisiz çalışması için PM2 ile başlatalım:
```bash
cd frontend
npm install
npm run build

# PM2 ile frontend'i 5173 portundan yayına alın
npm install -g serve
npx pm2 start serve --name "cryptflow-frontend" -- build --port 5173 --single
```

### 4. Nginx Reverse Proxy Yapılandırması (Örnek):
Eğer IP adresi veya subdomain üzerinden yönlendirme yapmak isterseniz, `/etc/nginx/sites-available/default` dosyanıza şu yönlendirme bloğunu ekleyebilirsiniz:

```nginx
server {
    listen 80;
    server_name cryptflow.alanadiniz.com; # Veya sunucu IP adresiniz

    location / {
        proxy_pass http://127.0.0.1:5173; # PM2 ile çalışan frontend portu
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }

    location /api {
        proxy_pass http://127.0.0.1:8080/api; # Spring Boot REST API portu
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws {
        proxy_pass http://127.0.0.1:8080/ws; # WebSocket endpointi
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```
Yapılandırmayı kaydedip Nginx servisini test edip yeniden başlatın:
```bash
nginx -t
systemctl restart nginx
```
Uygulamanız artık tamamen hazır ve canlıda olacaktır!
