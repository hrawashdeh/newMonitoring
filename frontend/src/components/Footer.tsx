import { BUILD_INFO } from '../buildInfo';

export function Footer() {
  const formatBuildDate = (isoDate: string) => {
    try {
      const date = new Date(isoDate);
      return date.toLocaleString('en-US', {
        year: 'numeric',
        month: 'short',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: true,
      });
    } catch {
      return isoDate;
    }
  };

  return (
    <footer className="border-t bg-muted/30 mt-auto">
      <div className="container mx-auto px-6 py-3">
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <div className="flex items-center gap-4">
            <span>Loader Management System v{BUILD_INFO.version}</span>
            <span className="hidden sm:inline">•</span>
            <span className="hidden sm:inline">
              Build: <span className="font-mono font-semibold text-foreground">{BUILD_INFO.buildNumber}</span>
            </span>
          </div>
          <div className="text-right">
            <span className="hidden md:inline">Deployed: </span>
            <span className="font-mono">{formatBuildDate(BUILD_INFO.buildDate)}</span>
          </div>
        </div>
      </div>
    </footer>
  );
}