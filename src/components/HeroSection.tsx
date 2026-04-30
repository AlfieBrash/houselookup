import { FileText, Search, Shield } from "lucide-react";

const features = [
  {
    icon: Search,
    title: "Search by postcode",
    desc: "Find any UK address instantly",
  },
  {
    icon: FileText,
    title: "Instant report",
    desc: "EPC, flood risk & sale prices",
  },
  {
    icon: Shield,
    title: "Spot red flags",
    desc: "Know the risks before you pay",
  },
];

export function HeroSection() {
  return (
    <section className="py-4 text-center fade-in sm:py-8 md:py-12">
      <h2 className="text-2xl font-bold leading-tight tracking-normal text-foreground sm:text-3xl md:text-4xl">
        Your property red-flag detector
      </h2>
      <p className="mx-auto mt-2 max-w-[32rem] text-sm leading-6 text-muted-foreground sm:mt-3 sm:text-base md:text-lg md:leading-8">
        Thinking of buying or renting in the UK? Get a quick sanity check on any
        property, including EPC ratings, flood risk, sale history and more,
        before you commit.
      </p>

      <div className="mx-auto mt-4 grid max-w-md grid-cols-1 gap-2.5 sm:mt-6 sm:max-w-2xl sm:grid-cols-3 sm:gap-4 md:mt-10 md:gap-6">
        {features.map(({ icon: Icon, title, desc }) => (
          <div
            key={title}
            className="panel flex items-center gap-3 p-3 text-left sm:flex-col sm:gap-2 sm:px-3 sm:py-5 sm:text-center"
          >
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-accent">
              <Icon className="h-4 w-4 text-accent-foreground sm:h-5 sm:w-5" />
            </div>
            <div className="min-w-0">
              <h3 className="text-sm font-semibold text-foreground">{title}</h3>
              <p className="mt-0.5 text-xs leading-5 text-muted-foreground sm:mt-0 sm:leading-normal">
                {desc}
              </p>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
