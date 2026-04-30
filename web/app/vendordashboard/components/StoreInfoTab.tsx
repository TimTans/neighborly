import VendorMap from "@/components/VendorMap";
import type { ReactNode } from "react";
import type { Store } from "../types";

interface StoreInfoTabProps {
  store: Store | null;
  storeInitials: string;
  storeName: string;
  onEditStoreInfo: () => void;
}

interface StoreField {
  label: string;
  value: string | null | undefined;
  icon: ReactNode;
}

const StoreInfoTab = ({ store, storeInitials, storeName, onEditStoreInfo }: StoreInfoTabProps) => {
  const fields: StoreField[] = [
    {
      label: "Store Name",
      value: store?.name,
      icon: (
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
          <polyline points="9 22 9 12 15 12 15 22" />
        </svg>
      ),
    },
    {
      label: "Address",
      value: store?.address,
      icon: (
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
          <circle cx="12" cy="10" r="3" />
        </svg>
      ),
    },
    {
      label: "Phone",
      value: store?.phone,
      icon: (
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
        </svg>
      ),
    },
    {
      label: "Website",
      value: store?.website_url,
      icon: (
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="12" cy="12" r="10" />
          <line x1="2" y1="12" x2="22" y2="12" />
          <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
        </svg>
      ),
    },
    {
      label: "Zip Code",
      value: store?.zip_code,
      icon: (
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
          <polyline points="22,6 12,13 2,6" />
        </svg>
      ),
    },
  ];

  return (
    <div className="max-w-[1320px] mx-auto px-8 pb-12 grid grid-cols-5 gap-5 items-start">
      <div className="bg-white rounded-2xl p-7 border border-black/[0.05] hover:shadow-xl hover:-translate-y-0.5 transition-all duration-300 col-span-2">
        <div className="flex justify-between items-center mb-5">
          <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-widest text-stone-400">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
              <polyline points="9 22 9 12 15 12 15 22" />
            </svg>
            Store Details
          </div>
          <button
            onClick={onEditStoreInfo}
            className="border border-green-800 text-green-800 bg-transparent px-3.5 py-1.5 rounded-xl text-xs font-semibold cursor-pointer hover:bg-green-50 transition-colors"
          >
            Edit Store Info
          </button>
        </div>

        <div className="flex items-center gap-4 mb-6">
          <div
            className="w-14 h-14 rounded-2xl flex items-center justify-center text-white font-bold text-xl fraunces"
            style={{ background: "linear-gradient(135deg,#D94F30,#F4A261)" }}
          >
            {storeInitials}
          </div>
          <div>
            <div className="fraunces text-[22px] font-semibold tracking-tight">{storeName}</div>
            <div className="text-sm text-stone-400">{store?.address || "—"}</div>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4">
          {fields.map((f, i) => (
            <div
              key={`${f.label}-${i}`}
              className="bg-stone-50 rounded-xl px-4 py-3.5 border border-stone-100 flex items-start gap-3"
            >
              <div className="text-stone-400 mt-0.5 flex-shrink-0">{f.icon}</div>
              <div>
                <div className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1">
                  {f.label}
                </div>
                <div className="text-sm font-medium break-words">{f.value || "—"}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="bg-white rounded-2xl border border-black/[0.05] hover:shadow-xl hover:-translate-y-0.5 transition-all duration-300 overflow-hidden col-span-3">
        <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-widest text-stone-400 px-7 pt-7 pb-4">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
            <circle cx="12" cy="10" r="3" />
          </svg>
          Location
        </div>
        <div style={{ height: "620px" }}>
          <VendorMap address={store?.address || ""} lat={store?.lat || 40.6892} lng={store?.lng || -73.9857} />
        </div>
      </div>
    </div>
  );
};

export default StoreInfoTab;
