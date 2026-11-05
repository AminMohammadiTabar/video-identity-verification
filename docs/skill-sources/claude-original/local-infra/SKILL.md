---
name: local-infra
description: Start local development infrastructure (Redis, SonarQube) for mellat-provider-app via Docker Compose.
---

Docker Compose files live under `src/main/docker/`:

```bash
docker compose -f src/main/docker/redis.yml up            # Redis cache
docker compose -f src/main/docker/sonar.yml up -d          # local SonarQube
```
