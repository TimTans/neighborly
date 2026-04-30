from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    ENVIRONMENT: str = "development"
    CORS_ORIGINS: list[str] = ["http://localhost:3000", "http://localhost:8081"]
    FDC_API_KEY: str = ""
    SUPABASE_URL: str = ""
    SUPABASE_SERVICE_KEY: str = ""
    OPENAI_API_KEY: str = ""
    OPENAI_MODEL: str = "gpt-4.1-mini"
    OPENAI_BASE_URL: str = "https://api.openai.com/v1"

    model_config = {"env_file": ".env", "case_sensitive": True}


settings = Settings()
