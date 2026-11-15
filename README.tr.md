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
- Hesap silme (KVKK): kullanıcı şifresini girerek onaylar. Hesap silinmez, anonimleştirilir; çünkü geçmiş giderler ve ödemeler ev arkadaşlarının bakiyelerinin parçasıdır. Kullanıcı, diğer üyelerin bulunduğu bir evin kurucusuysa veya tek admin ise silme reddedilir; önce yetkiyi devretmesi gerekir. Kullanıcının tek üye olduğu evler silinir. Açık borçlar uyarı olarak gösterilir, silmeyi engellemez.

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
- **Fatura hatırlatmaları:** her tekrarlayan giderin `reminderDaysBefore` ayarı vardır: vade tarihinden `0` (kapalı), `1`, `3` veya `7` gün önce. Belirtilmezse varsayılan `3`'tür. İş her saat başı 09:00–21:00 arasında (Europe/Istanbul) çalışır ve ödeyene ile payı olan herkese `BILL_DUE_SOON` bildirimi gönderir. Her vade tarihi için yalnızca bir kez hatırlatma yapılır.

**Bakiyeler, borç kapatma önerileri ve ödemeler**
- Her üyenin net bakiyesi giderlerden, paylardan ve ödemelerden anlık olarak hesaplanır; hiçbir yerde saklanmaz. Sıfır olmayan bakiyeye sahip eski üyeler görünmeye devam eder.
- "Kim kime öder": doğrudan borçlara dayanan öneri ödemeler. Her iki kişi arasındaki gider payları, deposu devirleri ve ödemeler birbiriyle mahsup edilir; böylece herkes gerçekten borçlu olduğu kişiye öder. Her üyenin net bakiyesi bundan etkilenmez. Alacaklının IBAN'ı tanımlıysa öneriye eklenir.
- Ödemeler, iki üye arasındaki geri ödemeyi isteğe bağlı bir notla kaydeder. Bir ödemeyi yalnızca gönderen veya alan kişi kaydedebilir, düzenleyebilir ya da silebilir; admin, yalnızca kişilerden biri evden ayrıldıysa müdahale edebilir. Fazla ödeme yapılmasına izin verilir ve doğrudan bakiyelere yansır.

**Borç hatırlatmaları**
- Alacaklı, kendisine borcu olan bir ev arkadaşına isteğe bağlı en fazla 200 karakterlik bir notla "borcunu ödeyebilir misin" türünde nazik bir push gönderebilir. Hatırlatma yalnızca o borç varken kabul edilir.
- Alacaklı, aynı borçluya ev başına 24 saatte bir hatırlatma yapabilir. Bu süre içindeki denemeler `429` döner.

**Ekler**
- Fişler ve faturalar giderlere, havale dekontları ödemelere eklenebilir.
- Kabul edilen biçimler: JPEG, PNG, WEBP ve PDF. Dosya türü, dosya adına veya istemcinin gönderdiği `Content-Type` başlığına değil, dosyanın ilk baytlarına (magic bytes) bakılarak kontrol edilir.
- Sınırlar: dosya başına 10 MB, gider veya ödeme başına 5 ek.
- Dosyalar diskte, bilgileri (metadata) veritabanında saklanır. Dosyayı indirmek için aktif üyelik gerekir. Silme yetkisi; yükleyene, ödeyen veya gönderen kişiye ve adminlere aittir.

**Etkinlik geçmişi**
- Evde neyin değiştiğini en yeniden eskiye gösteren bir kayıt; ör. bir giderde `amount: 30000.00 -> 31000.00`. Kapsadığı olaylar: üyelerin katılması, ayrılması, çıkarılması, rol değişiklikleri, yeniden adlandırmalar ve kurucu devirleri; eklenen, düzenlenen (alan alan ve her pay tek tek), silinen ve tekrarlayan kurallarla üretilen giderler; eklenen, düzenlenen ve silinen ödemeler; görev değişiklikleri; depozito değişiklikleri ve devirler.
- Görev tamamlamaları kaydedilmez, çünkü çok sık tekrarlanırlar. Bunun yerine bir görev, son tamamlamalarını gösterir.

