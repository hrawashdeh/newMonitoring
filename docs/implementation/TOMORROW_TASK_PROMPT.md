# Tomorrow Task: Start POC Stage 1 Development

**Date:** 2025-12-26
**Sprint:** POC Stage 1 - Functional Loader UI (Week 1-2)
**Objective:** Build production-quality React frontend proving the Loader → Signals pattern
**Strategic Context:** POC is not just a demo - it's proving the core replication pattern for unified observability platform

---

## 🎯 Session Start Prompt

When starting tomorrow's session, use this prompt with Claude Code:

```
I'm ready to start POC Stage 1 development for the Enterprise Monitoring Platform.

STRATEGIC CONTEXT (CRITICAL):
This POC is not just a loader management UI. It's proving the **core replication pattern** that unlocks the entire unified observability platform.

If we perfect the Loader → Signals → Visualization pattern:
- Same model works for Kibana errors (Elasticsearch source)
- Same model works for Prometheus metrics (Prometheus source)
- Same model works for custom app metrics (any database source)
- All use same charts, alerts, incidents, RCA reports, Jira integration

POC SUCCESS CRITERIA:
1. Multi-database support works (PostgreSQL + MySQL minimum)
2. Segment drill-down smooth (proves service/error-type categorization)
3. Large dataset performance (100K+ signals render smoothly)
4. Time-range queries fast (<200ms for 1M records with partitioning)
5. Backfill works end-to-end (gap detection + remediation)
6. Chart interactions flawless (zoom/pan/tooltip/export)
7. RBAC enforced (3 roles work correctly)
8. Encryption working (SQL queries, passwords encrypted)

TODAY'S TASKS (Day 1):
1. Set up React 18 + TypeScript + Vite project
2. Install shadcn/ui + TailwindCSS
3. Install TanStack Table + TanStack Query
4. Install Apache ECharts
5. Project structure: /api, /components, /pages, /lib, /types, /hooks
6. Basic routing setup
7. Environment configuration

REFERENCE DOCUMENTS:
- /Volumes/Files/Projects/newLoader/POC_CHECKLIST.md - Detailed day-by-day tasks
- /Volumes/Files/Projects/newLoader/UI_WIREFRAMES_AND_MOCKUPS.md - Complete UI specs
- /Volumes/Files/Projects/newLoader/LOADER_FUNCTIONALITY_TREE.md - Feature inventory
- /Volumes/Files/Projects/newLoader/KNOWN_ISSUES.md - Issue #18: POC pattern proof
- /Volumes/Files/Projects/newLoader/PROJECT_TRACKER.md - Sprint planning

TECHNOLOGY STACK:
Frontend:
- React 18 + TypeScript 5 + Vite 5
- shadcn/ui + TailwindCSS (single design system, no hybrid)
- TanStack Table v8 (virtualization for 10K+ rows)
- TanStack Query v5 (caching, optimistic updates)
- Apache ECharts 5.4 (millions of data points, WebGL)
- React Hook Form + Zod (type-safe validation)
- Zustand (global state management, 3KB)
- React Router v6

Backend (existing):
- Spring Boot 3.5.6
- JWT authentication (HMAC-SHA256)
- RBAC: ADMIN, OPERATOR, VIEWER
- AES-256-GCM encryption
- PostgreSQL 15 + MySQL

NEW for POC:
- Spring HATEOAS (role-based action links in API responses)
- Backend returns _links object with allowed actions only
- Frontend consumes links dynamically (no hardcoded role checks)

WORKING DIRECTORY:
/Volumes/Files/Projects/newLoader/frontend/ (create this directory)

BACKEND API BASE:
http://localhost:8080 (loader-service already running)

DEFAULT USERS:
- admin/admin123 (ROLE_ADMIN) - full access
- operator/operator123 (ROLE_OPERATOR) - read + operational
- viewer/viewer123 (ROLE_VIEWER) - read-only

HATEOAS EXAMPLE:
Backend response for ADMIN:
{
  "loaderCode": "DAILY_SALES",
  "status": "ACTIVE",
  "_links": {
    "self": { "href": "/api/v1/loaders/DAILY_SALES" },
    "pause": { "href": "/api/v1/loaders/DAILY_SALES/pause", "method": "POST" },
    "edit": { "href": "/api/v1/loaders/DAILY_SALES", "method": "PUT" },
    "delete": { "href": "/api/v1/loaders/DAILY_SALES", "method": "DELETE" }
  }
}

Backend response for VIEWER (same resource):
{
  "loaderCode": "DAILY_SALES",
  "status": "ACTIVE",
  "_links": {
    "self": { "href": "/api/v1/loaders/DAILY_SALES" }
    // No action links - read only
  }
}

Frontend (dynamic rendering):
{loader._links.delete && <Button onClick={handleDelete}>Delete</Button>}
{loader._links.pause && <Button onClick={handlePause}>Pause</Button>}

DESIGN PRINCIPLES:
1. Bank-grade professional (no playful elements)
2. Information density (dense tables, compact forms)
3. Data-first (charts prominent)
4. Action-oriented (primary actions prominent)
5. Feedback & validation (inline validation, toast notifications)

FIRST SESSION CHECKLIST:
□ Create /Volumes/Files/Projects/newLoader/frontend directory
□ Initialize Vite project with React + TypeScript
□ Configure Tailwind CSS
□ Initialize shadcn/ui with base components
□ Install all dependencies (see UI_WIREFRAMES_AND_MOCKUPS.md)
□ Create folder structure (/src/api, /components, /pages, etc.)
□ Set up React Router
□ Create basic AppLayout component (header + sidebar placeholder)
□ Create HomePage with card navigation (from wireframes)
□ Test dev server runs on http://localhost:5173
□ Commit to git: "POC Stage 1: Initialize React project"

EXPECTED OUTPUT:
By end of Day 1:
- ✅ Dev server running on localhost:5173
- ✅ Home page with 8 feature cards (from wireframes)
- ✅ Basic navigation structure
- ✅ TailwindCSS working
- ✅ shadcn/ui components imported
- ✅ All dependencies installed
- ✅ Clean git commit

NEXT SESSION (Day 2-3):
- Build Loaders List page (TanStack Table)
- Search, filter, pagination
- HATEOAS-based action menu
- API integration with TanStack Query

IMPORTANT REMINDERS:
1. Focus on PERFECTION, not just functionality (proving the pattern)
2. Use shadcn/ui exclusively (no hybrid UI libraries)
3. All API calls through TanStack Query (caching, optimistic updates)
4. HATEOAS: backend controls what actions are available
5. TypeScript strict mode (no 'any' types)
6. Accessibility: WCAG 2.1 AA compliance
7. Responsive: mobile-first, works on all screen sizes

Let's start building!
```

