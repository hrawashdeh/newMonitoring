#!/bin/bash
set -euo pipefail

###############################################################################
# Config
###############################################################################
APISERVER_ADDRESS="127.0.0.1"     # Avoid WiFi IP; always loopback
POD_CIDR="10.244.0.0/16"          # Flannel default
K8S_VERSION="1.30"                # Minor version; apt will pick a 1.30.x
FLANNEL_MANIFEST="https://raw.githubusercontent.com/flannel-io/flannel/master/Documentation/kube-flannel.yml"

###############################################################################
# Helper
###############################################################################
run() {
  echo
  echo ">>> $*"
  eval "$@"
}

###############################################################################
# 0. Basic sanity
###############################################################################
if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required. Installing curl ..."
  sudo apt update
  sudo apt install -y curl
fi

echo ">>> Disabling swap (required by Kubernetes) ..."
sudo swapoff -a
sudo sed -i.bak '/ swap / s/^\(.*\)$/#\1/' /etc/fstab || true

###############################################################################
# 1. Kernel modules & sysctl for Kubernetes networking
###############################################################################
echo ">>> Loading kernel modules: overlay, br_netfilter ..."
cat <<EOF | sudo tee /etc/modules-load.d/k8s.conf
overlay
br_netfilter
EOF

sudo modprobe overlay
sudo modprobe br_netfilter

echo ">>> Setting sysctl params for Kubernetes networking ..."
cat <<EOF | sudo tee /etc/sysctl.d/99-k8s.conf
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF

sudo sysctl --system

###############################################################################
# 2. Install & configure containerd
###############################################################################
if ! command -v containerd >/dev/null 2>&1; then
  echo ">>> Installing containerd ..."
  sudo apt update
  sudo apt install -y containerd
fi

echo ">>> Configuring containerd (SystemdCgroup = true) ..."
sudo mkdir -p /etc/containerd
if [ ! -f /etc/containerd/config.toml ]; then
  sudo containerd config default | sudo tee /etc/containerd/config.toml >/dev/null
fi

# Set SystemdCgroup = true
sudo sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml

echo ">>> Restarting containerd ..."
sudo systemctl daemon-reload
sudo systemctl enable containerd
sudo systemctl restart containerd

###############################################################################
# 3. Install Kubernetes packages (kubeadm, kubelet, kubectl)
###############################################################################
if ! command -v kubeadm >/dev/null 2>&1; then
  echo ">>> Installing Kubernetes packages kubeadm, kubelet, kubectl ..."

  sudo apt update
  sudo apt install -y apt-transport-https ca-certificates gpg

  sudo mkdir -p /etc/apt/keyrings
  curl -fsSL https://pkgs.k8s.io/core:/stable:/v${K8S_VERSION}/deb/Release.key | \
    sudo gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg

  echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] \
https://pkgs.k8s.io/core:/stable:/v${K8S_VERSION}/deb/ /" | \
    sudo tee /etc/apt/sources.list.d/kubernetes.list

  sudo apt update
  sudo apt install -y kubelet kubeadm kubectl
  sudo apt-mark hold kubelet kubeadm kubectl
else
  echo "kubeadm/kubelet/kubectl already installed."
fi

###############################################################################
# 4. kubeadm init with fixed API server IP (127.0.0.1) and flannel CIDR
###############################################################################
echo ">>> Running kubeadm init with apiserver ${APISERVER_ADDRESS} and pod CIDR ${POD_CIDR} ..."

sudo kubeadm init \
  --pod-network-cidr="${POD_CIDR}" \
  --apiserver-advertise-address="${APISERVER_ADDRESS}" \
  --control-plane-endpoint="${APISERVER_ADDRESS}:6443" \
  --cri-socket=unix:///var/run/containerd/containerd.sock

###############################################################################
# 5. Setup kubeconfig for current user
###############################################################################
echo ">>> Configuring kubeconfig for user: $USER ..."
mkdir -p "$HOME/.kube"
sudo cp /etc/kubernetes/admin.conf "$HOME/.kube/config"
sudo chown "$(id -u):$(id -g)" "$HOME/.kube/config"

echo ">>> Verifying kubeconfig server points to 127.0.0.1 ..."
grep server: "$HOME/.kube/config" || true

###############################################################################
# 6. Install Flannel CNI
###############################################################################
echo ">>> Installing flannel CNI ..."
run "kubectl apply -f ${FLANNEL_MANIFEST}"

echo ">>> Waiting for flannel DaemonSet to be ready ..."
run "kubectl -n kube-flannel rollout status daemonset/kube-flannel-ds --timeout=300s"

###############################################################################
# 7. Allow scheduling on control-plane node
###############################################################################
echo ">>> Removing control-plane taint so workloads can run on this node ..."
run "kubectl taint nodes --all node-role.kubernetes.io/control-plane- || true"
run "kubectl taint nodes --all node-role.kubernetes.io/master- || true"

###############################################################################
# 8. Summary
###############################################################################
echo
echo "======================================================================"
echo "Kubernetes single-node dev cluster is ready."
echo
echo "API server URL      : https://${APISERVER_ADDRESS}:6443"
echo "Pod network (CIDR)  : ${POD_CIDR}"
echo "Container runtime   : containerd"
echo "CNI                 : flannel"
echo
echo "kubeconfig          : $HOME/.kube/config"
echo "Check node status   : kubectl get nodes -o wide"
echo "Check system pods   : kubectl get pods -A"
echo
echo "Because we use 127.0.0.1, changing WiFi / IP will NOT break kubectl."
echo "======================================================================"
