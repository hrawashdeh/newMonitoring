import { LucideIcon, MoreVertical } from "lucide-react"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { cn } from "@/lib/utils"

export interface ActionButton {
  icon: LucideIcon
  label: string
  onClick: () => void
  variant?: "default" | "destructive" | "outline" | "secondary" | "ghost" | "link"
  loading?: boolean
  disabled?: boolean
}

export interface DropdownAction {
  icon?: LucideIcon
  label?: string
  onClick?: () => void
  divider?: boolean
  disabled?: boolean
}

export interface PageHeaderProps {
  title: string
  subtitle?: string
  primaryActions?: ActionButton[]
  secondaryActions?: DropdownAction[]
  className?: string
}

export function PageHeader({
  title,
  subtitle,
  primaryActions = [],
  secondaryActions = [],
  className,
}: PageHeaderProps) {
  return (
    <div className={cn("border-b bg-background pb-4 mb-6", className)}>
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        {/* Left: Title & Subtitle */}
        <div className="flex-1">
          <h2 className="text-2xl font-bold tracking-tight">{title}</h2>
          {subtitle && (
            <p className="text-muted-foreground mt-1 text-sm">{subtitle}</p>
          )}
        </div>

        {/* Right: Actions */}
        <div className="flex items-center gap-2">
          {/* Primary Actions */}
          {primaryActions.map((action, idx) => {
            const Icon = action.icon
            return (
              <Button
                key={idx}
                variant={action.variant || "ghost"}
                size="sm"
                onClick={action.onClick}
                disabled={action.disabled || action.loading}
                title={action.label}
                aria-label={action.label}
                className="relative gap-2"
              >
                {action.loading ? (
                  <div className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
                ) : (
                  <Icon className="h-4 w-4" />
                )}
                <span className="hidden sm:inline">{action.label}</span>
              </Button>
            )
          })}

          {/* Secondary Actions (Dropdown Menu) */}
          {secondaryActions.length > 0 && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label="More actions"
                  title="More actions"
                >
                  <MoreVertical className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-56">
                {secondaryActions.map((item, idx) => {
                  if (item.divider) {
                    return <DropdownMenuSeparator key={`divider-${idx}`} />
                  }

                  const Icon = item.icon
                  return (
                    <DropdownMenuItem
                      key={idx}
                      onClick={item.onClick}
                      disabled={item.disabled}
                      className="cursor-pointer"
                    >
                      {Icon && <Icon className="mr-2 h-4 w-4" />}
                      <span>{item.label}</span>
                    </DropdownMenuItem>
                  )
                })}
              </DropdownMenuContent>
            </DropdownMenu>
          )}
        </div>
      </div>
    </div>
  )
}
