# Neighborly Backend

FastAPI backend for the Neighborly grocery price comparison and route optimizer.

## Requirements

- Python 3.14+
- [uv](https://docs.astral.sh/uv/) (package manager)

## Setup

1. Install dependencies:
   ```bash
   uv sync
   ```

2. Copy the environment template and fill in your values:
   ```bash
   cp .env.example .env
   ```

   Required env vars: `SUPABASE_URL`, `SUPABASE_SERVICE_KEY`, `FDC_API_KEY`

   Get a free FDC API key at https://fdc.nal.usda.gov/api-key-signup/

3. Install Playwright browsers (needed for scraping):
   ```bash
   uv run playwright install chromium
   ```

## Running the API

```bash
uv run uvicorn app.main:app --reload
```

The API will be available at `http://localhost:8000`.

- Interactive docs (Swagger): `http://localhost:8000/docs`
- Health check: `http://localhost:8000/health`

## Scrapers

All scraper commands run from the `backend/` directory with `PYTHONPATH=.`.

### ShopRite

ShopRite uses Cloudflare protection, so you need a saved browser session first.

**1. Save a session** (one-time, lasts several hours-days):

```bash
PYTHONPATH=. uv run python scripts/save_session.py
```

A browser window opens. Solve the Cloudflare challenge(if seen), then the session saves to `data/shoprite_session.json`.

**2. Scrape products:**

```bash
# all stores and categories (JSON files to data/)
PYTHONPATH=. uv run python scripts/scrape_shoprite.py

# write to supabase instead of JSON
PYTHONPATH=. uv run python scripts/scrape_shoprite.py --db

# single store or category
PYTHONPATH=. uv run python scripts/scrape_shoprite.py --store 218
PYTHONPATH=. uv run python scripts/scrape_shoprite.py --category milk

# combine flags
PYTHONPATH=. uv run python scripts/scrape_shoprite.py --store 218 --category milk --db
```

### KeyFood

KeyFood covers multiple banners (Key Food, Marketplace, SuperFresh, etc.).

**1. Save a session per banner:**

```bash
# list available banners
PYTHONPATH=. uv run python scripts/save_keyfood_session.py --list

# save session for a specific banner
PYTHONPATH=. uv run python scripts/save_keyfood_session.py --banner marketplace
PYTHONPATH=. uv run python scripts/save_keyfood_session.py --banner keyfood
```

**2. Scrape products:**

```bash
# all banners, stores, and categories
PYTHONPATH=. uv run python scripts/scrape_keyfood.py

# write to supabase
PYTHONPATH=. uv run python scripts/scrape_keyfood.py --db

# filter by banner, store, or category
PYTHONPATH=. uv run python scripts/scrape_keyfood.py --banner keyfood
PYTHONPATH=. uv run python scripts/scrape_keyfood.py --store 2138
PYTHONPATH=. uv run python scripts/scrape_keyfood.py --category refrigerated

# headed mode (visible browser) for debugging
PYTHONPATH=. uv run python scripts/scrape_keyfood.py --headed
```

### Nutrition Enrichment

When you scrape with `--db`, nutrition enrichment runs automatically after scraping. It looks up each product's UPC in the USDA FoodData Central API and saves nutrition data (calories, protein, fat, allergens, etc.) to the `product_nutrition` table.

Enrichment runs 5 concurrent FDC lookups with a small per-request delay to stay under the free-tier rate limit (~1000 req/hour). Progress is logged every 200 products. If the FDC API returns 429 (rate limited), it retries with exponential backoff.

Not every product will get nutrition data -- FDC's branded database doesn't cover all grocery items, and some store products use internal SKUs rather than real UPC barcodes.

## Tests

```bash
PYTHONPATH=. uv run pytest -v
```