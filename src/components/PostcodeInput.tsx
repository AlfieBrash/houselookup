import { useState, useEffect, useCallback } from "react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Search, Loader2 } from "lucide-react";

interface PostcodeInputProps {
  onSearch: (postcode: string) => void;
  isLoading: boolean;
  error?: string | null;
}

// UK postcode regex - matches full postcodes
const UK_POSTCODE_REGEX = /^[A-Z]{1,2}[0-9][0-9A-Z]?\s*[0-9][A-Z]{2}$/i;

const WAITING_MESSAGES = [
  "Still searching… the Royal Mail isn't known for speed 🐌",
  "Hang tight — our hamster is running as fast as he can 🐹",
  "Good things come to those who wait… unlike UK house prices 📈",
  "Still going… must be a really popular postcode 🏘️",
  "We're not stuck, we're just being thorough. Very thorough 🔍",
  "Fun fact: you've now waited longer than most estate agents return calls 📞",
  "Plot twist: the data was hiding behind the sofa 🛋️",
  "Almost there… probably… we think… 🤞",
];

export function PostcodeInput({ onSearch, isLoading, error }: PostcodeInputProps) {
  const [postcode, setPostcode] = useState("");
  const [hasSearched, setHasSearched] = useState(false);
  const [waitingMessageIndex, setWaitingMessageIndex] = useState(-1);

  const isValidPostcode = UK_POSTCODE_REGEX.test(postcode.trim());

  // Cycle through funny waiting messages every 10s while loading
  useEffect(() => {
    if (!isLoading) {
      setWaitingMessageIndex(-1);
      return;
    }
    const initial = setTimeout(() => setWaitingMessageIndex(0), 10000);
    const interval = setInterval(() => {
      setWaitingMessageIndex((prev) => (prev + 1) % WAITING_MESSAGES.length);
    }, 10000);
    return () => {
      clearTimeout(initial);
      clearInterval(interval);
    };
  }, [isLoading]);

  const handleSearch = useCallback(() => {
    if (isValidPostcode && !isLoading) {
      setHasSearched(true);
      onSearch(postcode.trim());
    }
  }, [isValidPostcode, isLoading, postcode, onSearch]);

  // Auto-search when postcode becomes valid
  useEffect(() => {
    if (isValidPostcode && !hasSearched) {
      const timer = setTimeout(() => {
        handleSearch();
      }, 500);
      return () => clearTimeout(timer);
    }
  }, [postcode, isValidPostcode, hasSearched, handleSearch]);

  // Reset hasSearched when postcode changes significantly
  useEffect(() => {
    setHasSearched(false);
  }, [postcode]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && isValidPostcode) {
      handleSearch();
    }
  };

  return (
    <div className="panel slide-in">
      <div className="space-y-4">
        <div>
          <label htmlFor="postcode" className="block text-sm font-medium text-foreground mb-2">
            Postcode
          </label>
          <div className="flex gap-3">
            <div className="relative flex-1">
              <Input
                id="postcode"
                type="text"
                value={postcode}
                onChange={(e) => setPostcode(e.target.value.toUpperCase())}
                onKeyDown={handleKeyDown}
                placeholder="e.g. EX2 6DZ"
                className="h-12 text-base pr-10"
                disabled={isLoading}
              />
              {isLoading && (
                <div className="absolute right-3 top-1/2 -translate-y-1/2">
                  <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                </div>
              )}
            </div>
            <Button
              onClick={handleSearch}
              disabled={!isValidPostcode || isLoading}
              className="h-12 px-5"
            >
              <Search className="h-4 w-4 mr-2" />
              Search
            </Button>
          </div>
          <p className="helper-text">Enter a UK postcode to find addresses</p>
        </div>

        {waitingMessageIndex >= 0 && isLoading && (
          <p className="text-sm text-muted-foreground text-center fade-in mt-2">
            {WAITING_MESSAGES[waitingMessageIndex]}
          </p>
        )}

        {error && (
          <div className="error-inline fade-in">
            {error}
          </div>
        )}
      </div>
    </div>
  );
}