**Görevler**
- Ev arkadaşları arasında sabit bir sırayla dönen ortak görevler (ör. bulaşık, banyo temizliği). Bir evde en fazla 20 görev olabilir.
- Her görevin bir sırası (dönüş sırası) ve o an sırası olan bir kişisi vardır. Görev tamamlandı olarak işaretlenince sıra, tamamlayanın ardındaki kişiye geçer; görevi herkes tamamlandı olarak işaretleyebilir.
- Yeni üyeler sıraya otomatik eklenmez; kişiler görevi düzenleyerek eklenir.
- Sırası gelen kişiye `CHORE_TURN` bildirimi gider. Bir ev arkadaşı, sırası olan kişiyi hatırlatmayla dürtebilir; bu, görev ve kişi başına 24 saatte bir kez yapılabilir.

**Alışveriş listesi**
- Ortak bir ürün listesi (ör. süt, deterjan). Bir evde en fazla 100 ürün olabilir. Herkes ürün ekleyebilir, düzenleyebilir, "alındı" olarak işaretleyebilir veya silebilir.
- Alınan ürünler listede 7 gün kalır; sonra liste bir sonraki açılışta bunları otomatik olarak siler.
- Kasa işlemi, alınan ürünleri tek bir işlemde tek bir gidere dönüştürür ve normal gider kurallarını uygular. Bir gidere kaydedilmiş bir ürün tekrar açık listeye alınamaz. Bu gider silinirse ürünlerinin bağlantısı kalkar.

**Depozito ve taşınma (move-out)**
- Evin kira depozitosu ve her kişinin ondan ne kadar sahip olduğu. Paylar her zaman toplamı verir.
- Evden ayrılan kişi payını devreder; pay, diğer aktif üyeler arasında eşit olarak bölünür. Yeni gelen biri, mevcut sahiplerden bir tutar veya paylarına orantılı olarak depozitoya katılabilir.
- Her devir bir transfer olarak kaydedilir ve bakiyelere yansır: payı devralan kişi, bırakan kişiye bu tutarı borçludur. Adminler tek bir transferi geri alabilir. Depozito kaydının kendisi yalnızca hiç transfer yoksa silinebilir.

**Dönem raporu ve analizler**
- Herhangi bir tarih aralığı için dönem raporu (en fazla 366 gün; varsayılan: Türkiye saatiyle içinde bulunulan ay).
- Kişi bazında toplamlar: ödenen tutar, borçlu olunan tutar, net durum, gönderilen ve alınan ödemeler.
- "Baskın ödeyen" analizi: dönemdeki giderlerin en az %50'sini ödeyen üyeyi işaretler.
- Kategori dağılımı, 6 aylık trend ve ayrıntılı gider ile ödeme listesi.

