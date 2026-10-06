// ============================================================
// Customer-facing pages added on top of the original screens:
// Saved cars, Compare, a single Trip, Account, and Not found.
// Everything here is read from endpoints the backend already has;
// the saved and compare lists are the visitor's own, kept in the
// browser (see prefs.js).
// ============================================================
import { api } from '../api.js';
import { store, getBranches, getVehicle, isCustomer, isStaff } from '../store.js';
import { navigate, signOut } from '../app.js';
import {
  $, $$, esc, icon, money, number, fmtDate, fmtDateTime, fmtTime, isoDate, addDays, rentalDays, humanize,
  statusChip, vehicleVisual, field, toast, empty, skeletons, initials, confirmDialog, runAction,
  openDialog, readForm, formError, validateForm, showFieldErrors, errorSummary, markField,
} from '../ui.js';
import { saved, compare, recent, carActions, COMPARE_MAX } from '../prefs.js';
import { vehicleCard, footer, branchClock, MAX_RENTAL_DAYS, MAX_ADVANCE_DAYS, OFF_ROAD } from './public.js';
import { changeDatesDialog } from './customer.js';

const today = () => isoDate();
const validDate = (v) => (/^\d{4}-\d{2}-\d{2}$/.test(v || '') ? v : null);
const DATES_KEY = 'axle.dates.v1';

/** The dates a visitor last worked with, so each page starts where they were. */
export function rememberedDates(query = {}) {
  let p = validDate(query.pickup);
  let r = validDate(query.return);
  if (!p || !r) {
    try {
      const s = JSON.parse(sessionStorage.getItem(DATES_KEY) || 'null');
      if (s) { p = p || validDate(s.pickup); r = r || validDate(s.return); }
    } catch { /* no session storage */ }
  }
  if (!p || p < today()) p = addDays(1);
  if (!r || r <= p) r = addDays(2, new Date(`${p}T00:00:00`));
  return { pickup: p, ret: r };
}
export function rememberDates(pickup, ret) {
  try { sessionStorage.setItem(DATES_KEY, JSON.stringify({ pickup, return: ret })); } catch { /* ignore */ }
}

export function datebar(pickup, ret, label = 'Check') {
  return `<form class="datebar" id="datebar" novalidate>
    ${field({ name: 'pickup', label: 'Pick-up', type: 'date', value: pickup, min: today(), max: addDays(MAX_ADVANCE_DAYS) })}
    ${field({ name: 'return', label: 'Return', type: 'date', value: ret, min: addDays(1, new Date(`${pickup}T00:00:00`)), max: addDays(MAX_RENTAL_DAYS, new Date(`${pickup}T00:00:00`)) })}
    <button class="btn" type="submit">${icon('calendar', 16)} ${esc(label)}</button>
  </form>`;
}

/** Wires a datebar; `onDates(pickup, ret)` runs on a valid change. */
export function bindDatebar(el, onDates) {
  const form = $('#datebar', el);
  const ret = form.elements.return;
  form.elements.pickup.addEventListener('change', (e) => {
    if (!e.target.value) return;
    const from = new Date(`${e.target.value}T00:00:00`);
    ret.min = addDays(1, from);
    ret.max = addDays(MAX_RENTAL_DAYS, from);
    if (!ret.value || ret.value < ret.min) ret.value = ret.min;
    if (ret.value > ret.max) ret.value = ret.max;
  });
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const p = form.elements.pickup.value;
    const r = ret.value;
    if (!p || !r || r <= p) { toast('Return must be after pick-up', 'error'); return; }
    if (p < today()) { toast('Pick-up cannot be in the past', 'error'); return; }
    rememberDates(p, r);
    onDates(p, r);
  });
}

async function loadCars(ids) {
  const cars = await Promise.all(ids.map((id) => getVehicle(id).catch(() => null)));
  return cars.filter(Boolean);
}

export function pageHead({ eyebrow, title, lede = '', side = '', id = '' }) {
  return `<header class="ph">
    <div class="ph__copy">
      <p class="eyebrow rule">${eyebrow}</p>
      <h1 class="display ph__title">${title}</h1>
      ${lede ? `<p class="lede ph__lede"${id ? ` id="${id}"` : ''}>${lede}</p>` : ''}
    </div>
    ${side ? `<div class="ph__side">${side}</div>` : ''}
  </header>`;
}

// ==================================================================
// Saved cars
// ==================================================================
export async function renderSaved({ el, query }) {
  let { pickup, ret } = rememberedDates(query);
  const state = { cars: [], free: null, sort: 'saved' };

  el.innerHTML = `
    ${pageHead({ eyebrow: 'Your shortlist', title: 'Saved cars', lede: 'Loading…', id: 'saved-sum', side: datebar(pickup, ret) })}
    <div class="toolbar-row">
      <div class="seg" id="saved-sort" role="radiogroup" aria-label="Order">
        ${[['saved', 'Recently saved'], ['free', 'Free first'], ['price', 'Price']].map(([k, l]) => `
          <button type="button" role="radio" data-sort="${k}" class="${k === 'saved' ? 'is-active' : ''}" aria-checked="${k === 'saved'}">${l}</button>`).join('')}
      </div>
      <span class="muted small">${icon('lock', 14)} Kept on this device only</span>
    </div>
    <div id="saved-grid">${skeletons(3, 'skeleton-grid skeleton-grid--cards')}</div>
    ${footer()}`;

  const [branches] = await Promise.all([getBranches().catch(() => [])]);
  const byId = new Map(branches.map((b) => [b.branchId, b]));

  async function load() {
    state.cars = await loadCars(saved.list());
    await check();
  }
  async function check() {
    state.free = await api.vehicles.search(pickup, ret).then((l) => new Set(l.map((v) => v.vehicleId))).catch(() => null);
    draw();
  }
  function draw() {
    const ids = saved.list();
    const cars = state.cars.filter((v) => ids.includes(v.vehicleId));
    const nights = rentalDays(pickup, ret);
    const freeCount = state.free ? cars.filter((v) => state.free.has(v.vehicleId)).length : null;
    $('#saved-sum', el).textContent = cars.length
      ? `${cars.length} car${cars.length === 1 ? '' : 's'} saved${freeCount !== null ? ` · ${freeCount} free ${fmtDate(pickup)} → ${fmtDate(ret)}` : ''}.`
      : 'Tap the heart on any car to keep it here.';
    const host = $('#saved-grid', el);
    if (!cars.length) {
      host.innerHTML = empty({
        icon: 'heart', title: 'Nothing saved yet',
        text: 'Save cars while you browse and they wait here, with their availability for your dates.',
        action: '<a class="btn" href="#/explore">Browse the collection</a>',
      });
      return;
    }
    const order = [...cars];
    if (state.sort === 'price') order.sort((a, b) => Number(a.rentalPricePerDay) - Number(b.rentalPricePerDay));
    if (state.sort === 'free' && state.free) order.sort((a, b) => Number(state.free.has(b.vehicleId)) - Number(state.free.has(a.vehicleId)));
    if (state.sort === 'saved') order.sort((a, b) => ids.indexOf(a.vehicleId) - ids.indexOf(b.vehicleId));
    const qs = new URLSearchParams({ pickup, return: ret });
    host.innerHTML = `<div class="vehicle-grid">${order.map((v, i) => vehicleCard(v, byId.get(v.branchId), nights, qs, i, order.length, false, {
      avail: OFF_ROAD.includes(v.status) ? 'booked' : !state.free ? 'unknown' : state.free.has(v.vehicleId) ? 'free' : 'booked',
    })).join('')}</div>`;
  }

  bindDatebar(el, (p, r) => { pickup = p; ret = r; check(); });
  $('#saved-sort', el).addEventListener('click', (e) => {
    const b = e.target.closest('[data-sort]');
    if (!b) return;
    state.sort = b.dataset.sort;
    $$('#saved-sort [data-sort]', el).forEach((x) => { x.classList.toggle('is-active', x === b); x.setAttribute('aria-checked', String(x === b)); });
    draw();
  });
  const onPrefs = (e) => {
    if (!el.isConnected || !$('#saved-grid', el)) { window.removeEventListener('prefs:change', onPrefs); return; }
    if (e.detail.key !== 'saved') return;
    const missing = saved.list().some((id) => !state.cars.some((v) => v.vehicleId === id));
    if (missing) load(); else draw();
  };
  window.addEventListener('prefs:change', onPrefs);
  await load();
}

