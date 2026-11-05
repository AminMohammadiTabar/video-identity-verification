# کاتالوگ جامع پروژه Video Identity Verification
## سند تحویل به Coding Agentها و راهنمای ادامه توسعه

**تاریخ:** ۶ نوامبر ۲۰۲۶  
**نسخه سند:** 1.0  
**مخاطب:** Claude Code، Antigravity و سایر Coding Agentها

---

## ۱. هدف و نحوه استفاده از این سند

این سند تصمیم‌های معماری، محدوده فعلی، مسئولیت سرویس‌ها، فناوری‌ها، مدل داده، APIها، امنیت، Logging، Swagger، اسکیل‌ها و تاریخچه توسعه را جمع‌بندی می‌کند تا Agent بتواند کار را بدون تغییر ناخواسته معماری ادامه دهد.

**این سند جایگزین بررسی سورس نیست.** Agent باید ابتدا کد واقعی را بخواند و هر مورد را با وضعیت سورس تطبیق دهد. اگر اختلافی وجود داشت، آن را گزارش کند و خودسرانه تصمیم معماری را تغییر ندهد. هیچ Build یا Test نباید موفق اعلام شود مگر اینکه واقعاً اجرا شده باشد.

---

# ۲. معرفی پروژه

**نام:** Video Identity Verification

**هدف:** ساخت سامانه‌ای برای ثبت‌نام کاربران، دریافت و نگهداری ویدئوی احراز هویت و فراهم‌کردن امکان بررسی و تأیید یا رد هویت توسط ادمین.

سناریوی کسب‌وکار:
1. کاربر ثبت‌نام می‌کند.
2. وارد سامانه می‌شود.
3. به صفحه انتخاب/ضبط ویدئوی احراز هویت هدایت می‌شود.
4. ویدئو در آینده به DMS ارسال می‌شود.
5. درخواست احراز هویت در انتظار بررسی قرار می‌گیرد.
6. ادمین در آینده ویدئو را بررسی و تأیید یا رد می‌کند.
7. در فازهای بعدی ایمیل نتیجه و احراز هویت مجدد اضافه می‌شود.

محدوده فعلی شامل AUTH پایه، رابط انتخاب ویدئو، DMS مستقل، Swagger و Logging است. اتصال واقعی AUTH به DMS و Workflow اجرایی هنوز پیاده‌سازی نشده‌اند.

---

# ۳. تصمیم معماری قطعی: سه پروژه مستقل در یک Workspace

این تصمیم در طول توسعه اصلاح شده و نهایی است.

```text
video-identity-verification/
├── auth/
│   ├── pom.xml
│   └── src/
├── dms/
│   ├── pom.xml
│   └── src/
└── workflow/
    ├── pom.xml
    └── src/
```

قواعد:
- AUTH، DMS و WORKFLOW سه پروژه Maven مستقل هستند.
- هر پروژه `pom.xml`، Spring Boot Application، تنظیمات، Build و اجرای مستقل دارد.
- پورت‌های مورد انتظار: AUTH=`8080`، DMS=`8081`، WORKFLOW=`8082`.
- هیچ پروژه‌ای نباید Maven dependency پروژه دیگر باشد.
- در ریشه Workspace، Parent POM مشترک یا Maven Multi-Module Root ایجاد نکن.
- ارتباط آینده AUTH و DMS از طریق API و در صورت نیاز RabbitMQ خواهد بود، نه Java Class Sharing.
- Workspace مشترک صرفاً برای بازکردن و مدیریت هر سه پروژه در یک پنجره IDE است.

**هشدار:** معماری را به Modular Monolith یا Maven Multi-Module تبدیل نکن. کاربر صراحتاً سه پروژه مستقل می‌خواهد.

---

# ۴. فناوری‌ها و نسخه‌های مورد توافق

