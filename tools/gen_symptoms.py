#!/usr/bin/env python3
"""Generates core/src/main/resources/symptoms/*.json (rule-based symptom decision trees).

Every leaf is phrased as *possible* causes with an urgency colour and a rough BDT cost range.
Costs are indicative Dhaka workshop ranges and should be reviewed periodically.
Run: python3 tools/gen_symptoms.py
"""
import json, os
def L(en, bn): return {"en": en, "bn": bn}
def D(causes, urgency, cmin, cmax, adv, part=None, garage=None):
    d = {"causes": [{"text": L(*c[:2]), "likelihood": c[2] if len(c) > 2 else 50} for c in causes],
         "urgency": urgency, "costMinBdt": cmin, "costMaxBdt": cmax, "advice": L(*adv)}
    if part: d["partId"] = part
    if garage: d["garageType"] = garage
    return d
def O(label, nxt=None, diag=None):
    d = {"label": L(*label)}
    if nxt: d["next"] = nxt
    if diag: d["diagnosis"] = diag
    return d
def N(id, q, *opts): return {"id": id, "question": L(*q), "options": list(opts)}
def E(id, label, start): return {"id": id, "label": L(*label), "start": start}
YES = ("Yes", "হ্যাঁ"); NO = ("No", "না")
SAFE, WEEK, STOP = "SAFE", "THIS_WEEK", "STOP_NOW"
BIKE = "bike_mechanic"; CAR = "car_workshop"; BATT = "battery_shop"; CNG = "cng_kit"

trees = {}

