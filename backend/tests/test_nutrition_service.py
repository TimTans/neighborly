# backend/tests/test_nutrition_service.py
"""tests for nutrition_service — FDC responses are fully mocked."""

from unittest.mock import AsyncMock, patch

import pytest

from app.services.nutrition_service import (
    _parse_allergens,
    _parse_nutrients,
    _serving_size_g,
    lookup_by_upc,
)

# -- allergen parsing --

def test_parse_allergens_peanuts():
    flags = _parse_allergens("ROASTED PEANUTS, SALT, SUGAR")
    assert flags["contains_peanuts"] is True
    assert flags["contains_dairy"] is False


def test_parse_allergens_dairy_whey():
    flags = _parse_allergens("WATER, WHEY PROTEIN, SUGAR")
    assert flags["contains_dairy"] is True


def test_parse_allergens_shellfish():
    flags = _parse_allergens("SHRIMP, GARLIC, OLIVE OIL")
    assert flags["contains_shellfish"] is True


def test_parse_allergens_wheat():
    flags = _parse_allergens("ENRICHED WHEAT FLOUR, WATER, YEAST")
    assert flags["contains_wheat"] is True


def test_parse_allergens_none_triggered():
    flags = _parse_allergens("WATER, SUGAR, CITRIC ACID")
    assert flags == {
        "contains_dairy": False,
        "contains_peanuts": False,
        "contains_shellfish": False,
        "contains_wheat": False,
    }


def test_parse_allergens_none_ingredients():
    flags = _parse_allergens(None)
    assert flags == {
        "contains_dairy": None,
        "contains_peanuts": None,
        "contains_shellfish": None,
        "contains_wheat": None,
    }


# -- serving size conversion --

def test_serving_size_g_grams():
    assert _serving_size_g(28.0, "g") == 28.0


def test_serving_size_g_oz():
    assert abs(_serving_size_g(1.0, "oz") - 28.35) < 0.01


def test_serving_size_g_ml():
    assert _serving_size_g(240.0, "ml") == 240.0


def test_serving_size_g_unknown_unit():
    assert _serving_size_g(1.0, "cup") is None


# -- nutrient extraction --

def test_parse_nutrients_extracts_sodium():
    food_nutrients = [
        {"nutrientId": 1093, "value": 150.0, "nutrientName": "Sodium"},
        {"nutrientId": 1003, "value": 5.0, "nutrientName": "Protein"},
    ]
    result = _parse_nutrients(food_nutrients)
    assert result["sodium_mg"] == 150.0
    assert result["protein_g"] == 5.0


def test_parse_nutrients_missing_nutrient_is_none():
    result = _parse_nutrients([])
    assert result["sodium_mg"] is None
    assert result["cholesterol_mg"] is None


# -- lookup_by_upc (mocked FDC) --

_MOCK_FDC_RESPONSE = {
    "foods": [
        {
            "fdcId": 999999,
            "gtinUpc": "073296046304",
            "servingSize": 28.0,
            "servingSizeUnit": "g",
            "servingsPerContainer": 8.0,
            "ingredients": "PEANUTS, SALT",
            "foodNutrients": [
                {"nutrientId": 1093, "value": 140.0},
                {"nutrientId": 1253, "value": 0.0},
                {"nutrientId": 2000, "value": 1.0},
                {"nutrientId": 1008, "value": 190.0},
                {"nutrientId": 1003, "value": 7.0},
                {"nutrientId": 1004, "value": 16.0},
                {"nutrientId": 1005, "value": 6.0},
                {"nutrientId": 1079, "value": 2.0},
            ],
        }
    ]
}


@pytest.mark.asyncio
async def test_lookup_by_upc_found():
    with patch(
        "app.services.nutrition_service.fdc.search_foods",
        new=AsyncMock(return_value=_MOCK_FDC_RESPONSE),
    ):
        result = await lookup_by_upc("073296046304")

    assert result is not None
    assert result["fdc_id"] == 999999
    assert result["sodium_mg"] == 140.0
    assert result["contains_peanuts"] is True
    assert result["serving_size_g"] == 28.0


@pytest.mark.asyncio
async def test_lookup_by_upc_no_match():
    response = {"foods": [{"fdcId": 1, "gtinUpc": "000000000000"}]}
    with patch(
        "app.services.nutrition_service.fdc.search_foods",
        new=AsyncMock(return_value=response),
    ):
        result = await lookup_by_upc("073296046304")

    assert result is None


@pytest.mark.asyncio
async def test_lookup_by_upc_empty_results():
    with patch(
        "app.services.nutrition_service.fdc.search_foods",
        new=AsyncMock(return_value={"foods": []}),
    ):
        result = await lookup_by_upc("073296046304")

    assert result is None
