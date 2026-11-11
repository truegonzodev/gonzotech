#!/usr/bin/env python3
"""Compare aggregate PIPE-BENCH logs. No third-party dependencies, no winner inference."""
import argparse
from pathlib import Path
import re

MARKER = '[GONZOTECH PIPE-BENCH] '

def read(path, variant, session=None, skip_ticks=600):
    rows = []
    for line in Path(path).read_text(encoding='utf-8', errors='replace').splitlines():
        if MARKER not in line:
            continue
        text = line.split(MARKER, 1)[1]
        row = dict(re.findall(r'(\w+)=([^\s{}]+)', text.split('resourceChargeMilli=', 1)[0]))
        match = re.search(r'resourceChargeMilli=\{([^}]*)\}(.*)', text)
        if not match:
            raise ValueError('Incomplete PIPE-BENCH line')
        row['charges'] = {k: int(v) for k,v in re.findall(r'(\w+)=(-?\d+)', match[1])}
        row.update(dict(re.findall(r'(\w+)=(\d+)', match[2])))
        if row.get('schema') != '1':
            raise ValueError('Unsupported PIPE-BENCH schema')
        if session is None or row.get('session') == session:
            rows.append(row)
    if not rows:
        raise ValueError(f'{path}: no samples for selected session')
    if len({r['session'] for r in rows}) != 1:
        raise ValueError(f'{path}: multiple sessions; select --session-a/--session-b')
    expected = 'pipe-II-339-' + ('A' if variant == 'baseline' else 'B')
    if any(r.get('variant') != variant or r.get('snapshot') != expected for r in rows):
        raise ValueError(f'{path}: expected {expected}/{variant}')
    totals = [int(r['totalTicks']) for r in rows]
    if any(b <= a for a,b in zip(totals, totals[1:])):
        raise ValueError(f'{path}: duplicate or out-of-order samples')
    rows = [r for r in rows if int(r['ticks']) == 200 and int(r['totalTicks']) - 200 >= skip_ticks]
    if not rows:
        raise ValueError(f'{path}: no full windows after warmup ({skip_ticks} ticks)')
    return rows

def summarize(rows):
    ticks = sum(int(r['ticks']) for r in rows)
    avg = lambda key: sum(int(r[key]) * int(r['ticks']) for r in rows) / ticks
    total = lambda key: sum(int(r[key]) for r in rows)
    out = {
        'Тики в выборке': ticks,
        'Средний tickSpan, ms': avg('tickSpanMeanUs') / 1000,
        'Максимальный tickSpan, ms': max(int(r['tickSpanMaxUs']) for r in rows) / 1000,
        'Наибольший оконный p95, ms': max(int(r['tickSpanP95Us']) for r in rows) / 1000,
        'GC, ms суммарно': total('gcMs'),
        'Максимум ожидающих поисков': max(int(r['pendingMax']) for r in rows),
        'Максимум ячеек': max(int(r['cells']) for r in rows),
        'Максимум коридоров': max(int(r['corridors']) for r in rows),
        'Максимум занятого heap, MiB': max(int(r['heapUsedBytes']) for r in rows) / 1048576,
    }
    for key in ('SEARCH_NS','ITEM_NS','FILTER_NS','RESOURCE_NS'):
        out[key + ', ms/тик'] = total(key) / ticks / 1e6
    for key in ('REQUESTS','PLAN_HITS','RESTARTS','EXPANDED','STATE_READS','INVALIDATIONS',
                'PRECISE_SKIPS','ROUTE_KEEPS','WELD_BUILDS','WELD_HITS','WELD_STEPS','NODE_ITEMS'):
        out[key + '/тик'] = total(key) / ticks
    channels = sorted({k for r in rows for k in r['charges']})
    for channel in channels:
        out[f'Расход {channel}, milli/тик'] = sum(r['charges'].get(channel,0) for r in rows) / ticks
    return out

def compare(a, b):
    sa, sb = summarize(a), summarize(b)
    lines = ['| Показатель | A — baseline | B — experimental |', '|---|---:|---:|']
    for key in dict.fromkeys([*sa, *sb]):
        lines.append(f'| {key} | {sa.get(key,0):.4f} | {sb.get(key,0):.4f} |')
    lines += ['', 'tickSpan — интервал серверных Pre→Post событий, не точный vanilla MSPT. '
              'Оконный p95 не является p95 всего прогона. SEARCH_NS вложен в часть routing-вызовов: таймеры не складывать.',
              'NODE_ITEMS считает перенос обычных предметных узлов; фильтры в этот счётчик не входят. '
              'Расход ресурсов включает потери. GC/heap встроенного сервера относятся ко всей JVM, включая клиент.']
    if len(a) < 10 or len(b) < 10:
        lines.append('ВНИМАНИЕ: менее 2000 измеренных тиков; выборка короткая.')
    if len(a) != len(b):
        lines.append('ВНИМАНИЕ: длительность выборок различается.')
    for key in sa.keys() | sb.keys():
        if key == 'NODE_ITEMS/тик' or key.startswith('Расход '):
            x,y = sa.get(key,0),sb.get(key,0)
            if abs(x-y) > max(abs(x),abs(y)) * .02:
                lines.append(f'ВНИМАНИЕ: {key} различается более чем на 2%; проверить одинаковую нагрузку/прогрев.')
    lines.append('Выбор победителя требует одинакового мира, нагрузки и проверки поведения в игре.')
    return '\n'.join(lines)

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('a'); p.add_argument('b')
    p.add_argument('--session-a'); p.add_argument('--session-b')
    p.add_argument('--skip-ticks', type=int, default=600)
    p.add_argument('--output', type=Path)
    args = p.parse_args()
    if args.skip_ticks < 0: p.error('--skip-ticks must be nonnegative')
    try:
        report = compare(read(args.a,'baseline',args.session_a,args.skip_ticks),
                         read(args.b,'experimental',args.session_b,args.skip_ticks))
    except (ValueError, KeyError, OSError) as e:
        p.error(str(e))
    if args.output: args.output.write_text(report+'\n',encoding='utf-8')
    print(report)
