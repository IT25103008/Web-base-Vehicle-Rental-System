// ============================================================
// Vehicles: the whole rentable fleet as a catalogue - browsable by
// body, powertrain, seats, branch, year and price, with or without
// dates. It reads the public catalogue: every car a customer could
// actually book. Cars in the workshop or without insurance cover are,
// correctly, never in it.
// ============================================================
import { api } from '../api.js';
import { getBranches } from '../store.js';
import {
  $, $$, esc, icon, money, number, addDays, rentalDays, humanize, carArt, vehicleVisual, empty, skeletons, debounce,
} from '../ui.js';
import { carActions } from '../prefs.js';
import { vehicleCard, footer, OFF_ROAD } from './public.js';
import { datebar, bindDatebar, rememberDates } from './extras.js';

const WINDOWS = [[1, 2], [7, 8], [14, 15], [30, 31], [60, 61]];
let fleetMemo = null;

/**
 * The public catalogue (GET /api/vehicles/catalogue): every car a customer
 * could book at some point. Against an older server without that endpoint,
 * fall back to the union of several date windows.
 */
function loadFleet() {
  if (!fleetMemo) {
    fleetMemo = api.vehicles.catalogue().catch(() => Promise.all(
      WINDOWS.map(([a, b]) => api.vehicles.search(addDays(a), addDays(b)).catch(() => [])),
    ).then((lists) => {
      const byId = new Map();
      lists.flat().forEach((v) => byId.set(v.vehicleId, v));
      return [...byId.values()];
    }));
    // Fresh for a few minutes, then asked again.
    setTimeout(() => { fleetMemo = null; }, 5 * 60 * 1000);
  }
  return fleetMemo;
}

const BODY_PLURAL = { CAR: 'Cars', SUV: 'SUVs', VAN: 'Vans', BUS: 'Buses', LUXURY: 'Luxury', PICKUP: 'Pickups', TRUCK: 'Trucks', BIKE: 'Bikes', MOTORBIKE: 'Motorbikes' };
const bodyName = (c) => BODY_PLURAL[String(c || '').toUpperCase()] || humanize(c || 'Other');
const SORTS = [
  ['body', 'By body'], ['price-asc', 'Price, low to high'], ['price-desc', 'Price, high to low'],
  ['newest', 'Newest first'], ['seats', 'Most seats'], ['name', 'Name, A–Z'],
];
const list = (s) => (s ? String(s).split(',').filter(Boolean) : []);
const validDate = (v) => (/^\d{4}-\d{2}-\d{2}$/.test(v || '') ? v : null);