trees["motorcycle"] = {"id": "motorcycle", "entries": [
    E("pulls", ("Pulls to one side", "এক দিকে টানে"), "pulls_q1"),
    E("brake", ("Brake feels weak / spongy / noisy", "ব্রেক দুর্বল / নরম / শব্দ করে"), "brake_q1"),
    E("start", ("Hard to start / won't start", "স্টার্ট নিতে চায় না"), "start_q1"),
    E("smoke", ("Smoke from exhaust", "সাইলেন্সার দিয়ে ধোঁয়া"), "smoke_q1"),
    E("mileage", ("Low mileage", "মাইলেজ কম"), "mil_q1"),
    E("noise", ("Rattling / chain noise", "খটখট / চেইনের শব্দ"), "noise_q1"),
    E("pickup", ("Poor pickup / misfiring / jerking", "পিকআপ কম / মিসিং / ঝাঁকুনি"), "pick_q1"),
    E("heat", ("Engine overheating", "ইঞ্জিন অতিরিক্ত গরম"), "heat_q1"),
], "nodes": [
    N("pulls_q1", ("Does it pull only when braking?", "শুধু ব্রেক করলেই কি টানে?"),
      O(YES, diag=D([("Uneven brake pad wear or stuck caliper piston", "ব্রেক প্যাড অসমান ক্ষয় বা ক্যালিপার আটকে আছে", 60), ("Bent disc", "ডিস্ক বাঁকা", 25), ("Low pressure in one tyre", "একটি টায়ারে হাওয়া কম", 15)], WEEK, 300, 2500, ("Avoid hard braking until checked.", "চেক না করা পর্যন্ত জোরে ব্রেক করবেন না।"), "brake_front", BIKE)),
      O(NO, "pulls_q2")),
    N("pulls_q2", ("Is the handlebar crooked when riding straight?", "সোজা চালালে কি হ্যান্ডেল বাঁকা থাকে?"),
      O(YES, diag=D([("Fork misalignment after a fall", "পড়ে গিয়ে ফর্ক বাঁকা", 55), ("Bent handlebar or fork tube", "হ্যান্ডেল বা ফর্ক টিউব বাঁকা", 35), ("Wheel alignment off", "চাকার অ্যালাইনমেন্ট ঠিক নেই", 10)], WEEK, 500, 4000, ("Get fork alignment checked; don't ride fast.", "ফর্ক অ্যালাইনমেন্ট চেক করান; দ্রুত চালাবেন না।"), None, BIKE)),
      O(NO, diag=D([("Rear wheel not aligned after chain adjustment", "চেইন টাইটের পর পেছনের চাকা সোজা নেই", 50), ("Low tyre pressure", "টায়ারে হাওয়া কম", 30), ("Worn steering bearing", "স্টিয়ারিং বিয়ারিং ক্ষয়", 20)], WEEK, 0, 1500, ("Check tyre pressure first; then align rear wheel marks.", "আগে টায়ারের হাওয়া দেখুন; তারপর পেছনের চাকার দাগ মিলিয়ে নিন।"), "chain", BIKE))),
    N("brake_q1", ("What do you notice?", "কী লক্ষ্য করছেন?"),
      O(("Lever/pedal goes too far, feels soft", "লিভার/প্যাডেল অনেক দূর যায়, নরম লাগে"), "brake_q2"),
      O(("Grinding / metal scraping sound", "ঘষা / ধাতব কিচকিচ শব্দ"), diag=D([("Brake pads worn to the metal", "ব্রেক প্যাড শেষ হয়ে ধাতু ঘষছে", 80), ("Stone or debris in caliper", "ক্যালিপারে পাথর/ময়লা", 20)], STOP, 400, 3000, ("Stop riding: the disc is being damaged and braking is unreliable.", "চালানো বন্ধ করুন: ডিস্ক নষ্ট হচ্ছে এবং ব্রেক ভরসাযোগ্য নয়।"), "brake_front", BIKE)),
      O(("Squeaking only", "শুধু চিঁচিঁ শব্দ"), diag=D([("Dust/glazing on pads", "প্যাডে ধুলা/চকচকে হয়ে গেছে", 60), ("Pads nearly worn", "প্যাড প্রায় শেষ", 40)], SAFE, 0, 900, ("Clean the brake; check pad thickness with the guided check.", "ব্রেক পরিষ্কার করুন; গাইডেড চেকে প্যাডের পুরুত্ব দেখুন।"), "brake_front", BIKE))),
    N("brake_q2", ("Is it a disc brake (with fluid reservoir)?", "এটা কি ডিস্ক ব্রেক (তেলের রিজার্ভার আছে)?"),
      O(YES, diag=D([("Air in brake line", "ব্রেক লাইনে বাতাস", 50), ("Old/low brake fluid or leak", "ব্রেক ফ্লুইড পুরনো/কম বা লিক", 35), ("Worn pads", "প্যাড ক্ষয়", 15)], STOP, 300, 1500, ("Spongy disc brake is dangerous: bleed the brake and refill fluid before riding.", "নরম ডিস্ক ব্রেক বিপজ্জনক: চালানোর আগে ব্লিডিং করে ফ্লুইড ভরুন।"), "brake_fluid", BIKE)),
      O(NO, diag=D([("Drum brake shoes worn", "ড্রাম ব্রেকের শু ক্ষয়", 60), ("Brake cable/rod needs adjustment", "ব্রেকের তার/রড অ্যাডজাস্ট দরকার", 40)], WEEK, 0, 800, ("Adjust the brake nut; replace shoes if indicator is past the limit.", "ব্রেকের নাট টাইট দিন; ইন্ডিকেটর সীমা পার হলে শু বদলান।"), "brake_rear", BIKE))),
    N("start_q1", ("Does the starter/horn work normally?", "সেল্ফ/হর্ন কি স্বাভাবিক কাজ করে?"),
      O(YES, "start_q2"),
      O(("Weak / clicking / dead", "দুর্বল / টিক টিক / একদম না"), diag=D([("Weak or old battery", "ব্যাটারি দুর্বল বা পুরনো", 60), ("Loose/corroded terminals", "টার্মিনাল ঢিলা/মরিচা", 25), ("Charging (regulator) fault", "চার্জিং (রেগুলেটর) সমস্যা", 15)], WEEK, 0, 3500, ("Try kick start; check terminals; test battery voltage (should be 12.6V+).", "কিক দিয়ে চেষ্টা করুন; টার্মিনাল দেখুন; ব্যাটারি ভোল্টেজ ১২.৬V+ হওয়া উচিত।"), "battery", BIKE))),
    N("start_q2", ("Is it worse in the morning / when cold?", "সকালে / ঠান্ডায় কি বেশি সমস্যা হয়?"),
      O(YES, diag=D([("Choke/idle setting (carburettor) or cold-start sensor (FI)", "চোক/আইডল সেটিং (কার্বুরেটর) বা কোল্ড-স্টার্ট সেন্সর (FI)", 45), ("Worn spark plug", "স্পার্ক প্লাগ ক্ষয়", 35), ("Old engine oil too thick", "পুরনো তেল ঘন হয়ে গেছে", 20)], SAFE, 150, 1200, ("Use choke on carb bikes; clean/replace plug; carb tuning.", "কার্বুরেটর বাইকে চোক দিন; প্লাগ পরিষ্কার/বদলান; কার্ব টিউনিং করান।"), "spark_plug", BIKE)),
      O(NO, diag=D([("Fouled spark plug", "স্পার্ক প্লাগে কার্বন জমেছে", 40), ("Dirty carburettor / fuel filter", "কার্বুরেটর / ফুয়েল ফিল্টার ময়লা", 35), ("Water or bad fuel", "পানি বা ভেজাল তেল", 25)], WEEK, 200, 1500, ("Check plug spark; drain carb bowl; refuel from a trusted pump.", "প্লাগে স্পার্ক দেখুন; কার্বের তেল ফেলে দিন; বিশ্বস্ত পাম্প থেকে তেল নিন।"), "spark_plug", BIKE))),
    N("smoke_q1", ("What colour is the smoke?", "ধোঁয়ার রং কী?"),
      O(("Blue / grey, smells of oil", "নীল / ধূসর, তেলের গন্ধ"), diag=D([("Worn piston rings / cylinder", "পিস্টন রিং / সিলিন্ডার ক্ষয়", 55), ("Valve stem seals leaking", "ভালভ সিল লিক", 30), ("Oil overfilled", "অতিরিক্ত তেল দেওয়া হয়েছে", 15)], WEEK, 2500, 12000, ("Check oil level often; plan an engine top overhaul.", "নিয়মিত তেলের লেভেল দেখুন; ইঞ্জিন টপ ওভারহল প্ল্যান করুন।"), "engine_oil", BIKE)),
      O(("Black, smells of fuel", "কালো, তেলের (পেট্রোল) গন্ধ"), diag=D([("Rich mixture: carb needs tuning", "মিশ্রণ বেশি: কার্ব টিউনিং দরকার", 50), ("Clogged air filter", "এয়ার ফিল্টার বন্ধ", 40), ("Choke stuck on", "চোক আটকে আছে", 10)], SAFE, 200, 1000, ("Clean/replace air filter and get carb tuned.", "এয়ার ফিল্টার পরিষ্কার/বদলান ও কার্ব টিউন করান।"), "air_filter", BIKE)),
      O(("White, only on cold start, disappears", "সাদা, শুধু ঠান্ডায়, পরে চলে যায়"), diag=D([("Normal condensation", "স্বাভাবিক বাষ্প", 90)], SAFE, 0, 0, ("Normal. If it stays thick after warm-up, get it checked.", "স্বাভাবিক। গরম হওয়ার পরও ঘন থাকলে চেক করান।")))),
    N("mil_q1", ("Have you checked tyre pressure in the last 2 weeks?", "গত ২ সপ্তাহে কি টায়ারের হাওয়া চেক করেছেন?"),
      O(NO, diag=D([("Low tyre pressure", "টায়ারে হাওয়া কম", 60), ("Chain too tight/dry", "চেইন টাইট/শুকনো", 20), ("Dirty air filter", "এয়ার ফিল্টার ময়লা", 20)], SAFE, 0, 500, ("Fill to recommended pressure (usually 25/33 psi front/rear) and lube chain.", "নির্দেশিত হাওয়া দিন (সাধারণত সামনে ২৫/পেছনে ৩৩ psi) ও চেইনে তেল দিন।"), "chain", BIKE)),
      O(YES, "mil_q2")),
    N("mil_q2", ("Does the wheel spin freely when lifted (no drag)?", "চাকা তুলে ঘোরালে কি সহজে ঘোরে?"),
      O(YES, diag=D([("Dirty air filter / spark plug", "এয়ার ফিল্টার / স্পার্ক প্লাগ ময়লা", 45), ("Carb/FI tuning off", "কার্ব/FI টিউনিং ঠিক নেই", 35), ("Riding style / traffic / fuel quality", "চালানোর ধরন / জ্যাম / তেলের মান", 20)], SAFE, 300, 1500, ("Do a tune-up: air filter, plug, carb cleaning.", "টিউন-আপ করান: এয়ার ফিল্টার, প্লাগ, কার্ব পরিষ্কার।"), "air_filter", BIKE)),
      O(NO, diag=D([("Dragging brake", "ব্রেক লেগে থাকছে", 70), ("Dry/tight wheel bearing", "হুইল বিয়ারিং শুকনো/টাইট", 30)], WEEK, 200, 1200, ("Loosen brake adjustment; grease/replace bearing.", "ব্রেক অ্যাডজাস্টমেন্ট ঢিলা দিন; বিয়ারিং গ্রিজ/বদলান।"), "brake_rear", BIKE))),
    N("noise_q1", ("Where does the noise come from?", "শব্দ কোথা থেকে আসে?"),
      O(("Chain area, rhythmic slapping", "চেইনের দিক থেকে, তালে তালে"), diag=D([("Chain too loose", "চেইন বেশি ঢিলা", 60), ("Worn sprocket teeth", "স্প্রোকেটের দাঁত ক্ষয়", 30), ("Dry chain", "চেইন শুকনো", 10)], WEEK, 0, 4000, ("Adjust and lube; replace chain-sprocket set if teeth are hooked.", "টাইট দিয়ে তেল দিন; দাঁত বাঁকা হলে চেইন-স্প্রোকেট সেট বদলান।"), "chain", BIKE)),
      O(("Engine, ticking that rises with RPM", "ইঞ্জিন থেকে, RPM বাড়লে টিকটিক বাড়ে"), diag=D([("Valve clearance (tappet) needs adjustment", "ভালভ ক্লিয়ারেন্স (ট্যাপেট) অ্যাডজাস্ট দরকার", 60), ("Low engine oil", "ইঞ্জিন অয়েল কম", 30), ("Cam chain tensioner", "ক্যাম চেইন টেনশনার", 10)], WEEK, 300, 2000, ("Check oil level now; get tappet set.", "এখনই তেলের লেভেল দেখুন; ট্যাপেট সেট করান।"), "engine_oil", BIKE)),
      O(("Front, over bumps", "সামনে, ঝাঁকুনিতে"), diag=D([("Fork oil low / worn bushes", "ফর্ক অয়েল কম / বুশ ক্ষয়", 50), ("Loose steering bearing", "স্টিয়ারিং বিয়ারিং ঢিলা", 35), ("Loose mudguard/bolts", "মাডগার্ড/বোল্ট ঢিলা", 15)], WEEK, 0, 2500, ("Check bolts; service fork.", "বোল্ট দেখুন; ফর্ক সার্ভিস করান।"), "fork_oil", BIKE))),
    N("pick_q1", ("Is the check-engine / FI light on?", "চেক ইঞ্জিন / FI বাতি কি জ্বলছে?"),
      O(YES, diag=D([("FI sensor fault (TPS, O2, MAP)", "FI সেন্সর সমস্যা (TPS, O2, MAP)", 60), ("Injector clogged", "ইনজেক্টর বন্ধ", 25), ("Low fuel pressure", "ফুয়েল প্রেসার কম", 15)], WEEK, 500, 6000, ("Visit an authorised service centre with a scanner for your brand.", "আপনার ব্র্যান্ডের স্ক্যানার আছে এমন সার্ভিস সেন্টারে যান।"), None, BIKE)),
      O(NO, diag=D([("Worn spark plug / weak ignition", "স্পার্ক প্লাগ ক্ষয় / দুর্বল ইগনিশন", 40), ("Clutch slipping", "ক্লাচ স্লিপ করছে", 25), ("Dirty carburettor / air filter", "কার্বুরেটর / এয়ার ফিল্টার ময়লা", 25), ("Chain too tight", "চেইন বেশি টাইট", 10)], SAFE, 200, 2500, ("Replace plug and clean air filter first — cheapest fixes.", "প্রথমে প্লাগ বদলান ও এয়ার ফিল্টার পরিষ্কার করুন — সবচেয়ে সস্তা সমাধান।"), "spark_plug", BIKE))),
    N("heat_q1", ("Is the bike liquid-cooled (has a radiator)?", "বাইকে কি রেডিয়েটর আছে (লিকুইড কুলড)?"),
      O(YES, diag=D([("Low coolant / leak", "কুল্যান্ট কম / লিক", 50), ("Radiator fan not working", "রেডিয়েটর ফ্যান কাজ করছে না", 35), ("Thermostat stuck", "থার্মোস্ট্যাট আটকে আছে", 15)], STOP, 300, 3000, ("Stop and let it cool. Do not open the cap hot.", "থামুন ও ঠান্ডা হতে দিন। গরম অবস্থায় ক্যাপ খুলবেন না।"), None, BIKE)),
      O(NO, diag=D([("Low or old engine oil", "ইঞ্জিন অয়েল কম বা পুরনো", 55), ("Lean mixture (carb tuning)", "মিশ্রণ কম (কার্ব টিউনিং)", 30), ("Long idling in traffic", "জ্যামে দীর্ঘ সময় স্টার্ট রাখা", 15)], WEEK, 450, 1500, ("Check oil level now; change oil if overdue.", "এখনই তেলের লেভেল দেখুন; সময় পেরোলে তেল বদলান।"), "engine_oil", BIKE))),
]}

