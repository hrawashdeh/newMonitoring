# SDLC Integration Guide

## Overview

This guide explains how to manage EPICs, User Stories, Bugs, and Enhancements using markdown files that can be synchronized with external SDLC tools.

**Philosophy**:
- **Source of Truth**: Git repository (markdown files)
- **Sync Target**: External SDLC tool (Jira, Azure DevOps, Linear, etc.)
- **Workflow**: Edit in markdown → Sync to SDLC tool → Track progress → Update status back to markdown

---

## Directory Structure

```
docs/project-management/
├── SDLC_INTEGRATION_GUIDE.md     # This file
├── epics/
│   ├── EPIC-001-loader-details-page.md
│   ├── EPIC-002-real-time-monitoring.md
│   ├── EPIC-003-data-visualization.md
│   └── README.md                  # EPIC index
├── user-stories/
│   ├── US-001-view-loader-list.md
│   ├── US-002-pause-resume-loader.md
│   ├── US-003-edit-loader-config.md
│   └── README.md                  # User story index
├── bugs/
│   ├── BUG-001-login-405-error.md
│   ├── BUG-002-postgres-oom.md
│   └── README.md                  # Bug tracker index
├── enhancements/
│   ├── ENH-001-auto-refresh-ui.md
│   ├── ENH-002-dark-mode.md
│   └── README.md                  # Enhancement tracker index
├── sprints/
│   ├── sprint-01-2025-12-20.md
│   ├── sprint-02-2025-12-27.md
│   └── README.md                  # Sprint index
├── roadmap.md                     # High-level roadmap
├── known-issues.md                # Current known issues
└── decision-log.md                # Architecture decision records
```

---

## ID Numbering Convention

### EPIC IDs
**Format**: `EPIC-XXX-short-name`
**Example**: `EPIC-001-loader-management`
**Range**: 001-999

### User Story IDs
**Format**: `US-XXX-short-name`
**Example**: `US-015-pause-loader`
**Range**: 001-9999

### Bug IDs
**Format**: `BUG-XXX-short-name`
**Example**: `BUG-003-login-timeout`
**Range**: 001-9999

### Enhancement IDs
**Format**: `ENH-XXX-short-name`
**Example**: `ENH-007-dark-mode`
**Range**: 001-9999

### Sprint IDs
**Format**: `sprint-XX-YYYY-MM-DD`
**Example**: `sprint-01-2025-12-20`
**Range**: 01-99

---

## SDLC Tool Recommendations

### Option 1: Jira (Enterprise)
**Best For**: Large teams, complex workflows, enterprise environments
**Pros**:
- Industry standard
- Powerful automation
- Extensive integrations
- Advanced reporting
**Cons**:
- Expensive ($7-14/user/month)
- Complex setup
- Heavy UI
**Sync Method**:
- Jira REST API
- Python script to sync markdown ↔ Jira
- GitHub Actions automation

### Option 2: Linear (Modern, Recommended)
**Best For**: Fast-moving teams, startups, modern workflows
**Pros**:
- Lightning-fast UI
- Keyboard-first design
- Great GitHub integration
- Markdown support built-in
- Affordable ($8/user/month)
**Cons**:
- Newer (less mature than Jira)
- Fewer integrations
**Sync Method**:
- Linear API
- GraphQL-based sync
- Linear CLI

### Option 3: Azure DevOps (Microsoft Ecosystem)
**Best For**: Teams using Azure cloud, .NET projects
**Pros**:
- Free for small teams (up to 5 users)
- Tight Azure integration
- Boards + Repos + Pipelines in one
**Cons**:
- Complex UI
- Microsoft-centric
**Sync Method**:
- Azure DevOps REST API
- Azure CLI

### Option 4: GitHub Projects (Free, Simple)
**Best For**: Small teams, open-source, budget-conscious
**Pros**:
- Free
- Native GitHub integration
- Simple, clean UI
- Issues + Projects in one place
**Cons**:
- Limited features vs Jira
- Basic reporting
**Sync Method**:
- GitHub Issues = markdown files
- GitHub CLI for automation
- GitHub Actions for sync

