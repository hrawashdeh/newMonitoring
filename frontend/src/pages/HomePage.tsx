import { useNavigate } from 'react-router-dom';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '../components/ui/card';
import { Button } from '../components/ui/button';
import {
  Database,
  Users,
  Activity,
  BarChart3,
  Settings,
  Shield,
  LogOut,
  Bell,
  FileText,
  CheckCircle,
} from 'lucide-react';

interface FeatureCard {
  title: string;
  description: string;
  icon: React.ReactNode;
  path: string;
  available: boolean;
  badge?: string;
}

export default function HomePage() {
  const navigate = useNavigate();
  const username = localStorage.getItem('auth_username') || 'User';
  const roles = JSON.parse(localStorage.getItem('auth_roles') || '[]');

  const handleLogout = () => {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_username');
    localStorage.removeItem('auth_roles');
    window.location.href = '/login';
  };

  const features: FeatureCard[] = [
    {
      title: 'Loaders Management',
      description: 'Configure and monitor data loaders, manage execution intervals, and track loader performance',
      icon: <Database className="h-8 w-8" />,
      path: '/loaders',
      available: true,
      badge: 'Active',
    },
    {
      title: 'Pending Approvals',
      description: 'Review and approve pending changes to loaders, dashboards, and other entities',
      icon: <CheckCircle className="h-8 w-8" />,
      path: '/approvals',
      available: true,
      badge: 'Admin',
    },
    {
      title: 'System Monitoring',
      description: 'Real-time monitoring of system health, resource utilization, and service status',
      icon: <Activity className="h-8 w-8" />,
      path: '/monitoring',
      available: false,
    },
    {
      title: 'Reports & Analytics',
      description: 'Generate comprehensive reports, view analytics dashboards, and export data insights',
      icon: <BarChart3 className="h-8 w-8" />,
      path: '/reports',
      available: false,
    },
    {
      title: 'User Management',
      description: 'Manage user accounts, roles, permissions, and access control policies',
      icon: <Users className="h-8 w-8" />,
      path: '/users',
      available: false,
    },
    {
      title: 'Security & Audit',
      description: 'View security logs, audit trails, login attempts, and security compliance reports',
      icon: <Shield className="h-8 w-8" />,
      path: '/security',
      available: false,
    },
    {
      title: 'Notifications',
      description: 'Configure alerts, manage notification channels, and review system notifications',
      icon: <Bell className="h-8 w-8" />,
      path: '/notifications',
      available: false,
    },
    {
      title: 'Documentation',
      description: 'Access system documentation, API references, user guides, and best practices',
      icon: <FileText className="h-8 w-8" />,
      path: '/documentation',
      available: false,
    },
    {
      title: 'System Settings',
      description: 'Configure system preferences, integration settings, and application parameters',
      icon: <Settings className="h-8 w-8" />,
      path: '/settings',
      available: false,
    },
  ];

  const handleCardClick = (feature: FeatureCard) => {
    if (feature.available) {
      navigate(feature.path);
    }
  };

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="border-b bg-card">
        <div className="container mx-auto px-6 py-4">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-2xl font-bold text-foreground">
                Loader Management System
              </h1>
              <p className="text-sm text-muted-foreground mt-1">
                Enterprise Data Integration Platform
              </p>
            </div>
            <div className="flex items-center gap-4">
              <div className="text-right">
                <p className="text-sm font-medium text-foreground">{username}</p>
                <p className="text-xs text-muted-foreground">
                  {roles.join(', ') || 'User'}
                </p>
              </div>
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
      <main className="container mx-auto px-6 py-8">
        {/* Welcome Section */}
        <div className="mb-8">
          <h2 className="text-3xl font-bold text-foreground mb-2">
            Welcome back, {username}
          </h2>
          <p className="text-muted-foreground">
            Select a module below to access system features and functionality
          </p>
        </div>

        {/* Feature Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {features.map((feature, index) => (
            <Card
              key={index}
              className={`
                transition-all duration-200
                ${
                  feature.available
                    ? 'cursor-pointer hover:shadow-lg hover:border-primary hover:-translate-y-1'
                    : 'opacity-60 cursor-not-allowed'
                }
              `}
              onClick={() => handleCardClick(feature)}
            >
              <CardHeader>
                <div className="flex items-start justify-between mb-3">
                  <div
                    className={`
                    p-3 rounded-lg
                    ${
                      feature.available
                        ? 'bg-primary/10 text-primary'
                        : 'bg-muted text-muted-foreground'
                    }
                  `}
                  >
                    {feature.icon}
                  </div>
                  {feature.badge && feature.available && (
                    <span className="px-2 py-1 text-xs font-semibold bg-green-100 text-green-800 rounded-full">
                      {feature.badge}
                    </span>
                  )}
                  {!feature.available && (
                    <span className="px-2 py-1 text-xs font-semibold bg-gray-100 text-gray-600 rounded-full">
                      Coming Soon
                    </span>
                  )}
                </div>
                <CardTitle className="text-lg">{feature.title}</CardTitle>
                <CardDescription className="text-sm leading-relaxed mt-2">
                  {feature.description}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <Button
                  variant={feature.available ? 'default' : 'outline'}
                  className="w-full"
                  disabled={!feature.available}
                >
                  {feature.available ? 'Open Module' : 'Coming Soon'}
                </Button>
              </CardContent>
            </Card>
          ))}
        </div>

        {/* Footer Info */}
        <div className="mt-12 pt-8 border-t">
          <div className="flex items-center justify-between text-sm text-muted-foreground">
            <p>System Status: All services operational</p>
            <p>Version 1.0.0 | Build 2025.12.25</p>
          </div>
        </div>
      </main>
    </div>
  );
}