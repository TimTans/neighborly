# Android Catch-Up — Stage 1: Core Trip Parity

Date: 2026-05-02
Predecessor: `docs/android-ios-catchup-plan.md` (2026-04-23)

## Objective

Bring the Android app to functional parity with iOS for the **primary user journey**: search → list → optimize route → see real map → swap items. After Stage 1, an Android user can plan and view the same shopping trip an iOS user can. Stage 1 closes Epic C in full and finishes the remaining Epic B items, plus lays foundational infrastructure (location, image loading, map SDK, connectivity) that Stage 2 also depends on.

## Current State (entering Stage 1)

Reference audit conducted 2026-05-02 against `main`.

### Completed since the April 23 plan

- Epic A (API foundation): `A1`, `A2`, `A3`, `A4` — all **DONE**
  - `KtorNeighborlyApi` implements `searchProducts`, `getProduct`, `optimizeRoute`, `getAlternatives` with typed `Result<T>` and sealed `NeighborlyApiError`
  - DTOs: `Product`, `ProductCategory`, `StoreProduct`, `Store`, `ProductSearchResponse`, `OptimizedRoute`, `RouteStop`, `RouteItem`
  - `API_BASE_URL` loaded from `android/.env` via `BuildConfig`, with `MissingBaseUrl` error if blank
- Epic B (grocery list):
  - `B1` Local persistence — **DONE** (`SharedPreferencesGroceryListLocalDataSource` JSON store)
  - `B2` Repository refactor — **PARTIAL** (repository pattern in place, but route state not shared, no Create Route button)
  - `B3` Backend search — **DONE** (debounced `/products?q=…` with loading / no-results / error states)
- Epic D:
  - `D2` Local preference persistence — **DONE** (`SharedPreferencesPreferenceRepository`)

### Outstanding gaps Stage 1 will close

| ID | Gap | iOS reference |
|----|-----|---------------|
| 1 | Route repository wired to backend | `APIService.optimizeRoute` (APIService.swift:69-99) |
| 2 | Shared route state across List ↔ Route screens | `RouteState` injected from `MainTabView` |
| 3 | "Create Route" button on grocery list | `GroceryListView.swift:485-514` |
| 4 | Location permissions + last-known location | `LocationHelper` (GroceryListView.swift:40-97) |
| 5 | Real map: pins, polyline, ETA badges | `MapboxRouteMap.swift`, `MapboxDirectionsService.swift` |
| 6 | Swap alternatives flow | `SwapSheet` (RouteView.swift:417-528) |
| 7 | Product images via Coil | `AsyncImage` everywhere |
| 8 | Multi-store pricing UI in product / item detail sheets | `ProductDetailSheet`, `ItemDetailSheet` (GroceryListView.swift:756-1036) |
| 9 | Sale strikethrough + out-of-stock label | All product surfaces |
| 10 | Network reconnect → price refresh | `NetworkMonitor.didReconnect` (GroceryListView.swift:9-36) |
| 11 | Send `mode`, `max_stops`, `max_radius_miles` to `/routes/optimize` | GroceryListView.swift:536-544 |

## Workstreams

### S1.1 — Wire `RouteRepository` to backend

**Goal:** replace `UnavailableRouteRepository` with a real implementation calling `NeighborlyApi.optimizeRoute` and `getAlternatives`.

**Changes:**

- New `ApiRouteRepository` implementing `RouteRepository`
- Extend `OptimizeRouteRequest` (`data/model/RouteModels.kt:45-53`) with `mode: String?`, `maxStops: Int?`, `maxRadiusMiles: Double?` (all `@SerialName`-mapped)
- Map `OptimizationPriority` → backend mode (`LowestCost` → `"cost"`, `ShortestRoute` → `"distance"`, `FastestTrip` → `"stops"`) — mirror iOS `Priority.backendMode` (PreferencesView.swift:23-29)

**Files touched:**

