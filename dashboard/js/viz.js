/*
 * Live algorithm visualisation. Every frame is rebuilt from the execution trace that the Java algorithm
 * recorded while it ran (run.trace). Nothing here is scripted: seek to event i and the renderer derives
 * the whole picture from events 0..i.
 */
const Viz = (() => {
  const esc = Charts.esc, PITCH = 23;
  const vis = ch => ch === ' ' ? '␣' : ch === '\u0001' ? '⏐' : ch === '\n' ? '⏎' : ch;
  const $ = (el, sel) => el.querySelector(sel);

  /** (Re)build a row of character cells only when its text changes. */
  function cells(el, str, cls = '') {
    if (el._s !== str) {
      el._s = str;
      el.innerHTML = str.split('').map(ch => `<div class="c ${cls}">${esc(vis(ch))}</div>`).join('');
      el._c = [...el.children];
    }
    return el._c;
  }
  function numRow(el, arr) {
    const key = JSON.stringify(arr);
    if (el._s !== key) { el._s = key; el.innerHTML = arr.map(v => `<div class="c s">${v}</div>`).join(''); el._c = [...el.children]; }
    return el._c;
  }
  const reset = (cs, keep = []) => cs.forEach(c => { c.className = 'c' + (c.dataset.k ? ' ' + c.dataset.k : ''); });
  const mark = (cs, a, b, cls) => { for (let k = Math.max(0, a); k < Math.min(cs.length, b); k++) cs[k].classList.add(cls); };
  const chips = o => '<div class="kv">' + Object.entries(o).map(([k, v]) => `<span class="chip"><span>${esc(k)}</span> <b>${esc(v)}</b></span>`).join('') + '</div>';
  const scrollTo = (el, idx) => { const p = el.parentElement; if (p) p.scrollLeft = Math.max(0, idx * PITCH - p.clientWidth / 2); };

  // ============================================================ string matching tapes
  const tape = kind => ({
    init(st) {
      st.innerHTML = `<div class="lbl">TEXT (pointer ▲ highlighted)</div><div style="overflow-x:auto;padding-bottom:6px"><div class="tape" data-r="text"></div>` +
        `<div data-r="patwrap" class="tape pat" style="flex-direction:column;align-items:flex-start"><div class="tape" data-r="pat"></div><div class="tape" data-r="lps"></div></div></div>` +
        `<div data-r="info"></div>`;
      st.text = $(st, '[data-r=text]'); st.patwrap = $(st, '[data-r=patwrap]'); st.pat = $(st, '[data-r=pat]'); st.lps = $(st, '[data-r=lps]'); st.info = $(st, '[data-r=info]');
    },
    draw(st, ev, i, run) {
      if (kind === 'kmp') return kmpDraw(st, ev, i, run);
      if (kind === 'z') return zDraw(st, ev, i, run);
      return rkDraw(st, ev, i, run);
    }
  });

  function kmpDraw(st, ev, i, run) {
    const tc = cells(st.text, run.inputPreview.text || '');
    let pat = '', lps = null, off = 0, matched = 0, badAt = -1, phase = 'prepare', found = [], ti = -1, li = -1, len = -1;
    for (let k = 0; k <= i; k++) {
      const e = ev[k];
      if (e.t === 'pattern') { pat = e.pattern; lps = null; off = 0; matched = 0; badAt = -1; phase = 'prepare'; ti = -1; }
      else if (e.t.startsWith('lps')) { phase = 'build the LPS table'; lps = e.lps; li = e.i; len = e.len; off = 0; matched = 0; badAt = -1; }
      else if (e.t === 'cmp') { phase = 'search'; ti = e.ti; off = e.ti - e.pj; matched = e.pj; badAt = e.eq ? -1 : e.pj; if (e.eq) matched = e.pj + 1; }
      else if (e.t === 'shift') { phase = 'search · shift'; ti = e.ti; off = e.ti - e.to; matched = e.to; badAt = -1; }
      else if (e.t === 'match') { found.push([e.pos, e.pos + e.pattern.length]); }
    }
    const e = ev[i];
    if (e.t === 'match') { off = e.pos; matched = pat.length; phase = 'match found'; }
    reset(tc);
    found.forEach(f => mark(tc, f[0], f[1], 'found'));
    if (ti >= 0 && ti < tc.length) { tc[ti].classList.add('cur'); scrollTo(st.text, ti); }
    st.patwrap.style.transform = `translateX(${Math.max(0, off) * PITCH}px)`;
    const pc = cells(st.pat, pat);
    reset(pc);
    if (phase.startsWith('build')) { if (li >= 0 && li < pc.length) pc[li].classList.add('cur'); if (len >= 0 && len < pc.length) pc[len].classList.add('box'); }
    else { mark(pc, 0, Math.min(matched, pc.length), 'ok'); if (badAt >= 0 && badAt < pc.length) pc[badAt].classList.add('bad'); }
    if (lps) { const lc = numRow(st.lps, lps); lc.forEach((c, k) => c.classList.toggle('hi', phase.startsWith('build') && k === li)); } else st.lps.innerHTML = '', st.lps._s = null;
    st.info.innerHTML = `<div class="lbl">PATTERN "${esc(pat)}" + LPS table (under it)</div><span class="phase">${esc(phase)}</span>` +
      chips({ 'text index i': ti < 0 ? '–' : ti, 'pattern offset': Math.max(0, off), 'matches so far': found.length });
  }

  function zDraw(st, ev, i) {
    let s = '', m = 0, z = null, zi = -1, l = 0, r = 0, found = [];
    for (let k = 0; k <= i; k++) {
      const e = ev[k];
      if (e.t === 'start') { s = e.s; m = e.m; z = null; found = []; }
      else if (e.t === 'z') { z = e.z; zi = e.i; l = e.l; r = e.r; }
      else if (e.t === 'match') found.push(e.pos);
    }
    const c = cells(st.text, s);
    reset(c);
    mark(c, 0, m, 'win');
    if (z) mark(c, l, r, 'box');
    found.forEach(p => mark(c, p + m + 1, p + 2 * m + 1, 'found'));
    if (zi >= 0 && zi < c.length) { c[zi].classList.add('cur'); scrollTo(st.text, zi); }
    st.patwrap.style.transform = 'none';
    st.pat.innerHTML = ''; st.pat._s = null;
    if (z) numRow(st.lps, z); else st.lps.innerHTML = '';
    const e = ev[i];
    st.info.innerHTML = `<div class="lbl">Row 1: pattern + ⏐ + text · Row 2: Z array · yellow = pattern, purple = current Z-box [l, r)</div>` +
      chips({ i: zi < 0 ? '–' : zi, 'Z-box': z ? `[${l}, ${r})` : '–', 'z[i]': e.zi ?? '–', 'copied from box': e.reused === undefined ? '–' : e.reused, 'matches': found.length });
  }

  function rkDraw(st, ev, i, run) {
    const tc = cells(st.text, run.inputPreview.text || '');
    let pat = '', m = 0, w = null, found = [], coll = 0;
    for (let k = 0; k <= i; k++) {
      const e = ev[k];
      if (e.t === 'pattern') { pat = e.pattern; m = e.m; w = null; }
      else if (e.t === 'window') { w = e; if (e.real) found.push([e.i, e.i + m]); if (e.collision) coll++; }
    }
    reset(tc);
    found.forEach(f => mark(tc, f[0], f[1], 'found'));
    if (w) { mark(tc, w.i, w.i + m, 'win'); scrollTo(st.text, w.i); }
    st.patwrap.style.transform = `translateX(${(w ? w.i : 0) * PITCH}px)`;
    const pc = cells(st.pat, pat);
    reset(pc);
    if (w) mark(pc, 0, pc.length, w.real ? 'ok' : w.eq ? 'bad' : '');
    st.lps.innerHTML = ''; st.lps._s = null;
    st.info.innerHTML = `<div class="lbl">Rolling hash window (yellow) vs pattern hash</div>` + (w ? `<span class="phase">${w.real ? 'hash equal → characters verified: MATCH' : w.collision ? 'hash equal but characters differ: COLLISION' : 'hash differs → skip without comparing characters'}</span>` : '') +
      chips({ 'window hash': w ? w.wh : '–', 'pattern hash': w ? w.ph : '–', 'matches': found.length, 'collisions': coll });
  }

  // ============================================================ Aho-Corasick trie
  const trie = {
    init(st, run) {
      const ev = run.trace, nodes = { 0: { id: 0, parent: -1, ch: '', depth: 0, created: -1, endAt: 1e9, kids: [] } }, fails = [];
      ev.forEach((e, k) => {
        if (e.t === 'insert') {
          if (e.created) { nodes[e.node] = { id: e.node, parent: e.parent, ch: e.ch, depth: nodes[e.parent].depth + 1, created: k, endAt: 1e9, kids: [] }; nodes[e.parent].kids.push(e.node); }
          if (e.end) nodes[e.node].endAt = Math.min(nodes[e.node].endAt, k);
        } else if (e.t === 'fail') fails.push({ node: e.node, to: e.to, at: k });
      });
      let leaf = 0, maxD = 0;
      (function lay(id) { const n = nodes[id]; maxD = Math.max(maxD, n.depth); if (!n.kids.length) n.x = leaf++; else { n.kids.forEach(lay); n.x = (nodes[n.kids[0]].x + nodes[n.kids[n.kids.length - 1]].x) / 2; } })(0);
      const X = n => 40 + n.x * 50, Y = n => 34 + n.depth * 62, W = Math.max(300, leaf * 50 + 80), H = (maxD + 1) * 62 + 30;
      let svg = `<svg class="viz" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}"><defs><marker id="ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0 0L10 5L0 10z" fill="var(--warn)"/></marker></defs>`;
      Object.values(nodes).forEach(n => { if (n.parent >= 0) { const p = nodes[n.parent]; svg += `<line class="edge" id="te${n.id}" x1="${X(p)}" y1="${Y(p)}" x2="${X(n)}" y2="${Y(n)}"/><text id="tl${n.id}" x="${(X(p) + X(n)) / 2 + 6}" y="${(Y(p) + Y(n)) / 2 - 2}" opacity="0" style="font-weight:700">${esc(n.ch)}</text>`; } });
      fails.forEach(f => { const a = nodes[f.node], b = nodes[f.to]; if (f.to === 0 && a.depth <= 1) return; const mx = (X(a) + X(b)) / 2 - 38, my = (Y(a) + Y(b)) / 2; svg += `<path class="failk" id="tf${f.node}" d="M${X(a)},${Y(a)} Q${mx},${my} ${X(b)},${Y(b)}" marker-end="url(#ah)"/>`; });
      Object.values(nodes).forEach(n => { svg += `<g class="node" id="tn${n.id}"><circle cx="${X(n)}" cy="${Y(n)}" r="14"/><text x="${X(n)}" y="${Y(n) + 4}" text-anchor="middle">${n.id}</text></g>`; });
      st.innerHTML = `<div data-r="phase"></div><div style="overflow:auto">${svg}</svg></div><div class="lbl">TEXT being scanned</div><div style="overflow-x:auto"><div class="tape" data-r="text"></div></div><div data-r="info"></div><div class="legend"><span><i style="background:var(--warn)"></i>dashed amber = failure link</span><span><i style="background:var(--bad)"></i>red ring = end of a signature</span><span><i style="background:var(--accent)"></i>filled = current state</span></div>`;
      st.nodes = nodes; st.fails = fails; st.text = $(st, '[data-r=text]'); st.info = $(st, '[data-r=info]'); st.phase = $(st, '[data-r=phase]');
    },
    draw(st, ev, i, run) {
      const e = ev[i], nodes = st.nodes;
      let cur = 0, found = [], pos = -1, hotFail = -1, hotFrom = -1;
      for (let k = 0; k <= i; k++) { const x = ev[k]; if (x.t === 'match') found.push(x); }
      if (e.t === 'insert' || e.t === 'fail' || e.t === 'match') cur = e.node;
      else if (e.t === 'goto') cur = e.to;
      else if (e.t === 'fallback') { cur = e.to; hotFail = e.node; hotFrom = e.node; }
      if (e.t === 'fail') hotFail = e.node;
      if (e.i !== undefined) pos = e.i;
      Object.values(nodes).forEach(n => {
        const g = $(st, '#tn' + n.id), vis = n.created <= i;
        g.classList.toggle('show', vis); g.classList.toggle('cur', n.id === cur);
        g.classList.toggle('endn', n.endAt <= i);
        if (n.parent >= 0) { $(st, '#te' + n.id).classList.toggle('show', vis); const t = $(st, '#tl' + n.id); t.setAttribute('opacity', vis ? 1 : 0); }
      });
      st.fails.forEach(f => { const p = $(st, '#tf' + f.node); if (p) { p.classList.toggle('show', f.at <= i); p.classList.toggle('hot', f.node === hotFail); } });
      const phase = ['insert'].includes(e.t) ? '1 · Build the trie (one path per signature)' : e.t === 'fail' ? '2 · Compute failure links (BFS)' : e.t === 'search-start' ? '3 · Scan the text once' : '3 · Scan the text: goto edges, failure links, outputs';
      st.phase.innerHTML = `<span class="phase">Phase ${esc(phase)}</span>`;
      const text = run.inputPreview.text || '', tc = cells(st.text, text);
      reset(tc);
      found.forEach(m => mark(tc, m.start, m.i + 1, 'found'));
      if (pos >= 0 && pos < tc.length) { tc[pos].classList.add('cur'); scrollTo(st.text, pos); }
      st.info.innerHTML = chips({ 'current node': cur, 'trie nodes': Object.values(nodes).filter(n => n.created <= i).length, 'matches': found.length }) +
        (found.length ? '<div class="kv">' + [...new Set(found.map(m => m.pattern))].map(p => `<span class="chip bad">${esc(p)}</span>`).join('') + '</div>' : '');
    }
  };

  // ============================================================ suffix array rows
  const suffix = {
    init(st, run) {
      const s0 = run.trace.find(e => e.t === 'start');
      st.s = s0 ? s0.s : ''; st.n = st.s.length + 1;
      let rows = '';
      for (let idx = 0; idx < st.n; idx++) rows += `<div class="sarow" id="sr${idx}"><span class="ix">${idx}</span><span class="cl">–</span><span class="tx"></span></div>`;
      st.innerHTML = `<div data-r="info"></div><div style="position:relative;height:${st.n * 28}px;margin-top:6px">${rows}</div>`;
      st.info = $(st, '[data-r=info]');
    },
    draw(st, ev, i) {
      let round = null, idxR = -1;
      for (let k = 0; k <= i; k++) if (ev[k].t === 'round') { round = ev[k]; idxR = k; }
      const n = st.n, pos = Array.from({ length: n }, (_, k) => k);
      if (round) round.order.forEach((idx, p) => { pos[idx] = p; });
      const cnt = {};
      if (round) round.cls.forEach(c => cnt[c] = (cnt[c] || 0) + 1);
      for (let idx = 0; idx < n; idx++) {
        const row = $(st, '#sr' + idx), full = (idx === st.s.length ? '' : st.s.slice(idx)) + '$', k = round ? round.k : 0;
        row.style.transform = `translateY(${pos[idx] * 28}px)`;
        const cl = $(row, '.cl');
        if (round) { cl.textContent = round.cls[idx]; cl.style.background = `hsl(${round.cls[idx] * 47 % 360} 62% 45%)`; } else { cl.textContent = '–'; cl.style.background = 'var(--muted)'; }
        row.classList.toggle('uniq', !!round && cnt[round.cls[idx]] === 1);
        $(row, '.tx').innerHTML = `<u>${esc(full.slice(0, k))}</u>${esc(full.slice(k))}`;
      }
      st.info.innerHTML = round ? `<span class="phase">sorted by the first ${round.k} character(s)</span>` + chips({ 'distinct classes': `${round.classes} of ${n}`, 'all suffixes unique': round.classes === n ? 'yes ✓' : 'not yet' }) :
        `<span class="phase">suffixes in text order - not sorted yet</span><div class="lbl">Colour chips = equivalence class (equal first-k characters share a colour). Rows glide to their sorted position each round.</div>`;
    }
  };

  // ============================================================ edit-distance DP grid
  const dp = {
    init(st) { st.innerHTML = '<div data-r="info"></div><div data-r="grid" style="overflow:auto"></div><div class="legend" data-r="lg"></div>'; st.info = $(st, '[data-r=info]'); st.grid = $(st, '[data-r=grid]'); st.pairIdx = -2;
      st.lg = $(st, '[data-r=lg]'); st.lg.innerHTML = [['match', 'match'], ['substitute', 'substitute'], ['look-alike', 'look-alike (o↔0, l↔1…)'], ['insert', 'insert'], ['delete', 'delete'], ['swap', 'swap']].map(([c, t]) => `<span><i class="op-${c}" style="border:1px solid var(--border)"></i>${t}</span>`).join(''); },
    draw(st, ev, i) {
      let p = -1;
      for (let k = 0; k <= i; k++) if (ev[k].t === 'pair') p = k;
      if (p < 0) return;
      const pe = ev[p];
      if (st.pairIdx !== p) {
        st.pairIdx = p;
        let h = '<table class="dpt"><tr><th></th><th>·</th>' + pe.b.split('').map(c => `<th>${esc(vis(c))}</th>`).join('') + '</tr>';
        for (let r = 0; r < pe.rows; r++) { h += `<tr><th>${r ? esc(vis(pe.a[r - 1])) : '·'}</th>`; for (let c = 0; c < pe.cols; c++) h += `<td id="d${r}_${c}"></td>`; h += '</tr>'; }
        st.grid.innerHTML = h + '</table>';
      }
      for (let r = 0; r < pe.rows; r++) for (let c = 0; c < pe.cols; c++) { const td = $(st, `#d${r}_${c}`); td.className = ''; td.textContent = r === 0 ? c : c === 0 ? r : ''; }
      let last = null, path = null;
      for (let k = p + 1; k <= i; k++) {
        const e = ev[k]; if (e.t === 'pair') break;
        if (e.t === 'cell') { const td = $(st, `#d${e.i}_${e.j}`); td.textContent = e.v; td.className = 'op-' + e.op; last = e; }
        else if (e.t === 'path') path = e;
      }
      if (last && !path) $(st, `#d${last.i}_${last.j}`).classList.add('cur');
      if (path) path.path.forEach(([r, c]) => $(st, `#d${r}_${c}`).classList.add('path'));
      st.info.innerHTML = `<span class="phase">"${esc(pe.a)}" → "${esc(pe.b)}"</span>` + chips({ 'cells filled': ev.slice(p + 1, i + 1).filter(e => e.t === 'cell').length + ' / ' + (pe.rows - 1) * (pe.cols - 1), 'distance': path ? path.dist : 'in progress' });
    }
  };

  // ============================================================ network flow graph
  const flow = {
    init(st, run) {
      const g = run.trace.find(e => e.t === 'graph'); if (!g) { st.innerHTML = '<div class="empty">No graph event</div>'; return; }
      const n = g.nodes.length, lvl = Array(n).fill(-1), q = [g.source]; lvl[g.source] = 0;
      while (q.length) { const u = q.shift(); g.edges.forEach(([a, b]) => { [[a, b], [b, a]].forEach(([x, y]) => { if (x === u && lvl[y] < 0) { lvl[y] = lvl[u] + 1; q.push(y); } }); }); }
      const maxL = Math.max(...lvl, 0); lvl.forEach((l, k) => { if (l < 0) lvl[k] = maxL + 1; });
      const byL = {}; lvl.forEach((l, k) => (byL[l] = byL[l] || []).push(k));
      const rows = Math.max(...Object.values(byL).map(a => a.length)), W = 90 + (Math.max(...lvl) + 1) * 140, H = Math.max(240, rows * 78);
      const pos = g.nodes.map((_, k) => { const a = byL[lvl[k]]; return [60 + lvl[k] * 140, (a.indexOf(k) + 1) * H / (a.length + 1)]; });
      let svg = `<svg class="viz" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}"><defs><marker id="ar" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0 0L10 5L0 10z" fill="var(--muted)"/></marker></defs>`;
      g.edges.forEach(([a, b, c], k) => {
        const [x1, y1] = pos[a], [x2, y2] = pos[b], d = Math.hypot(x2 - x1, y2 - y1) || 1, ux = (x2 - x1) / d, uy = (y2 - y1) / d;
        svg += `<line class="edge show" id="fe${k}" x1="${x1 + ux * 22}" y1="${y1 + uy * 22}" x2="${x2 - ux * 25}" y2="${y2 - uy * 25}" marker-end="url(#ar)"/><text id="fl${k}" x="${(x1 + x2) / 2}" y="${(y1 + y2) / 2 - 5}" text-anchor="middle" style="font-size:11px">0/${c}</text>`;
      });
      g.nodes.forEach((nm, k) => { const [x, y] = pos[k]; svg += `<g class="node show" id="fn${k}"><circle cx="${x}" cy="${y}" r="21" ${k === g.source ? 'style="stroke:var(--good)"' : k === g.sink ? 'style="stroke:var(--bad)"' : ''}/><text x="${x}" y="${y + 4}" text-anchor="middle" style="font-size:10px">${esc(nm.slice(0, 7))}</text><text id="fv${k}" x="${x}" y="${y - 27}" text-anchor="middle" style="font-size:10px;fill:var(--accent2)"></text></g>`; });
      st.innerHTML = '<div data-r="info"></div><div style="overflow:auto">' + svg + '</svg></div><div class="legend"><span><i style="background:var(--accent)"></i>animated = augmenting path</span><span><i style="background:var(--warn)"></i>saturated link</span><span><i style="background:var(--bad)"></i>min-cut (critical) link</span><span>green ring = source · red ring = sink</span></div>';
      st.g = g; st.info = $(st, '[data-r=info]');
    },
    draw(st, ev, i) {
      const g = st.g; if (!g) return;
      let flows = g.edges.map(() => 0), e = ev[i], level = null, total = 0, aug = 0;
      for (let k = 0; k <= i; k++) { const x = ev[k]; if (x.flows) flows = x.flows; if (x.t === 'level') level = x.level; if (x.total !== undefined) total = x.total; if (x.t === 'augment') aug++; }
      const hot = new Set();
      if (e.t === 'augment') for (let k = 0; k + 1 < e.path.length; k++) g.edges.forEach(([a, b], idx) => { if ((a === e.path[k] && b === e.path[k + 1]) || (b === e.path[k] && a === e.path[k + 1])) hot.add(idx); });
      const cut = new Set(e.t === 'cut' ? e.cut : []);
      g.edges.forEach(([a, b, c], k) => {
        const l = $(st, '#fe' + k), t = $(st, '#fl' + k), f = flows[k] || 0;
        t.textContent = `${f}/${c}`; l.style.strokeWidth = 1.5 + 4 * f / c; l.style.stroke = f === c && c > 0 ? 'var(--warn)' : f > 0 ? 'var(--accent)' : '';
        l.classList.toggle('hot', hot.has(k)); l.classList.toggle('cut', cut.has(k));
      });
      g.nodes.forEach((_, k) => { const n = $(st, '#fn' + k); n.classList.toggle('cur', e.t === 'augment' && e.path.includes(k)); $(st, '#fv' + k).textContent = level ? 'L' + level[k] : ''; if (e.t === 'cut') n.firstElementChild.style.fill = e.side[k] ? 'color-mix(in srgb,var(--good) 25%,var(--panel))' : ''; else n.firstElementChild.style.fill = ''; });
      st.info.innerHTML = chips({ 'augmenting paths so far': aug, 'flow pushed': total, [e.t === 'augment' ? 'bottleneck' : 'event']: e.t === 'augment' ? e.bottleneck : e.t }) +
        (e.t === 'augment' ? `<div class="kv"><span class="chip acc">${e.path.map(p => esc(g.nodes[p])).join(' → ')}</span></div>` : '');
    }
  };

  // ============================================================ vertex cover graph
  const vc = {
    init(st, run) {
      const g = run.trace.find(e => e.t === 'graph'); if (!g) return;
      const n = g.nodes.length, R = 105, W = 320, pos = g.nodes.map((_, k) => [W / 2 + R * Math.cos(2 * Math.PI * k / n - Math.PI / 2), W / 2 + R * Math.sin(2 * Math.PI * k / n - Math.PI / 2)]);
      let svg = `<svg class="viz" width="${W}" height="${W}" viewBox="0 0 ${W} ${W}">`;
      g.edges.forEach(([a, b], k) => svg += `<line class="edge show" id="ve${k}" x1="${pos[a][0]}" y1="${pos[a][1]}" x2="${pos[b][0]}" y2="${pos[b][1]}"/>`);
      g.nodes.forEach((nm, k) => svg += `<g class="node show" id="vn${k}"><circle cx="${pos[k][0]}" cy="${pos[k][1]}" r="19"/><text x="${pos[k][0]}" y="${pos[k][1] + 4}" text-anchor="middle" style="font-size:9px">${esc(nm.slice(0, 6))}</text></g>`);
      st.innerHTML = '<div data-r="info"></div><div style="overflow:auto">' + svg + '</svg></div><div class="legend"><span><i style="background:var(--accent)"></i>filled = in the cover</span><span><i style="background:var(--bad)"></i>red edge = picked uncovered edge</span></div>';
      st.g = g; st.info = $(st, '[data-r=info]');
    },
    draw(st, ev, i) {
      const g = st.g; if (!g) return;
      let cover = [], picked = [];
      for (let k = 0; k <= i; k++) if (ev[k].t === 'pick-edge') { cover = ev[k].cover; picked.push([ev[k].u, ev[k].v]); }
      const e = ev[i];
      g.edges.forEach(([a, b], k) => { const l = $(st, '#ve' + k); const isP = picked.some(p => (p[0] === a && p[1] === b)); l.classList.toggle('cut', isP); l.classList.toggle('hot', e.t === 'pick-edge' && e.u === a && e.v === b); });
      g.nodes.forEach((_, k) => { $(st, '#vn' + k).classList.toggle('cur', cover.includes(k)); });
      st.info.innerHTML = chips({ 'cover size': cover.length, 'picked edges (matching)': picked.length });
    }
  };

  // ============================================================ generic renderer (arrays, tables, state chips)
  function barsHtml(arr, cls) {
    const mx = Math.max(...arr.map(Math.abs), 1);
    return '<div class="bars">' + arr.map((v, k) => `<div class="bar ${cls ? cls(v, k) : ''}" style="height:${Math.max(3, Math.abs(v) / mx * 100)}%"><i>${v}</i></div>`).join('') + '</div>';
  }
  const generic = {
    init(st) { st.innerHTML = '<div data-r="g"></div>'; st.g = $(st, '[data-r=g]'); },
    draw(st, ev, i, run) {
      const e = ev[i]; let h = '';
      const sc = {};
      Object.entries(e).forEach(([k, v]) => { if (k === 't' || k === 'msg') return;
        if (Array.isArray(v) && v.length && Array.isArray(v[0]) && typeof v[0][0] === 'number' && v.length <= 24) {
          h += `<div class="lbl">${esc(k)}</div><table class="dpt">` + v.map(r => '<tr>' + r.map(x => `<td>${x}</td>`).join('') + '</tr>').join('') + '</table>';
        } else if (Array.isArray(v) && v.length && v.every(x => typeof x === 'number') && v.length <= 64) {
          let cls = null;
          if (e.t === 'partition' && k === 'arr') cls = (val, idx) => idx < e.lo || idx > e.hi ? 'dim' : (idx >= e.lt && idx <= e.gt ? 'e' : '');
          if (k === 'reservoir') cls = (val, idx) => idx === e.replaced ? 'p' : '';
          h += `<div class="lbl">${esc(k)}${e.t === 'partition' && k === 'arr' ? ` (pivot ${e.pivot}; purple = equal to pivot)` : ''}</div>` + barsHtml(v, cls);
        } else if (typeof v === 'string' && /^[SL]+$/.test(v)) {
          h += `<div class="lbl">${esc(k)} (S = smaller than next suffix, L = larger)</div><div class="tape">${v.split('').map(c => `<div class="c ${c === 'S' ? 'ok' : 'bad'}">${c}</div>`).join('')}</div>`;
        } else if (typeof v !== 'object' || v === null) sc[k] = v;
        else if (Array.isArray(v) && v.length <= 24) sc[k] = v.map(x => typeof x === 'object' ? JSON.stringify(x) : x).join(', ');
        else sc[k] = `[${v.length} values]`;
      });
      st.g.innerHTML = `<span class="phase">${esc(e.t)}</span>` + chips(sc) + h;
    }
  };

  const REGISTRY = { kmp: tape('kmp'), z: tape('z'), rabinkarp: tape('rk'), ahocorasick: trie, suffixarray: suffix, editdistance: dp,
    edmondskarp: flow, dinic: flow, fordfulkerson: flow, mincut: flow, vertexcover: vc };

  // ============================================================ player
  class Player {
    constructor(root, run) {
      this.run = run; this.ev = run.trace || []; this.i = 0; this.timer = null; this.speed = 5;
      this.r = REGISTRY[run.algorithm] || generic;
      root.innerHTML = `<div class="stage" data-r="stage"></div>
        <div class="ctrl"><button class="btn sm" data-r="first" title="Restart">⏮</button><button class="btn sm" data-r="prev" title="Step back">◀</button><button class="btn" data-r="play">⏸ Pause</button><button class="btn sm" data-r="next" title="Step forward">▶</button><button class="btn sm" data-r="last" title="Jump to end">⏭</button>
        <label class="grow" style="display:flex;gap:8px;align-items:center;color:var(--muted);font-size:12px">Step<input class="grow" type="range" data-r="seek" min="0" max="${Math.max(0, this.ev.length - 1)}" value="0"></label>
        <label style="display:flex;gap:8px;align-items:center;color:var(--muted);font-size:12px">Speed<input type="range" data-r="speed" min="1" max="10" value="5"></label><span class="chip mono" data-r="count"></span></div>
        <div class="explain"><span class="tag" data-r="tag"></span><div data-r="msg"></div></div>
        <div class="lbl">EXECUTION TRACE (recorded by the Java algorithm${run.traceTruncated ? ' - truncated at the event limit' : ''})</div><div class="evlog" data-r="log"></div>`;
      const q = s => $(root, `[data-r=${s}]`);
      this.stage = q('stage'); this.seekEl = q('seek'); this.playBtn = q('play'); this.countEl = q('count'); this.tag = q('tag'); this.msg = q('msg'); this.log = q('log');
      q('first').onclick = () => { this.pause(); this.seek(0); }; q('prev').onclick = () => { this.pause(); this.seek(this.i - 1); };
      q('next').onclick = () => { this.pause(); this.seek(this.i + 1); }; q('last').onclick = () => { this.pause(); this.seek(this.ev.length - 1); };
      this.playBtn.onclick = () => this.toggle(); this.seekEl.oninput = () => { this.pause(); this.seek(+this.seekEl.value); };
      q('speed').oninput = e => { this.speed = +e.target.value; if (this.timer) { this.pause(); this.play(); } };
      this.log.onclick = e => { const d = e.target.closest('div[data-i]'); if (d) { this.pause(); this.seek(+d.dataset.i); } };
      if (!this.ev.length) { this.stage.innerHTML = '<div class="empty">This run recorded no trace events.</div>'; return; }
      this.r.init(this.stage, run);
      this.seek(0);
    }
    delay() { return Math.max(18, 900 / Math.pow(1.5, this.speed - 1)); }
    play() { if (this.timer || !this.ev.length) return; if (this.i >= this.ev.length - 1) this.seek(0); this.playBtn.textContent = '⏸ Pause'; this.timer = setInterval(() => { if (this.i >= this.ev.length - 1) return this.pause(); this.seek(this.i + 1); }, this.delay()); }
    pause() { if (this.timer) clearInterval(this.timer); this.timer = null; this.playBtn.textContent = '▶ Play'; }
    toggle() { this.timer ? this.pause() : this.play(); }
    destroy() { this.pause(); }
    seek(n) {
      if (!this.ev.length) return;
      this.i = Math.max(0, Math.min(this.ev.length - 1, n));
      const e = this.ev[this.i];
      this.seekEl.value = this.i; this.countEl.textContent = `${this.i + 1} / ${this.ev.length}`;
      this.tag.textContent = e.t; this.msg.textContent = e.msg;
      const a = Math.max(0, this.i - 4), b = Math.min(this.ev.length, this.i + 12);
      this.log.innerHTML = this.ev.slice(a, b).map((x, k) => `<div data-i="${a + k}" class="${a + k === this.i ? 'cur' : ''}">${a + k + 1}. [${esc(x.t)}] ${esc(x.msg)}</div>`).join('');
      try { this.r.draw(this.stage, this.ev, this.i, this.run); } catch (err) { console.error('render error', err); this.stage.insertAdjacentHTML('beforeend', `<div class="alert">Renderer error: ${esc(err.message)}</div>`); this.pause(); }
    }
  }
  return { Player, REGISTRY };
})();