| فناوری | کاربرد |
|---|---|
| Java 25 | زبان توسعه |
| Spring Boot 4.1.2 | نسخه انتخاب‌شده در طراحی |
| Spring MVC | وب و REST |
| Mustache | صفحات Server-Side در AUTH |
| Spring Security | Authentication، Authorization و Session |
| Spring Data JPA / Hibernate | Persistence و ORM |
| Maven | Build مستقل هر سرویس |
| PostgreSQL | دیتابیس پایدار |
| H2 | دیتابیس توسعه محلی |
| Flyway | Database Migration |
| springdoc-openapi 3.2.0 | OpenAPI و Swagger UI |
| Local File System | ذخیره فایل DMS |
| Redis | برای نیازهای آینده؛ فعلاً Integration فعال در محدوده نیست |
| RabbitMQ | ارتباط Async آینده؛ فعلاً پیاده‌سازی نشده |

نسخه واقعی را از POM هر پروژه استخراج کن. در صورت اختلاف با این جدول، اختلاف را گزارش کن و بدون بررسی سازگاری نسخه‌ها را تغییر نده.

---

# ۵. وضعیت قابلیت‌ها

| قابلیت | وضعیت گزارش‌شده |
|---|---|
| سه پروژه مستقل Maven | ساختار ایجاد شده |
| اجرای مستقل سه سرویس | در طراحی پیش‌بینی شده؛ Build واقعی باید بررسی شود |
| AUTH Registration/Login/Logout | پیاده‌سازی شده |
| BCrypt | پیاده‌سازی شده |
| Roleهای USER و ADMIN | پیاده‌سازی شده |
| صفحات Mustache | صفحات اولیه پیاده‌سازی شده |
| صفحه انتخاب ویدئو | پیاده‌سازی شده؛ ارسال واقعی ندارد |
| DMS Upload/Download | پیاده‌سازی شده |
| DMS Logical Delete/Physical Delete | پیاده‌سازی شده |
| PostgreSQL و H2 | تنظیمات و Migrationها اضافه شده‌اند؛ اجرای واقعی باید بررسی شود |
| Swagger UI برای هر سه سرویس | اضافه شده |
| HTTP Logging برای هر سه سرویس | اضافه شده |
| Action Logging | برای Actionهای اصلی اضافه شده |
| Skills برای Claude Code و Antigravity | در نسخه بازبینی‌شده قرار داده شده |
| اتصال واقعی AUTH به DMS | **پیاده‌سازی نشده** |
| بررسی ویدئو توسط ادمین | **پیاده‌سازی نشده** |
| ارسال ایمیل | **پیاده‌سازی نشده** |
| احراز هویت مجدد | **پیاده‌سازی نشده** |
| Workflow Engine | **پیاده‌سازی نشده** |
| Dynamic StateMachine | **پیاده‌سازی نشده** |
| Redis Integration | **در محدوده فعلی پیاده‌سازی نشده** |
| RabbitMQ | **پیاده‌سازی نشده** |

بدون درخواست صریح، موارد پیاده‌سازی‌نشده را وارد Scope نکن.

---

# ۶. AUTH

## مسئولیت‌ها
- ثبت‌نام، ورود و خروج
- Session Management
- Authentication و Authorization
- مدیریت Roleها
- صفحات Mustache
- صفحه انتخاب ویدئوی احراز هویت
- وضعیت اولیه احراز هویت به‌صورت enum
- OpenAPI/Swagger
- HTTP Logging و Action Audit

## امنیت
مدل فعلی Session-Based و Form Login است:
- Spring Security
- BCrypt
- CSRF فعال
- نقش‌های `ROLE_USER` و `ROLE_ADMIN`
- JWT و OAuth2 در Scope فعلی نیستند.

## صفحات
- Register
- Login
- Logout
- صفحه اصلی کاربر
- صفحه انتخاب/بارگذاری ویدئو
- صفحه اولیه Admin

