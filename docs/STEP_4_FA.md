# گام چهارم — AUTH و Swagger UI در سه سرویس مستقل

## تغییرات این گام

**AUTH:**

- ثبت‌نام HTML با Mustache و POST امن (CSRF فعال).
- ثبت‌نام JSON از طریق `POST /api/v1/auth/registrations` برای مصرف‌کنندگان API؛ اعتبارسنجی ایمیل/رمز، کنترل تکراری بودن حساب و ذخیره هش BCrypt.
- ورود و خروج مبتنی بر Session و فرم استاندارد Spring Security؛ بدون JWT یا Keycloak.
- بارگذاری کاربران از دیتابیس و اعمال `ROLE_USER`/`ROLE_ADMIN`؛ ثبت‌نام عمومی همیشه فقط `ROLE_USER` می‌گیرد.
- Bootstrap اختیاری و یک‌باره ادمین با متغیر محیطی؛ هیچ رمز عبور پیش‌فرضی در کد ذخیره نشده است.
- صفحه `/verification/upload` فقط فایل را در مرورگر **انتخاب** می‌کند؛ عمداً هیچ فرم ارسال یا ارتباطی با DMS ندارد. وضعیت به‌صورت ثابت `NOT_SUBMITTED` نمایش داده می‌شود. تاریخچه و درخواست احراز هویت در این گام ایجاد نمی‌شود.
- صفحه `/admin` تنها کنترل دسترسی نقش ادمین را نشان می‌دهد؛ بررسی/تأیید ویدئو هنوز پیاده‌سازی نشده است.

**DMS:** پیاده‌سازی فایل در گام قبل حفظ شد. توصیف OpenAPI همه APIهای بارگذاری، دریافت، حذف منطقی و فیزیکی، خطاها، **HTTP Basic** و فرمت multipart به آن اضافه شد.

**WORKFLOW:** بدون موتور StateMachine و بدون دیتابیس؛ فقط `GET /api/v1/workflow/info` با وضعیت `NOT_IMPLEMENTED` برای مستندات و بررسی راه‌اندازی.

## آدرس Swagger و مستندات استاندارد

| سرویس | رابط Swagger | سند OpenAPI |
|---|---|---|
| AUTH | `http://localhost:8080/swagger-ui.html` | `/v3/api-docs/auth-api` |
| DMS | `http://localhost:8081/swagger-ui.html` | `/v3/api-docs/dms-api` |
| WORKFLOW | `http://localhost:8082/swagger-ui.html` | `/v3/api-docs/workflow-api` |

از کتابخانه `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0` استفاده شده که برای Boot 4.1 سازگار است. هر سرویس OpenAPI Configuration مستقل، عنوان/نسخه، گروه Endpointها، مدل امنیت و توضیحات پاسخ دارد.

### نکته مهم CSRF در Swagger AUTH

Spring Security برای ثبت‌نام API نیز CSRF را غیرفعال نکرده است. استفاده در Swagger به این صورت است:

1. `GET /api/v1/auth/csrf` را در Swagger اجرا کنید تا Session Cookie و مقدار توکن دریافت شود.
2. مقدار `token` را در پارامتر Header با نام `X-CSRF-TOKEN` برای `POST /api/v1/auth/registrations` قرار دهید.
3. مرورگر باید همان Cookie مرحله قبل را به‌صورت خودکار ارسال کند.

برای تست ساده از PowerShell نمونه موجود در README استفاده کنید. برای دریافت `/api/v1/auth/me` باید ابتدا با فرم `/login` وارد شده باشید و درخواست را در همان مرورگر ارسال کنید.

### امنیت در محیط‌ها

- `h2` و `local`: Swagger و API docs فعال؛ در Production به‌صورت پیش‌فرض خاموش.
- `DMS`: درخواست‌های API نیازمند HTTP Basic هستند؛ در محیط تولید **حتماً HTTPS** لازم است.
- `AUTH`: Session Cookie و CSRF فعال؛ کنترل مسیر `/admin` بر اساس ROLE_ADMIN؛ مسیر `/verification/upload` برای کاربران واردشده.
- H2 Console فقط با پروفایل `h2` و در آدرس Loopback فعال است.
- نگهداری Production با PostgreSQL؛ H2 فقط برای توسعه محلی.

## روش اجرای گام چهارم

برای هر سرویس از Terminal جدا در یک Workspace مشترک استفاده کنید؛ هیچ وابستگی Maven بین سرویس‌ها وجود ندارد.

```powershell
# Terminal 1: AUTH
cd auth
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=h2'

# Terminal 2: DMS
cd dms
$env:DMS_API_PASSWORD = 'a-strong-local-password'
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=h2'

# Terminal 3: WORKFLOW
cd workflow
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

## محدوده تعهدات

گام چهارم **شامل** ثبت‌نام واقعی، ورود/خروج، Role-Based Authorization، صفحات Mustache و Swagger واقعی هر سه سرویس است. **شامل نیست:** آپلود واقعی از AUTH به DMS، بررسی درخواست‌ها توسط ادمین، ایمیل، احراز هویت مجدد، RabbitMQ یا Workflow Engine.

## آزمون‌ها و محدودیت اجرا

- تست‌های مبتنی بر H2/MockMvc برای ثبت‌نام، رمز هش‌شده، لاگین/لاگ‌اوت، CSRF، نقش ادمین، قرارداد OpenAPI، امنیت DMS و وضعیت WORKFLOW در سورس اضافه شده‌اند.
- بررسی نحوی ساختار XML/YAML و استقلال پروژه‌ها انجام می‌شود؛ ولی امکان اجرای کامل Build و تست‌های Spring در محیط تولید بسته وجود ندارد (فقط JDK 21 و بدون Maven/dependency network). نتیجه موفق Build را **نباید مفروض گرفت**. لطفاً برای تأیید نهایی با JDK 25 فرمان `clean verify` را برای هر سه پروژه اجرا کنید.