### Option 5: ClickUp (All-in-One)
**Best For**: Teams wanting docs + tasks + chat in one tool
**Pros**:
- Free tier available
- Extremely flexible
- Includes docs, wiki, chat
- Custom fields and views
**Cons**:
- Can be overwhelming
- Performance issues at scale
**Sync Method**:
- ClickUp API
- Python/Node.js scripts

### Option 6: Notion (Documentation-First)
**Best For**: Teams prioritizing documentation + light project management
**Pros**:
- Beautiful UI
- Great for documentation
- Databases + pages
- Free for individuals
**Cons**:
- Slower than Linear
- Limited PM features vs Jira
**Sync Method**:
- Notion API
- Database sync scripts

### Option 7: Plane (Open-Source)
**Best For**: Self-hosted, privacy-focused teams
**Pros**:
- Open-source
- Self-hosted or cloud
- Similar to Linear/Jira
- Free (self-hosted)
**Cons**:
- Requires hosting
- Smaller community
**Sync Method**:
- Plane API
- Custom sync scripts

---

## Recommended Choice for This Project

### **Linear** (Primary Recommendation)
**Why**:
1. Fast, modern UI perfect for development teams
2. Native markdown support (your files map directly)
3. Excellent GitHub integration (auto-link commits, PRs)
4. Keyboard shortcuts for speed
5. Clean, simple hierarchy: Projects → EPICs → Issues
6. GraphQL API for custom sync
7. Affordable ($8/user/month or free for small teams)

### **GitHub Projects** (Budget Alternative)
**Why**:
1. Free
2. Already using GitHub for code
3. Issues can be managed via CLI
4. Simple automation with GitHub Actions
5. Good enough for small teams

---

## Sync Strategy

### Approach: Git-First (Markdown as Source of Truth)

**Workflow**:
```
1. Create/Edit in Markdown (Local)
   ↓
2. Git Commit & Push
   ↓
3. GitHub Actions Trigger
   ↓
4. Sync Script Runs
   ↓
5. Creates/Updates in SDLC Tool (Linear/Jira)
   ↓
6. Team works in SDLC Tool
   ↓
7. Status Updates
   ↓
8. Periodic Sync Back to Markdown
   ↓
9. Git Commit (Automated)
```

### Sync Script (Python + Linear Example)

```python
#!/usr/bin/env python3
"""
sync_to_linear.py - Sync markdown files to Linear
"""
import os
import yaml
import requests
from pathlib import Path

LINEAR_API_KEY = os.getenv("LINEAR_API_KEY")
LINEAR_TEAM_ID = os.getenv("LINEAR_TEAM_ID")

def parse_frontmatter(md_file):
    """Extract YAML frontmatter from markdown"""
    with open(md_file, 'r') as f:
        content = f.read()

    if content.startswith('---'):
        parts = content.split('---', 2)
        frontmatter = yaml.safe_load(parts[1])
        body = parts[2].strip()
        return frontmatter, body
    return {}, content

def sync_epic_to_linear(epic_file):
    """Create or update EPIC in Linear"""
    meta, body = parse_frontmatter(epic_file)

    # GraphQL mutation
    mutation = """
    mutation CreateProject($input: ProjectCreateInput!) {
      projectCreate(input: $input) {
        project {
          id
          name
        }
      }
    }
    """

    variables = {
        "input": {
            "teamId": LINEAR_TEAM_ID,
            "name": meta['title'],
            "description": body,
            "state": meta.get('status', 'planned').upper()
        }
    }

    response = requests.post(
        "https://api.linear.app/graphql",
        json={"query": mutation, "variables": variables},
        headers={"Authorization": LINEAR_API_KEY}
    )

    return response.json()

def sync_all_epics():
    """Sync all EPICs from docs/project-management/epics/"""
    epics_dir = Path("docs/project-management/epics")
    for epic_file in epics_dir.glob("EPIC-*.md"):
        print(f"Syncing {epic_file.name}...")
        result = sync_epic_to_linear(epic_file)
        print(f"  → Created/Updated: {result}")

if __name__ == "__main__":
    sync_all_epics()
```

