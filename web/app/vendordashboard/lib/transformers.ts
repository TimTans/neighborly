import type { CatalogProduct, StoreProduct, StoreReview } from "../types";

/* eslint-disable @typescript-eslint/no-explicit-any */
export const transformProducts = (raw: any[]): StoreProduct[] =>
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

export const transformReviews = (raw: any[]): StoreReview[] =>
  raw.map((r) => ({
    id: r.id,
    rating: r.rating,
    comment: r.comment,
    user_name: r.users
      ? `${r.users.first_name || ""} ${r.users.last_name?.charAt(0) || ""}.`.trim()
      : "Anonymous",
    created_at: r.created_at,
  }));

export const transformCatalog = (raw: any[]): CatalogProduct[] =>
  raw.map((p) => ({
    id: p.id,
    name: p.name,
    brand: p.brand,
    category: p.product_categories?.name || "Other",
    unit_size: p.unit_size || "",
  }));
/* eslint-enable @typescript-eslint/no-explicit-any */
