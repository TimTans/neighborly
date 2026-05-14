"""crowdsourced price reports service.

users on the route screen can flag an inaccurate price. reports are stored
in store_product_price_reports but do not auto-update canonical prices.
"""

from datetime import datetime, timedelta, timezone

from fastapi import HTTPException

from app.core.supabase import get_supabase


REPORTS_PER_USER_PER_DAY = 40


async def submit_report(
    user_id: str,
    store_product_id: str,
    reported_price: float,
    photo_path: str | None,
) -> dict:
    """insert a price report. caller is responsible for auth.

    raises HTTPException 404 if store_product_id doesn't exist.
    raises HTTPException 400 if photo_path is set but doesn't belong to user_id.
    raises HTTPException 429 if the user has hit the daily report limit.
    """
    sb = get_supabase()

    sp_check = sb.table("store_products").select("id").eq(
        "id", store_product_id
    ).execute()
    if not sp_check.data:
        raise HTTPException(status_code=404, detail="store_product not found")

    if photo_path is not None and not photo_path.startswith(f"{user_id}/"):
        raise HTTPException(
            status_code=400,
            detail="photo_path must be under your user folder",
        )

    cutoff = (datetime.now(timezone.utc) - timedelta(hours=24)).isoformat()
    recent = sb.table("store_product_price_reports").select(
        "id", count="exact"
    ).eq("user_id", user_id).gte("created_at", cutoff).execute()
    if (recent.count or 0) >= REPORTS_PER_USER_PER_DAY:
        raise HTTPException(status_code=429, detail="daily report limit reached")

    insert_result = sb.table("store_product_price_reports").insert({
        "store_product_id": store_product_id,
        "user_id": user_id,
        "reported_price": reported_price,
        "photo_path": photo_path,
    }).execute()

    row = (insert_result.data or [{}])[0]
    return {"id": row.get("id"), "created_at": row.get("created_at")}


async def get_summary(pairs: list[dict]) -> list[dict]:
    """aggregate open reports for a batch of (product_id, store_id) pairs.

    pairs with zero reports are omitted from the response.
    """
    if not pairs:
        return []

    sb = get_supabase()

    product_ids = list({p["product_id"] for p in pairs})
    store_ids = list({p["store_id"] for p in pairs})

    sp_result = sb.table("store_products").select(
        "id, product_id, store_id"
    ).in_("product_id", product_ids).in_("store_id", store_ids).execute()

    requested = {(p["product_id"], p["store_id"]) for p in pairs}
    sp_to_pair: dict[str, tuple[str, str]] = {}
    for row in sp_result.data or []:
        key = (row["product_id"], row["store_id"])
        if key in requested:
            sp_to_pair[row["id"]] = key

    if not sp_to_pair:
        return []

    sp_ids = list(sp_to_pair.keys())
    reports = sb.table("store_product_price_reports").select(
        "store_product_id, reported_price, created_at, status"
    ).in_("store_product_id", sp_ids).eq("status", "open").execute()

    grouped: dict[str, list[dict]] = {}
    for row in reports.data or []:
        grouped.setdefault(row["store_product_id"], []).append(row)

    summaries: list[dict] = []
    for sp_id, rows in grouped.items():
        if not rows:
            continue
        latest = max(rows, key=lambda r: r["created_at"])
        product_id, store_id = sp_to_pair[sp_id]
        summaries.append({
            "product_id": product_id,
            "store_id": store_id,
            "count": len(rows),
            "latest_reported_price": latest["reported_price"],
            "latest_reported_at": latest["created_at"],
        })
    return summaries
