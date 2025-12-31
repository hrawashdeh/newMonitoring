# User Stories: Import/Export Features

## User Story 1: Export Loaders to Excel with Column Selection

### Story
As an authenticated user (ADMIN, OPERATOR, or VIEWER), I want to export loader configurations to an Excel file with customizable column selection so that I can share, analyze, or backup loader data while respecting field-level permissions.

### Current Implementation Status (reverse-documented)
- **PARTIALLY IMPLEMENTED** in the current codebase.
- Basic CSV export exists in `LoadersListPage.tsx:603-640` with hardcoded columns (Loader Code, Status, Min Interval, Max Interval, Max Parallel, Query Period).
- No column selection UI - exports fixed set of fields only.
- No protected field handling - exports all fields regardless of user role.
- Exports to CSV format, not Excel (.xlsx).
- No exclusion of `loadStatus` (IDLE) column.
- Toast notification on successful export.

### What exists today (closest behavior)
- User clicks "Export" button in loaders list page.
- System generates CSV file with 6 hardcoded columns for all loaders in current view.
- File downloaded as `loaders-YYYY-MM-DD.csv`.
- No modal/popup - direct download.

### Acceptance Criteria (target state)
1. **Modal Interface**:
   - Clicking "Export" button opens a modal with "Export" and "Import" tabs.
   - Export tab shows list of all available loader fields as checkboxes.
   - Checkboxes for columns currently visible in table view are pre-selected by default.
   - `loadStatus` (IDLE) field is excluded entirely from available columns.

2. **Protected Field Handling**:
   - Fields in `protectedFields` array (returned by API based on user role) are:
     - Either excluded from available columns, OR
     - Included in export with value "***protected***" if selected.
   - Protected fields are visually marked in the column selection list.

3. **Excel Generation**:
   - Export button generates Excel (.xlsx) file, not CSV.
   - File named `loaders-export-YYYY-MM-DD-HHmmss.xlsx`.
   - Includes only selected columns.
   - First row contains column headers (human-readable names).
   - Subsequent rows contain loader data.

4. **Data Filtering**:
   - Export respects current page filters (enabled/disabled, approval status, data source).
   - Only loaders currently visible in filtered view are exported.

5. **Error Handling**:
   - If no columns selected: show warning "Please select at least one column".
   - If no loaders to export: show warning "No loaders to export".
   - Toast notification on successful export with count: "Exported N loader(s) to Excel".

### Explicit Limitations (current state)
- CSV format only, no Excel support.
- No column selection - fixed 6 columns.
- No protected field handling - all data exported regardless of role.
- `loadStatus` field not explicitly excluded.
- No modal interface - direct download.

### Technical Requirements
- Frontend: Install `xlsx` library (SheetJS) for Excel generation ✓ (completed).
- Frontend: Create `ImportExportModal.tsx` component with tabbed interface.
- Frontend: Export tab with checkbox list of all Loader type fields.
- Frontend: Excel generation using `XLSX.utils.book_new()` and `XLSX.writeFile()`.
- Frontend: Protected field filtering based on `protectedFields` array from API.

---

## User Story 2: Import Loaders from Excel with Validation

### Story
As an ADMIN user, I want to import loader configurations from an Excel file so that I can bulk-create loaders for approval workflow, reducing manual data entry and enabling migration from legacy systems (deprecating dev ETL_initializer service).

### Current Implementation Status (reverse-documented)
- **NOT IMPLEMENTED** in the current codebase.
- No import functionality exists.
- Loader creation currently happens via:
  - Manual entry through `/loaders/new` page (NewLoaderPage.tsx).
  - Dev data seeding via `DevDataLoader.java` (dev profile only).
  - ETL_initializer service (creates loaders from YAML config).

### What exists today (closest behavior)
- User manually creates loaders one-by-one via "New Loader" form.
- Form validates inputs and submits to `POST /api/v1/res/loaders` endpoint.
- New loaders inherit approval status based on business rules (currently not enforced).

### Acceptance Criteria (target state)
1. **Modal Interface**:
   - Import tab in Import/Export modal shows file upload area.
   - Drag-and-drop or click-to-browse file selection.
   - Accepts only .xlsx files (reject .csv, .xls, etc.).
   - Shows file name and size after selection.

