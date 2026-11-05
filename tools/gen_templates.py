#!/usr/bin/env python3
"""Generates core/src/main/resources/templates/*.json.

Templates are plain JSON so they can also be pushed via Firebase Remote Config
(key: template_<id>) without an app update. Edit here, then run:  python3 tools/gen_templates.py
Intervals are typical defaults for Bangladeshi riding conditions; users can edit them per vehicle.
"""
import json, os

def L(en, bn): return {"en": en, "bn": bn}
def P(id, en, bn, cat, km=None, months=None, weight=5, check=None, cost=None):
    d = {"id": id, "name": L(en, bn), "category": cat, "weight": weight}
    if km: d["intervalKm"] = km
    if months: d["intervalMonths"] = months
    if check: d["checkId"] = check
    if cost: d["costMinBdt"], d["costMaxBdt"] = cost
    return d
def M(id, brand, model, energy=None, **ov):
    d = {"id": id, "brand": brand, "model": model}
    if energy: d["energyType"] = energy
    if ov:
        d["overrides"] = {k: ({"intervalKm": v[0]} if v[1] is None else {"intervalKm": v[0], "intervalMonths": v[1]}) if isinstance(v, tuple) else {"intervalKm": v} for k, v in ov.items()}
    return d
def C(id, en, bn, steps, refs): return {"id": id, "title": L(en, bn), "steps": [L(*s) for s in steps], "references": [{"label": L(*r[0]), "description": L(*r[1]), "passes": r[2], "image": r[3] if len(r) > 3 else None} for r in refs]}

# ---------- guided visual checks ----------
TYRE = C("tyre_tread", "Tyre tread check", "টায়ারের গ্রিপ (ট্রেড) চেক",
    [("Park on flat ground and look at the centre of the tyre.", "সমান জায়গায় পার্ক করে টায়ারের মাঝখানটা দেখুন।"),
     ("Find the small raised bar (wear indicator) inside the grooves.", "খাঁজের ভেতরে ছোট উঁচু দাগ (ওয়্যার ইন্ডিকেটর) খুঁজুন।"),
     ("Insert a 1-taka coin edge into the groove and compare.", "১ টাকার কয়েনের কিনারা খাঁজে ঢুকিয়ে তুলনা করুন।")],
    [(("Deep grooves", "গভীর খাঁজ"), ("Grooves clearly deeper than the wear bar (>2 mm).", "খাঁজ ওয়্যার দাগের চেয়ে স্পষ্ট গভীর (>২ মিমি)।"), True, "tyre_good"),
     (("Level with wear bar", "ওয়্যার দাগের সমান"), ("Tread is flush with the indicator: replace soon.", "ট্রেড ইন্ডিকেটরের সমান: শীঘ্রই বদলান।"), False, "tyre_worn"),
     (("Bald / cracked / bulge", "মসৃণ / ফাটা / ফোলা"), ("No grooves, cracks in sidewall or a bulge: unsafe.", "খাঁজ নেই, পাশে ফাটল বা ফোলা: বিপজ্জনক।"), False, "tyre_bald")])
CHAIN = C("chain_slack", "Chain slack check", "চেইন ঢিলা চেক",
    [("Put the bike on the main stand, gear in neutral.", "বাইক মেইন স্ট্যান্ডে রাখুন, গিয়ার নিউট্রালে।"),
     ("Push the chain up and down at the middle of the lower run.", "নিচের অংশের মাঝখানে চেইন উপরে-নিচে চাপ দিন।"),
     ("Measure total up-down movement.", "মোট কতটুকু ওঠানামা করে মাপুন।")],
    [(("20–30 mm movement", "২০–৩০ মিমি নড়ে"), ("Correct slack, chain looks oiled.", "ঠিক আছে, চেইনে তেল আছে।"), True, "chain_good"),
     (("More than 40 mm / sagging", "৪০ মিমির বেশি / ঝুলে আছে"), ("Too loose: adjust now. Can jump the sprocket.", "বেশি ঢিলা: এখনই টাইট দিন। স্প্রোকেট থেকে পড়ে যেতে পারে।"), False, "chain_loose"),
     (("Tight, rusty or kinked links", "টাইট, মরিচা বা বাঁকা লিংক"), ("Clean, lubricate, or replace chain set.", "পরিষ্কার করে তেল দিন বা চেইন সেট বদলান।"), False, "chain_rusty")])
