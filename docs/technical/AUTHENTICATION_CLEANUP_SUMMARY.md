# Authentication Service Separation - Cleanup Summary

## Date: 2025-12-24
## Author: Hassan Rawashdeh

---

## Overview

Successfully separated authentication concerns from the Loader Service into a dedicated Auth Service, following microservices best practices. This document summarizes all changes made to both services.

---

## Backup Created

**File:** `/Volumes/Files/Projects/newLoader/services/loader-service-backup-20251224-175917.tar.gz`
**Size:** 59MB
**Status:** ✅ Complete

---

## Changes to Loader Service

### Files Removed

1. **AuthController.java** - `api/auth/AuthController.java`
   - **Reason:** Login endpoint moved to Auth Service
   - **Functionality:** User authentication and JWT token generation
   - **Status:** ✅ Removed

2. **LoginRequest.java** - `dto/auth/LoginRequest.java`
   - **Reason:** DTO no longer needed
   - **Status:** ✅ Removed

3. **LoginResponse.java** - `dto/auth/LoginResponse.java`
   - **Reason:** DTO no longer needed
   - **Status:** ✅ Removed

4. **Directories:**
   - `api/auth/` - ✅ Removed
   - `dto/auth/` - ✅ Removed

### Files Modified

#### 1. JwtTokenProvider.java (`infra/security/JwtTokenProvider.java`)

**Changes:**
- ❌ Removed `generateToken()` method (now handled by Auth Service)
- ❌ Removed `expirationMs` field (not needed for validation)
- ✅ Kept `validateToken()` - validates tokens from Auth Service
- ✅ Kept `getUsernameFromToken()` - extracts username from token
- ✅ Kept `getRolesFromToken()` - extracts roles from token

**Before:**
```java
public String generateToken(Authentication authentication) {
    // Token generation logic
}
```

**After:**
```java
// Token generation removed - handled by Auth Service
// Only validation methods remain
```

**Purpose:** This service now only validates JWT tokens issued by the Auth Service.

---

#### 2. SecurityConfig.java (`infra/security/SecurityConfig.java`)

**Changes:**
- ❌ Removed `userDetailsService()` bean - user management now in Auth Service
- ❌ Removed `passwordEncoder()` bean - not needed for token validation
- ❌ Removed `authenticationManager()` bean - only needed for login
- ❌ Removed `/api/v1/auth/**` public endpoint (no longer exists)
- ✅ Kept `securityFilterChain()` - still needed for authorization
- ✅ Kept `JwtAuthenticationFilter` - validates tokens on each request

**Before:**
```java
@Bean
public UserDetailsService userDetailsService() {
    // In-memory users: admin, operator, viewer
}

.requestMatchers("/api/v1/auth/**").permitAll()
```

**After:**
```java
// User management removed
// Only /actuator/health is public
.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
```

**Purpose:** Service now focuses purely on JWT token validation and authorization.

---

### Files Kept (Security)

1. **JwtAuthenticationFilter.java** - ✅ Unchanged
   - **Reason:** Needed to intercept requests and validate JWT tokens
   - **Functionality:** Extracts JWT from Authorization header and validates

2. **SecurityAdminController.java** - ✅ Unchanged
   - **Reason:** Database security auditing, not user authentication
   - **Functionality:** Read-only compliance verification

---

## Changes to Auth Service

### Files Modified

#### 1. SecurityConfig.java (`auth-service/security/SecurityConfig.java`)

**Enhanced CORS Configuration:**

**Before:**
```java
configuration.setAllowedOrigins(List.of(
    "http://localhost:3000",    // React
    "http://localhost:5173"     // Vite
));
configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
```

**After:**
```java
configuration.setAllowedOrigins(Arrays.asList(
    "http://localhost:3000",    // React default
    "http://localhost:5173",    // Vite default
    "http://localhost:4200",    // Angular default
    "http://localhost:8081"     // Custom frontend port
));
configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
configuration.setMaxAge(3600L); // Cache preflight for 1 hour
```

**Public Endpoints:**
```java
.requestMatchers("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/validate").permitAll()
```

---

#### 2. AuthController.java (`auth-service/controller/AuthController.java`)

**New Endpoint for Frontend Routing:**

