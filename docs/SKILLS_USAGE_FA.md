# راهنمای استفاده از اسکیل‌ها در Claude Code و Google Antigravity

این نسخه از آرشیو اصلی `claude.rar` استفاده می‌کند؛ ۲۴ اسکیل ارسالی بدون دستکاری در `docs/skill-sources/claude-original/` نگهداری شده‌اند. به دلیل تفاوت معماری پروژه مبدا و پروژه Video KYC، **۲۰ اسکیل عمومی** در هر دو ابزار فعال شده‌اند و **۴ اسکیل ویژه پروژه Mellat** فقط آرشیو شده‌اند. چهار اسکیل جدید مخصوص این Workspace نیز ساخته شده‌اند.

## محل دقیق اسکیل‌ها

| ابزار | مسیر | نحوه بارگذاری |
|---|---|---|
| Claude Code | `.claude/skills/<name>/SKILL.md` | شناسایی و بارگذاری خودکار اسکیل‌های مرتبط؛ فراخوانی `/name` |
| Google Antigravity | `.agents/skills/<name>/SKILL.md` | شناسایی از ریشه Workspace؛ درخواست اجرای اسکیل مرتبط |

هر دو مسیر شامل **۲۴ اسکیل فعالِ یکسان** هستند: ۲۰ اسکیل عمومی ارسالی + ۴ اسکیل مخصوص پروژه. فایل‌های هر اسکیل شامل `SKILL.md` و منابع جانبی آن هستند؛ صرفاً کپی عنوان‌ها نیستند.

برای اینکه اسکیل‌ها در هر سه پروژه در دسترس باشند، پوشه **`video-identity-verification` را به‌عنوان Workspace Root** باز کنید، نه فقط `auth` یا `dms` را. `CLAUDE.md` راهنمای Claude Code و `AGENTS.md` قواعد عمومی را تعیین می‌کنند.

## ۲۰ اسکیل عمومی فعال از آرشیو شما

- `architecture-review`, `spring-boot-patterns`, `java-code-review`, `clean-code`, `solid-principles`, `design-patterns`
- `security-audit`, `concurrency-review`, `test-quality`, `api-contract-review`, `logging-patterns`, `maven-dependency-audit`
- `microservices-architect`, `adversarial-reviewer`, `performance-smell-detection`, `java-migration`, `jspecify`
- `changelog-generator`, `issue-triage`, `git-commit`

**چهار مورد آرشیوشده و غیرفعال:** `add-rest-endpoint`, `java-spring-code-review`, `local-infra`, `run-mellat-provider-app`؛ چون این موارد به مسیرهای JHipster، احراز هویت متفاوت، سرویس‌های Mellat و آدرس‌های sandbox وابسته‌اند. با انتقالشان به پوشه فعال، دستورات ناخواسته و ناامن برای این پروژه ایجاد می‌شود. برای استفاده مجدد از این موارد باید قبلاً بازنویسی شوند.

## چهار اسکیل اختصاصی پروژه

| اسکیل | کاربرد |
|---|---|
| `video-kyc-development` | رعایت مرز سه سرویس، محدوده فاز فعلی، Flyway/H2/Postgres و Swagger |
| `auth-security-mvc` | ثبت‌نام، Session، CSRF، نقش‌ها و فرم‌های Mustache |
| `dms-file-safety` | آپلود امن، مسیر تاریخ/کاربر، حذف منطقی/فیزیکی و بازیابی خطا |
| `quality-gate-video-kyc` | Code Review مستند، تست‌های منفی، وابستگی‌ها و گزارش وضعیت واقعی تست |

### مثال‌های پیشنهادی درخواست به Claude Code یا Antigravity

```text
Use the video-kyc-development, spring-boot-patterns and architecture-review skills to improve the AUTH registration feature. Preserve all three independent Maven projects.
```

```text
Use dms-file-safety, security-audit, concurrency-review and test-quality. Review the upload/purge flow, write negative tests, and report only verified findings.
```

```text
Run quality-gate-video-kyc. Execute Python static checks and the Maven tests if prerequisites are available. Report NOT RUN for anything not executed.
```

## تقدم دستورالعمل‌ها

۱. محدوده فاز فعلی پروژه و تصمیم‌های `AGENTS.md` مقدم هستند. ۲. اسکیل اختصاصی Video KYC بر مثال‌های عمومی در اسکیل‌های واردشده مقدم است. ۳. هر ایراد باید با شاهد واقعی در سورس یا خروجی تست پشتیبانی شود؛ شرط «حتماً یک ایراد پیدا کن» در اسکیل عمومی `adversarial-reviewer` مجوز ساخت ایراد غیرواقعی نیست. ۴. امنیت AUTH را با مثال‌های پروژه مبدا که `/api/**` را `permitAll()` می‌کنند تضعیف نکنید.

## اعتبارسنجی

از ریشه Workspace:

```powershell
python scripts/verify_workspace.py
```

در محیط نصب‌شده با JDK 25 و دسترسی به Maven Central برای هر سرویس:

```powershell
cd auth; .\mvnw.cmd clean verify
cd ..\dms; .\mvnw.cmd clean verify
cd ..\workflow; .\mvnw.cmd clean verify
```

اسکریپت Python فقط ساختار را بررسی می‌کند. دستور `java scripts/JavaSyntaxCheck.java` صرفاً Parse نحوی Java را بدون بررسی نوع‌ها انجام می‌دهد. موفقیت این دو به معنی اجرای تست‌های Spring Boot نیست.


## Observability skill

`http-observability` is now present in both agent skill directories; use it for JSON logging, HTTP request tracing, IP handling and Swagger UI access.