### GitHub Actions Workflow

```yaml
# .github/workflows/sync-to-linear.yml
name: Sync to Linear

on:
  push:
    paths:
      - 'docs/project-management/**/*.md'
  workflow_dispatch:

jobs:
  sync:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up Python
        uses: actions/setup-python@v4
        with:
          python-version: '3.11'

      - name: Install dependencies
        run: |
          pip install pyyaml requests

      - name: Sync to Linear
        env:
          LINEAR_API_KEY: ${{ secrets.LINEAR_API_KEY }}
          LINEAR_TEAM_ID: ${{ secrets.LINEAR_TEAM_ID }}
        run: |
          python scripts/sync_to_linear.py
```

---

## Frontmatter Schema

All EPICs, User Stories, Bugs, and Enhancements use YAML frontmatter for metadata.

### Common Fields
```yaml
---
id: "EPIC-001"                    # Unique ID
title: "Loader Details Page"     # Short title
status: "in_progress"             # Status (see statuses below)
priority: "high"                  # Priority (see priorities below)
created: "2025-12-27"             # Creation date
updated: "2025-12-27"             # Last update date
assignee: "john.doe"              # Assigned person
labels: ["frontend", "ui"]        # Tags/labels
linear_id: "PRJ-123"              # External tool ID (synced)
---
```

### Status Values
- `backlog` - Not started, in backlog
- `planned` - Planned for upcoming sprint
- `in_progress` - Currently being worked on
- `blocked` - Blocked by dependency or issue
- `review` - In code review
- `testing` - In QA/testing
- `done` - Completed
- `cancelled` - Cancelled/won't do

### Priority Values
- `critical` - P0, production down
- `high` - P1, important feature/bug
- `medium` - P2, normal priority
- `low` - P3, nice to have

---

## File Templates

Templates are located in `docs/templates/`:
- `epic-template.md`
- `user-story-template.md`
- `bug-template.md`
- `enhancement-template.md`

---

## Index Files

Each subdirectory has a README.md that auto-generates a list:

**Example**: `docs/project-management/epics/README.md`
```markdown
# EPICs Index

| ID | Title | Status | Priority | Assignee |
|----|-------|--------|----------|----------|
| [EPIC-001](EPIC-001-loader-details.md) | Loader Details Page | in_progress | high | john |
| [EPIC-002](EPIC-002-real-time.md) | Real-Time Monitoring | planned | medium | jane |
```

### Auto-Generation Script
```python
#!/usr/bin/env python3
"""generate_indexes.py - Auto-generate README indexes"""
import os
from pathlib import Path

def generate_epic_index():
    epics_dir = Path("docs/project-management/epics")
    epics = []

    for epic_file in sorted(epics_dir.glob("EPIC-*.md")):
        meta, _ = parse_frontmatter(epic_file)
        epics.append({
            'id': meta['id'],
            'file': epic_file.name,
            'title': meta['title'],
            'status': meta['status'],
            'priority': meta.get('priority', 'medium'),
            'assignee': meta.get('assignee', 'unassigned')
        })

    # Generate markdown table
    with open(epics_dir / "README.md", "w") as f:
        f.write("# EPICs Index\n\n")
        f.write("| ID | Title | Status | Priority | Assignee |\n")
        f.write("|----|-------|--------|----------|----------|\n")
        for epic in epics:
            f.write(f"| [{epic['id']}]({epic['file']}) | {epic['title']} | "
                   f"{epic['status']} | {epic['priority']} | {epic['assignee']} |\n")

if __name__ == "__main__":
    generate_epic_index()
    # Similar for user-stories, bugs, enhancements
```

---

## Integration Examples

