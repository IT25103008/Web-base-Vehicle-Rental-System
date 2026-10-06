// ============================================================
// Building blocks shared by the console screens.
// ============================================================
import { api } from '../api.js';
import {
  esc, icon, number, money, isoDate, fmtDate, rentalDays, carArt, vehicleVisual, field, checkbox, openDialog,
} from '../ui.js';

export function consoleHead({ eyebrow = '', title, text = '', actions = '' }) {
  return `<header class="console-head">
    <div>
      ${eyebrow ? `<p class="eyebrow">${esc(eyebrow)}</p>` : ''}
      <h1 class="title-lg" style="margin-top:${eyebrow ? '8px' : '0'}">${esc(title)}</h1>
      ${text ? `<p>${esc(text)}</p>` : ''}
    </div>
    <div class="console-head__actions">${actions}</div>
  </header>`;
}

/** Segmented control. options: [[key, label, count?]] */
export function segmented(options, active, attr = 'data-seg') {
  return `<div class="seg" role="tablist">${options.map(([key, label, count]) => `
    <button type="button" role="tab" ${attr}="${esc(key)}" class="${key === active ? 'is-active' : ''}" aria-selected="${key === active}">
      ${esc(label)}${count !== undefined ? ` <span class="count">${count}</span>` : ''}
    </button>`).join('')}</div>`;
}

export function searchBox(placeholder = 'Search', value = '') {
  return `<label class="search-input">${icon('search', 18)}
    <input type="search" data-search maxlength="100" placeholder="${esc(placeholder)}" value="${esc(value)}" aria-label="${esc(placeholder)}">
  </label>`;
}

/** Responsive data list. cols = CSS grid-template-columns. */
export function dataList({ cols, headers, rows }) {
  return `<div class="dlist" style="--cols:${cols}">
    <div class="dlist__head">${headers.map((h) => `<div>${esc(h)}</div>`).join('')}</div>
    ${rows.join('')}
  </div>`;
}

export const row = (cells, index = 0) =>
  `<div class="drow" style="animation-delay:${Math.min(index, 14) * 25}ms">${cells.join('')}</div>`;

export const cell = (label, html, cls = '') =>
  `<div class="cell${cls ? ` ${cls}` : ''}"><span class="cell-label">${esc(label)}</span>${html}</div>`;

export const actionsCell = (html) => `<div class="cell cell--actions">${html}</div>`;

export function vehicleCell(v, fallbackId) {
  if (!v) return `<b>Vehicle #${esc(fallbackId)}</b><small>Removed from fleet</small>`;
  return `<div class="with-art">
    <div class="mini-car">${vehicleVisual(v, { compact: true })}</div>
    <div><b>${esc(v.model)}</b><small>${esc(v.plateNumber)}${v.category ? ` · ${esc(v.category)}` : ''}</small></div>
  </div>`;
}

export function personCell(u, fallbackId) {
  if (!u) return `<b>User #${esc(fallbackId ?? '—')}</b>`;
  return `<b>${esc(u.fullName)}</b><small>${esc(u.email)}</small>`;
}

export function datesCell(from, to) {
  const n = rentalDays(from, to);
  return `<b>${fmtDate(from)}</b><small>→ ${fmtDate(to)} · ${n} night${n === 1 ? '' : 's'}</small>`;
}

export function vehicleOptions(vehicles, { placeholder = 'Select a vehicle', filter } = {}) {
  const list = filter ? vehicles.filter(filter) : vehicles;
  return [{ value: '', label: placeholder },
    ...list.map((v) => ({ value: v.vehicleId, label: `${v.model} · ${v.plateNumber}` }))];
}

export function branchSelectOptions(branches, { placeholder, activeOnly = false } = {}) {
  const list = activeOnly ? branches.filter((b) => b.status === 'ACTIVE') : branches;
  const opts = list.map((b) => ({ value: b.branchId, label: `${b.name}${b.status !== 'ACTIVE' ? ' (inactive)' : ''}` }));
  return placeholder ? [{ value: '', label: placeholder }, ...opts] : opts;
}

export const FUEL_LEVELS = ['Full', '3/4', '1/2', '1/4', 'Empty'].map((f) => ({ value: f, label: f }));
export const SEVERITIES = [
  { value: 'MINOR', label: 'Minor' },
  { value: 'MODERATE', label: 'Moderate' },
  { value: 'SEVERE', label: 'Severe' },
];

