# ETL Monitoring System - Documentation Hub

Welcome to the ETL Monitoring System documentation! This directory contains all project documentation organized by category for easy navigation.

---

## 🚀 Quick Start

### For New Users
- **[Getting Started](user-guides/getting-started.md)** - First time using the system? Start here!
- **[Loader Management Guide](user-guides/loader-table-reference.md)** - Complete guide to managing loaders

### For Developers
- **[Local Development Setup](developer-guides/setup-local-development.md)** - Set up your dev environment
- **[Contributing Guidelines](developer-guides/contributing.md)** - How to contribute to the project

### For DevOps/SRE
- **[Deployment Guide](deployment/deployment-guide.md)** - Deploy the system to Kubernetes
- **[Runbooks](runbooks/README.md)** - Operational procedures for common issues

---

## 📚 Documentation Categories

### 🏗️ [Architecture](architecture/README.md)
System design, architecture decisions, and high-level overviews
- System Architecture Overview
- Microservices Communication
- Database Schema Design
- Security Architecture

### 📖 [User Guides](user-guides/README.md)
End-user documentation for operators and administrators
- **[Loader Table Reference](user-guides/loader-table-reference.md)** - Complete field guide (19KB)
- Troubleshooting Guide
- Best Practices
- FAQ

### 💻 [Developer Guides](developer-guides/README.md)
Developer onboarding, contribution guides, and coding standards
- **[Field Protection Guide](developer-guides/FIELD_PROTECTION_GUIDE.md)** - Role-based field protection implementation
- Local Development Setup
- Code Structure Walkthrough
- Testing Guide
- Git Workflow

### 🚢 [Deployment](deployment/README.md)
Deployment procedures, infrastructure setup, and operations
- **[Deployment Guide](deployment/deployment-guide.md)** - Complete deployment instructions
- Kubernetes Setup
- Database Setup
- CI/CD Pipeline

### 🔌 [API Reference](api-reference/README.md)
REST API documentation and specifications
- Loader API Endpoints
- Authentication API
- Gateway API
- OpenAPI Specification

### 🎨 [UI Reference](ui-reference/README.md)
Frontend component library and UI design patterns
- **[Loader Table UI Guide](ui-reference/loader-table-ui-guide.md)** - Field presentation guide (32KB)
- Component Library
- Page Layouts
- Design System

### 🗄️ [Database](database/README.md)
Database schema, data dictionary, and migration guides
- Schema Overview
- Table Documentation
- Migration Guide
- Query Optimization

### 🚨 [Runbooks](runbooks/README.md)
Step-by-step operational procedures for common tasks
- Incident Response
- Backup & Restore
- Scaling Procedures
- Common Issues Resolution

### 📋 [Project Management](project-management/README.md)
EPICs, user stories, bugs, enhancements, and project tracking

**SDLC Integration**:
- **[SDLC Integration Guide](project-management/SDLC_INTEGRATION_GUIDE.md)** - Sync with Jira/Linear/GitHub

**Categories**:
- **[EPICs](project-management/epics/README.md)** - Major features and initiatives
- **[User Stories](project-management/user-stories/README.md)** - Detailed user requirements
- **[Bugs](project-management/bugs/README.md)** - Bug tracking and resolution
- **[Enhancements](project-management/enhancements/README.md)** - Improvements and optimizations
- **[Sprints](project-management/sprints/README.md)** - Sprint planning and retrospectives
- **[Known Issues](project-management/known-issues.md)** - Current known issues
- **[Roadmap](project-management/roadmap.md)** - High-level product roadmap
- **[Decision Log](project-management/decision-log.md)** - Architecture decision records

### 📦 [Archive](archive/README.md)
Historical documentation and completed task summaries
- Completed migration summaries
- Old architecture (before refactors)
- Historical incident reports

### 📝 [Templates](templates/README.md)
Reusable document templates for consistency
- **[EPIC Template](templates/epic-template.md)**
- **[User Story Template](templates/user-story-template.md)**
- **[Bug Template](templates/bug-template.md)**
- **[Enhancement Template](templates/enhancement-template.md)**

---

## 🎯 Common Tasks

### I want to...
- **Understand the system architecture** → [Architecture Overview](architecture/system-overview.md)
- **Deploy to Kubernetes** → [Deployment Guide](deployment/deployment-guide.md)
- **Understand loader table fields** → [Loader Table Reference](user-guides/loader-table-reference.md)
- **Know how to display UI fields** → [Loader Table UI Guide](ui-reference/loader-table-ui-guide.md)
- **Report a bug** → [Bug Template](templates/bug-template.md) → [Create bug](project-management/bugs/)
- **Propose an enhancement** → [Enhancement Template](templates/enhancement-template.md)
- **Check what's next** → [Roadmap](project-management/roadmap.md) & [Known Issues](project-management/known-issues.md)
- **Fix a production issue** → [Runbooks](runbooks/README.md)

---

## 📊 Project Status

### Current Release: v1.1.0
**Deployed**: 2025-12-27

**Recent Completions**:
- ✅ Loader Details Page (EPIC-001)
- ✅ Authentication Service
- ✅ API Gateway Integration
- ✅ React Frontend POC

