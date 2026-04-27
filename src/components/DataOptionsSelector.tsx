import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Address } from "@/components/AddressSelector";
import { AlertCircle, CheckCircle, Loader2, XCircle } from "lucide-react";
import { ReportPreviewResponse } from "@/lib/report-api";

export interface DataOptions {
  epc: boolean;
  priceHistory: boolean;
  floodRisk: boolean;
  schoolsCatchment: boolean;
  crimeStats: boolean;
}

interface DataOptionsSelectorProps {
  address: Address;
  preview: ReportPreviewResponse | null;
  onPrepare: (options: DataOptions) => Promise<void>;
  previewError?: string | null;
  reportOptions: DataOptions;
  isAuthenticated: boolean;
  useLegacyDownload?: boolean;
  credits: number;
  isPreparing?: boolean;
  previewLoading?: boolean;
}

const dataAvailabilityList = [
  {
    id: "epc" as const,
    label: "Environmental Performance",
    description: "EPC rating, energy costs, building fabric details",
    available: true,
  },
  {
    id: "priceHistory" as const,
    label: "Historical Prices",
    description: "Past sale prices from HM Land Registry",
    available: true,
  },
  {
    id: "floodRisk" as const,
    label: "Flood Risk Assessment",
    description: "Environment Agency active flood alerts and warnings",
  },
  {
    id: "crimeStats" as const,
    label: "Crime Statistics",
    description: "Local crime data from Police UK",
  },
];

export function DataOptionsSelector({
  address,
  preview,
  onPrepare,
  previewError,
  reportOptions,
  isAuthenticated,
  useLegacyDownload = false,
  credits,
  isPreparing = false,
  previewLoading = false,
}: DataOptionsSelectorProps) {
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const downloadOptions: DataOptions | null = preview
    ? {
        ...reportOptions,
        epc: preview.epcAvailable,
        priceHistory: preview.priceHistoryAvailable,
        floodRisk: preview.floodRiskAvailable,
        schoolsCatchment: false,
        crimeStats: preview.crimeStatsAvailable,
      }
    : null;

  const handlePrepare = async () => {
    if (!preview) {
      setError("Report data is still being checked. Try again in a moment.");
      return;
    }
    if (preview.availableSectionCount === 0) {
      setError("No report data was found for this address.");
      return;
    }
    if (!isAuthenticated) {
      setError("Sign in to download reports.");
      return;
    }
    if (!useLegacyDownload && credits < 1) {
      setError("You need at least one credit to download.");
      return;
    }

    setError(null);
    try {
      await onPrepare(downloadOptions ?? reportOptions);
      setSuccess(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not prepare download.");
      setSuccess(false);
    }
  };

  const formatAddress = () => {
    const parts = [address.line1, address.line2, address.town, address.postcode];
    return parts.filter(Boolean).join(", ");
  };

  const isDataAvailable = (id: (typeof dataAvailabilityList)[number]["id"]) => {
    if (!preview) {
      return false;
    }
    switch (id) {
      case "epc":
        return preview.epcAvailable;
      case "priceHistory":
        return preview.priceHistoryAvailable;
      case "floodRisk":
        return preview.floodRiskAvailable;
      case "crimeStats":
        return preview.crimeStatsAvailable;
    }
  };

  return (
    <div className="panel slide-in">
      <div className="panel-header flex items-center gap-2">
        <CheckCircle className="h-5 w-5 text-success" />
        <span>Selected address</span>
      </div>

      <div className="space-y-6">
        <div>
          <p className="text-foreground font-semibold text-base leading-relaxed">{formatAddress()}</p>
          <p className="text-sm text-muted-foreground mt-2">
            UPRN: <span className="font-mono">{address.uprn}</span>
          </p>
        </div>

        <div className="pt-4 border-t border-border">
          <h3 className="text-sm font-medium text-foreground mb-4">Report data found</h3>

          <div className="space-y-3">
            {dataAvailabilityList.map((option) => {
              const available = isDataAvailable(option.id);

              return (
                <div
                  key={option.id}
                  className={`flex items-start gap-3 p-3 rounded-lg border transition-colors ${
                    previewLoading || !preview
                      ? "border-border bg-muted/30"
                      : available
                        ? "border-success/30 bg-success/5"
                        : "border-destructive/30 bg-destructive/5"
                  }`}
                >
                  {previewLoading || !preview ? (
                    <Loader2 className="h-4 w-4 mt-0.5 text-muted-foreground animate-spin" />
                  ) : available ? (
                    <CheckCircle className="h-4 w-4 mt-0.5 text-success" />
                  ) : (
                    <XCircle className="h-4 w-4 mt-0.5 text-destructive" />
                  )}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-3">
                      <p className="text-sm font-medium text-foreground">
                        {option.label}
                      </p>
                      <span
                        className={`text-xs font-medium ${
                          previewLoading || !preview
                            ? "text-muted-foreground"
                            : available
                              ? "text-success"
                              : "text-destructive"
                        }`}
                      >
                        {previewLoading || !preview ? "checking..." : available ? "found" : "not found"}
                      </span>
                    </div>
                    <p className="text-xs text-muted-foreground mt-1">{option.description}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        <div className="flex flex-col gap-2 pt-4 border-t border-border">
          <Button
            onClick={handlePrepare}
            disabled={isPreparing || previewLoading || !preview || preview.availableSectionCount === 0}
            className="w-full h-11"
          >
            {isPreparing ? (
              <>
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                Preparing download...
              </>
            ) : success ? (
              <>
                <CheckCircle className="h-4 w-4 mr-2" />
                Download started
              </>
            ) : (
              <>Download (1 credit)</>
            )}
          </Button>

          <p className="helper-text text-center">
            {useLegacyDownload
              ? "Data check is free. Legacy download is available until paywall enforcement is enabled."
              : "Data check is free. Download requires 1 credit per report."}
          </p>

          {preview ? (
            <div className="space-y-3 text-xs">
              {preview.availableSectionCount === 0 && <p className="text-destructive">No matching data sections found.</p>}
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">
              {previewLoading ? "Checking what data is available..." : "Data availability has not been checked yet."}
            </p>
          )}

          {isAuthenticated && <p className="text-xs text-muted-foreground">Balance: {credits} credit{credits === 1 ? "" : "s"}</p>}
        </div>

        {(previewError || error) && (
          <div className="error-inline fade-in flex items-start gap-2">
            <AlertCircle className="h-4 w-4 flex-shrink-0 mt-0.5" />
            <span>{error || previewError}</span>
          </div>
        )}
      </div>
    </div>
  );
}