export function bookingSummary(b, v, customer) {
  return `<div class="notice">
    <div class="mini-car" style="width:92px">${v ? carArt(v) : icon('car', 28)}</div>
    <div>
      <b style="display:block;color:var(--ink)">${esc(v?.model || `Vehicle #${b.vehicleId}`)} · #${b.bookingId}</b>
      <span>${esc(customer?.fullName || `Customer #${b.customerId}`)} · ${fmtDate(b.pickupDate)} → ${fmtDate(b.returnDate)}</span>
    </div>
  </div>`;
}

/** Hand the vehicle to the customer (booking APPROVED -> ACTIVE_RENTAL). */
export function pickupDialog(b, v, customer, payment) {
  const current = v?.mileage ?? 0;
  const unpaid = payment && payment.status !== 'PAID';
  return openDialog({
    title: 'Hand over vehicle',
    subtitle: 'Record the condition as the customer drives away.',
    submitLabel: 'Confirm pick-up',
    body: `
      ${bookingSummary(b, v, customer)}
      ${unpaid ? `<div class="notice notice--red">${icon('alert', 18)}<span>This booking has not been paid (${money(payment.amount)} outstanding). Collect payment on the Payments screen first — the handover will be refused until it is settled.</span></div>` : ''}
      <div class="form-grid">
        ${field({ name: 'mileageAtEvent', label: 'Odometer (km)', type: 'number', value: current, min: current, required: true, hint: `Last recorded ${number(current)} km` })}
        ${field({ name: 'fuelLevel', label: 'Fuel level', type: 'select', value: 'Full', options: FUEL_LEVELS, required: true })}
        ${field({ name: 'conditionNotes', label: 'Condition notes', type: 'textarea', span: true })}
      </div>`,
    onSubmit: (values) => api.handovers.pickup(b.bookingId, values),
  });
}

/** Receive the vehicle back (ACTIVE_RENTAL -> COMPLETED), optionally filing damage. */
export function returnDialog(b, v, customer) {
  const current = v?.mileage ?? 0;
  return openDialog({
    title: 'Receive vehicle',
    subtitle: 'A late return adds a surcharge, and any repair estimate is added to the bill.',
    submitLabel: 'Complete return',
    body: `
      ${bookingSummary(b, v, customer)}
      <div class="form-grid">
        ${field({ name: 'returnMileage', label: 'Odometer (km)', type: 'number', value: current, min: current, required: true, hint: `Pick-up reading ${number(current)} km` })}
        ${field({ name: 'fuelLevel', label: 'Fuel level', type: 'select', value: 'Full', options: FUEL_LEVELS })}
        ${field({ name: 'conditionNotes', label: 'Condition notes', type: 'textarea', span: true })}
        ${checkbox({ name: 'damageFound', label: 'Damage found', description: 'Files a damage report. Moderate or severe damage keeps the vehicle off the road until the report is resolved.' })}
        <div class="span-2 form-grid" data-damage hidden>
          ${field({ name: 'damageDescription', label: 'Describe the damage', type: 'textarea', span: true })}
          ${field({ name: 'damageSeverity', label: 'Severity', type: 'select', value: 'MINOR', options: SEVERITIES })}
          ${field({ name: 'damageEstimatedCost', label: 'Repair estimate', type: 'number', min: 0, step: '0.01', value: '0', hint: 'Added to the customer bill' })}
          ${field({ name: 'damageDate', label: 'Damage occurred', type: 'date', value: isoDate(), max: isoDate(), span: true })}
        </div>
      </div>`,
    onOpen(form) {
      const toggle = form.elements.damageFound;
      const box = form.querySelector('[data-damage]');
      const sync = () => {
        box.hidden = !toggle.checked;
        form.elements.damageDescription.required = toggle.checked;
        form.elements.damageDescription.disabled = !toggle.checked;
        form.elements.damageSeverity.disabled = !toggle.checked;
        form.elements.damageEstimatedCost.disabled = !toggle.checked;
        form.elements.damageDate.disabled = !toggle.checked;
      };
      toggle.addEventListener('change', sync);
      sync();
    },
    onSubmit: (values) => api.handovers.returnVehicle(b.bookingId, {
      returnMileage: values.returnMileage,
      fuelLevel: values.fuelLevel,
      conditionNotes: values.conditionNotes,
      damageDescription: values.damageFound ? values.damageDescription : null,
      damageSeverity: values.damageFound ? values.damageSeverity : null,
      damageEstimatedCost: values.damageFound ? Number(values.damageEstimatedCost || 0) : null,
      damageDate: values.damageFound ? values.damageDate : null,
    }),
  });
}

export function filterByText(items, text, pick) {
  const q = (text || '').trim().toLowerCase();
  if (!q) return items;
  return items.filter((item) => pick(item).filter(Boolean).join(' ').toLowerCase().includes(q));
}

// ---------- long lists (C1) ----------
/** Previous / next under a server-paged list. p is a PageResponse. */
export function pager(p) {
  if (!p || !p.total) return '';
  if (p.total <= p.size) return `<p class="pager"><span class="pager__info">${p.total} in total</span></p>`;
  const first = (p.page - 1) * p.size + 1;
  const last = Math.min(p.total, p.page * p.size);
  return `<nav class="pager" aria-label="Pages">
    <span class="pager__info">${first}&ndash;${last} of ${p.total}</span>
    <div class="pager__btns">
      <button type="button" class="icon-btn icon-btn--sm" data-page="${p.page - 1}" ${p.page <= 1 ? 'disabled' : ''} aria-label="Previous page">${icon('chevronLeft', 16)}</button>
      <span class="pager__num">Page ${p.page} of ${p.pages}</span>
      <button type="button" class="icon-btn icon-btn--sm" data-page="${p.page + 1}" ${p.page >= p.pages ? 'disabled' : ''} aria-label="Next page">${icon('chevronRight', 16)}</button>
    </div>
  </nav>`;
}

/** Every page of a paged endpoint, for exports. Stops at a sensible limit. */
export async function fetchAllPages(fetchPage, query, max = 5000) {
  const out = [];
  for (let page = 1; ; page++) {
    const p = await fetchPage({ ...query, page, size: 200 });
    out.push(...p.items);
    if (page >= p.pages || out.length >= max) return out;
  }
}

// ---------- exports (C4) ----------
const csvCell = (v) => {
  let s = v == null ? '' : String(v);
  // A leading = + - @ turns a cell into a formula in a spreadsheet.
  if (/^[=+\-@\t\r]/.test(s)) s = `'${s}`;
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
};

