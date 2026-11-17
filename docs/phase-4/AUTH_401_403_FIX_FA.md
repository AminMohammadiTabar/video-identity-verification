# رفع خطای تست AUTH: تفاوت 401 و 403

## خطای گزارش‌شده

`AuthFlowH2IntegrationTest.documentedRestEndpointsAreLiveAndCsrfProtected`، خط 104 در نسخه قبلی: انتظار HTTP `403`، نتیجه واقعی HTTP `401`.

## علت ریشه‌ای

در `AuthSecurityConfiguration.restApiSecurity`، مسیر `POST /api/v1/auth/csrf` مجاز نیست و درخواست بدون ورود کاربر ارسال شده است. `csrf()` توکن معتبر ایجاد می‌کند، پس فیلتر CSRF درخواست را رد نمی‌کند؛ سپس `anyRequest().denyAll()` دسترسی را رد می‌کند. `ExceptionTranslationFilter` به دلیل anonymous بودن درخواست، `HttpStatusEntryPoint(401)` را اجرا می‌کند. اگر کاربر احراز هویت شده باشد، `AccessDeniedHandler` پاسخ `403` می‌دهد.

این یک **اشتباه در انتظار تست** بود و نه یک آسیب‌پذیری یا نقص در `SecurityFilterChain`.

## تغییرات

- انتقال بررسی متد نامجاز CSRF به تست مستقل `unsupportedCsrfEndpointMethodDistinguishesAuthenticationFromAuthorization`.
- تست سه رفتار مستقل:
  - ناشناس + CSRF معتبر + POST نامجاز → `401`.
  - احرازشده (`ROLE_USER`) + CSRF معتبر + POST نامجاز → `403`.
  - ناشناس + CSRF نامعتبر/حذف‌شده + POST → `403` به‌دلیل رد توسط فیلتر CSRF.
- افزودن توضیح درباره این سیاست در `AuthSecurityConfiguration`؛ **هیچ رفتار امنیتی موجودی تغییر نکرده است**.
- تست‌های قبلی ثبت‌نام، ورود، خروج، سطح دسترسی و Swagger حفظ شدند.
- پروژه DMS و پروژه WORKFLOW از نظر کد تغییری نکردند.

## دستور اجرای تست روی Windows PowerShell

از مسیر `auth` با JDK 25:

```powershell
.\mvnw.cmd '-Dtest=AuthFlowH2IntegrationTest' test
.\mvnw.cmd clean verify
```

برای تست کل Workspace از فولدر هر سرویس دستور `clean verify` را مستقل اجرا کنید.

## وضعیت اعتبارسنجی در محیط تهیه بسته

- بررسی 54 مورد ساختاری: اجرا شد؛ نتیجه در زمان تولید بسته ثبت می‌شود.
- Parse نحوی فایل‌های Java: اجرا شد؛ نتیجه در زمان تولید بسته ثبت می‌شود.
- Maven / Spring Boot Integration Tests: **اجرا نشده** (محیط تهیه بسته JDK 21 دارد و Maven / مخزن وابستگی‌ها در دسترس نیست).

**منابع بررسی:** فایل‌های `auth-security-mvc`, `security-audit`, `test-quality` و مستندات Spring Security درباره `ExceptionTranslationFilter`.
