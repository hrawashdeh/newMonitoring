# PROJECT TRACKER

**Enterprise Monitoring, Alerting, and Incident Management Platform**

> Single source of truth for cross-session development tracking and handovers.

**Last Updated:** 2025-12-22 (Updated: POC-first approach for manager demo)
**Project Status:** Phase 0 Complete → **POC Stage 1 Ready to Start**
**Team:** Solo Developer + Claude Code (Two-Man Army)
**POC Timeline:** 2-3 weeks (Stage 1: 2 weeks, Stage 2: 1 week)
**Target Manager Demo:** Week 2 (functional) + Week 3 (with security)

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Project Vision](#project-vision)
3. [Technology Stack](#technology-stack)
4. [Architecture Overview](#architecture-overview)
5. [Current State (Phase 0)](#current-state-phase-0)
6. [Development Phases](#development-phases)
7. [Key Decisions Log](#key-decisions-log)
8. [Cross-References](#cross-references)
9. [Team & Deployment Info](#team--deployment-info)

---

## Executive Summary

### What We're Building

A **definition-driven monitoring and incident management platform** for enterprise/fintech environments that:

- **Unifies observability** across multiple signal sources (APM, logs, APIs, infrastructure)
- **Enables declarative alerting** with complex time-based conditions and historical comparisons
- **Manages incidents as first-class entities** with full lifecycle tracking and RCA artifacts
- **Integrates with external systems** (Jira, notification channels: Email/SMS/IVR)
- **Provides governance** through versioning, audit trails, and RBAC

### Why This Approach

- **Definition-driven**: Charts and alerts are declarative configurations, not hardcoded logic
- **Microservices architecture**: Independent scalability and team ownership
- **Security-first**: JWT authentication, AES-256 encryption, full audit trails
- **MENA-optimized stack**: React + Spring Boot = easiest hiring in Saudi Arabia (when team grows)
- **Solo developer friendly**: Built with AI assistance (Claude Code), practical sprint planning

### Key Differentiators

1. **Alerts extend chart definitions** → No duplicate logic between visualization and alerting
2. **Incidents are first-class** → Not just "acknowledged alerts" but full investigation workflows
3. **Bi-directional Jira sync** → Incidents stay in sync with external ticketing
4. **Multi-channel escalation** → Email → SMS → IVR with policy-driven routing

---

## Project Vision

### Core Objectives

The platform acts as a **single operational control plane** that allows teams to:

#### 1. Define Visualizations Declaratively

**Requirements:**
- Charts created using data definitions and templates (not hardcoded)
- Definitions specify: data sources, dimensions, aggregations, time windows, visualization behavior
- Definitions must be reusable, extendable, and versioned

**Implementation:**
- `dashboards-service`: Stores chart/dashboard definitions (JSON schemas)
- Apache ECharts configurations generated from definitions
- Versioning via PostgreSQL audit columns + migration history

#### 2. Derive Alerting Logic from Chart Definitions

**Requirements:**
- Alerts created by extending existing chart definitions
- Alert rules support:
  - Complex time-based conditions
  - Comparisons to historical baselines (yesterday, last week, last month)
  - Weighted evaluation across multiple time windows
  - Suppression, escalation, severity classification
- Configurable via UI templates, fully auditable

**Implementation:**
- `alerting-service`: Rule evaluation engine
- Rules stored as extensions of chart definitions
- Spring `@Scheduled` + RabbitMQ for distributed evaluation
- Weighted scoring algorithm for multi-window conditions

#### 3. Compose Dashboards and RCA Artifacts

**Requirements:**
- Charts composable into dashboards
- Dashboards reusable in:
  - Operational dashboards
  - Incident RCA reports
  - Investigation artifacts and exports
- Reports support structured evidence and attachments

**Implementation:**
- Dashboard definitions reference chart definitions (composition)
- RCA PDF generator (using iText or similar)
- S3-compatible storage (PVC-mounted MinIO) for attachments

#### 4. Manage Incidents as First-Class Entities

**Requirements:**
- Alerts grouped/escalated into incident records
- Incidents support:
  - Lifecycle management (status transitions, ownership, resolution)
  - Investigation notes and attachments
  - Audit trails for all changes
- Traceability to originating alerts and signals

**Implementation:**
- `incidents-service`: Full CRUD + workflow state machine
- State transitions: New → Investigating → Resolved → Closed
- Ownership assignment + reassignment audit
- Attachments via S3 (PVC storage)

#### 5. Integrate with External Tracking Systems

**Requirements:**
- Incidents creatable/updatable in Jira
- Bi-directional status synchronization
- Reliable, idempotent, auditable integration

**Implementation:**
- `integrations-service`: Jira REST API client
- Webhook listener for Jira → Platform updates
- Idempotency via external reference ID tracking
- Retry with exponential backoff

#### 6. Support Multi-Channel Notifications

**Requirements:**
- Notifications via: Email, SMS, IVR/voice calls
- Routing based on:
  - Severity and criticality
  - Contact groups and escalation policies
  - Acknowledgment and timeout rules
- Asynchronous, reliable, traceable delivery

**Implementation:**
- `notifications-service`: RabbitMQ consumer for notification events
- Integration with:
  - SMTP for email
  - Custom SMS provider (via middleware service to be introduced)
  - Custom IVR provider (via middleware service to be introduced)
- Escalation ladder with timeout-based progression
- Delivery audit log

### Non-Functional Requirements

| Category | Requirement | Implementation |
|----------|-------------|----------------|
| **Security** | Strong authentication, authorization, auditability | JWT with RBAC, AES-256-GCM encryption, PostgreSQL audit columns |
| **Extensibility** | New signal sources, alert templates, integrations | Plugin architecture, RabbitMQ event bus, versioned definitions |
| **Scalability** | High-cardinality signals, large time-series datasets | PostgreSQL partitioning, Redis caching, Apache ECharts sampling |
| **Governance** | Full audit trails, versioned configs, accountability | Created/updated timestamps, change history tables, user tracking |
| **Operational Safety** | Guardrails against expensive queries, clear retry semantics | Query timeout limits, circuit breakers (Resilience4j), RabbitMQ DLQ |

---

## Technology Stack

### ✅ APPROVED STACK (2025-12-22)

#### Frontend

```yaml
Framework:        React 18 + TypeScript
Build Tool:       Vite 5
UI Library:       shadcn/ui + Tailwind CSS
Data Tables:      TanStack Table v8 (virtualization for large datasets)
Charts:           Apache ECharts (millions of data points, WebGL support)
Form Handling:    React Hook Form + Zod (schema validation)
State Management: Zustand (lightweight, 3KB)
Data Fetching:    TanStack Query (caching, optimistic updates)
```

**Rationale:**
- React: 117+ jobs in Saudi Arabia, easiest MENA hiring
- shadcn/ui: Single design system (no hybrid compatibility issues)
- Apache ECharts: MANDATORY for time-series signals (handles millions of points)
- Zod: Type-safe validation, reusable schemas across client/server

#### Backend

```yaml
Architecture:     Microservices (6 independent services)
Framework:        Spring Boot 3.5.6
API Gateway:      Spring Cloud Gateway 4.x
Messaging:        RabbitMQ (async backbone)
Database:         PostgreSQL 15+ (system of record)
Cache:            Redis 7+ (distributed locks, aggregates)
Storage:          PVC-mounted MinIO (S3-compatible)
Monitoring:       Prometheus + Grafana
Service Mesh:     (Optional) Istio for mTLS, tracing
```

**Rationale:**
- Microservices: Independent scalability, team ownership, fault isolation
- RabbitMQ: Reliable async messaging (alert triggers, notifications, incidents)
- PostgreSQL: ACID compliance, partitioning for time-series, JSON columns for definitions
- PVC Storage: On-prem S3-compatible storage (MinIO) for attachments/RCA PDFs

#### Infrastructure

```yaml
Container Runtime:   Docker
Orchestration:       Kubernetes
Ingress:             NGINX Ingress Controller
Service Discovery:   Kubernetes DNS
Config Management:   Kubernetes ConfigMaps + Sealed Secrets
CI/CD:               (TBD - GitHub Actions, GitLab CI, or Jenkins)
```

### Technology Decisions

| Decision | Choice | Alternative Considered | Reason |
|----------|--------|------------------------|--------|
| **Frontend Framework** | React 18 | Vue 3, Angular 17 | 117+ jobs in KSA, MENA developers "fluent in React" |
| **Charts Library** | Apache ECharts | Recharts | ECharts handles millions of points (WebGL), Recharts limited to ~10K |
| **UI Library** | shadcn/ui | PrimeVue, Ant Design, Material-UI | Single design system, no hybrid compatibility issues |
| **Backend Architecture** | Microservices | Modular Monolith | User requirement: "go with Microservice always and in all phases" |
| **Messaging** | RabbitMQ | Spring @Async, Kafka | User preference: "prefer rabbit MQ unless stability issue" |
| **Storage** | PVC-mounted MinIO | AWS S3, Azure Blob | User requirement: "let us go now with mounted PVC in cluster" |
| **Database** | PostgreSQL | MongoDB, Cassandra | Already in use, ACID compliance, partitioning support |

---

## Architecture Overview

### Target Microservices Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    React 18 Frontend (Vite)                     │
│  (Dashboard, Alerts, Incidents, Integrations, Admin UI)        │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│              Spring Cloud Gateway (API Gateway)                 │
│  - JWT Authentication                                           │
│  - Rate Limiting (Redis)                                        │
│  - Request Routing                                              │
│  - Response Caching                                             │
└───┬──────┬──────┬──────┬──────┬──────────────────────────────┬──┘
    │      │      │      │      │                              │
    ▼      ▼      ▼      ▼      ▼                              ▼
┌─────┐┌─────┐┌─────┐┌─────┐┌─────┐┌─────────────────────────┐
│Sgnls││Dash ││Alert││Incid││Integ││    Notifications         │
│Svc  ││Svc  ││Svc  ││Svc  ││Svc  ││        Service           │
└──┬──┘└──┬──┘└──┬──┘└──┬──┘└──┬──┘└──┬──────────────────────┘
   │      │      │      │      │      │
   │      │      └──────┴──────┴──────┼───► RabbitMQ
   │      │                           │    (alert.triggered)
   │      │                           │    (incident.created)
   │      │                           │    (notification.send)
   │      │                           │
   ├──────┴───────────────────────────┼───► PostgreSQL
   │                                  │    - signals (partitioned)
   │                                  │    - dashboards
   │                                  │    - alert_rules
   │                                  │    - incidents
   │                                  │    - jira_mappings
   │                                  │    - notification_log
   │                                  │
   └──────────────────────────────────┼───► Redis
                                      │    - Dashboard aggregates (TTL: 5min)
                                      │    - Alert evaluation locks
                                      │    - Rate limiting counters
                                      │
                                      └───► MinIO (PVC)
                                           - RCA PDFs
                                           - Incident attachments
                                           - Evidence screenshots
```

### Service Responsibilities

| Service | Port | Responsibility | Key APIs | Data Owned |
|---------|------|----------------|----------|------------|
| **signals-service** | 8081 | Time-series query API for signal history | `GET /api/v1/signals/query` | `signals_history` (PostgreSQL partitioned) |
| **dashboards-service** | 8082 | Dashboard and chart definition management | `POST /api/v1/dashboards`, `GET /api/v1/charts/{id}` | `dashboards`, `chart_definitions`, `dashboard_versions` |
| **alerting-service** | 8083 | Alert rule evaluation engine | `POST /api/v1/alerts/rules`, `GET /api/v1/alerts/history` | `alert_rules`, `alert_evaluations`, `alert_history` |
| **incidents-service** | 8084 | Incident lifecycle management | `POST /api/v1/incidents`, `PUT /api/v1/incidents/{id}/status` | `incidents`, `incident_timeline`, `incident_attachments` |
| **integrations-service** | 8085 | External system integrations (Jira) | `POST /api/v1/integrations/jira/sync` | `jira_mappings`, `external_refs` |
| **notifications-service** | 8086 | Multi-channel notification delivery | `POST /api/v1/notifications/send` | `notification_log`, `contact_groups`, `escalation_policies` |

### RabbitMQ Event Flow

```
Alert Evaluation (alerting-service)
    │
    ├─► alert.triggered ────► incidents-service (group into incidents)
    │                     └─► notifications-service (send immediate alerts)
    │
Incident Created (incidents-service)
    │
    ├─► incident.created ───► integrations-service (create Jira ticket)
    │                     └─► notifications-service (notify on-call)
    │
Incident Updated (incidents-service)
    │
    └─► incident.updated ───► integrations-service (update Jira)
                          └─► notifications-service (notify assignee)
```

### Data Flow Example: Alert to Incident to Jira

```
1. alerting-service evaluates rule every 30s
   └─► Rule: "API error rate > 5% for 5 minutes"
   └─► Queries signals-service: GET /api/v1/signals/query
   └─► Condition met → Publish to RabbitMQ: alert.triggered

2. incidents-service consumes alert.triggered
   └─► Groups related alerts (same loader, same time window)
   └─► Creates incident record (status: NEW)
   └─► Publishes: incident.created

3. integrations-service consumes incident.created
   └─► Creates Jira ticket via REST API
   └─► Stores mapping: incident_id → jira_key
   └─► Returns Jira URL to frontend

4. notifications-service consumes incident.created
   └─► Looks up on-call engineer (escalation policy)
   └─► Sends Email → (if no ACK in 5min) → SMS → (if no ACK in 10min) → IVR
   └─► Logs all delivery attempts

5. User resolves incident via frontend
   └─► incidents-service: PUT /api/v1/incidents/123/status (RESOLVED)
   └─► Publishes: incident.updated
   └─► integrations-service updates Jira ticket status
```

---

## Current State (Phase 0)

### ✅ What Exists Today

**Deployed Services:**
- `loader-service` (Spring Boot 3.5.6) - Fully operational in Kubernetes

**Features Implemented:**
1. **ETL Loader Management**
   - CRUD operations for loader definitions
   - Multi-database support (PostgreSQL, MySQL)
   - Dynamic connection pooling (HikariCP)
   - Source database encryption (AES-256-GCM)

2. **Signals Ingestion & Query**
   - Bulk signals ingestion API (`POST /api/v1/res/signals/bulk`)
   - Time-range signal queries (`GET /api/v1/res/signals/signal/{loaderCode}`)
   - PostgreSQL storage (ready for partitioning)

3. **Security**
   - JWT authentication with RBAC (ADMIN, OPERATOR, VIEWER roles)
   - AES-256-GCM encryption for sensitive data (passwords, SQL queries)
   - Read-only compliance auditing (`DbPermissionInspector`)

4. **Infrastructure**
   - Kubernetes deployment on bare-metal cluster
   - PostgreSQL 15 (two schemas: `loader`, `signals`)
   - Prometheus monitoring (node-exporter, metrics endpoint)
   - Sealed Secrets for credential management

**Database Schema (Current):**
```sql
-- loader schema
loader               (id, loader_code, source_database_id, loader_sql [encrypted], ...)
source_databases     (id, host, port, db_name, user_name, pass_word [encrypted], ...)
segments_dictionary  (id, segment_code, description)

-- signals schema
signals_history      (id, loader_code, load_time_stamp, segment_code, rec_count, max_val, ...)
```

**What's Missing for Phase 1:**
- ❌ Frontend (no UI yet)
- ❌ Dashboard definitions
- ❌ Alert rules
- ❌ Incidents management
- ❌ Jira integration
- ❌ Notifications (Email/SMS/IVR)
- ❌ RabbitMQ messaging
- ❌ MinIO storage

---

## Development Sprints

**Sprint Duration:** 2 weeks per sprint
**Release Strategy:** Incremental releases - each sprint delivers usable functionality
**Priority Order:** Based on current environment status and replacement roadmap

### Sprint Priority Rationale

Your current environment already has:
- ✅ Loader/scrapper service (almost ready)
- ⚠️ Alternative alerting solution (to be replaced)
- ⚠️ Alternative visualization (to be replaced)
- ⚠️ Alternative notification system (to be replaced later)

**Priority Order:**
1. **Loader/Scrapper UI** (HIGH) - Complete existing service with UI
2. **Alerts** (HIGH) - Replace existing alternative solution
3. **Visualization** (HIGH) - Replace existing alternative solution
4. **RCA Templates** (MEDIUM) - New capability
5. **Incident Management** (MEDIUM) - New capability
6. **Notifications** (LOW) - Has alternative, replace last

---

## 🎯 POC: Loader GUI (Manager Demo)

**Priority:** 🔴 **HIGHEST - SHOW VALUE FIRST**

**Objective:** Demonstrate working Loader Management UI to manager as proof of concept

**Timeline:** 2-3 weeks (solo developer)

**Strategy:** Two-stage delivery - functional first, security second

---

### Stage 1: Functional Loader GUI (Week 1-2)

**Goal:** Working UI that demonstrates all loader management capabilities (NO AUTH YET)

**Deliverables:**

**React 18 + Vite Frontend (Days 1-3)**
- [ ] Project scaffolding: React 18 + TypeScript + Vite
- [ ] Install shadcn/ui + Tailwind CSS
- [ ] Project structure: `/src/pages`, `/src/components`, `/src/lib`, `/src/api`
- [ ] API client setup (axios or fetch)
- [ ] TanStack Query configuration
- [ ] Basic routing (React Router)

**Loaders List Page - `/loaders` (Days 4-5)**
- [ ] **Component:** `LoadersListPage.tsx`
- [ ] **Features:**
  - TanStack Table with loaders data
  - Columns: Loader Code, Source DB (host:port/dbname), Status, Last Run, Interval, Actions
  - Search by loader code (debounced)
  - Filter by status (ACTIVE, PAUSED, FAILED)
  - Sort by code, last run, interval
  - Pagination (10/25/50/100 per page)
  - "Create New Loader" button → opens modal
  - Row actions: Edit (icon), Pause/Resume (toggle), Delete (with confirmation)
- [ ] **API:** `GET /api/v1/res/loaders/loaders`
- [ ] **Styling:** shadcn/ui Table + Card components

**Loader Details Page - `/loaders/{code}` (Days 6-7)**
- [ ] **Component:** `LoaderDetailsPage.tsx`
- [ ] **Sections:**
  - **Header:** Loader code, status badge, Edit/Delete buttons
  - **Configuration Tab:**
    - Source database info (host, port, database, username, type)
    - SQL query (read-only, Monaco editor with syntax highlighting)
    - Execution settings (interval, max parallelism, fetch size)
    - Segments assigned (chips/badges)
  - **Execution History Tab:**
    - TanStack Table with last 50 executions
    - Columns: Timestamp, Status, Records Processed, Duration, Error Message
    - Filter by status
    - Export to CSV button
  - **Signals Tab:**
    - Mini chart preview (optional - defer to later sprint)
    - Link to full signals explorer
- [ ] **APIs:**
  - `GET /api/v1/res/loaders/{code}`
  - `GET /api/v1/res/loaders/{code}/history` (you may need to create this)
- [ ] **Styling:** shadcn/ui Tabs + Table components

**Create/Edit Loader Form (Days 8-10)**
- [ ] **Component:** `LoaderEditorDialog.tsx` (modal dialog)
- [ ] **Form Fields (React Hook Form + Zod validation):**
  - Loader Code (text, required, max 64 chars, unique)
  - Source Database (dropdown, fetch from `/api/v1/admin/res/db-sources`)
  - Loader SQL (Monaco editor, required, SQL syntax highlighting)
  - Interval (number + unit selector: minutes/hours/days)
  - Max Parallelism (number, default 1, min 1, max 10)
  - Fetch Size (number, default 1000, min 100)
  - Segments (multi-select, fetch from segments dictionary)
  - Purge Strategy (dropdown: NONE, OLD_RUNS, ALL)
- [ ] **Validation:**
  - Loader code: alphanumeric + underscore, no spaces
  - SQL query: not empty, basic SQL syntax check (starts with SELECT)
  - Interval: > 0
- [ ] **APIs:**
  - `POST /api/v1/res/loaders` (create)
  - `PUT /api/v1/res/loaders/{code}` (update)
  - `GET /api/v1/admin/res/db-sources` (source dropdown)
- [ ] **UX:**
  - Validation errors shown inline
  - Loading spinner during submit
  - Success toast notification
  - Close dialog and refresh list on success

**Source Databases Page - `/sources` (Day 11)**
- [ ] **Component:** `SourceDatabasesPage.tsx`
- [ ] **Features:**
  - TanStack Table with sources
  - Columns: Code, Host, Port, Database, Type, Username, Read-Only Status (badge)
  - Passwords shown as `********` (encrypted, don't decrypt in UI)
  - Read-only compliance badge: ✅ Verified / ❌ Failed
  - "Test Connection" button per row (optional)
  - "Create New Source" button (optional - can defer)
- [ ] **API:** `GET /api/v1/admin/res/db-sources`
- [ ] **Styling:** shadcn/ui Table + Badge components

**CORS Configuration (Day 12)**
- [ ] **Backend:** Add CORS configuration to loader-service
  ```java
  @Configuration
  public class WebConfig implements WebMvcConfigurer {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
          registry.addMapping("/api/**")
                  .allowedOrigins("http://localhost:5173", "http://localhost:3000")
                  .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                  .allowedHeaders("*")
                  .allowCredentials(true);
      }
  }
  ```
- [ ] Test from React dev server (http://localhost:5173)

**Local Testing (Days 13-14)**
- [ ] End-to-end flows:
  - Create loader → appears in list
  - Edit loader → changes saved
  - Delete loader → confirmation dialog → removed from list
  - View loader details → see config and history
  - Filter/search loaders → results update
  - View sources → credentials hidden
- [ ] Fix bugs, improve UX
- [ ] Polish styling, responsive design

**Stage 1 Demo to Manager:**
✅ **Show:** Fully functional loader management UI
✅ **Explain:** "This is working but accessible to anyone - we'll add security in Stage 2"
✅ **Value:** Can manage loaders through UI instead of database queries

---

### Stage 2: Login + Access Control (Week 3)

**Goal:** Secure the UI with JWT authentication and role-based access control

**Deliverables:**

**Authentication UI (Days 1-2)**
- [ ] **Login Page:** `LoginPage.tsx`
  - Form: username + password (React Hook Form + Zod)
  - API: `POST /api/v1/auth/login`
  - Store JWT token in localStorage
  - Redirect to `/loaders` on success
  - Show error message on failure
  - Professional styling (shadcn/ui Form components)
- [ ] **Auth Context:**
  - `AuthContext.tsx`: stores user, token, roles
  - `useAuth()` hook: login, logout, isAuthenticated
  - `ProtectedRoute` component: redirect to login if not authenticated
- [ ] **Token Management:**
  - Attach token to all API requests (axios interceptor)
  - Handle 401 responses (token expired → logout → redirect to login)
  - Optional: Token refresh logic

**Access Control (Days 3-4)**
- [ ] **Role-Based UI:**
  - ADMIN: Full access (create, edit, delete loaders)
  - OPERATOR: Read + operational endpoints (pause/resume, view)
  - VIEWER: Read-only (no create/edit/delete buttons)
- [ ] **Conditional Rendering:**
  ```tsx
  {hasRole('ADMIN') && (
    <Button onClick={handleDelete}>Delete</Button>
  )}
  ```
- [ ] **Navigation:**
  - Add user profile dropdown (top-right)
  - Show username + role
  - Logout button
  - Avatar/initials

**App Shell & Layout (Day 5)**
- [ ] **Component:** `AppLayout.tsx`
  - Sidebar navigation (loaders, sources, signals, etc.)
  - Header with breadcrumbs + user profile
  - Main content area
  - Responsive (collapse sidebar on mobile)
- [ ] **Navigation Items:**
  - Home (dashboard - placeholder for now)
  - Loaders (link to `/loaders`)
  - Sources (link to `/sources`)
  - Signals (placeholder)
  - Settings (placeholder)

**Testing & Polish (Days 6-7)**
- [ ] Test with 3 users:
  - admin/admin123 (ROLE_ADMIN) - can do everything
  - operator/operator123 (ROLE_OPERATOR) - can view + pause/resume
  - viewer/viewer123 (ROLE_VIEWER) - read-only
- [ ] Verify RBAC works (buttons hidden for VIEWER)
- [ ] Logout → redirects to login
- [ ] Token expired → redirects to login
- [ ] Fix any security issues

**Stage 2 Demo to Manager:**
✅ **Show:** Same UI but now with login screen and role-based access
✅ **Explain:** "Now it's production-ready with security - only authorized users can access"
✅ **Value:** Enterprise-grade security, audit trail (who did what)

---

## POC Success Criteria (Manager Demo Checklist)

**Stage 1 Demo (Functional UI):**
- ✅ Login to application (Stage 2)
- ✅ See list of 5+ loaders in table
- ✅ Search for loader by code → finds it instantly
- ✅ Filter by status (ACTIVE) → shows only active loaders
- ✅ Click "Create New Loader" → form opens
- ✅ Fill form, submit → loader created, appears in list
- ✅ Click loader row → details page opens
- ✅ See loader configuration (SQL query, source DB, interval)
- ✅ See execution history (last 50 runs)
- ✅ Click Edit → form opens with current values
- ✅ Change interval, submit → loader updated
- ✅ Click Delete → confirmation dialog → loader deleted
- ✅ View source databases → see encrypted credentials as `***`
- ✅ UI is responsive, fast, professional

**Stage 2 Demo (Security):**
- ✅ Logout → redirected to login page
- ✅ Login as VIEWER → see loaders but no Create/Edit/Delete buttons
- ✅ Login as OPERATOR → see loaders, can pause/resume, no delete
- ✅ Login as ADMIN → full access
- ✅ Token expiry handled gracefully
- ✅ Unauthorized API calls return 403 Forbidden

**Manager Talking Points:**
1. **Speed:** "Built in 2-3 weeks with AI assistance (Claude Code)"
2. **Technology:** "Modern stack - React 18, TypeScript, shadcn/ui - same as Netflix, Vercel"
3. **Security:** "JWT authentication, role-based access control, encrypted credentials"
4. **Scalability:** "Built on Spring Boot + Kubernetes, can handle 1000s of loaders"
5. **Next Steps:** "This is the foundation - we'll add charts, alerting, incidents on top"

---

## Sprint Breakdown (After POC)

**Once POC is approved, continue with these sprints:**

### Sprint 1: Infrastructure + Signals Visualization (Weeks 4-7)

**Goal:** Deploy production infrastructure and add time-series charts

**Priority:** 🔴 HIGH - Make it production-ready + visualize loader execution data

**Dependencies Introduced:**
- ✅ Redis (caching + rate limiting)
- ✅ Spring Cloud Gateway (API gateway + routing)
- ✅ SSL Certificates (HTTPS/TLS)
- ✅ Authentication frontend (JWT-based login)

**Sprint 1 (Weeks 1-2): Infrastructure**

**Deliverables:**
- [ ] **Redis Deployment**
  - Redis StatefulSet in Kubernetes
  - Persistent volume for cache data
  - Redis password in Sealed Secret
  - Health check endpoint
  - Connection test from loader-service
- [ ] **Spring Cloud Gateway Deployment**
  - Gateway pod (port 8080)
  - JWT validation filter (reuse loader-service JWT secret)
  - Route configuration:
    - `/api/v1/res/loaders/**` → `loader-service:8080`
    - `/api/v1/res/signals/**` → `loader-service:8080`
    - `/ops/v1/admin/**` → `loader-service:8080`
    - `/api/v1/auth/**` → `loader-service:8080`
  - Rate limiting (100 req/min per user, Redis-backed)
  - CORS configuration (allow frontend origin)
  - Health check: `/actuator/health`
- [ ] **SSL Certificate Setup**
  - TLS certificate generation (Let's Encrypt or self-signed)
  - Kubernetes Secret for TLS cert/key
  - NGINX Ingress with TLS termination
  - HTTP → HTTPS redirect
  - Certificate renewal automation (if Let's Encrypt)
- [ ] **Update loader-service**
  - Remove direct NodePort exposure
  - Service becomes ClusterIP only
  - All traffic routes through Gateway

**Acceptance Criteria:**
- ✅ Gateway accessible via HTTPS (https://your-domain.com)
- ✅ `curl -k https://your-domain.com/api/v1/res/loaders/loaders` returns 401 (JWT required)
- ✅ Redis pingable: `redis-cli -h redis-service ping` returns PONG
- ✅ Rate limiting works: 101st request in 1 minute returns 429 Too Many Requests
- ✅ loader-service APIs accessible via Gateway only

---

**Sprint 2 (Weeks 3-4): Loader Management UI**



**Deliverables:**
- [ ] **React 18 + Vite Frontend Setup**
  - TypeScript + ESLint + Prettier
  - Folder structure: `/src/pages`, `/src/components`, `/src/lib`, `/src/api`
  - shadcn/ui + Tailwind CSS installation
  - Custom theme configuration
- [ ] **Authentication UI**
  - Login page (React Hook Form + Zod)
  - JWT token storage (localStorage)
  - Protected routes (React Router)
  - Token refresh logic
  - User profile dropdown
- [ ] **Layout Components**
  - AppShell (sidebar, header, main content)
  - Navigation menu
  - Breadcrumbs
- [ ] **Loader Management Pages**
  - **Loaders List** (`/loaders`)
    - TanStack Table with loaders
    - Columns: Code, Source DB, Status, Last Run, Actions
    - Search/filter
    - Create button → modal
  - **Loader Details** (`/loaders/{code}`)
    - Loader metadata (code, SQL query [decrypted], interval, parallelism)
    - Source database info (host, port, dbname, username, type)
    - Segments assigned
    - Execution history (last 50 runs)
    - Actions: Edit, Pause/Resume, Delete
  - **Loader Editor** (`/loaders/{code}/edit`)
    - Form: React Hook Form + Zod validation
    - SQL query editor (CodeMirror or Monaco)
    - Source database dropdown (from `/api/v1/admin/res/db-sources`)
    - Segment multi-select
    - Preview button (test SQL query)
- [ ] **Source Databases Page** (`/sources`)
  - TanStack Table with sources
  - Read-only compliance badge (✅ / ❌)
  - Create source button → modal
  - Credentials displayed as `***` (encrypted)

**Acceptance Criteria:**
- ✅ User can log in with admin/admin123
- ✅ User can view all loaders in table
- ✅ User can create new loader with validation
- ✅ User can edit existing loader
- ✅ User can pause/resume loader
- ✅ SQL query editor has syntax highlighting
- ✅ Frontend deployed to Kubernetes (NGINX pod)
- ✅ Accessible via https://your-domain.com

**Release:** ✅ **LOADER UI v1.0** - Complete loader management capability

---

### Sprint 3-4: Signals Visualization (4 weeks)

**Goal:** Replace existing alternative visualization with Apache ECharts time-series

**Priority:** 🔴 HIGH - See loader execution data

**Sprint 3 (Weeks 5-6): Signals Query Enhancement**

**Deliverables:**
- [ ] **Enhance signals-service APIs** (extend loader-service)
  - `GET /api/v1/signals/query` with parameters:
    - `loaderCode` (required)
    - `fromEpoch`, `toEpoch` (required)
    - `segmentCode` (optional)
    - `aggregation`: `raw`, `avg`, `sum`, `min`, `max` (default: `raw`)
    - `bucket`: `1m`, `5m`, `15m`, `1h`, `1d` (time bucketing)
    - `limit`: max points to return (default: 10000)
  - Data sampling (LTTB algorithm) if result > 10K points
  - Response format:
    ```json
    {
      "loaderCode": "DAILY_SALES",
      "timeRange": {"from": 1640000000, "to": 1640086400},
      "aggregation": "avg",
      "bucket": "1h",
      "dataPoints": [
        {"timestamp": 1640000000, "recCount": 1250, "avgVal": 45.5, ...},
        ...
      ]
    }
    ```
- [ ] **Redis caching for signals**
  - Cache key: `signals:{loaderCode}:{fromEpoch}:{toEpoch}:{aggregation}:{bucket}`
  - TTL: 5 minutes for recent data (<24h ago), 1 hour for historical
  - Cache warming for common queries
- [ ] **PostgreSQL optimization**
  - Create partitions for `signals_history` by month
  - Indexes on: `(loader_code, load_time_stamp)`, `(segment_code, load_time_stamp)`
  - Automated partition creation (cron job)

**Acceptance Criteria:**
- ✅ Query 1M signals returns in <200ms (cached)
- ✅ Query 1M signals returns in <2s (uncached, with sampling)
- ✅ Aggregations return correct values (tested with known dataset)

**Sprint 4 (Weeks 7-8): Charts UI**

**Deliverables:**
- [ ] **Signals Explorer Page** (`/signals`)
  - Loader selector (dropdown)
  - Time range picker (last 1h, 6h, 24h, 7d, custom)
  - Segment filter (multi-select)
  - Aggregation selector (raw, avg, sum, min, max)
  - Bucket selector (1m, 5m, 1h, 1d)
- [ ] **Apache ECharts Integration**
  - Install `echarts` and `echarts-for-react`
  - Line chart component (time-series)
  - Configuration:
    - X-axis: timestamp (formatted as dates)
    - Y-axis: recCount, avgVal, sumVal, minVal, maxVal (multi-series)
    - Zoom/pan support
    - Tooltip with data details
    - Legend toggle (show/hide series)
  - Chart types: Line, Area, Bar
- [ ] **Real-time Updates**
  - TanStack Query with `refetchInterval: 30000` (30s)
  - "Last updated" timestamp display
  - Auto-refresh toggle

**Acceptance Criteria:**
- ✅ User can select loader and see signals chart
- ✅ Chart renders 100K+ points smoothly (with sampling)
- ✅ Time range changes update chart in <1s
- ✅ Chart updates every 30s automatically
- ✅ Zoom/pan works without lag

**Release:** ✅ **SIGNALS VIZ v1.0** - Time-series visualization for loader monitoring

---

### Sprint 5-6: Alerting Engine (4 weeks)

**Goal:** Replace existing alternative alerting solution with rule-based engine

**Priority:** 🔴 HIGH - Critical for monitoring

**Sprint 5 (Weeks 9-10): Alert Rules Service**

**Deliverables:**
- [ ] **alerting-service** (Spring Boot, port 8083)
  - PostgreSQL connection
  - JPA entities: `AlertRule`, `AlertEvaluation`, `AlertHistory`
  - REST APIs:
    - `POST /api/v1/alerts/rules` (create rule)
    - `GET /api/v1/alerts/rules` (list rules)
    - `PUT /api/v1/alerts/rules/{id}` (update rule)
    - `PUT /api/v1/alerts/rules/{id}/status` (activate/pause)
    - `GET /api/v1/alerts/history` (firing history)
- [ ] **Alert Rule Schema**
  ```json
  {
    "ruleId": "uuid",
    "ruleName": "High Error Rate",
    "loaderCode": "DAILY_SALES",
    "metric": "error_rate",
    "conditions": [
      {"operator": ">", "threshold": 0.05, "duration": "5m"}
    ],
    "severity": "HIGH",
    "suppressionWindow": "30m"
  }
  ```
- [ ] **Rule Evaluation Engine**
  - Spring `@Scheduled(fixedDelay = 30000)` (every 30s)
  - Fetch active rules from database
  - Query signals-service for each rule
  - Evaluate conditions (>, <, =, >=, <=)
  - Track state: OK → PENDING → FIRING → RESOLVED
  - Store evaluation results in `alert_evaluations` table
- [ ] **RabbitMQ Integration**
  - Deploy RabbitMQ (if not already)
  - Exchange: `alerts` (topic)
  - Queues: `alert.triggered`, `alert.resolved`
  - Publish event when rule fires:
    ```json
    {
      "alertId": "uuid",
      "ruleId": "uuid",
      "ruleName": "High Error Rate",
      "loaderCode": "DAILY_SALES",
      "severity": "HIGH",
      "triggeredAt": 1640000000,
      "currentValue": 0.08,
      "threshold": 0.05
    }
    ```
- [ ] **Redis Distributed Locks**
  - Lock key: `alert:eval:lock:{ruleId}`
  - TTL: 60s
  - Prevents duplicate evaluations (if multiple alerting-service pods)

**Acceptance Criteria:**
- ✅ Rules evaluate every 30s
- ✅ Alert triggers when condition met for full duration
- ✅ Suppression window prevents duplicate alerts
- ✅ Events published to RabbitMQ successfully

**Sprint 6 (Weeks 11-12): Alert Management UI + Email Notifications**

**Deliverables:**
- [ ] **Alert Rules Page** (`/alerts/rules`)
  - TanStack Table with rules
  - Status badges: ACTIVE (green), PAUSED (gray), FIRING (red)
  - Create rule button → modal
  - Actions: Edit, Pause/Resume, Delete
- [ ] **Alert Rule Editor** (`/alerts/rules/new`)
  - Loader selector
  - Metric selector (recCount, error_rate, avgVal, etc.)
  - Condition builder (operator, threshold, duration)
  - Severity selector (INFO, WARNING, HIGH, CRITICAL)
  - Suppression window input
  - Test rule button (dry-run evaluation)
- [ ] **Alert History Page** (`/alerts/history`)
  - TanStack Table with alert events
  - Columns: Rule name, Loader, Severity, Triggered at, Current value, Status
  - Filter by: rule, severity, time range, status
  - View details modal (full event payload)
  - Acknowledge button
- [ ] **Email Notifications (SMTP)**
  - notifications-service consumes `alert.triggered` from RabbitMQ
  - SMTP integration (use existing SMTP server)
  - Email template (Thymeleaf):
    - Subject: `[{severity}] {ruleName} triggered`
    - Body: Rule name, loader code, current value, threshold, chart image (optional)
  - Retry logic: 3 attempts with exponential backoff
  - Delivery log in `notification_log` table

**Acceptance Criteria:**
- ✅ User can create alert rule from UI
- ✅ Test rule shows preview of evaluation
- ✅ Alert fires and appears in history within 30s
- ✅ Email sent within 30s of alert firing
- ✅ User can acknowledge alert

**Release:** ✅ **ALERTING v1.0** - Rule-based monitoring with email notifications

---

### Sprint 7-8: Advanced Visualization + Dashboards (4 weeks)

**Goal:** Multi-chart dashboards with definition-driven approach

**Priority:** 🟡 MEDIUM - Improves visualization capability

**Sprint 7 (Weeks 13-14): Dashboard Definitions Service**

**Deliverables:**
- [ ] **dashboards-service** (Spring Boot, port 8082)
  - PostgreSQL connection
  - JPA entities: `Dashboard`, `ChartDefinition`, `DashboardVersion`
  - REST APIs:
    - `POST /api/v1/dashboards` (create dashboard)
    - `GET /api/v1/dashboards` (list dashboards)
    - `GET /api/v1/dashboards/{id}` (get dashboard with charts)
    - `POST /api/v1/charts` (create chart definition)
    - `GET /api/v1/charts/{id}` (get chart definition)
- [ ] **Chart Definition Schema**
  ```json
  {
    "chartId": "uuid",
    "chartName": "Daily Sales Records",
    "chartType": "line",
    "dataSource": {
      "service": "signals-service",
      "endpoint": "/api/v1/signals/query",
      "params": {
        "loaderCode": "DAILY_SALES",
        "aggregation": "avg",
        "bucket": "1h"
      }
    },
    "visualization": {
      "xAxis": "timestamp",
      "yAxis": ["recCount", "avgVal"],
      "title": "Daily Sales Records",
      "colors": ["#8884d8", "#82ca9d"]
    }
  }
  ```
- [ ] **Dashboard Composition**
  - Dashboard references multiple chart definitions
  - Layout: grid positions (x, y, width, height)
  - Shared time range across charts
- [ ] **Versioning**
  - Track dashboard versions (v1, v2, v3, ...)
  - Rollback capability
  - Audit columns: created_by, created_at, updated_by, updated_at

**Acceptance Criteria:**
- ✅ Dashboard definitions stored in PostgreSQL
- ✅ Chart definitions reusable across dashboards
- ✅ Versioning tracks all changes

**Sprint 8 (Weeks 15-16): Dashboard UI**

**Deliverables:**
- [ ] **Dashboards Page** (`/dashboards`)
  - Grid view of dashboards (thumbnails)
  - Create dashboard button
  - Actions: View, Edit, Delete, Duplicate
- [ ] **Dashboard View** (`/dashboards/{id}`)
  - Grid layout (CSS Grid or React Grid Layout)
  - Render charts from definitions (Apache ECharts)
  - Shared time range picker (affects all charts)
  - Refresh button (re-fetch all charts)
  - Auto-refresh toggle (every 30s)
  - Export to PDF button (future)
- [ ] **Dashboard Editor** (`/dashboards/{id}/edit`)
  - Add chart button → select from chart definitions
  - Drag-and-drop layout (React Grid Layout)
  - Resize charts
  - Remove chart
  - Save/Cancel
- [ ] **Chart Definition Editor** (`/charts/new`)
  - Loader selector
  - Metrics selector (multi-select)
  - Chart type selector (line, area, bar, pie)
  - Preview pane (live chart preview)
  - Save as template

**Acceptance Criteria:**
- ✅ User can create dashboard with 5+ charts
- ✅ Charts render without performance issues
- ✅ Time range change updates all charts
- ✅ Dashboard layout saved correctly

**Release:** ✅ **DASHBOARDS v1.0** - Multi-chart operational dashboards

---

### Sprint 9-10: RCA Templates + Basic Incidents (4 weeks)

**Goal:** RCA report generation + incident tracking

**Priority:** 🟡 MEDIUM - New capability for investigations

**Sprint 9 (Weeks 17-18): MinIO + PDF Generation**

**Deliverables:**
- [ ] **MinIO Deployment** (PVC-mounted S3-compatible storage)
  - StatefulSet with PVC (100GB)
  - Buckets: `attachments`, `rca-reports`, `exports`
  - Access keys in Sealed Secret
  - Health check endpoint
  - S3 CLI test (`mc`)
- [ ] **RCA Template Engine** (in dashboards-service)
  - REST API: `POST /api/v1/rca/generate`
  - Input: incident_id, chart_ids[], time_range, notes
  - PDF generation (iText or Apache PDFBox)
  - Template sections:
    - Cover page (incident title, date, assignee)
    - Executive summary
    - Timeline
    - Charts (embedded ECharts images)
    - Investigation notes
    - Root cause analysis
    - Recommendations
  - Upload PDF to MinIO
  - Return presigned URL (expires in 7 days)

**Acceptance Criteria:**
- ✅ MinIO accessible via S3 CLI
- ✅ RCA PDF generated with charts and notes
- ✅ PDF downloadable via presigned URL

**Sprint 10 (Weeks 19-20): Basic Incidents Service**

**Deliverables:**
- [ ] **incidents-service** (Spring Boot, port 8084)
  - PostgreSQL connection
  - JPA entities: `Incident`, `IncidentTimeline`
  - REST APIs:
    - `POST /api/v1/incidents` (create incident)
    - `GET /api/v1/incidents` (list incidents)
    - `GET /api/v1/incidents/{id}` (get incident details)
    - `PUT /api/v1/incidents/{id}/status` (update status: NEW → INVESTIGATING → RESOLVED → CLOSED)
    - `PUT /api/v1/incidents/{id}/assign` (assign to user)
    - `POST /api/v1/incidents/{id}/notes` (add investigation note)
- [ ] **Incidents UI** (`/incidents`)
  - TanStack Table with incidents
  - Status badges (NEW, INVESTIGATING, RESOLVED, CLOSED)
  - Create incident button → modal
- [ ] **Incident Details** (`/incidents/{id}`)
  - Incident metadata (title, status, assignee, created date)
  - Timeline (audit trail)
  - Investigation notes (rich text editor)
  - Generate RCA button
  - Actions: Assign, Change status, Add note

**Acceptance Criteria:**
- ✅ User can create incident manually
- ✅ User can change incident status
- ✅ Timeline tracks all changes
- ✅ RCA PDF generated from incident details

**Release:** ✅ **RCA v1.0** - Investigation documentation capability

---

### Sprint 11-12: Full Incident Management + Alert Grouping (4 weeks)

**Goal:** Complete incident lifecycle + alert grouping

**Priority:** 🟡 MEDIUM - Mature incident management

**Sprint 11 (Weeks 21-22): Alert Grouping Logic**

**Deliverables:**
- [ ] **Alert Grouping in incidents-service**
  - Consume `alert.triggered` from RabbitMQ
  - Grouping rules:
    - Same loader_code
    - Within 10-minute window
    - Same severity
  - Create incident if: 3+ alerts in group OR severity = CRITICAL
  - Link alerts to incident (`incident_alerts` table)
  - Publish `incident.created` event to RabbitMQ
- [ ] **Incident Details Enhancement**
  - Related alerts section (table with alert events)
  - Alert timeline visualization
  - Charts from alerts (embedded signals charts)

**Acceptance Criteria:**
- ✅ 3+ alerts create incident automatically
- ✅ Incident shows all related alerts
- ✅ Charts embedded in incident view

**Sprint 12 (Weeks 23-24): Attachments + Enhanced Workflow**

**Deliverables:**
- [ ] **Attachments Feature**
  - Upload button on incident details page
  - File types: PDF, PNG, JPG, TXT, LOG (max 10MB)
  - Upload to MinIO (`attachments/{incident_id}/{filename}`)
  - Store reference in `incident_attachments` table
  - Download via presigned URL
- [ ] **Enhanced Workflow**
  - State machine validation (prevent invalid transitions)
  - Assignment notifications (email to assignee)
  - Resolution notes (required when resolving)
  - Closure reason (required when closing)
- [ ] **Incident Search & Filters**
  - Search by: title, description, notes
  - Filter by: status, assignee, severity, date range, loader
  - Export to CSV

**Acceptance Criteria:**
- ✅ User can upload attachments (screenshots, logs)
- ✅ Attachments downloadable from incident details
- ✅ Workflow validates state transitions
- ✅ Incident search returns correct results

**Release:** ✅ **INCIDENTS v1.0** - Complete incident management

---

### Sprint 13-14: Jira Integration (4 weeks)

**Goal:** Bi-directional sync with Jira

**Priority:** 🟡 MEDIUM - External ticketing integration

**Sprint 13 (Weeks 25-26): Jira Ticket Creation**

**Deliverables:**
- [ ] **integrations-service** (Spring Boot, port 8085)
  - Jira REST API client (Spring RestTemplate)
  - PostgreSQL connection
  - JPA entities: `JiraMapping`, `ExternalRef`
  - REST APIs:
    - `POST /api/v1/integrations/jira/sync` (manual sync)
    - `GET /api/v1/integrations/jira/mappings` (list mappings)
- [ ] **Jira Configuration**
  - Jira URL, username, API token (Sealed Secret)
  - Project key, issue type (configurable)
- [ ] **Ticket Creation**
  - Consume `incident.created` from RabbitMQ
  - Map incident fields → Jira issue fields:
    - Summary: incident title
    - Description: incident description + alerts
    - Priority: severity → Jira priority
    - Assignee: map internal user → Jira user
  - Create ticket via Jira API
  - Store mapping: `incident_id` → `jira_key`
  - Retry logic: 3 attempts with exponential backoff
  - Idempotency: check `jira_mappings` before creating

**Acceptance Criteria:**
- ✅ Incident creation triggers Jira ticket
- ✅ Jira ticket URL visible in incident details
- ✅ Mapping stored in database

**Sprint 14 (Weeks 27-28): Bi-directional Sync**

**Deliverables:**
- [ ] **Incident → Jira Sync**
  - Consume `incident.updated` from RabbitMQ
  - Update Jira ticket:
    - Status change: map internal status → Jira status
    - Assignee change
    - Notes added → Jira comment
- [ ] **Jira → Incident Sync (Webhook)**
  - REST API: `POST /api/v1/integrations/jira/webhook`
  - Validate webhook signature (HMAC)
  - Parse Jira webhook payload
  - Update incident:
    - Status change: map Jira status → internal status
    - Assignee change
    - Comment added → incident note
- [ ] **Webhook Configuration in Jira**
  - Register webhook in Jira project settings
  - Events: issue_updated, issue_comment_created
  - URL: `https://your-domain.com/api/v1/integrations/jira/webhook`
  - Secret for signature validation
- [ ] **Audit Log**
  - Track all Jira API calls
  - Store request/response for debugging
  - Table: `jira_sync_log`

**Acceptance Criteria:**
- ✅ Incident status change updates Jira ticket
- ✅ Jira status change updates incident
- ✅ Webhook signature validated
- ✅ Sync history logged

**Release:** ✅ **JIRA INTEGRATION v1.0** - Bi-directional ticketing sync

---

### Sprint 15-16: Notifications (Optional - Has Alternative) (4 weeks)

**Goal:** Multi-channel notifications with custom providers

**Priority:** 🟢 LOW - Has alternative, replace last

**Sprint 15 (Weeks 29-30): Notification Framework**

**Deliverables:**
- [ ] **notifications-service** (Spring Boot, port 8086)
  - RabbitMQ consumer for `alert.triggered`, `incident.created`, `incident.updated`
  - PostgreSQL connection (notification_log)
  - REST APIs:
    - `POST /api/v1/notifications/send` (manual notification)
    - `GET /api/v1/notifications/log` (delivery history)
- [ ] **Notification Provider Interface**
  ```java
  public interface NotificationProvider {
      NotificationResult send(NotificationRequest request);
      DeliveryStatus checkStatus(String messageId);
  }
  ```
- [ ] **SMTP Provider** (complete implementation)
  - Email template engine (Thymeleaf)
  - Templates: alert-triggered, incident-created, incident-resolved
  - Retry logic: 3 attempts
- [ ] **Custom SMS/IVR Adapters** (mock implementations)
  - Mock SMS provider (logs to console)
  - Mock IVR provider (logs to console)
  - User will provide actual implementations later
- [ ] **Contact Groups & Policies**
  - PostgreSQL tables: `contact_groups`, `escalation_policies`
  - REST APIs:
    - `POST /api/v1/notifications/contact-groups` (create group)
    - `POST /api/v1/notifications/escalation-policies` (create policy)
  - UI for contact group management

**Acceptance Criteria:**
- ✅ Email notifications work end-to-end
- ✅ Mock SMS/IVR providers testable
- ✅ Contact groups configurable via UI

**Sprint 16 (Weeks 31-32): Escalation Ladder**

**Deliverables:**
- [ ] **Escalation Logic**
  - Level 1: Email → wait 5 min
  - Level 2: SMS (mock) → wait 10 min
  - Level 3: IVR (mock) → wait 15 min
  - Level 4: Manager escalation
  - Acknowledgment API: `POST /api/v1/alerts/{id}/acknowledge`
  - Stop escalation when acknowledged
- [ ] **Notification Log UI** (`/notifications/log`)
  - TanStack Table with delivery attempts
  - Columns: Type, Recipient, Status, Sent at, Delivered at, Error
  - Filter by: type, status, date range
- [ ] **Documentation for Custom Providers**
  - Guide: "Implementing Custom SMS Provider"
  - Guide: "Implementing Custom IVR Provider"
  - Example code with middleware integration patterns

**Acceptance Criteria:**
- ✅ Escalation ladder works with timing
- ✅ Acknowledgment stops escalation
- ✅ All delivery attempts logged
- ✅ Documentation complete for user to implement custom providers

**Release:** ✅ **NOTIFICATIONS v1.0** - Multi-channel alerting (Email complete, SMS/IVR framework ready)

---

## Sprint Status Summary

| Sprint | Weeks | Duration | Goal | Priority | Status | Completion % |
|--------|-------|----------|------|----------|--------|--------------|
| **Sprint 1** | 1-2 | 2 weeks | Infrastructure (Redis, Gateway, SSL, Auth) | 🔴 CRITICAL | ⏸️ PENDING | 0% |
| **Sprint 2** | 3-4 | 2 weeks | Loader Management UI | 🔴 CRITICAL | ⏸️ PENDING | 0% |
| **Sprint 3** | 5-6 | 2 weeks | Signals Query Enhancement | 🔴 HIGH | ⏸️ PENDING | 0% |
| **Sprint 4** | 7-8 | 2 weeks | Charts UI (Apache ECharts) | 🔴 HIGH | ⏸️ PENDING | 0% |
| **Sprint 5** | 9-10 | 2 weeks | Alert Rules Service | 🔴 HIGH | ⏸️ PENDING | 0% |
| **Sprint 6** | 11-12 | 2 weeks | Alert Management UI + Email | 🔴 HIGH | ⏸️ PENDING | 0% |
| **Sprint 7** | 13-14 | 2 weeks | Dashboard Definitions Service | 🟡 MEDIUM | ⏸️ PENDING | 0% |
| **Sprint 8** | 15-16 | 2 weeks | Dashboard UI | 🟡 MEDIUM | ⏸️ PENDING | 0% |
| **Sprint 9** | 17-18 | 2 weeks | MinIO + RCA PDF Generation | 🟡 MEDIUM | ⏸️ PENDING | 0% |
| **Sprint 10** | 19-20 | 2 weeks | Basic Incidents Service | 🟡 MEDIUM | ⏸️ PENDING | 0% |
| **Sprint 11** | 21-22 | 2 weeks | Alert Grouping | 🟡 MEDIUM | ⏸️ PENDING | 0% |
| **Sprint 12** | 23-24 | 2 weeks | Attachments + Enhanced Workflow | 🟡 MEDIUM | ⏸️ PENDING | 0% |
| **Sprint 13** | 25-26 | 2 weeks | Jira Ticket Creation | 🟡 MEDIUM | ⏸️ PENDING | 0% |
| **Sprint 14** | 27-28 | 2 weeks | Bi-directional Jira Sync | 🟡 MEDIUM | ⏸️ PENDING | 0% |
| **Sprint 15** | 29-30 | 2 weeks | Notification Framework | 🟢 LOW | ⏸️ PENDING | 0% |
| **Sprint 16** | 31-32 | 2 weeks | Escalation Ladder | 🟢 LOW | ⏸️ PENDING | 0% |

**Total Duration:** 32 weeks (8 months)
**Current Sprint:** Sprint 0 (Phase 0 complete, preparing for Sprint 1)

---

## Release Milestones

| Milestone | Sprints | Features | Business Value |
|-----------|---------|----------|----------------|
| **M1: Loader UI v1.0** | Sprint 1-2 | Infrastructure + Loader management UI | Complete existing loader service with UI |
| **M2: Signals Viz v1.0** | Sprint 3-4 | Time-series visualization | Replace alternative visualization |
| **M3: Alerting v1.0** | Sprint 5-6 | Rule-based monitoring + email notifications | Replace alternative alerting solution |
| **M4: Dashboards v1.0** | Sprint 7-8 | Multi-chart operational dashboards | Enhanced visualization capability |
| **M5: RCA v1.0** | Sprint 9-10 | RCA report generation + basic incidents | Investigation documentation |
| **M6: Incidents v1.0** | Sprint 11-12 | Complete incident management | Full incident lifecycle |
| **M7: Jira Integration v1.0** | Sprint 13-14 | Bi-directional Jira sync | External ticketing integration |
| **M8: Notifications v1.0** | Sprint 15-16 | Multi-channel notifications (framework) | Replace alternative notification system |

---

## Sprint Planning Guidelines

### Sprint Kickoff (Day 1)
1. Review sprint goals and deliverables
2. Break down deliverables into tasks (Jira/Linear)
3. Estimate task effort (story points or hours)
4. Assign tasks to team members
5. Identify dependencies and blockers

### Daily Standups (15 minutes)
1. What did I complete yesterday?
2. What am I working on today?
3. Any blockers?

### Sprint Review (Last Day)
1. Demo completed features to stakeholders
2. Review acceptance criteria (all met?)
3. Gather feedback
4. Update PROJECT_TRACKER.md (check off deliverables)

### Sprint Retrospective (Last Day)
1. What went well?
2. What could be improved?
3. Action items for next sprint

### Definition of Done
- [ ] Code written and reviewed
- [ ] Unit tests passing
- [ ] Integration tests passing (if applicable)
- [ ] Documentation updated
- [ ] Deployed to staging/production
- [ ] Acceptance criteria validated
- [ ] PROJECT_TRACKER.md updated
  - Pod disruption budgets
  - Liveness/readiness probes
- [ ] Security hardening
  - Network policies (Kubernetes)
  - Pod security policies
  - Secret rotation automation
  - Vulnerability scanning (Trivy)
- [ ] Observability
  - Centralized logging (ELK or Loki)
  - Distributed tracing (Jaeger or Zipkin)
  - Custom Grafana dashboards for services
  - Alerting on service health

**Acceptance Criteria:**
- All services survive pod restarts
- 99.9% uptime SLA met
- Security scan passes with zero high/critical vulnerabilities
- Backup/restore tested successfully

#### 4.4 Documentation & Training (Week 7-8)

**Deliverables:**
- [ ] Administrator guide
  - Deployment procedures
  - Troubleshooting common issues
  - Backup/restore procedures
- [ ] User guide
  - Dashboard creation
  - Alert rule setup
  - Incident management workflow
- [ ] API documentation
  - OpenAPI/Swagger specs for all services
  - Postman collections
- [ ] Training materials
  - Video tutorials
  - Interactive demos
- [ ] Runbook
  - On-call procedures
  - Incident response playbook
  - Escalation paths

**Acceptance Criteria:**
- All documentation published
- Training delivered to team
- Runbook tested in drill

---

## Key Decisions Log

| Date | Decision | Rationale | Alternatives Considered | Decision Maker |
|------|----------|-----------|-------------------------|----------------|
| 2025-12-22 | **Use React 18 instead of Vue 3** | 117+ jobs in Saudi Arabia, MENA developers fluent in React, easier hiring | Vue 3 (better performance but harder hiring), Angular 17 | User approval after market analysis |
| 2025-12-22 | **Use Apache ECharts instead of Recharts** | ECharts handles millions of points (WebGL), Recharts limited to ~10K. Critical for signals time-series. | Recharts (simpler but performance issues), Chart.js, Victory | Technical requirement |
| 2025-12-22 | **Use shadcn/ui (single UI library)** | Avoid hybrid compatibility issues. Single design system = consistent UX. | shadcn-vue + PrimeVue (hybrid, compatibility risk), Ant Design, Material-UI | User concern: "NO HYBRID" |
| 2025-12-22 | **Microservices from Day 1** | User requirement: "go with Microservice always and in all phases" | Modular monolith (recommended but rejected) | User directive |
| 2025-12-22 | **Use RabbitMQ for messaging** | User preference: "prefer rabbit MQ unless stability issue" | Spring @Async (simpler but no persistence), Kafka (overkill) | User directive |
| 2025-12-22 | **Use PVC-mounted MinIO for storage** | User requirement: "let us go now with mounted PVC in cluster" | AWS S3, Azure Blob Storage | User directive |
| 2025-12-22 | **PostgreSQL for all services** | Already deployed, ACID compliance, partitioning support for time-series | MongoDB (no ACID), Cassandra (overkill), InfluxDB (time-series only) | Existing infrastructure |
| 2025-12-22 | **JWT with RBAC for authentication** | Stateless, scalable, already implemented in Phase 0 | OAuth2, SAML (enterprise SSO for later) | Existing implementation |
| 2025-12-22 | **React Hook Form + Zod for forms** | Type-safe validation, reusable schemas, 2025 best practice | Formik (older), React Final Form | Performance + DX |
| 2025-12-22 | **TanStack Table for data tables** | Handles 10K+ rows with virtualization, headless (customizable) | PrimeVue DataTable (hybrid issue), Ag-Grid (paid) | Performance + no hybrid |
| 2025-12-22 | **Custom notification providers (not Twilio)** | User has existing SMS/IVR provider and middleware service to be introduced later. Use adapter pattern for integration. | Twilio, AWS SNS, generic third-party providers | User directive: "exclude twillo, i have my pwn provider and middle ware service" |

---

## Cross-References

### Technical Documentation

- **`services/loader/CLAUDE.md`** - Current loader service architecture, API reference, encryption guide
- **`DEPLOYMENT_SUMMARY.md`** - Kubernetes deployment status and procedures
- **`FINAL_DEPLOYMENT_CHECKLIST.md`** - Pre-deployment verification checklist
- **`FLYWAY_CONSOLIDATION.md`** - Database migration strategy
- **`PROMETHEUS_CONFIGURATION.md`** - Monitoring setup guide
- **`PRE_RESET_CHECKLIST.md`** - Disaster recovery procedures
- **`README.md`** - Project overview and quick start

### To Be Created

- **`docs/ARCHITECTURE.md`** - Detailed microservices architecture diagrams
- **`docs/API_REFERENCE.md`** - Unified API documentation for all services
- **`docs/DATABASE_SCHEMA.md`** - Complete PostgreSQL schema reference
- **`docs/RABBITMQ_EVENTS.md`** - Event schema and messaging patterns
- **`docs/DEPLOYMENT_GUIDE.md`** - Phase-by-phase deployment procedures
- **`docs/USER_GUIDE.md`** - End-user documentation
- **`docs/ADMIN_GUIDE.md`** - Administrator handbook
- **`docs/RUNBOOK.md`** - On-call procedures and troubleshooting

---

## Team & Deployment Info

### Team Structure (ACTUAL)

**Reality:** Solo Developer + AI Assistant (Claude Code)

| Role | Resource | Responsibilities |
|------|----------|------------------|
| **Developer (You)** | 1 person | All development, architecture decisions, deployment, testing |
| **AI Assistant (Claude Code)** | 24/7 available | Code generation, troubleshooting, documentation, architecture guidance, best practices |

**Working Model:**
- **You:** High-level decisions, business logic, testing, deployment, Git operations
- **Claude Code:** Code scaffolding, boilerplate generation, debugging assistance, documentation writing, architecture planning

**Sprint Adjustments for Solo Developer:**
- Original estimate: 2 weeks per sprint (team of 5-7)
- **Realistic solo estimate: 3-4 weeks per sprint**
- Focus on MVP (Minimum Viable Product) for each sprint
- Defer advanced features to later sprints
- Prioritize working functionality over perfection

**Recommended Approach:**
1. **80/20 Rule:** Implement 80% of value with 20% of features first
2. **Iterate:** Ship working version, gather feedback, improve
3. **Leverage existing:** Use libraries/frameworks instead of building from scratch
4. **Automation:** CI/CD, testing, deployment scripts (Claude Code helps build these)
5. **Documentation as you go:** PROJECT_TRACKER.md + code comments

### Deployment Environment

**Current Environment:**
- **Cluster:** Bare-metal Kubernetes (on-prem)
- **Nodes:** (to be documented)
- **PostgreSQL:** Deployed
- **Prometheus:** Deployed
- **Sealed Secrets:** Deployed

**To Be Deployed:**
- [ ] Spring Cloud Gateway
- [ ] RabbitMQ
- [ ] Redis (if not already deployed)
- [ ] MinIO (PVC-mounted)
- [ ] 6 microservices
- [ ] React frontend (NGINX ingress)

### Current Git Status

```
Current branch: main

Staged changes:
A  .idea/.gitignore
AM .idea/dataSources.xml
M  .idea/stat.log
M  services/dataGenerator/target/data-generator-0.0.1-SNAPSHOT.jar
M  services/dataGenerator/target/data-generator-0.0.1-SNAPSHOT.jar.original
A  services/etl_initializer/test-data/etl-data-v7.yaml
M  services/loader/k8s_manifist/loader-deployment.yaml
M  services/loader/src/main/java/.../DbConnectivityRunner.java
M  services/loader/src/main/java/.../DevDataLoader.java
M  services/loader/src/main/resources/application-prod.yaml
M  services/secrets/app-secrets-sealed.yaml

Untracked files:
?? services/etl_initializer/ (entire service)
?? services/loader/.gitignore
?? services/loader/k8s_manifist/loader-secrets-template.yaml
?? services/loader/src/main/resources/schema-prod.sql
```

**Recent Commits:**
- `3bb6a23` add application services
- `5fc8377` add application services
- `e7d8479` restructure project
- `f83c36d` rename name space nad files

---

## Phase Status Summary

| Phase | Status | Start Date | Target End Date | Completion % | Blockers |
|-------|--------|------------|-----------------|--------------|----------|
| **Phase 0: Foundation** | ✅ COMPLETE | - | 2025-12-22 | 100% | None |
| **POC Stage 1: Functional UI** | 🔵 READY TO START | 2025-12-23 | 2026-01-06 | 0% | None |
| **POC Stage 2: Login + Security** | ⏸️ PENDING | 2026-01-06 | 2026-01-13 | 0% | Stage 1 complete |
| **Sprint 1: Infrastructure + Viz** | ⏸️ PENDING | TBD | TBD | 0% | POC approval |
| **Sprint 2+: Features** | ⏸️ PENDING | TBD | TBD | 0% | Sprint 1 complete |

---

## Next Steps (POC - Loader GUI)

**Objective:** Build POC for manager demo in 2-3 weeks

**Strategy:** Show functional value fast (Stage 1), add security after (Stage 2)

---

### Week 1: Repository Cleanup + Start Stage 1

**Day 1 Morning: Clean Repository (30 minutes)**
```bash
# Execute these commands
cd /Volumes/Files/Projects/newLoader

# 1. Remove .DS_Store files
find . -name ".DS_Store" -not -path "*/backup/*" -delete
echo ".DS_Store" >> .gitignore

# 2. Add untracked gitignore files
git add services/loader/.gitignore
git add services/dataGenerator/.idea/.gitignore

# 3. Commit cleanup
git add .gitignore
git commit -m "Clean repository: remove .DS_Store, add gitignore files"

# 4. Push to origin
git push origin main
```

**Day 1 Afternoon - Day 3: React Project Setup**

**Tell Claude Code:**
```
"I need to create a React 18 project with TypeScript and Vite for a loader management UI.

Requirements:
- React 18 + TypeScript + Vite
- Install shadcn/ui with Tailwind CSS
- Install TanStack Table v8 + TanStack Query v5
- Install React Router v6
- Install React Hook Form + Zod for forms
- Install axios for API calls

Project structure:
/src
  /api - API client and hooks
  /components - Reusable components
  /pages - Page components
  /lib - Utilities
  /types - TypeScript types

Configure:
- Tailwind with shadcn/ui theme
- TypeScript strict mode
- Vite dev server on port 5173
- API base URL: http://localhost:8080 (loader-service)

Generate:
- Complete package.json with all dependencies
- vite.config.ts
- tsconfig.json
- tailwind.config.ts
- src/main.tsx (entry point)
- src/App.tsx (main app with router)
- Basic folder structure
"
```

**Claude Code will generate the complete project structure. You:**
1. Copy files to `/Volumes/Files/Projects/newLoader/frontend/` directory
2. Run `npm install`
3. Run `npm run dev`
4. Verify dev server runs on http://localhost:5173

**Days 4-5: Loaders List Page**

**Tell Claude Code:**
```
"Create a loaders list page component using TanStack Table and shadcn/ui.

API Endpoint: GET http://localhost:8080/api/v1/res/loaders/loaders
Response format: Array of loader objects with fields:
- loaderCode (string)
- sourceDatabase (object with host, port, dbName, type)
- status (enum: ACTIVE, PAUSED, FAILED)
- lastRun (timestamp)
- intervalSeconds (number)
- maxParallelism (number)

Component requirements:
- File: src/pages/LoadersListPage.tsx
- Use TanStack Table for data display
- Columns: Loader Code, Source DB (host:port/dbname), Status (badge), Last Run, Interval, Actions
- Search input (debounced) that filters by loader code
- Status filter dropdown (All, ACTIVE, PAUSED, FAILED)
- Pagination (10, 25, 50, 100 per page)
- Sort by: code, lastRun, intervalSeconds
- Actions column: Edit (icon button), Delete (icon button with confirmation)
- "Create New Loader" button (top-right)
- Use shadcn/ui: Table, Button, Badge, Input, Select components
- Use TanStack Query for data fetching with loading/error states
- TypeScript with proper types

Also generate:
- src/api/loaders.ts - API functions (getLoaders, deleteLoader)
- src/types/loader.ts - TypeScript types
- src/hooks/useLoaders.ts - TanStack Query hook
"
```

**Claude Code will generate all files. You:**
1. Copy files to project
2. Test page at http://localhost:5173/loaders
3. Fix CORS errors (Claude Code will help if needed)

**Days 6-7: Loader Details Page**

**Tell Claude Code:**
```
"Create a loader details page with tabs for configuration, execution history, and signals.

API Endpoints:
- GET /api/v1/res/loaders/{code} - Get loader details
- GET /api/v1/res/loaders/{code}/history - Get execution history (you may need to create this endpoint)

Component requirements:
- File: src/pages/LoaderDetailsPage.tsx
- Use React Router params to get loader code
- Use shadcn/ui Tabs component with 3 tabs:
  1. Configuration tab:
     - Display loader metadata in cards
     - Source database info (read-only)
     - SQL query in Monaco editor (read-only, syntax highlighting)
     - Execution settings (interval, parallelism, fetch size)
     - Segments as badges
  2. Execution History tab:
     - TanStack Table with last 50 runs
     - Columns: Timestamp, Status, Records, Duration, Error
     - Filter by status
  3. Signals tab:
     - Placeholder for now: "View signals in Signals Explorer"
- Header: Loader code + status badge + Edit/Delete buttons
- Use shadcn/ui: Tabs, Card, Badge, Button, Table
- TypeScript types for loader details

Also generate:
- src/api/loaders.ts - Add getLoaderDetails, getLoaderHistory
- src/types/loader.ts - Add LoaderDetails type
"
```

**Days 8-10: Create/Edit Loader Form**

**Tell Claude Code:**
```
"Create a loader editor dialog (modal) with form for creating/editing loaders.

API Endpoints:
- POST /api/v1/res/loaders - Create loader
- PUT /api/v1/res/loaders/{code} - Update loader
- GET /api/v1/admin/res/db-sources - Get source databases for dropdown

Component requirements:
- File: src/components/LoaderEditorDialog.tsx
- Use shadcn/ui Dialog component
- Use React Hook Form + Zod for validation
- Form fields:
  - Loader Code (text input, required, max 64 chars, alphanumeric + underscore)
  - Source Database (select dropdown from API)
  - Loader SQL (Monaco editor or textarea, required, must start with SELECT)
  - Interval (number input + unit selector: minutes/hours/days)
  - Max Parallelism (number input, default 1, min 1, max 10)
  - Fetch Size (number input, default 1000, min 100)
  - Segments (multi-select, fetch from API if available, or text input)
  - Purge Strategy (select: NONE, OLD_RUNS, ALL)
- Validation:
  - Show inline errors
  - Disable submit while validating
- UX:
  - Loading spinner during submit
  - Success toast notification (use shadcn/ui Toast)
  - Close dialog on success
  - Refresh parent component data
- TypeScript with Zod schema

Also generate:
- src/api/loaders.ts - Add createLoader, updateLoader
- src/api/sources.ts - Add getSources
- src/schemas/loader.ts - Zod validation schema
"
```

**Day 11: Source Databases Page**

**Tell Claude Code:**
```
"Create a source databases page to display all configured data sources.

API Endpoint: GET /api/v1/admin/res/db-sources

Component requirements:
- File: src/pages/SourceDatabasesPage.tsx
- Use TanStack Table
- Columns: Code, Host, Port, Database, Type, Username, Read-Only Status
- Password column: show as ******** (never decrypt in UI)
- Read-Only Status: Badge (✅ Verified or ❌ Failed)
- No create/edit for now (defer to later)
- Use shadcn/ui: Table, Badge, Card

Also generate:
- src/api/sources.ts - getSources function
- src/types/source.ts - TypeScript types
"
```

**Day 12: CORS Configuration**

**If you get CORS errors, tell Claude Code:**
```
"I'm getting CORS errors when calling loader-service APIs from React app running on http://localhost:5173.

Error: "No 'Access-Control-Allow-Origin' header is present on the requested resource"

Backend: Spring Boot 3.5.6 (loader-service)
Frontend: React on http://localhost:5173

Generate Spring Boot CORS configuration class to allow:
- Origins: http://localhost:5173, http://localhost:3000
- Methods: GET, POST, PUT, DELETE, OPTIONS
- Headers: * (all)
- Credentials: true
"
```

**Claude Code will provide WebConfig.java. You:**
1. Add to `services/loader/src/main/java/.../config/WebConfig.java`
2. Rebuild: `mvn clean package`
3. Restart loader-service
4. Test React app again

**Days 13-14: Testing + Bug Fixes**

**Test all flows:**
- ✅ View loaders list
- ✅ Search loaders
- ✅ Filter by status
- ✅ Create new loader
- ✅ Edit existing loader
- ✅ Delete loader (with confirmation)
- ✅ View loader details (all 3 tabs)
- ✅ View source databases

**For each bug, tell Claude Code:**
```
"Bug: [describe what's not working]
Expected: [what should happen]
Actual: [what actually happens]
Error message: [paste error from console]
"
```

**Claude Code will debug and provide fixes.**

---

### Week 2 End: Stage 1 Demo to Manager

**What to show:**
1. Open http://localhost:5173/loaders
2. Show loaders list with search/filter
3. Create a new loader (live)
4. Edit the loader
5. View loader details
6. Delete the loader
7. Show source databases page

**What to say:**
- "This is a working UI for managing loaders - replaces database queries"
- "It's functional but not secured yet - we'll add login in Stage 2"
- "Built in 2 weeks using modern React stack with AI assistance"

**Manager should see:**
✅ Professional-looking UI
✅ Fast, responsive
✅ Working CRUD operations
✅ Real data from database

---

### Week 3: Stage 2 (Login + Access Control)

**Days 1-2: Authentication**

**Tell Claude Code:**
```
"Add JWT authentication to the React app.

Backend API: POST /api/v1/auth/login
Request: {"username": "admin", "password": "admin123"}
Response: {"token": "jwt_token", "username": "admin", "roles": ["ROLE_ADMIN"]}

Requirements:
1. Login page (src/pages/LoginPage.tsx):
   - Form with username + password (React Hook Form + Zod)
   - Call /api/v1/auth/login
   - Store JWT token in localStorage
   - Redirect to /loaders on success
   - Show error message on failure

2. Auth context (src/contexts/AuthContext.tsx):
   - Store user, token, roles in context
   - Provide useAuth() hook with: login, logout, isAuthenticated, hasRole
   - Load token from localStorage on app start

3. Protected routes:
   - Wrap routes with ProtectedRoute component
   - Redirect to /login if not authenticated

4. Axios interceptor:
   - Attach token to all API requests (Authorization: Bearer {token})
   - Handle 401 responses (logout + redirect to /login)

5. Update App.tsx routing to use ProtectedRoute

Generate all necessary files with TypeScript.
"
```

**Days 3-4: Role-Based Access Control**

**Tell Claude Code:**
```
"Implement role-based access control (RBAC) in the UI.

Roles:
- ROLE_ADMIN: Full access (create, edit, delete)
- ROLE_OPERATOR: Read + pause/resume
- ROLE_VIEWER: Read-only

Requirements:
1. Conditional rendering based on role:
   - Hide "Create New Loader" button for VIEWER/OPERATOR
   - Hide Edit/Delete buttons for VIEWER
   - Show Pause/Resume for OPERATOR

2. User profile dropdown (top-right):
   - Show username
   - Show role badge
   - Logout button
   - Avatar with initials

3. Update components:
   - LoadersListPage: conditional Create button
   - LoaderDetailsPage: conditional Edit/Delete buttons
   - Add useAuth hook usage

Generate updated components with RBAC logic.
"
```

**Day 5: App Shell & Layout**

**Tell Claude Code:**
```
"Create an app shell layout with sidebar navigation.

Requirements:
- File: src/components/AppLayout.tsx
- Sidebar with navigation items:
  - Home (placeholder)
  - Loaders (/loaders)
  - Sources (/sources)
  - Signals (placeholder)
  - Settings (placeholder)
- Header with:
  - Breadcrumbs
  - User profile dropdown (right side)
- Main content area
- Responsive (collapse sidebar on mobile)
- Use shadcn/ui components

Update App.tsx to use AppLayout wrapper for protected routes.
"
```

**Days 6-7: Testing + Polish**

**Test with 3 user accounts:**

```bash
# In loader-service, users are:
# admin/admin123 (ROLE_ADMIN)
# operator/operator123 (ROLE_OPERATOR)
# viewer/viewer123 (ROLE_VIEWER)
```

**Test scenarios:**
1. Login as VIEWER → no Create/Edit/Delete buttons ✅
2. Login as OPERATOR → can Pause/Resume, no Delete ✅
3. Login as ADMIN → full access ✅
4. Logout → redirected to login ✅
5. Token expiry → redirected to login ✅
6. Direct URL access when not logged in → redirected ✅

**Fix any RBAC bugs with Claude Code's help.**

---

### Week 3 End: Final Manager Demo

**What to show:**
1. Login screen
2. Login as VIEWER → show limited access
3. Logout
4. Login as ADMIN → show full access
5. All CRUD operations
6. User profile with role display

**What to say:**
- "Now the UI is secured with enterprise-grade authentication"
- "Role-based access control ensures users only see what they're allowed to"
- "JWT tokens with 24-hour expiry"
- "This is production-ready for the loader management module"

**Manager should approve:**
✅ POC demonstrates value
✅ Modern, professional UI
✅ Secure with RBAC
✅ Fast delivery (3 weeks total)

---

## After POC Approval

**Next priorities (from manager):**
1. ✅ Charts/Visualization (show signal data trends)
2. ✅ Alerting (replace existing solution)
3. ✅ Deploy to production (add Gateway, SSL, Redis)
4. ⏸️ Incidents, Jira integration (later)

**Adjust PROJECT_TRACKER.md based on manager feedback.**

---
## Success Metrics (KPIs)

### Phase 1 Success Criteria
- [ ] User can log in and view dashboard within 2 seconds
- [ ] Dashboard can display 10+ charts without performance issues
- [ ] Chart updates every 30s with fresh data
- [ ] System handles 100 concurrent users (load test)
- [ ] API p95 latency <500ms

### Phase 2 Success Criteria
- [ ] Alert rules evaluate every 30s with <5s execution time
- [ ] Notifications delivered within 30s of alert trigger
- [ ] Escalation ladder works correctly (Email → SMS → IVR)
- [ ] False positive rate reduced by 50% vs simple threshold alerts
- [ ] System handles 1,000 alerts/hour

### Phase 3 Success Criteria
- [ ] Incidents created from grouped alerts within 1 minute
- [ ] Jira ticket created within 30s of incident creation
- [ ] Bi-directional sync works without data loss
- [ ] RCA PDF generated within 10s
- [ ] Incident timeline tracks all changes accurately

### Phase 4 Success Criteria
- [ ] 99.9% uptime SLA achieved
- [ ] All services survive pod restarts with zero downtime
- [ ] Backup/restore tested successfully
- [ ] Security scan passes with zero critical vulnerabilities
- [ ] Documentation complete and training delivered

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-12-22 | Claude Code | Initial creation - comprehensive project tracker with 4 phases |

---

**End of Document**

For questions or updates, refer to this document in all cross-session handovers.
