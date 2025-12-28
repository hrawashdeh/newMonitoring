import { Card, CardContent, CardFooter, CardHeader, CardTitle } from './ui/card';
import { Button } from './ui/button';
import { cn } from '@/lib/utils';
import { LucideIcon, ArrowRight } from 'lucide-react';

export interface ActionCardProps {
  icon: LucideIcon;
  title: string;
  description: string;
  actionLabel?: string;
  onClick?: () => void;
  className?: string;
  disabled?: boolean;
}

export function ActionCard({
  icon: Icon,
  title,
  description,
  actionLabel = 'View',
  onClick,
  className,
  disabled = false,
}: ActionCardProps) {
  return (
    <Card
      className={cn(
        'cursor-pointer transition-all hover:shadow-lg hover:border-primary/50',
        disabled && 'opacity-60 cursor-not-allowed hover:shadow-sm hover:border-border',
        className
      )}
      onClick={disabled ? undefined : onClick}
    >
      <CardHeader>
        <div className="flex items-center gap-3">
          <div className="rounded-lg bg-primary/10 p-2">
            <Icon className="h-6 w-6 text-primary" />
          </div>
          <CardTitle className="text-lg">{title}</CardTitle>
        </div>
      </CardHeader>
      <CardContent>
        <p className="text-sm text-muted-foreground">{description}</p>
      </CardContent>
      <CardFooter>
        <Button
          variant="link"
          className="gap-2 p-0"
          disabled={disabled}
        >
          {actionLabel} <ArrowRight className="h-4 w-4" />
        </Button>
      </CardFooter>
    </Card>
  );
}
