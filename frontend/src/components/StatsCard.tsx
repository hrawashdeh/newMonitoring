import { Card, CardContent, CardHeader } from './ui/card';
import { cn } from '@/lib/utils';
import { LucideIcon } from 'lucide-react';

export interface StatsCardProps {
  label: string;
  value: number | string;
  subtitle?: string;
  icon?: LucideIcon;
  trend?: {
    direction: 'up' | 'down' | 'neutral';
    value: string;
  };
  status?: 'success' | 'warning' | 'error' | 'default';
  className?: string;
  onClick?: () => void;
}

const statusStyles = {
  success: 'border-green-500/20 bg-green-50/50 dark:bg-green-950/20',
  warning: 'border-yellow-500/20 bg-yellow-50/50 dark:bg-yellow-950/20',
  error: 'border-red-500/20 bg-red-50/50 dark:bg-red-950/20',
  default: '',
};

const trendStyles = {
  up: 'text-green-600 dark:text-green-400',
  down: 'text-red-600 dark:text-red-400',
  neutral: 'text-muted-foreground',
};

const trendIcons = {
  up: '↗',
  down: '↘',
  neutral: '→',
};

export function StatsCard({
  label,
  value,
  subtitle,
  icon: Icon,
  trend,
  status = 'default',
  className,
  onClick,
}: StatsCardProps) {
  return (
    <Card className={cn(statusStyles[status], className)} onClick={onClick}>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <h3 className="text-sm font-medium text-muted-foreground">{label}</h3>
        {Icon && <Icon className="h-4 w-4 text-muted-foreground" />}
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold">{value}</div>
        {subtitle && (
          <p className="text-xs text-muted-foreground mt-1">{subtitle}</p>
        )}
        {trend && (
          <p className={cn('text-xs mt-1 flex items-center gap-1', trendStyles[trend.direction])}>
            <span>{trendIcons[trend.direction]}</span>
            <span>{trend.value}</span>
          </p>
        )}
      </CardContent>
    </Card>
  );
}
