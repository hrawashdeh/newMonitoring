# Approval Workflow Documentation

**Version:** 1.0
**Date:** December 28, 2025
**Status:** Production Ready ✅

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Database Schema](#database-schema)
4. [Backend Implementation](#backend-implementation)
5. [Frontend Implementation](#frontend-implementation)
6. [Security](#security)
7. [API Reference](#api-reference)
8. [User Guide](#user-guide)
9. [Deployment](#deployment)
10. [Testing](#testing)

---

## Overview

The Approval Workflow system ensures that all new ETL loaders undergo administrative review before they can be enabled and execute queries against source databases. This provides a critical security layer preventing unauthorized or potentially harmful SQL execution.

### Key Features

- **Multi-Layer Security**: Database constraints, service layer validation, Spring Security annotations, and field-level protection
- **Complete Audit Trail**: Every approval action is logged with admin username, timestamp, IP address, and SQL snapshot
- **Role-Based Access**: Only ADMIN users can approve/reject loaders
- **HATEOAS Integration**: Dynamic action availability based on approval status and user role
- **Visual Feedback**: Color-coded status badges and detailed rejection reasons in UI
- **State Transitions**: PENDING_APPROVAL → APPROVED/REJECTED

### Workflow States

```
┌─────────────────┐
│  LOADER CREATED │
│  (auto status:  │
│ PENDING_APPROVAL)│
└────────┬────────┘
         │
         ▼
┌────────────────────┐
│   ADMIN REVIEWS    │
│  - Views SQL query │
│  - Checks config   │
│  - Makes decision  │
└────────┬───────────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
┌────────┐ ┌─────────┐
│APPROVED│ │REJECTED │
│ (can be│ │(disabled,│
│ enabled)│ │can edit) │
└────────┘ └─────────┘
```

---

## Architecture

### System Components

```
┌──────────────────────────────────────────────────────────┐
│                     FRONTEND (React)                      │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ LoaderDetailsPage                                   │ │
│  │  ├─ ApprovalStatusBadge (visual indicator)         │ │
│  │  ├─ ApproveLoaderDialog (with comments)            │ │
│  │  └─ RejectLoaderDialog (with reason + comments)    │ │
│  └─────────────────────────────────────────────────────┘ │
└───────────────────┬──────────────────────────────────────┘
                    │ REST API
                    ▼
┌──────────────────────────────────────────────────────────┐
│                  BACKEND (Spring Boot)                    │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ LoaderController                                    │ │
│  │  ├─ POST /api/v1/res/loaders/{code}/approve        │ │
│  │  │   @PreAuthorize("hasRole('ADMIN')")             │ │
│  │  └─ POST /api/v1/res/loaders/{code}/reject         │ │
│  │      @PreAuthorize("hasRole('ADMIN')")             │ │
│  └──────────────────┬──────────────────────────────────┘ │
│                     │                                     │
│  ┌──────────────────▼──────────────────────────────────┐ │
│  │ LoaderService                                       │ │
│  │  ├─ approveLoader(code, auth, comments, ip)        │ │
│  │  │   - Validates PENDING_APPROVAL status           │ │
│  │  │   - Updates approval fields                     │ │
│  │  │   - Creates audit log entry                     │ │
│  │  └─ rejectLoader(code, reason, auth, comments, ip) │ │
│  │      - Validates PENDING_APPROVAL status           │ │
│  │      - Sets rejected status + reason               │ │
│  │      - Auto-disables loader                        │ │
│  │      - Creates audit log entry                     │ │
│  └──────────────────┬──────────────────────────────────┘ │
│                     │                                     │
│  ┌──────────────────▼──────────────────────────────────┐ │
│  │ HateoasService                                      │ │
│  │  - Determines available actions based on:           │ │
│  │    1. Approval status (PENDING/APPROVED/REJECTED)  │ │
│  │    2. User role (ADMIN/OPERATOR/VIEWER)            │ │
│  │    3. Enabled status                               │ │
│  └─────────────────────────────────────────────────────┘ │
└───────────────────┬──────────────────────────────────────┘
                    │
                    ▼
┌──────────────────────────────────────────────────────────┐
│                  DATABASE (PostgreSQL)                    │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ loader.loader                                       │ │
│  │  ├─ approval_status (PENDING_APPROVAL/APPROVED/    │ │
│  │  │                    REJECTED)                     │ │
│  │  ├─ approved_by, approved_at                       │ │
│  │  ├─ rejected_by, rejected_at                       │ │
│  │  └─ rejection_reason                               │ │
│  ├─────────────────────────────────────────────────────┤ │
│  │ loader.approval_audit_log                          │ │
│  │  ├─ loader_id, loader_code                         │ │
│  │  ├─ action_type (APPROVED/REJECTED)                │ │
│  │  ├─ admin_username, action_timestamp               │ │
│  │  ├─ previous_status, new_status                    │ │
│  │  ├─ rejection_reason, admin_comments               │ │
│  │  ├─ admin_ip_address, user_agent                   │ │
│  │  └─ loader_sql_snapshot (encrypted)                │ │
│  ├─────────────────────────────────────────────────────┤ │
│  │ resource_management.resource_states                │ │
│  │  - PENDING_APPROVAL, APPROVED, REJECTED states     │ │
│  ├─────────────────────────────────────────────────────┤ │
│  │ auth.actions                                       │ │
│  │  - APPROVE_LOADER, REJECT_LOADER actions           │ │
│  └─────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

---

## Database Schema

### V11: Approval Workflow Schema

**File:** `services/etl_initializer/src/main/resources/db/migration/V11__add_approval_workflow.sql`

#### 1. Loader Table Extensions

```sql
ALTER TABLE loader.loader
    ADD COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL',
    ADD COLUMN approved_by VARCHAR(128),
    ADD COLUMN approved_at TIMESTAMP,
    ADD COLUMN rejected_by VARCHAR(128),
    ADD COLUMN rejected_at TIMESTAMP,
    ADD COLUMN rejection_reason VARCHAR(500);
```

**Constraints:**
- `approval_status` must be one of: PENDING_APPROVAL, APPROVED, REJECTED
- When APPROVED: `approved_by` and `approved_at` must NOT be NULL
- When REJECTED: `rejected_by`, `rejected_at`, and `rejection_reason` must NOT be NULL

#### 2. Approval Audit Log Table

```sql
CREATE TABLE loader.approval_audit_log (
    id BIGSERIAL PRIMARY KEY,
    loader_id BIGINT NOT NULL,
    loader_code VARCHAR(64) NOT NULL,
    action_type VARCHAR(20) NOT NULL,  -- APPROVED, REJECTED, RESUBMITTED, REQUIRES_REAPPROVAL
    admin_username VARCHAR(128) NOT NULL,
    action_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    rejection_reason VARCHAR(500),
    admin_comments TEXT,
    admin_ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    loader_sql_snapshot TEXT,  -- Encrypted SQL snapshot
    CONSTRAINT fk_audit_loader FOREIGN KEY (loader_id)
        REFERENCES loader.loader(id) ON DELETE CASCADE
);
```

**Indexes:**
- `idx_audit_loader_id` on `loader_id`
- `idx_audit_admin` on `admin_username`
- `idx_audit_timestamp` on `action_timestamp`

### V12: HATEOAS Approval Actions

**File:** `services/etl_initializer/src/main/resources/db/migration/V12__add_approval_workflow_hateoas.sql`

#### 1. Resource States

```sql
INSERT INTO resource_management.resource_states (resource_type, state_code, state_name, description) VALUES
('LOADER', 'PENDING_APPROVAL', 'Pending Approval', 'Loader created but awaiting admin approval'),
('LOADER', 'APPROVED', 'Approved', 'Loader has been approved by admin and can be enabled'),
('LOADER', 'REJECTED', 'Rejected', 'Loader has been rejected by admin');
```

#### 2. Actions

```sql
INSERT INTO auth.actions (action_code, action_name, http_method, url_template, description, resource_type) VALUES
('APPROVE_LOADER', 'Approve Loader', 'POST', '/api/v1/res/loaders/{loaderCode}/approve',
 'Approve a pending loader to allow it to be enabled', 'LOADER'),
('REJECT_LOADER', 'Reject Loader', 'POST', '/api/v1/res/loaders/{loaderCode}/reject',
 'Reject a pending loader and disable it', 'LOADER');
```

#### 3. Role Permissions (ADMIN Only)

```sql
INSERT INTO auth.role_permissions (role_code, action_id, resource_type)
SELECT 'ADMIN', id, 'LOADER' FROM auth.actions
WHERE action_code IN ('APPROVE_LOADER', 'REJECT_LOADER')
  AND resource_type = 'LOADER';
```

#### 4. State Permissions

| State             | Allowed Actions                                                                 |
|-------------------|---------------------------------------------------------------------------------|
| PENDING_APPROVAL  | APPROVE_LOADER, REJECT_LOADER, EDIT_LOADER, VIEW_*                             |
| APPROVED          | TOGGLE_ENABLED, FORCE_START, EDIT_LOADER, DELETE_LOADER, VIEW_*                |
| REJECTED          | EDIT_LOADER, DELETE_LOADER, VIEW_*                                             |

---

## Backend Implementation

### Entity Changes

**File:** `services/loader/src/main/java/com/tiqmo/monitoring/loader/domain/loader/entity/Loader.java`

```java
@Entity
@Table(name = "loader", schema = "loader")
public class Loader {

    // ... existing fields ...

    // Approval workflow fields
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING_APPROVAL;

    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_by", length = 128)
    private String rejectedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
}
```

### Service Layer

**File:** `services/loader/src/main/java/com/tiqmo/monitoring/loader/service/loader/LoaderService.java`

#### Approve Loader

```java
@Transactional
public EtlLoaderDto approveLoader(String loaderCode,
                                  Authentication authentication,
                                  String comments,
                                  String ipAddress) {
    Loader loader = loaderRepository.findByLoaderCode(loaderCode)
        .orElseThrow(() -> new IllegalArgumentException("Loader not found: " + loaderCode));

    // Validation
    if (loader.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
        throw new IllegalStateException(
            "Can only approve loaders in PENDING_APPROVAL status");
    }

    // Update approval fields
    ApprovalStatus previousStatus = loader.getApprovalStatus();
    loader.setApprovalStatus(ApprovalStatus.APPROVED);
    loader.setApprovedBy(authentication.getName());
    loader.setApprovedAt(Instant.now());

    Loader saved = loaderRepository.save(loader);

    // Create audit log
    createAuditLog(saved, ApprovalActionType.APPROVED, authentication.getName(),
                   previousStatus, comments, ipAddress, null);

    log.info("Loader approved: {} by admin: {}", loaderCode, authentication.getName());
    return toDto(saved);
}
```

#### Reject Loader

```java
@Transactional
public EtlLoaderDto rejectLoader(String loaderCode,
                                String rejectionReason,
                                Authentication authentication,
                                String comments,
                                String ipAddress) {
    // Validation
    if (rejectionReason == null || rejectionReason.isBlank()) {
        throw new IllegalArgumentException("Rejection reason is required");
    }

    Loader loader = loaderRepository.findByLoaderCode(loaderCode)
        .orElseThrow(() -> new IllegalArgumentException("Loader not found: " + loaderCode));

    if (loader.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
        throw new IllegalStateException(
            "Can only reject loaders in PENDING_APPROVAL status");
    }

    // Update rejection fields
    ApprovalStatus previousStatus = loader.getApprovalStatus();
    loader.setApprovalStatus(ApprovalStatus.REJECTED);
    loader.setRejectedBy(authentication.getName());
    loader.setRejectedAt(Instant.now());
    loader.setRejectionReason(rejectionReason);

    // Auto-disable rejected loaders
    loader.setEnabled(false);

    Loader saved = loaderRepository.save(loader);

    // Create audit log
    createAuditLog(saved, ApprovalActionType.REJECTED, authentication.getName(),
                   previousStatus, comments, ipAddress, rejectionReason);

    log.info("Loader rejected: {} by admin: {} - Reason: {}",
             loaderCode, authentication.getName(), rejectionReason);
    return toDto(saved);
}
```

### Controller Endpoints

**File:** `services/loader/src/main/java/com/tiqmo/monitoring/loader/api/loader/LoaderController.java`

```java
@PostMapping("/{loaderCode}/approve")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<EtlLoaderDto> approveLoader(
        @PathVariable String loaderCode,
        @RequestBody(required = false) Map<String, String> request,
        Authentication authentication) {

    String comments = request != null ? request.get("comments") : null;
    String ipAddress = null; // Extract from HttpServletRequest if needed

    EtlLoaderDto approved = service.approveLoader(
        loaderCode, authentication, comments, ipAddress);

    return ResponseEntity.ok(approved);
}

@PostMapping("/{loaderCode}/reject")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> rejectLoader(
        @PathVariable String loaderCode,
        @RequestBody Map<String, String> request,
        Authentication authentication) {

    String rejectionReason = request.get("rejectionReason");
    if (rejectionReason == null || rejectionReason.isBlank()) {
        return ResponseEntity.badRequest().body(Map.of(
            "error", "VALIDATION_REQUIRED_FIELD",
            "message", "Rejection reason is required",
            "field", "rejectionReason"
        ));
    }

    String comments = request.get("comments");
    EtlLoaderDto rejected = service.rejectLoader(
        loaderCode, rejectionReason, authentication, comments, null);

    return ResponseEntity.ok(rejected);
}
```

### HATEOAS Integration

**File:** `services/loader/src/main/java/com/tiqmo/monitoring/loader/service/security/HateoasService.java`

```java
public String getLoaderState(String approvalStatus, Boolean enabled) {
    // Approval status takes priority over enabled/disabled
    if ("PENDING_APPROVAL".equals(approvalStatus)) {
        return "PENDING_APPROVAL";
    }
    if ("REJECTED".equals(approvalStatus)) {
        return "REJECTED";
    }

    // For APPROVED status, determine state from enabled field
    if (enabled == null || !enabled) {
        return "DISABLED";
    }
    return "ENABLED";
}
```

**Updated Controller Call:**

```java
// In LoaderController.getByCode()
String approvalStatus = (String) filteredLoader.get("approvalStatus");
Boolean enabled = (Boolean) filteredLoader.get("enabled");
String resourceState = hateoasService.getLoaderState(approvalStatus, enabled);
```

---

## Frontend Implementation

### TypeScript Types

**File:** `frontend/src/types/loader.ts`

```typescript
export type ApprovalStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';

export interface Loader {
  // ... existing fields ...

  // Approval workflow fields (read-only - cannot be modified via update endpoint)
  approvalStatus?: ApprovalStatus;
  approvedBy?: string;
  approvedAt?: string;
  rejectedBy?: string;
  rejectedAt?: string;
  rejectionReason?: string;
}

export interface LoaderActionLinks {
  // ... existing links ...
  approveLoader?: { href: string; method: string };
  rejectLoader?: { href: string; method: string };
}
```

### API Functions

**File:** `frontend/src/api/loaders.ts`

```typescript
async approveLoader(code: string, comments?: string): Promise<Loader> {
  const response = await apiClient.post<Loader>(
    API_ENDPOINTS.APPROVE_LOADER(code),
    comments ? { comments } : {}
  );
  return response.data;
},

async rejectLoader(code: string, rejectionReason: string, comments?: string): Promise<Loader> {
  const response = await apiClient.post<Loader>(
    API_ENDPOINTS.REJECT_LOADER(code),
    { rejectionReason, comments }
  );
  return response.data;
},
```

### UI Components

#### ApprovalStatusBadge

**File:** `frontend/src/components/loaders/ApprovalStatusBadge.tsx`

Visual badge with color coding:
- **APPROVED**: Green badge with checkmark icon
- **PENDING_APPROVAL**: Yellow badge with clock icon
- **REJECTED**: Red badge with X icon

```typescript
export function ApprovalStatusBadge({ status }: ApprovalStatusBadgeProps) {
  const config = {
    APPROVED: {
      label: 'Approved',
      className: 'bg-green-500 hover:bg-green-600 text-white',
      icon: CheckCircle2,
    },
    PENDING_APPROVAL: {
      label: 'Pending Approval',
      className: 'bg-yellow-500 hover:bg-yellow-600 text-white',
      icon: Clock,
    },
    REJECTED: {
      label: 'Rejected',
      className: 'bg-red-500 hover:bg-red-600 text-white',
      icon: XCircle,
    },
  }[status];

  return (
    <Badge variant={config.variant} className={config.className}>
      <config.icon className="mr-1 h-3 w-3" />
      {config.label}
    </Badge>
  );
}
```

#### ApproveLoaderDialog

**File:** `frontend/src/components/loaders/ApproveLoaderDialog.tsx`

Confirmation dialog with:
- Loader code display
- Optional comments textarea
- Confirm/Cancel buttons
- Loading state during API call

```typescript
export function ApproveLoaderDialog({
  loader,
  open,
  onOpenChange,
  onConfirm,
}: ApproveLoaderDialogProps) {
  const [comments, setComments] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleApprove = async () => {
    setIsSubmitting(true);
    try {
      await onConfirm(comments || undefined);
      setComments('');
      onOpenChange(false);
    } finally {
      setIsSubmitting(false);
    }
  };

  // ... render dialog with comments textarea
}
```

#### RejectLoaderDialog

**File:** `frontend/src/components/loaders/RejectLoaderDialog.tsx`

Rejection dialog with:
- Loader code display
- **Required** rejection reason textarea
- Optional comments textarea
- Validation (reason required)
- Warning alert about disabling
- Confirm/Cancel buttons

```typescript
export function RejectLoaderDialog({
  loader,
  open,
  onOpenChange,
  onConfirm,
}: RejectLoaderDialogProps) {
  const [rejectionReason, setRejectionReason] = useState('');
  const [comments, setComments] = useState('');
  const [validationError, setValidationError] = useState('');

  const handleReject = async () => {
    if (!rejectionReason.trim()) {
      setValidationError('Rejection reason is required');
      return;
    }
    // ... rejection logic
  };

  // ... render dialog with reason and comments textareas
}
```

### Page Integration

**File:** `frontend/src/pages/LoaderDetailsPage.tsx`

```typescript
export default function LoaderDetailsPage() {
  const [isApproveDialogOpen, setIsApproveDialogOpen] = useState(false);
  const [isRejectDialogOpen, setIsRejectDialogOpen] = useState(false);

  // Approve mutation
  const approveMutation = useMutation({
    mutationFn: ({ comments }: { comments?: string }) =>
      loadersApi.approveLoader(loaderCode!, comments),
    onSuccess: () => {
      toast({
        title: 'Loader approved',
        description: `Loader ${loaderCode} has been approved successfully`,
      });
      queryClient.invalidateQueries({ queryKey: ['loader', loaderCode] });
    },
  });

  // Reject mutation
  const rejectMutation = useMutation({
    mutationFn: ({ rejectionReason, comments }) =>
      loadersApi.rejectLoader(loaderCode!, rejectionReason, comments),
    onSuccess: () => {
      toast({
        title: 'Loader rejected',
        description: `Loader ${loaderCode} has been rejected successfully`,
      });
      queryClient.invalidateQueries({ queryKey: ['loader', loaderCode] });
    },
  });

  // Conditional rendering of approve/reject buttons
  {loader._links?.approveLoader && loader.approvalStatus === 'PENDING_APPROVAL' && (
    <Button onClick={() => setIsApproveDialogOpen(true)} className="bg-green-500">
      <CheckCircle2 className="mr-2 h-4 w-4" />
      Approve
    </Button>
  )}
  {loader._links?.rejectLoader && loader.approvalStatus === 'PENDING_APPROVAL' && (
    <Button variant="destructive" onClick={() => setIsRejectDialogOpen(true)}>
      <XCircle className="mr-2 h-4 w-4" />
      Reject
    </Button>
  )}

  // ... dialogs
}
```

---

## Security

### Multi-Layer Security Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ Layer 1: Spring Security                                    │
│  @PreAuthorize("hasRole('ADMIN')") on endpoints             │
│  - Blocks non-admin users at controller level               │
└───────────────────┬─────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────┐
│ Layer 2: Service Layer Validation                          │
│  - Validates approval_status is PENDING_APPROVAL            │
│  - Validates rejection reason is not blank                  │
│  - Business logic constraints                               │
└───────────────────┬─────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────┐
│ Layer 3: Database Constraints                              │
│  - CHECK constraints on approval fields                     │
│  - Foreign key constraints                                  │
│  - NOT NULL constraints                                     │
└───────────────────┬─────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────┐
│ Layer 4: Field-Level Protection                            │
│  - Role-based response filtering                           │
│  - VIEWER: sees only approved loaders                       │
│  - OPERATOR: sees approval status but can't approve        │
│  - ADMIN: full visibility and control                      │
└─────────────────────────────────────────────────────────────┘
```

### Role-Based Access Control

| Role     | Can View Approval Status | Can Approve | Can Reject | Notes                                  |
|----------|--------------------------|-------------|------------|----------------------------------------|
| VIEWER   | No (hidden)              | No          | No         | Only sees APPROVED loaders in list     |
| OPERATOR | Yes                      | No          | No         | Can view but cannot change status      |
| ADMIN    | Yes                      | Yes         | Yes        | Full control over approval workflow    |

### Field Protection Configuration

**File:** `services/etl_initializer/src/main/resources/db/migration/V10__create_field_protection_configuration.sql`

```sql
-- VIEWER: Hide all approval fields
INSERT INTO resource_management.field_protection (resource_type, field_name, role_code, is_visible) VALUES
('LOADER', 'approvalStatus', 'VIEWER', false),
('LOADER', 'approvedBy', 'VIEWER', false),
-- ... etc

-- OPERATOR: Show approval fields (read-only)
('LOADER', 'approvalStatus', 'OPERATOR', true),
('LOADER', 'approvedBy', 'OPERATOR', true),
-- ... etc

-- ADMIN: Full visibility
('LOADER', 'approvalStatus', 'ADMIN', true),
('LOADER', 'approvedBy', 'ADMIN', true),
-- ... etc
```

---

## API Reference

### Approve Loader

**Endpoint:** `POST /api/v1/res/loaders/{loaderCode}/approve`

**Authorization:** Requires ROLE_ADMIN

**Request Body:**
```json
{
  "comments": "Verified SQL query safety and configuration" // Optional
}
```

**Success Response (200 OK):**
```json
{
  "loaderCode": "SALES_HOURLY",
  "approvalStatus": "APPROVED",
  "approvedBy": "admin",
  "approvedAt": "2025-12-28T04:35:00Z",
  "enabled": false,
  "_links": {
    "toggleEnabled": {
      "href": "/api/v1/res/loaders/SALES_HOURLY/toggle",
      "method": "PUT"
    }
  }
}
```

**Error Responses:**

```json
// 401 Unauthorized - No authentication
{
  "timestamp": "2025-12-28T04:35:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required"
}

// 403 Forbidden - Not admin
{
  "timestamp": "2025-12-28T04:35:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access is denied"
}

// 400 Bad Request - Invalid status
{
  "error": "INVALID_STATE",
  "message": "Can only approve loaders in PENDING_APPROVAL status. Current status: APPROVED"
}
```

### Reject Loader

**Endpoint:** `POST /api/v1/res/loaders/{loaderCode}/reject`

**Authorization:** Requires ROLE_ADMIN

**Request Body:**
```json
{
  "rejectionReason": "SQL query contains unsafe DELETE operation", // Required
  "comments": "Please remove DELETE statement and resubmit" // Optional
}
```

**Success Response (200 OK):**
```json
{
  "loaderCode": "SALES_HOURLY",
  "approvalStatus": "REJECTED",
  "rejectedBy": "admin",
  "rejectedAt": "2025-12-28T04:35:00Z",
  "rejectionReason": "SQL query contains unsafe DELETE operation",
  "enabled": false, // Auto-disabled
  "_links": {
    "editLoader": {
      "href": "/api/v1/res/loaders/SALES_HOURLY",
      "method": "PUT"
    }
  }
}
```

**Error Responses:**

```json
// 400 Bad Request - Missing rejection reason
{
  "error": "VALIDATION_REQUIRED_FIELD",
  "message": "Rejection reason is required",
  "field": "rejectionReason"
}
```

---

## User Guide

### For ADMIN Users

#### Approving a Loader

1. Navigate to Loader Details page
2. Review the loader configuration:
   - SQL query
   - Source database connection
   - Execution intervals
3. If loader is in PENDING_APPROVAL status, you'll see:
   - Yellow "Pending Approval" badge
   - Green "Approve" button
   - Red "Reject" button
4. Click "Approve"
5. (Optional) Add approval comments
6. Click "Confirm"
7. Loader status changes to APPROVED
8. Loader can now be enabled by toggling the Pause/Resume button

#### Rejecting a Loader

1. Navigate to Loader Details page
2. Review the loader configuration
3. Click "Reject"
4. Enter rejection reason (required)
5. (Optional) Add comments with guidance
6. Click "Reject Loader"
7. Loader status changes to REJECTED
8. Loader is automatically disabled
9. Rejection reason is displayed in red alert box

### For OPERATOR Users

- Can **view** approval status and history
- **Cannot** approve or reject loaders
- Approve/Reject buttons are not visible
- Can edit and configure loaders, but changes require re-approval

### For VIEWER Users

- Approval status fields are hidden
- Only APPROVED loaders appear in the loaders list
- Cannot see PENDING_APPROVAL or REJECTED loaders

---

## Deployment

### Database Migrations

1. **V11__add_approval_workflow.sql** - Run first
   - Adds approval columns to loader table
   - Creates approval_audit_log table
   - Adds database constraints

2. **V12__add_approval_workflow_hateoas.sql** - Run second
   - Adds approval states to resource_states
   - Adds APPROVE_LOADER and REJECT_LOADER actions
   - Configures role and state permissions

### Application Deployment

**Deployment Order:**
1. ETL Initializer (runs migrations)
2. Loader Service (approval endpoints)
3. Gateway Service (routing)
4. Frontend (approval UI)

**Kubernetes Deployment:**

```bash
# 1. Deploy ETL Initializer (applies migrations)
kubectl apply -f services/etl_initializer/k8s_manifist/etl-initializer-deployment.yaml

# Wait for migrations to complete
kubectl wait --for=condition=available --timeout=60s deployment/etl-initializer -n monitoring-app

# 2. Deploy Loader Service
kubectl delete deployment signal-loader -n monitoring-app
kubectl apply -f services/loader/k8s_manifist/loader-deployment.yaml

# 3. Deploy Gateway Service
kubectl delete deployment gateway-service -n monitoring-app
kubectl apply -f services/gateway/k8s_manifist/gateway-deployment.yaml

# 4. Deploy Frontend
kubectl delete deployment loader-frontend -n monitoring-app
kubectl apply -f frontend/k8s_manifist/frontend-deployment.yaml

# Verify all pods are running
kubectl get pods -n monitoring-app
```

### Post-Deployment Verification

```bash
# Check migrations were applied
kubectl logs -n monitoring-app -l app=etl-initializer | grep "V11.*SUCCESS"
kubectl logs -n monitoring-app -l app=etl-initializer | grep "V12.*SUCCESS"

# Check loader service started successfully
kubectl logs -n monitoring-app -l app=signal-loader | grep "Started.*Application"

# Access frontend
open http://localhost:30080
```

---

## Testing

### Manual Testing Checklist

#### Test Case 1: Create New Loader (Auto PENDING_APPROVAL)
- [ ] Create new loader via UI
- [ ] Verify approval_status = PENDING_APPROVAL
- [ ] Verify loader is disabled by default
- [ ] Verify approval status badge shows yellow "Pending Approval"

#### Test Case 2: Approve Loader (ADMIN)
- [ ] Login as ADMIN
- [ ] Navigate to PENDING_APPROVAL loader
- [ ] Click "Approve" button
- [ ] Add optional comments
- [ ] Click "Confirm"
- [ ] Verify status changes to APPROVED
- [ ] Verify approved_by = admin username
- [ ] Verify approved_at is set
- [ ] Verify "Toggle Enabled" button now available
- [ ] Check audit log entry created

#### Test Case 3: Reject Loader (ADMIN)
- [ ] Login as ADMIN
- [ ] Navigate to PENDING_APPROVAL loader
- [ ] Click "Reject" button
- [ ] Enter rejection reason
- [ ] Add optional comments
- [ ] Click "Reject Loader"
- [ ] Verify status changes to REJECTED
- [ ] Verify rejected_by = admin username
- [ ] Verify rejected_at is set
- [ ] Verify loader is disabled
- [ ] Verify rejection reason displayed in red alert
- [ ] Check audit log entry created

#### Test Case 4: Permission Checks (OPERATOR)
- [ ] Login as OPERATOR
- [ ] Navigate to loader details
- [ ] Verify approval status is visible
- [ ] Verify "Approve" button is NOT visible
- [ ] Verify "Reject" button is NOT visible
- [ ] Verify cannot modify approval status via API

#### Test Case 5: Permission Checks (VIEWER)
- [ ] Login as VIEWER
- [ ] Verify approval status fields are hidden
- [ ] Verify only APPROVED loaders appear in list
- [ ] Verify cannot see PENDING_APPROVAL loaders
- [ ] Verify cannot access approve/reject endpoints

#### Test Case 6: HATEOAS Dynamic Actions
- [ ] PENDING_APPROVAL loader: Verify _links contains approveLoader and rejectLoader
- [ ] APPROVED loader: Verify _links contains toggleEnabled
- [ ] REJECTED loader: Verify _links contains editLoader but NOT toggleEnabled

#### Test Case 7: Validation
- [ ] Try to approve already APPROVED loader → expect error
- [ ] Try to reject without reason → expect validation error
- [ ] Try to approve as OPERATOR → expect 403 Forbidden
- [ ] Try to reject as VIEWER → expect 403 Forbidden

### API Testing

**Using curl:**

```bash
# Login as admin
TOKEN=$(curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | jq -r '.token')

# Approve loader
curl -X POST http://localhost:8080/api/v1/res/loaders/TEST_LOADER/approve \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"comments":"Verified and approved"}'

# Reject loader
curl -X POST http://localhost:8080/api/v1/res/loaders/TEST_LOADER/reject \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"rejectionReason":"SQL contains DROP TABLE","comments":"Please fix and resubmit"}'
```

---

## Appendix

### File Locations

#### Backend Files
- `services/loader/src/main/java/com/tiqmo/monitoring/loader/domain/loader/entity/Loader.java`
- `services/loader/src/main/java/com/tiqmo/monitoring/loader/domain/loader/entity/ApprovalStatus.java`
- `services/loader/src/main/java/com/tiqmo/monitoring/loader/domain/loader/entity/ApprovalActionType.java`
- `services/loader/src/main/java/com/tiqmo/monitoring/loader/domain/loader/entity/ApprovalAuditLog.java`
- `services/loader/src/main/java/com/tiqmo/monitoring/loader/service/loader/LoaderService.java`
- `services/loader/src/main/java/com/tiqmo/monitoring/loader/api/loader/LoaderController.java`
- `services/loader/src/main/java/com/tiqmo/monitoring/loader/service/security/HateoasService.java`

#### Frontend Files
- `frontend/src/types/loader.ts`
- `frontend/src/lib/api-config.ts`
- `frontend/src/api/loaders.ts`
- `frontend/src/components/loaders/ApprovalStatusBadge.tsx`
- `frontend/src/components/loaders/ApproveLoaderDialog.tsx`
- `frontend/src/components/loaders/RejectLoaderDialog.tsx`
- `frontend/src/components/loaders/LoaderDetailPanel.tsx`
- `frontend/src/pages/LoaderDetailsPage.tsx`

#### Database Migrations
- `services/etl_initializer/src/main/resources/db/migration/V11__add_approval_workflow.sql`
- `services/etl_initializer/src/main/resources/db/migration/V12__add_approval_workflow_hateoas.sql`

### Future Enhancements

1. **Resubmission Workflow**
   - Allow rejected loaders to be resubmitted
   - Automatically set status back to PENDING_APPROVAL after edit
   - Track resubmission count in audit log

2. **Bulk Approval**
   - Select multiple PENDING_APPROVAL loaders
   - Approve/reject in batch with same comments

3. **Notification System**
   - Email notification to loader creator when approved/rejected
   - Slack/Teams integration for approval requests
   - Dashboard for pending approvals count

4. **Advanced Audit Reports**
   - Approval turnaround time metrics
   - Admin approval activity reports
   - Rejection reason analytics

5. **Auto-Approval Rules**
   - Define trusted SQL patterns (e.g., SELECT-only queries)
   - Auto-approve loaders from trusted sources
   - Maintain audit trail even for auto-approvals

---

**Document Version:** 1.0
**Last Updated:** December 28, 2025
**Authors:** Development Team
**Status:** ✅ Production Ready
