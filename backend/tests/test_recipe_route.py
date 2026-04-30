from fastapi.testclient import TestClient

from app.main import app
from app.routes import recipes
from app.services.recipe_service import GenerateRecipeResponse


client = TestClient(app)


def test_generate_recipe_route_returns_structured_payload(monkeypatch):
    async def fake_generate_recipe(_request):
        return GenerateRecipeResponse(
            title="Herbed Lentil Soup",
            summary="A hearty vegan soup with low-sodium ingredients.",
            why_it_matches=["Vegan", "Lower sodium pantry choices"],
            prep_minutes=10,
            cook_minutes=30,
            servings=4,
            ingredients=["1 cup lentils", "4 cups unsalted vegetable broth"],
            steps=["Rinse lentils.", "Simmer all ingredients until tender."],
            nutrition_notes=["Use unsalted broth to control sodium."],
        )

    monkeypatch.setattr(recipes, "generate_recipe", fake_generate_recipe)

    response = client.post(
        "/recipes/generate",
        json={
            "dietary_preferences": ["vegan"],
            "avoid_ingredients": ["dairy"],
            "nutrition_targets": {"sodium": "1500 mg/day"},
        },
    )

    assert response.status_code == 200
    assert response.json()["title"] == "Herbed Lentil Soup"


def test_generate_recipe_route_rejects_invalid_payload():
    response = client.post("/recipes/generate", json={"nutrition_targets": "bad"})

    assert response.status_code == 422
