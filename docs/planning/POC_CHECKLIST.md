# POC CHECKLIST - Loader GUI for Manager Demo

**Project:** Enterprise Monitoring Platform - Loader Management UI
**Timeline:** 2-3 weeks (Stage 1: 2 weeks, Stage 2: 1 week)
**Team:** Solo Developer + Claude Code
**Target:** Manager demonstration and approval

---

## 🔒 CRITICAL RULES

### Protected Directories (READ ONLY - NEVER MODIFY)

```
/Volumes/Files/Projects/newLoaderBackup/  ← REFERENCE ONLY
```

**Purpose:** Verified, tested implementation - use for reference when in doubt

**Rule:** If unsure whether a file/service exists or what it contains:
1. Check backup directory first
2. Compare with work directory
3. Only modify work directory

### Work Directory (Safe to Modify)

```
/Volumes/Files/Projects/newLoader/  ← WORK HERE
```

**Safe to create/modify:**
- ✅ `/frontend/` - NEW (entire React app for POC)
- ✅ `/POC_CHECKLIST.md` - NEW (this file)
- ✅ `/PROJECT_TRACKER.md` - EXISTING (update progress)
- ✅ `.gitignore` - EXISTING (add frontend ignores)

**NO-TOUCH zones (unless explicitly required in checklist):**
- ❌ `/services/loader/` - EXISTING, TESTED (only add CORS if needed)
- ❌ `/services/etl_initializer/` - EXISTING, TESTED
- ❌ `/services/dataGenerator/` - EXISTING, TESTED
- ❌ `/services/gateway/` - EXISTING (not used in POC)
- ❌ `/infra/` - EXISTING, TESTED
- ❌ `/kube/` - EXISTING, TESTED

---

## 📋 POC STAGES OVERVIEW

### Stage 1: Functional Loader GUI (Days 1-14)
**Goal:** Working UI without authentication
**Demo:** Week 2 end - show manager functional UI

### Stage 2: Login + Access Control (Days 15-21)
**Goal:** Add JWT authentication and RBAC
**Demo:** Week 3 end - show manager secured UI with roles

---

## 🎯 STAGE 1: FUNCTIONAL LOADER GUI (14 Days)

### ✅ Day 0: Pre-Flight Checks (BEFORE Starting)

**Verify Existing Services:**

- [ ] **Backup directory exists:**
  ```bash
  ls -la /Volumes/Files/Projects/newLoaderBackup/
  ```
  Expected: See services/, infra/, PROJECT_TRACKER.md

- [ ] **loader-service is running:**
  ```bash
  # Check if pod is running
  kubectl get pods | grep loader

  # Test API endpoint
  curl http://localhost:8080/actuator/health
  ```
  Expected: `{"status":"UP"}`

- [ ] **PostgreSQL is accessible:**
  ```bash
  kubectl get pods | grep postgres
  ```
  Expected: postgres pod running

- [ ] **Check existing APIs (reference backup):**
  ```bash
  # List existing controllers
  find /Volumes/Files/Projects/newLoaderBackup/services/loader/src/main/java -name "*Controller.java"
  ```
  Expected: LoaderController.java, SignalsController.java, etc.

- [ ] **Verify API endpoints work:**
  ```bash
  # Test GET loaders (may require JWT in production, skip if 401)
  curl http://localhost:8080/api/v1/res/loaders/loaders
  ```
  Expected: JSON array of loaders OR 401 (auth required)

**Document Current State:**

- [ ] **Take snapshot of current services:**
  ```bash
  cd /Volumes/Files/Projects/newLoader
  git status > POC_START_GIT_STATUS.txt
  git log --oneline -5 >> POC_START_GIT_STATUS.txt
  ```

- [ ] **Note loader-service version:**
  ```bash
  grep "<version>" /Volumes/Files/Projects/newLoader/services/loader/pom.xml | head -1
  ```
  Record: _______________

---

### ✅ Day 1 Morning: Repository Cleanup (30 minutes)

**Clean .DS_Store files:**

- [ ] **Execute cleanup:**
  ```bash
  cd /Volumes/Files/Projects/newLoader

  # Remove .DS_Store files
  find . -name ".DS_Store" -not -path "*/backup/*" -delete

  # Verify removed
  find . -name ".DS_Store" | wc -l
  ```
  Expected: 0

- [ ] **Update .gitignore:**
  ```bash
  echo ".DS_Store" >> .gitignore
  echo "node_modules/" >> .gitignore
  echo "frontend/dist/" >> .gitignore
  echo "frontend/.env.local" >> .gitignore
  ```

- [ ] **Add untracked files:**
  ```bash
  git add services/loader/.gitignore
  git add services/dataGenerator/.idea/.gitignore
  ```

- [ ] **Commit cleanup:**
  ```bash
  git add .gitignore
  git commit -m "Clean repository: remove .DS_Store, update gitignore for frontend"
  git push origin main
  ```

**Verify:**
- [ ] Git status clean (no .DS_Store files)
- [ ] Pushed to origin successfully

---

### ✅ Day 1-3: React Project Setup

**Create frontend directory:**

- [ ] **Create directory structure:**
  ```bash
  cd /Volumes/Files/Projects/newLoader
  mkdir -p frontend
  cd frontend
  ```

**Generate React project (Claude Code will provide files):**

- [ ] **Ask Claude Code:**
  ```
  "Generate a complete React 18 + TypeScript + Vite project for loader management UI.

  Requirements:
  - React 18 + TypeScript + Vite
  - shadcn/ui with Tailwind CSS
  - TanStack Table v8 + TanStack Query v5
  - React Router v6
  - React Hook Form + Zod
  - axios for API calls
  - API base URL: http://localhost:8080

  Project structure:
  /src
    /api - API client
    /components - UI components
    /pages - Page components
    /lib - Utilities
    /types - TypeScript types
    /hooks - Custom hooks

  Generate complete setup:
  - package.json with all dependencies
  - vite.config.ts
  - tsconfig.json
  - tailwind.config.ts
  - src/main.tsx
  - src/App.tsx
  - Basic routing setup
  "
  ```