trees["car"] = {"id": "car", "entries": [
    E("smoke_white", ("White smoke from exhaust", "সাইলেন্সার দিয়ে সাদা ধোঁয়া"), "ws_q1"),
    E("overheat", ("Temperature gauge high", "টেম্পারেচার বেশি দেখাচ্ছে"), "oh_q1"),
    E("brake", ("Brake noise / vibration / soft pedal", "ব্রেকে শব্দ / কাঁপুনি / নরম প্যাডেল"), "br_q1"),
    E("ac", ("AC not cooling", "এসি ঠান্ডা করছে না"), "ac_q1"),
    E("start", ("Won't start / slow crank", "স্টার্ট হয় না / ধীরে ঘোরে"), "st_q1"),
    E("vibration", ("Vibration at speed / pulls to side", "গতিতে কাঁপে / এক দিকে টানে"), "vib_q1"),
    E("cng", ("Problems on CNG (jerking, no start on gas)", "সিএনজিতে সমস্যা (ঝাঁকুনি, গ্যাসে স্টার্ট নেয় না)"), "cng_q1"),
    E("light", ("Check-engine light on", "চেক ইঞ্জিন লাইট জ্বলছে"), "cel_q1"),
], "nodes": [
    N("ws_q1", ("Does the white smoke continue after the engine is fully warm?", "ইঞ্জিন পুরো গরম হওয়ার পরও কি সাদা ধোঁয়া থাকে?"),
      O(NO, diag=D([("Normal condensation", "স্বাভাবিক বাষ্প", 90)], SAFE, 0, 0, ("Normal on cool mornings.", "ঠান্ডা সকালে এটা স্বাভাবিক।"))),
      O(YES, "ws_q2")),
    N("ws_q2", ("Is the coolant level dropping without a visible leak?", "দৃশ্যমান লিক ছাড়াই কি কুল্যান্ট কমে যাচ্ছে?"),
      O(YES, diag=D([("Head gasket leak", "হেড গ্যাসকেট লিক", 60), ("Cracked cylinder head", "সিলিন্ডার হেড ফাটা", 25), ("Coolant leak into intake", "ইনটেকে কুল্যান্ট লিক", 15)], STOP, 8000, 45000, ("Stop driving: risk of severe engine damage. Tow to a workshop.", "চালানো বন্ধ করুন: ইঞ্জিন মারাত্মক নষ্ট হতে পারে। টো করে ওয়ার্কশপে নিন।"), "coolant", CAR)),
      O(NO, diag=D([("Transmission fluid being burnt (vacuum modulator)", "ট্রান্সমিশন ফ্লুইড পুড়ছে", 40), ("Rich mixture / injector leak", "মিশ্রণ বেশি / ইনজেক্টর লিক", 35), ("Condensation in short trips", "ছোট যাত্রায় বাষ্প", 25)], WEEK, 1000, 10000, ("Get a scan and compression test.", "স্ক্যান ও কম্প্রেশন টেস্ট করান।"), None, CAR))),
    N("oh_q1", ("Is the coolant reservoir low (engine cold)?", "রিজার্ভারে কি কুল্যান্ট কম (ইঞ্জিন ঠান্ডা অবস্থায়)?"),
      O(YES, diag=D([("Coolant leak (hose, radiator, water pump)", "কুল্যান্ট লিক (হোস, রেডিয়েটর, ওয়াটার পাম্প)", 70), ("Head gasket", "হেড গ্যাসকেট", 30)], STOP, 500, 15000, ("Stop now. Let it cool, top up, and get the leak fixed.", "এখনই থামুন। ঠান্ডা হলে কুল্যান্ট দিন ও লিক ঠিক করান।"), "coolant", CAR)),
      O(NO, "oh_q2")),
    N("oh_q2", ("Does it overheat mainly in traffic jams?", "মূলত জ্যামে কি গরম হয়?"),
      O(YES, diag=D([("Radiator fan / fan relay fault", "রেডিয়েটর ফ্যান / রিলে সমস্যা", 65), ("Radiator fins clogged", "রেডিয়েটরের জালি বন্ধ", 35)], STOP, 800, 6000, ("Turn off AC, pull over safely; check whether fan runs.", "এসি বন্ধ করে নিরাপদে থামুন; ফ্যান ঘুরছে কিনা দেখুন।"), "coolant", CAR)),
      O(NO, diag=D([("Thermostat stuck closed", "থার্মোস্ট্যাট আটকে বন্ধ", 45), ("Water pump failing", "ওয়াটার পাম্প দুর্বল", 35), ("Clogged radiator", "রেডিয়েটর বন্ধ", 20)], STOP, 1500, 10000, ("Do not keep driving with high temperature.", "উচ্চ তাপমাত্রায় চালিয়ে যাবেন না।"), "coolant", CAR))),
    N("br_q1", ("What is the brake symptom?", "ব্রেকের সমস্যা কী?"),
      O(("Pedal soft / sinks to floor", "প্যাডেল নরম / নিচে চলে যায়"), diag=D([("Brake fluid leak", "ব্রেক ফ্লুইড লিক", 50), ("Air in lines", "লাইনে বাতাস", 30), ("Master cylinder failing", "মাস্টার সিলিন্ডার নষ্ট", 20)], STOP, 800, 8000, ("Do not drive. Brakes may fail.", "চালাবেন না। ব্রেক ফেল করতে পারে।"), "brake_fluid", CAR)),
      O(("Steering shakes when braking", "ব্রেক করলে স্টিয়ারিং কাঁপে"), diag=D([("Warped brake discs (rotors)", "ব্রেক ডিস্ক বাঁকা", 75), ("Worn suspension bushes", "সাসপেনশন বুশ ক্ষয়", 25)], WEEK, 2000, 9000, ("Skim or replace discs; replace pads together.", "ডিস্ক স্কিম বা বদলান; প্যাডও একসাথে বদলান।"), "brake_pads", CAR)),
      O(("Grinding / squealing", "ঘষা / চিঁচিঁ শব্দ"), diag=D([("Pads worn to wear indicator", "প্যাড ওয়্যার ইন্ডিকেটর পর্যন্ত ক্ষয়", 75), ("Debris / rust", "ময়লা / মরিচা", 25)], WEEK, 2500, 6000, ("Replace pads soon to avoid disc damage.", "ডিস্ক নষ্ট হওয়ার আগে প্যাড বদলান।"), "brake_pads", CAR))),
    N("ac_q1", ("Does the AC blow air but not cold?", "এসি বাতাস দেয় কিন্তু ঠান্ডা নয়?"),
      O(YES, diag=D([("Low refrigerant (gas leak)", "গ্যাস কম (লিক)", 55), ("Compressor clutch not engaging", "কম্প্রেসর ক্লাচ কাজ করছে না", 25), ("Condenser fan / dirty condenser", "কনডেন্সার ফ্যান / ময়লা কনডেন্সার", 20)], SAFE, 2000, 25000, ("Leak test before refilling gas, or it will leak again.", "গ্যাস দেওয়ার আগে লিক টেস্ট করান, নাহলে আবার বের হয়ে যাবে।"), "ac_service", CAR)),
      O(("Weak airflow", "বাতাস কম"), diag=D([("Clogged cabin filter", "কেবিন ফিল্টার বন্ধ", 70), ("Blower motor weak", "ব্লোয়ার মোটর দুর্বল", 30)], SAFE, 500, 4000, ("Replace cabin filter first.", "আগে কেবিন ফিল্টার বদলান।"), "ac_filter", CAR))),
    N("st_q1", ("When you turn the key, what happens?", "চাবি ঘোরালে কী হয়?"),
      O(("Click or nothing, lights dim", "টিক শব্দ বা কিছুই না, লাইট মৃদু"), diag=D([("Weak/dead battery", "ব্যাটারি দুর্বল/শেষ", 65), ("Corroded terminals", "টার্মিনালে মরিচা", 25), ("Starter motor", "স্টার্টার মোটর", 10)], WEEK, 0, 14000, ("Try a jump start; test battery (12.4V+ at rest).", "জাম্প স্টার্ট দিন; ব্যাটারি টেস্ট করুন (বিশ্রামে ১২.৪V+)।"), "battery", CAR)),
      O(("Cranks normally but won't fire", "ঘোরে কিন্তু চালু হয় না"), diag=D([("Fuel pump / relay", "ফুয়েল পাম্প / রিলে", 40), ("Ignition coil / plugs", "ইগনিশন কয়েল / প্লাগ", 30), ("Immobiliser / sensor (crank sensor)", "ইমোবিলাইজার / ক্র্যাংক সেন্সর", 30)], WEEK, 1000, 15000, ("Scan with OBD dongle for codes.", "OBD ডঙ্গল দিয়ে কোড স্ক্যান করুন।"), "spark_plugs", CAR))),
    N("vib_q1", ("At what speed does it vibrate?", "কোন গতিতে কাঁপে?"),
      O(("60–100 km/h, steering shakes", "৬০–১০০ কিমি/ঘণ্টা, স্টিয়ারিং কাঁপে"), diag=D([("Wheel balancing needed", "হুইল ব্যালান্সিং দরকার", 65), ("Bent rim / tyre bulge", "রিম বাঁকা / টায়ার ফোলা", 35)], WEEK, 400, 6000, ("Get wheel balancing and inspect tyres for bulges.", "হুইল ব্যালান্সিং করান ও টায়ার ফোলা কিনা দেখুন।"), "tyre_rotation", CAR)),
      O(("Pulls to one side always", "সবসময় এক দিকে টানে"), diag=D([("Wheel alignment", "হুইল অ্যালাইনমেন্ট", 60), ("Uneven tyre pressure", "টায়ারে অসমান হাওয়া", 30), ("Sticking brake caliper", "ব্রেক ক্যালিপার আটকে আছে", 10)], WEEK, 0, 2500, ("Equalise tyre pressure; get alignment.", "টায়ারের হাওয়া সমান করুন; অ্যালাইনমেন্ট করান।"), "tyre_rotation", CAR))),
    N("cng_q1", ("Does the car run fine on petrol?", "পেট্রোলে কি গাড়ি ঠিক চলে?"),
      O(YES, diag=D([("CNG kit tuning / reducer needs service", "সিএনজি কিট টিউনিং / রিডিউসার সার্ভিস দরকার", 50), ("Clogged CNG filter", "সিএনজি ফিল্টার বন্ধ", 30), ("Gas leak — smell of gas?", "গ্যাস লিক — গন্ধ পাচ্ছেন?", 20)], WEEK, 500, 4000, ("If you smell gas, stop, close the cylinder valve, and ventilate. Otherwise get the kit serviced.", "গ্যাসের গন্ধ পেলে থামুন, সিলিন্ডার ভালভ বন্ধ করুন ও দরজা খুলে দিন। নাহলে কিট সার্ভিস করান।"), "cng_kit_tune", CNG)),
      O(NO, diag=D([("Ignition: plugs and coils (CNG wears them faster)", "ইগনিশন: প্লাগ ও কয়েল (সিএনজিতে দ্রুত ক্ষয়)", 60), ("Air filter / throttle body dirty", "এয়ার ফিল্টার / থ্রটল বডি ময়লা", 40)], WEEK, 1500, 9000, ("CNG cars need plugs more often — replace and gap correctly.", "সিএনজি গাড়িতে প্লাগ বেশি ঘন ঘন বদলাতে হয়।"), "spark_plugs", CAR))),
    N("cel_q1", ("Is the light flashing?", "লাইট কি জ্বলছে-নিভছে (ফ্ল্যাশ)?"),
      O(YES, diag=D([("Severe misfire (risk of catalytic converter damage)", "মারাত্মক মিসফায়ার (ক্যাটালিটিক কনভার্টার নষ্ট হতে পারে)", 90)], STOP, 1500, 15000, ("Reduce speed and stop soon. Scan codes with an OBD dongle.", "গতি কমিয়ে শীঘ্রই থামুন। OBD ডঙ্গলে কোড স্ক্যান করুন।"), "spark_plugs", CAR)),
      O(NO, diag=D([("Loose fuel cap", "ফুয়েল ক্যাপ ঢিলা", 25), ("O2 or MAF sensor", "O2 বা MAF সেন্সর", 45), ("Ignition / EVAP fault", "ইগনিশন / EVAP সমস্যা", 30)], WEEK, 0, 8000, ("Tighten fuel cap; connect the OBD dongle to read the exact code.", "ফুয়েল ক্যাপ টাইট দিন; সঠিক কোড পড়তে OBD ডঙ্গল লাগান।"), None, CAR))),
]}