```java
@PostMapping("/validate")
public ResponseEntity<TokenValidationResponse> validateToken(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        return ResponseEntity.ok(TokenValidationResponse.invalid("Missing or invalid Authorization header"));
    }

    String token = authHeader.substring(7);

    if (!tokenProvider.validateToken(token)) {
        return ResponseEntity.ok(TokenValidationResponse.invalid("Invalid or expired token"));
    }

    String username = tokenProvider.getUsernameFromToken(token);
    String roles = tokenProvider.getRolesFromToken(token);

    return ResponseEntity.ok(TokenValidationResponse.valid(username, roles));
}
```

**Response Model:**
```java
public static class TokenValidationResponse {
    private boolean valid;
    private String username;
    private String roles;
    private String message;
}
```

**Purpose:** Enables frontend to validate tokens before routing to protected pages.

**Enhanced CORS:**
```java
@CrossOrigin(origins = {
    "http://localhost:3000",
    "http://localhost:5173",
    "http://localhost:4200",
    "http://localhost:8081"
})
```

---

## Architecture Changes

### Before (Monolithic)

```
┌─────────────────────┐
│   Loader Service    │
├─────────────────────┤
│ • Auth Logic        │
│ • JWT Generation    │
│ • User Management   │
│ • Loader Logic      │
│ • Signal Processing │
└─────────────────────┘
     Port: 8080
```

### After (Microservices)

```
┌──────────────────┐        ┌──────────────────┐
│  Auth Service    │        │  Loader Service  │
├──────────────────┤        ├──────────────────┤
│ • User Login     │        │ • Loader Logic   │
│ • JWT Generation │━━━━━━━▶│ • JWT Validation │
│ • User Mgmt      │  JWT   │ • Authorization  │
│ • Token Validate │        │ • Signal Process │
│ • Login Audit    │        │ • Data Queries   │
└──────────────────┘        └──────────────────┘
    Port: 30081                 Port: 8080
```

---

## API Changes

### Loader Service

#### Removed Endpoints
- ❌ `POST /api/v1/auth/login` - Moved to Auth Service

#### Public Endpoints
- ✅ `GET /actuator/health` - Health check

#### Protected Endpoints (Require JWT from Auth Service)
- ✅ `GET /api/v1/res/loaders/loaders` - List all loaders
- ✅ `GET /api/v1/res/signals/signal/{code}` - Query signals
- ✅ `POST /api/v1/res/loaders` - Create loader (ADMIN only)
- ✅ `PUT /api/v1/res/loaders/{code}` - Update loader (ADMIN only)
- ✅ `DELETE /api/v1/res/loaders/{code}` - Delete loader (ADMIN only)
- ✅ `GET /api/v1/admin/security/read-only-check` - Security audit

---

### Auth Service

#### Public Endpoints
- ✅ `POST /api/v1/auth/login` - User authentication
- ✅ `POST /api/v1/auth/validate` - Token validation (NEW)
- ✅ `GET /actuator/health` - Health check

---

## Authentication Flow

### 1. User Login (Frontend → Auth Service)

```bash
curl -X POST http://localhost:30081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "HaAdmin123"
  }'
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "username": "admin",
  "roles": ["ROLE_ADMIN"]
}
```

---

### 2. Token Validation (Frontend → Auth Service)

**Use Case:** Before routing to protected pages, validate token

```bash
curl -X POST http://localhost:30081/api/v1/auth/validate \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

**Response (Valid Token):**
```json
{
  "valid": true,
  "username": "admin",
  "roles": "ROLE_ADMIN",
  "message": "Token is valid"
}
```

**Response (Invalid Token):**
```json
{
  "valid": false,
  "username": null,
  "roles": null,
  "message": "Invalid or expired token"
}
```

---

### 3. API Access (Frontend → Loader Service)

```bash
curl -X GET http://localhost:8080/api/v1/res/loaders/loaders \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

**Flow:**
1. Frontend sends request with JWT token
2. Loader Service's `JwtAuthenticationFilter` intercepts request
3. `JwtTokenProvider` validates token signature and expiration
4. Extracts username and roles from token
5. Sets Spring Security authentication context
6. `SecurityConfig` checks role-based permissions
7. Request proceeds to controller if authorized

---

## Frontend Integration Guide

### Login Flow

```typescript
// 1. Login via Auth Service
const response = await fetch('http://localhost:30081/api/v1/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username, password })
});

const { token, username, roles } = await response.json();

// 2. Store token in localStorage or sessionStorage
localStorage.setItem('jwt_token', token);
localStorage.setItem('username', username);
localStorage.setItem('roles', JSON.stringify(roles));
```

