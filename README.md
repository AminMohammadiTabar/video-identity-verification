# Video Identity Verification System

> سامانه جامع احراز هویت ویدئویی مبتنی بر سه میکروسرویس کاملاً مستقل Spring Boot در یک Workspace مشترک:
> - **`auth/`** (سرویس احراز هویت و مدیریت فرآیند — پورت 8080)
> - **`dms/`** (سرویس مدیریت اسناد و ذخیره‌سازی امن فایل — پورت 8081)
> - **`workflow/`** (سرویس ممیزی و اسکلت فرآیند — پورت 8082)
>
> ⚠️ **اصول بنیادین معماری:** این سه سرویس به عنوان پروژه‌های کاملاً مجزا (Sibling) طراحی شده‌اند. هیچ‌گونه `parent pom` مشترک، وابستگی Maven بین‌سرویسی یا ارتباط مستقیم دیتابیسی مجاز نیست.

---

## ۱. وضعیت کیفی و اعتبارسنجی (Verified Quality Gate)

تمامی آزمون‌های ساختاری، نحوی، و تست‌های یکپارچه‌سازی در محیط **Java 25** با موفقیت ۱۰۰٪ اجرا و تأیید شده‌اند:

| بررسی کیفی | دامنه بررسی | نتیجه | وضعیت |
| :--- | :--- | :---: | :---: |
| **کنترل ساختار و قوانین معماری** | اسکریپت `verify_workspace.py` | **92 از 92** | ✅ **PASS (100%)** |
| **اعتبارسنجی سینتکس جاوا (Java 25)** | اسکریپت `JavaSyntaxCheck.java` | **111 فایل سورس** | ✅ **0 Syntax Errors** |
| **تست‌های سرویس `auth`** | `mvnw test -f auth/pom.xml` | **31 تست** (29 موفق، 2 Skip دیتابیس خارجی) | ✅ **BUILD SUCCESS** |
| **تست‌های سرویس `dms`** | `mvnw test -f dms/pom.xml` | **17 تست** (15 موفق، 2 Skip دیتابیس خارجی) | ✅ **BUILD SUCCESS** |
| **تست‌های سرویس `workflow`** | `mvnw test -f workflow/pom.xml` | **4 تست** (4 موفق) | ✅ **BUILD SUCCESS** |

---

## ۲. مشخصات و وظایف سرویس‌ها

```
                           +--------------------------+
                           |     Client Browser       |
                           +--------------------------+
                                    |        |
             (HTML Session / CSRF)  |        |  (Protected Video Stream)
                                    v        v
                           +--------------------------+
                           |   AUTH Service (:8080)   |
                           +--------------------------+
                                    |
          (Internal HTTP /          | W3C Traceparent /
           AUTH_SERVICE Basic Auth) v Scoped Access Policy
                           +--------------------------+
                           |    DMS Service (:8081)   |
                           +--------------------------+
                                    |
                                    v Local Disk Storage
                  SUBSYSTEM/YYYY/MM/DD/OWNER_ID/UUID.ext
```

### ۲.۱. سرویس احراز هویت (`auth` — پورت 8080)
- **ثبت‌نام و ورود:** فرم‌های HTML با قالب Mustache، ثبت‌نام REST API، احراز هویت سشن‌محور Spring Security و هش گذرواژه با BCrypt.
- **حفاظت CSRF:** فعال بودن اجباری توکن CSRF روی تمامی فرم‌ها و APIهای تغییردهنده داده.
- **آپلود ویدئوی احراز هویت:** دریافت فایل از کاربر مرورگر و ارسال مستقیم به DMS از طریق کلاینت امن سرور-به-سرور (`HttpDmsGateway`).
- **مدل مبتنی بر تلاش (Attempt-based History):** هر ارسال ویدئو پس از رد شدن درخواست (`REJECTED`) یک تلاش جدید با شناسه و شماره مستقل (`attempt_number + 1`) ایجاد می‌کند و سوابق قبلی برای رعایت قوانین ممیزی غیرقابل تغییر باقی می‌مانند.
- **بازیابی خطای آپلود:** شکست‌های گذرا در مرحله آپلود به DMS وضعیت `UPLOAD_FAILED` گرفته و امکان تلاش مجدد همان شماره تلاش را دارند.
- **بررسی ادمین:** مشاهده امن ویدئو از طریق پراکسی سرور و ثبت تصمیم (`APPROVED` یا `REJECTED` همراه با دلیل رد درخواست).
- **صف ایمیل تراکنشی (Transactional Outbox):** ثبت پیام‌های نوتیفیکیشن در همان تراکنش تصمیم‌گیری در جدول `auth.email_outbox` و ارسال غیرهمزمان از طریق Worker پس‌زمینه با الگوی Exponential Backoff.
- **ممیزی مستقل:** ثبت وقایع در جدول `auth.audit_events`.

