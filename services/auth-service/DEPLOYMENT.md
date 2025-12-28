# Auth Service Deployment Guide

## Overview

The Authentication Service has been successfully created and integrated into the application installer. This service follows microservices design principles by separating authentication concerns from the loader service.

## What Was Created

### 1. Auth Service Structure
```
services/auth-service/
├── pom.xml                                    # Maven dependencies
├── Dockerfile                                  # Container image definition
├── k8s/
│   └── deployment.yaml                         # Kubernetes manifests
├── src/main/java/com/tiqmo/monitoring/auth/
│   ├── AuthServiceApplication.java            # Main Spring Boot application
│   ├── domain/                                # JPA Entities
│   │   ├── User.java                          # User entity
│   │   ├── Role.java                          # Role entity
│   │   └── LoginAttempt.java                  # Login audit entity
│   ├── repository/                            # Spring Data JPA repositories
│   │   ├── UserRepository.java
│   │   ├── RoleRepository.java
│   │   └── LoginAttemptRepository.java
│   ├── dto/                                   # Data Transfer Objects
│   │   ├── LoginRequest.java
│   │   └── LoginResponse.java
│   ├── security/                              # Security configuration
│   │   ├── JwtTokenProvider.java             # JWT token generation/validation
│   │   └── SecurityConfig.java               # Spring Security setup
│   ├── service/                               # Business logic
│   │   ├── AuthService.java                  # Authentication service
│   │   └── CustomUserDetailsService.java     # User loading from database
│   └── controller/                            # REST endpoints
│       └── AuthController.java               # Login endpoint
└── src/main/resources/
    └── application.yaml                       # Application configuration
```

### 2. Database Changes

**New Migration:** `V6__add_initial_admin_user.sql`
- Creates 3 initial users with BCrypt-encoded passwords:
  - `admin` / `HaAdmin123` (ROLE_ADMIN)
  - `operator` / `HaOperator123` (ROLE_OPERATOR)
  - `viewer` / `HaViewer123` (ROLE_VIEWER)

### 3. Installer Integration

**Modified:** `app_installer.sh`
- Backup created: `app_installer.sh.backup-YYYYMMDD-HHMMSS`
- Auth service deployment added between ETL Initializer and Data Generator
- Follows the same pattern as other services:
  - Maven build
  - Docker image build
  - Kubernetes deployment
  - Health monitoring
  - Log scanning
  - Actuator endpoint testing

## Deployment Flow

1. **ETL Initializer** runs and executes V6 migration
2. **Auth Service** deployed with access to `auth` schema
3. **Data Generator** deployed
4. **Signal Loader** deployed

## Service Details

### Endpoints

| Endpoint | Method | Description | Auth Required |
|----------|--------|-------------|---------------|
| `/api/v1/auth/login` | POST | User login | No |
| `/actuator/health` | GET | Health check | No |
| `/actuator/info` | GET | Service info | No |

### Environment Configuration

The service uses the following environment variables (configured in `k8s/deployment.yaml`):

```yaml
POSTGRES_HOST: postgres-postgresql.monitoring-infra.svc.cluster.local
POSTGRES_PORT: 5432
POSTGRES_DB: alerts_db
POSTGRES_USER: alerts_user
POSTGRES_PASSWORD: from app-secrets (POSTGRES_APP_PASSWORD)
JWT_SECRET: from app-secrets (JWT_SECRET)
JWT_EXPIRATION_MS: 86400000 (24 hours)
```

### Kubernetes Resources

- **Namespace:** `monitoring-app`
- **Deployment:** `auth-service` (1 replica)
- **Service (ClusterIP):** `auth-service:8081`
- **Service (NodePort):** `auth-service-nodeport:30081`

### Resource Limits

```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

## Testing the Service

### 1. Check Service Status

```bash
kubectl get pods -n monitoring-app -l app=auth-service
kubectl get svc -n monitoring-app auth-service
```

### 2. Test Login (NodePort)

```bash
curl -X POST http://localhost:30081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "HaAdmin123"
  }'
```

Expected response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "username": "admin",
  "roles": ["ROLE_ADMIN"]
}
```

### 3. Test Login (Within Cluster)

```bash
kubectl run curl-test --rm -it --image=curlimages/curl --restart=Never -- \
  curl -X POST http://auth-service.monitoring-app.svc.cluster.local:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"HaAdmin123"}'
```