// ==================================================================
// Compare
// ==================================================================
const CMP_ROWS = [
  { key: 'rate', label: 'Per day', best: 'min', value: (v) => Number(v.rentalPricePerDay), show: (v) => money(v.rentalPricePerDay) },
  { key: 'total', label: 'Your dates', best: 'min', value: (v, c) => Number(v.rentalPricePerDay) * c.nights, show: (v, c) => `${money(Number(v.rentalPricePerDay) * c.nights)}<small>${c.nights} night${c.nights === 1 ? '' : 's'}</small>` },
  { key: 'avail', label: 'Availability', value: (v, c) => (c.free ? (c.free.has(v.vehicleId) ? 'Free' : 'Booked') : '—'), show: (v, c) => (!c.free ? '—' : c.free.has(v.vehicleId) ? '<span class="status status--green">Free</span>' : '<span class="status status--red">Booked</span>') },
  { key: 'seats', label: 'Seats', best: 'max', value: (v) => Number(v.passengerCapacity) || 0, show: (v) => esc(v.passengerCapacity ?? '—') },
  { key: 'fuel', label: 'Powertrain', value: (v) => v.fuelType || '—', show: (v) => esc(v.fuelType || '—') },
  { key: 'year', label: 'Model year', best: 'max', value: (v) => Number(v.manufactureYear) || 0, show: (v) => `${esc(v.manufactureYear ?? '—')}${v.vehicleAge != null ? `<small>${esc(v.vehicleAge)} yr old</small>` : ''}` },
  { key: 'odo', label: 'Odometer', best: 'min', value: (v) => Number(v.mileage) || 0, show: (v) => `${number(v.mileage)}<small>km</small>` },
  { key: 'cat', label: 'Body', value: (v) => v.category || '—', show: (v) => esc(humanize(v.category || '—')) },
  { key: 'branch', label: 'Collected from', value: (v, c) => c.byId.get(v.branchId)?.name || '—', show: (v, c) => { const b = c.byId.get(v.branchId); return b ? `${esc(b.name)}<small>${esc(b.city || '')}${b.openTime ? ` · ${fmtTime(b.openTime)}–${fmtTime(b.closeTime)}` : ''}</small>` : '—'; } },
];

export async function renderCompare({ el, query }) {
  if (query.ids) {
    const ids = query.ids.split(',').map(Number).filter((n) => Number.isInteger(n) && n > 0).slice(0, COMPARE_MAX);
    if (ids.length) { compare.clear(); ids.forEach((id) => compare.toggle(id)); }
  }
  let { pickup, ret } = rememberedDates(query);
  const state = { cars: [], free: null, diff: false };

  el.innerHTML = `
    ${pageHead({ eyebrow: 'Side by side', title: 'Compare', lede: `Up to ${COMPARE_MAX} cars, line by line. The best figure in each row is marked.`, side: datebar(pickup, ret) })}
    <div class="toolbar-row">
      <label class="switch"><input type="checkbox" id="cmp-diff"><span class="switch__track"><i></i></span> Only rows that differ</label>
      <div class="toolbar-row__end">
        <button class="btn btn--sm btn--tonal" type="button" id="cmp-share">${icon('link', 15)} Copy link</button>
        <button class="btn btn--sm btn--text" type="button" id="cmp-clear">Clear all</button>
      </div>
    </div>
    <div id="cmp">${skeletons(1)}</div>
    ${footer()}`;

  const branches = await getBranches().catch(() => []);
  const byId = new Map(branches.map((b) => [b.branchId, b]));

  async function load() {
    state.cars = await loadCars(compare.list());
    await check();
  }
  async function check() {
    state.free = await api.vehicles.search(pickup, ret).then((l) => new Set(l.map((v) => v.vehicleId))).catch(() => null);
    draw();
  }
  function draw() {
    const ids = compare.list();
    const cars = ids.map((id) => state.cars.find((v) => v.vehicleId === id)).filter(Boolean);
    const host = $('#cmp', el);
    $('#cmp-clear', el).hidden = !cars.length;
    $('#cmp-share', el).hidden = !cars.length;
    if (!cars.length) {
      host.innerHTML = empty({
        icon: 'layers', title: 'Nothing to compare yet',
        text: `Tap the layers button on up to ${COMPARE_MAX} cars in the collection, then come back here.`,
        action: '<a class="btn" href="#/explore">Choose cars</a>',
      });
      return;
    }
    const ctx = { nights: rentalDays(pickup, ret), free: state.free, byId };
    const qs = new URLSearchParams({ pickup, return: ret });
    const cols = cars.length + (cars.length < COMPARE_MAX ? 1 : 0);

    const rows = CMP_ROWS.map((row) => {
      const vals = cars.map((v) => row.value(v, ctx));
      const same = vals.every((x) => String(x) === String(vals[0]));
      let bestVal = null;
      if (row.best && cars.length > 1 && !same) bestVal = row.best === 'min' ? Math.min(...vals) : Math.max(...vals);
      return `<div class="cmp__row${same && cars.length > 1 ? ' is-same' : ''}" role="row">
        <div class="cmp__label" role="rowheader">${esc(row.label)}</div>
        ${cars.map((v, i) => `<div class="cmp__cell${bestVal !== null && vals[i] === bestVal ? ' is-best' : ''}" role="cell">
          ${row.show(v, ctx)}${bestVal !== null && vals[i] === bestVal ? `<em class="cmp__best">${row.best === 'min' ? (row.key === 'odo' ? 'Lowest' : 'Best value') : (row.key === 'year' ? 'Newest' : 'Most')}</em>` : ''}
        </div>`).join('')}
        ${cars.length < COMPARE_MAX ? '<div class="cmp__cell cmp__cell--empty" role="cell"></div>' : ''}
      </div>`;
    }).join('');

    host.innerHTML = `<div class="cmp${state.diff ? ' is-diff' : ''}" role="table" style="--cols:${cols}">
      <div class="cmp__row cmp__row--head" role="row">
        <div class="cmp__label" role="columnheader"><span class="muted small">${cars.length} of ${COMPARE_MAX}</span></div>
        ${cars.map((v) => `<div class="cmp__car" role="columnheader">
          <button class="cmp__remove" type="button" data-cmp-remove="${v.vehicleId}" aria-label="Remove ${esc(v.model)}">${icon('close', 14)}</button>
          <a class="cmp__stage" href="#/vehicle/${v.vehicleId}?${qs}">${vehicleVisual(v)}</a>
          <p class="eyebrow">${esc(v.category || 'Vehicle')}${v.manufactureYear ? ` · ${esc(v.manufactureYear)}` : ''}</p>
          <h3>${esc(v.model)}</h3>
        </div>`).join('')}
        ${cars.length < COMPARE_MAX ? `<a class="cmp__add" href="#/explore?${qs}#results-section" role="columnheader">
          <span>${icon('plus', 22)}</span><b>Add a car</b><small>${COMPARE_MAX - cars.length} more place${COMPARE_MAX - cars.length === 1 ? '' : 's'}</small></a>` : ''}
      </div>
      ${rows}
      <div class="cmp__row cmp__row--foot" role="row">
        <div class="cmp__label" role="rowheader"></div>
        ${cars.map((v) => `<div class="cmp__cell" role="cell">
          <a class="btn btn--sm btn--block" href="#/vehicle/${v.vehicleId}?${qs}">${OFF_ROAD.includes(v.status) ? 'View' : 'Reserve'} ${icon('arrowRight', 15)}</a>
          ${carActions(v)}
        </div>`).join('')}
        ${cars.length < COMPARE_MAX ? '<div class="cmp__cell cmp__cell--empty" role="cell"></div>' : ''}
      </div>
    </div>`;
  }

  bindDatebar(el, (p, r) => { pickup = p; ret = r; check(); });
  $('#cmp-diff', el).addEventListener('change', (e) => { state.diff = e.target.checked; $('.cmp', el)?.classList.toggle('is-diff', state.diff); });
  $('#cmp-clear', el).addEventListener('click', () => compare.clear());
  $('#cmp-share', el).addEventListener('click', async () => {
    const url = `${location.origin}${location.pathname}#/compare?ids=${compare.list().join(',')}`;
    try { await navigator.clipboard.writeText(url); toast('Link copied — anyone with it sees the same comparison', 'success'); } catch { toast(url); }
  });
  el.addEventListener('click', (e) => {
    const r = e.target.closest('[data-cmp-remove]');
    if (r) compare.remove(r.dataset.cmpRemove);
  });
  const onPrefs = (e) => {
    if (!el.isConnected || !$('#cmp', el)) { window.removeEventListener('prefs:change', onPrefs); return; }
    if (e.detail.key !== 'compare') return;
    const missing = compare.list().some((id) => !state.cars.some((v) => v.vehicleId === id));
    if (missing) load(); else draw();
  };
  window.addEventListener('prefs:change', onPrefs);
  await load();
}

