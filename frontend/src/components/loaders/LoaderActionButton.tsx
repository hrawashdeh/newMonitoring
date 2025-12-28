import { LucideIcon } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

export interface LoaderAction {
  id: string;
  icon: LucideIcon;
  label: string;
  onClick: () => void;
  enabled: boolean; // Based on _links presence or field protection
  variant?: 'default' | 'destructive' | 'outline' | 'secondary' | 'ghost' | 'link';
  iconColor?: string;
  disabledReason?: string; // Reason why action is disabled (e.g., "Action disabled due to data protection")
}

interface LoaderActionButtonProps {
  action: LoaderAction;
  showLabel?: boolean; // true for detail panel, false for icon-only
  className?: string;
}

export function LoaderActionButton({
  action,
  showLabel = false,
  className,
}: LoaderActionButtonProps) {
  const Icon = action.icon;

  // Determine tooltip text - show disabled reason if disabled, otherwise show label
  const tooltipText = !action.enabled && action.disabledReason
    ? action.disabledReason
    : action.label;

  if (showLabel) {
    // Detail panel or dropdown: Icon + Label
    return (
      <Button
        variant={action.variant || 'outline'}
        size="sm"
        onClick={(e) => {
          e.stopPropagation();
          action.onClick();
        }}
        disabled={!action.enabled}
        className={cn('justify-start gap-2', className)}
        title={tooltipText}
      >
        <Icon className={cn('h-4 w-4 flex-shrink-0', action.iconColor)} />
        <span className="truncate">{action.label}</span>
      </Button>
    );
  }

  // Row actions: Icon only with tooltip
  return (
    <Button
      variant="ghost"
      size="icon"
      className={cn('h-8 w-8', className)}
      onClick={(e) => {
        e.stopPropagation();
        action.onClick();
      }}
      disabled={!action.enabled}
      title={tooltipText}
    >
      <Icon className={cn('h-4 w-4', action.iconColor)} />
    </Button>
  );
}
