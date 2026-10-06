// ============================================================
// The landing page's interactive pieces, kept apart from public.js so
// the page itself stays readable:
//
//   quick dates + live count   the search capsule answers as you type
//   trip composer              four questions -> the best free car
//   availability pulse         eight weeks of the real calendar, drag to pick
//   torch walkaround           the handover band, lit where you point
//   section rail               where you are on a long page
//   hero light, magnetic CTAs, lit steps, FAQ search
//
// Everything reads the live fleet. Nothing here stands in for data that
// does not exist - if the server has no answer, the piece says so.
// ============================================================
import { api } from '../api.js';
import {
  $, $$, esc, icon, money, fmtDate, isoDate, addDays, rentalDays, vehicleVisual, debounce,
} from '../ui.js';

const STILL = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const FINE = () => window.matchMedia('(hover: hover) and (pointer: fine)').matches;
const dayOf = (iso) => new Date(`${iso}T00:00:00`);
const plural = (n, word, many = `${word}s`) => `${n} ${n === 1 ? word : many}`;

/** Runs `fn` for every item, at most `limit` at a time. */
async function pool(items, limit, fn) {
  const out = new Array(items.length);
  let next = 0;
  const worker = async () => {
    while (next < items.length) {
      const i = next++;
      out[i] = await fn(items[i], i);
    }
  };
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
  return out;
}

// ==================================================================
// 1. Quick dates and the live count under the search capsule
// ==================================================================
const QUICK = [
  ['tomorrow', 'Tomorrow', '2 nights'],
  ['weekend', 'This weekend', 'Sat → Mon'],
  ['week', 'A full week', '7 nights'],
  ['fortnight', 'Two weeks', '14 nights'],
];

function quickDates(key) {
  const now = new Date();
  const tomorrow = addDays(1, now);
  if (key === 'tomorrow') return [tomorrow, addDays(3, now)];
  if (key === 'week') return [tomorrow, addDays(8, now)];
  if (key === 'fortnight') return [tomorrow, addDays(15, now)];
  // The coming Saturday (today, if it is Saturday) to the Monday after.
  const toSat = (6 - now.getDay() + 7) % 7;
  const sat = addDays(toSat, now);
  return [sat, addDays(2, dayOf(sat))];
}

export function quickRow() {
  return `<div class="lp-quick" id="quick" role="group" aria-label="Quick dates">
    <span class="lp-quick__label">${icon('bolt', 13)} Quick pick</span>
    ${QUICK.map(([key, title, sub]) => `<button type="button" class="lp-quick__chip" data-quick="${key}">
      <b>${title}</b><small>${sub}</small></button>`).join('')}
    <span class="lp-quick__live" id="quick-live" aria-live="polite"><i aria-hidden="true"></i><span>Checking the fleet…</span></span>
  </div>`;
}

/**
 * Wire the chips and the live readout to the search form. The readout asks
 * the server how many cars are free for whatever is in the form right now,
 * before anyone presses Search.
 */
export function mountSearchExtras(root, form, { onPick }) {
  const live = $('#quick-live', root);
  const text = $('span', live);
  const cache = new Map();
  let seq = 0;

  const markChip = () => {
    const p = form.elements.pickup.value;
    const r = form.elements.return.value;
    $$('[data-quick]', root).forEach((b) => {
      const [qp, qr] = quickDates(b.dataset.quick);
      b.classList.toggle('is-on', qp === p && qr === r);
    });
  };

  const check = debounce(async () => {
    const p = form.elements.pickup.value;
    const r = form.elements.return.value;
    const b = form.elements.branch.value;
    markChip();
    if (!p || !r || r <= p) { text.textContent = 'Choose a return after the pick-up'; live.dataset.state = 'off'; return; }
    const key = `${p}|${r}|${b}`;
    const mine = ++seq;
    live.dataset.state = 'busy';
    try {
      if (!cache.has(key)) cache.set(key, api.vehicles.search(p, r, b || undefined));
      const list = await cache.get(key);
      if (mine !== seq || !live.isConnected) return;
      const n = rentalDays(p, r);
      const from = list.length ? Math.min(...list.map((v) => Number(v.rentalPricePerDay) || Infinity)) : 0;
      live.dataset.state = list.length ? 'on' : 'off';
      text.innerHTML = list.length
        ? `<b>${list.length}</b> ${list.length === 1 ? 'car' : 'cars'} free · ${plural(n, 'night')}${from ? ` · from <b>${esc(money(from * n))}</b>` : ''}`
        : `Nothing free for those ${plural(n, 'night')} — try the pulse below`;
      live.classList.remove('is-tick');
      void live.offsetWidth;
      live.classList.add('is-tick');
    } catch {
      cache.delete(key);
      if (mine === seq) { live.dataset.state = 'off'; text.textContent = 'Could not reach the fleet just now'; }
    }
  }, 260);

  root.addEventListener('click', (e) => {
    const chip = e.target.closest('[data-quick]');
    if (!chip) return;
    const [p, r] = quickDates(chip.dataset.quick);
    onPick(p, r);
    check();
  });
  form.addEventListener('change', check);
  check();
  return { check };
}