2. **Role-Based Access Control**:
   - Import tab only visible to users with ADMIN role.
   - Non-ADMIN users see message: "Import requires ADMIN privileges".
   - Backend endpoint `POST /api/v1/admin/loaders/import` requires `@PreAuthorize("hasRole('ADMIN')")`.

3. **Import Options**:
   - Text input for "Import Label" (tag for all imported loaders, e.g., "2024-12-Migration").
   - Checkbox: "Validate only (dry run)" - parse file and report errors without saving.
   - Import button disabled until file selected and label provided.

4. **Protected Field Handling**:
   - During import, skip/ignore fields that are protected for current user role.
   - Protected fields retain system defaults (e.g., null, 0, or server-assigned values).
   - Import summary shows: "X protected fields skipped".

5. **Business Rules for Imported Loaders**:
   - All imported loaders set to `approvalStatus = PENDING_APPROVAL`.
   - All imported loaders set to `enabled = false` (cannot execute until approved).
   - All imported loaders tagged with import label (new field: `importLabel`).
   - `createdBy` set to importing user's username.
   - `createdAt` set to current timestamp.

6. **Validation**:
   - Validate Excel structure: required columns present (loaderCode, loaderSql, sourceDatabaseId, etc.).
   - Validate data types: numeric fields are numbers, timestamps are valid ISO 8601, etc.
   - Validate business rules: loaderCode unique, sourceDatabaseId exists, etc.
   - Show detailed validation errors in modal: "Row 5: loaderCode cannot be empty", "Row 12: sourceDatabaseId 999 not found".

7. **File Storage (Persistent Volume Claim)**:
   - Uploaded Excel file stored in PVC mounted at `/app/imports`.
   - File renamed: `import-{timestamp}-{username}-{originalFilename}.xlsx`.
   - File retained for audit trail (not deleted after import).
   - PVC path stored in import audit log.

8. **Import Audit Trail**:
   - Create new table: `loader.import_audit_log` with fields:
     - `id` (BIGSERIAL PRIMARY KEY)
     - `file_name` (VARCHAR - original filename)
     - `file_path` (VARCHAR - PVC storage path)
     - `file_size_bytes` (BIGINT)
     - `import_label` (VARCHAR - user-provided tag)
     - `imported_by` (VARCHAR - username)
     - `imported_at` (TIMESTAMP - upload time)
     - `total_rows` (INT - rows in Excel file)
     - `success_count` (INT - loaders successfully created)
     - `failure_count` (INT - rows with validation errors)
     - `validation_errors` (TEXT - JSON array of errors)
     - `dry_run` (BOOLEAN - true if validate-only mode)
   - Record created for every import attempt (success or failure).

9. **Success Feedback**:
   - Show import summary modal:
     - "Successfully imported N loaders"
     - "M validation errors (see details below)"
     - List of created loader codes
     - Download link for error report (CSV with row number, field, error message)
   - Toast notification: "Import completed: N loaders created, M errors".

10. **Error Handling**:
    - Invalid file format: "Please upload an Excel (.xlsx) file".
    - Missing required columns: "Missing required column: loaderCode".
    - Validation errors: Show inline in summary with row numbers.
    - File upload failure: "Failed to upload file. Please try again.".
    - Duplicate loaderCode: "Row 8: Loader code 'LOADER_X' already exists".

### Explicit Limitations (current state)
- No import functionality.
- No import audit trail table.
- No `importLabel` field in Loader entity.
- No PVC for file storage.
- No role-based import access control.
- No batch loader creation endpoint.

### Technical Requirements
- **Backend**:
  - Create `POST /api/v1/admin/loaders/import` endpoint (ADMIN only).
  - Add Apache POI or similar library for Excel parsing (Spring Boot compatible).
  - Create `ImportAuditLog` entity and repository.
  - Create migration V14: `import_audit_log` table and `loader.import_label` column.
  - Implement file upload with MultipartFile to PVC storage.
  - Implement import business logic: parse Excel, validate, create loaders.

- **Infrastructure**:
  - Create PVC YAML: `loader-imports-pvc.yaml` (10Gi ReadWriteOnce).
  - Mount PVC to loader service deployment at `/app/imports`.
  - Update loader-deployment.yaml with volume and volumeMount.

