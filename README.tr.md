[English](README.md) | **Türkçe**

# Ortak Gider Takip Sistemi

Ev arkadaşlarıyla yaşayanlar için ortak ev giderlerini takip eden bir REST API. Türkiye'deki öğrenciler ve genç çalışanlar için yerelleştirilmiş bir Splitwise alternatifi.

Kullanıcılar kayıt olur, bir ev (household) oluşturur, ev arkadaşlarını davet eder ve kimin neyi ödediğini (kira, faturalar, market, …) kaydeder. Her gider eşit, yüzdeye göre veya tam tutarlarla paylaştırılabilir. API, her üyenin güncel bakiyesini hesaplar ve herkesin borcunu kapatmak için gereken en az sayıda ödemeyi önerir.

API, mobil uygulamanın backend'idir: [roommate-expense-app](https://github.com/dogukanTufan00/roommate-expense-app) (Flutter, öncelikle Android).

## Kullanılan Teknolojiler

| Alan | Teknoloji |
|---|---|
| Dil | Java 25 |
| Framework | Spring Boot 4.1 (Web MVC, Data JPA, Security, Validation, Scheduling) |
| Veritabanı | PostgreSQL 16 (Docker) |
| Kimlik doğrulama | JWT (stateless, `jjwt` 0.13); şifreler BCrypt ile hash'lenir |
| Push bildirimleri | Firebase Admin SDK 9.12 (Firebase Cloud Messaging), isteğe bağlı |
| API dokümantasyonu | Swagger UI (`springdoc-openapi` 3.1) |
| Build | Gradle, Lombok |

## Özellikler

**Hesaplar ve kimlik doğrulama**
- Kayıt ve giriş. Şifreler BCrypt ile hash'lenir (en az 8 karakter); giriş başarılı olursa diğer tüm isteklerde kullanılacak bir JWT döner.
- Ad ve isteğe bağlı Türk IBAN'ı içeren profil. IBAN, ISO 13616 mod-97 kontrol algoritmasıyla doğrulanır; yazım hataları kaydedilmeden reddedilir.
- Mevcut şifreyi isteyen şifre değiştirme.

**Evler, roller ve davetler**
- Her kullanıcı ev oluşturabilir. Oluşturan kişi evin **kurucusu (founder)** ve **admin**'i olur.
- Roller: kurucu, adminler ve üyeler. Adminler üyelerin rolünü değiştirebilir ve davetleri yönetebilir. Kurucu, hâlâ üyeyken başka hiçbir kullanıcı tarafından değiştirilemez veya çıkarılamaz; kurucu rolünü başka bir üyeye devredebilir.
- Bir evde en fazla 3 aktif üye bulunabilir. Bekleyen davetler de bu limite dahildir.
- Adminler, kayıtlı kullanıcıları e-posta adresiyle davet eder. Davet edilen kişi daveti kabul veya reddetmelidir; kimse onayı olmadan eve katılmaz.
- Üyeler evden ayrılabilir. Evin son admini ayrılamaz ve admin rolü düşürülemez.

**Giderler ve paylaştırma**
- Tutar, kategori (`RENT`, `UTILITIES`, `GROCERIES`, `OTHER`), ödeyen kişi, tarih ve açıklama ile tam CRUD.
- Üç paylaşım türü: `EQUAL` (eşit), `PERCENT` (yüzde) ve `AMOUNT` (tutar). Ödeyen kişi her giderin hangi üyeler arasında paylaşılacağını seçer.
- Paylar kuruş cinsinden tam sayı olarak hesaplanır; böylece paylar her zaman tutarın tamamına eşit olur (ör. 100 TL / 3 = 33,34 + 33,33 + 33,33).
- Her kategori için kaydedilebilir bir varsayılan paylaşım (kişi başı yüzde) tanımlanabilir. Bu varsayılan yalnızca aynı kişiler hâlâ aktif üye olduğu sürece kullanılır.
- Bir gideri yalnızca ödeyen kişi veya admin düzenleyebilir ya da silebilir.

