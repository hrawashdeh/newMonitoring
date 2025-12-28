import { useEffect, useState } from 'react';

const VERSION_CHECK_INTERVAL = 60000; // Check every 60 seconds

interface VersionInfo {
  version: string;
  buildDate: string;
}

export function useVersionCheck() {
  const [newVersionAvailable, setNewVersionAvailable] = useState(false);

  useEffect(() => {
    const checkVersion = async () => {
      try {
        const response = await fetch('/version.json', {
          cache: 'no-cache',
          headers: {
            'Cache-Control': 'no-cache',
            'Pragma': 'no-cache',
          },
        });

        if (response.ok) {
          const versionInfo: VersionInfo = await response.json();
          const storedVersion = localStorage.getItem('app_version');

          // First visit - store version
          if (!storedVersion) {
            localStorage.setItem('app_version', versionInfo.version);
            return;
          }

          // Version changed - new deployment detected
          if (storedVersion !== versionInfo.version) {
            console.log(
              `New version detected: ${storedVersion} → ${versionInfo.version}`
            );
            setNewVersionAvailable(true);
          }
        }
      } catch (error) {
        console.error('Failed to check version:', error);
      }
    };

    // Check immediately on mount
    checkVersion();

    // Then check periodically
    const interval = setInterval(checkVersion, VERSION_CHECK_INTERVAL);

    return () => clearInterval(interval);
  }, []);

  const reloadApp = () => {
    // Update stored version before reload
    fetch('/version.json', { cache: 'no-cache' })
      .then((res) => res.json())
      .then((versionInfo: VersionInfo) => {
        localStorage.setItem('app_version', versionInfo.version);
        window.location.reload();
      })
      .catch(() => {
        // Reload anyway
        window.location.reload();
      });
  };

  return { newVersionAvailable, reloadApp };
}
