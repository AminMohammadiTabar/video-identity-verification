# مرحله سوم: DMS عملیاتی + پروفایل H2

## دامنه نهایی این مرحله

پروژه همچنان شامل سه اپلیکیشن مستقل `auth`، `dms` و `workflow` است. هیچ وابستگی Maven، کلاس مشترک یا Parent POM بین سرویس‌ها ایجاد نشده است. ارتباط واقعی از فرم AUTH به DMS و موتور Workflow برای فاز بعدی باقی مانده‌اند.

### DMS

- آپلود `multipart/form-data` با چهار فیلد `subsystem`، `ownerType`، `ownerReference` و `file`
- تولید نام فایل UUID، ذخیره بر اساس **زیرسیستم ← سال ← ماه ← روز ← شناسه مالک ← فایل**
- دانلود فایل فعال و دریافت Metadata بدون افشای مسیر فیزیکی
- حذف منطقی و حذف فیزیکی چندمرحله‌ای و قابل تکرار
- ذخیره `SHA-256`، حجم، نام اصلی، نام ذخیره‌شده و سایر مشخصات در جدول `dms.files`
- محدودیت ۵۰ مگابایت و بررسی امضای اولیه PDF، PNG، JPEG، MP4 و WebM
- کنترل دسترسی مستقل روی APIهای DMS با Spring Security، حساب ادمین و HTTP Basic

حذف فیزیکی را فقط پس از حذف منطقی اجرا کنید. وضعیت `PURGE_PENDING` برای بازیابی در شرایطی است که حذف از هارد یا ثبت نهایی وضعیت در دیتابیس با مشکل روبه‌رو شود. این راهکار **جایگزین ابزار reconciliation** برای فایل‌های جاافتاده پس از Crash نیست.

### H2 در توسعه

برای AUTH و DMS یک پروفایل `h2` و Migrationهای Flyway سازگار با H2 اضافه شده‌اند. هر کدام دیتابیس فایل‌محور مختص خود را دارند؛ با توقف برنامه پاک نمی‌شوند. از `application.yml` و Migrationهای PostgreSQL برای تولید واقعی استفاده کنید.

H2 سازگاری صددرصد با PostgreSQL ندارد؛ مثلاً unique index بر اساس `lower(email)` در Migration مخصوص PostgreSQL و unique index ساده در H2 متفاوت هستند. بنابراین تست رفتار اختصاصی PostgreSQL باید روی خود PostgreSQL اجرا شود.

### اجرای محلی در ویندوز

برای AUTH:

```powershell
cd auth
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=h2'
```

برای DMS، در ترمینال مستقل:

```powershell
cd dms
$env:DMS_API_PASSWORD = 'a-long-local-secret'
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=h2'
```

آدرس DMS: `http://localhost:8081` و کنسول H2 آن: `http://localhost:8081/h2-console`. نام کاربری پیش‌فرض API در محیط توسعه `dms-admin` است. پسورد پیش‌فرض وجود ندارد و باید از متغیر محیطی تعیین شود.

نمونه فراخوانی در PowerShell:

```powershell
curl.exe -u 'dms-admin:a-long-local-secret' `
  -F 'subsystem=AUTH' `
  -F 'ownerType=SYSTEM' `
  -F 'ownerReference=SYSTEM-AUTH-REGISTER' `
  -F 'file=@C:\temp\sample.pdf' `
  http://localhost:8081/api/v1/dms/files
```

سایر APIها و دستورات دانلود و حذف در README ریشه پروژه آمده‌اند.

### امنیت و ملاحظات Production

- رمز DMS را در Git ثبت نکنید؛ خارج از محیط محلی از HTTPS و Secret Management استفاده کنید.
- کنسول H2 تنها در پروفایل توسعه فعال است و سرویس در همین پروفایل روی `127.0.0.1` گوش می‌دهد.
- DMS از مالکیت ادعاشده توسط کلاینت فقط پس از احراز هویت ادمین فعلی استفاده می‌کند. هنگام اتصال AUTH باید مالکیت فایل را با هویت معتبر درخواست‌دهنده یکپارچه کنیم.
- کنترل محدود امضای فایل، جایگزین اسکن بدافزار، بررسی کامل محتوای ویدئو یا تشخیص فایل‌های خطرناک نیست.
- پوشه ذخیره‌سازی در مکان خصوصی سرور و خارج از Static/Web Root قرار بگیرد.
- آدرس مسیر فایل از `subsystem` و مالک معتبر ساخته می‌شود؛ نام اصلی فایل برای مسیر فیزیکی استفاده نمی‌شود.
- به‌دلیل تفاوت در رفتار دیتابیس و سیستم فایل، یک عملیات تطبیق دوره‌ای (reconciliation) برای نسخه Production آینده پیشنهاد می‌شود.

### تست

تست‌های H2 برای ساخت جداول AUTH، آپلود و دانلود DMS، انتقال وضعیت حذف، عدم دسترسی بدون احراز هویت و ورودی نامعتبر افزوده شده‌اند. در محیط ساخت فایل، Build کامل قابل اجرا نبود (JDK 21 و نبود Maven/وابستگی‌ها). پس از اجرای `clean verify` در سیستم توسعه، خروجی را برای بررسی خطاهای احتمالی ارسال کنید.
