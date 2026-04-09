"""
keyfood banner scraper using playwright + DOM extraction.

works with any keyfood cooperative banner (marketplace, key food, superfresh,
etc.) they all share the same SAP Hybris platform and DOM structure.

prices only render after a store is selected in the server-side session.
to set up a session:

    PYTHONPATH=. uv run python scripts/save_keyfood_session.py --banner marketplace

this opens a browser where you click "Set as my store", then saves the
session cookies. the scraper loads these cookies so the server knows which
store's prices to return.

pagination uses standard page links. the scraper follows the "next" button
rather than constructing URLs manually.

DOM structure (per product):
    div.product
    ├── a.product-card-anchor  (href contains /p/UPC-{code})
    │   ├── h3.product__name   → "Brand - Product Name"
    │   ├── div.product__size  → "16 FL"
    │   └── div.product__price
    │       └── div.price      → "$3.99 each"
    └── form.add_to_cart_form
        └── input[name="productCodePost"] value="UPC-073296046304"
"""

import logging
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

logger = logging.getLogger(__name__)


@dataclass
class MarketplaceProduct:
    """a single product scraped from marketplace."""
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


def _parse_price(price_text: str) -> float | None:
    """parse price from text like '$3.99 each' or '$3.99/lb'."""
    match = re.search(r"\$(\d+\.?\d*)", price_text)
    if match:
        return float(match.group(1))
    return None


def _extract_upc(raw_upc: str | None) -> str | None:
    """strip 'UPC-' prefix from marketplace UPC codes."""
    if raw_upc and raw_upc.startswith("UPC-"):
        return raw_upc[4:]
    return raw_upc or None


def _extract_brand(name: str) -> tuple[str | None, str]:
    """
    extract brand from product name.
    marketplace format: "Brand - Product Name"
    returns (brand, full_name).  full_name is kept as-is for consistency.
    """
    if " - " in name:
        brand, _ = name.split(" - ", 1)
        return brand.strip(), name
    return None, name


async def _parse_products_from_page(
    page,
    store_id: str,
    store_zip: str,
    category: str | None,
) -> list[MarketplaceProduct]:
    """extract product data from the current page DOM."""
    scraped_at = datetime.now(timezone.utc).isoformat()
    products = []

    product_elements = await page.query_selector_all("div.product")

    for el in product_elements:
        try:
            # name
            name_el = await el.query_selector("h3.product__name")
            if not name_el:
                continue
            full_name = (await name_el.inner_text()).strip()

            brand, _ = _extract_brand(full_name)

            # size
            size_el = await el.query_selector("div.product__size")
            unit_size = (await size_el.inner_text()).strip() if size_el else ""

            # price
            price_el = await el.query_selector("div.price")
            if not price_el:
                continue
            price_text = (await price_el.inner_text()).strip()
            price = _parse_price(price_text)
            if price is None:
                continue

            # UPC from hidden form input
            upc_el = await el.query_selector("input.js-key-product-cart-code")
            raw_upc = (await upc_el.get_attribute("value")) if upc_el else None
            upc = _extract_upc(raw_upc)

            # image. we could skip placeholder "image-coming-soon" images
            # to keep all images (including placeholders), swap the two lines below:
            img_el = await el.query_selector("div.product__img img")
            image_url = None
            if img_el:
                src = await img_el.get_attribute("src")
                if src and "image-coming-soon" not in src:
                    image_url = src if src.startswith("http") else None
                # image_url = src if src else None  # keep all images including placeholders

            # sale detection: marketplace uses a "price--was" element for
            # the original price when a product is on sale.
            on_sale = False
            sale_price = None
            was_price_el = await el.query_selector("div.price--was")
            if was_price_el:
                was_text = (await was_price_el.inner_text()).strip()
                original_price = _parse_price(was_text)
                if original_price:
                    on_sale = True
                    sale_price = price
                    price = original_price

            products.append(MarketplaceProduct(
                name=full_name,
                price=price,
                unit_size=unit_size,
                upc=upc,
                store_id=store_id,
                store_zip=store_zip,
                scraped_at=scraped_at,
                brand=brand,
                category=category,
                image_url=image_url,
                on_sale=on_sale,
                sale_price=sale_price,
            ))
        except Exception as e:
            logger.warning("skipping malformed product: %s", e)
            continue

    return products


def _dedup_products(
    products: list[MarketplaceProduct],
) -> list[MarketplaceProduct]:
    """remove duplicate products by UPC, falling back to name."""
    seen: set[str] = set()
    result = []
    for product in products:
        key = product.upc if product.upc is not None else product.name
        if key not in seen:
            seen.add(key)
            result.append(product)
    return result


async def scrape_store(
    store_id: str,
    store_zip: str,
    category_url: str,
    base_domain: str,
    category_name: str | None = None,
    session_state: str | Path | None = None,
    headless: bool = True,
) -> list[MarketplaceProduct]:
    """
    scrape all products for a keyfood banner store + category.

    works with any keyfood banner (marketplace, key food, superfresh, etc.)
    the base_domain determines which site's pagination links to follow.

    session_state: path to a playwright storage-state JSON file containing
        cookies from a store-selection session. generate one with:
        scripts/save_keyfood_session.py --banner <name>
    """
    from playwright.async_api import async_playwright

    if session_state is not None:
        session_state = Path(session_state)
        if not session_state.exists():
            raise FileNotFoundError(
                f"session file not found: {session_state}\n"
                "run `PYTHONPATH=. uv run python scripts/save_keyfood_session.py` "
                "to create one."
            )
        logger.info("using saved session from %s", session_state)
    else:
        raise FileNotFoundError(
            "no session file provided. prices won't render without a "
            "store session.\n"
            "run: PYTHONPATH=. uv run python scripts/save_keyfood_session.py "
            "--banner <name>"
        )

    collected: list[MarketplaceProduct] = []

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=headless)
        context = await browser.new_context(
            storage_state=str(session_state) if session_state else None,
        )
        page = await context.new_page()

        current_url = category_url
        page_num = 1

        while True:
            logger.info("loading page %d: %s", page_num, current_url)
            await page.goto(current_url, wait_until="domcontentloaded")
            await page.wait_for_load_state("networkidle")

            # wait for prices to render via JS
            try:
                await page.wait_for_selector("div.price", timeout=15000)
            except Exception:
                logger.info(
                    "no price elements found on page %d — store session may "
                    "be missing or expired. stopping.", page_num,
                )
                break

            products = await _parse_products_from_page(
                page, store_id, store_zip, category_name,
            )

            if not products:
                logger.info("no products on page %d, stopping", page_num)
                break

            collected.extend(products)
            logger.info(
                "page %d: %d products (collected: %d total)",
                page_num, len(products), len(collected),
            )

            # follow the next page link if it exists
            next_el = await page.query_selector(
                "li.pagination-next:not(.disabled) a"
            )
            if not next_el:
                logger.info("no next page, stopping")
                break

            next_href = await next_el.get_attribute("href")
            if not next_href:
                break
            current_url = f"{base_domain}{next_href}"
            page_num += 1

        await browser.close()

    products = _dedup_products(collected)

    if not products:
        raise RuntimeError(
            f"scrape completed for store {store_id} but no products were "
            f"collected. prices likely didn't render — the store session "
            f"may be missing or expired.\n"
            f"run: PYTHONPATH=. uv run python scripts/save_keyfood_session.py "
            f"--banner <name>"
        )

    logger.info(
        "scraped %d unique products from store %s", len(products), store_id,
    )
    return products
