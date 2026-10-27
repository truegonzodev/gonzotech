#!/usr/bin/env python3
"""DOC-003: approved psyche contracts and chemical 0.2%/second regression (not MC)."""
from pathlib import Path
import ast
import itertools
import math
import re
from collections import Counter
from fractions import Fraction
import _ver

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/com/gonzotech'
DOC = (ROOT / 'docs/PSYCHE.md').read_text()
_ver.at_least('0.3.312', 'approved chemical decay P04')


def source(path):
    return (JAVA / path).read_text()


def number(text, name):
    match = re.search(r'\b' + name + r'\s*=\s*([\d_.]+)[FfDd]?\s*;', text)
    assert match, name
    return float(match[1].replace('_', ''))


chemical = source('core/psyche/PsycheChemical.java')
rate = number(chemical, 'DECAY_PERCENT_PER_SECOND')
assert rate == 0.2, 'P04: 0.2 percent of CURRENT chemical per second, not 0.3'
assert 'player.tickCount % 20 != 0' in chemical
assert 'state.decayAcc -= decay;' in chemical
assert 'int decay = (int) state.decayAcc;' in chemical
assert 'before + income - decay' in chemical
# Evaluate only the restricted arithmetic expression extracted from production.
# This tests that expression + fractional algorithm, not Java/NeoForge wiring.
expression = re.search(r'state\.decayAcc \+= ([^;]+);', chemical)[1]
tree = ast.parse(expression, mode='eval')
assert all(isinstance(n, (ast.Expression, ast.BinOp, ast.Mult, ast.Div,
                          ast.Name, ast.Load, ast.Constant)) for n in ast.walk(tree))
assert {n.id for n in ast.walk(tree) if isinstance(n, ast.Name)} == {
    'before', 'DECAY_PERCENT_PER_SECOND'}
assert expression == 'before * (DECAY_PERCENT_PER_SECOND / 100.0)'
formula = compile(tree, '<extracted chemical decay>', 'eval')


def step(before, remainder=0.0, income=0):
    accumulated = remainder + eval(formula, {'__builtins__': {}}, {
        'before': before, 'DECAY_PERCENT_PER_SECOND': rate})
    decay = int(accumulated)
    return max(0, min(1000, before + income - decay)), accumulated - decay


assert step(1000) == (998, 0.0)
assert step(500) == (499, 0.0)
assert step(0) == (0, 0.0)
assert step(0, income=1)[0] == 1  # not based on post-income value
assert step(1000, income=100)[0] == 1000  # clamp after income/decay
value, remainder = 1, 0.0
for _ in range(499):
    value, remainder = step(value, remainder)
assert value == 1
value, remainder = step(value, remainder)
assert value == 0, 'fractional decay must not freeze at low chemical'
value, remainder = 1000, 0.0
for _ in range(60):
    value, remainder = step(value, remainder)
assert 886 <= value <= 889  # 0.998**60 ~ 0.8868, not 0.997**60 ~ 0.8350
assert abs(value - 1000 * 0.998 ** 60) <= 1.1
for before in range(1001):
    after, residue = step(before)
    assert 0 <= after <= before and 0 <= residue < 1

# P14: countdown reaches zero on the due tick, not one tick before a return.
tremor = source('core/psyche/client/PsycheTremorClient.java')
clean_tremor = re.sub(r'//[^\n]*', '', tremor)
assert re.search(r'if \(nextJitter > 0\)\s*\{\s*if \(--nextJitter > 0\)\s*\{\s*return;', clean_tremor)
assert 'nextJitter = 1 + player.getRandom().nextInt(2);' in tremor
assert 'nextJitter = 0;' in tremor
# Enumerate every sequence of 8 random intervals. Source guard above ties the
# model to the production predecrement-and-test branch; no Java/MC execution.
for intervals in itertools.product((1, 2), repeat=8):
    jitter_ticks = [0]
    for interval in intervals:
        counter = interval
        tick = jitter_ticks[-1]
        while True:
            tick += 1
            if counter > 0:
                counter -= 1
                if counter > 0:
                    continue
            jitter_ticks.append(tick)
            break
    assert tuple(b - a for a, b in zip(jitter_ticks, jitter_ticks[1:])) == intervals