- [ ] **Copy generated files to `/Volumes/Files/Projects/newLoader/frontend/`**

- [ ] **Install dependencies:**
  ```bash
  cd /Volumes/Files/Projects/newLoader/frontend
  npm install
  ```

- [ ] **Verify dev server starts:**
  ```bash
  npm run dev
  ```
  Expected: Server running on http://localhost:5173

- [ ] **Test in browser:**
  - Open http://localhost:5173
  - Should see React app running (blank or welcome page)

- [ ] **Initialize shadcn/ui:**
  ```bash
  npx shadcn-ui@latest init
  ```
  Follow prompts, select defaults

- [ ] **Install core shadcn components:**
  ```bash
  npx shadcn-ui@latest add button
  npx shadcn-ui@latest add card
  npx shadcn-ui@latest add table
  npx shadcn-ui@latest add dialog
  npx shadcn-ui@latest add form
  npx shadcn-ui@latest add input
  npx shadcn-ui@latest add label
  npx shadcn-ui@latest add select
  npx shadcn-ui@latest add badge
  npx shadcn-ui@latest add toast
  ```

**Verify:**
- [ ] npm install completed without errors
- [ ] Dev server runs without errors
- [ ] Browser shows React app
- [ ] shadcn/ui components installed

**Commit:**
- [ ] **Git commit:**
  ```bash
  cd /Volumes/Files/Projects/newLoader
  git add frontend/
  git commit -m "POC Stage 1: Initialize React 18 + Vite + shadcn/ui project"
  git push origin main
  ```

---

### ✅ Day 4-5: Loaders List Page

**Before starting:**
- [ ] **Verify loader-service API endpoint exists (check backup):**
  ```bash
  grep -r "GET.*loaders/loaders" /Volumes/Files/Projects/newLoaderBackup/services/loader/src/main/java/
  ```
  Expected: Should find `@GetMapping("/loaders")` in LoaderController.java

- [ ] **Test API manually:**
  ```bash
  # Without auth (if allowed)
  curl http://localhost:8080/api/v1/res/loaders/loaders

  # With auth (if required) - skip for now, Stage 2
  ```

**Generate components (Claude Code):**

- [ ] **Ask Claude Code:**
  ```
  "Create a loaders list page using TanStack Table and shadcn/ui.

  API Endpoint: GET http://localhost:8080/api/v1/res/loaders/loaders

  Response format (verify from backend):
  [
    {
      "loaderCode": "string",
      "sourceDatabase": {
        "host": "string",
        "port": number,
        "dbName": "string",
        "type": "POSTGRESQL" | "MYSQL"
      },
      "status": "ACTIVE" | "PAUSED" | "FAILED",
      "lastRun": timestamp,
      "intervalSeconds": number,
      "maxParallelism": number
    }
  ]

  Component: src/pages/LoadersListPage.tsx
  Features:
  - TanStack Table with columns: Loader Code, Source DB, Status, Last Run, Interval, Actions
  - Search by loader code (debounced)
  - Filter by status dropdown
  - Pagination (10/25/50/100 per page)
  - Sort by code, lastRun, interval
  - Actions: Edit button, Delete button with confirmation
  - "Create New Loader" button (top-right)
  - Loading states, error handling

  Also generate:
  - src/api/loaders.ts - API functions
  - src/types/loader.ts - TypeScript types
  - src/hooks/useLoaders.ts - TanStack Query hook
  - src/components/LoadersTable.tsx - Table component
  "
  ```

- [ ] **Copy generated files to project**

- [ ] **Add route to App.tsx:**
  ```tsx
  <Route path="/loaders" element={<LoadersListPage />} />
  ```

- [ ] **Test page:**
  - Navigate to http://localhost:5173/loaders
  - Should see table (may be empty if no data)

**Handle CORS errors (if they occur):**

- [ ] **If you see CORS error in browser console:**
  ```
  Access to XMLHttpRequest at 'http://localhost:8080/api/v1/res/loaders/loaders'
  from origin 'http://localhost:5173' has been blocked by CORS policy
  ```

- [ ] **Ask Claude Code:**
  ```
  "I'm getting CORS error when calling loader-service from React app on localhost:5173.

  Generate Spring Boot CORS configuration for loader-service.

  Allow:
  - Origins: http://localhost:5173, http://localhost:3000
  - Methods: GET, POST, PUT, DELETE, OPTIONS
  - Headers: *
  - Credentials: true
  "
  ```

- [ ] **Add generated WebConfig.java to loader-service:**
  ```bash
  # File location: /Volumes/Files/Projects/newLoader/services/loader/src/main/java/com/tiqmo/monitoring/loader/config/WebConfig.java
  ```

- [ ] **Rebuild loader-service:**
  ```bash
  cd /Volumes/Files/Projects/newLoader/services/loader
  mvn clean package -DskipTests
  ```

- [ ] **Restart loader-service:**
  ```bash
  # If running locally:
  # java -jar target/loader-0.0.1-SNAPSHOT.jar

  # If in Kubernetes:
  kubectl rollout restart deployment/loader-service
  ```

- [ ] **Test again - CORS error should be gone**

**Verify:**
- [ ] Table renders without errors
- [ ] Can see loaders data (or empty state if no loaders)
- [ ] Search box works
- [ ] Filter dropdown works
- [ ] Pagination works
- [ ] No CORS errors

