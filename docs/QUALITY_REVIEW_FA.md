# گزارش بازبینی و بهبود سورس گام چهارم با اسکیل‌ها

**مبدأ:** `video-identity-verification-step4.zip` (نسخه تحویلی قبلی). هیچ‌کدام از سه پروژه با یک پروژه جدید جایگزین نشده‌اند؛ قابلیت‌های موجود در AUTH و DMS و placeholder ماژول WORKFLOW حفظ شده‌اند.

## اسکیل‌های مبنای بازبینی

`architecture-review`, `spring-boot-patterns`, `java-code-review`, `security-audit`, `concurrency-review`, `test-quality`, `api-contract-review`, `clean-code`, `logging-patterns`, `maven-dependency-audit` در کنار قراردادهای جدید `video-kyc-development`, `dms-file-safety`, `auth-security-mvc`, `quality-gate-video-kyc`.

این گزارش یک **بازبینی هدفمند روی کد و قراردادهای فعلی** است، نه ادعای تأیید رسمی امنیتی/پنتست یا بی‌نقص بودن تمام کدها.

## ایرادهای مشاهده‌شده و اصلاحات انجام‌شده

| شدت | فایل/بخش | مشاهده در نسخه پیشین | اصلاح این نسخه |
|---|---|---|---|
| متوسط | `auth/service/RegistrationService.java` | تمام `DataIntegrityViolationException`ها به «ایمیل تکراری» تبدیل می‌شدند و می‌توانستند خطاهای دیگر دیتابیس را مخفی کنند. | تنها SQLSTATE `23505` به خطای ایمیل تکراری تبدیل می‌شود؛ سایر خطاها دوباره پرتاب می‌شوند. |
| متوسط | `auth/service/RegistrationService.java` و `AdminBootstrap.java` | Service فقط خالی/طولانی بودن ایمیل را بررسی می‌کرد و اعتبارسنجی از لایه UI قابل دورزدن بود؛ Bootstrap اعتبارسنجی متفاوتی داشت. | اعتبارسنجی و نرمال‌سازی مشترک ایمیل در Service و Bootstrap؛ اضافه‌شدن تست‌های منفی. |
| متوسط | `dms/storage/LocalDiskStorage.java` | بررسی symlink از مسیر root شروع می‌شد و symlink بودن **اجداد دایرکتوری root** را بررسی نمی‌کرد. | اعتبارسنجی تمام اجزای مسیر absolute از filesystem root تا فایل، همراه با تست روی parent symlink. |
| کم | `auth/web/AuthApiController.java` | پارامتر `X-CSRF-TOKEN` به صورت آرگومانِ بلااستفاده کنترلر بود، و پاسخ CSRF صراحتاً `no-store` نداشت. | مستندسازی Header در OpenAPI به جای پارامتر بی‌استفاده، تعیین `Cache-Control: no-store` و تست رگرسیون. |
| کم | `auth/config/AuthSecurityConfiguration.java` | قواعد مجاز برای مسیرهای REST محدود به HTTP Method نبودند. | تفکیک صریح GET/POST، با الگوی deny-by-default برای سایر متدها. |
| کم | `dms/service/DmsFileService.java` | لاگ عملیاتی موفق آپلود و حذف برای عیب‌یابی وجود نداشت. | لاگ با UUID، عملیات و حجم اضافه شد؛ نام واقعی فایل، ایمیل، توکن و مسیر فیزیکی لاگ نمی‌شوند. |

## مواردی که عمداً تغییر نکرده‌اند

- AUTH هنوز فایل را به DMS نمی‌فرستد. فرم انتخاب ویدئو فقط UI است.
- وضعیت‌های احراز هویت در `VerificationStatus` یک enum ثابت باقی مانده‌اند؛ Workflow Engine داینامیک و RabbitMQ توسعه داده نشده‌اند.
- هیچ Dependency بین `auth`, `dms`, `workflow` وجود ندارد. هر کدام JVM و پورت مستقل دارند.
- ساختار DMS: `SUBSYSTEM/YYYY/MM/DD/OWNER_ID/UUID.ext` و مالک سیستمی `SYSTEM-AUTH-REGISTER`.
- PostgreSQL با Flyway اصلی و H2 در پروفایل توسعه مستقل باقی مانده‌اند.
- Swagger/OpenAPI هر سه سرویس، با سیاست توسعه/Production قبلی، حفظ شده‌اند.

## تست‌های افزوده‌شده

- `RegistrationServiceReviewTest`: نرمال‌سازی ایمیل، ورودی نامعتبر، محدودیت بایت رمز عبور، تمایز SQLSTATE `23505` از خطاهای دیگر دیتابیس.
- `LocalDiskStorageSafetyTest`: پوشه روز سپس کاربر، نام فیزیکی UUID، رد traversal، پاکسازی فایل نامعتبر، رد symlink والد مسیر ذخیره‌سازی (در سیستم‌های فاقد پشتیبانی تست آخر Skip می‌شود).
- `AuthFlowH2IntegrationTest`: بررسی `no-store` روی توکن CSRF و رد متد POST نامعتبر روی مسیر CSRF.

## نتیجه واقعی اعتبارسنجی در محیط تولید این بسته

