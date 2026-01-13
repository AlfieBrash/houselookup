import { MapPin } from "lucide-react";

export interface PostcodeData {
  postcode: string;
  adminDistrict: string;
  region: string;
  country: string;
  constituency: string;
  latitude: number;
  longitude: number;
}

interface PostcodeDetailsProps {
  data: PostcodeData;
}

export function PostcodeDetails({ data }: PostcodeDetailsProps) {
  const details = [
    { label: "Normalised postcode", value: data.postcode },
    { label: "Local authority", value: data.adminDistrict },
    { label: "Region", value: data.region },
    { label: "Country", value: data.country },
    { label: "Parliamentary constituency", value: data.constituency },
    { label: "Latitude", value: data.latitude.toFixed(6) },
    { label: "Longitude", value: data.longitude.toFixed(6) },
  ];

  return (
    <div className="panel slide-in">
      <div className="panel-header flex items-center gap-2">
        <MapPin className="h-5 w-5 text-primary" />
        <span>Postcode details</span>
      </div>
      <div className="space-y-0">
        {details.map((item) => (
          <div key={item.label} className="key-value-row">
            <span className="key-label">{item.label}</span>
            <span className="key-value">{item.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
