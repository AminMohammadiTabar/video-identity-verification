> **یادداشت نسخه:** این سند فقط وضعیت گام پنجم را توصیف می‌کند. برای وضعیت جاری، تاریخچه Attemptها، ایمیل، Audit و Distributed Tracing به `README.md` و `STEP_6_IMPLEMENTATION_FA.md` مراجعه کنید.

# گام پنجم: یکپارچه‌سازی AUTH و DMS و بررسی درخواست توسط ادمین

## محدوده

این نسخه جریان اصلی احراز هویت ویدئویی را تا مرحله تصمیم ادمین پیاده‌سازی می‌کند. ماژول WORKFLOW همچنان مستقل و بدون موتور اجرایی است. ارسال ایمیل، احراز هویت مجدد پس از رد، RabbitMQ، Redis و Workflow داینامیک فعلاً پیاده‌سازی نشده‌اند.

## معماری

- سه پروژه Maven جدا: `auth` (8080)، `dms` (8081) و `workflow` (8082).
- ارتباط AUTH و DMS فقط از طریق HTTP است؛ هیچ Maven dependency مشترکی افزوده نشده است.
- مرورگر با JavaScript فایل و CSRF Header را به AUTH می‌فرستد؛ AUTH از طریق `HttpDmsGateway` فایل را به DMS ارسال می‌کند.
- AUTH برای DMS از حساب اختصاصی `ROLE_AUTH_SERVICE` استفاده می‌کند؛ کلاینت به رمز سرویس دسترسی ندارد.
- ادمین ویدئو را از Endpoint محافظت‌شده AUTH می‌بیند؛ مرورگر هیچ تماس مستقیم به DMS ندارد.
- DMS حساب سرویس را به Upload/Download/Delete/Purge فایل‌های متعلق به `AUTH` و `USER` محدود می‌کند. سایر عملیات در اختیار حساب مدیریتی DMS باقی می‌ماند.

## وضعیت‌ها

```text
NOT_SUBMITTED (فقط وضعیت مجازی در UI)
       |
       v
   UPLOADING ----[خطای آپلود]---> UPLOAD_FAILED --[retry]--> UPLOADING
       |
       v
 PENDING_REVIEW
    /       \
   v         v
APPROVED   REJECTED
```

تا قبل از ثبت درخواست، رکوردی برای کاربر وجود ندارد و UI `NOT_SUBMITTED` را نشان می‌دهد. با شروع ارسال، یک رکورد در `auth.verification_requests` ایجاد می‌شود. هم‌زمانی برای هر کاربر با قفل روی رکورد User و قید یکتایی `user_id` محدود شده است. تصمیم ادمین تنها در وضعیت `PENDING_REVIEW` مجاز است و با قفل دیتابیس ثبت می‌شود.

## Migration

در AUTH:

- PostgreSQL: `db/migration/V3__verification_requests.sql`
- H2: `db/migration-h2/V3__verification_requests.sql`

ستون‌ها: `id`, `user_id`, `dms_file_id`, `status`, `submitted_at`, `reviewed_at`, `reviewed_by`, `review_reason`, `version`.

کاربران و نقش‌ها در جداول قبلی باقی مانده‌اند. هیچ Foreign Key بین دیتابیس یا Schema دو سرویس وجود ندارد. DMS Schema جدیدی در این گام دریافت نکرده است.

## پیکربندی اتصال

| متغیر | سرویس | توضیح |
| --- | --- | --- |
| `AUTH_DMS_URL` | AUTH | پیش‌فرض `http://127.0.0.1:8081` |
| `AUTH_DMS_USERNAME` | AUTH | پیش‌فرض `auth-service` |
| `AUTH_DMS_PASSWORD` | AUTH | رمز اختصاصی سرویس |
| `DMS_AUTH_SERVICE_USERNAME` | DMS | پیش‌فرض `auth-service` |
| `DMS_AUTH_SERVICE_PASSWORD` | DMS | همان رمز اختصاصی سرویس |
| `DMS_API_PASSWORD` | DMS | رمز مدیریتی جداگانه DMS |

