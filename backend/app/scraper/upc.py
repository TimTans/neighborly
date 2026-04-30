"""shared upc/gtin validation for all scrapers."""

import re

_UPC_RE = re.compile(r"^\d{8,14}$")
_KNOWN_PREFIXES = ("UPC-", "KEY-", "SKU-", "ITEM-")


def _validate_upc(raw: str | None) -> str | None:
    """
    strip known store-internal prefixes and validate the result is a real GTIN.

    real GTINs are 8-14 digits (EAN-8 through GTIN-14).
    returns None for internal codes (KEY-..., SKU-...), non-numeric values,
    wrong-length strings, or empty/None input.
    """
    if not raw:
        return None
    upper = raw.upper()
    for prefix in _KNOWN_PREFIXES:
        if upper.startswith(prefix):
            raw = raw[len(prefix):]
            break
    return raw if _UPC_RE.match(raw) else None
