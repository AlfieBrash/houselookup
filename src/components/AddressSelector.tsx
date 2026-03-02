import { useState } from "react";
import { Button } from "@/components/ui/button";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Label } from "@/components/ui/label";
import { Home, CheckCircle2 } from "lucide-react";

export interface Address {
  uprn: string;
  line1: string;
  line2?: string;
  town: string;
  postcode: string;
}

interface AddressSelectorProps {
  postcode: string;
  addresses: Address[];
  onSelect: (address: Address) => void;
  error?: string | null;
}

export function AddressSelector({ postcode, addresses, onSelect, error }: AddressSelectorProps) {
  const [selectedUprn, setSelectedUprn] = useState<string>("");

  const handleConfirm = () => {
    const selected = addresses.find((a) => a.uprn === selectedUprn);
    if (selected) {
      onSelect(selected);
    }
  };

  const formatAddress = (address: Address) => {
    const parts = [address.line1];
    if (address.line2) parts.push(address.line2);
    parts.push(address.town);
    return parts.join(", ");
  };

  if (addresses.length === 0) {
    return (
      <div className="panel slide-in">
        <div className="panel-header flex items-center gap-2">
          <Home className="h-5 w-5 text-primary" />
          <span>Addresses at {postcode}</span>
        </div>
        <div className="text-center py-8">
          <p className="text-muted-foreground">
            {error ? "The addresses refused to come out. Typical." : "No addresses found here — it's quieter than a village on a Sunday."}
          </p>
          <p className="text-sm text-muted-foreground/70 mt-2">
            You could try a different postcode, or pop the kettle on and try again.
          </p>
        </div>
        {error && <div className="error-inline fade-in">{error}</div>}
      </div>
    );
  }

  return (
    <div className="panel slide-in">
      <div className="panel-header flex items-center gap-2">
        <Home className="h-5 w-5 text-primary" />
        <span>Addresses at {postcode}</span>
      </div>

      <RadioGroup value={selectedUprn} onValueChange={setSelectedUprn} className="space-y-2">
        {addresses.map((address) => (
          <div
            key={address.uprn}
            className={`flex items-start space-x-3 p-3 rounded-lg border transition-colors cursor-pointer ${
              selectedUprn === address.uprn
                ? "border-primary bg-accent"
                : "border-border hover:border-primary/50 hover:bg-accent/50"
            }`}
            onClick={() => setSelectedUprn(address.uprn)}
          >
            <RadioGroupItem value={address.uprn} id={address.uprn} className="mt-0.5" />
            <Label htmlFor={address.uprn} className="flex-1 cursor-pointer font-normal">
              <span className="text-foreground">{formatAddress(address)}</span>
              <span className="text-muted-foreground ml-1">{address.postcode}</span>
            </Label>
          </div>
        ))}
      </RadioGroup>

      {error && <div className="error-inline fade-in mt-4">{error}</div>}

      <div className="mt-6 pt-4 border-t border-border">
        <Button
          onClick={handleConfirm}
          disabled={!selectedUprn}
          className="w-full h-11"
        >
          <CheckCircle2 className="h-4 w-4 mr-2" />
          Select address
        </Button>
      </div>
    </div>
  );
}
