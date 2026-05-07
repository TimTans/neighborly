"""recipe generation service backed by an LLM provider.

recipes are grounded in the actual product catalog: we inject a slim view
of in-stock products into the prompt and ask the model to return ingredients
keyed to product_id where possible. server-side post-processing fills in
canonical price/store from the catalog so the model never invents prices.
"""

from __future__ import annotations

import json
from typing import Awaitable, Callable

import httpx
from pydantic import BaseModel, Field, ValidationError

from app.core.config import settings
from app.services.product_service import fetch_recipe_catalog


class RecipeGenerationError(RuntimeError):
    """Raised when recipe generation fails or returns invalid output."""


class NutritionTargets(BaseModel):
    cholesterol: str | None = None
    sodium: str | None = None
    sugar: str | None = None


class GenerateRecipeRequest(BaseModel):
    dietary_preferences: list[str] = Field(default_factory=list)
    avoid_ingredients: list[str] = Field(default_factory=list)
    nutrition_targets: NutritionTargets | None = None
    user_lat: float | None = None
    user_lng: float | None = None
    max_radius_miles: float | None = None


class RecipeIngredient(BaseModel):
    name: str
    quantity: str
    product_id: str | None = None
    from_catalog: bool
    store_name: str | None = None
    price: float | None = None
    image_url: str | None = None
    category_slug: str | None = None


class GenerateRecipeResponse(BaseModel):
    title: str
    summary: str
    why_it_matches: list[str]
    prep_minutes: int
    cook_minutes: int
    servings: int
    ingredients: list[RecipeIngredient]
    steps: list[str]
    nutrition_notes: list[str]


# json_schema for the LLM. note: ingredients carry product_id as nullable — the
# model returns null for pantry items (salt, oil, water, spices). server-side
# we ignore any price/store the model emits and refill from the catalog.
RECIPE_JSON_SCHEMA = {
    "type": "object",
    "properties": {
        "title": {"type": "string"},
        "summary": {"type": "string"},
        "why_it_matches": {
            "type": "array",
            "items": {"type": "string"},
        },
        "prep_minutes": {"type": "integer"},
        "cook_minutes": {"type": "integer"},
        "servings": {"type": "integer"},
        "ingredients": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "quantity": {"type": "string"},
                    "product_id": {"type": ["string", "null"]},
                    "from_catalog": {"type": "boolean"},
                },
                "required": ["name", "quantity", "product_id", "from_catalog"],
                "additionalProperties": False,
            },
        },
        "steps": {
            "type": "array",
            "items": {"type": "string"},
        },
        "nutrition_notes": {
            "type": "array",
            "items": {"type": "string"},
        },
    },
    "required": [
        "title",
        "summary",
        "why_it_matches",
        "prep_minutes",
        "cook_minutes",
        "servings",
        "ingredients",
        "steps",
        "nutrition_notes",
    ],
    "additionalProperties": False,
}


def _format_catalog_lines(catalog: list[dict], prefs: GenerateRecipeRequest) -> str:
    """
    format catalog as compact pipe-delimited lines, one per product.
    line: id|name|brand|category|$price|store|allergens

    pre-filters out products that obviously violate the user's hard avoidances
    (e.g. dairy when avoid_ingredients includes "dairy"). this keeps the prompt
    smaller and prevents the model from picking violating items.
    """
    avoids = {a.lower() for a in prefs.avoid_ingredients}
    lines: list[str] = []

    for p in catalog:
        if "dairy" in avoids and p.get("contains_dairy"):
            continue
        if "peanuts" in avoids and p.get("contains_peanuts"):
            continue
        if "shellfish" in avoids and p.get("contains_shellfish"):
            continue
        if "wheat" in avoids and p.get("contains_wheat"):
            continue

        brand = p.get("brand") or ""
        category = p.get("category_slug") or ""
        price = p.get("best_price")
        store = p.get("best_price_store_name") or ""
        line = f"{p['id']}|{p['name']}|{brand}|{category}|${price:.2f}|{store}"
        lines.append(line)

    return "\n".join(lines)


def _build_prompt(request: GenerateRecipeRequest, catalog: list[dict]) -> str:
    diet = ", ".join(request.dietary_preferences) or "none"
    avoids = ", ".join(request.avoid_ingredients) or "none"

    nutrition_lines: list[str] = []
    if request.nutrition_targets:
        for label, value in request.nutrition_targets.model_dump().items():
            if value:
                nutrition_lines.append(f"- {label}: {value}")
    nutrition = "\n".join(nutrition_lines) or "- none"

    catalog_block = _format_catalog_lines(catalog, request)

    return (
        "Create one home-cookable recipe suggestion for a grocery shopper.\n"
        "Use ingredients from the AVAILABLE CATALOG below where possible.\n"
        "For each catalog ingredient, return product_id (the leading uuid on\n"
        "that catalog line) and set from_catalog=true. For pantry essentials\n"
        "not in the catalog (salt, pepper, oil, water, common spices), return\n"
        "product_id=null and from_catalog=false. Prefer catalog items over\n"
        "pantry items when both could apply. Do NOT invent product_ids.\n\n"
        "The ingredient `name` field MUST be a short, human-readable cooking\n"
        "name like 'black beans', 'olive oil', or 'low-sodium chicken broth'.\n"
        "Do NOT copy the brand or the full uppercase product name from the\n"
        "catalog into `name` — the product_id already links the exact product,\n"
        "so `name` should read naturally inside a recipe.\n\n"
        "Each ingredient must include a quantity (e.g. '1 cup', '2 tbsp', '1 lb').\n"
        "Keep the recipe practical and the steps concise.\n\n"
        f"Dietary preferences: {diet}\n"
        f"Avoid ingredients: {avoids}\n"
        f"Nutrition targets:\n{nutrition}\n\n"
        "AVAILABLE CATALOG (format: id|name|brand|category|$price|store):\n"
        f"{catalog_block}\n"
    )


