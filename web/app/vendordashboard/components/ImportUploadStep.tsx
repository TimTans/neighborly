import { useRef, useState, type DragEvent } from "react";
import { downloadImportTemplate } from "../lib/importTemplate";

interface ImportUploadStepProps {
  onParsed: (file: File, autoAccept: boolean) => Promise<void>;
  onCancel: () => void;
  loading: boolean;
  error: string | null;
}

export default function ImportUploadStep({ onParsed, onCancel, loading, error }: ImportUploadStepProps) {
  const [file, setFile] = useState<File | null>(null);
  const [autoAccept, setAutoAccept] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const [fileError, setFileError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleFile = (selectedFile: File) => {
    const extension = selectedFile.name.split(".").pop()?.toLowerCase();
    if (extension === "csv" || extension === "xlsx" || extension === "xls") {
      setFile(selectedFile);
      setFileError(null);
      return;
    }

    setFile(null);
    setFileError("Upload a CSV or Excel file (.csv, .xlsx, .xls).");
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setDragOver(false);
    const selectedFile = event.dataTransfer.files[0];
    if (selectedFile) handleFile(selectedFile);
  };

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="fraunces text-xl font-semibold tracking-tight text-stone-900">Bulk Import Inventory</h2>
          <p className="text-sm text-stone-500 mt-1">Upload a CSV or Excel file to update inventory in one import.</p>
        </div>
        <div className="flex gap-2 text-xs whitespace-nowrap pt-1">
          <button
            type="button"
            onClick={() => downloadImportTemplate("csv")}
            className="text-green-800 underline hover:text-green-900 border-none bg-transparent cursor-pointer"
          >
            CSV template
          </button>
          <span className="text-stone-300">|</span>
          <button
            type="button"
            onClick={() => downloadImportTemplate("xlsx")}
            className="text-green-800 underline hover:text-green-900 border-none bg-transparent cursor-pointer"
          >
            Excel template
          </button>
        </div>
      </div>

      <div
        onClick={() => inputRef.current?.click()}
        onDragOver={(event) => {
          event.preventDefault();
          setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleDrop}
        className={`border-2 border-dashed rounded-2xl p-10 text-center cursor-pointer transition-colors ${
          dragOver ? "border-green-700 bg-green-50" : "border-stone-300 hover:border-green-500 hover:bg-stone-50"
        }`}
      >
        <input
          ref={inputRef}
          type="file"
          accept=".csv,.xlsx,.xls"
          className="hidden"
          onChange={(event) => {
            const selectedFile = event.target.files?.[0];
            if (selectedFile) handleFile(selectedFile);
          }}
        />
        {file ? (
          <div>
            <p className="text-green-800 font-semibold">{file.name}</p>
            <p className="text-xs text-stone-400 mt-1">{(file.size / 1024).toFixed(1)} KB - click to change</p>
          </div>
        ) : (
          <div>
            <p className="font-semibold text-stone-700">Drop your inventory file here</p>
            <p className="text-sm text-stone-400 mt-1">or click to choose a CSV, XLSX, or XLS file</p>
          </div>
        )}
      </div>

      <label className="flex items-start gap-3 rounded-xl bg-stone-50 border border-stone-100 p-4 cursor-pointer select-none">
        <input
          type="checkbox"
          checked={autoAccept}
          onChange={(event) => setAutoAccept(event.target.checked)}
          className="mt-0.5 w-4 h-4 accent-green-700 cursor-pointer"
        />
        <span>
          <span className="block text-sm font-semibold text-stone-700">Auto-accept clean imports</span>
          <span className="block text-xs text-stone-400 mt-0.5">Skip preview only when every row matches and has no validation errors.</span>
        </span>
      </label>

      {(error || fileError) && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-xl px-4 py-3">
          {error || fileError}
        </div>
      )}

      <div className="flex justify-end gap-3">
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
          onClick={() => file && onParsed(file, autoAccept)}
          disabled={!file || loading}
          className="px-5 py-2 rounded-xl text-sm font-semibold bg-green-800 text-white hover:bg-green-900 transition-colors border-none cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
        >
          {loading ? "Parsing..." : "Preview Import"}
        </button>
      </div>
    </div>
  );
}
