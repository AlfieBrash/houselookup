import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { Address } from "@/components/AddressSelector";
import { AlertCircle, CheckCircle, Loader2 } from "lucide-react";
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
  onPreview: (options: DataOptions) => Promise<ReportPreviewResponse>;
  onPrepare: (options: DataOptions) => Promise<void>;
  isAuthenticated: boolean;
  useLegacyDownload?: boolean;
  credits: number;
  isPreparing?: boolean;
  previewLoading?: boolean;
  onOptionsChange?: (options: DataOptions) => void;
}

const dataOptionsList = [
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
    available: true,
  },
  {
    id: "schoolsCatchment" as const,
    label: "Schools & Catchment",
    description: "Nearby schools and catchment areas",
    available: false,
  },
  {
    id: "crimeStats" as const,
    label: "Crime Statistics",
    description: "Local crime data from Police UK",
    available: false,
  },
];

export function DataOptionsSelector({
  address,
  preview,
  onPreview,
  onPrepare,
  isAuthenticated,
  useLegacyDownload = false,
  credits,
  isPreparing = false,
  previewLoading = false,
  onOptionsChange,
}: DataOptionsSelectorProps) {
  const [options, setOptions] = useState<DataOptions>({
    epc: true,
    priceHistory: true,
    floodRisk: false,
    schoolsCatchment: false,
    crimeStats: false,
  });
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const hasSelectedOptions = Object.values(options).some((value) => value);

  const handleOptionChange = (optionId: keyof DataOptions, checked: boolean) => {
    const next = { ...options, [optionId]: checked };
    setOptions(next);
    setSuccess(false);
    onOptionsChange?.(next);
  };

  const handlePreview = async () => {
    if (!hasSelectedOptions) return;
    setError(null);
    setSuccess(false);
    try {
      await onPreview(options);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not preview report.");
    }
  };

  const handlePrepare = async () => {
    if (!hasSelectedOptions) return;
    if (!preview) {
      setError("Run a preview first, then download.");
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
      await onPrepare(options);
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

  const availabilityIndicatorClass = (requested: boolean, available: boolean) => {
    if (!requested) {
      return "text-muted-foreground";
    }
    return available ? "text-success" : "text-destructive";
  };

  const availabilityIconClass = (requested: boolean, available: boolean) => {
    if (!requested) {
      return "text-muted-foreground";
    }
    return available ? "text-success" : "text-destructive";
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
          <h3 className="text-sm font-medium text-foreground mb-4">Select data to include</h3>

          <div className="space-y-3">
            {dataOptionsList.map((option) => {
              const isChecked = options[option.id];
              const isDisabled = !option.available;

              return (
                <div
                  key={option.id}
                  className={`flex items-start gap-3 p-3 rounded-lg border transition-colors ${
                    isDisabled
                      ? "border-border bg-muted/30 opacity-60"
                      : isChecked
                        ? "border-primary/30 bg-primary/5"
                        : "border-border hover:border-primary/20"
                  }`}
                >
                  <Checkbox
                    id={option.id}
                    checked={isChecked}
                    onCheckedChange={(checked) =>
                      handleOptionChange(option.id, checked as boolean)
                    }
                    disabled={isDisabled}
                    className="mt-0.5"
                  />
                  <div className="flex-1 min-w-0">
                    <Label
                      htmlFor={option.id}
                      className={`flex items-center gap-2 text-sm font-medium cursor-pointer ${isDisabled ? "cursor-not-allowed" : ""}`}
                    >
                      {option.label}
                      {isDisabled && <span className="text-xs text-muted-foreground font-normal">(coming soon)</span>}
                    </Label>
                    <p className="text-xs text-muted-foreground mt-1">{option.description}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        <div className="flex flex-col gap-2 pt-4 border-t border-border">
          <Button onClick={handlePreview} disabled={previewLoading || !hasSelectedOptions} className="w-full h-11" variant="outline">
            {previewLoading ? (
              <>
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                Previewing...
              </>
            ) : (
              "Preview Report"
            )}
          </Button>

          <Button
            onClick={handlePrepare}
            disabled={isPreparing || !hasSelectedOptions || !preview}
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
              ? "Preview is free. Legacy download is available until paywall enforcement is enabled."
              : "Preview is free. Download requires 1 credit per report."}
          </p>

          {preview ? (
            <div className="space-y-3 text-xs">
              <div className="space-y-1">
                <p className="font-medium text-foreground">Data availability</p>
                <p className={availabilityIndicatorClass(preview.requested.includeEpc, preview.epcAvailable)}>
                  <CheckCircle
                    className={`h-3 w-3 inline mr-1 ${availabilityIconClass(preview.requested.includeEpc, preview.epcAvailable)}`}
                  />
                  Environmental Performance:{" "}
                  {preview.requested.includeEpc
                    ? preview.epcAvailable
                      ? "found"
                      : "not found"
                    : "not requested"}
                </p>
                <p className={availabilityIndicatorClass(preview.requested.includePriceHistory, preview.priceHistoryAvailable)}>
                  <CheckCircle
                    className={`h-3 w-3 inline mr-1 ${availabilityIconClass(preview.requested.includePriceHistory, preview.priceHistoryAvailable)}`}
                  />
                  Historical Prices:{" "}
                  {preview.requested.includePriceHistory
                    ? preview.priceHistoryAvailable
                      ? "found"
                      : "not found"
                    : "not requested"}
                </p>
                <p className={availabilityIndicatorClass(preview.requested.includeFloodRisk, preview.floodRiskAvailable)}>
                  <CheckCircle
                    className={`h-3 w-3 inline mr-1 ${availabilityIconClass(preview.requested.includeFloodRisk, preview.floodRiskAvailable)}`}
                  />
                  Flood Risk:{" "}
                  {preview.requested.includeFloodRisk
                    ? preview.floodRiskAvailable
                      ? "found"
                      : "not found"
                    : "not requested"}
                </p>
              </div>

              {preview.availableSectionCount === 0 && <p className="text-destructive">No matching data sections found.</p>}
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">Run a preview before downloading.</p>
          )}

          {isAuthenticated && <p className="text-xs text-muted-foreground">Balance: {credits} credit{credits === 1 ? "" : "s"}</p>}
        </div>

        {error && (
          <div className="error-inline fade-in flex items-start gap-2">
            <AlertCircle className="h-4 w-4 flex-shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}
      </div>
    </div>
  );
}
