Drop a Teachable Machine (image, TensorFlow Lite, floating point) export here as:

- `warning_lights.tflite`
- `warning_lights_labels.txt` (one label per line; labels must match ids in
  `core/.../symptom/WarningLights.kt`, e.g. `check_engine`, `oil_pressure`)

Without these files the app falls back to the manual symbol picker.
