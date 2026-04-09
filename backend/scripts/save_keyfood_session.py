"""
save a browser session after selecting a keyfood store.

opens a visible browser to the banner's store locator. select your
preferred store, then the script saves cookies to
data/{banner}_session.json for use by the headless scraper.

sessions are per-subdomain, so each banner needs its own session.

usage (from backend/):
    # save session for a specific banner:
    PYTHONPATH=. uv run python scripts/save_keyfood_session.py --banner marketplace
    PYTHONPATH=. uv run python scripts/save_keyfood_session.py --banner keyfood

    # list available banners:
    PYTHONPATH=. uv run python scripts/save_keyfood_session.py --list
"""

import argparse
import asyncio
from pathlib import Path

from app.scraper.config import KEYFOOD_BANNERS, get_keyfood_session_path

DATA_DIR = Path(__file__).parent.parent / "data"


async def main():
    from playwright.async_api import async_playwright

    banner_names = ", ".join(KEYFOOD_BANNERS.keys())
    parser = argparse.ArgumentParser(
        description="Save a browser session for a KeyFood banner",
    )
    parser.add_argument(
        "--banner", type=str,
        help=f"banner to save session for ({banner_names})",
    )
    parser.add_argument(
        "--list", action="store_true",
        help="list available banners and exit",
    )
    args = parser.parse_args()

    if args.list:
        print("Available banners:")
        for key, b in KEYFOOD_BANNERS.items():
            print(f"  {key:15s}  {b.store_locator_url}")
        return

    if not args.banner:
        parser.error(f"--banner is required (options: {banner_names})")

    if args.banner not in KEYFOOD_BANNERS:
        parser.error(f"unknown banner: {args.banner} (options: {banner_names})")

    banner = KEYFOOD_BANNERS[args.banner]
    session_path = DATA_DIR / get_keyfood_session_path(args.banner)
    DATA_DIR.mkdir(exist_ok=True)

    async with async_playwright() as p:
        browser = await p.chromium.launch(
            headless=False,
            args=["--disable-blink-features=AutomationControlled"],
        )
        context = await browser.new_context()
        page = await context.new_page()

        print(f"opening {banner.name} store locator...")
        print()
        print("  1. search your zip code")
        print("  2. click 'Set as my store' for your preferred store")
        print()
        print("the script will auto-detect when a store is selected.\n")

        await page.goto(banner.store_locator_url, wait_until="networkidle")

        # poll get-session-store until a store is set (up to 120 seconds)
        check_url = f"/store/{banner.site_id}/en/store-locator/get-session-store"
        for i in range(120):
            try:
                response = await page.evaluate("""
                    async (url) => {
                        const r = await fetch(url);
                        if (!r.ok) return null;
                        const data = await r.json();
                        return data.keyStoreNum || null;
                    }
                """, check_url)
                if response:
                    print(f"\nstore {response} selected!")
                    break
            except Exception:
                pass
            await page.wait_for_timeout(1000)
        else:
            print("ERROR: timed out waiting for store selection.")
            await browser.close()
            return

        await context.storage_state(path=str(session_path))
        print(f"session saved to: {session_path}")
        print("you can close this browser window now.")

        await browser.close()


if __name__ == "__main__":
    asyncio.run(main())