**Commit:**
- [ ] **Git commit:**
  ```bash
  git add frontend/src/pages/LoadersListPage.tsx
  git add frontend/src/api/loaders.ts
  git add frontend/src/types/loader.ts
  git add frontend/src/hooks/useLoaders.ts
  git add frontend/src/components/LoadersTable.tsx
  git add services/loader/src/main/java/com/tiqmo/monitoring/loader/config/WebConfig.java
  git commit -m "POC Stage 1: Add loaders list page with TanStack Table"
  git push origin main
  ```

---

### ✅ Day 6-7: Loader Details Page

**Verify API endpoints (check backup):**

- [ ] **Check if GET /loaders/{code} exists:**
  ```bash
  grep -r "GetMapping.*{code}" /Volumes/Files/Projects/newLoaderBackup/services/loader/src/main/java/ | grep -i loader
  ```

- [ ] **Test API:**
  ```bash
  curl http://localhost:8080/api/v1/res/loaders/SOME_LOADER_CODE
  ```

**Note:** If loader history API doesn't exist, we'll show placeholder for now (defer to later)

**Generate components (Claude Code):**

- [ ] **Ask Claude Code:**
  ```
  "Create loader details page with tabs.

  API: GET http://localhost:8080/api/v1/res/loaders/{code}

  Component: src/pages/LoaderDetailsPage.tsx

  Features:
  - Use React Router useParams to get loader code
  - shadcn/ui Tabs with 3 tabs:
    1. Configuration:
       - Loader metadata cards
       - Source database info (host, port, dbname, type, username)
       - SQL query display (read-only, code block or Monaco editor)
       - Execution settings (interval, parallelism, fetch size)
       - Segments as badges
    2. Execution History:
       - Placeholder: "Execution history coming in next sprint"
       - TODO: TanStack Table with executions
    3. Signals:
       - Placeholder: "View in Signals Explorer (coming soon)"

  - Header: Loader code + status badge + Edit/Delete buttons
  - Loading state, error handling

  Also generate:
  - src/api/loaders.ts - Add getLoaderDetails(code)
  - Update src/types/loader.ts - Add LoaderDetails type
  "
  ```

- [ ] **Copy generated files**

- [ ] **Add route:**
  ```tsx
  <Route path="/loaders/:code" element={<LoaderDetailsPage />} />
  ```

- [ ] **Update LoadersListPage - make rows clickable:**
  - Click row → navigate to `/loaders/{code}`

- [ ] **Test:**
  - Click a loader in list → details page opens
  - See all 3 tabs
  - Configuration tab shows loader data
  - History/Signals tabs show placeholders

**Verify:**
- [ ] Details page renders
- [ ] Tabs switch correctly
- [ ] Configuration data displays
- [ ] Back to list works

**Commit:**
- [ ] **Git commit:**
  ```bash
  git add frontend/src/pages/LoaderDetailsPage.tsx
  git commit -m "POC Stage 1: Add loader details page with tabs"
  git push origin main
  ```

---

### ✅ Day 8-10: Create/Edit Loader Form

**Verify API endpoints (check backup):**

- [ ] **Check POST /loaders exists:**
  ```bash
  grep -r "PostMapping" /Volumes/Files/Projects/newLoaderBackup/services/loader/src/main/java/ | grep -i loader
  ```

- [ ] **Check PUT /loaders/{code} exists:**
  ```bash
  grep -r "PutMapping" /Volumes/Files/Projects/newLoaderBackup/services/loader/src/main/java/ | grep -i loader
  ```

- [ ] **Check GET /db-sources exists:**
  ```bash
  grep -r "db-sources" /Volumes/Files/Projects/newLoaderBackup/services/loader/src/main/java/
  ```

**Generate form component (Claude Code):**

- [ ] **Ask Claude Code:**
  ```
  "Create loader editor dialog (modal) for create/edit.

  APIs:
  - POST /api/v1/res/loaders (create)
  - PUT /api/v1/res/loaders/{code} (update)
  - GET /api/v1/admin/res/db-sources (source dropdown)

  Component: src/components/LoaderEditorDialog.tsx

  Props:
  - open: boolean
  - onClose: () => void
  - loader?: Loader (undefined = create mode, defined = edit mode)
  - onSuccess: () => void

  Form fields (React Hook Form + Zod):
  - Loader Code (text, required, max 64, alphanumeric + underscore)
  - Source Database (select dropdown)
  - Loader SQL (textarea or Monaco, required, basic validation)
  - Interval (number + unit select: minutes/hours/days)
  - Max Parallelism (number, default 1, min 1, max 10)
  - Fetch Size (number, default 1000, min 100)
  - Segments (text input for now, comma-separated)
  - Purge Strategy (select: NONE, OLD_RUNS, ALL)

  Validation:
  - Inline errors
  - Disable submit while submitting

  UX:
  - Loading spinner on submit
  - Success toast (shadcn/ui Sonner)
  - Close dialog on success
  - Call onSuccess() callback

  Also generate:
  - src/api/loaders.ts - Add createLoader, updateLoader
  - src/api/sources.ts - Add getSources
  - src/schemas/loader.ts - Zod schema
  "
  ```

- [ ] **Install additional dependencies if needed:**
  ```bash
  npm install @monaco-editor/react  # If using Monaco editor
  npm install sonner  # For toast notifications
  ```

- [ ] **Copy generated files**

- [ ] **Update LoadersListPage:**
  - "Create New Loader" button → open dialog
  - Edit button → open dialog with loader data

- [ ] **Update LoaderDetailsPage:**
  - Edit button → open dialog with loader data

- [ ] **Test create flow:**
  - Click "Create New Loader"
  - Fill form
  - Submit
  - Should create loader and refresh list

- [ ] **Test edit flow:**
  - Click Edit on existing loader
  - Form pre-filled with data
  - Change interval
  - Submit
  - Should update loader

