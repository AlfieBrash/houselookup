import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";

const CheckoutCancel = () => {
  return (
    <main className="min-h-screen bg-background text-foreground">
      <section className="max-w-md mx-auto px-4 py-12 text-center space-y-4">
        <h1 className="text-2xl font-semibold">Payment Cancelled</h1>
        <p className="text-muted-foreground">No charges were made. You can retry checkout any time.</p>
        <Link to="/credits">
          <Button>Back to credits</Button>
        </Link>
      </section>
    </main>
  );
};

export default CheckoutCancel;
