import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Address } from "@/components/AddressSelector";
import { CheckCircle, Download, Loader2, AlertCircle } from "lucide-react";

interface SelectedAddressProps {
  address: Address;
  onDownloadEPC: () => Promise<void>;
}

export function SelectedAddress({ address, onDownloadEPC }: SelectedAddressProps) {
  const [isDownloading, setIsDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [downloadSuccess, setDownloadSuccess] = useState(false);

  const formatFullAddress = () => {
    const parts = [address.line1];
    if (address.line2) parts.push(address.line2);
    parts.push(address.town);
    parts.push(address.postcode);
    return parts.join(", ");
  };

  const handleDownload = async () => {
    setIsDownloading(true);
    setDownloadError(null);
    setDownloadSuccess(false);
    
    try {
      await onDownloadEPC();
      setDownloadSuccess(true);
      setTimeout(() => setDownloadSuccess(false), 3000);
    } catch (error) {
      setDownloadError(
        error instanceof Error 
          ? error.message 
          : "EPC not available for this property"
      );
    } finally {
      setIsDownloading(false);
    }
  };

  return (
    <div className="panel slide-in">
      <div className="panel-header flex items-center gap-2">
        <CheckCircle className="h-5 w-5 text-success" />
        <span>Selected address</span>
      </div>

      <div className="space-y-4">
        <div>
          <p className="text-foreground font-semibold text-base leading-relaxed">
            {formatFullAddress()}
          </p>
          <p className="text-sm text-muted-foreground mt-2">
            UPRN: <span className="font-mono">{address.uprn}</span>
          </p>
        </div>

        <div className="pt-4 border-t border-border">
          <Button
            variant="secondary"
            onClick={handleDownload}
            disabled={isDownloading}
            className="w-full h-11"
          >
            {isDownloading ? (
              <>
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                Downloading...
              </>
            ) : downloadSuccess ? (
              <>
                <CheckCircle className="h-4 w-4 mr-2 text-success" />
                Downloaded!
              </>
            ) : (
              <>
                <Download className="h-4 w-4 mr-2" />
                Download EPC (PDF)
              </>
            )}
          </Button>
          <p className="helper-text text-center">
            Downloads energy performance data from the official UK EPC service
          </p>
        </div>

        {downloadError && (
          <div className="error-inline fade-in flex items-start gap-2">
            <AlertCircle className="h-4 w-4 flex-shrink-0 mt-0.5" />
            <span>{downloadError}</span>
          </div>
        )}
      </div>
    </div>
  );
}
