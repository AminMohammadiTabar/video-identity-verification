# گام ششم — راهنمای پیاده‌سازی و بازبینی

این سند مکمل `README.md` و `STEP6_AUDIT_TRACING_TECHNICAL_DESIGN_FA.md` است.

## ۱. تغییرات AUTH

- `verification_requests` اکنون تاریخچه Attemptها را نگه می‌دارد.
- `GET /api/v1/auth/verifications/history` فهرست تلاش‌های کاربر را با ترتیب جدیدترین به قدیمی‌ترین برمی‌گرداند.
- رد درخواست اجازه ایجاد Attempt جدید می‌دهد؛ Attempt قبلی با `REJECTED` و علت رد باقی می‌ماند.
- `UPLOAD_FAILED` همان Attempt را قابل Retry می‌کند.
- تصمیم ادمین و Audit Event و رکورد Outbox (با شناسه کاربر موضوع پیام، نه ایمیل در Audit) در یک تراکنش AUTH ثبت می‌شوند.
- ایمیل تأیید/رد از Outbox ارسال می‌شود؛ SMTP پیش‌فرض خاموش است.

## ۲. تغییرات Audit

| سرویس | جدول | Migration |
|---|---|---|
| AUTH | `auth.audit_events` | `V5__create_audit_events.sql` |
| DMS | `dms.audit_events` | `V2__create_audit_events.sql` |
| WORKFLOW | `workflow.audit_events` | `V1__create_audit_events.sql` |

در هر سرویس `AuditEventRecorder`، `AuditEventRepository` و `AuditContextProvider` مستقل وجود دارند. هیچ سرویس برای ثبت Audit، سرویس دیگری را فراخوانی نمی‌کند.

- `record()` با `REQUIRED` برای تغییر موفق کسب‌وکار استفاده می‌شود.
- `recordIndependent()` با `REQUIRES_NEW` برای رویدادهای شکست/امنیتی استفاده می‌شود که باید از Rollback تراکنش اصلی جان سالم به در ببرند.
- Repository عمومی Insert و Query شمارشی محدود دارد؛ Update/Delete در Application API وجود ندارد.
- در Production باید Runtime DB User از Migration Owner جدا شود و مجوز UPDATE/DELETE جدول Audit نداشته باشد.

## ۳. رویدادهای کلیدی

AUTH:
- Registration Success/Rejected
- Login Success/Failure و Logout
- CSRF/Authorization Denied
- Verification Attempt Created/Denied
- Upload Started/Failed/Submitted
- Review Approved/Rejected/Denied
- Email Queued/Sent/Failed

DMS:
- Upload Success/Failure
- Metadata Read و Download Started/Failed
- Logical Delete
- Purge Started/Succeeded/Failed
- Authentication/Access Denied

WORKFLOW:
- فعلاً جدول و Recorder مستقل دارد؛ تا زمان ساخت Workflow Engine، رویداد Transition کسب‌وکاری ندارد.

## ۴. Distributed Tracing

در هر سه POM از `spring-boot-starter-opentelemetry` و `spring-boot-starter-actuator` استفاده می‌شود. W3C propagation فعال است.

- `X-Request-ID`: شناسه همبستگی برنامه.
- `traceId`: شناسه Trace توزیع‌شده.
- `spanId`: شناسه یک مرحله از Trace.

AUTH باید از `RestClient.Builder` خودکار پیکربندی‌شده Spring Boot استفاده کند تا `traceparent` هنگام فراخوانی DMS منتقل شود. `HttpAuditFilter` مقدارهای Trace/Span را در Log تکمیل درخواست ثبت می‌کند و `AuditContextProvider` در صورت موجود بودن آن‌ها را در Audit ذخیره می‌کند.

برای مشاهده محلی:

```powershell
docker compose --profile observability up -d jaeger
$env:OTEL_EXPORTER_OTLP_ENABLED = 'true'
$env:OTEL_EXPORTER_OTLP_TRACES_ENDPOINT = 'http://127.0.0.1:4318/v1/traces'
```

سپس هر سه سرویس را اجرا و در `http://localhost:16686` Traceها را بررسی کنید. اگر Collector اجرا نیست، `OTEL_EXPORTER_OTLP_ENABLED=false` بماند؛ این کار نباید درخواست‌های کسب‌وکار را متوقف کند.

## ۵. فعال‌سازی SMTP

SMTP فقط با `EMAIL_NOTIFICATIONS_ENABLED=true` فعال می‌شود. لازم است `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` و `EMAIL_FROM` تنظیم شوند. تنظیمات را در فایل یا Git Commit نکنید.

Outbox حتی با SMTP خاموش ساخته می‌شود؛ در این حالت رکوردها `PENDING` می‌مانند و بعد از فعال‌سازی SMTP قابل ارسال‌اند.

## ۶. سناریوهای بازبینی دستی

1. یک کاربر جدید ثبت‌نام و Login کند.
2. ویدئو ارسال کند؛ Attempt شماره ۱ باید `PENDING_REVIEW` شود.
3. ادمین آن را رد و علت رد ثبت کند.
4. کاربر صفحه خود را باز کند؛ علت رد و تاریخچه Attempt شماره ۱ را ببیند.
5. کاربر ویدئوی جدید ارسال کند؛ Attempt شماره ۲ ایجاد شود و Attempt اول باقی بماند.
6. ادمین Attempt دوم را تأیید کند؛ پس از آن ارسال جدید باید `409` دریافت کند.
7. در `auth.email_outbox` برای هر تصمیم، رکورد `PENDING` ببینید. SMTP خاموش باشد، ایمیل ارسال نمی‌شود.
8. در `auth.audit_events` رویدادهای Attempt، Submit، Reject/Approve و Email Queued را بررسی کنید.
9. در `dms.audit_events` Upload/Download/Delete/Purge را بررسی کنید.
10. Jaeger را فعال کنید و Trace یک آپلود را از AUTH تا DMS دنبال کنید؛ Trace ID مشترک و Span IDهای متفاوت را بررسی کنید.
11. کاربر عادی را به `/admin` یا API ادمین بفرستید؛ باید دسترسی رد و Audit امنیتی ثبت شود.
12. بدون CSRF درخواست تغییر وضعیت بفرستید؛ پاسخ 403 باید حفظ شود و رویداد CSRF ثبت شود.

## ۷. محدودیت‌ها

- Build و Integration Testهای واقعی باید با JDK 25 اجرا شوند.
- SMTP ارسال Exactly-Once تضمین نمی‌کند؛ پس از ارسال موفق و قبل از ثبت `SENT` احتمال ارسال مجدد پس از Crash وجود دارد.
- AUTH↔DMS تراکنش توزیع‌شده نیست؛ Reconciliation برای فایل یتیم یا نتیجه مبهم لازم است.
- Audit API/پنل مشاهده و سیاست Retention هنوز وجود ندارد.
- Workflow Engine، Redis و RabbitMQ در این گام پیاده‌سازی نشده‌اند.
