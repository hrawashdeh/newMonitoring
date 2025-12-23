import apiClient from '../lib/axios';
import { API_ENDPOINTS } from '../lib/api-config';
import type { LoginRequest, LoginResponse } from '../types/auth';

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
    localStorage.setItem('auth_token', token);
    localStorage.setItem('auth_user', JSON.stringify({ username, roles }));
  },
};
