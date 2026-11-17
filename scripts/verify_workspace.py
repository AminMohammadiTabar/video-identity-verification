#!/usr/bin/env python3
"""Dependency-free (stdlib) static gate for the three independent projects.

Run from any directory: python scripts/verify_workspace.py
This is NOT a substitute for Maven builds or integration tests.
"""
from __future__ import annotations

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
SERVICES = {'auth': '8080', 'dms': '8081', 'workflow': '8082'}
MAVEN_NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
failures: list[str] = []
count = 0


def check(condition: bool, message: str) -> None:
    global count
    count += 1
    if not condition:
        failures.append(message)
        print('FAIL', message)
    else:
        print('PASS', message)


check(not (ROOT / 'pom.xml').exists(), 'No aggregator parent POM at workspace root')
check((ROOT / 'AGENTS.md').is_file() and (ROOT / 'CLAUDE.md').is_file(),
      'Both coding-agent guide files exist')

for service, port in SERVICES.items():
    folder = ROOT / service
    pom_path = folder / 'pom.xml'
    check(pom_path.is_file(), f'{service}: standalone POM exists')
    if not pom_path.exists():
        continue
    pom = ET.parse(pom_path).getroot()
    artifact = pom.findtext('m:artifactId', namespaces=MAVEN_NS)
    parent = pom.find('m:parent', MAVEN_NS)
    check(artifact == service and parent is not None and
          parent.findtext('m:artifactId', namespaces=MAVEN_NS) == 'spring-boot-starter-parent',
          f'{service}: Boot parent and correct artifact ID')
    java_version = pom.findtext('m:properties/m:java.version', namespaces=MAVEN_NS)
    check(java_version == '25', f'{service}: targets Java 25')
    deps = pom.findall('m:dependencies/m:dependency', MAVEN_NS)
    artifacts = {dep.findtext('m:artifactId', namespaces=MAVEN_NS) for dep in deps}
    check(not (artifacts & set(SERVICES)), f'{service}: no sibling dependency')
    check('springdoc-openapi-starter-webmvc-ui' in artifacts, f'{service}: has Swagger/OpenAPI')
    check('spring-boot-starter-webmvc' in artifacts, f'{service}: MVC starter')
    check((folder / 'mvnw.cmd').is_file() and (folder / 'mvnw').is_file(),
          f'{service}: independent Maven wrapper')
    application = folder / 'src/main/resources/application.yml'
    check(application.exists() and bool(re.search(r'port:.*' + port, application.read_text())),
          f'{service}: independent configured port {port}')
    check((folder / 'src/main/resources/application-local.yml').is_file(),
          f'{service}: local development profile')
    check((folder / 'src/main/java').exists(), f'{service}: independent source tree')

for name in ('auth', 'dms'):
    folder = ROOT / name / 'src/main/resources'
    check((folder / 'db/migration' / f'V1__create_{name}_tables.sql').is_file(),
          f'{name}: PostgreSQL baseline migration')
    check((folder / 'db/migration-h2' / f'V1__create_{name}_tables.sql').is_file(),
          f'{name}: H2 baseline migration')
    check((folder / 'application-h2.yml').is_file(), f'{name}: dedicated H2 profile')

# Step 6: independent durable Audit persistence and tracing for all three services.
for service in SERVICES:
    resources = ROOT / service / 'src/main/resources'
    java_base = ROOT / service / 'src/main/java/com/example/videokyc' / service
    pg_audit = list((resources / 'db/migration').glob('*audit_events.sql'))
    h2_audit = list((resources / 'db/migration-h2').glob('*audit_events.sql'))
    check(len(pg_audit) == 1 and len(h2_audit) == 1,
          f'{service}: one independent PostgreSQL and H2 Audit migration')
    check((java_base / 'observability/audit/AuditEventRecorder.java').is_file() and
          (java_base / 'observability/audit/AuditEventRepository.java').is_file(),
          f'{service}: local Audit recorder/repository exists')
    pom = ET.parse(ROOT / service / 'pom.xml').getroot()
    artifacts = {dep.findtext('m:artifactId', namespaces=MAVEN_NS)
                 for dep in pom.findall('m:dependencies/m:dependency', MAVEN_NS)}
    check('spring-boot-starter-opentelemetry' in artifacts and
          'spring-boot-starter-actuator' in artifacts,
          f'{service}: OpenTelemetry and Actuator starters declared')
    config = (resources / 'application.yml').read_text(encoding='utf-8')
    check('type: w3c' in config and 'OTEL_EXPORTER_OTLP_ENABLED' in config and
          'TRACING_SAMPLING_PROBABILITY' in config,
          f'{service}: W3C tracing and configurable OTLP export')
    filter_source = (java_base / 'observability/HttpAuditFilter.java').read_text(encoding='utf-8')
    check('traceId' in filter_source and 'spanId' in filter_source,
          f'{service}: HTTP access log correlates Trace ID and Span ID')