// ==================================================================
// 2. The trip composer
// ==================================================================
const MOODS = {
  city: {
    label: 'City', caption: 'Errands, meetings, easy parking', icon: 'building', price: 'low',
    cat: { CAR: 1, LUXURY: 0.55, SUV: 0.5, PICKUP: 0.25, VAN: 0.2, BUS: 0 },
    fuel: { Electric: 1, Hybrid: 1, Petrol: 0.6, Diesel: 0.45 },
  },
  coast: {
    label: 'Coast road', caption: 'Long days on open roads', icon: 'route', price: 'mid',
    cat: { LUXURY: 1, SUV: 0.85, CAR: 0.75, PICKUP: 0.45, VAN: 0.3, BUS: 0.1 },
    fuel: { Petrol: 1, Hybrid: 0.85, Diesel: 0.75, Electric: 0.6 },
  },
  family: {
    label: 'Family', caption: 'Room for people and bags', icon: 'users', price: 'mid',
    cat: { SUV: 1, VAN: 0.95, BUS: 0.7, CAR: 0.55, LUXURY: 0.5, PICKUP: 0.5 },
    fuel: { Hybrid: 1, Diesel: 0.9, Petrol: 0.75, Electric: 0.7 },
  },
  occasion: {
    label: 'Occasion', caption: 'Weddings, arrivals, a statement', icon: 'sparkle', price: 'high',
    cat: { LUXURY: 1, CAR: 0.6, SUV: 0.55, VAN: 0.15, PICKUP: 0.1, BUS: 0.05 },
    fuel: { Petrol: 1, Hybrid: 0.8, Electric: 0.8, Diesel: 0.5 },
  },
};
const PAX_MAX = 12;
const RING = 2 * Math.PI * 44;

export function composerSection(n) {
  return `<section class="section" id="composer-section" data-rail-label="Composer">
    <div class="lp-comp" data-reveal>
      <span class="lp-comp__glow" aria-hidden="true"></span>
      <div class="lp-comp__grid">
        <div class="lp-comp__controls">
          <header class="lp-comp__head">
            <p class="eyebrow"><span class="eyebrow__n">${n}</span> Trip composer</p>
            <h2>Tell us the trip.<br><em>We'll find the car.</em></h2>
            <p>Four answers. The match comes from the cars that are actually free for your dates, scored as you move.</p>
          </header>
          <fieldset class="lp-q">
            <legend><span>01</span> What is the trip?</legend>
            <div class="lp-mood" role="radiogroup" aria-label="Kind of trip">
              ${Object.entries(MOODS).map(([key, m]) => `<button type="button" role="radio" aria-checked="false" data-mood="${key}">
                <i>${icon(m.icon, 18)}</i><b>${m.label}</b><small>${m.caption}</small></button>`).join('')}
            </div>
          </fieldset>
          <fieldset class="lp-q">
            <legend><span>02</span> Who is coming?</legend>
            <div class="lp-pax">
              <button type="button" class="lp-pax__btn" data-pax="-1" aria-label="One fewer">−</button>
              <output class="lp-pax__n" id="pax-n" aria-live="polite">2</output>
              <button type="button" class="lp-pax__btn" data-pax="1" aria-label="One more">+</button>
              <span class="lp-pax__seats" id="pax-seats" aria-hidden="true">${'<i></i>'.repeat(PAX_MAX)}</span>
            </div>
          </fieldset>
          <fieldset class="lp-q">
            <legend><span>03</span> Daily budget</legend>
            <div class="lp-budget">
              <div class="lp-budget__hist" id="budget-hist" aria-hidden="true"></div>
              <input type="range" id="budget" min="0" max="1" step="1" value="1" aria-label="Highest daily rate">
              <div class="lp-budget__out"><span>Up to <b id="budget-out">—</b> a day</span><small id="budget-count"></small></div>
            </div>
          </fieldset>
          <fieldset class="lp-q">
            <legend><span>04</span> Powertrain</legend>
            <div class="lp-power" id="power" role="radiogroup" aria-label="Powertrain"></div>
          </fieldset>
        </div>
        <div class="lp-comp__result" id="comp-result" aria-live="polite">
          <div class="lp-match lp-match--empty"><span class="skeleton" style="height:100%"></span></div>
        </div>
      </div>
    </div>
  </section>`;
}

/**
 * ctx: { list(), nights(), qs(), branch(id), apply({ seats, fuel, max }) }
 * Returns { refresh() } - call it whenever the free list changes.
 */