### ۲.۲. سرویس مدیریت اسناد (`dms` — پورت 8081)
- **ذخیره‌سازی دیسک:** ذخیره‌سازی فایل‌ها طبق ساختار دقیق:
  ```text
  STORAGE_ROOT/SUBSYSTEM/YYYY/MM/DD/OWNER_ID/UUID.ext
  ```
- **امنیت فایل‌سیستم:** ممانعت قطعی از حملات Path Traversal و رد هرگونه پیوند نمادین (Symlink) از مسیر ذخیره‌سازی تا ریشه دیسک.
- **اعتبارسنجی امضای بایت‌های جادویی (Magic Bytes):** اعتبارسنجی فرمت‌های مجاز (PDF, PNG, JPEG, MP4, WebM) پیش از بافر کامل فایل و رد پسوندهای جعلی با خطای `415 Unsupported Media Type`.
- **حذف دوفازی:** حذف منطقی (`DELETED`) و حذف فیزیکی پاک‌سازی دیسک (`PURGE_PENDING` -> `PURGED`).
- **کنترل دسترسی مبتنی بر نقش (HTTP Basic):**
  - نقش `ADMIN`: دسترسی نامحدود به متادیتا، آپلود، دانلود، حذف منطقی و Purge فیزیکی.
  - نقش `AUTH_SERVICE`: دسترسی کاملاً محدود به فایل‌های زیرسیستم `AUTH` و مالک نوع `USER`.

### ۲.۳. سرویس فرآیند و گردش کار (`workflow` — پورت 8082)
- اسکلت مستقل سرویس با پورت و دیتابیس مجزا.
- پیاده‌سازی لایه ماندگاری مستقل برای ثبت وقایع ممیزی در جدول `workflow.audit_events`.

---

## ۳. پایگاه داده و مهاجرت‌ها (Flyway)

هر سرویس مالک انحصاری شمای دیتابیس خود است و هیچ کلید خارجی یا وابستگی دیتابیسی بین سرویس‌ها وجود ندارد.

| سرویس | دیتابیس / اسکیما | نسخه‌های Flyway در PostgreSQL | نسخه‌های Flyway در H2 |
| :--- | :--- | :--- | :--- |
| **`auth`** | `video_identity_db` / `auth` | `V1` پایه کاربران، `V2` نقش‌ها، `V3` احراز هویت، `V4` تاریخچه تلاش‌ها، `V5` ممیزی، `V6` صف ایمیل Outbox | نسخه‌های متناظر در `db/migration-h2` |
| **`dms`** | `video_identity_db` / `dms` | `V1` متادیتای فایل‌ها، `V2` وقایع ممیزی | نسخه‌های متناظر در `db/migration-h2` |
| **`workflow`**| `video_identity_db` / `workflow` | `V1` وقایع ممیزی | نسخه متناظر در `db/migration-h2` |

> تنظیم Hibernate در تمام سرویس‌ها روی `ddl-auto=validate` قرار دارد و Flyway مرجع انحصاری تغییرات ساختار داده است.

---

## ۴. اصول ممیزی و رهگیری توزیع‌شده (Audit & Tracing Invariants)

1. **تفکیک سه مفهوم:**
   - **Operational Logs:** رخدادهای فنی، مسیرها، خطاها و زمان پاسخ در قالب ساختاریافته Logstash JSON در فایل و کنسول.
   - **Audit Trail:** رخدادهای تجاری و امنیتی پایدار و ماندگار در دیتابیس اختصاصی هر سرویس (`*.audit_events`).
   - **Distributed Tracing:** رهگیری زنجیره فراخوانی درخواست‌ها بین سرویس‌ها با استفاده از استانداردهای OpenTelemetry و W3C Trace Context.
2. **قواعد تراکنش‌های ممیزی:**
   - رخدادهای موفق کسب‌وکار با انتشار تراکنشی `Propagation.REQUIRED` ثبت می‌شوند تا در صورت Rollback عملیات تجاری، لاگ موفقیت کاذب ثبت نشود.
   - رخدادهای شکست، نقض امنیت و اعتبارسنجی با انتشار `Propagation.REQUIRES_NEW` ثبت می‌شوند تا حتی در صورت Rollback تراکنش اصلی، مدرک تخلف امنیتی در دیتابیس باقی بماند.
3. **حفظ حریم خصوصی و امنیت داده:**
   - ثبت گذرواژه‌ها، هش‌ها، توکن‌های CSRF، کوکی‌ها، سشن‌ها، بایت‌های ویدئو، متن خام Multipart و ایمیل کاربر در لاگ، تریس و رویدادهای ممیزی **اکیداً ممنوع** است.
   - هدرهای `X-Request-ID`، `traceId` و `spanId` شناسه‌های مجزا بوده و همبستگی امن را برقرار می‌کنند.

