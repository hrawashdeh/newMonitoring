# Documentation Structure Proposal

## Current Situation

We currently have **9+ markdown files** scattered in the root directory:

**Root Directory**:
- AUTHENTICATION_CLEANUP_SUMMARY.md
- AUTH_DEPLOYMENT_VERIFICATION.md
- AUTH_SERVICE_SUMMARY.md
- FINAL_AUTH_STATUS.md
- GATEWAY_DEPLOYMENT_TASK.md
- KNOWN_ISSUES.md
- DEPLOYMENT.md
- LOADER_TABLE_USER_GUIDE.md
- LOADER_DATABASE_TABLE_UI_REFERENCE.md
- LOADER_LIST_PAGE_USER_GUIDE.md

**Problems**:
1. ❌ Root directory is cluttered
2. ❌ No clear categorization
3. ❌ Hard to find specific documentation
4. ❌ No master index or navigation
5. ❌ Mixing temporary docs (AUTH summaries) with permanent guides
6. ❌ No versioning or archiving strategy

---

## Proposed Structure

```
newLoader/
├── README.md                           # Main project README
├── docs/                               # ALL DOCUMENTATION HERE
│   ├── README.md                       # Documentation index/hub
│   │
│   ├── architecture/                   # System design & architecture
│   │   ├── README.md
│   │   ├── system-overview.md
│   │   ├── microservices-architecture.md
│   │   ├── database-schema.md
│   │   └── security-design.md
│   │
│   ├── user-guides/                    # End-user documentation
│   │   ├── README.md
│   │   ├── getting-started.md
│   │   ├── loader-management.md
│   │   ├── loader-table-reference.md  # ← LOADER_TABLE_USER_GUIDE.md
│   │   └── troubleshooting.md
│   │
│   ├── developer-guides/               # Developer documentation
│   │   ├── README.md
│   │   ├── setup-local-development.md
│   │   ├── coding-standards.md
│   │   ├── testing-guide.md
│   │   └── contributing.md
│   │
│   ├── deployment/                     # Deployment & operations
│   │   ├── README.md
│   │   ├── deployment-guide.md         # ← DEPLOYMENT.md
│   │   ├── kubernetes-setup.md
│   │   ├── database-setup.md
│   │   └── monitoring-setup.md
│   │
│   ├── api-reference/                  # API documentation
│   │   ├── README.md
│   │   ├── loader-api.md
│   │   ├── auth-api.md
│   │   ├── gateway-api.md
│   │   └── openapi.yaml
│   │
│   ├── ui-reference/                   # Frontend/UI documentation
│   │   ├── README.md
│   │   ├── component-library.md
│   │   ├── loader-table-ui-guide.md    # ← LOADER_DATABASE_TABLE_UI_REFERENCE.md
│   │   ├── page-layouts.md
│   │   └── design-system.md
│   │
│   ├── database/                       # Database documentation
│   │   ├── README.md
│   │   ├── schema-overview.md
│   │   ├── loader-table-schema.md
│   │   ├── migrations.md
│   │   └── data-dictionary.md
│   │
│   ├── runbooks/                       # Operational runbooks
│   │   ├── README.md
│   │   ├── incident-response.md
│   │   ├── backup-restore.md
│   │   ├── scaling.md
│   │   └── common-issues.md            # ← Extract from KNOWN_ISSUES.md
│   │
│   ├── project-management/             # Project tracking & planning
│   │   ├── README.md
│   │   ├── roadmap.md
│   │   ├── known-issues.md             # ← KNOWN_ISSUES.md
│   │   ├── sprint-planning.md
│   │   └── decision-log.md
│   │
│   ├── archive/                        # Historical/deprecated docs
│   │   ├── README.md
│   │   ├── 2025-12-24-auth-cleanup.md  # ← AUTHENTICATION_CLEANUP_SUMMARY.md
│   │   ├── 2025-12-24-auth-deployment.md # ← AUTH_DEPLOYMENT_VERIFICATION.md
│   │   ├── 2025-12-24-auth-service.md  # ← AUTH_SERVICE_SUMMARY.md
│   │   ├── 2025-12-24-final-auth.md    # ← FINAL_AUTH_STATUS.md
│   │   └── 2025-12-24-gateway-task.md  # ← GATEWAY_DEPLOYMENT_TASK.md
│   │
│   └── templates/                      # Document templates
│       ├── feature-spec-template.md
│       ├── runbook-template.md
│       ├── api-endpoint-template.md
│       └── user-guide-template.md
│
├── frontend/
├── services/
├── infra/
└── ...
```