صفحه ویدئو فعلاً رابط کاربری است و فایل را به DMS ارسال نمی‌کند. پنل Admin نیز هنوز منطق بررسی و تأیید ویدئو ندارد.

## وضعیت احراز هویت

```java
public enum VerificationStatus {
    NOT_SUBMITTED,
    PENDING_REVIEW,
    APPROVED,
    REJECTED
}
```

این enum فقط وضعیت‌های فعلی را تعریف می‌کند؛ موتور Workflow وجود ندارد.

## Swagger در AUTH
مسیرهای زیر در صورت فعال‌بودن Swagger باید بدون Login قابل دسترسی باشند:

```text
/swagger-ui.html
/swagger-ui/**
/v3/api-docs/**
/v3/api-docs.yaml
```

عمومی‌بودن Swagger نباید APIهای کسب‌وکار را عمومی کند. Swagger در توسعه فعال و در Production به‌صورت پیش‌فرض غیرفعال است. بازکردن Swagger نباید به Login Redirect شود؛ Redirect داخلی springdoc به `swagger-ui/index.html` طبیعی است.

## خطای قبلی 401/403
در `AuthFlowH2IntegrationTest` اختلاف 401 و 403 گزارش شده بود. سناریوها باید جدا باشند:
- Anonymous + CSRF معتبر روی Endpoint محافظت‌شده: معمولاً `401`.
- Authenticated + دسترسی ناکافی: `403`.
- CSRF نامعتبر یا غایب در درخواست محافظت‌شده: `403`.

برای پاس‌کردن تست، Security Configuration را تضعیف نکن. تست باید رفتار صحیح Spring Security را منعکس کند.

---

# ۷. DMS

## مسئولیت‌ها
- Upload
- Download
- Metadata Retrieval
- Logical Delete
- Physical Delete
- ساخت مسیر Storage
- ثبت Metadata
- کنترل دسترسی فایل
- OpenAPI/Swagger
- HTTP Logging و Action Audit

DMS نباید به Entity، Repository یا کلاس‌های AUTH وابسته باشد.

## ساختار قطعی دایرکتوری

**ترتیب دقیق: `SUBSYSTEM / YEAR / MONTH / DAY / USER / FILE`**

```text
storage/
└── AUTH/
    └── 2026/
        └── 11/
            └── 06/
                ├── SYSTEM-AUTH-REGISTER/
                │   └── file-uuid.webm
                ├── user-uuid-1/
                │   └── video-uuid.mp4
                └── user-uuid-2/
                    └── video-uuid.webm
```

- کاربر ثبت‌شده: UUID کاربر نام پوشه است.
- قبل از ایجاد حساب: `SYSTEM-AUTH-REGISTER`.
- نام فیزیکی فایل UUID است.
- نام اصلی فایل در Metadata ذخیره می‌شود.
- از ایمیل یا کد ملی در مسیر فایل استفاده نشود.
- Owner از Uploader مستقل است.

فیلدهای طراحی‌شده:
- `owner_type`
- `owner_reference`
- `uploaded_by_user_id`

وجود و تطابق این فیلدها را در Entity و Migration واقعی بررسی کن؛ اگر ناقص‌اند، Gap را گزارش بده و بدون هماهنگی Schema را تغییر نده.

## Metadata مورد انتظار

`id`, `subsystem_key`, `original_filename`, `stored_filename`, `relative_path`, `content_type`, `size_bytes`, `checksum_sha256`, `status`, `created_at`, `deleted_at`, `purged_at`, `version`

نام دقیق فیلدها را از سورس استخراج کن.

## وضعیت فایل

```text
ACTIVE --Logical Delete--> DELETED --Physical Purge--> PURGED
```

- `ACTIVE`: فایل فعال است.
- `DELETED`: از نظر منطقی حذف شده، اما ممکن است روی هارد باشد.
- `PURGED`: محتوای فایل از Storage حذف شده است؛ Metadata می‌تواند برای Audit باقی بماند.

