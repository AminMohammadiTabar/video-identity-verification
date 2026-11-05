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

print(f'\nStatic checks: {count - len(failures)}/{count} passed')
print('Reminder: static checks do not compile Java or exercise Spring Boot/Flyway.')
sys.exit(1 if failures else 0)
