"""
shoprite scraper using playwright + server-rendered state extraction.

navigates to shoprite category pages and extracts product data from
window.__PRELOADED_STATE__.search.productCardDictionary, the redux store
that the SSR page hydrates with product listings.

NOTE: the api (storefrontgateway.shoprite.com) rejects direct http calls due to tls
fingerprinting, so playwright with real crhomium is needed. products are
server rendered on initial page load rather than fetched via xhr, so we
read the preloaded redux state rather than intercepting network requests.

shoprite.com is behind cloudflare turnstile, which blocks headless browsers
to bypass, solve challenge once in a visible browser using
scripts/save_session.py, then pass the saved session file to scrape_store().

pagination is handled by navigating to successive page urls increment accordingly and extracting
fresh state from each page load.
"""

import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from app.scraper.upc import _validate_upc

logger = logging.getLogger(__name__)

_PAGE_SIZE = 30


class CloudflareBlockedError(RuntimeError):
    """raised when cloudflare challenge is detected during scraping."""
    pass

# consistent user agent used by both save_session.py and scrape_store().
# cloudflare binds cf_clearance cookies to the user agent string, so both
# the headed session (where the challenge is solved) and the headless
# scraper must present the same one.
USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/131.0.0.0 Safari/537.36"
)


@dataclass
class StoreConfig:
    """
    configuration for a single shoprite store + category to scrape.

    store_id: shoprite's numeric store id (e.g. "218")
    zip_code: human-readable location context
    browse_url: the exact category page URL to navigate to.
    """
    store_id: str
    zip_code: str
    browse_url: str


@dataclass
class ShopRiteProduct:
    """
    a single product scraped from shoprite.

    upc is nullable so products without a upc are still stored but cannot get
    further data via the fdc nutrition api.
    """
    name: str
    price: float
    unit_size: str
    upc: str | None
    store_id: str
    store_zip: str
    scraped_at: str
    brand: str | None = None
    category: str | None = None
    image_url: str | None = None
    on_sale: bool = False
    sale_price: float | None = None

    def to_dict(self) -> dict:
        return {
            "name": self.name,
            "price": self.price,
            "unit_size": self.unit_size,
            "upc": self.upc,
            "store_id": self.store_id,
            "store_zip": self.store_zip,
            "scraped_at": self.scraped_at,
            "brand": self.brand,
            "category": self.category,
            "image_url": self.image_url,
            "on_sale": self.on_sale,
            "sale_price": self.sale_price,
        }


def _parse_preloaded_products(
    product_dict: dict, config: StoreConfig,
) -> list[ShopRiteProduct]:
    """
    parse products from __PRELOADED_STATE__.search.productCardDictionary.

    product_dict is a dict mapping sku -> product data. each product has:
        name, price ("$4.09"), brand, sku, unitOfSize, description, etc.

    malformed items are skipped with a warning.
    """
    products = []
    scraped_at = datetime.now(timezone.utc).isoformat()

    for sku, item in product_dict.items():
        try:
            name = item["name"]

            # price comes as "$4.09", "$10.21 avg/ea", or "$2.99/lb"
            price_str = item["price"].replace("$", "").replace(",", "")
            # strip trailing suffixes: "avg/ea", "/lb", "/ea", etc.
            price_str = price_str.split()[0] if " " in price_str else price_str
            price_str = price_str.split("/")[0]
            price = float(price_str)

            size = item.get("unitOfSize", {})
            unit_size = f"{size.get('size', '')} {size.get('abbreviation', '')}".strip()

            # sale price: parse wasPrice when item is discounted
            on_sale = bool(item.get("isDiscounted", False))
            sale_price = None
            if on_sale:
                was_price_str = item.get("wasPrice", "")
                if was_price_str:
                    try:
                        # "price" is the current (sale) price, wasPrice is the original
                        # so sale_price = current price, and price = original
                        sale_price = price
                        price = float(was_price_str.replace("$", "").replace(",", ""))
                    except (ValueError, TypeError):
                        sale_price = None

            # image url from nested image object
            image_data = item.get("image", {})
            image_url = image_data.get("default") if isinstance(image_data, dict) else None

            products.append(ShopRiteProduct(
                name=name,
                price=price,
                unit_size=unit_size,
                upc=_validate_upc(sku),
                store_id=config.store_id,
                store_zip=config.zip_code,
                scraped_at=scraped_at,
                brand=item.get("brand"),
                category=item.get("category"),
                image_url=image_url,
                on_sale=on_sale,
                sale_price=sale_price,
            ))
        except (KeyError, TypeError, ValueError) as e:
            logger.warning("skipping malformed product (sku=%s): %s", sku, e)
            continue

    return products


