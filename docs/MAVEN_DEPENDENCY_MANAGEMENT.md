# Maven Dependency Management - Missing JAR Fix

## Problem Statement

When deploying on a new machine, `signal-loader` service fails to build because it depends on `approval-workflow-core.jar`, which is not tracked in Git (excluded by `.gitignore`).

**Error Message:**
```
[ERROR] Failed to execute goal on project loader:
Could not resolve dependencies for project com.tiqmo.monitoring:loader:jar:0.0.1-SNAPSHOT:
Could not find artifact com.tiqmo.monitoring:approval-workflow-core:jar:1.0.0
```

---

## Root Cause

1. **Dependency Chain**: `loader` → `approval-workflow-core` (Maven dependency)
2. **Git Exclusion**: `*.jar` files excluded from Git (to keep repository clean)
3. **Build Order Issue**: `app_installer.sh` builds `loader` without first building `approval-workflow-core`

---

## ✅ Solution 1: Fixed Installer (APPLIED)

**Status**: ✅ **Already implemented in `app_installer.sh`**

The installer now builds dependencies in correct order:

```bash
# Step 1: Build and install approval-workflow-core to local Maven repo
cd services/approval-workflow-core
mvn clean install -Dmaven.test.skip=true

# Step 2: Build loader (now finds approval-workflow-core in ~/.m2/repository)
cd services/loader
mvn clean package -Dmaven.test.skip=true
```

**How it Works**:
- `mvn install` compiles the JAR and installs it to `~/.m2/repository/com/tiqmo/monitoring/approval-workflow-core/1.0.0/`
- When `loader` builds, Maven finds the dependency in local repository
- **No JAR files need to be committed to Git**

**Verification**:
```bash
# Check if JAR is installed locally
ls -lh ~/.m2/repository/com/tiqmo/monitoring/approval-workflow-core/1.0.0/

# Expected output:
# approval-workflow-core-1.0.0.jar
# approval-workflow-core-1.0.0.pom
```

---

## 🔄 Solution 2: Multi-Module Parent POM (RECOMMENDED for Production)

**When to Use**: For CI/CD pipelines, Jenkins, GitHub Actions

Create a parent POM to manage all services as a single Maven reactor build.

### Implementation

**Step 1**: Create `/Volumes/Files/Projects/newLoader/services/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.tiqmo.monitoring</groupId>
    <artifactId>monitoring-platform-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>ETL Monitoring Platform - Parent POM</name>
    <description>Multi-module build for all microservices</description>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <spring-boot.version>3.4.1</spring-boot.version>
    </properties>

    <modules>
        <!-- Build order matters: dependencies first -->
        <module>approval-workflow-core</module>
        <module>loader</module>
        <module>auth-service</module>
        <module>gateway</module>
        <module>import-export-service</module>
        <module>etl_initializer</module>
        <module>dataGenerator</module>
    </modules>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot.version}</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

**Step 2**: Update each child POM to reference parent:

```xml
<!-- In services/loader/pom.xml -->
<parent>
    <groupId>com.tiqmo.monitoring</groupId>
    <artifactId>monitoring-platform-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
</parent>
```

**Step 3**: Build all services with single command:

```bash
cd /Volumes/Files/Projects/newLoader/services
mvn clean install -Dmaven.test.skip=true

# Maven automatically:
# 1. Builds approval-workflow-core first
# 2. Installs it to local repo
# 3. Builds loader (finds dependency)
# 4. Builds remaining services in dependency order
```

**Benefits**:
- ✅ Automatic dependency resolution
- ✅ Single command builds entire platform
- ✅ CI/CD friendly
- ✅ Version management in one place

---

## 📦 Solution 3: Maven Repository Manager (Enterprise Solution)

**When to Use**: Multi-team environments, production deployments

Deploy a Maven repository manager like **Nexus** or **Artifactory**.

### Architecture

```
Developer Machine 1          Maven Repository (Nexus)       Developer Machine 2
─────────────────           ─────────────────────────       ─────────────────
Build workflow-core    ──►  Upload JAR to Nexus      ◄──   Download JAR
                                                            Build loader
