from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.routes import fdc, products, stores, categories, grocery_lists, scraper, routes

app = FastAPI(
    title="Neighborly API",
    version="0.2.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(fdc.router)
app.include_router(products.router)
app.include_router(stores.router)
app.include_router(categories.router)
app.include_router(grocery_lists.router)
app.include_router(scraper.router)
app.include_router(routes.router)


@app.get("/health", tags=["health"])
def health_check():
    return {"status": "ok"}
