import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { Address } from "@/components/AddressSelector";
import { CheckCircle, Download, Loader2, AlertCircle, FileText, PoundSterling, Leaf, MapPin, Users } from "lucide-react";

export interface DataOptions {
  epc: boolean;
  priceHistory: boolean;
  floodRisk: boolean;
  schoolsCatchment: boolean;
  crimeStats: boolean;
}

interface DataOptionsSelectorProps {
  address: Address;
  onGenerateReport: (options: DataOptions) => Promise<void>;
}

const dataOptionsList = [
  {
    id: "epc" as const,
    label: "Environmental Performance",
    description: "EPC rating, energy costs, building fabric details",
    icon: Leaf,
    available: true,
  },
  {
    id: "priceHistory" as const,
    label: "Historical Prices",
    description: "Past sale prices from HM Land Registry",
    icon: PoundSterling,
    available: true,
  },
  {
    id: "floodRisk" as const,
    label: "Flood Risk Assessment",
    description: "Environment Agency active flood alerts and warnings",
    icon: MapPin,
    available: true,
  },
  {
    id: "schoolsCatchment" as const,
    label: "Schools & Catchment",
    description: "Nearby schools and catchment areas",
    icon: Users,
    available: false,
  },
  {
    id: "crimeStats" as const,
    label: "Crime Statistics",
    description: "Local crime data from Police UK",
    icon: FileText,
    available: false,
  },
];

export function DataOptionsSelector({ address, onGenerateReport }: DataOptionsSelectorProps) {
  const [options, setOptions] = useState<DataOptions>({
    epc: true,
    priceHistory: true,
    floodRisk: false,
    schoolsCatchment: false,
    crimeStats: false,
  });
  const [isGenerating, setIsGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const formatFullAddress = () => {
    const parts = [address.line1];
    if (address.line2) parts.push(address.line2);
    parts.push(address.town);
    parts.push(address.postcode);
    return parts.join(", ");
  };

  const handleOptionChange = (optionId: keyof DataOptions, checked: boolean) => {
    setOptions(prev => ({ ...prev, [optionId]: checked }));
  };

  const hasSelectedOptions = Object.values(options).some(v => v);

  const handleGenerate = async () => {
    if (!hasSelectedOptions) return;
    
    setIsGenerating(true);
    setError(null);
    setSuccess(false);

    try {
      await onGenerateReport(options);
      setSuccess(true);
      setTimeout(() => setSuccess(false), 3000);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to generate report");
    } finally {
      setIsGenerating(false);
    }
  };

  return (
    <div className="panel slide-in">
      <div className="panel-header flex items-center gap-2">
        <CheckCircle className="h-5 w-5 text-success" />
        <span>Selected address</span>
      </div>

      <div className="space-y-6">
        {/* Address display */}
        <div>
          <p className="text-foreground font-semibold text-base leading-relaxed">
            {formatFullAddress()}
          </p>
          <p className="text-sm text-muted-foreground mt-2">
            UPRN: <span className="font-mono">{address.uprn}</span>
          </p>
        </div>

        {/* Data options */}
        <div className="pt-4 border-t border-border">
          <h3 className="text-sm font-medium text-foreground mb-4">
            Select data to include in your report
          </h3>
          
          <div className="space-y-3">
            {dataOptionsList.map((option) => {
              const Icon = option.icon;
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
                      className={`flex items-center gap-2 text-sm font-medium cursor-pointer ${
                        isDisabled ? "cursor-not-allowed" : ""
                      }`}
                    >
                      <Icon className="h-4 w-4 text-primary" />
                      {option.label}
                      {isDisabled && (
                        <span className="text-xs text-muted-foreground font-normal">
                          (coming soon)
                        </span>
                      )}
                    </Label>
                    <p className="text-xs text-muted-foreground mt-1">
                      {option.description}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Generate button */}
        <div className="pt-4 border-t border-border">
          <Button
            onClick={handleGenerate}
            disabled={isGenerating || !hasSelectedOptions}
            className="w-full h-11"
          >
            {isGenerating ? (
              <>
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                Generating report...
              </>
            ) : success ? (
              <>
                <CheckCircle className="h-4 w-4 mr-2" />
                Downloaded!
              </>
            ) : (
              <>
                <Download className="h-4 w-4 mr-2" />
                Get this data
              </>
            )}
          </Button>
          <p className="helper-text text-center">
            Downloads a PDF report with your selected data
          </p>
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
