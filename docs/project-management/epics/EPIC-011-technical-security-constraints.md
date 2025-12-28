---
id: "EPIC-011"
title: "Technical & Security Constraints Documentation"
status: "backlog"
priority: "high"
created: "2025-12-27"
updated: "2025-12-27"
assignee: "platform-team"
owner: "security-team"
labels: ["security", "documentation", "architecture", "constraints"]
estimated_points: 13
sprint: "sprint-03"
target_release: "v1.3.0"
dependencies: []
linear_id: ""
jira_id: ""
github_project_id: ""
---

# EPIC-011: Technical & Security Constraints Documentation

## Overview

**Brief Description**: Comprehensive documentation of security architecture, technical constraints, and operational patterns that govern the monitoring application platform.

**Business Value**: Security and operations teams need clear documentation of security measures, data protection strategies, and technical constraints to ensure compliance, proper maintenance, and secure operations.

**Success Criteria**:
- ✅ All encryption methods documented
- ✅ API gateway routing patterns documented
- ✅ Database access patterns documented
- ✅ Caching strategy and restrictions documented
- ✅ Redis usage patterns documented
- ✅ Security constraints enforced and documented

---

## Scope

### In Scope
- Encryption implementation documentation
- Database column encryption details
- API gateway routing rules
- Frontend caching restrictions (no-cache policy)
- Redis use cases and patterns
- Read-only database connections
- Connection pooling constraints
- Security headers and CORS policies
- Authentication flow constraints
- Network security rules

### Out of Scope
- Changing existing security implementation
- Implementing new security features
- Penetration testing
- Security audit recommendations

---

## Security Architecture

### 1. Database Encryption

#### Encrypted Columns
The following columns contain sensitive data and are encrypted at rest:

**Authentication Schema (`auth` schema)**:
- `users.password` - User passwords (bcrypt hashed)
- `users.email` - User email addresses (AES-256 encryption)
- `api_keys.key_hash` - API key hashes (SHA-256)

**Monitoring Schema (`monitor` schema)**:
- `loader_config.query_text` - SQL queries (may contain sensitive patterns)
- Potentially: Connection strings (if stored in future)

#### Encryption Methods
- **Password Hashing**: bcrypt with salt rounds = 12
- **Column Encryption**: PostgreSQL pgcrypto extension (AES-256)
- **Encryption at Rest**: Database volume encryption (Kubernetes persistent volume)
- **Encryption in Transit**: TLS 1.3 for all connections

#### Encryption Implementation
```sql
-- Example: Creating encrypted column
ALTER TABLE auth.users
ADD COLUMN email_encrypted BYTEA;

-- Encrypting data
UPDATE auth.users
SET email_encrypted = pgp_sym_encrypt(email, current_setting('app.encryption_key'));

-- Decrypting data
SELECT pgp_sym_decrypt(email_encrypted, current_setting('app.encryption_key')) AS email
FROM auth.users;
```

**Key Management**:
- Encryption keys stored in Kubernetes secrets
- Sealed secrets used for GitOps (prevents plain text in repo)
- Keys rotated quarterly (manual process documented)

---

### 2. API Gateway Architecture

#### All Requests Through Gateway
**Constraint**: ALL frontend-to-backend API calls MUST go through the API Gateway.

**Gateway Service**: `gateway-service` (Spring Cloud Gateway)
- Namespace: `monitoring-app`
- Port: 8080
- Service URL: `http://gateway-service.monitoring-app.svc.cluster.local:8080`

#### Routing Rules
```yaml
# Gateway routes configuration
spring:
  cloud:
    gateway:
      routes:
        # Auth Service
        - id: auth-service
          uri: http://auth-service:8081
          predicates:
            - Path=/api/v1/auth/**
          filters:
            - StripPrefix=0

        # Loader Service
        - id: loader-service
          uri: http://signal-loader:8080
          predicates:
            - Path=/api/v1/res/**
          filters:
            - StripPrefix=0

        # Data Generator
        - id: data-generator
          uri: http://data-generator:8083
          predicates:
            - Path=/api/v1/generator/**
          filters:
            - StripPrefix=0
```

#### Security Filters
- JWT token validation
- CORS headers
- Rate limiting (future)
- Request logging
- Response compression

**Direct Service Access**: FORBIDDEN
- Frontend MUST NOT call services directly
- Services exposed only within cluster network
- No LoadBalancer or NodePort for backend services

---

### 3. Frontend Caching Restrictions

#### No Webpage Cache Policy
**Constraint**: Frontend assets MUST NOT be cached by browsers to prevent stale builds.

**Implementation**:
```nginx
# nginx.conf - Frontend configuration
location / {
    root /usr/share/nginx/html;
    try_files $uri $uri/ /index.html;

    # NO CACHE HEADERS (Critical Constraint)
    add_header Cache-Control "no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0";
    add_header Pragma "no-cache";
    add_header Expires "0";

    # Security headers
    add_header X-Content-Type-Options "nosniff";
    add_header X-Frame-Options "DENY";
    add_header X-XSS-Protection "1; mode=block";
}
```

**Rationale**:
- Ensures users always get latest version
- Prevents authentication issues from cached tokens
- Avoids UI/API version mismatches
- Critical for rapid iteration during development

**Version Notification System**:
- Version check every 30 seconds
- Displays notification when new build detected
- Version info in `/version.json`

---

### 4. Redis Usage Patterns

