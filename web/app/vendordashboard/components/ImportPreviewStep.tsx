import { useMemo, useState } from "react";
import type { ImportRow, ImportRowStatus } from "../types";

interface ImportPreviewStepProps {
  rows: ImportRow[];
  createNew: boolean;
  onToggleCreateNew: (enabled: boolean) => void;
  onImport: () => void;
  onCancel: () => void;
  loading: boolean;
}

type FilterTab = "all" | Extract<ImportRowStatus, "update" | "add" | "create" | "unchanged" | "error">;

const STATUS_LABELS: Record<ImportRowStatus, string> = {
  pending: "Pending",
  update: "Update",
  add: "Add",
  create: "Create",
  unchanged: "Unchanged",
  error: "Error",
};

const STATUS_COLORS: Record<ImportRowStatus, string> = {
  pending: "bg-stone-100 text-stone-600",
  update: "bg-blue-100 text-blue-700",
  add: "bg-green-100 text-green-800",
  create: "bg-yellow-100 text-yellow-800",
  unchanged: "bg-stone-100 text-stone-600",
  error: "bg-red-100 text-red-700",
};

function money(value: number | null): string {
  return value === null ? "-" : `$${value.toFixed(2)}`;
}

export default function ImportPreviewStep({ rows, createNew, onToggleCreateNew, onImport, onCancel, loading }: ImportPreviewStepProps) {
  const [filter, setFilter] = useState<FilterTab>("all");

  const counts = useMemo(() => ({
    update: rows.filter((row) => row.status === "update").length,
    add: rows.filter((row) => row.status === "add").length,
    create: rows.filter((row) => row.status === "create").length,
    unchanged: rows.filter((row) => row.status === "unchanged").length,
    error: rows.filter((row) => row.status === "error").length,
  }), [rows]);

  const importCount = counts.update + counts.add + counts.create;
  const filteredRows = filter === "all" ? rows : rows.filter((row) => row.status === filter);
  const tabs: Array<{ key: FilterTab; label: string; count: number }> = [
    { key: "all", label: "All", count: rows.length },
    { key: "update", label: "Updates", count: counts.update },
    { key: "add", label: "Adds", count: counts.add },
    { key: "create", label: "Creates", count: counts.create },
    { key: "unchanged", label: "Unchanged", count: counts.unchanged },
    { key: "error", label: "Errors", count: counts.error },
  ];

  return (
    <div className="flex flex-col gap-4">
      <h2 className="fraunces text-xl font-semibold tracking-tight text-stone-900">Preview Import</h2>

      {rows.length >= 1000 && (
        <div className="bg-yellow-50 border border-yellow-200 rounded-xl px-4 py-2.5 text-sm text-yellow-800">
          Large import detected ({rows.length} rows). This may take a moment to process.
        </div>
      )}

      <div className="flex gap-3 flex-wrap">
        {(["update", "add", "create", "unchanged", "error"] as const).map((status) => (
          <span key={status} className={`text-xs font-semibold px-2.5 py-1 rounded-full ${STATUS_COLORS[status]}`}>
            {counts[status]} {STATUS_LABELS[status]}{counts[status] === 1 ? "" : "s"}
          </span>
        ))}
      </div>

      <label className="flex items-center gap-3 cursor-pointer select-none">
        <button
          type="button"
          onClick={() => onToggleCreateNew(!createNew)}
          className={`relative w-10 h-5 rounded-full transition-colors border-none cursor-pointer ${createNew ? "bg-green-800" : "bg-stone-300"}`}
          aria-pressed={createNew}
        >
          <span className={`absolute left-0.5 top-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform ${createNew ? "translate-x-5" : "translate-x-0"}`} />
        </button>
        <span className="text-sm text-stone-600">Create new catalog products for unmatched rows</span>
      </label>

      <div className="flex gap-1 border-b border-stone-200 overflow-x-auto">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => setFilter(tab.key)}
            className={`px-3 py-1.5 text-xs font-medium rounded-t transition-colors border-none bg-transparent cursor-pointer ${
              filter === tab.key ? "border-b-2 border-green-800 text-green-800" : "text-stone-500 hover:text-stone-700"
            }`}
          >
            {tab.label} {tab.count > 0 && <span className="text-stone-400">({tab.count})</span>}
          </button>
        ))}
      </div>

      <div className="overflow-auto max-h-80 rounded-xl border border-stone-200">
        <table className="w-full text-xs text-left">
          <thead className="bg-stone-50 text-stone-500 sticky top-0">
            <tr>
              <th className="px-3 py-2 font-medium">Status</th>
              <th className="px-3 py-2 font-medium">Product</th>
              <th className="px-3 py-2 font-medium">Category</th>
              <th className="px-3 py-2 font-medium">Unit</th>
              <th className="px-3 py-2 font-medium">Price</th>
              <th className="px-3 py-2 font-medium">Sale</th>
              <th className="px-3 py-2 font-medium">Stock</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-stone-100">
            {filteredRows.map((row, index) => (
              <tr key={`${row.name}-${index}`} className={row.status === "error" ? "bg-red-50" : "bg-white"}>
                <td className="px-3 py-2">
                  <span className={`px-2 py-0.5 rounded-full font-semibold text-[10px] ${STATUS_COLORS[row.status]}`}>
                    {STATUS_LABELS[row.status]}
                  </span>
                </td>
                <td className="px-3 py-2 min-w-44">
                  <div className="font-medium text-stone-800">{row.name || <span className="text-stone-400 italic">Missing name</span>}</div>
                  {row.brand && <div className="text-stone-400">{row.brand}</div>}
                  {row.status === "error" && row.error && <div className="text-red-600 text-[10px] mt-0.5">{row.error}</div>}
                </td>
                <td className="px-3 py-2 text-stone-600">{row.category}</td>
                <td className="px-3 py-2 text-stone-500">{row.unitSize || "-"}</td>
                <td className="px-3 py-2 whitespace-nowrap">
                  {row.status === "update" && row.currentPrice !== null && row.currentPrice !== row.parsedPrice && (
                    <span className="text-stone-400 line-through mr-1">{money(row.currentPrice)}</span>
                  )}
                  <span>{money(row.parsedPrice)}</span>
                </td>
                <td className="px-3 py-2 text-stone-500 whitespace-nowrap">
                  {row.status === "update" && row.currentSalePrice !== row.parsedSalePrice && row.currentSalePrice !== null && (
                    <span className="text-stone-400 line-through mr-1">{money(row.currentSalePrice)}</span>
                  )}
                  <span>{money(row.parsedSalePrice)}</span>
                </td>
                <td className="px-3 py-2 whitespace-nowrap">
                  {row.status === "update" && row.currentInStock !== null && row.currentInStock !== row.parsedInStock && (
                    <span className="text-stone-400 line-through mr-1">{row.currentInStock ? "Yes" : "No"}</span>
                  )}
                  <span className={`font-medium ${row.parsedInStock ? "text-green-700" : "text-red-600"}`}>
                    {row.parsedInStock ? "Yes" : "No"}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex justify-between items-center gap-4 pt-1">
        {counts.error > 0 && (
          <p className="text-xs text-stone-400">{counts.error} error row{counts.error === 1 ? "" : "s"} will be skipped.</p>
        )}
        <div className="flex gap-3 ml-auto">
          <button
            type="button"
            onClick={onCancel}
            disabled={loading}
            className="px-4 py-2 rounded-xl text-sm text-stone-600 hover:bg-stone-100 transition-colors border-none bg-transparent cursor-pointer disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onImport}
            disabled={importCount === 0 || loading}
            className="px-5 py-2 rounded-xl text-sm font-semibold bg-green-800 text-white hover:bg-green-900 transition-colors border-none cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
          >
            {loading ? "Importing..." : `Import ${importCount} item${importCount === 1 ? "" : "s"}`}
          </button>
        </div>
      </div>
    </div>
  );
}
