# AI features

Car Doctor reads papers, garage bills, voice and dashboard lights with **no server of its own**.
Each feature tries the free options first and uses cloud AI only when the user asks.

| Feature | 1. On the phone (free, offline, unlimited) | 2. On-device AI (free) | 3. Cloud AI (counts against your quota) |
|---|---|---|---|
| **Paper scanner** (registration, tax token, fitness, insurance, licence, route permit, CNG retest) | ML Kit OCR → `DocumentTextParser` finds type, number, expiry (Bangla/English dates) | Gemini Nano structures the OCR text | Gemini reads the photo ("Read with AI") |
| **Bill scanner** | ML Kit OCR → `ReceiptTextParser` finds parts (Bangla/English/Banglish words), amounts, total, date, odometer | Gemini Nano on the OCR text | Gemini reads the photo |
| **Voice logging** | Android speech recognition (bn-BD / en-US) → `VoiceCommandParser` (service / fuel / CNG / odometer) | Gemini Nano on the sentence | Gemini on the sentence ("Understand with AI") |
| **Warning lights** | TensorFlow Lite model if bundled, else symbol list | — | Gemini identifies the photographed light |

The user always reviews what was read before anything is saved. Saved papers get expiry reminders automatically; scanned bills log one service per part, with the bill photo attached.

## Firebase setup (one time)
1. Create a Firebase project, add the Android app `com.garidoctor.app`, put `google-services.json` in `app/`.
2. **AI Logic**: Firebase console → AI Logic → get started → choose the **Gemini Developer API** (works on the free Spark plan).
3. **App Check** (required for AI Logic from 2 Nov 2026): register **Play Integrity** for release. Debug builds use the debug provider: run the app, copy the debug token from Logcat (`DebugAppCheckProvider`) and add it in App Check → Manage debug tokens. Then enforce App Check for AI Logic and Firestore.
4. **Authentication**: enable the **Anonymous** sign-in provider (users see no login; it gives per-user limits).
5. **Firestore**: create the database and deploy `firestore.rules` from this repo.
6. **AI Logic per-user rate limit**: set a low per-user requests-per-minute limit (e.g. 5).
7. **Remote Config** (all optional; defaults in brackets):

| Key | Meaning |
|---|---|
| `ai_enabled` (true) | Off switch for all cloud AI |
| `ai_model` (`gemini-2.5-flash-lite`) | Model name. Change it when Google retires a model. |
| `ai_daily_free` (3) / `ai_daily_pro` (30) | Cloud reads per user per day. Firestore rules cap everyone at 40. |
| `ai_max_output_tokens` (512) | Answer length cap |
| `ai_prompt_paper` / `ai_prompt_receipt` / `ai_prompt_light` | Prompt overrides |

## Who pays, and how usage is limited
All cloud AI usage counts against **your** Firebase project, not the user's account. Layers that keep it under control:
- Most reads never reach the cloud (steps 1–2 above).
- Per-user daily allowance stored in `usage/{uid}/days/{utcDay}`; `firestore.rules` only allow today's document, +1 per write, a hard cap of 40, and at most 3 rewarded-ad bonuses a day.
- App Check blocks modified apps and scripts from using your quota.
- AI Logic per-user rate limit and `ai_enabled` off switch.
- Photos are downscaled to 1280 px and answers capped at `ai_max_output_tokens`.
- Free users: `ai_daily_free` reads, +1 per rewarded ad. Pro: `ai_daily_pro`.

The plan limit (free vs Pro) is applied in the app; only a server could verify Pro status, so the rules enforce the hard cap that applies to everyone.

## Privacy
Nothing leaves the phone until the user allows cloud AI (asked once, can be turned off in Settings → AI reading). Only the photo or sentence the user chose is sent. Google's terms for the free tier allow it to use requests to improve its products; switch to a paid project if that is a concern.

## Gemini Nano (opt-in)
On-device Gemini Nano uses the ML Kit GenAI Prompt API (`com.google.mlkit:genai-prompt`, beta). Because the beta API may change, it is compiled only with `-PenableNano`:
```bash
./gradlew :app:assembleDebug -PenableNano
```
Without the flag a stub reports "not available" and the app goes straight from rules to cloud AI. Supported phones: recent flagships (e.g. Pixel 9 and later, Galaxy S24 and later).
