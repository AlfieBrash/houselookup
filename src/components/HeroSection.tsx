import { Search, FileText, Shield } from "lucide-react";

export function HeroSection() {
  return (
    <section className="py-4 sm:py-8 md:py-12 text-center fade-in">
      <h2 className="text-xl font-bold tracking-tight text-foreground sm:text-3xl md:text-4xl">
        Your property red-flag detector
      </h2>
      <p className="mt-2 sm:mt-3 text-sm sm:text-base md:text-lg text-muted-foreground max-w-xl mx-auto">
        Thinking of buying or renting in the UK? Get a quick sanity check on any
        property — EPC ratings, flood risk, sale history and more — before you
        commit.
      </p>

      <div className="mt-4 sm:mt-6 md:mt-10 grid grid-cols-3 gap-2 sm:gap-4 md:gap-6 max-w-2xl mx-auto">
        {[
        {
          icon: Search,
          title: "Search by postcode",
          desc: "Find any UK address instantly"
        },
        {
          icon: FileText,
          title: "Instant report",
          desc: "EPC, flood risk & sale prices"
        },
        {
          icon: Shield,
          title: "Spot red flags",
          desc: "Know the risks before you pay"
        }].
        map(({ icon: Icon, title, desc }) =>
        <div
          key={title}
          className="panel flex flex-col items-center gap-1.5 sm:gap-2 py-3 sm:py-5 px-0">
          
            <div className="h-8 w-8 sm:h-10 sm:w-10 rounded-full bg-accent flex items-center justify-center">
              <Icon className="h-4 w-4 sm:h-5 sm:w-5 text-accent-foreground" />
            </div>
            <h3 className="text-xs sm:text-sm font-semibold text-foreground">{title}</h3>
            <p className="text-[11px] sm:text-xs text-muted-foreground hidden sm:block">{desc}</p>
          </div>
        )}
      </div>
    </section>);

}