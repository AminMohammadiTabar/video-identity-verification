# طراحی فنی Audit Trail و Distributed Tracing
## گام ششم پروژه Video Identity Verification

**نسخه سند:** 1.0  
**وضعیت:** طراحی پیشنهادی برای تأیید پیش از پیاده‌سازی  
**مبنای طراحی:** سورس گام پنجم `video_kyc_step5_auth_dms_review.zip`  
**معماری Audit:** مستقل در هر سرویس؛ بدون Audit Service مرکزی

---

# ۱. تصمیم‌های معماری

تصمیم‌های زیر مبنای پیاده‌سازی گام ششم هستند:

1. هر سرویس مالک Audit Eventهای خودش است.
2. هیچ Audit Service مرکزی ایجاد نمی‌شود.
3. هیچ Maven dependency مشترکی بین AUTH، DMS و WORKFLOW ایجاد نمی‌شود.
4. Audit Eventهای موفقِ مربوط به تغییر داده، در همان تراکنش محلی تغییر Business Data ذخیره می‌شوند.
5. برای رویدادهای شکست که تراکنش کسب‌وکار Rollback می‌شود، ثبت Audit در تراکنش مستقل `REQUIRES_NEW` انجام می‌شود.
6. Audit Trail از Operational Logging و Distributed Tracing جداست.
7. Request ID فعلی حفظ می‌شود؛ Trace ID و Span ID استاندارد OpenTelemetry به آن اضافه می‌شوند.
8. ارتباط AUTH به DMS از طریق W3C Trace Context ردیابی می‌شود.
9. Audit Eventها Append-Only هستند و در مسیر عادی برنامه Update/Delete نمی‌شوند.
10. Audit، Trace و Log هیچ‌کدام نباید Password، Token، Session ID، ویدئو یا داده حساس غیرضروری را ذخیره کنند.

## تفکیک سه مفهوم

| قابلیت | پرسشی که پاسخ می‌دهد | محل نگهداری |
|---|---|---|
| Operational Log | چه اتفاق فنی افتاد و خطا کجا رخ داد؟ | فایل JSON هر سرویس |
| Audit Trail | چه کسی چه عملیات کسب‌وکاری انجام داد؟ | جدول Audit همان سرویس |
| Distributed Trace | یک درخواست از چه سرویس‌ها و Spanهایی عبور کرد؟ | Backend مشاهده Trace |

Audit و Trace جایگزین یکدیگر نیستند. Trace ممکن است Sample شود؛ Audit کسب‌وکاری نباید بر اساس Sampling حذف شود.

---

# ۲. وضعیت فعلی سورس گام پنجم

بررسی سورس تحویلی نشان می‌دهد:

- AUTH دارای `HttpAuditFilter` است که Request ID، IP، مسیر، متد، وضعیت و زمان اجرا را در Log ثبت می‌کند.
- DMS و WORKFLOW نیز Filter مشابه دارند.
- AUTH دارای `AuthActionAudit` است؛ این کلاس صراحتاً Operational Log تولید می‌کند و Durable Compliance Audit نیست.
- Request ID با `X-Request-ID` بین AUTH و DMS منتقل می‌شود.
- در `HttpDmsGateway` انتقال دستی `X-Request-ID` وجود دارد.
- Trace ID و Span ID استاندارد هنوز در زنجیره ثبت و انتقال نشده‌اند.
- AUTH و DMS از JPA و Flyway استفاده می‌کنند و Schemaهای `auth` و `dms` دارند.
- WORKFLOW فعلاً فقط یک سرویس Placeholder است و JPA، دیتابیس و Flyway ندارد.

بنابراین در گام ششم باید Audit Persistence به هر سه سرویس اضافه شود و برای WORKFLOW نیز Persistence مستقل و حداقلی ایجاد شود. این کار به معنی پیاده‌سازی Workflow Engine نیست.

---

# ۳. معماری استقرار و مالکیت داده

## ۳.۱. ساختار منطقی

```text
video-identity-verification/
├── auth/
│   └── owns auth.audit_events
├── dms/
│   └── owns dms.audit_events
└── workflow/
    └── owns workflow.audit_events
```

هیچ سرویسی برای ثبت Audit به سرویس دیگر درخواست HTTP نمی‌فرستد.

## ۳.۲. PostgreSQL

برای حفظ ساختار فعلی می‌توان از یک PostgreSQL Database/Instance با Schemaهای جدا استفاده کرد:

