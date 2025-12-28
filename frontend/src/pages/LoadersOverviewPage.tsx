import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { loadersApi } from '../api/loaders';
import { StatsCard } from '../components/StatsCard';
import { ActionCard } from '../components/ActionCard';
import { Button } from '../components/ui/button';
import { ArrowLeft, LogOut, Database, CheckCircle, PauseCircle, AlertCircle, FileText, History, BarChart3, HardDrive, FileCode, Activity } from 'lucide-react';

export default function LoadersOverviewPage() {
  const navigate = useNavigate();
  const username = localStorage.getItem('auth_username') || 'User';

  // Fetch operational statistics
  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['loaders', 'stats'],
    queryFn: loadersApi.getLoadersStats,
    refetchInterval: 30000, // Auto-refresh every 30s
  });

  // Fetch recent activity
  const { data: activity = [], isLoading: activityLoading } = useQuery({
    queryKey: ['loaders', 'activity'],
    queryFn: () => loadersApi.getLoadersActivity(5),
    refetchInterval: 10000, // Auto-refresh every 10s
  });

  const handleLogout = () => {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_username');
    localStorage.removeItem('auth_roles');
    window.location.href = '/login';
  };

  const formatActivityTime = (timestamp: string) => {
    const date = new Date(timestamp);
    return date.toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    });
  };

  const getActivityIcon = (status: string) => {
    switch (status) {
      case 'success':
        return '✅';
      case 'error':
        return '❌';
      case 'warning':
        return '⚠️';
      default:
        return 'ℹ️';
    }
  };

  if (statsLoading) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="text-lg text-muted-foreground">Loading overview...</div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="border-b bg-card">
        <div className="container mx-auto px-6 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => navigate('/')}
                className="gap-2"
              >
                <ArrowLeft className="h-4 w-4" />
                Back to Home
              </Button>
              <div className="h-6 w-px bg-border" />
              <div>
                <h1 className="text-xl font-bold text-foreground">
                  Loaders Overview
                </h1>
                <p className="text-sm text-muted-foreground">
                  Manage ETL loaders and monitor operational status
                </p>
              </div>
            </div>
            <div className="flex items-center gap-4">
              <Button
                onClick={() => navigate('/loaders/new')}
              >
                Create Loader
              </Button>
              <p className="text-sm font-medium text-foreground">{username}</p>
              <Button
                variant="outline"
                size="sm"
                onClick={handleLogout}
                className="gap-2"
              >
                <LogOut className="h-4 w-4" />
                Logout
              </Button>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <div className="container mx-auto px-6 py-10">
        {/* Operational Statistics */}
        <div>
          <h2 className="text-lg font-semibold mb-4">Operational Statistics</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-10">
            <StatsCard
              label="Total Loaders"
              value={stats?.total || 0}
              subtitle="Across all statuses"
              icon={Database}
            />
            <StatsCard
              label="Active"
              value={stats?.active || 0}
              subtitle={stats?.total ? `${Math.round((stats.active / stats.total) * 100)}% of total` : '0% of total'}
              icon={CheckCircle}
              trend={stats?.trend ? {
                direction: 'up',
                value: stats.trend.activeChange,
              } : undefined}
              status="success"
            />
            <StatsCard
              label="Paused"
              value={stats?.paused || 0}
              subtitle={stats?.total ? `${Math.round((stats.paused / stats.total) * 100)}% of total` : '0% of total'}
              icon={PauseCircle}
            />
            <StatsCard
              label="Failed"
              value={stats?.failed || 0}
              subtitle={stats?.failed && stats.failed > 0 ? '⚠️ Attention needed' : 'All running smoothly'}
              icon={AlertCircle}
              status={stats?.failed && stats.failed > 0 ? 'error' : 'default'}
            />
          </div>
        </div>

        {/* Quick Actions */}
        <div>
          <h2 className="text-lg font-semibold mb-4">Quick Actions</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mb-10">
            <ActionCard
              icon={FileText}
              title="View All Loaders"
              description="Complete list with search & filters"
              actionLabel="View"
              onClick={() => navigate('/loaders/list')}
            />
            <ActionCard
              icon={History}
              title="Backfill Jobs"
              description="Manual data reload operations"
              actionLabel="Manage"
              onClick={() => navigate('/backfill')}
              disabled
            />
            <ActionCard
              icon={BarChart3}
              title="Signals Explorer"
              description="View time-series data"
              actionLabel="Explore"
              onClick={() => navigate('/signals')}
              disabled
            />
            <ActionCard
              icon={HardDrive}
              title="Source Databases"
              description="Manage data source connections"
              actionLabel="Configure"
              onClick={() => navigate('/sources')}
              disabled
            />
            <ActionCard
              icon={FileCode}
              title="Templates"
              description="Pre-built loader configs"
              actionLabel="Browse"
              onClick={() => navigate('/loaders/templates')}
              disabled
            />
            <ActionCard
              icon={Activity}
              title="Executions"
              description="View all execution history"
              actionLabel="View"
              onClick={() => navigate('/executions')}
              disabled
            />
          </div>
        </div>

        {/* Recent Activity */}
        <div>
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold">Recent Activity</h2>
            <Button
              variant="link"
              size="sm"
              onClick={() => navigate('/activity')}
              disabled
            >
              View All →
            </Button>
          </div>
          <div className="border rounded-lg bg-card">
            {activityLoading ? (
              <div className="p-8 text-center text-muted-foreground">
                Loading activity...
              </div>
            ) : activity.length === 0 ? (
              <div className="p-8 text-center text-muted-foreground">
                No recent activity
              </div>
            ) : (
              <div className="divide-y">
                {activity.map((event, index) => (
                  <div
                    key={index}
                    className="p-4 hover:bg-muted/50 transition-colors flex items-center justify-between"
                  >
                    <div className="flex items-center gap-3">
                      <span className="text-xl">{getActivityIcon(event.status)}</span>
                      <div>
                        <p className="text-sm font-medium">
                          {formatActivityTime(event.timestamp)} {event.message}
                        </p>
                        {event.loaderCode && (
                          <p className="text-xs text-muted-foreground mt-0.5">
                            Loader: {event.loaderCode}
                          </p>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
