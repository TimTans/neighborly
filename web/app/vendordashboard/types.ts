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
