/* Tiny dependency-free SVG/HTML chart helpers. */
const Charts = (() => {
  const COLORS = ['#38bdf8', '#a78bfa', '#34d399', '#fbbf24', '#f87171', '#fb923c', '#2dd4bf', '#e879f9'];
  const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const fmt = v => {
    if (v === null || v === undefined || Number.isNaN(v)) return '–';
    const a = Math.abs(v);
    if (a >= 1e9) return (v / 1e9).toFixed(2) + 'G';
    if (a >= 1e6) return (v / 1e6).toFixed(2) + 'M';
    if (a >= 1e4) return (v / 1e3).toFixed(1) + 'k';
    return Number.isInteger(v) ? String(v) : v.toFixed(a < 1 ? 3 : 2);
  };
  const bytes = b => b < 1024 ? b + ' B' : b < 1048576 ? (b / 1024).toFixed(1) + ' KB' : (b / 1048576).toFixed(2) + ' MB';

  function hbars(data, o = {}) {
    if (!data.length) return '<div class="empty">No data</div>';
    const max = o.max || Math.max(1, ...data.map(d => d.value));
    return '<div class="hbars">' + data.map((d, i) =>
      `<div class="hb" title="${esc(d.label)}"><span class="hl">${esc(d.label)}</span><div class="ht"><div class="hf" style="width:${(d.value / max * 100).toFixed(1)}%;background:${d.color || COLORS[i % COLORS.length]}"></div></div><b>${o.format ? o.format(d.value) : fmt(d.value)}${o.unit || ''}</b></div>`).join('') + '</div>';
  }

  function donut(data, size = 150) {
    const total = data.reduce((s, d) => s + d.value, 0) || 1, r = 54, C = 2 * Math.PI * r;
    let off = 0;
    const arcs = data.map((d, i) => {
      const len = d.value / total * C;
      const s = `<circle r="${r}" cx="75" cy="75" fill="none" stroke="${d.color || COLORS[i % COLORS.length]}" stroke-width="22" stroke-dasharray="${len} ${C - len}" stroke-dashoffset="${-off}" transform="rotate(-90 75 75)"><title>${esc(d.label)}: ${d.value}</title></circle>`;
      off += len;
      return s;
    }).join('');
    const legend = data.map((d, i) => `<span><i style="background:${d.color || COLORS[i % COLORS.length]}"></i>${esc(d.label)} <b>${d.value}</b></span>`).join('');
    return `<div style="display:flex;gap:18px;align-items:center;flex-wrap:wrap"><svg viewBox="0 0 150 150" width="${size}" height="${size}">${arcs}<text x="75" y="80" text-anchor="middle" style="font:700 22px system-ui">${total}</text></svg><div class="legend" style="flex-direction:column;gap:6px">${legend}</div></div>`;
  }

  /** series: [{name, points:[[x,y],...], color}] */
  function line(series, o = {}) {
    const W = 680, H = 260, L = 56, R = 14, T = 12, B = 34;
    const pts = series.flatMap(s => s.points);
    if (!pts.length) return '<div class="empty">No data yet</div>';
    const xs = pts.map(p => p[0]), ys = pts.map(p => p[1]);
    const x0 = Math.min(...xs), x1 = Math.max(...xs), y1 = Math.max(...ys, 1e-9) * 1.08;
    const X = x => L + (x1 === x0 ? 0.5 : (x - x0) / (x1 - x0)) * (W - L - R);
    const Y = y => H - B - (y / y1) * (H - T - B);
    let g = '';
    for (let i = 0; i <= 4; i++) {
      const yv = y1 * i / 4, y = Y(yv);
      g += `<line x1="${L}" x2="${W - R}" y1="${y}" y2="${y}" stroke="var(--border)"/><text x="${L - 8}" y="${y + 4}" text-anchor="end">${fmt(yv)}</text>`;
    }
    const xt = [...new Set(xs)].sort((a, b) => a - b);
    xt.forEach(x => g += `<text x="${X(x)}" y="${H - 14}" text-anchor="middle">${o.xfmt ? o.xfmt(x) : fmt(x)}</text>`);
    const lines = series.map((s, i) => {
      const c = s.color || COLORS[i % COLORS.length];
      const d = s.points.map(p => X(p[0]) + ',' + Y(p[1])).join(' ');
      return `<polyline points="${d}" fill="none" stroke="${c}" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round" pathLength="1" style="stroke-dasharray:1;stroke-dashoffset:0;animation:dashin 1s ease both"/>` +
        s.points.map(p => `<circle cx="${X(p[0])}" cy="${Y(p[1])}" r="4" fill="${c}"><title>${esc(s.name)} @ ${fmt(p[0])}: ${fmt(p[1])}${o.unit || ''}</title></circle>`).join('');
    }).join('');
    const legend = series.map((s, i) => `<span><i style="background:${s.color || COLORS[i % COLORS.length]}"></i>${esc(s.name)}</span>`).join('');
    return `<svg viewBox="0 0 ${W} ${H}" width="100%" style="max-height:300px"><g style="font:11px system-ui;fill:var(--muted)">${g}</g>${lines}</svg><div class="legend">${legend}</div>`;
  }

  /** Column chart of grouped values: items [{label, value}] */
  function columns(items, o = {}) {
    if (!items.length) return '<div class="empty">No data</div>';
    const max = Math.max(...items.map(i => i.value), 1e-9);
    return `<div class="bars" style="height:${o.height || 160}px">` + items.map((it, i) =>
      `<div class="bar" style="height:${Math.max(2, it.value / max * 100)}%;background:${it.color || COLORS[i % COLORS.length]}" title="${esc(it.label)}: ${fmt(it.value)}${o.unit || ''}"><i>${fmt(it.value)}</i></div>`).join('') +
      '</div><div style="display:flex;gap:3px">' + items.map(it => `<span style="flex:1;font-size:10px;text-align:center;overflow:hidden;text-overflow:ellipsis;color:var(--muted)">${esc(it.label)}</span>`).join('') + '</div>';
  }

  /** Traffic area chart with highlighted minutes. */
  function area(values, marks = [], truth = []) {
    const W = 760, H = 180, L = 40, R = 10, T = 10, B = 24, n = values.length;
    if (!n) return '<div class="empty">No traffic data</div>';
    const max = Math.max(...values, 1), X = i => L + i / Math.max(1, n - 1) * (W - L - R), Y = v => H - B - v / max * (H - T - B);
    const path = values.map((v, i) => `${i ? 'L' : 'M'}${X(i)},${Y(v)}`).join(' ');
    const m = marks.map(i => `<line x1="${X(i)}" x2="${X(i)}" y1="${T}" y2="${H - B}" stroke="var(--bad)" stroke-width="2" opacity=".7"><title>anomalous minute ${i}: ${values[i]} flows</title></line>`).join('');
    const t = truth.map(i => `<circle cx="${X(i)}" cy="${H - B + 9}" r="3" fill="var(--warn)"><title>ground-truth DDoS minute ${i}</title></circle>`).join('');
    return `<svg viewBox="0 0 ${W} ${H}" width="100%"><g style="font:10px system-ui;fill:var(--muted)"><text x="4" y="${T + 8}">${max}/min</text><text x="${L}" y="${H - 4}">minute 0</text><text x="${W - R}" y="${H - 4}" text-anchor="end">${n - 1}</text></g><path d="${path} L${X(n - 1)},${Y(0)} L${X(0)},${Y(0)}Z" fill="var(--accent)" opacity=".18"/><path d="${path}" fill="none" stroke="var(--accent)" stroke-width="2"/>${m}${t}</svg>`;
  }

  function spark(vals, w = 120, h = 28) {
    if (vals.length < 2) return '';
    const mx = Math.max(...vals, 1e-9), mn = Math.min(...vals);
    const d = vals.map((v, i) => `${i ? 'L' : 'M'}${(i / (vals.length - 1) * w).toFixed(1)},${(h - 3 - (v - mn) / ((mx - mn) || 1) * (h - 6)).toFixed(1)}`).join(' ');
    return `<svg viewBox="0 0 ${w} ${h}" width="${w}" height="${h}"><path d="${d}" fill="none" stroke="var(--accent)" stroke-width="1.8"/></svg>`;
  }

  const style = document.createElement('style');
  style.textContent = '@keyframes dashin{from{stroke-dashoffset:1}to{stroke-dashoffset:0}}';
  document.head.appendChild(style);
  return { hbars, donut, line, columns, area, spark, esc, fmt, bytes, COLORS };
})();