```text
video_identity_db
├── auth
│   ├── users
│   ├── roles
│   ├── verification_requests
│   └── audit_events
│
├── dms
│   ├── files
│   └── audit_events
│
└── workflow
    └── audit_events
```

مالکیت منطقی داده‌ها:

- AUTH فقط جدول Audit خودش را مدیریت می‌کند.
- DMS فقط جدول Audit خودش را مدیریت می‌کند.
- WORKFLOW فقط جدول Audit خودش را مدیریت می‌کند.
- هیچ Foreign Key بین Schemaهای سرویس‌ها ایجاد نمی‌شود.
- هر سرویس Flyway History و Migration مستقل دارد.

استفاده از یک PostgreSQL Instance به معنی ایجاد وابستگی نرم‌افزاری بین سرویس‌ها نیست. در صورت نیاز عملیاتی، دیتابیس فیزیکی هر سرویس را می‌توان بعداً جدا کرد.

## ۳.۳. H2

هر سرویس H2 مستقل و File-Based خودش را دارد:

```text
auth/data/auth-db
dms/data/dms-db
workflow/data/workflow-db
```

H2 فقط برای Development و Test است. PostgreSQL دیتابیس محیط پایدار باقی می‌ماند.

## ۳.۴. WORKFLOW

برای اینکه WORKFLOW نیز Audit مستقل داشته باشد، در گام ششم این قابلیت‌های زیرساختی به آن اضافه می‌شوند:

- Spring Data JPA
- Flyway
- PostgreSQL Driver
- H2
- تنظیم Datasource و Schema مستقل `workflow`
- Migration اولیه Audit

این کار Workflow Engine، State، Transition یا پنل مدیریت Workflow ایجاد نمی‌کند.

---

# ۴. طراحی جدول Audit

نام جدول در هر سه Schema یکسان است:

```text
audit_events
```

اما هر سرویس جدول فیزیکی مستقل خودش را دارد:

```text
auth.audit_events
dms.audit_events
workflow.audit_events
```

## ۴.۱. ساختار پیشنهادی

| ستون | نوع پیشنهادی | Null | توضیح |
|---|---|---:|---|
| `id` | UUID | No | شناسه یکتای رویداد |
| `service_name` | VARCHAR(32) | No | نام سرویس ثبت‌کننده |
| `event_type` | VARCHAR(120) | No | نام پایدار رویداد |
| `event_version` | SMALLINT | No | نسخه قرارداد Event |
| `occurred_at` | TIMESTAMPTZ | No | زمان وقوع عملیات |
| `recorded_at` | TIMESTAMPTZ | No | زمان ثبت در دیتابیس |
| `actor_type` | VARCHAR(24) | No | نوع عامل اجراکننده |
| `actor_id` | VARCHAR(128) | Yes | شناسه عامل |
| `subject_type` | VARCHAR(64) | Yes | نوع شخص/موجودیتی که عملیات درباره اوست |
| `subject_id` | VARCHAR(128) | Yes | شناسه Subject |
| `target_type` | VARCHAR(64) | Yes | نوع منبع هدف |
| `target_id` | VARCHAR(128) | Yes | شناسه منبع هدف |
| `outcome` | VARCHAR(16) | No | نتیجه عملیات |
| `reason_code` | VARCHAR(64) | Yes | کد دلیل یا خطا |
| `previous_state` | VARCHAR(64) | Yes | وضعیت قبلی در تغییر وضعیت |
| `new_state` | VARCHAR(64) | Yes | وضعیت جدید |
| `request_id` | VARCHAR(64) | Yes | شناسه همبستگی HTTP موجود |
| `trace_id` | VARCHAR(32) | Yes | شناسه Trace استاندارد |
| `span_id` | VARCHAR(16) | Yes | شناسه Span مرتبط |
| `client_ip` | VARCHAR(45) | Yes | IPv4 یا IPv6 |
| `user_agent` | VARCHAR(512) | Yes | User-Agent محدودشده |
| `details_json` | VARCHAR(4000) | Yes | Metadata مجاز و غیرحساس |

### چرا Actor، Subject و Target جدا هستند؟

مثال: سرویس AUTH با حساب سرویس خودش و به نمایندگی از یک کاربر، ویدئویی را در DMS ذخیره می‌کند.

