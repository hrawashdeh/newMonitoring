import apiClient from '../lib/axios';
import { API_ENDPOINTS } from '../lib/api-config';

export interface User {
  id: number;
  username: string;
  email: string | null;
  fullName: string | null;
  enabled: boolean;
  accountNonExpired: boolean;
  accountNonLocked: boolean;
  credentialsNonExpired: boolean;
  roles: string[];
  createdAt: string;
  updatedAt: string | null;
  lastLoginAt: string | null;
  createdBy: string | null;
  updatedBy: string | null;
}

export interface CreateUserRequest {
  username: string;
  password: string;
  email?: string;
  fullName?: string;
  roles?: string[];
}

export interface UpdateUserRequest {
  email?: string;
  fullName?: string;
  enabled?: boolean;
  roles?: string[];
}

export interface Role {
  id: number;
  roleName: string;
  description: string | null;
  createdAt: string;
}

export interface CreateRoleRequest {
  roleName: string;
  description?: string;
}

export interface UpdateRoleRequest {
  description?: string;
}

export const usersApi = {
  async getUsers(): Promise<User[]> {
    const response = await apiClient.get<User[]>(API_ENDPOINTS.USERS_LIST);
    return response.data;
  },

  async getUser(id: number): Promise<User> {
    const response = await apiClient.get<User>(API_ENDPOINTS.USER_DETAILS(id));
    return response.data;
  },

  async createUser(data: CreateUserRequest): Promise<User> {
    const response = await apiClient.post<User>(API_ENDPOINTS.CREATE_USER, data);
    return response.data;
  },

  async updateUser(id: number, data: UpdateUserRequest): Promise<User> {
    const response = await apiClient.put<User>(API_ENDPOINTS.UPDATE_USER(id), data);
    return response.data;
  },

  async deleteUser(id: number): Promise<void> {
    await apiClient.delete(API_ENDPOINTS.DELETE_USER(id));
  },

  async changePassword(id: number, newPassword: string): Promise<void> {
    await apiClient.post(API_ENDPOINTS.CHANGE_PASSWORD(id), { newPassword });
  },

  async toggleUserEnabled(id: number): Promise<User> {
    const response = await apiClient.post<User>(API_ENDPOINTS.TOGGLE_USER_ENABLED(id));
    return response.data;
  },
};

export const rolesApi = {
  async getRoles(): Promise<Role[]> {
    const response = await apiClient.get<Role[]>(API_ENDPOINTS.ROLES_LIST);
    return response.data;
  },

  async getRole(id: number): Promise<Role> {
    const response = await apiClient.get<Role>(API_ENDPOINTS.ROLE_DETAILS(id));
    return response.data;
  },

  async createRole(data: CreateRoleRequest): Promise<Role> {
    const response = await apiClient.post<Role>(API_ENDPOINTS.CREATE_ROLE, data);
    return response.data;
  },

  async updateRole(id: number, data: UpdateRoleRequest): Promise<Role> {
    const response = await apiClient.put<Role>(API_ENDPOINTS.UPDATE_ROLE(id), data);
    return response.data;
  },

  async deleteRole(id: number): Promise<void> {
    await apiClient.delete(API_ENDPOINTS.DELETE_ROLE(id));
  },
};

export const permissionsApi = {
  async getEndpoints(): Promise<any[]> {
    const response = await apiClient.get(API_ENDPOINTS.PERMISSIONS_ENDPOINTS);
    return response.data;
  },

  async getPermissionsByRole(): Promise<Record<string, string[]>> {
    const response = await apiClient.get<Record<string, string[]>>(API_ENDPOINTS.PERMISSIONS_BY_ROLE);
    return response.data;
  },

  async setPermissionsForRole(role: string, endpointKeys: string[]): Promise<void> {
    await apiClient.put(API_ENDPOINTS.SET_PERMISSIONS_FOR_ROLE(role), { endpointKeys });
  },

  async grantPermission(endpointKey: string, roleName: string): Promise<void> {
    await apiClient.post(API_ENDPOINTS.GRANT_PERMISSION, { endpointKey, roleName });
  },

  async revokePermission(endpointKey: string, roleName: string): Promise<void> {
    await apiClient.post(API_ENDPOINTS.REVOKE_PERMISSION, { endpointKey, roleName });
  },
};