| بررسی | نتیجه | توضیح |
|---|---|---|
| تحلیل ساختار Maven و استقلال سه سرویس | PASS | `scripts/verify_workspace.py`؛ ۵۴ مورد مستقل |
| وجود اسکیل‌های قابل شناسایی برای هر دو ابزار | PASS | ۲۴ اسکیل فعال در هر مسیر؛ ۲۴ اصل در آرشیو |
| XML/POM و YAML | PASS | Parsing در فرآیند بسته‌بندی، بدون اجرا یا رزولوشن dependency |
| Java syntax parse (۵۸ منبع) | PASS | `java scripts/JavaSyntaxCheck.java` روی JDK 21 با `JavacTask.parse`؛ فقط نحوی، بدون Type Checking و کامپایل |
| اجرای `./mvnw clean verify` برای AUTH | NOT RUN | محیط تنها JDK 21 دارد؛ JDK 25، Maven cache و دسترسی Maven Central فراهم نیست |
| اجرای `./mvnw clean verify` برای DMS | NOT RUN | همان محدودیت |
| اجرای `./mvnw clean verify` برای WORKFLOW | NOT RUN | همان محدودیت |
| اجرای H2/Flyway و تست‌های MockMvc | NOT RUN | اجرا وابسته به Build کامل و Java 25 است |
| ممیزی CVE با پایگاه آسیب‌پذیری آنلاین | NOT RUN | دسترسی اینترنت/ابزار اسکن وابستگی فراهم نیست |

**نکته:** PASS بودن تحلیل ساختار تضمین نمی‌کند که برنامه بدون خطا Build و Run شود. پیش از انتشار باید هر سه Build و تست‌های واقعی اجرا شوند.

## ریسک‌ها و کارهای باقی‌مانده

۱. **بالا / قبل از Production:** حساب Basic فعلی DMS دسترسی سراسری به فایل‌ها دارد؛ برای چند Subsystem و کاربران واقعی، احراز هویت سرویس‌به‌سرویس و مجوز مالکیت فایل لازم است. تا آن زمان API فقط روی شبکه مورداعتماد و با HTTPS در دسترس باشد.

۲. **بالا / قبل از Production:** فایل‌های ویدئویی مدارک هویتی حساس‌اند؛ سیاست نگهداری، رمزگذاری ذخیره‌سازی، بکاپ و پاکسازی امن، آنتی‌ویروس/اسکن محتوا و سازوکار Audit مستقل لازم است. فرمت فایل در نسخه فعلی فقط با magic bytes و extension تشخیص داده می‌شود، نه اعتبارسنجی کامل container یا پاکسازی بدافزار.

۳. **متوسط:** فایل‌سیستم و PostgreSQL تراکنش توزیع‌شده ندارند؛ مکانیزم کنونی وضعیت `PURGE_PENDING` و جبران آپلود دارد، اما Reconciliation/cleanup خودکار هنوز وجود ندارد. آزمون crash/قطع دیسک و تست هم‌زمانی باید اضافه شود.

۴. **متوسط:** بررسی symlink، خطر symlinkهای ازپیش‌موجود را کاهش می‌دهد، اما در فایل‌سیستم مشترک با بازیگر محلی متخاصم هنوز شرایط TOCTOU ممکن است؛ DMS باید تحت کاربر OS اختصاصی و مسیر ذخیره‌سازی غیرقابل‌دستکاری توسط سایر کاربران اجرا شود.

۵. **متوسط:** محدودسازی تلاش‌های Login/ثبت‌نام (rate limiting)، قفل/اخطار رفتار مشکوک، بررسی هدرهای reverse proxy و تنظیمات تولیدی TLS در فاز عملیاتی بعد بررسی شوند.

۶. **متوسط:** فرایند کامل ثبت‌نام→ویدئو→تأیید ادمین، ایمیل و Workflow Engine طبق محدوده فاز اول هنوز اجرا نمی‌شود.

۷. **کیفیت:** Postgres واقعی برای Migration و قیود، و ابزار CVE audit آنلاین باید در CI اضافه شوند. تست‌های جدید نوشته شده‌اند اما در این محیط اجرا نشده‌اند.


## اصلاح پس از اجرای کاربر — ۱۴۰۵/۰۸/۱۴ (۲۰۲۶-۱۱-۰۵)

- گزارش خطا: `AuthFlowH2IntegrationTest.documentedRestEndpointsAreLiveAndCsrfProtected` با انتظار 403 و پاسخ واقعی 401 در خط 104 نسخه قبلی.
- علت: ناشناس بودن درخواست POST به endpoint مجاز فقط برای GET، با توکن CSRF معتبر. در REST chain، `HttpStatusEntryPoint` برای anonymous پاسخ 401 تولید می‌کند.
- اصلاح: تست مستقل برای حالت ناشناس/احرازشده/بدون CSRF اضافه شد. تنظیمات Spring Security تنها توضیح داده شدند؛ سیاست‌های امنیتی تغییر نکرده‌اند.
- مستندات کامل: `docs/AUTH_401_403_FIX_FA.md`.
- اعتبارسنجی محلی: **54/54 کنترل ساختاری پاس**؛ **58 فایل Java بدون خطای نحوی**؛ Maven/JDK25 Integration Test **اجرا نشده**.
