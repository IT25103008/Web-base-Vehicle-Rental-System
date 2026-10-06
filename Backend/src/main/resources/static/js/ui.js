// ============================================================
// Shared UI helpers: escaping, formatting, icons, status chips,
// form fields, dialogs, snackbars and the vehicle illustration.
// ============================================================

import { FIELD_DEFAULTS, RULES, problemWith } from './rules.js';

/** Escape any value before putting it into innerHTML. */
export const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (c) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}[c]));

export const $ = (selector, root = document) => root.querySelector(selector);
export const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

// ---------- formatting ----------
export function money(value, { decimals = false } = {}) {
  if (value === null || value === undefined || value === '') return '—';
  const n = Number(value);
  return 'LKR ' + n.toLocaleString('en-US', {
    minimumFractionDigits: decimals ? 2 : 0,
    maximumFractionDigits: decimals ? 2 : (Number.isInteger(n) ? 0 : 2),
  });
}

export function number(value) {
  if (value === null || value === undefined) return '—';
  return Number(value).toLocaleString('en-US');
}

function parseDate(value) {
  if (!value) return null;
  const s = String(value);
  return new Date(s.length === 10 ? `${s}T00:00:00` : s);
}

export function fmtDate(value) {
  const d = parseDate(value);
  if (!d || Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function fmtShortDate(value) {
  const d = parseDate(value);
  if (!d || Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' });
}

export function fmtDateTime(value) {
  const d = parseDate(value);
  if (!d || Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString('en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
}

export function relativeTime(value) {
  const d = parseDate(value);
  if (!d) return '';
  const diff = (Date.now() - d.getTime()) / 1000;
  if (diff < 60) return 'Just now';
  if (diff < 3600) return `${Math.floor(diff / 60)} min ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} h ago`;
  if (diff < 86400 * 7) return `${Math.floor(diff / 86400)} d ago`;
  return fmtDate(value);
}

export function fmtTime(value) {
  return value ? String(value).slice(0, 5) : '—';
}

/** yyyy-mm-dd in the browser's local time zone */
export function isoDate(date = new Date()) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function addDays(days, from = new Date()) {
  const d = new Date(from);
  d.setDate(d.getDate() + days);
  return isoDate(d);
}

/** Same rule as the backend's CostCalculator: nights between dates, minimum 1. */
export function rentalDays(pickup, ret) {
  const a = parseDate(pickup);
  const b = parseDate(ret);
  if (!a || !b) return 0;
  const days = Math.round((b - a) / 86400000);
  return days < 1 ? 1 : days;
}

export function humanize(value) {
  if (!value) return '—';
  const s = String(value).replace(/_/g, ' ').toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

export function initials(name = '') {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('') || '?';
}

export function debounce(fn, wait = 200) {
  let t;
  return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), wait); };
}

// ---------- icons (24px grid, 1.8 stroke) ----------
const ICONS = {
  search: '<circle cx="11" cy="11" r="6.5"/><path d="m20 20-4.2-4.2"/>',
  calendar: '<rect x="3.5" y="5" width="17" height="15.5" rx="3"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
  pin: '<path d="M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z"/><circle cx="12" cy="9.5" r="2.5"/>',
  car: '<path d="M4 16.5V13l2-5.2A2 2 0 0 1 7.9 6.5h8.2a2 2 0 0 1 1.9 1.3L20 13v3.5"/><path d="M3 16.5h18v1.5a1 1 0 0 1-1 1h-1.5a1 1 0 0 1-1-1v-.5h-11v.5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z"/><path d="M4.5 12.5h15"/><circle cx="7.5" cy="14.5" r=".6"/><circle cx="16.5" cy="14.5" r=".6"/>',
  seat: '<path d="M7 4h5a2 2 0 0 1 2 2v7H9a2 2 0 0 1-2-2z"/><path d="M5 13h11a2 2 0 0 1 2 2v2H7a2 2 0 0 1-2-2zM8 17v3M16 17v3"/>',
  fuel: '<path d="M5 20V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v15M3.5 20h13"/><path d="M8 7.5h4"/><path d="M15 9h2a1.5 1.5 0 0 1 1.5 1.5V16a1.5 1.5 0 0 0 3 0V8.5L19 6"/>',
  gauge: '<path d="M4.5 17a8.5 8.5 0 1 1 15 0"/><path d="m12 13 3.5-4"/><circle cx="12" cy="13.5" r="1.3"/>',
  clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
  bell: '<path d="M6 10a6 6 0 1 1 12 0c0 5 2 6.5 2 6.5H4S6 15 6 10z"/><path d="M10 19.5a2 2 0 0 0 4 0"/>',
  user: '<circle cx="12" cy="8.5" r="4"/><path d="M4.5 20a7.5 7.5 0 0 1 15 0"/>',
  users: '<circle cx="9" cy="9" r="3.5"/><path d="M3 19.5a6 6 0 0 1 12 0"/><path d="M15.5 5.5a3.5 3.5 0 0 1 0 7M17.5 14.2a6 6 0 0 1 3.5 5.3"/>',
  logout: '<path d="M14 4h3.5A2.5 2.5 0 0 1 20 6.5v11a2.5 2.5 0 0 1-2.5 2.5H14"/><path d="M10 16.5 5.5 12 10 7.5M5.5 12H15"/>',
  arrowRight: '<path d="M5 12h14M13 6l6 6-6 6"/>',
  arrowLeft: '<path d="M19 12H5M11 6l-6 6 6 6"/>',
  arrowUpRight: '<path d="M7 17 17 7M9 7h8v8"/>',
  chevronDown: '<path d="m6 9 6 6 6-6"/>',
  chevronLeft: '<path d="m14.5 6-6 6 6 6"/>',
  chevronRight: '<path d="m9.5 6 6 6-6 6"/>',
  close: '<path d="M6 6l12 12M18 6 6 18"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
  edit: '<path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16z"/><path d="m13.5 6.5 4 4"/>',
  trash: '<path d="M4.5 7h15M10 11v6M14 11v6"/><path d="M6 7l1 12a2 2 0 0 0 2 1.8h6a2 2 0 0 0 2-1.8L18 7M9 7V5a1.5 1.5 0 0 1 1.5-1.5h3A1.5 1.5 0 0 1 15 5v2"/>',
  grid: '<rect x="3.5" y="3.5" width="7" height="7" rx="2"/><rect x="13.5" y="3.5" width="7" height="7" rx="2"/><rect x="3.5" y="13.5" width="7" height="7" rx="2"/><rect x="13.5" y="13.5" width="7" height="7" rx="3.5"/>',
  ticket: '<path d="M4 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v2.5a2.5 2.5 0 0 0 0 5V17a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-2.5a2.5 2.5 0 0 0 0-5z"/><path d="M14 5.5v2M14 11v2M14 16.5v2"/>',
  key: '<circle cx="8" cy="15" r="4"/><path d="m11 12 8.5-8.5M16 7l2.5 2.5M14 9l1.8 1.8"/>',
  wrench: '<path d="M14.5 6.5a4 4 0 0 1 5.3-2.3l-2.6 2.6 1 2 2 1 2.6-2.6a4 4 0 0 1-5.4 5.2L9 20.8a2 2 0 0 1-2.8 0l-.9-.9a2 2 0 0 1 0-2.8l8.4-8.4a4 4 0 0 1 .8-2.2z"/>',
  shield: '<path d="M12 3 5 6v5.5c0 4.5 3 7.8 7 9.5 4-1.7 7-5 7-9.5V6z"/><path d="m9 12 2 2 4-4"/>',
  alert: '<path d="M10.3 4.3 2.9 17.5A2 2 0 0 0 4.6 20.5h14.8a2 2 0 0 0 1.7-3L13.7 4.3a2 2 0 0 0-3.4 0z"/><path d="M12 9.5v4M12 17h.01"/>',
  wallet: '<path d="M4 7.5A2.5 2.5 0 0 1 6.5 5H18v3"/><rect x="4" y="8" width="16.5" height="11.5" rx="2.5"/><path d="M16 13.8h.01"/>',
  building: '<path d="M4 20.5V6a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2v14.5M15 10h3a2 2 0 0 1 2 2v8.5M2.5 20.5h19"/><path d="M8 8h3M8 12h3M8 16h3"/>',
  swap: '<path d="M4 8h14l-3.5-3.5M20 16H6l3.5 3.5"/>',
  doc: '<path d="M6.5 3.5h7L19 9v10a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 19V5a1.5 1.5 0 0 1 1.5-1.5z"/><path d="M13 3.5V9h6M8.5 13h7M8.5 16.5h5"/>',
  sparkle: '<path d="M12 3.5c.6 4.4 2.1 5.9 6.5 6.5-4.4.6-5.9 2.1-6.5 6.5-.6-4.4-2.1-5.9-6.5-6.5 4.4-.6 5.9-2.1 6.5-6.5zM18.5 15.5c.3 2 1 2.7 3 3-2 .3-2.7 1-3 3-.3-2-1-2.7-3-3 2-.3 2.7-1 3-3z"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5M12 8h.01"/>',
  refresh: '<path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3L19.5 9"/><path d="M19.5 4v5h-5"/>',
  phone: '<path d="M6.5 3.5h3l1.5 4-2 1.5a11 11 0 0 0 6 6l1.5-2 4 1.5v3a2 2 0 0 1-2 2A16 16 0 0 1 4.5 5.5a2 2 0 0 1 2-2z"/>',
  mail: '<rect x="3.5" y="5.5" width="17" height="13" rx="2.5"/><path d="m4 7 8 6 8-6"/>',
  bolt: '<path d="M13 3 5 13.5h6L10 21l8-10.5h-6z"/>',
  route: '<circle cx="6" cy="18" r="2.5"/><circle cx="18" cy="6" r="2.5"/><path d="M8.5 18H16a3 3 0 0 0 0-6H8a3 3 0 0 1 0-6h7.5"/>',
  lock: '<rect x="5" y="10.5" width="14" height="10" rx="2.5"/><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5"/>',
  receipt: '<path d="M6 3.5h12v17l-2.5-1.5-2 1.5-1.5-1.5-1.5 1.5-2-1.5L6 20.5z"/><path d="M9 8h6M9 11.5h6M9 15h3.5"/>',
  layers: '<path d="m12 4 8.5 4.5L12 13 3.5 8.5z"/><path d="m3.5 12.5 8.5 4.5 8.5-4.5M3.5 16.5 12 21l8.5-4.5"/>',
  history: '<path d="M4 12a8 8 0 1 0 2.4-5.7L4 8.5"/><path d="M4 4v4.5h4.5M12 8v4.5l3 1.8"/>',
  shieldAlert: '<path d="M12 3 5 6v5.5c0 4.5 3 7.8 7 9.5 4-1.7 7-5 7-9.5V6z"/><path d="M12 8.5v4M12 15.5h.01"/>',
  flag: '<path d="M5 21V4.5M5 4.5h11l-2 4 2 4H5"/>',
  menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
  compass: '<circle cx="12" cy="12" r="8.5"/><path d="m15 9-2 4-4 2 2-4z"/>',
  star: '<path d="m12 4 2.4 5 5.6.8-4 3.9 1 5.5-5-2.7-5 2.7 1-5.5-4-3.9 5.6-.8z"/>',
  trend: '<path d="M4 16.5 9.5 11l3.5 3.5L20 7"/><path d="M15 7h5v5"/>',
  camera: '<path d="M4 8.5h3l1.5-2.5h7L17 8.5h3a1.5 1.5 0 0 1 1.5 1.5v8a1.5 1.5 0 0 1-1.5 1.5H4A1.5 1.5 0 0 1 2.5 18v-8A1.5 1.5 0 0 1 4 8.5z"/><circle cx="12" cy="13.5" r="3.5"/>',
  heart: '<path d="M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z"/>',
  share: '<path d="M12 3.5v12M7.5 8 12 3.5 16.5 8"/><path d="M5 12.5v6A2 2 0 0 0 7 20.5h10a2 2 0 0 0 2-2v-6"/>',
  link: '<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1.2 1.2"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1.2-1.2"/>',
  download: '<path d="M12 4v11M7.5 10.5 12 15l4.5-4.5M5 19.5h14"/>',
  external: '<path d="M14 4.5h5.5V10M19.5 4.5 11 13"/><path d="M18 14v4.5a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 4 18.5v-11A1.5 1.5 0 0 1 5.5 6H10"/>',
  printer: '<path d="M7 9V3.5h10V9"/><rect x="3.5" y="9" width="17" height="8" rx="2"/><path d="M7 14h10v6.5H7z"/>',
  navigation: '<path d="M4 11 20 4l-7 16-2-7z"/>',
  filter: '<path d="M4 6h16M7 12h10M10 18h4"/>',
  command: '<path d="M9 6a3 3 0 1 0-3 3h12a3 3 0 1 0-3-3v12a3 3 0 1 0 3-3H6a3 3 0 1 0 3 3z"/>',
};

export function icon(name, size = 20) {
  const body = ICONS[name] || ICONS.info;
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${body}</svg>`;
}

// ---------- status chips ----------
const STATUS_TONE = {
  AVAILABLE: 'green', RESERVED: 'blue', RENTED: 'violet', UNDER_MAINTENANCE: 'amber', UNAVAILABLE: 'red',
  PENDING_APPROVAL: 'amber', APPROVED: 'blue', ACTIVE_RENTAL: 'violet', COMPLETED: 'green', REJECTED: 'red', CANCELLED: '', NO_SHOW: 'red',
  SCHEDULED: 'blue', IN_PROGRESS: 'amber',
  UNDER_REVIEW: 'amber', RESOLVED: 'green',
  SUBMITTED: 'blue',
  PAID: 'green', PENDING: 'amber',
  ACTIVE: 'green', INACTIVE: '', EXPIRED: 'red',
  CUSTOMER: 'blue', STAFF: 'violet', ADMINISTRATOR: '',
};

const STATUS_LABEL = {
  PENDING_APPROVAL: 'Awaiting approval',
  ACTIVE_RENTAL: 'On trip',
  NO_SHOW: 'No-show',
  UNDER_MAINTENANCE: 'In maintenance',
  UNDER_REVIEW: 'Under review',
};

export function statusChip(status) {
  if (!status) return '';
  const tone = STATUS_TONE[status];
  return `<span class="status${tone ? ` status--${tone}` : ''}">${esc(STATUS_LABEL[status] || humanize(status))}</span>`;
}

/** Hex colours for charts, aligned with the chip tones. */
export const TONE_HEX = {
  green: '#1F7A4D', blue: '#2A5F9E', violet: '#57499F', amber: '#8A6216', red: '#A63A32', '': '#A8A8AE',
};

/**
 * Charts stay monochrome: a saturated bar would be the only colour on an
 * otherwise black-and-white page and would read as an alert. Statuses are
 * separated by value instead, darkest first, and the legend carries the name.
 */
const CHART_HEX = {
  green: '#1d1d1f', blue: '#3f3f45', violet: '#62626a', amber: '#8e8e95',
  red: '#b4b4bb', '': '#d6d6dc',
};
export const statusColor = (status) => CHART_HEX[STATUS_TONE[status] ?? ''] || CHART_HEX[''];

// ---------- page states ----------
export function empty({ icon: iconName = 'sparkle', title, text = '', action = '' }) {
  return `<div class="empty">
    <div class="empty__icon">${icon(iconName, 28)}</div>
    <h3>${esc(title)}</h3>
    ${text ? `<p>${esc(text)}</p>` : ''}
    ${action}
  </div>`;
}

export function errorState(err, retryAction = 'retry') {
  return empty({
    icon: 'alert',
    title: 'Could not load this page',
    text: err?.message || 'Unexpected error',
    action: `<button class="btn btn--tonal" data-action="${retryAction}">${icon('refresh', 18)} Try again</button>`,
  });
}

export function skeletons(count = 6, cls = 'skeleton-grid') {
  return `<div class="${cls}">${'<div class="skeleton"></div>'.repeat(count)}</div>`;
}

// ---------- form fields ----------
/**
 * M3 outlined text field.
 * type: text | email | password | number | date | time | textarea | select
 * options: [{ value, label }] for select
 */
export function field({
  name, label, type = 'text', value = '', required = false, options = [],
  min, max, step, span = false, hint = '', autocomplete, minlength, maxlength, rule, counter,
  after, strictAfter = false, afterMsg, compact = false, disabled = false,
}) {
  // Limits and formats come from rules.js by field name (the same names the
  // server's request classes use), so every form gets them without repeating
  // them. Anything passed in explicitly wins.
  const d = FIELD_DEFAULTS[name] || {};
  const textual = !['number', 'date', 'time', 'select', 'checkbox'].includes(type);
  const numeric = type === 'number';
  if (textual) {
    maxlength ??= d.maxlength;
    minlength ??= d.minlength;
    rule ??= d.rule;
    counter ??= d.counter;
  }
  if (numeric) {
    min ??= d.min;
    max ??= d.max;
    step ??= d.step;
  }
  autocomplete ??= d.autocomplete;
  const r = rule ? RULES[rule] : null;
  const attrs = [
    `name="${esc(name)}"`,
    `id="f-${esc(name)}"`,
    required ? 'required' : '',
    min !== undefined ? `min="${esc(min)}"` : '',
    max !== undefined ? `max="${esc(max)}"` : '',
    step !== undefined ? `step="${esc(step)}"` : '',
    minlength ? `minlength="${esc(minlength)}"` : '',
    maxlength ? `maxlength="${esc(maxlength)}"` : '',
    autocomplete ? `autocomplete="${esc(autocomplete)}"` : '',
    r ? `data-rule="${esc(rule)}"` : '',
    r?.inputmode ? `inputmode="${esc(r.inputmode)}"` : '',
    r?.upper ? 'autocapitalize="characters" spellcheck="false"' : '',
    // "Must come after that other box": its min follows the other one (syncLinkedMins).
    after ? `data-after="${esc(after)}"` : '',
    after && strictAfter ? 'data-after-strict' : '',
    after && afterMsg ? `data-after-msg="${esc(afterMsg)}"` : '',
    disabled ? 'disabled' : '',
    type === 'number' ? 'data-type="number"' : '',
  ].filter(Boolean).join(' ');

  const staticLabel = ['date', 'time', 'select'].includes(type);
  let control;
  if (type === 'select') {
    control = `<select class="field__control" ${attrs}>${options.map((o) =>
      `<option value="${esc(o.value)}"${String(o.value) === String(value ?? '') ? ' selected' : ''}>${esc(o.label)}</option>`).join('')}</select>`;
  } else if (type === 'textarea') {
    control = `<textarea class="field__control" placeholder=" " ${attrs}>${esc(value)}</textarea>`;
  } else {
    control = `<input class="field__control" type="${esc(type)}" placeholder=" " value="${esc(value ?? '')}" ${attrs}>`;
  }
  return `<label class="field${staticLabel ? ' field--static' : ''}${compact ? ' field--compact' : ''}${span ? ' span-2' : ''}" for="f-${esc(name)}">
    ${control}
    <span class="field__label">${esc(label)}${required ? ' *' : ''}</span>
    ${hint ? `<small class="field__hint">${esc(hint)}</small>` : ''}
    ${counter && maxlength ? `<small class="field__count" aria-live="off">${String(value ?? '').length} / ${esc(maxlength)}</small>` : ''}
  </label>`;
}

// ---------- validation messages next to each box ----------
function fieldBox(el) {
  return el.closest('.field, .check') || el.parentElement;
}

/** Put a message under one control (or clear it with an empty message). */
export function markField(el, message) {
  const box = fieldBox(el);
  if (!box) return;
  let note = box.querySelector(':scope > .field__error');
  if (!message) {
    box.classList.remove('is-invalid');
    el.removeAttribute('aria-invalid');
    note?.remove();
    return;
  }
  box.classList.add('is-invalid');
  el.setAttribute('aria-invalid', 'true');
  if (!note) {
    note = document.createElement('small');
    note.className = 'field__error';
    note.id = `err-${el.id || el.name}`;
    box.append(note);
  }
  note.innerHTML = `${icon('alert', 13)}<span>${esc(message)}</span>`;
  el.setAttribute('aria-describedby', note.id);
}

export function clearFieldErrors(form) {
  $$('.is-invalid', form).forEach((box) => {
    const el = box.querySelector('[name]');
    if (el) markField(el, '');
    else box.classList.remove('is-invalid');
  });
}

/**
 * Check every enabled control in a form and mark the ones that are wrong,
 * with the same wording the server uses. Focuses the first problem.
 * Returns true when everything is fine.
 */
/**
 * Keep each data-after box's min in step with the box it must follow
 * ("expiryDate after startDate"). data-after may list fallbacks, first
 * filled one wins: "expectedEndDate|eventDate".
 */
export function syncLinkedMins(form) {
  if (!form) return;
  $$('[data-after]', form).forEach((el) => {
    const source = el.dataset.after.split('|')
      .map((n) => form.elements[n])
      .find((s) => s && !s.disabled && s.value);
    if (!source) { el.removeAttribute('min'); return; }
    let min = source.value;
    if (el.hasAttribute('data-after-strict') && el.type === 'date') {
      min = addDays(1, new Date(`${source.value}T00:00:00`));
    }
    el.min = min;
  });
}

export function validateForm(form) {
  syncLinkedMins(form);
  let first = null;
  $$('[name]', form).forEach((el) => {
    if (el.disabled || el.type === 'hidden') return;
    const msg = problemWith(el);
    markField(el, msg);
    if (msg && !first) first = el;
  });
  if (first) {
    first.focus({ preventScroll: true });
    first.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }
  return !first;
}

/**
 * Show a 400's fieldErrors ({"phoneNumber": "..."}) under the matching boxes.
 * `aliases` maps a server field to a form field where their names differ.
 * Returns how many were placed; the caller shows the rest as a summary.
 */
export function showFieldErrors(form, err, aliases = {}) {
  const errors = err?.body?.fieldErrors;
  if (!form || !errors || typeof errors !== 'object') return 0;
  let placed = 0;
  let first = null;
  Object.entries(errors).forEach(([key, msg]) => {
    const name = aliases[key] || key;
    const el = form.querySelector(`[name="${CSS.escape(name)}"]`);
    if (!el) return;
    markField(el, msg);
    placed += 1;
    first ??= el;
  });
  first?.focus({ preventScroll: true });
  return placed;
}

/** The summary to show above or below a form once the boxes carry their own messages. */
export function errorSummary(err, placed) {
  const total = Object.keys(err?.body?.fieldErrors || {}).length;
  if (placed && placed === total) return placed === 1 ? 'Please fix the highlighted field.' : 'Please fix the highlighted fields.';
  return err?.message || 'Something went wrong';
}

// One listener for the whole page: typing clears a box's error and moves its
// counter; leaving a box checks it (only once something has been typed, so an
// untouched required box is not flagged the moment it loses focus).
if (typeof document !== 'undefined') {
  document.addEventListener('input', (e) => {
    const el = e.target;
    if (!(el instanceof HTMLElement) || !el.matches('.field__control, .check input')) return;
    const box = fieldBox(el);
    if (box?.classList.contains('is-invalid')) markField(el, '');
    const count = box?.querySelector(':scope > .field__count');
    if (count && el.maxLength > 0) {
      count.textContent = `${el.value.length} / ${el.maxLength}`;
      count.classList.toggle('is-near', el.value.length >= el.maxLength * 0.9);
    }
  });
  document.addEventListener('change', (e) => {
    const form = e.target?.form;
    if (form && form.querySelector('[data-after]')) syncLinkedMins(form);
  });
  document.addEventListener('focusout', (e) => {
    const el = e.target;
    if (!(el instanceof HTMLElement) || !el.matches('.field__control')) return;
    const r = RULES[el.dataset.rule];
    if (r?.upper && el.value) el.value = el.value.toUpperCase();
    if (el.value && el.value.trim() !== '') {
      const msg = problemWith(el);
      if (msg) markField(el, msg);
    }
  });
}

export function checkbox({ name, label, description = '', checked = false, span = true }) {
  return `<label class="check${span ? ' span-2' : ''}">
    <input type="checkbox" name="${esc(name)}"${checked ? ' checked' : ''}>
    <span class="check__box">${icon('check', 14)}</span>
    <span><b>${esc(label)}</b>${description ? `<small>${esc(description)}</small>` : ''}</span>
  </label>`;
}

/** Read a form into a plain object: numbers typed, blanks -> null, checkboxes -> boolean. */
export function readForm(form) {
  const out = {};
  $$('[name]', form).forEach((el) => {
    if (el.disabled) return;
    if (el.type === 'checkbox') { out[el.name] = el.checked; return; }
    const raw = typeof el.value === 'string' ? el.value.trim() : el.value;
    if (raw === '') { out[el.name] = null; return; }
    out[el.name] = el.dataset.type === 'number' ? Number(raw) : raw;
  });
  return out;
}

export function formError(message) {
  return `<div class="form-error" role="alert">${icon('alert', 18)}<span>${esc(message)}</span></div>`;
}

// ---------- snackbar ----------
export function toast(message, tone = 'info') {
  const host = document.getElementById('toasts');
  const el = document.createElement('div');
  el.className = `toast toast--${tone}`;
  const iconName = tone === 'success' ? 'check' : tone === 'error' ? 'alert' : 'info';
  el.innerHTML = `<span class="toast__icon">${icon(iconName, 16)}</span><span>${esc(message)}</span>
    <button class="icon-btn icon-btn--ghost icon-btn--sm" style="color:#fff" aria-label="Dismiss">${icon('close', 16)}</button>`;
  host.appendChild(el);
  const remove = () => {
    el.classList.add('is-leaving');
    setTimeout(() => el.remove(), 260);
  };
  el.querySelector('button').addEventListener('click', remove);
  setTimeout(remove, tone === 'error' ? 6000 : 3800);
}

// ---------- dialog ----------
/**
 * Opens the shared <dialog>. `onSubmit(values, form)` may throw — the error
 * message is shown inside the dialog and it stays open. Resolves with the
 * onSubmit result (or true), or null when cancelled.
 */
export function openDialog({
  title, subtitle = '', body = '', submitLabel = 'Save', tone = '', wide = false,
  onSubmit, onOpen, hideSubmit = false, cancelLabel = 'Cancel', fieldAliases = {},
}) {
  const dlg = document.getElementById('dialog');
  if (dlg.open) dlg.close();
  dlg.className = `dialog${wide ? ' dialog--wide' : ''}`;
  dlg.innerHTML = `
    <form class="dialog__form" novalidate>
      <header class="dialog__head">
        <div>
          <h2>${esc(title)}</h2>
          ${subtitle ? `<p>${esc(subtitle)}</p>` : ''}
        </div>
        <button type="button" class="icon-btn icon-btn--ghost" data-close aria-label="Close">${icon('close')}</button>
      </header>
      <div class="dialog__body">${body}</div>
      <div class="dialog__error" hidden></div>
      <footer class="dialog__foot">
        <button type="button" class="btn btn--text" data-close>${esc(cancelLabel)}</button>
        ${hideSubmit ? '' : `<button type="submit" class="btn${tone ? ` btn--${tone}` : ''}">${esc(submitLabel)}</button>`}
      </footer>
    </form>`;

  return new Promise((resolve) => {
    const form = dlg.querySelector('form');
    let settled = false;
    const finish = (value) => {
      if (settled) return;
      settled = true;
      dlg.close();
      resolve(value);
    };
    dlg.querySelectorAll('[data-close]').forEach((b) => b.addEventListener('click', () => finish(null)));
    dlg.oncancel = (e) => { e.preventDefault(); finish(null); };
    dlg.onclick = (e) => { if (e.target === dlg) finish(null); };

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const errBox = form.querySelector('.dialog__error');
      if (!validateForm(form)) {
        errBox.innerHTML = formError('Please fix the highlighted fields.');
        errBox.hidden = false;
        return;
      }
      const btn = form.querySelector('[type=submit]');
      errBox.hidden = true;
      btn.disabled = true;
      btn.classList.add('is-busy');
      try {
        const result = onSubmit ? await onSubmit(readForm(form), form) : true;
        finish(result === undefined ? true : result);
      } catch (err) {
        const placed = showFieldErrors(form, err, fieldAliases);
        errBox.innerHTML = formError(errorSummary(err, placed));
        errBox.hidden = false;
      } finally {
        btn.disabled = false;
        btn.classList.remove('is-busy');
      }
    });

    dlg.showModal();
    const first = form.querySelector('.dialog__body [name]');
    if (first) first.focus({ preventScroll: true });
    syncLinkedMins(form);
    if (onOpen) onOpen(form);
  });
}

export function confirmDialog({ title, text, confirmLabel = 'Confirm', tone = '' }) {
  return openDialog({
    title,
    body: `<p class="muted">${esc(text)}</p>`,
    submitLabel: confirmLabel,
    tone,
  });
}

/**
 * Ask for a short piece of text — a reason for rejecting, cancelling or
 * reversing something. Resolves to the text, or null if the user backed out.
 * With required: true an empty answer is not accepted.
 */
export async function promptDialog({
  title, text = '', label = 'Reason', placeholder = '',
  confirmLabel = 'Confirm', tone = '', required = false,
}) {
  const values = await openDialog({
    title,
    body: `${text ? `<p class="muted">${esc(text)}</p>` : ''}
      ${field({ name: 'reason', label, type: 'textarea', span: true, required, hint: placeholder })}`,
    submitLabel: confirmLabel,
    tone,
  });
  if (!values) return null;
  const reason = (values.reason || '').trim();
  return required && !reason ? null : reason;
}

/**
 * Run an action from a button: busy state, success snackbar, error snackbar.
 * Returns true when the action succeeded.
 */
export async function runAction(button, fn, successMessage) {
  if (button) { button.disabled = true; button.classList.add('is-busy'); }
  try {
    await fn();
    if (successMessage) toast(successMessage, 'success');
    return true;
  } catch (err) {
    toast(err.message || 'Action failed', 'error');
    return false;
  } finally {
    if (button && button.isConnected) { button.disabled = false; button.classList.remove('is-busy'); }
  }
}

// ---------- vehicle illustration ----------
// Studio-rendered side profiles on an 800x360 stage. Every vehicle is drawn
// from the same chassis: one silhouette, a glazed greenhouse, a lit beltline,
// forged alloys and a polished-floor reflection. Paint is monochrome by
// design and is stable per plate number, so a vehicle always looks the same.

// Every vehicle stands on the same floor (y = GROUND) so a row of them lines
// up, but each body style brings its own wheels and its own size.
const GROUND = 306;
const SILL = 276;

/**
 * Wheel arches are generated from the axle rather than drawn by hand, so a
 * coach can sit on small wheels set wide apart while a pickup gets tall ones,
 * and the body outline still meets them exactly.
 */
const arch = (cx, r) => {
  const w = r + 8;
  const top = GROUND - 2 * r + 17;
  const c = Math.round(w * 0.5625);
  return `L ${cx + w} ${SILL} C ${cx + w} ${top + 28} ${cx + c} ${top} ${cx} ${top}
          C ${cx - c} ${top} ${cx - w} ${top + 28} ${cx - w} ${SILL}`;
};

/** rear/front are the bumper x-positions; axle carries the wheels. */
const stance = (rear, front, axle) => `C ${front + 2} 250 ${front + 2} 262 ${front - 3} 268
  C ${front - 7} 274 ${front - 13} ${SILL} ${front - 21} ${SILL}
  ${arch(axle.front, axle.r)}
  ${arch(axle.rear, axle.r)}
  L ${rear + 16} ${SILL} C ${rear + 6} ${SILL} ${rear} 270 ${rear} 258`;

/**
 * Wheels, and how large the vehicle draws on the shared canvas.
 *
 * `scale` is a compressed version of real length, not a literal one: a coach
 * really is 2.5x a hatchback, but drawing it that way would leave the hatchback
 * illegible in a grid cell. This ramp keeps the hierarchy readable — you can
 * see at a glance that the bus is the big one — without shrinking the small
 * cars into nothing.
 */
const AXLES = {
  sedan:  { rear: 206, front: 594, r: 56, scale: 0.80 },
  suv:    { rear: 206, front: 594, r: 58, scale: 0.86 },
  van:    { rear: 198, front: 600, r: 52, scale: 0.94 },
  bus:    { rear: 176, front: 622, r: 46, scale: 1.00 },
  luxury: { rear: 206, front: 594, r: 57, scale: 0.87 },
  pickup: { rear: 204, front: 596, r: 58, scale: 0.90 },
};

const SHAPES = {
  // Low fastback saloon.
  sedan: {
    body: `M 54 250 C 50 224 58 204 76 197 L 160 176
           C 210 144 262 116 332 103 C 378 95 434 96 472 113
           C 512 131 550 149 582 161 C 632 169 688 179 718 193
           C 738 202 746 217 744 240 ${stance(54, 744, AXLES.sedan)} Z`,
    glass: [
      'M 408 120 L 470 127 L 540 164 L 408 170 Z',
      'M 396 119 L 340 123 L 236 168 L 396 170 Z',
    ],
    belt: 'M 74 190 C 232 176 448 171 636 186',
    crease: 'M 132 218 C 300 208 512 206 668 216',
    sill: 'M 148 266 L 526 266',
    cuts: ['M 402 118 L 402 272', 'M 268 140 L 268 272'],
    handles: [[418, 184], [298, 190]],
    mirror: [560, 152],
    head: 'M 694 190 C 716 190 734 199 741 212 L 702 215 C 699 204 696 196 694 190 Z',
    drl: 'M 702 207 L 737 209',
    tail: 'M 58 198 C 66 194 78 192 92 192 L 94 212 C 80 213 66 216 59 220 Z',
    tailGlow: 'M 61 202 L 90 200',
  },

  // Short overhangs, a tall flat bonnet and a cabin that stops well before the
  // tail — the proportions that separate an SUV from a wagon.
  suv: {
    body: `M 84 250 C 80 222 86 200 104 194 L 120 190
           L 120 100 C 120 80 132 68 154 67 L 398 62
           C 428 61 450 72 468 92 L 514 140
           C 586 147 654 158 700 172 C 724 180 742 200 742 240 ${stance(84, 742, AXLES.suv)} Z`,
    glass: [
      'M 396 86 L 442 84 C 454 85 462 92 470 104 L 500 140 L 396 138 Z',
      'M 272 88 L 386 86 L 386 138 L 272 138 Z',
      'M 158 90 L 262 88 L 262 138 L 158 138 Z',
    ],
    belt: 'M 126 158 C 272 152 472 153 662 167',
    crease: 'M 132 216 C 306 208 520 207 690 218',
    sill: 'M 150 266 L 524 266',
    cuts: ['M 268 86 L 268 272', 'M 392 84 L 392 272'],
    handles: [[302, 160], [420, 158]],
    mirror: [506, 130],
    rails: 'M 140 64 L 390 59',
    head: 'M 686 178 C 710 180 730 190 739 205 L 694 206 C 691 194 688 184 686 178 Z',
    drl: 'M 694 198 L 735 200',
    tail: 'M 90 194 L 120 192 L 122 224 L 92 226 Z',
    tailGlow: 'M 94 200 L 118 199',
  },

  // High-roof panel van.
  van: {
    body: `M 50 254 C 46 218 52 192 72 186 L 72 84
           C 72 62 86 50 110 50 L 452 48
           C 486 48 510 60 532 92 L 574 150
           C 646 158 700 168 716 180 C 736 194 746 212 744 240 ${stance(50, 744, AXLES.van)} Z`,
    glass: [
      'M 468 80 C 490 82 504 92 516 110 L 548 150 L 468 148 Z',
      'M 358 78 L 454 76 L 454 148 L 358 148 Z',
      'M 212 80 L 344 78 L 344 148 L 212 148 Z',
      'M 94 82 L 198 80 L 198 148 L 94 148 Z',
    ],
    belt: 'M 78 166 C 250 160 470 162 668 176',
    crease: 'M 92 218 C 280 210 520 210 700 220',
    sill: 'M 148 266 L 526 266',
    cuts: ['M 352 76 L 352 272', 'M 206 78 L 206 272'],
    handles: [[318, 168], [388, 166]],
    mirror: [548, 138],
    head: 'M 692 184 C 714 185 732 194 740 207 L 700 209 C 697 197 694 189 692 184 Z',
    drl: 'M 700 201 L 736 203',
    tail: 'M 54 190 L 84 189 L 86 222 L 56 224 Z',
    tailGlow: 'M 58 196 L 82 195',
  },

  // Long coach: small wheels set wide apart, six bays under a body-colour rail.
  bus: {
    body: `M 46 256 C 44 212 48 178 66 172 L 66 58
           C 66 40 78 30 98 30 L 700 30
           C 726 30 742 43 744 68 L 744 240 ${stance(46, 744, AXLES.bus)} Z`,
    glass: [
      'M 90 56 L 196 54 L 196 126 L 90 128 Z',
      'M 210 54 L 316 52 L 316 124 L 210 126 Z',
      'M 330 52 L 436 50 L 436 122 L 330 124 Z',
      'M 450 50 L 556 48 L 556 120 L 450 122 Z',
      'M 570 48 L 676 46 L 676 118 L 570 120 Z',
      'M 690 46 L 724 46 C 730 46 732 50 732 56 L 732 116 L 690 118 Z',
    ],
    belt: 'M 72 148 C 260 143 520 140 738 146',
    crease: 'M 72 214 C 280 207 540 205 740 212',
    sill: 'M 148 266 L 526 266',
    cuts: ['M 203 54 L 203 272', 'M 323 52 L 323 272'],
    handles: [],
    mirror: [748, 78],
    sign: 'M 98 38 L 262 36 L 262 48 L 98 50 Z',
    head: 'M 700 190 C 718 191 734 198 740 210 L 704 212 C 702 202 701 195 700 190 Z',
    drl: 'M 704 204 L 736 206',
    tail: 'M 50 190 L 78 189 L 80 220 L 52 222 Z',
    tailGlow: 'M 54 196 L 76 195',
  },

  // Long-bonnet grand tourer: the lowest, widest stance of the six.
  luxury: {
    body: `M 52 246 C 46 218 56 196 78 190 L 176 170
           C 232 132 296 106 378 96 C 428 90 486 94 524 112
           C 556 127 588 143 612 154 C 662 162 706 172 724 186
           C 740 197 748 214 744 240 ${stance(52, 744, AXLES.luxury)} Z`,
    glass: [
      'M 402 114 L 462 118 C 488 123 508 133 524 144 L 572 162 L 402 166 Z',
      'M 390 114 L 336 120 L 252 162 L 390 166 Z',
    ],
    belt: 'M 78 186 C 240 170 460 164 656 180',
    crease: 'M 128 214 C 306 202 520 200 682 212',
    sill: 'M 148 264 L 526 264',
    cuts: ['M 396 112 L 396 270'],
    handles: [[428, 180], [302, 186]],
    mirror: [588, 150],
    head: 'M 692 188 C 714 188 734 197 741 211 L 700 214 C 697 202 694 194 692 188 Z',
    drl: 'M 700 206 L 737 208',
    tail: 'M 57 192 C 67 188 80 186 96 186 L 98 206 C 82 207 68 210 59 214 Z',
    tailGlow: 'M 61 196 L 94 194',
  },

  // Double-cab pickup: cab forward, dropped bed rail behind it, tall tyres.
  pickup: {
    body: `M 50 254 C 46 228 52 206 70 200 L 70 176 L 300 172
           L 300 92 C 300 72 312 62 334 61 L 452 57
           C 480 56 500 66 516 86 L 556 142
           C 622 152 678 164 708 180 C 732 191 746 208 744 238 ${stance(50, 744, AXLES.pickup)} Z`,
    glass: [
      'M 398 82 L 452 79 C 464 80 472 87 480 99 L 510 140 L 398 138 Z',
      'M 332 84 L 390 82 L 390 138 L 332 138 Z',
    ],
    belt: 'M 308 156 C 400 152 548 154 664 166',
    crease: 'M 84 216 C 300 208 520 207 686 218',
    sill: 'M 148 266 L 526 266',
    cuts: ['M 394 80 L 394 272', 'M 306 170 L 306 272'],
    handles: [[350, 160], [422, 158]],
    mirror: [522, 132],
    bed: 'M 78 182 L 294 179',
    head: 'M 690 180 C 712 181 732 190 740 204 L 698 206 C 695 194 692 186 690 180 Z',
    drl: 'M 698 198 L 736 200',
    tail: 'M 56 184 L 66 184 L 66 206 L 56 206 Z',
    tailGlow: 'M 58 188 L 64 188',
  },
};

export function shapeFor(category = '') {
  const c = String(category).toUpperCase();
  if (/BUS|COACH|MINIBUS/.test(c)) return 'bus';
  if (/PICK|TRUCK|LORRY|DOUBLE CAB|UTE/.test(c)) return 'pickup';
  if (/VAN|MPV|MINIVAN|PEOPLE/.test(c)) return 'van';
  if (/LUX|PREMIUM|EXEC|LIMO|SPORT|COUPE/.test(c)) return 'luxury';
  if (/SUV|JEEP|4X4|4WD|CROSS|WAGON|ESTATE/.test(c)) return 'suv';
  return 'sedan';
}

// ---------- the shared sprite ----------
// Only the paint gradients differ between two vehicles. Everything else — the
// glazing, the alloy and tyre shading, the lamps, the floor fade and the six
// clip paths — is identical every time, so it is declared once in a hidden
// sprite and referenced by id. That keeps a fleet page from shipping the same
// forty gradient stops over and over.
const SPRITE_ID = 'vr-car-sprite';

const SHARED_DEFS = `
  <linearGradient id="vrGlass" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#3C424B"/>
    <stop offset=".55" stop-color="#282D34"/>
    <stop offset="1" stop-color="#31363E"/>
  </linearGradient>
  <linearGradient id="vrForm" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="#fff" stop-opacity="0"/>
    <stop offset=".3" stop-color="#fff" stop-opacity=".2"/>
    <stop offset=".72" stop-color="#fff" stop-opacity=".14"/>
    <stop offset="1" stop-color="#fff" stop-opacity="0"/>
  </linearGradient>
  <radialGradient id="vrLamp" cx=".3" cy=".4" r=".85">
    <stop offset="0" stop-color="#FFFFFF"/>
    <stop offset="1" stop-color="#D7DCE2"/>
  </radialGradient>
  <radialGradient id="vrCast" cx=".5" cy=".5" r=".5">
    <stop offset="0" stop-color="#0E1115" stop-opacity=".26"/>
    <stop offset=".45" stop-color="#0E1115" stop-opacity=".12"/>
    <stop offset="1" stop-color="#0E1115" stop-opacity="0"/>
  </radialGradient>
  ${Object.keys(SHAPES).map((key) => `
    <clipPath id="vrClip-${key}"><path d="${SHAPES[key].body}"/></clipPath>`).join('')}`;

let spriteReady = false;

function ensureSprite() {
  if (spriteReady) return;
  try {
    if (typeof document === 'undefined' || typeof document.createElement !== 'function') return;
    if (document.getElementById && document.getElementById(SPRITE_ID)) { spriteReady = true; return; }
    const host = document.body || document.documentElement;
    if (!host || typeof host.appendChild !== 'function') return;
    const holder = document.createElement('div');
    holder.innerHTML = `<svg id="${SPRITE_ID}" aria-hidden="true" focusable="false"
      style="position:absolute;width:0;height:0;overflow:hidden"><defs>${SHARED_DEFS}</defs></svg>`;
    if (holder.firstElementChild) {
      host.appendChild(holder.firstElementChild);
      spriteReady = true;
    }
  } catch {
    /* Without the sprite the gradients simply do not resolve; that is worth
       knowing about but must never take a page down. */
  }
}

// ---------- drawing ----------
// The material is clay, not car paint: a matte surface under one soft key
// light, with a blurred contact shadow and no environment. That means low
// contrast on the body, no specular streak, no chrome, and no floor mirror —
// a polished reflection would read as a different material entirely.
const PAINTS = [
  '#F1F2F4', // chalk
  '#E2E4E8', // bone
  '#CED1D6', // silver
  '#A9ADB4', // ash
  '#6F747B', // slate
  '#464A51', // graphite
  '#2A2E34', // ink
  '#DADDE1', // pearl
];

function hash(str) {
  let h = 0;
  for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) | 0;
  return Math.abs(h);
}

function shade(hex, amount) {
  const n = parseInt(hex.slice(1), 16);
  const clamp = (v) => Math.max(0, Math.min(255, v));
  const r = clamp(((n >> 16) & 255) + amount);
  const g = clamp(((n >> 8) & 255) + amount);
  const b = clamp((n & 255) + amount);
  return `#${((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1)}`;
}

/**
 * A soft matte wheel: dark tyre, a slightly lighter dish and one quiet rim
 * ring. The old multi-spoke alloy was chrome, which a clay render has none of.
 */
function wheel(cx, r, compact) {
  return `<g transform="translate(${cx} ${GROUND - r}) scale(${(r / 56).toFixed(4)})">
    <circle r="56" fill="#23272D"/>
    <circle r="55" fill="none" stroke="#14171B" stroke-opacity=".5" stroke-width="2"/>
    <circle r="35" fill="#3B4048"/>
    ${compact ? '' : '<circle r="35" fill="none" stroke="#767C85" stroke-width="2.5"/>'}
    ${compact ? '' : '<circle r="22" fill="none" stroke="#585E67" stroke-width="1.5"/>'}
    <circle r="9" fill="#5A606A"/>
  </g>`;
}

/** The car, drawn once. */
function carBody(key, shape, axle, id, base, compact) {
  const soft = shade(base, -26);
  const [mx, my] = shape.mirror;

  const cuts = (shape.cuts || [])
    .map((d) => `<path d="${d}" fill="none" stroke="${soft}" stroke-opacity=".28" stroke-width="1.4"/>`).join('');
  const handles = (shape.handles || [])
    .map(([hx, hy]) => `<rect x="${hx}" y="${hy}" width="28" height="5" rx="2.5" fill="${soft}" fill-opacity=".42"/>`).join('');
  const glass = shape.glass
    .map((d) => `<path d="${d}" fill="url(#vrGlass)"/>`).join('');

  return `
    <path d="${shape.body}" fill="url(#${id}paint)"/>
    <g clip-path="url(#vrClip-${key})">
      <rect x="0" y="0" width="800" height="400" fill="url(#${id}ao)"/>
      <path d="${shape.belt}" fill="none" stroke="url(#vrForm)" stroke-width="3" stroke-linecap="round"/>
      ${cuts}
      ${shape.bed ? `<path d="${shape.bed}" fill="none" stroke="${soft}" stroke-opacity=".34" stroke-width="2.5"/>` : ''}
    </g>

    <g clip-path="url(#vrClip-${key})">
      ${glass}
      ${shape.sign ? `<path d="${shape.sign}" fill="#23272D"/>` : ''}
      ${shape.rails ? `<path d="${shape.rails}" fill="none" stroke="#3B4048" stroke-width="5.5" stroke-linecap="round"/>` : ''}
      ${handles}
      <path d="${shape.head}" fill="url(#vrLamp)"/>
      <path d="${shape.drl}" stroke="#FFFFFF" stroke-width="3" stroke-linecap="round"/>
      <path d="${shape.tail}" fill="#41454C"/>
      <path d="${shape.tailGlow}" stroke="#A08C89" stroke-width="2.4" stroke-linecap="round" stroke-opacity=".55"/>
    </g>
    <path d="${shape.body}" fill="none" stroke="${soft}" stroke-opacity=".3" stroke-width="1.2"/>

    <path d="M ${mx} ${my} c 8 -11 22 -13 31 -7 l 1 11 c -11 4 -23 4 -32 -4 z"
          fill="${shade(base, -12)}"/>

    ${wheel(axle.rear, axle.r, compact)}
    ${wheel(axle.front, axle.r, compact)}`;
}

let artSeq = 0;

/**
 * A drawn vehicle. Colour is stable per plate number.
 *   paint    force a hex
 *   compact  drop the finer wheel detail and draw every category at one size —
 *            in a dense list a row of uniform thumbnails reads better than a
 *            size hierarchy.
 */
export function carArt(vehicle = {}, { paint, compact = false } = {}) {
  ensureSprite();
  const key = shapeFor(vehicle.category);
  const shape = SHAPES[key];
  const axle = AXLES[key];
  const base = paint || PAINTS[hash(String(vehicle.plateNumber || vehicle.model || 'axle')) % PAINTS.length];
  const id = `ca${++artSeq}`;
  const deep = shade(base, -60);
  const body = carBody(key, shape, axle, id, base, compact);

  // Scaling about the contact patch keeps every vehicle standing on the same
  // floor line while a coach still draws visibly longer than a hatchback.
  const s = compact ? 1 : axle.scale;
  const fit = `translate(400 ${GROUND}) scale(${s}) translate(-400 ${-GROUND})`;

  return `<svg class="car-art" viewBox="0 0 800 400" role="img" aria-label="${esc(vehicle.model || 'Vehicle')}" xmlns="http://www.w3.org/2000/svg">
    <defs>
      <linearGradient id="${id}paint" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="${shade(base, 26)}"/>
        <stop offset=".42" stop-color="${base}"/>
        <stop offset=".84" stop-color="${shade(base, -20)}"/>
        <stop offset="1" stop-color="${shade(base, -8)}"/>
      </linearGradient>
      <linearGradient id="${id}ao" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="${deep}" stop-opacity="0"/>
        <stop offset=".6" stop-color="${deep}" stop-opacity="0"/>
        <stop offset=".9" stop-color="${deep}" stop-opacity=".16"/>
        <stop offset="1" stop-color="${deep}" stop-opacity=".07"/>
      </linearGradient>
    </defs>

    <g transform="${fit}">
      <ellipse cx="400" cy="${GROUND + 2}" rx="336" ry="22" fill="url(#vrCast)"/>
      <ellipse cx="${axle.rear}" cy="${GROUND - 2}" rx="${axle.r + 4}" ry="6" fill="#0E1115" fill-opacity=".22"/>
      <ellipse cx="${axle.front}" cy="${GROUND - 2}" rx="${axle.r + 4}" ry="6" fill="#0E1115" fill-opacity=".22"/>
      ${body}
    </g>
  </svg>`;
}

/**
 * Is this image reference safe to put in a src? Absolute http(s) is allowed,
 * and so is a same-origin path like `assets/renders/suv.webp` — that is how
 * you would ship your own renders. Anything carrying another scheme
 * (javascript:, data:) or pointing at another host protocol-relatively is not.
 */
export function usableImage(raw) {
  const s = String(raw ?? '').trim();
  if (!s) return false;
  if (s.startsWith('//')) return false;
  if (/^[a-z][a-z0-9+.-]*:/i.test(s)) return /^https?:/i.test(s);
  return true;
}

/** Real photo when the vehicle has an imageUrl, otherwise the drawn car. */
export function vehicleVisual(vehicle, options) {
  if (vehicle && usableImage(vehicle.imageUrl)) {
    queueStalledPhotoSweep();
    return `<img class="vehicle-img" src="${esc(sized(vehicle.imageUrl, 960))}"${srcsetFor(vehicle.imageUrl, options)} alt="${esc(vehicle.model)}" loading="lazy" decoding="async" data-fallback="${esc(vehicle.category || '')}|${esc(vehicle.plateNumber || '')}">`;
  }
  return carArt(vehicle, options);
}

/** Photos stored by the app can be served smaller: ?w= asks for a scaled copy. */
function sized(url, width) {
  if (!/^\/api\/vehicles\/\d+\/image/.test(url)) return url;
  return `${url}${url.includes('?') ? '&' : '?'}w=${width}`;
}

function srcsetFor(url, options = {}) {
  if (!/^\/api\/vehicles\/\d+\/image/.test(url)) return '';
  const set = [320, 640, 960, 1280].map((w) => `${esc(sized(url, w))} ${w}w`).join(', ');
  const sizes = options?.sizes || (options?.compact ? '120px' : '(max-width: 620px) 92vw, (max-width: 1100px) 46vw, 420px');
  return ` srcset="${set}" sizes="${esc(sizes)}"`;
}

/** Put the drawn car where a photo should have been. */
function fallBackToDrawing(img) {
  if (!img.isConnected || !img.dataset.fallback) return;
  const [category, plateNumber] = img.dataset.fallback.split('|');
  const wrap = document.createElement('div');
  wrap.innerHTML = carArt({ category, plateNumber, model: img.alt });
  img.replaceWith(wrap.firstElementChild);
}

// Broken photo -> swap in the drawn car (capture phase: error events don't bubble).
document.addEventListener('error', (e) => {
  const img = e.target;
  if (!(img instanceof HTMLImageElement) || !img.dataset.fallback) return;
  fallBackToDrawing(img);
}, true);

/**
 * A host that is merely unreachable never fires `error` — the request just
 * hangs, and the tile stays empty for as long as the page is open. So a photo
 * that has neither loaded nor failed within a few seconds is treated as
 * failed.
 *
 * Only images near the viewport are considered: an off-screen `loading="lazy"`
 * photo has not started yet, and swapping those would throw away photos that
 * were going to load perfectly well.
 */
const STALL_MS = 8000;
let sweepQueued = false;

function queueStalledPhotoSweep() {
  if (sweepQueued || typeof document === 'undefined' || !document.querySelectorAll) return;
  sweepQueued = true;
  setTimeout(() => {
    sweepQueued = false;
    const margin = 200;
    document.querySelectorAll('img.vehicle-img[data-fallback]').forEach((img) => {
      if (img.complete) return;                       // loaded, or already handled by `error`
      const box = img.getBoundingClientRect();
      const near = box.bottom > -margin && box.top < (window.innerHeight || 0) + margin;
      if (near) fallBackToDrawing(img);
    });
  }, STALL_MS);
}