---

## Category Descriptions

### 1. `/docs/architecture/`
**Purpose**: System design, architecture decisions, high-level overviews
**Audience**: Architects, senior developers, technical leads
**Examples**:
- System diagrams
- Microservices communication patterns
- Database schema ER diagrams
- Security architecture
- Technology stack decisions

### 2. `/docs/user-guides/`
**Purpose**: End-user documentation for operators and administrators
**Audience**: Operations team, system administrators, power users
**Examples**:
- How to create/edit loaders
- How to troubleshoot failed loaders
- Understanding loader table fields
- Dashboard usage
- Monitoring best practices

### 3. `/docs/developer-guides/`
**Purpose**: Developer onboarding and contribution guides
**Audience**: Software developers (internal team + contributors)
**Examples**:
- Local development setup
- Code structure walkthrough
- Testing guide
- Git workflow
- PR review checklist

### 4. `/docs/deployment/`
**Purpose**: Deployment procedures, infrastructure setup, operations
**Audience**: DevOps engineers, SREs, deployment team
**Examples**:
- Kubernetes cluster setup
- Database initialization
- CI/CD pipeline configuration
- Environment-specific configs (dev/staging/prod)
- Rollback procedures

### 5. `/docs/api-reference/`
**Purpose**: REST API documentation
**Audience**: Frontend developers, API consumers, integrators
**Examples**:
- Endpoint specifications (GET/POST/PUT/DELETE)
- Request/response formats
- Authentication flow
- Error codes
- OpenAPI/Swagger specs

### 6. `/docs/ui-reference/`
**Purpose**: Frontend component library and UI design patterns
**Audience**: Frontend developers, UX designers
**Examples**:
- Component usage (buttons, tables, forms)
- Page layout templates
- Data presentation guidelines (how to display each field)
- Design system (colors, typography, spacing)
- Accessibility guidelines

### 7. `/docs/database/`
**Purpose**: Database schema, data dictionary, migration guides
**Audience**: Backend developers, DBAs, data engineers
**Examples**:
- Complete schema diagrams
- Table-by-table documentation
- Index strategies
- Migration history
- Query optimization tips

### 8. `/docs/runbooks/`
**Purpose**: Step-by-step operational procedures for common tasks
**Audience**: On-call engineers, operations team
**Examples**:
- "Loader stuck in RUNNING state - what to do"
- "Database backup and restore"
- "Scale up during high load"
- "Deploy new version safely"
- "Rollback procedure"

### 9. `/docs/project-management/`
**Purpose**: Project tracking, roadmap, known issues, planning
**Audience**: Product managers, project leads, entire team
**Examples**:
- Feature roadmap
- Known issues and bugs
- Technical debt tracker
- Sprint planning docs
- Decision log (why we chose X over Y)

### 10. `/docs/archive/`
**Purpose**: Historical documentation, completed task summaries
**Audience**: Reference only (rarely accessed)
**Examples**:
- Completed migration summaries
- Old architecture (before refactor)
- Deprecated API versions
- Historical incident reports

### 11. `/docs/templates/`
**Purpose**: Reusable document templates for consistency
**Audience**: All contributors
**Examples**:
- Feature specification template
- API endpoint documentation template
- Runbook template
- Architecture decision record (ADR) template

---

## Document Naming Convention

### Format: `{category}-{topic}-{type}.md`

**Categories**:
- `arch-` = Architecture
- `guide-` = User/Developer Guide
- `api-` = API Reference
- `db-` = Database
- `deploy-` = Deployment
- `runbook-` = Runbook
- `ui-` = UI Reference

**Examples**:
```
✓ arch-microservices-overview.md
✓ guide-loader-management.md
✓ api-loader-endpoints.md
✓ db-loader-table-schema.md
✓ deploy-kubernetes-setup.md
✓ runbook-loader-stuck-running.md
✓ ui-loader-table-display.md
```

### Alternative: Natural Names (Recommended for User-Facing Docs)
```
✓ getting-started.md
✓ loader-management-guide.md
✓ database-schema.md
✓ deployment-guide.md
```

---

## Master Documentation Index

**File**: `/docs/README.md`

