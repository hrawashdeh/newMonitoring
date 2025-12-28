import { Badge } from '@/components/ui/badge';
import { ApprovalStatus } from '@/types/loader';
import { CheckCircle2, Clock, XCircle } from 'lucide-react';

interface ApprovalStatusBadgeProps {
  status: ApprovalStatus;
  className?: string;
}

/**
 * Visual badge for approval workflow status
 */
export function ApprovalStatusBadge({ status, className }: ApprovalStatusBadgeProps) {
  const getStatusConfig = (status: ApprovalStatus) => {
    switch (status) {
      case 'APPROVED':
        return {
          label: 'Approved',
          variant: 'default' as const,
          className: 'bg-green-500 hover:bg-green-600 text-white',
          icon: CheckCircle2,
        };
      case 'PENDING_APPROVAL':
        return {
          label: 'Pending Approval',
          variant: 'secondary' as const,
          className: 'bg-yellow-500 hover:bg-yellow-600 text-white',
          icon: Clock,
        };
      case 'REJECTED':
        return {
          label: 'Rejected',
          variant: 'destructive' as const,
          className: 'bg-red-500 hover:bg-red-600 text-white',
          icon: XCircle,
        };
      default:
        return {
          label: 'Unknown',
          variant: 'outline' as const,
          className: '',
          icon: Clock,
        };
    }
  };

  const config = getStatusConfig(status);
  const Icon = config.icon;

  return (
    <Badge variant={config.variant} className={`${config.className} ${className || ''} gap-1.5`}>
      <Icon className="h-3.5 w-3.5" />
      {config.label}
    </Badge>
  );
}