trees["easy_bike"] = {"id": "easy_bike", "entries": [
    E("range", ("Range dropped (runs fewer km per charge)", "রেঞ্জ কমে গেছে (এক চার্জে কম চলে)"), "rg_q1"),
    E("no_power", ("No power / cuts out", "পাওয়ার নেই / হঠাৎ বন্ধ হয়"), "np_q1"),
    E("charging", ("Charging problem", "চার্জ হতে সমস্যা"), "ch_q1"),
    E("noise", ("Motor noise / jerk", "মোটরে শব্দ / ঝাঁকুনি"), "mn_q1"),
    E("brake", ("Brakes weak", "ব্রেক দুর্বল"), "eb_br"),
], "nodes": [
    N("rg_q1", ("Has the range dropped to about half or less?", "রেঞ্জ কি প্রায় অর্ধেক বা তার কম হয়ে গেছে?"),
      O(YES, "rg_q2"),
      O(("Only slightly (10–30%)", "সামান্য (১০–৩০%)"), diag=D([("Low battery water (lead-acid)", "ব্যাটারিতে পানি কম (লেড-অ্যাসিড)", 40), ("Low tyre pressure / overloading", "টায়ারে হাওয়া কম / অতিরিক্ত যাত্রী", 35), ("Normal ageing", "স্বাভাবিক বয়সজনিত", 25)], SAFE, 0, 500, ("Top up distilled water, fill tyres, avoid overloading.", "ডিস্টিলড পানি দিন, টায়ারে হাওয়া দিন, অতিরিক্ত লোড এড়ান।"), "battery_water", BATT))),
    N("rg_q2", ("Is one battery hotter or bulging compared with others after a ride?", "চালানোর পর কি একটি ব্যাটারি অন্যগুলোর চেয়ে বেশি গরম বা ফোলা?"),
      O(YES, diag=D([("One dead/weak battery dragging the whole set", "একটি দুর্বল ব্যাটারি পুরো সেটকে নামিয়ে দিচ্ছে", 80), ("Shorted cell", "সেল শর্ট", 20)], WEEK, 8000, 18000, ("Test each battery's voltage under load; replace only the weak one to save money.", "লোডে প্রতিটি ব্যাটারির ভোল্টেজ টেস্ট করুন; শুধু দুর্বলটি বদলে টাকা বাঁচান।"), "traction_battery", BATT)),
      O(NO, "rg_q3")),
    N("rg_q3", ("Does the charger's indicator turn green (full) much faster than before?", "চার্জারের বাতি কি আগের চেয়ে অনেক তাড়াতাড়ি সবুজ (ফুল) হয়?"),
      O(YES, diag=D([("Battery set capacity worn out (sulphation)", "ব্যাটারি সেটের ক্ষমতা শেষ (সালফেশন)", 75), ("Charger cutting off early", "চার্জার আগেই বন্ধ হয়ে যাচ্ছে", 25)], WEEK, 40000, 75000, ("Set is near end of life. Get a capacity test before buying a new set.", "সেট প্রায় শেষ। নতুন কেনার আগে ক্যাপাসিটি টেস্ট করান।"), "traction_battery", BATT)),
      O(NO, diag=D([("Faulty charger not fully charging", "চার্জার ফুল চার্জ দিচ্ছে না", 50), ("Loose/corroded terminals", "টার্মিনাল ঢিলা/মরিচা", 30), ("Dragging brakes / bearings", "ব্রেক / বিয়ারিং লেগে আছে", 20)], WEEK, 200, 4000, ("Check charger output voltage and clean all terminals.", "চার্জারের আউটপুট ভোল্টেজ দেখুন ও সব টার্মিনাল পরিষ্কার করুন।"), "charger_check", BATT))),
    N("np_q1", ("Does the display/horn still work when it cuts out?", "বন্ধ হলে কি ডিসপ্লে/হর্ন চলে?"),
      O(YES, diag=D([("Controller fault or overheating", "কন্ট্রোলার সমস্যা বা অতিরিক্ত গরম", 50), ("Throttle wiring loose", "থ্রটলের তার ঢিলা", 30), ("Low-voltage cutoff on hills (weak battery)", "চড়াইয়ে ভোল্টেজ কমে কাটঅফ (দুর্বল ব্যাটারি)", 20)], WEEK, 500, 6000, ("Check throttle connector; let controller cool.", "থ্রটল কানেক্টর দেখুন; কন্ট্রোলার ঠান্ডা হতে দিন।"), "motor_controller", BATT)),
      O(NO, diag=D([("Main fuse / breaker tripped", "মেইন ফিউজ / ব্রেকার পড়ে গেছে", 45), ("Loose main terminal", "মেইন টার্মিনাল ঢিলা", 40), ("Key switch fault", "চাবি সুইচ সমস্যা", 15)], STOP, 0, 1500, ("Loose high-current terminals can spark and burn wiring. Stop and tighten/repair.", "ঢিলা টার্মিনালে স্পার্ক হয়ে তার পুড়তে পারে। থামুন ও ঠিক করুন।"), "terminals", BATT))),
    N("ch_q1", ("What happens when you plug in the charger?", "চার্জার লাগালে কী হয়?"),
      O(("Nothing / no light", "কিছুই না / বাতি জ্বলে না"), diag=D([("Charger fuse or charger dead", "চার্জারের ফিউজ বা চার্জার নষ্ট", 60), ("Charging socket loose", "চার্জিং সকেট ঢিলা", 40)], WEEK, 200, 5000, ("Test with another charger; check socket.", "অন্য চার্জার দিয়ে টেস্ট করুন; সকেট দেখুন।"), "charger_check", BATT)),
      O(("Batteries get very hot / smell", "ব্যাটারি খুব গরম / গন্ধ"), diag=D([("Overcharging (charger fault)", "ওভারচার্জিং (চার্জার সমস্যা)", 60), ("Low water exposing plates", "পানি কমে প্লেট বের হয়ে গেছে", 40)], STOP, 200, 5000, ("Unplug immediately. Charge in open air. Fire and acid risk.", "এখনই চার্জার খুলুন। খোলা জায়গায় চার্জ দিন। আগুন ও অ্যাসিডের ঝুঁকি।"), "battery_water", BATT))),
    N("mn_q1", ("Is there grinding from the rear axle?", "পেছনের এক্সেল থেকে কি ঘষার শব্দ আসে?"),
      O(YES, diag=D([("Differential/gear oil low", "ডিফারেনশিয়াল অয়েল কম", 50), ("Worn bearings", "বিয়ারিং ক্ষয়", 50)], WEEK, 300, 3000, ("Check differential oil and bearings.", "ডিফারেনশিয়াল অয়েল ও বিয়ারিং দেখুন।"), "differential_oil", BATT)),
      O(NO, diag=D([("Motor hall sensor / controller", "মোটর হল সেন্সর / কন্ট্রোলার", 60), ("Loose motor mounting", "মোটর মাউন্টিং ঢিলা", 40)], WEEK, 500, 5000, ("Have motor & controller checked.", "মোটর ও কন্ট্রোলার চেক করান।"), "motor_controller", BATT))),
    N("eb_br", ("Does the brake lever pull all the way?", "ব্রেক লিভার কি পুরোটা চলে যায়?"),
      O(YES, diag=D([("Brake shoes worn / cable stretched", "ব্রেক শু ক্ষয় / তার ঢিলা", 80), ("Oil on brake drum", "ড্রামে তেল", 20)], STOP, 300, 900, ("With passengers, weak brakes are dangerous. Fix before next trip.", "যাত্রী নিয়ে দুর্বল ব্রেক বিপজ্জনক। পরের ট্রিপের আগে ঠিক করুন।"), "brakes", BATT)),
      O(NO, diag=D([("Needs adjustment", "অ্যাডজাস্ট দরকার", 70), ("Glazed shoes", "শু মসৃণ হয়ে গেছে", 30)], WEEK, 0, 600, ("Adjust brake and clean drum.", "ব্রেক অ্যাডজাস্ট করুন ও ড্রাম পরিষ্কার করুন।"), "brakes", BATT))),
]}

