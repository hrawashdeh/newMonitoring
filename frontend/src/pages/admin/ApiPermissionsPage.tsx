import { useState, useEffect } from 'react';
import {
  Shield,
  Plus,
  Trash2,
  RefreshCw,
  Search,
  CheckCircle,
  XCircle,
  Loader2,
  Users,
  Key,
  Settings,
  Filter,
} from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useToast } from '@/hooks/use-toast';
import apiClient from '@/lib/axios';
import logger from '@/lib/logger';

interface ApiEndpoint {
  endpointKey: string;
  path: string;
  httpMethod: string;
  serviceId: string;
  controllerClass: string;
  methodName: string;
  description: string;
  enabled: boolean;
  tags: string[];
  status: string;
  lastSeenAt: string;
}

interface RolePermission {
  role: string;
  endpoints: string[];
}

// Standard roles
const ROLES = [
  { id: 'ROLE_SUPER_ADMIN', name: 'Super Admin', description: 'Full access to all features' },
  { id: 'ROLE_ADMIN', name: 'Admin', description: 'Manage loaders, users, and approvals' },
  { id: 'ROLE_APPROVER', name: 'Approver', description: 'Approve and reject submissions' },
  { id: 'ROLE_OPERATOR', name: 'Operator', description: 'Create and edit loaders' },
  { id: 'ROLE_VIEWER', name: 'Viewer', description: 'Read-only access' },
];

const serviceColors: Record<string, string> = {
  ldr: 'bg-blue-100 text-blue-800',
  auth: 'bg-purple-100 text-purple-800',
  ie: 'bg-green-100 text-green-800',
};

