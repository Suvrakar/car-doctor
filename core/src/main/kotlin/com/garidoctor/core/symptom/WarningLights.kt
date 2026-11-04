package com.garidoctor.core.symptom

import com.garidoctor.core.model.L10n

/** A dashboard warning symbol. [id] matches the label in the TFLite model's labels.txt. */
data class WarningLight(val id: String, val name: L10n, val meaning: L10n, val urgency: Urgency, val forBikes: Boolean, val forCars: Boolean, val partId: String? = null)

/** Offline catalog. Used as the manual picker and to explain what the camera classifier finds. */
object WarningLights {
    val all = listOf(
        WarningLight("check_engine", L10n("Check engine / MIL", "চেক ইঞ্জিন"), L10n("Engine or emissions fault stored. If flashing: severe misfire, slow down and stop soon.", "ইঞ্জিন বা ইমিশন ত্রুটি। ফ্ল্যাশ করলে মারাত্মক মিসফায়ার, গতি কমিয়ে শীঘ্রই থামুন।"), Urgency.THIS_WEEK, true, true),
        WarningLight("oil_pressure", L10n("Oil pressure (oil can)", "অয়েল প্রেসার (তেলের কেটলি)"), L10n("Oil pressure too low. Stop the engine immediately to avoid seizure.", "তেলের চাপ খুব কম। ইঞ্জিন জ্যাম এড়াতে এখনই বন্ধ করুন।"), Urgency.STOP_NOW, true, true, "engine_oil"),
        WarningLight("coolant_temp", L10n("Coolant temperature (thermometer)", "কুল্যান্ট তাপমাত্রা (থার্মোমিটার)"), L10n("Engine overheating. Stop, let it cool, never open a hot radiator cap.", "ইঞ্জিন অতিরিক্ত গরম। থামুন, ঠান্ডা হতে দিন, গরম রেডিয়েটর ক্যাপ খুলবেন না।"), Urgency.STOP_NOW, true, true, "coolant"),
        WarningLight("battery_charge", L10n("Battery / charging", "ব্যাটারি / চার্জিং"), L10n("Charging system fault (alternator/regulator). You may have limited time before the battery dies.", "চার্জিং সিস্টেমে সমস্যা (অল্টারনেটর/রেগুলেটর)। ব্যাটারি শেষ হওয়ার আগে অল্প সময় পাবেন।"), Urgency.THIS_WEEK, true, true, "battery"),
        WarningLight("brake_system", L10n("Brake system (!)", "ব্রেক সিস্টেম (!)"), L10n("Handbrake on, low brake fluid, or brake fault. If handbrake is off, do not drive.", "হ্যান্ডব্রেক লাগানো, ব্রেক ফ্লুইড কম বা ব্রেক ত্রুটি। হ্যান্ডব্রেক খোলা থাকলে চালাবেন না।"), Urgency.STOP_NOW, false, true, "brake_fluid"),
        WarningLight("abs", L10n("ABS", "এবিএস"), L10n("Anti-lock braking disabled. Normal brakes still work; brake gently on wet roads.", "এবিএস বন্ধ। সাধারণ ব্রেক কাজ করবে; ভেজা রাস্তায় আস্তে ব্রেক করুন।"), Urgency.THIS_WEEK, true, true),
        WarningLight("airbag", L10n("Airbag / SRS", "এয়ারব্যাগ / SRS"), L10n("Airbags may not deploy in a crash.", "দুর্ঘটনায় এয়ারব্যাগ কাজ নাও করতে পারে।"), Urgency.THIS_WEEK, false, true),
        WarningLight("tpms", L10n("Tyre pressure (TPMS)", "টায়ার প্রেসার (TPMS)"), L10n("One or more tyres low on air. Check and inflate.", "এক বা একাধিক টায়ারে হাওয়া কম। চেক করে হাওয়া দিন।"), Urgency.THIS_WEEK, false, true, "tyres"),
        WarningLight("fuel_low", L10n("Low fuel", "জ্বালানি কম"), L10n("Refuel soon. Running dry can damage the fuel pump.", "শীঘ্রই তেল নিন। একদম শেষ হলে ফুয়েল পাম্প নষ্ট হতে পারে।"), Urgency.SAFE, true, true),
        WarningLight("fi_light", L10n("FI / engine (bikes)", "FI / ইঞ্জিন (বাইক)"), L10n("Fuel-injection fault on the bike. Visit a brand service centre with a scanner.", "বাইকের ফুয়েল ইনজেকশন ত্রুটি। স্ক্যানার আছে এমন সার্ভিস সেন্টারে যান।"), Urgency.THIS_WEEK, true, false),
        WarningLight("power_steering", L10n("Power steering", "পাওয়ার স্টিয়ারিং"), L10n("Steering assist fault; steering will be heavy.", "স্টিয়ারিং অ্যাসিস্ট ত্রুটি; স্টিয়ারিং ভারী হবে।"), Urgency.THIS_WEEK, false, true),
        WarningLight("glow_plug", L10n("Glow plug (diesel coil)", "গ্লো প্লাগ (ডিজেল কয়েল)"), L10n("Wait until it goes off before starting. If it flashes while driving, engine fault.", "নিভে যাওয়া পর্যন্ত অপেক্ষা করে স্টার্ট দিন। চলার সময় ফ্ল্যাশ করলে ইঞ্জিন ত্রুটি।"), Urgency.SAFE, false, true),
        WarningLight("ev_battery_fault", L10n("EV battery / ready fault", "ইভি ব্যাটারি / রেডি ত্রুটি"), L10n("High-voltage or traction battery fault. Park safely and contact a battery shop.", "হাই-ভোল্টেজ বা ব্যাটারি ত্রুটি। নিরাপদে পার্ক করে ব্যাটারি দোকানে যোগাযোগ করুন।"), Urgency.STOP_NOW, true, true, "traction_battery"),
        WarningLight("seatbelt", L10n("Seat belt", "সিট বেল্ট"), L10n("Fasten seat belts.", "সিট বেল্ট বাঁধুন।"), Urgency.SAFE, false, true),
    )
    fun byId(id: String) = all.firstOrNull { it.id == id }
}