PAD = C("brake_pad", "Brake pad thickness", "ব্রেক প্যাডের পুরুত্ব",
    [("Look through the caliper gap with a phone torch.", "ফোনের টর্চ দিয়ে ক্যালিপারের ফাঁক দিয়ে দেখুন।"),
     ("Find the pad material between the metal plate and the disc.", "ধাতব প্লেট আর ডিস্কের মাঝে প্যাডের অংশ দেখুন।"),
     ("For drum brakes: check the wear-indicator arrow on the hub.", "ড্রাম ব্রেকে: হাবের ওয়্যার ইন্ডিকেটর তীর দেখুন।")],
    [(("3 mm or more", "৩ মিমি বা বেশি"), ("Plenty of pad left.", "যথেষ্ট প্যাড আছে।"), True, "pad_good"),
     (("Less than 2 mm", "২ মিমির কম"), ("Replace pads this week.", "এই সপ্তাহে প্যাড বদলান।"), False, "pad_thin"),
     (("Metal on metal / grinding", "ধাতুতে ধাতু ঘষা / কিচকিচ শব্দ"), ("Stop riding: disc is being damaged.", "চালানো বন্ধ করুন: ডিস্ক নষ্ট হচ্ছে।"), False, "pad_gone")])
OIL = C("oil_level", "Engine oil level", "ইঞ্জিন অয়েলের লেভেল",
    [("Engine off for 5 minutes, vehicle upright on level ground.", "ইঞ্জিন ৫ মিনিট বন্ধ রাখুন, সমান জায়গায় সোজা রাখুন।"),
     ("Pull the dipstick (or look in the sight glass), wipe, re-insert, check.", "ডিপস্টিক তুলে মুছে আবার ঢুকিয়ে দেখুন (বা সাইট গ্লাস দেখুন)।")],
    [(("Between MIN and MAX, amber/brown", "MIN ও MAX এর মাঝে, হালকা বাদামি"), ("Level and colour OK.", "লেভেল ও রং ঠিক আছে।"), True, "oil_good"),
     (("Below MIN", "MIN এর নিচে"), ("Top up today; check for leaks.", "আজই তেল দিন; লিক আছে কিনা দেখুন।"), False, "oil_low"),
     (("Black & thick or milky", "কালো ঘন বা দুধের মতো"), ("Change oil. Milky oil can mean coolant leak.", "তেল বদলান। দুধের মতো হলে কুল্যান্ট লিক হতে পারে।"), False, "oil_dirty")])
BATT = C("battery_terminal", "Battery terminals", "ব্যাটারি টার্মিনাল",
    [("Engine off. Look at both battery terminals.", "ইঞ্জিন বন্ধ। ব্যাটারির দুই টার্মিনাল দেখুন।"),
     ("Try to wiggle each cable clamp by hand.", "হাত দিয়ে প্রতিটি ক্লাম্প নাড়িয়ে দেখুন।")],
    [(("Clean and tight", "পরিষ্কার ও টাইট"), ("No corrosion, clamps don't move.", "মরিচা নেই, ক্লাম্প নড়ে না।"), True, "batt_good"),
     (("White/green powder", "সাদা/সবুজ গুঁড়া"), ("Corrosion: clean with baking soda water.", "মরিচা: খাবার সোডা পানি দিয়ে পরিষ্কার করুন।"), False, "batt_corroded"),
     (("Loose clamp", "ক্লাম্প ঢিলা"), ("Tighten; loose terminals cause slow start.", "টাইট দিন; ঢিলা টার্মিনালে স্টার্ট দেরিতে হয়।"), False, "batt_loose")])
COOL = C("coolant_level", "Coolant level", "কুল্যান্ট লেভেল",
    [("Engine COLD. Never open the radiator cap when hot.", "ইঞ্জিন ঠান্ডা থাকতে হবে। গরম অবস্থায় রেডিয়েটর ক্যাপ খুলবেন না।"),
     ("Check the reservoir tank level marks.", "রিজার্ভার ট্যাংকের দাগ দেখুন।")],
    [(("Between LOW and FULL", "LOW ও FULL এর মাঝে"), ("OK.", "ঠিক আছে।"), True, "coolant_good"),
     (("Below LOW / empty", "LOW এর নিচে / খালি"), ("Top up and check for leaks.", "কুল্যান্ট দিন ও লিক দেখুন।"), False, "coolant_low"),
     (("Rusty or oily", "মরিচা রঙের বা তেলতেলে"), ("Flush coolant; oil in coolant can mean head-gasket issue.", "কুল্যান্ট বদলান; তেল মিশলে হেড গ্যাসকেট সমস্যা হতে পারে।"), False, "coolant_dirty")])
