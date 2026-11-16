> **یادداشت نسخه:** این گزارش تاریخی گام پنجم است و تغییرات گام ششم را پوشش نمی‌دهد. گزارش جاری در `STEP_6_QUALITY_REVIEW_FA.md` قرار دارد.

# گزارش کیفیت گام پنجم — Video Identity Verification

**تاریخ گزارش:** ۷ نوامبر ۲۰۲۶

## Scope

- ارتباط HTTP از AUTH به DMS با حساب محدود `AUTH_SERVICE`.
- ذخیره درخواست احراز هویت در AUTH و وضعیت‌های Enum.
- نمایش ویدئو و تصمیم Approve/Reject در پنل ادمین و REST API.
- ضبط ویدئو از طریق مرورگر و ارسال Multipart با CSRF Header.
- حفظ Swagger، Logging، استقلال سرویس‌ها و اسکیل‌های قبلی.

## اسکیل‌های مبنای بازبینی

`video-kyc-development`، `auth-security-mvc`، `dms-file-safety`، `security-audit`، `concurrency-review`، `api-contract-review`، `http-observability` و `quality-gate-video-kyc`؛ از آن‌ها برای بررسی مرز سرویس‌ها، کنترل دسترسی، جبران خطای فایل، CSRF، قفل دیتابیس و لاگ استفاده شد. اسکیل‌ها در `.claude/skills` و `.agents/skills` حفظ و دستورالعمل‌های مرحله‌ای مرتبط اصلاح شده‌اند.

## نتیجه بررسی‌های انجام‌شده

| بررسی | نتیجه |
|---|---|
| کنترل ساختار Workspace با `python scripts/verify_workspace.py` | **PASS، ۶۹/۶۹** |
| Parse فایل‌های `application*.yml` با PyYAML | **PASS** |
| بررسی XML سه POM از طریق اسکریپت Workspace | **PASS** |
| نبودن وابستگی Maven بین پروژه‌ها | **PASS (ایستا)** |
| بررسی JDK موجود | Java 21 (نسخه هدف پروژه Java 25) |
| Maven `clean verify` / اجرای تست‌ها | **NOT RUN**؛ JDK 25 و Maven dependencies در محیط ساخت در دسترس نیستند |
| تست E2E واقعی بین دو سرویس AUTH و DMS | **NOT RUN** |
| تست عملکرد و بار | **NOT RUN** |

به‌خاطر اجرا نشدن Maven، هیچ ادعای موفقیت Build یا Integration Test مطرح نمی‌شود.

## تست‌های افزوده‌شده به سورس (اجرای آن‌ها با کاربر)

- `auth/VerificationLifecycleH2IntegrationTest` — ثبت ویدئو، جلوگیری از درخواست تکراری، کنترل دسترسی ادمین، تصمیم تکراری، CSRF و خطای DMS.
- `auth/domain/VerificationRequestEntityTest` — انتقال‌های enum و اعتبارسنجی رد درخواست.
- `dms/DmsAuthServiceScopeH2IntegrationTest` — حساب سرویس و محدودشدن دسترسی به فایل‌های USER زیرسیستم AUTH.
- به‌روزرسانی انتظار UI در `AuthFlowH2IntegrationTest`.

## نکات باقی‌مانده قبل از Production

1. اتصال AUTH و DMS تراکنش توزیع‌شده واحد ندارد. بعد از خطای ناشناخته در Commit، فایل نباید خودکار حذف شود؛ `verification.orphan` به Reconciliation نیاز دارد.
2. AUTH در مسیر Upload و Play از `byte[]` (حداکثر ۵۰ MiB) استفاده می‌کند؛ Stream و HTTP Range برای مقیاس تولید مناسب‌تر است.
3. حساب سرویس DMS برای AUTH باید به‌صورت امن مدیریت شود. HTTPS بین ماشین‌ها، Secret Store و چرخش رمز لازم است.
4. در این گام امکان احراز هویت مجدد برای درخواست REJECTED فراهم نیست.
5. Workflow Engine، RabbitMQ، ارسال ایمیل و Liveness Detection عمداً در Scope این گام نیستند.
6. Audit Log متنی جایگزین تاریخچه دائمی و مستقل تصمیم‌های مدیریتی نیست؛ فعلاً reviewer UUID، زمان تصمیم و دلیل در جدول ذخیره می‌شود.
7. اعتبارسنجی Signature فایل در DMS پایه‌ای است؛ برای محتوای غیرقابل اعتماد در محیط عملیاتی ابزار تشخیص محتوای پیشرفته و اسکن فایل لازم خواهد بود.

## دستورات قابل اجرا توسط کاربر

از پوشه هر سرویس:

```powershell
.\mvnw.cmd clean verify
```

ترتیب راه‌اندازی: DMS، سپس AUTH. رمز `AUTH_DMS_PASSWORD` در AUTH باید با `DMS_AUTH_SERVICE_PASSWORD` در DMS یکسان باشد. پیش از شروع بازبینی، در صورت وجود H2 قدیمی، اجازه دهید Migration جدید V3 روی داده‌های فعلی اعمال شود؛ داده‌های قبلی را بدون Backup پاک نکنید.