check((ROOT / 'auth/src/main/resources/db/migration/V4__support_verification_attempt_history.sql').is_file() and
      (ROOT / 'auth/src/main/resources/db/migration-h2/V4__support_verification_attempt_history.sql').is_file(),
      'AUTH: attempt-history migration exists for PostgreSQL and H2')
check((ROOT / 'auth/src/main/resources/db/migration/V6__create_email_notification_outbox.sql').is_file() and
      (ROOT / 'auth/src/main/resources/db/migration-h2/V6__create_email_notification_outbox.sql').is_file(),
      'AUTH: transactional email outbox migration exists for PostgreSQL and H2')
check((ROOT / 'auth/src/main/java/com/example/videokyc/auth/service/EmailOutboxService.java').is_file() and
      (ROOT / 'auth/src/main/java/com/example/videokyc/auth/service/EmailNotificationProcessor.java').is_file(),
      'AUTH: email outbox and retry processor exist')
check('EMAIL_NOTIFICATIONS_ENABLED:false' in
      (ROOT / 'auth/src/main/resources/application.yml').read_text(encoding='utf-8'),
      'AUTH: SMTP delivery is opt-in by default')
check((ROOT / 'auth/src/main/java/com/example/videokyc/auth/web/VerificationApiController.java').is_file() and
      '"/history"' in (ROOT / 'auth/src/main/java/com/example/videokyc/auth/web/VerificationApiController.java').read_text(encoding='utf-8'),
      'AUTH: verification attempt history API exists')
check((ROOT / 'dms/src/main/java/com/example/videokyc/dms/storage/LocalDiskStorage.java').is_file() and
      (ROOT / 'dms/src/main/java/com/example/videokyc/dms/storage/FileSignaturePolicy.java').is_file(),
      'DMS: safe local disk storage implementation is present')
check((ROOT / 'workflow/src/main/resources/db/migration/V1__create_audit_events.sql').is_file() and
      (ROOT / 'workflow/src/main/resources/db/migration-h2/V1__create_audit_events.sql').is_file(),
      'WORKFLOW: isolated Audit persistence exists')

# Phase 7 Step 2: Database Schema & Migrations (PostgreSQL and H2 parity)
for v in ('V2__create_workflow_definition_schema.sql',
          'V3__create_workflow_publication_schema.sql',
          'V4__create_workflow_outbox_schema.sql'):
    check((ROOT / 'workflow/src/main/resources/db/migration' / v).is_file() and
          (ROOT / 'workflow/src/main/resources/db/migration-h2' / v).is_file(),
          f'WORKFLOW: {v.split("__")[0]} migration exists for PostgreSQL and H2')

check((ROOT / 'workflow/src/test/java/com/example/videokyc/workflow/WorkflowDatabaseMigrationIntegrationTest.java').is_file(),
      'WORKFLOW: database migration and constraint integration tests present')

# Phase 7 Step 1: Generic Workflow Engine Domain Foundation
check((ROOT / 'docs/phase-7/PHASE_7_ROADMAP.md').is_file() and
      (ROOT / 'docs/phase-7/PHASE_7_STEP_1_DOMAIN_DESIGN.md').is_file() and
      (ROOT / 'docs/phase-7/PHASE_7_STEP_2_DATABASE_DESIGN.md').is_file(),
      'WORKFLOW: Phase 7 roadmap and design specifications present')

wf_domain = ROOT / 'workflow/src/main/java/com/example/videokyc/workflow/domain'
check((wf_domain / 'definition/WorkflowDefinition.java').is_file() and
      (wf_domain / 'version/WorkflowVersion.java').is_file() and
      (wf_domain / 'transition/guard/GuardParser.java').is_file() and
      (wf_domain / 'transition/guard/GuardEvaluator.java').is_file(),
      'WORKFLOW: domain entities and Guard DSL evaluator present')

# Hexagonal architecture purity: domain layer must remain free of JPA annotations
jpa_leaks = [p.name for p in wf_domain.rglob('*.java')
             if 'jakarta.persistence' in p.read_text(encoding='utf-8')]
