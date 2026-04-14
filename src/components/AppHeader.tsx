import { Link } from "react-router-dom";
import { CircleDollarSign, Home, LogIn, Wrench } from "lucide-react";
import { ThemeToggle } from "@/components/ThemeToggle";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { useAuth } from "@/context/AuthContext";

interface AppHeaderProps {
  onReset?: () => void;
  stubEnabled?: boolean;
  stubPostcode?: string;
  onToggleStub?: () => void;
}

export function AppHeader({ onReset, stubEnabled = false, stubPostcode, onToggleStub }: AppHeaderProps) {
  const { isAuthenticated, userEmail, credits } = useAuth();

  return (
    <header className="border-b border-border bg-card">
      <div className="max-w-3xl mx-auto px-4 sm:px-6 py-2 sm:py-3 md:py-4">
        <div className="flex items-center justify-between gap-3">
          <button
            onClick={onReset}
            className="flex items-center gap-3 group transition-all duration-200 hover:opacity-90"
          >
            <div className="h-10 w-10 rounded-lg bg-primary flex items-center justify-center transition-transform duration-200 group-hover:scale-110 group-hover:rotate-[-3deg]">
              <Home className="h-5 w-5 text-primary-foreground transition-transform duration-200 group-hover:scale-110" />
            </div>
            <div className="text-left">
              <h1 className="text-lg font-semibold text-foreground">Property Pal</h1>
              <p className="text-sm text-muted-foreground">UK first-time buyer assistant</p>
            </div>
          </button>
          <div className="flex items-center gap-2">
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={onToggleStub}
                  aria-label={stubEnabled ? "Disable OS Places stub mode" : "Enable OS Places stub mode"}
                  aria-pressed={stubEnabled}
                  className={stubEnabled ? "bg-accent text-primary hover:bg-accent/80" : ""}
                >
                  <Wrench className="h-5 w-5" />
                </Button>
              </TooltipTrigger>
              {stubEnabled && stubPostcode ? (
                <TooltipContent side="bottom">
                  <p>Developer mode</p>
                </TooltipContent>
              ) : null}
            </Tooltip>
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