export function mountComposer(root, ctx) {
  const section = $('#composer-section', root);
  const s = { mood: null, pax: 2, budget: null, power: 'Any', topId: null, lo: 0, hi: 0, stops: [] };

  // The slider moves from one car's rate to the next, so a single very
  // expensive car cannot squash every other price into the first notch.
  function bounds() {
    const prices = ctx.list().map((v) => Number(v.rentalPricePerDay)).filter((n) => n > 0);
    s.stops = [...new Set(prices)].sort((a, b) => a - b);
    s.lo = s.stops[0] || 0;
    s.hi = s.stops[s.stops.length - 1] || 0;
    if (s.budget === null || s.budget > s.hi || s.budget < s.lo) s.budget = s.hi;
  }

  function paintControls() {
    const list = ctx.list();
    $$('[data-mood]', section).forEach((b) => {
      const on = b.dataset.mood === s.mood;
      b.classList.toggle('is-on', on);
      b.setAttribute('aria-checked', String(on));
    });

    $('#pax-n', section).textContent = s.pax;
    $$('#pax-seats i', section).forEach((pip, i) => pip.classList.toggle('is-on', i < s.pax));
    $('[data-pax="-1"]', section).disabled = s.pax <= 1;
    $('[data-pax="1"]', section).disabled = s.pax >= PAX_MAX;

    // A skyline of the fleet: one bar per car, cheapest first, the height on
    // a log scale so a LKR 4,000 car and a LKR 200,000 car both read.
    const range = $('#budget', section);
    const sorted = [...list].sort((a, b) => Number(a.rentalPricePerDay) - Number(b.rentalPricePerDay));
    const logLo = Math.log(Math.max(1, s.lo));
    const logSpan = Math.max(0.0001, Math.log(Math.max(1, s.hi)) - logLo);
    $('#budget-hist', section).innerHTML = sorted.map((v) => {
      const r = Number(v.rentalPricePerDay) || 0;
      const h = 22 + ((Math.log(Math.max(1, r)) - logLo) / logSpan) * 78;
      return `<i style="--h:${h.toFixed(1)}%" class="${r <= s.budget ? 'is-in' : ''}" title="${esc(v.model)} · ${esc(money(r))} a day"></i>`;
    }).join('');
    const idx = Math.max(0, s.stops.indexOf(s.budget));
    range.min = 0;
    range.max = Math.max(1, s.stops.length - 1);
    range.value = idx;
    range.disabled = s.stops.length < 2;
    range.setAttribute('aria-valuetext', `${money(s.budget)} a day`);
    range.style.setProperty('--p', `${s.stops.length > 1 ? ((idx / (s.stops.length - 1)) * 100).toFixed(1) : 100}%`);
    $('#budget-out', section).textContent = money(s.budget);
    const within = list.filter((v) => Number(v.rentalPricePerDay) <= s.budget).length;
    $('#budget-count', section).textContent = `${within} of ${list.length} within`;

    const fuels = [...new Set(list.map((v) => v.fuelType).filter(Boolean))].sort();
    if (!fuels.includes(s.power)) s.power = 'Any';
    $('#power', section).innerHTML = ['Any', ...fuels].map((f) => `<button type="button" role="radio"
      data-power="${esc(f)}" class="${s.power === f ? 'is-on' : ''}" aria-checked="${s.power === f}">
      ${f === 'Electric' ? icon('bolt', 14) : f === 'Any' ? icon('sparkle', 14) : icon('fuel', 14)} ${f === 'Any' ? 'Any' : esc(f)}</button>`).join('');
  }

  function score(v) {
    const m = s.mood ? MOODS[s.mood] : null;
    const cap = Number(v.passengerCapacity) || 4;
    const rate = Number(v.rentalPricePerDay) || 0;
    const rel = s.hi > s.lo ? (rate - s.lo) / (s.hi - s.lo) : 0.5;
    const cat = m ? (m.cat[v.category] ?? 0.4) : 0.65;
    const fuel = s.power !== 'Any' ? (v.fuelType === s.power ? 1 : 0) : m ? (m.fuel[v.fuelType] ?? 0.6) : 0.7;
    const seats = cap >= s.pax ? 1 - Math.min(1, (cap - s.pax) / 9) : 0;
    const price = rate > s.budget ? 0
      : !m ? 1 - rel * 0.5
        : m.price === 'low' ? 1 - rel
          : m.price === 'high' ? 0.35 + rel * 0.65
            : 1 - Math.abs(rel - 0.5) * 1.2;
    const fits = cap >= s.pax && rate <= s.budget && (s.power === 'Any' || v.fuelType === s.power);
    const total = cat * 0.34 + fuel * 0.18 + seats * 0.2 + price * 0.28;
    return { v, fits, cap, rate, pct: fits ? Math.round(52 + total * 47) : Math.round(total * 48) };
  }

  function reasons(r) {
    const m = s.mood ? MOODS[s.mood] : null;
    const out = [];
    out.push([r.cap >= s.pax, `${r.cap} seats · room for ${s.pax}`]);
    out.push([r.rate <= s.budget, r.rate <= s.budget ? 'Within your budget' : 'Over your budget']);
    if (r.v.fuelType) out.push([s.power === 'Any' || r.v.fuelType === s.power, r.v.fuelType]);
    if (m && r.v.category) out.push([(m.cat[r.v.category] ?? 0) >= 0.7, `${r.v.category.charAt(0)}${r.v.category.slice(1).toLowerCase()} for ${m.label.toLowerCase()}`]);
    out.push([true, `Free all ${plural(ctx.nights(), 'night')}`]);
    return out;
  }

  function paintResult() {
    const host = $('#comp-result', section);
    const list = ctx.list();
    if (!list.length) {
      host.innerHTML = `<div class="lp-match lp-match--empty">
        <p class="lp-match__none">${icon('calendar', 22)}<b>No cars are free for your dates.</b>
        <span>Pick a quieter window on the availability pulse below, then compose again.</span></p>
        <button class="btn btn--sm lp-comp__btn" type="button" data-scroll="#pulse-section">Open the pulse ${icon('arrowRight', 15)}</button></div>`;
      s.topId = null;
      return;
    }
    const ranked = list.map(score).sort((a, b) => b.pct - a.pct || a.rate - b.rate);
    const top = ranked[0];
    const fitsAll = ranked.filter((r) => r.fits);
    const nights = ctx.nights();
    const qs = ctx.qs();
    const changed = s.topId !== top.v.vehicleId;
    s.topId = top.v.vehicleId;
    const b = ctx.branch(top.v.branchId);

    host.innerHTML = `
      <article class="lp-match${changed ? ' is-new' : ''}">
        <div class="lp-match__stage">
          <span class="lp-match__beam" aria-hidden="true"></span>
          <div class="lp-match__art">${vehicleVisual(top.v)}</div>
          <div class="lp-match__mirror" aria-hidden="true">${vehicleVisual(top.v)}</div>
          <div class="lp-match__ring" role="img" aria-label="${top.pct}% match">
            <svg viewBox="0 0 100 100" aria-hidden="true">
              <circle class="bg" cx="50" cy="50" r="44"/>
              <circle class="fg" cx="50" cy="50" r="44" stroke-dasharray="${RING.toFixed(1)}" stroke-dashoffset="${RING.toFixed(1)}" data-pct="${top.pct}"/>
            </svg>
            <b><span data-ring-n>0</span><small>%</small></b>
            <em>${top.fits ? 'match' : 'closest'}</em>
          </div>
        </div>
        <div class="lp-match__body">
          <p class="lp-match__kicker">${top.fits ? 'Best match' : 'Nothing fits every answer — closest'} · ${icon('pin', 12)} ${esc(b?.name || 'Branch')}</p>
          <h3>${esc(top.v.model)}</h3>
          <ul class="lp-match__why">${reasons(top).map(([ok, t]) => `<li class="${ok ? 'is-ok' : 'is-no'}">${icon(ok ? 'check' : 'close', 12)}${esc(t)}</li>`).join('')}</ul>
          <div class="lp-match__foot">
            <div class="lp-match__price"><b>${money(top.rate * nights)}</b><span>${plural(nights, 'night')} · ${money(top.rate)} a day</span></div>
            <a class="btn lp-comp__btn" href="#/vehicle/${top.v.vehicleId}${qs ? `?${qs}` : ''}">Reserve ${icon('arrowRight', 16)}</a>
          </div>
        </div>
      </article>
      ${ranked.length > 1 ? `<ol class="lp-alts">${ranked.slice(1, 3).map((r, i) => `
        <li><a href="#/vehicle/${r.v.vehicleId}${qs ? `?${qs}` : ''}" style="--d:${i}">
          <span class="lp-alts__n">${String(i + 2).padStart(2, '0')}</span>
          <span class="lp-alts__art">${vehicleVisual(r.v)}</span>
          <span class="lp-alts__name"><b>${esc(r.v.model)}</b><small>${money(r.rate)} a day · ${esc(r.v.fuelType || '')}</small></span>
          <span class="lp-alts__meter" style="--p:${r.pct}%"><i></i><b>${r.pct}%</b></span>
        </a></li>`).join('')}</ol>` : ''}
      <button class="lp-comp__all" type="button" data-comp-apply>
        ${fitsAll.length
          ? `See ${fitsAll.length === 1 ? 'the 1 car' : `all ${fitsAll.length} cars`} that fit, in the collection`
          : 'Loosen an answer to see more cars'} ${icon('arrowRight', 15)}</button>`;

    // Sweep the gauge and count up to the score.
    const fg = $('.lp-match__ring .fg', host);
    const num = $('[data-ring-n]', host);
    const target = top.pct;
    requestAnimationFrame(() => {
      fg.style.strokeDashoffset = (RING * (1 - target / 100)).toFixed(1);
      if (STILL()) { num.textContent = target; return; }
      const t0 = performance.now();
      const step = (t) => {
        const k = Math.min(1, (t - t0) / 900);
        num.textContent = Math.round(target * (1 - Math.pow(1 - k, 3)));
        if (k < 1 && num.isConnected) requestAnimationFrame(step);
      };
      requestAnimationFrame(step);
    });
  }

  function paint() { paintControls(); paintResult(); }

  section.addEventListener('click', (e) => {
    const mood = e.target.closest('[data-mood]');
    if (mood) { s.mood = s.mood === mood.dataset.mood ? null : mood.dataset.mood; paint(); return; }
    const pax = e.target.closest('[data-pax]');
    if (pax) { s.pax = Math.min(PAX_MAX, Math.max(1, s.pax + Number(pax.dataset.pax))); paint(); return; }
    const power = e.target.closest('[data-power]');
    if (power) { s.power = power.dataset.power; paint(); return; }
    if (e.target.closest('[data-comp-apply]')) {
      ctx.apply({ seats: s.pax, fuel: s.power, max: s.budget });
    }
  });
  const slid = debounce(paintResult, 90);
  section.addEventListener('input', (e) => {
    if (e.target.id !== 'budget') return;
    s.budget = s.stops[Number(e.target.value)] ?? s.hi;
    paintControls();
    slid();
  });
  // Arrow keys move through the trip cards like any radio group.
  section.addEventListener('keydown', (e) => {
    const cur = e.target.closest('[data-mood]');
    if (!cur || !['ArrowRight', 'ArrowLeft', 'ArrowDown', 'ArrowUp'].includes(e.key)) return;
    e.preventDefault();
    const all = $$('[data-mood]', section);
    const i = all.indexOf(cur) + (['ArrowRight', 'ArrowDown'].includes(e.key) ? 1 : -1);
    const nxt = all[(i + all.length) % all.length];
    nxt.focus();
    nxt.click();
  });

  return {
    refresh() { bounds(); paint(); },
  };
}

