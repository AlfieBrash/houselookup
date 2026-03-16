import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { createCheckout, getPricing } from "@/lib/payment-api";
import { useAuth } from "@/context/AuthContext";

const Credits = () => {
  const { isAuthenticated, refresh, credits, loading, userEmail } = useAuth();
  const [packs, setPacks] = useState<Array<{ credits: number; amountCents: number; currency: string }>>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    getPricing()
      .then(setPacks)
      .catch((err) => setError(err instanceof Error ? err.message : "Could not load credit packs."));
  }, []);

  if (loading) {
    return <div className="min-h-screen flex items-center justify-center">Loading credits...</div>;
  }

  if (!isAuthenticated) {
    return (
      <main className="min-h-screen bg-background text-foreground">
        <section className="max-w-lg mx-auto px-4 py-12 space-y-4">
          <h1 className="text-2xl font-semibold">Buy report credits</h1>
          <p className="text-muted-foreground">Sign in to buy report credits for paid downloads.</p>
          <Link to="/auth">
            <Button>Sign in</Button>
          </Link>
        </section>
      </main>
    );
  }

  const buy = async (creditsToBuy: number) => {
    setError(null);
    setBusy(true);
    try {
      const checkout = await createCheckout(creditsToBuy);
      window.location.href = checkout.checkoutUrl;
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not start checkout.");
      setBusy(false);
    }
  };

  return (
    <main className="min-h-screen bg-background text-foreground">
      <section className="max-w-lg mx-auto px-4 py-12 space-y-4">
        <h1 className="text-2xl font-semibold">Credits</h1>
        <p className="text-muted-foreground">You are signed in as {userEmail}.</p>
        <p className="text-sm">Current balance: {credits}</p>

        {error && <p className="text-sm text-destructive">{error}</p>}

        <div className="panel space-y-3">
          <h2 className="font-medium">Choose a pack</h2>
          {packs.map((pack) => (
            <div key={pack.credits} className="flex items-center justify-between border-b border-border pb-2">
              <div>
                <p className="text-sm font-medium">{pack.credits} credits</p>
                <p className="text-xs text-muted-foreground">{(pack.amountCents / 100).toFixed(2)} {pack.currency}</p>
              </div>
              <Button onClick={() => buy(pack.credits)} disabled={busy}>
                Buy
              </Button>
            </div>
          ))}
          {!packs.length && <p className="helper-text">No credit packs available.</p>}
        </div>

        <div className="flex justify-between gap-3">
          <Link to="/" className="flex-1">
            <Button variant="outline" className="w-full">Back</Button>
          </Link>
        </div>
      </section>
    </main>
  );
};

export default Credits;