**Tekrarlayan giderler**
- Bir gider her ay aynı günde tekrarlanabilir (kira, internet, abonelikler).
- Her gece 00:05'te (Europe/Istanbul) çalışan bir iş, vadesi gelmiş kopyaları oluşturur. Bu iş uygulama başladığında ve tekrarlayan bir gider kaydedildikten hemen sonra da çalışır.
- Kaçırılan aylar telafi edilir; bir çalıştırmada en fazla 12 ay. Aynı kişiler hâlâ üye ise kopya önceki paylaşımı kullanır.
- Ödeyen kişi evden ayrıldıysa zincir durur; böylece kimse ödemediği bir giderden sorumlu tutulmaz.
- Adminler, demo ve test amacıyla kendi evleri için oluşturmayı manuel olarak tetikleyebilir.

**Bakiyeler, borç kapatma önerileri ve ödemeler**
- Her üyenin net bakiyesi giderlerden, paylardan ve ödemelerden anlık olarak hesaplanır; hiçbir yerde saklanmaz. Sıfır olmayan bakiyeye sahip eski üyeler görünmeye devam eder.
- "Kim kime öder": herkesin borcunu kapatan kısa bir öneri ödeme listesi (açgözlü algoritma, en fazla n-1 ödeme). Alacaklının IBAN'ı tanımlıysa öneriye eklenir.
- Ödemeler, iki üye arasındaki geri ödemeyi isteğe bağlı bir notla kaydeder. Ödemeyi gönderen kişi veya admin düzenleyebilir ya da silebilir. Fazla ödeme yapılmasına izin verilir ve doğrudan bakiyelere yansır.

**Ekler**
- Fişler ve faturalar giderlere, havale dekontları ödemelere eklenebilir.
- Kabul edilen biçimler: JPEG, PNG, WEBP ve PDF. Dosya türü, dosya adına veya istemcinin gönderdiği `Content-Type` başlığına değil, dosyanın ilk baytlarına (magic bytes) bakılarak kontrol edilir.
- Sınırlar: dosya başına 10 MB, gider veya ödeme başına 5 ek.
- Dosyalar diskte, bilgileri (metadata) veritabanında saklanır. Dosyayı indirmek için aktif üyelik gerekir. Silme yetkisi; yükleyene, ödeyen veya gönderen kişiye ve adminlere aittir.

**Dönem raporu ve analizler**
- Herhangi bir tarih aralığı için dönem raporu (en fazla 366 gün; varsayılan: Türkiye saatiyle içinde bulunulan ay).
- Kişi bazında toplamlar: ödenen tutar, borçlu olunan tutar, net durum, gönderilen ve alınan ödemeler.
- "Baskın ödeyen" analizi: dönemdeki giderlerin en az %50'sini ödeyen üyeyi işaretler.
- Kategori dağılımı, 6 aylık trend ve ayrıntılı gider ile ödeme listesi.

**Bildirimler**
- Yeni gider, ödeme, davet veya kabul edilen davet gibi olaylar için uygulama içi gelen kutusu. Bildirimler 90 gün saklanır.
- İsteğe bağlı Firebase Cloud Messaging push bildirimleri. Her cihaz kendi token'ını kaydeder; Firebase'in geçersiz saydığı token'lar otomatik silinir. Firebase yapılandırılmadığında uygulama normal çalışır, yalnızca push bildirimleri devre dışı kalır.

**API kalitesi**
- Tutarlı JSON hata yanıtları ve doğru HTTP durum kodları.
- Ev verilerine erişimi olmayan kullanıcılar `404` alır; böylece API hangi evlerin var olduğunu açığa çıkarmaz.
- Swagger UI ile etkileşimli dokümantasyon; JWT için "Authorize" butonu dahil.

## Mimari

```
controller  →  service  →  repository  →  entity
   (HTTP)     (iş kuralları)  (Spring Data JPA)  (JPA / PostgreSQL)
```

- **Controller'lar** sade tutulur: isteği doğrular, service'i çağırır ve DTO döner.
- **Service'ler** tüm iş kurallarını ve yetki kontrollerini içerir.
- **DTO'lar** (Java record'ları) API sözleşmesini veritabanı entity'lerinden ayırır; böylece şifre hash'i gibi iç alanlar sunucudan asla çıkmaz.
- **Olaylar** (`event` paketi): service'ler bir işlemden sonra küçük bir olay yayınlar; bildirim ve tekrarlayan gider dinleyicileri, işlemin veritabanına kaydedilmesinden sonra bu olaylara tepki verir.
- **`GlobalExceptionHandler`** tüm istisnaları aynı JSON hata gövdesine dönüştürür.
- **`JwtAuthenticationFilter`** her istekte `Authorization: Bearer <token>` başlığını okur.