// ==================================================================
// 3. The availability pulse
// ==================================================================
const PULSE_DAYS = 56;
const MAX_NIGHTS = 30;

export function pulseSection(n) {
  return `<section class="section" id="pulse-section" data-rail-label="Availability">
    <div class="section-head" data-reveal>
      <div>
        <p class="eyebrow rule"><span class="eyebrow__n">${n}</span> Plan around the fleet</p>
        <h2 class="title-lg" style="margin-top:16px">The next eight weeks, at a glance</h2>
        <p>Every square is a day. Clear days have the whole collection free; the darker a day, the more of it is out. Drag across the days you want.</p>
      </div>
      <div class="lp-pulse__legend" aria-hidden="true"><span>Fully booked</span>${[0, 1, 2, 3, 4].map((l) => `<i class="l${l}"></i>`).join('')}<span>All free</span></div>
    </div>
    <div class="lp-pulse" data-reveal style="--d:1">
      <div class="lp-pulse__scroll"><div class="lp-pulse__grid" id="pulse-grid"><span class="skeleton" style="grid-column:1/-1;grid-row:1/-1"></span></div></div>
      <aside class="lp-pulse__panel" id="pulse-panel" aria-live="polite">
        <p class="lp-pulse__hint">${icon('calendar', 18)} Reading the calendar of every car…</p>
      </aside>
    </div>
  </section>`;
}