- `android/app/src/main/java/com/example/android/data/repository/RouteRepository.kt`
- `android/app/src/main/java/com/example/android/data/api/NeighborlyApi.kt`
- `android/app/src/main/java/com/example/android/data/model/RouteModels.kt`

**Acceptance:**

- Calling `RouteRepository.optimizeRoute` reaches the backend and returns a parsed `OptimizedRoute` or a typed error
- The 4xx / 5xx body from the backend surfaces in `NeighborlyApiError.Server.responseBody`
- Unit test: round-trip JSON parse for an optimize-route response sample

### S1.2 — Share route state across screens

**Goal:** the same `RouteViewModel` (or scoped `RouteState`) is consumed by `GroceryListScreen` and `RouteScreen`, so creating a route on one screen is visible on the other.

**Changes:**

- Hoist `RouteViewModel` to activity scope (or introduce a shared `RouteStateHolder` in DI)
- `RouteScreen` reads from the same instance instead of constructing its own (`AppScaffold.kt:59`)
- Add empty / loading / error rendering branches (UI is already drafted)

**Files touched:**

- `MainActivity.kt` or DI module
- `ui/AppScaffold.kt`
- `ui/route/RouteScreen.kt`
- `ui/lists/GroceryListScreen.kt`

**Acceptance:**

- Optimizing a route from the grocery list updates `RouteScreen` without re-instantiating the view model
- Navigating away and back to `RouteScreen` preserves the optimized route in memory

### S1.3 — "Create Route" button on grocery list

**Goal:** primary action button at the bottom of the grocery list that triggers route optimization.

**Changes:**

- Bottom-anchored CTA, enabled only when the list contains items with non-null `productId`
- Tapping calls `ShopperViewModel.createRoute()`, which collects product IDs, requests location, and invokes `RouteRepository.optimizeRoute(productIds, lat?, lng?, mode, maxStops, maxRadius)`
- On success, navigate to the Route tab; on `noRoute = true`, surface the reason in a snackbar; on transport error, show a snackbar with retry

**Files touched:**

- `viewmodel/shopper/ShopperViewModel.kt`
- `ui/lists/GroceryListScreen.kt`
- Navigation host (route to `Route` tab)

**Acceptance:**

- Button is hidden when list has no priced items
- A successful optimize switches tabs and shows real data on `RouteScreen`
- An empty-product-IDs case shows a user-facing error rather than calling the API

### S1.4 — Location permissions + last-known location

**Goal:** request location at the moment the user taps "Create Route", with a 3 s timeout and silent fallback to "no location".

**Changes:**

- Add to `AndroidManifest.xml`:
  - `<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />`
  - `<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />`
- New `LocationHelper` using `FusedLocationProviderClient` (Google Play Services Location)
- `requestLocation()` returns `Result<LatLng?>` with a 3 s timeout (matches iOS `LocationHelper`:60)
- Permission rationale dialog on first request; denial returns `null` and the optimize proceeds without coords

**Files touched:**

- `android/app/src/main/AndroidManifest.xml`
- New `data/location/LocationHelper.kt`
- `viewmodel/shopper/ShopperViewModel.kt`

**Acceptance:**

- Granting permission attaches `user_lat` / `user_lng` to the optimize call
- Denying or timing out still produces a route (without location tie-breaker)
- Permission state survives process death

### S1.5 — Add map SDK and render route

**Goal:** real map on `RouteScreen` with numbered store pins, user puck, walking polyline, and per-leg ETA badges, matching iOS `MapboxRouteMap.swift`.

**Decision:** **Mapbox**. Rationale: iOS already uses Mapbox Directions for ETA; reusing the same waypoint contract avoids a divergent map experience between platforms, and the `MapboxDirectionsService` response shape is portable. Google Maps is the alternative if Mapbox token provisioning is a blocker — call out in the implementation kickoff.

**Changes:**

