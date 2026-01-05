import apiClient from '../lib/axios';
import { API_ENDPOINTS } from '../lib/api-config';
import type { LoginRequest, LoginResponse } from '../types/auth';

export interface MenuItemDTO {
  menuCode: string;
  parentCode: string | null;
  label: string;
  icon: string | null;
  route: string | null;
  menuType: string;
  children?: MenuItemDTO[];
}

export const authApi = {
  /**
   * Login with username and password
   */
  async login(credentials: LoginRequest): Promise<LoginResponse> {
    // Login endpoint doesn't require auth, so use axios directly without interceptor
    const response = await apiClient.post<LoginResponse>(
      API_ENDPOINTS.LOGIN,
      credentials
    );
    return response.data;
  },

  /**
   * Logout (clear local storage)
   */
  logout(): void {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_user');
    localStorage.removeItem('auth_username');
    localStorage.removeItem('auth_role');
  },

  /**
   * Get stored user info
   */
  getStoredUser(): { username: string; roles: string[] } | null {
    const userStr = localStorage.getItem('auth_user');
    if (!userStr) return null;
    try {
      return JSON.parse(userStr);
    } catch {
      return null;
    }
  },

  /**
   * Store auth data
   */
  storeAuth(token: string, username: string, roles: string[]): void {
    const primaryRole = roles && roles.length > 0 ? roles[0] : 'VIEWER';
    console.log('[issue_blocked_imp] Storing auth data:', {
      username,
      roles,
      primaryRole,
      timestamp: new Date().toISOString()
    });

    localStorage.setItem('auth_token', token);
    localStorage.setItem('auth_user', JSON.stringify({ username, roles }));
    // Store username and primary role separately for easy access
    localStorage.setItem('auth_username', username);
    localStorage.setItem('auth_role', primaryRole);

    console.log('[issue_blocked_imp] Auth data stored in localStorage:', {
      auth_username: localStorage.getItem('auth_username'),
      auth_role: localStorage.getItem('auth_role'),
      auth_user: localStorage.getItem('auth_user')
    });
  },

  /**
   * Get user menus based on roles
   */
  async getMenus(): Promise<MenuItemDTO[]> {
    try {
      const response = await apiClient.get<MenuItemDTO[]>(API_ENDPOINTS.MENUS_USER);
      return response.data;
    } catch (error) {
      console.error('Failed to fetch menus:', error);
      return [];
    }
  },
};