/**
 * ctx: { branchId(), onSearch(pickup, return) }
 * Reads each car's busy days once, when the section comes near the screen.
 */
export function mountPulse(root, ctx) {
  const section = $('#pulse-section', root);
  const grid = $('#pulse-grid', section);
  const panel = $('#pulse-panel', section);
  const start = isoDate();
  const days = Array.from({ length: PULSE_DAYS }, (_, i) => addDays(i, dayOf(start)));
  const s = { cars: [], busy: new Map(), anchor: null, hover: null, sel: null, dragging: false, ready: false };

  const carsHere = () => {
    const b = ctx.branchId();
    return b ? s.cars.filter((v) => String(v.branchId) === String(b)) : s.cars;
  };
  const freeOn = (v, day) => !(s.busy.get(v.vehicleId) || []).some((r) => r.from <= day && day <= r.to);
  // The same rule the server uses: the car must be free from the pick-up day
  // through the return day itself, since another booking cannot start on the
  // day this one hands the car back.
  const freeFor = (v, a, b) => {
    for (let d = a; d <= b; d = addDays(1, dayOf(d))) if (!freeOn(v, d)) return false;
    return true;
  };

  async function load() {
    try {
      s.cars = await api.vehicles.catalogue();
      const to = days[days.length - 1];
      const ranges = await pool(s.cars, 4, (v) => api.vehicles.busy(v.vehicleId, { from: start, to }).then((r) => r.busy || []).catch(() => []));
      s.cars.forEach((v, i) => s.busy.set(v.vehicleId, ranges[i]));
      s.ready = true;
      paintGrid();
      paintPanel();
    } catch (err) {
      panel.innerHTML = `<p class="lp-pulse__hint">${icon('alert', 18)} ${esc(err.message || 'Could not read the calendar')}</p>`;
      grid.innerHTML = '';
    }
  }

  function paintGrid() {
    const cars = carsHere();
    const total = cars.length || 1;
    // Columns are weeks starting on Monday; the first column is padded.
    const lead = (dayOf(start).getDay() + 6) % 7;
    const cells = [];
    let lastMonth = '';
    const months = [];
    days.forEach((d, i) => {
      const slot = i + lead;
      const col = Math.floor(slot / 7) + 2;
      const row = (slot % 7) + 2;
      const free = cars.filter((v) => freeOn(v, d)).length;
      const level = !cars.length ? 0 : Math.min(4, Math.round((free / total) * 4));
      const date = dayOf(d);
      const m = date.toLocaleDateString('en-GB', { month: 'short' });
      if (m !== lastMonth && row <= 4) { months.push(`<span class="lp-pulse__m" style="grid-column:${col}">${m}</span>`); lastMonth = m; }
      cells.push(`<button type="button" class="lp-day l${level}${i === 0 ? ' is-today' : ''}" style="grid-column:${col};grid-row:${row};--k:${i}" data-day="${d}"
        tabindex="${i === 0 ? 0 : -1}" aria-label="${date.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' })}: ${free} of ${cars.length} cars free">
        <span>${date.getDate()}</span></button>`);
    });
    const weeks = Math.ceil((days.length + lead) / 7);
    grid.style.setProperty('--weeks', weeks);
    grid.innerHTML = `${months.join('')}
      ${['Mon', '', 'Wed', '', 'Fri', '', 'Sun'].map((w, i) => `<span class="lp-pulse__w" style="grid-row:${i + 2}">${w}</span>`).join('')}
      ${cells.join('')}`;
    paintSelection();
  }

  const rangeOf = (a, b) => {
    let [p, r] = a <= b ? [a, b] : [b, a];
    r = addDays(1, dayOf(r));                       // the last day picked is a night
    if (rentalDays(p, r) > MAX_NIGHTS) r = addDays(MAX_NIGHTS, dayOf(p));
    return [p, r];
  };

  function paintSelection() {
    const [p, r] = s.sel || (s.dragging && s.anchor && s.hover ? rangeOf(s.anchor, s.hover) : [null, null]);
    $$('.lp-day', grid).forEach((c) => {
      const d = c.dataset.day;
      const inside = p && d >= p && d < r;
      c.classList.toggle('is-sel', Boolean(inside));
      c.classList.toggle('is-start', d === p);
      c.classList.toggle('is-end', Boolean(r) && d === addDays(-1, dayOf(r)));
      c.classList.toggle('is-hover', d === s.hover);
    });
  }

  function paintPanel() {
    if (!s.ready) return;
    const cars = carsHere();
    if (!cars.length) {
      panel.innerHTML = `<p class="lp-pulse__hint">${icon('car', 18)} No cars are listed at this branch yet.</p>`;
      return;
    }
    const names = (list) => `<ul class="lp-pulse__cars">${list.slice(0, 5).map((v) => `<li>${esc(v.model)}<small>${esc(money(v.rentalPricePerDay))}</small></li>`).join('')}
      ${list.length > 5 ? `<li class="more">+ ${list.length - 5} more</li>` : ''}</ul>`;

    if (s.sel) {
      const [p, r] = s.sel;
      const nights = rentalDays(p, r);
      const free = cars.filter((v) => freeFor(v, p, r));
      const cheapest = free.length ? Math.min(...free.map((v) => Number(v.rentalPricePerDay) || Infinity)) : 0;
      panel.innerHTML = `
        <p class="lp-pulse__kicker">Your window</p>
        <h3>${fmtDate(p)} <span>→</span> ${fmtDate(r)}</h3>
        <p class="lp-pulse__big"><b>${free.length}</b><span>of ${cars.length} cars free for all ${plural(nights, 'night')}</span></p>
        ${free.length ? names(free) : '<p class="lp-pulse__hint">Every car is taken on at least one of these nights. Try a shorter stay or slide the window.</p>'}
        ${cheapest ? `<p class="lp-pulse__from">From <b>${money(cheapest * nights)}</b> for the stay</p>` : ''}
        <div class="lp-pulse__acts">
          <button class="btn btn--sm" type="button" data-pulse-go ${free.length ? '' : 'disabled'}>${icon('search', 15)} Search these dates</button>
          <button class="btn btn--sm btn--text" type="button" data-pulse-clear>Clear</button>
        </div>`;
      return;
    }
    const d = s.hover || start;
    const free = cars.filter((v) => freeOn(v, d));
    panel.innerHTML = `
      <p class="lp-pulse__kicker">${s.hover ? 'On this day' : 'Today'}</p>
      <h3>${dayOf(d).toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' })}</h3>
      <p class="lp-pulse__big"><b>${free.length}</b><span>of ${cars.length} cars free</span></p>
      ${free.length ? names(free) : ''}
      <p class="lp-pulse__hint">${icon('info', 15)} Press on a day and drag to the last night you want. Up to ${MAX_NIGHTS} nights.</p>
      ${quietWeek(cars)}`;
  }

  // The seven days, starting tomorrow or later, with the most car-days free.
  function quietWeek(cars) {
    let best = null;
    for (let i = 1; i + 7 <= days.length; i++) {
      const seven = days.slice(i, i + 7);
      const free = seven.reduce((n, d) => n + cars.filter((v) => freeOn(v, d)).length, 0);
      if (!best || free > best.free) best = { i, free };
    }
    if (!best) return '';
    const from = days[best.i];
    const to = days[best.i + 6];
    const share = Math.round((best.free / (cars.length * 7)) * 100);
    return `<div class="lp-pulse__tip"><span>Quietest week ahead</span>
      <b>${fmtDate(from)} – ${fmtDate(to)} · ${share}% of car-days free</b>
      <button type="button" data-pulse-week="${from}">Select that week</button></div>`;
  }

  // ---------- pointer: press, drag, release ----------
  const cellAt = (e) => e.target.closest?.('.lp-day');
  grid.addEventListener('pointerdown', (e) => {
    const c = cellAt(e);
    if (!c || !s.ready) return;
    e.preventDefault();
    grid.setPointerCapture?.(e.pointerId);
    s.dragging = true;
    s.sel = null;
    s.anchor = c.dataset.day;
    s.hover = c.dataset.day;
    paintSelection();
  });
  grid.addEventListener('pointermove', (e) => {
    if (!s.ready) return;
    const c = s.dragging ? document.elementFromPoint(e.clientX, e.clientY)?.closest('.lp-day') : cellAt(e);
    if (!c || c.dataset.day === s.hover) return;
    s.hover = c.dataset.day;
    paintSelection();
    if (!s.sel) paintPanel();
  });
  const finish = () => {
    if (!s.dragging) return;
    s.dragging = false;
    if (s.anchor && s.hover) s.sel = rangeOf(s.anchor, s.hover);
    paintSelection();
    paintPanel();
  };
  grid.addEventListener('pointerup', finish);
  grid.addEventListener('pointercancel', finish);
  grid.addEventListener('pointerleave', () => { if (!s.dragging && !s.sel) { s.hover = null; paintSelection(); paintPanel(); } });

  // ---------- keyboard: arrows move, Enter marks the first then the last night ----------
  grid.addEventListener('keydown', (e) => {
    const c = e.target.closest('.lp-day');
    if (!c) return;
    const i = days.indexOf(c.dataset.day);
    const step = { ArrowRight: 7, ArrowLeft: -7, ArrowDown: 1, ArrowUp: -1 }[e.key];
    if (step !== undefined) {
      e.preventDefault();
      const next = days[Math.min(days.length - 1, Math.max(0, i + step))];
      const n = $(`.lp-day[data-day="${next}"]`, grid);
      $$('.lp-day', grid).forEach((x) => { x.tabIndex = -1; });
      n.tabIndex = 0;
      n.focus();
      s.hover = next;
      if (!s.sel && !s.anchor) paintPanel();
      paintSelection();
      return;
    }
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      if (!s.anchor || s.sel) { s.sel = null; s.anchor = c.dataset.day; s.hover = c.dataset.day; s.dragging = true; paintSelection(); return; }
      s.hover = c.dataset.day;
      finish();
      s.anchor = null;
    }
    if (e.key === 'Escape') { s.sel = null; s.anchor = null; s.dragging = false; paintSelection(); paintPanel(); }
  });

  panel.addEventListener('click', (e) => {
    if (e.target.closest('[data-pulse-clear]')) { s.sel = null; s.anchor = null; paintSelection(); paintPanel(); return; }
    if (e.target.closest('[data-pulse-go]') && s.sel) ctx.onSearch(s.sel[0], s.sel[1]);
    const week = e.target.closest('[data-pulse-week]');
    if (week) {
      s.sel = [week.dataset.pulseWeek, addDays(7, dayOf(week.dataset.pulseWeek))];
      s.anchor = null;
      paintSelection();
      paintPanel();
    }
  });

  // Only read the calendars once the section is close to the screen.
  if ('IntersectionObserver' in window) {
    const io = new IntersectionObserver((entries) => {
      if (entries.some((en) => en.isIntersecting)) { io.disconnect(); load(); }
    }, { rootMargin: '600px 0px' });
    io.observe(section);
  } else {
    load();
  }

  return {
    refresh() { if (s.ready) { paintGrid(); paintPanel(); } },
  };
}

