# GariDoctor (গাড়িডাক্তার)

**A health doctor for every vehicle.** One Android app that tracks, checks and explains the health of any vehicle — motorcycle, scooter, car, SUV/jeep, CNG auto-rickshaw, electric easy-bike or e-scooter, microbus, bus, pickup, truck, or anything else via "Other vehicle" — in **Bangla and English**, running almost entirely on the phone. Available in **11 languages**: English, বাংলা, हिन्दी, اردو, नेपाली, العربية, 中文, 日本語, Русский, Deutsch, Italiano (right-to-left layouts for Urdu and Arabic).

[![Build](https://github.com/limon27121/GariDoctor/actions/workflows/build.yml/badge.svg)](https://github.com/limon27121/GariDoctor/actions/workflows/build.yml)

> "Your Pulsar is at **72/100**. Engine oil is 400 km overdue (−13), chain hasn't been adjusted in 1,200 km (−8), and your tax token expires in 9 days (−3)."

## What's in this repo

| Module | What it is |
|---|---|
| [`core/`](core) | Pure Kotlin (JVM) domain engine — no Android dependency, fully unit-tested. Health score, maintenance scheduler, template system, fuel/CNG/charge anomaly detection, symptom decision trees, OBD-II decoding, odometer OCR parsing, battery health, paper expiry, Vehicle Passport, fleet summary, Pro codes. |
| [`core/src/main/resources/templates`](core/src/main/resources/templates) | 12 vehicle-type templates (JSON) with **68 Bangladesh model presets** (Pulsar, FZS, Apache, Splendor, Axio, Premio, Prius, HiAce, Bajaj RE, easy-bike 48V/60V, Prado, Hino AK1J…). Users add as many vehicles as they like and filter the garage by type. |
| [`core/src/main/resources/symptoms`](core/src/main/resources/symptoms) | 5 bilingual symptom decision trees, 29 entry symptoms, each leaf with urgency colour + approx. BDT cost. |
| [`core/src/main/resources/obd/dtc.json`](core/src/main/resources/obd/dtc.json) | Offline OBD fault-code dictionary in Bangla + English. |
| [`app/`](app) | Android app: Kotlin + Jetpack Compose, Room, WorkManager, ML Kit text recognition, TensorFlow Lite, ELM327 over Bluetooth SPP or USB OTG (usb-serial-for-android), PdfDocument + ZXing QR, Firebase Remote Config (optional). |
| [`tools/`](tools) | Generators for templates and symptom trees (`python3 tools/gen_templates.py`, `python3 tools/gen_symptoms.py`). |
| [`docs/`](docs) | [Architecture & database](docs/ARCHITECTURE.md), [screens](docs/SCREENS.md), [product & business plan](docs/PRODUCT.md). |

## Features

- **Vehicle Health Score 0–100**: oil, brakes, tyres, battery, chain/belt, filters, plugs, coolant, OBD fault codes, mileage drop and papers each pull the score down, with a plain-language reason for every point.
- **One app, every vehicle**: each vehicle type is a JSON template (parts, intervals, papers, guided checks, symptom tree, model overrides). Add a vehicle type by adding a template; push fixes through **Firebase Remote Config** without an app update.
- **Smart maintenance schedule**: km **or** months, whichever comes first, per-model defaults, user-editable, daily on-device reminders via WorkManager.
- **Odometer without typing**: photograph the meter → ML Kit on-device OCR (handles Bangla digits, trip/clock confusion); or GPS trip estimate.
- **Fuel / CNG / charge tracker**: km/L, km/m³, km/charge (tank-to-tank method), cost per km, and **anomaly alerts** ("Mileage dropped 18% this month — common causes: …").
- **Symptom doctor**: tap-through questions in Bangla → possible causes with likelihood, **urgency colour** (safe / fix this week / stop now), approx. cost, and a "confirm with a mechanic" disclaimer.
- **Camera checks**: warning-light scanner (TFLite, Teachable Machine model drop-in, manual picker fallback) and guided visual checks (tyre tread, chain slack, brake pads, oil, battery terminals, coolant, easy-bike battery water).
- **OBD mode (cars, SUVs, microbuses, pickups)**: ELM327 over a Bluetooth dongle **or a USB cable** through an OTG adapter (FTDI, CH340, CP210x, PL2303; baud rate found automatically), fault codes decoded offline in Bangla, live data (Pro), voltage → health score.
- **Papers**: registration, fitness, tax token, insurance, driving licence, route permit, CNG cylinder retest — reminders at 30/15/7/3/1 days, weekly once expired.
- **Vehicle Passport**: a shareable PDF with health score, service history, papers, mileage and a QR fingerprint. Sellers share it to get a better price.
- **Easy-bike battery health**: charge cycles, range fade, water top-up reminders, and an alert when one weak battery is pulling the whole set down.
- **Garage finder + SOS**: filter by bike mechanic / car workshop / battery shop / CNG kit; SOS sends location + vehicle + latest diagnosis by SMS or WhatsApp.
- **Fleet mode**: every vehicle's score, overdue services and expiring papers on one screen, with driver names.
- **AI scanning without a server** ([details](docs/AI.md)): photograph a **paper** (registration, tax token, fitness, insurance, licence) and the app reads the expiry date and sets reminders; photograph a **garage bill** and it logs each part with cost, date and odometer; **say** "আজ ৪০০ টাকায় তেল বদলালাম" and it logs it; photograph a **warning light** to identify it. Reading happens on the phone first (ML Kit + Bangla/English rules, optional Gemini Nano); cloud Gemini via Firebase AI Logic only on request, with consent, App Check, and per-user daily limits enforced by Firestore rules.
- **Fast on low-end phones** ([details](docs/PERFORMANCE.md)): background startup, data work off the main thread, automatic Lite rendering (Settings → Performance), startup profile, smaller APK; one-tap actions on vehicle cards and launcher shortcuts (Speak, Scan paper, Scan bill, Log fuel).
- **11 languages**: a first-launch language picker (native names first) and Settings → Language. English and Bangla are written at each call site; the other nine come from JSON catalogs keyed by the English text (`core/src/main/resources/i18n/<code>.json`), with `{0}` placeholders for numbers and names and English as the fallback. Urdu and Arabic mirror the layout automatically. Voice input uses the matching speech language. See [docs/I18N.md](docs/I18N.md).
- **Editable schedules**: every part's km/month interval can be changed per vehicle (template default → model preset → user override).
- **Backup**: export/restore a small JSON file (works offline), plus optional free cloud backup (Firebase Auth email + one Firestore document per user, see `firestore.rules`). Photos never leave the phone.
- **Money hooks**: one-time **Pro unlock via Google Play Billing** (product id `garidoctor_pro`), offline promo codes for Facebook-group admins and dongle bundles, **AdMob** in the free tier (home banner + rewarded ad for one free passport), featured garage listings, sponsored maintenance tips at the exact moment a part is due (Remote Config `sponsor_<CATEGORY>`).

## Build

Requirements: JDK 17+, Android SDK 35 (for `:app`).

```bash
./gradlew :core:test -PcoreOnly        # domain engine tests; no Android SDK needed
./gradlew :app:assembleDebug           # APK at app/build/outputs/apk/debug/
```

CI builds both on every push and uploads the debug APK as an artifact (Actions → Build → `garidoctor-debug-apk`).

### Optional: Firebase
Put your `google-services.json` in `app/` and the Google Services plugin is applied automatically. Remote Config keys:

| Key | Value |
|---|---|
| `template_<id>` | Full template JSON. Applied only if `version` is newer and it validates. |
| `symptoms_<id>` | Symptom tree JSON. |
| `garages` | Garage directory JSON (same format as `app/src/main/assets/garages.json`). |
| `sponsor_<CATEGORY>` | `{"brand":"…","text":{"en":"…","bn":"…"}}`, e.g. `sponsor_ENGINE_OIL`. |

### Play Billing & AdMob
Create an in-app product `garidoctor_pro` in Play Console. AdMob uses Google's public **test ids** by default; pass real ones for release:
```bash
./gradlew :app:assembleRelease -PadmobAppId=ca-app-pub-xxx~yyy -PadmobRewardedId=ca-app-pub-xxx/zzz -PadmobBannerId=ca-app-pub-xxx/www
```

### Promo codes
```bash
./gradlew :core:proCodes -PcoreOnly -Pfrom=1 -Pcount=50 -Psalt=YOUR_SECRET
./gradlew :app:assembleRelease -PgariProSalt=YOUR_SECRET
```

### Warning-light model
Train an image model in Teachable Machine (labels = ids in `WarningLights.kt`), export as TensorFlow Lite (float), and drop `warning_lights.tflite` + `warning_lights_labels.txt` into `app/src/main/assets/`.

## Safety
Diagnoses are always shown as **possible causes** with urgency, and users are told to confirm with a mechanic. Intervals and costs are typical defaults for Bangladesh, not manufacturer guarantees; users can edit them.

## Status
Every feature in the product plan (Phases 1–3) is implemented in the engine and the app.

Content you still need to supply before a store release:
- a hand-verified garage list (the bundled list is clearly marked as sample data), via Remote Config `garages`
- a trained warning-light model (`warning_lights.tflite`); the manual symbol picker works without it
- your own `google-services.json`, Play Console product and AdMob ids
- real photos for guided checks are optional; drawn reference diagrams are built in
