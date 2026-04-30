import { fmtDate, starsStr } from "../lib/formatters";
import type { StoreReview } from "../types";

interface RatingCount {
  rating: number;
  count: number;
  pct: number;
}

interface ReviewsTabProps {
  avgRating: string;
  reviews: StoreReview[];
  ratingCounts: RatingCount[];
}

const AVATAR_GRADIENTS = [
  ["#2D6A4F", "#52B788"],
  ["#D94F30", "#F4A261"],
  ["#1565C0", "#42A5F5"],
  ["#D4700A", "#FFB74D"],
  ["#6D6560", "#A1887F"],
] as const;

const ReviewsTab = ({ avgRating, reviews, ratingCounts }: ReviewsTabProps) => {
  return (
    <div className="max-w-[1320px] mx-auto px-8 pb-12 grid grid-cols-3 gap-5">
      <div className="bg-white rounded-2xl p-7 border border-black/[0.05] hover:shadow-xl hover:-translate-y-0.5 transition-all duration-300">
        <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-widest text-stone-400 mb-6">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
          </svg>
          Overall Rating
        </div>

        <div className="text-center mb-8">
          <div className="fraunces text-[72px] font-semibold leading-none text-orange-600">{avgRating}</div>
          <div className="text-[26px] text-orange-500 tracking-widest mt-2">
            {"★".repeat(Math.round(parseFloat(avgRating)))}
            {"☆".repeat(5 - Math.round(parseFloat(avgRating)))}
          </div>
          <div className="text-sm text-stone-400 mt-2">Based on {reviews.length} reviews</div>
        </div>

        <div className="flex flex-col gap-3">
          {ratingCounts.map(({ rating, count, pct }) => (
            <div key={rating} className="flex items-center gap-3">
              <span className="text-sm font-medium text-stone-500 w-4 text-right">{rating}</span>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="#D4700A" stroke="none">
                <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
              </svg>
              <div className="flex-1 bg-stone-100 rounded-full h-2.5 overflow-hidden">
                <div
                  className="h-full rounded-full bg-orange-400 transition-all duration-500"
                  style={{ width: `${pct}%` }}
                />
              </div>
              <span className="text-sm font-medium text-stone-500 w-6 text-right">{count}</span>
            </div>
          ))}
        </div>

        <div className="mt-8 pt-6 border-t border-stone-100 grid grid-cols-2 gap-4">
          <div className="bg-stone-50 rounded-xl px-4 py-3 border border-stone-100 text-center">
            <div className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1">
              5-Star
            </div>
            <div className="fraunces text-xl font-semibold text-green-800">
              {reviews.length ? Math.round((ratingCounts[0].count / reviews.length) * 100) : 0}%
            </div>
          </div>
          <div className="bg-stone-50 rounded-xl px-4 py-3 border border-stone-100 text-center">
            <div className="text-[11px] font-semibold text-stone-400 uppercase tracking-wider mb-1">
              Avg Score
            </div>
            <div className="fraunces text-xl font-semibold text-orange-600">{avgRating}</div>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-2xl p-7 border border-black/[0.05] hover:shadow-xl hover:-translate-y-0.5 transition-all duration-300 col-span-2">
        <div className="flex justify-between items-center mb-5">
          <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-widest text-stone-400">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
            </svg>
            Customer Reviews
          </div>
          <span className="text-xs font-medium text-stone-400 bg-stone-100 px-3 py-1.5 rounded-full">
            {reviews.length} total
          </span>
        </div>

        {reviews.length === 0 ? (
          <div className="text-center py-12 text-stone-400 text-sm">No reviews yet.</div>
        ) : (
          <div className="flex flex-col">
            {reviews.map((r, i) => {
              const [from, to] = AVATAR_GRADIENTS[i % AVATAR_GRADIENTS.length];
              return (
                <div
                  key={r.id}
                  className={`py-5 ${i < reviews.length - 1 ? "border-b border-stone-100" : ""}`}
                >
                  <div className="flex justify-between items-start mb-2.5">
                    <div className="flex items-center gap-3">
                      <div
                        className="w-10 h-10 rounded-xl flex items-center justify-center text-sm font-bold text-white"
                        style={{ background: `linear-gradient(135deg, ${from}, ${to})` }}
                      >
                        {r.user_name.charAt(0)}
                      </div>
                      <div>
                        <div className="font-semibold text-[15px]">{r.user_name}</div>
                        <div className="text-orange-500 text-sm tracking-wider mt-0.5">
                          {starsStr(r.rating)}
                        </div>
                      </div>
                    </div>
                    <span className="text-xs text-stone-400 bg-stone-50 px-3 py-1.5 rounded-full border border-stone-100">
                      {fmtDate(r.created_at)}
                    </span>
                  </div>
                  {r.comment ? (
                    <div className="text-sm text-stone-600 leading-relaxed ml-[52px] bg-stone-50 rounded-xl px-4 py-3 border border-stone-100">
                      &ldquo;{r.comment}&rdquo;
                    </div>
                  ) : (
                    <div className="text-sm text-stone-300 italic ml-[52px]">No written review</div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};

export default ReviewsTab;