```markdown
# ETL Monitoring System - Documentation Hub

Welcome to the ETL Monitoring System documentation!

## Quick Links

### 🚀 Getting Started
- [Getting Started Guide](user-guides/getting-started.md)
- [Local Development Setup](developer-guides/setup-local-development.md)
- [Deployment Guide](deployment/deployment-guide.md)

### 📚 User Guides
- [Loader Management Guide](user-guides/loader-management.md)
- [Loader Table Reference](user-guides/loader-table-reference.md)
- [Troubleshooting](user-guides/troubleshooting.md)

### 🏗️ Architecture
- [System Overview](architecture/system-overview.md)
- [Microservices Architecture](architecture/microservices-architecture.md)
- [Database Schema](architecture/database-schema.md)
- [Security Design](architecture/security-design.md)

### 💻 Developer Guides
- [Setup Local Development](developer-guides/setup-local-development.md)
- [Coding Standards](developer-guides/coding-standards.md)
- [Testing Guide](developer-guides/testing-guide.md)
- [Contributing](developer-guides/contributing.md)

### 🔌 API Reference
- [Loader API](api-reference/loader-api.md)
- [Authentication API](api-reference/auth-api.md)
- [Gateway API](api-reference/gateway-api.md)
- [OpenAPI Specification](api-reference/openapi.yaml)

### 🎨 UI Reference
- [Component Library](ui-reference/component-library.md)
- [Loader Table UI Guide](ui-reference/loader-table-ui-guide.md)
- [Page Layouts](ui-reference/page-layouts.md)
- [Design System](ui-reference/design-system.md)

### 🗄️ Database
- [Schema Overview](database/schema-overview.md)
- [Loader Table Schema](database/loader-table-schema.md)
- [Migrations](database/migrations.md)
- [Data Dictionary](database/data-dictionary.md)

### 🚨 Runbooks
- [Incident Response](runbooks/incident-response.md)
- [Backup & Restore](runbooks/backup-restore.md)
- [Scaling](runbooks/scaling.md)
- [Common Issues](runbooks/common-issues.md)

### 📋 Project Management
- [Roadmap](project-management/roadmap.md)
- [Known Issues](project-management/known-issues.md)
- [Sprint Planning](project-management/sprint-planning.md)
- [Decision Log](project-management/decision-log.md)

### 🚢 Deployment
- [Deployment Guide](deployment/deployment-guide.md)
- [Kubernetes Setup](deployment/kubernetes-setup.md)
- [Database Setup](deployment/database-setup.md)
- [Monitoring Setup](deployment/monitoring-setup.md)

### 📦 Archive
- [Historical Documents](archive/README.md)

---

## Documentation Standards

### Writing Guidelines
- Use clear, concise language
- Include code examples where applicable
- Add diagrams for complex concepts
- Keep documentation up-to-date with code changes
- Use consistent formatting (see [templates](templates/))

### Document Lifecycle
1. **Draft** - Work in progress, may have TODOs
2. **Review** - Ready for peer review
3. **Published** - Approved and current
4. **Archived** - Moved to archive/ when deprecated

### Contribution
- All documentation changes require PR review
- Update relevant docs when changing code
- Use templates from `templates/` directory
- Follow naming conventions

---

**Last Updated**: 2025-12-27
**Maintained By**: ETL Monitoring Team
```

---

## Migration Plan

### Phase 1: Create Structure (Immediate)
```bash
# Create directory structure
mkdir -p docs/{architecture,user-guides,developer-guides,deployment,api-reference,ui-reference,database,runbooks,project-management,archive,templates}

# Create README files for each category
for dir in docs/*/; do
  touch "${dir}README.md"
done

# Create master documentation index
touch docs/README.md
```

### Phase 2: Move Existing Documents (Next)
```bash
# Move permanent documentation
mv DEPLOYMENT.md docs/deployment/deployment-guide.md
mv KNOWN_ISSUES.md docs/project-management/known-issues.md
mv LOADER_TABLE_USER_GUIDE.md docs/user-guides/loader-table-reference.md
mv LOADER_DATABASE_TABLE_UI_REFERENCE.md docs/ui-reference/loader-table-ui-guide.md

# Archive temporary/completed task docs (with date prefix for sorting)
mv AUTHENTICATION_CLEANUP_SUMMARY.md docs/archive/2025-12-24-auth-cleanup-summary.md
mv AUTH_DEPLOYMENT_VERIFICATION.md docs/archive/2025-12-24-auth-deployment-verification.md
mv AUTH_SERVICE_SUMMARY.md docs/archive/2025-12-24-auth-service-summary.md
mv FINAL_AUTH_STATUS.md docs/archive/2025-12-24-final-auth-status.md
mv GATEWAY_DEPLOYMENT_TASK.md docs/archive/2025-12-24-gateway-deployment-task.md

# Remove the UI list page guide (not needed, was created by mistake)
rm LOADER_LIST_PAGE_USER_GUIDE.md
```