trees["cng_auto"] = {"id": "cng_auto", "entries": [
    E("gas_smell", ("Smell of gas", "গ্যাসের গন্ধ"), "gs_q1"),
    E("no_start", ("Hard to start", "স্টার্ট নিতে সমস্যা"), "ns_q1"),
    E("low_power", ("Low power / poor mileage on gas", "পাওয়ার কম / গ্যাসে মাইলেজ কম"), "lp_q1"),
    E("brake", ("Brakes weak", "ব্রেক দুর্বল"), "cb_q1"),
], "nodes": [
    N("gs_q1", ("Is the smell strong near the cylinder or engine?", "সিলিন্ডার বা ইঞ্জিনের কাছে কি গন্ধ তীব্র?"),
      O(YES, diag=D([("Gas leak at fittings, pipe or regulator", "ফিটিং, পাইপ বা রেগুলেটরে গ্যাস লিক", 85), ("Cylinder valve leak", "সিলিন্ডার ভালভ লিক", 15)], STOP, 300, 4000, ("Stop, engine off, close cylinder valve, no smoking or sparks. Get a leak test.", "থামুন, ইঞ্জিন বন্ধ, সিলিন্ডার ভালভ বন্ধ, ধূমপান/আগুন নয়। লিক টেস্ট করান।"), "cng_kit_tune", CNG)),
      O(("Faint, only after refilling", "হালকা, শুধু গ্যাস নেওয়ার পর"), diag=D([("Filling valve O-ring worn", "ফিলিং ভালভের ও-রিং ক্ষয়", 70), ("Normal residue at nozzle", "নজলে স্বাভাবিক অবশিষ্ট গ্যাস", 30)], WEEK, 100, 800, ("Get filling valve checked soon.", "শীঘ্রই ফিলিং ভালভ চেক করান।"), "cng_kit_tune", CNG))),
    N("ns_q1", ("Is it worse in the morning?", "সকালে কি বেশি সমস্যা?"),
      O(YES, diag=D([("Spark plug worn (CNG needs frequent replacement)", "স্পার্ক প্লাগ ক্ষয় (সিএনজিতে ঘন ঘন বদলাতে হয়)", 60), ("Weak battery", "দুর্বল ব্যাটারি", 40)], WEEK, 200, 4500, ("Replace plug; check battery.", "প্লাগ বদলান; ব্যাটারি দেখুন।"), "spark_plug", BIKE)),
      O(NO, diag=D([("Regulator/mixer needs tuning", "রেগুলেটর/মিক্সার টিউনিং দরকার", 55), ("Air filter clogged", "এয়ার ফিল্টার বন্ধ", 45)], WEEK, 300, 1500, ("Service kit and air filter.", "কিট ও এয়ার ফিল্টার সার্ভিস করান।"), "cng_kit_tune", CNG))),
    N("lp_q1", ("When was the CNG kit last tuned?", "সিএনজি কিট শেষ কবে টিউন করা হয়েছে?"),
      O(("More than 3 months ago / don't know", "৩ মাসের বেশি / জানি না"), diag=D([("Kit tuning drifted", "কিট টিউনিং নড়ে গেছে", 50), ("Clogged CNG filter", "সিএনজি ফিল্টার বন্ধ", 30), ("Worn plug", "প্লাগ ক্ষয়", 20)], SAFE, 300, 1500, ("Get kit tuned and filter replaced.", "কিট টিউন ও ফিল্টার বদলান।"), "cng_kit_tune", CNG)),
      O(("Recently", "সম্প্রতি"), diag=D([("Clutch slipping", "ক্লাচ স্লিপ", 40), ("Dragging brakes / low tyre pressure", "ব্রেক লেগে থাকা / টায়ারে হাওয়া কম", 35), ("Engine compression low", "ইঞ্জিন কম্প্রেশন কম", 25)], WEEK, 0, 6000, ("Check clutch free play and tyre pressure.", "ক্লাচ ফ্রি প্লে ও টায়ারের হাওয়া দেখুন।"), "clutch", BIKE))),
    N("cb_q1", ("Does the pedal/lever go too far?", "প্যাডেল/লিভার কি অনেক দূর যায়?"),
      O(YES, diag=D([("Brake shoes worn", "ব্রেক শু ক্ষয়", 70), ("Brake fluid/cable issue", "ব্রেক ফ্লুইড/তার সমস্যা", 30)], STOP, 400, 1500, ("Fix before carrying passengers.", "যাত্রী তোলার আগে ঠিক করুন।"), "brakes", BIKE)),
      O(NO, diag=D([("Needs adjustment", "অ্যাডজাস্ট দরকার", 100)], WEEK, 0, 300, ("Adjust brake.", "ব্রেক অ্যাডজাস্ট করুন।"), "brakes", BIKE))),
]}

