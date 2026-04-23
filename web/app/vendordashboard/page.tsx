"use client";
import React, { useState, useRef, useEffect, useCallback } from "react";
import VendorMap from "@/components/VendorMap";
import ProfileDropdown from "@/components/ProfileDropdown";
import {
  getVendorStore,
  getStoreProducts,
  getStoreReviews,
  getProductPriceHistory,
  searchCatalog,
  updateProductPrice,
  toggleProductStock,
  addCatalogProduct,
  createAndAddProduct,
  deleteStoreProduct,
} from "./actions";

/* ═══ INTERFACES ═══ */

interface StoreProduct {
  id: string;
  product_id: string;
  name: string;
  brand: string | null;
  category: string;
  unit_size: string;
  price: number;
  sale_price: number | null;
  in_stock: boolean;
  data_source: string;
  updated_at: string;
}

interface PriceHistoryEntry {
  price: number;
  sale_price: number | null;
  created_at: string;
}

interface StoreReview {
  id: string;
  rating: number;
  comment: string | null;
  user_name: string;
  created_at: string;
}

interface Store {
  id: string;
  name: string;
  chain: string | null;
  address: string | null;
  zip_code: string | null;
  lat: number | null;
  lng: number | null;
  phone: string | null;
  website_url: string | null;
}

interface CatalogProduct {
  id: string;
  name: string;
  brand: string | null;
  category: string;
  unit_size: string;
}

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
  scraper: "Scraped",
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
  const now = new Date();
  const m = Math.floor((now.getTime() - d.getTime()) / 60000);
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric" });
};

const fmtDate = (iso: string): string =>
  new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric" });

const starsStr = (n: number): string => "★".repeat(n) + "☆".repeat(5 - n);

/* eslint-disable @typescript-eslint/no-explicit-any */
const transformProducts = (raw: any[]): StoreProduct[] =>
  raw.map((sp) => ({
    id: sp.id,
    product_id: sp.products?.id || "",
    name: sp.products?.name || "Unknown",
    brand: sp.products?.brand || null,
    category: sp.products?.product_categories?.name || "Other",
    unit_size: sp.products?.unit_size || "",
    price: sp.price,
    sale_price: sp.sale_price,
    in_stock: sp.in_stock,
    data_source: sp.data_source,
    updated_at: sp.updated_at,
  }));

const transformReviews = (raw: any[]): StoreReview[] =>
  raw.map((r) => ({
    id: r.id,
    rating: r.rating,
    comment: r.comment,
    user_name: r.users
      ? `${r.users.first_name || ""} ${r.users.last_name?.charAt(0) || ""}.`.trim()
      : "Anonymous",
    created_at: r.created_at,
  }));

const transformCatalog = (raw: any[]): CatalogProduct[] =>
  raw.map((p) => ({
    id: p.id,
    name: p.name,
    brand: p.brand,
    category: p.product_categories?.name || "Other",
    unit_size: p.unit_size || "",
  }));
/* eslint-enable @typescript-eslint/no-explicit-any */

/* ═══ PAGINATION CONTROLS ═══ */

const PaginationControls: React.FC<{
  page: number;
  totalPages: number;
  onChange: (p: number) => void;
}> = ({ page, totalPages, onChange }) => {
  const [jumpValue, setJumpValue] = useState("");

  const go = (p: number) => onChange(Math.max(1, Math.min(totalPages, p)));

  const handleJump = () => {
    const n = parseInt(jumpValue, 10);
    if (!isNaN(n)) go(n);
    setJumpValue("");
  };

  // Build the list of page numbers to render, collapsing long ranges with "…"
  const pages: (number | "…")[] = [];
  if (totalPages <= 7) {
    for (let i = 1; i <= totalPages; i++) pages.push(i);
  } else {
    pages.push(1);
    if (page > 3) pages.push("…");
    const start = Math.max(2, page - 1);
    const end = Math.min(totalPages - 1, page + 1);
    for (let i = start; i <= end; i++) pages.push(i);
    if (page < totalPages - 2) pages.push("…");
    pages.push(totalPages);
  }

  const btnBase =
    "h-8 min-w-[32px] px-2 rounded-lg text-xs font-semibold transition-colors cursor-pointer flex items-center justify-center";
  const btnIdle = "border border-stone-200 bg-white text-stone-600 hover:bg-stone-50";
  const btnActive = "bg-green-800 text-white border border-green-800";
  const btnDisabled = "opacity-40 cursor-not-allowed";

  return (
    <div className="flex items-center gap-1.5 flex-wrap justify-end">
      <button
        onClick={() => go(page - 1)}
        disabled={page === 1}
        className={`${btnBase} ${btnIdle} ${page === 1 ? btnDisabled : ""}`}
      >
        ← Prev
      </button>

      {pages.map((p, i) =>
        p === "…" ? (
          <span key={`e${i}`} className="px-1 text-stone-400 text-xs select-none">
            …
          </span>
        ) : (
          <button
            key={p}
            onClick={() => go(p)}
            className={`${btnBase} ${p === page ? btnActive : btnIdle}`}
          >
            {p}
          </button>
        ),
      )}

      <button
        onClick={() => go(page + 1)}
        disabled={page === totalPages}
        className={`${btnBase} ${btnIdle} ${page === totalPages ? btnDisabled : ""}`}
      >
        Next
      </button>

      <div className="flex items-center gap-1.5 ml-3 pl-3 border-l border-stone-200">
        <span className="text-xs text-stone-400">Go to</span>
        <input
          type="number"
          min={1}
          max={totalPages}
          value={jumpValue}
          onChange={(e) => setJumpValue(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              handleJump();
            }
          }}
          placeholder={String(page)}
          className="w-14 px-2 py-1.5 text-xs font-semibold text-center border border-stone-200 rounded-lg outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 bg-white"
        />
        <button
          onClick={handleJump}
          disabled={!jumpValue}
          className={`${btnBase} ${btnIdle} ${!jumpValue ? btnDisabled : ""}`}
        >
          Go
        </button>
      </div>
    </div>
  );
};