/** Save rows as a CSV the user opens in a spreadsheet. */
export function downloadCsv(filename, headers, rows) {
  const text = [headers, ...rows].map((r) => r.map(csvCell).join(',')).join('\r\n');
  const blob = new Blob(['﻿', text], { type: 'text/csv;charset=utf-8' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `${filename}-${isoDate()}.csv`;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}

export const exportButtons = () => `
  <button class="btn btn--tonal btn--sm" type="button" data-export="csv">${icon('download', 16)} Export CSV</button>
  <button class="btn btn--tonal btn--sm" type="button" data-export="print">${icon('printer', 16)} Print</button>`;

/**
 * Print a self-contained page through a hidden frame, so the console itself
 * is never what ends up on paper.
 */
export function printHtml(title, body) {
  const frame = document.createElement('iframe');
  frame.setAttribute('aria-hidden', 'true');
  frame.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0';
  document.body.append(frame);
  const doc = frame.contentDocument;
  doc.open();
  doc.write(`<!doctype html><html><head><meta charset="utf-8"><title>${esc(title)}</title><style>
    *{box-sizing:border-box}body{font:12px/1.45 system-ui,-apple-system,"Segoe UI",sans-serif;color:#111;margin:28px}
    h1{font-size:20px;margin:0 0 4px}h2{font-size:12px;margin:22px 0 8px;text-transform:uppercase;letter-spacing:.06em}
    .muted{color:#666}table{width:100%;border-collapse:collapse;margin-top:12px}
    th,td{text-align:left;padding:6px 8px;border-bottom:1px solid #ddd;vertical-align:top}
    th{font-size:11px;text-transform:uppercase;letter-spacing:.05em;color:#555}
    .grid{display:grid;grid-template-columns:repeat(2,1fr);gap:4px 28px}
    .grid div{display:flex;justify-content:space-between;gap:12px;border-bottom:1px solid #eee;padding:5px 0;min-height:26px}
    .grid span{color:#666}.box{border:1px solid #bbb;border-radius:6px;height:80px;margin-top:6px}
    .fuel{display:flex;gap:18px;margin-top:6px}
    .fuel span:before{content:"";display:inline-block;width:11px;height:11px;border:1px solid #333;margin-right:5px;vertical-align:-1px}
    .sign{display:grid;grid-template-columns:1fr 1fr;gap:40px;margin-top:46px}.sign div{border-top:1px solid #333;padding-top:6px}
    header{display:flex;justify-content:space-between;align-items:flex-start;gap:20px;border-bottom:2px solid #111;padding-bottom:10px;margin-bottom:10px}
    @page{margin:14mm}
  </style></head><body>${body}</body></html>`);
  doc.close();
  const go = () => {
    frame.contentWindow.focus();
    frame.contentWindow.print();
    setTimeout(() => frame.remove(), 1500);
  };
  if (doc.readyState === 'complete') setTimeout(go, 60); else frame.onload = go;
}

const printedAt = () => `Printed ${new Date().toLocaleString('en-GB')}`;

/** A table for printing. Cells are already-escaped HTML. */
export function printTable(title, subtitle, headers, rows) {
  printHtml(title, `<header><div><h1>${esc(title)}</h1><div class="muted">${esc(subtitle)}</div></div>
    <div class="muted">${printedAt()}</div></header>
    <table><thead><tr>${headers.map((h) => `<th>${esc(h)}</th>`).join('')}</tr></thead>
    <tbody>${rows.map((r) => `<tr>${r.map((c) => `<td>${c}</td>`).join('')}</tr>`).join('')}</tbody></table>
    <p class="muted" style="margin-top:12px">${rows.length} row${rows.length === 1 ? '' : 's'}</p>`);
}

/**
 * The sheet that goes with the car at the counter (C5): who, what, when, and
 * blanks for the odometer, fuel, condition and both signatures.
 */
export function printHandoverSheet(kind, b, v, u, branch, payment) {
  const pickup = kind === 'pickup';
  const kv = (k, val) => `<div><span>${esc(k)}</span><b>${val}</b></div>`;
  printHtml(`${pickup ? 'Pick-up' : 'Return'} sheet #${b.bookingId}`, `
    <header><div><h1>${pickup ? 'Vehicle pick-up' : 'Vehicle return'}</h1>
      <div class="muted">Booking #${b.bookingId}${branch ? ` &middot; ${esc(branch.name)}` : ''}</div></div>
      <div class="muted">${printedAt()}</div></header>
    <h2>Customer</h2>
    <div class="grid">
      ${kv('Name', esc(u?.fullName || `Customer #${b.customerId}`))}
      ${kv('Email', esc(u?.email || ''))}
      ${kv('Phone', esc(u?.phoneNumber || ''))}
      ${kv('Licence no.', esc(u?.drivingLicenseNumber || ''))}
    </div>
    <h2>Vehicle</h2>
    <div class="grid">
      ${kv('Model', esc(v?.model || `Vehicle #${b.vehicleId}`))}
      ${kv('Plate', esc(v?.plateNumber || ''))}
      ${kv('Last odometer', v?.mileage != null ? `${number(v.mileage)} km` : '')}
      ${kv('Category', esc(v?.category || ''))}
    </div>
    <h2>Trip</h2>
    <div class="grid">
      ${kv('Pick-up', fmtDate(b.pickupDate))}
      ${kv('Due back', fmtDate(b.returnDate))}
      ${kv('Nights', rentalDays(b.pickupDate, b.returnDate))}
      ${kv('Quoted', money(b.estimatedCost))}
      ${kv('Payment', esc(payment ? payment.status : ''))}
      ${kv('Amount', payment ? money(payment.amount) : '')}
    </div>
    ${b.specialRequests ? `<h2>Special requests</h2><p>${esc(b.specialRequests)}</p>` : ''}
    <h2>At the counter</h2>
    <div class="grid">${kv('Odometer (km)', '')}${kv('Date and time', '')}</div>
    <p style="margin-top:12px">Fuel</p>
    <div class="fuel"><span>Full</span><span>3/4</span><span>1/2</span><span>1/4</span><span>Empty</span></div>
    <p style="margin-top:14px">Condition, scratches and damage</p><div class="box"></div>
    <div class="sign"><div>Customer signature</div><div>Staff signature</div></div>`);
}