def _extract_output_text(payload: dict) -> str:
    if isinstance(payload.get("output_text"), str) and payload["output_text"].strip():
        return payload["output_text"]

    texts: list[str] = []
    for item in payload.get("output", []):
        for content in item.get("content", []):
            content_type = content.get("type")
            if content_type == "output_text" and content.get("text"):
                texts.append(content["text"])
            if content_type == "refusal":
                raise RecipeGenerationError("Recipe generation request was refused by the model.")

    output_text = "".join(texts).strip()
    if not output_text:
        raise RecipeGenerationError("Recipe generation returned an empty response.")
    return output_text


def _enrich_ingredients(
    raw_ingredients: list[dict],
    catalog: list[dict],
) -> list[dict]:
    """
    fill in price/store_name for ingredients flagged from_catalog by looking up
    their product_id in the catalog. ignore any price/store the model returned.
    drops product_ids that don't exist in the catalog (treat as pantry).
    """
    by_id = {p["id"]: p for p in catalog}
    enriched: list[dict] = []

    for ing in raw_ingredients:
        product_id = ing.get("product_id")
        from_catalog = bool(ing.get("from_catalog"))

        if from_catalog and product_id and product_id in by_id:
            p = by_id[product_id]
            enriched.append(
                {
                    "name": ing["name"],
                    "quantity": ing.get("quantity", ""),
                    "product_id": product_id,
                    "from_catalog": True,
                    "store_name": p.get("best_price_store_name"),
                    "price": p.get("best_price"),
                    "image_url": p.get("image_url"),
                    "category_slug": p.get("category_slug"),
                }
            )
        else:
            # pantry, or model returned an unknown id
            enriched.append(
                {
                    "name": ing["name"],
                    "quantity": ing.get("quantity", ""),
                    "product_id": None,
                    "from_catalog": False,
                    "store_name": None,
                    "price": None,
                    "image_url": None,
                    "category_slug": None,
                }
            )

    return enriched


# type alias so tests can swap in a fake catalog fetcher
CatalogFetcher = Callable[[], Awaitable[list[dict]]]


async def generate_recipe(
    request: GenerateRecipeRequest,
    client: httpx.AsyncClient | None = None,
    catalog_fetcher: CatalogFetcher | None = None,
) -> GenerateRecipeResponse:
    if not settings.OPENAI_API_KEY:
        raise RecipeGenerationError("Recipe generation is not configured.")

    if catalog_fetcher is not None:
        catalog = await catalog_fetcher()
    else:
        catalog = await fetch_recipe_catalog(
            user_lat=request.user_lat,
            user_lng=request.user_lng,
            max_radius_miles=request.max_radius_miles,
        )

    payload: dict = {
        "model": settings.OPENAI_MODEL,
        "input": [
            {
                "role": "system",
                "content": [
                    {
                        "type": "input_text",
                        "text": (
                            "You generate a single recipe suggestion that honors dietary "
                            "preferences, avoided ingredients, and nutrition targets. "
                            "When given an AVAILABLE CATALOG of products, prefer those "
                            "products and return their product_id."
                        ),
                    }
                ],
            },
            {
                "role": "user",
                "content": [
                    {
                        "type": "input_text",
                        "text": _build_prompt(request, catalog),
                    }
                ],
            },
        ],
        "reasoning": {"effort": "none"},
        "text": {
            "format": {
                "type": "json_schema",
                "name": "recipe_suggestion",
                "strict": True,
                "schema": RECIPE_JSON_SCHEMA,
            }
        },
    }

    if settings.OPENAI_REASONING_EFFORT:
        payload["reasoning"] = {"effort": settings.OPENAI_REASONING_EFFORT}

    owns_client = client is None
    client = client or httpx.AsyncClient(
        base_url=settings.OPENAI_BASE_URL,
        timeout=30.0,
        headers={
            "Authorization": f"Bearer {settings.OPENAI_API_KEY}",
            "Content-Type": "application/json",
        },
    )

    try:
        response = await client.post("/responses", json=payload)
        response.raise_for_status()
        response_payload = response.json()
        output_text = _extract_output_text(response_payload)
        raw = json.loads(output_text)
        raw["ingredients"] = _enrich_ingredients(raw.get("ingredients", []), catalog)
        return GenerateRecipeResponse.model_validate(raw)
    except httpx.HTTPStatusError as exc:
        raise RecipeGenerationError(
            f"Recipe generation failed with status {exc.response.status_code}."
        ) from exc
    except (json.JSONDecodeError, ValidationError) as exc:
        raise RecipeGenerationError("Recipe generation returned invalid structured output.") from exc
    except httpx.HTTPError as exc:
        raise RecipeGenerationError("Recipe generation request failed.") from exc
    finally:
        if owns_client:
            await client.aclose()