### Linear Integration
```bash
# Install Linear CLI
npm install -g @linear/cli

# Authenticate
linear login

# Create issue from markdown
linear issue create \
  --title "$(grep '^title:' EPIC-001.md | cut -d':' -f2)" \
  --description "$(tail -n +10 EPIC-001.md)" \
  --priority high

# List issues
linear issue list --filter "status:in_progress"
```

### GitHub Projects Integration
```bash
# Install GitHub CLI
brew install gh

# Create issue
gh issue create \
  --title "Loader Details Page" \
  --body-file docs/project-management/epics/EPIC-001.md \
  --label "epic" \
  --assignee "@me"

# Add to project
gh project item-add <project-number> \
  --owner <org> \
  --url <issue-url>
```

### Jira Integration
```bash
# Install Jira CLI
pip install jira-cli

# Create epic
jira create epic \
  --project MON \
  --summary "Loader Details Page" \
  --description "$(cat docs/project-management/epics/EPIC-001.md)"
```

---

## Workflow Best Practices

### Daily Workflow
1. Pull latest changes: `git pull`
2. Check your assigned tasks: Review `epics/README.md`, `user-stories/README.md`
3. Update status in frontmatter when starting work
4. Commit and push: Status syncs to Linear/Jira
5. Work in SDLC tool during the day
6. End of day: Sync back status to markdown (automated)

### Sprint Planning
1. Create new sprint file: `docs/project-management/sprints/sprint-XX.md`
2. Link EPICs and User Stories for the sprint
3. Set sprint goals and dates
4. Sync to SDLC tool
5. Track progress in SDLC tool
6. Update sprint retrospective at end

### Bug Triage
1. Create bug file: `docs/project-management/bugs/BUG-XXX.md`
2. Set priority (critical/high/medium/low)
3. Assign to developer
4. Sync to SDLC tool
5. Developer updates status as they work
6. Close when deployed

---

## Reporting

### Generate Sprint Report
```bash
# Count stories by status in current sprint
grep -r "^status:" docs/project-management/user-stories/*.md | \
  sort | uniq -c

# Output:
#  5 status: done
#  3 status: in_progress
#  2 status: blocked
```

### Generate Burndown Data
```python
# scripts/generate_burndown.py
import yaml
from pathlib import Path
import pandas as pd

def count_stories_by_status(sprint_id):
    stories_dir = Path("docs/project-management/user-stories")
    statuses = {}

    for story_file in stories_dir.glob("US-*.md"):
        meta, _ = parse_frontmatter(story_file)
        if meta.get('sprint') == sprint_id:
            status = meta['status']
            statuses[status] = statuses.get(status, 0) + 1

    return statuses

# Output CSV for burndown chart
print("date,todo,in_progress,done")
print(f"2025-12-27,10,5,15")
```

---

## Migration from KNOWN_ISSUES.md

Current `KNOWN_ISSUES.md` contains mixed content. Here's how to migrate:

**Extract EPICs**:
- "Implement execution history table" → `EPIC-001-execution-history.md`
- "Data Visualization with Charts" → `EPIC-002-data-visualization.md`
- "Full CRUD Operations" → `EPIC-003-crud-operations.md`

**Extract User Stories**:
- "Pause/Resume loader from list page" → `US-001-pause-resume.md`
- "Edit loader configuration" → `US-002-edit-config.md`

**Extract Bugs**:
- "PostgreSQL OOMKilled" → `BUG-001-postgres-oom.md` (CLOSED)
- "Login 405 error" → `BUG-002-login-405.md` (CLOSED)

**Keep in KNOWN_ISSUES.md**:
- Technical debt summary
- High-level roadmap overview
- Link to detailed EPICs/stories

---

## Next Steps

1. ✅ Choose SDLC tool (Recommended: Linear or GitHub Projects)
2. ✅ Create sync script using templates above
3. ✅ Set up GitHub Actions for automation
4. ✅ Migrate existing KNOWN_ISSUES.md to EPICs/Stories
5. ✅ Start using markdown-first workflow
6. ✅ Generate weekly reports

---

**Last Updated**: 2025-12-27
**Maintained By**: Project Management Team
