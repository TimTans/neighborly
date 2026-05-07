import httpx
import pytest

from app.services import recipe_service
from app.services.recipe_service import (
    GenerateRecipeRequest,
    NutritionTargets,
    RecipeGenerationError,
    _enrich_ingredients,
    _format_catalog_lines,
    generate_recipe,
)


def _fake_catalog() -> list[dict]:
    return [
        {
            "id": "rice-1",
            "name": "Carolina Long Grain Brown Rice",
            "brand": "Carolina",
            "category_slug": "pasta-rice-grains",
            "best_price": 3.49,
            "best_price_store_name": "ShopRite",
            "contains_dairy": False,
            "contains_peanuts": False,
            "contains_shellfish": False,
            "contains_wheat": False,
        },
        {
            "id": "chick-1",
            "name": "Goya No Salt Added Chickpeas",
            "brand": "Goya",
            "category_slug": "canned-packaged-foods",
            "best_price": 1.79,
            "best_price_store_name": "KeyFood",
            "contains_dairy": False,
            "contains_peanuts": False,
            "contains_shellfish": False,
            "contains_wheat": False,
        },
        {
            "id": "milk-1",
            "name": "Whole Milk",
            "brand": "Lactaid",
            "category_slug": "milk",
            "best_price": 4.99,
            "best_price_store_name": "ShopRite",
            "contains_dairy": True,
            "contains_peanuts": False,
            "contains_shellfish": False,
            "contains_wheat": False,
        },
    ]


async def _fake_catalog_fetcher() -> list[dict]:
    return _fake_catalog()


def _ok_response_handler(payload_text: str):
    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path.endswith("/responses")
        return httpx.Response(
            200,
            json={
                "output": [
                    {
                        "content": [
                            {"type": "output_text", "text": payload_text}
                        ]
                    }
                ]
            },
        )

    return handler


@pytest.mark.asyncio
async def test_generate_recipe_parses_grounded_ingredients(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(recipe_service.settings, "OPENAI_API_KEY", "test-key")

    model_response = """
    {
      "title": "Lemon Chickpea Rice Bowl",
      "summary": "A bright vegan bowl with herbs and vegetables.",
      "why_it_matches": ["Fully vegan", "Uses low-sodium pantry ingredients"],
      "prep_minutes": 15,
      "cook_minutes": 20,
      "servings": 4,
      "ingredients": [
        {"name": "Brown rice", "quantity": "1 cup", "product_id": "rice-1", "from_catalog": true},
        {"name": "Chickpeas", "quantity": "1 can", "product_id": "chick-1", "from_catalog": true},
        {"name": "Salt", "quantity": "to taste", "product_id": null, "from_catalog": false}
      ],
      "steps": ["Cook the rice.", "Saute the vegetables.", "Assemble and serve."],
      "nutrition_notes": ["Choose no-salt-added chickpeas to keep sodium down."]
    }
    """

    client = httpx.AsyncClient(
        transport=httpx.MockTransport(_ok_response_handler(model_response)),
        base_url="https://api.openai.com/v1",
    )
    request = GenerateRecipeRequest(
        dietary_preferences=["vegan"],
        nutrition_targets=NutritionTargets(sodium="1500 mg/day"),
    )

    recipe = await generate_recipe(
        request,
        client=client,
        catalog_fetcher=_fake_catalog_fetcher,
    )

    assert recipe.title == "Lemon Chickpea Rice Bowl"
    assert len(recipe.ingredients) == 3

    rice = recipe.ingredients[0]
    assert rice.from_catalog is True
    assert rice.product_id == "rice-1"
    assert rice.price == 3.49
    assert rice.store_name == "ShopRite"

    salt = recipe.ingredients[2]
    assert salt.from_catalog is False
    assert salt.product_id is None
    assert salt.price is None

    await client.aclose()


@pytest.mark.asyncio
async def test_generate_recipe_drops_unknown_product_ids(monkeypatch: pytest.MonkeyPatch):
    """if the model hallucinates a product_id, server treats it as pantry."""
    monkeypatch.setattr(recipe_service.settings, "OPENAI_API_KEY", "test-key")

    model_response = """
    {
      "title": "Test",
      "summary": "Test recipe.",
      "why_it_matches": ["test"],
      "prep_minutes": 5,
      "cook_minutes": 10,
      "servings": 2,
      "ingredients": [
        {"name": "Mystery Item", "quantity": "1", "product_id": "fake-id-not-in-catalog", "from_catalog": true}
      ],
      "steps": ["Cook."],
      "nutrition_notes": []
    }
    """

    client = httpx.AsyncClient(
        transport=httpx.MockTransport(_ok_response_handler(model_response)),
        base_url="https://api.openai.com/v1",
    )

    recipe = await generate_recipe(
        GenerateRecipeRequest(),
        client=client,
        catalog_fetcher=_fake_catalog_fetcher,
    )

    assert recipe.ingredients[0].from_catalog is False
    assert recipe.ingredients[0].product_id is None
    assert recipe.ingredients[0].price is None

    await client.aclose()


@pytest.mark.asyncio
async def test_generate_recipe_rejects_invalid_output(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(recipe_service.settings, "OPENAI_API_KEY", "test-key")

    client = httpx.AsyncClient(
        transport=httpx.MockTransport(_ok_response_handler('{"title":"Incomplete"}')),
        base_url="https://api.openai.com/v1",
    )

    with pytest.raises(RecipeGenerationError):
        await generate_recipe(
            GenerateRecipeRequest(),
            client=client,
            catalog_fetcher=_fake_catalog_fetcher,
        )

    await client.aclose()


def test_format_catalog_lines_filters_avoided_allergens():
    request = GenerateRecipeRequest(avoid_ingredients=["dairy"])
    lines = _format_catalog_lines(_fake_catalog(), request)
    assert "milk-1" not in lines  # Whole Milk dropped because contains_dairy
    assert "rice-1" in lines
    assert "chick-1" in lines


def test_format_catalog_lines_includes_price_and_store():
    request = GenerateRecipeRequest()
    lines = _format_catalog_lines(_fake_catalog(), request)
    assert "rice-1|Carolina Long Grain Brown Rice|Carolina|pasta-rice-grains|$3.49|ShopRite" in lines
    assert "chick-1|Goya No Salt Added Chickpeas|Goya|canned-packaged-foods|$1.79|KeyFood" in lines


def test_enrich_ingredients_uses_canonical_price_not_model_price():
    """server must overwrite any price the LLM emits with the catalog truth."""
    raw = [
        {
            "name": "Brown rice",
            "quantity": "1 cup",
            "product_id": "rice-1",
            "from_catalog": True,
            "price": 99.99,  # model lied
            "store_name": "Wrong Store",  # model lied
        }
    ]
    enriched = _enrich_ingredients(raw, _fake_catalog())
    assert enriched[0]["price"] == 3.49
    assert enriched[0]["store_name"] == "ShopRite"
