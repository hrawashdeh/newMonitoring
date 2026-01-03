    deployment="signal-loader"
    namespace="monitoring-app"
    timeout=120
    elapsed=0
    interval=10

    echo "Monitoring pod health for deployment: $deployment"
    echo "Timeout: ${timeout}s | Check interval: ${interval}s"
    echo

    while [ $elapsed -lt $timeout ]; do
         ready_replicas=$(kubectl get deployment "$deployment" -n "$namespace" \
 		 -o jsonpath='{.status.readyReplicas}' 2>/dev/null)

		 desired_replicas=$(kubectl get deployment "$deployment" -n "$namespace" \
  		-o jsonpath='{.spec.replicas}' 2>/dev/null)

		ready_replicas=${ready_replicas:-0}
		desired_replicas=${desired_replicas:-0}

        echo -ne "\r${BLUE}[$(date +%H:%M:%S)]${NC} Ready: $ready_replicas/$desired_replicas pods"

        if [ "$ready_replicas" -eq "$desired_replicas" ] && [ "$ready_replicas" != 0 ]; then
            echo
            echo "All pods are ready ($ready_replicas/$desired_replicas)"
        fi

        sleep $interval
        elapsed=$((elapsed + interval))
    done

    echo
    echo "Timeout: Pods did not become ready within ${timeout}s"

    # Show pod status for debugging
    echo
    echo "Current pod status:"
    kubectl get pods -n "$namespace" -l "app=$deployment" --no-headers
