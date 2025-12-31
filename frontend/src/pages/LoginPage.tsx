import { useState, useRef } from 'react';
import { authApi } from '../api/auth';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';

export default function LoginPage() {
  const [username, setUsername] = useState('');
  const passwordRef = useRef<HTMLInputElement>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    const password = passwordRef.current?.value || '';

    try {
      console.log('[issue_blocked_imp] Login attempt:', { username, timestamp: new Date().toISOString() });
      const response = await authApi.login({ username, password });

      console.log('[issue_blocked_imp] Login response received:', {
        username: response.username,
        roles: response.roles,
        hasToken: !!response.token,
        timestamp: new Date().toISOString()
      });

      // Store token and user info
      authApi.storeAuth(response.token, response.username, response.roles);

      console.log('[issue_blocked_imp] Redirecting to home page');
      // Redirect to home page (use window.location to force full page reload)
      window.location.href = '/';
    } catch (err: any) {
      console.error('[Login] Error occurred:', {
        status: err.response?.status,
        statusText: err.response?.statusText,
        data: err.response?.data,
        message: err.message,
      });

      setError(
        err.response?.data?.message ||
        err.message ||
        'Invalid username or password'
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen bg-background">
      <div className="w-full max-w-md p-8 space-y-6 bg-card rounded-lg border shadow-lg">
        <div className="text-center">
          <h1 className="text-3xl font-bold">Loader Management</h1>
          <p className="text-muted-foreground mt-2">Sign in to your account</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <label htmlFor="username" className="text-sm font-medium">
              Username
            </label>
            <Input
              id="username"
              type="text"
              placeholder="Enter username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
              disabled={loading}
            />
          </div>

          <div className="space-y-2">
            <label htmlFor="password" className="text-sm font-medium">
              Password
            </label>
            <Input
              ref={passwordRef}
              id="password"
              type="password"
              placeholder="Enter password"
              required
              disabled={loading}
              autoComplete="current-password"
            />
          </div>

          {error && (
            <div className="p-3 text-sm text-destructive bg-destructive/10 border border-destructive/20 rounded-md">
              {error}
            </div>
          )}

          <Button type="submit" className="w-full" disabled={loading}>
            {loading ? 'Signing in...' : 'Sign In'}
          </Button>
        </form>
      </div>
    </div>
  );
}
