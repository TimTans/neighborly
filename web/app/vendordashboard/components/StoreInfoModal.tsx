export interface StoreInfoFormValues {
  name: string;
  chain: string;
  address: string;
  zip_code: string;
  phone: string;
  website_url: string;
}

interface StoreInfoModalProps {
  storeForm: StoreInfoFormValues;
  storeFormError: string | null;
  savingStore: boolean;
  onClose: () => void;
  onSave: () => void;
  onChangeField: (field: keyof StoreInfoFormValues, value: string) => void;
}

const StoreInfoModal = ({
  storeForm,
  storeFormError,
  savingStore,
  onClose,
  onSave,
  onChangeField,
}: StoreInfoModalProps) => {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" onClick={onClose}>
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" />

      <div
        className="relative bg-white rounded-2xl w-full max-w-[620px] max-h-[85vh] overflow-hidden flex flex-col"
        style={{ boxShadow: "0 25px 60px rgba(0,0,0,0.15)" }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-7 pt-6 pb-4 border-b border-stone-100">
          <div className="flex justify-between items-center">
            <div className="fraunces text-xl font-semibold tracking-tight">Edit Store Info</div>
            <button
              onClick={onClose}
              disabled={savingStore}
              className="w-8 h-8 rounded-lg flex items-center justify-center text-stone-400 hover:bg-stone-100 hover:text-stone-600 transition-colors border-none bg-transparent cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            onSave();
          }}
          className="px-7 py-5 overflow-y-auto flex-1"
        >
          {storeFormError && (
            <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm rounded-xl px-4 py-3">
              {storeFormError}
            </div>
          )}

          <div className="flex flex-col gap-4">
            <div>
              <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1.5 block">
                Store Name *
              </label>
              <input
                value={storeForm.name}
                onChange={(e) => onChangeField("name", e.target.value)}
                placeholder="Store name"
                className="w-full border border-stone-200 rounded-xl px-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1.5 block">
                  Chain
                </label>
                <input
                  value={storeForm.chain}
                  onChange={(e) => onChangeField("chain", e.target.value)}
                  placeholder="Optional"
                  className="w-full border border-stone-200 rounded-xl px-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
                />
              </div>
              <div>
                <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1.5 block">
                  Zip Code
                </label>
                <input
                  value={storeForm.zip_code}
                  onChange={(e) => onChangeField("zip_code", e.target.value)}
                  placeholder="Optional"
                  className="w-full border border-stone-200 rounded-xl px-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
                />
              </div>
            </div>

            <div>
              <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1.5 block">
                Address
              </label>
              <input
                value={storeForm.address}
                onChange={(e) => onChangeField("address", e.target.value)}
                placeholder="Optional"
                className="w-full border border-stone-200 rounded-xl px-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1.5 block">
                  Phone
                </label>
                <input
                  value={storeForm.phone}
                  onChange={(e) => onChangeField("phone", e.target.value)}
                  placeholder="Optional"
                  className="w-full border border-stone-200 rounded-xl px-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
                />
              </div>
              <div>
                <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1.5 block">
                  Website URL
                </label>
                <input
                  value={storeForm.website_url}
                  onChange={(e) => onChangeField("website_url", e.target.value)}
                  placeholder="https://example.com"
                  className="w-full border border-stone-200 rounded-xl px-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
                />
              </div>
            </div>
          </div>

          <div className="mt-6 pt-4 border-t border-stone-100 flex justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              disabled={savingStore}
              className="border border-stone-200 text-stone-500 bg-transparent px-5 py-2.5 rounded-xl text-sm font-semibold cursor-pointer hover:bg-stone-50 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={savingStore}
              className="bg-green-800 text-white border-none px-5 py-2.5 rounded-xl text-sm font-semibold cursor-pointer hover:bg-green-900 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {savingStore ? "Saving..." : "Save Changes"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default StoreInfoModal;