- Actor: `SERVICE / auth-service`
- Subject: `USER / user-uuid`
- Target: `DMS_FILE / file-uuid`

مثال دیگر: ادمین درخواست یک کاربر را رد می‌کند.

- Actor: `ADMIN / admin-user-uuid`
- Subject: `USER / applicant-user-uuid`
- Target: `VERIFICATION_REQUEST / request-uuid`

این تفکیک از اشتباه‌گرفتن سرویس اجراکننده با صاحب داده جلوگیری می‌کند.

## ۴.۲. مقادیر مجاز

`actor_type`:

```text
USER
ADMIN
SERVICE
SYSTEM
ANONYMOUS
```

`outcome`:

```text
SUCCESS
FAILURE
DENIED
```

در رویداد `VERIFICATION_REJECTED`، اگر ادمین مجاز باشد و تصمیم با موفقیت ثبت شود، مقدار `outcome` برابر `SUCCESS` است؛ ردشدن هویت نتیجه کسب‌وکاری است، نه خطای اجرای عملیات.

## ۴.۳. Migration نمونه PostgreSQL

این DDL برای هر Schema با نام‌گذاری همان سرویس تکرار می‌شود. نسخه H2 باید در مسیر Migration مخصوص H2 نگهداری شود.

```sql
CREATE TABLE auth.audit_events (
    id UUID PRIMARY KEY,
    service_name VARCHAR(32) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    event_version SMALLINT NOT NULL DEFAULT 1,

    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    actor_type VARCHAR(24) NOT NULL,
    actor_id VARCHAR(128),

    subject_type VARCHAR(64),
    subject_id VARCHAR(128),

    target_type VARCHAR(64),
    target_id VARCHAR(128),

    outcome VARCHAR(16) NOT NULL,
    reason_code VARCHAR(64),

    previous_state VARCHAR(64),
    new_state VARCHAR(64),

    request_id VARCHAR(64),
    trace_id VARCHAR(32),
    span_id VARCHAR(16),

    client_ip VARCHAR(45),
    user_agent VARCHAR(512),

    details_json VARCHAR(4000),

    CONSTRAINT ck_auth_audit_actor_type
        CHECK (actor_type IN ('USER', 'ADMIN', 'SERVICE', 'SYSTEM', 'ANONYMOUS')),

    CONSTRAINT ck_auth_audit_outcome
        CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED')),

    CONSTRAINT ck_auth_audit_event_version
        CHECK (event_version > 0)
);

CREATE INDEX idx_auth_audit_occurred_at
    ON auth.audit_events (occurred_at DESC);

CREATE INDEX idx_auth_audit_actor
    ON auth.audit_events (actor_type, actor_id, occurred_at DESC);

CREATE INDEX idx_auth_audit_target
    ON auth.audit_events (target_type, target_id, occurred_at DESC);

CREATE INDEX idx_auth_audit_event_type
    ON auth.audit_events (event_type, occurred_at DESC);

CREATE INDEX idx_auth_audit_request_id
    ON auth.audit_events (request_id);

CREATE INDEX idx_auth_audit_trace_id
    ON auth.audit_events (trace_id);
```

برای DMS و WORKFLOW، نام Schema و Prefix نام Constraint/Index به `dms` و `workflow` تغییر می‌کند.

`details_json` به‌صورت `VARCHAR(4000)` محدود شده تا اندازه Metadata کنترل‌شده و Migrationهای PostgreSQL و H2 هم‌خوان باشند. JSON باید در Application Layer Serialize و Validate شود. در این فاز نیازی به Query روی فیلدهای داخلی JSON نداریم.

## ۴.۴. Foreign Key تعریف نمی‌کنیم

برای ستون‌های زیر Foreign Key ایجاد نمی‌شود:

- `actor_id`
- `subject_id`
- `target_id`

دلایل:

- Audit باید بعد از حذف منطقی یا فیزیکی فایل باقی بماند.
- حذف یا غیرفعال‌شدن حساب نباید تاریخچه را از بین ببرد.
- DMS نباید به جدول کاربران AUTH وابسته شود.
- WORKFLOW نباید به Schema دو سرویس دیگر وابسته شود.

این شناسه‌ها Referenceهای تاریخی‌اند، نه رابطه Referential Integrity بین سرویس‌ها.

---

# ۵. Index و کارایی

Indexهای اولیه:

1. `occurred_at DESC`: مرور تاریخچه زمانی.
2. `(actor_type, actor_id, occurred_at DESC)`: جست‌وجوی عملیات یک Actor.
3. `(target_type, target_id, occurred_at DESC)`: تاریخچه یک Resource.
4. `(event_type, occurred_at DESC)`: جست‌وجوی نوع رویداد.
5. `request_id`: یافتن رویدادهای یک درخواست.
6. `trace_id`: اتصال Audit به Trace.

این Indexها برای شروع کافی‌اند. Partitioning ماهانه یا سالانه در شروع انجام نمی‌شود؛ پس از مشاهده حجم واقعی و نیاز Retention قابل بررسی است.

---

# ۶. فهرست رویدادهای Audit

## ۶.۱. AUTH

| Event Type | زمان ثبت |
|---|---|
| `auth.user.registered` | ثبت موفق کاربر |
| `auth.login.succeeded` | ورود موفق |
| `auth.login.failed` | ورود ناموفق |
| `auth.logout` | خروج |
| `auth.verification.upload.started` | رزرو تلاش آپلود |
| `auth.verification.upload.failed` | شکست آپلود یا ذخیره |
| `auth.verification.submitted` | اتصال فایل DMS و ورود به انتظار بررسی |
| `auth.verification.approved` | تأیید ادمین |
| `auth.verification.rejected` | رد ادمین |
| `auth.authorization.denied` | دسترسی غیرمجاز به عملیات حساس |

برای تغییر وضعیت، `previous_state` و `new_state` پر می‌شوند.

## ۶.۲. DMS

| Event Type | زمان ثبت |
|---|---|
| `dms.file.uploaded` | ثبت موفق Metadata و فایل |
| `dms.file.upload.failed` | شکست Upload |
| `dms.file.metadata.read` | خواندن Metadata در صورت نیاز Audit |
| `dms.file.download.started` | شروع دانلود مجاز |
| `dms.file.download.failed` | شکست دانلود |
| `dms.file.logical_deleted` | حذف منطقی |
| `dms.file.purge.started` | ورود به PURGE_PENDING |
| `dms.file.purged` | حذف فیزیکی موفق |
| `dms.file.purge.failed` | شکست حذف فیزیکی |
| `dms.file.access.denied` | دسترسی غیرمجاز به فایل |

ثبت رویداد برای تمام GETهای عمومی یا Health Check لازم نیست. HTTP Access Log این درخواست‌ها را پوشش می‌دهد. Audit برای عملیات معنادار کسب‌وکاری و امنیتی است.

## ۶.۳. WORKFLOW

در گام ششم فقط زیرساخت و جدول آماده می‌شود. با پیاده‌سازی موتور در گام بعد، رویدادهای زیر اضافه می‌شوند:

- `workflow.definition.created`
- `workflow.definition.updated`
- `workflow.definition.published`
- `workflow.definition.disabled`
- `workflow.instance.started`
- `workflow.transition.succeeded`
- `workflow.transition.rejected`

در وضعیت فعلی، فراخوانی `GET /api/v1/workflow/info` به‌تنهایی Business Audit محسوب نمی‌شود.

---

# ۷. الگوی ثبت Audit در کد

هر سرویس پیاده‌سازی داخلی خودش را دارد؛ کد مشترک Maven ساخته نمی‌شود.

ساختار پیشنهادی در هر پروژه:

```text
observability/
└── audit/
    ├── AuditEventEntity.java
    ├── AuditEventRepository.java
    ├── AuditEventRecorder.java
    ├── AuditEventCommand.java
    ├── AuditActor.java
    ├── AuditContextProvider.java
    └── AuditEventType.java
```

مسئولیت‌ها:

- `AuditEventCommand`: ورودی تایپ‌شده برای ثبت Event.
- `AuditActor`: نوع و شناسه Actor.
- `AuditContextProvider`: جمع‌آوری Request ID، Trace ID، Span ID، IP و User-Agent.
- `AuditEventRecorder`: ساخت Entity و ثبت آن.
- `AuditEventRepository`: فقط عملیات لازم برای Insert و Query محدود.

از ثبت Audit عمومی و مبهم با Reflection یا AOP خودداری شود. برای عملیات کسب‌وکاری، ثبت صریح در Application Service خواناتر و قابل تست‌تر است.

## ۷.۱. موفقیت

برای تغییر موفق داده:

