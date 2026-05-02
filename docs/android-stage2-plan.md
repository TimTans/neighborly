# Android Catch-Up — Stage 2: Wellness, Recipes, Preferences Sync & Polish

Date: 2026-05-02
Predecessors: `docs/android-ios-catchup-plan.md` (2026-04-23), `docs/android-stage1-plan.md` (2026-05-02)

## Objective

Complete iOS feature parity for surfaces *outside* the primary trip flow: wellness violations everywhere products appear, the recipe generator and home recipe widget, server-synced preferences, a dynamic home dashboard, plus the quality cleanup deferred from Epic D. After Stage 2, every screen and feature shipping on iOS has an Android counterpart.

## Why a Stage 2

Two new iOS features merged in late April 2026 — the **recipe generator** (PR #60, merged 2026-04-30) and **wellness violation warnings** — sit on top of, but don't block, the trip optimization path. Splitting them out of Stage 1 lets Stage 1 ship as a self-contained "trip parity" release. Stage 2 also picks up the Epic D items (`D1`, `D3`, `D4`, `D5`) that were always intended to come after the core path stabilized.

## Current State (entering Stage 2)

Stage 2 assumes Stage 1 has shipped: route repository wired, shared route state, location, Mapbox, swap flow, Coil image loading, detail sheets, network reconnect.

### Outstanding gaps Stage 2 will close

| ID | Gap | iOS reference |
|----|-----|---------------|
| 12 | `ProductNutrition` model + violation engine | `Product.swift:35-58` |
| 13 | Wellness chips on search rows + grocery items | `GroceryListView.swift:449-455` |
| 14 | `WellnessPanel` in product detail sheet | `GroceryListView.swift:1073-1162` |
| 15 | `WellnessWarningSheet` confirmation before route optimize | `GroceryListView.swift:678-693, 1166-1229` |
| 16 | Recipe model (`RecipeSuggestion`, `RecipeRequestPayload`, nutrition targets) | `Models/RecipeSuggestion.swift` |
| 17 | `/recipes/generate` endpoint on `NeighborlyApi` | `APIService.swift:116-133` |
| 18 | Featured recipe widget on Home + detail screen | `HomeView.swift:216-333`, `RecipeDetailView.swift` |
| 19 | Auto-regenerate recipe on preferences change | `HomeViewModel.swift:56-74` |
| 20 | Preferences upsert / fetch against Supabase `user_preferences` | `PreferencesService.swift` |
| 21 | Home dashboard driven by real data | `HomeView.swift` (partially live on iOS) |
| 22 | Theme finalization (D1 finish) | n/a — internal cleanup |
| 23 | Password reset, email verification UI | `LoginView.swift:56-63` |
| 24 | Test coverage for parsing + view models | iOS recipe tests (commit 437229a) |

## Workstreams

### S2.1 — `ProductNutrition` model + violation engine

**Goal:** port the iOS data model and violation logic so any product can be checked against a `PreferenceState`.

**Changes:**

- Add `ProductNutrition` to `data/model/Product.kt`, mirroring `Product.swift:35-58`:
  - Numerics: `servingSizeG`, `servingsPerContainer`, `caloriesKcal`, `proteinG`, `fatG`, `carbsG`, `fiberG`, `sodiumMg`, `cholesterolMg`, `sugarG` (all nullable)
  - Allergen flags: `containsDairy`, `containsPeanuts`, `containsShellfish`, `containsWheat` (`Boolean?`)
- New `domain/wellness/Violations.kt`:
  - Sealed `WellnessViolation`: `Allergen(name)`, `NutrientExceeded(name, actual, limit)`
  - Extension `ProductNutrition.violations(against: PreferenceState): List<WellnessViolation>`
- Cache nutrition responses by `productId` in `ShopperViewModel` (mirrors iOS `nutritionCache`, GroceryListView.swift:130). Populate from `getProduct` lazily.

**Files touched:**

- `data/model/Product.kt`
- New `domain/wellness/Violations.kt`
- `viewmodel/shopper/ShopperViewModel.kt`

**Acceptance:**

- A `Product` with `containsPeanuts = true` produces an `Allergen` violation when prefs include `avoidPeanuts`
- Nutrient violations only fire when `wellnessEnabled = true` and the field is non-empty / non-zero
- Unit tests cover both branches plus the no-violation happy path

### S2.2 — Wellness warning UI

**Goal:** the user always knows when a product violates their preferences, both at-a-glance and before committing to a route.

**Changes:**

- **Allergen chip row** on search-result rows and grocery items (red exclamation + violated allergen names) — mirror GroceryListView.swift:449-455
- **`WellnessPanel`** in the product detail sheet (Stage 1 S1.8) — mirror GroceryListView.swift:1073-1162
  - Lists allergens triggered, nutrient overruns ("Sodium: 800 mg, limit 600 mg")
- **`WellnessWarningSheet`** — confirmation modal that intercepts route creation when any list item violates preferences (iOS GroceryListView.swift:678-693, 1166-1229)
  - Lists offending products with their violations
  - "Optimize anyway" / "Cancel" buttons; only "Optimize anyway" proceeds to `RouteRepository.optimizeRoute`

**Files touched:**

- New `ui/lists/WellnessPanel.kt`
- New `ui/lists/WellnessWarningSheet.kt`
- `ui/lists/GroceryListScreen.kt`
- `ui/lists/ProductDetailSheet.kt` (built in Stage 1)
- `viewmodel/shopper/ShopperViewModel.kt`

**Acceptance:**

- A list containing a peanut product with `avoidPeanuts = true` shows the warning sheet on "Create Route"
- "Cancel" leaves the list and route untouched
- "Optimize anyway" proceeds to optimize without further prompts
- Disabling wellness in preferences hides all chips and skips the warning sheet

### S2.3 — Recipe generator (model + API + repository)

**Goal:** a server-generated recipe based on the user's preferences.

**Changes:**

- New `data/model/RecipeSuggestion.kt`:
  - `RecipeSuggestion`: `title`, `summary`, `whyItMatches: List<String>`, `prepMinutes`, `cookMinutes`, `servings`, `ingredients: List<String>`, `steps: List<String>`, `nutritionNotes: List<String>`
  - `RecipeRequestPayload`: `dietaryPreferences: List<String>`, `avoidIngredients: List<String>`, `nutritionTargets: NutritionTargetsPayload?`
  - `NutritionTargetsPayload`: `cholesterolMg`, `sodiumMg`, `sugarG` (nullable)
- Extend `NeighborlyApi`:
  - `suspend fun generateRecipe(payload: RecipeRequestPayload): Result<RecipeSuggestion>` → POST `/recipes/generate`
- New `data/repository/RecipeRepository.kt`:
  - `generate(force: Boolean = false): Result<RecipeSuggestion>`
  - Caches the last `RecipeRequestPayload` and returned `RecipeSuggestion`; only re-requests when payload changes or `force = true` (mirror HomeViewModel.swift:56-74)
- New extension `PreferenceState.toRecipeRequestPayload()` mirroring iOS `Preferences.recipeRequestPayload` (RecipeSuggestion.swift:36-68)

**Files touched:**

- New `data/model/RecipeSuggestion.kt`
- `data/api/NeighborlyApi.kt`
- New `data/repository/RecipeRepository.kt`
- New `domain/preferences/RecipePayloadMapper.kt`

**Acceptance:**

- Calling `RecipeRepository.generate()` twice with unchanged preferences makes one network call and returns the cache the second time
- `force = true` always re-requests
- Unit test: round-trip parse of a recipe response sample; payload mapping for a "vegan, avoid peanuts" preference set

### S2.4 — Featured recipe widget on Home + detail screen

**Goal:** a card on the Home screen showing the current recipe with a refresh button, and a detail screen on tap.

**Changes:**

- New `ui/home/components/FeaturedRecipeCard.kt`:
  - Title, summary, "why it matches" bullets, refresh icon, tap-through arrow
  - Loading shimmer + error states (mirror HomeView.swift:292-318)
- New `ui/home/RecipeDetailScreen.kt` mirroring `RecipeDetailView.swift`:
  - Pills for prep / cook / servings
  - Numbered ingredients, steps, "why it matches", nutrition notes
- `HomeViewModel` owns `featuredRecipe: RecipeSuggestion?`, `isLoadingRecipe`, `recipeError`
- Auto-regenerate on `PreferenceState` change (debounce in the view model, skip if cached payload unchanged — iOS HomeViewModel.swift:56-74)

**Files touched:**

- `ui/home/HomeScreen.kt`
- New `ui/home/components/FeaturedRecipeCard.kt`
- New `ui/home/RecipeDetailScreen.kt`
- `viewmodel/home/HomeViewModel.kt`
- Navigation host (route to recipe detail)

**Acceptance:**

- Home renders a recipe card on first launch (after preferences load)
- Editing preferences triggers regeneration without an explicit user action
- Refresh button forces a regenerate
- Tapping the card opens the detail screen with full recipe content
- Network errors render an inline error state, not a crash

### S2.5 — Preferences ↔ Supabase sync

**Goal:** preferences edited on Android persist to the same Supabase `user_preferences` row that iOS reads, and vice versa.

**Changes:**

- Install `Postgrest` plugin on `SupabaseClient` (`data/SupabaseClient.kt:1-13`)
- New `data/repository/PreferenceRemoteRepository.kt`:
  - `fetch(userId): Result<PreferenceState?>` reads `user_preferences` by `user_id`
  - `save(prefs, userId)` upserts on `user_id` conflict
  - Conversion helpers: miles ↔ km (`× 0.621371` / `× 1.60934`), `0.0` km = "unlimited" → slider value `11` (mirror PreferencesService.swift:86-91)
- `ShopperViewModel`:
  - On preferences screen open, fetch from Supabase and reconcile with local state — local takes precedence on the user's session, remote takes precedence on first open after login
  - Save button writes to both local and remote
- Confirm column names match iOS exactly so a user signing into both platforms sees the same preferences

**Files touched:**

- `data/SupabaseClient.kt`
- New `data/repository/PreferenceRemoteRepository.kt`
- `viewmodel/shopper/ShopperViewModel.kt`
- `ui/preferences/PreferencesScreen.kt` (Save button wiring)

**Acceptance:**

- Saving on Android updates the row visible in Supabase Studio
- Logging in with the same user on iOS shows the values just saved on Android
- Network failures during fetch fall back to local preferences without blocking the UI
- "Unlimited" sentinel is preserved across the round trip

### S2.6 — Home dashboard off static data

**Goal:** the home metrics and "Start Trip" CTA reflect real state instead of the hardcoded values currently in `HomeViewModel`.

**Changes:**

- Replace hardcoded metrics (HomeViewModel.kt:23-32) with:
  - **Items tracked**: count of persisted grocery items
  - **Latest trip**: total cost, store count, item count from the most recent `OptimizedRoute` in shared `RouteState`
  - **Budget / savings**: keep static demo as a fallback, override with real values when available
- "Start Trip" button: enabled only when an optimized route exists; tapping switches to the Route tab
- Remove static `HomeViewModel.routeStops` (3 hardcoded stops) and render either the real route preview or an empty state

**Files touched:**

- `viewmodel/home/HomeViewModel.kt`
- `ui/home/HomeScreen.kt`

**Acceptance:**

- A fresh user with no list and no route sees the empty / demo state
- After adding items, the count updates live
- After optimizing a route, the home preview shows real store count and total cost
- "Start Trip" is disabled when no route exists

### S2.7 — Theme cleanup (D1 finish)

**Goal:** no screen defines its own color literals; everything routes through `NeighborlyTheme`.

**Changes:**

- Move local color constants out of `LoginScreen.kt:41-43` and any other screens with `Color(0xFF…)` literals into `ui/theme/NeighborlyTheme.kt`
- Audit `GroceryListScreen.kt`, `RouteScreen.kt`, `PreferencesScreen.kt`, `HomeScreen.kt` for raw colors and replace with theme tokens
- Centralize spacing tokens already defined in `NeighborlySpacing` (apply where dp literals leak)

**Files touched:**

- `ui/theme/NeighborlyTheme.kt`
- All `ui/**/*Screen.kt` files

**Acceptance:**

- Grep `Color(0xFF` in `ui/` returns only `NeighborlyTheme.kt`
- Light / dark mode switch produces consistent results across all screens (matches iOS f77cd02 behavior)

### S2.8 — Auth polish

**Goal:** complete the auth flow with the missing affordances.

**Changes:**

- Password reset entry point on `LoginScreen` ("Forgot password?")
  - Calls Supabase `resetPasswordForEmail(email)` and shows a confirmation snackbar
- Sign-up confirmation message when Supabase requires email verification (mirror LoginView.swift:56-63)
  - `LoginViewModel.onSubmit` already returns whether a session was obtained immediately; surface the "check your email" alert when not

**Files touched:**

- `viewmodel/login/LoginViewModel.kt`
- `ui/login/LoginScreen.kt`

**Acceptance:**

- Tapping "Forgot password?" with a registered email triggers the Supabase flow and shows a confirmation
- Signing up with email verification enabled in Supabase shows the confirmation message instead of leaving the user on the login form silently

### S2.9 — Test coverage (D5)

**Goal:** lock in the parity gains with unit tests so future regressions show up in CI.

**Changes:**

- API parsing tests (`data/api/`):
  - Product search response (happy path + empty)
  - Optimize route response (happy path + `noRoute = true` branch)
  - Recipe response
- Repository tests with fake `NeighborlyApi`:
  - `GroceryListRepository.addOrIncrement` UPC-merges quantity
  - `GroceryListRepository.refreshPrices` ignores per-item failures
  - `RouteRepository.optimizeRoute` propagates typed errors
  - `RecipeRepository` skips redundant calls when payload unchanged
- View-model tests (`viewmodel/`):
  - `ShopperViewModel` search debounce + cancellation
  - `RouteViewModel` swap → re-optimize sequence
  - `HomeViewModel` regenerates recipe on preferences change

**Files touched:** `app/src/test/...`

**Acceptance:**

- `./gradlew test` runs the new tests in CI
- Tests cover at minimum every endpoint parser and every repository public method

## Sequencing & Parallelization

```
Independent tracks (can start in parallel once Stage 1 is in main):

  Track A — Wellness:       S2.1 ─> S2.2
  Track B — Recipe:         S2.3 ─> S2.4
  Track C — Prefs sync:     S2.5
  Track D — Home polish:    S2.6  (depends on Stage 1 shared RouteState)
  Track E — Cleanup:        S2.7, S2.8 (no deps)

Final:                       S2.9 (after each track lands its surface area)
```

Tracks A through E are independently shippable. A four-engineer team can take one track each; a smaller team should sequence Track A → Track B → Track C → Track D → Track E (highest user-visible value first).

## Risks & Open Questions

- **`user_preferences` schema parity.** Confirm column names are identical to iOS before wiring `PreferenceRemoteRepository`. Mismatches will silently corrupt cross-platform sync.
- **Recipe API rate limits / cost.** Recipe generation likely calls an LLM; confirm with backend that doubling traffic from Android is within budget. Caching by payload (S2.3) mitigates but doesn't eliminate.
- **Dietary preference cardinality.** iOS uses `dietVegan` / `dietGlutenFree` etc. as separate booleans. Preserve the same names and serialization so cross-platform DB rows stay readable.
- **Wellness UX on a long list.** If a user's grocery list has 20+ violating items, the warning sheet may feel overwhelming. iOS doesn't truncate; mirror that for parity but flag for product review post-launch.
- **Home dashboard "savings" metric.** iOS still has it hardcoded. Decide whether to leave Android matching that demo behavior or compute a real number from sale prices in the latest route.

## Stage 2 Deliverables

- Wellness violations surfaced everywhere products appear, with a confirmation sheet before route optimization
- Recipe generator and home recipe widget at iOS parity
- Preferences synced bidirectionally with Supabase
- Home dashboard reflecting real list and route state
- Theme cleanup (`D1`) finished, password reset shipped, base test coverage in CI

## Out of Scope

- Push notifications (iOS doesn't have them either; deferred indefinitely)
- Deep links (same)
- Analytics / telemetry (cross-platform decision, not Android-specific)
- Vendor / admin surfaces (web-only on iOS as well)
