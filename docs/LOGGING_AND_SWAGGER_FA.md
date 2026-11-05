# مستندات لاگ‌گیری و دسترسی Swagger — نسخه 4.1

## تغییرات

این نسخه بر مبنای سورس گام چهارم، همراه با رفع خطای تست 401/403، ساخته شده است. سه پروژه `auth`، `dms` و `workflow` **کاملاً مستقل** هستند؛ نه Parent POM مشترک داریم و نه Dependency بین پروژه‌ها.

### ۱. لاگ هر HTTP Request

در هر سه سرویس، `HttpAuditFilter` برای تمام درخواست‌ها (حتی خطاهای امنیتی 401 و 403، فایل‌های استاتیک و Swagger) دو رکورد ثبت می‌کند:

- `http.request.started`: آغاز درخواست، نام سرویس، شناسه پیگیری، روش HTTP، مسیر بدون Query String، IP مستقیم، User-Agent محدودشده و اندازه اعلام‌شده درخواست.
- `http.request.completed`: پایان درخواست، شناسه پیگیری، IP کلاینت، وضعیت پاسخ، زمان پاسخ‌گویی به میلی‌ثانیه، نتیجه و شناسه بازیگر در صورت احراز هویت.

به تمام پاسخ‌ها هدر `X-Request-ID` افزوده می‌شود. این شناسه در MDC هم قرار می‌گیرد تا لاگ‌های مربوط به یک Request، از جمله Action Logها، قابل جست‌وجو باشند. در پایان درخواست، MDC به حالت قبل برمی‌گردد. برای درخواست‌های Servlet Async، نتیجه در Listener مربوط به پایان اجرا ثبت می‌شود.

**ثبت اطلاعات حساس ممنوع است:** بدنه درخواست و پاسخ، رمز عبور، Cookie، Authorization، توکن CSRF، Session ID، محتوای فایل، Query String و ایمیل کاربر در لاگ قرار نمی‌گیرند.

### ۲. Action Logging

| سرویس | Actionها |
| --- | --- |
| AUTH | `AUTH_REGISTER` (موفق، ایمیل تکراری، ورودی نامعتبر)، `AUTH_LOGIN` (موفق/ناموفق)، `AUTH_LOGOUT` |
| DMS | `UPLOAD`، `READ_METADATA`، `DOWNLOAD_REQUESTED`، `LOGICAL_DELETE`، `PHYSICAL_PURGE` و خطاهای بارگذاری |
| WORKFLOW | `INFO` (فعلاً تنها Placeholder) |

در AUTH از `AccountPrincipal` با شناسه UUID استفاده شده تا درخواست‌های کاربر در لاگ با `user:<UUID>` مشخص شوند؛ ایمیل ثبت نمی‌شود. در DMS فعلاً با حساب مدیریتی HTTP Basic، حالت `authenticated` ذخیره می‌شود.

**توجه:** این لاگ‌ها عملیاتی هستند و جایگزین Audit Trail پایدار و غیرقابل‌تغییر در دیتابیس نیستند. رویداد دانلود به معنای مجازشدن/آماده‌شدن دانلود است، نه تضمین انتقال کامل بایت‌ها به کلاینت.

### ۳. ساختار و محل فایل‌ها

لاگ‌ها با قابلیت **Structured Logging** خود Spring Boot و قالب JSON سازگار با Logstash در کنسول و فایل نوشته می‌شوند. مسیر پیش‌فرض هر پروژه (نسبت به Working Directory همان سرویس):

```text
auth/logs/auth.json.log
dms/logs/dms.json.log
workflow/logs/workflow.json.log
```

اگر از داخل پوشه سرویس برنامه را اجرا کنید، فولدر `logs/` در همان پوشه ساخته می‌شود. این مسیرها به Git اضافه نمی‌شوند. با `APP_LOG_FILE` می‌توان فایل را تغییر داد. محدودیت Rolling پیش‌فرض ۲۰ مگابایت برای هر فایل، ۱۴ روز تاریخچه و سقف ۳۰۰ مگابایت حجم است.