check(not jpa_leaks, f'WORKFLOW: domain layer has zero JPA annotations ({len(jpa_leaks)} leaks found)')
check((ROOT / 'compose.yml').read_text(encoding='utf-8').find('jaegertracing/all-in-one') >= 0,
      'Optional Jaeger backend is defined for local tracing')

for skill in ('video-kyc-development', 'auth-security-mvc',
              'dms-file-safety', 'quality-gate-video-kyc', 'http-observability'):
    for prefix in ('.claude/skills', '.agents/skills'):
        path = ROOT / prefix / skill / 'SKILL.md'
        check(path.is_file() and path.read_text(encoding='utf-8').startswith('---\n'),
              f'{prefix}/{skill}: agent-discoverable SKILL.md')

claude = ROOT / '.claude/skills'
agents = ROOT / '.agents/skills'
a = {p.parent.name for p in claude.glob('*/SKILL.md')}
b = {p.parent.name for p in agents.glob('*/SKILL.md')}
check(a == b and len(a) == 25, 'Claude Code and Antigravity have the same 25 active skills')
archive = ROOT / 'docs/skill-sources/claude-original'
check(len(list(archive.glob('*/SKILL.md'))) == 24,
      'All 24 supplied skills are preserved in original archive')

# Explicitly protect against a latent import that couples one Java service to another.
for service in SERVICES:
    java_source = ROOT / service / 'src/main/java'
    forbidden = [p.relative_to(ROOT) for p in java_source.rglob('*.java')
                 if re.search(r'import\s+com\.example\.videokyc\.(?:auth|dms|workflow)\.',
                              p.read_text(encoding='utf-8'))
                 and any(re.search(r'import\s+com\.example\.videokyc\.' + other + r'\.',
                                   p.read_text(encoding='utf-8')) for other in SERVICES if other != service)]
    check(not forbidden, f'{service}: no cross-service Java imports ({len(forbidden)} found)')

for service in ('auth', 'dms', 'workflow'):
    common = (ROOT / service / 'src/main/resources/application.yml').read_text(encoding='utf-8')
    check('SWAGGER_ENABLED:false' in common, f'{service}: Swagger disabled by default')

# Operational logging and anonymous Swagger readiness
for service in SERVICES:
    base = ROOT / service / 'src/main/java/com/example/videokyc' / service
    check((base / 'observability/HttpAuditFilter.java').is_file(),
          f'{service}: request tracing filter exists')
    config = (ROOT / service / 'src/main/resources/application.yml').read_text()
    check('console: logstash' in config and 'file: logstash' in config and 'APP_LOG_FILE' in config,
          f'{service}: structured console+file logs configured')
check('.permitAll()' in (ROOT / 'auth/src/main/java/com/example/videokyc/auth/config/AuthSecurityConfiguration.java').read_text(),
      'AUTH: public Swagger matchers present (inspect their exact routes in code review)')

# Step 5: AUTH <-> DMS integration remains HTTP-only; verification is still enum-backed.
check((ROOT / 'auth/src/main/resources/db/migration/V3__verification_requests.sql').is_file() and
      (ROOT / 'auth/src/main/resources/db/migration-h2/V3__verification_requests.sql').is_file(),
      'AUTH: V3 verification migrations exist for both DB profiles')
check((ROOT / 'auth/src/main/java/com/example/videokyc/auth/service/HttpDmsGateway.java').is_file(),
      'AUTH: independent HTTP DMS gateway is present')
check((ROOT / 'dms/src/main/java/com/example/videokyc/dms/config/DmsAccessPolicy.java').is_file(),
      'DMS: scoped AUTH_SERVICE access policy is present')
check((ROOT / 'auth/src/main/java/com/example/videokyc/auth/web/ReviewApiController.java').is_file(),
      'AUTH: admin review API is present')
check((ROOT / 'auth/src/main/resources/static/js/video-recording.js').is_file() and
      'X-CSRF-TOKEN' in (ROOT / 'auth/src/main/resources/static/js/video-recording.js').read_text(),
      'AUTH: video submission uses explicit CSRF header')
check('DMS_AUTH_SERVICE_PASSWORD' in (ROOT / 'dms/src/main/resources/application.yml').read_text() and
      'AUTH_DMS_PASSWORD' in (ROOT / 'auth/src/main/resources/application.yml').read_text(),
      'Inter-service credentials are environment-configurable')

print(f'\nStatic checks: {count - len(failures)}/{count} passed')
print('Reminder: static checks do not compile Java or exercise Spring Boot/Flyway.')
sys.exit(1 if failures else 0)
