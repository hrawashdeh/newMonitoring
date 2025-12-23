import apiClient from '../lib/axios';
import { API_ENDPOINTS } from '../lib/api-config';
import type { Loader, CreateLoaderRequest, UpdateLoaderRequest } from '../types/loader';

export const loadersApi = {
  /**
   * Get all loaders
   */
  async getLoaders(): Promise<Loader[]> {
    const response = await apiClient.get<Loader[]>(API_ENDPOINTS.LOADERS_LIST);
    return response.data;
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
};
