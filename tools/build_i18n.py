#!/usr/bin/env python3
"""Builds core/src/main/resources/i18n/<code>.json from tools/i18n_src/<code>*.txt.

Source format: one line per phrase, "<index>|<translation>", where <index> is the position in
tools/i18n_keys.json (made by extract_strings.py). Several files per language are merged
(e.g. hi.1.txt, hi.2.txt). Checks: indices in range, no duplicates, the same {n} placeholders
as the English. Missing phrases are reported and fall back to English at runtime.
Usage: python3 tools/build_i18n.py [code ...]
"""
import glob, json, os, re, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
keys = json.load(open(os.path.join(ROOT, 'tools/i18n_keys.json'), encoding='utf-8'))
ph = re.compile(r'\{\d+\}')
codes = sys.argv[1:] or sorted({os.path.basename(p).split('.')[0] for p in glob.glob(os.path.join(ROOT, 'tools/i18n_src/*.txt'))})
ok = True
for code in codes:
    out, errors = {}, []
    for path in sorted(glob.glob(os.path.join(ROOT, f'tools/i18n_src/{code}.*txt'))):
        for n, line in enumerate(open(path, encoding='utf-8'), 1):
            line = line.rstrip('\n')
            if not line.strip(): continue
            idx, sep, text = line.partition('|')
            if not sep or not idx.strip().isdigit(): errors.append(f'{path}:{n}: bad line'); continue
            i = int(idx)
            if not 0 <= i < len(keys): errors.append(f'{path}:{n}: index {i} out of range'); continue
            if keys[i] in out: errors.append(f'{path}:{n}: duplicate index {i}'); continue
            if sorted(ph.findall(keys[i])) != sorted(ph.findall(text)):
                errors.append(f'{path}:{n}: placeholders differ for {i} "{keys[i]}" -> "{text}"'); continue
            out[keys[i]] = text.strip()
    missing = [i for i, k in enumerate(keys) if k not in out]
    dest = os.path.join(ROOT, f'core/src/main/resources/i18n/{code}.json')
    json.dump(dict(sorted(out.items())), open(dest, 'w', encoding='utf-8'), ensure_ascii=False, indent=0)
    print(f'{code}: {len(out)}/{len(keys)} translated' + (f', missing {missing[:15]}{"…" if len(missing) > 15 else ""}' if missing else ''))
    for e in errors: print('  ERROR', e); ok = False
sys.exit(0 if ok else 1)
