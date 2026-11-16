# Video Identity Verification — Step 6

> سه برنامه مستقل Spring Boot در یک Workspace مشترک: `auth/`، `dms/` و `workflow/`. هر برنامه POM، Maven Wrapper، دیتابیس/Schema و چرخه اجرای مستقل دارد. هیچ پروژه‌ای Maven dependency پروژه دیگر نیست.
>
> **وضعیت اعتبارسنجی این بسته:** ساختار، XML/YAML و Parse نحوی Java بررسی شده‌اند؛ Maven `clean verify` و اجرای واقعی سرویس‌ها در محیط بسته‌بندی قابل اجرا نبودند. قبل از ادغام یا Production، Build و Integration Testها را با JDK 25 اجرا کنید.

## محدوده گام ششم

### AUTH
- ثبت‌نام، ورود و خروج با Spring Security، Session و CSRF.
- آپلود ویدئو از مرورگر از طریق HTTP داخلی AUTH به DMS.
- هر ارسال پس از رد درخواست، یک `Verification Attempt` مستقل ایجاد می‌کند؛ Attemptهای قبلی باقی می‌مانند.
- شکست موقت DMS در همان Attempt قابل Retry است.
- تصمیم ادمین (`APPROVED` / `REJECTED`) در تراکنش با Audit ثبت می‌شود.
- API تاریخچه: `GET /api/v1/auth/verifications/history`.
- ایمیل نتیجه از طریق Transactional Outbox؛ ارسال SMTP به‌صورت opt-in و قابل Retry است.

### DMS
- Upload، Download، Metadata، Logical Delete و Physical Purge.
- ذخیره‌سازی: `SUBSYSTEM/YYYY/MM/DD/OWNER_ID/UUID.ext`.
- کنترل دسترسی `AUTH_SERVICE` محدود به فایل‌های زیرسیستم AUTH و مالک USER.
- Audit مستقل برای عملیات فایل و رویدادهای امنیتی.

### WORKFLOW
- همچنان فاقد Workflow Engine، State و Transition اجرایی است.
- برای داشتن Audit Store مستقل، JPA، Flyway و H2/PostgreSQL اضافه شده‌اند.
- در گام بعد می‌توان Definition/Instance/Transitionها را به این سرویس اضافه کرد.

### زیرساخت مشترک از نظر استاندارد، نه کد/وابستگی
- هر سرویس جدول مستقل `audit_events` دارد.
- Audit رویدادهای موفقِ تغییر داده در همان تراکنش محلی ذخیره می‌شود؛ رویدادهای شکست در تراکنش `REQUIRES_NEW` ثبت می‌شوند.
- Audit در مسیر عادی برنامه Append-Only است؛ برای Production، Runtime DB User باید مجوز UPDATE/DELETE روی جدول Audit نداشته باشد.
- Distributed Tracing با Micrometer/OpenTelemetry و W3C Trace Context آماده شده است.
- `X-Request-ID`، `traceId` و `spanId` شناسه‌های متفاوت‌اند.
- لاگ عملیاتی، Audit پایدار و Trace سه داده مستقل هستند.

## ساختار و پورت‌ها

| پروژه | پورت | دیتابیس/Schema | نقش |
|---|---:|---|---|
| AUTH | 8080 | PostgreSQL `auth` یا H2 | حساب، Attempt، تصمیم ادمین، ایمیل |
| DMS | 8081 | PostgreSQL `dms` یا H2 | فایل و Metadata |
| WORKFLOW | 8082 | PostgreSQL `workflow` یا H2 | فعلاً Audit Persistence و Scaffold |

## اجرای محلی با PostgreSQL

```powershell
# از ریشه Workspace
docker compose up -d postgres
$env:AUTH_DB_PASSWORD = 'same-password-as-POSTGRES_PASSWORD'
$env:DMS_DB_PASSWORD = 'same-password-as-POSTGRES_PASSWORD'
$env:WORKFLOW_DB_PASSWORD = 'same-password-as-POSTGRES_PASSWORD'
```

در صورت تغییر `POSTGRES_PASSWORD` در Compose، همان مقدار را برای هر سه متغیر بالا قرار دهید. هر سرویس فقط Schema خودش را با Flyway مدیریت می‌کند.

## اجرای محلی با H2 (Windows PowerShell)

از JDK 25 استفاده کنید. هر برنامه را در Terminal جداگانه اجرا کنید.