WATER = C("battery_water", "Battery water level (lead-acid)", "ব্যাটারির পানি (লেড-অ্যাসিড)",
    [("Charger unplugged. Open each cell cap of every battery.", "চার্জার খুলে নিন। প্রতিটি ব্যাটারির প্রতিটি সেলের ক্যাপ খুলুন।"),
     ("Look at the electrolyte level against the plates.", "প্লেটের তুলনায় পানির লেভেল দেখুন।")],
    [(("Plates covered by ~5–10 mm", "প্লেটের উপর ৫–১০ মিমি পানি"), ("OK.", "ঠিক আছে।"), True, "water_good"),
     (("Plates visible", "প্লেট দেখা যাচ্ছে"), ("Top up with DISTILLED water only, after charging.", "চার্জের পরে শুধু ডিস্টিলড পানি দিন।"), False, "water_low"),
     (("Bulging case / acid leak", "বডি ফুলে গেছে / অ্যাসিড লিক"), ("Replace that battery; check charger for overcharging.", "ব্যাটারিটি বদলান; চার্জার ওভারচার্জ করছে কিনা দেখুন।"), False, "water_bulge")])

CHECKS_BIKE = [TYRE, CHAIN, PAD, OIL, BATT]
CHECKS_CAR = [TYRE, PAD, OIL, BATT, COOL]

# ---------- parts ----------
def bike_parts(oil_km=1500):
    return [
        P("engine_oil", "Engine oil", "ইঞ্জিন অয়েল", "ENGINE_OIL", oil_km, 4, 20, "oil_level", (450, 1200)),
        P("chain", "Chain clean & adjust", "চেইন পরিষ্কার ও টাইট", "DRIVE", 500, 1, 8, "chain_slack", (0, 200)),
        P("chain_set", "Chain-sprocket set", "চেইন-স্প্রোকেট সেট", "DRIVE", 18000, 30, 8, None, (1800, 4000)),
        P("brake_front", "Front brake pads/shoes", "সামনের ব্রেক প্যাড/শু", "BRAKES", 8000, 12, 15, "brake_pad", (300, 900)),
        P("brake_rear", "Rear brake shoes/pads", "পেছনের ব্রেক শু/প্যাড", "BRAKES", 10000, 12, 10, "brake_pad", (250, 800)),
        P("tyre_front", "Front tyre", "সামনের টায়ার", "TYRES", 20000, 36, 10, "tyre_tread", (2000, 4500)),
        P("tyre_rear", "Rear tyre", "পেছনের টায়ার", "TYRES", 15000, 36, 12, "tyre_tread", (2500, 5500)),
        P("battery", "Battery", "ব্যাটারি", "BATTERY", None, 24, 8, "battery_terminal", (1500, 3500)),
        P("spark_plug", "Spark plug", "স্পার্ক প্লাগ", "IGNITION", 8000, 12, 6, None, (150, 600)),
        P("air_filter", "Air filter", "এয়ার ফিল্টার", "FILTER", 6000, 12, 6, None, (200, 700)),
        P("clutch_cable", "Clutch & throttle cable lube", "ক্লাচ ও থ্রটল তারে তেল", "OTHER", 3000, 3, 3, None, (0, 150)),
        P("fork_oil", "Front fork oil", "ফর্ক অয়েল", "OTHER", 15000, 24, 4, None, (500, 1200)),
        P("brake_fluid", "Brake fluid (disc)", "ব্রেক ফ্লুইড (ডিস্ক)", "BRAKES", None, 24, 5, None, (300, 600)),
    ]

def scooter_parts():
    return [
        P("engine_oil", "Engine oil", "ইঞ্জিন অয়েল", "ENGINE_OIL", 2000, 4, 20, "oil_level", (450, 1000)),
        P("gear_oil", "Transmission (gear) oil", "গিয়ার অয়েল", "ENGINE_OIL", 6000, 12, 6, None, (200, 400)),
        P("cvt_belt", "CVT belt & rollers", "সিভিটি বেল্ট ও রোলার", "DRIVE", 18000, 24, 10, None, (1500, 3500)),
        P("brake_front", "Front brake", "সামনের ব্রেক", "BRAKES", 8000, 12, 15, "brake_pad", (300, 900)),
        P("brake_rear", "Rear brake shoes", "পেছনের ব্রেক শু", "BRAKES", 10000, 12, 10, "brake_pad", (250, 700)),
        P("tyre_front", "Front tyre", "সামনের টায়ার", "TYRES", 15000, 36, 10, "tyre_tread", (1800, 3500)),
        P("tyre_rear", "Rear tyre", "পেছনের টায়ার", "TYRES", 12000, 36, 12, "tyre_tread", (2000, 4000)),
        P("battery", "Battery", "ব্যাটারি", "BATTERY", None, 24, 8, "battery_terminal", (1500, 3000)),
        P("spark_plug", "Spark plug", "স্পার্ক প্লাগ", "IGNITION", 8000, 12, 6, None, (150, 500)),
        P("air_filter", "Air filter", "এয়ার ফিল্টার", "FILTER", 6000, 12, 6, None, (250, 700)),
    ]

