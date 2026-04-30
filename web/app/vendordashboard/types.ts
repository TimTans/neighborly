export interface StoreProduct {
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

export interface PriceHistoryEntry {
  price: number;
  sale_price: number | null;
  created_at: string;
}

export interface StoreReview {
  id: string;
  rating: number;
  comment: string | null;
  user_name: string;
  created_at: string;
}

export interface Store {
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

export interface CatalogProduct {
  id: string;
  name: string;
  brand: string | null;
  category: string;
  unit_size: string;
}

export type ImportRowStatus = "pending" | "update" | "add" | "create" | "unchanged" | "error";

export interface ImportRow {
  name: string;
  brand: string;
  category: string;
  unitSize: string;
  price: string;
  salePrice: string;
  inStock: string;
  catalogProductId: string;
  parsedPrice: number | null;
  parsedSalePrice: number | null;
  parsedInStock: boolean;
  status: ImportRowStatus;
  error: string | null;
  matchedProductId: string | null;
  storeProductId: string | null;
  currentPrice: number | null;
  currentSalePrice: number | null;
  currentInStock: boolean | null;
}

export interface ImportResult {
  updated: number;
  added: number;
  created: number;
  failed: Array<{ rowIndex: number; reason: string }>;
}
