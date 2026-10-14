/* CyberShield dashboard - talks to the Java backend through /api/*. No data on screen is hard-coded. */
const $ = (s, r = document) => r.querySelector(s);
const esc = Charts.esc, fmt = Charts.fmt;
const S = { dataset: localStorage.getItem('cs.dataset') || 'enterprise-sample', algos: [], datasets: [], scan: null, profile: null, samples: [], player: null, lastRun: null, page: 'overview', status: null };

// ------------------------------------------------------------------ plumbing
async function api(path, opt = {}) {
  const o = { method: opt.method || 'GET', headers: {} };
  if (opt.body !== undefined) { o.body = typeof opt.body === 'string' ? opt.body : JSON.stringify(opt.body); o.headers['Content-Type'] = typeof opt.body === 'string' ? 'text/plain' : 'application/json'; }
  const r = await fetch(path, o);
  let data = null;
  try { data = await r.json(); } catch { /* non-json */ }
  if (!r.ok) throw new Error((data && data.error) || `HTTP ${r.status}`);
  return data;
}
function toast(msg, bad) { const t = $('#toast'); t.textContent = msg; t.className = 'toast' + (bad ? ' bad' : ''); t.hidden = false; clearTimeout(toast.t); toast.t = setTimeout(() => t.hidden = true, bad ? 6000 : 3000); }
const loading = (msg = 'Running the Java algorithm…') => `<div class="empty"><span class="spin"></span> ${msg}</div>`;
const sevClass = s => s >= 10 ? 's10' : s >= 8 ? 's8' : s >= 5 ? 's5' : 's1';
const algo = id => S.algos.find(a => a.id === id);
const mem = b => Charts.bytes(b || 0);