حذف فایل روی دیسک و تغییر دیتابیس تراکنش اتمیک مشترک ندارند. خطاهای میانی، وضعیت قابل بازیابی و Reconciliation را در نظر بگیر.

## APIهای طراحی‌شده

| Method | Endpoint | کاربرد |
|---|---|---|
| POST | `/api/v1/dms/files` | Upload |
| GET | `/api/v1/dms/files/{id}` | Metadata |
| GET | `/api/v1/dms/files/{id}/content` | Download |
| DELETE | `/api/v1/dms/files/{id}` | Logical Delete |
| POST | `/api/v1/dms/files/{id}/purge` | Physical Delete |

Upload با `multipart/form-data` است. نام دقیق فیلدهای Multipart و Response را از Controller و OpenAPI واقعی بخوان.

## امنیت DMS
- HTTP Basic برای API مستقل DMS در نظر گرفته شده.
- نام کاربری توسعه گزارش‌شده: `dms-admin`.
- رمز از `DMS_API_PASSWORD` خوانده شود.
- Secret ثابت در سورس قرار نده.
- مسیر فایل از Client پذیرفته نشود.
- Path Traversal و Symlink بررسی شوند.
- فایل DELETED/PURGED بدون مجوز قابل دریافت نباشد.
- مسیر فیزیکی Storage در API عمومی افشا نشود.
- نوع و اندازه فایل اعتبارسنجی شود.

در نسخه قبلی Allowlist شامل PDF، PNG، JPEG، MP4 و WebM و سقف ۵۰ مگابایت گزارش شده بود؛ مقدار واقعی را از Configuration استخراج کن و قطعی فرض نکن.

---

# ۸. Database و Migration

## PostgreSQL
طرح قبلی یک Database با نام پیشنهادی `video_identity_db` و Schemaهای مستقل دارد:

```text
video_identity_db
├── auth
│   ├── users
│   ├── roles
│   ├── user_roles
│   └── flyway_schema_history
└── dms
    ├── files
    └── flyway_schema_history
```

- AUTH مالک Schema `auth` است.
- DMS مالک Schema `dms` است.
- Migrationها مستقل هستند.
- Foreign Key بین Schemaهای سرویس‌ها تعریف نشود.
- هر سرویس فقط Schema خودش را مدیریت کند.

این طراحی استقلال کامل دیتابیس در سطح زیرساخت نیست؛ هر دو می‌توانند از یک PostgreSQL Instance/Database با Schemaهای مجزا استفاده کنند. جداسازی فیزیکی دیتابیس‌ها در آینده قابل بررسی است.

## H2
- برای Development محلی است، نه Production.
- H2 برای AUTH و DMS مستقل باشد.
- حالت File-Based انتخاب شده تا داده پس از Restart باقی بماند.
- H2 Console فقط در محیط توسعه فعال باشد.
- Profileهای H2 و PostgreSQL مستقل باشند.

نمونه JDBC URL طراحی‌شده:

```text
jdbc:h2:file:./data/auth-db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE
```

`MODE=PostgreSQL` سازگاری کامل PostgreSQL را تضمین نمی‌کند. در صورت نیاز Migrationهای H2 و PostgreSQL جدا باشند.

## Flyway/Hibernate
- Schema با Flyway مدیریت شود.
- از `ddl-auto=update` استفاده نکن.
- Hibernate در محیط‌های موردنظر روی `validate` باشد.
- Migration اجراشده را بدون Migration اصلاحی بازنویسی نکن.

---

# ۹. Swagger و OpenAPI

Swagger UI برای هر سه سرویس الزام دائمی است:

| سرویس | آدرس |
|---|---|
| AUTH | `http://localhost:8080/swagger-ui.html` |
| DMS | `http://localhost:8081/swagger-ui.html` |
| WORKFLOW | `http://localhost:8082/swagger-ui.html` |

