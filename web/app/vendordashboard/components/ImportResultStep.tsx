import type { ImportResult } from "../types";

interface ImportResultStepProps {
  result: ImportResult;
  onClose: () => void;
}

export default function ImportResultStep({ result, onClose }: ImportResultStepProps) {
  const total = result.updated + result.added + result.created;
  const hasFailures = result.failed.length > 0;

  return (
    <div className="flex flex-col gap-5 text-center">
      <div className={`text-4xl ${hasFailures ? "text-yellow-600" : "text-green-700"}`}>
        {hasFailures ? "!" : "OK"}
      </div>

      <div>
        <h2 className="fraunces text-xl font-semibold tracking-tight text-stone-900 mb-1">
          {total > 0 ? "Import Complete" : "Nothing Imported"}
        </h2>
        <p className="text-sm text-stone-500">
          {total} item{total === 1 ? "" : "s"} imported successfully.
        </p>
      </div>

      <div className="flex justify-center gap-4 text-sm flex-wrap">
        {result.updated > 0 && (
          <div className="bg-blue-50 text-blue-700 px-4 py-2 rounded-xl">
            <span className="font-bold text-lg block">{result.updated}</span>
            Updated
          </div>
        )}
        {result.added > 0 && (
          <div className="bg-green-50 text-green-700 px-4 py-2 rounded-xl">
            <span className="font-bold text-lg block">{result.added}</span>
            Added
          </div>
        )}
        {result.created > 0 && (
          <div className="bg-yellow-50 text-yellow-700 px-4 py-2 rounded-xl">
            <span className="font-bold text-lg block">{result.created}</span>
            Created
          </div>
        )}
        {result.failed.length > 0 && (
          <div className="bg-red-50 text-red-600 px-4 py-2 rounded-xl">
            <span className="font-bold text-lg block">{result.failed.length}</span>
            Failed
          </div>
        )}
      </div>

      {hasFailures && (
        <div className="text-xs text-stone-500 max-h-32 overflow-auto text-left bg-stone-50 rounded-xl p-3 border border-stone-100">
          {result.failed.map((failure, index) => (
            <div key={`${failure.rowIndex}-${index}`}>Row {failure.rowIndex + 1}: {failure.reason}</div>
          ))}
        </div>
      )}

      <button
        type="button"
        onClick={onClose}
        className="mx-auto px-8 py-2.5 rounded-xl text-sm font-semibold bg-green-800 text-white hover:bg-green-900 transition-colors border-none cursor-pointer"
      >
        Done
      </button>
    </div>
  );
}