def car_parts(oil_km=5000):
    return [
        P("engine_oil", "Engine oil & oil filter", "ইঞ্জিন অয়েল ও অয়েল ফিল্টার", "ENGINE_OIL", oil_km, 6, 20, "oil_level", (3500, 7500)),
        P("air_filter", "Air filter", "এয়ার ফিল্টার", "FILTER", 15000, 12, 6, None, (600, 1800)),
        P("ac_filter", "AC cabin filter", "এসি কেবিন ফিল্টার", "FILTER", 15000, 12, 3, None, (500, 1500)),
        P("fuel_filter", "Fuel filter", "ফুয়েল ফিল্টার", "FILTER", 40000, 36, 5, None, (1200, 3500)),
        P("spark_plugs", "Spark plugs", "স্পার্ক প্লাগ", "IGNITION", 40000, 36, 7, None, (2000, 8000)),
        P("brake_pads", "Front brake pads", "সামনের ব্রেক প্যাড", "BRAKES", 30000, 24, 15, "brake_pad", (2500, 6000)),
        P("brake_fluid", "Brake fluid", "ব্রেক ফ্লুইড", "BRAKES", 40000, 24, 6, None, (800, 2000)),
        P("tyres", "Tyres", "টায়ার", "TYRES", 45000, 48, 12, "tyre_tread", (24000, 48000)),
        P("tyre_rotation", "Tyre rotation & alignment", "টায়ার রোটেশন ও অ্যালাইনমেন্ট", "TYRES", 10000, 12, 4, None, (800, 2000)),
        P("battery", "Battery", "ব্যাটারি", "BATTERY", None, 30, 8, "battery_terminal", (7000, 14000)),
        P("coolant", "Coolant", "কুল্যান্ট", "COOLING", 40000, 24, 8, "coolant_level", (1200, 3000)),
        P("atf", "Transmission fluid (ATF/CVT)", "ট্রান্সমিশন ফ্লুইড (ATF/CVT)", "ENGINE_OIL", 40000, 48, 8, None, (4000, 12000)),
        P("ac_service", "AC gas & service", "এসি গ্যাস ও সার্ভিস", "OTHER", None, 12, 3, None, (2000, 5000)),
        P("wiper", "Wiper blades", "ওয়াইপার ব্লেড", "BODY", None, 12, 2, None, (600, 1500)),
    ]

def cng_kit_parts():
    return [
        P("cng_kit_tune", "CNG kit tuning & leak test", "সিএনজি কিট টিউনিং ও লিক টেস্ট", "CNG_KIT", 10000, 6, 10, None, (500, 1500)),
        P("cng_filter", "CNG filter", "সিএনজি ফিল্টার", "CNG_KIT", 10000, 12, 5, None, (300, 900)),
    ]

def heavy_parts(oil_km):
    return [
        P("engine_oil", "Engine oil & filter", "ইঞ্জিন অয়েল ও ফিল্টার", "ENGINE_OIL", oil_km, 4, 20, "oil_level", (5000, 15000)),
        P("fuel_filter", "Diesel fuel filter / water separator", "ডিজেল ফুয়েল ফিল্টার / ওয়াটার সেপারেটর", "FILTER", 15000, 6, 8, None, (1200, 4000)),
        P("air_filter", "Air filter", "এয়ার ফিল্টার", "FILTER", 15000, 6, 6, None, (1500, 5000)),
        P("brakes", "Brake linings/pads & adjustment", "ব্রেক লাইনার/প্যাড ও অ্যাডজাস্টমেন্ট", "BRAKES", 15000, 6, 15, "brake_pad", (3000, 15000)),
        P("brake_air", "Air brake tank drain & check", "এয়ার ব্রেক ট্যাংক ড্রেন ও চেক", "BRAKES", None, 1, 5, None, (0, 500)),
        P("tyres", "Tyres (incl. spare)", "টায়ার (স্পেয়ার সহ)", "TYRES", 60000, 36, 12, "tyre_tread", (20000, 120000)),
        P("greasing", "Chassis greasing", "চেসিস গ্রিজিং", "OTHER", 5000, 1, 5, None, (300, 1200)),
        P("clutch", "Clutch plate inspection", "ক্লাচ প্লেট পরীক্ষা", "DRIVE", 40000, 24, 6, None, (5000, 25000)),
        P("gear_oil", "Gearbox & differential oil", "গিয়ারবক্স ও ডিফারেনশিয়াল অয়েল", "ENGINE_OIL", 40000, 24, 6, None, (3000, 10000)),
        P("coolant", "Coolant", "কুল্যান্ট", "COOLING", 40000, 24, 8, "coolant_level", (1500, 5000)),
        P("battery", "Batteries", "ব্যাটারি", "BATTERY", None, 24, 8, "battery_terminal", (8000, 30000)),
        P("wheel_bearing", "Wheel bearings & hub grease", "হুইল বিয়ারিং ও হাব গ্রিজ", "OTHER", 30000, 12, 5, None, (1500, 6000)),
    ]