```text
BEGIN
  UPDATE business_table
  INSERT INTO audit_events
COMMIT
```

اگر هرکدام شکست بخورند، هر دو تغییر Rollback می‌شوند.

## ۷.۲. شکست

برای شکست عملیاتی که تراکنش اصلی Rollback شده:

```text
BUSINESS TRANSACTION -> ROLLBACK

NEW TRANSACTION
  INSERT failure audit event
COMMIT
```

این مسیر می‌تواند با `REQUIRES_NEW` پیاده‌سازی شود. خطای ثبت Audit شکست نباید Exception اصلی را بی‌دلیل Mask کند؛ خطای Audit باید جداگانه Log و Alert شود.

---

# ۸. سازگاری Audit با عملیات بین‌سرویسی

هیچ Distributed Transaction بین AUTH و DMS ایجاد نمی‌کنیم.

## سناریوی Upload

1. AUTH درخواست را در وضعیت `UPLOADING` رزرو می‌کند و Audit شروع را در همان تراکنش ثبت می‌کند.
2. AUTH درخواست HTTP را به DMS می‌فرستد.
3. DMS فایل را روی Disk می‌نویسد.
4. DMS Metadata و Audit موفقیت را در تراکنش محلی خودش ثبت می‌کند.
5. AUTH پس از پاسخ موفق DMS، فایل را به Verification Request متصل می‌کند و Audit `SUBMITTED` را در همان تراکنش ثبت می‌کند.
6. اگر DMS شکست بخورد، AUTH وضعیت `UPLOAD_FAILED` و Audit شکست را ثبت می‌کند.

اگر DMS موفق شود ولی AUTH نتواند نتیجه را Commit کند، Audit مستقل DMS باقی می‌ماند و AUTH باید رویداد Recovery/Reconciliation ثبت کند. `request_id` و `trace_id` برای تطبیق دو طرف استفاده می‌شوند.

## سناریوی تأیید یا رد

تغییر وضعیت Verification و Audit آن باید در یک تراکنش AUTH انجام شوند:

```text
BEGIN
  LOCK verification_request
  UPDATE status
  INSERT audit_event
COMMIT
```

برای جلوگیری از تصمیم تکراری، قفل و کنترل وضعیت فعلی حفظ می‌شود.

## سناریوی DMS Physical Purge

چون File System و Database یک تراکنش اتمیک ندارند:

1. تغییر وضعیت به `PURGE_PENDING` و Audit شروع در یک تراکنش.
2. حذف فایل از Disk.
3. تغییر وضعیت به `PURGED` و Audit موفقیت در یک تراکنش دیگر.
4. در شکست، Audit شکست با `REQUIRES_NEW` و امکان Retry/Reconciliation حفظ شود.

---

# ۹. Append-Only و امنیت Audit

## قواعد Application

- هیچ Update یا Delete برای Audit Event در Repository عمومی ارائه نشود.
- Audit Entity بعد از Persist قابل تغییر نباشد.
- Audit API عمومی در این مرحله ایجاد نشود.
- دسترسی خواندن Audit در آینده باید به نقش مشخص Auditor/Security محدود شود.
- اطلاعات آزاد و دلخواه Client در `details_json` ذخیره نشود؛ فقط Metadata Allowlist‌شده.

## قواعد PostgreSQL در Production

برای تضمین قوی‌تر:

- Migration Owner و Runtime DB User از هم جدا شوند.
- Runtime User برای `audit_events` فقط `INSERT` و در صورت نیاز `SELECT` داشته باشد.
- `UPDATE` و `DELETE` از Runtime User گرفته شود.
- دسترسی Maintenance/Archival به حساب عملیاتی جداگانه محدود شود.

در نسخه Development که یک DB User ساده استفاده می‌شود، Append-Only بودن در سطح Application و تست کنترل می‌شود؛ تضمین امنیتی DB در Production باید با Roleهای جدا اعمال شود.

## اطلاعات ممنوع

هرگز این موارد در Audit یا Trace ذخیره نشوند:

- Password و Password Hash
- Authorization Header
- Cookie و Session ID
- CSRF Token
- JWT و Refresh Token
- بایت‌های ویدئو
- محتوای Multipart
- کد ملی و داده‌های هویتی غیرضروری
- ایمیل کاربر در `details_json`
- نام کامل فایل ارسالی در `details_json`
- متن Exception خام که ممکن است Secret داشته باشد