- Add Mapbox Maps SDK + Directions to `gradle/libs.versions.toml` and `app/build.gradle.kts`
- Add `MAPBOX_ACCESS_TOKEN` to `android/.env.example`, load via `BuildConfig`
- New `ui/route/MapboxRouteMap.kt` Compose wrapper:
  - Streets style, `Puck2D` with heading
  - `PointAnnotationGroup` of numbered pins (1, 2, 3, …)
  - `PolylineAnnotation` for the walking route, fall back to straight lines if Directions API fails (mirror MapboxRouteMap.swift:60)
  - ETA badge per pin, computed `int(max(1, duration_seconds / 60))`
- New `data/maps/MapboxDirectionsService.kt` calling Directions API v5 (walking, geojson)
- Replace the placeholder card on `RouteScreen` with the real map

**Files touched:**

- `gradle/libs.versions.toml`, `app/build.gradle.kts`
- `android/.env.example`
- `AndroidManifest.xml` (Mapbox metadata)
- `ui/route/RouteScreen.kt`
- New `ui/route/MapboxRouteMap.kt`
- New `data/maps/MapboxDirectionsService.kt`

**Acceptance:**

- Pins are numbered in route order and tappable to focus a single stop
- Polyline renders between stops, including from user location when available
- ETAs render as "X min" badges; falling back to straight lines when Directions returns an error does not crash

### S1.6 — Swap alternatives flow

**Goal:** tapping a route item opens the alternatives sheet, selecting one updates the persisted grocery list and re-optimizes.

**Changes:**

- Wire the existing swap dialog (`RouteScreen.kt:411-453`) to `RouteRepository.getSwapAlternatives(productId)`
- On selection: `GroceryListRepository.replaceProduct(oldUpc, newProduct)` then `RouteRepository.optimizeRoute(...)` with the updated product list
- Loading / error / empty branches in the sheet

**Files touched:**

- `viewmodel/route/RouteViewModel.kt`
- `viewmodel/shopper/ShopperViewModel.kt`
- `data/repository/GroceryListRepository.kt`
- `ui/route/RouteScreen.kt`

**Acceptance:**

- Tapping any route item opens the sheet; alternatives load from `/products/:id/alternatives`
- Selecting an alternative replaces the grocery item and re-renders the route with the new totals
- Cancelling the sheet leaves the list and route unchanged

### S1.7 — Image loading via Coil

**Goal:** product images render everywhere they appear on iOS, with a category-emoji fallback.

**Changes:**

- Add `coil-compose` to dependencies
- Replace emoji-only thumbnails in: search rows, grocery item rows, route item rows, swap dialog, future detail sheets (S1.8) — wherever `imageUrl` is non-null
- `error` and `placeholder` slots use the existing `ProductCategory.emoji` fallback

**Files touched:**

- `gradle/libs.versions.toml`, `app/build.gradle.kts`
- `ui/lists/GroceryListScreen.kt`
- `ui/route/RouteScreen.kt`
- `ui/components/ProductImage.kt` (new shared composable)

**Acceptance:**

