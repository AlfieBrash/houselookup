import { useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { PostcodeInput } from "@/components/PostcodeInput";
import { PostcodeDetails, PostcodeData } from "@/components/PostcodeDetails";
import { AddressSelector, Address } from "@/components/AddressSelector";
import { SelectedAddress } from "@/components/SelectedAddress";

// Mock data for demonstration
const mockPostcodeData: PostcodeData = {
  postcode: "EX1 1GN",
  adminDistrict: "Exeter",
  region: "South West",
  country: "England",
  constituency: "Exeter",
  latitude: 50.725,
  longitude: -3.527,
};

const mockAddresses: Address[] = [
  {
    uprn: "100040214823",
    line1: "1 Cathedral Close",
    town: "Exeter",
    postcode: "EX1 1GN",
  },
  {
    uprn: "100040214824",
    line1: "2 Cathedral Close",
    line2: "Flat A",
    town: "Exeter",
    postcode: "EX1 1GN",
  },
  {
    uprn: "100040214825",
    line1: "3 Cathedral Close",
    town: "Exeter",
    postcode: "EX1 1GN",
  },
  {
    uprn: "100040214826",
    line1: "4 Cathedral Close",
    line2: "Ground Floor",
    town: "Exeter",
    postcode: "EX1 1GN",
  },
];

const Index = () => {
  const [isLoading, setIsLoading] = useState(false);
  const [postcodeError, setPostcodeError] = useState<string | null>(null);
  const [postcodeData, setPostcodeData] = useState<PostcodeData | null>(null);
  const [addresses, setAddresses] = useState<Address[] | null>(null);
  const [selectedAddress, setSelectedAddress] = useState<Address | null>(null);
  const [currentPostcode, setCurrentPostcode] = useState<string>("");

  const handlePostcodeSearch = async (postcode: string) => {
    setIsLoading(true);
    setPostcodeError(null);
    setPostcodeData(null);
    setAddresses(null);
    setSelectedAddress(null);
    setCurrentPostcode(postcode);

    // Simulate API call
    await new Promise((resolve) => setTimeout(resolve, 800));

    // Mock validation - reject some postcodes for demo
    if (postcode.toUpperCase().startsWith("ZZ")) {
      setPostcodeError("Invalid postcode. Please check and try again.");
      setIsLoading(false);
      return;
    }

    // Normalise the postcode for display
    const normalised = postcode.replace(/\s+/g, "").toUpperCase();
    const formattedPostcode = `${normalised.slice(0, -3)} ${normalised.slice(-3)}`;

    setPostcodeData({
      ...mockPostcodeData,
      postcode: formattedPostcode,
    });

    // Simulate fetching addresses
    await new Promise((resolve) => setTimeout(resolve, 400));

    // For demo, show empty state for certain postcodes
    if (postcode.toUpperCase().startsWith("XX")) {
      setAddresses([]);
    } else {
      setAddresses(
        mockAddresses.map((addr) => ({
          ...addr,
          postcode: formattedPostcode,
        }))
      );
    }

    setIsLoading(false);
  };

  const handleAddressSelect = (address: Address) => {
    setSelectedAddress(address);
  };

  const handleDownloadEPC = async () => {
    // Simulate EPC download
    await new Promise((resolve) => setTimeout(resolve, 1200));

    // Simulate occasional failure for demo
    if (Math.random() < 0.2) {
      throw new Error("EPC not available for this property");
    }

    // Create and download mock EPC JSON
    const epcData = {
      address: selectedAddress,
      epcRating: "C",
      currentEnergyEfficiency: 72,
      potentialEnergyEfficiency: 85,
      propertyType: "Semi-detached house",
      builtForm: "Semi-Detached",
      totalFloorArea: 95,
      mainFuel: "Natural Gas",
      lodgementDate: "2023-06-15",
    };

    const blob = new Blob([JSON.stringify(epcData, null, 2)], {
      type: "application/json",
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `epc-${selectedAddress?.uprn}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="min-h-screen bg-background">
      <AppHeader />

      <main className="max-w-3xl mx-auto px-6 py-8">
        <div className="space-y-6">
          {/* Step 1: Postcode Input */}
          <PostcodeInput
            onSearch={handlePostcodeSearch}
            isLoading={isLoading}
            error={postcodeError}
          />

          {/* Step 2: Postcode Details */}
          {postcodeData && <PostcodeDetails data={postcodeData} />}

          {/* Step 3: Address Selection */}
          {addresses && !selectedAddress && (
            <AddressSelector
              postcode={currentPostcode}
              addresses={addresses}
              onSelect={handleAddressSelect}
            />
          )}

          {/* Step 4 & 5: Selected Address + EPC Download */}
          {selectedAddress && (
            <SelectedAddress
              address={selectedAddress}
              onDownloadEPC={handleDownloadEPC}
            />
          )}
        </div>

        {/* Context reminder when address is selected */}
        {selectedAddress && postcodeData && (
          <div className="mt-8 pt-6 border-t border-border fade-in">
            <p className="text-sm text-muted-foreground text-center">
              Showing results for{" "}
              <span className="font-medium text-foreground">
                {postcodeData.postcode}
              </span>
              {" · "}
              <button
                onClick={() => {
                  setSelectedAddress(null);
                }}
                className="text-primary hover:underline"
              >
                Change address
              </button>
              {" · "}
              <button
                onClick={() => {
                  setPostcodeData(null);
                  setAddresses(null);
                  setSelectedAddress(null);
                }}
                className="text-primary hover:underline"
              >
                New search
              </button>
            </p>
          </div>
        )}
      </main>
    </div>
  );
};

export default Index;
