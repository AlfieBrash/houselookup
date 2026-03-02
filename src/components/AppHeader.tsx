import { Home } from "lucide-react";
import { ThemeToggle } from "@/components/ThemeToggle";

interface AppHeaderProps {
  onReset?: () => void;
}

export function AppHeader({ onReset }: AppHeaderProps) {
  return (
    <header className="border-b border-border bg-card">
      <div className="max-w-3xl mx-auto px-4 sm:px-6 py-2 sm:py-3 md:py-4">
        <div className="flex items-center justify-between">
          <button
            onClick={onReset}
            className="flex items-center gap-3 hover:opacity-80 transition-opacity"
          >
            <div className="h-10 w-10 rounded-lg bg-primary flex items-center justify-center">
              <Home className="h-5 w-5 text-primary-foreground" />
            </div>
            <div className="text-left">
              <h1 className="text-lg font-semibold text-foreground">
                Property Pal
              </h1>
              <p className="text-sm text-muted-foreground">
                UK first-time buyer assistant
              </p>
            </div>
          </button>
          <ThemeToggle />
        </div>
      </div>
    </header>
  );
}
