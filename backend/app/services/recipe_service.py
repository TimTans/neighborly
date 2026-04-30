"""recipe generation service backed by an LLM provider."""

from __future__ import annotations

import json

import httpx
from pydantic import BaseModel, Field, ValidationError

from app.core.config import settings


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


class GenerateRecipeResponse(BaseModel):
    title: str
    summary: str
    why_it_matches: list[str]
    prep_minutes: int
    cook_minutes: int
    servings: int
    ingredients: list[str]
    steps: list[str]
    nutrition_notes: list[str]


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
            "items": {"type": "string"},
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


def _build_prompt(request: GenerateRecipeRequest) -> str:
    diet = ", ".join(request.dietary_preferences) or "none"
    avoids = ", ".join(request.avoid_ingredients) or "none"

    nutrition_lines: list[str] = []
    if request.nutrition_targets:
        for label, value in request.nutrition_targets.model_dump().items():
            if value:
                nutrition_lines.append(f"- {label}: {value}")
    nutrition = "\n".join(nutrition_lines) or "- none"

    return (
        "Create one home-cookable recipe suggestion for a grocery shopper.\n"
        "The response must be valid JSON matching the provided schema.\n"
        "Use realistic ingredients and concise instructions.\n"
        "If the user asks for restrictive nutrition goals, keep the recipe plausible\n"
        "and explain how it fits in nutrition_notes and why_it_matches.\n\n"
        f"Dietary preferences: {diet}\n"
        f"Avoid ingredients: {avoids}\n"
        f"Nutrition targets:\n{nutrition}\n"
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


async def generate_recipe(
    request: GenerateRecipeRequest,
    client: httpx.AsyncClient | None = None,
) -> GenerateRecipeResponse:
    if not settings.OPENAI_API_KEY:
        raise RecipeGenerationError("Recipe generation is not configured.")

    payload = {
        "model": settings.OPENAI_MODEL,
        "input": [
            {
                "role": "system",
                "content": [
                    {
                        "type": "input_text",
                        "text": (
                            "You generate a single recipe suggestion that honors dietary "
                            "preferences, avoided ingredients, and nutrition targets."
                        ),
                    }
                ],
            },
            {
                "role": "user",
                "content": [
                    {
                        "type": "input_text",
                        "text": _build_prompt(request),
                    }
                ],
            },
        ],
        "text": {
            "format": {
                "type": "json_schema",
                "name": "recipe_suggestion",
                "strict": True,
                "schema": RECIPE_JSON_SCHEMA,
            }
        },
    }

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
        return GenerateRecipeResponse.model_validate(json.loads(output_text))
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