- [ ] **Test validation:**
  - Submit empty form → see errors
  - Enter invalid loader code (spaces) → see error
  - Enter invalid SQL (not starting with SELECT) → see error

**Verify:**
- [ ] Create loader works
- [ ] Edit loader works
- [ ] Form validation works
- [ ] Success toast appears
- [ ] Dialog closes on success
- [ ] List refreshes after create/edit

**Commit:**
- [ ] **Git commit:**
  ```bash
  git add frontend/src/components/LoaderEditorDialog.tsx
  git add frontend/src/api/sources.ts
  git add frontend/src/schemas/loader.ts
  git commit -m "POC Stage 1: Add loader create/edit form with validation"
  git push origin main
  ```

---

### ✅ Day 11: Source Databases Page

**Verify API (check backup):**

- [ ] **Check GET /db-sources exists:**
  ```bash
  grep -r "db-sources" /Volumes/Files/Projects/newLoaderBackup/services/loader/src/main/java/
  ```

- [ ] **Test API:**
  ```bash
  curl http://localhost:8080/api/v1/admin/res/db-sources
  ```

**Generate page (Claude Code):**

- [ ] **Ask Claude Code:**
  ```
  "Create source databases page.

  API: GET /api/v1/admin/res/db-sources

  Component: src/pages/SourceDatabasesPage.tsx

  Features:
  - TanStack Table
  - Columns: Code, Host, Port, Database, Type, Username, Read-Only Status
  - Password column: always show as ******** (never decrypt)
  - Read-Only Status: Badge (✅ Verified or ❌ Failed)
  - No create/edit for now (defer)

  Also generate:
  - src/types/source.ts - TypeScript types
  "
  ```

- [ ] **Copy generated files**

- [ ] **Add route:**
  ```tsx
  <Route path="/sources" element={<SourceDatabasesPage />} />
  ```

- [ ] **Test:**
  - Navigate to http://localhost:5173/sources
  - See sources table
  - Passwords shown as ********

**Verify:**
- [ ] Sources page renders
- [ ] Table shows sources
- [ ] Passwords masked

**Commit:**
- [ ] **Git commit:**
  ```bash
  git add frontend/src/pages/SourceDatabasesPage.tsx
  git add frontend/src/types/source.ts
  git commit -m "POC Stage 1: Add source databases page"
  git push origin main
  ```

---

### ✅ Day 12: Navigation & Polish

**Add navigation menu:**

- [ ] **Ask Claude Code:**
  ```
  "Create a simple navigation component for POC.

  Component: src/components/Navigation.tsx

  Features:
  - Simple header with links
  - Links: Home, Loaders, Sources
  - Current route highlighted
  - Responsive (mobile-friendly)
  "
  ```

- [ ] **Update App.tsx to include Navigation**

- [ ] **Test navigation:**
  - Click links
  - Current page highlighted

**Add home page:**

- [ ] **Create simple home page:**
  ```tsx
  // src/pages/HomePage.tsx
  - Welcome message
  - Cards with links to Loaders and Sources
  - Quick stats (optional)
  ```

- [ ] **Add route:**
  ```tsx
  <Route path="/" element={<HomePage />} />
  ```

**Polish UI:**

- [ ] **Review all pages for consistency:**
  - Spacing consistent
  - Colors consistent (use Tailwind theme)
  - Loading states present
  - Error states handled

- [ ] **Add delete confirmation dialog:**
  ```tsx
  // When user clicks Delete loader
  - Show confirmation dialog
  - "Are you sure you want to delete {loaderCode}?"
  - Cancel / Delete buttons
  - Only delete if confirmed
  ```

**Verify:**
- [ ] Navigation works
- [ ] Home page accessible
- [ ] All pages have consistent styling
- [ ] Delete confirmation works

**Commit:**
- [ ] **Git commit:**
  ```bash
  git add frontend/src/components/Navigation.tsx
  git add frontend/src/pages/HomePage.tsx
  git commit -m "POC Stage 1: Add navigation and home page"
  git push origin main
  ```

---

### ✅ Day 13-14: End-to-End Testing & Bug Fixes

**Full testing checklist:**

- [ ] **Test: View loaders list**
  - Go to http://localhost:5173/loaders
  - See list of loaders
  - No errors in console

- [ ] **Test: Search loaders**
  - Type loader code in search box
  - Results filter as you type (debounced)

- [ ] **Test: Filter by status**
  - Select ACTIVE → see only active loaders
  - Select PAUSED → see only paused loaders
  - Select All → see all loaders

- [ ] **Test: Pagination**
  - Change page size to 10
  - Navigate next/previous pages
  - Page count correct

- [ ] **Test: Sort columns**
  - Click Loader Code column header → sorts ascending
  - Click again → sorts descending

- [ ] **Test: Create new loader**
  - Click "Create New Loader"
  - Fill form with valid data
  - Submit
  - Success toast appears
  - Dialog closes
  - New loader appears in list
  - Verify in database (optional):
    ```bash
    # Connect to postgres
    kubectl exec -it postgres-pod -- psql -U user -d dbname
    SELECT * FROM loader.loader WHERE loader_code = 'NEW_LOADER_CODE';
    ```

- [ ] **Test: Edit loader**
  - Click Edit button on existing loader
  - Form pre-filled with current values
  - Change interval from 60 to 120 minutes
  - Submit
  - Success toast appears
  - Dialog closes
  - List refreshes, shows updated interval

- [ ] **Test: Delete loader**
  - Click Delete button
  - Confirmation dialog appears
  - Click Cancel → nothing happens
  - Click Delete again, click Confirm → loader deleted
  - Success toast appears
  - Loader removed from list

- [ ] **Test: View loader details**
  - Click a loader row
  - Details page opens
  - Configuration tab shows all data
  - Execution History tab shows placeholder
  - Signals tab shows placeholder