### Veri modeli

| Entity | Görevi |
|---|---|
| `User` | Bir hesap: ad, e-posta, BCrypt şifre hash'i, isteğe bağlı IBAN |
| `Household` | Ortak bir ev. Kurucusunu (`createdBy`) tutar |
| `Membership` | Bir kullanıcıyı bir evle, bir rolle (`ADMIN` / `MEMBER`) ilişkilendirir. `leftAt` evden ayrılmış üyeyi işaretler |
| `Invitation` | Bir adminin kayıtlı bir kullanıcıya gönderdiği davet. Durum: `PENDING`, `ACCEPTED`, `DECLINED`, `CANCELLED` |
| `Expense` | Bir üyenin ödediği tutar: kategori, açıklama, tarih, paylaşım türü, tekrar bilgisi ve zincir bağlantısı |
| `ExpenseShare` | Bir giderde bir üyenin payı: borçlu olduğu tutar ve yüzde paylaşımlarda yüzdesi |
| `CategorySplitDefault` | Bir evin, bir kategori için kaydedilmiş varsayılan paylaşımı |
| `Payment` | Bir üyenin diğerine yaptığı geri ödeme: tutar, not ve zaman |
| `Attachment` | Bir fiş, fatura veya havale dekontunun bilgileri (tür, boyut, özgün ad); bir gidere veya ödemeye bağlıdır |
| `Notification` | Bir alıcı için tek bir gelen kutusu kaydı |
| `DeviceToken` | Bir kullanıcının bir cihazına ait push token'ı |

Enum'lar: `MembershipRole`, `InvitationStatus`, `ExpenseCategory`, `SplitType`, `NotificationType`.

Temel fikir üç kavramın ayrılmasıdır:
- **Expense** = kim neyi ödedi.
- **ExpenseShare** = o giderde her kişinin ne kadar borçlu olduğu.
- **Payment** = iki kişi arasındaki gerçek bir geri ödeme.

Bakiyeler ve borç kapatma önerileri bu üç tablodan her istekte **hesaplanır**, hiçbir yerde saklanmaz. Böylece bir gider veya ödeme düzenlendiğinde ya da silindiğinde verilerin birbiriyle uyumsuz hale gelmesi engellenir.

## Tasarım Kararları

- **Entity yerine DTO.** Controller'lar hiçbir zaman entity döndürmez. Bu sayede şifre hash'leri yanıtlara girmez, lazy-loading ve JSON döngü sorunları önlenir ve API sözleşmesi veritabanı şemasından bağımsız değişebilir.
- **Bakiyelerin hesaplanması.** Tek bir doğruluk kaynağı vardır: giderler, paylar ve ödemeler. Saklanan bakiyeler her düzenleme ve silmede güncellenmek zorunda kalırdı; hesaplamak daha basit ve yapısı gereği doğrudur. Bir ev ölçeğinde maliyeti ihmal edilebilir.
- **Kuruş bazında paylaştırma.** Kayan noktalı bölme kuruş kaybettirir veya kazandırır (150,50 / 3 tam sayı değildir). Tutarlar tam sayı kuruşa bölünür, artan kuruşlar ilk paylara birer birer dağıtılır; böylece paylar her zaman toplama eşit olur.
- **Magic byte kontrolü.** Dosya adı ve `Content-Type` başlığı istemci tarafından seçilir ve sahte olabilir (adı `receipt.pdf` yapılmış bir `.exe` bu kontrolden geçerdi). Bir dosya biçiminin ilk baytları biçimin kendisi tarafından belirlendiği için kontrol bu baytlar üzerinden yapılır.
- **Bildirim ve tekrarlayan giderler için `AFTER_COMMIT` olayları.** Bildirimler, push'lar ve tekrarlayan kopyalar yalnızca veritabanı işlemi kaydedildikten sonra çalışır. Geri alınmış bir değişikliği asla duyurmaz veya kopyalamaz; bu aşamadaki bir hata, başarılı bir isteği hataya çevirmez.
- **Dosyalar diskte, bilgiler veritabanında.** İkili dosyalar veritabanının dışında tutulur. Her dosyaya rastgele bir UUID adı verilir; böylece kullanıcının verdiği ad yol geçişine (path traversal) veya çakışmaya yol açamaz. Dosyalar yalnızca veritabanı değişikliği kaydedildikten sonra silinir. Üretim ortamında bu katman Amazon S3 gibi bir nesne depolama servisiyle değiştirilebilir; kodun geri kalanı değişmez.
- **Üyelikte soft delete.** Evden ayrılmak satırı silmez, `leftAt` alanını doldurur. Geçmiş giderler gerçek bir kullanıcıya bağlı kalır ve borçlar kapatılana kadar görünür durumda kalır.
- **Başlangıçta `Locale.ROOT` ("Türkçe I" hatası).** Varsayılan dili Türkçe olan bir makinede `"HIGH".toLowerCase()` çağrısı `"hıgh"` (noktasız ı) üretir. Firebase SDK gibi kütüphaneler enum adlarını küçük harfe çevirir ve push mesajları reddedilirdi. Bu yüzden JVM'in varsayılan dili uygulama başlarken `Locale.ROOT` olarak ayarlanır; Türkçe metin gerektiğinde ise açıkça Türkçe biçimlendirilir.

