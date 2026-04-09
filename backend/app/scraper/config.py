"""
multi-store, multi-category scrape configuration.

shoprite:
    category_id and url_path come from shoprite.com. browse to a category with
    your store selected and extract from the URL:
    /sm/planning/rsid/{store_id}/categories/{url_path}-id-{category_id}?f=Breadcrumb:...

keyfood banners (marketplace, key food, superfresh, food universe, etc.):
    all keyfood cooperative banners share the same SAP Hybris platform with
    identical department slugs and DOM structure. only the subdomain and
    site path differ:
        https://{subdomain}.keyfood.com/store/{site_id}/en/c/dept/dept-{store_id}-{slug}

    sessions (cookies) are per-subdomain, so each banner needs its own
    session file saved via save_keyfood_session.py --banner <name>.
"""

from dataclasses import dataclass, field
from urllib.parse import quote


@dataclass
class CategoryConfig:
    """a shoprite product category to scrape."""
    name: str
    category_id: str
    url_path: str
    breadcrumb: str
    slug: str


@dataclass
class KeyFoodCategoryConfig:
    """a keyfood department to scrape (shared across all banners)."""
    name: str
    slug: str


# keep alias for backwards compat with db_writer import
MarketplaceCategoryConfig = KeyFoodCategoryConfig


@dataclass
class KeyFoodBanner:
    """a keyfood cooperative banner (marketplace, key food, superfresh, etc.)."""
    name: str
    subdomain: str
    site_id: str
    chain: str

    @property
    def base_url(self) -> str:
        return f"https://{self.subdomain}.keyfood.com/store/{self.site_id}/en"

    @property
    def store_locator_url(self) -> str:
        return f"{self.base_url}/store-locator"

    @property
    def domain(self) -> str:
        return f"https://{self.subdomain}.keyfood.com"


KEYFOOD_BANNERS: dict[str, KeyFoodBanner] = {
    "marketplace": KeyFoodBanner(
        name="Marketplace",
        subdomain="marketplace",
        site_id="marketplace",
        chain="marketplace",
    ),
    "keyfood": KeyFoodBanner(
        name="Key Food",
        subdomain="keyfoodstores",
        site_id="keyfoodstores",
        chain="keyfood",
    ),
    # "superfresh": KeyFoodBanner(
    #     name="SuperFresh",
    #     subdomain="superfresh",
    #     site_id="superfresh",
    #     chain="superfresh",
    # ),
}


@dataclass
class StoreInfo:
    """a store location (works for any chain)."""
    store_id: str
    zip_code: str
    name: str
    chain: str = "shoprite"
    banner: str = ""  # key in KEYFOOD_BANNERS, empty for non-keyfood stores
    lat: float = 0.0
    lng: float = 0.0
    address: str = ""

STORES = [
    StoreInfo(store_id="218", zip_code="11230", name="ShopRite Brooklyn",
              lat=40.62771, lng=-73.97597,
              address="1080 McDonald Ave, Brooklyn, NY 11230"),
    StoreInfo(store_id="204", zip_code="11356", name="ShopRite Queens",
              lat=40.78331, lng=-73.83506,
              address="133-11 20th Ave, College Point, NY 11356"),
]

CATEGORIES = [
    CategoryConfig(
        name="Milk",
        category_id="520592",
        url_path="dairy/milk",
        breadcrumb="grocery/dairy/milk",
        slug="milk",
    ),
    CategoryConfig(
        name="Water",
        category_id="520161",
        url_path="beverages/water",
        breadcrumb="grocery/beverages/water",
        slug="water",
    ),
    CategoryConfig(
        name="Yogurt",
        category_id="520598",
        url_path="dairy/yogurt",
        breadcrumb="grocery/dairy/yogurt",
        slug="yogurt",
    ),
    CategoryConfig(
        name="Bread",
        category_id="520567",
        url_path="bread-bakery/bread",
        breadcrumb="grocery/bread & bakery/bread",
        slug="bread",
    ),
    CategoryConfig(
        name="Chicken",
        category_id="519882",
        url_path="meat/chicken",
        breadcrumb="grocery/meat/chicken",
        slug="chicken",
    ),
    CategoryConfig(
        name="Turkey",
        category_id="519886",
        url_path="meat/turkey",
        breadcrumb="grocery/meat/turkey",
        slug="turkey",
    ),
    CategoryConfig(
        name="Cereal",
        category_id="520744",
        url_path="breakfast-cereal/cereal",
        breadcrumb="grocery/breakfast & cereal/cereal",
        slug="cereal",
    ),
    CategoryConfig(
        name="Eggs",
        category_id="520591",
        url_path="dairy/eggs",
        breadcrumb="grocery/dairy/eggs",
        slug="eggs",
    ),
    CategoryConfig(
        name="Cheese",
        category_id="520599",
        url_path="dairy/cheese",
        breadcrumb="grocery/dairy/cheese",
        slug="cheese",
    ),
    CategoryConfig(
        name="Fresh Fruit",
        category_id="520537",
        url_path="produce/fresh-fruit",
        breadcrumb="grocery/produce/fresh fruit",
        slug="fresh-fruit",
    ),
    CategoryConfig(
        name="Fresh Vegetables",
        category_id="520538",
        url_path="produce/fresh-vegetables",
        breadcrumb="grocery/produce/fresh vegetables",
        slug="fresh-vegetables",
    ),
    CategoryConfig(
        name="Pasta, Rice & Grains",
        category_id="520626",
        url_path="pantry/pasta-rice-grains",
        breadcrumb="grocery/pantry/pasta, rice & grains",
        slug="pasta-rice-grains",
    ),
    CategoryConfig(
        name="Chips",
        category_id="520166",
        url_path="snacks/chips",
        breadcrumb="grocery/snacks/chips",
        slug="chips",
    ),
    CategoryConfig(
        name="Canned & Packaged Foods",
        category_id="520625",
        url_path="pantry/canned-packaged-foods",
        breadcrumb="grocery/pantry/canned & packaged foods",
        slug="canned-packaged-foods",
    ),
    CategoryConfig(
        name="Frozen Vegetables",
        category_id="520614",
        url_path="frozen/frozen-vegetables",
        breadcrumb="grocery/frozen/frozen vegetables",
        slug="frozen-vegetables",
    ),
]