---

## 📋 Detailed Day 1 Tasks (from POC_CHECKLIST.md)

### Morning (2-3 hours):

**1. Project Initialization**
```bash
cd /Volumes/Files/Projects/newLoader
mkdir frontend
cd frontend

# Initialize Vite project
npm create vite@latest . -- --template react-ts

# Install dependencies
npm install
```

**2. Install Core Libraries**
```bash
# UI & Styling
npm install -D tailwindcss postcss autoprefixer
npm install class-variance-authority clsx tailwind-merge
npm install lucide-react

# shadcn/ui initialization
npx shadcn-ui@latest init
# Select: Default style, Slate color, CSS variables

# Install shadcn components
npx shadcn-ui@latest add button card table dialog form input label select badge toast tabs dropdown-menu popover alert skeleton progress

# Data Management
npm install @tanstack/react-table @tanstack/react-query
npm install axios zod react-hook-form @hookform/resolvers

# Charts
npm install echarts echarts-for-react

# State & Routing
npm install zustand react-router-dom

# Utilities
npm install date-fns
```

**3. Project Structure**
```bash
mkdir -p src/{api,components,pages,lib,types,hooks,contexts}
mkdir -p src/components/ui
```

### Afternoon (3-4 hours):

**4. Create Base Files**

File: `src/lib/axios.ts`
```typescript
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080',
  headers: {
    'Content-Type': 'application/json'
  }
});

// Request interceptor (add JWT token)
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor (handle 401)
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('auth_token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;
```

File: `src/types/loader.ts`
```typescript
interface Link {
  href: string;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
}

export interface Loader {
  loaderCode: string;
  status: 'ACTIVE' | 'PAUSED' | 'FAILED';
  sourceDatabase: {
    host: string;
    port: number;
    dbName: string;
    type: 'POSTGRESQL' | 'MYSQL';
  };
  lastRun?: string;
  intervalSeconds: number;
  maxParallelism: number;
  _links: {
    self: Link;
    pause?: Link;
    resume?: Link;
    run?: Link;
    edit?: Link;
    delete?: Link;
    backfill?: Link;
  };
}
```

File: `src/App.tsx`
```typescript
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import HomePage from './pages/HomePage';
import LoadersListPage from './pages/LoadersListPage';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/loaders" element={<LoadersListPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
```

**5. Create HomePage (from wireframes)**

