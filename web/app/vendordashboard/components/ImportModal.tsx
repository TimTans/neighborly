import { useCallback, useState } from "react";
import { bulkImportProducts, getProductCategories, matchCatalogProducts } from "../actions";
import { parseImportFile } from "../lib/importParser";
import type { ImportResult, ImportRow } from "../types";
import ImportPreviewStep from "./ImportPreviewStep";
import ImportResultStep from "./ImportResultStep";
import ImportUploadStep from "./ImportUploadStep";

interface ImportModalProps {
  onClose: () => void;
  onImportComplete: () => Promise<void> | void;
}

type Step = "upload" | "preview" | "result";
const SERVER_ACTION_CHUNK_SIZE = 200;

function chunkArray<T>(items: T[], size: number): T[][] {
  const chunks: T[][] = [];
  for (let index = 0; index < items.length; index += size) {
    chunks.push(items.slice(index, index + size));
  }
  return chunks;
}

function moneyEqual(left: number | null, right: number | null): boolean {
  if (left === null || right === null) return left === right;
  return Math.abs(left - right) < 0.005;
}

function rowHasChanges(row: ImportRow): boolean {
  return (
    !moneyEqual(row.currentPrice, row.parsedPrice) ||
    !moneyEqual(row.currentSalePrice, row.parsedSalePrice) ||
    row.currentInStock !== row.parsedInStock
  );
}

function isUnmatchedRow(row: ImportRow): boolean {
  return row.matchedProductId === null && row.error === "No catalog match found. Enable create new products to add it.";
}

function applyCreateNew(rows: ImportRow[], createNew: boolean): ImportRow[] {
  return rows.map((row) => {
    if (createNew && isUnmatchedRow(row)) {
      return { ...row, status: "create", error: null };
    }

    if (!createNew && row.status === "create" && row.matchedProductId === null) {
      return {
        ...row,
        status: "error",
        error: "No catalog match found. Enable create new products to add it.",
      };
    }

    return row;
  });
}

