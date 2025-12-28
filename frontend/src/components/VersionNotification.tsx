import { useVersionCheck } from '../hooks/useVersionCheck';
import { Button } from './ui/button';
import { AlertCircle } from 'lucide-react';

export function VersionNotification() {
  const { newVersionAvailable, reloadApp } = useVersionCheck();

  if (!newVersionAvailable) {
    return null;
  }

  return (
    <div className="fixed bottom-4 right-4 z-50 max-w-md">
      <div className="bg-primary text-primary-foreground rounded-lg shadow-lg p-4 border border-primary-foreground/20">
        <div className="flex items-start gap-3">
          <AlertCircle className="h-5 w-5 mt-0.5 flex-shrink-0" />
          <div className="flex-1">
            <h3 className="font-semibold mb-1">New Version Available</h3>
            <p className="text-sm opacity-90 mb-3">
              A new version of the application has been deployed. Please reload to get the latest features and fixes.
            </p>
            <Button
              onClick={reloadApp}
              variant="secondary"
              size="sm"
              className="w-full"
            >
              Reload Now
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