```

### Setup Nexus (Self-Hosted)

**Step 1**: Deploy Nexus to Kubernetes:

```bash
kubectl create namespace nexus

# Deploy Nexus
kubectl apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nexus
  namespace: nexus
spec:
  replicas: 1
  selector:
    matchLabels:
      app: nexus
  template:
    metadata:
      labels:
        app: nexus
    spec:
      containers:
      - name: nexus
        image: sonatype/nexus3:3.60.0
        ports:
        - containerPort: 8081
        volumeMounts:
        - name: nexus-data
          mountPath: /nexus-data
      volumes:
      - name: nexus-data
        persistentVolumeClaim:
          claimName: nexus-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: nexus-service
  namespace: nexus
spec:
  type: NodePort
  ports:
  - port: 8081
    targetPort: 8081
    nodePort: 30081
  selector:
    app: nexus
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: nexus-pvc
  namespace: nexus
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi
EOF
```

**Step 2**: Configure Maven `settings.xml` (~/.m2/settings.xml):

```xml
<settings>
  <servers>
    <server>
      <id>nexus-releases</id>
      <username>admin</username>
      <password>admin123</password>
    </server>
  </servers>

  <mirrors>
    <mirror>
      <id>nexus</id>
      <mirrorOf>*</mirrorOf>
      <url>http://localhost:30081/repository/maven-public/</url>
    </mirror>
  </mirrors>
</settings>
```

**Step 3**: Configure service POM to deploy to Nexus:

```xml
<!-- In services/approval-workflow-core/pom.xml -->
<distributionManagement>
  <repository>
    <id>nexus-releases</id>
    <url>http://localhost:30081/repository/maven-releases/</url>
  </repository>
</distributionManagement>
```

**Step 4**: Deploy artifacts:

```bash
cd services/approval-workflow-core
mvn clean deploy  # Uploads JAR to Nexus

# On any other machine:
cd services/loader
mvn clean package  # Downloads workflow-core JAR from Nexus automatically
```

**Benefits**:
- ✅ Centralized artifact storage
- ✅ Team collaboration (shared JARs)
- ✅ Version control for binaries
- ✅ Offline builds (cached dependencies)

---

## 🚀 Solution 4: Manual Build Script (Quick Workaround)

**When to Use**: One-time deployments, debugging

Create a simple build script that ensures correct order:

**File**: `/Volumes/Files/Projects/newLoader/build-all.sh`

```bash
#!/bin/bash
set -e

PROJECT_ROOT="/Volumes/Files/Projects/newLoader"

echo "=== Building All Services in Dependency Order ==="

# Step 1: Build approval-workflow-core
echo ""
echo "[1/7] Building approval-workflow-core..."
cd "${PROJECT_ROOT}/services/approval-workflow-core"
mvn clean install -Dmaven.test.skip=true
echo "✓ approval-workflow-core installed"

# Step 2: Build loader (depends on workflow-core)
echo ""
echo "[2/7] Building loader..."
cd "${PROJECT_ROOT}/services/loader"
mvn clean package -Dmaven.test.skip=true
echo "✓ loader built"

# Step 3: Build auth-service
echo ""
echo "[3/7] Building auth-service..."
cd "${PROJECT_ROOT}/services/auth-service"
mvn clean package -Dmaven.test.skip=true
echo "✓ auth-service built"

# Step 4: Build gateway
echo ""
echo "[4/7] Building gateway..."
cd "${PROJECT_ROOT}/services/gateway"
mvn clean package -Dmaven.test.skip=true
echo "✓ gateway built"

# Step 5: Build import-export-service
echo ""
echo "[5/7] Building import-export-service..."
cd "${PROJECT_ROOT}/services/import-export-service"
mvn clean package -Dmaven.test.skip=true
echo "✓ import-export-service built"

# Step 6: Build etl_initializer
echo ""
echo "[6/7] Building etl_initializer..."
cd "${PROJECT_ROOT}/services/etl_initializer"
mvn clean package -Dmaven.test.skip=true
echo "✓ etl_initializer built"

# Step 7: Build dataGenerator
echo ""
echo "[7/7] Building dataGenerator..."
cd "${PROJECT_ROOT}/services/dataGenerator"
mvn clean package -Dmaven.test.skip=true
echo "✓ dataGenerator built"