export async function renderVehicles({ el, query }) {
  const f = {
    q: query.q || '',
    body: list(query.body), fuel: list(query.fuel), branch: list(query.branch).map(Number),
    seats: Number(query.seats) || 0, max: Number(query.max) || null, year: Number(query.year) || null,
    sort: SORTS.some(([k]) => k === query.sort) ? query.sort : 'body',
    view: query.view === 'list' ? 'list' : 'grid',
  };
  let pickup = validDate(query.pickup);
  let ret = validDate(query.return);
  if (pickup && (!ret || ret <= pickup)) ret = addDays(1, new Date(`${pickup}T00:00:00`));
  let free = null;

  el.innerHTML = `
    <header class="ph vx-head">
      <div class="ph__copy">
        <p class="eyebrow rule">The fleet</p>
        <h1 class="display ph__title">Every car we rent.</h1>
        <p class="lede ph__lede" id="vx-sum">Loading the fleet…</p>
      </div>
      <div class="ph__side">
        ${datebar(pickup || addDays(1), ret || addDays(3), 'Check dates')}
        <p class="vx-dates" id="vx-dates"></p>
      </div>
    </header>
    <div class="vx-shelf" id="vx-shelf"></div>
    <div class="vx">
      <aside class="vf" id="vf" aria-label="Filters">
        <div class="vf__head"><b>Filters</b>
          <button class="btn btn--sm btn--text" type="button" data-vx-clear>Clear all</button>
          <button class="icon-btn vf__close" type="button" data-vf-close aria-label="Close filters">${icon('close', 18)}</button>
        </div>
        <div id="vf-body">${skeletons(3, 'skeleton-grid')}</div>
      </aside>
      <div class="vf-scrim" data-vf-close></div>
      <section class="vx__main">
        <div class="vt">
          <span class="vt__count" id="vt-count" aria-live="polite"></span>
          <div class="vt__chips" id="vt-chips"></div>
          <div class="vt__end">
            <button class="btn btn--sm btn--tonal vt__filters" type="button" data-vf-open>${icon('filter', 15)} Filters <span id="vt-n"></span></button>
            <label class="vt__sort">${icon('swap', 14)}<select id="vt-sort" aria-label="Order">${SORTS.map(([k, l]) => `<option value="${k}"${f.sort === k ? ' selected' : ''}>${l}</option>`).join('')}</select></label>
            <div class="seg seg--sm" role="radiogroup" aria-label="View">
              <button type="button" role="radio" data-view="grid" class="${f.view === 'grid' ? 'is-active' : ''}" aria-checked="${f.view === 'grid'}" aria-label="Grid">${icon('grid', 15)}</button>
              <button type="button" role="radio" data-view="list" class="${f.view === 'list' ? 'is-active' : ''}" aria-checked="${f.view === 'list'}" aria-label="List">${icon('menu', 15)}</button>
            </div>
          </div>
        </div>
        <div id="vx-results">${skeletons(3, 'skeleton-grid skeleton-grid--cards')}</div>
      </section>
    </div>
    ${footer()}`;

  const [fleet, branches] = await Promise.all([loadFleet(), getBranches().catch(() => [])]);
  const byId = new Map(branches.map((b) => [b.branchId, b]));
  const prices = fleet.map((v) => Number(v.rentalPricePerDay)).filter((n) => n > 0);
  const lo = prices.length ? Math.floor(Math.min(...prices) / 500) * 500 : 0;
  const hi = prices.length ? Math.ceil(Math.max(...prices) / 500) * 500 : 0;
  const years = fleet.map((v) => Number(v.manufactureYear)).filter(Boolean);
  const yLo = years.length ? Math.min(...years) : 0;
  const yHi = years.length ? Math.max(...years) : 0;
  if (!f.max || f.max > hi || f.max < lo) f.max = hi;
  if (!f.year || f.year < yLo || f.year > yHi) f.year = yLo;

  // -- matching: every test can be switched off, so each facet can count
  //    what it *would* show with the others still applied.
  function pass(v, skip = '') {
    const q = f.q.trim().toLowerCase();
    if (skip !== 'q' && q && !`${v.model} ${v.category} ${v.fuelType} ${byId.get(v.branchId)?.name || ''} ${byId.get(v.branchId)?.city || ''}`.toLowerCase().includes(q)) return false;
    if (skip !== 'body' && f.body.length && !f.body.includes(v.category)) return false;
    if (skip !== 'fuel' && f.fuel.length && !f.fuel.includes(v.fuelType)) return false;
    if (skip !== 'branch' && f.branch.length && !f.branch.includes(v.branchId)) return false;
    if (skip !== 'seats' && f.seats && (Number(v.passengerCapacity) || 0) < f.seats) return false;
    if (skip !== 'max' && f.max && Number(v.rentalPricePerDay) > f.max) return false;
    if (skip !== 'year' && f.year && (Number(v.manufactureYear) || 0) < f.year) return false;
    return true;
  }
  const count = (skip, test) => fleet.filter((v) => pass(v, skip) && test(v)).length;
  const activeCount = () => (f.q.trim() ? 1 : 0) + f.body.length + f.fuel.length + f.branch.length
    + (f.seats ? 1 : 0) + (f.max < hi ? 1 : 0) + (f.year > yLo ? 1 : 0);

  function syncUrl() {
    const qs = new URLSearchParams();
    if (f.q.trim()) qs.set('q', f.q.trim());
    if (f.body.length) qs.set('body', f.body.join(','));
    if (f.fuel.length) qs.set('fuel', f.fuel.join(','));
    if (f.branch.length) qs.set('branch', f.branch.join(','));
    if (f.seats) qs.set('seats', f.seats);
    if (f.max < hi) qs.set('max', f.max);
    if (f.year > yLo) qs.set('year', f.year);
    if (f.sort !== 'body') qs.set('sort', f.sort);
    if (f.view !== 'grid') qs.set('view', f.view);
    if (pickup) { qs.set('pickup', pickup); qs.set('return', ret); }
    const s = qs.toString();
    history.replaceState(null, '', `#/vehicles${s ? `?${s}` : ''}`);
  }

  // -- the shelf: one tile per body style, a quick way in
  function paintShelf() {
    const bodies = [...new Set(fleet.map((v) => v.category).filter(Boolean))];
    if (bodies.length < 2) { $('#vx-shelf', el).innerHTML = ''; return; }
    $('#vx-shelf', el).innerHTML = bodies.map((c) => {
      const cars = fleet.filter((v) => v.category === c);
      const from = Math.min(...cars.map((v) => Number(v.rentalPricePerDay)));
      const on = f.body.includes(c);
      return `<button type="button" class="vx-tile${on ? ' is-on' : ''}" data-shelf="${esc(c)}" aria-pressed="${on}">
        <span class="vx-tile__art">${carArt({ category: c, plateNumber: `shelf-${c}` }, { paint: '#d7d7dc' })}</span>
        <b>${esc(bodyName(c))}</b>
        <small>${cars.length} car${cars.length === 1 ? '' : 's'} · from ${money(from)}</small>
      </button>`;
    }).join('');
  }

  // -- the filter panel
  function paintFilters() {
    const facet = (key, values, label = (x) => x) => values.map((x) => {
      const n = count(key, (v) => (key === 'branch' ? v.branchId === x : (key === 'body' ? v.category : v.fuelType) === x));
      const on = f[key].includes(x);
      return `<button type="button" class="vf-chip${on ? ' is-on' : ''}" data-facet="${key}" data-value="${esc(x)}" aria-pressed="${on}"${!n && !on ? ' disabled' : ''}>
        ${esc(label(x))}<em>${n}</em></button>`;
    }).join('');
    const bodies = [...new Set(fleet.map((v) => v.category).filter(Boolean))].sort();
    const fuels = [...new Set(fleet.map((v) => v.fuelType).filter(Boolean))].sort();
    const brs = [...new Set(fleet.map((v) => v.branchId))].filter((id) => byId.has(id));
    const seatOpts = [0, 2, 4, 5, 7].filter((n) => !n || fleet.some((v) => (Number(v.passengerCapacity) || 0) >= n));
    const pct = (x, a, b) => (b > a ? (((x - a) / (b - a)) * 100).toFixed(1) : 100);
    $('#vf-body', el).innerHTML = `
      <label class="filters__search vf__search">${icon('search', 16)}
        <input type="search" id="vf-q" placeholder="Model, branch or town" value="${esc(f.q)}" autocomplete="off" aria-label="Search the fleet">
      </label>
      ${bodies.length > 1 ? `<fieldset class="vf__group"><legend>Body</legend><div class="vf__chips">${facet('body', bodies, bodyName)}</div></fieldset>` : ''}
      ${fuels.length > 1 ? `<fieldset class="vf__group"><legend>Powertrain</legend><div class="vf__chips">${facet('fuel', fuels)}</div></fieldset>` : ''}
      ${seatOpts.length > 1 ? `<fieldset class="vf__group"><legend>Seats</legend><div class="seg seg--sm vf__seg">${seatOpts.map((n) => `
        <button type="button" data-seats="${n}" class="${f.seats === n ? 'is-active' : ''}" aria-pressed="${f.seats === n}">${n ? `${n}+` : 'Any'}</button>`).join('')}</div></fieldset>` : ''}
      ${hi > lo ? `<fieldset class="vf__group"><legend>Daily rate <b id="vf-max-out">up to ${money(f.max)}</b></legend>
        <input type="range" id="vf-max" min="${lo}" max="${hi}" step="500" value="${f.max}" style="--p:${pct(f.max, lo, hi)}%" aria-label="Highest daily rate">
        <div class="vf__scale"><span>${money(lo)}</span><span>${money(hi)}</span></div></fieldset>` : ''}
      ${yHi > yLo ? `<fieldset class="vf__group"><legend>Model year <b id="vf-year-out">${f.year > yLo ? `${f.year} or newer` : 'any'}</b></legend>
        <input type="range" id="vf-year" min="${yLo}" max="${yHi}" step="1" value="${f.year}" style="--p:${pct(f.year, yLo, yHi)}%" aria-label="Oldest model year">
        <div class="vf__scale"><span>${yLo}</span><span>${yHi}</span></div></fieldset>` : ''}
      ${brs.length > 1 ? `<fieldset class="vf__group"><legend>Collected from</legend><div class="vf__chips">${facet('branch', brs, (id) => byId.get(id)?.name || `Branch ${id}`)}</div></fieldset>` : ''}
      <button class="btn btn--block vf__done" type="button" data-vf-close>Show <span id="vf-done-n"></span> cars</button>`;
  }

  // -- the results
  function card(v, i, total) {
    const nights = pickup ? rentalDays(pickup, ret) : 0;
    const qs = pickup ? new URLSearchParams({ pickup, return: ret }) : '';
    const avail = OFF_ROAD.includes(v.status) ? 'booked' : !free ? 'unknown' : free.has(v.vehicleId) ? 'free' : 'booked';
    if (f.view === 'grid') return vehicleCard(v, byId.get(v.branchId), nights, qs, i, total, false, { avail });
    const b = byId.get(v.branchId);
    const href = `#/vehicle/${v.vehicleId}${qs ? `?${qs}` : ''}`;
    return `<article class="vrow" style="animation-delay:${Math.min(i, 12) * 40}ms">
      <a class="vrow__art" href="${href}" tabindex="-1" aria-hidden="true">${vehicleVisual(v)}</a>
      <div class="vrow__main">
        <p class="eyebrow">${esc(v.category || 'Vehicle')}${v.manufactureYear ? ` · ${esc(v.manufactureYear)}` : ''}${b ? ` · ${esc(b.name)}` : ''}</p>
        <h3><a href="${href}">${esc(v.model)}</a></h3>
        <ul class="vrow__spec">
          <li>${icon('seat', 14)} ${esc(v.passengerCapacity ?? '—')} seats</li>
          <li>${icon('fuel', 14)} ${esc(v.fuelType || '—')}</li>
          <li>${icon('gauge', 14)} ${number(v.mileage)} km</li>
        </ul>
      </div>
      <div class="vrow__price">
        <b>${money(v.rentalPricePerDay)}</b><span>per day</span>
        ${avail === 'free' ? '<span class="status status--green">Free</span>' : avail === 'booked' ? '<span class="status status--red">Booked</span>' : ''}
        ${nights ? `<small>${money(Number(v.rentalPricePerDay) * nights)} for ${nights} night${nights === 1 ? '' : 's'}</small>` : ''}
      </div>
      <div class="vrow__acts">${carActions(v)}<a class="btn btn--sm" href="${href}">View ${icon('arrowRight', 14)}</a></div>
    </article>`;
  }

  function order(cars) {
    const p = (v) => Number(v.rentalPricePerDay) || 0;
    const out = [...cars];
    if (f.sort === 'price-asc' || f.sort === 'body') out.sort((a, b) => p(a) - p(b));
    if (f.sort === 'price-desc') out.sort((a, b) => p(b) - p(a));
    if (f.sort === 'newest') out.sort((a, b) => (b.manufactureYear || 0) - (a.manufactureYear || 0));
    if (f.sort === 'seats') out.sort((a, b) => (b.passengerCapacity || 0) - (a.passengerCapacity || 0));
    if (f.sort === 'name') out.sort((a, b) => String(a.model).localeCompare(String(b.model)));
    if (free && pickup) out.sort((a, b) => Number(free.has(b.vehicleId)) - Number(free.has(a.vehicleId)));
    return out;
  }

  function paintResults() {
    const cars = order(fleet.filter((v) => pass(v)));
    const n = activeCount();
    $('#vt-count', el).innerHTML = `<b>${cars.length}</b> of ${fleet.length} car${fleet.length === 1 ? '' : 's'}`;
    $('#vt-n', el).textContent = n ? `(${n})` : '';
    const done = $('#vf-done-n', el); if (done) done.textContent = cars.length;

    const chips = [
      f.q.trim() ? ['q', '', `“${f.q.trim()}”`] : null,
      ...f.body.map((x) => ['body', x, bodyName(x)]),
      ...f.fuel.map((x) => ['fuel', x, x]),
      f.seats ? ['seats', '', `${f.seats}+ seats`] : null,
      f.max < hi ? ['max', '', `Up to ${money(f.max)}`] : null,
      f.year > yLo ? ['year', '', `${f.year} or newer`] : null,
      ...f.branch.map((x) => ['branch', x, byId.get(x)?.name || `Branch ${x}`]),
    ].filter(Boolean);
    $('#vt-chips', el).innerHTML = chips.map(([k, val, label]) => `<button type="button" class="vt-chip" data-unset="${k}" data-value="${esc(val)}">${esc(label)} ${icon('close', 12)}</button>`).join('')
      + (chips.length > 1 ? '<button type="button" class="vt-chip vt-chip--clear" data-vx-clear>Clear all</button>' : '');

    const host = $('#vx-results', el);
    if (!fleet.length) {
      host.innerHTML = empty({ icon: 'car', title: 'No cars to show right now', text: 'The fleet is fully committed for the next two months. Check back soon.' });
      return;
    }
    if (!cars.length) {
      host.innerHTML = empty({
        icon: 'filter', title: 'Nothing matches all of that',
        text: 'Loosen a filter or two — the chips above show what is switched on.',
        action: '<button class="btn btn--tonal" type="button" data-vx-clear>Clear all filters</button>',
      });
      return;
    }
    const wrap = (items) => (f.view === 'grid'
      ? `<div class="vehicle-grid">${items}</div>` : `<div class="vrow-list">${items}</div>`);
    if (f.sort === 'body') {
      const groups = new Map();
      cars.forEach((v) => { const k = v.category || 'OTHER'; if (!groups.has(k)) groups.set(k, []); groups.get(k).push(v); });
      host.innerHTML = [...groups.entries()].map(([k, vs]) => `
        <section class="vgroup">
          <header class="vgroup__head"><h2>${esc(bodyName(k))}</h2><span>${vs.length}</span><i></i><small>from ${money(Math.min(...vs.map((v) => Number(v.rentalPricePerDay))))} a day</small></header>
          ${wrap(vs.map((v, i) => card(v, i, vs.length)).join(''))}
        </section>`).join('');
    } else {
      host.innerHTML = wrap(cars.map((v, i) => card(v, i, cars.length)).join(''));
    }
  }

  function paintSummary() {
    const bodies = new Set(fleet.map((v) => v.category).filter(Boolean)).size;
    const from = prices.length ? Math.min(...prices) : 0;
    $('#vx-sum', el).textContent = fleet.length
      ? `${fleet.length} cars across ${bodies} body style${bodies === 1 ? '' : 's'}, from ${money(from)} a day. Browse them all, or add dates to see what is free.`
      : 'No cars are open for booking at the moment.';
    const d = $('#vx-dates', el);
    if (pickup) {
      const n = free ? fleet.filter((v) => free.has(v.vehicleId)).length : null;
      d.innerHTML = `${icon('calendar', 14)} ${n !== null ? `<b>${n}</b> free for these dates` : 'Checking…'} <button type="button" class="btn btn--sm btn--text" data-anytime>Any time</button>`;
    } else {
      d.innerHTML = `${icon('info', 14)} Showing the whole fleet — add dates to see what is free.`;
    }
  }

  function paintAll() { paintShelf(); paintFilters(); paintResults(); paintSummary(); syncUrl(); }
  function refresh() { paintShelf(); paintFilters(); paintResults(); syncUrl(); }

  async function checkDates() {
    free = null;
    paintSummary(); paintResults();
    if (!pickup) return;
    free = await api.vehicles.search(pickup, ret).then((l) => new Set(l.map((v) => v.vehicleId))).catch(() => null);
    paintSummary(); paintResults(); syncUrl();
  }

  // -- wiring
  bindDatebar(el, (p, r) => { pickup = p; ret = r; rememberDates(p, r); checkDates(); });
  const typed = debounce(() => { paintResults(); paintShelf(); syncUrl(); }, 140);
  el.addEventListener('input', (e) => {
    if (e.target.id === 'vf-q') { f.q = e.target.value; typed(); }
    if (e.target.id === 'vf-max') {
      f.max = Number(e.target.value);
      $('#vf-max-out', el).textContent = `up to ${money(f.max)}`;
      e.target.style.setProperty('--p', `${(((f.max - lo) / (hi - lo)) * 100).toFixed(1)}%`);
      paintResults(); syncUrl();
    }
    if (e.target.id === 'vf-year') {
      f.year = Number(e.target.value);
      $('#vf-year-out', el).textContent = f.year > yLo ? `${f.year} or newer` : 'any';
      e.target.style.setProperty('--p', `${(((f.year - yLo) / (yHi - yLo)) * 100).toFixed(1)}%`);
      paintResults(); syncUrl();
    }
  });
  $('#vt-sort', el).addEventListener('change', (e) => { f.sort = e.target.value; paintResults(); syncUrl(); });

  const toggle = (arr, x) => (arr.includes(x) ? arr.filter((y) => y !== x) : [...arr, x]);
  el.addEventListener('click', (e) => {
    const t = e.target;
    const chip = t.closest('[data-facet]');
    if (chip) {
      const key = chip.dataset.facet;
      const val = key === 'branch' ? Number(chip.dataset.value) : chip.dataset.value;
      f[key] = toggle(f[key], val);
      refresh();
      return;
    }
    const shelf = t.closest('[data-shelf]');
    if (shelf) { f.body = toggle(f.body, shelf.dataset.shelf); refresh(); $('.vx__main', el).scrollIntoView({ behavior: 'smooth', block: 'start' }); return; }
    const seats = t.closest('[data-seats]');
    if (seats) { f.seats = Number(seats.dataset.seats); refresh(); return; }
    const view = t.closest('[data-view]');
    if (view) {
      f.view = view.dataset.view;
      $$('[data-view]', el).forEach((b) => { b.classList.toggle('is-active', b === view); b.setAttribute('aria-checked', String(b === view)); });
      paintResults(); syncUrl();
      return;
    }
    const unset = t.closest('[data-unset]');
    if (unset) {
      const k = unset.dataset.unset;
      const val = unset.dataset.value;
      if (k === 'q') f.q = '';
      if (k === 'seats') f.seats = 0;
      if (k === 'max') f.max = hi;
      if (k === 'year') f.year = yLo;
      if (k === 'body' || k === 'fuel') f[k] = f[k].filter((x) => x !== val);
      if (k === 'branch') f.branch = f.branch.filter((x) => x !== Number(val));
      refresh();
      return;
    }
    if (t.closest('[data-vx-clear]')) {
      Object.assign(f, { q: '', body: [], fuel: [], branch: [], seats: 0, max: hi, year: yLo });
      refresh();
      return;
    }
    if (t.closest('[data-anytime]')) { pickup = null; ret = null; free = null; paintSummary(); paintResults(); syncUrl(); return; }
    if (t.closest('[data-vf-open]')) { $('#vf', el).classList.add('is-open'); document.documentElement.classList.add('has-sheet'); return; }
    if (t.closest('[data-vf-close]')) { $('#vf', el).classList.remove('is-open'); document.documentElement.classList.remove('has-sheet'); }
  });
  window.addEventListener('hashchange', () => document.documentElement.classList.remove('has-sheet'), { once: true });

  paintAll();
  if (pickup) checkDates();
}
