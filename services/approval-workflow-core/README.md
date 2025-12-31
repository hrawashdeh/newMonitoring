# Approval Workflow Core Library

**Version:** 1.0.0
**Purpose:** Shared library for draft/active/archive versioning system across all monitoring services

---

## Overview

This library provides a standardized approval workflow for entities that require version control and admin approval:
- ✅ Loaders (ETL jobs)
- ✅ Dashboards (future)
- ✅ Incidents (future)
- ✅ Charts (future)

---

## Architecture

**Pattern:** Template Method + Strategy Pattern

```
WorkflowEntity (interface)
        ↑
        │ implements
        │
   Loader, Dashboard, etc.
        ↑
        │ passed to
        │
AbstractDraftService<T>
        ↑
        │ extends
        │
LoaderDraftService (concrete)
```

---

## Usage

### Step 1: Implement WorkflowEntity

```java
public class Loader implements WorkflowEntity {
    @Column(name = "loader_code")
    private String loaderCode;

    @Override
    public String getEntityCode() {
        return this.loaderCode;
    }

    // Implement all other WorkflowEntity methods
}
```

### Step 2: Extend Abstract Services

```java
@Service
public class LoaderDraftService extends AbstractDraftService<Loader> {
    private final LoaderRepository repository;

    @Override
    protected Loader findActiveByEntityCode(String code) {
        return repository.findActiveByLoaderCode(code).orElse(null);
    }

    // Implement other abstract methods
}
```

### Step 3: Use in Controllers

```java
@RestController
public class LoaderController {
    private final LoaderDraftService draftService;

    @PostMapping("/loaders")
    public Loader createLoader(@RequestBody LoaderDto dto, Authentication auth) {
        return draftService.createDraft(dto, auth.getName(), ChangeType.MANUAL_EDIT, null);
    }
}
```

---

## Components

### Domain
- `VersionStatus` - ACTIVE, DRAFT, PENDING_APPROVAL
- `ChangeType` - IMPORT_CREATE, IMPORT_UPDATE, MANUAL_EDIT, ROLLBACK
- `WorkflowEntity` - Interface for versioned entities (25+ methods)

### Services
- `AbstractDraftService` - Draft CRUD operations ✅
- `AbstractApprovalService` - Approve drafts ⏳ (to be created)
- `AbstractRejectionService` - Reject drafts ⏳
- `AbstractArchiveService` - Archival operations ⏳

---

## Building

```bash
cd /Volumes/Files/Projects/newLoader/services/approval-workflow-core
mvn clean install
```

This installs the library to your local Maven repository (`~/.m2/repository`).

---

## Importing in Services

```xml
<dependency>
    <groupId>com.tiqmo.monitoring</groupId>
    <artifactId>approval-workflow-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Next Steps for Full Implementation

### 1. Complete Remaining Abstract Services (30 min)

Create these files in `src/main/java/com/tiqmo/monitoring/workflow/service/`:

**AbstractApprovalService.java:**
- `approveDraft(draftId, admin, comments)` - PENDING → ACTIVE
- Calls `archiveOldActive()` before activating
- Sets `approvedByVersion`, `approvedAtVersion`

**AbstractArchiveService.java:**
- `archiveEntity(entity, archivedBy, reason)` - Move to archive table
- Abstract method: `saveToArchive(entity)` - Entity-specific

**AbstractRejectionService.java:**
- `rejectDraft(draftId, admin, reason)` - PENDING → ARCHIVED
- Sets rejection metadata
- Calls AbstractArchiveService

**AbstractRevocationService.java:**
- `revokeActive(entityCode, admin, reason)` - ACTIVE → ARCHIVED

### 2. Build Core Library (5 min)

```bash
mvn clean install
```

### 3. Update loader/pom.xml (2 min)

Add dependency on `approval-workflow-core:1.0.0`

### 4. Update Loader Entity (15 min)

- Delete old `VersionStatus.java` and `ChangeType.java` from loader package
- Import from `com.tiqmo.monitoring.workflow.domain`
- Implement `WorkflowEntity` interface
- Add `getEntityCode()` method

### 5. Create LoaderArchive Entity (20 min)

JPA entity mirroring Loader structure + archive metadata.

### 6. Create Repositories (15 min)

- `LoaderArchiveRepository`
- Update `LoaderRepository` with versioning queries

### 7. Create Concrete Loader Services (45 min)

Extend abstract services from core library.

### 8. Update ETL Initializer (15 min)

Use `LoaderDraftService` instead of direct JPA save.

### 9. Create API Endpoints (60 min)

Update `LoaderController` with draft/approval endpoints.

---

## Design Principles

1. **Single Responsibility** - Each service has one clear purpose
2. **DRY** - Workflow logic in abstract classes, entity logic in concrete
3. **Type Safety** - Compile-time checking with generics
4. **Reusability** - Same code for all entities
5. **Testability** - Abstract services can be unit tested

---

## Benefits

✅ **Standardization** - Same workflow for all entities
✅ **Maintenance** - Change once, deploy everywhere
✅ **Quality** - Shared unit tests ensure correctness
✅ **Velocity** - New entities integrate in minutes

---

## Status

- ✅ Domain models (VersionStatus, ChangeType, WorkflowEntity)
- ✅ AbstractDraftService
- ⏳ Other abstract services (need implementation)
- ⏳ Unit tests
- ⏳ Documentation

---

**Last Updated:** 2025-12-30
**Author:** Hassan Rawashdeh
