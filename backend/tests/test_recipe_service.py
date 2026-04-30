import httpx
import pytest

from app.services import recipe_service
from app.services.recipe_service import (
    GenerateRecipeRequest,
    NutritionTargets,
    RecipeGenerationError,
    generate_recipe,
)


@pytest.mark.asyncio
async def test_generate_recipe_parses_structured_response(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(recipe_service.settings, "OPENAI_API_KEY", "test-key")

    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/responses"
        payload = request.read().decode()
        assert "vegan" in payload
        assert "low sodium" not in payload
        return httpx.Response(
            200,
            json={
                "output": [
                    {
                        "content": [
                            {
                                "type": "output_text",
                                "text": """
                                {
                                  "title": "Lemon Chickpea Rice Bowl",
                                  "summary": "A bright vegan bowl with herbs and vegetables.",
                                  "why_it_matches": ["Fully vegan", "Uses low-sodium pantry ingredients"],
                                  "prep_minutes": 15,
                                  "cook_minutes": 20,
                                  "servings": 4,
                                  "ingredients": ["1 cup brown rice", "1 can no-salt chickpeas"],
                                  "steps": ["Cook the rice.", "Saute the vegetables.", "Assemble and serve."],
                                  "nutrition_notes": ["Choose no-salt-added chickpeas to keep sodium down."]
                                }
                                """,
                            }
                        ]
                    }
                ]
            },
        )

    client = httpx.AsyncClient(
        transport=httpx.MockTransport(handler),
        base_url="https://api.openai.com/v1",
    )
    request = GenerateRecipeRequest(
        dietary_preferences=["vegan"],
        nutrition_targets=NutritionTargets(sodium="1500 mg/day"),
    )

    recipe = await generate_recipe(request, client=client)

    assert recipe.title == "Lemon Chickpea Rice Bowl"
    assert recipe.why_it_matches == ["Fully vegan", "Uses low-sodium pantry ingredients"]
    await client.aclose()


@pytest.mark.asyncio
async def test_generate_recipe_rejects_invalid_output(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(recipe_service.settings, "OPENAI_API_KEY", "test-key")

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "output": [
                    {
                        "content": [
                            {
                                "type": "output_text",
                                "text": '{"title":"Incomplete"}',
                            }
                        ]
                    }
                ]
            },
        )

    client = httpx.AsyncClient(
        transport=httpx.MockTransport(handler),
        base_url="https://api.openai.com/v1",
    )

    with pytest.raises(RecipeGenerationError):
        await generate_recipe(GenerateRecipeRequest(), client=client)

    await client.aclose()
