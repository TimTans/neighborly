import * as XLSX from "xlsx";

const TEMPLATE_HEADERS = [
  "Product Name",
  "Brand",
  "Category",
  "Unit Size",
  "Price",
  "Sale Price",
  "In Stock",
  "Catalog Product ID",
];

const EXAMPLE_ROWS = [
  ["Organic Whole Milk", "Horizon", "Milk", "1 gal", "5.99", "", "Yes", ""],
  ["Sourdough Bread", "Acme", "Bread", "1 loaf", "4.49", "3.99", "Yes", ""],
  ["Roma Tomatoes", "", "Fresh Fruit", "lb", "1.99", "", "Yes", ""],
];

function csvEscape(value: string): string {
  if (/[",\r\n]/.test(value)) return `"${value.replace(/"/g, '""')}"`;
  return value;
}

export function downloadImportTemplate(format: "csv" | "xlsx") {
  const data = [TEMPLATE_HEADERS, ...EXAMPLE_ROWS];

  if (format === "xlsx") {
    const worksheet = XLSX.utils.aoa_to_sheet(data);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, "Inventory");
    XLSX.writeFile(workbook, "inventory-import-template.xlsx");
    return;
  }

  const csv = data.map((row) => row.map((cell) => csvEscape(String(cell))).join(",")).join("\r\n");
  const blob = new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "inventory-import-template.csv";
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