- Search rows show real product images when available
- Network errors fall back to the category emoji silently
- No image flicker on list scroll (Coil's default disk cache enabled)

### S1.8 — Product / item detail sheets

**Goal:** match iOS `ProductDetailSheet` and `ItemDetailSheet` (GroceryListView.swift:756-1036): image, brand, size, sorted multi-store prices, sale strikethrough, in-stock label, quantity controls.

**Changes:**

- Tap on a search result opens a `ProductDetailSheet` with an "Add" button (do not add directly on row tap — current behavior diverges from iOS)
- Tap on a list item opens `ItemDetailSheet` with quantity +/- and remove
- Both sheets render store-product rows sorted by effective price ascending
- Render sale price with strikethrough on the original (iOS:840-857, 988-1010)
- Out-of-stock items show a label and are visually de-emphasized

**Files touched:**

- New `ui/lists/ProductDetailSheet.kt`
- New `ui/lists/ItemDetailSheet.kt`
- `ui/lists/GroceryListScreen.kt`
- `viewmodel/shopper/ShopperViewModel.kt` (sheet state)

**Acceptance:**

- Search row tap → sheet → "Add" closes the sheet and increments / inserts the item
- Multi-store pricing visibly matches the iOS layout (price, store name, sale strikethrough, OOS)
- Sheet dismiss does not trigger an add

### S1.9 — Network reconnect → price refresh

**Goal:** when the device loses and regains connectivity, prices for items with `productId` are silently refreshed (matches iOS `NetworkMonitor.didReconnect`, GroceryListView.swift:9-36).

**Changes:**

- New `NetworkMonitor` wrapping `ConnectivityManager.NetworkCallback`, exposed as a `Flow<Boolean>`
- `ShopperViewModel` collects the flow; on a `false → true` transition it calls `repository.refreshPrices()`
- Refresh failures do not clear the list or block normal operations

**Files touched:**

- New `data/connectivity/NetworkMonitor.kt`
- `viewmodel/shopper/ShopperViewModel.kt`

**Acceptance:**

- Toggling airplane mode off triggers exactly one refresh per reconnect (debounced)
- Failure path preserves the list and shows a snackbar but does not navigate or wipe state

### S1.10 — Verification

**Manual smoke checklist:**

1. Sign in
2. Search "milk", confirm results render with images
3. Open the product detail sheet, add to list
4. Force-stop and reopen the app; confirm the list persists
5. Tap "Create Route"; deny location → route still optimizes
6. Tap "Create Route" again; grant location → route uses lat / lng
7. On the Route tab: pins numbered, polyline visible, ETA badges render
8. Tap an item, swap to an alternative; route re-optimizes with new totals
9. Toggle airplane mode on / off on the list screen; observe one refresh
10. Sign out

**Unit tests:**

- `OptimizeRouteRequest` snake_case mapping (mode, max_stops, max_radius_miles)
- `OptimizedRoute` JSON parse for happy path + `noRoute` branch
- `LocationHelper` denied-permission returns null; timeout returns null
- `OptimizationPriority.toBackendMode()` mapping
- `NetworkMonitor` emits a single reconnect event for an off → on toggle

**Files touched:** `android/app/src/test/...`

## Sequencing & Parallelization

```
S1.1 ─┐
      ├─> S1.2 ─> S1.3 ─┐
S1.4 ─┘                  ├─> S1.6
                         │
S1.5 (parallel from start, gated by S1.1) ─┘

S1.7 (parallel from start, no deps)
S1.8 (after S1.7)
S1.9 (parallel from start)
S1.10 (after all)
```

Two engineers can work S1.1 / S1.2 / S1.3 (the optimize path) while a second pair handles S1.5 (map) and S1.7 / S1.8 (images + sheets).

## Risks & Open Questions

- **Mapbox token provisioning + billing.** Coordinate with whoever owns the iOS Mapbox account before adding the SDK.
- **Backend `mode` parameter behavior.** iOS already sends `"distance"` and `"stops"` even though the backend currently only optimizes for cost. Confirm with backend that unsupported modes don't 5xx.
- **Emulator location.** Android emulator GPS is awkward without setting it via the extended controls; document the manual override in the smoke checklist.
- **Mapbox SDK size.** Adds ~15 MB to the APK. If size becomes a concern, evaluate `mapbox-maps-android` lite vs full.
- **`FusedLocationProviderClient` requires Google Play Services.** Document fallback (likely "no location, optimize without coords") for non-GMS devices.

## Stage 1 Deliverables

- All Epic C items (`C1` – `C6`) closed
- Epic B `B4` and `B5` closed
- Foundational infrastructure (location, map, image loading, connectivity) ready for Stage 2
- An Android user can complete the same end-to-end shopping trip an iOS user can

## Out of Scope (handled in Stage 2)

- Wellness violations and warnings
- Recipe generator and home recipe widget
- Preferences sync to Supabase
- Home dashboard dynamic data
- Theme cleanup, password reset, additional test coverage
