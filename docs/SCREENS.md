# Main screens

| Route | Screen | Purpose |
|---|---|---|
| `home` | **Garage (home)** | All vehicles as cards with score rings, overdue/paper counts, top issue. FAB: add vehicle (free = 1). Top bar: garages & SOS, fleet, settings. |
| `add` | **Add vehicle** | Pick type → model preset (sets intervals) → energy → nickname, reg no., odometer, last oil change, EV rated range, driver. |
| `vehicle/{id}` | **Checkup** | Big score ring + band, one-line summary, every deduction with points, action grid, mileage alert, open problems, all parts with km/days left, cost and sponsored tip. |
| `service/{id}` | **Service log** | Tap a part → log km, date, cost, note, receipt photo. History with delete. |
| `odometer/{id}` | **Odometer** | Photo → OCR candidates → tap correct; manual entry; GPS trip with jump/jitter filter. |
| `fuel/{id}` | **Fuel / CNG / charge log** | Efficiency, 30-day trend, cost/km, sparkline, anomaly causes; add fill-up (full/partial, CNG vs petrol for dual-fuel). |
| `docs/{id}` | **Papers** | Every paper the vehicle type needs, colour-coded by expiry; set date + number. |
| `symptoms/{id}` | **Symptom doctor** | Entry symptoms → questions → causes with %, urgency chip, cost, advice, disclaimer, "add to health record", find mechanic. |
| `checks/{id}` | **Visual checks** | Step-by-step guide, compare with reference states, tap "looks like this"; battery voltage entry. |
| `lights/{id}` | **Warning lights** | Camera classifier (if model bundled) or symbol list → meaning + urgency. |
| `obd/{id}` | **OBD scan** | Paired dongles → connect → read/clear codes (Bangla) → live data (Pro) → save voltage. |
| `battery/{id}` | **Battery health** | Easy-bike/e-scooter state of health %, range vs rated, cycles left, advice. |
| `garages` | **Garages & SOS** | Big red SOS (vehicle, latest problem, location → SMS/WhatsApp to saved contacts); garage list filtered by type, featured first, distance. |
| `fleet` | **Fleet** | Avg score, attention count, overdue services, papers; vehicles sorted worst-first with driver and top issue. |
| `settings` | **Settings** | বাংলা/English, Pro & code redeem, SOS contacts, privacy note. |

Design notes: Bangla is the default language; numbers render in Bangla digits when Bangla is selected; urgency always uses the same three colours (green / amber / red); every diagnosis repeats "possible causes, confirm with a mechanic".
