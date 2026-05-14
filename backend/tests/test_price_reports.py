"""tests for crowdsourced price reports."""

from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.services import price_report_service

client = TestClient(app)


class _FakeQuery:
    """chainable supabase query stand-in. matches the pattern in test_price_history.py."""

    def __init__(self, rows, count=None):
        self.rows = rows
        self.count_value = count
        self.filters: dict = {}
        self.inserted: list = []

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

    def insert(self, row):
        self.inserted.append(row)
        return self

    def execute(self):
        class R:
            pass
        r = R()
        # on insert, supabase echoes the row back (with server-assigned id and
        # created_at). simulate that by returning the canned rows from the fake
        # instead of the raw payload the service passed in.
        rows = self.rows
        # apply eq filters so the fake honors filters that the service relies on
        # for correctness (e.g. status='open'). filters that target a field not
        # present in the rows are tolerated.
        for key, value in self.filters.items():
            if key.startswith("eq:") and rows and isinstance(rows[0], dict):
                field = key.removeprefix("eq:")
                rows = [row for row in rows if row.get(field) == value or field not in row]
        r.data = rows
        r.count = self.count_value
        return r


class _FakeSupabase:
    def __init__(self, tables):
        self.tables = tables
        self.last_queries: dict[str, _FakeQuery] = {}

    def table(self, name):
        entry = self.tables.get(name, {"rows": [], "count": None})
        q = _FakeQuery(entry.get("rows", []), count=entry.get("count"))
        self.last_queries[name] = q
        return q


@pytest.mark.asyncio
async def test_submit_report_inserts_row():
    sp_id = "sp-1"
    user_id = "user-1"
    insert_target = {
        "id": "report-1",
        "store_product_id": sp_id,
        "user_id": user_id,
        "reported_price": 4.49,
        "photo_path": None,
        "created_at": "2026-05-13T18:00:00Z",
    }
    fake = _FakeSupabase({
        "store_products":              {"rows": [{"id": sp_id}]},
        "store_product_price_reports": {"rows": [insert_target], "count": 0},
    })

    with patch("app.services.price_report_service.get_supabase", return_value=fake):
        result = await price_report_service.submit_report(
            user_id=user_id,
            store_product_id=sp_id,
            reported_price=4.49,
            photo_path=None,
        )

    assert result["id"] == "report-1"
    insert_q = fake.last_queries["store_product_price_reports"]
    assert insert_q.inserted[0]["store_product_id"] == sp_id
    assert insert_q.inserted[0]["user_id"] == user_id
    assert insert_q.inserted[0]["reported_price"] == 4.49


@pytest.mark.asyncio
async def test_submit_report_404_when_store_product_missing():
    fake = _FakeSupabase({"store_products": {"rows": []}})

    with patch("app.services.price_report_service.get_supabase", return_value=fake):
        with pytest.raises(Exception) as exc:
            await price_report_service.submit_report(
                user_id="user-1",
                store_product_id="missing",
                reported_price=4.49,
                photo_path=None,
            )
    assert "404" in str(exc.value) or "not found" in str(exc.value)


@pytest.mark.asyncio
async def test_submit_report_400_when_photo_path_outside_user_folder():
    fake = _FakeSupabase({
        "store_products":              {"rows": [{"id": "sp-1"}]},
        "store_product_price_reports": {"rows": [], "count": 0},
    })

    with patch("app.services.price_report_service.get_supabase", return_value=fake):
        with pytest.raises(Exception) as exc:
            await price_report_service.submit_report(
                user_id="user-1",
                store_product_id="sp-1",
                reported_price=4.49,
                photo_path="other-user/photo.jpg",
            )
    assert "photo_path" in str(exc.value)


@pytest.mark.asyncio
async def test_submit_report_429_when_daily_limit_reached():
    fake = _FakeSupabase({
        "store_products":              {"rows": [{"id": "sp-1"}]},
        "store_product_price_reports": {"rows": [], "count": 40},
    })

    with patch("app.services.price_report_service.get_supabase", return_value=fake):
        with pytest.raises(Exception) as exc:
            await price_report_service.submit_report(
                user_id="user-1",
                store_product_id="sp-1",
                reported_price=4.49,
                photo_path=None,
            )
    assert "429" in str(exc.value) or "limit" in str(exc.value)


