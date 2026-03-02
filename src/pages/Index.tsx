import { useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { PostcodeInput } from "@/components/PostcodeInput";
import { PostcodeDetails, PostcodeData } from "@/components/PostcodeDetails";
import { AddressSelector, Address } from "@/components/AddressSelector";
import { DataOptionsSelector, DataOptions } from "@/components/DataOptionsSelector";
import { lookupPostcode } from "@/lib/postcodes-api";
import { lookupAddressesByPostcode } from "@/lib/os-places";
import { downloadPropertyReport } from "@/lib/report-api";
import { HeroSection } from "@/components/HeroSection";

const Index = () => {
  const [isLoading, setIsLoading] = useState(false);
  const [postcodeError, setPostcodeError] = useState<string | null>(null);
  const [postcodeData, setPostcodeData] = useState<PostcodeData | null>(null);
  const [addresses, setAddresses] = useState<Address[] | null>(null);
  const [selectedAddress, setSelectedAddress] = useState<Address | null>(null);
  const [currentPostcode, setCurrentPostcode] = useState<string>("");
  const [addressError, setAddressError] = useState<string | null>(null);

  const handlePostcodeSearch = async (postcode: string) => {
    setIsLoading(true);
    setPostcodeError(null);
    setPostcodeData(null);
    setAddresses(null);
    setSelectedAddress(null);
    setCurrentPostcode(postcode);
    setAddressError(null);

    try {
      // Call real postcodes.io API
      const result = await lookupPostcode(postcode);

      setPostcodeData({
        postcode: result.postcode,
        adminDistrict: result.admin_district || "Unknown",
        region: result.region || "Unknown",
        country: result.country,
        constituency: result.parliamentary_constituency || "Unknown",
        latitude: result.latitude,
        longitude: result.longitude,
      });

      try {
        const addressResults = await lookupAddressesByPostcode(postcode);
        setAddresses(addressResults);
      } catch (error) {
        setAddressError(
          error instanceof Error ? error.message : "Failed to lookup addresses"
        );
        setAddresses([]);
      }
    } catch (error) {
      setPostcodeError(error instanceof Error ? error.message : "Failed to lookup postcode");
    } finally {
      setIsLoading(false);
    }
  };

  const handleAddressSelect = (address: Address) => {
    setSelectedAddress(address);
  };

  const handleGenerateReport = async (options: DataOptions) => {
    if (!selectedAddress) {
      throw new Error("No address selected");
    }

    const pdfBlob = await downloadPropertyReport({
      uprn: selectedAddress.uprn,
      postcode: currentPostcode,
      paon: selectedAddress.line1?.split(" ")[0], // Extract house number
      latitude: postcodeData?.latitude,
      longitude: postcodeData?.longitude,
      options,
    });

    const url = URL.createObjectURL(pdfBlob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `property-report-${selectedAddress.uprn}.pdf`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="min-h-screen bg-background">
      <AppHeader />

      <main className="max-w-3xl mx-auto px-6 py-4 sm:py-8">
        {/* Hero – hidden once a search has been performed */}
        {!postcodeData && !isLoading && <HeroSection />}

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
              error={addressError}
            />
          )}

          {/* Step 4: Data Options + Generate Report */}
          {selectedAddress && (
            <DataOptionsSelector
              address={selectedAddress}
              onGenerateReport={handleGenerateReport}
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
