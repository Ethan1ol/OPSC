# Battle Fitness design system

One set of rules used by every screen. Built with Material Components for Android (Material 3), XML views.

## Principles
Modern, clean, lots of whitespace, card-based, rounded corners, subtle elevation, no clutter. Brand identity is the logo and the orange.

## Colour
All colours are tokens in `res/values/colors.xml` (light) and `res/values-night/colors.xml` (dark) and are used through theme attributes such as `?attr/colorPrimary`. Screens never contain hex values.

| Role | Light | Dark | Use |
|---|---|---|---|
| Primary | `#A94A00` | `#FFB784` | Buttons, links, selected tab |
| On primary | `#FFFFFF` | `#4A2300` | Text on primary |
| Primary container | `#FFDCC2` | `#7A3A00` | Highlighted cards |
| Brand orange | `#FF891B` | `#FF891B` | Decoration and logo only, always with `#331700` text |
| Background | `#FFF8F4` | `#1A120B` | Screen background (warm neutral) |
| Surface | `#FFFBF8` | `#1F160F` | Cards and sheets |
| On surface | `#231A12` | `#F0DFD2` | Main text |
| Success | `#1B6E36` | `#82DB97` | Confirmations, verified |
| Error | `#BA1A1A` | `#FFB4AB` | Errors, destructive actions |

Why two oranges: the logo orange `#FF891B` has only 2.4:1 contrast against white, so it fails for text and buttons with white text. The interactive primary is the deeper `#A94A00` (5.7:1). The brand orange stays for decoration and as a background with dark text (7.0:1).

**Contrast is tested.** `DesignContrastTest` reads the colour files and checks 19 text/background pairs in light and dark mode against WCAG 2.2 (4.5:1 for text, 3:1 for outlines). It runs with `./gradlew testDebugUnitTest`.

## Typography
One family: **Poppins** (SIL Open Font License), bundled in `res/font`, so it works offline.

| Style | Size / line | Weight | Used for |
|---|---|---|---|
| Headline | 28 / 36 sp | SemiBold | Screen greetings and big titles |
| Title | 20 / 28 sp | Medium | Card and section titles |
| Body | 16 / 24 sp | Regular | Normal text |
| Caption | 12 / 16 sp | Regular | Helper text, metadata |
| Label | 14 / 20 sp | Medium | Buttons, chips |

Text sizes are in `sp`, so they follow the phone's font-size setting.

**Implementation note (a trap we hit):** each weight is its own small family file (`res/font/poppins_family_*.xml`) and each text style sets the font in both `android:fontFamily` and `fontFamily` (Material's parent styles set the second one, and AppCompat reads it first). Never set `android:fontFamily` on the *theme*: it overrides the text styles and every weight renders as Regular. We found this by measuring the stroke thickness of screenshots against the font files.

## Spacing and shape
8dp grid: 4, 8, 16, 24, 32 dp (`space_xs` to `space_xl`). Screen padding 16dp. Cards 16dp radius with 2dp elevation. Buttons and inputs 12dp radius. Sheets 28dp. Minimum touch target 48dp.

## Components
- **Buttons:** `Widget.Battle.Button` (primary, default), `.Secondary` (tonal: filled with the soft orange container colour), `.Text`. 48dp tall.
- **Appearance:** dark mode is the default. Profile has a Dark / Light / System switch; the choice is saved (`ThemeManager`).
- **Text input:** `TextInputLayout` with inline error text under the field.
- **Cards:** `MaterialCardView` (default style is the Battle card).
- **Chips:** Material 3 filter and assist chips (fixture status, category filters).
- **`StateLayout`** (`ui/StateLayout.kt`): wraps a screen's content and shows loading, empty (icon, one line, optional action), error (with Try again) or offline states.
- **`UiMessages`**: snackbars for info, success (green), error (red, optional retry) and undo.
- **`ConfirmDialog`**: one confirmation dialog for risky actions.
- **Offline banner**: shown under the top bar whenever the phone has no internet (`ConnectivityMonitor`).
- **Icons:** hand-built 24dp stroke icons in `res/drawable/ic_*.xml`, tinted by the theme.

## Navigation
One `AppActivity` hosts the screens as fragments using the Navigation component (`res/navigation/app_nav_graph.xml`).
- Top app bar with the title. Detail screens get a back arrow automatically.
- Bottom navigation: Home, Clubs, Workouts, Profile.
- Back: from a detail screen, back goes to the tab. From another tab, back goes to Home. From Home, back leaves the app.

## Rules every screen follows
1. Handle loading, empty, error and offline states (use `StateLayout`).
2. No hard-coded text: everything is in `strings.xml`, translated to Afrikaans and isiZulu.
3. Invalid input shows an inline message beside the field and never crashes.
4. Touch targets of at least 48dp. Icons and images have a content description, or are marked decorative.
5. Survive rotation without losing typed input (state kept in a ViewModel or saved state).
6. Works on small and large phones, light and dark mode.

## Still to do on the design system
- The login, sign-up and settings screens use the older layout until their feature is rebuilt, and use `Theme.BattleFitness.Legacy` meanwhile.
- App icon, splash screen and illustrations come in the app-icon feature.

## Previewing it

Screenshots from the Android emulator (not a real phone): [light 1](images/gallery-light-1.png), [light 2](images/gallery-light-2.png), [light 3](images/gallery-light-3.png), [dark](images/gallery-dark-1.png).

Debug builds have a **Design gallery** button on the Profile tab. It shows every component in one scrolling screen, with a dark-mode toggle and buttons to trigger each state, snackbar and the dialog. The gallery is not included in release builds.
