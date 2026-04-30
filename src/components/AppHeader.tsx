import { Link } from "react-router-dom";
import { Coins, Home, LogIn, Menu, Moon, Sun, Wrench } from "lucide-react";
import { useTheme } from "next-themes";
import { ThemeToggle } from "@/components/ThemeToggle";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { useAuth } from "@/context/AuthContext";

interface AppHeaderProps {
  onReset?: () => void;
  stubEnabled?: boolean;
  stubPostcode?: string;
  onToggleStub?: () => void;
}

export function AppHeader({ onReset, stubEnabled = false, onToggleStub }: AppHeaderProps) {
  const { isAuthenticated, userEmail, credits } = useAuth();
  const { resolvedTheme, setTheme } = useTheme();
  const nextTheme = resolvedTheme === "dark" ? "light" : "dark";
  const developerTooltipText = stubEnabled ? "Leave developer mode" : "Switch to developer mode";

  return (
    <header className="border-b border-border bg-card">
      <div className="hidden md:block max-w-3xl mx-auto px-6 py-4">
        <div className="flex items-center justify-between gap-3">
          <button
            onClick={onReset}
            className="flex min-w-0 items-center gap-3 group transition-all duration-200 hover:opacity-90"
          >
            <div className="h-10 w-10 rounded-lg bg-primary flex items-center justify-center transition-transform duration-200 group-hover:scale-110 group-hover:rotate-[-3deg]">
              <Home className="h-5 w-5 text-primary-foreground transition-transform duration-200 group-hover:scale-110" />
            </div>
            <div className="text-left">
              <h1 className="text-lg font-semibold tracking-normal text-foreground">Property Pal</h1>
              <p className="text-sm text-muted-foreground">UK first-time buyer assistant</p>
            </div>
          </button>
          <div className="flex min-w-0 items-center gap-2">
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={onToggleStub}
                  disabled={!onToggleStub}
                  aria-label={stubEnabled ? "Disable OS Places stub mode" : "Enable OS Places stub mode"}
                  aria-pressed={stubEnabled}
                  className={stubEnabled ? "bg-accent text-primary hover:bg-accent/80" : ""}
                >
                  <Wrench className="h-5 w-5" />
                </Button>
              </TooltipTrigger>
              <TooltipContent side="bottom">
                <p>{developerTooltipText}</p>
              </TooltipContent>
            </Tooltip>
            <ThemeToggle />
            <Link
              to="/credits"
              className="inline-flex h-9 shrink-0 items-center gap-2 rounded-md border border-border px-2.5 text-sm"
            >
              <Coins className="h-4 w-4" />
              Credits: {credits}
            </Link>
            {isAuthenticated ? (
              <span className="max-w-[180px] truncate text-sm text-muted-foreground">{userEmail}</span>
            ) : (
              <Link
                to="/auth"
                className="inline-flex h-9 shrink-0 items-center gap-2 rounded-md border border-border px-2.5 text-sm"
              >
                <LogIn className="h-4 w-4" />
                Sign in
              </Link>
            )}
          </div>
        </div>
      </div>

      <div className="md:hidden">
        <div className="flex h-16 items-center justify-between gap-2 px-4">
          <button
            onClick={onReset}
            className="flex min-w-0 items-center gap-2 text-left transition-opacity duration-200 hover:opacity-90"
          >
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary">
              <Home className="h-5 w-5 text-primary-foreground" />
            </div>
            <span className="min-w-0 truncate text-base font-semibold tracking-normal text-foreground">
              Property Pal
            </span>
          </button>

          <div className="flex shrink-0 items-center gap-2">
            <Link
              to="/credits"
              aria-label={`Credits: ${credits}`}
              className="inline-flex h-10 items-center gap-1.5 rounded-md border border-border px-2.5 text-sm font-medium"
            >
              <Coins className="h-4 w-4" />
              <span>{credits}</span>
            </Link>

            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" aria-label="Open menu">
                  <Menu className="h-5 w-5" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-64">
                <DropdownMenuLabel>Account</DropdownMenuLabel>
                {isAuthenticated ? (
                  <div className="px-2 py-1.5 text-sm text-muted-foreground break-all">
                    {userEmail}
                  </div>
                ) : (
                  <DropdownMenuItem asChild>
                    <Link to="/auth" className="flex items-center gap-2">
                      <LogIn className="h-4 w-4" />
                      Sign in
                    </Link>
                  </DropdownMenuItem>
                )}

                <DropdownMenuSeparator />

                <DropdownMenuItem onSelect={() => setTheme(nextTheme)} className="gap-2">
                  {resolvedTheme === "dark" ? (
                    <Sun className="h-4 w-4" />
                  ) : (
                    <Moon className="h-4 w-4" />
                  )}
                  Switch to {nextTheme === "dark" ? "night" : "day"} mode
                </DropdownMenuItem>

                <DropdownMenuItem
                  disabled={!onToggleStub}
                  onSelect={() => onToggleStub?.()}
                  className="gap-2"
                >
                  <Wrench className="h-4 w-4" />
                  {stubEnabled ? "Leave developer mode" : "Developer mode"}
                </DropdownMenuItem>

                <DropdownMenuSeparator />

                <DropdownMenuItem asChild>
                  <Link to="/credits" className="flex items-center gap-2">
                    <Coins className="h-4 w-4" />
                    Credits: {credits}
                  </Link>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </div>
    </header>
  );
}