برای علت رد احراز هویت، در Audit فقط `reason_code` ذخیره شود. متن توضیحی موردنیاز کسب‌وکار در رکورد Verification و با کنترل دسترسی نگهداری شود.

IP و User-Agent نیز داده قابل شناسایی/شخصی محسوب می‌شوند؛ دسترسی و دوره نگهداری آن‌ها باید با سیاست سازمان هماهنگ شود.

---

# ۱۰. Distributed Tracing

## ۱۰.۱. استاندارد

- OpenTelemetry
- W3C Trace Context
- `traceparent`
- `tracestate` در صورت نیاز

Request ID فعلی با Trace ID یکی نیست:

| شناسه | کاربرد |
|---|---|
| `requestId` | شناسه همبستگی Application و Header فعلی `X-Request-ID` |
| `traceId` | شناسه یک Trace توزیع‌شده |
| `spanId` | شناسه یک مرحله از Trace |

## ۱۰.۲. مسیر Trace

```text
Browser
  |
  v
AUTH - HTTP Server Span
  |
  v
AUTH - verification.submit
  |
  | RestClient + W3C traceparent
  v
DMS - HTTP Server Span
  |
  v
DMS - dms.file.upload
  |
  v
DMS - Database / Storage operations
  |
  v
AUTH - verification.submitted
```

همه Spanها یک Trace ID دارند؛ هر مرحله Span ID مستقل دارد.

## ۱۰.۳. وابستگی‌های فنی پیشنهادی

در هر سه پروژه، با کنترل سازگاری Spring Boot 4 و BOM همان نسخه:

- Spring Boot Actuator
- Micrometer Tracing
- `micrometer-tracing-bridge-otel`
- OpenTelemetry OTLP Exporter

از اضافه‌کردن نسخه‌های دستی و متناقض برای کتابخانه‌های Micrometer/OpenTelemetry خودداری شود؛ ابتدا dependency management نسخه Spring Boot بررسی شود.

## ۱۰.۴. پیکربندی محیط

پیشنهاد اولیه:

- Development: Sampling برابر 100%.
- Production: Sampling قابل تنظیم و متناسب با بار و سیاست مانیتورینگ.
- Audit: همیشه ثبت شود و از Sampling تبعیت نکند.

هر سه سرویس به OpenTelemetry Collector داده ارسال می‌کنند. Collector فقط زیرساخت Telemetry است و Audit Service مرکزی محسوب نمی‌شود.

برای مشاهده Trace در Development می‌توان Jaeger را استفاده کرد؛ در محیط پایدار Jaeger یا Grafana Tempo با توجه به زیرساخت نهایی انتخاب می‌شود.

## ۱۰.۵. Correlation در Log

Structured JSON Log فعلی حفظ می‌شود و این فیلدها به آن افزوده می‌شوند:

- `requestId`
- `traceId`
- `spanId`
- `service`
- `event`
- `actorId` در صورت مجازبودن
- `durationMs`

برای AUTH → DMS:
- انتقال `X-Request-ID` فعلی حفظ شود.
- انتقال `traceparent` توسط Instrumentation استاندارد انجام شود.
- Trace ID را به‌صورت دستی به‌جای `traceparent` ارسال نکن.
- از Baggage برای ایمیل، کد ملی یا داده حساس استفاده نکن.

## ۱۰.۶. Spanهای کسب‌وکاری

نام‌های پیشنهادی:

- `auth.registration`
- `auth.verification.submit`
- `auth.verification.review`
- `dms.file.upload`
- `dms.file.download`
- `dms.file.purge`

Attributeها فقط شناسه‌های غیرحساس و اطلاعات فنی مجاز باشند؛ مانند `verification.request.id`، `dms.file.id`، `file.size` و `outcome`.

---

# ۱۰.۷. AUTH Verification Attempt History و Email Outbox

در گام ششم، مدل یک درخواست به ازای هر کاربر به مدل Attempt-based تبدیل می‌شود:

- `verification_requests` هر بار تلاش مستقل را نگه می‌دارد.
- قید یکتایی از `(user_id)` به `(user_id, attempt_number)` تغییر می‌کند.
- تلاش اول با `attempt_number=1` شروع می‌شود.
- بعد از `REJECTED`، ارسال بعدی رکورد جدید با شماره تلاش افزایشی می‌سازد.
- بعد از `UPLOAD_FAILED`، Retry همان رکورد و همان شماره تلاش است.
- پس از `APPROVED`، ارسال جدید مجاز نیست.
- قفل روی ردیف User هنگام رزرو Attempt، درخواست‌های هم‌زمان یک کاربر را سریالی می‌کند.