مسیر مستندات JSON معمولاً `/v3/api-docs` است.

الزامات:
- Title و Version مشخص
- توضیحات Endpointها
- Request/Response Schema
- HTTP Status Codeها
- Validation Errors
- Multipart Upload
- Security Schemeها
- HTTP Basic در DMS Authorize
- هماهنگی مستندات AUTH با Session/CSRF
- Swagger در توسعه فعال و در Production پیش‌فرض غیرفعال
- Swagger AUTH بدون Login باز شود
- عمومی‌بودن Swagger به معنی عمومی‌بودن API کسب‌وکار نیست

نسخه کتابخانه انتخاب‌شده در طراحی `springdoc-openapi 3.2.0` است؛ نسخه واقعی را از POM بخوان.

---

# ۱۰. Logging و Observability

کاربر خواسته است تمام Requestها و Actionهای مهم در هر سه سرویس Log شوند.

## HTTP Logging
برای هر Request حداقل این اطلاعات:
- Request ID
- HTTP Method
- Path
- Client IP
- Remote IP
- User-Agent
- Actor یا User UUID در صورت احراز هویت
- HTTP Status
- Duration
- Outcome
- Service Name
- Timestamp

برای هر درخواست رویداد شروع و پایان ثبت شود. Exception و درخواست ناقص نیز قابل ردیابی باشد.

## Correlation
- Request ID برای هر Request
- Request ID در Response Header
- استفاده درست از MDC
- پاک‌کردن MDC در پایان Request
- Context Propagation صریح در Async آینده

## IP و Proxy
- Remote IP از Socket گرفته شود.
- `X-Forwarded-For` کورکورانه قابل اعتماد نیست.
- فقط Proxyهای Trusted اجازه تعیین Client IP از Forwarded Headers داشته باشند.
- Client نتواند با جعل Header، IP دلخواه را معتبر جلوه دهد.

## Action Audit

AUTH:
- Registration Success/Failure
- Login Success/Failure
- Logout
- Duplicate Registration
- عملیات حساس حساب

DMS:
- Upload Success/Failure
- Metadata Retrieval
- Download
- Logical Delete
- Physical Purge
- Storage Failure

WORKFLOW:
- Endpointهای موجود
- در آینده Transition Attempt/Success/Failure و Actor

## داده‌های ممنوع در Log
هرگز Password، Password Hash، Authorization Header، Cookie، Session ID، CSRF Token، JWT، Refresh Token، محتوای فایل، Multipart Body یا داده حساس غیرضروری را Log نکن.

Exception Message و Stack Trace نیز نباید Secret افشا کنند.

## فرمت و نگهداری
Structured JSON Logging ترجیح داده شده است. مسیرهای مورد انتظار نسخه تحویلی:

```text
auth/logs/auth.json.log
dms/logs/dms.json.log
workflow/logs/workflow.json.log
```

مسیر واقعی را از Configuration بخوان. Log Rotation و Retention محدود باشد؛ مقدار ۱۴ روز در نسخه قبلی گزارش شده و باید با تنظیمات واقعی تطبیق داده شود.

---

# ۱۱. WORKFLOW و StateMachine آینده

فعلاً:
- پروژه WORKFLOW مستقل است.
- موتور Workflow وجود ندارد.
- وضعیت‌های اولیه در AUTH enum هستند.
- WORKFLOW نباید به AUTH یا DMS Maven dependency داشته باشد.

طرح آینده مورد بحث:
- `WorkflowDefinition`
- `WorkflowVersion`
- `WorkflowState`
- `WorkflowTransition`
- `WorkflowInstance`
- `WorkflowHistory`
- `WorkflowGuard`

پیشنهاد معماری آینده:
- PostgreSQL: Source of Truth
- Redis: Runtime Cache و Coordination
- Versioning
- Guard و Action از Registry امن و از پیش تعریف‌شده
- حفظ امکان اجرای نسخه قبلی برای Instanceهای در حال اجرا
- انتشار نسخه جدید بدون Restart

