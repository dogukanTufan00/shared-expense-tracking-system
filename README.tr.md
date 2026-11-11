[English](README.md) | **Türkçe**

# Ortak Gider Takip Sistemi

Ev arkadaşlarıyla yaşayanlar için ortak ev giderlerini takip eden bir REST API. Türkiye'deki öğrenciler ve genç çalışanlar için yerelleştirilmiş bir Splitwise alternatifi.

Kullanıcılar kayıt olur, bir ev (household) oluşturur, ev arkadaşlarını ekler ve kimin neyi ödediğini (kira, faturalar, market, …) kaydeder. Bu API, ileride geliştirilecek mobil uygulamanın (Flutter, öncelikle Android) backend'idir.

## Kullanılan Teknolojiler

| Alan | Teknoloji |
|---|---|
| Dil | Java 25 |
| Framework | Spring Boot 4.1 (Web MVC, Data JPA, Security, Validation) |
| Veritabanı | PostgreSQL 16 (Docker) |
| Kimlik doğrulama | JWT (stateless, `jjwt`), şifreler BCrypt ile hash'lenir |
| API dokümantasyonu | Swagger UI (`springdoc-openapi`) |
| Build | Gradle |

## Özellikler

- **Kayıt ve giriş:** Giriş başarılı olursa diğer tüm isteklerde kullanılacak bir JWT döner.
- **Evler (households):** Ev oluşturma, listeleme, görüntüleme, yeniden adlandırma ve silme. Evi oluşturan kişi o evin `ADMIN`'i olur.
- **Üyeler:** Admin, ev arkadaşlarını e-posta adresiyle ekler ve çıkarır. Bir evde en fazla 3 aktif üye olabilir.
- **Giderler:** Tutar, kategori (`RENT`, `UTILITIES`, `GROCERIES`, `OTHER`), ödeyen kişi, tarih ve tekrarlayan gider bilgisiyle tam CRUD.
- **Gider paylaşımı:** her gider, oluşturulduğu anda evin aktif üyeleri arasında eşit olarak bölünür. Tutarlar kuruş cinsinden tam sayı olarak bölünür, böylece paylar her zaman tam olarak toplanır (ör. 100 TL / 3 = 33,34 + 33,33 + 33,33).
- **Ödemeler:** bir üyeden diğerine yapılan geri ödemeyi kaydedin (`POST`, `GET`, `DELETE`). Bir ödemeyi yalnızca gönderen kişi veya admin kaydedebilir ya da silebilir. Evden ayrılmış eski üyeler de gönderen veya alıcı olabilir; böylece evden taşınan biri borcunu kapatabilir (kaydı bir admin girer).
- **Bakiyeler:** her üyenin net bakiyesi. Pozitif değer evin ona borçlu olduğunu, negatif değer ise onun borçlu olduğunu gösterir. Giderler, paylar ve ödemelerden anlık hesaplanır; hiçbir yerde saklanmaz. Sıfır olmayan bakiyeye sahip eski üyeler görünmeye devam eder.
- **Borç kapatma önerileri ("kim kime öder"):** herkesin borcunu kapatan kısa bir öneri ödeme listesi. Açgözlü (greedy) algoritma kullanılır: en büyük borçlu, en büyük alacaklıya öder; en fazla n-1 ödeme önerilir.
- **Backend'de uygulanan yetkilendirme kuralları**
  - Bir evi ve verilerini yalnızca o evin aktif üyeleri görebilir. Üye olmayanlara `404` döner; böylece API hangi evlerin var olduğunu açığa çıkarmaz.
  - Evi yeniden adlandırma, silme ve üye yönetimini yalnızca admin yapabilir.
  - Bir gideri yalnızca ödeyen kişi veya admin düzenleyip silebilir.
  - Üye çıkarma "soft delete" ile yapılır (`leftAt` alanı doldurulur), böylece geçmiş giderler hâlâ gerçek bir kullanıcıya bağlı kalır.
  - Evin son admini çıkarılamaz (409).
- **Tutarlı hata yanıtları:** Doğrulama hataları, bulunamayan kayıtlar, çakışmalar ve yetkisiz işlemler doğru HTTP durum koduyla, her zaman aynı JSON formatında döner. Bozuk JSON, tanınmayan enum değerleri ve yanlış tipte path parametreleri `400` döner.

## Mimari

```
controller  →  service  →  repository  →  entity
   (HTTP)     (iş kuralları)  (Spring Data JPA)  (JPA / PostgreSQL)
```