- **Frontend**:
  - Create Import tab in `ImportExportModal.tsx`.
  - File upload component with drag-and-drop (use shadcn `Input type="file"` or react-dropzone).
  - Import options form (label, dry-run checkbox).
  - Import summary modal component.
  - Role check: hide Import tab if user role !== 'ADMIN'.

---

## User Story 3: View Import Audit Trail

### Story
As an ADMIN user, I want to view a history of all loader import operations so that I can track who imported what data, when, and troubleshoot any import issues.

### Current Implementation Status (reverse-documented)
- **NOT IMPLEMENTED** in the current codebase.
- No approval audit trail exists (`approval_audit_log` table created but not populated).
- No import audit trail exists.

### What exists today (closest behavior)
- Approval workflow has `approval_audit_log` table (V11 migration) with columns for tracking approve/reject/revoke actions.
- Table structure exists but no service layer writes to it.
- Loader entity has `createdBy`, `createdAt`, `updatedBy`, `updatedAt` fields for basic audit.

### Acceptance Criteria (target state)
1. **Audit Log Page**:
   - New page: `/admin/import-audit` (ADMIN only).
   - Table showing all import operations (similar to loaders list page).
   - Columns: Import Date, Imported By, File Name, Label, Total Rows, Success Count, Failure Count, Dry Run, Actions.

2. **Filtering**:
   - Filter by date range (from/to).
   - Filter by user (imported by).
   - Filter by label.
   - Filter by status (success, partial success, failed).

3. **Details View**:
   - Click row to expand: show validation errors as JSON.
   - "Download File" button: retrieve original Excel file from PVC.
   - "Download Error Report" button: generate CSV with row-by-row errors.

4. **Retention Policy**:
   - Import audit records never auto-deleted (manual cleanup by DBA only).
   - Uploaded Excel files retained indefinitely in PVC (manual cleanup if storage full).

### Explicit Limitations (current state)
- No import audit log table.
- No UI for viewing audit trail.
- No file download from PVC.

### Technical Requirements
- **Backend**:
  - `GET /api/v1/admin/loaders/import-audit` endpoint (ADMIN only).
  - `GET /api/v1/admin/loaders/import-audit/{id}/file` endpoint to download original file.
  - `GET /api/v1/admin/loaders/import-audit/{id}/errors` endpoint to download error report CSV.

- **Frontend**:
  - Create `ImportAuditPage.tsx` with table and filters.
  - Add route in App.tsx: `/admin/import-audit`.
  - Add navigation link in admin menu (if exists).

---

## User Story 4: Enforce Business Rules for Imported Loaders

### Story
As the system, when loaders are imported from Excel, I want to enforce business rules (PENDING_APPROVAL, disabled, labeled) so that imported loaders cannot execute until reviewed and approved by ADMIN.

### Current Implementation Status (reverse-documented)
- **PARTIALLY IMPLEMENTED** in the current codebase.
- Database constraint exists (V13 migration) preventing PENDING_APPROVAL loaders from being enabled.
- Approval workflow endpoints exist (`approveLoader`, `rejectLoader`) but do not write to audit log.
- No `importLabel` field in Loader entity.

### What exists today (closest behavior)
- Loaders manually created via UI default to `approvalStatus = PENDING_APPROVAL` (backend logic).
- Constraint `chk_approval_before_enable` enforces: `approval_status = 'PENDING_APPROVAL' AND enabled = false`.
- ADMIN can approve/reject loaders via dedicated endpoints.

### Acceptance Criteria (target state)
1. **Import Business Rules**:
   - All imported loaders created with:
     - `approvalStatus = PENDING_APPROVAL` (cannot be overridden by Excel data).
     - `enabled = false` (cannot be overridden by Excel data).
     - `importLabel = <user-provided-label>` (new field).
     - `createdBy = <importing-user>`.
     - `createdAt = <import-timestamp>`.

2. **Database Constraints**:
   - V13 constraint `chk_approval_before_enable` enforced (already exists).
   - Import endpoint must set `approvalStatus` and `enabled` explicitly, ignoring Excel values.

3. **Audit Trail Integration**:
   - Import operation logged in `import_audit_log` table.
   - Each created loader references import batch via `importLabel` (for bulk operations tracking).

