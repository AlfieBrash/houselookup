import { Home } from "lucide-react";

export function AppHeader() {
  return (
    <header className="border-b border-border bg-card">
      <div className="max-w-3xl mx-auto px-6 py-4">
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-lg bg-primary flex items-center justify-center">
            <Home className="h-5 w-5 text-primary-foreground" />
          </div>
          <div>
            <h1 className="text-lg font-semibold text-foreground">
              Property Lookup
            </h1>
            <p className="text-sm text-muted-foreground">
              UK first-time buyer assistant
            </p>
          </div>
        </div>
      </div>
    </header>
  );
}