DOC_BIKE = ["REGISTRATION", "TAX_TOKEN", "INSURANCE", "DRIVING_LICENCE"]
DOC_CAR = ["REGISTRATION", "FITNESS", "TAX_TOKEN", "INSURANCE", "DRIVING_LICENCE"]
DOC_COMMERCIAL = DOC_CAR + ["ROUTE_PERMIT"]

templates = []

templates.append({"id": "motorcycle", "version": 1, "name": L("Motorcycle", "মোটরসাইকেল"), "defaultEnergy": "OCTANE",
    "parts": bike_parts(), "documents": DOC_BIKE, "checks": CHECKS_BIKE, "symptomTreeId": "motorcycle", "fleetFriendly": True,
    "models": [
        M("bajaj_pulsar_150", "Bajaj", "Pulsar 150", engine_oil=1500),
        M("bajaj_pulsar_n160", "Bajaj", "Pulsar N160", engine_oil=2000),
        M("bajaj_discover_125", "Bajaj", "Discover 125", engine_oil=1500),
        M("bajaj_platina_100", "Bajaj", "Platina 100", engine_oil=1500),
        M("hero_splendor_plus", "Hero", "Splendor+", engine_oil=1500),
        M("hero_glamour", "Hero", "Glamour", engine_oil=1500),
        M("tvs_apache_rtr_160_4v", "TVS", "Apache RTR 160 4V", engine_oil=2000),
        M("tvs_metro_plus", "TVS", "Metro Plus", engine_oil=1500),
        M("honda_cb_shine", "Honda", "CB Shine", engine_oil=1500),
        M("honda_livo", "Honda", "Livo", engine_oil=1500),
        M("honda_hornet_2", "Honda", "CB Hornet 2.0", engine_oil=2000),
        M("yamaha_fzs_v3", "Yamaha", "FZS-FI V3", engine_oil=2000, spark_plug=10000),
        M("yamaha_r15_v4", "Yamaha", "R15 V4", engine_oil=2000, spark_plug=10000),
        M("suzuki_gixxer", "Suzuki", "Gixxer", engine_oil=2000),
        M("runner_bullet", "Runner", "Bullet 100", engine_oil=1000),
        M("royal_enfield_classic_350", "Royal Enfield", "Classic 350", engine_oil=3000),
    ]})

templates.append({"id": "scooter", "version": 1, "name": L("Scooter", "স্কুটার"), "defaultEnergy": "OCTANE",
    "parts": scooter_parts(), "documents": DOC_BIKE, "checks": [TYRE, PAD, OIL, BATT], "symptomTreeId": "motorcycle", "fleetFriendly": True,
    "models": [
        M("honda_dio", "Honda", "Dio"),
        M("tvs_ntorq_125", "TVS", "Ntorq 125"),
        M("yamaha_ray_zr", "Yamaha", "Ray ZR 125"),
        M("suzuki_access_125", "Suzuki", "Access 125"),
        M("vespa_vxl", "Vespa", "VXL 125", engine_oil=3000),
    ]})

