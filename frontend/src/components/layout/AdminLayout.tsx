import { useState, useEffect } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import {
  ChevronLeft,
  ChevronRight,
  LayoutDashboard,
  Database,
  CheckCircle,
  Key,
  Users,
  Settings,
  LogOut,
  Menu,
  X,
  Loader2,
} from 'lucide-react';
import { authApi, type MenuItemDTO } from '@/api/auth';
import logger from '@/lib/logger';

interface MenuItem {
  menuCode: string;
  label: string;
  icon: string;
  route: string | null;
  parentCode: string | null;
  children?: MenuItem[];
}

// Icon mapping
const iconMap: Record<string, React.ElementType> = {
  dashboard: LayoutDashboard,
  database: Database,
  'check-circle': CheckCircle,
  key: Key,
  users: Users,
  settings: Settings,
  list: Database,
  plus: Database,
  clock: CheckCircle,
  history: CheckCircle,
  search: Key,
  shield: Key,
  user: Users,
  'file-text': Settings,
};

// Default menu items (used when DB menu not available)
const defaultMenuItems: MenuItem[] = [
  {
    menuCode: 'admin',
    label: 'Admin Dashboard',
    icon: 'dashboard',
    route: '/admin',
    parentCode: null,
    children: [
      {
        menuCode: 'loaders',
        label: 'Loaders',
        icon: 'database',
        route: null,
        parentCode: 'admin',
        children: [
          { menuCode: 'loaders-list', label: 'All Loaders', icon: 'list', route: '/admin/loaders', parentCode: 'loaders' },
          { menuCode: 'loaders-create', label: 'Create Loader', icon: 'plus', route: '/admin/loaders/new', parentCode: 'loaders' },
          { menuCode: 'loaders-pending', label: 'Pending Approvals', icon: 'clock', route: '/admin/loaders/pending', parentCode: 'loaders' },
        ],
      },
      {
        menuCode: 'approvals',
        label: 'Approvals',
        icon: 'check-circle',
        route: null,
        parentCode: 'admin',
        children: [
          { menuCode: 'approvals-pending', label: 'Pending', icon: 'clock', route: '/admin/approvals/pending', parentCode: 'approvals' },
          { menuCode: 'approvals-history', label: 'History', icon: 'history', route: '/admin/approvals/history', parentCode: 'approvals' },
        ],
      },
      {
        menuCode: 'api-mgmt',
        label: 'API Management',
        icon: 'key',
        route: null,
        parentCode: 'admin',
        children: [
          { menuCode: 'api-discovery', label: 'Discovered APIs', icon: 'search', route: '/admin/api/discovery', parentCode: 'api-mgmt' },
          { menuCode: 'api-permissions', label: 'Permissions', icon: 'shield', route: '/admin/api/permissions', parentCode: 'api-mgmt' },
        ],
      },
      {
        menuCode: 'users',
        label: 'Users & Roles',
        icon: 'users',
        route: null,
        parentCode: 'admin',
        children: [
          { menuCode: 'users-list', label: 'Users', icon: 'user', route: '/admin/users', parentCode: 'users' },
          { menuCode: 'roles-list', label: 'Roles', icon: 'shield', route: '/admin/roles', parentCode: 'users' },
        ],
      },
      {
        menuCode: 'system',
        label: 'System',
        icon: 'settings',
        route: null,
        parentCode: 'admin',
        children: [
          { menuCode: 'system-sources', label: 'Source Databases', icon: 'database', route: '/admin/system/sources', parentCode: 'system' },
          { menuCode: 'system-audit', label: 'Audit Logs', icon: 'file-text', route: '/admin/system/audit', parentCode: 'system' },
        ],
      },
    ],
  },
];

