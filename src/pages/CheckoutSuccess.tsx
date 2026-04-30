import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/context/AuthContext";

const CheckoutSuccess = () => {
  const { refresh, isAuthenticated } = useAuth();
  const [checkingBalance, setCheckingBalance] = useState(false);

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }

    let cancelled = false;
    let timeoutId: ReturnType<typeof window.setTimeout> | undefined;
    let attempts = 0;

    const pollBalance = async () => {
      attempts += 1;
      setCheckingBalance(true);

      try {
        await refresh();
      } finally {
        if (cancelled) {
          return;
        }

        if (attempts < 6) {
          timeoutId = window.setTimeout(pollBalance, 2000);
        } else {
          setCheckingBalance(false);
        }
      }
    };

    pollBalance();

    return () => {
      cancelled = true;
      if (timeoutId) {
        window.clearTimeout(timeoutId);
      }
    };
  }, [isAuthenticated, refresh]);

  return (
    <main className="min-h-screen bg-background text-foreground">
      <section className="max-w-md mx-auto px-4 py-12 text-center space-y-4">
        <h1 className="text-2xl font-semibold">Payment Successful</h1>
        <p className="text-muted-foreground">
          {checkingBalance
            ? "Confirming your updated credit balance..."
            : "Your credit balance should update in a few seconds."}
        </p>
        <Link to="/credits">
          <Button variant="outline">View balance</Button>
        </Link>
        <Link to="/" className="inline-block">
          <Button>Download reports</Button>
        </Link>
      </section>
    </main>
  );
};

export default CheckoutSuccess;