## Kurulum

### Gereksinimler

- JDK 25 (`build.gradle` içindeki Gradle toolchain bunu ister)
- Docker Desktop

### Yerelde çalıştırma

Windows (PowerShell):

```powershell
# 1. Ortam değişkenleri dosyasını oluşturun (docker compose bu dosyayı okur)
Copy-Item .env.example .env

# 2. PostgreSQL 16'yı başlatın
docker compose up -d

# 3. API'yi başlatın
.\gradlew.bat bootRun
```

macOS / Linux: `cp .env.example .env` ve `./gradlew bootRun` kullanın.

API `http://localhost:8080` adresinde çalışır.

İsteğe bağlı veritabanı arayüzü (pgAdmin, `http://localhost:5050`):

```powershell
docker compose --profile tools up -d
```

### Ortam değişkenleri

Spring Boot, `.env` dosyasını kendiliğinden okumaz. `src/main/resources/application.yaml` içindeki varsayılan değerler `.env.example` ile aynıdır; bu yüzden `bootRun` hiçbir değişken dışa aktarılmadan çalışır. Bir değişkeni yalnızca farklı bir değere ihtiyaç duyduğunuzda tanımlayın. Yerel geliştirme dışındaki her ortamda kendi `JWT_SECRET` değerinizi ve veritabanı şifrenizi belirleyin.