export function AdminLayout() {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [expandedSections, setExpandedSections] = useState<string[]>(['loaders', 'approvals']);
  const [menuItems, setMenuItems] = useState<MenuItem[]>(defaultMenuItems);
  const [isLoadingMenus, setIsLoadingMenus] = useState(true);
  const navigate = useNavigate();

  // Get user info
  const userStr = localStorage.getItem('auth_user');
  const user = userStr ? JSON.parse(userStr) : null;

  // Fetch menus from backend on mount
  useEffect(() => {
    const fetchMenus = async () => {
      logger.entry('AdminLayout', 'fetchMenus');
      setIsLoadingMenus(true);
      try {
        const menus = await authApi.getMenus();
        if (menus && menus.length > 0) {
          // Transform backend DTO to our MenuItem format
          const transformedMenus: MenuItem[] = menus.map(transformMenuItem);
          // Wrap in a root "admin" item to match our structure
          const adminMenu: MenuItem = {
            menuCode: 'admin',
            label: 'Admin Dashboard',
            icon: 'dashboard',
            route: '/admin',
            parentCode: null,
            children: transformedMenus,
          };
          setMenuItems([adminMenu]);
          logger.result('AdminLayout', 'fetchMenus', `Loaded ${menus.length} menu items from backend`);
        } else {
          logger.debug('AdminLayout', 'fetchMenus', 'No menus from backend, using defaults');
        }
      } catch (error) {
        logger.error('AdminLayout', 'fetchMenus', error as Error);
        // Keep default menus on error
      } finally {
        setIsLoadingMenus(false);
        logger.exit('AdminLayout', 'fetchMenus', true);
      }
    };

    fetchMenus();
  }, []);

  // Transform backend MenuItemDTO to our MenuItem format
  const transformMenuItem = (dto: MenuItemDTO): MenuItem => ({
    menuCode: dto.menuCode,
    label: dto.label,
    icon: dto.icon || 'dashboard',
    route: dto.route,
    parentCode: dto.parentCode,
    children: dto.children?.map(transformMenuItem),
  });

  const handleLogout = () => {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_user');
    navigate('/login');
  };

  const toggleSection = (sectionCode: string) => {
    setExpandedSections(prev =>
      prev.includes(sectionCode)
        ? prev.filter(code => code !== sectionCode)
        : [...prev, sectionCode]
    );
  };

  const renderMenuItem = (item: MenuItem, depth = 0) => {
    const Icon = iconMap[item.icon] || LayoutDashboard;
    const hasChildren = item.children && item.children.length > 0;
    const isExpanded = expandedSections.includes(item.menuCode);

    if (hasChildren) {
      return (
        <div key={item.menuCode}>
          <button
            onClick={() => toggleSection(item.menuCode)}
            className={cn(
              'flex items-center w-full px-3 py-2 text-sm font-medium rounded-md transition-colors',
              'hover:bg-muted text-muted-foreground hover:text-foreground',
              depth > 0 && 'pl-6'
            )}
          >
            <Icon className={cn('h-4 w-4', collapsed ? '' : 'mr-3')} />
            {!collapsed && (
              <>
                <span className="flex-1 text-left">{item.label}</span>
                <ChevronRight
                  className={cn(
                    'h-4 w-4 transition-transform',
                    isExpanded && 'rotate-90'
                  )}
                />
              </>
            )}
          </button>
          {isExpanded && !collapsed && (
            <div className="mt-1 space-y-1">
              {item.children!.map(child => renderMenuItem(child, depth + 1))}
            </div>
          )}
        </div>
      );
    }

    return (
      <NavLink
        key={item.menuCode}
        to={item.route || '#'}
        className={({ isActive }) =>
          cn(
            'flex items-center px-3 py-2 text-sm font-medium rounded-md transition-colors',
            isActive
              ? 'bg-primary text-primary-foreground'
              : 'hover:bg-muted text-muted-foreground hover:text-foreground',
            depth > 0 && 'pl-9'
          )
        }
        onClick={() => setMobileMenuOpen(false)}
      >
        <Icon className={cn('h-4 w-4', collapsed ? '' : 'mr-3')} />
        {!collapsed && <span>{item.label}</span>}
      </NavLink>
    );
  };

  const adminSection = menuItems[0];

  return (
    <div className="flex h-screen bg-background">
      {/* Mobile menu button */}
      <button
        className="lg:hidden fixed top-4 left-4 z-50 p-2 rounded-md bg-background border"
        onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
      >
        {mobileMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
      </button>

      {/* Sidebar */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 flex flex-col bg-card border-r transition-all duration-300',
          collapsed ? 'w-16' : 'w-64',
          mobileMenuOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'
        )}
      >
        {/* Logo/Header */}
        <div className="flex items-center justify-between h-16 px-4 border-b">
          {!collapsed && (
            <span className="text-lg font-semibold">Admin</span>
          )}
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setCollapsed(!collapsed)}
            className="hidden lg:flex"
          >
            {collapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
          </Button>
        </div>

        {/* Navigation */}
        <nav className="flex-1 overflow-y-auto p-4 space-y-2">
          {isLoadingMenus ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
              {!collapsed && <span className="ml-2 text-sm text-muted-foreground">Loading...</span>}
            </div>
          ) : (
            adminSection?.children?.map(section => renderMenuItem(section))
          )}
        </nav>

        {/* User section */}
        <div className="border-t p-4">
          {!collapsed && user && (
            <div className="mb-2 text-sm">
              <div className="font-medium">{user.username}</div>
              <div className="text-muted-foreground text-xs">
                {user.roles?.join(', ') || 'User'}
              </div>
            </div>
          )}
          <Button
            variant="ghost"
            size={collapsed ? 'icon' : 'default'}
            onClick={handleLogout}
            className="w-full justify-start"
          >
            <LogOut className={cn('h-4 w-4', collapsed ? '' : 'mr-2')} />
            {!collapsed && 'Logout'}
          </Button>
        </div>
      </aside>

      {/* Overlay for mobile */}
      {mobileMenuOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-30 lg:hidden"
          onClick={() => setMobileMenuOpen(false)}
        />
      )}

      {/* Main content */}
      <main
        className={cn(
          'flex-1 overflow-y-auto transition-all duration-300',
          collapsed ? 'lg:ml-16' : 'lg:ml-64'
        )}
      >
        <div className="p-6 lg:p-8">
          <Outlet />
        </div>
      </main>
    </div>
  );
}

export default AdminLayout;
