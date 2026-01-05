import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  FileText,
  RefreshCw,
  Search,
  CheckCircle,
  XCircle,
  Loader2,
  AlertTriangle,
  Clock,
  User,
  Globe,
  Monitor,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useToast } from '@/hooks/use-toast';
import apiClient from '@/lib/axios';
import { API_ENDPOINTS } from '@/lib/api-config';
import logger from '@/lib/logger';

interface LoginAttempt {
  id: number;
  username: string;
  ipAddress: string | null;
  userAgent: string | null;
  success: boolean;
  failureReason: string | null;
  attemptedAt: string;
}

interface AuditStats {
  successfulLoginsLast24h: number;
  failedLoginsLast24h: number;
  successfulLoginsLast7d: number;
  failedLoginsLast7d: number;
  totalAttempts: number;
  suspiciousUsers: string[];
}

interface PagedResponse {
  content: LoginAttempt[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export default function AuditLogsPage() {
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'SUCCESS' | 'FAILED'>('ALL');
  const [page, setPage] = useState(0);
  const pageSize = 25;

  const { toast } = useToast();

  const { data: stats, isLoading: statsLoading, refetch: refetchStats } = useQuery({
    queryKey: ['audit-stats'],
    queryFn: async () => {
      const response = await apiClient.get<AuditStats>(API_ENDPOINTS.AUDIT_STATS);
      return response.data;
    },
  });

  const { data: attemptsData, isLoading: attemptsLoading, refetch: refetchAttempts, isRefetching } = useQuery({
    queryKey: ['login-attempts', page, statusFilter, searchTerm],
    queryFn: async () => {
      const params = new URLSearchParams();
      params.set('page', page.toString());
      params.set('size', pageSize.toString());
      if (searchTerm) params.set('username', searchTerm);
      if (statusFilter === 'SUCCESS') params.set('success', 'true');
      if (statusFilter === 'FAILED') params.set('success', 'false');

      const response = await apiClient.get<PagedResponse>(
        `${API_ENDPOINTS.AUDIT_LOGIN_ATTEMPTS}?${params.toString()}`
      );
      return response.data;
    },
  });

  const refetch = () => {
    refetchStats();
    refetchAttempts();
  };

  const attempts = attemptsData?.content || [];
  const totalPages = attemptsData?.totalPages || 0;
  const totalElements = attemptsData?.totalElements || 0;

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleString();
  };

  const parseUserAgent = (ua: string | null): string => {
    if (!ua) return 'Unknown';
    if (ua.includes('Chrome')) return 'Chrome';
    if (ua.includes('Firefox')) return 'Firefox';
    if (ua.includes('Safari')) return 'Safari';
    if (ua.includes('Edge')) return 'Edge';
    return 'Other';
  };

  if (statsLoading || attemptsLoading) {
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
          <h1 className="text-2xl font-bold">Audit Logs</h1>
          <p className="text-muted-foreground">Monitor login attempts and security events</p>
        </div>
        <Button variant="outline" size="sm" onClick={refetch} disabled={isRefetching}>
          <RefreshCw className={`h-4 w-4 mr-2 ${isRefetching ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-4 gap-4">
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-muted-foreground">Logins (24h)</p>
              <p className="text-2xl font-bold text-green-600">{stats?.successfulLoginsLast24h || 0}</p>
            </div>
            <CheckCircle className="w-8 h-8 text-green-500" />
          </div>
        </Card>
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-muted-foreground">Failed (24h)</p>
              <p className="text-2xl font-bold text-red-600">{stats?.failedLoginsLast24h || 0}</p>
            </div>
            <XCircle className="w-8 h-8 text-red-500" />
          </div>
        </Card>
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-muted-foreground">Total (7d)</p>
              <p className="text-2xl font-bold">
                {(stats?.successfulLoginsLast7d || 0) + (stats?.failedLoginsLast7d || 0)}
              </p>
            </div>
            <Clock className="w-8 h-8 text-blue-500" />
          </div>
        </Card>
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-muted-foreground">Suspicious</p>
              <p className="text-2xl font-bold text-orange-600">
                {stats?.suspiciousUsers?.length || 0}
              </p>
            </div>
            <AlertTriangle className="w-8 h-8 text-orange-500" />
          </div>
        </Card>
      </div>

      {/* Suspicious Users Alert */}
      {stats?.suspiciousUsers && stats.suspiciousUsers.length > 0 && (
        <Card className="p-4 border-orange-300 bg-orange-50">
          <div className="flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-orange-600 mt-0.5" />
            <div>
              <h4 className="font-medium text-orange-800">Suspicious Activity Detected</h4>
              <p className="text-sm text-orange-700">
                The following users have had 5+ failed login attempts in the last 24 hours:
              </p>
              <div className="flex flex-wrap gap-2 mt-2">
                {stats.suspiciousUsers.map((user) => (
                  <Badge key={user} variant="outline" className="border-orange-400 text-orange-700">
                    {user}
                  </Badge>
                ))}
              </div>
            </div>
          </div>
        </Card>
      )}

      {/* Filters */}
      <div className="flex items-center gap-4">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search by username..."
            value={searchTerm}
            onChange={(e) => { setSearchTerm(e.target.value); setPage(0); }}
            className="pl-9"
          />
        </div>
        <Select value={statusFilter} onValueChange={(v) => { setStatusFilter(v as any); setPage(0); }}>
          <SelectTrigger className="w-[150px]">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All Status</SelectItem>
            <SelectItem value="SUCCESS">Successful</SelectItem>
            <SelectItem value="FAILED">Failed</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* Login Attempts Table */}
      <Card>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Time</TableHead>
              <TableHead>Username</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>IP Address</TableHead>
              <TableHead>Browser</TableHead>
              <TableHead>Details</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {attempts.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center py-8 text-muted-foreground">
                  No login attempts found
                </TableCell>
              </TableRow>
            ) : (
              attempts.map((attempt) => (
                <TableRow key={attempt.id}>
                  <TableCell className="text-sm">
                    <div className="flex items-center gap-1">
                      <Clock className="w-3 h-3 text-muted-foreground" />
                      {formatDate(attempt.attemptedAt)}
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <User className="w-4 h-4 text-muted-foreground" />
                      <span className="font-medium">{attempt.username}</span>
                    </div>
                  </TableCell>
                  <TableCell>
                    {attempt.success ? (
                      <Badge className="bg-green-100 text-green-800">
                        <CheckCircle className="w-3 h-3 mr-1" />
                        Success
                      </Badge>
                    ) : (
                      <Badge className="bg-red-100 text-red-800">
                        <XCircle className="w-3 h-3 mr-1" />
                        Failed
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1 font-mono text-sm">
                      <Globe className="w-3 h-3 text-muted-foreground" />
                      {attempt.ipAddress || 'Unknown'}
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1 text-sm">
                      <Monitor className="w-3 h-3 text-muted-foreground" />
                      {parseUserAgent(attempt.userAgent)}
                    </div>
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {attempt.failureReason || '-'}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </Card>

      {/* Pagination */}
      <div className="flex items-center justify-between">
        <div className="text-sm text-muted-foreground">
          Showing {page * pageSize + 1} to {Math.min((page + 1) * pageSize, totalElements)} of {totalElements} attempts
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setPage(p => Math.max(0, p - 1))}
            disabled={page === 0}
          >
            <ChevronLeft className="h-4 w-4" />
            Previous
          </Button>
          <span className="text-sm text-muted-foreground">
            Page {page + 1} of {totalPages || 1}
          </span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setPage(p => p + 1)}
            disabled={page >= totalPages - 1}
          >
            Next
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>
  );
}