@pytest.mark.asyncio
async def test_submit_report_under_limit_succeeds():
    sp_id = "sp-1"
    insert_target = {"id": "report-2", "created_at": "2026-05-13T18:00:00Z"}
    fake = _FakeSupabase({
        "store_products":              {"rows": [{"id": sp_id}]},
        "store_product_price_reports": {"rows": [insert_target], "count": 39},
    })

    with patch("app.services.price_report_service.get_supabase", return_value=fake):
        result = await price_report_service.submit_report(
            user_id="user-1",
            store_product_id=sp_id,
            reported_price=4.49,
            photo_path=None,
        )
    assert result["id"] == "report-2"


@pytest.mark.asyncio
async def test_get_summary_aggregates_per_pair_and_omits_zero():
    sp_rows = [
        {"id": "sp-1", "product_id": "p-A", "store_id": "s-A"},
        {"id": "sp-2", "product_id": "p-B", "store_id": "s-B"},
        {"id": "sp-3", "product_id": "p-C", "store_id": "s-C"},
    ]
    report_rows = [
        {"store_product_id": "sp-1", "reported_price": 4.49,
         "created_at": "2026-05-12T10:00:00Z", "status": "open"},
        {"store_product_id": "sp-1", "reported_price": 4.29,
         "created_at": "2026-05-13T10:00:00Z", "status": "open"},
        {"store_product_id": "sp-2", "reported_price": 3.99,
         "created_at": "2026-05-11T10:00:00Z", "status": "open"},
        {"store_product_id": "sp-2", "reported_price": 5.99,
         "created_at": "2026-05-10T10:00:00Z", "status": "dismissed"},
    ]
    fake = _FakeSupabase({
        "store_products":              {"rows": sp_rows},
        "store_product_price_reports": {"rows": report_rows},
    })

    with patch("app.services.price_report_service.get_supabase", return_value=fake):
        result = await price_report_service.get_summary([
            {"product_id": "p-A", "store_id": "s-A"},
            {"product_id": "p-B", "store_id": "s-B"},
            {"product_id": "p-C", "store_id": "s-C"},
        ])

    by_pair = {(r["product_id"], r["store_id"]): r for r in result}
    assert ("p-C", "s-C") not in by_pair
    assert by_pair[("p-A", "s-A")]["count"] == 2
    assert by_pair[("p-A", "s-A")]["latest_reported_price"] == 4.29
    assert by_pair[("p-B", "s-B")]["count"] == 1
    assert by_pair[("p-B", "s-B")]["latest_reported_price"] == 3.99


def test_summary_endpoint_returns_only_pairs_with_reports():
    async def fake_get_summary(pairs):
        return [{
            "product_id": "p-A",
            "store_id": "s-A",
            "count": 2,
            "latest_reported_price": 4.29,
            "latest_reported_at": "2026-05-13T10:00:00Z",
        }]

    with patch.object(price_report_service, "get_summary", side_effect=fake_get_summary):
        response = client.post(
            "/price-reports/summary",
            json={"pairs": [
                {"product_id": "p-A", "store_id": "s-A"},
                {"product_id": "p-B", "store_id": "s-B"},
            ]},
        )
    assert response.status_code == 200
    body = response.json()
    assert len(body["summaries"]) == 1
    assert body["summaries"][0]["product_id"] == "p-A"


def test_submit_endpoint_requires_auth():
    # require_auth uses Header(...) so a missing Authorization header is
    # surfaced by fastapi as a 422 validation error rather than a 401.
    # an invalid (non-bearer) header value, however, hits the body of
    # require_auth and yields 401. exercise both to prove auth is enforced.
    missing = client.post(
        "/price-reports",
        json={
            "store_product_id": "sp-1",
            "reported_price": 4.49,
        },
    )
    assert missing.status_code == 422

    invalid = client.post(
        "/price-reports",
        json={
            "store_product_id": "sp-1",
            "reported_price": 4.49,
        },
        headers={"Authorization": "not-a-bearer-token"},
    )
    assert invalid.status_code == 401


def test_submit_endpoint_returns_201_with_id_and_created_at():
    # bypass require_auth by overriding the dependency, and stub the service.
    async def fake_submit_report(*, user_id, store_product_id, reported_price, photo_path):
        return {"id": "report-99", "created_at": "2026-05-13T18:00:00Z"}

    from app.auth.deps import require_auth
    app.dependency_overrides[require_auth] = lambda: "user-1"
    try:
        with patch.object(price_report_service, "submit_report", side_effect=fake_submit_report):
            response = client.post(
                "/price-reports",
                json={
                    "store_product_id": "sp-1",
                    "reported_price": 4.49,
                },
            )
    finally:
        app.dependency_overrides.pop(require_auth, None)

    assert response.status_code == 201
    body = response.json()
    assert body["id"] == "report-99"
    assert body["created_at"] == "2026-05-13T18:00:00Z"
