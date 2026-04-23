import type { Dispatch, RefObject, SetStateAction } from "react";
import { CAT_COLORS } from "../constants";
import { catColor } from "../lib/formatters";
import type { CatalogProduct } from "../types";

export interface NewProductFormValues {
  name: string;
  brand: string;
  category: string;
  unit_size: string;
  price: string;
  sale_price: string;
  in_stock: boolean;
}

interface ProductModalProps {
  closeModal: () => void;
  modalMode: "search" | "create";
  setModalMode: Dispatch<SetStateAction<"search" | "create">>;
  modalError: string | null;
  setModalError: Dispatch<SetStateAction<string | null>>;
  submitting: boolean;
  searchInputRef: RefObject<HTMLInputElement | null>;
  catalogSearch: string;
  setCatalogSearch: Dispatch<SetStateAction<string>>;
  catalogResults: CatalogProduct[];
  catalogLoading: boolean;
  selectedCatalogItem: CatalogProduct | null;
  setSelectedCatalogItem: Dispatch<SetStateAction<CatalogProduct | null>>;
  catalogPrice: string;
  setCatalogPrice: Dispatch<SetStateAction<string>>;
  catalogSalePrice: string;
  setCatalogSalePrice: Dispatch<SetStateAction<string>>;
  catalogInStock: boolean;
  setCatalogInStock: Dispatch<SetStateAction<boolean>>;
  addFromCatalog: () => void;
  newProduct: NewProductFormValues;
  setNewProduct: Dispatch<SetStateAction<NewProductFormValues>>;
  createNewProduct: () => void;
}