---

## ۵. راهنمای اجرا و راه‌اندازی سریع

### پیش‌نیازها
- **Java 25** (پیشنهاد: Eclipse Adoptium JDK 25)
- **PowerShell** یا ترمینال استاندارد لینوکس/ویندوز
- **Docker Compose** (جهت اجرای اختیاری PostgreSQL و Jaeger)

---

### سناریوی ۱: اجرای محلی سریع با دیتابیس توکار H2 (Zero Dependencies)

برای اجرا در محیط محلی ویندوز با دیتابیس مستقل H2، سه ترمینال جداگانه باز کنید:

#### ترمینال ۱ — سرویس DMS:
```powershell
cd dms
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
$env:DMS_API_USERNAME = "dms-admin"
$env:DMS_API_PASSWORD = "local-dms-admin-secret"
$env:DMS_AUTH_SERVICE_USERNAME = "auth-service"
$env:DMS_AUTH_SERVICE_PASSWORD = "shared-auth-service-secret"

.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=h2'
```

#### ترمینال ۲ — سرویس AUTH:
```powershell
cd auth
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
$env:AUTH_DMS_URL = "http://127.0.0.1:8081"
$env:AUTH_DMS_USERNAME = "auth-service"
$env:AUTH_DMS_PASSWORD = "shared-auth-service-secret"
$env:AUTH_BOOTSTRAP_ADMIN_ENABLED = "true"
$env:AUTH_BOOTSTRAP_ADMIN_EMAIL = "admin@example.com"
$env:AUTH_BOOTSTRAP_ADMIN_PASSWORD = "Admin-Secret-Password-2026"

.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=h2'
```

#### ترمینال ۳ — سرویس WORKFLOW:
```powershell
cd workflow
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"

.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

---

### سناریوی ۲: اجرای کامل با PostgreSQL و Jaeger (محیط Production-ready)

1. راه‌اندازی دیتابیس PostgreSQL و سرور Jaeger:
```powershell
docker compose --profile observability up -d
```

2. تنظیم کلمه عبور دیتابیس و اجرای سرویس‌ها در ترمینال‌های مجزا:
```powershell
# برای هر ترمینال:
$env:AUTH_DB_PASSWORD = "postgres_secure_password"
$env:DMS_DB_PASSWORD = "postgres_secure_password"
$env:WORKFLOW_DB_PASSWORD = "postgres_secure_password"

# فعال‌سازی ارسال تریس به OpenTelemetry Collector / Jaeger:
$env:OTEL_EXPORTER_OTLP_ENABLED = "true"
$env:OTEL_EXPORTER_OTLP_TRACES_ENDPOINT = "http://127.0.0.1:4318/v1/traces"
$env:TRACING_SAMPLING_PROBABILITY = "1.0"
```

---

### فعال‌سازی ارسال واقعی ایمیل (SMTP Outbox)

به صورت پیش‌فرض پیام‌ها در جدول Outbox صف‌بندی می‌شوند اما ارسال SMTP خاموش است. جهت فعال‌سازی ارسال واقعی:
```powershell
$env:EMAIL_NOTIFICATIONS_ENABLED = "true"
$env:EMAIL_FROM = "kyc-noreply@yourdomain.com"
$env:SPRING_MAIL_HOST = "smtp.yourdomain.com"
$env:SPRING_MAIL_PORT = "587"
$env:SPRING_MAIL_USERNAME = "smtp-username"
$env:SPRING_MAIL_PASSWORD = "smtp-password"
$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH = "true"
$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE = "true"
```

---

## ۶. فهرست اندپوینت‌های کلیدی و مستندات API

### مستندات تعاملی Swagger / OpenAPI
- **سرویس AUTH:** `http://localhost:8080/swagger-ui.html` (OpenAPI Doc: `/v3/api-docs/auth-api`)
- **سرویس DMS:** `http://localhost:8081/swagger-ui.html` (OpenAPI Doc: `/v3/api-docs/dms-api`)
- **سرویس WORKFLOW:** `http://localhost:8082/swagger-ui.html` (OpenAPI Doc: `/v3/api-docs/workflow-api`)

> *نکته امنیتی:* مستندات Swagger فقط در پروفایل‌های توسعه محلی (`local` و `h2`) فعال بوده و در محیط عملیاتی به صورت پیش‌فرض خاموش هستند.

### جدول اندپوینت‌های اصلی

