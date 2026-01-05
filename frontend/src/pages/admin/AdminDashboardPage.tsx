import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Database,
  CheckCircle,
  Key,
  Users,
  ArrowRight,
  Clock,
  AlertCircle,
  Activity,
} from 'lucide-react';
import { loadersApi } from '@/api/loaders';
import { approvalsApi } from '@/api/approvals';

export function AdminDashboardPage() {
  // Fetch loader stats
  const { data: loaderStats } = useQuery({
    queryKey: ['loader-stats'],
    queryFn: loadersApi.getStats,
    staleTime: 30 * 1000, // 30 seconds
  });

  // Fetch pending approvals count
  const { data: pendingApprovals } = useQuery({
    queryKey: ['pending-approvals'],
    queryFn: approvalsApi.getPendingApprovals,
    staleTime: 30 * 1000,
  });

  const pendingCount = pendingApprovals?.length || 0;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Admin Dashboard</h1>
        <p className="text-muted-foreground">
          Manage loaders, approvals, APIs, and system configuration
        </p>
      </div>

      {/* Quick Stats */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total Loaders</CardTitle>
            <Database className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{loaderStats?.totalLoaders || 0}</div>
            <p className="text-xs text-muted-foreground">
              {loaderStats?.activeLoaders || 0} active
            </p>
          </CardContent>
        </Card>

        <Card className={pendingCount > 0 ? 'border-amber-200 bg-amber-50/50' : ''}>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Pending Approvals</CardTitle>
            <Clock className="h-4 w-4 text-amber-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{pendingCount}</div>
            <p className="text-xs text-muted-foreground">
              {pendingCount > 0 ? 'Requires attention' : 'All clear'}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Running</CardTitle>
            <Activity className="h-4 w-4 text-green-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{loaderStats?.runningLoaders || 0}</div>
            <p className="text-xs text-muted-foreground">Currently executing</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Failed</CardTitle>
            <AlertCircle className="h-4 w-4 text-red-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{loaderStats?.failedLoaders || 0}</div>
            <p className="text-xs text-muted-foreground">Need investigation</p>
          </CardContent>
        </Card>
      </div>

      {/* Quick Actions */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        <Card className="hover:border-primary transition-colors">
          <Link to="/admin/loaders">
            <CardHeader>
              <div className="flex items-center justify-between">
                <Database className="h-8 w-8 text-primary" />
                <ArrowRight className="h-5 w-5 text-muted-foreground" />
              </div>
              <CardTitle>Loader Management</CardTitle>
              <CardDescription>
                View, create, and manage ETL loaders. Configure SQL queries and scheduling.
              </CardDescription>
            </CardHeader>
          </Link>
        </Card>

        <Card className="hover:border-primary transition-colors">
          <Link to="/admin/approvals/pending">
            <CardHeader>
              <div className="flex items-center justify-between">
                <div className="relative">
                  <CheckCircle className="h-8 w-8 text-primary" />
                  {pendingCount > 0 && (
                    <Badge
                      variant="destructive"
                      className="absolute -top-2 -right-2 h-5 w-5 flex items-center justify-center p-0 text-xs"
                    >
                      {pendingCount}
                    </Badge>
                  )}
                </div>
                <ArrowRight className="h-5 w-5 text-muted-foreground" />
              </div>
              <CardTitle>Approvals</CardTitle>
              <CardDescription>
                Review and approve pending changes. Manage approval workflow.
              </CardDescription>
            </CardHeader>
          </Link>
        </Card>

        <Card className="hover:border-primary transition-colors">
          <Link to="/admin/api/discovery">
            <CardHeader>
              <div className="flex items-center justify-between">
                <Key className="h-8 w-8 text-primary" />
                <ArrowRight className="h-5 w-5 text-muted-foreground" />
              </div>
              <CardTitle>API Management</CardTitle>
              <CardDescription>
                View discovered APIs across all services. Manage permissions.
              </CardDescription>
            </CardHeader>
          </Link>
        </Card>

        <Card className="hover:border-primary transition-colors">
          <Link to="/admin/users">
            <CardHeader>
              <div className="flex items-center justify-between">
                <Users className="h-8 w-8 text-primary" />
                <ArrowRight className="h-5 w-5 text-muted-foreground" />
              </div>
              <CardTitle>Users & Roles</CardTitle>
              <CardDescription>
                Manage users and role assignments. Configure access control.
              </CardDescription>
            </CardHeader>
          </Link>
        </Card>

        <Card className="hover:border-primary transition-colors">
          <Link to="/admin/system/sources">
            <CardHeader>
              <div className="flex items-center justify-between">
                <Database className="h-8 w-8 text-muted-foreground" />
                <ArrowRight className="h-5 w-5 text-muted-foreground" />
              </div>
              <CardTitle>Source Databases</CardTitle>
              <CardDescription>
                View and manage source database connections.
              </CardDescription>
            </CardHeader>
          </Link>
        </Card>

        <Card className="hover:border-primary transition-colors">
          <Link to="/admin/system/audit">
            <CardHeader>
              <div className="flex items-center justify-between">
                <Activity className="h-8 w-8 text-muted-foreground" />
                <ArrowRight className="h-5 w-5 text-muted-foreground" />
              </div>
              <CardTitle>Audit Logs</CardTitle>
              <CardDescription>
                View system audit logs and user activity.
              </CardDescription>
            </CardHeader>
          </Link>
        </Card>
      </div>
    </div>
  );
}

export default AdminDashboardPage;
