/**
 * Authentication types for JWT-based auth
 */

export type UserRole = 'ROLE_ADMIN' | 'ROLE_OPERATOR' | 'ROLE_VIEWER';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  type: string; // "Bearer"
  username: string;
  roles: UserRole[];
}

export interface AuthUser {
  username: string;
  roles: UserRole[];
  token: string;
}

export interface AuthContextType {
  user: AuthUser | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  isAuthenticated: boolean;
  hasRole: (role: UserRole) => boolean;
  isLoading: boolean;
}
