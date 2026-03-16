import { useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { HeroSection } from "@/components/HeroSection";
import { PostcodeInput } from "@/components/PostcodeInput";
import { PostcodeDetails, PostcodeData } from "@/components/PostcodeDetails";
import { AddressSelector, Address } from "@/components/AddressSelector";
import { DataOptions, DataOptionsSelector } from "@/components/DataOptionsSelector";
import { useAuth } from "@/context/AuthContext";
import {
  ReportPreviewResponse,
  downloadPropertyReportLegacy,
  downloadReportByToken,
  prepareReportDownload,
  previewReport,
} from "@/lib/report-api";
import { lookupPostcode } from "@/lib/postcodes-api";
import { lookupAddressesByPostcode } from "@/lib/os-places";

const Index = () => {
  const { isAuthenticated, credits, refresh } = useAuth();
  const useLegacyDownload =
    (import.meta.env.VITE_REPORT_LEGACY_DOWNLOAD_ENABLED as string | undefined) === "true";

  const [isLoading, setIsLoading] = useState(false);
  const [postcodeError, setPostcodeError] = useState<string | null>(null);
  const [postcodeData, setPostcodeData] = useState<PostcodeData | null>(null);
  const [addresses, setAddresses] = useState<Address[] | null>(null);
  const [selectedAddress, setSelectedAddress] = useState<Address | null>(null);
  const [currentPostcode, setCurrentPostcode] = useState<string>("");
  const [addressError, setAddressError] = useState<string | null>(null);
  const [preview, setPreview] = useState<ReportPreviewResponse | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [prepareLoading, setPrepareLoading] = useState(false);

  const handlePostcodeSearch = async (postcode: string) => {
    setIsLoading(true);
    setPostcodeError(null);
    setPostcodeData(null);
    setAddresses(null);
    setSelectedAddress(null);
    setCurrentPostcode(postcode);
    setAddressError(null);
    setPreview(null);

    try {
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
        setAddressError(error instanceof Error ? error.message : "The addresses seem to have gone into hiding.");
        setAddresses([]);
      }
    } catch (error) {
      setPostcodeError(error instanceof Error ? error.message : "Something went wrong.");
    } finally {
      setIsLoading(false);
    }
  };

  const handleAddressSelect = (address: Address) => {
    setSelectedAddress(address);
    setPreview(null);
  };

  const buildReportRequestBase = () => {
    if (!selectedAddress) {
      throw new Error("No address selected");
    }

    return {
      uprn: selectedAddress.uprn,
      postcode: currentPostcode,
      paon: selectedAddress.line1?.split(" ")[0],
      latitude: postcodeData?.latitude,
      longitude: postcodeData?.longitude,
    };
  };

  const handlePreview = async (options: DataOptions) => {
    const base = buildReportRequestBase();
    setPreviewLoading(true);

    try {
      setPreview(null);

      const reportPreview = await previewReport({
        ...base,
        options,
      });

      setPreview(reportPreview);
      return reportPreview;
    } catch (err) {
      throw err;
    } finally {
      setPreviewLoading(false);
    }
  };

  const handlePrepare = async (options: DataOptions) => {
    const base = buildReportRequestBase();
    setPrepareLoading(true);
    try {
      if (useLegacyDownload) {
        const blob = await downloadPropertyReportLegacy({
          ...base,
          options,
        });

        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = `property-report-${selectedAddress?.uprn}.pdf`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
        return;
      }

      const prepared = await prepareReportDownload({
        ...base,
        options,
      });

      const blob = await downloadReportByToken(prepared.downloadToken);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `property-report-${selectedAddress?.uprn}.pdf`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);

      await refresh();
    } finally {
      setPrepareLoading(false);
    }
  };

  const handleReset = () => {
    setPostcodeData(null);
    setAddresses(null);
    setSelectedAddress(null);
    setPostcodeError(null);
    setAddressError(null);
    setCurrentPostcode("");
    setPreview(null);
  };

  return (
    <div className="min-h-screen bg-background">
      <AppHeader onReset={handleReset} />

      <main className="max-w-3xl mx-auto px-4 sm:px-6 py-3 sm:py-6 md:py-8">
        {!postcodeData && !isLoading && <HeroSection />}

        <div className="space-y-6">
          <PostcodeInput
            onSearch={handlePostcodeSearch}
            isLoading={isLoading}
            error={postcodeError}
          />

          {postcodeData && <PostcodeDetails data={postcodeData} />}

          {addresses && !selectedAddress && (
            <AddressSelector
              postcode={currentPostcode}
              addresses={addresses}
              onSelect={handleAddressSelect}
              error={addressError}
            />
          )}

          {selectedAddress && (
            <DataOptionsSelector
              address={selectedAddress}
              preview={preview}
              onPreview={handlePreview}
              onPrepare={async (options) => {
                if (!isAuthenticated && options) {
                  throw new Error("Sign in to download reports.");
                }
                await handlePrepare(options);
              }}
              useLegacyDownload={useLegacyDownload}
              isAuthenticated={isAuthenticated}
              credits={credits}
              isPreparing={prepareLoading}
              previewLoading={previewLoading}
              onOptionsChange={() => {
                setPreview(null);
              }}
            />
          )}
        </div>

        {selectedAddress && postcodeData && (
          <div className="mt-8 pt-6 border-t border-border fade-in">
            <p className="text-sm text-muted-foreground text-center">
              Showing results for <span className="font-medium text-foreground">{postcodeData.postcode}</span>
              <span className="mx-2">·</span>
              <button
                onClick={() => {
                  setSelectedAddress(null);
                }}
                className="text-primary hover:underline"
              >
                Change address
              </button>
              <span className="mx-2">·</span>
              <button
                onClick={handleReset}
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
