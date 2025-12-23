# Loader Management UI

React 18 + TypeScript frontend for managing ETL loaders.

## Tech Stack

- **React 18** with TypeScript
- **Vite** - Build tool
- **Tailwind CSS** + **shadcn/ui** - UI components
- **TanStack Table** - Data tables
- **TanStack Query** - Data fetching
- **React Router** - Routing
- **Axios** - HTTP client

## Architecture

The frontend is deployed in Kubernetes alongside the `signal-loader` backend service:

```
Browser → NodePort (30080) → NGINX → React App
                                  ↓
                              /api/* → signal-loader:8080
```

- **Frontend**: NGINX serving static React build
- **Backend API**: Proxied to `signal-loader:8080` (internal service)
- **Namespace**: `monitoring-app`

## Local Development

### Prerequisites

- Node.js 20+
- npm or yarn

### Install Dependencies

```bash
npm install
```

### Run Development Server

```bash
npm run dev
```

Runs on http://localhost:5173

### Build for Production

```bash
npm run build
```

Output: `dist/` directory

## Kubernetes Deployment

### 1. Build Docker Image

```bash
docker build -t loader-frontend:0.1.0 .
```

### 2. Deploy to Kubernetes

```bash
kubectl apply -f k8s_manifist/frontend-deployment.yaml
```

### 3. Verify Deployment

```bash
kubectl get pods -n monitoring-app | grep loader-frontend
kubectl get svc -n monitoring-app | grep loader-frontend
```

### 4. Access UI

- **NodePort (POC)**: http://localhost:30080
- **ClusterIP (internal)**: http://loader-frontend.monitoring-app.svc.cluster.local

## API Configuration

The frontend calls the backend via NGINX proxy:

- **Frontend URL**: `/api/v1/...`
- **NGINX Proxy**: → `http://signal-loader:8080/api/v1/...`

See `nginx.conf` for proxy configuration.

## Project Structure

```
frontend/
├── src/
│   ├── api/              # API client functions
│   ├── components/       # Reusable UI components
│   │   └── ui/          # shadcn/ui components
│   ├── pages/           # Page components
│   ├── lib/             # Utilities (axios, api-config)
│   ├── types/           # TypeScript types
│   ├── App.tsx          # Main app component
│   ├── main.tsx         # Entry point
│   └── index.css        # Global styles
├── k8s_manifist/        # Kubernetes manifests
├── Dockerfile           # Multi-stage build
├── nginx.conf           # NGINX configuration
└── package.json         # Dependencies
```

## Features (POC Stage 1)

- ✅ View all loaders in table
- ✅ Status badges (ACTIVE, PAUSED, FAILED)
- ✅ Pagination
- ⏳ Search and filter
- ⏳ Create new loader
- ⏳ Edit loader
- ⏳ Delete loader
- ⏳ View loader details

## Next Steps

1. Add Input and Select components
2. Implement search/filter functionality
3. Create loader editor dialog
4. Add loader details page
5. Implement CRUD operations
6. Add authentication (Stage 2)
