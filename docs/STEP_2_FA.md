# مرحله دوم پروژه: PostgreSQL، Flyway و مدل داده

## هدف

تکمیل لایه داده دو پروژه مستقل AUTH و DMS با حفظ ساختار **سه پروژه مستقل در یک Workspace**. پروژه WORKFLOW در این مرحله فقط اسکلت است و به دیتابیس متصل نمی‌شود.

## ساختار اجرایی

- `auth/`: برنامه Spring Boot مستقل با Maven Wrapper، `pom.xml` و پورت 8080.
- `dms/`: برنامه Spring Boot مستقل با Maven Wrapper، `pom.xml` و پورت 8081.
- `workflow/`: برنامه Spring Boot مستقل با Maven Wrapper، `pom.xml` و پورت 8082.
- هیچ فایل `pom.xml` در ریشه وجود ندارد؛ هیچ وابستگی Maven از یک سرویس به سرویس دیگر تعریف نشده است.

## پایگاه داده

در محیط توسعه، هر دو سرویس روی دیتابیس `video_identity_db` در PostgreSQL قرار می‌گیرند؛ اما به Schemaهای جداگانه دسترسی دارند.

| سرویس | Schema | جداول | نسخه‌های Flyway |
|---|---|---|---|
| AUTH | `auth` | `users`, `roles`, `user_roles` | `V1`, `V2` |
| DMS | `dms` | `files` | `V1` |
| WORKFLOW | ندارد | ندارد | ندارد |

هر سرویس تاریخچه جداگانه `flyway_schema_history` دارد و Hibernate تنها ساختار جدول‌ها را اعتبارسنجی می‌کند (`ddl-auto=validate`).

### AUTH

- کاربران با شناسه UUID، ایمیل و هش رمز عبور، وضعیت فعال‌بودن، زمان ایجاد و ویرایش، و نسخه کنترل هم‌زمانی ذخیره می‌شوند.
- نقش‌های اولیه `ROLE_USER` و `ROLE_ADMIN` از طریق Flyway ثبت می‌شوند.
- ایمیل در لایه دامنه به حروف کوچک تبدیل می‌شود و در PostgreSQL ایندکس یکتای بدون حساسیت به بزرگی/کوچکی حروف دارد.
- کد ثبت‌نام و تولید هش رمز عبور هنوز پیاده‌سازی نشده‌اند؛ ذخیره رمز خام ممنوع است.

### DMS

- جدول `dms.files` فقط اطلاعات فایل را ثبت می‌کند و هنوز عملیات دیسک/HTTP پیاده‌سازی نشده است.
- مالک فایل در دو ستون `owner_type` و `owner_reference` ذخیره می‌شود و با `uploaded_by_user_id` متفاوت است.
- برای فایل‌هایی که قبل از ایجاد حساب ثبت می‌شوند: `owner_type=SYSTEM` و `owner_reference=SYSTEM-AUTH-REGISTER`.
- مسیر فایل در فاز بعد طبق قرارداد زیر ساخته خواهد شد:

```text
STORAGE_ROOT/SUBSYSTEM/YYYY/MM/DD/OWNER_ID/FILE_UUID.ext
```

- وضعیت‌های فایل: `ACTIVE`, `DELETED`, `PURGE_PENDING`, `PURGED`.
- جدول DMS هیچ کلید خارجی به جداول AUTH ندارد؛ در آینده می‌توان سرویس‌ها را در دیتابیس‌های مجزا استقرار داد.

## راه‌اندازی سریع روی Windows/PowerShell

اگر PostgreSQL از قبل نصب است، دیتابیس `video_identity_db` را برای حساب توسعه ایجاد کنید و دسترسی ایجاد Schema بدهید. یا از `docker compose up -d postgres` در ریشه Workspace استفاده کنید. مقادیر پیش‌فرض Docker Compose **فقط مخصوص محیط محلی** هستند.

ترمینال AUTH:

```powershell
$env:AUTH_DB_USERNAME = 'vik_local'
$env:AUTH_DB_PASSWORD = 'change-me-local-only'
cd auth
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

ترمینال DMS:

```powershell
$env:DMS_DB_USERNAME = 'vik_local'
$env:DMS_DB_PASSWORD = 'change-me-local-only'
cd dms
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

در هر دو برنامه آدرس پیش‌فرض JDBC برابر است با `jdbc:postgresql://localhost:5432/video_identity_db`. می‌توان با `AUTH_DB_URL` و `DMS_DB_URL` آن را تغییر داد. در صورت تغییر پورت یا نام کاربری PostgreSQL، مقادیر را متناسب تنظیم کنید.

پس از اولین اجرای موفق، مهاجرت‌ها به‌طور خودکار اعمال می‌شوند. برای بررسی:

```sql
SELECT version, description, success FROM auth.flyway_schema_history;
SELECT version, description, success FROM dms.flyway_schema_history;
SELECT name FROM auth.roles;
```

برای اجرای **تست‌های اتصال واقعی به دیتابیس**، متغیر `RUN_DB_INTEGRATION_TESTS=true` را قبل از `clean verify` تنظیم کنید. این تست‌ها نیازمند PostgreSQL در دسترس هستند و نباید روی دیتابیس عملیاتی اجرا شوند.

## محدودیت فاز

- AUTH هنوز ثبت‌نام/ورود کامل و ارسال ویدئو ندارد.
- DMS هنوز آپلود/دانلود/حذف فایل ندارد.
- Spring Security در فاز توسعه AUTH فعال می‌شود.
- WORKFLOW بدون منطق اجرایی باقی مانده و `VerificationStatus` همچنان در AUTH یک Enum است.
- خروجی این مرحله ساختار داده و کد لایه Persistence است؛ صحت اجرای Maven و مهاجرت‌ها باید با JDK 25 و PostgreSQL در محیط توسعه آزمایش شود.