این موارد طرح آینده‌اند، نه قابلیت فعلی.

در گفتگو، Spring Statemachine برای Spring Boot 4 گزینه مناسبی تشخیص داده نشد. فعلاً Spring Boot 4 حفظ می‌شود و انتخاب/طراحی موتور آینده به زمان اجرای Workflow موکول است. بدون بررسی مجدد، Spring Statemachine را اضافه نکن.

---

# ۱۲. Redis و RabbitMQ

## Redis
در استک آینده مطرح شده، اما فعلاً نباید بدون نیاز واقعی به جریان اصلی اضافه شود. موارد استفاده احتمالی:
- Cache
- Workflow Definition Cache
- Coordination
- Rate Limiting
- Session Store در صورت تصمیم معماری

Redis جایگزین PostgreSQL برای وضعیت پایدار کسب‌وکار نیست.

## RabbitMQ
برای ارتباط Async آینده:
- VideoUploaded
- VerificationRequested
- VerificationApproved
- VerificationRejected
- NotificationRequested

Transactional Outbox برای انتشار قابل اتکای رویدادهای وابسته به تراکنش قابل بررسی است. RabbitMQ فعلاً پیاده‌سازی نشده است.

---

# ۱۳. Skills و Coding Agentها

آرشیو اولیه شامل ۲۴ Skill بوده است. در نسخه بازبینی‌شده، ساختار استفاده برای دو ابزار در نظر گرفته شده:

```text
.claude/
└── skills/

.agents/
└── skills/

CLAUDE.md
AGENTS.md
```

آرشیو اصلی در مسیر زیر نگهداری می‌شود:

```text
docs/skill-sources/claude-original/
```

## Skills عمومی شناخته‌شده
- `architecture-review`
- `microservices-architect`
- `solid-principles`
- `design-patterns`
- `api-contract-review`
- `spring-boot-patterns`
- `clean-code`
- `java-code-review`
- `jspecify`
- `java-migration`
- `security-audit`
- `concurrency-review`
- `test-quality`
- `adversarial-reviewer`
- `logging-patterns`
- `maven-dependency-audit`
- `performance-smell-detection`
- `git-commit`
- `changelog-generator`
- `issue-triage`

## Skills اختصاصی پروژه
- `auth-security-mvc`
- `dms-file-safety`
- `dynamic-workflow-engine`
- `http-observability`

وجود Skill مربوط به Dynamic Workflow به معنی پیاده‌سازی Workflow Engine نیست.

## Skills وابسته به پروژه قبلی Mellat
این چهار مورد نباید بدون تطبیق فعال شوند:
- `add-rest-endpoint`
- `java-spring-code-review`
- `local-infra`
- `run-mellat-provider-app`

## قواعد استفاده
- قبل از کار مرتبط، Skill را واقعاً بخوان.
- صرف وجود فایل Skill به معنی استفاده از آن نیست.
- Skills معماری قبل از تغییر ساختار.
- Skills امنیتی برای Spring Security و DMS.
- Concurrency Review برای عملیات فایل و تغییر وضعیت.
- Test Quality برای طراحی تست.
- در گزارش تحویل، Skills استفاده‌شده و نتیجه واقعی را ذکر کن.
- دستورالعمل متناقض را کورکورانه اجرا نکن؛ تناقض را گزارش کن.

---

# ۱۴. تاریخچه مراحل توسعه

## مرحله ۱: اسکلت پروژه
- ایجاد AUTH، DMS و WORKFLOW.
- ابتدا Maven Multi-Module مطرح شد.
- سپس بنا به تصریح کاربر به سه پروژه مستقل در یک Workspace اصلاح شد.
- هر پروژه Build و Run مستقل دارد.

