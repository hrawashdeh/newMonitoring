import apiClient from '../lib/axios';
import { API_ENDPOINTS } from '../lib/api-config';
import type {
  Loader,
  CreateLoaderRequest,
  UpdateLoaderRequest,
  LoadersStats,
  ActivityEvent,
  SourceDatabase,
  TestQueryRequest,
  TestQueryResponse,
} from '../types/loader';

export const loadersApi = {
  /**
   * Get all loaders with protected fields list
   */
  async getLoaders(): Promise<{ loaders: Loader[]; protectedFields: string[] }> {
    const response = await apiClient.get<{ loaders: Loader[]; _protectedFields: string[] }>(
      API_ENDPOINTS.LOADERS_LIST
    );
    return {
      loaders: response.data.loaders,
      protectedFields: response.data._protectedFields || [],
    };
  },

  /**
   * Get loader by code
   */
  async getLoader(code: string): Promise<Loader> {
    const response = await apiClient.get<Loader>(API_ENDPOINTS.LOADER_DETAILS(code));
    return response.data;
  },

  /**
   * Create new loader
   */
  async createLoader(data: CreateLoaderRequest): Promise<Loader> {
    const response = await apiClient.post<Loader>(API_ENDPOINTS.CREATE_LOADER, data);
    return response.data;
  },

  /**
   * Update existing loader
   */
  async updateLoader(code: string, data: UpdateLoaderRequest): Promise<Loader> {
    const response = await apiClient.put<Loader>(API_ENDPOINTS.UPDATE_LOADER(code), data);
    return response.data;
  },

  /**
   * Delete loader
   */
  async deleteLoader(code: string): Promise<void> {
    await apiClient.delete(API_ENDPOINTS.DELETE_LOADER(code));
  },

  /**
   * Get operational statistics for loaders overview
   */
  async getLoadersStats(): Promise<LoadersStats> {
    const response = await apiClient.get<LoadersStats>(API_ENDPOINTS.LOADERS_STATS);
    return response.data;
  },

  /**
   * Get recent activity events
   */
  async getLoadersActivity(limit = 5): Promise<ActivityEvent[]> {
    const response = await apiClient.get<ActivityEvent[]>(
      `${API_ENDPOINTS.LOADERS_ACTIVITY}?limit=${limit}`
    );
    return response.data;
  },

  /**
   * Get list of source databases for selection
   */
  async getSourceDatabases(): Promise<SourceDatabase[]> {
    const response = await apiClient.get<SourceDatabase[]>(API_ENDPOINTS.SOURCE_DATABASES);
    return response.data;
  },

  /**
   * Approve a pending loader (ADMIN only)
   */
  async approveLoader(code: string, comments?: string): Promise<Loader> {
    const response = await apiClient.post<Loader>(
      API_ENDPOINTS.APPROVE_LOADER(code),
      comments ? { comments } : {}
    );
    return response.data;
  },

  /**
   * Reject a pending loader (ADMIN only)
   */
  async rejectLoader(code: string, rejectionReason: string, comments?: string): Promise<Loader> {
    const response = await apiClient.post<Loader>(
      API_ENDPOINTS.REJECT_LOADER(code),
      { rejectionReason, comments }
    );
    return response.data;
  },

  /**
   * Test SQL query against a source database
   * Validates syntax and execution before creating/updating a loader
   */
  async testQuery(request: TestQueryRequest): Promise<TestQueryResponse> {
    // DEBUG: Remove after debugging
    console.log('DEBUG [loaders.ts]: testQuery called');
    console.log('DEBUG [loaders.ts]: Endpoint:', API_ENDPOINTS.TEST_QUERY);
    console.log('DEBUG [loaders.ts]: Request payload:', request);

    try {
      const response = await apiClient.post<TestQueryResponse>(
        API_ENDPOINTS.TEST_QUERY,
        request
      );
      console.log('DEBUG [loaders.ts]: Response received:', response.data); // DEBUG: Remove after debugging
      return response.data;
    } catch (error) {
      console.error('DEBUG [loaders.ts]: API call failed:', error); // DEBUG: Remove after debugging
      throw error;
    }
  },
};