// ==================================================================
// One trip
// ==================================================================
const dayDiff = (a, b) => Math.round((new Date(`${a}T00:00:00`) - new Date(`${b}T00:00:00`)) / 86400000);
const plural = (n, w) => `${n} ${w}${n === 1 ? '' : 's'}`;
const weekday = (d) => new Date(`${d}T00:00:00`).toLocaleDateString('en-GB', { weekday: 'long' });

/** The one line that says where a trip stands, as a big number and a caption. */
export function tripClock(b) {
  const t = today();
  if (b.status === 'REJECTED') return { big: '—', unit: 'Declined', note: 'The branch could not take this request.' };
  if (b.status === 'CANCELLED') return { big: '—', unit: 'Cancelled', note: 'This trip was called off.' };
  if (b.status === 'NO_SHOW') return { big: '—', unit: 'Not collected', note: 'The car was not picked up, so the booking was closed.' };
  if (b.status === 'COMPLETED') {
    const on = b.actualReturnDate || b.returnDate;
    return { big: '✓', unit: 'Returned', note: `Handed back ${fmtDate(on)}.` };
  }
  if (b.status === 'ACTIVE_RENTAL') {
    const d = dayDiff(b.returnDate, t);
    if (d > 0) return { big: String(d), unit: `day${d === 1 ? '' : 's'} left`, note: `Due back ${weekday(b.returnDate)}, ${fmtDate(b.returnDate)}.` };
    if (d === 0) return { big: 'Today', unit: 'Return day', note: 'Bring the car back to the branch before it closes.' };
    return { big: String(-d), unit: `day${d === -1 ? '' : 's'} overdue`, note: 'Please return the car or call the branch.' };
  }
  const d = dayDiff(b.pickupDate, t);
  const waiting = b.status === 'PENDING_APPROVAL';
  if (d > 0) return { big: String(d), unit: `day${d === 1 ? '' : 's'} to pick-up`, note: waiting ? 'The branch is reviewing your request.' : `Collect on ${weekday(b.pickupDate)}, ${fmtDate(b.pickupDate)}.` };
  if (d === 0) return { big: 'Today', unit: 'Pick-up day', note: waiting ? 'Still awaiting the branch — call ahead.' : 'Your car is ready at the branch.' };
  return { big: String(-d), unit: `day${d === -1 ? '' : 's'} past pick-up`, note: 'Pick-up date has passed — call the branch.' };
}

function icsFor(b, v, branch) {
  const d = (s) => s.replace(/-/g, '');
  const esc2 = (s) => String(s || '').replace(/[\\,;]/g, (c) => `\\${c}`).replace(/\n/g, '\\n');
  const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d+/, '');
  const where = branch ? [branch.name, branch.street, branch.city].filter(Boolean).join(', ') : '';
  return [
    'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Axle//Trip//EN', 'CALSCALE:GREGORIAN',
    'BEGIN:VEVENT',
    `UID:axle-trip-${b.bookingId}@axle`,
    `DTSTAMP:${stamp}`,
    `DTSTART;VALUE=DATE:${d(b.pickupDate)}`,
    `DTEND;VALUE=DATE:${d(addDays(1, new Date(`${b.returnDate}T00:00:00`)))}`,
    `SUMMARY:${esc2(`Axle trip #${b.bookingId} — ${v?.model || 'car'}`)}`,
    `LOCATION:${esc2(where)}`,
    `DESCRIPTION:${esc2(`Collect from ${branch?.name || 'the branch'}${branch?.openTime ? ` (${fmtTime(branch.openTime)}–${fmtTime(branch.closeTime)})` : ''}. Bring your driving licence.${branch?.contactNumber ? ` Branch: ${branch.contactNumber}` : ''}`)}`,
    'BEGIN:VALARM', 'TRIGGER:-P1D', 'ACTION:DISPLAY', 'DESCRIPTION:Axle pick-up tomorrow', 'END:VALARM',
    'END:VEVENT', 'END:VCALENDAR',
  ].join('\r\n');
}