جدول `auth.email_outbox` در همان تراکنش تصمیم ادمین ثبت می‌شود. فیلدهای اصلی آن عبارت‌اند از `id`، `verification_request_id`، `subject_user_id`، `recipient_email`، `notification_type`، `status`، `attempt_count`، `next_attempt_at`، `locked_until`، `created_at`، `sent_at` و `last_error_code`.

وضعیت‌ها: `PENDING`، `PROCESSING`، `SENT` و `DEAD`. Worker محلی با Lease و Backoff ارسال را Retry می‌کند. ارسال SMTP فقط با `EMAIL_NOTIFICATIONS_ENABLED=true` فعال می‌شود. پس از وضعیت نهایی `SENT` یا `DEAD` آدرس گیرنده از Outbox پاک می‌شود. SMTP دقیقاً یک‌بار بودن ارسال را تضمین نمی‌کند؛ الگو At-Least-Once است.

# ۱۱. Migration Plan

| سرویس | Migration PostgreSQL | Migration H2 |
|---|---|---|
| AUTH | `V4__support_verification_attempt_history.sql`، `V5__create_audit_events.sql`، `V6__create_email_notification_outbox.sql` | نسخه‌های متناظر در `db/migration-h2` |
| DMS | `V2__create_audit_events.sql` | همان نسخه در `db/migration-h2` |
| WORKFLOW | `V1__create_audit_events.sql` | همان نسخه در `db/migration-h2` |

برای WORKFLOW، علاوه بر Migration، تنظیمات Datasource و Flyway و وابستگی‌های JPA/PostgreSQL/H2 اضافه می‌شوند. این تغییر فقط برای Persistence مستقل Audit است.

---

# ۱۲. تست‌های الزامی

## Audit Persistence
- ثبت Event موفق در دیتابیس.
- عدم ثبت Event در صورت Rollback عملیات موفق.
- باقی‌ماندن Failure Event در `REQUIRES_NEW` پس از Rollback تراکنش اصلی.
- بررسی مقدارهای Actor، Subject، Target و Outcome.
- عدم وجود Foreign Key بین Audit و جداول سرویس‌های دیگر.
- عدم امکان Update/Delete از مسیر Application.
- محدودیت طول و اعتبارسنجی Event Type و Details.

## AUTH
- Registration و Audit در یک تراکنش.
- Login Success/Failure.
- Verification Upload Started/Failed/Submitted.
- Approve/Reject و ثبت Previous/New State.
- ثبت Actor واقعی Admin بدون ثبت ایمیل یا Password.

## DMS
- Upload Success/Failure.
- Download مجاز و غیرمجاز.
- Logical Delete.
- Purge Pending/Success/Failure.
- Audit باید با وضعیت نهایی Metadata سازگار باشد.

## WORKFLOW
- Migration و اتصال مستقل H2/PostgreSQL.
- ثبت Event آزمایشی از Recorder.
- عدم وابستگی به AUTH یا DMS.

## Distributed Tracing
- ایجاد Trace برای Request ورودی.
- انتقال Trace Context از AUTH به DMS.
- یکسان‌بودن Trace ID در دو سرویس.
- متفاوت‌بودن Span ID سرویس‌ها.
- درج Trace ID و Span ID در JSON Log.
- درج Trace ID در Audit Event.
- عدم ثبت Token، Cookie، Email و Body حساس.
- اجرای برنامه در نبود Collector نباید باعث شکست درخواست‌های کسب‌وکار شود.

---

# ۱۳. Scope گام ششم

## داخل Scope
- جدول و Persistence مستقل Audit در هر سه سرویس.
- Audit Recorder و قرارداد Event ثابت در هر سرویس.
- ثبت رویدادهای کسب‌وکاری و امنیتی مهم.
- Append-Only و کنترل دسترسی.
- OpenTelemetry/Micrometer Tracing.
- W3C Trace Context در ارتباط AUTH و DMS.
- اتصال Trace ID به Log و Audit.
- تست‌های واحد و Integration.
- به‌روزرسانی Swagger فقط در صورت اضافه‌شدن Endpoint جدید.
- به‌روزرسانی Skills و مستندات Claude Code و Antigravity.

