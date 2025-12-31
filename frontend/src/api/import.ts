import apiClient from '../lib/axios';

export interface ImportError {
  row: number;
  field: string;
  value: string;
  errorMessage: string;
}

export interface ImportResultDto {
  totalRows: number;
  successCount: number;
  errorCount: number;
  created: number;
  updated: number;
  skipped: number;
  errors: ImportError[];
  importLabel?: string;
  dryRun: boolean;
}

export const importApi = {
  /**
   * Upload and import loaders from Excel file
   */
  async uploadImportFile(
    file: File,
    importLabel?: string,
    dryRun: boolean = false
  ): Promise<ImportResultDto> {
    const formData = new FormData();
    formData.append('file', file);

    if (importLabel) {
      formData.append('importLabel', importLabel);
    }

    formData.append('dryRun', dryRun.toString());

    const response = await apiClient.post<ImportResultDto>(
      '/import/upload',
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      }
    );

    return response.data;
  },

  /**
   * Validate import file without actually importing
   */
  async validateImportFile(file: File): Promise<ImportResultDto> {
    const formData = new FormData();
    formData.append('file', file);

    const response = await apiClient.post<ImportResultDto>(
      '/import/validate',
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      }
    );

    return response.data;
  },
};
