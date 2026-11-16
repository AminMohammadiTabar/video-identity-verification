# گزارش کنترل کیفیت گام ششم

**نسخه:** Step 6 — Verification Attempts + Email Outbox + Per-Service Audit + OpenTelemetry Tracing  
**تاریخ بررسی:** ۱۵ نوامبر ۲۰۲۶  
**نتیجه:** کنترل‌های ساختاری و Parse نحوی موفق؛ Build و اجرای واقعی هنوز تأیید نشده است.

---

## ۱. خلاصه تغییرات

### AUTH
- تبدیل احراز هویت به مدل Attempt-based.
- نگهداری Attemptهای قبلی پس از رد درخواست.
- Retry همان Attempt پس از شکست موقت آپلود.
- جلوگیری از ارسال مجدد پس از تأیید.
- نمایش تاریخچه و علت رد به صاحب حساب.
- API جدید `GET /api/v1/auth/verifications/history`.
- افزودن جدول `auth.email_outbox` و Worker اختیاری SMTP با Lease، Backoff و وضعیت‌های `PENDING`، `PROCESSING`، `SENT` و `DEAD`.
- ثبت Audit رویدادهای Registration، Login، Logout، CSRF/Authorization، Attempt، Review و Notification.

### DMS
- ثبت Audit مستقل برای Upload، Metadata Read، Download، Logical Delete، Purge و رویدادهای امنیتی.
- ثبت Actor به‌صورت `ADMIN` یا `SERVICE` و ثبت مالک فایل به‌عنوان Subject در رویدادهای اصلی.
- حفظ ساختار ذخیره‌سازی و کنترل دسترسی موجود.
- افزودن Spanهای کسب‌وکاری برای Upload، Download و Purge.

### WORKFLOW
- افزودن JPA، Flyway، PostgreSQL و H2 مستقل فقط برای `workflow.audit_events`.
- هیچ Workflow Engine، State، Transition یا پنل مدیریتی اضافه نشده است.

### Observability
- افزودن Spring Boot Actuator و OpenTelemetry Starter رسمی به هر سه پروژه.
- W3C Trace Context و پیکربندی OTLP.
- ثبت `traceId` و `spanId` در HTTP Access Log و Audit Context در صورت وجود.
- انتقال خودکار Trace Context از AUTH به DMS از طریق `RestClient.Builder` خودکار Spring Boot.
- اضافه‌شدن Jaeger اختیاری در Compose با Profile مستقل `observability`.

---

## ۲. بررسی‌های اجراشده

| بررسی | نتیجه | توضیح |
|---|---|---|
| `python scripts/verify_workspace.py` | **92/92 موفق** | ساختار مستقل، POMها، Migrationها، Skills، Audit و Tracing |
| `java scripts/JavaSyntaxCheck.java` | **108 فایل، صفر خطای Syntax** | فقط Parse نحوی؛ Type Check یا Dependency Resolution نیست |
| XML Parse | **3/3 موفق** | هر سه POM معتبرند |
| YAML Parse با Duplicate-Key Detection | **13/13 موفق** | هیچ کلید تکراری در فایل‌های `application*.yml` پیدا نشد |
| بررسی نبود Maven dependency بین سرویس‌ها | موفق | هیچ Import یا Dependency بین AUTH، DMS و WORKFLOW اضافه نشده است |
| بررسی ZIP قبلی گام پنجم | اصلاح شد | چهار فایل Storage در DMS در ZIP گام پنجم وجود نداشتند؛ از Baseline قبلی بازگردانده شدند |

### وضعیت محیط Build

- Java نصب‌شده در محیط بسته‌بندی: OpenJDK `21.0.12`.
- نسخه هدف پروژه: Java `25`.
- Maven Binary در محیط موجود نبود.
- اجرای Maven Wrapper برای دریافت Maven `3.9.12` به DNS نیاز داشت و با خطای `Could not resolve host: repo.maven.apache.org` متوقف شد.

بنابراین موارد زیر در این محیط اجرا نشده‌اند:

- `mvnw clean verify` برای AUTH.
- `mvnw clean verify` برای DMS.
- `mvnw clean verify` برای WORKFLOW.
- اجرای واقعی Spring Boot و Flyway روی H2/PostgreSQL.
- Integration Testهای جدید Audit Transaction، Attempt History، DMS Audit و WORKFLOW Audit.
- تست انتهابه‌انتهای Trace با Jaeger.
- ارسال واقعی ایمیل با SMTP.

**هیچ‌یک از موارد بالا موفق اعلام نمی‌شوند؛ اجرای آن‌ها با JDK 25 و دسترسی به Maven Dependencies لازم است.**

---

## ۳. Migrationهای اضافه‌شده

