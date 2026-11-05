# Speed on low-end phones

Target: smooth on a 2 GB RAM, Android 8+ phone that costs under ৳10,000.

## Startup
- **Instant launch screen** (`core-splashscreen`): no blank white frame; it holds until data is ready, at most 1.2 s.
- **Nothing heavy in `Application.onCreate`**: templates, symptom trees and the selected language's catalog load on a background coroutine; the fault-code dictionary loads only when a car with OBD codes needs it.
- **Bundled JSON is in Android assets**, not Java resources: opening an asset is much faster than scanning the APK's resources (and they're no longer packaged twice).
- **Ads, billing and Remote Config start 1.5 s after launch**, off the critical path. MobileAds initialises off the main thread.
- **Startup profile** (`app/src/main/baseline-prof.txt` + `profileinstaller`): app and core startup classes are compiled ahead of time on install, so first launch doesn't wait for the JIT. Compose ships its own profiles. A fuller profile can be generated on a device with Macrobenchmark's `BaselineProfileRule`.

## While using the app
- **All data work off the main thread**: database reads, joins and every vehicle's health checkup run on `Dispatchers.Default` (`flowOn`). The main thread only draws.
- **Lite rendering, chosen automatically** (`DevicePerf`): on Android 8 or older, low-RAM devices, phones under 3 GB, battery saver, or with system animations off, the app uses flat surfaces with a hairline edge instead of blurred shadows, skips decorative animations, and switches screens instantly. Users can force **Auto / Full effects / Lite** in Settings → Performance.
- **Soft shadows are cached**: paints and blur filters are built once per size (`drawWithCache`), not every frame.
- **Score ring animates in the draw phase only**: the number shows immediately, so the sweep never recomposes text.
- **Lists are built once per data change** (`remember`) instead of on every recomposition; sponsored tips are cached.
- **Photos are decoded downscaled**: OCR uses a 2048 px copy and the light classifier a 1/8 copy, instead of a ~48 MB full-size bitmap.

## Size
- **Play-services OCR** (`play-services-mlkit-text-recognition`): the text model comes from Google Play services (downloaded at install via `com.google.mlkit.vision.DEPENDENCIES`), keeping the APK several MB smaller.
- **R8 + resource shrinking** in release builds; Play delivers per-device splits from the App Bundle.

## Fewer taps
- **One-tap actions on every vehicle card**: Speak, Scan bill, Fuel.
- **Launcher shortcuts** (long-press the icon): Speak, Scan paper, Scan bill, Log fuel; they open the last-used vehicle.
- **Predictive back** (Android 14+), skeleton placeholders while data loads.