echo ""
echo "=== ✓ All services built successfully ==="
echo ""
echo "JAR files location:"
echo "  - approval-workflow-core: ~/.m2/repository/com/tiqmo/monitoring/approval-workflow-core/1.0.0/"
echo "  - loader: services/loader/target/loader-0.0.1-SNAPSHOT.jar"
echo "  - auth-service: services/auth-service/target/auth-service-0.0.1-SNAPSHOT.jar"
echo "  - gateway: services/gateway/target/gateway-service-0.0.1-SNAPSHOT.jar"
echo "  - import-export: services/import-export-service/target/import-export-service-0.0.1-SNAPSHOT.jar"
```

**Usage**:

```bash
chmod +x /Volumes/Files/Projects/newLoader/build-all.sh
./build-all.sh
```

---

## 📋 Comparison: Which Solution to Use?

| Solution | Setup Time | Best For | Pros | Cons |
|----------|------------|----------|------|------|
| **1. Fixed Installer** | ✅ 0 min (done) | Single deployments | Simple, no config changes | Manual edit of installer |
| **2. Parent POM** | 30 min | CI/CD, automation | Maven standard, automatic order | Requires POM restructure |
| **3. Nexus/Artifactory** | 2-4 hours | Multi-team, enterprise | Centralized, offline builds | Infrastructure overhead |
| **4. Build Script** | 10 min | Ad-hoc builds | Quick, flexible | Not Maven-native |

---

## ✅ Recommended Approach

### For Your Current Situation
**Use Solution 1** (already applied): Fixed installer builds dependencies first.

### For Future (Production-Ready)
**Use Solution 2**: Create parent POM for reactor builds.

---

## 🔍 How Maven Resolves Dependencies

When you run `mvn clean package` in `loader/`:

1. **Read pom.xml**: Finds dependency on `approval-workflow-core:1.0.0`
2. **Check local repo** (~/.m2/repository): Looks for JAR
3. **If not found**: Checks Maven Central, configured repos
4. **If still not found**: BUILD FAILS ❌

**Solution**: Run `mvn install` in `approval-workflow-core` first to populate local repo.

---

## 🛠️ Troubleshooting

### Issue: "Could not find artifact approval-workflow-core"

**Fix**:
```bash
cd /Volumes/Files/Projects/newLoader/services/approval-workflow-core
mvn clean install -Dmaven.test.skip=true
```

### Issue: "Parent POM not found"

**Fix**: Ensure `<relativePath>../pom.xml</relativePath>` points to correct parent.

### Issue: "Version conflict"

**Fix**: Check version consistency:
```bash
# In approval-workflow-core/pom.xml
<version>1.0.0</version>

# In loader/pom.xml dependency
<dependency>
    <groupId>com.tiqmo.monitoring</groupId>
    <artifactId>approval-workflow-core</artifactId>
    <version>1.0.0</version>  <!-- Must match! -->
</dependency>
```

### Clear Maven Cache

If builds fail with corrupted cache:
```bash
rm -rf ~/.m2/repository/com/tiqmo/monitoring/
mvn clean install -U  # -U forces update
```

---

## 📝 Current Status

✅ **Immediate Fix Applied**: `app_installer.sh` updated to build `approval-workflow-core` before `loader`

**Next Steps** (Optional):
1. Create parent POM for CI/CD automation
2. Set up Nexus for team collaboration
3. Document build process in README

---

## 🔐 Security Note

**Why we exclude JARs from Git**:
- ✅ Keeps repository size small (<100 MB vs >500 MB with JARs)
- ✅ Prevents accidental commit of vulnerable dependencies
- ✅ Forces clean builds (no stale JARs)
- ✅ Industry best practice (Maven repos manage binaries)

**Alternatives to track**:
- ✅ Source code (`.java`, `.xml`)
- ✅ Build scripts (`.sh`, `pom.xml`)
- ✅ Documentation (`.md`)
- ❌ Build artifacts (`.jar`, `.class`, `target/`)

---

**Document Version**: 1.0
**Last Updated**: January 3, 2026
**Author**: Hassan Rawashdeh
