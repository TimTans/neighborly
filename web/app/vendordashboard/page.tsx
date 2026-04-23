"use client";
import React, { useState, useRef, useEffect } from "react";
import VendorMap from "@/components/VendorMap";

interface StoreProduct {
  id: string;
  name: string;
  brand: string | null;
  category: string;
  unit_type: string;
  price: number;
  sale_price: number | null;
  in_stock: boolean;
  data_source: string;
  updated_at: string;
}

interface PriceHistoryEntry {
  price: number;
  sale_price: number | null;
  recorded_at: string;
}

interface StoreReview {
  rating: number;
  comment: string | null;
  user_name: string;
  created_at: string;
}

interface Store {
  name: string;
  address: string;
  phone: string | null;
  website_url: string | null;
  zip_code: string | null;
}

/* ═══ DATA ═══ */

const INITIAL_PRODUCTS: StoreProduct[] = [
  { id: "sp1", name: "Organic Bananas", brand: "Trader Joe's", category: "Produce", unit_type: "bunch", price: 1.29, sale_price: null, in_stock: true, data_source: "vendor", updated_at: "2026-02-24T10:30:00" },
  { id: "sp2", name: "Whole Milk 1 Gal", brand: "Trader Joe's", category: "Dairy", unit_type: "gal", price: 4.69, sale_price: null, in_stock: true, data_source: "vendor", updated_at: "2026-02-24T11:00:00" },
  { id: "sp3", name: "Chicken Breast", brand: null, category: "Meat", unit_type: "lb", price: 8.49, sale_price: 6.99, in_stock: true, data_source: "vendor", updated_at: "2026-02-24T08:15:00" },
  { id: "sp4", name: "Sourdough Bread", brand: "Trader Joe's", category: "Bakery", unit_type: "loaf", price: 4.49, sale_price: null, in_stock: true, data_source: "vendor", updated_at: "2026-02-24T11:45:00" },
  { id: "sp5", name: "Baby Spinach 5oz", brand: "Trader Joe's", category: "Produce", unit_type: "bag", price: 3.49, sale_price: 2.99, in_stock: true, data_source: "api", updated_at: "2026-02-24T09:20:00" },
  { id: "sp6", name: "Greek Yogurt 32oz", brand: "Fage", category: "Dairy", unit_type: "tub", price: 5.09, sale_price: null, in_stock: false, data_source: "vendor", updated_at: "2026-02-24T06:00:00" },
  { id: "sp7", name: "Olive Oil 500ml", brand: "Trader Joe's", category: "Pantry", unit_type: "bottle", price: 7.99, sale_price: 5.49, in_stock: true, data_source: "vendor", updated_at: "2026-02-24T11:00:00" },
  { id: "sp8", name: "Avocados (4 pk)", brand: null, category: "Produce", unit_type: "pack", price: 4.49, sale_price: 2.99, in_stock: true, data_source: "community", updated_at: "2026-02-23T17:30:00" },
];

const PRICE_HISTORY: Record<string, PriceHistoryEntry[]> = {
  sp3: [
    { price: 8.49, sale_price: null, recorded_at: "2026-02-01" },
    { price: 8.49, sale_price: 7.49, recorded_at: "2026-02-10" },
    { price: 8.49, sale_price: 6.99, recorded_at: "2026-02-20" },
  ],
  sp7: [
    { price: 8.99, sale_price: null, recorded_at: "2026-01-15" },
    { price: 7.99, sale_price: null, recorded_at: "2026-02-01" },
    { price: 7.99, sale_price: 5.49, recorded_at: "2026-02-22" },
  ],
  sp1: [
    { price: 1.49, sale_price: null, recorded_at: "2026-02-01" },
    { price: 1.29, sale_price: null, recorded_at: "2026-02-15" },
  ],
};

const REVIEWS: StoreReview[] = [
  { rating: 5, comment: "Great prices on produce, always fresh.", user_name: "Maria S.", created_at: "2026-02-22" },
  { rating: 4, comment: "Love the store brand items. Wish they had more organic options.", user_name: "James L.", created_at: "2026-02-20" },
  { rating: 5, comment: "Best prices in the neighborhood for everyday staples.", user_name: "Priya K.", created_at: "2026-02-18" },
  { rating: 3, comment: "Sometimes out of stock on popular items.", user_name: "David R.", created_at: "2026-02-15" },
  { rating: 5, comment: null, user_name: "Aisha M.", created_at: "2026-02-12" },
];

