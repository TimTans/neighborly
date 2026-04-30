# Android / iOS Catch-Up Plan

Date: 2026-04-23

This plan compares the current Android app against the iOS implementation and breaks the Android catch-up work into topologically sorted tickets. The epics are designed so separate agents can work in parallel once their listed blockers are complete.

## Current State

### iOS Has

- Supabase auth through `AuthController`, with a session-driven app shell.
- Backend API client in `APIService.swift` for product search, product detail, route optimization, and product alternatives.
- Codable API/domain models in `Models/Product.swift`.
- Local grocery list persistence through SwiftData `GroceryListItem`.
- Grocery list screen with debounced backend search, product detail sheet, multi-store prices, quantity/delete controls, price refresh, network reconnect refresh, and route creation.
- Shared `RouteState` between list and route tabs.
- Route screen driven by real optimized route data, including total cost, store stops, items, missing items, and swap alternatives.
- Mapbox map with numbered pins, user puck, walking route polyline, and ETA badges.
- Preferences UI, mostly local UI state.
- Home dashboard UI, mostly static/demo state.

### Android Has

- Supabase auth in `LoginViewModel` and `SupabaseClient.kt`.
- Main Compose shell with Home, Lists, Route, and Preferences destinations.
- Home/dashboard UI, mostly static data from `HomeViewModel`.
- Grocery list UI backed by in-memory `ShopperViewModel` sample catalog.
- Preferences UI backed by in-memory `PreferenceState`.
- Route UI backed by static `HomeViewModel.routeStops` and a map placeholder.

### Main Gaps

- No Android backend API client or DTO/domain models matching the FastAPI contract.
- No Android `API_BASE_URL` config.
- No persistent grocery list equivalent to iOS SwiftData.
- Product search is local sample data, not `/products`.
- Product detail does not show real images, category fallback, sale prices, stock status, or multi-store pricing.
- Route optimization is not connected to `/routes/optimize`.
- Route tab is static and not shared with list tab state.
- No product alternatives / swap flow.
- No location tie-breaker when optimizing routes.
- No map route rendering.
- No price refresh or network reconnect handling.
- Preferences are not persisted or connected to Supabase/backend.

## Topological Overview

Implement in this dependency order:

1. `A1` -> `A2` -> `A3` -> `A4`
2. `B1` can start after `A3`; `B2` and later require `A4`
3. `C1` can start after `A3`; `C2` and later require `A4` plus `B2`
4. `D1` can start immediately; `D2` depends on `B2`; `D3` depends on `C3`; `D4` depends on `A4`

Recommended parallelization:

- Agent 1 owns Epic A first. This is the critical path.
- Agent 2 can prepare Epic B persistence/UI refactor after `A3` model shape is stable.
- Agent 3 can prepare Epic C state shell and route UI components while waiting on API methods.
- Agent 4 can work Epic D polish and preference persistence that does not touch the same files as B/C where possible.

## Epic A: Android API Foundation

Goal: create the Android equivalent of iOS `APIService.swift` and `Models/Product.swift`.

### `A1` Add Android Backend Config

Dependencies: none

Files likely touched:

- `android/app/build.gradle.kts`
- `android/.env.example`
- `android/app/src/main/java/com/example/android/data/*`

Acceptance criteria:

- `API_BASE_URL` is loaded from `android/.env` into `BuildConfig`.
- `.env.example` documents `API_BASE_URL=http://10.0.2.2:8000` for emulator local backend use.
- App fails gracefully or shows a clear error if API base URL is missing.

### `A2` Add Network Stack

Dependencies: `A1`

Files likely touched:

- `android/app/build.gradle.kts`
- `android/gradle/libs.versions.toml`
- new `android/app/src/main/java/com/example/android/data/api/*`

Acceptance criteria:

- Ktor client supports JSON serialization, content negotiation, and reasonable timeouts.
- API errors are represented as typed failures instead of crashing.
- Client can be injected or replaced by a fake for view-model testing.

### `A3` Add API Models

Dependencies: `A1`

Files likely touched:

- new `android/app/src/main/java/com/example/android/data/model/*`

Acceptance criteria:

- Models match backend snake_case JSON and iOS domain model coverage:
- `Product`, `ProductCategory`, `StoreProduct`, `Store`, `ProductSearchResponse`
- `OptimizedRoute`, `RouteStop`, `RouteItem`
- Computed helpers exist for `bestPrice`, `bestPriceStoreName`, and category emoji fallback.