**قبل از آپلود واقعی باید هر دو سرویس در حال اجرا باشند و رمز حساب سرویس در دو سمت برابر باشد.** استفاده از HTTP فقط روی Loopback محلی مجاز است؛ برای سرور مجزا HTTPS لازم است.

## سناریوهای بازبینی دستی

۱. DMS را با پروفایل `h2` و دو رمز ادمین و سرویس اجرا کنید.

۲. AUTH را با پروفایل `h2` اجرا کنید؛ برای ایجاد ادمین اولیه در دیتابیس تازه از Bootstrap صریح استفاده کنید.

۳. یک حساب `ROLE_USER` ثبت کنید، وارد شوید و ویدئوی MP4/WebM واقعی را از `/verification/upload` ارسال کنید.

۴. در AUTH درخواست با وضعیت `PENDING_REVIEW` و شناسه `dms_file_id` در جدول ثبت می‌شود. DMS فایل را در مسیر `AUTH/YYYY/MM/DD/USER_UUID/UUID.ext` ذخیره می‌کند.

۵. کاربر با تلاش مجدد برای ارسال، پاسخ تضاد وضعیت دریافت می‌کند (در REST: `409`).

۶. با ادمین وارد شوید، `/admin` را باز کنید، ویدئو را مشاهده کنید و تأیید یا رد را ثبت کنید. درخواست دیگر نباید در فهرست Pending باشد.

۷. بررسی کنید کاربر عادی به `/admin` و APIهای ادمین دسترسی ندارد؛ درخواست POST بدون CSRF باید رد شود.

۸. DMS را خاموش کنید و با کاربر جدید آپلود را آزمایش کنید؛ وضعیت `UPLOAD_FAILED` باید ثبت شود و امکان تلاش مجدد وجود داشته باشد.

۹. Swagger UI در AUTH بدون Redirect به Login باز شود، اما برای APIهای محافظت‌شده همچنان به Session معتبر نیاز باشد.

## تست‌ها

- `auth`: `VerificationLifecycleH2IntegrationTest`
- `dms`: `DmsAuthServiceScopeH2IntegrationTest`
- تست‌های قبلی AUTH، DMS و WORKFLOW هم باید اجرا شوند.

از هر پوشه سرویس `./mvnw clean verify` یا در ویندوز `.\mvnw.cmd clean verify` را اجرا کنید.

**وضعیت تأیید:** اجرای واقعی Build و Integration Testها در این محیط انجام نشده است. گزارش کنترل ایستا در `docs/STEP_5_QUALITY_REVIEW_FA.md` قرار می‌گیرد.

## ریسک‌های شناخته‌شده

- ذخیره‌سازی فایل DMS و ثبت درخواست AUTH در یک تراکنش توزیع‌شده نیستند. خطای میانی ممکن است فایل Orphan ایجاد کند؛ شناسه در Log با رویداد `verification.orphan` ثبت می‌شود و به Reconciliation عملیاتی نیاز دارد.
- Upload و Playback در AUTH در این نسخه `byte[]` با محدودیت ۵۰ MiB دارند. برای مقیاس Production باید Streaming، Range Requests، محدودیت هم‌زمانی و Rate Limiting طراحی شود.
- اطمینان از واقعی‌بودن هویت شخص در ویدئو (Liveness/Face Matching) فعلاً خارج از Scope است؛ تصمیم ادمین دستی است.
- لاگ عملیاتی جایگزین Audit Trail مقاوم و دائمی نیست. در فازهای بعد Audit History و در صورت نیاز Outbox اضافه شود.
- درخواست ردشده در این گام قابلیت احراز هویت مجدد ندارد؛ قابلیت مربوطه جداگانه پیاده‌سازی خواهد شد.