- **Controller'lar** sade tutulur: isteği doğrular, service'i çağırır, DTO döner.
- **Service'ler** tüm iş kurallarını ve yetki kontrollerini içerir.
- **DTO'lar** (Java record'ları) API sözleşmesini veritabanı entity'lerinden ayırır; böylece şifre hash'i gibi iç alanlar yanıtlara sızmaz.
- **`GlobalExceptionHandler`** hataları tutarlı yanıtlara dönüştürür.
- **`JwtAuthenticationFilter`** her istekte `Authorization: Bearer <token>` başlığını okur.

### Veri modeli

`User`, `Household`, `Membership` (rolüyle birlikte kullanıcı ↔ ev ilişkisi), `Expense`, `ExpenseShare` (her üyenin bir gider için ne kadar borçlu olduğu), `Payment` (iki üye arasındaki bir geri ödeme).

## Kurulum

### Gereksinimler

- JDK 25
- Docker Desktop

### Yerelde çalıştırma

```bash
# 1. Ortam değişkenleri dosyasını oluşturun
cp .env.example .env

# 2. PostgreSQL'i başlatın
docker compose up -d

# 3. API'yi başlatın (Windows: gradlew.bat bootRun)
./gradlew bootRun
```

API `http://localhost:8080` adresinde çalışır.

İsteğe bağlı veritabanı arayüzü (pgAdmin, `http://localhost:5050`):

```bash
docker compose --profile tools up -d
```

### Yapılandırma

Ayarlar ortam değişkenlerinden okunur; yerel geliştirme için varsayılan değerler `application.yaml` içindedir. Tüm değişkenlerin listesi için [`.env.example`](.env.example) dosyasına bakın. Yerel geliştirme dışındaki her ortamda kendi `JWT_SECRET` değerinizi (en az 32 karakter) ve veritabanı şifrenizi belirleyin.

## API Dokümantasyonu

Uygulama çalışırken Swagger UI şu adreste açılır: **http://localhost:8080/swagger-ui/index.html**

Korumalı endpoint'leri Swagger'dan çağırmak için:

1. `POST /api/auth/register` ile bir kullanıcı oluşturun.
2. `POST /api/auth/login` ile giriş yapın ve yanıttaki `token` değerini kopyalayın.
3. **Authorize** butonuna tıklayıp token'ı yapıştırın.

### Endpoint'ler

| Metot | Yol | Açıklama |
|---|---|---|
| `POST` | `/api/auth/register` | Yeni kullanıcı kaydı |
| `POST` | `/api/auth/login` | Giriş yap ve JWT al |
| `POST` | `/api/households` | Ev oluştur |
| `GET` | `/api/households` | Üyesi olduğum evleri listele |
| `GET` | `/api/households/{id}` | Ev detayı |
| `PUT` | `/api/households/{id}` | Evi yeniden adlandır (admin) |
| `DELETE` | `/api/households/{id}` | Evi sil (admin) |
| `GET` | `/api/households/{id}/balances` | Her üyenin net bakiyesi |
| `GET` | `/api/households/{id}/settlements` | Önerilen ödemeler: kim kime öder |
| `POST` | `/api/households/{householdId}/members` | E-posta ile üye ekle (admin) |
| `GET` | `/api/households/{householdId}/members` | Aktif üyeleri listele |
| `DELETE` | `/api/households/{householdId}/members/{userId}` | Üyeyi çıkar (admin) |
| `POST` | `/api/households/{householdId}/expenses` | Gider ekle |
| `GET` | `/api/households/{householdId}/expenses` | Giderleri listele |
| `GET` | `/api/households/{householdId}/expenses/{expenseId}` | Gider detayı |
| `PUT` | `/api/households/{householdId}/expenses/{expenseId}` | Gideri güncelle (ödeyen kişi veya admin) |
| `DELETE` | `/api/households/{householdId}/expenses/{expenseId}` | Gideri sil (ödeyen kişi veya admin) |
| `POST` | `/api/households/{householdId}/payments` | Ödeme kaydet (gönderen veya admin) |
| `GET` | `/api/households/{householdId}/payments` | Ödemeleri listele |
| `DELETE` | `/api/households/{householdId}/payments/{paymentId}` | Ödemeyi sil (gönderen veya admin) |

`/api/auth/**` dışındaki tüm endpoint'ler geçerli bir JWT gerektirir.

## Yol Haritası

- Türkiye'deki ödeme yöntemleriyle (IBAN / FAST / Papara) borç kapatma takibi
- Otomatik testler
- Flyway ile veritabanı migration'ları
- Flutter mobil uygulama (geliştirme sürüyor)
- IBAN bilgisi içeren kullanıcı profili (daha hızlı geri ödeme için)