| سرویس | PostgreSQL | H2 | هدف |
|---|---|---|---|
| AUTH | V4 | V4 | تاریخچه Attemptها و قید یکتایی `(user_id, attempt_number)` |
| AUTH | V5 | V5 | `auth.audit_events` |
| AUTH | V6 | V6 | `auth.email_outbox` |
| DMS | V2 | V2 | `dms.audit_events` |
| WORKFLOW | V1 | V1 | `workflow.audit_events` |

Migrationهای قبلی بازنویسی نشده‌اند؛ Migrationهای جدید افزایشی هستند.

---

## ۴. Skills استفاده‌شده در بازبینی و پیاده‌سازی

اسکیل‌های اختصاصی پروژه در هر دو مسیر `.claude/skills` و `.agents/skills` به‌روزرسانی شدند. در کار این مرحله از دستورالعمل‌های زیر استفاده شد:

- `video-kyc-development`
- `auth-security-mvc`
- `dms-file-safety`
- `http-observability`
- `quality-gate-video-kyc`
- `architecture-review`
- `microservices-architect`
- `security-audit`
- `concurrency-review`
- `logging-patterns`
- `spring-boot-patterns`
- `api-contract-review`
- `test-quality`

وجود Skill در Workspace به‌تنهایی تضمین اجرای خودکار آن نیست؛ Agent باید برای هر کار مرتبط فایل Skill را بخواند و در گزارش کار خود نتیجه را ذکر کند.

---

## ۵. ریسک‌ها و موارد نیازمند بازبینی اجرایی

1. **Build و Type Check:** Parse نحوی موفق بوده، اما Type Check واقعی و Maven Build اجرا نشده است.
2. **Flyway/H2:** Migrationهای V4 تا V6 در H2 واقعی اجرا نشده‌اند؛ مخصوصاً Drop Constraint و Outbox FK باید با Integration Test تأیید شوند.
3. **OpenTelemetry:** نام Dependencyها و تنظیمات بر اساس مستندات Spring Boot 4.1.2 انتخاب شده‌اند، اما Export و Propagation در Runtime با Jaeger تأیید نشده‌اند.
4. **SMTP:** ارسال واقعی فقط با SMTP معتبر و `EMAIL_NOTIFICATIONS_ENABLED=true` فعال می‌شود. Outbox At-Least-Once است و در Crash پس از ارسال SMTP و قبل از ثبت `SENT` احتمال ارسال تکراری وجود دارد.
5. **Audit DB Permissions:** Append-Only در Application پیاده‌سازی شده است؛ Runtime DB User در Production هنوز باید از Migration Owner جدا و از مجوز UPDATE/DELETE محروم شود.
6. **PII Retention:** IP و User-Agent در Audit ذخیره می‌شوند؛ دوره نگهداری و دسترسی آن‌ها باید با سیاست سازمان تعیین شود.
7. **AUTH↔DMS Recovery:** تراکنش توزیع‌شده وجود ندارد. اگر DMS فایل را ذخیره کند ولی پاسخ Upload به AUTH نرسد، احتمال فایل یتیم وجود دارد؛ Reconciliation و در مرحله بعد Idempotency Key باید بررسی شوند.
8. **Video Memory:** `HttpDmsGateway` فعلی فایل را در حافظه به `byte[]` تبدیل می‌کند؛ برای ویدئوهای بزرگ‌تر و بار بالا باید Streaming بررسی شود.
9. **Audit UI:** API و پنل مشاهده Audit هنوز در Scope این گام نیستند.
10. **WORKFLOW:** دیتابیس و Audit آماده شده‌اند؛ موتور Workflow هنوز ساخته نشده است.

---

## ۶. معیار تحویل برای بازبینی کاربر

پیش از پذیرش گام ششم، روی سیستم توسعه با JDK 25 اجرا شود:

```powershell
cd auth
.\mvnw.cmd clean verify

cd ..\dms
.\mvnw.cmd clean verify

cd ..\workflow
.\mvnw.cmd clean verify
```

سپس سناریوهای زیر دستی بررسی شوند:

1. ثبت‌نام، ورود و ارسال ویدئو.
2. رد Attempt اول و مشاهده علت رد.
3. ارسال ویدئوی جدید و ایجاد Attempt دوم.
4. تأیید Attempt دوم و جلوگیری از ارسال مجدد.
5. مشاهده Outbox و Auditهای AUTH و DMS.
6. اجرای Jaeger و دنبال‌کردن Trace از AUTH تا DMS.
7. فعال‌سازی SMTP آزمایشی و بررسی Retry و وضعیت نهایی Outbox.
8. بررسی 401/403 و ثبت رویدادهای امنیتی بدون تضعیف CSRF یا Roleها.

**وضعیت نهایی این گزارش:** سورس و مستندات آماده بازبینی هستند؛ پذیرش اجرایی منوط به اجرای Build و Integration Testهای بالا است.