### Phase 3: Create Missing Documentation (Ongoing)
- Architecture overview
- Getting started guide
- API reference for all endpoints
- Runbooks for common issues
- Developer setup guide

### Phase 4: Update References (Critical)
```bash
# Update all cross-references in existing docs
# Example: Change links from:
#   See DEPLOYMENT.md
# To:
#   See [Deployment Guide](deployment/deployment-guide.md)
```

---

## Documentation Maintenance Strategy

### 1. Version Control
- All documentation in Git
- Tag releases with version numbers
- Maintain CHANGELOG.md for documentation updates

### 2. Review Process
- Documentation PRs require review (same as code)
- Use GitHub Pages or similar for published docs
- CI/CD checks for broken links

### 3. Automation
```yaml
# .github/workflows/docs-lint.yml
name: Documentation Linting
on: [pull_request]
jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Check broken links
        uses: gaurav-nelson/github-action-markdown-link-check@v1
      - name: Spell check
        uses: rojopolis/spellcheck-github-actions@v0
```

### 4. Templates
Create standard templates for consistency:
- Feature specification
- API endpoint documentation
- Runbook template
- Architecture decision record (ADR)

### 5. Archiving Strategy
- Move completed task docs to `archive/` with date prefix
- Keep for historical reference (6-12 months)
- Purge old archives annually (keep in Git history)

---

## Recommended Tools

### Documentation Generation
- **Docusaurus** - Documentation website generator
- **MkDocs** - Python-based static site generator
- **VitePress** - Vite-powered static site generator

### Diagrams
- **Mermaid** - Diagrams in markdown
- **PlantUML** - UML diagrams as code
- **Draw.io** - Visual diagrams (export as SVG)

### API Documentation
- **Swagger/OpenAPI** - REST API specs
- **Redoc** - OpenAPI renderer
- **Postman** - API collection & documentation

### Link Checking
- **markdown-link-check** - Validate links in markdown
- **linkchecker** - Check for broken links

---

## Benefits of This Structure

### ✅ Organization
- Clear categorization by audience and purpose
- Easy to find specific documentation
- Scales as project grows

### ✅ Maintainability
- Separation of concerns (API vs UI vs deployment)
- Templates ensure consistency
- Archive strategy prevents clutter

### ✅ Discoverability
- Master index provides clear navigation
- Category READMEs guide users
- Consistent naming makes searching easier

### ✅ Collaboration
- Clear contribution guidelines
- Review process ensures quality
- Version control tracks changes

### ✅ Professional
- Industry-standard structure
- Suitable for open-source projects
- Impressive for new team members

---

## Alternative Structures (Considered)

### Option A: Flat Structure (Rejected)
```
docs/
├── deployment-guide.md
├── loader-table-reference.md
├── api-loader-endpoints.md
└── ... (100+ files all in one directory)
```
❌ **Problems**: Hard to navigate, doesn't scale, no organization

### Option B: By Service (Rejected)
```
docs/
├── loader-service/
├── auth-service/
├── gateway/
└── frontend/
```
❌ **Problems**: Duplicates content (deployment across all services), doesn't fit cross-cutting docs

### Option C: By Audience Only (Rejected)
```
docs/
├── users/
├── developers/
├── operators/
└── architects/
```
❌ **Problems**: Same doc might fit multiple audiences, unclear where to put API docs

---

## Next Steps

1. **Get User Approval** - Review this proposal with team/user
2. **Create Structure** - Run Phase 1 commands
3. **Move Documents** - Run Phase 2 commands
4. **Update Cross-References** - Fix all internal links
5. **Create Master Index** - Write comprehensive README
6. **Fill Gaps** - Identify and create missing documentation
7. **Automate** - Set up link checking and spell checking
8. **Publish** - Consider GitHub Pages or Docusaurus for web view

---

**Proposal Version**: 1.0
**Date**: 2025-12-27
**Status**: PENDING APPROVAL
