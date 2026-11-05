# Main screens

## Design system
- **Two visual styles**, switchable in Settings → Appearance:
  - **Soft (default)**: neumorphic. Background and surfaces share one base colour; every surface casts a light shadow up-left and a dark shadow down-right (`neuRaised`), sinks when pressed, and tracks/rings sit in recessed wells (`neuInset`). Text keeps full contrast and status stays in colour pills, which avoids neumorphism's usual accessibility problem. On Android 8 and older (no blur in hardware rendering) surfaces render flat with a hairline edge.
  - **Material**: flat M3 cards with tonal surfaces.
  - Every screen draws surfaces through one primitive, `AppSurface`, so the setting restyles the whole app at once.
- **Theme**: System / Light / Dark (AppCompat night mode, so status-bar icons follow). **Accent**: Teal, Blue, Orange, Purple (M3 primary roles for both themes).
- **Material 3** base palette from the brand seed `#0E7C66` (light + dark), edge-to-edge drawing.
- **Semantic status colours** (`Status.colors`): good / warning / bad, each with a container colour, tuned for contrast in both themes. Urgency always uses the same three colours.
- **Shapes**: 6 / 10 / 16 / 24 / 32 dp. **Spacing**: 4 dp grid, 16 dp gutter. Headings semi-bold; body line-height raised for Bangla script.
- **Navigation**: bottom bar with four tabs (Garage, Fleet, Help & SOS, Settings), each keeping its own state; large collapsing app bars on top-level screens; pushed screens slide and fade in.
- **Components** (`ui/Components.kt`): animated `ScoreRing` (sweeps in, TalkBack reads "Health score 72 of 100"), `StatusPill`, `IconBadge`, `PointsPill`, `ActionRow` (badge + two lines + chevron), `UsageBar` (how much of a service interval is used), `SectionHeader`, `GroupCard`, `StatusBanner`, `EmptyState`.
- **UX rules**: every problem shown is tappable and opens the screen that fixes it; Home leads with a cross-vehicle "Needs attention" list; long forms are split into short steps; touch targets ≥ 48 dp; Bangla digits when Bangla is selected.

| Route | Screen | Purpose |
|---|---|---|
| `home` | **Garage (home, tab)** | Large collapsing title, one-line status ("3 things need your attention"), vehicle cards (animated ring, band pill, top issue, odometer chip → update), then a **Needs attention** list across all vehicles (overdue services, papers, open problems) sorted by severity; each row opens its fix. FAB shrinks on scroll. |
| `add` | **Add vehicle** | 3 steps with progress bar and animated transitions: (1) vehicle-type cards, (2) searchable model list showing oil interval, or "Other", (3) details with inline validation. System back goes to the previous step. |
| `vehicle/{id}` | **Checkup** | Hero card (148 dp animated ring, band, model, odometer chip), horizontally scrolling quick actions, **What's lowering the score** (each reason tappable → its fix, with −points pill) or an "Everything looks good" banner, mileage-drop banner, open problems with "Fixed", parts grouped **Due now / Coming up / Healthy** with interval-usage bars and costs. Edit / Passport / Delete in the overflow menu. |
| `service/{id}` | **Service log** | Tap a part → log km, date, cost, note, receipt photo. History with delete. |
| `odometer/{id}` | **Odometer** | Photo → OCR candidates → tap correct; manual entry; GPS trip with jump/jitter filter. |
| `fuel/{id}` | **Fuel / CNG / charge log** | Efficiency, 30-day trend, cost/km, sparkline, anomaly causes; add fill-up (full/partial, CNG vs petrol for dual-fuel). |
| `docs/{id}` | **Papers** | Every paper the vehicle type needs, colour-coded by expiry; set date + number. |
| `symptoms/{id}` | **Symptom doctor** | Progress bar, one question per page with large tappable answer cards and directional slide animations (back goes back a question). Result: urgency banner (safe / this week / stop now) with advice, likelihood bars per cause, cost range, disclaimer, add to health record, find a mechanic. |
| `checks/{id}` | **Visual checks** | Step-by-step guide, compare with reference states, tap "looks like this"; battery voltage entry. |
| `lights/{id}` | **Warning lights** | Camera classifier (if model bundled) or symbol list → meaning + urgency. |
| `obd/{id}` | **OBD scan** | Paired dongles → connect → read/clear codes (Bangla) → live data (Pro) → save voltage. |
| `battery/{id}` | **Battery health** | Easy-bike/e-scooter state of health %, range vs rated, cycles left, advice. |
| `garages` | **Garages & SOS** | Big red SOS (vehicle, latest problem, location → SMS/WhatsApp to saved contacts); garage list filtered by type, featured first, distance. |
| `fleet` | **Fleet** | Avg score, attention count, overdue services, papers; vehicles sorted worst-first with driver and top issue. |
| `settings` | **Settings** | Appearance (style, theme, accent), বাংলা/English, Pro & code redeem, backup, SOS contacts, privacy note. |

Design notes: Bangla is the default language; numbers render in Bangla digits when Bangla is selected; urgency always uses the same three colours (green / amber / red); every diagnosis repeats "possible causes, confirm with a mechanic".

## Vehicle ID & QR (`vehicleid/{id}`)

- QR preview at the top (plain text, UTF-8, so any phone camera shows it), a switch to leave out driver details, **Share QR** (PNG card to any app) and **Print** (one A4 page).
- Vehicle: registration, engine and chassis numbers. Each is compared against the other vehicles in the garage (ignoring spaces, dashes, case and Bangla vs Latin digits); a clash shows under the field and blocks saving.
- Driver: name, driving licence number, phone. "Scan registration" and "Scan licence" run the paper scanner, save the paper with its expiry reminder and fill any blank references.

## History (`history/{id}`)

- Spending card: total, last 12 months, number of bills, and service / fuel / other split.
- Filter chips: All, Service, Fuel, Other bills, Checks & problems.
- Month sections with the month's spend; each row shows date, odometer, note and amount, and a receipt icon when a bill photo exists. Tapping opens details, the bill photo and Delete.
- **Add bill** button: scan a bill, log a service, log fuel, or add another bill (paper renewal fee, insurance, repair, toll, parking, fine, wash, accessories, driver salary, other) with an optional photo.
- Top bar: **Print** and **Share PDF** for the Vehicle Passport.

