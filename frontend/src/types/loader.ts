/**
 * Type definitions for Loader Management
 * Based on loader-service API contracts
 */

export type LoaderStatus = 'ACTIVE' | 'PAUSED' | 'FAILED';

export type DatabaseType = 'POSTGRESQL' | 'MYSQL';

export interface SourceDatabase {
  code: string;
  host: string;
  port: number;
  dbName: string;
  type: DatabaseType;
  userName: string;
  // Password is encrypted and never returned in API responses
  passWord?: string;
}

export interface Segment {
  segmentCode: string;
  segmentDescription?: string;
}

export interface Loader {
  loaderCode: string;
  sourceDatabase: SourceDatabase;
  loaderSql: string; // Encrypted in backend
  intervalSeconds: number;
  maxParallelism: number;
  fetchSize: number;
  segments: Segment[];
  status: LoaderStatus;
  lastRun?: string; // ISO timestamp
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateLoaderRequest {
  loaderCode: string;
  sourceDatabase: {
    code: string;
    host: string;
    port: number;
    dbName: string;
    type: DatabaseType;
    userName: string;
    passWord: string;
  };
  loaderSql: string;
  intervalSeconds: number;
  maxParallelism: number;
  fetchSize: number;
  segments: string[]; // Array of segment codes
}

export interface UpdateLoaderRequest extends Partial<CreateLoaderRequest> {
  loaderCode: string;
}

export interface SignalData {
  timestamp: number; // epoch milliseconds
  recordCount: number;
  minValue: number;
  maxValue: number;
  avgValue: number;
  segmentCode?: string;
}

export interface SignalsQueryParams {
  fromEpoch: number;
  toEpoch: number;
  segmentCode?: string;
}