**In Progress**:
- 🚧 Execution History Implementation
- 🚧 Data Visualization with Charts
- 🚧 Full CRUD Operations

**Next Up**:
- 📅 Real-Time Monitoring
- 📅 WebSocket Integration
- 📅 Advanced Analytics

See [Roadmap](project-management/roadmap.md) for details.

---

## 🔧 Documentation Standards

### Writing Guidelines
1. **Clarity First**: Use clear, concise language
2. **Code Examples**: Include code snippets where applicable
3. **Diagrams**: Add diagrams for complex concepts (use Mermaid)
4. **Keep Current**: Update docs when code changes
5. **Use Templates**: Follow templates from `templates/` directory

### Formatting Standards
- Use markdown for all documentation
- Follow [Markdown Style Guide](https://www.markdownguide.org/)
- Code blocks must specify language (```typescript, ```sql, etc.)
- Use frontmatter for metadata (YAML)
- Maximum line length: 120 characters (except code blocks)

### Document Lifecycle
1. **Draft** ✍️ - Work in progress, may have TODOs
2. **Review** 👀 - Ready for peer review
3. **Published** ✅ - Approved and current
4. **Archived** 📦 - Moved to archive/ when deprecated

### File Naming Conventions
- Use lowercase with hyphens: `loader-table-reference.md`
- Descriptive names: `deployment-guide.md` not `deploy.md`
- Date prefix for archives: `2025-12-24-task-name.md`

---

## 🤝 Contributing to Documentation

### How to Update Docs
1. **Edit**: Update the relevant markdown file
2. **Test**: Verify links work, code examples run
3. **Commit**: Use clear commit message (e.g., "docs: update loader API reference")
4. **PR**: Submit pull request for review
5. **Review**: Documentation PRs require approval (same as code)

### Creating New Documents
1. Check if template exists in `templates/`
2. Copy template to appropriate category folder
3. Fill in all sections
4. Update category README index
5. Link from master README (if high priority)

### Questions or Issues?
- **Slack**: #documentation channel
- **GitHub Issues**: Tag with "documentation" label
- **Email**: docs@team.com

---

## 🔍 Search & Navigation

### Finding Documentation
- **Use GitHub Search**: Search across all markdown files
- **Category READMEs**: Each folder has an index
- **This File**: Master index with quick links
- **VS Code**: Use `Ctrl+P` to find files quickly

### Documentation Tools
- **Markdown Preview**: VS Code has built-in preview
- **Link Checker**: Run `npm run docs:lint` to check broken links
- **Spell Check**: VS Code + Code Spell Checker extension
- **Mermaid Preview**: VS Code + Markdown Preview Mermaid extension

---

## 📈 Documentation Metrics

### Coverage
- **Architecture**: 60% complete
- **User Guides**: 40% complete
- **API Reference**: 30% complete
- **Deployment**: 90% complete
- **Runbooks**: 20% complete

### Priorities
1. 🔴 **Critical Gaps**: API reference, runbooks for common issues
2. 🟡 **Medium Priority**: Architecture diagrams, testing guides
3. 🟢 **Nice to Have**: Advanced tutorials, video walkthroughs

---

## 🛠️ Tooling & Automation

### Documentation Tools in Use
- **Markdown**: All documentation
- **Mermaid**: Diagrams as code
- **OpenAPI**: API specifications
- **GitHub Actions**: Link checking, spell checking
- **Docusaurus** (planned): Static site generator

### Automation
- **Link Checking**: GitHub Actions runs on every PR
- **Spell Checking**: VS Code extension
- **Index Generation**: Scripts to auto-generate category indexes
- **SDLC Sync**: Auto-sync to Linear/Jira (in progress)

---

## 📞 Support

### Getting Help
- **Documentation Issues**: File GitHub issue with "documentation" label
- **Technical Questions**: See [Developer Guides](developer-guides/README.md)
- **Operational Issues**: See [Runbooks](runbooks/README.md)
- **Feature Requests**: Create [Enhancement](project-management/enhancements/)

### Maintainers
- **Architecture**: Technical Lead Team
- **User Guides**: Product Team
- **API Reference**: Backend Team
- **UI Reference**: Frontend Team
- **Deployment**: DevOps Team
- **Project Management**: Product Manager

---

## 📜 Change Log

### 2025-12-27
- ✅ Created comprehensive documentation structure
- ✅ Migrated all existing docs to new structure
- ✅ Created templates for EPIC, User Stories, Bugs, Enhancements
- ✅ Added SDLC integration guide (Linear, Jira, GitHub Projects)
- ✅ Archived completed task documentation
- ✅ Created master documentation index

### Previous Changes
- See Git history for detailed change log

---

## 🗺️ Documentation Roadmap

### Next Steps
- [ ] Generate architecture diagrams (Mermaid)
- [ ] Complete API reference (OpenAPI spec)
- [ ] Create video tutorials for common tasks
- [ ] Set up Docusaurus for web view
- [ ] Implement SDLC sync automation
- [ ] Create comprehensive runbooks

---

**Last Updated**: 2025-12-27
**Version**: 1.0
**Maintained By**: ETL Monitoring Team

For questions or feedback about this documentation, please open an issue or contact the documentation team.
