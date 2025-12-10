# Monitronig Project – Local Kubernetes & Database Infrastructure (Dev Setup)

This document provides **raw shell commands only** to install and operate a local Kubernetes-based database infrastructure for development.

It covers:
- Single-node Kubernetes cluster for development
- Stable API endpoint via `127.0.0.1` (no Wi‑Fi IP dependency)
- `kubectl`, `kubeadm`, `helm`, `kubeseal`
- Sealed Secrets controller
- PostgreSQL via Bitnami Helm chart
- MySQL via Bitnami Helm chart

> **Important:**
> - This repository intentionally contains **no `.sh` files**.
> - All steps below are provided as **direct shell commands**.
> - Plain secrets must **never** be committed.

---

## 0. Project Files

All commands assume this root path on your machine is already synchronized with git repo:

```bash
./
├── kube
│   ├── install.sh #(ignored)
│   └── unistall.sh#(ignored)
├── monitoring
│   ├── infra
│   │   ├── 00_setup_sealed_secret.sh #(ignored)
│   │   ├── 01_install_postgress.sh #(ignored)
│   │   ├── 02_install_mysql.sh  #(ignored)
│   │   ├── mysql
│   │   │   └── values-mysql.yaml 
│   │   ├── postgress
│   │   │   ├── postgres-nodeport.yaml
│   │   │   └── values-postgresql.yaml
│   │   ├── secrets
│   │   │   ├── monitoring-secrets-plain.yaml #(ignored)
│   │   │   └── monitoring-secrets-sealed.yaml
│   │   └── unistalKubeObjects.sh #(ignored)
│   └── services (in progress)
```


---

## 1. Linux – Kubernetes Cluster with Static API (`127.0.0.1`)

> Skip this section if you already have a working cluster (e.g., on macOS via Docker Desktop).

### 1.1 Reset Existing Cluster (if needed)

```bash
sudo kubeadm reset -f
sudo systemctl stop kubelet
sudo systemctl stop containerd || true
sudo rm -rf /etc/cni/net.d
sudo ip link delete cni0 2>/dev/null || true
sudo ip link delete flannel.1 2>/dev/null || true
sudo rm -rf /var/lib/etcd
sudo rm -rf /etc/kubernetes
```

---

### 1.2 Install and Configure containerd

```bash
sudo apt update
sudo apt install -y containerd
sudo mkdir -p /etc/containerd
containerd config default | sudo tee /etc/containerd/config.toml >/dev/null
sudo sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
sudo systemctl restart containerd
sudo systemctl enable containerd
```

---

### 1.3 Kernel Modules & Sysctl

```bash
cat <<EOF | sudo tee /etc/modules-load.d/k8s.conf
overlay
br_netfilter
EOF

sudo modprobe overlay
sudo modprobe br_netfilter

cat <<EOF | sudo tee /etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF

sudo sysctl --system
```

---

### 1.4 Disable Swap

```bash
sudo swapoff -a
sudo sed -i '/ swap / s/^\(.*\)$/#\1/g' /etc/fstab
```

---

### 1.5 Install kubeadm, kubelet, kubectl

```bash
sudo mkdir -p /etc/apt/keyrings

curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.30/deb/Release.key | \
  sudo gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg

echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.30/deb/ /" | \
  sudo tee /etc/apt/sources.list.d/kubernetes.list

sudo apt update
sudo apt install -y kubelet kubeadm kubectl
sudo apt-mark hold kubelet kubeadm kubectl
```

---

### 1.6 Initialize Cluster with Stable API

```bash
sudo kubeadm init \
  --control-plane-endpoint=127.0.0.1:6443 \
  --apiserver-advertise-address=0.0.0.0 \
  --pod-network-cidr=192.168.0.0/16 \
  --cri-socket=unix:///var/run/containerd/containerd.sock
```

After success:

```bash
mkdir -p $HOME/.kube
sudo cp /etc/kubernetes/admin.conf $HOME/.kube/config
sudo chown "$(id -u):$(id -g)" $HOME/.kube/config
```

Verify:

```bash
kubectl get nodes
grep server: ~/.kube/config
```

---

## 2. macOS – Tools Only

On macOS you typically use Docker Desktop or another local Kubernetes distribution.

```bash
brew install kubectl helm
```

