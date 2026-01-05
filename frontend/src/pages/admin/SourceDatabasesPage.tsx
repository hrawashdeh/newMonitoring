import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Database,
  RefreshCw,
  Search,
  Server,
  CheckCircle,
  XCircle,
  Loader2,
  Globe,
  Lock,
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
import { loadersApi } from '@/api/loaders';
import type { SourceDatabase } from '@/types/loader';
import logger from '@/lib/logger';

const dbTypeColors: Record<string, string> = {
  POSTGRESQL: 'bg-blue-100 text-blue-800 border-blue-300',
  MYSQL: 'bg-orange-100 text-orange-800 border-orange-300',
  ORACLE: 'bg-red-100 text-red-800 border-red-300',
  MSSQL: 'bg-purple-100 text-purple-800 border-purple-300',
};

export default function SourceDatabasesPage() {
  const [searchTerm, setSearchTerm] = useState('');
  const [typeFilter, setTypeFilter] = useState('ALL');

  const { toast } = useToast();

  const { data: databases = [], isLoading, refetch, isRefetching } = useQuery({
    queryKey: ['source-databases'],
    queryFn: loadersApi.getSourceDatabases,
  });

  const filteredDatabases = databases.filter((db) => {
    if (searchTerm &&
        !db.dbCode.toLowerCase().includes(searchTerm.toLowerCase()) &&
        !db.dbName.toLowerCase().includes(searchTerm.toLowerCase()) &&
        !db.ip.toLowerCase().includes(searchTerm.toLowerCase())) {
      return false;
    }
    if (typeFilter !== 'ALL' && db.dbType !== typeFilter) {
      return false;
    }
    return true;
  });

  const dbTypes = [...new Set(databases.map(db => db.dbType))];

  if (isLoading) {
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
          <h1 className="text-2xl font-bold">Source Databases</h1>
          <p className="text-muted-foreground">View configured source database connections</p>
        </div>
        <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isRefetching}>
          <RefreshCw className={`h-4 w-4 mr-2 ${isRefetching ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-4 gap-4">
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-muted-foreground">Total Sources</p>
              <p className="text-2xl font-bold">{databases.length}</p>
            </div>
            <Database className="w-8 h-8 text-blue-500" />
          </div>
        </Card>
        {dbTypes.slice(0, 3).map((type) => (
          <Card key={type} className="p-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-muted-foreground">{type}</p>
                <p className="text-2xl font-bold">
                  {databases.filter(db => db.dbType === type).length}
                </p>
              </div>
              <Server className="w-8 h-8 text-gray-500" />
            </div>
          </Card>
        ))}
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search by code, name, or IP..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="pl-9"
          />
        </div>
        <Select value={typeFilter} onValueChange={setTypeFilter}>
          <SelectTrigger className="w-[180px]">
            <Database className="h-4 w-4 mr-2" />
            <SelectValue placeholder="Database Type" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All Types</SelectItem>
            {dbTypes.map((type) => (
              <SelectItem key={type} value={type}>{type}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Databases Table */}
      <Card>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Database Code</TableHead>
              <TableHead>Type</TableHead>
              <TableHead>Host</TableHead>
              <TableHead>Port</TableHead>
              <TableHead>Database Name</TableHead>
              <TableHead>Username</TableHead>
              <TableHead>Password</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredDatabases.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="text-center py-8 text-muted-foreground">
                  No source databases found
                </TableCell>
              </TableRow>
            ) : (
              filteredDatabases.map((db) => (
                <TableRow key={db.dbCode}>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Database className="w-4 h-4 text-muted-foreground" />
                      <span className="font-medium">{db.dbCode}</span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge className={dbTypeColors[db.dbType] || 'bg-gray-100 text-gray-800'}>
                      {db.dbType}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <Globe className="w-3 h-3 text-muted-foreground" />
                      <span className="font-mono text-sm">{db.ip}</span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <span className="font-mono text-sm">{db.port}</span>
                  </TableCell>
                  <TableCell>
                    <span className="font-mono text-sm">{db.dbName}</span>
                  </TableCell>
                  <TableCell>
                    <span className="font-mono text-sm">{db.userName}</span>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1 text-muted-foreground">
                      <Lock className="w-3 h-3" />
                      <span className="text-sm">*****</span>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </Card>

      {/* Info Card */}
      <Card className="p-4 bg-muted/50">
        <div className="flex items-start gap-3">
          <Lock className="w-5 h-5 text-muted-foreground mt-0.5" />
          <div>
            <h4 className="font-medium">Security Note</h4>
            <p className="text-sm text-muted-foreground">
              Database passwords are encrypted and stored securely. They are never exposed through the API
              and cannot be viewed in the admin interface. Source database configurations are managed through
              the infrastructure team for security compliance.
            </p>
          </div>
        </div>
      </Card>

      <div className="text-sm text-muted-foreground text-center">
        Showing {filteredDatabases.length} of {databases.length} source databases
      </div>
    </div>
  );
}
