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
import { CheckCircle2, Loader2 } from 'lucide-react';
import { Loader } from '@/types/loader';

interface ApproveLoaderDialogProps {
  loader: Loader;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: (comments?: string) => Promise<void>;
}

/**
 * Confirmation dialog for approving a loader
 */
export function ApproveLoaderDialog({
  loader,
  open,
  onOpenChange,
  onConfirm,
}: ApproveLoaderDialogProps) {
  const [comments, setComments] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleApprove = async () => {
    setIsSubmitting(true);
    try {
      await onConfirm(comments || undefined);
      setComments(''); // Reset form
      onOpenChange(false);
    } catch (error) {
      // Error handled by parent component
      console.error('Approval failed:', error);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <CheckCircle2 className="h-5 w-5 text-green-500" />
            Approve Loader
          </DialogTitle>
          <DialogDescription>
            You are about to approve loader <span className="font-semibold">{loader.loaderCode}</span>.
            This will allow the loader to be enabled and execute.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          {/* Optional Comments */}
          <div className="space-y-2">
            <Label htmlFor="approve-comments">
              Comments <span className="text-muted-foreground text-sm">(Optional)</span>
            </Label>
            <Textarea
              id="approve-comments"
              value={comments}
              onChange={(e) => setComments(e.target.value)}
              placeholder="E.g., Verified SQL query safety and configuration..."
              rows={4}
              disabled={isSubmitting}
              className="resize-none"
            />
            <p className="text-xs text-muted-foreground">
              Add any notes about your approval decision for audit purposes.
            </p>
          </div>

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
          </div>
        </div>

        <DialogFooter className="gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isSubmitting}
          >
            Cancel
          </Button>
          <Button
            type="button"
            onClick={handleApprove}
            disabled={isSubmitting}
            className="bg-green-500 hover:bg-green-600"
          >
            {isSubmitting ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Approving...
              </>
            ) : (
              <>
                <CheckCircle2 className="mr-2 h-4 w-4" />
                Approve Loader
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