Verify:

```bash
kubectl get nodes
```

---

## 3. Install Helm and kubeseal (Linux & macOS)

### Helm

Linux:

```bash
curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

macOS:

```bash
brew install helm
```

---

### kubeseal

```bash
KUBESEAL_VERSION="0.19.1"
curl -sSL https://github.com/bitnami-labs/sealed-secrets/releases/download/v${KUBESEAL_VERSION}/kubeseal-${KUBESEAL_VERSION}-$(uname | tr '[:upper:]' '[:lower:]')-amd64.tar.gz -o kubeseal.tar.gz

tar -xzf kubeseal.tar.gz
sudo install -m 0755 kubeseal /usr/local/bin/kubeseal
rm -f kubeseal kubeseal.tar.gz
kubeseal --version
```

---

## 4. Enable Workloads on Single Node

```bash
kubectl taint nodes --all node-role.kubernetes.io/control-plane- || true
```

---

## 5. Install Sealed Secrets Controller

```bash
SEALED_NS="sealed-secrets"
helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
helm repo update

kubectl create namespace ${SEALED_NS} --dry-run=client -o yaml | kubectl apply -f -

helm upgrade --install sealed-secrets sealed-secrets/sealed-secrets \
  --namespace ${SEALED_NS} \
  --set fullnameOverride=sealed-secrets

kubectl rollout status deployment/sealed-secrets -n ${SEALED_NS} --timeout=180s
```

---

## 6. Create Application Namespace

```bash
NAMESPACE="monitoring-infra"
kubectl create namespace ${NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
```

---

## 7. Create Plain Secret Locally and Seal It

```bash
cd ./monitoring/infra
mkdir -p secrets
```

```bash
cat > secrets/monitoring-secrets-plain.yaml <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: monitoring-secrets
  namespace: ${NAMESPACE}
type: Opaque
stringData:
  #postgres infra keys
  postgres-admin-password: "someVal"
  postgres-app-password:   "someVal"

  #mysql infra keys
  mysql-root-password:     "someVal"
  mysql-password:          "CHANGE_ME"
  mysql-replication-password: "someVal"
  MYSQL_APP_USER: "someVal"
  MYSQL_APP_DB:   "someVal"
EOF
```

```bash
kubeseal \
  --controller-name=sealed-secrets \
  --controller-namespace=${SEALED_NS} \
  --namespace ${NAMESPACE} \
  --format yaml \
  < secrets/monitoring-secrets-plain.yaml \
  > secrets/monitoring-secrets-sealed.yaml

kubectl apply -f secrets/monitoring-secrets-sealed.yaml
```

---

## 8. StorageClass (Linux Bare-Metal)

```bash
curl -fsSL https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml -o local-path-storage.yaml
kubectl apply -f local-path-storage.yaml
kubectl get storageclass
```

---

## 9. Install PostgreSQL via Helm

```bash
cd ./monitoring/infra
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

helm upgrade --install postgres bitnami/postgresql \
  -n ${NAMESPACE} \
  -f postgress/values-postgresql.yaml
```

```bash
kubectl rollout status statefulset/postgres-postgresql -n ${NAMESPACE} --timeout=300s
kubectl get pods -n ${NAMESPACE}
```

```bash
kubectl apply -f postgress/postgres-nodeport.yaml
kubectl get svc -n ${NAMESPACE}
```

```bash
psql -h 127.0.0.1 -p 30432 -U app_user -d app_db
```

---

## 10. Install MySQL via Helm

```bash
cd ./monitoring/infra

helm upgrade --install mysql bitnami/mysql \
  -n ${NAMESPACE} \
  -f mysql/values-mysql.yaml
```

```bash
kubectl rollout status statefulset/mysql -n ${NAMESPACE} --timeout=300s
kubectl get pods -n ${NAMESPACE}
```

---


## 11. Health Check

```bash
kubectl get nodes -o wide
kubectl get pods -A
kubectl get svc -n ${NAMESPACE}
kubectl get secret -n ${NAMESPACE}
```

---

## ✅ Result

- Encrypted secrets only
- PostgreSQL + MySQL deployed via Helm
- Stable Kubernetes API on `127.0.0.1`
- No plaintext secrets in Git
- No shell scripts tracked in the repository
