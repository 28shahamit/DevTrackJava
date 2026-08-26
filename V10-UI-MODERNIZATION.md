# DevTrack v10 — Phase 1: Material Components foundation

## What changed

The app was pure `android.app.Activity` + framework widgets (`Theme.Material`,
manual `Button`/`LinearLayout` bottom nav, no AndroidX at all —
`android.useAndroidX=false`). That's now the foundation for a real,
industry-standard Android UI stack instead of hand-rolled views everywhere.

1. **AndroidX + Material Components enabled.** Flipped
   `android.useAndroidX=true`, added `com.google.android.material:material`,
   `androidx.appcompat`, `androidx.recyclerview`, `androidx.core` as
   dependencies. This unlocks real Material widgets, RecyclerView, and
   Material dialogs for future phases.

2. **Real Material theme.** `AppTheme` now extends
   `Theme.MaterialComponents.DayNight.NoActionBar` with proper
   `colorPrimary` / `colorSecondary` / `colorSurface` / `colorError` role
   colors instead of a single `colorAccent` override. This is what makes
   Material components (bottom nav, dialogs, buttons) render correctly and
   consistently instead of falling back to plain framework styling.

3. **Real bottom navigation.** Replaced the 4 emoji `Button`s in a plain
   `LinearLayout` with a `com.google.android.material.bottomnavigation.
   BottomNavigationView` driven by a menu resource (`bottom_nav_menu.xml`)
   and real vector icons (`ic_nav_home/learn/plan/progress`). It now has:
   - **Real selected-state indication** — active tab icon/label tints to
     the primary color, others stay muted (`nav_item_tint.xml` color
     selector), and it now stays in sync even when navigation happens
     programmatically (e.g. "Open today's plan" from Home) via a new
     `syncNavSelection()` hook wired into `base()`.
   - **Proper ripple/touch feedback** from Material's built-in item ripple,
     not the old borderless framework button.
   - A `suppressNavCallback` guard so syncing selection programmatically
     never re-triggers a duplicate screen rebuild through the nav listener.

4. **Design tokens started.** Added `dimens.xml` with a real spacing scale
   (`space_xs`…`space_xxl`) and type scale (`text_xs`…`text_display`), and
   expanded `colors.xml` with Material role names (`primary`, `on_primary`,
   `secondary`, `error`, etc.) alongside the existing custom palette names
   so nothing currently referencing `@color/accent` etc. breaks.

## What this does NOT include yet (by design)

This is phase 1 of a multi-phase modernization, kept intentionally scoped
so it's safe to build and verify before going further:

- Screen bodies (`showHome`, `showRoadmap`, `showPlan`, `showProgress`) are
  still hand-built `LinearLayout`/`TextView`/`Button` trees in one
  `ScrollView` each — no `RecyclerView` yet. The 421-question DSA list is
  still one giant inflate. **Phase 2.**
- Dialogs (task/phase/topic/roadmap edit, schedule block) are still raw
  `AlertDialog` + stacked `EditText`/`Spinner`/`CheckBox`, not yet
  `MaterialAlertDialogBuilder` + `TextInputLayout`. **Phase 3.**
- The `dp(N)` / `tv(text, N)` inline magic numbers throughout
  `MainActivity.java` aren't refactored onto the new `dimens.xml` tokens
  yet — safer to do screen-by-screen alongside the RecyclerView work.

Each future phase converts one screen while the others keep working
unchanged, so the app is never in a half-broken state between sessions.

## Version

versionCode 5, versionName 2.2.0