templates.append({"id": "car", "version": 1, "name": L("Car", "প্রাইভেট কার"), "defaultEnergy": "OCTANE",
    "parts": car_parts() + cng_kit_parts(), "documents": DOC_CAR, "checks": CHECKS_CAR, "symptomTreeId": "car", "supportsObd": True,
    "models": [
        M("toyota_axio", "Toyota", "Corolla Axio", engine_oil=5000),
        M("toyota_allion", "Toyota", "Allion", engine_oil=5000),
        M("toyota_premio", "Toyota", "Premio", engine_oil=5000),
        M("toyota_corolla_x", "Toyota", "Corolla X / G (old)", engine_oil=4000),
        M("toyota_prius", "Toyota", "Prius (hybrid)", engine_oil=7500, battery=(None, 48)),
        M("toyota_aqua", "Toyota", "Aqua (hybrid)", engine_oil=7500),
        M("honda_vezel", "Honda", "Vezel", engine_oil=5000),
        M("honda_grace", "Honda", "Grace (hybrid)", engine_oil=7500),
        M("nissan_xtrail", "Nissan", "X-Trail", engine_oil=5000),
        M("mitsubishi_lancer", "Mitsubishi", "Lancer", engine_oil=5000),
        M("suzuki_alto", "Suzuki", "Alto 800", engine_oil=5000),
        M("toyota_avanza", "Toyota", "Avanza (2004–2011)", engine_oil=5000, spark_plugs=20000),
        M("toyota_avanza_2012", "Toyota", "Avanza (2012 on)", engine_oil=5000),
        M("toyota_axio_cng", "Toyota", "Axio (CNG converted)", "PETROL_CNG", engine_oil=4000, spark_plugs=20000),
    ]})

templates.append({"id": "cng_auto", "version": 1, "name": L("CNG auto-rickshaw", "সিএনজি অটোরিকশা"), "defaultEnergy": "CNG",
    "parts": [
        P("engine_oil", "Engine oil", "ইঞ্জিন অয়েল", "ENGINE_OIL", 1500, 1, 20, "oil_level", (500, 900)),
        P("gear_oil", "Gear oil", "গিয়ার অয়েল", "ENGINE_OIL", 6000, 6, 6, None, (300, 600)),
        P("spark_plug", "Spark plug", "স্পার্ক প্লাগ", "IGNITION", 5000, 3, 8, None, (200, 500)),
        P("air_filter", "Air filter", "এয়ার ফিল্টার", "FILTER", 5000, 3, 6, None, (250, 600)),
        P("brakes", "Brake shoes", "ব্রেক শু", "BRAKES", 8000, 6, 15, "brake_pad", (400, 1200)),
        P("clutch", "Clutch plate & cable", "ক্লাচ প্লেট ও তার", "DRIVE", 15000, 12, 6, None, (800, 2500)),
        P("tyres", "Tyres", "টায়ার", "TYRES", 20000, 18, 12, "tyre_tread", (2500, 4500)),
        P("battery", "Battery", "ব্যাটারি", "BATTERY", None, 18, 6, "battery_terminal", (2500, 4500)),
        P("cng_kit_tune", "CNG kit service & leak test", "সিএনজি কিট সার্ভিস ও লিক টেস্ট", "CNG_KIT", 10000, 3, 10, None, (300, 1000)),
        P("cng_filter", "CNG filter", "সিএনজি ফিল্টার", "CNG_KIT", 10000, 6, 4, None, (300, 700)),
    ],
    "documents": DOC_COMMERCIAL + ["CNG_CYLINDER_RETEST"], "checks": [TYRE, PAD, OIL, BATT], "symptomTreeId": "cng_auto", "fleetFriendly": True,
    "models": [M("bajaj_re_4s", "Bajaj", "RE 4S"), M("bajaj_re_2s", "Bajaj", "RE 2-stroke (old)", engine_oil=1000), M("tvs_king", "TVS", "King")]})

