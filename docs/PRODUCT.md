# GariDoctor — product & business plan

**One-line pitch:** One app that tracks, checks, and explains the health of any vehicle (motorcycle, scooter, car, SUV/jeep, CNG auto-rickshaw, electric easy-bike or e-scooter, microbus, bus, pickup, truck, or any other vehicle) in Bangla and English, running almost entirely on the phone.

## 1. Vehicle Health Score
Every vehicle gets a score from 0 to 100, like a medical checkup, computed on the phone from engine oil, brakes, tyres, battery, chain/belt, filters/plugs/coolant, fault codes, fuel efficiency and papers. It turns boring maintenance into something people want to keep high, like a fitness ring. See [ARCHITECTURE.md](ARCHITECTURE.md#health-score-rules-healthscoreengine) for the exact rules.

## 2. One app, every vehicle type
A template system: each vehicle type is a JSON template listing its parts, service intervals, common symptoms, and checks. Add a vehicle type by adding a template, with no new code. Templates can be served from Firebase Remote Config (free).

| Vehicle | Special focus |
|---|---|
| Car | OBD dongle diagnostics, fault codes, AC, CNG conversion checks |
| Motorcycle / scooter | Chain care, brake pads, oil every 1,000–2,000 km, spark plug, carb/FI symptoms |
| CNG auto-rickshaw | CNG cylinder retest dates, daily-use wear, driver-owner tracking |
| Electric easy-bike / e-scooter | Battery health: charge cycles, range drop, water top-up for lead-acid |
| Microbus / bus / pickup / truck | Heavy-use schedules, route permit and fitness papers, fleet mode |

The electric easy-bike angle is a big, ignored market in Bangladesh: owners lose money every time a battery set dies early.

## 3. Features (all on the phone)
A. Smart maintenance schedule · B. Odometer photo / GPS · C. Fuel, CNG and charge tracker with anomaly alerts · D. Symptom checker in Bangla · E. Camera checks (warning lights, guided visual checks) · F. OBD mode for cars · G. Paper reminders · H. Vehicle Passport PDF with QR · I. Garage finder and SOS · J. Fleet mode.

## 4. Tech stack (near-zero running cost)
Kotlin + Compose · Room · WorkManager · ML Kit Text Recognition · TensorFlow Lite · Bluetooth SPP · PdfDocument · Firebase Auth/Firestore (text only, free tier) · FCM · Remote Config · Crashlytics/Analytics. Keep photos on the phone; avoid Cloud Functions and Cloud Storage at first.

## 5. Revenue
| Stream | Who pays | Why it works |
|---|---|---|
| Pro unlock (one-time ৳299–499) | Owners | Multiple vehicles, PDF passport, live OBD graphs, no ads |
| AdMob (free tier) | Advertisers | Rewarded ads unlock one free report or symptom deep-dive |
| Featured garage listings (monthly, bKash/Nagad) | Garages | Top spot for "bike mechanic near me" |
| Fleet subscription (per vehicle per month) | CNG, easy-bike, delivery, transport owners | Downtime and fines cost them real money |
| Dongle bundle | Car owners | Tested OBD dongles with a Pro code |
| Sponsored maintenance tips | Oil, tyre, battery brands | Reach the user at the exact moment a part is due |
| Passport verification (later) | Used-vehicle sellers | "Garage-verified" badge |

## 6. Roadmap
- **Phase 1 (MVP, 2–3 months):** templates for the 20 most common bike/car models, schedule + reminders, odometer photo, fuel/CNG tracker + alerts, paper reminders, health score, ads + Pro. ✅ engine + UI in this repo (ads/billing pending)
- **Phase 2 (months 4–6):** symptom checker in Bangla, warning-light scanner, OBD mode, PDF passport. ✅ (warning-light model to be trained)
- **Phase 3 (months 7–12):** garage directory with paid featured listings, fleet mode, easy-bike battery module, brand sponsorships. ✅ app side (needs verified garage list + sales)
- **Phase 4:** similar two/three-wheeler markets; consented engine-sound recordings for an on-device noise model.

## 7. Growth
Partner with bike and car Facebook groups (free Pro codes for admins — `./gradlew :core:proCodes`); mechanic referrals; "paper expiry" Reels/TikTok; visit easy-bike and CNG garages in person.

## 8. Risks
- **Users stop logging** → odometer photos, GPS estimates, health score habit hook.
- **Wrong advice** → "possible causes" + urgency + "confirm with a mechanic" everywhere.
- **Fake dongles** → tested-dongle list or sell our own.
- **Garage trust** → start with 20–30 hand-picked Dhaka garages before opening the directory.

**Bottom line:** the money is most likely in bike riders (largest group), fleet owners (pay monthly) and brands (pay to reach people at the moment they need a part). One template-driven app covering every vehicle type makes that possible.