// ------------------------------------------------------------------ shell
const PAGES = [['overview', '◈', 'Overview'], ['lab', '▶', 'Live Algorithm Lab'], ['detections', '⚠', 'Detections'], ['compare', '⇄', 'Compare'], ['benchmark', '⏱', 'Benchmarks'], ['history', '☰', 'History'], ['algorithms', '∑', 'Algorithms']];
$('#nav').innerHTML = PAGES.map(([id, ic, t]) => `<a data-p="${id}"><span class="ic">${ic}</span>${t}</a>`).join('');
$('#nav').onclick = e => { const a = e.target.closest('a'); if (a) { location.hash = a.dataset.p; $('#side').classList.remove('open'); } };
$('#menuBtn').onclick = () => $('#side').classList.toggle('open');
$('#themeBtn').onclick = () => { const t = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'; document.documentElement.dataset.theme = t; localStorage.setItem('cs.theme', t); };
document.documentElement.dataset.theme = localStorage.getItem('cs.theme') || (matchMedia('(prefers-color-scheme:dark)').matches ? 'dark' : 'light');
$('#datasetSel').onchange = e => { S.dataset = e.target.value; localStorage.setItem('cs.dataset', S.dataset); S.scan = S.profile = null; S.samples = []; route(); };
$('#uploadBtn').onclick = () => $('#uploadFile').click();
$('#uploadFile').onchange = async e => {
  const f = e.target.files[0]; if (!f) return;
  try {
    const txt = await f.text();
    const r = await api('/api/datasets/upload?name=' + encodeURIComponent(f.name.replace(/\.[^.]+$/, '')), { method: 'POST', body: txt });
    toast(`Loaded ${r.lines} lines from ${f.name}`); await loadDatasets(r.id); route();
  } catch (err) { toast('Upload failed: ' + err.message, true); }
  e.target.value = '';
};
async function loadDatasets(select) {
  const d = await api('/api/datasets'); S.datasets = d.datasets;
  if (select) S.dataset = select;
  if (!S.datasets.find(x => x.id === S.dataset)) S.dataset = S.datasets[0].id;
  localStorage.setItem('cs.dataset', S.dataset);
  $('#datasetSel').innerHTML = S.datasets.map(x => `<option value="${esc(x.id)}" ${x.id === S.dataset ? 'selected' : ''}>${esc(x.name)}</option>`).join('');
}
async function pollStatus() {
  try {
    const s = await api('/api/status'); S.status = s;
    const chip = $('#statusChip'), busy = s.activeRuns > 0;
    chip.innerHTML = `<i class="dot ${busy ? 'busy' : 'on'}"></i><span>${busy ? 'running ' + s.running.join(', ') : 'backend online'}</span>`;
    $('#heapInfo').textContent = `JVM heap ${s.heapUsedMB} MB · ${s.processors} CPU`;
  } catch { $('#statusChip').innerHTML = '<i class="dot bad"></i><span>backend offline</span>'; }
}
async function getScan() { if (!S.scan || S.scan.dataset.id !== S.dataset) S.scan = await api('/api/scan?dataset=' + encodeURIComponent(S.dataset) + '&limit=1000'); return S.scan; }
async function getProfile() { if (!S.profile || S.profile.id !== S.dataset) S.profile = await api(`/api/datasets/${encodeURIComponent(S.dataset)}/profile`); return S.profile; }

async function route() {
  if (S.player) { S.player.destroy(); S.player = null; }
  const p = (location.hash || '#overview').slice(1).split('?')[0]; S.page = PAGES.find(x => x[0] === p) ? p : 'overview';
  document.querySelectorAll('#nav a').forEach(a => a.classList.toggle('on', a.dataset.p === S.page));
  $('#pageTitle').textContent = PAGES.find(x => x[0] === S.page)[2];
  const view = $('#view'); view.innerHTML = loading('Loading…');
  try { await PAGE[S.page](view); } catch (err) { view.innerHTML = `<div class="alert"><b>Could not load this page.</b> ${esc(err.message)}</div>`; console.error(err); }
}
window.addEventListener('hashchange', route);

// ------------------------------------------------------------------ overview
const PAGE = {};
PAGE.overview = async view => {
  const [prof, scan, hist] = await Promise.all([getProfile(), getScan(), api('/api/history?limit=500')]);
  const ev = scan.evaluation, tot = scan.totals, last = S.lastRun, runs = hist.runs;
  const stages = scan.pipeline;
  const steps = [
    ['Network / Data Input', 'done', stages[0].detail],
    ['Preprocessing', 'done', stages[1].detail + ` (${stages[1].ms} ms)`],
    ['Threat Detection', stages[3].status === 'failed' ? 'failed' : 'done', `${tot.matches} matches · ${stages[2].ms} ms`],
    ['Java Algorithms', 'done', `${S.algos.length} algorithms across M1–M6`],
    ['Execution Trace', last ? 'done' : 'idle', last ? `${(last.trace || []).length} events from ${last.algorithm}` : 'run one in the Live Lab'],
    ['Live Visualization', last && last.trace ? 'done' : 'idle', last ? 'replayed from the trace' : 'waiting for a trace'],
    ['Threat Results', 'done', `${tot.flaggedLines} flagged lines`],
    ['Analytics & Comparison', runs.length ? 'done' : 'idle', `${runs.length} runs in history`]];
  const net = prof.network;
  view.innerHTML = `
  <div class="card mb"><h3>Detection pipeline <small>${esc(scan.dataset.name)}</small></h3>
    <div class="pipe">${steps.map(([n, st, d]) => `<div class="step"><i class="dot ${st === 'done' ? 'on' : st === 'failed' ? 'bad' : ''}"></i><span class="packet" style="animation-delay:${Math.random() * 1.5}s"></span><b>${n}</b><small>${esc(d)}</small></div>`).join('')}</div></div>
  <div class="grid g4 mb">
    <div class="card kpi acc"><b>${fmt(prof.lines)}</b><span>log lines analysed</span><small>${prof.kilobytes} KB · ${prof.uniqueSourceIps} unique source IPs</small></div>
    <div class="card kpi bad"><b>${tot.flaggedLines}</b><span>threat lines detected</span><small>${tot.matches} signature matches · ${tot.categoriesSeen} categories</small></div>
    <div class="card kpi ${ev ? 'good' : ''}"><b>${ev ? (ev.recall * 100).toFixed(1) + '%' : '–'}</b><span>recall vs ground truth</span><small>${ev ? `precision ${(ev.precision * 100).toFixed(1)}% · ${ev.falseNegatives} missed` : 'no labels for this dataset'}</small></div>
    <div class="card kpi"><b>${net ? fmt(net.flows) : '–'}</b><span>network flows</span><small>${net ? `${net.megabytes} MB · ${net.uniqueSources} sources` : 'upload has no NetFlow'}</small></div></div>
  <div class="grid g2 mb">
    <div class="card"><h3>Threats by category <small>flagged lines</small></h3>${Charts.hbars(Object.entries(scan.byCategory).sort((a, b) => b[1] - a[1]).map(([k, v]) => ({ label: k, value: v })))}</div>
    <div class="card"><h3>Severity distribution</h3>${Charts.donut([['critical', '#b91c1c'], ['high', '#ea580c'], ['medium', '#ca8a04'], ['low', '#2563eb']].map(([k, c]) => ({ label: k, value: scan.bySeverity[k] || 0, color: c })))}
      <h3 style="margin-top:18px">Top attacking source IPs</h3>${Charts.hbars(scan.topIps.map(x => ({ label: x.ip, value: x.count })))}</div></div>
  ${scan.traffic ? `<div class="card mb"><h3>Network traffic (flows / minute) <small>spikes found by Parallel Reduce · <span style="color:var(--warn)">●</span> = ground-truth DDoS minute</small></h3>${Charts.area(scan.traffic.perMinute, scan.traffic.spikeMinutes || [], scan.traffic.groundTruthDdosMinutes || [])}
    <div class="kv"><span class="chip bad">anomalous minutes: <b>${(scan.traffic.spikeMinutes || []).join(', ') || 'none'}</b></span><span class="chip">port scanners: <b>${scan.traffic.portScanners.map(p => p.src + ' (' + p.distinctPorts + ' ports)').join(', ') || 'none'}</b></span></div></div>` : ''}
  <div class="grid g2 mb">
    <div class="card"><h3>Dataset overview</h3>${Charts.hbars(Object.entries(prof.perSource).map(([k, v]) => ({ label: k, value: v })))}
      <div class="kv">${Object.entries(prof.httpStatus).map(([k, v]) => `<span class="chip">HTTP ${k}: <b>${v}</b></span>`).join('')}</div>
      <div class="lbl" style="margin-top:10px;color:var(--muted);font-size:12px">Sample records</div><div class="logline">${prof.preview.map(l => esc(l)).join('<br>')}</div></div>
    <div class="card"><h3>Detection accuracy <small>ground truth from labels.csv</small></h3>${ev ? `
      <div class="kv"><span class="chip good">TP <b>${ev.truePositives}</b></span><span class="chip bad">FN <b>${ev.falseNegatives}</b></span><span class="chip warn">FP <b>${ev.falsePositives}</b></span><span class="chip acc">labelled attacks <b>${ev.labelledAttackLines}</b></span></div>
      ${Object.keys(ev.missedByLabel).length ? `<p style="color:var(--muted)">Missed: ${Object.entries(ev.missedByLabel).map(([k, v]) => `<b>${esc(k)}</b> × ${v}`).join(', ')}. ${esc(ev.note)}</p>` : '<p>No misses.</p>'}` : '<div class="empty">Uploaded logs have no ground-truth labels.</div>'}
      ${scan.processes ? `<h3 style="margin-top:14px">Suspicious process names <small>Damerau-Levenshtein</small></h3><div class="kv"><span class="chip good">found <b>${scan.processes.truePositives}</b> / ${scan.processes.uniqueTruth} labelled</span><span class="chip warn">extra flags <b>${scan.processes.falsePositives}</b></span></div>
      <div class="tw" style="max-height:170px"><table><tr><th>observed</th><th>nearest</th><th class="num">dist</th><th>verdict</th></tr>${scan.processes.flagged.slice(0, 40).map(f => `<tr><td class="mono">${esc(f.token)}</td><td class="mono">${esc(f.nearest)}</td><td class="num">${f.distance}</td><td><span class="chip ${f.verdict === 'MASQUERADING' ? 'warn' : 'bad'}">${f.verdict}</span></td></tr>`).join('')}</table></div>` : ''}</div></div>
  <div class="card"><h3>Highest-severity detections <small>click ▶ to watch the algorithms process that exact log line</small></h3>${detTable(scan.detections.slice(0, 8))}</div>`;
  bindViz(view);
};
function detTable(list) {
  if (!list.length) return '<div class="empty">No threats detected.</div>';
  return `<div class="tw"><table><tr><th>Sev</th><th>Source</th><th>Categories</th><th>Matched patterns</th><th>Log line</th><th></th></tr>${list.map(d => `<tr><td><span class="sev ${sevClass(d.severity)}">${d.severity}</span></td><td class="mono">${esc(d.source)}:${d.line}</td><td>${d.categories.map(c => `<span class="chip bad">${esc(c)}</span>`).join(' ')}</td><td class="mono">${d.patterns.map(esc).join('<br>')}</td><td class="logline">${esc(d.text)}</td><td><button class="btn sm" data-viz="${d.id}">▶</button></td></tr>`).join('')}</table></div>`;
}
function bindViz(root) { root.querySelectorAll('[data-viz]').forEach(b => b.onclick = () => { S.pendingLine = +b.dataset.viz; location.hash = 'lab'; }); }

// ------------------------------------------------------------------ live lab
PAGE.lab = async view => {
  const sm = await api(`/api/datasets/${encodeURIComponent(S.dataset)}/samples`); S.samples = sm.samples;
  const groups = {}; S.algos.forEach(a => (groups[a.module] = groups[a.module] || []).push(a));
  const sel = S.labAlgo || 'kmp';
  view.innerHTML = `<div class="lab"><div class="card"><h3>Run an algorithm</h3>
    <div class="field"><label>Algorithm</label><select id="labAlgo">${Object.entries(groups).map(([m, l]) => `<optgroup label="${esc(m)}">${l.map(a => `<option value="${a.id}" ${a.id === sel ? 'selected' : ''}>${esc(a.name)}</option>`).join('')}</optgroup>`).join('')}</select></div>
    <div id="labInfo"></div><div id="labSrc"></div>
    <div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:8px"><button class="btn" id="runViz">▶ Run &amp; visualize</button><button class="btn ghost" id="runFull" title="Run on the complete dataset - metrics only, no trace">Run on full dataset</button></div>
    <p class="hint" style="color:var(--muted);font-size:12px">“Run &amp; visualize” executes the Java algorithm on a real snippet with tracing enabled; the animation is replayed from the recorded trace. “Full dataset” measures the same algorithm at scale without a trace.</p></div>
    <div id="labOut"><div class="card"><div class="empty">Choose an algorithm and press <b>Run &amp; visualize</b>.</div></div></div></div>`;
  const render = () => {
    const a = algo($('#labAlgo').value); S.labAlgo = a.id;
    $('#labInfo').innerHTML = `<div class="card" style="background:var(--panel2);box-shadow:none;margin-bottom:12px"><div class="kv"><span class="chip acc">${esc(a.co)}</span><span class="chip">time <b>${esc(a.time)}</b></span><span class="chip">space <b>${esc(a.space)}</b></span></div><p style="margin:6px 0">${esc(a.description)}</p><p style="margin:0;color:var(--muted)"><b>CyberShield use:</b> ${esc(a.useCase)}</p></div>`;
    const textual = a.kind === 'TEXT_PATTERNS' || a.kind === 'TEXT' || a.kind === 'TOKENS_NAMES';
    $('#labSrc').innerHTML = textual ? `<div class="field"><label>Input source</label><select id="srcSel"><option value="auto">Auto: most severe real attack line</option>${S.samples.map(s => `<option value="${s.id}" ${S.pendingLine === s.id ? 'selected' : ''}>${esc(s.categories.join('+'))} · ${esc(s.source)}:${s.line}</option>`).join('')}<option value="custom">Custom text…</option></select></div>
      <div id="customBox" hidden><div class="field"><label>${a.kind === 'TOKENS_NAMES' ? 'Process name' : 'Text (URL-decoded and lower-cased by the backend)'}</label><textarea id="cText" rows="3"></textarea></div>${a.kind === 'TEXT_PATTERNS' ? '<div class="field"><label>Patterns (one per line, optional)</label><textarea id="cPat" rows="3"></textarea></div>' : ''}</div>` :
      `<p style="color:var(--muted);font-size:12.5px">Input is built by the backend from the dataset (${esc(a.kind.toLowerCase().replace('_', ' '))}).</p>`;
    const ss = $('#srcSel'); if (ss) { ss.onchange = () => $('#customBox').hidden = ss.value !== 'custom'; if (S.pendingLine !== undefined && ss.querySelector(`option[value="${S.pendingLine}"]`)) ss.value = S.pendingLine; }
  };
  $('#labAlgo').onchange = () => { S.pendingLine = undefined; render(); };
  render();
  if (S.pendingLine !== undefined) { const ids = S.pendingLine; const ss = $('#srcSel'); if (ss && !ss.querySelector(`option[value="${ids}"]`)) { /* line not in sample list - use it directly */ const o = document.createElement('option'); o.value = ids; o.textContent = 'Selected detection #' + ids; ss.insertBefore(o, ss.firstChild); ss.value = ids; } $('#labAlgo').value = ['kmp', 'ahocorasick'].includes(S.labAlgo) ? S.labAlgo : 'ahocorasick'; render(); if ($('#srcSel')) $('#srcSel').value = ids; }
  $('#runViz').onclick = () => runLab('visualize');
  $('#runFull').onclick = () => runLab('full');
};
async function runLab(mode) {
  const a = algo($('#labAlgo').value), out = $('#labOut');
  const body = { algorithm: a.id, dataset: S.dataset, mode };
  const ss = $('#srcSel');
  if (ss && mode === 'visualize') {
    if (ss.value === 'custom') { body.text = $('#cText').value; const p = $('#cPat'); if (p) body.patterns = p.value.split('\n').map(x => x.trim()).filter(Boolean); if (!body.text.trim()) return toast('Enter some text first', true); }
    else if (ss.value !== 'auto') body.line = +ss.value;
  }
  if (S.player) { S.player.destroy(); S.player = null; }
  out.innerHTML = `<div class="card">${loading()}</div>`; $('#runViz').disabled = $('#runFull').disabled = true;
  try {
    const r = await api('/api/run', { method: 'POST', body });
    if (mode === 'visualize') S.lastRun = { algorithm: a.id, trace: r.trace || [] };
    out.innerHTML = runCard(a, r);
    if (r.status === 'SUCCESS' && r.trace && r.trace.length) S.player = new Viz.Player($('#player'), r);
    else if (r.status === 'SUCCESS' && mode === 'visualize') $('#player').innerHTML = '<div class="empty">No trace events for this input.</div>';
  } catch (err) { out.innerHTML = `<div class="alert">Run failed: ${esc(err.message)}</div>`; }
  $('#runViz').disabled = $('#runFull').disabled = false;
}
function runCard(a, r) {
  const ok = r.status === 'SUCCESS', ip = r.inputPreview || {};
  const sum = r.summary ? Object.entries(r.summary).filter(([k, v]) => k !== 'checkValue' && k !== 'prefix' && (typeof v !== 'object' || (Array.isArray(v) && v.length <= 12) || (v && !Array.isArray(v) && Object.keys(v).length <= 10))) : [];
  const items = r.items || [], cols = items.length ? Object.keys(items[0]) : [];
  return `<div class="card mb"><h3>${esc(a.name)} <span class="chip ${ok ? 'good' : 'bad'}">${r.status}</span></h3>
    ${ok ? '' : `<div class="alert">${esc(r.error)}</div>`}
    <div class="tiles"><div class="tile"><b>${r.timeMs} ms</b><span>execution time</span></div><div class="tile"><b>${fmt(r.comparisons)}</b><span>comparisons</span></div><div class="tile"><b>${fmt(r.operations)}</b><span>operations</span></div><div class="tile"><b>${mem(r.memoryBytes)}</b><span>memory allocated</span></div><div class="tile"><b>${esc(a.time)}</b><span>time complexity</span></div><div class="tile"><b>${r.trace ? r.trace.length + (r.traceTruncated ? '+' : '') : '–'}</b><span>trace events</span></div></div>
    <p style="margin:10px 0 0"><b>${esc(r.headline)}</b> <span style="color:var(--muted)">· input: ${esc(r.input)}${r.mode === 'full' ? ' (full dataset, no trace)' : ''}</span></p></div>
    ${r.mode === 'visualize' && ok ? `<div class="card mb"><h3>Live visualization <small>driven by the recorded execution trace</small></h3>${ip.patterns && ip.patterns.length ? `<div class="kv">${ip.patterns.map(p => `<span class="chip bad">${esc(p)}</span>`).join('')}</div>` : ''}<div id="player"></div></div>` : ''}
    ${ok ? `<div class="card"><h3>Result details</h3><div class="kv">${sum.map(([k, v]) => `<span class="chip"><span>${esc(k)}</span> <b>${esc(typeof v === 'object' ? JSON.stringify(v) : v)}</b></span>`).join('')}</div>
      ${items.length ? `<div class="tw"><table><tr>${cols.map(c => `<th>${esc(c)}</th>`).join('')}</tr>${items.slice(0, 60).map(it => `<tr>${cols.map(c => `<td class="mono">${esc(Array.isArray(it[c]) ? it[c].join(', ') : it[c])}</td>`).join('')}</tr>`).join('')}</table></div><p style="color:var(--muted);font-size:12px">Showing ${Math.min(60, items.length)} of ${r.itemsTotal} items.</p>` : ''}</div>` : ''}`;
}

// ------------------------------------------------------------------ detections
PAGE.detections = async view => {
  const scan = await getScan(), cats = Object.keys(scan.byCategory);
  let cat = '', minSev = 0;
  const draw = () => {
    const list = scan.detections.filter(d => (!cat || d.categories.includes(cat)) && d.severity >= minSev);
    $('#detBody').innerHTML = detTable(list.slice(0, 400)); $('#detCount').textContent = `${list.length} of ${scan.detectionsTotal} flagged lines`; bindViz($('#detBody'));
  };
  view.innerHTML = `<div class="card"><h3>Detected threats &amp; matched patterns <small id="detCount"></small></h3>
    <div class="ctrl"><label class="sel"><span>Category</span><select id="fCat"><option value="">All</option>${cats.map(c => `<option>${esc(c)}</option>`).join('')}</select></label>
    <label class="sel"><span>Min severity</span><select id="fSev"><option value="0">any</option><option value="5">5+</option><option value="8">8+</option><option value="10">10 (critical)</option></select></label>
    <span class="chip">Aho-Corasick = KMP cross-check: <b>${scan.crossCheck.agree ? 'agree ✓ (' + scan.crossCheck.aho + ')' : 'MISMATCH'}</b></span></div><div id="detBody"></div></div>`;
  $('#fCat').onchange = e => { cat = e.target.value; draw(); }; $('#fSev').onchange = e => { minSev = +e.target.value; draw(); }; draw();
};

// ------------------------------------------------------------------ compare
const PRESETS = { 'String matching': ['kmp', 'z', 'rabinkarp', 'ahocorasick'], 'Suffix structures': ['suffixarray', 'sais', 'kasai', 'suffixautomaton'], 'Max flow': ['fordfulkerson', 'edmondskarp', 'dinic', 'mincut'] };
PAGE.compare = async view => {
  view.innerHTML = `<div class="card mb"><h3>Algorithm-to-algorithm comparison <small>same real input, measured by the Java engine</small></h3>
    <div class="tabs" id="presets">${Object.keys(PRESETS).map((p, i) => `<button data-p="${esc(p)}" class="${i ? '' : 'on'}">${esc(p)}</button>`).join('')}</div>
    <div class="checks" id="cmpChecks">${S.algos.map(a => `<label><input type="checkbox" value="${a.id}"> ${esc(a.name)}</label>`).join('')}</div>
    <div class="ctrl"><button class="btn" id="cmpRun">Compare selected</button><span style="color:var(--muted);font-size:12px">Tip: compare algorithms from the same module - they solve the same problem, so their results must agree.</span></div></div><div id="cmpOut"></div>`;
  const setPreset = name => { document.querySelectorAll('#cmpChecks input').forEach(c => c.checked = PRESETS[name].includes(c.value)); document.querySelectorAll('#presets button').forEach(b => b.classList.toggle('on', b.dataset.p === name)); };
  $('#presets').onclick = e => { const b = e.target.closest('button'); if (b) setPreset(b.dataset.p); };
  setPreset('String matching');
  $('#cmpRun').onclick = async () => {
    const ids = [...document.querySelectorAll('#cmpChecks input:checked')].map(c => c.value), out = $('#cmpOut');
    if (ids.length < 2) return toast('Select at least two algorithms', true);
    out.innerHTML = `<div class="card">${loading('Running ' + ids.length + ' algorithms…')}</div>`;
    try {
      const r = await api('/api/compare', { method: 'POST', body: { algorithms: ids, dataset: S.dataset } }), runs = r.runs;
      const col = k => runs.map(x => ({ label: x.name, value: x[k] }));
      out.innerHTML = `<div class="card mb"><div class="kv"><span class="chip ${r.consistent ? 'good' : 'bad'}">${r.consistent ? '✓ all algorithms return the same answer' : '✗ results differ - investigate'}</span>${r.sameFamily ? '' : '<span class="chip warn">different families compared</span>'}</div></div>
      <div class="grid g2 mb"><div class="card"><h3>Execution time (ms)</h3>${Charts.columns(col('timeMs'), { unit: ' ms' })}</div><div class="card"><h3>Comparisons</h3>${Charts.columns(col('comparisons'))}</div>
      <div class="card"><h3>Operations</h3>${Charts.columns(col('operations'))}</div><div class="card"><h3>Memory allocated</h3>${Charts.columns(runs.map(x => ({ label: x.name, value: x.memoryBytes / 1024 })), { unit: ' KB' })}</div></div>
      <div class="card"><h3>Details &amp; complexity</h3><div class="tw"><table><tr><th>Algorithm</th><th class="num">Time</th><th class="num">Comparisons</th><th class="num">Operations</th><th class="num">Memory</th><th>Time complexity</th><th>Space</th><th>Result</th></tr>${runs.map(x => `<tr><td><b>${esc(x.name)}</b></td><td class="num">${x.timeMs} ms</td><td class="num">${fmt(x.comparisons)}</td><td class="num">${fmt(x.operations)}</td><td class="num">${mem(x.memoryBytes)}</td><td class="mono">${esc(x.timeComplexity)}</td><td class="mono">${esc(x.spaceComplexity)}</td><td>${esc(x.headline)}</td></tr>`).join('')}</table></div></div>`;
    } catch (err) { out.innerHTML = `<div class="alert">${esc(err.message)}</div>`; }
  };
};

// ------------------------------------------------------------------ benchmark
PAGE.benchmark = async view => {
  view.innerHTML = `<div class="card mb"><h3>Scaling benchmark <small>growing slices of the real log corpus, median of N runs</small></h3>
    <div class="ctrl"><label class="sel"><span>Group</span><select id="bGroup"><option value="string-matching">String matching</option><option value="suffix-structures">Suffix structures</option></select></label>
    <label class="sel"><span>Sizes (chars)</span><input type="text" id="bSizes" value="10000, 25000, 50000, 100000, 200000" size="34"></label>
    <label class="sel"><span>Repeats</span><input type="number" id="bRep" value="3" min="1" max="9" style="width:60px"></label><button class="btn" id="bRun">Run benchmark</button></div></div><div id="bOut"></div>`;
  $('#bRun').onclick = async () => {
    const out = $('#bOut'), sizes = $('#bSizes').value.split(/[ ,]+/).map(Number).filter(n => n > 0);
    out.innerHTML = `<div class="card">${loading('Benchmarking - this can take a few seconds…')}</div>`; $('#bRun').disabled = true;
    try {
      const r = await api('/api/benchmark', { method: 'POST', body: { group: $('#bGroup').value, sizes, repeats: +$('#bRep').value, dataset: S.dataset } });
      const ids = Object.keys(r.series), name = id => (algo(id) || {}).name || id;
      const mk = key => ids.map(id => ({ name: name(id), points: r.series[id].map(p => [p.size, p[key]]) }));
      out.innerHTML = `<div class="grid g2 mb"><div class="card"><h3>Time vs input size (ms)</h3>${Charts.line(mk('timeMs'), { unit: ' ms' })}</div><div class="card"><h3>Comparisons vs input size</h3>${Charts.line(mk('comparisons'))}</div>
      <div class="card"><h3>Operations vs input size</h3>${Charts.line(mk('operations'))}</div><div class="card"><h3>Memory allocated (KB)</h3>${Charts.line(ids.map(id => ({ name: name(id), points: r.series[id].map(p => [p.size, p.memoryBytes / 1024]) })), { unit: ' KB' })}</div></div>
      <div class="card"><h3>Raw numbers</h3><div class="tw"><table><tr><th>Algorithm</th><th class="num">Size</th><th class="num">Time (ms)</th><th class="num">Comparisons</th><th class="num">Operations</th><th class="num">Memory</th></tr>${ids.flatMap(id => r.series[id].map(p => `<tr><td>${esc(name(id))}</td><td class="num">${p.size}</td><td class="num">${p.timeMs}</td><td class="num">${fmt(p.comparisons)}</td><td class="num">${fmt(p.operations)}</td><td class="num">${mem(p.memoryBytes)}</td></tr>`)).join('')}</table></div></div>`;
    } catch (err) { out.innerHTML = `<div class="alert">${esc(err.message)}</div>`; }
    $('#bRun').disabled = false;
  };
};

// ------------------------------------------------------------------ history
PAGE.history = async view => {
  const h = (await api('/api/history?limit=400')).runs;
  const by = {}; h.slice().reverse().forEach(r => { if (r.status === 'SUCCESS' && r.dataset === S.dataset) (by[r.algorithm] = by[r.algorithm] || []).push(r.timeMs); });
  view.innerHTML = `<div class="card mb"><h3>Execution history <small>${h.length} runs · persisted by the backend in data/history.jsonl</small><button class="btn ghost sm" id="hClear">Clear</button></h3>
    ${h.length ? `<div class="kv">${Object.entries(by).map(([id, v]) => `<span class="chip">${esc((algo(id) || {}).name || id)} ${Charts.spark(v, 70, 20)} <b>${(v.reduce((a, b) => a + b, 0) / v.length).toFixed(2)} ms avg</b></span>`).join('')}</div>` : ''}
    ${h.length ? `<div class="tw" style="max-height:560px"><table><tr><th>When</th><th>Algorithm</th><th>Dataset</th><th>Input</th><th>Status</th><th class="num">Time</th><th class="num">Comparisons</th><th class="num">Operations</th><th class="num">Memory</th><th>Result</th></tr>${h.map(r => `<tr><td class="mono">${new Date(r.timestamp).toLocaleTimeString()}</td><td>${esc(r.name)}</td><td>${esc(r.dataset)}</td><td>${esc(r.input)}</td><td><span class="chip ${r.status === 'SUCCESS' ? 'good' : 'bad'}">${r.status}</span></td><td class="num">${r.timeMs} ms</td><td class="num">${fmt(r.comparisons)}</td><td class="num">${fmt(r.operations)}</td><td class="num">${mem(r.memoryBytes)}</td><td>${esc(r.headline)}</td></tr>`).join('')}</table></div>` : '<div class="empty">No runs yet.</div>'}</div>`;
  $('#hClear').onclick = async () => { await api('/api/history', { method: 'DELETE' }); toast('History cleared'); route(); };
};

// ------------------------------------------------------------------ algorithm catalogue
PAGE.algorithms = async view => {
  const st = (await api('/api/status')).lastRuns, groups = {}; S.algos.forEach(a => (groups[a.module] = groups[a.module] || []).push(a));
  view.innerHTML = Object.entries(groups).map(([m, l]) => `<h3 style="margin:18px 0 10px">${esc(m)}</h3><div class="grid g3">${l.map(a => { const r = st[a.id]; return `<div class="card algo-card"><h4>${esc(a.name)} <span class="chip acc">${esc(a.co)}</span></h4>
    <div class="kv"><span class="chip">time <b>${esc(a.time)}</b></span><span class="chip">space <b>${esc(a.space)}</b></span></div><p>${esc(a.description)}</p><p><b>Use:</b> ${esc(a.useCase)}</p>
    <div class="kv">${r ? `<span class="chip ${r.status === 'SUCCESS' ? 'good' : 'bad'}">${r.status} · ${r.timeMs} ms · ${fmt(r.comparisons)} cmp</span>` : '<span class="chip">not run yet</span>'}</div>
    <button class="btn sm" data-open="${a.id}">Open in Live Lab</button></div>`; }).join('')}</div>`).join('');
  view.querySelectorAll('[data-open]').forEach(b => b.onclick = () => { S.labAlgo = b.dataset.open; location.hash = 'lab'; });
};

// ------------------------------------------------------------------ boot
(async function boot() {
  try {
    await loadDatasets();
    S.algos = (await api('/api/algorithms')).algorithms;
    pollStatus(); setInterval(pollStatus, 4000);
    route();
  } catch (err) { $('#view').innerHTML = `<div class="alert"><b>Cannot reach the CyberShield backend.</b> ${esc(err.message)}<br>Start it with <code>./start.sh</code> (or <code>start.bat</code> on Windows).</div>`; }
})();