### 1. DMS

```powershell
cd dms
$env:DMS_API_USERNAME = 'dms-admin'
$env:DMS_API_PASSWORD = 'a-unique-local-admin-password'
$env:DMS_AUTH_SERVICE_USERNAME = 'auth-service'
$env:DMS_AUTH_SERVICE_PASSWORD = 'same-service-password-on-both-apps'
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=h2'
```

### 2. AUTH

```powershell
cd auth
$env:AUTH_DMS_URL = 'http://127.0.0.1:8081'
$env:AUTH_DMS_USERNAME = 'auth-service'
$env:AUTH_DMS_PASSWORD = 'same-service-password-on-both-apps'
$env:AUTH_BOOTSTRAP_ADMIN_ENABLED = 'true'
$env:AUTH_BOOTSTRAP_ADMIN_EMAIL = 'admin@example.com'
$env:AUTH_BOOTSTRAP_ADMIN_PASSWORD = 'your-own-long-unique-admin-password'
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=h2'
```

پس از ایجاد اولین Admin، `AUTH_BOOTSTRAP_ADMIN_ENABLED` را خاموش کنید. ثبت‌نام عمومی فقط `ROLE_USER` می‌دهد.

### 3. WORKFLOW

```powershell
cd workflow
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

WORKFLOW در این مرحله فقط دیتابیس مستقل برای Audit و Endpoint معرفی سرویس دارد؛ موتور Workflow اجرا نمی‌کند.

## تست Distributed Tracing با Jaeger

Jaeger در Compose با Profile اختیاری قرار دارد و به‌صورت پیش‌فرض اجرا نمی‌شود.

```powershell
docker compose --profile observability up -d jaeger
```

در Terminal هر سه سرویس قبل از اجرا:

```powershell
$env:OTEL_EXPORTER_OTLP_ENABLED = 'true'
$env:OTEL_EXPORTER_OTLP_TRACES_ENDPOINT = 'http://127.0.0.1:4318/v1/traces'
$env:TRACING_SAMPLING_PROBABILITY = '1.0'
```

UI مشاهده Trace:

- Jaeger: `http://localhost:16686`
- OTLP/HTTP Collector: `http://127.0.0.1:4318`

AUTH از `RestClient.Builder` خودکار پیکربندی‌شده Spring Boot استفاده می‌کند؛ بنابراین `traceparent` به DMS منتقل می‌شود. در صورت عدم اجرای Collector، ارسال OTLP را غیرفعال نگه دارید؛ Instrumentation و propagation همچنان در برنامه وجود دارند.

## فعال‌سازی ارسال ایمیل

Outbox در لحظه تأیید/رد ادمین در دیتابیس AUTH ثبت می‌شود، حتی اگر SMTP غیرفعال باشد. ارسال واقعی به‌صورت پیش‌فرض خاموش است.

```powershell
$env:EMAIL_NOTIFICATIONS_ENABLED = 'true'
$env:EMAIL_FROM = 'no-reply@your-domain.example'
$env:SPRING_MAIL_HOST = 'smtp.your-domain.example'
$env:SPRING_MAIL_PORT = '587'
$env:SPRING_MAIL_USERNAME = 'smtp-user'
$env:SPRING_MAIL_PASSWORD = 'smtp-secret'
$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH = 'true'
$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE = 'true'
```

سپس AUTH را اجرا کنید. Worker محلی Outbox را Poll می‌کند، با Backoff تا حداکثر تعداد تلاش تنظیم‌شده Retry می‌کند و وضعیت‌های `PENDING`، `PROCESSING`، `SENT` و `DEAD` را نگه می‌دارد. شناسه کاربر موضوع پیام جدا از شناسه Attempt ذخیره می‌شود؛ آدرس ایمیل پس از وضعیت نهایی `SENT` یا `DEAD` از Outbox پاک می‌شود.

**توجه:** SMTP تضمین Exactly-Once نمی‌دهد. اگر ایمیل ارسال شود ولی برنامه پیش از ثبت `SENT` متوقف شود، ممکن است پس از پایان Lease ایمیل تکراری ارسال شود؛ این الگوی Outbox عملاً At-Least-Once است.

## مسیرهای اصلی

