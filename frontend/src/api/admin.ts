import api from '@/lib/axios';

// Menu types
export interface MenuItem {
  menuCode: string;
  parentCode: string | null;
  label: string;
  icon: string | null;
  route: string | null;
  requiredApiKey: string | null;
  sortOrder: number;
  enabled: boolean;
  menuType: 'LINK' | 'SECTION' | 'DIVIDER';
}

export interface MenuResponse {
  menus: MenuItem[];
  role: string;
}

// API Endpoint types
export interface EndpointInfo {
  key: string;
  path: string;
  httpMethod: string;
  serviceId: string;
  controllerClass: string;
  methodName: string;
  description: string;
  enabled: boolean;
  tags: string[];
  registeredAt: string;
  registeredBy: string;
}

export interface ApiEndpointsResponse {
  services: string[];
  totalEndpoints: number;
  endpoints: EndpointInfo[];
}

export interface ApiRolePermission {
  id: number;
  endpointKey: string;
  roleName: string;
  grantedBy: string;
  grantedAt: string;
}

// Menu API
export const menuApi = {
  // Get menus for current user's role
  getMenus: async (): Promise<MenuResponse> => {
    const response = await api.get<MenuResponse>('/v1/ldr/menu/user');
    return response.data;
  },

  // Get all menus (admin only)
  getAllMenus: async (): Promise<MenuItem[]> => {
    const response = await api.get<MenuItem[]>('/v1/ldr/menu/all');
    return response.data;
  },
};

// API Config API
export const apiConfigApi = {
  // Get all endpoints across all services
  getAllEndpoints: async (): Promise<ApiEndpointsResponse> => {
    const response = await api.get<ApiEndpointsResponse>('/v1/ldr/apiconf/all');
    return response.data;
  },

  // Get endpoints by service
  getEndpointsByService: async (serviceId: string): Promise<EndpointInfo[]> => {
    const response = await api.get<EndpointInfo[]>('/v1/ldr/apiconf/endpoints', {
      params: { serviceId },
    });
    return response.data;
  },

  // Get single endpoint info
  getEndpoint: async (key: string): Promise<EndpointInfo> => {
    const response = await api.get<EndpointInfo>(`/v1/ldr/apiconf/endpoints/${key}`);
    return response.data;
  },

  // Refresh endpoint cache
  refreshCache: async (): Promise<{ status: string }> => {
    const response = await api.post<{ status: string }>('/v1/ldr/apiconf/refresh');
    return response.data;
  },
};

// Source databases API
export const sourcesApi = {
  // Get all source databases
  getSources: async () => {
    const response = await api.get('/v1/ldr/src/db-sources');
    return response.data;
  },

  // Reload security
  reloadSecurity: async () => {
    const response = await api.post('/v1/ldr/src/security/reload');
    return response.data;
  },
};

// Admin operations API
export const adminOpsApi = {
  // Get loader status
  getLoaderStatus: async (loaderCode: string) => {
    const response = await api.get(`/v1/ldr/admn/${loaderCode}/status`);
    return response.data;
  },

  // Pause loader
  pauseLoader: async (loaderCode: string) => {
    const response = await api.post(`/v1/ldr/admn/${loaderCode}/pause`);
    return response.data;
  },

  // Resume loader
  resumeLoader: async (loaderCode: string) => {
    const response = await api.post(`/v1/ldr/admn/${loaderCode}/resume`);
    return response.data;
  },

  // Adjust timestamp
  adjustTimestamp: async (loaderCode: string, timestamp: string | null) => {
    const response = await api.post(`/v1/ldr/admn/${loaderCode}/adjust-timestamp`, {
      timestamp,
    });
    return response.data;
  },

  // Get execution history
  getExecutionHistory: async (params?: {
    loaderCode?: string;
    status?: string;
    limit?: number;
  }) => {
    const response = await api.get('/v1/ldr/admn/history', { params });
    return response.data;
  },
};
