"""Benchmark dashboard: parses CSV output from `gradlew runBenchmarkSuite --csv --runs N`
and generates HTML charts for thesis graphs.

Usage:
    python scripts/dashboard.py < benchmark_output.csv > report.html
    python scripts/dashboard.py --input benchmark_output.csv --output report.html
"""

import sys
import re
import math
from collections import defaultdict

def parse_csv(lines):
    data = defaultdict(list)
    for line in lines:
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        parts = line.split(',')
        if not parts:
            continue
        if parts[0] == 'empirical' and len(parts) >= 9:
            data['empirical'].append({
                'operator': parts[1], 'progress': int(parts[2]),
                'preserv': int(parts[3]), 'determ': int(parts[4]),
                'bigstep': int(parts[5]), 'virred': int(parts[6]),
                'vsorig': int(parts[7]), 'anypbt': int(parts[8)]
            })
        elif parts[0] == 'steps' and len(parts) >= 3:
            data['steps'].append({'run': parts[0], 'steps': parts[1], 'count': int(parts[2])})
        elif len(parts) >= 6 and parts[0].isdigit():
            data['scalability'].append({
                'size': int(parts[0]), 'parseNs': int(parts[1]),
                'tcNs': int(parts[2]), 'evalNs': int(parts[3]),
                'reduceNs': int(parts[4]), 'steps': int(parts[5]),
                'name': parts[6], 'run': parts[7] if len(parts) > 7 else '0'
            })
        elif len(parts) == 5 and parts[0].isdigit():
            data['breakdown'].append({
                'run': parts[0] if len(parts) > 4 else '0',
                'size': int(parts[0] if len(parts) == 4 else parts[1]),
                'parseNs': int(parts[1] if len(parts) == 4 else parts[2]),
                'tcNs': int(parts[2] if len(parts) == 4 else parts[3]),
                'evalNs': int(parts[3] if len(parts) == 4 else parts[4]),
                'reduceNs': int(parts[4] if len(parts) == 4 else parts[5])
            })
    return data

def fmt_ns(ns):
    if ns < 1000: return f"{ns}ns"
    us = ns / 1000
    if us < 1000: return f"{us:.1f}us"
    ms = us / 1000
    return f"{ms:.2f}ms"

def gen_html(data):
    html = """<!DOCTYPE html><html><head><meta charset="UTF-8">
<title>Benchmark Dashboard</title>
<style>
body { font-family: 'Segoe UI', Arial, sans-serif; margin: 20px; background: #f5f5f5; }
h1 { color: #333; }
h2 { color: #555; margin-top: 30px; }
table { border-collapse: collapse; margin: 10px 0; background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,.1); }
th, td { padding: 6px 12px; border: 1px solid #ddd; text-align: right; }
th { background: #4a90d9; color: #fff; }
tr:nth-child(even) { background: #f9f9f9; }
.bar-container { width: 100%; max-width: 600px; margin: 4px 0; }
.bar { height: 20px; background: #4a90d9; border-radius: 3px; text-align: right; padding-right: 6px; color: #fff; font-size: 11px; line-height: 20px; }
.summary { background: #e8f4e8; padding: 10px; border-radius: 5px; margin: 10px 0; }
</style></head><body>
<h1>Benchmark Dashboard</h1>
<p>Generated from CSV benchmark data</p>
"""

    if data.get('empirical'):
        html += "<h2>Empirical PBT-vs-Mutation Matrix</h2><table><tr><th>Operator</th><th>Progress</th><th>Preserv</th><th>Determ</th><th>BigStep</th><th>ValIrred</th><th>VsOrig</th><th>AnyPBT</th></tr>"
        for row in data['empirical']:
            html += f"<tr><td style='text-align:left'>{row['operator']}</td><td>{row['progress']}</td><td>{row['preserv']}</td><td>{row['determ']}</td><td>{row['bigstep']}</td><td>{row['virred']}</td><td>{row['vsorig']}</td><td><b>{row['anypbt']}</b></td></tr>"
        html += "</table>"

    if data.get('steps'):
        html += "<h2>Step Distribution</h2><table><tr><th>Steps</th><th>Count</th><th>Bar</th></tr>"
        total = sum(r['count'] for r in data['steps'])
        for row in sorted(data['steps'], key=lambda r: int(r['steps']) if r['steps'] != '20+' else 20):
            pct = row['count'] / total * 100 if total else 0
            html += f"<tr><td>{row['steps']}</td><td>{row['count']}</td><td><div class='bar-container'><div class='bar' style='width:{pct*4:.0f}px'>{row['count']}</div></div></td></tr>"
        html += f"<tr><td><b>Total</b></td><td><b>{total}</b></td><td></td></tr></table>"

    if data.get('scalability'):
        html += "<h2>Scalability</h2><table><tr><th>Program</th><th>Size</th><th>Parse</th><th>TypeCheck</th><th>Eval</th><th>Reduce</th><th>Steps</th></tr>"
        for row in data['scalability']:
            html += f"<tr><td style='text-align:left'>{row['name']}</td><td>{row['size']}</td><td>{fmt_ns(row['parseNs'])}</td><td>{fmt_ns(row['tcNs'])}</td><td>{fmt_ns(row['evalNs'])}</td><td>{fmt_ns(row['reduceNs'])}</td><td>{row['steps']}</td></tr>"
        html += "</table>"

    if data.get('breakdown'):
        html += "<h2>Runtime Breakdown</h2><table><tr><th>Run</th><th>#</th><th>Parse</th><th>TypeCheck</th><th>Eval</th><th>Reduce</th><th>Size</th></tr>"
        for i, row in enumerate(data['breakdown'][:50]):
            html += f"<tr><td>{row['run']}</td><td>{i+1}</td><td>{fmt_ns(row['parseNs'])}</td><td>{fmt_ns(row['tcNs'])}</td><td>{fmt_ns(row['evalNs'])}</td><td>{fmt_ns(row['reduceNs'])}</td><td>{row['size']}</td></tr>"
        if len(data['breakdown']) > 50:
            html += f"<tr><td colspan='7' style='text-align:center;color:#999'>... {len(data['breakdown']) - 50} more rows omitted</td></tr>"
        html += "</table>"

        parse_vals = [r['parseNs'] for r in data['breakdown']]
        tc_vals = [r['tcNs'] for r in data['breakdown']]
        eval_vals = [r['evalNs'] for r in data['breakdown']]
        reduce_vals = [r['reduceNs'] for r in data['breakdown']]
        html += "<div class='summary'><h3>Summary Statistics</h3>"
        html += f"<p><b>Parse:</b> avg={fmt_ns(sum(parse_vals)/len(parse_vals))}, min={fmt_ns(min(parse_vals))}, max={fmt_ns(max(parse_vals))}</p>"
        html += f"<p><b>TypeCheck:</b> avg={fmt_ns(sum(tc_vals)/len(tc_vals))}, min={fmt_ns(min(tc_vals))}, max={fmt_ns(max(tc_vals))}</p>"
        html += f"<p><b>Eval:</b> avg={fmt_ns(sum(eval_vals)/len(eval_vals))}, min={fmt_ns(min(eval_vals))}, max={fmt_ns(max(eval_vals))}</p>"
        html += f"<p><b>Reduce:</b> avg={fmt_ns(sum(reduce_vals)/len(reduce_vals))}, min={fmt_ns(min(reduce_vals))}, max={fmt_ns(max(reduce_vals))}</p>"
        html += "</div>"

    html += "</body></html>"
    return html

if __name__ == '__main__':
    lines = sys.stdin.readlines()
    data = parse_csv(lines)
    print(gen_html(data))