const STORE: Store = {
  name: "Trader Joe's",
  address: "130 Court St, Brooklyn, NY 11201",
  phone: "(718) 246-8460",
  website_url: "traderjoes.com",
  zip_code: "11201",
};

/* ═══ HELPERS ═══ */

const CAT_COLORS: Record<string, string> = {
  Produce: "#2D6A4F",
  Dairy: "#1565C0",
  Meat: "#C1292E",
  Bakery: "#D4700A",
  Pantry: "#6D6560",
};

const SRC_LABELS: Record<string, string> = {
  vendor: "Vendor",
  api: "API",
  community: "Community",
  scrape: "Scraped",
};

const catColor = (c: string): string => CAT_COLORS[c] || "#8B8680";
const srcLabel = (s: string): string => SRC_LABELS[s] || s;

const srcPillClass = (s: string): string => {
  if (s === "vendor") return "bg-green-100 text-green-800";
  if (s === "api") return "bg-blue-100 text-blue-700";
  if (s === "community") return "bg-orange-100 text-orange-700";
  return "bg-stone-100 text-stone-500";
};

const fmtTime = (iso: string): string => {
  const d = new Date(iso);
  const now = new Date("2026-02-24T12:00:00");
  const m = Math.floor((now.getTime() - d.getTime()) / 60000);
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric" });
};

const fmtDate = (iso: string): string =>
  new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric" });

const starsStr = (n: number): string => "★".repeat(n) + "☆".repeat(5 - n);

/* ═══ COMPONENT ═══ */