**Bildirimler**
- Yeni gider, ödeme, davet, kabul edilen davet, görev sırasının değişmesi, görev veya ödeme hatırlatması ya da yaklaşan fatura gibi olaylar için uygulama içi gelen kutusu. Bildirimler 90 gün saklanır.
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
| `User` | Bir hesap: ad, e-posta, BCrypt şifre hash'i, isteğe bağlı IBAN. Silinen hesap anonimleştirilir ve satırı `deletedAt` alanıyla kalır |
| `Household` | Ortak bir ev. Kurucusunu (`createdBy`) tutar |
| `Membership` | Bir kullanıcıyı bir evle, bir rolle (`ADMIN` / `MEMBER`) ilişkilendirir. `leftAt` evden ayrılmış üyeyi işaretler |
| `Invitation` | Bir adminin kayıtlı bir kullanıcıya gönderdiği davet. Durum: `PENDING`, `ACCEPTED`, `DECLINED`, `CANCELLED` |
| `Expense` | Bir üyenin ödediği tutar: kategori, açıklama, tarih, paylaşım türü, tekrar bilgisi, zincir bağlantısı ve fatura hatırlatma ayarı (`reminderDaysBefore`: `0`, `1`, `3` veya `7`) |
| `ExpenseShare` | Bir giderde bir üyenin payı: borçlu olduğu tutar ve yüzde paylaşımlarda yüzdesi |
| `CategorySplitDefault` | Bir evin, bir kategori için kaydedilmiş varsayılan paylaşımı |
| `Payment` | Bir üyenin diğerine yaptığı geri ödeme: tutar, not ve zaman |
| `Attachment` | Bir fiş, fatura veya havale dekontunun bilgileri (tür, boyut, özgün ad); bir gidere veya ödemeye bağlıdır |
| `Notification` | Bir alıcı için tek bir gelen kutusu kaydı |
| `DeviceToken` | Bir kullanıcının bir cihazına ait push token'ı |
| `ActivityLog` | Bir ev olayına ait bir geçmiş kaydı: tür, ilgili kişiler, isteğe bağlı tutar ve alan değişiklikleri (`activity_logs`) |
| `Chore` | Bir ev görevi ve şu an sırası olan kişi (`chores`) |
| `ChoreRotationMember` | Bir kişinin, bir görevin sırasındaki yeri (`chore_rotation_members`) |
| `ChoreCompletion` | "Tamamlandı" kaydı: görevi kimin ne zaman yaptığı (`chore_completions`) |
| `ChoreReminder` | Sırası gelen kişiye gönderilen hatırlatma (`chore_reminders`) |
| `ShoppingItem` | Alışveriş listesi ürünü, alan kişi ve kaydedildiği gider (`shopping_items`) |
| `HouseholdDeposit` | Bir evin depozito toplamı (`household_deposits`) |
| `DepositShare` | Bir kişinin depozitodaki payı (`deposit_shares`) |
| `DepositTransfer` | Depozito payının iki kişi arasında devri. Bakiyelere yansır (`deposit_transfers`) |
| `PaymentReminder` | Alacaklıdan borçluya giden borç hatırlatması; 24 saatlik sınır için kullanılır (`payment_reminders`) |

Enum'lar: `MembershipRole`, `InvitationStatus`, `ExpenseCategory`, `SplitType`, `NotificationType`, `ActivityType`.

Temel fikir üç kavramın ayrılmasıdır:
- **Expense** = kim neyi ödedi.
- **ExpenseShare** = o giderde her kişinin ne kadar borçlu olduğu.
- **Payment** = iki kişi arasındaki gerçek bir geri ödeme.

Bakiyeler ve borç kapatma önerileri bu üç tablodan (ve depozito transferlerinden) her istekte **hesaplanır**, hiçbir yerde saklanmaz. Böylece bir gider veya ödeme düzenlendiğinde ya da silindiğinde verilerin birbiriyle uyumsuz hale gelmesi engellenir.

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
- **Ödeyen veya admin**: bir gideri ödeyen üye ya da evin bir admini (gider düzenleme ve silme).
- **Gönderen veya alan**: bir ödemenin iki tarafı. Admin, yalnızca bu kişilerden biri evden ayrılmışsa işlem yapabilir.
- **Kendisi veya admin**: kişinin kendisi (ayrıldıktan sonra da) ya da bir admin. Depozito taşınma (move-out) ve katılım işlemleri için kullanılır.

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
| `GET` | `/api/users/me/deletion-check` | Hesabımı silmenin ne yapacağını önizle: engeller, açık bakiyeler ve silinecek evler | Oturum açmış kullanıcı |
| `DELETE` | `/api/users/me` | Hesabımı sil (anonimleştir). Gövde: `{"password": "..."}`. 204 döner; yanlış şifrede `400`; ben başka üyeleri olan bir evin kurucusu veya tek admini isem `409` | Oturum açmış kullanıcı |

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
| `POST` | `/api/households/{householdId}/expenses/recurring/run` | Vadesi gelen kopyaları ve fatura hatırlatmalarını hemen oluştur/gönder (demo ve test için). `generated` ve `remindersSent` döner | Admin |

