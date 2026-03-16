import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useAuth } from "@/context/AuthContext";

const Auth = () => {
  const navigate = useNavigate();
  const { isAuthenticated, register, login, userEmail, loading } = useAuth();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (loading) {
    return <div className="min-h-screen flex items-center justify-center">Checking your session...</div>;
  }

  if (isAuthenticated) {
    return (
      <main className="min-h-screen bg-background text-foreground">
        <div className="max-w-2xl mx-auto px-4 py-12 space-y-4">
          <h1 className="text-2xl font-semibold">Welcome back, {userEmail}</h1>
          <p className="text-muted-foreground">You are already signed in.</p>
          <Button onClick={() => navigate("/")}>Back to reports</Button>
        </div>
      </main>
    );
  }

  const submit = async () => {
    try {
      setBusy(true);
      setError(null);
      if (mode === "login") {
        await login(email, password);
      } else {
        await register(email, password);
      }
      navigate("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="min-h-screen bg-background text-foreground">
      <section className="max-w-md mx-auto px-4 py-12">
        <div className="panel">
          <h1 className="text-2xl font-semibold">{mode === "login" ? "Sign in" : "Create account"}</h1>
          <p className="helper-text mt-2">Reports are previewed for free. Downloads are protected by credits.</p>

          <div className="space-y-3 mt-5">
            <Input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="Email"
            />
            <Input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="Password (min 8 chars, upper/lower/number)"
            />
          </div>

          <Button
            className="w-full mt-4"
            disabled={busy || !email || password.length < 8}
            onClick={submit}
          >
            {busy ? "Working..." : mode === "login" ? "Sign in" : "Create account"}
          </Button>

          {error && <p className="text-sm text-destructive mt-3">{error}</p>}

          <p className="mt-4 text-sm text-muted-foreground">
            {mode === "login" ? "No account yet?" : "Already have an account?"}
            <Button
              variant="link"
              className="px-2 h-auto"
              onClick={() => setMode(mode === "login" ? "register" : "login")}
            >
              {mode === "login" ? "Create one" : "Sign in"}
            </Button>
          </p>
        </div>
      </section>
    </main>
  );
};

export default Auth;