- [ ] **Test: View sources**
  - Navigate to /sources
  - See sources table
  - Passwords shown as ********
  - Read-only badge present

- [ ] **Test: Navigation**
  - Click Home → goes to home
  - Click Loaders → goes to loaders
  - Click Sources → goes to sources
  - Current page highlighted

- [ ] **Test: Error handling**
  - Stop loader-service
  - Refresh loaders page
  - Should show error message (not crash)
  - Start loader-service
  - Retry → data loads

- [ ] **Test: Form validation**
  - Create New Loader
  - Leave Loader Code empty → shows error
  - Enter code with spaces → shows error
  - Enter code > 64 chars → shows error
  - Leave SQL empty → shows error
  - Enter interval = 0 → shows error
  - Fix all errors → submit works

**Fix bugs:**

For each bug found:

- [ ] **Document bug:**
  ```
  Bug #X:
  - What: [description]
  - Expected: [what should happen]
  - Actual: [what happens]
  - Error: [console error if any]
  ```

- [ ] **Ask Claude Code for fix:**
  ```
  "Bug: [paste bug description and error]

  Component: [component name]

  Please provide fix."
  ```

- [ ] **Apply fix, test again**

- [ ] **Commit fix:**
  ```bash
  git commit -m "POC Stage 1: Fix bug - [short description]"
  ```

**Performance check:**

- [ ] **Test with many loaders (if possible):**
  - If < 10 loaders in DB, create more (using UI)
  - Test with 50+ loaders
  - Pagination should handle it
  - No lag when typing in search

- [ ] **Check browser console:**
  - No errors
  - No warnings (or only minor ones)

**Final polish:**

- [ ] **Check all pages on mobile (browser dev tools):**
  - Resize to mobile width
  - Tables responsive (scrollable)
  - Forms usable
  - Navigation works

- [ ] **Check all pages in different browsers:**
  - Chrome ✓
  - Firefox ✓
  - Safari ✓ (if on Mac)

**Documentation:**

- [ ] **Update PROJECT_TRACKER.md:**
  ```bash
  # Mark Stage 1 deliverables as complete
  # Update Phase Status Summary
  ```

- [ ] **Create demo script:**
  ```bash
  # Create STAGE1_DEMO_SCRIPT.md
  - What to show manager
  - Talking points
  - Order of actions
  ```

**Final commit:**

- [ ] **Git commit:**
  ```bash
  git add .
  git commit -m "POC Stage 1: Complete - Functional loader management UI"
  git push origin main
  ```

---

## 🎬 STAGE 1 DEMO TO MANAGER (End of Week 2)

### Pre-Demo Checklist

- [ ] **Loader-service running:**
  ```bash
  curl http://localhost:8080/actuator/health
  ```

- [ ] **PostgreSQL has sample data:**
  ```bash
  # At least 5-10 loaders for demo
  ```

- [ ] **React dev server running:**
  ```bash
  cd /Volumes/Files/Projects/newLoader/frontend
  npm run dev
  ```
  Open http://localhost:5173

- [ ] **No errors in browser console**

- [ ] **Rehearse demo flow** (practice 2-3 times)

### Demo Script (15 minutes)

**Introduction (2 min):**
- "I'd like to show you the Loader Management UI we built"
- "This is Stage 1 - functional but not secured yet"
- "Built in 2 weeks using modern React + Spring Boot"

**Demo Flow (10 min):**

1. **Show loaders list** (2 min)
   - Navigate to http://localhost:5173/loaders
   - "Here's the list of all our ETL loaders"
   - Show search: type loader code → filters instantly
   - Show filter: select ACTIVE → only active loaders
   - Show pagination: change to 25 per page
   - "All data comes from our PostgreSQL database via loader-service REST APIs"

2. **Create new loader** (3 min)
   - Click "Create New Loader"
   - Fill form:
     - Loader Code: DEMO_LOADER
     - Source Database: select from dropdown
     - SQL: SELECT * FROM demo_table
     - Interval: 60 minutes
     - Max Parallelism: 2
   - "Notice the form validation - won't let me submit invalid data"
   - Submit
   - "Success! New loader appears in the list"

3. **View loader details** (2 min)
   - Click DEMO_LOADER row
   - "Details page with 3 tabs"
   - Configuration tab: "See all loader settings, SQL query, source database"
   - Execution History tab: "Placeholder for now - next sprint"
   - Signals tab: "Will show time-series charts - next sprint"

4. **Edit loader** (1 min)
   - Click Edit button
   - Change interval to 120 minutes
   - Submit
   - "Updated! Changes saved to database"

5. **View sources** (1 min)
   - Navigate to Sources
   - "All our source databases"
   - "Notice passwords are masked - security"

6. **Delete loader** (1 min)
   - Go back to loaders
   - Click Delete on DEMO_LOADER
   - Show confirmation dialog
   - "Safety feature - prevents accidental deletion"
   - Confirm delete
   - "Removed from database"

**Conclusion (3 min):**
- "This is fully functional - can manage loaders without SQL queries"
- "Next week: Stage 2 - add login and role-based access control"
- "After that: charts for signal visualization, alerts, incidents"
- "Questions?"

### Key Talking Points

✅ **Speed:** "Built in 2 weeks with AI assistance (Claude Code)"
✅ **Technology:** "Modern React 18 + TypeScript + Spring Boot - same stack as Netflix"
✅ **Value:** "Replaces manual database queries with professional UI"
✅ **Security:** "Stage 2 adds login - only authorized users can access"
✅ **Foundation:** "This is the base - we'll add charts, monitoring, alerts on top"

### Expected Manager Questions & Answers

