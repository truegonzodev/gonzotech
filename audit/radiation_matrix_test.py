#!/usr/bin/env python3
"""0.3.337 / RAD-VALID1: матрица взаимодействий радиации сверена с production-кодом.

Проверяет не «красивый текст», а что разделы A–H и F1–F6 на месте, что ключевые
формулы действительно существуют в исходниках и что границы доказательств не
стёрты. Не заменяет нативный Minecraft/TPS/GameTest-прогон и не меняет баланс.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs'
RAD = ROOT / 'src/main/java/com/gonzotech/radiation'

matrix = (ROOT / 'RADIATION_INTERACTIONS.md').read_text(encoding='utf-8')


def src(path):
    return (RAD / path).read_text(encoding='utf-8')


def pin(condition, label):
    assert condition, 'RAD-VALID1: ' + label


# ── 1. Матрица цела: все разделы и якорь ────────────────────────────────
pin('<a id="rad-matrix337"></a>' in matrix, 'якорь матрицы')
for section in ('## 1. Обозначения и константы', '## 2. Схема потоков',
                '## 3. A. Блок', '## 4. B. Геометрия', '## 5. C. Игрок',
                '## 6. D. Предметный кластер', '## 7. E. Мировые владельцы',
                '## 8. F. Свинцовый ящик', '## 9. G. Переносы',
                '## 10. H. Сеть', '## 11. Итоги валидации',
                '## 12. Пробелы', '## 13. Что в этот блок не входит'):
    pin(section in matrix, 'раздел ' + section)
for row in ('| A1 |', '| A7 |', '| B10 |', '| C11 |', '| D11 |', '| E9 |',
            '| F1 |', '| F6 |', '| G8 |', '| H7 |'):
    pin(row in matrix, 'строка ' + row)

# ── 2. Формулы матрицы подтверждены реальным кодом ─────────────────────
contour = src('RadiationContour.java')
containment = src('Containment.java')
system = src('RadiationSystem.java')
chunk = src('ChunkRadiationData.java')
cluster = src('engine/RadiationCluster.java')
item = src('ItemRadioactivity.java')
carrier = src('CarrierItem.java')
lead = (ROOT / 'src/main/java/com/gonzotech/machines/block/ThirdLeadChestBlock.java').read_text()

pin('INSIDE_DOSE_WEIGHT = 0.05' in contour, 'прямые 5 %')
pin('VISIT_CAP = 2048' in contour and 'RANGE = 32' in contour
    and 'MAX_LAYERS = 8' in contour, 'ограничения обхода контура')
pin('{1, 1, 1}' in contour and '{-1, -1, -1}' in contour, '14-связность')
pin('enclosed ? emission * INSIDE_DOSE_WEIGHT : 0' in contour, 'insideDose блоков')
pin('CACHE_TTL_TICKS = 400' in containment and 'CACHE_MAX = 4096' in containment,
    'TTL/предел кэша контура')
pin(containment.count('CACHE.remove(level)') == 3, 'полный сброс кэша уровня на 3 событиях')
pin('contents * RadiationContour.INSIDE_DOSE_WEIGHT' in containment,
    'доза содержимого полости')
pin('RadiationCluster.inspect(roots).total()' in containment,
    'room-dose читает полную эмиссию, не chunkTarget')
pin('entity != observer' in containment, 'собственный вклад наблюдателя исключён')
pin('CHUNK_DOSE_WEIGHT = 0.1' in system and 'NZT_PER_PERMILLE = 1.35e8' in system,
    'вес фона чанка и цена permille')
pin('INVENTORY_TO_CHUNK_RATE = 0.01' in system
    and 'INVENTORY_TRANSFER_EFFICIENCY = 0.95' in system
    and 'CHEST_TO_CHUNK_RATE = 0.01' in system, 'ставки переноса в чанк')
pin('return sourceEmission > contamination ? (sourceEmission - contamination) * rate : 0.0;' in system,
    'transferTowardChunk')
pin('ThirdLeadChestBlock.CONTENT_CHUNK_FACTOR * factor' in system,
    'свинцовая цель до вычитания contamination')
pin('DOSE_CLEAR_FLOOR = 100.0' in system and 'RECOVERY_RATE = 0.002' in system,
    'спад только без облучения')
pin('SHED_TO_CHUNK = 0.2' in system and 'SHED_CEILING = 100.0 * RadUnits.MILLI' in system,
    'потолок сброса дозы в чанк')
pin('MAX_PROBES_PER_CHUNK = 16' in chunk and 'PLACED_CONVERGE = 0.05' in chunk
    and 'DECAY = 0.99' in chunk, 'бюджет проб, логистика, распад')
pin('CHUNK_THRESHOLD = 30 * RadUnits.MILLI' in cluster
    and 'LEAD = new Policy(0.2)' in cluster and 'NORMAL = new Policy(1)' in cluster,
    'порог 30 mZt и свинец 0.2')
pin('incomingChunk>CHUNK_THRESHOLD' in cluster,
    'строгое сравнение порога после коэффициента владельца')
pin('public double chunkTarget() { return preset + strongestInduced; }' in cluster,
    'цель чанка без count')
pin('GROWTH = 1.25' in item and 'UPTAKE_COST = 0.0013' in item
    and 'INDUCED_REEMIT = 0.75' in item, 'рост/насыщение/переизлучение')
pin('RADIOACTIVITY_FACTOR = 0.60' in carrier, 'щипцы/ковш ×0.6')
pin('CONTENT_CHUNK_FACTOR = 0.2' in lead, 'выходной коэффициент свинцового ящика')

# ── 3. Границы доказательств не стёрты ─────────────────────────────────
for phrase in ('нативной Gradle/NeoForge/JAR-сборки', 'TPS/аллокационного профиля',
               'SKIP ≠ PASS', 'selfTest3FAIL', 'third_grinder', 'D-AUD1',
               'задавать неподтверждённый «нормальный TPS»'):
    pin(phrase in matrix, 'граница: ' + phrase)
pin('14 658 971' in matrix, 'честное байтовое расхождение ECJ repack')
pin('23' in matrix and '@GameTest' in matrix, 'GameTest не запускались')

# ── 4. Синхронные записи и живые якоря ─────────────────────────────────
for name in ('CURRENT-STATE.md', 'HISTORY.md', 'AUDIT.md', 'RAD_WORLD.md',
             'RAD_ITEMS.md', 'CAPABILITIES.md'):
    text = (DOCS / name).read_text(encoding='utf-8')
    pin('<a id="rad-valid1"></a>' in text, 'якорь RAD-VALID1 в ' + name)
deferred = (DOCS / 'DEFFERED.md').read_text(encoding='utf-8')
pin('<a id="d-perf12"></a>' in deferred, 'якорь D-PERF12')
pin('RAD-VALID1' in deferred, 'D-PERF12 ссылается на валидацию')
current = (DOCS / 'CURRENT-STATE.md').read_text(encoding='utf-8')
for task in ('RAD-VALID1', 'D-PIPE2', 'PIPE-LOAD1', 'D-CONTENT1'):
    section = current.split('### ' + task + ' — ', 1)[1].split('\n### ', 1)[0]
    pin('**Статус: ждёт приёмки; заморожено' in section, task + ' ждёт приёмки')
for task in ('D-PERF12', 'D-QA1'):
    section = current.split('### ' + task + ' — ', 1)[1].split('\n### ', 1)[0]
    pin('**Статус: в работе.' in section, task + ' в работе')
    pin('### ' + task + ' — ' not in deferred, task + ' не дублируется в DEFFERED')

print('RAD-VALID1 matrix: A–H sections, code-confirmed formulas, evidence bounds and docs anchors PASS')
