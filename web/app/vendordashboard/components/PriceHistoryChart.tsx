import { useMemo, useState } from "react";
import type { PriceHistoryEntry } from "../types";

type RangeKey = "7d" | "30d" | "90d" | "all";

interface PriceHistoryChartProps {
  productName: string;
  history: PriceHistoryEntry[];
  loading?: boolean;
  error?: string | null;
}

const RANGES: Array<{ key: RangeKey; label: string; days: number | null }> = [
  { key: "7d", label: "7D", days: 7 },
  { key: "30d", label: "30D", days: 30 },
  { key: "90d", label: "90D", days: 90 },
  { key: "all", label: "All", days: null },
];

const money = (value: number) => `$${value.toFixed(2)}`;

const shortDate = (iso: string) =>
  new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric" });

const compactDate = (iso: string) =>
  new Date(iso).toLocaleDateString("en-US", { month: "numeric", day: "numeric" });

const pointsToPath = (points: Array<{ x: number; y: number }>) =>
  points.map((point, index) => `${index === 0 ? "M" : "L"} ${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(" ");

const PriceHistoryChart = ({ productName, history, loading = false, error = null }: PriceHistoryChartProps) => {
  const [range, setRange] = useState<RangeKey>("30d");

  const sortedHistory = useMemo(
    () =>
      [...history]
        .filter((entry) => Number.isFinite(entry.price) && entry.created_at)
        .sort((a, b) => new Date(a.created_at).getTime() - new Date(b.created_at).getTime()),
    [history],
  );

  const filteredHistory = useMemo(() => {
    const selected = RANGES.find((item) => item.key === range);
    if (!selected?.days || sortedHistory.length === 0) return sortedHistory;

    const latestTime = new Date(sortedHistory[sortedHistory.length - 1].created_at).getTime();
    const cutoff = latestTime - selected.days * 24 * 60 * 60 * 1000;
    return sortedHistory.filter((entry) => new Date(entry.created_at).getTime() >= cutoff);
  }, [range, sortedHistory]);

  const chart = useMemo(() => {
    if (filteredHistory.length === 0) return null;

    const width = 760;
    const height = 220;
    const padding = { top: 18, right: 18, bottom: 38, left: 52 };
    const plotWidth = width - padding.left - padding.right;
    const plotHeight = height - padding.top - padding.bottom;
    const hasSalePrice = filteredHistory.some((entry) => entry.sale_price !== null);
    const allValues = filteredHistory.flatMap((entry) => {
      const actualPrice = entry.sale_price ?? entry.price;
      return hasSalePrice ? [entry.price, actualPrice] : [actualPrice];
    });
    const low = Math.min(...allValues);
    const high = Math.max(...allValues);
    const spread = Math.max(high - low, 0.5);
    const minY = Math.max(0, low - spread * 0.15);
    const maxY = high + spread * 0.15;
    const valueRange = Math.max(maxY - minY, 0.5);
    const xFor = (index: number) =>
      padding.left + (filteredHistory.length === 1 ? plotWidth / 2 : (index / (filteredHistory.length - 1)) * plotWidth);
    const yFor = (value: number) => padding.top + ((maxY - value) / valueRange) * plotHeight;

    const actualPoints = filteredHistory.map((entry, index) => {
      const actualPrice = entry.sale_price ?? entry.price;
      return {
        x: xFor(index),
        y: yFor(actualPrice),
        actualPrice,
        entry,
      };
    });
    const regularPoints = filteredHistory.map((entry, index) => ({
      x: xFor(index),
      y: yFor(entry.price),
      entry,
    }));
    const gridValues = [maxY, minY + valueRange * 0.5, minY];
    const first = filteredHistory[0];
    const latest = filteredHistory[filteredHistory.length - 1];
    const latestActual = latest.sale_price ?? latest.price;
    const firstActual = first.sale_price ?? first.price;
    const change = latestActual - firstActual;

    return {
      width,
      height,
      padding,
      plotWidth,
      actualPoints,
      regularPoints,
      hasSalePrice,
      gridValues: gridValues.map((value) => ({ value, y: yFor(value) })),
      labels: [
        { text: compactDate(first.created_at), x: padding.left, anchor: "start" as const },
        {
          text: compactDate(latest.created_at),
          x: padding.left + plotWidth,
          anchor: "end" as const,
        },
      ],
      stats: {
        latestActual,
        latestRegular: latest.price,
        latestSale: latest.sale_price,
        low,
        high,
        change,
        startDate: shortDate(first.created_at),
        endDate: shortDate(latest.created_at),
      },
    };
  }, [filteredHistory]);

  return (
    <div className="bg-stone-50 rounded-xl p-4 my-1 mb-2 border border-stone-100">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between mb-4">
        <div>
          <div className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider">
            Price History
          </div>
          <div className="text-sm font-semibold text-stone-800 mt-1">{productName}</div>
        </div>

        <div className="inline-flex w-fit rounded-xl border border-stone-200 bg-white p-1">
          {RANGES.map((item) => {
            const active = range === item.key;
            return (
              <button
                key={item.key}
                type="button"
                onClick={() => setRange(item.key)}
                className={`h-8 min-w-10 px-3 rounded-lg text-xs font-semibold transition-colors cursor-pointer border-none ${
                  active ? "bg-green-800 text-white" : "bg-transparent text-stone-500 hover:bg-stone-100 hover:text-stone-800"
                }`}
              >
                {item.label}
              </button>
            );
          })}
        </div>
      </div>

      {loading ? (
        <div className="h-48 rounded-xl border border-stone-100 bg-white flex items-center justify-center text-sm text-stone-400">
          Loading price history...
        </div>
      ) : error ? (
        <div className="h-48 rounded-xl border border-red-100 bg-red-50 flex items-center justify-center text-sm text-red-700">
          {error}
        </div>
      ) : sortedHistory.length === 0 ? (
        <div className="h-48 rounded-xl border border-stone-100 bg-white flex items-center justify-center text-sm text-stone-400">
          No price history has been recorded for this product yet.
        </div>
      ) : filteredHistory.length === 0 || !chart ? (
        <div className="h-48 rounded-xl border border-stone-100 bg-white flex items-center justify-center text-sm text-stone-400">
          No price changes in this time range.
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_210px]">
          <div className="rounded-xl border border-stone-100 bg-white p-3">
            <div className="flex items-center gap-4 text-xs text-stone-400 mb-2">
              <span className="inline-flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-green-700" />
                Actual
              </span>
              {chart.hasSalePrice && (
                <span className="inline-flex items-center gap-1.5">
                  <span className="h-2 w-2 rounded-full bg-stone-400" />
                  Regular
                </span>
              )}
            </div>
            <svg
              viewBox={`0 0 ${chart.width} ${chart.height}`}
              className="h-56 w-full overflow-visible"
              role="img"
              aria-label={`${productName} price history line chart`}
            >
              <rect x="0" y="0" width={chart.width} height={chart.height} rx="14" fill="#FFFFFF" />
              {chart.gridValues.map((grid) => (
                <g key={grid.value.toFixed(2)}>
                  <line
                    x1={chart.padding.left}
                    x2={chart.width - chart.padding.right}
                    y1={grid.y}
                    y2={grid.y}
                    stroke="#E7E5E0"
                    strokeWidth="1"
                  />
                  <text x="12" y={grid.y + 4} className="fill-stone-400 text-[11px]">
                    {money(grid.value)}
                  </text>
                </g>
              ))}
              {chart.hasSalePrice && chart.regularPoints.length > 1 && (
                <path
                  d={pointsToPath(chart.regularPoints)}
                  fill="none"
                  stroke="#A8A29E"
                  strokeWidth="2.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeDasharray="7 6"
                />
              )}
              <path
                d={pointsToPath(chart.actualPoints)}
                fill="none"
                stroke="#15803D"
                strokeWidth="3.5"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
              {chart.hasSalePrice &&
                chart.regularPoints.map((point, index) => (
                  <g key={`regular-${point.entry.created_at}-${index}`}>
                    <circle cx={point.x} cy={point.y} r="3.5" fill="#A8A29E" />
                    <title>
                      {shortDate(point.entry.created_at)} regular price {money(point.entry.price)}
                    </title>
                  </g>
                ))}
              {chart.actualPoints.map((point, index) => (
                <g key={`actual-${point.entry.created_at}-${index}`}>
                  <circle cx={point.x} cy={point.y} r="4.5" fill="#15803D" stroke="#FFFFFF" strokeWidth="2" />
                  <title>
                    {shortDate(point.entry.created_at)} actual price {money(point.actualPrice)}
                  </title>
                </g>
              ))}
              {chart.labels.map((label, index) => (
                <text
                  key={`${label.text}-${label.anchor}-${index}`}
                  x={label.x}
                  y={chart.height - 12}
                  textAnchor={label.anchor}
                  className="fill-stone-400 text-[11px]"
                >
                  {label.text}
                </text>
              ))}
            </svg>
          </div>

          <div className="grid grid-cols-2 gap-2 lg:grid-cols-1">
            <div className="rounded-xl border border-stone-100 bg-white p-3">
              <div className="text-[11px] text-stone-400 mb-1">Current actual</div>
              <div className="font-semibold text-stone-900">{money(chart.stats.latestActual)}</div>
              {chart.stats.latestSale !== null && (
                <div className="text-xs font-semibold text-stone-400 mt-1">Regular {money(chart.stats.latestRegular)}</div>
              )}
            </div>
            <div className="rounded-xl border border-stone-100 bg-white p-3">
              <div className="text-[11px] text-stone-400 mb-1">Range</div>
              <div className="font-semibold text-stone-900">
                {money(chart.stats.low)}-{money(chart.stats.high)}
              </div>
            </div>
            <div className="rounded-xl border border-stone-100 bg-white p-3">
              <div className="text-[11px] text-stone-400 mb-1">Actual change</div>
              <div className={`font-semibold ${chart.stats.change > 0 ? "text-red-600" : chart.stats.change < 0 ? "text-green-800" : "text-stone-900"}`}>
                {chart.stats.change === 0 ? "$0.00" : `${chart.stats.change > 0 ? "+" : "-"}${money(Math.abs(chart.stats.change))}`}
              </div>
            </div>
            <div className="rounded-xl border border-stone-100 bg-white p-3">
              <div className="text-[11px] text-stone-400 mb-1">Showing</div>
              <div className="font-semibold text-stone-900">{filteredHistory.length} points</div>
              <div className="text-[11px] text-stone-400 mt-1">
                {chart.stats.startDate} - {chart.stats.endDate}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PriceHistoryChart;