// ==================================================================
// 4. The torch walkaround on the handover band
// ==================================================================
// Points on the side-profile photograph (as fractions of the image), in the
// same order as the three proof points under it.
const HOTSPOTS = [
  { u: 0.56, v: 0.585, label: 'Cabin: odometer and fuel' },
  { u: 0.195, v: 0.575, label: 'Rear: bodywork and damage' },
  { u: 0.805, v: 0.66, label: 'Front: service and workshop' },
];

export function torchLayer(srcset, sizes, src) {
  return `<img class="lp-band__img lp-band__torch" src="${src}" srcset="${srcset}" sizes="${sizes}" alt="" loading="lazy" decoding="async" aria-hidden="true">
    ${HOTSPOTS.map((h, i) => `<button type="button" class="lp-hot" data-hot="${i}" aria-label="${esc(h.label)}"><span>${i + 1}</span></button>`).join('')}`;
}

/** Where a point of an object-fit: cover image lands inside its container. */
function imagePoint(img, u, v, host) {
  const r = img.getBoundingClientRect();
  const hr = host.getBoundingClientRect();
  const nw = img.naturalWidth || 1000;
  const nh = img.naturalHeight || 610;
  const scale = Math.max(r.width / nw, r.height / nh);
  const dw = nw * scale;
  const dh = nh * scale;
  const [px, py] = (getComputedStyle(img).objectPosition || '50% 50%').split(' ').map((p) => parseFloat(p) / 100);
  const x = r.left - hr.left + (r.width - dw) * (Number.isFinite(px) ? px : 0.5) + u * dw;
  const y = r.top - hr.top + (r.height - dh) * (Number.isFinite(py) ? py : 0.5) + v * dh;
  return [x, y];
}

