import { useState } from "react";

interface PaginationControlsProps {
  page: number;
  totalPages: number;
  onChange: (p: number) => void;
}

const PaginationControls = ({ page, totalPages, onChange }: PaginationControlsProps) => {
  const [jumpValue, setJumpValue] = useState("");

  const go = (p: number) => onChange(Math.max(1, Math.min(totalPages, p)));

  const handleJump = () => {
    const n = parseInt(jumpValue, 10);
    if (!isNaN(n)) go(n);
    setJumpValue("");
  };

  const pages: (number | "...")[] = [];
  if (totalPages <= 7) {
    for (let i = 1; i <= totalPages; i++) pages.push(i);
  } else {
    pages.push(1);
    if (page > 3) pages.push("...");
    const start = Math.max(2, page - 1);
    const end = Math.min(totalPages - 1, page + 1);
    for (let i = start; i <= end; i++) pages.push(i);
    if (page < totalPages - 2) pages.push("...");
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
        {"<-"} Prev
      </button>

      {pages.map((p, i) =>
        p === "..." ? (
          <span key={`e${i}`} className="px-1 text-stone-400 text-xs select-none">
            ...
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

export default PaginationControls;
