from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field

from app.auth.deps import require_auth
from app.services import price_report_service

router = APIRouter(prefix="/price-reports", tags=["price-reports"])


class SubmitRequest(BaseModel):
    store_product_id: str
    reported_price: float = Field(ge=0, lt=10000)
    photo_path: str | None = None


class SubmitResponse(BaseModel):
    id: str
    created_at: str | None = None


class PairRequest(BaseModel):
    product_id: str
    store_id: str


class SummaryRequest(BaseModel):
    pairs: list[PairRequest] = Field(max_length=200)


class SummaryItem(BaseModel):
    product_id: str
    store_id: str
    count: int
    latest_reported_price: float
    latest_reported_at: str


class SummaryResponse(BaseModel):
    summaries: list[SummaryItem]


@router.post("", response_model=SubmitResponse, status_code=201)
async def submit_price_report(
    body: SubmitRequest,
    user_id: str = Depends(require_auth),
):
    """submit a crowdsourced price correction.

    rate-limited to 40 reports per user per 24h.
    """
    result = await price_report_service.submit_report(
        user_id=user_id,
        store_product_id=body.store_product_id,
        reported_price=body.reported_price,
        photo_path=body.photo_path,
    )
    return SubmitResponse(id=result["id"], created_at=result.get("created_at"))


@router.post("/summary", response_model=SummaryResponse)
async def get_price_report_summary(body: SummaryRequest):
    """batch-fetch open-report counts for (product_id, store_id) pairs.

    pairs with zero reports are omitted from the response.
    """
    summaries = await price_report_service.get_summary(
        [p.model_dump() for p in body.pairs]
    )
    return SummaryResponse(summaries=summaries)