File: `src/pages/HomePage.tsx`
```typescript
import { useNavigate } from 'react-router-dom';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Database, Activity, Users, Shield, BarChart3, GitBranch, AlertCircle, Settings } from 'lucide-react';

const HomePage = () => {
  const navigate = useNavigate();

  const features = [
    {
      title: 'Loaders Management',
      description: 'Manage ETL loaders and data sources',
      icon: Database,
      path: '/loaders',
      active: true
    },
    {
      title: 'Backfill Jobs',
      description: 'Manual data reload operations',
      icon: Activity,
      path: '/backfill',
      active: false
    },
    {
      title: 'Signals Explorer',
      description: 'Time-series data visualization',
      icon: BarChart3,
      path: '/signals',
      active: false
    },
    {
      title: 'Source Databases',
      description: 'Configure data source connections',
      icon: GitBranch,
      path: '/sources',
      active: false
    },
    {
      title: 'User Management',
      description: 'Manage users and roles',
      icon: Users,
      path: '/admin/users',
      active: false
    },
    {
      title: 'Audit Logs',
      description: 'Track system activities',
      icon: Shield,
      path: '/admin/audit',
      active: false
    },
    {
      title: 'System Health',
      description: 'Monitor system status',
      icon: AlertCircle,
      path: '/admin/health',
      active: false
    },
    {
      title: 'Settings',
      description: 'Configure system preferences',
      icon: Settings,
      path: '/admin/settings',
      active: false
    }
  ];

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 to-slate-100">
      <header className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4">
          <h1 className="text-2xl font-bold text-slate-900">Monitoring Platform</h1>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="mb-8">
          <h2 className="text-3xl font-bold text-slate-900 mb-2">Dashboard</h2>
          <p className="text-slate-600">Select a module to get started</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {features.map((feature) => {
            const Icon = feature.icon;
            return (
              <Card
                key={feature.path}
                className={`cursor-pointer transition-all hover:shadow-lg hover:-translate-y-1 ${
                  !feature.active && 'opacity-60'
                }`}
                onClick={() => feature.active && navigate(feature.path)}
              >
                <CardHeader>
                  <div className="flex items-center gap-3">
                    <div className="p-2 bg-primary/10 rounded-lg">
                      <Icon className="h-6 w-6 text-primary" />
                    </div>
                    <div>
                      <CardTitle className="text-lg">{feature.title}</CardTitle>
                      {!feature.active && (
                        <span className="text-xs text-muted-foreground">Coming Soon</span>
                      )}
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  <CardDescription>{feature.description}</CardDescription>
                </CardContent>
              </Card>
            );
          })}
        </div>
      </main>
    </div>
  );
};

export default HomePage;
```

**6. Create Placeholder LoadersListPage**
```typescript
// src/pages/LoadersListPage.tsx
const LoadersListPage = () => {
  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold">Loaders List</h1>
      <p className="text-muted-foreground">Coming in Day 2-3</p>
    </div>
  );
};

export default LoadersListPage;
```

**7. Test & Commit**
```bash
# Run dev server
npm run dev

# Open http://localhost:5173
# Verify home page renders with 8 cards

# Git commit
cd /Volumes/Files/Projects/newLoader
git add frontend/
git commit -m "POC Stage 1: Initialize React project with homepage

- React 18 + TypeScript + Vite setup
- Tailwind CSS + shadcn/ui configured
- All dependencies installed
- Home page with card navigation (8 modules)
- Basic routing structure
- axios instance with JWT interceptors
- Type definitions for Loader with HATEOAS links

Next: Day 2-3 - Build Loaders List page"
```

---

## 📊 Success Metrics for Day 1

- ✅ Dev server runs without errors
- ✅ Home page renders professionally
- ✅ All 8 feature cards visible
- ✅ Hover effects work smoothly
- ✅ Click "Loaders Management" → navigates to /loaders
- ✅ TailwindCSS classes working
- ✅ shadcn/ui components imported successfully
- ✅ TypeScript compiles with no errors
- ✅ Git commit with clear message

---

## 🔄 Context for Next Session (Day 2-3)

Tomorrow we'll build the Loaders List page with:
- TanStack Table (virtualization, sorting, filtering)
- Search input (debounced 300ms)
- Status filter dropdown
- HATEOAS-based action menu
- API integration with TanStack Query
- Loading/error states
- Pagination

Reference: UI_WIREFRAMES_AND_MOCKUPS.md section "2. Loaders List Page"

---

## 📝 Questions to Address Tomorrow

1. Should we mock API data for Day 2, or does loader-service need updates first?
2. Do we need CORS configuration in loader-service for localhost:5173?
3. Should we implement Spring HATEOAS in backend first, or build frontend with mock links?

---

**End of Tomorrow Task Prompt**

Copy the "Session Start Prompt" section above when beginning tomorrow's session with Claude Code.