### 4. Check Health

```bash
curl http://localhost:30081/actuator/health
```

### 5. View Logs

```bash
kubectl logs -n monitoring-app deployment/auth-service -f
```

## Integration with Frontend

### Update API Base URL

Change the authentication endpoint in your frontend:

**Before (loader service):**
```typescript
const AUTH_URL = 'http://localhost:8080/api/v1/auth/login';
```

**After (auth service):**
```typescript
const AUTH_URL = 'http://localhost:30081/api/v1/auth/login';
```

### Frontend Integration Example

```typescript
// Login function
async function login(username: string, password: string) {
  const response = await fetch('http://localhost:30081/api/v1/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ username, password }),
  });

  if (!response.ok) {
    throw new Error('Login failed');
  }

  const data = await response.json();

  // Store token in localStorage
  localStorage.setItem('token', data.token);
  localStorage.setItem('username', data.username);
  localStorage.setItem('roles', JSON.stringify(data.roles));

  return data;
}

// Use token in subsequent requests
async function fetchLoaders() {
  const token = localStorage.getItem('token');

  const response = await fetch('http://localhost:8080/api/v1/res/loaders/loaders', {
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  return response.json();
}
```

## JWT Token Compatibility

The auth service generates JWT tokens that are **100% compatible** with the loader service:

- Same signing algorithm (HMAC-SHA256)
- Same secret key (from app-secrets)
- Same token structure (username + roles)
- Same expiration time (24 hours)

This means:
1. Frontend can authenticate via auth-service
2. Use the returned JWT token for loader-service API calls
3. No changes needed in loader-service authorization logic

## Troubleshooting

### Service Won't Start

1. Check PostgreSQL connectivity:
   ```bash
   kubectl logs -n monitoring-app deployment/auth-service
   ```

2. Verify secrets exist:
   ```bash
   kubectl get secret app-secrets -n monitoring-app
   ```

3. Verify database schema:
   ```sql
   SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'auth';
   SELECT * FROM auth.users;
   ```

### Login Fails

1. Check user exists:
   ```sql
   SELECT username, enabled, account_non_locked FROM auth.users WHERE username = 'admin';
   ```

2. Check login attempts:
   ```sql
   SELECT * FROM auth.login_attempts ORDER BY attempted_at DESC LIMIT 10;
   ```

3. Verify password hash is correct (V6 migration ran)

### Token Invalid

1. Verify JWT_SECRET matches between auth-service and loader-service
2. Check token expiration
3. Ensure loader-service can validate tokens from auth-service

## Security Notes

### Default Credentials

**⚠️ IMPORTANT:** Change default passwords in production!

The default passwords are:
- admin: `HaAdmin123`
- operator: `HaOperator123`
- viewer: `HaViewer123`

### Password Hashing

Passwords are hashed using BCrypt with cost factor 10.

To generate new password hashes:
```bash
# Using htpasswd
htpasswd -bnBC 10 "" YourNewPassword | tr -d ':\n'

# Using Python
python3 -c "from passlib.hash import bcrypt; print(bcrypt.hash('YourNewPassword'))"
```

### Production Recommendations

1. **Use HTTPS** for all communications
2. **Change default passwords** immediately
3. **Rotate JWT secrets** regularly
4. **Enable rate limiting** for login attempts
5. **Implement account lockout** after failed attempts
6. **Add refresh tokens** for better security
7. **Enable audit logging** for all authentication events

## Next Steps

1. ✅ Auth service created and integrated
2. ✅ Initial users created via migration
3. ✅ Installer updated with auth service deployment
4. 🔄 Update frontend to use auth-service
5. 📋 Test end-to-end authentication flow
6. 📋 Consider removing auth logic from loader-service (future cleanup)

## Deployment Commands

### Full Installation

```bash
# Run the complete app installer (includes auth service)
./app_installer.sh
```

### Verify Deployment

```bash
# Check all services are running
kubectl get pods -n monitoring-app

# Expected output:
# etl-initializer-xxx   1/1     Running
# auth-service-xxx      1/1     Running
# data-generator-xxx    1/1     Running
# signal-loader-xxx     1/1     Running
```

## Contact

For issues or questions:
- Author: Hassan Rawashdeh
- Email: hassan@tiqmo.sa