/** The trip's cost, line by line, from the server's own breakdown (B6). */
function costLines(b, c, payment, rate, nights, closed) {
  const row = (label, amount, cls = '') => `<div class="quote__row${cls}"><span>${label}</span><span>${amount}</span></div>`;
  if (!c) {
    return `<div class="quote" style="margin-top:14px">
      ${row(`${money(rate)} \u00d7 ${plural(nights, 'night')}`, money(b.estimatedCost))}
      <div class="quote__row quote__total"><span>${b.finalCost != null ? 'Final total' : 'Estimated total'}</span><b>${money(b.finalCost != null ? b.finalCost : b.estimatedCost)}</b></div>
    </div>`;
  }
  const lines = [];
  if (closed) {
    lines.push(row(b.status === 'NO_SHOW' ? 'No-show charge (one night)' : 'Late cancellation charge', money(c.cancellationCharge)));
  } else {
    lines.push(row(`${money(c.dailyRate)} \u00d7 ${plural(c.nights, 'night')}`, money(c.baseCost)));
    if (c.lateDays > 0) lines.push(row(`Late return \u00b7 ${plural(c.lateDays, 'day')} at 1.5\u00d7`, money(c.lateFee)));
    (c.damage || []).forEach((d) => {
      lines.push(row(`Damage: ${esc(d.description || humanize(d.severity || 'damage'))} <small>${esc(humanize(d.severity || ''))}${d.claimStatus ? ` \u00b7 insurance claim ${esc(humanize(d.claimStatus).toLowerCase())}` : ''}</small>`, money(d.cost)));
    });
    if (Number(c.insuranceCredit) > 0) lines.push(row('Paid by insurance', `\u2212${money(c.insuranceCredit)}`, ' quote__row--credit'));
  }
  const refund = payment?.refundAmount != null && payment.status === 'REFUNDED';
  const note = payment?.status === 'PAID' ? 'Paid in full \u2014 thank you.'
    : refund ? `A refund of ${money(payment.refundAmount)} is being processed.`
      : payment?.status === 'REFUNDED' ? 'Your refund is being processed.'
        : closed && !Number(c.cancellationCharge) ? 'Nothing is charged for this trip.'
          : closed ? 'Payable at the branch counter.'
            : c.finalTotal ? 'Anything outstanding is settled at the branch.'
              : 'Settled at the branch counter. Your rate is held at what you were quoted.';
  return `<div class="quote" style="margin-top:14px">
      ${lines.join('')}
      <div class="quote__row quote__total"><span>${c.finalTotal ? 'Final total' : 'Estimated total'}</span><b>${money(c.total)}</b></div>
    </div>
    <p class="muted small" style="margin-top:12px">${note}</p>`;
}

/** A later return date for an approved or on-the-road trip (B1). */
function extendDialog(b, v) {
  const minReturn = addDays(1, new Date(`${b.returnDate}T00:00:00`));
  const maxReturn = addDays(MAX_RENTAL_DAYS, new Date(`${b.pickupDate}T00:00:00`));
  const rate = v ? Number(v.rentalPricePerDay) : Number(b.estimatedCost) / rentalDays(b.pickupDate, b.returnDate);
  return openDialog({
    title: 'Extend your trip',
    subtitle: `${v?.model || 'Your car'} \u00b7 now due back ${fmtDate(b.returnDate)}`,
    submitLabel: 'Extend',
    body: `
      <div class="form-grid">
        ${field({ name: 'return', label: 'New return date', type: 'date', value: minReturn, min: minReturn, max: maxReturn, required: true, span: true })}
      </div>
      <div class="quote" id="extend-quote"></div>
      <p class="muted small">We check the extra days are free before confirming. ${b.status === 'ACTIVE_RENTAL' ? 'Keep the car until the new date - ' : ''}Any extra is settled at the branch.</p>`,
    onOpen(form) {
      const q = form.querySelector('#extend-quote');
      const paint = () => {
        const r = form.elements.return.value;
        if (!r || r <= b.returnDate) { q.innerHTML = '<div class="quote__row"><span>Choose a later date</span></div>'; return; }
        const extra = rentalDays(b.returnDate, r);
        const total = rate * rentalDays(b.pickupDate, r);
        q.innerHTML = `<div class="quote__row"><span>${plural(extra, 'extra night')}</span><span>+${money(rate * extra)}</span></div>
          <div class="quote__row quote__total"><span>New total</span><b>${money(total)}</b></div>`;
      };
      form.elements.return.addEventListener('change', paint);
      paint();
    },
    onSubmit: (values) => api.bookings.extend(b.bookingId, values.return),
  });
}