trees["heavy"] = {"id": "heavy", "entries": [
    E("black_smoke", ("Black smoke", "কালো ধোঁয়া"), "hb_q1"),
    E("air_brake", ("Air brake pressure low", "এয়ার ব্রেক প্রেসার কম"), "ha_q1"),
    E("overheat", ("Overheating", "অতিরিক্ত গরম"), "ho_q1"),
    E("tyre", ("Uneven tyre wear", "টায়ার অসমান ক্ষয়"), "ht_q1"),
], "nodes": [
    N("hb_q1", ("Only when loaded / climbing?", "শুধু লোড নিয়ে / চড়াইয়ে?"),
      O(YES, diag=D([("Clogged air filter", "এয়ার ফিল্টার বন্ধ", 45), ("Overloading", "অতিরিক্ত লোড", 30), ("Injector/pump timing", "ইনজেক্টর/পাম্প টাইমিং", 25)], WEEK, 1500, 20000, ("Replace air filter; respect rated load.", "এয়ার ফিল্টার বদলান; নির্ধারিত লোড মানুন।"), "air_filter", CAR)),
      O(NO, diag=D([("Injector nozzles worn", "ইনজেক্টর নজল ক্ষয়", 55), ("Turbo issue", "টার্বো সমস্যা", 25), ("Water in diesel", "ডিজেলে পানি", 20)], WEEK, 3000, 40000, ("Drain water separator; get injectors tested.", "ওয়াটার সেপারেটর ড্রেন করুন; ইনজেক্টর টেস্ট করান।"), "fuel_filter", CAR))),
    N("ha_q1", ("Does pressure drop with the engine off?", "ইঞ্জিন বন্ধ করলে কি প্রেসার নেমে যায়?"),
      O(YES, diag=D([("Air leak in lines, valves or chambers", "লাইন, ভালভ বা চেম্বারে বাতাস লিক", 85), ("Tank drain valve leaking", "ট্যাংক ড্রেন ভালভ লিক", 15)], STOP, 500, 8000, ("Do not drive until pressure holds. Brake failure risk.", "প্রেসার না থাকা পর্যন্ত চালাবেন না। ব্রেক ফেলের ঝুঁকি।"), "brake_air", CAR)),
      O(NO, diag=D([("Compressor worn / governor fault", "কম্প্রেসর ক্ষয় / গভর্নর সমস্যা", 70), ("Water/oil in tanks", "ট্যাংকে পানি/তেল", 30)], STOP, 2000, 25000, ("Drain tanks daily; service compressor.", "প্রতিদিন ট্যাংক ড্রেন করুন; কম্প্রেসর সার্ভিস করান।"), "brake_air", CAR))),
    N("ho_q1", ("Is the coolant level low?", "কুল্যান্ট কি কম?"),
      O(YES, diag=D([("Hose/radiator leak", "হোস/রেডিয়েটর লিক", 70), ("Head gasket", "হেড গ্যাসকেট", 30)], STOP, 1000, 40000, ("Stop and cool down; fix leak.", "থামুন ও ঠান্ডা করুন; লিক ঠিক করুন।"), "coolant", CAR)),
      O(NO, diag=D([("Fan belt loose/broken", "ফ্যান বেল্ট ঢিলা/ছেঁড়া", 45), ("Radiator clogged", "রেডিয়েটর বন্ধ", 35), ("Thermostat", "থার্মোস্ট্যাট", 20)], STOP, 500, 12000, ("Check the fan belt first.", "আগে ফ্যান বেল্ট দেখুন।"), "coolant", CAR))),
    N("ht_q1", ("Where is the wear?", "কোথায় ক্ষয়?"),
      O(("Inner or outer edge", "ভেতরের বা বাইরের কিনারায়"), diag=D([("Wheel alignment / king pin wear", "অ্যালাইনমেন্ট / কিং পিন ক্ষয়", 80), ("Bent axle", "এক্সেল বাঁকা", 20)], WEEK, 1500, 15000, ("Get alignment and king pins checked.", "অ্যালাইনমেন্ট ও কিং পিন চেক করান।"), "tyres", CAR)),
      O(("Centre", "মাঝখানে"), diag=D([("Over-inflation", "অতিরিক্ত হাওয়া", 100)], SAFE, 0, 0, ("Inflate to the recommended pressure for the load.", "লোড অনুযায়ী নির্দেশিত হাওয়া দিন।"), "tyres", CAR))),
]}

out = os.path.join(os.path.dirname(__file__), "..", "core", "src", "main", "resources", "symptoms")
os.makedirs(out, exist_ok=True)
for k, t in trees.items():
    with open(os.path.join(out, k + ".json"), "w", encoding="utf-8") as f:
        json.dump(t, f, ensure_ascii=False, indent=1)
print("wrote", len(trees), "trees,", sum(len(t["entries"]) for t in trees.values()), "symptoms")