const ProductModal = ({
  closeModal,
  modalMode,
  setModalMode,
  modalError,
  setModalError,
  submitting,
  searchInputRef,
  catalogSearch,
  setCatalogSearch,
  catalogResults,
  catalogLoading,
  selectedCatalogItem,
  setSelectedCatalogItem,
  catalogPrice,
  setCatalogPrice,
  catalogSalePrice,
  setCatalogSalePrice,
  catalogInStock,
  setCatalogInStock,
  addFromCatalog,
  newProduct,
  setNewProduct,
  createNewProduct,
}: ProductModalProps) => {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" onClick={closeModal}>
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" />

      <div
        className="relative bg-white rounded-2xl w-full max-w-[640px] max-h-[85vh] overflow-hidden flex flex-col"
        style={{ boxShadow: "0 25px 60px rgba(0,0,0,0.15)" }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-7 pt-6 pb-4 border-b border-stone-100">
          <div className="flex justify-between items-center mb-4">
            <div className="fraunces text-xl font-semibold tracking-tight">Add Product</div>
            <button
              onClick={closeModal}
              className="w-8 h-8 rounded-lg flex items-center justify-center text-stone-400 hover:bg-stone-100 hover:text-stone-600 transition-colors border-none bg-transparent cursor-pointer"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>

          <div className="flex gap-1 bg-stone-100 rounded-full p-1">
            <button
              onClick={() => {
                setModalMode("search");
                setSelectedCatalogItem(null);
                setCatalogPrice("");
                setCatalogSalePrice("");
                setCatalogInStock(true);
                setModalError(null);
              }}
              className={`flex-1 py-2 rounded-full text-sm font-medium transition-all duration-200 border-none cursor-pointer
                ${
                  modalMode === "search"
                    ? "bg-green-800 text-white shadow-sm"
                    : "bg-transparent text-stone-500 hover:text-stone-700"
                }`}
            >
              Search Catalog
            </button>
            <button
              onClick={() => {
                setModalMode("create");
                setModalError(null);
              }}
              className={`flex-1 py-2 rounded-full text-sm font-medium transition-all duration-200 border-none cursor-pointer
                ${
                  modalMode === "create"
                    ? "bg-green-800 text-white shadow-sm"
                    : "bg-transparent text-stone-500 hover:text-stone-700"
                }`}
            >
              Create New
            </button>
          </div>
        </div>

        <div className="px-7 py-5 overflow-y-auto flex-1">
          {modalError && (
            <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm rounded-xl px-4 py-3 flex items-start gap-2">
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                className="flex-shrink-0 mt-0.5"
              >
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
              <span>{modalError}</span>
            </div>
          )}

          {modalMode === "search" && (
            <div>
              <div className="relative mb-4">
                <svg
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-stone-400"
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <circle cx="11" cy="11" r="8" />
                  <line x1="21" y1="21" x2="16.65" y2="16.65" />
                </svg>
                <input
                  ref={searchInputRef}
                  value={catalogSearch}
                  onChange={(e) => {
                    setCatalogSearch(e.target.value);
                    setSelectedCatalogItem(null);
                    setCatalogPrice("");
                  }}
                  placeholder="Search by name, brand, or category..."
                  className="w-full border border-stone-200 rounded-xl pl-10 pr-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
                />
              </div>

              {catalogSearch.trim().length === 0 ? (
                <div className="text-center py-10">
                  <div className="text-stone-300 mb-2">
                    <svg className="mx-auto" width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                      <circle cx="11" cy="11" r="8" />
                      <line x1="21" y1="21" x2="16.65" y2="16.65" />
                    </svg>
                  </div>
                  <div className="text-sm text-stone-400">Type to search the product catalog</div>
                  <div className="text-xs text-stone-300 mt-1">
                    Or switch to &ldquo;Create New&rdquo; to add a custom product
                  </div>
                </div>
              ) : catalogLoading ? (
                <div className="text-center py-10">
                  <div className="inline-flex items-center justify-center w-10 h-10 rounded-full border-2 border-stone-200 border-t-green-700 animate-spin mb-2" />
                  <div className="text-sm text-stone-400">Searching catalog...</div>
                </div>
              ) : catalogResults.length === 0 ? (
                <div className="text-center py-10">
                  <div className="text-sm text-stone-400 mb-3">
                    No products found for &ldquo;{catalogSearch}&rdquo;
                  </div>
                  <button
                    onClick={() => {
                      setModalMode("create");
                      setNewProduct((prev) => ({ ...prev, name: catalogSearch }));
                    }}
                    className="bg-green-800 text-white border-none px-4 py-2 rounded-xl text-sm font-semibold cursor-pointer hover:bg-green-900 transition-colors"
                  >
                    Create &ldquo;{catalogSearch}&rdquo; as new product
                  </button>
                </div>
              ) : (
                <div className="flex flex-col gap-2">
                  {catalogResults.map((item) => {
                    const isSelected = selectedCatalogItem?.id === item.id;
                    return (
                      <div key={item.id}>
                        <div
                          className={`flex items-center justify-between p-4 rounded-xl border transition-all cursor-pointer group
                            ${
                              isSelected
                                ? "bg-green-50 border-green-200"
                                : "bg-stone-50 border-stone-100 hover:border-green-200 hover:bg-green-50/30"
                            }`}
                          onClick={() => {
                            setSelectedCatalogItem(item);
                            setCatalogPrice("");
                            setCatalogSalePrice("");
                            setCatalogInStock(true);
                            setModalError(null);
                          }}
                        >
                          <div className="flex items-center gap-3">
                            <div
                              className="w-2.5 h-2.5 rounded-full flex-shrink-0"
                              style={{ background: catColor(item.category) }}
                            />
                            <div>
                              <div className="font-medium text-sm">{item.name}</div>
                              <div className="text-[11px] text-stone-400">
                                {item.brand || "Generic"} · {item.category}
                                {item.unit_size ? ` · per ${item.unit_size}` : ""}
                              </div>
                            </div>
                          </div>
                          {!isSelected && (
                            <div className="w-8 h-8 rounded-lg bg-green-100 text-green-800 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <line x1="12" y1="5" x2="12" y2="19" />
                                <line x1="5" y1="12" x2="19" y2="12" />
                              </svg>
                            </div>
                          )}
                        </div>
                        {isSelected && (
                          <div className="mt-2 ml-4 p-4 rounded-xl bg-stone-50 border border-stone-100 flex flex-col gap-3">
                            <div className="flex items-center gap-3 flex-wrap">
                              <div className="flex flex-col gap-1">
                                <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider">
                                  Price *
                                </label>
                                <div className="relative">
                                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-stone-400 text-sm">$</span>
                                  <input
                                    value={catalogPrice}
                                    onChange={(e) => setCatalogPrice(e.target.value)}
                                    placeholder="0.00"
                                    autoFocus
                                    inputMode="decimal"
                                    onKeyDown={(e) => {
                                      if (e.key === "Enter") addFromCatalog();
                                    }}
                                    className="w-28 border border-stone-200 rounded-xl pl-7 pr-3 py-2 text-sm bg-white outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
                                  />
                                </div>
                              </div>
                              <div className="flex flex-col gap-1">
                                <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider">
                                  Sale Price
                                </label>
                                <div className="relative">
                                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-stone-400 text-sm">$</span>
                                  <input
                                    value={catalogSalePrice}
                                    onChange={(e) => setCatalogSalePrice(e.target.value)}
                                    placeholder="optional"
                                    inputMode="decimal"
                                    onKeyDown={(e) => {
                                      if (e.key === "Enter") addFromCatalog();
                                    }}
                                    className="w-28 border border-stone-200 rounded-xl pl-7 pr-3 py-2 text-sm bg-white outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
                                  />
                                </div>
                              </div>
                              <label className="flex items-center gap-2 text-sm text-stone-600 cursor-pointer select-none mt-5">
                                <input
                                  type="checkbox"
                                  checked={catalogInStock}
                                  onChange={(e) => setCatalogInStock(e.target.checked)}
                                  className="w-4 h-4 accent-green-700 cursor-pointer"
                                />
                                In stock
                              </label>
                            </div>
                            <div className="flex justify-end">
                              <button
                                onClick={addFromCatalog}
                                disabled={
                                  submitting ||
                                  !catalogPrice ||
                                  isNaN(parseFloat(catalogPrice)) ||
                                  parseFloat(catalogPrice) <= 0
                                }
                                className="bg-green-800 text-white border-none px-4 py-2 rounded-xl text-sm font-semibold cursor-pointer hover:bg-green-900 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                              >
                                {submitting ? "Adding..." : "Add"}
                              </button>
                            </div>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {modalMode === "create" && (
            <div className="flex flex-col gap-4">
              <div>
                <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1.5 block">
                  Product Name *
                </label>
                <input
                  value={newProduct.name}
                  onChange={(e) => setNewProduct({ ...newProduct, name: e.target.value })}
                  placeholder="e.g. Organic Whole Milk"
                  className="w-full border border-stone-200 rounded-xl px-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1.5 block">
                    Brand
                  </label>
                  <input
                    value={newProduct.brand}
                    onChange={(e) => setNewProduct({ ...newProduct, brand: e.target.value })}
                    placeholder="e.g. Horizon"
                    className="w-full border border-stone-200 rounded-xl px-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
                  />
                </div>
                <div>
                  <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1.5 block">
                    Category *
                  </label>
                  <select
                    value={newProduct.category}
                    onChange={(e) => setNewProduct({ ...newProduct, category: e.target.value })}
                    className="w-full border border-stone-200 rounded-xl px-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all cursor-pointer appearance-none"
                  >
                    {Object.keys(CAT_COLORS).map((cat) => (
                      <option key={cat} value={cat}>
                        {cat}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1.5 block">
                  Unit Size
                </label>
                <input
                  value={newProduct.unit_size}
                  onChange={(e) => setNewProduct({ ...newProduct, unit_size: e.target.value })}
                  placeholder="e.g. lb, gal, dozen"
                  className="w-full border border-stone-200 rounded-xl px-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1.5 block">
                    Price *
                  </label>
                  <div className="relative">
                    <span className="absolute left-4 top-1/2 -translate-y-1/2 text-stone-400 text-sm">$</span>
                    <input
                      value={newProduct.price}
                      onChange={(e) => setNewProduct({ ...newProduct, price: e.target.value })}
                      placeholder="0.00"
                      inputMode="decimal"
                      onKeyDown={(e) => {
                        if (e.key === "Enter") createNewProduct();
                      }}
                      className="w-full border border-stone-200 rounded-xl pl-8 pr-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
                    />
                  </div>
                </div>
                <div>
                  <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1.5 block">
                    Sale Price
                  </label>
                  <div className="relative">
                    <span className="absolute left-4 top-1/2 -translate-y-1/2 text-stone-400 text-sm">$</span>
                    <input
                      value={newProduct.sale_price}
                      onChange={(e) => setNewProduct({ ...newProduct, sale_price: e.target.value })}
                      placeholder="optional"
                      inputMode="decimal"
                      onKeyDown={(e) => {
                        if (e.key === "Enter") createNewProduct();
                      }}
                      className="w-full border border-stone-200 rounded-xl pl-8 pr-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
                    />
                  </div>
                </div>
              </div>

              <label className="flex items-center gap-2 text-sm text-stone-600 cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={newProduct.in_stock}
                  onChange={(e) => setNewProduct({ ...newProduct, in_stock: e.target.checked })}
                  className="w-4 h-4 accent-green-700 cursor-pointer"
                />
                In stock
              </label>

              {newProduct.name.trim() && (
                <div className="bg-stone-50 rounded-xl p-4 border border-stone-100 mt-2">
                  <div className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-2">
                    Preview
                  </div>
                  <div className="flex items-center gap-2.5">
                    <div className="w-2.5 h-2.5 rounded-full" style={{ background: catColor(newProduct.category) }} />
                    <div>
                      <div className="font-medium text-sm">{newProduct.name}</div>
                      <div className="text-[11px] text-stone-400">
                        {newProduct.brand || "Generic"} · {newProduct.category}
                        {newProduct.unit_size && ` · per ${newProduct.unit_size}`}
                        {newProduct.price && ` · $${parseFloat(newProduct.price).toFixed(2)}`}
                        {newProduct.sale_price && ` · sale $${parseFloat(newProduct.sale_price).toFixed(2)}`}
                        {!newProduct.in_stock && " · out of stock"}
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {modalMode === "create" && (
          <div className="px-7 py-4 border-t border-stone-100 flex justify-end gap-2">
            <button
              onClick={closeModal}
              className="border border-stone-200 text-stone-500 bg-transparent px-5 py-2.5 rounded-xl text-sm font-semibold cursor-pointer hover:bg-stone-50 transition-colors"
            >
              Cancel
            </button>
            <button
              onClick={createNewProduct}
              disabled={submitting || !newProduct.name.trim() || !newProduct.price}
              className="bg-green-800 text-white border-none px-5 py-2.5 rounded-xl text-sm font-semibold cursor-pointer hover:bg-green-900 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {submitting ? "Adding..." : "Add Product"}
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default ProductModal;
