# Architecture

```
┌──────────────────────────── app (Android) ───────────────────────────┐
│ Compose screens ── MainViewModel ── Repository ── Room (SQLite)      │
│        │                               │                             │
│  ML Kit OCR · TFLite · ELM327 BT/USB · PdfDocument · WorkManager · RC     │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ pure functions, no Android
┌───────────────────────────── core (JVM) ─────────────────────────────┐
│ TemplateRegistry  MaintenanceScheduler  HealthScoreEngine            │
│ FuelAnalyzer  SymptomTree/Session  ObdDecoder  OdometerParser        │
│ DocumentExpiry  BatteryHealth  VehiclePassport  Fleet  ProCodes      │
│ VehicleDoctor.checkup(template, records, today) → Checkup            │
└──────────────────────────────────────────────────────────────────────┘
```

Everything that decides something (score, due dates, alerts, diagnoses, decoding) lives in `:core`, so it is unit-tested on the JVM and could be reused in an iOS/KMP or server build later. The Android layer only stores data, talks to hardware and draws screens.

## Cost rules
- Photos (receipts, odometer, warning lights) stay in `files/photos` on the phone.
- Only small text records would ever be synced (Firestore free tier).
- No Cloud Functions / Cloud Storage (they need the pay-as-you-go plan).
- Reminders are local notifications from a daily WorkManager job.

## Template schema (`VehicleTemplate`)

```jsonc
{
  "id": "motorcycle",            // unique, also the Remote Config key suffix
  "version": 1,                  // remote copy wins only if higher
  "name": {"en": "Motorcycle", "bn": "মোটরসাইকেল"},
  "defaultEnergy": "OCTANE",     // PETROL|OCTANE|DIESEL|CNG|PETROL_CNG|ELECTRIC_LEAD_ACID|ELECTRIC_LITHIUM
  "parts": [{
    "id": "engine_oil",
    "name": {"en": "Engine oil", "bn": "ইঞ্জিন অয়েল"},
    "category": "ENGINE_OIL",    // grouping, icons, sponsor slot
    "intervalKm": 1500, "intervalMonths": 4,   // whichever comes first
    "weight": 20,                // max points this part can remove (1..40)
    "checkId": "oil_level",      // optional guided check
    "costMinBdt": 450, "costMaxBdt": 1200
  }],
  "documents": ["REGISTRATION", "TAX_TOKEN", "INSURANCE", "DRIVING_LICENCE"],
  "models": [{"id": "yamaha_fzs_v3", "brand": "Yamaha", "model": "FZS-FI V3",
              "overrides": {"engine_oil": {"intervalKm": 2000}}}],
  "checks": [{"id": "oil_level", "title": {…}, "steps": [{…}], "references": [{"label": {…}, "description": {…}, "passes": true}]}],
  "symptomTreeId": "motorcycle",
  "supportsObd": false,
  "fleetFriendly": true
}
```

Validation (`TemplateRegistry.validate`): non-empty parts, unique ids, weight 1..40, at least one interval per part, `checkId` must exist, model overrides must target existing parts. Invalid remote templates are rejected and the bundled one stays.

## Health score rules (`HealthScoreEngine`)

| Signal | Points off |
|---|---|
| Part due soon (85–100% of interval) | up to 20% of weight |
| Part overdue | 40% of weight at due point → 100% at 50% past due |
| Failed guided check (≤90 days, not serviced since) | 100% of weight |
| Reported symptom on the part (unresolved) | 60% of weight |
| Low battery voltage (normalised to 12 V) | 3 / 6 / 10 |
| Active OBD fault codes | 10 each, max 25 |
| Efficiency drop vs own average (last 30 days) | ≥15% → 6, ≥25% → 10 |
| Papers | expired 10, ≤7 d 5, ≤15 d 3, ≤30 d 1 — max 20 total |

A part counts once (its worst deduction). Score = clamp(100 − Σ, 0, 100). Bands: ≥90 excellent, ≥75 good, ≥55 needs attention, ≥35 poor, else critical.

## Database (Room, `garidoctor.db`)

| Table | Columns | Notes |
|---|---|---|
| `vehicles` | id, templateId, modelPresetId?, nickname, registrationNo?, odometerKm, baselineKm, energyType, createdAtEpochDay, driverName?, driverPhone?, ratedRangeKm? | `baselineKm` = odometer when added; parts with no log count from here. |
| `services` | id, vehicleId→vehicles, partId, odometerKm, epochDay, costBdt?, note?, receiptPath? | Receipt photo path is local only. |
| `fuel` | id, vehicleId, odometerKm, amount, priceBdt?, full, epochDay, rangeKm? | amount = L / m³ / charge fraction. `rangeKm` = EV range sample. |
| `documents` | id, vehicleId, type, expiryEpochDay, number? | `type` = `DocumentType` name. |
| `checks` | id, vehicleId, partId, checkId, passed, epochDay | Guided visual checks. |
| `symptoms` | id, vehicleId, partId, symptomId, urgency, summary, epochDay, resolved | From the symptom doctor. |
| `voltages` | id, vehicleId, volts, nominalVolts, epochDay | Multimeter or OBD PID 0x42. |
| `fault_codes` | id, vehicleId, code, epochDay, cleared | Replaced on each OBD read. |
| `odometer_log` | id, vehicleId, km, epochDay, source | manual / photo / gps / fuel / obd. |
| `interval_overrides` | (vehicleId, partId) PK, intervalKm?, intervalMonths? | User-edited intervals; applied after the model preset. |

All child tables cascade on vehicle delete. Dates are `LocalDate.toEpochDay()` (timezone-free). Settings (language, Pro, SOS contacts) are in SharedPreferences.

## Backup
`BackupManager` serialises every table to one JSON document (`format: garidoctor-backup`). It is saved to a user-chosen file (Storage Access Framework) or to Firestore `users/{uid}` as a single text field (free tier, rules in `firestore.rules`). Restore replaces local data inside one Room transaction. Receipt photo paths are kept but photos themselves never leave the phone.

## Monetisation plumbing
- `ProBilling`: Play Billing 7, one-time in-app product, restores purchases on start, acknowledges new ones.
- `ProCodes`: offline promo codes (`./gradlew :core:proCodes`).
- `Ads`: AdMob banner on home + rewarded ad → one free passport. Never shown to Pro users.
- Sponsored tips: Remote Config `sponsor_<PartCategory>` shown next to due parts, labelled "Sponsored".