## مرحله ۲: Persistence
- طراحی PostgreSQL
- Schemaهای جدا برای AUTH و DMS
- Entity و Repositoryهای اولیه
- Flyway مستقل
- حفظ استقلال پروژه‌ها

## مرحله ۳: DMS و H2
- Upload/Download
- Logical Delete/Physical Delete
- Metadata
- ساختار دایرکتوری Subsystem/Date/User
- H2 File-Based برای Development
- حفظ PostgreSQL

## مرحله ۴: AUTH و Swagger
- Registration/Login/Logout
- BCrypt
- Roleهای USER و ADMIN
- صفحات Mustache
- صفحه انتخاب ویدئو بدون ارسال واقعی
- Swagger برای هر سه سرویس

## بازبینی مبتنی بر Skills
- بازبینی معماری و امنیت
- اصلاح بخش‌هایی از اعتبارسنجی و مدیریت خطا
- گسترش تست‌ها
- افزودن Skills به ساختار Claude Code و Antigravity

## Logging و Swagger Fix
- HTTP Logging در هر سه سرویس
- Request ID
- IP و Actor
- Status و Duration
- Action Logging
- عدم ثبت Secret و Body حساس
- رفع Login Redirect در Swagger AUTH
- تست‌های مرتبط

---

# ۱۵. تست و کیفیت

## تفاوت انواع بررسی

| نوع | معنی |
|---|---|
| Static/Structural Check | بررسی ساختار و تنظیمات |
| Java Syntax Parse | بررسی نحوی کد |
| Maven Build | اجرای واقعی `mvn clean verify` |
| Unit Test | تست واحد |
| Integration Test | تست یکپارچه با Spring و DB |
| Security Test | تست واقعی امنیت |
| Manual API Test | بررسی با HTTP Client/Postman |

## دستورات پایه

در پوشه هر پروژه:

```powershell
.\mvnw.cmd clean verify
```