**Q: "Is this secure? Can anyone access it?"**
A: "Not yet - that's Stage 2 next week. We'll add JWT authentication and role-based access. I wanted to show you the functionality first before adding security layer."

**Q: "How long did this take?"**
A: "2 weeks, working solo with AI assistance from Claude Code. Claude generated about 70% of the code, I reviewed, tested, and integrated everything."

**Q: "What's next?"**
A: "Stage 2 next week - add login, 3 user roles (Admin, Operator, Viewer). After POC approval, we'll add: (1) Time-series charts for signal data, (2) Alerting to replace current solution, (3) Incident management."

**Q: "Can we deploy this to production?"**
A: "After Stage 2 yes - we'll add Spring Cloud Gateway for API routing, SSL certificates, and deploy to Kubernetes. For this demo it's running locally on my laptop."

**Q: "What if something breaks in production?"**
A: "Backend (loader-service) is already tested and running in production. Frontend is new but built on proven frameworks (React, TypeScript). We'll do QA testing before production deployment."

---

## 🔒 STAGE 2: LOGIN + ACCESS CONTROL (7 Days)

### ✅ Day 15-16: Authentication UI

**Verify backend auth exists (check backup):**

- [ ] **Check if /auth/login endpoint exists:**
  ```bash
  grep -r "auth/login" /Volumes/Files/Projects/newLoaderBackup/services/loader/src/main/java/
  ```

- [ ] **Test login API:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}'
  ```
  Expected: JWT token response

**Generate auth components (Claude Code):**

- [ ] **Ask Claude Code:**
  ```
  "Add JWT authentication to React app.

  Backend API: POST /api/v1/auth/login
  Request: {"username": "string", "password": "string"}
  Response: {"token": "jwt_token", "username": "string", "roles": ["ROLE_ADMIN"]}

  Generate:
  1. src/pages/LoginPage.tsx
     - Form: username + password (React Hook Form + Zod)
     - Submit to /api/v1/auth/login
     - Store token in localStorage
     - Redirect to /loaders on success
     - Show error on failure

  2. src/contexts/AuthContext.tsx
     - Store user, token, roles
     - Provide useAuth() hook
     - Methods: login(username, password), logout(), isAuthenticated(), hasRole(role)
     - Load token from localStorage on mount

  3. src/components/ProtectedRoute.tsx
     - Wrap routes
     - Redirect to /login if not authenticated

  4. src/lib/axios.ts (update)
     - Axios interceptor to attach token: Authorization: Bearer {token}
     - Handle 401 responses: logout + redirect to /login

  Also update:
  - src/App.tsx - use ProtectedRoute
  - Add /login route (public)
  "
  ```

- [ ] **Copy generated files**

- [ ] **Update App.tsx routing:**
  ```tsx
  <Route path="/login" element={<LoginPage />} />
  <Route path="/" element={<ProtectedRoute><HomePage /></ProtectedRoute>} />
  <Route path="/loaders" element={<ProtectedRoute><LoadersListPage /></ProtectedRoute>} />
  // ... etc
  ```

- [ ] **Test login flow:**
  - Go to http://localhost:5173
  - Should redirect to /login
  - Enter admin/admin123
  - Should redirect to /loaders
  - Token stored in localStorage

- [ ] **Test logout:**
  - Add logout button temporarily
  - Click logout
  - Token removed
  - Redirected to /login

- [ ] **Test token expiry:**
  - Login
  - Manually delete token from localStorage
  - Refresh page
  - Should redirect to /login

**Verify:**
- [ ] Login page works
- [ ] Successful login redirects to app
- [ ] Token stored in localStorage
- [ ] Token attached to API requests
- [ ] 401 errors trigger logout

**Commit:**
- [ ] **Git commit:**
  ```bash
  git add frontend/src/pages/LoginPage.tsx
  git add frontend/src/contexts/AuthContext.tsx
  git add frontend/src/components/ProtectedRoute.tsx
  git commit -m "POC Stage 2: Add JWT authentication"
  git push origin main
  ```

---

### ✅ Day 17-18: Role-Based Access Control

**Test roles in backend:**

- [ ] **Test admin role:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}'
  ```

- [ ] **Test operator role:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"operator","password":"operator123"}'
  ```

- [ ] **Test viewer role:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"viewer","password":"viewer123"}'
  ```

**Add RBAC to UI (Claude Code):**

- [ ] **Ask Claude Code:**
  ```
  "Implement role-based access control in UI.

  Roles:
  - ROLE_ADMIN: Full access (create, edit, delete)
  - ROLE_OPERATOR: Read + operational actions (pause/resume)
  - ROLE_VIEWER: Read-only

  Update components:
  1. LoadersListPage.tsx
     - Hide "Create New Loader" for VIEWER and OPERATOR
     - Hide Delete button for VIEWER and OPERATOR
     - Hide Edit button for VIEWER

  2. LoaderDetailsPage.tsx
     - Hide Edit/Delete buttons for VIEWER
     - Hide Edit button for OPERATOR

  3. Add user profile dropdown (top-right):
     - Component: src/components/UserProfile.tsx
     - Show username
     - Show role badge
     - Logout button

  Use useAuth() hook:
  {hasRole('ADMIN') && <Button>Delete</Button>}
  "
  ```

- [ ] **Copy generated files**

- [ ] **Update Navigation component:**
  - Add UserProfile in header (top-right)

- [ ] **Test with ADMIN (admin/admin123):**
  - Login
  - Should see all buttons (Create, Edit, Delete)

- [ ] **Test with OPERATOR (operator/operator123):**
  - Logout, login as operator
  - Should NOT see Create button
  - Should NOT see Edit button
  - Should NOT see Delete button
  - Can view loaders and sources

- [ ] **Test with VIEWER (viewer/viewer123):**
  - Logout, login as viewer
  - Should NOT see Create/Edit/Delete
  - Can only view data

