import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Shield,
  Plus,
  RefreshCw,
  Search,
  Edit,
  Trash2,
  Loader2,
  Users,
  Key,
  Settings,
} from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Textarea } from '@/components/ui/textarea';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { useToast } from '@/hooks/use-toast';
import { rolesApi, usersApi, permissionsApi } from '@/api/users';
import type { Role, CreateRoleRequest, UpdateRoleRequest } from '@/api/users';
import logger from '@/lib/logger';

const roleColors: Record<string, string> = {
  ROLE_ADMIN: 'bg-red-100 text-red-800 border-red-300',
  ROLE_SUPER_ADMIN: 'bg-purple-100 text-purple-800 border-purple-300',
  ROLE_OPERATOR: 'bg-blue-100 text-blue-800 border-blue-300',
  ROLE_APPROVER: 'bg-yellow-100 text-yellow-800 border-yellow-300',
  ROLE_VIEWER: 'bg-gray-100 text-gray-800 border-gray-300',
};

export default function RoleManagementPage() {
  const [searchTerm, setSearchTerm] = useState('');
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [selectedRole, setSelectedRole] = useState<Role | null>(null);

  // Form state
  const [formData, setFormData] = useState({
    roleName: '',
    description: '',
  });

  const { toast } = useToast();
  const queryClient = useQueryClient();

  const { data: roles = [], isLoading, refetch, isRefetching } = useQuery({
    queryKey: ['roles'],
    queryFn: rolesApi.getRoles,
  });

  const { data: users = [] } = useQuery({
    queryKey: ['users'],
    queryFn: usersApi.getUsers,
  });

  const { data: permissionsByRole = {} } = useQuery({
    queryKey: ['permissions-by-role'],
    queryFn: permissionsApi.getPermissionsByRole,
  });

  const createMutation = useMutation({
    mutationFn: (data: CreateRoleRequest) => rolesApi.createRole(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['roles'] });
      toast({ title: 'Success', description: 'Role created successfully' });
      setCreateDialogOpen(false);
      resetForm();
    },
    onError: (error: any) => {
      toast({
        title: 'Error',
        description: error.response?.data?.message || 'Failed to create role',
        variant: 'destructive',
      });
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateRoleRequest }) =>
      rolesApi.updateRole(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['roles'] });
      toast({ title: 'Success', description: 'Role updated successfully' });
      setEditDialogOpen(false);
      resetForm();
    },
    onError: (error: any) => {
      toast({
        title: 'Error',
        description: error.response?.data?.message || 'Failed to update role',
        variant: 'destructive',
      });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => rolesApi.deleteRole(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['roles'] });
      toast({ title: 'Success', description: 'Role deleted successfully' });
      setDeleteDialogOpen(false);
      setSelectedRole(null);
    },
    onError: (error: any) => {
      toast({
        title: 'Error',
        description: error.response?.data?.message || 'Failed to delete role',
        variant: 'destructive',
      });
    },
  });

  const resetForm = () => {
    setFormData({
      roleName: '',
      description: '',
    });
    setSelectedRole(null);
  };

  const openEditDialog = (role: Role) => {
    setSelectedRole(role);
    setFormData({
      roleName: role.roleName,
      description: role.description || '',
    });
    setEditDialogOpen(true);
  };

  const getUserCountForRole = (roleName: string): number => {
    return users.filter(u => u.roles.includes(roleName)).length;
  };

  const getPermissionCountForRole = (roleName: string): number => {
    return permissionsByRole[roleName]?.length || 0;
  };

  const isSystemRole = (roleName: string): boolean => {
    return ['ROLE_ADMIN', 'ROLE_VIEWER', 'ROLE_SUPER_ADMIN'].includes(roleName);
  };

  const filteredRoles = roles.filter((role) => {
    if (searchTerm && !role.roleName.toLowerCase().includes(searchTerm.toLowerCase()) &&
        !role.description?.toLowerCase().includes(searchTerm.toLowerCase())) {
      return false;
    }
    return true;
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Role Management</h1>
          <p className="text-muted-foreground">Manage system roles and permissions</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isRefetching}>
            <RefreshCw className={`h-4 w-4 mr-2 ${isRefetching ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button onClick={() => { resetForm(); setCreateDialogOpen(true); }}>
            <Plus className="h-4 w-4 mr-2" />
            Add Role
          </Button>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-4">
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-muted-foreground">Total Roles</p>
              <p className="text-2xl font-bold">{roles.length}</p>
            </div>
            <Shield className="w-8 h-8 text-blue-500" />
          </div>
        </Card>
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-muted-foreground">System Roles</p>
              <p className="text-2xl font-bold text-purple-600">
                {roles.filter(r => isSystemRole(r.roleName)).length}
              </p>
            </div>
            <Settings className="w-8 h-8 text-purple-500" />
          </div>
        </Card>
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-muted-foreground">Custom Roles</p>
              <p className="text-2xl font-bold text-green-600">
                {roles.filter(r => !isSystemRole(r.roleName)).length}
              </p>
            </div>
            <Users className="w-8 h-8 text-green-500" />
          </div>
        </Card>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search roles..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="pl-9"
          />
        </div>
      </div>

      {/* Roles Table */}
      <Card>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Role Name</TableHead>
              <TableHead>Description</TableHead>
              <TableHead className="text-center">Users</TableHead>
              <TableHead className="text-center">API Permissions</TableHead>
              <TableHead>Created</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredRoles.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center py-8 text-muted-foreground">
                  No roles found
                </TableCell>
              </TableRow>
            ) : (
              filteredRoles.map((role) => (
                <TableRow key={role.id}>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Badge className={roleColors[role.roleName] || 'bg-gray-100 text-gray-800'}>
                        {role.roleName.replace('ROLE_', '')}
                      </Badge>
                      {isSystemRole(role.roleName) && (
                        <Badge variant="outline" className="text-xs">System</Badge>
                      )}
                    </div>
                  </TableCell>
                  <TableCell className="text-muted-foreground max-w-md truncate">
                    {role.description || '-'}
                  </TableCell>
                  <TableCell className="text-center">
                    <div className="flex items-center justify-center gap-1">
                      <Users className="w-4 h-4 text-muted-foreground" />
                      <span>{getUserCountForRole(role.roleName)}</span>
                    </div>
                  </TableCell>
                  <TableCell className="text-center">
                    <div className="flex items-center justify-center gap-1">
                      <Key className="w-4 h-4 text-muted-foreground" />
                      <span>{getPermissionCountForRole(role.roleName)}</span>
                    </div>
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {role.createdAt
                      ? new Date(role.createdAt).toLocaleDateString()
                      : '-'}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => openEditDialog(role)}
                        title="Edit"
                      >
                        <Edit className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => {
                          setSelectedRole(role);
                          setDeleteDialogOpen(true);
                        }}
                        title="Delete"
                        className="text-red-500 hover:text-red-700"
                        disabled={isSystemRole(role.roleName)}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </Card>

      {/* Create Role Dialog */}
      <Dialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create Role</DialogTitle>
            <DialogDescription>Add a new role to the system</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label>Role Name *</Label>
              <Input
                value={formData.roleName}
                onChange={(e) => setFormData({ ...formData, roleName: e.target.value })}
                placeholder="ROLE_CUSTOM_NAME"
              />
              <p className="text-xs text-muted-foreground">
                Role names should start with "ROLE_" and use uppercase letters
              </p>
            </div>
            <div className="space-y-2">
              <Label>Description</Label>
              <Textarea
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                placeholder="Describe the purpose of this role..."
                rows={3}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateDialogOpen(false)}>Cancel</Button>
            <Button
              onClick={() => createMutation.mutate({
                roleName: formData.roleName,
                description: formData.description || undefined,
              })}
              disabled={!formData.roleName || createMutation.isPending}
            >
              {createMutation.isPending && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
              Create
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Edit Role Dialog */}
      <Dialog open={editDialogOpen} onOpenChange={setEditDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Edit Role</DialogTitle>
            <DialogDescription>Update role details for {selectedRole?.roleName}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label>Role Name</Label>
              <Input
                value={formData.roleName}
                disabled
                className="bg-muted"
              />
              <p className="text-xs text-muted-foreground">
                Role names cannot be changed after creation
              </p>
            </div>
            <div className="space-y-2">
              <Label>Description</Label>
              <Textarea
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                placeholder="Describe the purpose of this role..."
                rows={3}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditDialogOpen(false)}>Cancel</Button>
            <Button
              onClick={() => selectedRole && updateMutation.mutate({
                id: selectedRole.id,
                data: {
                  description: formData.description || undefined,
                },
              })}
              disabled={updateMutation.isPending}
            >
              {updateMutation.isPending && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
              Save
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Role</DialogTitle>
            <DialogDescription>
              Are you sure you want to delete role "{selectedRole?.roleName}"?
              {getUserCountForRole(selectedRole?.roleName || '') > 0 && (
                <span className="block mt-2 text-yellow-600">
                  Warning: {getUserCountForRole(selectedRole?.roleName || '')} user(s) have this role assigned.
                </span>
              )}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteDialogOpen(false)}>Cancel</Button>
            <Button
              variant="destructive"
              onClick={() => selectedRole && deleteMutation.mutate(selectedRole.id)}
              disabled={deleteMutation.isPending}
            >
              {deleteMutation.isPending && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