نمونه فرضی یک رکورد JSON (مقادیر صرفاً نمایشی):

```json
{"event":"http.request.completed","service":"auth","requestId":"c8f4e7d1-1234-4ad3-a642-a3cfbdc2c108","method":"GET","path":"/swagger-ui/index.html","remoteIp":"127.0.0.1","clientIp":"127.0.0.1","actor":"anonymous","status":200,"durationMs":24,"outcome":"completed"}
```

فایل‌های لاگ شامل IP هستند و باید از نظر دسترسی فایل، زمان نگهداری و حفاظت داده‌ها مدیریت شوند.

### ۴. تشخیص IP پشت Reverse Proxy

به‌صورت پیش‌فرض، **IP اتصال مستقیم به سرور** مبنای لاگ است و هدر `X-Forwarded-For` کلاینت مورد اعتماد نیست. برای تنظیم IP پروکسی مورد اعتماد:

```powershell
$env:AUDIT_TRUSTED_PROXIES = '127.0.0.1,10.0.0.10'
```

فقط IP سرورهای Proxy تحت کنترل خودتان را وارد کنید. NGINX یا Tunnel در نقطه ورودی شبکه باید `Forwarded` و `X-Forwarded-*` ارسالی کاربر را پاک و مقدار معتبر خود را جایگزین کند؛ در غیر این صورت امکان جعل IP وجود دارد.

### ۵. مشکل ریدایرکت Swagger به Login در AUTH

در `AuthSecurityConfiguration` دسترسی به این مسیرها در تمام پروفایل‌ها (وقتی Swagger فعال است) عمومی شده است:

```text
/swagger-ui.html
/swagger-ui/**
/v3/api-docs/**
/v3/api-docs.yaml
```

بنابراین درخواست Swagger به `/login` منتقل نمی‌شود. البته `swagger-ui.html` ممکن است به شکل طبیعی به `/swagger-ui/index.html` ریدایرکت شود که **ریدایرکت ورود نیست**.

**امنیت APIهای اصلی تغییری نکرده است:** ثبت‌نام همچنان CSRF دارد، API حساب کاربری نیازمند احراز هویت است و `/admin` فقط برای ADMIN مجاز است. فعال‌سازی Swagger در محیط توسعه با Profileهای `h2`/`local` انجام می‌شود. برای سایر محیط‌ها باید `SWAGGER_ENABLED=true` را صریحاً تنظیم کنید؛ در Production پیش‌فرض غیرفعال است.

### ۶. اجرا و آزمایش در ویندوز

برای هر پروژه جداگانه، با JDK 25:

```powershell
cd auth
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=h2'
```

در ترمینال دیگر:

```powershell
cd dms
$env:DMS_API_PASSWORD = 'a-strong-local-secret'
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=h2'
```

برای `workflow`، پروفایل `local` را استفاده کنید. سپس آدرس‌های زیر را بررسی کنید:

```text
http://localhost:8080/swagger-ui.html
http://localhost:8081/swagger-ui.html
http://localhost:8082/swagger-ui.html
```

آزمایش دریافت هدر Correlation ID:

```powershell
curl.exe -i http://localhost:8080/swagger-ui.html
curl.exe -i http://localhost:8080/v3/api-docs
```

### ۷. آزمون‌ها و وضعیت تأیید

تست‌های جدید برای ثبت لاگ شروع/پایان، رد IP جعلی، پذیرش IP از پروکسی مورد اعتماد، بازگردانی MDC، ثبت شناسه Request و بازشدن Swagger بدون Login نوشته شده‌اند. بررسی نحوی Java و کنترل ساختار پروژه اجرا شده؛ اما Maven Build و Integration Test واقعی در محیط تحویل **اجرا نشده‌اند**. لازم است روی سیستم توسعه با JDK 25، برای هر سه پروژه `clean verify` اجرا شود.

در صورت نیاز به جزئیات فنی انگلیسی، فایل `docs/LOGGING_AND_SWAGGER_EN.md` در بسته قرار دارد.