### `A4` Add Neighborly API Client

Dependencies: `A2`, `A3`

Files likely touched:

- `android/app/src/main/java/com/example/android/data/api/NeighborlyApi.kt`

Acceptance criteria:

- Implements:
- `searchProducts(query, page, pageSize)`
- `getProduct(id)`
- `optimizeRoute(productIds, userLat, userLng)`
- `getAlternatives(productId)`
- JSON and URL behavior match iOS `APIService.swift`.

## Epic B: Grocery List Parity

Goal: bring Android grocery list behavior up to iOS: backend search, persisted list, product details, and price refresh.

### `B1` Add Local Grocery List Persistence

Dependencies: `A3`

Files likely touched:

- `android/app/build.gradle.kts`
- new `android/app/src/main/java/com/example/android/data/local/*`
- `android/app/src/main/java/com/example/android/viewmodel/shopper/ShopperViewModel.kt`

Acceptance criteria:

- Grocery list survives app restart.
- Stored item fields cover iOS `GroceryListItem`: name, price, unit size, UPC, quantity, date added, product ID.
- Increment, decrement, delete, and add-or-increment by UPC work from persistence.

### `B2` Refactor Shopper State Around Repository

Dependencies: `A4`, `B1`

Files likely touched:

- `ShopperViewModel.kt`
- new repository classes

Acceptance criteria:

- `ShopperViewModel` no longer owns sample catalog as source of truth.
- UI state includes loading/error/search results/list items.
- Repository exposes product search, persisted list operations, product detail, and price refresh.

### `B3` Replace Sample Search With Backend Search

Dependencies: `B2`

Files likely touched:

- `GroceryListScreen.kt`
- `ShopperViewModel.kt`

Acceptance criteria:

- Search queries with at least 2 characters call `/products?q=...` with debounce.
- UI shows loading, no-results, and network-error states.
- Search result rows show product name, brand, unit size, best price, best store, and image/category fallback.

### `B4` Add Product and Item Detail Sheets

Dependencies: `B2`, `B3`

Files likely touched:

- `GroceryListScreen.kt`
- reusable product UI components if extracted

Acceptance criteria:

- Tapping a search result opens a product detail bottom sheet before adding.
- Tapping a list item opens an item detail bottom sheet.
- Detail sheets show image/category emoji, brand, size, quantity, and sorted prices by store.
- Sale prices, original strikethrough prices, and out-of-stock labels match iOS behavior.

### `B5` Add Price Refresh and Connectivity Handling

Dependencies: `B2`, `B4`

Files likely touched:

- `ShopperViewModel.kt`
- Android connectivity helper/repository

Acceptance criteria:

- On grocery screen load, current prices refresh for items with product IDs.
- When network reconnects, prices refresh again.
- Failures do not clear the list or block normal list operations.

## Epic C: Route Optimization, Map, and Swap

Goal: make Android route behavior data-driven like iOS.

### `C1` Add Shared Route State

Dependencies: `A3`

Files likely touched:

- `AppScaffold.kt`
- new `RouteState` / route view model
- `RouteScreen.kt`
- `GroceryListScreen.kt`

Acceptance criteria:

- Route state is shared between the list and route destinations.
- State includes optimized route, loading, and error.
- Route screen can render empty state when no route exists.

### `C2` Create Route From Grocery List

Dependencies: `A4`, `B2`, `C1`

Files likely touched:

- `GroceryListScreen.kt`
- `ShopperViewModel.kt`
- route view model/repository

Acceptance criteria:

- Grocery list screen has a `Create Route` button when list contains product IDs.
- Button calls `/routes/optimize`.
- On success, Android navigates to the Route tab with route state populated.
- Empty product ID cases show a user-facing error.

### `C3` Render Real Optimized Route

Dependencies: `C2`

Files likely touched:

- `RouteScreen.kt`

Acceptance criteria:

- Route tab renders API `totalCost`, item count, store count, stops, store subtotals, item rows, sale prices, and `itemsNotFound`.
- Static `HomeViewModel.routeStops` is no longer used by route screen.
- Route error and loading states are visible.

### `C4` Add Location Tie-Breaker

Dependencies: `C2`

Files likely touched:

- `AndroidManifest.xml`
- route/list view model or location helper

Acceptance criteria:

- App requests coarse/fine location when optimizing.
- Last known location is passed as `user_lat` and `user_lng` when available.
- Denied/missing location falls back to optimizing without location.

### `C5` Add Android Map Route Rendering

Dependencies: `C3`

Files likely touched:

- `build.gradle.kts`
- `AndroidManifest.xml`
- `RouteScreen.kt`
- new map component/service

Acceptance criteria:

- Route screen shows real store pins from optimized route coordinates.
- A route line is shown between user location and stops, or between stops if user location is unavailable.
- Numbered stop markers match route stop order.
- Product decision needed: use Mapbox for iOS parity or Google Maps to match the existing Android placeholder.

### `C6` Add Swap Alternatives Flow

Dependencies: `B2`, `C3`

Files likely touched:

- `RouteScreen.kt`
- `ShopperViewModel.kt`
- route repository/view model

Acceptance criteria:

- Tapping a route item opens an alternatives sheet.
- Alternatives come from `/products/{product_id}/alternatives`.
- Selecting an alternative updates the persisted grocery list item and re-optimizes the route.

## Epic D: Preferences, Home Data, and Platform Polish

Goal: finish catch-up areas that are less blocking but improve parity and maintainability.

### `D1` Extract Shared Theme and UI Components

Dependencies: none

Files likely touched:

- new `android/app/src/main/java/com/example/android/ui/theme/*`
- existing Compose screens

Acceptance criteria:

- Neighborly colors, typography choices, card shapes, and common spacing are centralized.
- Screens do not each define duplicate color constants.
- No functional behavior changes.

### `D2` Persist Preferences Locally

Dependencies: `B2`

Files likely touched:

- `PreferencesScreen.kt`
- `ShopperViewModel.kt`
- new preferences repository/DataStore

Acceptance criteria:

- Route priority, transport modes, max distance, max stops, wellness fields, dietary filters, and avoid-list survive app restart.
- State restoration does not block app startup.

### `D3` Connect Preferences to Route Requests Where Supported

Dependencies: `C3`, `D2`

Files likely touched:

- route repository/view model
- preferences repository

Acceptance criteria:

- Android route request can read current preferences.
- Current backend only supports lowest-cost strategy and optional location, so unsupported preferences are kept local and documented.
- UI copy avoids implying unsupported modes are active unless backend support exists.

### `D4` Bring Home Dashboard Off Static Data Where Feasible

Dependencies: `A4`, `B2`, `C3`

Files likely touched:

- `HomeViewModel.kt`
- `HomeScreen.kt`

Acceptance criteria:

- Home uses persisted grocery list item count and latest route summary when available.
- Static/demo fallback remains for missing data.
- Start Trip opens the Route tab only when an optimized route exists.

### `D5` Add Verification Coverage

Dependencies: `A4`, `B2`, `C3`

Files likely touched:

- `android/app/src/test/*`
- view model/repository test fakes

Acceptance criteria:

- Unit tests cover API JSON parsing for product search and route optimize responses.
- Unit tests cover add-or-increment, decrement/delete, route success/error, and swap/reoptimize behavior.
- Manual smoke checklist covers login, search, add, persist, optimize, route render, swap, and sign out.

## Suggested Agent Ownership

### Agent 1: API Foundation

Owns Epic A. This agent should finish before other agents wire real backend behavior. It should avoid touching UI except for config error surfacing if needed.

### Agent 2: Grocery List

Owns Epic B. This agent can start persistence scaffolding after `A3`, then wire backend behavior after `A4`.

### Agent 3: Route and Map

Owns Epic C. This agent can start `C1` while waiting on `A4`, but should not implement mock route behavior that later needs removal.

### Agent 4: Preferences and Polish

Owns Epic D. This agent can start with theme extraction immediately, then preferences persistence and home wiring after repository/state contracts stabilize.

## Implementation Notes

- Prefer keeping Android model names aligned with iOS and backend JSON names to reduce translation mistakes.
- The backend returns snake_case JSON. Kotlin serialization models should use `@SerialName` or a snake_case Json naming strategy if available.
- Emulator access to a local FastAPI server should use `http://10.0.2.2:8000`, not `localhost`.
- Existing Android sample data should be kept only as fallback/demo state after repositories are in place.
- Avoid mixing route state into `HomeViewModel`; route behavior should have its own route state/repository.
- If choosing Mapbox for Android parity, add `MAPBOX_ACCESS_TOKEN` config separately from `API_BASE_URL`.
