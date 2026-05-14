"use client";
import React, { useState, useRef, useEffect, useCallback } from "react";
import * as XLSX from "xlsx";
import ProfileDropdown from "@/components/ProfileDropdown";
import ImportModal from "./components/ImportModal";
import PaginationControls from "./components/PaginationControls";
import PriceHistoryChart from "./components/PriceHistoryChart";
import ProductModal, { type NewProductFormValues } from "./components/ProductModal";
import ReviewsTab from "./components/ReviewsTab";
import StoreInfoTab from "./components/StoreInfoTab";
import StoreInfoModal, { type StoreInfoFormValues } from "./components/StoreInfoModal";
import { SALES_PER_PAGE } from "./constants";
import {
  catColor,
  csvEscape,
  fmtTime,
  srcLabel,
  srcPillClass,
} from "./lib/formatters";
import { transformCatalog, transformProducts, transformReviews } from "./lib/transformers";
import type { CatalogProduct, PriceHistoryEntry, Store, StoreProduct, StoreReview } from "./types";
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
  updateVendorStoreInfo,
} from "./actions";

const VendorDashboard: React.FC = () => {
  /* ── Data state ── */
  const [loading, setLoading] = useState(true);
  const [store, setStore] = useState<Store | null>(null);
  const [storeProducts, setStoreProducts] = useState<StoreProduct[]>([]);
  const [reviews, setReviews] = useState<StoreReview[]>([]);
  const [priceHistories, setPriceHistories] = useState<Record<string, PriceHistoryEntry[]>>({});
  const [priceHistoryLoading, setPriceHistoryLoading] = useState<Record<string, boolean>>({});
  const [priceHistoryErrors, setPriceHistoryErrors] = useState<Record<string, string | null>>({});

  /* ── UI state ── */
  const [activeTab, setActiveTab] = useState<string>("products");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editPrice, setEditPrice] = useState<string>("");
  const [editSale, setEditSale] = useState<string>("");
  const [editError, setEditError] = useState<string | null>(null);
  const [historyOpen, setHistoryOpen] = useState<string | null>(null);
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [showStoreModal, setShowStoreModal] = useState(false);
  const [storeForm, setStoreForm] = useState<StoreInfoFormValues>({
    name: "",
    chain: "",
    address: "",
    zip_code: "",
    phone: "",
    website_url: "",
  });
  const [storeFormError, setStoreFormError] = useState<string | null>(null);
  const [savingStore, setSavingStore] = useState(false);
  const [showProductModal, setShowProductModal] = useState(false);
  const [showImportModal, setShowImportModal] = useState(false);
  const [exportMenuOpen, setExportMenuOpen] = useState(false);
  const [modalMode, setModalMode] = useState<"search" | "create">("search");
  const [catalogSearch, setCatalogSearch] = useState("");
  const [catalogResults, setCatalogResults] = useState<CatalogProduct[]>([]);
  const [catalogLoading, setCatalogLoading] = useState(false);
  const [selectedCatalogItem, setSelectedCatalogItem] = useState<CatalogProduct | null>(null);
  const [catalogPrice, setCatalogPrice] = useState("");
  const [catalogSalePrice, setCatalogSalePrice] = useState("");
  const [catalogInStock, setCatalogInStock] = useState(true);
  const [newProduct, setNewProduct] = useState<NewProductFormValues>({
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
  const exportMenuRef = useRef<HTMLDivElement>(null);

  /* ── Pagination state ── */
  const [productsPerPage, setProductsPerPage] = useState(10);
  const [productPage, setProductPage] = useState(1);
  const [salePage, setSalePage] = useState(1);

  /* ── Inventory search ── */
  const [inventorySearch, setInventorySearch] = useState("");

  /* ── Data fetching ── */
  const refreshStore = useCallback(async () => {
    const res = await getVendorStore();
    if (res.data) setStore(res.data as Store);
  }, []);

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

  useEffect(() => {
    if (!exportMenuOpen) return;

    const closeOnOutsideClick = (event: MouseEvent) => {
      if (!exportMenuRef.current?.contains(event.target as Node)) {
        setExportMenuOpen(false);
      }
    };

    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setExportMenuOpen(false);
    };

    document.addEventListener("mousedown", closeOnOutsideClick);
    document.addEventListener("keydown", closeOnEscape);

    return () => {
      document.removeEventListener("mousedown", closeOnOutsideClick);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [exportMenuOpen]);

  /* ── Catalog search with debounce ── */
  useEffect(() => {
    if (modalMode !== "search" || catalogSearch.trim().length === 0) {
      setCatalogLoading(false);
      setCatalogResults([]);
      return;
    }

    let cancelled = false;
    setCatalogLoading(true);
    const query = catalogSearch.trim();
    const timer = setTimeout(async () => {
      try {
        const res = await searchCatalog(query);
        if (!cancelled) setCatalogResults(res.data ? transformCatalog(res.data) : []);
      } finally {
        if (!cancelled) setCatalogLoading(false);
      }
    }, 300);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
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
    setEditError(null);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditError(null);
  };

  const saveEdit = async (id: string) => {
    const np = parseFloat(editPrice);
    if (isNaN(np) || np <= 0) {
      setEditError("Enter a valid regular price greater than 0.");
      return;
    }
    const ns = editSale.trim() ? parseFloat(editSale) : null;
    if (ns !== null && (isNaN(ns) || ns <= 0)) {
      setEditError("Sale price must be greater than 0.");
      return;
    }
    if (ns !== null && ns >= np) {
      setEditError("Sale price must be lower than the regular price.");
      return;
    }

    const res = await updateProductPrice(id, np, ns);
    if ('success' in res) {
      const updatedAt = res.data?.updated_at ?? res.history?.created_at ?? new Date().toISOString();
      const savedPrice = Number(res.data?.price ?? np);
      const savedSalePrice =
        res.data?.sale_price === null || res.data?.sale_price === undefined
          ? null
          : Number(res.data.sale_price);
      setStoreProducts((prev) =>
        prev.map((p) =>
          p.id === id
            ? { ...p, price: savedPrice, sale_price: savedSalePrice, data_source: "vendor", updated_at: updatedAt }
            : p
        )
      );
      setPriceHistories((prev) => {
        const existing = prev[id];
        if (!existing) return prev;
        const historyEntry = res.history ?? {
          price: savedPrice,
          sale_price: savedSalePrice,
          created_at: updatedAt,
        };
        return {
          ...prev,
          [id]: [...existing, historyEntry as PriceHistoryEntry],
        };
      });
      setEditError(null);
      setEditingId(null);
      return;
    }
    setEditError(res.error ?? "Unable to save this price.");
  };

  const updateEditPrice = (value: string) => {
    setEditPrice(value);
    if (editError) setEditError(null);
  };

  const updateEditSale = (value: string) => {
    setEditSale(value);
    if (editError) setEditError(null);
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

  const fetchPriceHistory = useCallback(async (storeProductId: string) => {
    setPriceHistoryLoading((prev) => ({ ...prev, [storeProductId]: true }));
    setPriceHistoryErrors((prev) => ({ ...prev, [storeProductId]: null }));

    const res = await getProductPriceHistory(storeProductId);
    if ("error" in res && res.error) {
      setPriceHistoryErrors((prev) => ({ ...prev, [storeProductId]: res.error || "Unable to load price history." }));
    } else {
      setPriceHistories((prev) => ({ ...prev, [storeProductId]: (res.data || []) as PriceHistoryEntry[] }));
    }
    setPriceHistoryLoading((prev) => ({ ...prev, [storeProductId]: false }));
  }, []);

  const loadPriceHistory = async (storeProductId: string) => {
    if (historyOpen === storeProductId) {
      setHistoryOpen(null);
      return;
    }
    setHistoryOpen(storeProductId);
    if (priceHistories[storeProductId]) return;

    await fetchPriceHistory(storeProductId);
  };

  const exportInventory = (format: "csv" | "xlsx") => {
    if (!storeProducts.length) return;

    const headers = [
      "Product Name",
      "Brand",
      "Category",
      "Unit Size",
      "Price",
      "Sale Price",
      "In Stock",
      "Source",
      "Last Updated (ISO)",
      "Store Product ID",
      "Catalog Product ID",
    ];

    const rows = storeProducts.map((p) => [
      p.name,
      p.brand || "Generic",
      p.category,
      p.unit_size,
      p.price.toFixed(2),
      p.sale_price !== null ? p.sale_price.toFixed(2) : "",
      p.in_stock ? "Yes" : "No",
      srcLabel(p.data_source),
      p.updated_at,
      p.id,
      p.product_id,
    ]);

    const dateStamp = new Date().toISOString().slice(0, 10);
    const safeStoreName =
      (store?.name || "store")
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/^-+|-+$/g, "") || "store";

    if (format === "xlsx") {
      const worksheet = XLSX.utils.aoa_to_sheet([headers, ...rows]);
      const workbook = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(workbook, worksheet, "Inventory");
      XLSX.writeFile(workbook, `${safeStoreName}-inventory-${dateStamp}.xlsx`);
      return;
    }

    const csv = [headers, ...rows]
      .map((row) => row.map(csvEscape).join(","))
      .join("\r\n");
    const fileName = `${safeStoreName}-inventory-${dateStamp}.csv`;

    const blob = new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const handleImportComplete = useCallback(async () => {
    await refreshProducts();
    setPriceHistories({});
    setPriceHistoryErrors({});
    setPriceHistoryLoading({});
    if (historyOpen) {
      await fetchPriceHistory(historyOpen);
    }
  }, [fetchPriceHistory, historyOpen, refreshProducts]);

  const openStoreInfoModal = () => {
    setStoreForm({
      name: store?.name || "",
      chain: store?.chain || "",
      address: store?.address || "",
      zip_code: store?.zip_code || "",
      phone: store?.phone || "",
      website_url: store?.website_url || "",
    });
    setStoreFormError(null);
    setSavingStore(false);
    setShowStoreModal(true);
  };

  const closeStoreInfoModal = () => {
    if (savingStore) return;
    setShowStoreModal(false);
    setStoreFormError(null);
  };

  const saveStoreInfo = async () => {
    if (savingStore) return;

    const name = storeForm.name.trim();
    if (!name) {
      setStoreFormError("Store name is required.");
      return;
    }

    setSavingStore(true);
    setStoreFormError(null);

    const res = await updateVendorStoreInfo({
      name,
      chain: storeForm.chain || null,
      address: storeForm.address || null,
      zipCode: storeForm.zip_code || null,
      phone: storeForm.phone || null,
      websiteUrl: storeForm.website_url || null,
    });

    if (!("success" in res)) {
      setStoreFormError(res.error ?? "Unable to update store info.");
      setSavingStore(false);
      return;
    }

    if (res.data) {
      setStore(res.data as Store);
    } else {
      await refreshStore();
    }
    setSavingStore(false);
    setShowStoreModal(false);
  };

  /* ── Modal helpers ── */
  const openModal = () => {
    setShowProductModal(true);
    setModalMode("search");
    setCatalogSearch("");
    setCatalogResults([]);
    setCatalogLoading(false);
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
              <div ref={exportMenuRef} className="relative">
                <button
                  type="button"
                  onClick={() => setExportMenuOpen((open) => !open)}
                  disabled={!storeProducts.length}
                  aria-haspopup="menu"
                  aria-expanded={exportMenuOpen}
                  className="inline-flex items-center gap-1.5 border border-green-800 text-green-800 bg-transparent px-3.5 py-1.5 rounded-xl text-xs font-semibold cursor-pointer hover:bg-green-50 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                    <polyline points="7 10 12 15 17 10"/>
                    <line x1="12" y1="15" x2="12" y2="3"/>
                  </svg>
                  Export
                  <svg className={`transition-transform ${exportMenuOpen ? "rotate-180" : ""}`} width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <polyline points="6 9 12 15 18 9"/>
                  </svg>
                </button>
                {exportMenuOpen && (
                  <div
                    role="menu"
                    className="absolute right-0 top-9 z-30 w-36 overflow-hidden rounded-xl border border-stone-100 bg-white shadow-xl"
                  >
                    <button
                      type="button"
                      role="menuitem"
                      onClick={() => {
                        exportInventory("csv");
                        setExportMenuOpen(false);
                      }}
                      className="flex w-full items-center gap-2 border-none bg-white px-3.5 py-2 text-left text-xs font-semibold text-stone-700 cursor-pointer hover:bg-green-50 hover:text-green-800 transition-colors"
                    >
                      CSV
                    </button>
                    <button
                      type="button"
                      role="menuitem"
                      onClick={() => {
                        exportInventory("xlsx");
                        setExportMenuOpen(false);
                      }}
                      className="flex w-full items-center gap-2 border-none bg-white px-3.5 py-2 text-left text-xs font-semibold text-stone-700 cursor-pointer hover:bg-green-50 hover:text-green-800 transition-colors"
                    >
                      XLSX
                    </button>
                  </div>
                )}
              </div>
              <button
                onClick={() => setShowImportModal(true)}
                className="border border-green-800 text-green-800 bg-transparent px-3.5 py-1.5 rounded-xl text-xs font-semibold cursor-pointer hover:bg-green-50 transition-colors"
              >
                Import
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
            const historyEntries = priceHistories[p.id] ?? [];
            const historyError = priceHistoryErrors[p.id] ?? null;
            const isHistoryLoading = !!priceHistoryLoading[p.id];
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
                          onChange={(e) => updateEditPrice(e.target.value)}
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
                          onChange={(e) => updateEditSale(e.target.value)}
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

                {isEditing && editError && (
                  <div className="bg-red-50 border border-red-200 rounded-xl px-4 py-3 my-1 mb-2 flex items-center gap-2 text-sm text-red-700">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="flex-shrink-0">
                      <circle cx="12" cy="12" r="10"/>
                      <line x1="12" y1="8" x2="12" y2="12"/>
                      <line x1="12" y1="16" x2="12.01" y2="16"/>
                    </svg>
                    <span>{editError}</span>
                  </div>
                )}

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

                {isHistoryOpen && (
                  <PriceHistoryChart
                    productName={p.name}
                    history={historyEntries}
                    loading={isHistoryLoading}
                    error={historyError}
                  />
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

        {/* On Sale */}
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

    
      {/* REVIEWS TAB */}
  
      {activeTab === "reviews" && (
        <ReviewsTab avgRating={avgRating} reviews={reviews} ratingCounts={ratingCounts} />
      )}

      {/* STORE INFO TAB */}
      {activeTab === "store info" && (
        <StoreInfoTab
          store={store}
          storeInitials={storeInitials}
          storeName={storeName}
          onEditStoreInfo={openStoreInfoModal}
        />
      )}

      {showStoreModal && (
        <StoreInfoModal
          storeForm={storeForm}
          storeFormError={storeFormError}
          savingStore={savingStore}
          onClose={closeStoreInfoModal}
          onSave={saveStoreInfo}
          onChangeField={(field, value) =>
            setStoreForm((prev) => ({ ...prev, [field]: value }))
          }
        />
      )}

      {/* PRODUCT MODAL */}
      {showProductModal && (
        <ProductModal
          closeModal={closeModal}
          modalMode={modalMode}
          setModalMode={setModalMode}
          modalError={modalError}
          setModalError={setModalError}
          submitting={submitting}
          searchInputRef={searchInputRef}
          catalogSearch={catalogSearch}
          setCatalogSearch={setCatalogSearch}
          catalogResults={catalogResults}
          catalogLoading={catalogLoading}
          selectedCatalogItem={selectedCatalogItem}
          setSelectedCatalogItem={setSelectedCatalogItem}
          catalogPrice={catalogPrice}
          setCatalogPrice={setCatalogPrice}
          catalogSalePrice={catalogSalePrice}
          setCatalogSalePrice={setCatalogSalePrice}
          catalogInStock={catalogInStock}
          setCatalogInStock={setCatalogInStock}
          addFromCatalog={addFromCatalog}
          newProduct={newProduct}
          setNewProduct={setNewProduct}
          createNewProduct={createNewProduct}
          onBulkImport={() => {
            closeModal();
            setShowImportModal(true);
          }}
        />
      )}

      {/* IMPORT MODAL */}
      {showImportModal && (
        <ImportModal
          onClose={() => setShowImportModal(false)}
          onImportComplete={handleImportComplete}
        />
      )}

    </div>
  );
};

export default VendorDashboard;