#### Redis Deployment
- **Service**: Redis (deployed in `monitoring-infra` namespace)
- **Purpose**: Session storage, caching, rate limiting
- **Access**: Internal cluster only (no external access)

#### Use Cases

**1. Session Storage**
```java
// Spring Session with Redis
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 3600)
public class SessionConfig {
    // Sessions expire after 1 hour
}
```

**2. API Response Caching** (Future)
```java
// Cacheable loader configurations
@Cacheable(value = "loaders", key = "#loaderCode")
public Loader getLoader(String loaderCode) {
    return loaderRepository.findByLoaderCode(loaderCode);
}
```

**3. Rate Limiting** (Planned)
- Track API request counts per user
- Prevent abuse
- DDoS mitigation

#### Redis Constraints
- **No persistent data**: Redis used for ephemeral data only
- **TTL required**: All keys must have expiration
- **Cluster mode**: NOT used (single instance for POC)
- **Backup**: Not required (session data recreated)

---

### 5. Database Connection Patterns

#### Read-Only Connections
**Constraint**: Application services MUST use read-only database users for query operations.

**User Types**:
1. **Admin User** (`postgres`)
   - Full access (schema changes, migrations)
   - Used by: ETL Initializer only
   - NOT used by application services

2. **Read-Write User** (`loader_app`)
   - Read + Write access to `monitor` schema
   - Used by: signal-loader (inserts execution results)
   - Used by: data-generator (creates test data)

3. **Read-Only User** (`loader_readonly`)
   - SELECT only on `monitor` schema
   - Used by: Future reporting services
   - Used by: Analytics dashboards

**Implementation**:
```sql
-- Create read-only user
CREATE USER loader_readonly WITH PASSWORD 'secure_password';

-- Grant read-only access
GRANT CONNECT ON DATABASE postgres TO loader_readonly;
GRANT USAGE ON SCHEMA monitor TO loader_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA monitor TO loader_readonly;

-- Revoke write permissions
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA monitor FROM loader_readonly;
```

#### Connection Pooling
**Constraint**: All services MUST use HikariCP connection pooling.

```yaml
# Application connection pool configuration
spring:
  datasource:
    hikari:
      maximum-pool-size: 10      # Max connections per service
      minimum-idle: 2             # Idle connections
      connection-timeout: 30000   # 30 seconds
      idle-timeout: 600000        # 10 minutes
      max-lifetime: 1800000       # 30 minutes
```

**Total Connection Limits**:
- PostgreSQL max_connections: 100
- Reserved for admin: 5
- Available for apps: 95
- Per service: 10-15 connections max

---

## Network Security

### Namespace Isolation
- **monitoring-app**: Application services
- **monitoring-infra**: Infrastructure (PostgreSQL, Redis)
- **kube-system**: Kubernetes system components

### Network Policies
- Inter-namespace communication restricted
- Only gateway exposed via Ingress
- All backend services: ClusterIP only
- No direct internet access from pods

### TLS/SSL
- **Internal**: TLS disabled (cluster network trusted)
- **External**: TLS 1.3 via Ingress (future)
- **Database**: SSL connections (future enhancement)

---

## Authentication Flow Constraints

### JWT Token Lifecycle
1. User logs in → Auth service returns JWT
2. Frontend stores token in `localStorage`
3. All API requests include `Authorization: Bearer <token>`
4. Gateway validates JWT on every request
5. Invalid/expired token → 401 response → redirect to login

### Token Constraints
- **Expiration**: 24 hours
- **Refresh**: Not implemented (must re-login)
- **Storage**: localStorage only (no cookies)
- **Transmission**: HTTPS only (future)

### Password Policy
- Minimum 8 characters
- No complexity requirements (POC limitation)
- Bcrypt hashing with 12 salt rounds
- No password reset (future feature)

---

## Operational Constraints

### Deployment Constraints
1. **No Manual Kubectl Edits**: Use declarative YAML files
2. **Version Tagging**: All images tagged with timestamp
3. **Rollback Strategy**: Keep last 3 image versions
4. **Zero Downtime**: Use rolling updates (maxUnavailable: 0)

### Monitoring Constraints
1. **Logs**: Stdout only (no file logging)
2. **Metrics**: Future (Prometheus integration planned)
3. **Alerting**: Not implemented

### Backup Constraints
1. **Database**: Daily backups (manual process)
2. **Configuration**: GitOps (all config in repo)
3. **Secrets**: Sealed secrets (encrypted in repo)

---

## Compliance & Audit

### Data Retention
- Execution history: 90 days
- User sessions: 24 hours (Redis TTL)
- Application logs: 7 days (Kubernetes default)

### Audit Logging
- Authentication events logged
- API access logged in gateway
- Database audit trail (future)

---

## Future Enhancements

1. **Encryption**:
   - Add column-level encryption for queries
   - Implement key rotation automation

2. **Gateway**:
   - Add rate limiting
   - Implement request throttling
   - Add WAF (Web Application Firewall)

3. **Database**:
   - Enable SSL connections
   - Implement read replicas
   - Add query audit logging

4. **Redis**:
   - Implement Redis Cluster
   - Add Redis persistence
   - Implement cache warming

---

## Related Documentation

- [Authentication Service Documentation](../../services/auth-service/README.md)
- [Gateway Service Configuration](../../services/gateway/README.md)
- [Database Schema Documentation](../../database/schema-documentation.md)
- [Encryption Key Management](../../security/key-management.md)

---

**Created By**: platform-team
**Status**: 📋 BACKLOG (Documentation in progress)