const VendorDashboard: React.FC = () => {
  const [activeTab, setActiveTab] = useState<string>("products");
  const [storeProducts, setStoreProducts] = useState<StoreProduct[]>(INITIAL_PRODUCTS);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editPrice, setEditPrice] = useState<string>("");
  const [editSale, setEditSale] = useState<string>("");
  const [historyOpen, setHistoryOpen] = useState<string | null>(null);
  const priceInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (editingId && priceInputRef.current) priceInputRef.current.focus();
  }, [editingId]);

  const inStockCount = storeProducts.filter((p) => p.in_stock).length;
  const outOfStockCount = storeProducts.filter((p) => !p.in_stock).length;
  const onSaleCount = storeProducts.filter((p) => p.sale_price !== null).length;
  const stockPct = Math.round((inStockCount / storeProducts.length) * 100);
  const avgRating = (REVIEWS.reduce((s, r) => s + r.rating, 0) / REVIEWS.length).toFixed(1);

  const categoryCounts: Record<string, number> = {};
  storeProducts.forEach((p) => {
    categoryCounts[p.category] = (categoryCounts[p.category] || 0) + 1;
  });

  const startEdit = (p: StoreProduct) => {
    setEditingId(p.id);
    setEditPrice(p.price.toFixed(2));
    setEditSale(p.sale_price ? p.sale_price.toFixed(2) : "");
  };

  const cancelEdit = () => setEditingId(null);

  const saveEdit = (id: string) => {
    const np = parseFloat(editPrice);
    if (isNaN(np)) return;
    const ns = editSale.trim() ? parseFloat(editSale) : null;
    if (ns !== null && isNaN(ns)) return;
    setStoreProducts((prev) =>
      prev.map((p) =>
        p.id === id
          ? { ...p, price: np, sale_price: ns, data_source: "vendor", updated_at: new Date().toISOString() }
          : p
      )
    );
    setEditingId(null);
  };

  const toggleStock = (id: string) => {
    setStoreProducts((prev) =>
      prev.map((p) =>
        p.id === id
          ? { ...p, in_stock: !p.in_stock, data_source: "vendor", updated_at: new Date().toISOString() }
          : p
      )
    );
  };

  const handleEditKeydown = (e: React.KeyboardEvent, id: string) => {
    if (e.key === "Enter") saveEdit(id);
    if (e.key === "Escape") cancelEdit();
  };

  /* ── Rating helpers ── */
  const ratingCounts = [5, 4, 3, 2, 1].map((r) => ({
    rating: r,
    count: REVIEWS.filter((rv) => rv.rating === r).length,
    pct: REVIEWS.length ? (REVIEWS.filter((rv) => rv.rating === r).length / REVIEWS.length) * 100 : 0,
  }));

  return (
    <div
      className="min-h-screen text-stone-900"
      style={{ background: "#F7F5F0", fontFamily: "'DM Sans', sans-serif" }}
    >
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=DM+Sans:opsz,wght@9..40,300;9..40,400;9..40,500;9..40,600;9..40,700&family=Fraunces:opsz,wght@9..144,300;9..144,400;9..144,600;9..144,700&display=swap');
        .fraunces { font-family: 'Fraunces', serif; }
        .stock-bar-fill {
          height: 100%; border-radius: 100px;
          background: linear-gradient(90deg, #2D6A4F, #52B788);
          transition: width 0.6s ease;
        }
      `}</style>

      {/* ── Header ── */}
      <header className="max-w-[1320px] mx-auto px-8 pt-6 pb-5 flex justify-between items-center">
        {/* Logo */}
        <div className="flex items-center gap-3.5">
          <div
            className="w-11 h-11 rounded-[14px] flex items-center justify-center text-white text-xl font-bold fraunces"
            style={{ background: "linear-gradient(135deg,#2D6A4F,#52B788)", boxShadow: "0 4px 14px rgba(45,106,79,.25)" }}
          >N</div>
          <div>
            <div className="flex items-center gap-2">
              <span className="fraunces text-[22px] font-bold tracking-tight">Neighborly</span>
              <span className="text-[11px] font-semibold text-orange-600 bg-orange-100 px-2.5 py-0.5 rounded-full tracking-wider">VENDOR</span>
            </div>
            <div className="text-xs text-stone-400 -mt-0.5">Store management portal</div>
          </div>
        </div>

        {/* Nav tabs */}
        <nav className="flex gap-1 bg-stone-200 rounded-full p-1">
          {["products", "reviews", "store info"].map((t) => (
            <button
              key={t}
              onClick={() => setActiveTab(t)}
              className={`px-5 py-2.5 rounded-full text-sm font-medium transition-all duration-200 border-none cursor-pointer
                ${activeTab === t
                  ? "bg-green-800 text-white shadow-sm"
                  : "bg-transparent text-stone-500 hover:bg-stone-300 hover:text-stone-800"}`}
            >
              {t.charAt(0).toUpperCase() + t.slice(1)}
            </button>
          ))}
        </nav>

        {/* Store badge */}
        <div className="flex items-center gap-2.5 cursor-pointer bg-stone-200 rounded-xl px-3.5 py-1.5">
          <div
            className="w-8 h-8 rounded-[10px] flex items-center justify-center text-white font-bold text-sm"
            style={{ background: "linear-gradient(135deg,#D94F30,#F4A261)" }}
          >TJ</div>
          <div>
            <div className="text-sm font-semibold leading-tight">{STORE.name}</div>
            <div className="text-[11px] text-stone-400">130 Court St</div>
          </div>
        </div>
      </header>

      {/* ── Banner ── */}
      <div className="max-w-[1320px] mx-auto px-8 pb-6">
        <div
          className="rounded-3xl px-10 py-8 flex justify-between items-center relative overflow-hidden"
          style={{ background: "linear-gradient(135deg,#1B4332 0%,#2D6A4F 50%,#40916C 100%)" }}
        >
          <div className="absolute -top-16 -right-10 w-60 h-60 rounded-full bg-white/[0.04] pointer-events-none"/>
          <div className="absolute -bottom-20 right-32 w-44 h-44 rounded-full bg-white/[0.03] pointer-events-none"/>
          <div className="relative z-10">
            <div className="fraunces text-[28px] font-semibold text-white tracking-tight mb-1.5">
              Welcome back, {STORE.name}
            </div>
            <div className="text-white/70 text-[15px] max-w-[520px]">
              <span className="text-green-300 font-semibold">{storeProducts.length} products</span> listed
              {" · "}{inStockCount} in stock
              {outOfStockCount > 0 && (
                <>{" · "}<span className="text-red-200 font-semibold">{outOfStockCount} out of stock</span></>
              )}
              {" · "}{onSaleCount} on sale
            </div>
          </div>
          <button className="relative z-10 bg-white text-green-800 font-semibold text-[15px] px-7 py-3.5 rounded-xl cursor-pointer border-none hover:bg-green-50 transition-all hover:-translate-y-0.5 hover:shadow-lg">
            + Add Product
          </button>
        </div>
      </div>

      {/* ═══════════════════════════════════════════ */}
      {/* ══ PRODUCTS TAB ══ */}
      {/* ═══════════════════════════════════════════ */}
      {activeTab === "products" && (
      <div className="max-w-[1320px] mx-auto px-8 pb-12 grid grid-cols-3 gap-5">

        {/* ── Total Products ── */}
        <div className="bg-white rounded-2xl p-7 border border-black/[0.05] hover:shadow-xl hover:-translate-y-0.5 transition-all duration-300">
          <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-widest text-stone-400 mb-4">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"/>
            </svg>
            Total Products
          </div>
          <div className="fraunces text-[42px] font-semibold leading-none">{storeProducts.length}</div>
          <div className="text-sm text-stone-400 mt-1 mb-4">listed in your store</div>
          <div className="flex flex-wrap gap-2">
            {Object.entries(categoryCounts).map(([cat, count]) => (
              <span
                key={cat}
                className="inline-flex items-center px-2.5 py-1 rounded-full text-[11px] font-semibold"
                style={{ background: `${catColor(cat)}18`, color: catColor(cat) }}
              >{cat} ({count})</span>
            ))}
          </div>
        </div>

        {/* ── Stock Status ── */}
        <div className="bg-white rounded-2xl p-7 border border-black/[0.05] hover:shadow-xl hover:-translate-y-0.5 transition-all duration-300">
          <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-widest text-stone-400 mb-4">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"/>
              <rect x="9" y="3" width="6" height="4" rx="1"/>
            </svg>
            Stock Status
          </div>
          <div className="flex gap-6 mb-4">
            <div>
              <div className="fraunces text-[42px] font-semibold leading-none text-green-800">{inStockCount}</div>
              <div className="text-sm text-stone-400">in stock</div>
            </div>
            <div>
              <div className={`fraunces text-[42px] font-semibold leading-none ${outOfStockCount > 0 ? "text-red-600" : "text-green-800"}`}>
                {outOfStockCount}
              </div>
              <div className="text-sm text-stone-400">out of stock</div>
            </div>
          </div>
          <div className="bg-stone-100 rounded-full h-2.5 overflow-hidden">
            <div className="stock-bar-fill" style={{ width: `${stockPct}%` }}/>
          </div>
          <div className="text-xs text-stone-400 mt-1.5">{stockPct}% availability</div>
        </div>

        {/* ── Reviews Summary ── */}
        <div className="bg-white rounded-2xl p-7 border border-black/[0.05] hover:shadow-xl hover:-translate-y-0.5 transition-all duration-300">
          <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-widest text-stone-400 mb-4">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
            </svg>
            Shopper Reviews
          </div>
          <div className="flex items-end gap-3 mb-1">
            <div className="fraunces text-[42px] font-semibold leading-none text-orange-600">{avgRating}</div>
            <div className="text-[22px] text-orange-500 tracking-widest pb-1">{"★".repeat(Math.round(parseFloat(avgRating)))}</div>
          </div>
          <div className="text-sm text-stone-400 mb-4">{REVIEWS.length} reviews</div>
          <div className="flex flex-col gap-1.5">
            {ratingCounts.map(({ rating, count, pct }) => (
              <div key={rating} className="flex items-center gap-2">
                <span className="text-xs text-stone-400 w-3 text-right">{rating}</span>
                <svg width="11" height="11" viewBox="0 0 24 24" fill="#D4700A" stroke="none">
                  <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
                </svg>
                <div className="flex-1 bg-stone-100 rounded-full h-1.5 overflow-hidden">
                  <div className="h-full rounded-full bg-orange-400" style={{ width: `${pct}%` }}/>
                </div>
                <span className="text-xs text-stone-400 w-4">{count}</span>
              </div>
            ))}
          </div>
        </div>

        {/* ── Product Table — col-span-3 ── */}
        <div className="bg-white rounded-2xl p-7 border border-black/[0.05] hover:shadow-xl hover:-translate-y-0.5 transition-all duration-300 col-span-3">
          <div className="flex justify-between items-center mb-3">
            <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-widest text-stone-400">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"/>
              </svg>
              Product Inventory
            </div>
            <div className="flex gap-2">
              <button className="border border-green-800 text-green-800 bg-transparent px-3.5 py-1.5 rounded-xl text-xs font-semibold cursor-pointer hover:bg-green-50 transition-colors">
                Export
              </button>
              <button className="bg-green-800 text-white border-none px-3.5 py-1.5 rounded-xl text-xs font-semibold cursor-pointer hover:bg-green-900 transition-colors">
                + Add Product
              </button>
            </div>
          </div>

          {/* Table header */}
          <div className="grid grid-cols-[2.2fr_0.8fr_0.9fr_0.7fr_0.7fr_0.7fr_80px] items-center py-3 border-b-2 border-stone-100 text-[11px] font-semibold text-stone-300 uppercase tracking-wider">
            <span>Product</span>
            <span>Price</span>
            <span>Sale Price</span>
            <span>In Stock</span>
            <span>Source</span>
            <span>Updated</span>
            <span className="text-right">Actions</span>
          </div>

          {/* Rows */}
          {storeProducts.map((p) => {
            const isEditing = editingId === p.id;
            const hasHistory = !!PRICE_HISTORY[p.id];
            const isHistoryOpen = historyOpen === p.id;

            return (
              <div key={p.id}>
                <div className="grid grid-cols-[2.2fr_0.8fr_0.9fr_0.7fr_0.7fr_0.7fr_80px] items-center py-3.5 border-b border-stone-100 last:border-b-0 text-sm">

                  {/* Name / brand / category */}
                  <div className="flex items-center gap-2.5">
                    <div className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: catColor(p.category) }}/>
                    <div>
                      <div className="font-medium">{p.name}</div>
                      <div className="text-[11px] text-stone-400">
                        {p.brand || "Generic"} · {p.category}{p.unit_type ? ` · per ${p.unit_type}` : ""}
                      </div>
                    </div>
                  </div>

                  {/* Price */}
                  <div>
                    {isEditing ? (
                      <div className="flex items-center gap-1">
                        <span className="text-xs text-stone-400">$</span>
                        <input
                          ref={priceInputRef}
                          value={editPrice}
                          onChange={(e) => setEditPrice(e.target.value)}
                          onKeyDown={(e) => handleEditKeydown(e, p.id)}
                          className="w-16 border border-stone-200 rounded-lg px-2 py-1.5 text-sm font-semibold text-right bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10"
                        />
                      </div>
                    ) : (
                      <span className="font-semibold cursor-pointer hover:text-green-800 transition-colors" onClick={() => startEdit(p)}>
                        ${p.price.toFixed(2)}
                      </span>
                    )}
                  </div>

                  {/* Sale price */}
                  <div>
                    {isEditing ? (
                      <div className="flex items-center gap-1">
                        <span className="text-xs text-stone-400">$</span>
                        <input
                          value={editSale}
                          placeholder="—"
                          onChange={(e) => setEditSale(e.target.value)}
                          onKeyDown={(e) => handleEditKeydown(e, p.id)}
                          className="w-16 border border-stone-200 rounded-lg px-2 py-1.5 text-sm font-semibold text-right bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10"
                        />
                      </div>
                    ) : p.sale_price ? (
                      <span className="font-semibold text-green-800">${p.sale_price.toFixed(2)}</span>
                    ) : (
                      <span className="text-stone-300">—</span>
                    )}
                  </div>

                  {/* In stock toggle */}
                  <div>
                    <button
                      onClick={() => toggleStock(p.id)}
                      className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold border-none cursor-pointer transition-colors
                        ${p.in_stock ? "bg-green-100 text-green-800 hover:bg-green-200" : "bg-red-100 text-red-600 hover:bg-red-200"}`}
                    >
                      <div className={`w-2 h-2 rounded-full ${p.in_stock ? "bg-green-700" : "bg-red-600"}`}/>
                      {p.in_stock ? "In Stock" : "Out"}
                    </button>
                  </div>

                  {/* Source */}
                  <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-[11px] font-semibold w-fit ${srcPillClass(p.data_source)}`}>
                    {srcLabel(p.data_source)}
                  </span>

                  {/* Updated */}
                  <span className="text-xs text-stone-400">{fmtTime(p.updated_at)}</span>

                  {/* Actions */}
                  <div className="flex gap-1 justify-end">
                    {isEditing ? (
                      <>
                        <button
                          onClick={() => saveEdit(p.id)}
                          className="text-green-800 font-semibold text-xs px-2.5 py-1.5 rounded-lg hover:bg-green-50 transition-colors border-none bg-transparent cursor-pointer"
                        >Save</button>
                        <button
                          onClick={cancelEdit}
                          className="text-stone-400 text-xs px-2 py-1.5 rounded-lg hover:bg-stone-100 transition-colors border-none bg-transparent cursor-pointer"
                        >✕</button>
                      </>
                    ) : (
                      <>
                        <button
                          onClick={() => startEdit(p)}
                          title="Edit price"
                          className="text-stone-400 p-1.5 rounded-lg hover:bg-stone-100 hover:text-stone-700 transition-colors border-none bg-transparent cursor-pointer"
                        >
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                          </svg>
                        </button>
                        {hasHistory && (
                          <button
                            onClick={() => setHistoryOpen(isHistoryOpen ? null : p.id)}
                            title="Price history"
                            className={`p-1.5 rounded-lg transition-colors border-none cursor-pointer
                              ${isHistoryOpen ? "bg-green-100 text-green-800" : "bg-transparent text-stone-400 hover:bg-stone-100 hover:text-stone-700"}`}
                          >
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                              <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
                            </svg>
                          </button>
                        )}
                      </>
                    )}
                  </div>
                </div>

                {/* Price history panel */}
                {isHistoryOpen && hasHistory && (
                  <div className="bg-stone-50 rounded-xl p-4 my-1 mb-2 border border-stone-100">
                    <div className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-3">
                      Price History — {p.name}
                    </div>
                    <div className="flex gap-3">
                      {PRICE_HISTORY[p.id].map((h, i) => (
                        <div key={i} className="flex-1 bg-white rounded-xl p-3.5 border border-stone-100">
                          <div className="text-[11px] text-stone-400 mb-1">{fmtDate(h.recorded_at)}</div>
                          <div className="font-semibold text-[15px]">${h.price.toFixed(2)}</div>
                          {h.sale_price && (
                            <div className="text-xs text-green-800 font-semibold mt-1">Sale: ${h.sale_price.toFixed(2)}</div>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            );
          })}

          {/* Footer */}
          <div className="flex justify-between items-center mt-4 pt-4 border-t-2 border-stone-100">
            <span className="text-sm text-stone-400">
              {storeProducts.length} products · {onSaleCount} on sale · {outOfStockCount} out of stock
            </span>
          </div>
        </div>

        {/* ── On Sale — col-span-3 ── */}
        <div className="bg-white rounded-2xl p-7 border border-black/[0.05] hover:shadow-xl hover:-translate-y-0.5 transition-all duration-300 col-span-3">
          <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-widest text-stone-400 mb-5">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/>
              <line x1="7" y1="7" x2="7.01" y2="7"/>
            </svg>
            Products on Sale
          </div>
          {onSaleCount > 0 ? (
            <div className="grid grid-cols-3 gap-3">
              {storeProducts.filter((p) => p.sale_price !== null).map((p) => {
                const pctOff = Math.round(((p.price - p.sale_price!) / p.price) * 100);
                return (
                  <div key={p.id} className="bg-stone-50 rounded-2xl p-[18px] border border-stone-100">
                    <div className="flex justify-between items-start mb-3">
                      <div>
                        <div className="font-semibold text-[15px]">{p.name}</div>
                        <div className="text-xs text-stone-400">{p.brand || "Generic"} · per {p.unit_type}</div>
                      </div>
                      <span className="inline-flex items-center px-2.5 py-1 rounded-full text-[11px] font-semibold bg-green-100 text-green-800">
                        SALE
                      </span>
                    </div>
                    <div className="flex items-baseline gap-2.5">
                      <span className="fraunces text-2xl font-semibold text-green-800">${p.sale_price!.toFixed(2)}</span>
                      <span className="text-sm text-stone-300 line-through">${p.price.toFixed(2)}</span>
                      <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold bg-green-100 text-green-800">
                        −{pctOff}%
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="text-center py-8 text-stone-400 text-sm">
              No active sales. Set a sale price on any product to create a deal.
            </div>
          )}
        </div>

      </div>
      )}

      {/* ══ REVIEWS TAB ══ */}
      {activeTab === "reviews" && (
      <div className="max-w-[1320px] mx-auto px-8 pb-12 grid grid-cols-3 gap-5">

        {/* ── Overall Rating Card ── */}
        <div className="bg-white rounded-2xl p-7 border border-black/[0.05] hover:shadow-xl hover:-translate-y-0.5 transition-all duration-300">
          <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-widest text-stone-400 mb-6">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
            </svg>
            Overall Rating
          </div>

          <div className="text-center mb-8">
            <div className="fraunces text-[72px] font-semibold leading-none text-orange-600">{avgRating}</div>
            <div className="text-[26px] text-orange-500 tracking-widest mt-2">
              {"★".repeat(Math.round(parseFloat(avgRating)))}{"☆".repeat(5 - Math.round(parseFloat(avgRating)))}
            </div>
            <div className="text-sm text-stone-400 mt-2">Based on {REVIEWS.length} reviews</div>
          </div>

          {/* Rating Breakdown */}
          <div className="flex flex-col gap-3">
            {ratingCounts.map(({ rating, count, pct }) => (
              <div key={rating} className="flex items-center gap-3">
                <span className="text-sm font-medium text-stone-500 w-4 text-right">{rating}</span>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="#D4700A" stroke="none">
                  <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
                </svg>
                <div className="flex-1 bg-stone-100 rounded-full h-2.5 overflow-hidden">
                  <div className="h-full rounded-full bg-orange-400 transition-all duration-500" style={{ width: `${pct}%` }}/>
                </div>
                <span className="text-sm font-medium text-stone-500 w-6 text-right">{count}</span>
              </div>
            ))}
          </div>

          {/* Quick Stats */}
          <div className="mt-8 pt-6 border-t border-stone-100 grid grid-cols-2 gap-4">
            <div className="bg-stone-50 rounded-xl px-4 py-3 border border-stone-100 text-center">
              <div className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1">5-Star</div>
              <div className="fraunces text-xl font-semibold text-green-800">
                {REVIEWS.length ? Math.round((ratingCounts[0].count / REVIEWS.length) * 100) : 0}%
              </div>
            </div>
            <div className="bg-stone-50 rounded-xl px-4 py-3 border border-stone-100 text-center">
              <div className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1">Avg Score</div>
              <div className="fraunces text-xl font-semibold text-orange-600">{avgRating}</div>
            </div>
          </div>
        </div>

        {/* ── Customer Reviews List ── */}
        <div className="bg-white rounded-2xl p-7 border border-black/[0.05] hover:shadow-xl hover:-translate-y-0.5 transition-all duration-300 col-span-2">
          <div className="flex justify-between items-center mb-5">
            <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-widest text-stone-400">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
              </svg>
              Customer Reviews
            </div>
            <span className="text-xs font-medium text-stone-400 bg-stone-100 px-3 py-1.5 rounded-full">
              {REVIEWS.length} total
            </span>
          </div>

          <div className="flex flex-col">
            {REVIEWS.map((r, i) => (
              <div key={i} className={`py-5 ${i < REVIEWS.length - 1 ? "border-b border-stone-100" : ""}`}>
                <div className="flex justify-between items-start mb-2.5">
                  <div className="flex items-center gap-3">
                    <div
                      className="w-10 h-10 rounded-xl flex items-center justify-center text-sm font-bold text-white"
                      style={{
                        background: `linear-gradient(135deg, ${
                          ["#2D6A4F", "#D94F30", "#1565C0", "#D4700A", "#6D6560"][i % 5]
                        }, ${
                          ["#52B788", "#F4A261", "#42A5F5", "#FFB74D", "#A1887F"][i % 5]
                        })`,
                      }}
                    >
                      {r.user_name.charAt(0)}
                    </div>
                    <div>
                      <div className="font-semibold text-[15px]">{r.user_name}</div>
                      <div className="text-orange-500 text-sm tracking-wider mt-0.5">{starsStr(r.rating)}</div>
                    </div>
                  </div>
                  <span className="text-xs text-stone-400 bg-stone-50 px-3 py-1.5 rounded-full border border-stone-100">
                    {fmtDate(r.created_at)}
                  </span>
                </div>
                {r.comment ? (
                  <div className="text-sm text-stone-600 leading-relaxed ml-[52px] bg-stone-50 rounded-xl px-4 py-3 border border-stone-100">
                    &ldquo;{r.comment}&rdquo;
                  </div>
                ) : (
                  <div className="text-sm text-stone-300 italic ml-[52px]">No written review</div>
                )}
              </div>
            ))}
          </div>
        </div>

      </div>
      )}

      {/* ══ STORE INFO TAB ══ */}
      {activeTab === "store info" && (
      <div className="max-w-[1320px] mx-auto px-8 pb-12 grid grid-cols-3 gap-5">

        {/* ── Store Details ── */}
        <div className="bg-white rounded-2xl p-7 border border-black/[0.05] hover:shadow-xl hover:-translate-y-0.5 transition-all duration-300 col-span-2">
          <div className="flex justify-between items-center mb-5">
            <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-widest text-stone-400">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                <polyline points="9 22 9 12 15 12 15 22"/>
              </svg>
              Store Details
            </div>
            <button className="border border-green-800 text-green-800 bg-transparent px-3.5 py-1.5 rounded-xl text-xs font-semibold cursor-pointer hover:bg-green-50 transition-colors">
              Edit Store Info
            </button>
          </div>

          {/* Store header */}
          <div className="flex items-center gap-4 mb-6">
            <div
              className="w-14 h-14 rounded-2xl flex items-center justify-center text-white font-bold text-xl fraunces"
              style={{ background: "linear-gradient(135deg,#D94F30,#F4A261)" }}
            >TJ</div>
            <div>
              <div className="fraunces text-[22px] font-semibold tracking-tight">{STORE.name}</div>
              <div className="text-sm text-stone-400">{STORE.address}</div>
            </div>
          </div>

          {/* Info grid */}
          <div className="grid grid-cols-2 gap-4">
            {([
              {
                label: "Store Name",
                value: STORE.name,
                icon: (
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                    <polyline points="9 22 9 12 15 12 15 22"/>
                  </svg>
                ),
              },
              {
                label: "Address",
                value: STORE.address,
                icon: (
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
                    <circle cx="12" cy="10" r="3"/>
                  </svg>
                ),
              },
              {
                label: "Phone",
                value: STORE.phone,
                icon: (
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>
                  </svg>
                ),
              },
              {
                label: "Website",
                value: STORE.website_url,
                icon: (
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <circle cx="12" cy="12" r="10"/>
                    <line x1="2" y1="12" x2="22" y2="12"/>
                    <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
                  </svg>
                ),
              },
              {
                label: "Zip Code",
                value: STORE.zip_code,
                icon: (
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/>
                    <polyline points="22,6 12,13 2,6"/>
                  </svg>
                ),
              },
            ] as { label: string; value: string | null; icon: React.ReactNode }[]).map((f, i) => (
              <div key={i} className="bg-stone-50 rounded-xl px-4 py-3.5 border border-stone-100 flex items-start gap-3">
                <div className="text-stone-400 mt-0.5 flex-shrink-0">{f.icon}</div>
                <div>
                  <div className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1">{f.label}</div>
                  <div className="text-sm font-medium break-words">{f.value || "—"}</div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* ── Map ── */}
        <div className="bg-white rounded-2xl border border-black/[0.05] hover:shadow-xl hover:-translate-y-0.5 transition-all duration-300 overflow-hidden">
          <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-widest text-stone-400 px-7 pt-7 pb-4">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
              <circle cx="12" cy="10" r="3"/>
            </svg>
            Location
          </div>
          <div className="h-[calc(100%-60px)] min-h-[280px]">
            <VendorMap address={STORE.address} lat={40.6892} lng={-73.9857} />
          </div>
        </div>

      </div>
      )}

    </div>
  );
};

export default VendorDashboard;