| سرویس | مسیر اندپوینت | متد | سطح دسترسی | شرح عملکرد |
| :--- | :--- | :---: | :---: | :--- |
| **AUTH** | `/register` | GET/POST | Anonymous (با CSRF) | فرم ثبت‌نام وب کاربری |
| **AUTH** | `/login` | GET/POST | Anonymous | فرم ورود استاندارد به سامانه |
| **AUTH** | `/api/v1/auth/csrf` | GET | Anonymous | دریافت کوکی سشن و توکن امنیتی CSRF |
| **AUTH** | `/api/v1/auth/registrations` | POST | Anonymous (با CSRF) | ثبت‌نام کاربر جدید از طریق REST API |
| **AUTH** | `/api/v1/auth/verifications/me` | GET | `ROLE_USER` | دریافت آخرین وضعیت احراز هویت کاربر جاری |
| **AUTH** | `/api/v1/auth/verifications/history` | GET | `ROLE_USER` | دریافت تاریخچه تلاش‌های کاربر به ترتیب زمانی |
| **AUTH** | `/api/v1/auth/verifications` | POST | `ROLE_USER` (با CSRF) | ارسال فایل ویدئوی احراز هویت (Multipart) |
| **AUTH** | `/api/v1/auth/admin/verifications/pending` | GET | `ROLE_ADMIN` | فهرست درخواست‌های در انتظار بررسی ادمین |
| **AUTH** | `/api/v1/auth/admin/verifications/{id}/decision` | POST | `ROLE_ADMIN` (با CSRF) | تأیید یا رد درخواست احراز هویت همراه با دلیل |
| **AUTH** | `/api/v1/auth/admin/verifications/{id}/video` | GET | `ROLE_ADMIN` | استریم ویدئوی کاربر از DMS بدون کش محلی |
| **DMS** | `/api/v1/dms/files` | POST | Basic Auth (`ADMIN` / `AUTH_SERVICE`) | آپلود فایل با اعتبارسنجی بایت‌های جادویی |
| **DMS** | `/api/v1/dms/files/{id}` | GET | Basic Auth | دریافت متادیتای ذخیره‌سازی فایل |
| **DMS** | `/api/v1/dms/files/{id}/content` | GET | Basic Auth | دانلود استریم محتوای فایل فعال |
| **DMS** | `/api/v1/dms/files/{id}` | DELETE | Basic Auth (`ADMIN`) | حذف منطقی فایل (`DELETED`) |
| **DMS** | `/api/v1/dms/files/{id}/purge` | POST | Basic Auth (`ADMIN`) | پاک‌سازی فیزیکی فایل از دیسک (`PURGED`) |

---

## ۷. اجرای تست‌ها و ممیزی Workspace

جهت اجرای کامل آزمون‌ها با Maven Wrapper و Java 25:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"

# اجرای تست‌های سرویس AUTH:
& "auth\mvnw.cmd" -f "auth\pom.xml" test

# اجرای تست‌های سرویس DMS:
& "dms\mvnw.cmd" -f "dms\pom.xml" test

# اجرای تست‌های سرویس WORKFLOW:
& "workflow\mvnw.cmd" -f "workflow\pom.xml" test

# بررسی ۹۲ آزمون معماری و استاتیک Workspace:
python scripts/verify_workspace.py

# اعتبارسنجی سینتکس کل کدهای جاوا:
& "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe" scripts/JavaSyntaxCheck.java
```

---

## ۸. تاریخچه مستندات و گزارش‌های فنی

تمامی گزارش‌های تاریخی فازهای پروژه، بررسی‌های کیفی و تغییرات معماری در پوشه `docs/` نگهداری می‌شوند:
- 📑 [گزارش جامع و گاه‌شمار پروژه (از گام ۲ تا گام ۶)](file:///d:/Amin/My%20Java%20Projects/video-identity-verification/docs/CHRONOLOGICAL_PROJECT_REPORTS_FA.md)
- 📐 [طراحی فنی سیستم ممیزی تراکنشی و Tracing](file:///d:/Amin/My%20Java%20Projects/video-identity-verification/docs/STEP6_AUDIT_TRACING_TECHNICAL_DESIGN_FA.md)
- 🛠️ [راهنمای پیاده‌سازی و بازبینی گام ششم](file:///d:/Amin/My%20Java%20Projects/video-identity-verification/docs/STEP_6_IMPLEMENTATION_FA.md)
- 📊 [گزارش کنترل کیفی گام ششم](file:///d:/Amin/My%20Java%20Projects/video-identity-verification/docs/STEP_6_QUALITY_REVIEW_FA.md)
- 🛡️ [راهنمای استفاده از اسکیل‌های توسعه](file:///d:/Amin/My%20Java%20Projects/video-identity-verification/docs/SKILLS_USAGE_FA.md)