اجرای AUTH با H2:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=h2'
```

اجرای DMS با H2:

```powershell
$env:DMS_API_PASSWORD = 'your-strong-local-password'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=h2'
```

Profile واقعی WORKFLOW را از سورس بررسی کن؛ در نسخه قبلی `local` گزارش شده بود.

## ترتیب پیشنهادی بررسی
1. `git status` و ساختار Workspace
2. POM هر سه پروژه
3. Java و Spring Boot Version
4. Configuration و Profileها
5. Flyway Migrationها
6. Security Configuration
7. OpenAPI
8. Logging و MDC
9. تست مستقل هر سرویس
10. `clean verify` هر سه سرویس
11. گزارش و اصلاح خطاها
12. ارائه Patch و خلاصه تحویل

**Build یا Test اجرا‌نشده را موفق اعلام نکن.** اگر محیط ابزار لازم را ندارد، دقیقاً بنویس چه چیزی اجرا نشده است.

---

# ۱۶. اصول غیرقابل‌مذاکره

1. سه پروژه مستقل باقی بمانند.
2. Maven dependency بین سرویس‌ها ایجاد نشود.
3. Workspace مشترک صرفاً برای مدیریت IDE است.
4. ارتباط AUTH و DMS از طریق API خواهد بود.
5. Workflow فعلاً فقط ساختار مستقل است.
6. وضعیت احراز هویت فعلاً enum است.
7. صفحه ویدئو فعلاً فایل را به DMS ارسال نمی‌کند.
8. ترتیب پوشه DMS: `Subsystem/Year/Month/Day/User/File`.
9. برای قبل از ایجاد حساب از `SYSTEM-AUTH-REGISTER` استفاده شود.
10. مسیر فیزیکی از Client دریافت نشود.
11. Logical و Physical Delete جدا باشند.
12. Swagger هر سه سرویس حفظ شود.
13. Swagger AUTH در توسعه بدون Login باز شود.
14. Swagger در Production پیش‌فرض غیرفعال باشد.
15. تمام Requestها و Actionهای مهم Log شوند.
16. Secret و داده حساس وارد Log نشود.
17. Forwarded IP فقط از Proxy مورد اعتماد پذیرفته شود.
18. تغییر دیتابیس با Flyway باشد.
19. H2 برای Development و PostgreSQL برای محیط پایدار حفظ شوند.
20. Security برای پاس‌شدن تست تضعیف نشود.
21. Redis و RabbitMQ بدون درخواست وارد Scope فعلی نشوند.
22. Skills واقعاً استفاده شوند و گزارش شوند.
23. تست اجرا‌نشده موفق اعلام نشود.
24. قبل از Refactor گسترده اثر تغییر بر قراردادها و داده‌ها مشخص شود.
25. هر مرحله تغییرات قابل بازبینی و مستقل داشته باشد.

---

# ۱۷. قالب گزارش تحویل Agent

در پایان هر کار این قالب را تکمیل کن:

## خلاصه
- چه چیزی تغییر کرد؟
- چرا لازم بود؟

## فایل‌های تغییرکرده
- فهرست دقیق فایل‌ها

## تصمیم‌های فنی
- API
- Database
- Security
- Logging

## Skills استفاده‌شده
- نام Skill
- بخش استفاده‌شده
- نتیجه

## تست‌ها
- دستور اجرا
- تعداد موفق
- تعداد ناموفق
- موارد اجرا‌نشده و دلیل

## ریسک‌ها و محدودیت‌ها
- موارد باقی‌مانده
- Migration/Compatibility Risk
- Security Concern

## خروجی
- Patch یا Commit
- مستندات به‌روزشده
- ZIP در صورت درخواست

---

# ۱۸. Prompt آماده برای شروع کار با Agent

متن زیر را به Coding Agent بده:

> شما مسئول ادامه توسعه پروژه Video Identity Verification هستید.
>
> ابتدا این کاتالوگ را کامل بخوانید و سپس سورس واقعی هر سه پروژه AUTH، DMS و WORKFLOW را بررسی کنید.
>
> معماری قطعی پروژه سه پروژه Maven مستقل در یک Workspace مشترک است. هیچ‌کدام نباید Maven dependency یکدیگر باشند.
>
> قبل از تغییر کد، Skillهای مرتبط را از `.claude/skills` یا `.agents/skills` بخوانید و دستورالعمل‌های `CLAUDE.md` و `AGENTS.md` را رعایت کنید.
>
> ابتدا یک Gap Analysis تهیه کنید و وضعیت واقعی کد را با این کاتالوگ مقایسه کنید. موارد پیاده‌سازی‌شده، ناقص و پیاده‌سازی‌نشده را جدا کنید.
>
> قابلیت خارج از Scope را بدون درخواست اضافه نکنید. به‌خصوص AUTH را به DMS متصل نکنید، Workflow Engine نسازید و RabbitMQ یا Redis Integration جدید اضافه نکنید، مگر اینکه صریحاً درخواست شود.
>
> امنیت، تست‌پذیری، Logging، OpenAPI و Flyway باید حفظ شوند.
>
> هیچ Build یا Test را بدون اجرای واقعی موفق اعلام نکنید.
>
> پس از Gap Analysis، برنامه مرحله‌ای ارائه کنید و مطابق درخواست کاربر پیش بروید.

---

# ۱۹. اعتبار سند

این کاتالوگ بر اساس تصمیم‌های ثبت‌شده در گفت‌وگوها و گزارش نسخه‌های تحویلی تهیه شده است. این سند تأیید مستقل تمام جزئیات سورس فعلی نیست.

**سورس کد، POMها، Migrationها، Configurationها و تست‌های قابل اجرا مرجع نهایی وضعیت واقعی هستند.**

Agent باید موارد را با سورس تطبیق دهد و اختلاف‌ها را شفاف گزارش کند. هدف سند جلوگیری از گم‌شدن تصمیم‌های معماری و Scope پروژه در ادامه توسعه است.
