"""tests for the GET /products/{id}/price-history endpoint and service."""

from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.routes import products as products_route
from app.services import product_service

client = TestClient(app)


class _FakeQuery:
    """tiny chainable stand-in for the supabase query builder.

    only models the calls used by get_price_history. records the filters that
    were applied so tests can assert them.
    """

    def __init__(self, rows):
        self.rows = rows
        self.filters: dict = {}

    def select(self, *_args, **_kwargs):  return self
    def order(self, *_args, **_kwargs):   return self
    def limit(self, *_args, **_kwargs):   return self

    def eq(self, key, value):
        self.filters[f"eq:{key}"] = value
        return self

    def in_(self, key, value):
        self.filters[f"in:{key}"] = list(value)
        return self

    def gte(self, key, value):
        self.filters[f"gte:{key}"] = value
        return self

    def execute(self):
        class R:
            pass
        r = R()
        r.data = self.rows
        return r


class _FakeSupabase:
    """routes table() calls by name to pre-canned _FakeQuery instances."""

    def __init__(self, tables: dict):
        self.tables = tables
        self.last_queries: dict[str, _FakeQuery] = {}

    def table(self, name):
        rows = self.tables.get(name, [])
        q = _FakeQuery(rows)
        self.last_queries[name] = q
        return q


@pytest.mark.asyncio
async def test_get_price_history_groups_by_store_and_orders():
    sp_rows = [
        {"id": "sp1", "store_id": "store-a",
         "stores": {"id": "store-a", "name": "Key Food", "chain": "keyfood"}},
        {"id": "sp2", "store_id": "store-b",
         "stores": {"id": "store-b", "name": "ShopRite", "chain": "shoprite"}},
    ]
    history_rows = [
        {"store_product_id": "sp1", "price": 4.99, "sale_price": None,
         "recorded_at": "2026-04-01T00:00:00Z"},
        {"store_product_id": "sp1", "price": 5.49, "sale_price": 3.99,
         "recorded_at": "2026-04-15T00:00:00Z"},
        {"store_product_id": "sp2", "price": 4.50, "sale_price": None,
         "recorded_at": "2026-04-10T00:00:00Z"},
    ]
    fake = _FakeSupabase({
        "store_products": sp_rows,
        "store_product_price_history": history_rows,
    })

    with patch("app.services.product_service.get_supabase", return_value=fake):
        series = await product_service.get_price_history("prod-1", days=90)

    by_store = {s["store_name"]: s for s in series}
    assert set(by_store.keys()) == {"Key Food", "ShopRite"}
    # Key Food has 2 points and is sorted first (more points first)
    assert series[0]["store_name"] == "Key Food"
    assert len(series[0]["points"]) == 2
    assert series[0]["points"][0]["price"] == 4.99
    assert series[0]["points"][1]["sale_price"] == 3.99
    assert by_store["ShopRite"]["points"][0]["price"] == 4.50

    # the store_id filter should NOT have been applied
    sp_q = fake.last_queries["store_products"]
    assert "eq:store_id" not in sp_q.filters
    assert sp_q.filters.get("eq:product_id") == "prod-1"


@pytest.mark.asyncio
async def test_get_price_history_filters_by_store_id():
    sp_rows = [{"id": "sp1", "store_id": "store-a",
                "stores": {"id": "store-a", "name": "Key Food", "chain": "keyfood"}}]
    history_rows = [
        {"store_product_id": "sp1", "price": 1.99, "sale_price": None,
         "recorded_at": "2026-04-01T00:00:00Z"},
    ]
    fake = _FakeSupabase({
        "store_products": sp_rows,
        "store_product_price_history": history_rows,
    })

    with patch("app.services.product_service.get_supabase", return_value=fake):
        series = await product_service.get_price_history(
            "prod-1", days=30, store_id="store-a"
        )

    assert len(series) == 1
    assert series[0]["store_name"] == "Key Food"

    sp_q = fake.last_queries["store_products"]
    assert sp_q.filters.get("eq:store_id") == "store-a"


@pytest.mark.asyncio
async def test_get_price_history_returns_empty_when_no_store_products():
    fake = _FakeSupabase({"store_products": [], "store_product_price_history": []})

    with patch("app.services.product_service.get_supabase", return_value=fake):
        series = await product_service.get_price_history("prod-x")

    assert series == []


@pytest.mark.asyncio
async def test_get_price_history_skips_stores_with_no_points():
    """a store_product with no recent history rows should not appear."""
    sp_rows = [
        {"id": "sp1", "store_id": "store-a",
         "stores": {"id": "store-a", "name": "Key Food", "chain": "keyfood"}},
        {"id": "sp2", "store_id": "store-b",
         "stores": {"id": "store-b", "name": "ShopRite", "chain": "shoprite"}},
    ]
    history_rows = [
        {"store_product_id": "sp1", "price": 4.99, "sale_price": None,
         "recorded_at": "2026-04-01T00:00:00Z"},
    ]
    fake = _FakeSupabase({
        "store_products": sp_rows,
        "store_product_price_history": history_rows,
    })

    with patch("app.services.product_service.get_supabase", return_value=fake):
        series = await product_service.get_price_history("prod-1")

    assert len(series) == 1
    assert series[0]["store_name"] == "Key Food"


@pytest.mark.asyncio
async def test_get_price_history_clamps_days():
    """days values are clamped to [1, 365] to bound query cost."""
    sp_rows = [{"id": "sp1", "store_id": "store-a",
                "stores": {"id": "store-a", "name": "Key Food", "chain": "keyfood"}}]
    fake = _FakeSupabase({"store_products": sp_rows, "store_product_price_history": []})

    with patch("app.services.product_service.get_supabase", return_value=fake):
        await product_service.get_price_history("prod-1", days=10_000)
        cutoff_huge = fake.last_queries["store_product_price_history"].filters["gte:recorded_at"]

        await product_service.get_price_history("prod-1", days=0)
        cutoff_zero = fake.last_queries["store_product_price_history"].filters["gte:recorded_at"]

    # huge days clamps to 365 (older cutoff), zero clamps to 1 (recent cutoff)
    assert cutoff_huge < cutoff_zero


def test_price_history_route_returns_data_envelope(monkeypatch):
    async def fake_history(product_id, days=90, store_id=None):
        assert product_id == "prod-1"
        assert days == 30
        assert store_id == "store-a"
        return [{"store_id": "store-a", "store_name": "Key Food",
                 "store_chain": "keyfood", "points": []}]

    monkeypatch.setattr(products_route.product_service, "get_price_history", fake_history)

    resp = client.get(
        "/products/prod-1/price-history",
        params={"days": 30, "store_id": "store-a"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body == {
        "data": [{"store_id": "store-a", "store_name": "Key Food",
                  "store_chain": "keyfood", "points": []}],
    }


def test_price_history_route_rejects_out_of_bounds_days():
    resp = client.get("/products/prod-1/price-history", params={"days": 9999})
    assert resp.status_code == 422
    resp = client.get("/products/prod-1/price-history", params={"days": 0})
    assert resp.status_code == 422