export default function ImportModal({ onClose, onImportComplete }: ImportModalProps) {
  const [step, setStep] = useState<Step>("upload");
  const [rows, setRows] = useState<ImportRow[]>([]);
  const [createNew, setCreateNew] = useState(false);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const runImport = useCallback(async (importRows: ImportRow[]) => {
    setLoading(true);
    setUploadError(null);

    try {
      const updates = importRows
        .map((row, rowIndex) => ({ row, rowIndex }))
        .filter(({ row }) => row.status === "update")
        .map(({ row, rowIndex }) => ({
          rowIndex,
          storeProductId: row.storeProductId!,
          price: row.parsedPrice!,
          salePrice: row.parsedSalePrice,
          inStock: row.parsedInStock,
        }));

      const adds = importRows
        .map((row, rowIndex) => ({ row, rowIndex }))
        .filter(({ row }) => row.status === "add")
        .map(({ row, rowIndex }) => ({
          rowIndex,
          catalogProductId: row.matchedProductId!,
          price: row.parsedPrice!,
          salePrice: row.parsedSalePrice,
          inStock: row.parsedInStock,
        }));

      const creates = importRows
        .map((row, rowIndex) => ({ row, rowIndex }))
        .filter(({ row }) => row.status === "create")
        .map(({ row, rowIndex }) => ({
          rowIndex,
          name: row.name,
          brand: row.brand,
          category: row.category,
          unitSize: row.unitSize,
          price: row.parsedPrice!,
          salePrice: row.parsedSalePrice,
          inStock: row.parsedInStock,
        }));

      const aggregate: ImportResult = { updated: 0, added: 0, created: 0, failed: [] };
      const payloads = [
        ...chunkArray(updates, SERVER_ACTION_CHUNK_SIZE).map((chunk) => ({ updates: chunk, adds: [], creates: [] })),
        ...chunkArray(adds, SERVER_ACTION_CHUNK_SIZE).map((chunk) => ({ updates: [], adds: chunk, creates: [] })),
        ...chunkArray(creates, SERVER_ACTION_CHUNK_SIZE).map((chunk) => ({ updates: [], adds: [], creates: chunk })),
      ];

      for (const payload of payloads) {
        const response = await bulkImportProducts(payload);
        if (response.error || !response.data) {
          setUploadError(response.error ?? "Import failed.");
          return;
        }

        aggregate.updated += response.data.updated;
        aggregate.added += response.data.added;
        aggregate.created += response.data.created;
        aggregate.failed.push(...response.data.failed);
      }

      setResult(aggregate);
      setStep("result");
    } finally {
      setLoading(false);
    }
  }, []);

  const handleUpload = useCallback(async (file: File, autoAccept: boolean) => {
    setLoading(true);
    setUploadError(null);

    try {
      const categoriesResponse = await getProductCategories();
      if (categoriesResponse.error) {
        setUploadError(categoriesResponse.error);
        return;
      }

      const parsed = await parseImportFile(file, categoriesResponse.data);
      if (parsed.length === 0) {
        setUploadError("The uploaded file does not contain any inventory rows.");
        return;
      }

      const validRows = parsed.filter((row) => row.status === "pending");
      const matchInput = validRows.map((row) => ({
        name: row.name,
        brand: row.brand,
        unitSize: row.unitSize,
        catalogProductId: row.catalogProductId,
      }));

      let classified = parsed;
      if (matchInput.length > 0) {
        const matches: NonNullable<Awaited<ReturnType<typeof matchCatalogProducts>>["data"]> = [];
        for (const chunk of chunkArray(matchInput, SERVER_ACTION_CHUNK_SIZE)) {
          const response = await matchCatalogProducts(chunk);
          if (response.error || !response.data) {
            setUploadError(response.error ?? "Unable to match products against the catalog.");
            return;
          }

          matches.push(...response.data);
        }

        let matchIndex = 0;
        classified = parsed.map((row) => {
          if (row.status !== "pending") return row;
          const match = matches[matchIndex++];
          if (match.matchedProductId) {
            const matchedRow = { ...row, ...match };
            return {
              ...matchedRow,
              status: match.storeProductId ? (rowHasChanges(matchedRow) ? "update" : "unchanged") : "add",
              error: null,
            };
          }

          return {
            ...row,
            status: "error",
            error: "No catalog match found. Enable create new products to add it.",
            ...match,
          };
        });
      }

      setRows(classified);
      setCreateNew(false);

      const hasErrors = classified.some((row) => row.status === "error");
      if (autoAccept && !hasErrors) {
        await runImport(classified);
      } else {
        setStep("preview");
      }
    } catch (error) {
      setUploadError(error instanceof Error ? error.message : "Failed to parse file.");
    } finally {
      setLoading(false);
    }
  }, [runImport]);

  const handleToggleCreateNew = useCallback((enabled: boolean) => {
    setCreateNew(enabled);
    setRows((previousRows) => applyCreateNew(previousRows, enabled));
  }, []);

  const handleClose = async () => {
    if (result && result.updated + result.added + result.created > 0) {
      await onImportComplete();
    }
    onClose();
  };

  return (
    <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4" onClick={loading ? undefined : onClose}>
      <div
        className="bg-white rounded-2xl shadow-2xl w-full max-w-3xl p-6 max-h-[90vh] overflow-y-auto"
        onClick={(event) => event.stopPropagation()}
      >
        {step === "upload" && (
          <ImportUploadStep onParsed={handleUpload} onCancel={onClose} loading={loading} error={uploadError} />
        )}
        {step === "preview" && (
          <ImportPreviewStep
            rows={rows}
            createNew={createNew}
            onToggleCreateNew={handleToggleCreateNew}
            onImport={() => runImport(rows)}
            onCancel={onClose}
            loading={loading}
          />
        )}
        {step === "result" && result && <ImportResultStep result={result} onClose={handleClose} />}
      </div>
    </div>
  );
}