def _dedup_products(products: list[ShopRiteProduct]) -> list[ShopRiteProduct]:
    """
    remove duplicate products. deduplicates by upc when available,
    falls back to product name when upc is None.
    """
    seen: set[str] = set()
    result = []
    for product in products:
        key = product.upc if product.upc is not None else product.name
        if key not in seen:
            seen.add(key)
            result.append(product)
    return result


async def scrape_store(
    config: StoreConfig,
    session_state: str | Path | None = None,
    headless: bool = False,
) -> list[ShopRiteProduct]:
    """
    scrape all products for a single shoprite store + category.

    navigates to the category page, extracts product data from the
    server-rendered redux state (__PRELOADED_STATE__), then paginates
    by appending &page=N to the url and extracting fresh state from each load.

    session_state: path to a playwright storage-state JSON file containing
        cookies from a previous cloudflare-verified session. generate one
        with scripts/save_session.py. if None, the scraper will attempt
        without session cookies (will fail if cloudflare is active).

    headless: whether to run the browser in headless mode. currently defaults
        to False (headed) because cloudflare blocks headless chromium on
        paginated requests (page 2+). the first page works headless, but
        subsequent pages get flagged.

        to switch back to headless:
          1. set headless=True here (or pass headless=True from the caller)
          2. cloudflare may block pagination(IF USING VPN USE RESIDENTIAL IP) if so, try increasing the
             wait_for_timeout delay (currently 2000ms) to 5000-10000ms
          3. if still blocked, headless needs session cookies from
             save_session.py and the anti-detection args below

    edge case of 0 products but "successful" scrape ->
    raise RuntimeError if zero products are collected.
    """
    from playwright.async_api import async_playwright

    if session_state is not None:
        session_state = Path(session_state)
        if not session_state.exists():
            raise FileNotFoundError(
                f"session file not found: {session_state}\n"
                "run `PYTHONPATH=. uv run python scripts/save_session.py` "
                "to create one."
            )

    collected: list[ShopRiteProduct] = []

    async with async_playwright() as p:
        # cloudflare binds cf_clearance to the TLS/browser fingerprint.
        # headed mode avoids fingerprint mismatch entirely. if switching
        # to headless=True(switch back if you stop scraping after second page) keep the anti-detection args below they
        # suppress automation signals that cloudflare checks.
        browser = await p.chromium.launch(
            headless=headless,
            args=[
                "--disable-blink-features=AutomationControlled",
            ],
        )
        context = await browser.new_context(
            storage_state=str(session_state) if session_state else None,
            user_agent=USER_AGENT,
        )
        # remove navigator.webdriver flag that cloudflare checks.
        # only matters in headless mode but harmless in headed mode.
        await context.add_init_script(
            "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
        )
        page = await context.new_page()

        page_num = 1
        total_items = None

        while True:
            # build url: append &page=N for pages 2+
            url = config.browse_url
            if page_num > 1:
                sep = "&" if "?" in url else "?"
                url = f"{url}{sep}page={page_num}"

            logger.info("loading page %d: %s", page_num, url)
            await page.goto(url, wait_until="domcontentloaded")
            await page.wait_for_load_state("load")
            await page.wait_for_timeout(2000)

            # detect cloudflare challenge page
            title = await page.title()
            if "just a moment" in title.lower():
                raise CloudflareBlockedError(
                    "blocked by cloudflare challenge. session cookies are "
                    "missing or expired."
                )

            # extract product data from the redux preloaded state
            state = await page.evaluate("""() => {
                const s = window.__PRELOADED_STATE__;
                if (!s || !s.search) return null;
                return {
                    products: s.search.productCardDictionary || {},
                    totalItems: (s.search.pagination && s.search.pagination.category)
                        ? s.search.pagination.category.totalItems
                        : 0,
                };
            }""")

            if not state or not state["products"]:
                logger.info("no products on page %d, stopping", page_num)
                break

            products = _parse_preloaded_products(state["products"], config)
            collected.extend(products)

            if total_items is None:
                total_items = state["totalItems"]
            logger.info(
                "page %d: %d products (collected: %d / %d total)",
                page_num, len(products), len(collected), total_items,
            )

            # check if we've collected everything
            if total_items and len(collected) >= total_items:
                logger.info("collected all %d items", total_items)
                break

            # safety: if page returned products we already have, stop
            if not products:
                break

            page_num += 1

        await browser.close()

    products = _dedup_products(collected)

    if not products:
        raise RuntimeError(
            f"scrape completed for store {config.store_id} / {config.browse_url} "
            "but no products were collected - "
            "check browse_url in StoreConfig. if cloudflare is active, "
            "run scripts/save_session.py to refresh session cookies."
        )

    logger.info("scraped %d unique products from store %s", len(products), config.store_id)
    return products
