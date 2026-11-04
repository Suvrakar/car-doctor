package com.garidoctor.core.ai

/**
 * Words people and garages use for each part, in English and Bangla (including common
 * Banglish spellings on handwritten bills). Each entry lists template part ids in priority
 * order; the caller keeps the first id that exists in the vehicle's template.
 */
object PartWords {
    data class Entry(val words: List<String>, val partIds: List<String>)

    val entries: List<Entry> = listOf(
        Entry(listOf("engine oil", "oil change", "mobil", "mobile oil", "motor oil", "lubricant", "ইঞ্জিন অয়েল", "ইঞ্জিন তেল", "মবিল", "মোবিল", "তেল বদল", "অয়েল চেঞ্জ", "oil filter", "অয়েল ফিল্টার"), listOf("engine_oil")),
        Entry(listOf("gear oil", "gearbox oil", "transmission oil", "গিয়ার অয়েল", "গিয়ার তেল"), listOf("gear_oil", "differential_oil")),
        Entry(listOf("atf", "cvt fluid", "transmission fluid"), listOf("atf")),
        Entry(listOf("air filter", "এয়ার ফিল্টার", "এয়ার ফিল্টার", "air cleaner"), listOf("air_filter")),
        Entry(listOf("ac filter", "cabin filter", "এসি ফিল্টার"), listOf("ac_filter")),
        Entry(listOf("fuel filter", "diesel filter", "ফুয়েল ফিল্টার", "ডিজেল ফিল্টার"), listOf("fuel_filter")),
        Entry(listOf("spark plug", "plug", "স্পার্ক প্লাগ", "প্লাগ"), listOf("spark_plug", "spark_plugs")),
        Entry(listOf("brake pad", "brake shoe", "brake lining", "disc pad", "ব্রেক প্যাড", "ব্রেক শু", "ব্রেক সু", "ব্রেক"), listOf("brake_front", "brake_pads", "brakes", "brake_rear")),
        Entry(listOf("rear brake", "পেছনের ব্রেক", "পিছনের ব্রেক"), listOf("brake_rear", "brakes")),
        Entry(listOf("brake oil", "brake fluid", "ব্রেক অয়েল", "ব্রেক ফ্লুইড"), listOf("brake_fluid")),
        Entry(listOf("chain sprocket", "chain set", "sprocket", "চেইন সেট", "স্প্রোকেট"), listOf("chain_set")),
        Entry(listOf("chain", "chain lube", "chain adjust", "চেইন"), listOf("chain")),
        Entry(listOf("cvt belt", "belt", "roller", "বেল্ট"), listOf("cvt_belt")),
        Entry(listOf("front tyre", "front tire", "সামনের টায়ার"), listOf("tyre_front", "tyres")),
        Entry(listOf("rear tyre", "rear tire", "back tyre", "পেছনের টায়ার", "পিছনের টায়ার"), listOf("tyre_rear", "tyres")),
        Entry(listOf("tyre", "tire", "টায়ার", "টায়ার"), listOf("tyres", "tyre_rear", "tyre_front")),
        Entry(listOf("alignment", "balancing", "wheel balance", "অ্যালাইনমেন্ট", "ব্যালান্সিং"), listOf("tyre_rotation")),
        Entry(listOf("battery water", "distilled water", "ডিস্টিল ওয়াটার", "ব্যাটারির পানি", "ব্যাটারি পানি"), listOf("battery_water")),
        Entry(listOf("battery set", "battery", "ব্যাটারি"), listOf("battery", "traction_battery")),
        Entry(listOf("coolant", "radiator", "কুল্যান্ট", "রেডিয়েটর"), listOf("coolant")),
        Entry(listOf("ac gas", "ac service", "এসি গ্যাস", "এসি সার্ভিস"), listOf("ac_service")),
        Entry(listOf("cng kit", "cng tuning", "kit tuning", "সিএনজি কিট", "কিট টিউনিং"), listOf("cng_kit_tune")),
        Entry(listOf("cng filter", "সিএনজি ফিল্টার"), listOf("cng_filter")),
        Entry(listOf("clutch", "ক্লাচ"), listOf("clutch", "clutch_cable")),
        Entry(listOf("fork oil", "ফর্ক অয়েল"), listOf("fork_oil")),
        Entry(listOf("wiper", "ওয়াইপার"), listOf("wiper")),
        Entry(listOf("greasing", "grease", "গ্রিজ"), listOf("greasing", "bearings")),
        Entry(listOf("bearing", "বিয়ারিং"), listOf("wheel_bearing", "bearings", "hub_motor")),
        Entry(listOf("charger", "চার্জার"), listOf("charger_check")),
        Entry(listOf("controller", "কন্ট্রোলার"), listOf("motor_controller")),
        Entry(listOf("terminal", "টার্মিনাল"), listOf("terminals")),
    )

    /** Part ids mentioned in [text], resolved against [available] template part ids. Longest phrases win. */
    fun find(text: String, available: Set<String>): List<String> {
        var rest = " " + text.lowercase() + " "
        val out = LinkedHashSet<String>()
        entries.flatMap { e -> e.words.map { it to e } }.sortedByDescending { it.first.length }.forEach { (w, e) ->
            if (rest.contains(w)) {
                e.partIds.firstOrNull { it in available }?.let { out += it }
                rest = rest.replace(w, " ")
            }
        }
        return out.toList()
    }
}