export async function renderTrip({ el, params }) {
  el.innerHTML = `<div class="skeleton" style="min-height:70vh;margin-top:26px"></div>`;
  const id = Number(params.id);
  const b = await api.bookings.get(id);
  const [v, branches, payment, charges] = await Promise.all([
    getVehicle(b.vehicleId).catch(() => null),
    getBranches().catch(() => []),
    api.bookings.payment(id).catch(() => null),
    api.bookings.charges(id).catch(() => null),
  ]);
  const branch = branches.find((x) => x.branchId === b.pickupBranchId);
  const nights = rentalDays(b.pickupDate, b.returnDate);
  const clock = tripClock(b);
  const upcoming = ['PENDING_APPROVAL', 'APPROVED'].includes(b.status);
  const canCancel = upcoming && b.pickupDate > today();
  const closed = ['REJECTED', 'CANCELLED', 'NO_SHOW'].includes(b.status);
  const canExtend = ['APPROVED', 'ACTIVE_RENTAL'].includes(b.status);
  const reached = { PENDING_APPROVAL: 1, APPROVED: 2, ACTIVE_RENTAL: 3, COMPLETED: 4 }[b.status] ?? 1;
  const late = b.actualReturnDate && b.actualReturnDate > b.returnDate;
  const tel = String(branch?.contactNumber || '').replace(/[^\d+]/g, '');
  const where = branch ? [branch.name, branch.street, branch.city].filter(Boolean).join(', ') : '';
  const bc = branch ? branchClock(branch) : null;
  const rate = v ? Number(v.rentalPricePerDay) : Number(b.estimatedCost) / nights;

  const steps = [
    { title: 'Requested', when: b.submittedDate ? fmtDateTime(b.submittedDate) : '', text: 'Your request reached the branch with the estimate locked to these dates.' },
    closed
      ? { title: b.status === 'REJECTED' ? 'Declined' : b.status === 'NO_SHOW' ? 'Not collected' : 'Cancelled', when: '',
          text: b.status === 'REJECTED' ? 'The branch could not take this request. Try another car or other dates.'
            : b.status === 'NO_SHOW' ? 'Nobody came for the car within the grace period, so the booking was closed.'
              : 'This trip was cancelled.', tone: 'end' }
      : { title: 'Confirmed by the branch', when: reached >= 2 ? 'Approved' : 'Awaiting review', text: reached >= 2 ? 'A person at the branch checked the car is free and approved it.' : 'A person at the branch reads every request and answers you directly.' },
    ...(closed ? [] : [
      { title: 'Pick-up', when: `${weekday(b.pickupDate)}, ${fmtDate(b.pickupDate)}`, text: `Collect from ${branch?.name || 'the branch'}${branch?.openTime ? ` between ${fmtTime(branch.openTime)} and ${fmtTime(branch.closeTime)}` : ''}. The car's condition is recorded with you at handover.` },
      { title: 'Return', when: `${weekday(b.actualReturnDate || b.returnDate)}, ${fmtDate(b.actualReturnDate || b.returnDate)}${late ? ' · late' : ''}`, text: late ? `Due ${fmtDate(b.returnDate)}; returned ${fmtDate(b.actualReturnDate)}. Extra days are added to the total.` : 'Bring it back to the same branch. The final total is settled at return.' },
    ]),
  ];

  el.innerHTML = `
    <a class="back-link" href="#/trips">${icon('arrowLeft', 16)} All trips</a>
    <section class="tripx">
      <div class="tripx__stage">${v ? vehicleVisual(v) : ''}</div>
      <div class="tripx__info">
        <p class="eyebrow rule">Trip #${b.bookingId}${b.submittedDate ? ` · requested ${fmtDate(b.submittedDate)}` : ''}</p>
        <h1 class="display display--model">${esc(v?.model || `Vehicle ${b.vehicleId}`)}</h1>
        <div class="tripx__meta">${statusChip(b.status)}${v ? `<span class="id-pill">${esc(v.plateNumber)}</span>` : ''}${v?.category ? `<span class="muted small">${esc(humanize(v.category))}</span>` : ''}</div>

        <div class="tripx__clock${closed ? ' is-closed' : ''}">
          <b>${esc(clock.big)}</b><span>${esc(clock.unit)}</span>
          <p>${esc(clock.note)}</p>
        </div>

        <div class="tripx__dates">
          <div><small>Pick-up</small><b>${fmtDate(b.pickupDate)}</b><span>${weekday(b.pickupDate)}</span></div>
          <i aria-hidden="true">${icon('arrowRight', 18)}<em>${plural(nights, 'night')}</em></i>
          <div><small>Return</small><b>${fmtDate(b.returnDate)}</b><span>${weekday(b.returnDate)}</span></div>
        </div>

        <div class="tripx__actions">
          ${!closed && b.status !== 'COMPLETED' ? `<button class="btn btn--sm" type="button" id="trip-ics">${icon('calendar', 15)} Add to calendar</button>` : ''}
          ${b.status === 'PENDING_APPROVAL' ? `<button class="btn btn--sm btn--tonal" type="button" id="trip-dates">${icon('edit', 15)} Change dates</button>` : ''}
          ${canExtend ? `<button class="btn btn--sm btn--tonal" type="button" id="trip-extend">${icon('plus', 15)} Extend</button>` : ''}
          ${canCancel ? '<button class="btn btn--sm btn--outline" type="button" id="trip-cancel">Cancel trip</button>' : ''}
          <button class="btn btn--sm btn--text" type="button" id="trip-print">${icon('printer', 15)} Print</button>
        </div>
      </div>
    </section>

    <div class="tripx__grid">
      <section class="tripx__card">
        <div class="panel-title"><span>Journey</span></div>
        <ol class="tline">${steps.map((s, i) => {
          const state = s.tone === 'end' ? 'is-end' : i < reached ? 'is-done' : i === reached ? 'is-now' : '';
          return `<li class="tline__step ${state}">
            <span class="tline__dot">${state === 'is-done' ? icon('check', 12) : state === 'is-end' ? icon('close', 12) : ''}</span>
            <div><b>${esc(s.title)}</b>${s.when ? `<small>${esc(s.when)}</small>` : ''}<p>${esc(s.text)}</p></div>
          </li>`;
        }).join('')}</ol>
        ${b.specialRequests ? `<blockquote class="tripx__note">${icon('doc', 16)}<span>“${esc(b.specialRequests)}”</span></blockquote>` : ''}
      </section>

      <aside class="tripx__side">
        <section class="tripx__card">
          <div class="panel-title"><span>Cost</span>${payment ? statusChip(payment.status) : ''}</div>
          ${costLines(b, charges, payment, rate, nights, closed)}
        </section>

        ${branch ? `<section class="tripx__card">
          <div class="panel-title"><span>Branch</span>${bc ? `<span class="br-state${bc.open ? ' is-on' : ''}"><i></i><span>${bc.open ? 'Open now' : 'Closed now'}</span></span>` : ''}</div>
          <h3 class="tripx__branch">${esc(branch.name)}</h3>
          <p class="muted small">${esc([branch.street, branch.city].filter(Boolean).join(', '))}</p>
          ${bc ? `<p class="small" style="margin-top:8px">${esc(bc.line)}</p>` : ''}
          <div class="tripx__branch-acts">
            ${tel ? `<a class="btn btn--sm btn--tonal" href="tel:${esc(tel)}">${icon('phone', 15)} Call</a>` : ''}
            <a class="btn btn--sm btn--tonal" href="https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(where)}" target="_blank" rel="noopener">${icon('navigation', 15)} Directions</a>
          </div>
        </section>` : ''}

        ${upcoming ? `<section class="tripx__card">
          <div class="panel-title"><span>Before pick-up</span></div>
          <ul class="checklist">
            <li class="${store.user?.licenseVerified ? 'is-ok' : ''}">${icon(store.user?.licenseVerified ? 'check' : 'info', 15)}<span>${store.user?.licenseVerified ? 'Your licence is verified on file.' : 'Bring your driving licence — it is checked before the keys change hands.'}</span></li>
            <li>${icon('clock', 15)}<span>Arrive within opening hours${branch?.openTime ? ` (${fmtTime(branch.openTime)}–${fmtTime(branch.closeTime)})` : ''}.</span></li>
            <li>${icon('camera', 15)}<span>Walk round the car with the staff member; its condition is recorded with you.</span></li>
            <li>${icon('wallet', 15)}<span>Payment is settled at the counter.</span></li>
          </ul>
        </section>` : ''}
      </aside>
    </div>
    ${footer(branch)}`;

  $('#trip-ics', el)?.addEventListener('click', () => {
    const blob = new Blob([icsFor(b, v, branch)], { type: 'text/calendar' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `axle-trip-${b.bookingId}.ics`;
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 2000);
    toast('Calendar file ready — open it to add the trip', 'success');
  });
  $('#trip-print', el).addEventListener('click', () => window.print());
  $('#trip-dates', el)?.addEventListener('click', async () => {
    if (await changeDatesDialog({ booking: b, vehicle: v })) { toast('Dates updated', 'success'); navigate(`/trip/${id}`); }
  });
  $('#trip-extend', el)?.addEventListener('click', async () => {
    if (await extendDialog(b, v)) { toast('Trip extended', 'success'); navigate(`/trip/${id}`); }
  });
  $('#trip-cancel', el)?.addEventListener('click', async (e) => {
    // Free until two days before pickup; later, one night is kept.
    const lateCancel = dayDiff(b.pickupDate, today()) < 2;
    const fee = Math.min(rate, Number(b.estimatedCost));
    const money_ = lateCancel
      ? `Cancelling this close to pick-up keeps one night (${money(fee)}). ${payment?.status === 'PAID' ? `The rest (${money(Number(payment.amount) - fee)}) is refunded.` : 'It is payable at the branch.'}`
      : `${payment?.status === 'PAID' ? 'Your payment will be refunded in full.' : 'Nothing will be charged.'}`;
    const ok = await confirmDialog({
      title: `Cancel trip #${b.bookingId}?`,
      text: `${v?.model || 'This car'} · ${fmtDate(b.pickupDate)} → ${fmtDate(b.returnDate)}. ${money_} This can't be undone.`,
      confirmLabel: 'Cancel trip', tone: 'danger',
    });
    if (ok && await runAction(e.currentTarget, () => api.bookings.cancel(b.bookingId, 'Cancelled by the customer'), 'Trip cancelled')) navigate(`/trip/${id}`);
  });
}

// ==================================================================
// Account
// ==================================================================
export async function renderAccount({ el }) {
  // Always the latest from the server: a licence verified a minute ago, a
  // changed name, two-step sign-in just switched on.
  let u = await api.me.get().catch(() => store.user);
  store.user = { ...store.user, ...u };
  const customer = isCustomer();
  const staffish = !customer;

  el.innerHTML = `
    <section class="acct-hero">
      <span class="acct-hero__avatar" aria-hidden="true">${esc(initials(u.fullName))}</span>
      <div>
        <p class="eyebrow rule">Your account</p>
        <h1 class="display ph__title" id="acct-name">${esc(u.fullName)}</h1>
        <p class="acct-hero__meta"><span>${icon('mail', 15)} ${esc(u.email)}</span>${statusChip(u.role)}
          ${u.emailVerified === false ? '<span class="status status--amber">Email not confirmed</span>' : ''}</p>
      </div>
      <button class="btn btn--tonal" type="button" id="acct-out">${icon('logout', 16)} Sign out</button>
    </section>
    ${u.emailVerified === false ? `<div class="notice notice--amber acct-banner">${icon('mail', 18)}
      <span><b>Please confirm your email address.</b> We sent a link to ${esc(u.email)} - you need it confirmed before your first reservation.</span>
      <button class="btn btn--sm" type="button" id="acct-resend">Send the link again</button></div>` : ''}
    <div class="acct-grid" id="acct-grid">${skeletons(4, 'skeleton-grid')}</div>

    <nav class="acct-tabs seg" role="tablist" aria-label="Account settings">
      <button type="button" role="tab" data-acct-tab="profile" class="is-active" aria-selected="true">${icon('user', 15)} Profile</button>
      <button type="button" role="tab" data-acct-tab="security" aria-selected="false">${icon('lock', 15)} Sign-in &amp; security</button>
      <button type="button" role="tab" data-acct-tab="alerts" aria-selected="false">${icon('bell', 15)} Email updates</button>
      <button type="button" role="tab" data-acct-tab="data" aria-selected="false">${icon('shield', 15)} Your data</button>
    </nav>
    <section class="acct-panel" id="acct-panel"></section>
    ${footer()}`;

  $('#acct-out', el).addEventListener('click', signOut);
  $('#acct-resend', el)?.addEventListener('click', (e) =>
    runAction(e.currentTarget, () => api.me.resendVerification(), 'A new confirmation link is on its way'));

  // ---------- the summary cards ----------
  let trips = [];
  let pays = [];
  if (customer) {
    trips = await api.bookings.mine().catch(() => []);
    pays = await Promise.all(trips.map((t) => api.bookings.payment(t.bookingId).catch(() => null)));
  }
  const branches = await getBranches().catch(() => []);
  const done = trips.filter((t) => t.status === 'COMPLETED');
  const nightsDriven = done.reduce((s, t) => s + rentalDays(t.pickupDate, t.actualReturnDate || t.returnDate), 0);
  const spent = pays.reduce((s, p) => s + (p && p.status === 'PAID' ? Number(p.amount || 0) : 0), 0);
  const branchCount = new Map();
  trips.filter((t) => !['REJECTED', 'CANCELLED', 'NO_SHOW'].includes(t.status)).forEach((t) => branchCount.set(t.pickupBranchId, (branchCount.get(t.pickupBranchId) || 0) + 1));
  const favBranch = [...branchCount.entries()].sort((a, b) => b[1] - a[1])[0];
  const favName = favBranch ? branches.find((b) => b.branchId === favBranch[0])?.name : null;
  const next = trips.filter((t) => ['PENDING_APPROVAL', 'APPROVED', 'ACTIVE_RENTAL'].includes(t.status))
    .sort((a, b) => a.pickupDate.localeCompare(b.pickupDate))[0];
  const nextCar = next ? await getVehicle(next.vehicleId).catch(() => null) : null;

  let licence = '';
  if (customer) {
    const exp = u.licenseExpiryDate;
    const left = exp ? dayDiff(exp, today()) : null;
    const tone = left === null ? '' : left < 0 ? 'is-bad' : left < 60 ? 'is-warn' : 'is-ok';
    const pct = left === null ? 0 : Math.max(0, Math.min(100, (left / 1825) * 100));
    const num = String(u.drivingLicenseNumber || '');
    licence = `<article class="acct-card acct-licence ${u.licenseVerified ? 'is-verified' : ''}">
      <div class="panel-title"><span>Driving licence</span>${u.licenseVerified ? '<span class="status status--green">Verified</span>' : '<span class="status status--amber">Awaiting check</span>'}</div>
      <div class="acct-licence__card">
        <span class="acct-licence__chip" aria-hidden="true"></span>
        <b>${num ? `•••• ${esc(num.slice(-4))}` : 'Not on file'}</b>
        <small>${esc(u.fullName)}</small>
        <span class="acct-licence__exp">${exp ? `Expires ${fmtDate(exp)}` : 'No expiry on file'}</span>
      </div>
      ${left !== null ? `<div class="acct-meter ${tone}"><i style="width:${pct.toFixed(1)}%"></i></div>
        <p class="small ${tone === 'is-ok' ? 'muted' : ''}">${left < 0 ? `Expired ${plural(-left, 'day')} ago — renew it before your next trip.` : left < 60 ? `Only ${plural(left, 'day')} left — renew soon, the branch checks it at handover.` : `Valid for another ${left > 365 ? `${(left / 365).toFixed(1)} years` : plural(left, 'day')}.`}</p>` : ''}
      ${u.licenseVerified ? '' : '<p class="muted small">Bring the licence to your next pick-up; staff verify it against your account.</p>'}
    </article>`;
  }

  const stat = (n, l) => `<div><b>${n}</b><span>${l}</span></div>`;
  $('#acct-grid', el).innerHTML = `
    ${licence}
    ${customer ? `<article class="acct-card acct-stats">
      <div class="panel-title"><span>On the road</span></div>
      <div class="acct-stats__grid">
        ${stat(trips.length, 'Trips requested')}
        ${stat(done.length, 'Completed')}
        ${stat(nightsDriven, 'Nights driven')}
        ${stat(spent ? money(spent) : '—', 'Paid so far')}
      </div>
      ${favName ? `<p class="muted small">Your usual branch: <b style="color:var(--ink)">${esc(favName)}</b></p>` : ''}
    </article>` : `<article class="acct-card"><div class="panel-title"><span>Staff account</span></div>
      <p class="muted">You are signed in as ${esc(humanize(u.role).toLowerCase())}${u.position ? `, ${esc(u.position)}` : ''}.</p>
      <p class="small ${u.totpEnabled ? '' : 'muted'}" style="margin-top:8px">${icon(u.totpEnabled ? 'shield' : 'info', 14)} Two-step sign-in is ${u.totpEnabled ? '<b>on</b>' : 'off - turn it on under Sign-in &amp; security'}.</p>
      <a class="btn btn--sm" href="#/console" style="margin-top:14px">Open the console ${icon('arrowRight', 15)}</a></article>`}

    ${customer ? `<article class="acct-card acct-next">
      <div class="panel-title"><span>Next up</span></div>
      ${next ? `<a class="acct-next__trip" href="#/trip/${next.bookingId}">
          <span class="acct-next__art">${nextCar ? vehicleVisual(nextCar) : ''}</span>
          <span><b>${esc(nextCar?.model || 'Your trip')}</b><small>${fmtDate(next.pickupDate)} → ${fmtDate(next.returnDate)}</small>${statusChip(next.status)}</span>
          <i>${icon('arrowRight', 16)}</i>
        </a>` : `<p class="muted">No trips planned.</p><a class="btn btn--sm" href="#/explore" style="margin-top:14px">Find a car ${icon('arrowRight', 15)}</a>`}
    </article>` : ''}

    <article class="acct-card acct-links">
      <div class="panel-title"><span>Shortcuts</span></div>
      <nav>
        ${customer ? `<a href="#/trips">${icon('route', 18)}<span>My trips</span><em>${trips.length}</em></a>` : ''}
        <a href="#/saved">${icon('heart', 18)}<span>Saved cars</span><em>${saved.list().length}</em></a>
        <a href="#/compare">${icon('layers', 18)}<span>Compare</span><em>${compare.list().length}</em></a>
        <a href="#/notifications">${icon('bell', 18)}<span>Notifications</span><em>${store.unread || ''}</em></a>
        <a href="#/privacy">${icon('shield', 18)}<span>Privacy notice</span></a>
      </nav>
    </article>`;

  // ---------- the settings panels ----------
  const panelHost = $('#acct-panel', el);
  const panels = {
    profile: profilePanel,
    security: securityPanel,
    alerts: alertsPanel,
    data: dataPanel,
  };

  function profilePanel() {
    panelHost.innerHTML = `
      <form class="acct-form" id="acct-profile" novalidate>
        <div class="acct-form__head"><h2 class="title-md">Profile</h2>
          <p class="muted small">Your email is how you sign in and your date of birth decides driving age, so a branch changes those for you.</p></div>
        <div class="form-grid">
          ${field({ name: 'firstName', label: 'First name', value: u.firstName || '', required: true, autocomplete: 'given-name' })}
          ${field({ name: 'lastName', label: 'Last name', value: u.lastName || '', required: true, autocomplete: 'family-name' })}
          ${field({ name: 'phoneNumber', label: 'Phone', type: 'tel', value: u.phoneNumber || '', autocomplete: 'tel' })}
          ${field({ name: 'address', label: 'Address', value: u.address || '', autocomplete: 'street-address' })}
          ${customer ? `
          ${field({ name: 'drivingLicenseNumber', label: 'Driving licence no.', value: u.drivingLicenseNumber || '', required: true })}
          ${field({ name: 'licenseExpiryDate', label: 'Licence expiry', type: 'date', value: u.licenseExpiryDate || '', min: addDays(1), required: true })}
          <p class="muted small span-2">${icon('info', 14)} A new licence number or expiry is checked again at the counter before your next booking.</p>` : ''}
        </div>
        <div id="acct-profile-error"></div>
        <div class="acct-form__acts"><button class="btn" type="submit">Save changes</button></div>
      </form>`;
    const form = $('#acct-profile', panelHost);
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (!validateForm(form)) return;
      const box = $('#acct-profile-error', panelHost);
      box.innerHTML = '';
      const values = readForm(form);
      const btn = form.querySelector('[type=submit]');
      const licenceChanged = customer && (values.drivingLicenseNumber !== u.drivingLicenseNumber || values.licenseExpiryDate !== u.licenseExpiryDate);
      if (licenceChanged && u.licenseVerified) {
        const ok = await confirmDialog({
          title: 'Change your licence details?',
          text: 'Your licence will need to be checked again at the counter before your next booking. Bookings already approved are not affected.',
          confirmLabel: 'Change them',
        });
        if (!ok) return;
      }
      btn.disabled = true; btn.classList.add('is-busy');
      try {
        u = await api.me.update(values);
        store.user = { ...store.user, ...u };
        toast('Profile saved', 'success');
        navigate('/account');
      } catch (err) {
        box.innerHTML = formError(errorSummary(err, showFieldErrors(form, err)));
      } finally {
        if (btn.isConnected) { btn.disabled = false; btn.classList.remove('is-busy'); }
      }
    });
  }

  async function securityPanel() {
    const sessions = await api.me.sessions().catch(() => ({ signedIn: 1 }));
    panelHost.innerHTML = `
      <div class="acct-split">
        <form class="acct-form" id="acct-password" novalidate>
          <div class="acct-form__head"><h2 class="title-md">Change password</h2>
            <p class="muted small">Every other device you are signed in on is signed out when it changes.</p></div>
          <div class="form-grid">
            ${field({ name: 'currentPassword', label: 'Current password', type: 'password', required: true, span: true, autocomplete: 'current-password' })}
            ${field({ name: 'newPassword', label: 'New password', type: 'password', required: true, minlength: 8, maxlength: 72, autocomplete: 'new-password', hint: 'Letters and numbers, 8 or more' })}
            ${field({ name: 'confirm', label: 'Type it again', type: 'password', required: true, autocomplete: 'new-password' })}
          </div>
          <div id="acct-password-error"></div>
          <div class="acct-form__acts"><button class="btn" type="submit">Change password</button></div>
        </form>

        <div class="acct-form">
          <div class="acct-form__head"><h2 class="title-md">Signed-in devices</h2>
            <p class="muted small">You are signed in on <b>${sessions.signedIn || 1}</b> ${sessions.signedIn === 1 ? 'device' : 'devices'}, including this one.</p></div>
          <button class="btn btn--tonal" type="button" id="acct-end-others" ${sessions.signedIn > 1 ? '' : 'disabled'}>${icon('logout', 15)} Sign out everywhere else</button>

          ${staffish ? `<div class="acct-2fa" id="acct-2fa">
            <div class="acct-form__head" style="margin-top:28px"><h2 class="title-md">Two-step sign-in</h2>
              <p class="muted small">A six-digit code from an authenticator app (Google or Microsoft Authenticator, 1Password…) on top of your password.</p></div>
            ${u.totpEnabled
              ? `<p class="status status--green">On</p>
                 <button class="btn btn--sm btn--outline" type="button" id="acct-2fa-off" style="margin-top:12px">Turn it off</button>`
              : '<button class="btn" type="button" id="acct-2fa-on">Set it up</button>'}
          </div>` : ''}
        </div>
      </div>`;

    const form = $('#acct-password', panelHost);
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (!validateForm(form)) return;
      const v = readForm(form);
      const box = $('#acct-password-error', panelHost);
      box.innerHTML = '';
      if (v.newPassword !== v.confirm) { markField(form.elements.confirm, 'The two new passwords are not the same.'); form.elements.confirm.focus(); return; }
      const btn = form.querySelector('[type=submit]');
      btn.disabled = true; btn.classList.add('is-busy');
      try {
        const r = await api.me.changePassword(v.currentPassword, v.newPassword);
        toast(r.otherSessionsEnded ? `Password changed — ${r.otherSessionsEnded} other device(s) signed out` : 'Password changed', 'success');
        form.reset();
      } catch (err) {
        box.innerHTML = formError(errorSummary(err, showFieldErrors(form, err)));
      } finally {
        btn.disabled = false; btn.classList.remove('is-busy');
      }
    });
    $('#acct-end-others', panelHost).addEventListener('click', async (e) => {
      if (await runAction(e.currentTarget, () => api.me.endOtherSessions(), 'Signed out everywhere else')) securityPanel();
    });

    $('#acct-2fa-on', panelHost)?.addEventListener('click', async (e) => {
      let setup;
      try { setup = await api.auth.twoFactorSetup(); } catch (err) { toast(err.message, 'error'); return; }
      const grouped = setup.secret.replace(/(.{4})/g, '$1 ').trim();
      const done = await openDialog({
        title: 'Set up two-step sign-in',
        subtitle: 'Add Axle to your authenticator app, then type the code it shows.',
        submitLabel: 'Turn it on',
        body: `
          <ol class="steps-mini" style="display:grid;gap:12px">
            <li><span>In your app, add an account and choose <b>enter a setup key</b>.</span></li>
            <li><span>Type this key (spaces don't matter):</span></li>
          </ol>
          <p class="totp-key"><code>${esc(grouped)}</code></p>
          <p class="muted small">On a phone, you can open it straight in the app: <a href="${esc(setup.otpauthUri)}">${icon('external', 13)} add to authenticator</a></p>
          ${field({ name: 'code', label: 'Six-digit code from the app', required: true, autocomplete: 'one-time-code' })}`,
        onSubmit: (vals) => api.auth.twoFactorEnable(vals.code),
      });
      if (done) {
        toast('Two-step sign-in is on. You will be asked for a code at every sign-in.', 'success');
        u = await api.me.get();
        securityPanel();
      }
    });
    $('#acct-2fa-off', panelHost)?.addEventListener('click', async () => {
      const done = await openDialog({
        title: 'Turn off two-step sign-in?',
        subtitle: 'Your account will be protected by your password alone.',
        submitLabel: 'Turn it off',
        tone: 'danger',
        body: field({ name: 'password', label: 'Your password', type: 'password', required: true, span: true, autocomplete: 'current-password' }),
        onSubmit: (vals) => api.auth.twoFactorDisable(vals.password),
      });
      if (done) { toast('Two-step sign-in is off', 'success'); u = await api.me.get(); securityPanel(); }
    });
  }

  async function alertsPanel() {
    panelHost.innerHTML = skeletons(1, '');
    const prefs = await api.me.preferences().catch(() => []);
    panelHost.innerHTML = `
      <form class="acct-form" id="acct-prefs">
        <div class="acct-form__head"><h2 class="title-md">Email updates</h2>
          <p class="muted small">Every update always appears under the bell. Choose which ones are emailed to ${esc(u.email)} as well.
          ${u.emailVerified === false ? '<br><b>Emails start once your address is confirmed.</b>' : ''}</p></div>
        <div class="pref-list">
          ${prefs.map((p) => `<div class="pref">
            <div><b>${esc(humanize(p.category))}</b><small>${esc(p.label || '')}</small></div>
            <label class="switch"><input type="checkbox" name="${esc(p.category)}"${p.email ? ' checked' : ''}><span class="switch__track"><i></i></span> Email</label>
            <label class="switch is-disabled" title="Text messages need an SMS provider to be connected"><input type="checkbox" disabled${p.sms ? ' checked' : ''}><span class="switch__track"><i></i></span> SMS</label>
          </div>`).join('')}
        </div>
        <p class="muted small">${icon('info', 14)} Text messages will be offered once an SMS provider is connected.</p>
        <div class="acct-form__acts"><button class="btn" type="submit">Save</button></div>
      </form>`;
    const form = $('#acct-prefs', panelHost);
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const body = prefs.map((p) => ({ category: p.category, label: p.label, email: form.elements[p.category].checked, sms: p.sms }));
      await runAction(form.querySelector('[type=submit]'), () => api.me.savePreferences(body), 'Email updates saved');
    });
  }

  function dataPanel() {
    panelHost.innerHTML = `
      <div class="acct-split">
        <div class="acct-form">
          <div class="acct-form__head"><h2 class="title-md">A copy of your data</h2>
            <p class="muted small">Everything we hold about you - account, bookings, payments, messages and the history of changes - as one file.</p></div>
          <button class="btn btn--tonal" type="button" id="acct-export">${icon('download', 15)} Download my data</button>
          <div class="acct-form__head" style="margin-top:28px"><h2 class="title-md">This device</h2>
            <p class="muted small">Your compare list and recently viewed cars live in this browser only.</p></div>
          <div class="acct-device__acts">
            <button class="btn btn--sm btn--tonal" type="button" data-clear="recent">Clear recently viewed</button>
            <button class="btn btn--sm btn--tonal" type="button" data-clear="compare">Clear compare list</button>
          </div>
        </div>
        ${customer ? `<div class="acct-form acct-danger">
          <div class="acct-form__head"><h2 class="title-md">Erase my account</h2>
            <p class="muted small">Removes your name, contact details, licence and saved cars, and signs you out everywhere.
            Bookings and payments are kept - without your name - for as long as the law requires.
            It cannot be undone. You need no bookings open and nothing left to pay.</p></div>
          <button class="btn btn--outline" type="button" id="acct-erase">${icon('trash', 15)} Erase my account</button>
        </div>` : ''}
      </div>`;

    $('#acct-export', panelHost).addEventListener('click', async (e) => {
      const btn = e.currentTarget;
      btn.disabled = true; btn.classList.add('is-busy');
      try {
        const data = await api.me.exportData();
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `axle-my-data-${today()}.json`;
        document.body.appendChild(a); a.click(); a.remove();
        setTimeout(() => URL.revokeObjectURL(a.href), 2000);
        toast('Your data file is ready', 'success');
      } catch (err) {
        toast(err.message, 'error');
      } finally {
        btn.disabled = false; btn.classList.remove('is-busy');
      }
    });
    $('#acct-erase', panelHost)?.addEventListener('click', async () => {
      const done = await openDialog({
        title: 'Erase your account?',
        subtitle: 'This cannot be undone.',
        submitLabel: 'Erase my account',
        tone: 'danger',
        body: `<p class="muted">Your personal details are removed for good and you are signed out. Type your password to confirm.</p>
          ${field({ name: 'password', label: 'Password', type: 'password', required: true, span: true, autocomplete: 'current-password' })}`,
        onSubmit: (vals) => api.me.erase(vals.password),
      });
      if (done) {
        store.user = null;
        toast('Your account has been erased', 'success');
        navigate('/explore');
      }
    });
  }

  function show(key) {
    $$('[data-acct-tab]', el).forEach((b) => {
      const on = b.dataset.acctTab === key;
      b.classList.toggle('is-active', on);
      b.setAttribute('aria-selected', String(on));
    });
    panels[key]();
  }
  el.addEventListener('click', (e) => {
    const tab = e.target.closest('[data-acct-tab]');
    if (tab) { show(tab.dataset.acctTab); return; }
    const c = e.target.closest('[data-clear]');
    if (!c) return;
    if (c.dataset.clear === 'compare') compare.clear();
    if (c.dataset.clear === 'recent') { try { localStorage.removeItem('axle.recent.v1'); } catch { /* ignore */ } window.dispatchEvent(new CustomEvent('prefs:change', { detail: { key: 'recent' } })); }
    toast('Cleared', 'success');
  });
  show('profile');
}

// ==================================================================
// Not found
// ==================================================================
export async function renderNotFound({ el, path }) {
  el.innerHTML = `
    <section class="nf">
      <p class="nf__code" aria-hidden="true">404</p>
      <p class="eyebrow rule">Wrong turn</p>
      <h1 class="display ph__title">This road doesn't go anywhere.</h1>
      <p class="lede">There is no page at <code>#/${esc(path)}</code>. It may have moved, or the link was mistyped.</p>
      <div class="nf__acts">
        <a class="btn btn--lg" href="#/explore">Back to the collection ${icon('arrowRight', 17)}</a>
        <button class="btn btn--lg btn--tonal" type="button" data-spotlight>${icon('search', 16)} Search</button>
      </div>
    </section>
    ${footer()}`;
}