| Değişken | Varsayılan | Açıklama |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/roommate_db` | PostgreSQL veritabanının JDBC adresi |
| `DB_USERNAME` | `roommate_user` | Veritabanı kullanıcısı |
| `DB_PASSWORD` | `change_me` | Veritabanı şifresi (yerel geliştirme dışında değiştirin) |
| `JWT_SECRET` | yer tutucu değer | HS256 imza anahtarı. En az 32 karakter olmalıdır |
| `JWT_EXPIRATION_MS` | `86400000` (24 saat) | JWT'nin milisaniye cinsinden geçerlilik süresi |
| `UPLOADS_DIR` | `./uploads` | Ek dosyalarının klasörü. Göreli yollar, uygulamanın başlatıldığı klasöre göre çözülür. Git tarafından yok sayılır |
| `FIREBASE_CREDENTIALS` | boş | Firebase servis hesabı JSON dosyasının yolu; dosya deponun **dışında** tutulmalıdır. Boşsa push bildirimleri kapalıdır, diğer her şey çalışır |

Firebase anahtarını yalnızca geçerli PowerShell oturumu için tanımlamak:

```powershell
$env:FIREBASE_CREDENTIALS = "C:\secrets\firebase-adminsdk.json"
.\gradlew.bat bootRun
```

## API Dokümantasyonu

Uygulama çalışırken Swagger UI şu adreste açılır: **http://localhost:8080/swagger-ui/index.html**

Korumalı endpoint'leri Swagger'dan çağırmak için:

1. `POST /api/auth/register` ile bir kullanıcı oluşturun.
2. `POST /api/auth/login` ile giriş yapın ve yanıttaki `token` değerini kopyalayın.
3. **Authorize** butonuna tıklayıp token'ı yapıştırın.

### Erişim kuralları

- **Herkese açık**: token gerekmez.
- **Oturum açmış kullanıcı**: geçerli herhangi bir JWT; endpoint çağıranın kendi verileri üzerinde çalışır.
- **Üye**: evin aktif bir üyesi. Üye olmayanlar `404` alır.
- **Admin**: `ADMIN` rolüne sahip aktif bir üye.
- **Kurucu**: evi oluşturan kişi, hâlâ aktif üye olduğu sürece.

`/api/auth/**` ve Swagger yolları dışındaki tüm endpoint'ler geçerli bir JWT gerektirir.

### Kimlik doğrulama

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `POST` | `/api/auth/register` | Yeni kullanıcı kaydı (201) | Herkese açık |
| `POST` | `/api/auth/login` | Giriş yap ve JWT al | Herkese açık |

### Profil ve cihazlar

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `GET` | `/api/users/me` | Profilimi getir | Oturum açmış kullanıcı |
| `PUT` | `/api/users/me` | Adı ve IBAN'ı güncelle (boş IBAN, IBAN'ı kaldırır) | Oturum açmış kullanıcı |
| `PUT` | `/api/users/me/password` | Şifreyi değiştir (204) | Oturum açmış kullanıcı |
| `PUT` | `/api/users/me/devices` | Bu cihazın push token'ını kaydet (204) | Oturum açmış kullanıcı |
| `DELETE` | `/api/users/me/devices` | Push token'ını kaldır, ör. çıkış yapıldığında (204) | Oturum açmış kullanıcı |

### Evler

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `POST` | `/api/households` | Ev oluştur; oluşturan kişi kurucu ve admin olur (201) | Oturum açmış kullanıcı |
| `GET` | `/api/households` | Üyesi olduğum evleri listele | Oturum açmış kullanıcı |
| `GET` | `/api/households/{id}` | Ev detayı | Üye |
| `PUT` | `/api/households/{id}` | Evi yeniden adlandır | Admin |
| `DELETE` | `/api/households/{id}` | Evi ve tüm verilerini sil (204) | Kurucu (kurucu ayrıldıysa herhangi bir admin) |
| `POST` | `/api/households/{id}/transfer-ownership` | Kurucu rolünü başka bir aktif üyeye devret | Kurucu |

### Üyeler

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `GET` | `/api/households/{householdId}/members` | Aktif üyeleri listele | Üye |
| `PATCH` | `/api/households/{householdId}/members/{userId}` | Bir üyenin rolünü değiştir (`ADMIN` / `MEMBER`) | Admin |
| `DELETE` | `/api/households/{householdId}/members/{userId}` | Üyeyi çıkar (204). Bir üye, kendi kullanıcı kimliğini kullanarak evden ayrılabilir | Admin veya üyenin kendisi |

### Davetler

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `POST` | `/api/households/{householdId}/invitations` | Kayıtlı bir kullanıcıyı e-posta ile davet et (201) | Admin |
| `GET` | `/api/households/{householdId}/invitations` | Bekleyen davetleri listele | Üye |
| `DELETE` | `/api/households/{householdId}/invitations/{invitationId}` | Bekleyen bir daveti iptal et (204) | Admin |

### Davetlerim

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `GET` | `/api/invitations` | Bekleyen davetlerimi, en yenisi önce, listele | Oturum açmış kullanıcı |
| `POST` | `/api/invitations/{invitationId}/accept` | Daveti kabul et ve eve katıl | Davet edilen kişi |
| `POST` | `/api/invitations/{invitationId}/decline` | Daveti reddet (204) | Davet edilen kişi |

### Giderler

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `POST` | `/api/households/{householdId}/expenses` | İsteğe bağlı paylaşımla gider oluştur (201) | Üye |
| `GET` | `/api/households/{householdId}/expenses` | Giderleri listele | Üye |
| `GET` | `/api/households/{householdId}/expenses/{expenseId}` | Gider detayı | Üye |
| `PUT` | `/api/households/{householdId}/expenses/{expenseId}` | Gideri güncelle | Ödeyen kişi veya admin |
| `DELETE` | `/api/households/{householdId}/expenses/{expenseId}` | Gideri sil (204) | Ödeyen kişi veya admin |

### Tekrarlayan giderler

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `GET` | `/api/households/{householdId}/expenses/recurring` | Tekrarlayan zincirleri ve sonraki kopyanın tarihini listele | Üye |
| `POST` | `/api/households/{householdId}/expenses/recurring/run` | Vadesi gelen kopyaları hemen oluştur (demo ve test için) | Admin |

### Varsayılan paylaşımlar

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `GET` | `/api/households/{householdId}/split-defaults` | Her kategorinin varsayılan paylaşımını listele | Üye |
| `PUT` | `/api/households/{householdId}/split-defaults/{category}` | Bir kategorinin varsayılan paylaşımını değiştir | Admin |
| `DELETE` | `/api/households/{householdId}/split-defaults/{category}` | Bir kategorinin varsayılan paylaşımını sil (204) | Admin |

### Ödemeler

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `POST` | `/api/households/{householdId}/payments` | Ödeme kaydet (201) | Gönderen veya admin |
| `GET` | `/api/households/{householdId}/payments` | Ödemeleri listele | Üye |
| `GET` | `/api/households/{householdId}/payments/{paymentId}` | Ödeme detayı | Üye |
| `PUT` | `/api/households/{householdId}/payments/{paymentId}` | Ödemeyi düzenle. Göndereni başka bir üyeyle değiştirmek yalnızca admin için mümkündür | Gönderen veya admin |
| `DELETE` | `/api/households/{householdId}/payments/{paymentId}` | Ödemeyi sil (204) | Gönderen veya admin |

### Ekler

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `POST` | `/api/households/{householdId}/expenses/{expenseId}/attachments` | Fiş veya fatura yükle (multipart, 201) | Üye |
| `GET` | `/api/households/{householdId}/expenses/{expenseId}/attachments` | Bir giderin eklerini listele | Üye |
| `POST` | `/api/households/{householdId}/payments/{paymentId}/attachments` | Havale dekontu yükle (multipart, 201) | Üye |
| `GET` | `/api/households/{householdId}/payments/{paymentId}/attachments` | Bir ödemenin eklerini listele | Üye |
| `GET` | `/api/households/{householdId}/attachments/{attachmentId}/file` | Dosyayı indir veya görüntüle | Üye |
| `DELETE` | `/api/households/{householdId}/attachments/{attachmentId}` | Eki sil (204) | Yükleyen, ödeyen veya gönderen kişi ya da admin |

### Bakiyeler ve borç kapatma önerileri

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `GET` | `/api/households/{id}/balances` | Her üyenin net bakiyesi | Üye |
| `GET` | `/api/households/{id}/settlements` | Önerilen ödemeler: kim kime öder | Üye |

### Dönem raporu

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `GET` | `/api/households/{id}/statement?from=yyyy-AA-gg&to=yyyy-AA-gg` | Dönem raporu ve kişi bazında istatistikler. İki tarihi birlikte verin ya da hiçbirini vermeyin | Üye |

### Bildirimler

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `GET` | `/api/notifications?limit=50` | Bildirimlerim, en yenisi önce (limit 1–100) | Oturum açmış kullanıcı |
| `GET` | `/api/notifications/unread-count` | Okunmamış bildirim sayısı | Oturum açmış kullanıcı |
| `POST` | `/api/notifications/{notificationId}/read` | Bir bildirimi okundu olarak işaretle (204) | Oturum açmış kullanıcı (alıcı) |
| `POST` | `/api/notifications/read-all` | Tüm bildirimlerimi okundu olarak işaretle (204) | Oturum açmış kullanıcı |

## Yol Haritası

- Bulut dağıtımı: API için container imajı, yönetilen PostgreSQL ve ekler için yerel disk yerine nesne depolama
- İş kurallarını kapsayan otomatik testler: paylaştırma, bakiyeler, tekrar tarihleri ve davet limitleri
- Flyway ile veritabanı migration'ları (şema şu anda `ddl-auto: update` ile yönetiliyor)
- Flutter istemcisinin Google Play'de yayınlanması: [roommate-expense-app](https://github.com/dogukanTufan00/roommate-expense-app)
- Yenileme (refresh) token'ları ve çıkış yapıldığında token iptali (şu anda çıkış yalnızca push token'ını siler)
- Önerilen borç kapatma ödemelerinden tek dokunuşla ödeme kaydı oluşturma
- Giriş ve kayıt işlemleri için istek sınırlaması (rate limiting)