**Verify:**
- [ ] ADMIN sees all buttons
- [ ] OPERATOR sees limited buttons
- [ ] VIEWER read-only
- [ ] User profile shows role
- [ ] Logout works from profile dropdown

**Commit:**
- [ ] **Git commit:**
  ```bash
  git add frontend/src/components/UserProfile.tsx
  git commit -m "POC Stage 2: Add role-based access control"
  git push origin main
  ```

---

### ✅ Day 19: App Shell & Layout

**Create layout (Claude Code):**

- [ ] **Ask Claude Code:**
  ```
  "Create app shell with sidebar navigation.

  Component: src/components/AppLayout.tsx

  Features:
  - Sidebar with nav items:
    - Home
    - Loaders
    - Sources
    - Signals (placeholder/disabled)
    - Settings (placeholder/disabled)
  - Header:
    - Breadcrumbs (current page)
    - UserProfile dropdown (right side)
  - Main content area
  - Responsive (collapse sidebar on mobile)

  Use shadcn/ui components.
  "
  ```

- [ ] **Copy generated files**

- [ ] **Update App.tsx:**
  - Wrap protected routes with AppLayout

- [ ] **Test:**
  - Sidebar navigation works
  - Mobile responsive (resize browser)
  - Breadcrumbs update on page change

**Verify:**
- [ ] Sidebar navigation works
- [ ] Layout responsive
- [ ] Breadcrumbs correct

**Commit:**
- [ ] **Git commit:**
  ```bash
  git add frontend/src/components/AppLayout.tsx
  git commit -m "POC Stage 2: Add app shell with sidebar navigation"
  git push origin main
  ```

---

### ✅ Day 20-21: Testing & Final Polish

**Full RBAC testing:**

- [ ] **Create test matrix:**
  | Feature | ADMIN | OPERATOR | VIEWER |
  |---------|-------|----------|--------|
  | View loaders list | ✓ | ✓ | ✓ |
  | Search/filter loaders | ✓ | ✓ | ✓ |
  | View loader details | ✓ | ✓ | ✓ |
  | Create loader | ✓ | ✗ | ✗ |
  | Edit loader | ✓ | ✗ | ✗ |
  | Delete loader | ✓ | ✗ | ✗ |
  | View sources | ✓ | ✓ | ✓ |

- [ ] **Test each role, verify matrix**

**Security testing:**

- [ ] **Test: Direct URL access when not logged in**
  - Logout
  - Navigate to http://localhost:5173/loaders
  - Should redirect to /login

- [ ] **Test: Token expiry**
  - Login
  - Wait 24 hours OR manually expire token
  - Make API call
  - Should logout and redirect

- [ ] **Test: Invalid credentials**
  - Try login with wrong password
  - Should show error message
  - Should not redirect

- [ ] **Test: API calls include token**
  - Login
  - Open browser DevTools → Network tab
  - Click loaders
  - Check request headers → should have: Authorization: Bearer ...

**Final polish:**

- [ ] **Review UI consistency:**
  - All pages use same spacing
  - Buttons same style
  - Forms consistent
  - Error messages friendly

- [ ] **Add loading states:**
  - Show spinner while logging in
  - Show spinner while loading loaders
  - Show spinner while submitting forms

- [ ] **Error handling:**
  - Network error → show retry button
  - 403 Forbidden → show "Access Denied" message
  - 404 Not Found → show "Loader not found"

**Documentation:**

- [ ] **Create STAGE2_DEMO_SCRIPT.md**

- [ ] **Update PROJECT_TRACKER.md:**
  - Mark Stage 2 complete

**Final commit:**

- [ ] **Git commit:**
  ```bash
  git add .
  git commit -m "POC Stage 2: Complete - JWT authentication + RBAC"
  git push origin main
  ```

---

## 🎬 STAGE 2 DEMO TO MANAGER (End of Week 3)

### Pre-Demo Checklist

- [ ] **All services running**
- [ ] **3 test users ready:**
  - admin/admin123
  - operator/operator123
  - viewer/viewer123
- [ ] **React app running**
- [ ] **No console errors**
- [ ] **Rehearsed demo**

### Demo Script (20 minutes)

**Introduction (2 min):**
- "Last week I showed you the functional UI"
- "This week I added enterprise security - login and role-based access"

**Demo Flow (15 min):**

1. **Show login page** (2 min)
   - "Now app starts with login screen"
   - Try wrong password → error message
   - Enter admin/admin123 → success

2. **Show ADMIN access** (3 min)
   - "As admin, I have full access"
   - Create loader
   - Edit loader
   - Delete loader
   - "Notice the user profile shows my role: Admin"

3. **Show VIEWER role** (3 min)
   - Logout → Click username → Logout
   - Login as viewer/viewer123
   - "Notice: no Create button"
   - Click a loader → "No Edit or Delete buttons"
   - "Viewer can only read data - perfect for analysts"

4. **Show OPERATOR role** (3 min)
   - Logout, login as operator/operator123
   - "Operator role: can view but not delete"
   - Show limited access

5. **Show security features** (2 min)
   - "All API calls use JWT token"
   - "Token expires after 24 hours"
   - "Backend validates every request"

6. **Show layout** (2 min)
   - "Professional app shell with sidebar"
   - "Breadcrumbs show current location"
   - "Responsive - works on mobile" (resize browser)

**Conclusion (3 min):**
- "POC complete - functional UI + enterprise security"
- "Ready to discuss next phase: deployment to production"
- "Or pivot to charts/alerting if that's higher priority"
- "Questions?"

### Key Talking Points

✅ **Security:** "JWT tokens, role-based access, 24-hour expiry"
✅ **Enterprise-ready:** "3 roles match our org structure"
✅ **Audit trail:** "Backend logs who did what (already built-in)"
✅ **Timeline:** "3 weeks total - 2 weeks functional, 1 week security"
✅ **Next:** "Deploy to Kubernetes with Gateway + SSL, or add charts/alerting"

