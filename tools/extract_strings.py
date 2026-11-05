#!/usr/bin/env python3
"""Lists every English phrase that needs translating, as catalog keys.

Sources: t("en", "bn") / pick("en", "bn") calls in the app, L10n("en", "bn") in core, every
{"en","bn"} object in the bundled JSON (templates, symptom trees, fault codes), and literal
Translations.tr/format calls. Kotlin string templates become {0}, {1}… placeholders.
Usage: python3 tools/extract_strings.py > /tmp/keys.json && mv /tmp/keys.json tools/i18n_keys.json
"""
import glob, json, re, sys

def templ(s):
    out, i, k = [], 0, 0
    while i < len(s):
        if s[i] == '\\' and i + 1 < len(s):
            out.append({'n': '\n', '"': '"', '$': '$', '\\': '\\'}.get(s[i + 1], s[i:i + 2])); i += 2; continue
        if s[i] == '$' and i + 1 < len(s) and s[i + 1] == '{':
            d, j = 1, i + 2
            while j < len(s) and d:
                d += {'{': 1, '}': -1}.get(s[j], 0); j += 1
            out.append('{%d}' % k); k += 1; i = j; continue
        if s[i] == '$' and i + 1 < len(s) and (s[i + 1].isalpha() or s[i + 1] == '_'):
            j = i + 1
            while j < len(s) and (s[j].isalnum() or s[j] == '_'): j += 1
            out.append('{%d}' % k); k += 1; i = j; continue
        out.append(s[i]); i += 1
    return ''.join(out)

STR = r'"((?:\\.|[^"\\])*)"'
keys = set()
for f in glob.glob('app/src/main/kotlin/**/*.kt', recursive=True):
    s = open(f, encoding='utf-8').read()
    for m in re.finditer(r'(?<![A-Za-z0-9_])(?:t|t2|pick)\(\s*(?:lang\s*,\s*|vm\s*,\s*)?' + STR + r'\s*,', s): keys.add(templ(m.group(1)))
for f in glob.glob('core/src/main/kotlin/**/*.kt', recursive=True):
    s = open(f, encoding='utf-8').read()
    for m in re.finditer(r'L10n\(\s*' + STR, s): keys.add(templ(m.group(1)))
    for m in re.finditer(r'Translations\.(?:tr|format)\(\s*lang\s*,\s*' + STR, s): keys.add(m.group(1))
def walk(o):
    if isinstance(o, dict):
        if set(o) == {'en', 'bn'}: keys.add(o['en']); return
        for v in o.values(): walk(v)
    elif isinstance(o, list):
        for v in o: walk(v)
for f in glob.glob('core/src/main/resources/templates/*.json') + glob.glob('core/src/main/resources/symptoms/*.json') + glob.glob('core/src/main/resources/obd/*.json'):
    walk(json.load(open(f, encoding='utf-8')))
# Phrases whose English formats numbers inside ${"..."} (nested quotes the regex above can't follow).
keys.update([
    "every {0} km", "last at {0} km", "Oil every {0} km", "Last 30 days: {0} vs usual {1}", "Cost per km: ৳{0}",
    "{0} km left", "{0} km over", "Current: {0} km", "Battery voltage low ({0} V)",
])
keys.discard('Battery voltage low ({0}')
keys.discard('')
# Append-only: translation sources refer to phrases by index, so existing phrases keep their
# position and new ones are added at the end. Phrases no longer used stay (harmless).
import os
known_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'i18n_keys.json')
known = json.load(open(known_path, encoding='utf-8')) if os.path.exists(known_path) else []
merged = known + sorted(k for k in keys if k not in set(known))
json.dump(merged, sys.stdout, ensure_ascii=False, indent=0)