4. **Validation**:
   - If Excel contains `approvalStatus = APPROVED`: log warning, override to `PENDING_APPROVAL`.
   - If Excel contains `enabled = true`: log warning, override to `false`.

### Explicit Limitations (current state)
- No `importLabel` field in Loader entity.
- No migration V14 to add `import_label` column.
- No enforcement of PENDING_APPROVAL + disabled during import (because import doesn't exist).

### Technical Requirements
- **Backend**:
  - Migration V14: Add `loader.import_label VARCHAR(255)`.
  - Update `Loader` entity: add `importLabel` field.
  - Import service: hard-code `approvalStatus = PENDING_APPROVAL` and `enabled = false`.
  - Log warnings if Excel contains conflicting values.

---

## User Story 5: Role-Based Access Control for Import/Export

### Story
As the system, I want to enforce role-based access control for import/export operations so that only authorized users can perform sensitive data operations.

### Current Implementation Status (reverse-documented)
- **PARTIALLY IMPLEMENTED** in the current codebase.
- Export currently has no role restrictions (any authenticated user can export).
- Import does not exist, so no role restrictions implemented.
- Backend uses `@PreAuthorize("hasRole('ADMIN')")` for approve/reject endpoints.
- HATEOAS `_links` control UI visibility of actions based on role.

### What exists today (closest behavior)
- JWT authentication with roles (ADMIN, OPERATOR, VIEWER).
- Role-based endpoint protection via `@PreAuthorize`.
- Protected fields filtering based on role (returned in `protectedFields` array).

### Acceptance Criteria (target state)
1. **Export Access**:
   - All authenticated users (ADMIN, OPERATOR, VIEWER) can export loaders.
   - Export respects protected fields based on user role.
   - No backend endpoint required (client-side Excel generation).

2. **Import Access**:
   - **ONLY ADMIN** users can import loaders.
   - Import tab hidden in UI if user role !== 'ADMIN'.
   - Backend endpoint `POST /api/v1/admin/loaders/import` requires `@PreAuthorize("hasRole('ADMIN')")`.
   - Non-ADMIN attempt returns 403 Forbidden with message: "Import requires ADMIN privileges".

3. **Audit Trail Access**:
   - **ONLY ADMIN** users can view import audit trail.
   - Route `/admin/import-audit` protected (redirect to login if not ADMIN).
   - Backend endpoint `GET /api/v1/admin/loaders/import-audit` requires `@PreAuthorize("hasRole('ADMIN')")`.

4. **UI Role Checks**:
   - Import tab in modal:
     ```typescript
     const userRole = localStorage.getItem('auth_role');
     const canImport = userRole === 'ADMIN';
     ```
   - If `!canImport`: show message "Import requires ADMIN privileges" instead of upload form.

### Explicit Limitations (current state)
- No import functionality, so no import role restrictions.
- Export has no explicit role restrictions (implicitly allowed for all).

### Technical Requirements
- **Backend**:
  - Import endpoint: `@PreAuthorize("hasRole('ADMIN')")`.
  - Import audit endpoint: `@PreAuthorize("hasRole('ADMIN')")`.

- **Frontend**:
  - Role check in ImportExportModal: hide/disable Import tab if not ADMIN.
  - Role check in routing: protect `/admin/import-audit` route.

---

## Summary Table: Feature Status

| Feature | Current Status | Target Status | Priority |
|---------|---------------|---------------|----------|
| Export to CSV with hardcoded columns | ✓ Implemented | Enhance to Excel with column selection | P0 |
| Export protected field handling | ✗ Not implemented | Implement filtering/"***protected***" | P0 |
| Export modal with column selection | ✗ Not implemented | Implement | P0 |
| Import from Excel | ✗ Not implemented | Implement | P1 |
| Import role-based access (ADMIN only) | N/A | Implement | P1 |
| Import protected field skipping | N/A | Implement | P1 |
| Import business rules (PENDING_APPROVAL, disabled, labeled) | Partial (constraint exists) | Implement | P1 |
| File storage in PVC | ✗ Not implemented | Implement | P1 |
| Import audit trail logging | ✗ Not implemented | Implement | P1 |
| Import audit trail UI | ✗ Not implemented | Implement | P2 |
| `importLabel` field in Loader entity | ✗ Not implemented | Implement | P1 |
| Migration V14 (import_audit_log, import_label) | ✗ Not implemented | Implement | P1 |

---

## Implementation Phases

### Phase 1: Export Enhancement (Frontend Only)
1. Create `ImportExportModal.tsx` component with Export tab.
2. Implement column selection checkboxes (default to current table columns).
3. Implement Excel generation using xlsx library.
4. Implement protected field filtering based on `protectedFields` array.
5. Exclude `loadStatus` field from available columns.
6. Update `LoadersListPage.tsx` to open modal instead of direct CSV download.
7. Test export with different roles (verify protected field handling).

### Phase 2: Import Backend Infrastructure
1. Create migration V14: `import_audit_log` table and `loader.import_label` column.
2. Create `ImportAuditLog` entity and repository.
3. Create PVC YAML and update loader deployment for volume mount.
4. Deploy PVC to Kubernetes cluster.
5. Test PVC mount: verify `/app/imports` directory writable.

### Phase 3: Import Backend Logic
1. Add Apache POI dependency for Excel parsing.
2. Create `POST /api/v1/admin/loaders/import` endpoint (ADMIN only).
3. Implement file upload to PVC storage.
4. Implement Excel parsing and validation logic.
5. Implement batch loader creation with business rules (PENDING_APPROVAL, disabled, labeled).
6. Implement import audit trail logging.
7. Test import endpoint: upload sample Excel, verify loaders created correctly.

### Phase 4: Import Frontend
1. Create Import tab in `ImportExportModal.tsx`.
2. Implement file upload UI with drag-and-drop.
3. Implement import options form (label, dry-run).
4. Implement import summary modal with validation errors.
5. Add role check: hide Import tab if not ADMIN.
6. Test import flow end-to-end.

### Phase 5: Import Audit Trail UI (Optional)
1. Create `ImportAuditPage.tsx`.
2. Add route and navigation link.
3. Implement table with filters (date, user, label).
4. Implement details view with validation errors.
5. Implement file download and error report download.
6. Test audit trail UI.

---

## Design Decisions (Answered)

1. **Excel Schema**: ✓ Provide downloadable Excel template with pre-defined column headers.

2. **Duplicate Handling**: ✓ Enhanced approach with versioning system:
   - Add "ImportAction" column in Excel: CREATE | UPDATE | SKIP
   - New table: `loader_version` to store drafts and version history
   - UPDATE action stores draft in `loader_version` table (not main `loader` table)
   - Draft approval moves version to main table
   - Only version definition columns (loaderCode, loaderSql, intervals, etc.)
   - Execution metrics NOT versioned (consecutiveZeroRecordRuns, etc.)
   - Error file generated for failed rows with error column appended

3. **Source Database Validation**: ✓ Reject the row if `sourceDatabaseId` doesn't exist.

4. **Encrypted Fields**: ✓ Plaintext in Excel, encrypt on import. Excel contains readable SQL.

5. **PVC Size**: 10Gi storage for imports - sufficient for ~100,000 files (100KB each = 10GB).

6. **Retention Policy**: Manual cleanup by DBA (no auto-deletion for audit compliance).

7. **Import Label Uniqueness**: Not enforced - allows multiple imports with same label.

8. **Dry-Run Mode**: Validate structure only (no temporary loaders created).

---

## User Story 6: Loader Versioning System with Draft Management

### Story
As an ADMIN user, when I import updates to existing loaders, I want changes stored as drafts in a versioning table so that I can review and approve changes before they take effect, maintaining a complete version history of loader configurations.

### Current Implementation Status (reverse-documented)
- **NOT IMPLEMENTED** in the current codebase.
- No versioning system exists for loaders.
- Updates to loaders via `PUT /api/v1/res/loaders/{code}` directly modify the main `loader` table.
- No draft storage or version history.

### What exists today (closest behavior)
- Loader entity has `updatedBy` and `updatedAt` fields for basic audit.
- Approval workflow tracks approve/reject actions (but audit table not populated).
- No rollback capability - once loader updated, previous version lost.

### Acceptance Criteria (target state)
1. **Versioning Table Schema**:
   - New table: `loader.loader_version` with fields:
     - `id` (BIGSERIAL PRIMARY KEY)
     - `loader_code` (VARCHAR - references loader.loader_code)
     - `version_number` (INT - incremental version per loader_code)
     - `version_status` (VARCHAR - DRAFT | APPROVED | REJECTED)
     - `change_type` (VARCHAR - IMPORT_UPDATE | MANUAL_EDIT | RESUBMIT)
     - **Definition columns** (versioned):
       - `loader_sql` (TEXT - encrypted)
       - `min_interval_seconds` (INT)
       - `max_interval_seconds` (INT)
       - `max_query_period_seconds` (INT)
       - `max_parallel_executions` (INT)
       - `purge_strategy` (VARCHAR)
       - `source_timezone_offset_hours` (INT)
       - `aggregation_period_seconds` (INT)
       - `source_database_id` (BIGINT)
     - **Audit columns**:
       - `created_by` (VARCHAR - who created this version)
       - `created_at` (TIMESTAMP - when version created)
       - `approved_by` (VARCHAR - who approved this version)
       - `approved_at` (TIMESTAMP - when version approved)
       - `rejected_by` (VARCHAR)
       - `rejected_at` (TIMESTAMP)
       - `rejection_reason` (TEXT)
     - **Metadata**:
       - `import_label` (VARCHAR - if from import operation)
       - `change_summary` (TEXT - description of changes)

2. **Import with UPDATE Action**:
   - Excel contains "ImportAction" column with values: CREATE | UPDATE | SKIP
   - If ImportAction = CREATE:
     - Create new loader in main `loader` table (existing behavior)
     - Set `approvalStatus = PENDING_APPROVAL`, `enabled = false`
   - If ImportAction = UPDATE:
     - Check if loader_code exists in main table
     - If exists: create new row in `loader_version` table with `version_status = DRAFT`
     - If not exists: reject row with error "Loader not found for UPDATE action"
     - Calculate version_number: `MAX(version_number) + 1` for this loader_code
     - Do NOT modify main `loader` table yet
   - If ImportAction = SKIP:
     - Skip row, log in import summary as "Skipped"

3. **Draft Approval Flow**:
   - New endpoint: `POST /api/v1/admin/loaders/{code}/versions/{versionId}/approve`
   - ADMIN reviews draft version (shows diff between current and draft)
   - On approval:
     - Copy definition columns from `loader_version` to main `loader` table
     - Update `loader_version.version_status = APPROVED`
     - Set `loader_version.approved_by` and `approved_at`
     - Keep execution metrics unchanged (consecutiveZeroRecordRuns, etc.)
     - Trigger approval audit log entry
   - New endpoint: `POST /api/v1/admin/loaders/{code}/versions/{versionId}/reject`
   - On rejection:
     - Update `loader_version.version_status = REJECTED`
     - Set `rejection_reason`, `rejected_by`, `rejected_at`
     - Draft remains in table (not deleted) for audit trail

4. **Version History UI**:
   - New page: `/loaders/{code}/versions` (ADMIN only)
   - Table showing all versions for a loader (DRAFT, APPROVED, REJECTED)
   - Columns: Version, Status, Change Type, Created By, Created At, Actions
   - Click version to view diff (side-by-side comparison)
   - Approve/Reject buttons for DRAFT versions

5. **Error File Generation (Reusable Module)**:
   - Create utility module: `ExcelErrorExporter`
   - When import has validation errors:
     - Generate new Excel file with same structure as input
     - Add "Error" column (last column) with error message
     - Include only failed rows (not successful rows)
     - Download as `import-errors-YYYY-MM-DD-HHmmss.xlsx`
   - Module reusable for other bulk operations (future features)

6. **Versioned Columns vs Execution Metrics**:
   - **Versioned** (stored in loader_version, affect behavior):
     - loaderSql, minIntervalSeconds, maxIntervalSeconds, maxQueryPeriodSeconds
     - maxParallelExecutions, purgeStrategy, sourceTimezoneOffsetHours
     - aggregationPeriodSeconds, sourceDatabaseId
   - **NOT versioned** (execution state, not drafted):
     - enabled (controlled by approval workflow)
     - consecutiveZeroRecordRuns (runtime metric)
     - approvalStatus (controlled by approval workflow)

7. **Concurrency Control**:
   - If multiple drafts exist for same loader_code, only most recent can be approved
   - Approving older draft: show warning "Newer draft exists (#N), approve anyway?"
   - Unique constraint: only one DRAFT per loader_code (reject new UPDATE if draft exists)

### Explicit Limitations (current state)
- No versioning table.
- No draft storage.
- No version history UI.
- Direct updates to main loader table (no review process).

### Technical Requirements
- **Backend**:
  - Migration V15: Create `loader_version` table with all definition columns.
  - Create `LoaderVersion` entity and repository.
  - Update import service: handle ImportAction column (CREATE/UPDATE/SKIP).
  - Create version approval endpoints.
  - Create `ExcelErrorExporter` utility module.

- **Frontend**:
  - Update Excel template: add "ImportAction" column with data validation (dropdown).
  - Create `LoaderVersionsPage.tsx` for version history.
  - Create version diff component (side-by-side comparison).
  - Create error file download in import summary modal.

---

## Updated Implementation Phases

### Phase 1: Export Enhancement (Frontend Only) - PRIORITY 1
1. Create `ImportExportModal.tsx` component with Export tab.
2. Implement column selection checkboxes (default to current table columns).
3. Implement Excel generation using xlsx library.
4. Implement protected field filtering based on `protectedFields` array.
5. Exclude `loadStatus` field from available columns.
6. Implement "Download Template" button (generates Excel with correct headers + ImportAction column).
7. Update `LoadersListPage.tsx` to open modal instead of direct CSV download.
8. Test export with different roles (verify protected field handling).

### Phase 2: Import Backend Infrastructure - PRIORITY 2
1. Create migration V14: `import_audit_log` table and `loader.import_label` column.
2. Create migration V15: `loader_version` table with all definition columns.
3. Create `ImportAuditLog` and `LoaderVersion` entities and repositories.
4. Create PVC YAML and update loader deployment for volume mount.
5. Deploy PVC to Kubernetes cluster.
6. Test PVC mount: verify `/app/imports` directory writable.

### Phase 3: Import Backend Logic - PRIORITY 3
1. Add Apache POI dependency for Excel parsing.
2. Create `ExcelErrorExporter` utility module (reusable).
3. Create `POST /api/v1/admin/loaders/import` endpoint (ADMIN only).
4. Implement file upload to PVC storage.
5. Implement Excel parsing with ImportAction column support.
6. Implement validation logic (sourceDatabaseId exists, required fields, etc.).
7. Implement CREATE action: batch loader creation with business rules.
8. Implement UPDATE action: create draft in `loader_version` table.
9. Implement SKIP action: log in import summary.
10. Implement import audit trail logging.
11. Implement error file generation (failed rows with error column).
12. Test import endpoint: upload sample Excel with CREATE/UPDATE/SKIP actions.

### Phase 4: Import Frontend - PRIORITY 4
1. Create Import tab in `ImportExportModal.tsx`.
2. Implement file upload UI with drag-and-drop.
3. Implement import options form (label, dry-run).
4. Implement import summary modal with validation errors.
5. Add error file download button (if errors exist).
6. Add role check: hide Import tab if not ADMIN.
7. Test import flow end-to-end.

### Phase 5: Versioning Backend - PRIORITY 5
1. Create `POST /api/v1/admin/loaders/{code}/versions/{versionId}/approve` endpoint.
2. Create `POST /api/v1/admin/loaders/{code}/versions/{versionId}/reject` endpoint.
3. Implement draft approval logic: copy definition columns to main table.
4. Implement concurrency control: one DRAFT per loader_code.
5. Test version approval: verify definition columns updated, execution metrics unchanged.

### Phase 6: Versioning Frontend - PRIORITY 6
1. Create `LoaderVersionsPage.tsx` with version history table.
2. Create version diff component (side-by-side comparison).
3. Add approve/reject actions for DRAFT versions.
4. Add route: `/loaders/{code}/versions`.
5. Add navigation link from loader details page.
6. Test versioning UI end-to-end.

### Phase 7: Import Audit Trail UI - PRIORITY 7 (Optional)
1. Create `ImportAuditPage.tsx`.
2. Add route and navigation link.
3. Implement table with filters (date, user, label).
4. Implement details view with validation errors.
5. Implement file download and error report download.
6. Test audit trail UI.