| سرویس | مسیر | توضیح |
|---|---|---|
| AUTH | `/register`, `/login` | ثبت‌نام و ورود |
| AUTH | `/verification/upload` | ارسال ویدئو و تاریخچه تلاش‌ها |
| AUTH | `/admin` | بررسی درخواست‌ها |
| AUTH | `/api/v1/auth/verifications/me` | آخرین وضعیت |
| AUTH | `/api/v1/auth/verifications/history` | تاریخچه Attemptها |
| AUTH | `/api/v1/auth/verifications` | Multipart video + CSRF |
| AUTH | `/api/v1/auth/admin/verifications/pending` | فهرست در انتظار |
| AUTH | `/api/v1/auth/admin/verifications/{id}/decision` | تصمیم ادمین + CSRF |
| AUTH | `/api/v1/auth/admin/verifications/{id}/video` | مشاهده ویدئوی محافظت‌شده |
| DMS | `/api/v1/dms/files` | Upload |
| DMS | `/api/v1/dms/files/{id}` | Metadata / Logical Delete |
| DMS | `/api/v1/dms/files/{id}/content` | Download |
| DMS | `/api/v1/dms/files/{id}/purge` | Physical Purge |
| هر سه | `/swagger-ui.html` | Swagger UI در پروفایل local/h2 |

## Migrationها

| سرویس | PostgreSQL | H2 |
|---|---|---|
| AUTH | V4 Attempt History، V5 Audit، V6 Email Outbox | نسخه‌های متناظر در `db/migration-h2` |
| DMS | V2 Audit | نسخه متناظر در `db/migration-h2` |
| WORKFLOW | V1 Audit | نسخه متناظر در `db/migration-h2` |

Flyway مرجع Schema است؛ `ddl-auto=update` استفاده نمی‌شود.

## حریم خصوصی و امنیت Audit

- هر سرویس فقط `auth.audit_events`، `dms.audit_events` یا `workflow.audit_events` خودش را می‌نویسد.
- هیچ Foreign Key بین سرویس‌ها وجود ندارد.
- Password، Password Hash، Authorization، Cookie، Session ID، CSRF Token، JWT، محتوای ویدئو و متن خام Multipart در Audit/Log/Trace ثبت نمی‌شوند.
- `details_json` فقط برای Metadata محدود و allowlist‌شده است.
- IP و User-Agent داده حساس عملیاتی/شخصی هستند؛ دسترسی و Retention آن‌ها باید در محیط Production تعیین شود.
- متن علت رد در Audit تکرار نمی‌شود؛ فقط وضعیت و کد علت ثبت می‌شود. متن علت در رکورد Attempt برای نمایش به صاحب درخواست باقی می‌ماند.
- Audit API و پنل مشاهده Audit در این گام ساخته نشده‌اند.

## محدودیت‌های باقی‌مانده

- Workflow Engine و Dynamic State/Transition هنوز وجود ندارد.
- Redis و RabbitMQ اضافه نشده‌اند.
- Audit مرکزی و تجمیع بین سرویس‌ها وجود ندارد.
- سیاست Retention/Archival Audit هنوز باید با سیاست سازمان تعیین شود.
- Streaming/Range برای ویدئو و حذف Buffering در حافظه، کار مقیاس‌پذیری بعدی است.
- ثبت موفق Audit در دیتابیس محلی با Business State اتمیک است؛ عملیات File System و دیتابیس DMS یا ارتباط AUTH↔DMS تراکنش توزیع‌شده نیستند و به Reconciliation نیاز دارند.
- قبل از Production، Runtime DB User جدا از Migration Owner و مجوز Insert-only برای جدول Audit تعریف شود.

## کنترل کیفیت

```powershell
python scripts/verify_workspace.py
java scripts/JavaSyntaxCheck.java
```

این دو دستور فقط کنترل ساختاری و Parse نحوی انجام می‌دهند. Build و تست واقعی:

```powershell
cd auth; .\mvnw.cmd clean verify
cd ..\dms; .\mvnw.cmd clean verify
cd ..\workflow; .\mvnw.cmd clean verify
```

گزارش دقیق تغییرات و محدودیت‌های محیط بسته‌بندی در `docs/STEP_6_QUALITY_REVIEW_FA.md` است. طراحی تفصیلی Audit و Tracing در `docs/STEP6_AUDIT_TRACING_TECHNICAL_DESIGN_FA.md` و راهنمای بازبینی در `docs/STEP_6_IMPLEMENTATION_FA.md` قرار دارد.