/* ═══ COMPONENT ═══ */

const VendorDashboard: React.FC = () => {
  /* ── Data state ── */
  const [loading, setLoading] = useState(true);
  const [store, setStore] = useState<Store | null>(null);
  const [storeProducts, setStoreProducts] = useState<StoreProduct[]>([]);
  const [reviews, setReviews] = useState<StoreReview[]>([]);
  const [priceHistories, setPriceHistories] = useState<Record<string, PriceHistoryEntry[]>>({});

  /* ── UI state ── */
  const [activeTab, setActiveTab] = useState<string>("products");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editPrice, setEditPrice] = useState<string>("");
  const [editSale, setEditSale] = useState<string>("");
  const [historyOpen, setHistoryOpen] = useState<string | null>(null);
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [showProductModal, setShowProductModal] = useState(false);
  const [modalMode, setModalMode] = useState<"search" | "create">("search");
  const [catalogSearch, setCatalogSearch] = useState("");
  const [catalogResults, setCatalogResults] = useState<CatalogProduct[]>([]);
  const [selectedCatalogItem, setSelectedCatalogItem] = useState<CatalogProduct | null>(null);
  const [catalogPrice, setCatalogPrice] = useState("");
  const [catalogSalePrice, setCatalogSalePrice] = useState("");
  const [catalogInStock, setCatalogInStock] = useState(true);
  const [newProduct, setNewProduct] = useState({
    name: "",
    brand: "",
    category: "Produce",
    unit_size: "",
    price: "",
    sale_price: "",
    in_stock: true,
  });
  const [modalError, setModalError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const priceInputRef = useRef<HTMLInputElement>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);

  /* ── Pagination state ── */
  const SALES_PER_PAGE = 9;
  const [productsPerPage, setProductsPerPage] = useState(10);
  const [productPage, setProductPage] = useState(1);
  const [salePage, setSalePage] = useState(1);

  /* ── Inventory search ── */
  const [inventorySearch, setInventorySearch] = useState("");

  /* ── Data fetching ── */
  const refreshProducts = useCallback(async () => {
    const res = await getStoreProducts();
    if (res.data) setStoreProducts(transformProducts(res.data));
  }, []);

  useEffect(() => {
    async function loadData() {
      const [storeRes, productsRes, reviewsRes] = await Promise.all([
        getVendorStore(),
        getStoreProducts(),
        getStoreReviews(),
      ]);
      if (storeRes.data) setStore(storeRes.data as Store);
      if (productsRes.data) setStoreProducts(transformProducts(productsRes.data));
      if (reviewsRes.data) setReviews(transformReviews(reviewsRes.data));
      setLoading(false);
    }
    loadData();
  }, []);

  useEffect(() => {
    if (editingId && priceInputRef.current) priceInputRef.current.focus();
  }, [editingId]);

  useEffect(() => {
    if (showProductModal && modalMode === "search" && searchInputRef.current) {
      searchInputRef.current.focus();
    }
  }, [showProductModal, modalMode]);

  /* ── Catalog search with debounce ── */
  useEffect(() => {
    if (modalMode !== "search" || catalogSearch.trim().length === 0) {
      setCatalogResults([]);
      return;
    }
    const timer = setTimeout(async () => {
      const res = await searchCatalog(catalogSearch);
      if (res.data) setCatalogResults(transformCatalog(res.data));
    }, 300);
    return () => clearTimeout(timer);
  }, [catalogSearch, modalMode]);

  /* ── Computed values ── */
  const inStockCount = storeProducts.filter((p) => p.in_stock).length;
  const outOfStockCount = storeProducts.filter((p) => !p.in_stock).length;
  const saleProducts = storeProducts.filter((p) => p.sale_price !== null);
  const onSaleCount = saleProducts.length;
  const stockPct = storeProducts.length ? Math.round((inStockCount / storeProducts.length) * 100) : 0;

  /* ── Filtered + paginated slices ── */
  const filteredProducts = React.useMemo(() => {
    const q = inventorySearch.trim().toLowerCase();
    if (!q) return storeProducts;
    return storeProducts.filter((p) =>
      p.name.toLowerCase().includes(q) ||
      (p.brand || "").toLowerCase().includes(q) ||
      p.category.toLowerCase().includes(q),
    );
  }, [storeProducts, inventorySearch]);

  const totalProductPages = Math.max(1, Math.ceil(filteredProducts.length / productsPerPage));
  const totalSalePages = Math.max(1, Math.ceil(saleProducts.length / SALES_PER_PAGE));
  const pagedProducts = filteredProducts.slice(
    (productPage - 1) * productsPerPage,
    productPage * productsPerPage,
  );
  const pagedSales = saleProducts.slice(
    (salePage - 1) * SALES_PER_PAGE,
    salePage * SALES_PER_PAGE,
  );

  useEffect(() => {
    setProductPage(1);
  }, [inventorySearch, productsPerPage]);

  useEffect(() => {
    if (productPage > totalProductPages) setProductPage(totalProductPages);
  }, [productPage, totalProductPages]);
  useEffect(() => {
    if (salePage > totalSalePages) setSalePage(totalSalePages);
  }, [salePage, totalSalePages]);
  const avgRating = reviews.length
    ? (reviews.reduce((s, r) => s + r.rating, 0) / reviews.length).toFixed(1)
    : "0.0";

  const categoryCounts: Record<string, number> = {};
  storeProducts.forEach((p) => {
    categoryCounts[p.category] = (categoryCounts[p.category] || 0) + 1;
  });

  const ratingCounts = [5, 4, 3, 2, 1].map((r) => ({
    rating: r,
    count: reviews.filter((rv) => rv.rating === r).length,
    pct: reviews.length ? (reviews.filter((rv) => rv.rating === r).length / reviews.length) * 100 : 0,
  }));

  const storeName = store?.name || "Your Store";
  const storeInitials = storeName.split(" ").map((w) => w[0]).join("").slice(0, 2).toUpperCase();

  /* ── Handlers ── */
  const startEdit = (p: StoreProduct) => {
    setEditingId(p.id);
    setEditPrice(p.price.toFixed(2));
    setEditSale(p.sale_price ? p.sale_price.toFixed(2) : "");
  };

  const cancelEdit = () => setEditingId(null);

  const saveEdit = async (id: string) => {
    const np = parseFloat(editPrice);
    if (isNaN(np)) return;
    const ns = editSale.trim() ? parseFloat(editSale) : null;
    if (ns !== null && isNaN(ns)) return;

    const res = await updateProductPrice(id, np, ns);
    if ('success' in res) {
      setStoreProducts((prev) =>
        prev.map((p) =>
          p.id === id
            ? { ...p, price: np, sale_price: ns, data_source: "vendor", updated_at: new Date().toISOString() }
            : p
        )
      );
    }
    setEditingId(null);
  };

  const toggleStock = async (id: string) => {
    const product = storeProducts.find((p) => p.id === id);
    if (!product) return;

    const res = await toggleProductStock(id, !product.in_stock);
    if ('success' in res) {
      setStoreProducts((prev) =>
        prev.map((p) =>
          p.id === id
            ? { ...p, in_stock: !p.in_stock, data_source: "vendor", updated_at: new Date().toISOString() }
            : p
        )
      );
    }
  };

  const confirmDelete = async (id: string) => {
    setDeletingId(id);
    const res = await deleteStoreProduct(id);
    if ('success' in res) {
      setStoreProducts((prev) => prev.filter((p) => p.id !== id));
      if (historyOpen === id) setHistoryOpen(null);
      if (editingId === id) setEditingId(null);
    }
    setDeletingId(null);
    setPendingDeleteId(null);
  };

  const handleEditKeydown = (e: React.KeyboardEvent, id: string) => {
    if (e.key === "Enter") saveEdit(id);
    if (e.key === "Escape") cancelEdit();
  };

  const loadPriceHistory = async (storeProductId: string) => {
    if (historyOpen === storeProductId) {
      setHistoryOpen(null);
      return;
    }
    if (!priceHistories[storeProductId]) {
      const res = await getProductPriceHistory(storeProductId);
      if (res.data && res.data.length > 0) {
        setPriceHistories((prev) => ({ ...prev, [storeProductId]: res.data as PriceHistoryEntry[] }));
      }
    }
    setHistoryOpen(storeProductId);
  };

  /* ── Modal helpers ── */
  const openModal = () => {
    setShowProductModal(true);
    setModalMode("search");
    setCatalogSearch("");
    setCatalogResults([]);
    setSelectedCatalogItem(null);
    setCatalogPrice("");
    setCatalogSalePrice("");
    setCatalogInStock(true);
    setNewProduct({
      name: "",
      brand: "",
      category: "Produce",
      unit_size: "",
      price: "",
      sale_price: "",
      in_stock: true,
    });
    setModalError(null);
    setSubmitting(false);
  };

  const closeModal = () => {
    setShowProductModal(false);
    setModalError(null);
    setSubmitting(false);
  };

  const addFromCatalog = async () => {
    if (submitting) return;
    setModalError(null);
    if (!selectedCatalogItem) return;

    const price = parseFloat(catalogPrice);
    if (isNaN(price) || price <= 0) {
      setModalError("Enter a valid price greater than 0.");
      return;
    }

    let salePrice: number | null = null;
    if (catalogSalePrice.trim()) {
      const sp = parseFloat(catalogSalePrice);
      if (isNaN(sp) || sp <= 0) {
        setModalError("Sale price must be greater than 0.");
        return;
      }
      if (sp >= price) {
        setModalError("Sale price must be lower than the regular price.");
        return;
      }
      salePrice = sp;
    }

    setSubmitting(true);
    const res = await addCatalogProduct(selectedCatalogItem.id, price, salePrice, catalogInStock);
    if (!('success' in res)) {
      setModalError(res.error ?? "Something went wrong. Please try again.");
      setSubmitting(false);
      return;
    }
    await refreshProducts();
    closeModal();
  };

  const createNewProduct = async () => {
    if (submitting) return;
    setModalError(null);

    const name = newProduct.name.trim();
    if (!name) {
      setModalError("Product name is required.");
      return;
    }

    const price = parseFloat(newProduct.price);
    if (isNaN(price) || price <= 0) {
      setModalError("Enter a valid price greater than 0.");
      return;
    }

    let salePrice: number | null = null;
    if (newProduct.sale_price.trim()) {
      const sp = parseFloat(newProduct.sale_price);
      if (isNaN(sp) || sp <= 0) {
        setModalError("Sale price must be greater than 0.");
        return;
      }
      if (sp >= price) {
        setModalError("Sale price must be lower than the regular price.");
        return;
      }
      salePrice = sp;
    }

    setSubmitting(true);
    const res = await createAndAddProduct({
      name,
      brand: newProduct.brand.trim(),
      category: newProduct.category,
      unitSize: newProduct.unit_size.trim(),
      price,
      salePrice,
      inStock: newProduct.in_stock,
    });

    if (!('success' in res)) {
      setModalError(res.error ?? "Something went wrong. Please try again.");
      setSubmitting(false);
      return;
    }
    await refreshProducts();
    closeModal();
  };

  /* ── Loading state ── */
  if (loading) {
    return (
      <div
        className="min-h-screen flex items-center justify-center text-stone-900"
        style={{ background: "#F7F5F0", fontFamily: "'DM Sans', sans-serif" }}
      >
        <style>{`
          @import url('https://fonts.googleapis.com/css2?family=DM+Sans:opsz,wght@9..40,300;9..40,400;9..40,500;9..40,600;9..40,700&family=Fraunces:opsz,wght@9..144,300;9..144,400;9..144,600;9..144,700&display=swap');
          .fraunces { font-family: 'Fraunces', serif; }
        `}</style>
        <div className="text-center">
          <div className="w-11 h-11 rounded-[14px] flex items-center justify-center text-white text-xl font-bold fraunces mx-auto mb-4"
            style={{ background: "linear-gradient(135deg,#2D6A4F,#52B788)", boxShadow: "0 4px 14px rgba(45,106,79,.25)" }}>N</div>
          <div className="text-sm text-stone-400">Loading your dashboard...</div>
        </div>
      </div>
    );
  }

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

        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2.5 bg-stone-200 rounded-xl px-3.5 py-1.5">
            <div
              className="w-8 h-8 rounded-[10px] flex items-center justify-center text-white font-bold text-sm"
              style={{ background: "linear-gradient(135deg,#D94F30,#F4A261)" }}
            >{storeInitials}</div>
            <div>
              <div className="text-sm font-semibold leading-tight">{storeName}</div>
              <div className="text-[11px] text-stone-400">{store?.address?.split(",")[0] || ""}</div>
            </div>
          </div>
          <ProfileDropdown showSettings={false} />
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
              Welcome back, {storeName}
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
          <button onClick={openModal} className="relative z-10 bg-white text-green-800 font-semibold text-[15px] px-7 py-3.5 rounded-xl cursor-pointer border-none hover:bg-green-50 transition-all hover:-translate-y-0.5 hover:shadow-lg">
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
          <div className="text-sm text-stone-400 mb-4">{reviews.length} reviews</div>
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
          <div className="flex justify-between items-center mb-4">
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
              <button onClick={openModal} className="bg-green-800 text-white border-none px-3.5 py-1.5 rounded-xl text-xs font-semibold cursor-pointer hover:bg-green-900 transition-colors">
                + Add Product
              </button>
            </div>
          </div>

          {/* Search + page size */}
          <div className="flex items-center gap-3 mb-4">
            <div className="relative flex-1">
              <svg className="absolute left-3.5 top-1/2 -translate-y-1/2 text-stone-400" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <input
                value={inventorySearch}
                onChange={(e) => setInventorySearch(e.target.value)}
                placeholder="Search by name, brand, or category..."
                className="w-full border border-stone-200 rounded-xl pl-10 pr-10 py-2.5 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
              />
              {inventorySearch && (
                <button
                  onClick={() => setInventorySearch("")}
                  aria-label="Clear search"
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 w-6 h-6 rounded-lg flex items-center justify-center text-stone-400 hover:bg-stone-200 hover:text-stone-600 transition-colors border-none bg-transparent cursor-pointer"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                  </svg>
                </button>
              )}
            </div>
            <div className="flex items-center gap-2 flex-shrink-0">
              <span className="text-xs text-stone-400">Show</span>
              <select
                value={productsPerPage}
                onChange={(e) => setProductsPerPage(parseInt(e.target.value, 10))}
                className="border border-stone-200 rounded-lg pl-3 pr-8 py-2 text-xs font-semibold bg-white text-stone-700 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all cursor-pointer appearance-none bg-no-repeat bg-right"
                style={{
                  backgroundImage:
                    "url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%238B8680' stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round'><polyline points='6 9 12 15 18 9'/></svg>\")",
                  backgroundPosition: "right 0.5rem center",
                }}
              >
                {[10, 25, 50, 100].map((n) => (
                  <option key={n} value={n}>{n}</option>
                ))}
              </select>
            </div>
          </div>

          {/* Table header */}
          <div className="grid grid-cols-[2.2fr_0.8fr_0.9fr_0.7fr_0.7fr_0.7fr_120px] items-center py-3 border-b-2 border-stone-100 text-[11px] font-semibold text-stone-300 uppercase tracking-wider">
            <span>Product</span>
            <span>Price</span>
            <span>Sale Price</span>
            <span>In Stock</span>
            <span>Source</span>
            <span>Updated</span>
            <span className="text-right">Actions</span>
          </div>

          {storeProducts.length === 0 ? (
            <div className="text-center py-12 text-stone-400 text-sm">
              No products yet. Click &ldquo;+ Add Product&rdquo; to get started.
            </div>
          ) : filteredProducts.length === 0 ? (
            <div className="text-center py-12 text-stone-400 text-sm">
              No products match &ldquo;{inventorySearch}&rdquo;.{" "}
              <button
                onClick={() => setInventorySearch("")}
                className="text-green-800 font-semibold hover:underline border-none bg-transparent cursor-pointer"
              >
                Clear search
              </button>
            </div>
          ) : pagedProducts.map((p) => {
            const isEditing = editingId === p.id;
            const hasHistory = !!priceHistories[p.id] && priceHistories[p.id].length > 0;
            const isHistoryOpen = historyOpen === p.id;

            return (
              <div key={p.id}>
                <div className="grid grid-cols-[2.2fr_0.8fr_0.9fr_0.7fr_0.7fr_0.7fr_120px] items-center py-3.5 border-b border-stone-100 last:border-b-0 text-sm">

                  <div className="flex items-center gap-2.5">
                    <div className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: catColor(p.category) }}/>
                    <div>
                      <div className="font-medium">{p.name}</div>
                      <div className="text-[11px] text-stone-400">
                        {p.brand || "Generic"} · {p.category}{p.unit_size ? ` · per ${p.unit_size}` : ""}
                      </div>
                    </div>
                  </div>

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

                  <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-[11px] font-semibold w-fit ${srcPillClass(p.data_source)}`}>
                    {srcLabel(p.data_source)}
                  </span>

                  <span className="text-xs text-stone-400">{fmtTime(p.updated_at)}</span>

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
                    ) : pendingDeleteId === p.id ? (
                      <>
                        <button
                          onClick={() => confirmDelete(p.id)}
                          disabled={deletingId === p.id}
                          className="text-red-600 font-semibold text-xs px-2.5 py-1.5 rounded-lg hover:bg-red-50 transition-colors border-none bg-transparent cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                          {deletingId === p.id ? "..." : "Delete"}
                        </button>
                        <button
                          onClick={() => setPendingDeleteId(null)}
                          disabled={deletingId === p.id}
                          className="text-stone-400 text-xs px-2 py-1.5 rounded-lg hover:bg-stone-100 transition-colors border-none bg-transparent cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
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
                        <button
                          onClick={() => loadPriceHistory(p.id)}
                          title="Price history"
                          className={`p-1.5 rounded-lg transition-colors border-none cursor-pointer
                            ${isHistoryOpen ? "bg-green-100 text-green-800" : "bg-transparent text-stone-400 hover:bg-stone-100 hover:text-stone-700"}`}
                        >
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
                          </svg>
                        </button>
                        <button
                          onClick={() => setPendingDeleteId(p.id)}
                          title="Remove from store"
                          className="text-stone-400 p-1.5 rounded-lg hover:bg-red-50 hover:text-red-600 transition-colors border-none bg-transparent cursor-pointer"
                        >
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <polyline points="3 6 5 6 21 6"/>
                            <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                            <path d="M10 11v6"/>
                            <path d="M14 11v6"/>
                            <path d="M9 6V4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2"/>
                          </svg>
                        </button>
                      </>
                    )}
                  </div>
                </div>

                {pendingDeleteId === p.id && (
                  <div className="bg-red-50 border border-red-200 rounded-xl px-4 py-3 my-1 mb-2 flex items-center gap-2 text-sm text-red-700">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="flex-shrink-0">
                      <circle cx="12" cy="12" r="10"/>
                      <line x1="12" y1="8" x2="12" y2="12"/>
                      <line x1="12" y1="16" x2="12.01" y2="16"/>
                    </svg>
                    <span>
                      Remove <span className="font-semibold">{p.name}</span> from your store? This will also delete its price history.
                    </span>
                  </div>
                )}

                {/* Price history panel */}
                {isHistoryOpen && hasHistory && (
                  <div className="bg-stone-50 rounded-xl p-4 my-1 mb-2 border border-stone-100">
                    <div className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-3">
                      Price History — {p.name}
                    </div>
                    <div className="flex gap-3">
                      {priceHistories[p.id].map((h, i) => (
                        <div key={i} className="flex-1 bg-white rounded-xl p-3.5 border border-stone-100">
                          <div className="text-[11px] text-stone-400 mb-1">{fmtDate(h.created_at)}</div>
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

          <div className="flex justify-between items-center mt-4 pt-4 border-t-2 border-stone-100">
            <span className="text-sm text-stone-400">
              {storeProducts.length === 0 ? (
                <>0 products</>
              ) : filteredProducts.length === 0 ? (
                <>0 matches for &ldquo;{inventorySearch}&rdquo;</>
              ) : inventorySearch.trim() ? (
                <>
                  {filteredProducts.length} match{filteredProducts.length === 1 ? "" : "es"} for &ldquo;{inventorySearch}&rdquo;
                  <span className="text-stone-300"> · of {storeProducts.length} products</span>
                </>
              ) : (
                <>
                  {storeProducts.length} products · {onSaleCount} on sale · {outOfStockCount} out of stock
                </>
              )}
            </span>
            {totalProductPages > 1 && (
              <PaginationControls
                page={productPage}
                totalPages={totalProductPages}
                onChange={setProductPage}
              />
            )}
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
            <>
              <div className="grid grid-cols-3 gap-3">
                {pagedSales.map((p) => {
                  const pctOff = Math.round(((p.price - p.sale_price!) / p.price) * 100);
                  return (
                    <div key={p.id} className="bg-stone-50 rounded-2xl p-[18px] border border-stone-100">
                      <div className="flex justify-between items-start mb-3">
                        <div>
                          <div className="font-semibold text-[15px]">{p.name}</div>
                          <div className="text-xs text-stone-400">{p.brand || "Generic"} · per {p.unit_size}</div>
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
              <div className="flex justify-between items-center mt-5 pt-4 border-t-2 border-stone-100">
                <span className="text-sm text-stone-400">
                  Showing {(salePage - 1) * SALES_PER_PAGE + 1}
                  –{Math.min(salePage * SALES_PER_PAGE, saleProducts.length)} of{" "}
                  {saleProducts.length} on sale
                </span>
                {totalSalePages > 1 && (
                  <PaginationControls
                    page={salePage}
                    totalPages={totalSalePages}
                    onChange={setSalePage}
                  />
                )}
              </div>
            </>
          ) : (
            <div className="text-center py-8 text-stone-400 text-sm">
              No active sales. Set a sale price on any product to create a deal.
            </div>
          )}
        </div>

      </div>
      )}

      {/* ═══════════════════════════════════════════ */}
      {/* ══ REVIEWS TAB ══ */}
      {/* ═══════════════════════════════════════════ */}
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
            <div className="text-sm text-stone-400 mt-2">Based on {reviews.length} reviews</div>
          </div>

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

          <div className="mt-8 pt-6 border-t border-stone-100 grid grid-cols-2 gap-4">
            <div className="bg-stone-50 rounded-xl px-4 py-3 border border-stone-100 text-center">
              <div className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1">5-Star</div>
              <div className="fraunces text-xl font-semibold text-green-800">
                {reviews.length ? Math.round((ratingCounts[0].count / reviews.length) * 100) : 0}%
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
              {reviews.length} total
            </span>
          </div>

          {reviews.length === 0 ? (
            <div className="text-center py-12 text-stone-400 text-sm">No reviews yet.</div>
          ) : (
            <div className="flex flex-col">
              {reviews.map((r, i) => (
                <div key={r.id} className={`py-5 ${i < reviews.length - 1 ? "border-b border-stone-100" : ""}`}>
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
          )}
        </div>

      </div>
      )}

      {/* ══ STORE INFO TAB ══ */}
      {activeTab === "store info" && (
      <div className="max-w-[1320px] mx-auto px-8 pb-12 grid grid-cols-5 gap-5 items-start">

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

          <div className="flex items-center gap-4 mb-6">
            <div
              className="w-14 h-14 rounded-2xl flex items-center justify-center text-white font-bold text-xl fraunces"
              style={{ background: "linear-gradient(135deg,#D94F30,#F4A261)" }}
            >{storeInitials}</div>
            <div>
              <div className="fraunces text-[22px] font-semibold tracking-tight">{storeName}</div>
              <div className="text-sm text-stone-400">{store?.address || "—"}</div>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4">
            {([
              {
                label: "Store Name",
                value: store?.name,
                icon: (
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                    <polyline points="9 22 9 12 15 12 15 22"/>
                  </svg>
                ),
              },
              {
                label: "Address",
                value: store?.address,
                icon: (
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
                    <circle cx="12" cy="10" r="3"/>
                  </svg>
                ),
              },
              {
                label: "Phone",
                value: store?.phone,
                icon: (
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>
                  </svg>
                ),
              },
              {
                label: "Website",
                value: store?.website_url,
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
                value: store?.zip_code,
                icon: (
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/>
                    <polyline points="22,6 12,13 2,6"/>
                  </svg>
                ),
              },
            ] as { label: string; value: string | null | undefined; icon: React.ReactNode }[]).map((f, i) => (
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
        <div className="bg-white rounded-2xl border border-black/[0.05] hover:shadow-xl hover:-translate-y-0.5 transition-all duration-300 overflow-hidden col-span-3">
          <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-widest text-stone-400 px-7 pt-7 pb-4">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
              <circle cx="12" cy="10" r="3"/>
            </svg>
            Location
          </div>
          <div style={{ height: "620px" }}>
            <VendorMap
              address={store?.address || ""}
              lat={store?.lat || 40.6892}
              lng={store?.lng || -73.9857}
            />
          </div>
        </div>

      </div>
      )}

      {/* ═══════════════════════════════════════════ */}
      {/* ══ PRODUCT MODAL ══ */}
      {/* ═══════════════════════════════════════════ */}
      {showProductModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center" onClick={closeModal}>
          <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" />

          <div
            className="relative bg-white rounded-2xl w-full max-w-[640px] max-h-[85vh] overflow-hidden flex flex-col"
            style={{ boxShadow: "0 25px 60px rgba(0,0,0,0.15)" }}
            onClick={(e) => e.stopPropagation()}
          >
            {/* Modal Header */}
            <div className="px-7 pt-6 pb-4 border-b border-stone-100">
              <div className="flex justify-between items-center mb-4">
                <div className="fraunces text-xl font-semibold tracking-tight">Add Product</div>
                <button
                  onClick={closeModal}
                  className="w-8 h-8 rounded-lg flex items-center justify-center text-stone-400 hover:bg-stone-100 hover:text-stone-600 transition-colors border-none bg-transparent cursor-pointer"
                >
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
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
                    ${modalMode === "search"
                      ? "bg-green-800 text-white shadow-sm"
                      : "bg-transparent text-stone-500 hover:text-stone-700"}`}
                >
                  Search Catalog
                </button>
                <button
                  onClick={() => { setModalMode("create"); setModalError(null); }}
                  className={`flex-1 py-2 rounded-full text-sm font-medium transition-all duration-200 border-none cursor-pointer
                    ${modalMode === "create"
                      ? "bg-green-800 text-white shadow-sm"
                      : "bg-transparent text-stone-500 hover:text-stone-700"}`}
                >
                  Create New
                </button>
              </div>
            </div>

            {/* Modal Body */}
            <div className="px-7 py-5 overflow-y-auto flex-1">

              {modalError && (
                <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm rounded-xl px-4 py-3 flex items-start gap-2">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="flex-shrink-0 mt-0.5">
                    <circle cx="12" cy="12" r="10"/>
                    <line x1="12" y1="8" x2="12" y2="12"/>
                    <line x1="12" y1="16" x2="12.01" y2="16"/>
                  </svg>
                  <span>{modalError}</span>
                </div>
              )}

              {modalMode === "search" && (
                <div>
                  <div className="relative mb-4">
                    <svg className="absolute left-3 top-1/2 -translate-y-1/2 text-stone-400" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
                    </svg>
                    <input
                      ref={searchInputRef}
                      value={catalogSearch}
                      onChange={(e) => { setCatalogSearch(e.target.value); setSelectedCatalogItem(null); setCatalogPrice(""); }}
                      placeholder="Search by name, brand, or category..."
                      className="w-full border border-stone-200 rounded-xl pl-10 pr-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
                    />
                  </div>

                  {catalogSearch.trim().length === 0 ? (
                    <div className="text-center py-10">
                      <div className="text-stone-300 mb-2">
                        <svg className="mx-auto" width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                          <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
                        </svg>
                      </div>
                      <div className="text-sm text-stone-400">Type to search the product catalog</div>
                      <div className="text-xs text-stone-300 mt-1">Or switch to &ldquo;Create New&rdquo; to add a custom product</div>
                    </div>
                  ) : catalogResults.length === 0 ? (
                    <div className="text-center py-10">
                      <div className="text-sm text-stone-400 mb-3">No products found for &ldquo;{catalogSearch}&rdquo;</div>
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
                                ${isSelected
                                  ? "bg-green-50 border-green-200"
                                  : "bg-stone-50 border-stone-100 hover:border-green-200 hover:bg-green-50/30"}`}
                              onClick={() => {
                                setSelectedCatalogItem(item);
                                setCatalogPrice("");
                                setCatalogSalePrice("");
                                setCatalogInStock(true);
                                setModalError(null);
                              }}
                            >
                              <div className="flex items-center gap-3">
                                <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: catColor(item.category) }}/>
                                <div>
                                  <div className="font-medium text-sm">{item.name}</div>
                                  <div className="text-[11px] text-stone-400">
                                    {item.brand || "Generic"} · {item.category}{item.unit_size ? ` · per ${item.unit_size}` : ""}
                                  </div>
                                </div>
                              </div>
                              {!isSelected && (
                                <div className="w-8 h-8 rounded-lg bg-green-100 text-green-800 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                    <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
                                  </svg>
                                </div>
                              )}
                            </div>
                            {isSelected && (
                              <div className="mt-2 ml-4 p-4 rounded-xl bg-stone-50 border border-stone-100 flex flex-col gap-3">
                                <div className="flex items-center gap-3 flex-wrap">
                                  <div className="flex flex-col gap-1">
                                    <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider">Price *</label>
                                    <div className="relative">
                                      <span className="absolute left-3 top-1/2 -translate-y-1/2 text-stone-400 text-sm">$</span>
                                      <input
                                        value={catalogPrice}
                                        onChange={(e) => setCatalogPrice(e.target.value)}
                                        placeholder="0.00"
                                        autoFocus
                                        inputMode="decimal"
                                        onKeyDown={(e) => { if (e.key === "Enter") addFromCatalog(); }}
                                        className="w-28 border border-stone-200 rounded-xl pl-7 pr-3 py-2 text-sm bg-white outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
                                      />
                                    </div>
                                  </div>
                                  <div className="flex flex-col gap-1">
                                    <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider">Sale Price</label>
                                    <div className="relative">
                                      <span className="absolute left-3 top-1/2 -translate-y-1/2 text-stone-400 text-sm">$</span>
                                      <input
                                        value={catalogSalePrice}
                                        onChange={(e) => setCatalogSalePrice(e.target.value)}
                                        placeholder="optional"
                                        inputMode="decimal"
                                        onKeyDown={(e) => { if (e.key === "Enter") addFromCatalog(); }}
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
                                    disabled={submitting || !catalogPrice || isNaN(parseFloat(catalogPrice)) || parseFloat(catalogPrice) <= 0}
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
                    <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1.5 block">Product Name *</label>
                    <input
                      value={newProduct.name}
                      onChange={(e) => setNewProduct({ ...newProduct, name: e.target.value })}
                      placeholder="e.g. Organic Whole Milk"
                      className="w-full border border-stone-200 rounded-xl px-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1.5 block">Brand</label>
                      <input
                        value={newProduct.brand}
                        onChange={(e) => setNewProduct({ ...newProduct, brand: e.target.value })}
                        placeholder="e.g. Horizon"
                        className="w-full border border-stone-200 rounded-xl px-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
                      />
                    </div>
                    <div>
                      <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1.5 block">Category *</label>
                      <select
                        value={newProduct.category}
                        onChange={(e) => setNewProduct({ ...newProduct, category: e.target.value })}
                        className="w-full border border-stone-200 rounded-xl px-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all cursor-pointer appearance-none"
                      >
                        {Object.keys(CAT_COLORS).map((cat) => (
                          <option key={cat} value={cat}>{cat}</option>
                        ))}
                      </select>
                    </div>
                  </div>

                  <div>
                    <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1.5 block">Unit Size</label>
                    <input
                      value={newProduct.unit_size}
                      onChange={(e) => setNewProduct({ ...newProduct, unit_size: e.target.value })}
                      placeholder="e.g. lb, gal, dozen"
                      className="w-full border border-stone-200 rounded-xl px-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1.5 block">Price *</label>
                      <div className="relative">
                        <span className="absolute left-4 top-1/2 -translate-y-1/2 text-stone-400 text-sm">$</span>
                        <input
                          value={newProduct.price}
                          onChange={(e) => setNewProduct({ ...newProduct, price: e.target.value })}
                          placeholder="0.00"
                          inputMode="decimal"
                          onKeyDown={(e) => { if (e.key === "Enter") createNewProduct(); }}
                          className="w-full border border-stone-200 rounded-xl pl-8 pr-4 py-3 text-sm bg-stone-50 outline-none focus:border-green-700 focus:ring-2 focus:ring-green-700/10 transition-all"
                        />
                      </div>
                    </div>
                    <div>
                      <label className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1.5 block">Sale Price</label>
                      <div className="relative">
                        <span className="absolute left-4 top-1/2 -translate-y-1/2 text-stone-400 text-sm">$</span>
                        <input
                          value={newProduct.sale_price}
                          onChange={(e) => setNewProduct({ ...newProduct, sale_price: e.target.value })}
                          placeholder="optional"
                          inputMode="decimal"
                          onKeyDown={(e) => { if (e.key === "Enter") createNewProduct(); }}
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
                      <div className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-2">Preview</div>
                      <div className="flex items-center gap-2.5">
                        <div className="w-2.5 h-2.5 rounded-full" style={{ background: catColor(newProduct.category) }}/>
                        <div>
                          <div className="font-medium text-sm">{newProduct.name}</div>
                          <div className="text-[11px] text-stone-400">
                            {newProduct.brand || "Generic"} · {newProduct.category}
                            {newProduct.unit_size && ` · per ${newProduct.unit_size}`}
                            {newProduct.price && ` · $${parseFloat(newProduct.price).toFixed(2)}`}
                            {newProduct.sale_price && ` · sale $${parseFloat(newProduct.sale_price).toFixed(2)}`}
                            {!newProduct.in_stock && ` · out of stock`}
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
      )}

    </div>
  );
};

export default VendorDashboard;