---

## 📊 POC COMPLETION CHECKLIST

### Stage 1 Completion Criteria

- [ ] ✅ React 18 + TypeScript + Vite project running
- [ ] ✅ shadcn/ui components installed
- [ ] ✅ Loaders list page with search/filter/pagination
- [ ] ✅ Loader details page with 3 tabs
- [ ] ✅ Create/edit loader form with validation
- [ ] ✅ Delete loader with confirmation
- [ ] ✅ Source databases page
- [ ] ✅ Navigation between pages
- [ ] ✅ CORS configured in loader-service
- [ ] ✅ All CRUD operations work
- [ ] ✅ No critical bugs
- [ ] ✅ Stage 1 demo successful

### Stage 2 Completion Criteria

- [ ] ✅ Login page with form validation
- [ ] ✅ JWT token storage in localStorage
- [ ] ✅ Protected routes redirect to login
- [ ] ✅ Token attached to API requests
- [ ] ✅ 401 errors trigger logout
- [ ] ✅ RBAC implemented (3 roles)
- [ ] ✅ User profile dropdown with role display
- [ ] ✅ App shell with sidebar navigation
- [ ] ✅ Breadcrumbs
- [ ] ✅ All roles tested
- [ ] ✅ Security verified
- [ ] ✅ Stage 2 demo successful

### Manager Approval Criteria

- [ ] ✅ Manager saw both demos
- [ ] ✅ Manager approved POC
- [ ] ✅ Manager decided next priority:
  - Option A: Deploy to production (Gateway + SSL)
  - Option B: Add charts/visualization
  - Option C: Add alerting
  - Option D: Other ___________
- [ ] ✅ Next sprint planned based on approval

---

## 📝 PROJECT STATE TRACKING

### Files Created (POC)

**New directories:**
- `/Volumes/Files/Projects/newLoader/frontend/` - Entire React app

**New files in root:**
- `/Volumes/Files/Projects/newLoader/POC_CHECKLIST.md` - This file
- `/Volumes/Files/Projects/newLoader/STAGE1_DEMO_SCRIPT.md` - Demo guide
- `/Volumes/Files/Projects/newLoader/STAGE2_DEMO_SCRIPT.md` - Demo guide
- `/Volumes/Files/Projects/newLoader/POC_START_GIT_STATUS.txt` - Snapshot

**Modified files:**
- `/Volumes/Files/Projects/newLoader/PROJECT_TRACKER.md` - Progress updates
- `/Volumes/Files/Projects/newLoader/.gitignore` - Added frontend ignores
- `/Volumes/Files/Projects/newLoader/services/loader/src/main/java/.../WebConfig.java` - CORS (if added)

**NOT modified (protected):**
- All other files in `/services/loader/` - Unchanged
- All files in `/services/etl_initializer/` - Unchanged
- All files in `/services/dataGenerator/` - Unchanged
- All files in `/infra/` - Unchanged
- All files in `/kube/` - Unchanged

### Backup Verification

**At any time, verify you haven't broken existing functionality:**

```bash
# Compare current with backup
diff -r /Volumes/Files/Projects/newLoaderBackup/services/loader \
        /Volumes/Files/Projects/newLoader/services/loader \
        --exclude=target --exclude=.idea

# Should only show WebConfig.java (if you added CORS)
# If shows more changes, investigate
```

---

## 🆘 TROUBLESHOOTING

### Common Issues

**Issue: CORS errors**
- **Check:** Did you add WebConfig.java?
- **Check:** Did you rebuild loader-service?
- **Check:** Is loader-service restarted?
- **Fix:** See Day 4-5 CORS section

**Issue: 401 Unauthorized**
- **Check:** Is JWT token in localStorage?
- **Check:** Is token attached to request? (DevTools → Network → Headers)
- **Check:** Is loader-service running?
- **Fix:** Login again, check axios interceptor

**Issue: React app won't start**
- **Check:** `npm install` completed?
- **Check:** Node version >= 16?
- **Check:** Port 5173 not in use?
- **Fix:** `killall node`, `npm install`, `npm run dev`

**Issue: API returns empty array**
- **Check:** Does PostgreSQL have data?
- **Check:** Is loader-service connected to correct database?
- **Fix:** Check loader-service logs

**Issue: Form validation not working**
- **Check:** Did you install Zod?
- **Check:** Is schema imported correctly?
- **Fix:** Ask Claude Code for debug help

### Getting Help from Claude Code

**For any issue:**

1. **Describe the problem clearly:**
   ```
   "Issue: [what's not working]
   Expected: [what should happen]
   Actual: [what actually happens]
   Error message: [paste full error from console]
   Component: [which file/component]
   "
   ```

2. **Claude Code will:**
   - Analyze the error
   - Provide fix
   - Explain why it happened

3. **Apply fix, test, commit**

---

## ✅ FINAL SUCCESS CRITERIA

**POC is successful if:**

1. ✅ Manager sees working loader management UI
2. ✅ Manager sees secure login with 3 roles
3. ✅ Manager approves moving to next phase
4. ✅ Zero changes to existing loader-service business logic
5. ✅ All code committed to Git
6. ✅ Demo scripts documented for handover

**POC delivers value:**
- 🎯 Proves UI can be built quickly (3 weeks)
- 🎯 Proves AI-assisted development works
- 🎯 Shows modern tech stack (React + Spring Boot)
- 🎯 Demonstrates enterprise security (JWT + RBAC)
- 🎯 Foundation for full platform (charts, alerts, incidents)

---

**Ready to start? Execute Day 0 Pre-Flight Checks!** ✈️
