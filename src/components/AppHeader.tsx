import { Link } from "react-router-dom";
import { CircleDollarSign, Home, LogIn } from "lucide-react";
import { ThemeToggle } from "@/components/ThemeToggle";
import { useAuth } from "@/context/AuthContext";

interface AppHeaderProps {
  onReset?: () => void;
}

export function AppHeader({ onReset }: AppHeaderProps) {
  const { isAuthenticated, userEmail, credits } = useAuth();

  return (
    <header className="border-b border-border bg-card">
      <div className="max-w-3xl mx-auto px-4 sm:px-6 py-2 sm:py-3 md:py-4">
        <div className="flex items-center justify-between gap-3">
          <button
            onClick={onReset}
            className="flex items-center gap-3 hover:opacity-80 transition-opacity"
          >
            <div className="h-10 w-10 rounded-lg bg-primary flex items-center justify-center">
              <Home className="h-5 w-5 text-primary-foreground" />
            </div>
            <div className="text-left">
              <h1 className="text-lg font-semibold text-foreground">Property Pal</h1>
              <p className="text-sm text-muted-foreground">UK first-time buyer assistant</p>
            </div>
          </button>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <Link
              to="/credits"
              className="inline-flex items-center gap-2 text-sm border border-border rounded-md px-2 py-1"
            >
              <CircleDollarSign className="h-4 w-4" />
              Credits: {credits}
            </Link>
            {isAuthenticated ? (
              <span className="text-sm text-muted-foreground">{userEmail}</span>
            ) : (
              <Link
                to="/auth"
                className="inline-flex items-center gap-2 text-sm border border-border rounded-md px-2 py-1"
              >
                <LogIn className="h-4 w-4" />
                Sign in
              </Link>
            )}
          </div>
        </div>
      </div>
    </header>
  );
}