KEYFOOD_STORES = [
    StoreInfo(
        store_id="2138",
        zip_code="11215",
        name="Marketplace Brooklyn",
        chain="marketplace",
        banner="marketplace",
        lat=40.66374, lng=-73.99061,
        address="615 5th Ave, Brooklyn, NY 11215",
    ),
    StoreInfo(
        store_id="2496",
        zip_code="11215",
        name="Urban Market Brooklyn",
        chain="keyfood",
        banner="keyfood",
        lat=40.66706, lng=-73.98784,
        address="509 5th Ave, Brooklyn, NY 11215",
    ),
    StoreInfo(
        store_id="2765",
        zip_code="11215",
        name="K-Slope Marketplace Brooklyn",
        chain="keyfood",
        banner="keyfood",
        lat=40.66907, lng=-73.98528,
        address="329 9th St, Brooklyn, NY 11215",
    ),
    StoreInfo(
        store_id="1770",
        zip_code="11215",
        name="Windsor Farms Market Brooklyn",
        chain="keyfood",
        banner="keyfood",
        lat=40.65676, lng=-73.97769,
        address="589 Prospect Ave, Brooklyn, NY 11215",
    ),
]

# department slugs are shared across all keyfood banners.
# to discover more: browse any banner's departments page.
KEYFOOD_CATEGORIES = [
    KeyFoodCategoryConfig(name="Bakery", slug="bakery"),
    KeyFoodCategoryConfig(name="Beverages", slug="beverages"),
    KeyFoodCategoryConfig(name="Breakfast", slug="breakfast"),
    KeyFoodCategoryConfig(name="Deli", slug="deli"),
    KeyFoodCategoryConfig(name="Frozen", slug="frozen"),
    KeyFoodCategoryConfig(name="International", slug="international"),
    KeyFoodCategoryConfig(name="Meat and Seafood", slug="meatandseafood"),
    KeyFoodCategoryConfig(name="Pantry", slug="pantry"),
    KeyFoodCategoryConfig(name="Produce", slug="produce"),
    KeyFoodCategoryConfig(name="Refrigerated", slug="refrigerated"),
    KeyFoodCategoryConfig(name="Snacks", slug="snacks"),
]


def build_browse_url(store: StoreInfo, category: CategoryConfig) -> str:
    """
    construct the full shoprite browse URL for a store + category.

    example output:
    https://www.shoprite.com/sm/planning/rsid/204/categories/dairy/milk-id-520592?f=Breadcrumb%3Agrocery%2Fdairy%2Fmilk
    """
    encoded_breadcrumb = quote(category.breadcrumb, safe="")
    return (
        f"https://www.shoprite.com/sm/planning/rsid/{store.store_id}"
        f"/categories/{category.url_path}-id-{category.category_id}"
        f"?f=Breadcrumb%3A{encoded_breadcrumb}"
    )


def build_keyfood_url(
    banner: KeyFoodBanner,
    store: StoreInfo,
    category: KeyFoodCategoryConfig,
) -> str:
    """
    construct a keyfood category URL for any banner + store + department.

    example output:
    https://marketplace.keyfood.com/store/marketplace/en/c/dept/dept-2138-refrigerated
    https://keyfoodstores.keyfood.com/store/keyFood/en/c/dept/dept-1264-refrigerated
    """
    return f"{banner.base_url}/c/dept/dept-{store.store_id}-{category.slug}"


def get_keyfood_session_path(banner_key: str) -> str:
    """return the session file name for a given banner."""
    return f"{banner_key}_session.json"
