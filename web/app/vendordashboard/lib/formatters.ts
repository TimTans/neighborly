import { CAT_COLORS, SRC_LABELS } from "../constants";

export const catColor = (c: string): string => CAT_COLORS[c] || "#8B8680";
export const srcLabel = (s: string): string => SRC_LABELS[s] || s;

export const srcPillClass = (s: string): string => {
  if (s === "vendor") return "bg-green-100 text-green-800";
  if (s === "api") return "bg-blue-100 text-blue-700";
  if (s === "community") return "bg-orange-100 text-orange-700";
  return "bg-stone-100 text-stone-500";
};

export const fmtTime = (iso: string): string => {
  const d = new Date(iso);
  const now = new Date();
  const m = Math.floor((now.getTime() - d.getTime()) / 60000);
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric" });
};

export const fmtDate = (iso: string): string =>
  new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric" });

export const starsStr = (n: number): string => "★".repeat(n) + "☆".repeat(5 - n);

export const csvEscape = (value: string | number | boolean | null | undefined): string => {
  if (value === null || value === undefined) return "";
  const str = String(value);
  if (/[",\r\n]/.test(str)) return `"${str.replace(/"/g, '""')}"`;
  return str;
};
