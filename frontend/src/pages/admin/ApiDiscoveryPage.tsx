import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiConfigApi, EndpointInfo } from '@/api/admin';
import { PageHeader } from '@/components/layout/PageHeader';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { RefreshCw, Search, Server, Key, Clock } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import { cn } from '@/lib/utils';

// HTTP Method badge colors
const methodColors: Record<string, string> = {
  GET: 'bg-green-100 text-green-800 border-green-200',
  POST: 'bg-blue-100 text-blue-800 border-blue-200',
  PUT: 'bg-yellow-100 text-yellow-800 border-yellow-200',
  DELETE: 'bg-red-100 text-red-800 border-red-200',
  PATCH: 'bg-purple-100 text-purple-800 border-purple-200',
};

export function ApiDiscoveryPage() {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedService, setSelectedService] = useState<string>('all');
  const { toast } = useToast();

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['api-endpoints'],
    queryFn: apiConfigApi.getAllEndpoints,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });

  const handleRefresh = async () => {
    try {
      await apiConfigApi.refreshCache();
      await refetch();
      toast({
        title: 'Cache Refreshed',
        description: 'API endpoint cache has been refreshed',
      });
    } catch (err) {
      toast({
        title: 'Refresh Failed',
        description: 'Failed to refresh endpoint cache',
        variant: 'destructive',
      });
    }
  };

  // Filter endpoints
  const filteredEndpoints = data?.endpoints?.filter(endpoint => {
    const matchesSearch =
      searchTerm === '' ||
      endpoint.key.toLowerCase().includes(searchTerm.toLowerCase()) ||
      endpoint.path.toLowerCase().includes(searchTerm.toLowerCase()) ||
      endpoint.description.toLowerCase().includes(searchTerm.toLowerCase());

    const matchesService =
      selectedService === 'all' || endpoint.serviceId === selectedService;

    return matchesSearch && matchesService;
  }) || [];

  // Group by service
  const endpointsByService = filteredEndpoints.reduce((acc, endpoint) => {
    if (!acc[endpoint.serviceId]) {
      acc[endpoint.serviceId] = [];
    }
    acc[endpoint.serviceId].push(endpoint);
    return acc;
  }, {} as Record<string, EndpointInfo[]>);

  const services = data?.services || [];

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <RefreshCw className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (error) {
    return (
      <Card className="border-destructive">
        <CardHeader>
          <CardTitle className="text-destructive">Error Loading APIs</CardTitle>
          <CardDescription>
            Failed to fetch API endpoints. Make sure you have admin permissions.
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="API Discovery"
        subtitle="View all discovered API endpoints across services"
        primaryAction={{
          label: 'Refresh Cache',
          icon: <RefreshCw className="h-4 w-4" />,
          onClick: handleRefresh,
        }}
      />

      {/* Stats Cards */}
      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total Endpoints</CardTitle>
            <Key className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{data?.totalEndpoints || 0}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Services</CardTitle>
            <Server className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{services.length}</div>
            <p className="text-xs text-muted-foreground">
              {services.join(', ')}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Filtered Results</CardTitle>
            <Search className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{filteredEndpoints.length}</div>
          </CardContent>
        </Card>
      </div>

      {/* Filters */}
      <Card>
        <CardHeader>
          <CardTitle>Filters</CardTitle>
        </CardHeader>
        <CardContent className="flex gap-4">
          <div className="flex-1">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Search by key, path, or description..."
                value={searchTerm}
                onChange={e => setSearchTerm(e.target.value)}
                className="pl-10"
              />
            </div>
          </div>
          <Tabs value={selectedService} onValueChange={setSelectedService}>
            <TabsList>
              <TabsTrigger value="all">All</TabsTrigger>
              {services.map(service => (
                <TabsTrigger key={service} value={service}>
                  {service.toUpperCase()}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
        </CardContent>
      </Card>

      {/* Endpoints Table */}
      <Card>
        <CardHeader>
          <CardTitle>Discovered Endpoints</CardTitle>
          <CardDescription>
            API endpoints registered by each service at startup
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-[100px]">Method</TableHead>
                <TableHead>API Key</TableHead>
                <TableHead>Path</TableHead>
                <TableHead>Description</TableHead>
                <TableHead>Tags</TableHead>
                <TableHead className="w-[80px]">Service</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredEndpoints.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="text-center text-muted-foreground">
                    No endpoints found
                  </TableCell>
                </TableRow>
              ) : (
                filteredEndpoints.map(endpoint => (
                  <TableRow key={endpoint.key}>
                    <TableCell>
                      <Badge
                        variant="outline"
                        className={cn(
                          'font-mono text-xs',
                          methodColors[endpoint.httpMethod] || 'bg-gray-100'
                        )}
                      >
                        {endpoint.httpMethod}
                      </Badge>
                    </TableCell>
                    <TableCell className="font-mono text-sm">
                      {endpoint.key}
                    </TableCell>
                    <TableCell className="font-mono text-sm text-muted-foreground">
                      {endpoint.path}
                    </TableCell>
                    <TableCell className="text-sm">
                      {endpoint.description || '-'}
                    </TableCell>
                    <TableCell>
                      <div className="flex gap-1 flex-wrap">
                        {endpoint.tags?.map(tag => (
                          <Badge key={tag} variant="secondary" className="text-xs">
                            {tag}
                          </Badge>
                        ))}
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge variant="outline">{endpoint.serviceId}</Badge>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}

export default ApiDiscoveryPage;