---

### Token Validation for Routing

```typescript
// Before navigating to protected route
async function validateToken() {
  const token = localStorage.getItem('jwt_token');

  if (!token) {
    return false;
  }

  const response = await fetch('http://localhost:30081/api/v1/auth/validate', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });

  const { valid } = await response.json();
  return valid;
}

// React Router example
<Route
  path="/loaders"
  element={
    <ProtectedRoute>
      <LoadersPage />
    </ProtectedRoute>
  }
/>

function ProtectedRoute({ children }) {
  const [isValid, setIsValid] = useState(null);

  useEffect(() => {
    validateToken().then(setIsValid);
  }, []);

  if (isValid === null) return <Loading />;
  if (!isValid) return <Navigate to="/login" />;

  return children;
}
```

---

### API Calls to Loader Service

```typescript
// Use token for API calls
const loadersResponse = await fetch('http://localhost:8080/api/v1/res/loaders/loaders', {
  headers: { 'Authorization': `Bearer ${localStorage.getItem('jwt_token')}` }
});

const loaders = await loadersResponse.json();
```

---

## Role-Based Access Control

### Roles Defined in Auth Service

| Role | Permissions | Use Case |
|------|-------------|----------|
| `ROLE_ADMIN` | Full access (CRUD, admin operations) | System administrators |
| `ROLE_OPERATOR` | Read + operational endpoints (pause/resume, reload) | DevOps engineers |
| `ROLE_VIEWER` | Read-only access | Monitoring dashboards |

### Default Users (Created by ETL Initializer)

| Username | Password | Role | Email |
|----------|----------|------|-------|
| admin | HaAdmin123 | ROLE_ADMIN | admin@me.sa |
| operator | HaOperator123 | ROLE_OPERATOR | operator@me.sa |
| viewer | HaViewer123 | ROLE_VIEWER | viewer@me.sa |

---

## Security Considerations

### JWT Token Compatibility

✅ **100% Compatible** between Auth Service and Loader Service:
- Same signing algorithm: HMAC-SHA256
- Same secret key: `${JWT_SECRET}` environment variable
- Same token structure: `{ subject: username, roles: "ROLE_ADMIN,ROLE_OPERATOR" }`
- Same expiration: 24 hours (86400000ms)

### CORS Configuration

**Auth Service allows:**
- Origins: `localhost:3000`, `localhost:5173`, `localhost:4200`, `localhost:8081`
- Methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `PATCH`
- Credentials: `true` (cookies allowed)
- Preflight cache: 1 hour

### Token Security

- **Stateless:** No server-side session storage
- **Expiration:** Tokens expire after 24 hours
- **Tamper-proof:** HMAC-SHA256 signature prevents modification
- **Role-based:** Roles embedded in token, validated on each request

---

## Testing

### Test Login

```bash
# Admin login
curl -X POST http://localhost:30081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"HaAdmin123"}'

# Save token to variable
TOKEN=$(curl -s -X POST http://localhost:30081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"HaAdmin123"}' \
  | jq -r '.token')
```

### Test Token Validation

```bash
# Validate token
curl -X POST http://localhost:30081/api/v1/auth/validate \
  -H "Authorization: Bearer $TOKEN"
```

### Test Loader Service API

```bash
# Get all loaders (requires valid token)
curl -X GET http://localhost:8080/api/v1/res/loaders/loaders \
  -H "Authorization: Bearer $TOKEN"

# Try without token (should fail with 401)
curl -X GET http://localhost:8080/api/v1/res/loaders/loaders
```

### Test Authorization

```bash
# Login as viewer
VIEWER_TOKEN=$(curl -s -X POST http://localhost:30081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"viewer","password":"HaViewer123"}' \
  | jq -r '.token')

# Try to create loader (should fail with 403 Forbidden)
curl -X POST http://localhost:8080/api/v1/res/loaders \
  -H "Authorization: Bearer $VIEWER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{...}'
```

---

## Deployment Checklist

### Prerequisites

- [ ] PostgreSQL running (monitoring-infra namespace)
- [ ] Auth schema and users created (via ETL Initializer)
- [ ] JWT secret configured in sealed secret
- [ ] Both services built and Docker images created

### Deployment Steps