export function mountTorch(root) {
  const band = $('.lp-band', root);
  if (!band) return;
  const img = $('.lp-band__img:not(.lp-band__torch)', band);
  const spots = $$('.lp-hot', band);
  const items = $$('.lp-band__grid > div', band);
  let points = [];
  let active = 0;

  const place = () => {
    if (!band.isConnected) { window.removeEventListener('resize', place); return; }
    points = HOTSPOTS.map((h) => imagePoint(img, h.u, h.v, band));
    spots.forEach((b, i) => { b.style.left = `${points[i][0]}px`; b.style.top = `${points[i][1]}px`; });
    if (!band.matches(':hover')) aim(active);
  };
  const setTorch = (x, y) => { band.style.setProperty('--tx', `${x}px`); band.style.setProperty('--ty', `${y}px`); };
  function aim(i) {
    active = i;
    if (points[i]) setTorch(points[i][0], points[i][1]);
    spots.forEach((b, k) => b.classList.toggle('is-on', k === i));
    items.forEach((it, k) => it.classList.toggle('is-on', k === i));
  }

  items.forEach((it, i) => {
    it.dataset.hotNo = String(i + 1);
    it.addEventListener('pointerenter', () => aim(i));
    it.addEventListener('focusin', () => aim(i));
  });
  spots.forEach((b, i) => {
    b.addEventListener('pointerenter', () => aim(i));
    b.addEventListener('focus', () => aim(i));
    b.addEventListener('click', () => aim(i));
  });
  // Free movement: the torch follows the pointer over the photograph.
  band.addEventListener('pointermove', (e) => {
    if (!FINE() || e.target.closest('.lp-band__grid, .lp-hot')) return;
    const r = band.getBoundingClientRect();
    band.classList.add('is-torch');
    setTorch(e.clientX - r.left, e.clientY - r.top);
  });
  band.addEventListener('pointerleave', () => { band.classList.remove('is-torch'); aim(active); });

  // A slow patrol between the three points while nobody is pointing.
  let patrol = null;
  const startPatrol = () => {
    if (STILL() || patrol) return;
    patrol = setInterval(() => {
      if (!band.isConnected) { clearInterval(patrol); return; }
      if (!band.matches(':hover') && !band.contains(document.activeElement)) aim((active + 1) % HOTSPOTS.length);
    }, 3200);
  };

  if (img.complete) place(); else img.addEventListener('load', place, { once: true });
  window.addEventListener('resize', place, { passive: true });
  if ('ResizeObserver' in window) new ResizeObserver(place).observe(band);
  if ('IntersectionObserver' in window) {
    new IntersectionObserver((entries) => {
      entries.forEach((en) => {
        if (en.isIntersecting) { place(); startPatrol(); } else if (patrol) { clearInterval(patrol); patrol = null; }
      });
    }, { threshold: 0.25 }).observe(band);
  }
}

// ==================================================================
// 5. Section rail
// ==================================================================
export function sectionRail() {
  return '<nav class="lp-railnav" id="railnav" aria-label="Sections on this page"><span class="lp-railnav__track"><i></i></span><ol></ol></nav>';
}

