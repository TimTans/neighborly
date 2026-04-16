# backend/tests/test_upc.py
from app.scraper.upc import _validate_upc


def test_strips_upc_prefix():
    assert _validate_upc("UPC-073296046304") == "073296046304"


def test_strips_key_prefix():
    assert _validate_upc("KEY-073296046304") == "073296046304"


def test_strips_sku_prefix():
    assert _validate_upc("SKU-073296046304") == "073296046304"


def test_strips_item_prefix():
    assert _validate_upc("ITEM-073296046304") == "073296046304"


def test_rejects_non_numeric_after_strip():
    assert _validate_upc("KEY-abc123") is None


def test_rejects_too_short():
    assert _validate_upc("1234567") is None  # 7 digits, min is 8


def test_rejects_too_long():
    assert _validate_upc("123456789012345") is None  # 15 digits, max is 14


def test_accepts_8_digit_ean8():
    assert _validate_upc("12345678") == "12345678"


def test_accepts_12_digit_upc_a():
    assert _validate_upc("012345678905") == "012345678905"


def test_accepts_14_digit_gtin():
    assert _validate_upc("00012345678905") == "00012345678905"


def test_none_input():
    assert _validate_upc(None) is None


def test_empty_string():
    assert _validate_upc("") is None


def test_plain_valid_upc_no_prefix():
    assert _validate_upc("073296046304") == "073296046304"


def test_whitespace_stripped():
    assert _validate_upc("UPC- 073296046304") is None  # space after prefix makes digits invalid


def test_lowercase_prefix_accepted():
    assert _validate_upc("upc-073296046304") == "073296046304"


def test_prefix_only_returns_none():
    assert _validate_upc("UPC-") is None
