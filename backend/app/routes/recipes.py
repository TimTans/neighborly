from fastapi import APIRouter, HTTPException

from app.services.recipe_service import (
    GenerateRecipeRequest,
    GenerateRecipeResponse,
    RecipeGenerationError,
    generate_recipe,
)

router = APIRouter(prefix="/recipes", tags=["recipes"])


@router.post("/generate", response_model=GenerateRecipeResponse)
async def generate_recipe_suggestion(body: GenerateRecipeRequest):
    try:
        return await generate_recipe(body)
    except RecipeGenerationError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