ELEC_CHECKS = [TYRE, PAD, WATER, BATT]
templates.append({"id": "easy_bike", "version": 1, "name": L("Electric easy-bike", "ইজি বাইক (ব্যাটারি চালিত)"), "defaultEnergy": "ELECTRIC_LEAD_ACID",
    "parts": [
        P("traction_battery", "Battery set (capacity)", "ব্যাটারি সেট (ক্ষমতা)", "TRACTION_BATTERY", 25000, 18, 25, "battery_water", (40000, 75000)),
        P("battery_water", "Battery water top-up", "ব্যাটারিতে পানি দেওয়া", "TRACTION_BATTERY", None, 1, 10, "battery_water", (50, 200)),
        P("terminals", "Terminal cleaning & tightening", "টার্মিনাল পরিষ্কার ও টাইট", "ELECTRICAL", None, 1, 6, "battery_terminal", (0, 200)),
        P("charger_check", "Charger output check", "চার্জার আউটপুট চেক", "ELECTRICAL", None, 6, 6, None, (200, 800)),
        P("motor_controller", "Motor & controller check", "মোটর ও কন্ট্রোলার চেক", "ELECTRICAL", 10000, 6, 8, None, (300, 1500)),
        P("brakes", "Brake shoes & cable", "ব্রেক শু ও তার", "BRAKES", 5000, 4, 15, "brake_pad", (300, 900)),
        P("tyres", "Tyres", "টায়ার", "TYRES", 12000, 12, 10, "tyre_tread", (1500, 3500)),
        P("bearings", "Wheel bearings & greasing", "হুইল বিয়ারিং ও গ্রিজ", "OTHER", 6000, 6, 5, None, (300, 1000)),
        P("differential_oil", "Differential/gear oil", "ডিফারেনশিয়াল অয়েল", "ENGINE_OIL", 10000, 12, 4, None, (300, 700)),
    ],
    "documents": ["REGISTRATION", "INSURANCE", "ROUTE_PERMIT", "DRIVING_LICENCE"], "checks": ELEC_CHECKS, "symptomTreeId": "easy_bike", "fleetFriendly": True,
    "models": [M("easybike_lead_48v", "Generic", "48V lead-acid (4×12V)"), M("easybike_lead_60v", "Generic", "60V lead-acid (5×12V)"),
               M("easybike_lithium", "Generic", "Lithium pack", "ELECTRIC_LITHIUM", traction_battery=(60000, 48), battery_water=(None, 120))]})

templates.append({"id": "e_scooter", "version": 1, "name": L("Electric scooter / e-bike", "ইলেকট্রিক স্কুটার / ই-বাইক"), "defaultEnergy": "ELECTRIC_LITHIUM",
    "parts": [
        P("traction_battery", "Battery pack (capacity)", "ব্যাটারি প্যাক (ক্ষমতা)", "TRACTION_BATTERY", 30000, 36, 25, None, (20000, 60000)),
        P("terminals", "Connectors & wiring check", "কানেক্টর ও ওয়্যারিং চেক", "ELECTRICAL", None, 3, 6, "battery_terminal", (0, 300)),
        P("brake_front", "Front brake", "সামনের ব্রেক", "BRAKES", 6000, 12, 15, "brake_pad", (300, 900)),
        P("brake_rear", "Rear brake", "পেছনের ব্রেক", "BRAKES", 8000, 12, 10, "brake_pad", (300, 800)),
        P("tyre_front", "Front tyre", "সামনের টায়ার", "TYRES", 12000, 36, 10, "tyre_tread", (1500, 3000)),
        P("tyre_rear", "Rear tyre", "পেছনের টায়ার", "TYRES", 10000, 36, 12, "tyre_tread", (1500, 3500)),
        P("hub_motor", "Hub motor bearings check", "হাব মোটর বিয়ারিং চেক", "ELECTRICAL", 10000, 12, 6, None, (300, 1500)),
    ],
    "documents": DOC_BIKE, "checks": [TYRE, PAD, BATT], "symptomTreeId": "easy_bike", "fleetFriendly": True,
    "models": [M("generic_lithium_scooter", "Generic", "Lithium e-scooter"), M("generic_lead_scooter", "Generic", "Lead-acid e-scooter", "ELECTRIC_LEAD_ACID")]})

templates.append({"id": "microbus", "version": 1, "name": L("Microbus", "মাইক্রোবাস"), "defaultEnergy": "PETROL_CNG",
    "parts": car_parts(4000) + cng_kit_parts(), "documents": DOC_COMMERCIAL, "checks": CHECKS_CAR, "symptomTreeId": "car", "supportsObd": True, "fleetFriendly": True,
    "models": [M("toyota_hiace", "Toyota", "HiAce", "DIESEL", engine_oil=5000), M("toyota_noah", "Toyota", "Noah"),
               M("nissan_caravan", "Nissan", "Caravan", "DIESEL", engine_oil=5000), M("toyota_hiace_cng", "Toyota", "HiAce (CNG)", "PETROL_CNG", engine_oil=3500)]})

templates.append({"id": "pickup", "version": 1, "name": L("Pickup / light truck", "পিকআপ / ছোট ট্রাক"), "defaultEnergy": "DIESEL",
    "parts": heavy_parts(5000), "documents": DOC_COMMERCIAL, "checks": CHECKS_CAR, "symptomTreeId": "heavy", "supportsObd": True, "fleetFriendly": True,
    "models": [M("tata_ace", "Tata", "Ace / Super Ace"), M("mahindra_bolero_pickup", "Mahindra", "Bolero Pik-Up"),
               M("toyota_hilux", "Toyota", "Hilux"), M("isuzu_dmax", "Isuzu", "D-Max"), M("tata_ultra", "Tata", "Ultra 1014")]})

