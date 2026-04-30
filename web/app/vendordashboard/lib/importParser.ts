import * as XLSX from "xlsx";
import type { ImportRow } from "../types";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
type ImportField = keyof Pick<ImportRow, "name" | "brand" | "category" | "unitSize" | "price" | "salePrice" | "inStock" | "catalogProductId">;

const COLUMN_MAP: Record<string, ImportField> = {
  "product name": "name",
  brand: "brand",
  category: "category",
  "unit size": "unitSize",
  price: "price",
  "sale price": "salePrice",
  "in stock": "inStock",
  "catalog product id": "catalogProductId",
};

function parseInStock(value: string): boolean {
  const normalized = value.trim().toLowerCase();
  return !(normalized === "false" || normalized === "no" || normalized === "0");
}

function parseMoney(value: string): number {
  return parseFloat(value.replace(/^\$/, "").trim());
}

function formatCategoryError(categories: string[]): string {
  if (categories.length === 0) return "Category must match an existing product category";
  const examples = categories.slice(0, 12).join(", ");
  const suffix = categories.length > 12 ? `, and ${categories.length - 12} more` : "";
  return `Category must match an existing product category. Examples: ${examples}${suffix}`;
}

export async function parseImportFile(file: File, categories: string[] = []): Promise<ImportRow[]> {
  const buffer = await file.arrayBuffer();
  const workbook = XLSX.read(buffer, { type: "array" });
  const firstSheetName = workbook.SheetNames[0];
  if (!firstSheetName) return [];

  const worksheet = workbook.Sheets[firstSheetName];
  const rawRows = XLSX.utils.sheet_to_json<Record<string, unknown>>(worksheet, {
    defval: "",
    raw: false,
  });

  if (rawRows.length === 0) return [];

  const headerToField: Record<string, ImportField> = {};
  for (const column of Object.keys(rawRows[0])) {
    const normalized = column.trim().toLowerCase();
    const field = COLUMN_MAP[normalized];
    if (field) headerToField[column] = field;
  }

  const requiredFields: ImportField[] = ["name", "category", "price"];
  const mappedFields = new Set(Object.values(headerToField));
  const missingFields = requiredFields.filter((field) => !mappedFields.has(field));
  if (missingFields.length > 0) {
    const labels: Record<string, string> = {
      name: "Product Name",
      category: "Category",
      price: "Price",
    };
    throw new Error(`Missing required columns: ${missingFields.map((field) => labels[field]).join(", ")}. Download the template for the expected format.`);
  }

  const normalizedCategories = new Map(categories.map((category) => [category.trim().toLowerCase(), category.trim()]));

  return rawRows.map((raw) => {
    const mapped = {
      name: "",
      brand: "",
      category: "",
      unitSize: "",
      price: "",
      salePrice: "",
      inStock: "",
      catalogProductId: "",
    };

    for (const [column, field] of Object.entries(headerToField)) {
      mapped[field] = String(raw[column] ?? "").trim();
    }

    const errors: string[] = [];

    if (!mapped.name) errors.push("Product Name is required");

    if (!mapped.category) {
      errors.push("Category is required");
    } else if (normalizedCategories.size > 0 && !normalizedCategories.has(mapped.category.toLowerCase())) {
      errors.push(formatCategoryError(categories));
    } else if (normalizedCategories.has(mapped.category.toLowerCase())) {
      mapped.category = normalizedCategories.get(mapped.category.toLowerCase()) ?? mapped.category;
    }

    const parsedPrice = parseMoney(mapped.price);
    if (!mapped.price || isNaN(parsedPrice) || parsedPrice <= 0) {
      errors.push("Price must be a positive number");
    }

    let parsedSalePrice: number | null = null;
    if (mapped.salePrice) {
      parsedSalePrice = parseMoney(mapped.salePrice);
      if (isNaN(parsedSalePrice) || parsedSalePrice <= 0) {
        errors.push("Sale Price must be a positive number if provided");
        parsedSalePrice = null;
      }
    }

    if (mapped.catalogProductId && !UUID_RE.test(mapped.catalogProductId)) {
      errors.push("Catalog Product ID must be a valid UUID");
    }

    return {
      name: mapped.name,
      brand: mapped.brand,
      category: mapped.category,
      unitSize: mapped.unitSize,
      price: mapped.price,
      salePrice: mapped.salePrice,
      inStock: mapped.inStock,
      catalogProductId: mapped.catalogProductId,
      parsedPrice: errors.length === 0 ? parsedPrice : null,
      parsedSalePrice,
      parsedInStock: parseInStock(mapped.inStock),
      status: errors.length > 0 ? "error" : "pending",
      error: errors[0] ?? null,
      matchedProductId: null,
      storeProductId: null,
      currentPrice: null,
      currentSalePrice: null,
      currentInStock: null,
    };
  });
}