export default function ApiPermissionsPage() {
  const [endpoints, setEndpoints] = useState<ApiEndpoint[]>([]);
  const [permissions, setPermissions] = useState<Record<string, string[]>>({});
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [serviceFilter, setServiceFilter] = useState('ALL');
  const [selectedRole, setSelectedRole] = useState(ROLES[0].id);

  // Dialog for managing permissions
  const [permissionDialog, setPermissionDialog] = useState<{
    open: boolean;
    role: string;
    selectedEndpoints: Set<string>;
    saving: boolean;
  }>({ open: false, role: '', selectedEndpoints: new Set(), saving: false });

  const { toast } = useToast();

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async (showRefreshing = false) => {
    logger.entry('ApiPermissionsPage', 'loadData');
    try {
      if (showRefreshing) setRefreshing(true);
      else setLoading(true);

      // Fetch endpoints from auth service permissions API
      const endpointsResponse = await apiClient.get('/v1/auth/permissions/endpoints');
      const endpointList = endpointsResponse.data || [];
      setEndpoints(endpointList);

      // Fetch role permissions from backend
      const permissionsResponse = await apiClient.get('/v1/auth/permissions/by-role');
      setPermissions(permissionsResponse.data || {});

      logger.result('ApiPermissionsPage', 'loadData', `Loaded ${endpointList.length} endpoints`);
    } catch (error: any) {
      logger.error('ApiPermissionsPage', 'loadData', error);
      toast({
        title: 'Error',
        description: error.message || 'Failed to load API permissions',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const filteredEndpoints = endpoints.filter((e) => {
    if (searchTerm && !e.endpointKey.toLowerCase().includes(searchTerm.toLowerCase()) &&
        !e.path.toLowerCase().includes(searchTerm.toLowerCase())) {
      return false;
    }
    if (serviceFilter !== 'ALL' && e.serviceId !== serviceFilter) {
      return false;
    }
    return true;
  });

  const hasPermission = (role: string, endpointKey: string): boolean => {
    return permissions[role]?.includes(endpointKey) || false;
  };

  const openPermissionDialog = (role: string) => {
    setPermissionDialog({
      open: true,
      role,
      selectedEndpoints: new Set(permissions[role] || []),
      saving: false,
    });
  };

  const toggleEndpointInDialog = (endpointKey: string) => {
    const newSelected = new Set(permissionDialog.selectedEndpoints);
    if (newSelected.has(endpointKey)) {
      newSelected.delete(endpointKey);
    } else {
      newSelected.add(endpointKey);
    }
    setPermissionDialog({ ...permissionDialog, selectedEndpoints: newSelected });
  };

  const selectAllInService = (serviceId: string) => {
    const serviceEndpoints = endpoints.filter(e => e.serviceId === serviceId).map(e => e.endpointKey);
    const newSelected = new Set(permissionDialog.selectedEndpoints);
    serviceEndpoints.forEach(k => newSelected.add(k));
    setPermissionDialog({ ...permissionDialog, selectedEndpoints: newSelected });
  };

  const deselectAllInService = (serviceId: string) => {
    const serviceEndpoints = endpoints.filter(e => e.serviceId === serviceId).map(e => e.endpointKey);
    const newSelected = new Set(permissionDialog.selectedEndpoints);
    serviceEndpoints.forEach(k => newSelected.delete(k));
    setPermissionDialog({ ...permissionDialog, selectedEndpoints: newSelected });
  };

  const savePermissions = async () => {
    try {
      setPermissionDialog({ ...permissionDialog, saving: true });

      // Save to backend
      await apiClient.put(`/v1/auth/permissions/role/${permissionDialog.role}`, {
        endpointKeys: Array.from(permissionDialog.selectedEndpoints),
      });

      // Update local state
      setPermissions({
        ...permissions,
        [permissionDialog.role]: Array.from(permissionDialog.selectedEndpoints),
      });

      toast({
        title: 'Permissions Updated',
        description: `Updated permissions for ${permissionDialog.role}`,
      });

      setPermissionDialog({ ...permissionDialog, open: false, saving: false });
    } catch (error: any) {
      toast({
        title: 'Error',
        description: error.response?.data?.message || error.message || 'Failed to save permissions',
        variant: 'destructive',
      });
      setPermissionDialog({ ...permissionDialog, saving: false });
    }
  };

  const getServiceEndpointCount = (serviceId: string, role: string): string => {
    const serviceEndpoints = endpoints.filter(e => e.serviceId === serviceId);
    const allowedCount = serviceEndpoints.filter(e => hasPermission(role, e.endpointKey)).length;
    return `${allowedCount}/${serviceEndpoints.length}`;
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  const services = [...new Set(endpoints.map(e => e.serviceId))];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">API Permissions</h1>
          <p className="text-muted-foreground">Manage role-based API endpoint access</p>
        </div>
        <Button variant="outline" onClick={() => loadData(true)} disabled={refreshing}>
          <RefreshCw className={`h-4 w-4 mr-2 ${refreshing ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-4 gap-4">
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-muted-foreground">Total Endpoints</p>
              <p className="text-2xl font-bold">{endpoints.length}</p>
            </div>
            <Key className="w-8 h-8 text-blue-500" />
          </div>
        </Card>
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-muted-foreground">Services</p>
              <p className="text-2xl font-bold">{services.length}</p>
            </div>
            <Settings className="w-8 h-8 text-purple-500" />
          </div>
        </Card>
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-muted-foreground">Roles</p>
              <p className="text-2xl font-bold">{ROLES.length}</p>
            </div>
            <Users className="w-8 h-8 text-green-500" />
          </div>
        </Card>
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-muted-foreground">Active</p>
              <p className="text-2xl font-bold">{endpoints.filter(e => e.enabled).length}</p>
            </div>
            <Shield className="w-8 h-8 text-orange-500" />
          </div>
        </Card>
      </div>

      <Tabs defaultValue="by-role">
        <TabsList>
          <TabsTrigger value="by-role">
            <Users className="w-4 h-4 mr-2" />
            By Role
          </TabsTrigger>
          <TabsTrigger value="by-endpoint">
            <Key className="w-4 h-4 mr-2" />
            By Endpoint
          </TabsTrigger>
        </TabsList>

        {/* By Role Tab */}
        <TabsContent value="by-role" className="space-y-4 mt-4">
          <div className="grid gap-4">
            {ROLES.map((role) => (
              <Card key={role.id} className="p-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-4">
                    <div className="p-2 rounded-full bg-muted">
                      <Shield className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="font-semibold">{role.name}</h3>
                      <p className="text-sm text-muted-foreground">{role.description}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-4">
                    {/* Service-wise breakdown */}
                    <div className="flex items-center gap-2">
                      {services.map((serviceId) => (
                        <Badge key={serviceId} variant="outline" className={serviceColors[serviceId]}>
                          {serviceId}: {getServiceEndpointCount(serviceId, role.id)}
                        </Badge>
                      ))}
                    </div>
                    <Badge variant="secondary">
                      {permissions[role.id]?.length || 0} / {endpoints.length} endpoints
                    </Badge>
                    <Button variant="outline" size="sm" onClick={() => openPermissionDialog(role.id)}>
                      <Settings className="w-4 h-4 mr-2" />
                      Configure
                    </Button>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        </TabsContent>

        {/* By Endpoint Tab */}
        <TabsContent value="by-endpoint" className="space-y-4 mt-4">
          {/* Filters */}
          <div className="flex items-center gap-4">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Search endpoints..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-9"
              />
            </div>
            <Select value={serviceFilter} onValueChange={setServiceFilter}>
              <SelectTrigger className="w-[150px]">
                <Filter className="h-4 w-4 mr-2" />
                <SelectValue placeholder="Service" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Services</SelectItem>
                {services.map((s) => (
                  <SelectItem key={s} value={s}>{s.toUpperCase()}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={selectedRole} onValueChange={setSelectedRole}>
              <SelectTrigger className="w-[180px]">
                <Users className="h-4 w-4 mr-2" />
                <SelectValue placeholder="Role" />
              </SelectTrigger>
              <SelectContent>
                {ROLES.map((role) => (
                  <SelectItem key={role.id} value={role.id}>{role.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <Card>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Endpoint Key</TableHead>
                  <TableHead>Method</TableHead>
                  <TableHead>Path</TableHead>
                  <TableHead>Service</TableHead>
                  <TableHead className="text-center">{ROLES.find(r => r.id === selectedRole)?.name}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filteredEndpoints.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} className="text-center py-8 text-muted-foreground">
                      No endpoints found
                    </TableCell>
                  </TableRow>
                ) : (
                  filteredEndpoints.map((endpoint) => (
                    <TableRow key={endpoint.endpointKey}>
                      <TableCell className="font-mono text-sm">{endpoint.endpointKey}</TableCell>
                      <TableCell>
                        <Badge variant="outline">{endpoint.httpMethod}</Badge>
                      </TableCell>
                      <TableCell className="font-mono text-xs text-muted-foreground">{endpoint.path}</TableCell>
                      <TableCell>
                        <Badge className={serviceColors[endpoint.serviceId]}>{endpoint.serviceId}</Badge>
                      </TableCell>
                      <TableCell className="text-center">
                        {hasPermission(selectedRole, endpoint.endpointKey) ? (
                          <CheckCircle className="w-5 h-5 text-green-500 mx-auto" />
                        ) : (
                          <XCircle className="w-5 h-5 text-gray-300 mx-auto" />
                        )}
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </Card>
        </TabsContent>
      </Tabs>

      {/* Permission Configuration Dialog */}
      <Dialog open={permissionDialog.open} onOpenChange={(open) => !permissionDialog.saving && setPermissionDialog({ ...permissionDialog, open })}>
        <DialogContent className="max-w-3xl max-h-[80vh] overflow-hidden flex flex-col">
          <DialogHeader>
            <DialogTitle>Configure Permissions</DialogTitle>
            <DialogDescription>
              Select which API endpoints <strong>{ROLES.find(r => r.id === permissionDialog.role)?.name}</strong> can access
            </DialogDescription>
          </DialogHeader>

          <div className="flex-1 overflow-y-auto py-4">
            {services.map((serviceId) => {
              const serviceEndpoints = endpoints.filter(e => e.serviceId === serviceId);
              const selectedCount = serviceEndpoints.filter(e => permissionDialog.selectedEndpoints.has(e.endpointKey)).length;

              return (
                <div key={serviceId} className="mb-6">
                  <div className="flex items-center justify-between mb-2">
                    <h4 className="font-semibold flex items-center gap-2">
                      <Badge className={serviceColors[serviceId]}>{serviceId.toUpperCase()}</Badge>
                      <span className="text-sm text-muted-foreground">
                        ({selectedCount}/{serviceEndpoints.length} selected)
                      </span>
                    </h4>
                    <div className="flex gap-2">
                      <Button variant="ghost" size="sm" onClick={() => selectAllInService(serviceId)}>
                        Select All
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => deselectAllInService(serviceId)}>
                        Deselect All
                      </Button>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-2 pl-4">
                    {serviceEndpoints.map((endpoint) => (
                      <div
                        key={endpoint.endpointKey}
                        className="flex items-center space-x-2 p-2 rounded hover:bg-muted cursor-pointer"
                        onClick={() => toggleEndpointInDialog(endpoint.endpointKey)}
                      >
                        <Checkbox
                          checked={permissionDialog.selectedEndpoints.has(endpoint.endpointKey)}
                          onCheckedChange={() => toggleEndpointInDialog(endpoint.endpointKey)}
                        />
                        <div className="flex-1 min-w-0">
                          <Label className="text-sm font-mono cursor-pointer truncate block">
                            {endpoint.endpointKey}
                          </Label>
                          <span className="text-xs text-muted-foreground">
                            {endpoint.httpMethod} {endpoint.path}
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>

          <DialogFooter>
            <div className="flex items-center justify-between w-full">
              <span className="text-sm text-muted-foreground">
                {permissionDialog.selectedEndpoints.size} endpoints selected
              </span>
              <div className="flex gap-2">
                <Button variant="outline" onClick={() => setPermissionDialog({ ...permissionDialog, open: false })} disabled={permissionDialog.saving}>
                  Cancel
                </Button>
                <Button onClick={savePermissions} disabled={permissionDialog.saving}>
                  {permissionDialog.saving ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : <CheckCircle className="h-4 w-4 mr-2" />}
                  Save Permissions
                </Button>
              </div>
            </div>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
