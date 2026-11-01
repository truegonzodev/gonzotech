#!/usr/bin/env python3
"""Explicit config labels/tooltips in RU/EN; resource regression, not a rendered GUI test."""
from pathlib import Path
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / 'src/main/java/com/gonzotech/core/config'
LANG = ROOT / 'src/main/resources/assets/gonzotech/lang'
KEYS = set()
for name in ('GonzoServerConfig.java', 'GonzoClientConfig.java'):
    KEYS.update(re.findall(r'\.translation\("([^"]+)"\)', (CONFIG / name).read_text()))
FLOOD = 'gonzotech.configuration.floodRequestThreshold'
assert FLOOD in KEYS


def check(values, language):
    for key in sorted(KEYS):
        for suffix in ('', '.tooltip'):
            value = values.get(key + suffix)
            assert isinstance(value, str) and value.strip(), f'{language}: missing {key + suffix}'
            if language == 'ru_ru':
                assert re.search('[А-Яа-яЁё]', value), f'{language}: untranslated {key + suffix}'


for language in ('ru_ru', 'en_us'):
    values = json.loads((LANG / (language + '.json')).read_text())
    check(values, language)
    mutant = dict(values)
    del mutant[FLOOD + '.tooltip']
    try:
        check(mutant, language)
    except AssertionError:
        pass
    else:
        raise AssertionError('missing-tooltip mutant survived: ' + language)

# Exact delivered330 resources reproduce the original missing-tooltip failure.
for language in ('ru_ru', 'en_us'):
    path = f'src/main/resources/assets/gonzotech/lang/{language}.json'
    old = json.loads(subprocess.check_output(['git', 'show', '8b89bd2:' + path], cwd=ROOT, text=True))
    assert FLOOD in old and FLOOD + '.tooltip' not in old
    try:
        check(old, language)
    except AssertionError as error:
        assert FLOOD + '.tooltip' in str(error), error
    else:
        raise AssertionError('exact330 resource negative survived: ' + language)
print(f'config tooltips PASS: {len(KEYS)} explicit keys × RU/EN labels + tooltips; 2 mutants / 2 exact330 negatives; not rendered GUI')