1. **Deploy Auth Service:**
   ```bash
   kubectl apply -f services/auth-service/k8s/deployment.yaml
   kubectl get pods -n monitoring-app -l app=auth-service
   ```

2. **Deploy Loader Service:**
   ```bash
   kubectl apply -f services/loader/k8s/deployment.yaml
   kubectl get pods -n monitoring-app -l app=loader
   ```

3. **Verify Health:**
   ```bash
   curl http://localhost:30081/actuator/health  # Auth Service
   curl http://localhost:8080/actuator/health   # Loader Service
   ```

4. **Test Authentication:**
   ```bash
   curl -X POST http://localhost:30081/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"HaAdmin123"}'
   ```

---

## Benefits of Separation

### 1. Separation of Concerns ✅
- Auth Service: User management, authentication, audit logging
- Loader Service: Business logic, data processing, signal management

### 2. Independent Scaling ✅
- Scale auth service based on login traffic
- Scale loader service based on data processing load

### 3. Security Isolation ✅
- User credentials only in Auth Service
- Loader Service never sees passwords
- Reduced attack surface

### 4. Easier Maintenance ✅
- Changes to auth logic don't require loader service redeployment
- Simpler codebase in each service
- Clear boundaries and responsibilities

### 5. Reusability ✅
- Auth Service can authenticate users for multiple services
- Centralized user management
- Single source of truth for authentication

---

## Migration Impact

### Breaking Changes ❌

**Frontend Must Update:**
- Login endpoint: `localhost:8080/api/v1/auth/login` → `localhost:30081/api/v1/auth/login`

### Non-Breaking Changes ✅

- JWT tokens remain 100% compatible
- Authorization headers unchanged
- Loader Service API endpoints unchanged
- Role-based access control unchanged

---

## Next Steps

### 1. Update Frontend Application
- [ ] Change login API endpoint to port 30081
- [ ] Implement token validation for routing
- [ ] Update API configuration files

### 2. Deploy to Cluster
- [ ] Run `./app_installer.sh` to deploy both services
- [ ] Verify users created in database
- [ ] Test end-to-end authentication flow

### 3. Production Considerations
- [ ] Generate production JWT secret (32+ bytes)
- [ ] Update CORS allowed origins for production domains
- [ ] Implement refresh token mechanism
- [ ] Add rate limiting on login endpoint
- [ ] Monitor login audit logs

---

## Troubleshooting

### Issue: "401 Unauthorized" on Loader Service

**Cause:** Invalid or missing JWT token

**Solution:**
```bash
# Verify token is valid
curl -X POST http://localhost:30081/api/v1/auth/validate \
  -H "Authorization: Bearer YOUR_TOKEN"

# If invalid, login again
curl -X POST http://localhost:30081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"HaAdmin123"}'
```

---

### Issue: "403 Forbidden" on Loader Service

**Cause:** Valid token but insufficient permissions

**Solution:**
- Check user role in token validation response
- Use admin account for admin operations
- Review role requirements in SecurityConfig.java

---

### Issue: CORS Error from Frontend

**Cause:** Frontend origin not in allowed list

**Solution:**
Update Auth Service `SecurityConfig.java`:
```java
configuration.setAllowedOrigins(Arrays.asList(
    "http://localhost:3000",
    "http://your-frontend-origin"  // Add your origin
));
```

---

## Contact

**Author:** Hassan Rawashdeh
**Email:** hassan@tiqmo.sa
**Date:** 2025-12-24

---

## File Manifest

### Backup
- `services/loader-service-backup-20251224-175917.tar.gz` (59MB)

### Modified Files
1. **Loader Service:**
   - `src/main/java/com/tiqmo/monitoring/loader/infra/security/JwtTokenProvider.java`
   - `src/main/java/com/tiqmo/monitoring/loader/infra/security/SecurityConfig.java`

2. **Auth Service:**
   - `src/main/java/com/tiqmo/monitoring/auth/controller/AuthController.java`
   - `src/main/java/com/tiqmo/monitoring/auth/security/SecurityConfig.java`

### Removed Files
1. `src/main/java/com/tiqmo/monitoring/loader/api/auth/AuthController.java`
2. `src/main/java/com/tiqmo/monitoring/loader/dto/auth/LoginRequest.java`
3. `src/main/java/com/tiqmo/monitoring/loader/dto/auth/LoginResponse.java`

---

**Status:** ✅ Complete
**Version:** 1.0
**Last Updated:** 2025-12-24