# Pin independently approved boundary/chance units and the actual call sites.
assert number(chemical, 'ABSORBENT_ADD_PERMILLE') == 10
for name, expected in {'ITCH1_PERMILLE': 380, 'ITCH2_PERMILLE': 520,
                       'ITCH3_PERMILLE': 690, 'DOSE_SHARE': 0.02}.items():
    assert number(chemical, name) == expected
stress = source('core/psyche/PsycheStress.java')
assert 'level.isThundering() || level.isRaining()' in stress
assert 'PsycheStress.ABSORBENT_STRESS_BURST' in source('radiation/RadAbsorbentItem.java')
assert 'PsycheStress' not in source('radiation/RadCleanse.java')
effects = source('core/psyche/PsycheStressEffects.java')
for name, expected in {'TREMOR_MIN_PERCENT': 40, 'TREMOR_CHANCE': .006,
                       'TREMOR_STRONG_MIN_PERCENT': 70, 'TREMOR_STRONG_CHANCE': .001,
                       'HALLUCINATION_MIN_PERCENT': 50, 'HALLUCINATION_CHANCE': .013,
                       'DROP_MIN_PERCENT': 91, 'DROP_CHANCE': .04,
                       'HEART_ATTACK_MIN_PERCENT': 99, 'HEART_ATTACK_CHANCE': .005,
                       'REACTION_MIN_PERCENT': 74, 'BLINDNESS_CHANCE': .004}.items():
    assert number(effects, name) == expected, name
crisis = source('core/psyche/PsycheCrisis.java')
for name, expected in {'CASCADE_MIN_PERCENT': 38, 'CASCADE_CHANCE': .003,
                       'SWAP_MIN_PERCENT': 56, 'SWAP_CHANCE': .018,
                       'MICROSTEP_MIN_PERCENT': 67, 'MICROSTEP_CHANCE': .04,
                       'OVERLAY_MIN_PERCENT': 74, 'FAKE_DEATH_MIN_PERCENT': 87,
                       'FAKE_DEATH_CHANCE': .31}.items():
    assert number(crisis, name) == expected, name
assert 'RESTORE_FAKE_TIME = false' in crisis
uv = source('core/psyche/PsycheUltraviolet.java')
for name, expected in {'RADIATION_SOURCE_PERMILLE': 700, 'GAIN_PERCENT_PER_TICK': .025,
                       'DECAY_PERCENT_PER_TICK': 1, 'TRIGGER_STEP_PERCENT': 5,
                       'WEAK_SLOW_PERCENT': 2, 'NECROSIS_PERCENT': .5}.items():
    assert number(uv, name) == expected, name
assert number(source('radiation/Necrosis.java'), 'BUBBLES_BASE_PER_SECOND') == 220
# Exhaustively enumerate the two extra independent 1-in-3 successes (P09).
sickness = source('radiation/RadSickness.java')
assert 'int successes = 1;' in sickness and 'i < 3' in sickness
assert 'RNG.nextInt(3) == 0' in sickness and 'Math.min(successes, pool.size())' in sickness
counts = Counter(1 + (a == 0) + (b == 0) for a, b in itertools.product(range(3), repeat=2))
assert {k: Fraction(v, 9) for k, v in counts.items()} == {
    1: Fraction(4, 9), 2: Fraction(4, 9), 3: Fraction(1, 9)}
assert math.isclose((1 - .2 / 30) ** 30, .81818, abs_tol=.0001)
# All debug IDs, materials and scale units are documented, not replaced by pointers.
ids = re.findall(r'"([a-z_]+)"', effects.split('TRIGGER_IDS = List.of(', 1)[1].split(');', 1)[0])
assert len(ids) == 32
for identifier in ids:
    assert f'`{identifier}`' in DOC, identifier
for heading in ['## Стресс', '## Зависимость', '## Экзистенциальный кризис',
                '## Химическое заражение', '## Облучение', '## УФ-излучение',
                '### Некроз', '### AlcoholDose']:
    assert heading in DOC, heading
for token in ['0,2% текущего', '0,54%', '0,67%', '0,88%', '0,98%', '4/9', '1/9',
              '220×', '0,025', '1,35×10⁸', '2% delivered', '10‰', '0,4%/с']:
    assert token in DOC, token
print('PASS: P04 decay + P14 countdown regressions and psyche source/table pins; not Java/MC playtest')