export function mountSectionRail(root) {
  const nav = $('#railnav', root);
  const list = $('ol', nav);
  const fill = $('.lp-railnav__track i', nav);
  const hero = $('.lp-hero', root);

  const paint = () => {
    const secs = $$('section[data-rail-label]', root).filter((s) => !s.hidden);
    list.innerHTML = secs.map((s, i) => `<li><button type="button" data-scroll="#${s.id}" data-rail-for="${s.id}">
      <span class="lp-railnav__n">${String(i + 1).padStart(2, '0')}</span><span class="lp-railnav__t">${esc(s.dataset.railLabel)}</span></button></li>`).join('');
    return secs;
  };
  let secs = paint();

  let ticking = false;
  const onScroll = () => {
    if (!nav.isConnected) { window.removeEventListener('scroll', onScroll); return; }
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(() => {
      ticking = false;
      const heroGone = hero.getBoundingClientRect().bottom < innerHeight * 0.35;
      const doc = document.documentElement;
      const pct = Math.min(1, Math.max(0, scrollY / Math.max(1, doc.scrollHeight - innerHeight)));
      nav.classList.toggle('is-on', heroGone);
      fill.style.transform = `scaleY(${pct.toFixed(4)})`;
      const mid = innerHeight * 0.42;
      let current = null;
      secs.forEach((s) => { if (s.getBoundingClientRect().top <= mid) current = s.id; });
      $$('[data-rail-for]', list).forEach((b) => {
        const on = b.dataset.railFor === current;
        b.classList.toggle('is-on', on);
        if (on) b.setAttribute('aria-current', 'true'); else b.removeAttribute('aria-current');
      });
    });
  };
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();
  return {
    refresh() { secs = paint(); onScroll(); },
  };
}

// ==================================================================
// 6. Small motions: hero light, magnetic buttons, lit steps, FAQ search
// ==================================================================
export function mountHeroLight(root) {
  const scene = $('.lp-scene', root);
  if (!scene || STILL()) return;
  let raf = 0;
  scene.addEventListener('pointermove', (e) => {
    if (!FINE()) return;
    cancelAnimationFrame(raf);
    raf = requestAnimationFrame(() => {
      const r = scene.getBoundingClientRect();
      const x = (e.clientX - r.left) / r.width;
      const y = (e.clientY - r.top) / r.height;
      scene.style.setProperty('--lx', `${(x * 100).toFixed(1)}%`);
      scene.style.setProperty('--ly', `${(y * 100).toFixed(1)}%`);
      scene.style.setProperty('--px', ((x - 0.5) * 2).toFixed(3));
      scene.style.setProperty('--py', ((y - 0.5) * 2).toFixed(3));
      scene.classList.add('is-lit');
    });
  });
  scene.addEventListener('pointerleave', () => {
    scene.classList.remove('is-lit');
    scene.style.setProperty('--px', '0');
    scene.style.setProperty('--py', '0');
  });
}

/** Buttons that lean towards the pointer as it comes near. */
export function mountMagnets(root) {
  if (STILL()) return;
  root.addEventListener('pointermove', (e) => {
    if (!FINE()) return;
    const b = e.target.closest('[data-magnetic]');
    if (!b) return;
    const r = b.getBoundingClientRect();
    const dx = e.clientX - (r.left + r.width / 2);
    const dy = e.clientY - (r.top + r.height / 2);
    b.style.translate = `${(dx * 0.22).toFixed(1)}px ${(dy * 0.3).toFixed(1)}px`;
  });
  root.addEventListener('pointerout', (e) => {
    const b = e.target.closest('[data-magnetic]');
    if (b && !b.contains(e.relatedTarget)) b.style.translate = '';
  });
}

/** The six steps light up one after another as the section passes through. */
export function mountStepsGlow(root) {
  const sec = $('#steps-section', root);
  if (!sec) return;
  const cards = $$('.lp-step', sec);
  let ticking = false;
  const onScroll = () => {
    if (!sec.isConnected) { window.removeEventListener('scroll', onScroll); return; }
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(() => {
      ticking = false;
      const r = sec.getBoundingClientRect();
      const p = Math.min(1, Math.max(0, (innerHeight * 0.85 - r.top) / (r.height * 0.9)));
      cards.forEach((c, i) => {
        const lit = Math.min(1, Math.max(0, p * (cards.length + 1) - i));
        c.style.setProperty('--lit', lit.toFixed(3));
        c.classList.toggle('is-lit', lit >= 1);
      });
    });
  };
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();
}

export function faqSearch() {
  return `<label class="lp-faq__search">${icon('search', 16)}
    <input type="search" id="faq-q" placeholder="Search the answers" aria-label="Search the questions and answers" autocomplete="off">
    <small id="faq-count" aria-live="polite"></small>
  </label>`;
}

export function mountFaqSearch(root) {
  const input = $('#faq-q', root);
  if (!input) return;
  const items = $$('.lp-faq__list details', root);
  const count = $('#faq-count', root);
  const empty = document.createElement('p');
  empty.className = 'lp-faq__none';
  empty.hidden = true;
  empty.innerHTML = `${icon('info', 16)} Nothing here mentions that. The branch team will know - <a href="#/branches">find a branch</a>.`;
  $('.lp-faq__list', root).append(empty);
  // Keep the original text so a highlight can always be undone.
  items.forEach((d) => {
    const sum = $('summary', d);
    sum.dataset.q = sum.firstChild.textContent;
  });

  input.addEventListener('input', debounce(() => {
    const q = input.value.trim().toLowerCase();
    let shown = 0;
    let first = null;
    items.forEach((d) => {
      const sum = $('summary', d);
      const text = `${sum.dataset.q} ${$('.lp-faq__body', d).textContent}`.toLowerCase();
      const hit = !q || text.includes(q);
      d.hidden = !hit;
      const label = sum.dataset.q;
      const at = q ? label.toLowerCase().indexOf(q) : -1;
      sum.firstChild.replaceWith(at >= 0
        ? Object.assign(document.createElement('span'), {
          innerHTML: `${esc(label.slice(0, at))}<mark>${esc(label.slice(at, at + q.length))}</mark>${esc(label.slice(at + q.length))}`,
        })
        : document.createTextNode(label));
      if (hit) { shown += 1; first ??= d; }
    });
    if (q && first) first.open = true;
    empty.hidden = shown > 0;
    count.textContent = q ? `${plural(shown, 'answer')}` : '';
  }, 120));
}
