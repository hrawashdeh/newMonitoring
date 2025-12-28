import { useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { XCircle, Loader2, AlertCircle } from 'lucide-react';
import { Loader } from '@/types/loader';

interface RejectLoaderDialogProps {
  loader: Loader;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: (rejectionReason: string, comments?: string) => Promise<void>;
}

/**
 * Confirmation dialog for rejecting a loader
 */
export function RejectLoaderDialog({
  loader,
  open,
  onOpenChange,
  onConfirm,
}: RejectLoaderDialogProps) {
  const [rejectionReason, setRejectionReason] = useState('');
  const [comments, setComments] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [validationError, setValidationError] = useState('');

  const handleReject = async () => {
    // Validation
    if (!rejectionReason.trim()) {
      setValidationError('Rejection reason is required');
      return;
    }

    setIsSubmitting(true);
    setValidationError('');
    try {
      await onConfirm(rejectionReason, comments || undefined);
      // Reset form
      setRejectionReason('');
      setComments('');
      onOpenChange(false);
    } catch (error) {
      // Error handled by parent component
      console.error('Rejection failed:', error);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleOpenChange = (open: boolean) => {
    if (!open) {
      // Reset form when closing
      setRejectionReason('');
      setComments('');
      setValidationError('');
    }
    onOpenChange(open);
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[600px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <XCircle className="h-5 w-5 text-red-500" />
            Reject Loader
          </DialogTitle>
          <DialogDescription>
            You are about to reject loader <span className="font-semibold">{loader.loaderCode}</span>.
            The loader will be disabled and cannot execute until approved.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          {/* Validation Error */}
          {validationError && (
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>{validationError}</AlertDescription>
            </Alert>
          )}

          {/* Rejection Reason (Required) */}
          <div className="space-y-2">
            <Label htmlFor="rejection-reason">
              Rejection Reason <span className="text-red-500">*</span>
            </Label>
            <Textarea
              id="rejection-reason"
              value={rejectionReason}
              onChange={(e) => {
                setRejectionReason(e.target.value);
                setValidationError(''); // Clear validation error on change
              }}
              placeholder="E.g., SQL query contains unsafe DELETE operations..."
              rows={4}
              disabled={isSubmitting}
              className={`resize-none ${validationError ? 'border-red-500' : ''}`}
            />
            <p className="text-xs text-muted-foreground">
              Required. Explain what needs to be fixed so the creator can resubmit.
            </p>
          </div>

          {/* Additional Comments (Optional) */}
          <div className="space-y-2">
            <Label htmlFor="reject-comments">
              Additional Comments <span className="text-muted-foreground text-sm">(Optional)</span>
            </Label>
            <Textarea
              id="reject-comments"
              value={comments}
              onChange={(e) => setComments(e.target.value)}
              placeholder="E.g., Please remove DELETE statements and resubmit for approval..."
              rows={3}
              disabled={isSubmitting}
              className="resize-none"
            />
            <p className="text-xs text-muted-foreground">
              Add any additional guidance or notes for audit purposes.
            </p>
          </div>

          {/* Warning Box */}
          <Alert>
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>
              Rejecting this loader will automatically disable it and prevent execution
              until it is resubmitted and approved.
            </AlertDescription>
          </Alert>

          {/* Loader Info */}
          <div className="rounded-md bg-muted/50 p-3 space-y-1 text-sm">
            <div className="flex justify-between">
              <span className="text-muted-foreground">Loader Code:</span>
              <span className="font-medium">{loader.loaderCode}</span>
            </div>
            {loader.sourceDatabase && (
              <div className="flex justify-between">
                <span className="text-muted-foreground">Source Database:</span>
                <span className="font-medium">{loader.sourceDatabase.dbCode}</span>
              </div>
            )}
            <div className="flex justify-between">
              <span className="text-muted-foreground">Current Status:</span>
              <span className="font-medium">{loader.enabled ? 'Enabled' : 'Disabled'}</span>
            </div>
          </div>
        </div>

        <DialogFooter className="gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => handleOpenChange(false)}
            disabled={isSubmitting}
          >
            Cancel
          </Button>
          <Button
            type="button"
            onClick={handleReject}
            disabled={isSubmitting}
            variant="destructive"
          >
            {isSubmitting ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Rejecting...
              </>
            ) : (
              <>
                <XCircle className="mr-2 h-4 w-4" />
                Reject Loader
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