### Varsayılan paylaşımlar

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `GET` | `/api/households/{householdId}/split-defaults` | Her kategorinin varsayılan paylaşımını listele | Üye |
| `PUT` | `/api/households/{householdId}/split-defaults/{category}` | Bir kategorinin varsayılan paylaşımını değiştir | Admin |
| `DELETE` | `/api/households/{householdId}/split-defaults/{category}` | Bir kategorinin varsayılan paylaşımını sil (204) | Admin |

### Ödemeler

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `POST` | `/api/households/{householdId}/payments` | Ödeme kaydet (201) | Gönderen veya alan (yalnızca biri evden ayrılmışsa admin) |
| `GET` | `/api/households/{householdId}/payments` | Ödemeleri listele | Üye |
| `GET` | `/api/households/{householdId}/payments/{paymentId}` | Ödeme detayı | Üye |
| `PUT` | `/api/households/{householdId}/payments/{paymentId}` | Ödemeyi düzenle. Değişiklikten önce ve sonra gönderen veya alan olmanız gerekir | Gönderen veya alan (yalnızca biri evden ayrılmışsa admin) |
| `DELETE` | `/api/households/{householdId}/payments/{paymentId}` | Ödemeyi sil (204) | Gönderen veya alan (yalnızca biri evden ayrılmışsa admin) |

### Hatırlatmalar

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `POST` | `/api/households/{householdId}/reminders` | Bana borcu olan birine ödeme hatırlatması gönder. Gövde: `debtorUserId`, isteğe bağlı `note` (en fazla 200 karakter). 201; borcu yoksa `400`; son 24 saatte aynı kişiye hatırlatma yaptıysam `429` | Üye |
| `GET` | `/api/households/{householdId}/reminders/cooldowns` | Son 24 saatte hatırlattığım borçlular ve onları tekrar ne zaman hatırlatabileceğim | Üye |

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
| `GET` | `/api/households/{id}/settlements` | Önerilen ödemeler: kim kime öder (doğrudan borçlar, kişi çiftleri bazında mahsup edilmiş) | Üye |

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

### Etkinlik geçmişi

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `GET` | `/api/households/{householdId}/activity?limit=30&beforeId=` | Ev geçmişi, en yeniden eskiye. `limit` 1–100 (varsayılan 30; değerler sınırlandırılır); `beforeId` verilen id'den eski kayıtları döndürür | Üye |

### Görevler

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `GET` | `/api/households/{householdId}/chores` | Görevleri ada göre listele; sıra, o an sırası olan kişi ve son tamamlamalarla birlikte | Üye |
| `POST` | `/api/households/{householdId}/chores` | Görev oluştur. Gövde: `name` ve `rotationUserIds` (sıra; ilk kişi başlar). Evde en fazla 20 görev (201; dolunca `409`) | Üye |
| `PUT` | `/api/households/{householdId}/chores/{choreId}` | Adı ve sırayı değiştir. İsteğe bağlı `currentUserId` sırada olmalıdır (aksi halde `400`) | Üye |
| `DELETE` | `/api/households/{householdId}/chores/{choreId}` | Görevi sil (204) | Üye |
| `POST` | `/api/households/{householdId}/chores/{choreId}/done` | Tamamlandı olarak işaretle. Sıra, çağıran kişinin ardındaki kişiye geçer. Herkes yapabilir | Üye |
| `POST` | `/api/households/{householdId}/chores/{choreId}/remind` | Sırası gelen kişiyi dürt (201). Sıra sizdeyse veya kimsedeyse `400`; son 24 saatte aynı kişiyi zaten hatırlattıysanız `429` | Üye |

