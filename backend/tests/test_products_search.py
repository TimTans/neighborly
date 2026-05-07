"""tests for the slim GET /products/search endpoint."""

from fastapi.testclient import TestClient

from app.main import app
from app.routes import products as products_route
from app.services import product_service

client = TestClient(app)


def test_search_route_returns_slim_rows(monkeypatch):
    async def fake_slim(query=None, page=1, page_size=50):
        return {
            "data": [
                {
                    "id": "p1",
                    "name": "Apple",
                    "brand": "Granny",
                    "image_url": None,
                    "unit_size": "1 lb",
                    "upc": "0001",
                    "category_slug": "fresh-fruit",
                    "best_price": 1.99,
                    "best_price_store_name": "ShopRite",
                    "contains_dairy": False,
                    "contains_peanuts": False,
                    "contains_shellfish": False,
                    "contains_wheat": False,
                }
            ],
            "count": 1,
        }

    monkeypatch.setattr(products_route.product_service, "search_products_slim", fake_slim)

    resp = client.get("/products/search", params={"q": "apple"})
    assert resp.status_code == 200
    body = resp.json()
    assert body["count"] == 1
    assert body["data"][0]["best_price"] == 1.99
    assert body["data"][0]["best_price_store_name"] == "ShopRite"
    assert "product_nutrition" not in body["data"][0]
    assert "store_products" not in body["data"][0]


def test_to_slim_row_picks_lowest_in_stock_price():
    row = {
        "id": "p1",
        "name": "Milk",
        "brand": "Brand",
        "image_url": None,
        "unit_size": "1 gal",
        "upc": "0002",
        "product_categories": {"slug": "milk"},
        "store_products": [
            {"price": 4.99, "sale_price": None, "in_stock": True, "stores": {"name": "A"}},
            {"price": 5.49, "sale_price": 3.99, "in_stock": True, "stores": {"name": "B"}},
            {"price": 2.99, "sale_price": None, "in_stock": False, "stores": {"name": "C"}},
        ],
        "product_nutrition": {
            "contains_dairy": True,
            "contains_peanuts": False,
            "contains_shellfish": False,
            "contains_wheat": False,
        },
    }

    slim = product_service._to_slim_row(row)
    assert slim["best_price"] == 3.99
    assert slim["best_price_store_name"] == "B"
    assert slim["contains_dairy"] is True
    assert slim["category_slug"] == "milk"


def test_to_slim_row_handles_missing_data():
    row = {
        "id": "p1",
        "name": "Mystery",
        "brand": None,
        "image_url": None,
        "unit_size": "1 ea",
        "upc": None,
        "product_categories": None,
        "store_products": [],
        "product_nutrition": None,
    }

    slim = product_service._to_slim_row(row)
    assert slim["best_price"] is None
    assert slim["best_price_store_name"] is None
    assert slim["category_slug"] is None
    assert slim["contains_dairy"] is None