## خارج از Scope مگر با درخواست جداگانه
- Audit Service مرکزی.
- تجمیع Audit با RabbitMQ.
- Event Bus.
- Workflow Engine داینامیک.
- پنل جست‌وجوی Audit.
- SIEM و هشدارهای امنیتی پیشرفته.
- Partitioning و Data Lake.
- نگهداری Trace برای همیشه.

---

# ۱۴. قالب Event نمونه

نمونه مفهومی یک Audit Event از AUTH:

```json
{
  "id": "e77a9f1d-1e75-49b6-a6aa-b50c5f4d5e6a",
  "serviceName": "auth",
  "eventType": "auth.verification.approved",
  "eventVersion": 1,
  "occurredAt": "2026-11-09T12:00:00Z",
  "actor": {
    "type": "ADMIN",
    "id": "admin-user-uuid"
  },
  "subject": {
    "type": "USER",
    "id": "applicant-user-uuid"
  },
  "target": {
    "type": "VERIFICATION_REQUEST",
    "id": "verification-request-uuid"
  },
  "outcome": "SUCCESS",
  "previousState": "PENDING_REVIEW",
  "newState": "APPROVED",
  "requestId": "c8f4e7d1-1234-4ad3-a642-a3cfbdc2c108",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7",
  "clientIp": "192.0.2.10",
  "reasonCode": null,
  "details": {
    "reviewChannel": "ADMIN_PORTAL"
  }
}
```

این نمونه فقط قرارداد مفهومی است؛ Entity در هر سرویس می‌تواند برای Persist از فیلدهای Flat استفاده کند.

---

# ۱۵. ترتیب پیاده‌سازی پیشنهادی

1. ایجاد Audit Event Contract و Schema در AUTH.
2. پیاده‌سازی Recorder و اتصال به Registration، Login و Verification.
3. ایجاد Schema و Recorder مستقل در DMS.
4. اتصال Audit به Upload، Download، Logical Delete و Purge.
5. اضافه‌کردن Persistence مستقل به WORKFLOW و ایجاد جدول Audit.
6. اضافه‌کردن Micrometer Tracing و OpenTelemetry به هر سه سرویس.
7. انتقال W3C Trace Context در AUTH → DMS.
8. افزودن Trace/Span به JSON Log و Audit Context.
9. نوشتن تست‌های Rollback، REQUIRES_NEW، Append-Only و Trace Propagation.
10. اجرای `clean verify` مستقل در هر سه پروژه.
11. تهیه گزارش Skills، Migration و کیفیت.

---

# ۱۶. تصمیم‌های تثبیت‌شده در پیاده‌سازی گام ششم

1. Retention Audit: فعلاً حذف خودکار ندارد؛ سیاست نگهداری Production بعداً با سازمان تعیین می‌شود.
2. IP و User-Agent: ذخیره می‌شوند، اما دسترسی و Retention باید محدود و کنترل‌شده باشد.
3. علت رد: متن آزاد در Audit تکرار نمی‌شود؛ فقط `reason_code` در Audit و متن کسب‌وکاری در رکورد Verification نگهداری می‌شود.
4. WORKFLOW: Datasource و Flyway مستقل برای Audit Persistence اضافه شده؛ Workflow Engine ساخته نشده است.
5. Audit API یا پنل: در این گام ساخته نشده؛ فعلاً Persistence و ثبت Event پیاده‌سازی شده است.

---

# ۱۷. معیار پذیرش نهایی گام ششم

- هر سه سرویس جدول Audit مستقل داشته باشند.
- هیچ سرویس برای Audit به سرویس دیگر درخواست ندهد.
- ثبت Audit موفق با تغییر Business State در یک تراکنش محلی باشد.
- Failure Eventهای لازم پس از Rollback نیز قابل ثبت باشند.
- هیچ Secret یا محتوای ویدئو در Audit، Log یا Trace ثبت نشود.
- Request ID و Trace ID از هم تفکیک شده باشند.
- Trace واحد AUTH → DMS قابل مشاهده باشد.
- Trace ID و Span ID در Logها موجود باشند.
- Audit Event هر سرویس در دیتابیس خودش باقی بماند.
- هر سه پروژه مستقل Build و Test شوند.
- نتیجه واقعی Build و Test گزارش شود؛ تست اجرا‌نشده موفق اعلام نشود.