templates.append({"id": "truck", "version": 1, "name": L("Truck / covered van", "ট্রাক / কাভার্ড ভ্যান"), "defaultEnergy": "DIESEL",
    "parts": heavy_parts(8000), "documents": DOC_COMMERCIAL, "checks": CHECKS_CAR, "symptomTreeId": "heavy", "fleetFriendly": True,
    "models": [M("tata_lpt_1613", "Tata", "LPT 1613"), M("ashok_leyland_1616", "Ashok Leyland", "Ecomet 1616"),
               M("hino_ranger", "Hino", "Ranger / FM"), M("eicher_pro", "Eicher", "Pro 3015")]})

templates.append({"id": "suv", "version": 1, "name": L("SUV / jeep", "এসইউভি / জিপ"), "defaultEnergy": "OCTANE",
    "parts": car_parts(5000), "documents": DOC_CAR, "checks": CHECKS_CAR, "symptomTreeId": "car", "supportsObd": True, "fleetFriendly": True,
    "models": [M("toyota_prado", "Toyota", "Land Cruiser Prado", "DIESEL", engine_oil=5000), M("toyota_harrier", "Toyota", "Harrier"),
               M("toyota_rav4", "Toyota", "RAV4"), M("mitsubishi_pajero", "Mitsubishi", "Pajero", "DIESEL", engine_oil=5000),
               M("nissan_xtrail_suv", "Nissan", "X-Trail"), M("honda_crv", "Honda", "CR-V"), M("hyundai_tucson", "Hyundai", "Tucson"),
               M("mahindra_scorpio", "Mahindra", "Scorpio", "DIESEL", engine_oil=7500)]})

templates.append({"id": "bus", "version": 1, "name": L("Bus", "বাস"), "defaultEnergy": "DIESEL",
    "parts": heavy_parts(10000), "documents": DOC_COMMERCIAL, "checks": CHECKS_CAR, "symptomTreeId": "heavy", "fleetFriendly": True,
    "models": [M("hino_ak1j", "Hino", "AK1J / RM2"), M("ashok_leyland_viking", "Ashok Leyland", "Viking / Falcon"),
               M("tata_lp_bus", "Tata", "LP 1512 / Starbus"), M("isuzu_journey", "Isuzu", "Journey / NQR (mini bus)", engine_oil=7500),
               M("eicher_skyline", "Eicher", "Skyline Pro"), M("toyota_coaster", "Toyota", "Coaster (mini bus)", engine_oil=7500)]})

# For anything not listed (tractor, ambulance, jeep-converted, imported bikes...): a small, safe set of
# common parts with conservative intervals. Users can edit intervals per vehicle.
templates.append({"id": "other", "version": 1, "name": L("Other vehicle", "অন্যান্য যানবাহন"), "defaultEnergy": "DIESEL",
    "parts": [
        P("engine_oil", "Engine oil", "ইঞ্জিন অয়েল", "ENGINE_OIL", 5000, 6, 20, "oil_level", (800, 8000)),
        P("air_filter", "Air filter", "এয়ার ফিল্টার", "FILTER", 10000, 12, 6, None, (300, 3000)),
        P("fuel_filter", "Fuel filter", "ফুয়েল ফিল্টার", "FILTER", 20000, 12, 5, None, (500, 3500)),
        P("brakes", "Brake pads / shoes", "ব্রেক প্যাড / শু", "BRAKES", 15000, 12, 15, "brake_pad", (500, 8000)),
        P("tyres", "Tyres", "টায়ার", "TYRES", 40000, 36, 12, "tyre_tread", (3000, 60000)),
        P("battery", "Battery", "ব্যাটারি", "BATTERY", None, 24, 8, "battery_terminal", (2000, 15000)),
        P("coolant", "Coolant", "কুল্যান্ট", "COOLING", 40000, 24, 6, "coolant_level", (800, 4000)),
    ],
    "documents": DOC_CAR, "checks": CHECKS_CAR, "symptomTreeId": "heavy", "fleetFriendly": True, "models": []})

out = os.path.join(os.path.dirname(__file__), "..", "core", "src", "main", "resources", "templates")
os.makedirs(out, exist_ok=True)
for t in templates:
    with open(os.path.join(out, t["id"] + ".json"), "w", encoding="utf-8") as f:
        json.dump(t, f, ensure_ascii=False, indent=1)
print("wrote", len(templates), "templates,", sum(len(t["models"]) for t in templates), "model presets")