### Alışveriş listesi

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `GET` | `/api/households/{householdId}/shopping-items` | Ürünleri listele: önce açık ürünler, sonra alınanlar. Bu çağrıda 7 günden eski alınan ürünler silinir | Üye |
| `POST` | `/api/households/{householdId}/shopping-items` | `name` ve isteğe bağlı `note` ile ürün ekle (201). Evde en fazla 100 ürün (dolunca `409`) | Üye |
| `PUT` | `/api/households/{householdId}/shopping-items/{itemId}` | Ürünün adını ve notunu düzenle | Üye |
| `DELETE` | `/api/households/{householdId}/shopping-items/{itemId}` | Ürünü sil (204) | Üye |
| `POST` | `/api/households/{householdId}/shopping-items/{itemId}/bought` | Alındı olarak işaretle. Zaten alınmışsa ilk alan kişi korunur | Üye |
| `POST` | `/api/households/{householdId}/shopping-items/{itemId}/restore` | Alınmış ürünü açık listeye geri al. Ürün zaten bir gidere kaydedilmişse `409` | Üye |
| `DELETE` | `/api/households/{householdId}/shopping-items/bought` | Alınan tüm ürünleri sil (204) | Üye |
| `POST` | `/api/households/{householdId}/shopping-items/checkout` | Verilen `itemIds` ürünlerini TEK bir gider olarak kaydet (`expense` alanı normal gider isteğiyle aynı biçimde). Gerekirse ürünler çağıran kişi tarafından alındı olarak işaretlenir. Tek işlemde çalışır (201). Aynı ürün id'si tekrarlanırsa `400`; bir ürün zaten bir giderdeyse `409` | Üye |

### Depozito ve taşınma

| Metot | Yol | Açıklama | Erişim |
|---|---|---|---|
| `GET` | `/api/households/{householdId}/deposit` | Depozito toplamı, her kişinin payı, dağıtılmamış tutar ve transfer geçmişi | Üye |
| `PUT` | `/api/households/{householdId}/deposit` | Depozitoyu kur veya düzelt. Gövde: `totalAmount` ve `shares`; paylar toplama tam eşit olmalıdır (aksi halde `400`). Her değişiklik kaydedilir | Üye |
| `DELETE` | `/api/households/{householdId}/deposit` | Depozito kaydını ve tüm payları sil (204). Transfer varken `409`; önce onları silin | Admin |
| `POST` | `/api/households/{householdId}/deposit/move-out` | Bir kişinin tüm payını diğer aktif üyelere eşit olarak devret. Gövde: `userId`. Devredilecek pay yoksa `400` | Kendisi veya admin |
| `POST` | `/api/households/{householdId}/deposit/join` | Yeni gelen bir kişinin mevcut sahiplerden depozitoya katılmasını sağla. Gövde: `userId`, isteğe bağlı `amount` (varsayılan: eşit pay). Zaten payı varsa veya aktif üye değilse `400` | Kendisi veya admin |
| `DELETE` | `/api/households/{householdId}/deposit/transfers/{transferId}` | Tek bir transferi geri al: tutar bırakana geri döner (alan kişi o kadar tutarı artık tutmuyorsa `400`) | Admin |

## Yol Haritası

- Bulut dağıtımı: API için container imajı, yönetilen PostgreSQL ve ekler için yerel disk yerine nesne depolama
- İş kurallarını kapsayan otomatik testler: paylaştırma, bakiyeler, tekrar tarihleri ve davet limitleri
- Flyway ile veritabanı migration'ları (şema şu anda `ddl-auto: update` ile yönetiliyor)
- Flutter istemcisinin Google Play'de yayınlanması: [roommate-expense-app](https://github.com/dogukanTufan00/roommate-expense-app)
- Yenileme (refresh) token'ları ve çıkış yapıldığında token iptali (şu anda çıkış yalnızca push token'ını siler)
- Önerilen borç kapatma ödemelerinden tek dokunuşla ödeme kaydı oluşturma
- Giriş ve kayıt işlemleri için istek sınırlaması (rate limiting)
