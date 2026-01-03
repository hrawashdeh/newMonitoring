#!/bin/bash
set -euo pipefail

echo ">>> Stopping kubelet (if running) ..."
sudo systemctl stop kubelet 2>/dev/null || true

echo ">>> Resetting kubeadm cluster state ..."
sudo kubeadm reset -f || true

echo ">>> Cleaning CNI configuration ..."
sudo rm -rf /etc/cni/net.d
sudo ip link delete cni0 2>/dev/null || true
sudo ip link delete flannel.1 2>/dev/null || true

echo ">>> Cleaning Kubernetes data directories ..."
sudo rm -rf /etc/kubernetes
sudo rm -rf /var/lib/etcd
sudo rm -rf /var/lib/kubelet/pki

echo ">>> Flushing iptables rules (nat, mangle, filter) ..."
sudo iptables -F || true
sudo iptables -t nat -F || true
sudo iptables -t mangle -F || true
sudo iptables -X || true

echo ">>> Removing old kubeconfig for current user ..."
rm -rf "$HOME/.kube" || true

echo
echo "======================================================================"
echo "Cluster reset complete."
echo "You can now run 01_install_k8s_single_node.sh to create a fresh cluster."
echo "======================================================================"
