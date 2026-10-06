// ============================================================
// Visitor preferences kept in the browser: saved cars, the compare
// list and recently viewed cars. None of this needs the server - it
// is the visitor's own shortlist - so it lives in localStorage and is
// simply empty where storage is unavailable (private windows etc).
// ============================================================
import { $$, esc, icon, toast } from './ui.js';
import { api } from './api.js';
import { store } from './store.js';

const signedInCustomer = () => store.user?.role === 'CUSTOMER';

const KEYS = { saved: 'axle.saved.v1', compare: 'axle.compare.v1', recent: 'axle.recent.v1' };
export const COMPARE_MAX = 3;
const RECENT_MAX = 8;

function read(key) {
  try {
    const raw = JSON.parse(localStorage.getItem(KEYS[key]) || '[]');
    return Array.isArray(raw) ? raw.map(Number).filter((n) => Number.isInteger(n) && n > 0) : [];
  } catch {
    return [];
  }
}

function write(key, list) {
  try { localStorage.setItem(KEYS[key], JSON.stringify(list)); } catch { /* storage blocked: keep going */ }
  window.dispatchEvent(new CustomEvent('prefs:change', { detail: { key } }));
}

export const saved = {
  list: () => read('saved'),
  has: (id) => read('saved').includes(Number(id)),
  toggle(id) {
    const list = read('saved');
    const n = Number(id);
    const on = !list.includes(n);
    write('saved', on ? [n, ...list] : list.filter((x) => x !== n));
    // Signed in, the account keeps the list too, so it follows you to other devices.
    if (signedInCustomer()) {
      (on ? api.me.addFavourite(n) : api.me.removeFavourite(n)).catch(() => { /* kept locally; merged next sign-in */ });
    }
    return on;
  },
};

export const compare = {
  list: () => read('compare'),
  has: (id) => read('compare').includes(Number(id)),
  /** @returns {'added'|'removed'|'full'} */
  toggle(id) {
    const list = read('compare');
    const n = Number(id);
    if (list.includes(n)) { write('compare', list.filter((x) => x !== n)); return 'removed'; }
    if (list.length >= COMPARE_MAX) return 'full';
    write('compare', [...list, n]);
    return 'added';
  },
  remove(id) { write('compare', read('compare').filter((x) => x !== Number(id))); },
  clear() { write('compare', []); },
};

export const recent = {
  list: () => read('recent'),
  push(id) {
    const n = Number(id);
    write('recent', [n, ...read('recent').filter((x) => x !== n)].slice(0, RECENT_MAX));
  },
};

/**
 * After sign-in: this device's saved cars are added to the account, and the
 * account's list (from every device) becomes this device's list.
 */
export async function syncSaved() {
  if (!signedInCustomer()) return;
  try {
    const ids = await api.me.mergeFavourites(saved.list().slice(0, 100));   // the server takes 100 at most
    if (Array.isArray(ids)) write('saved', ids.map(Number));
  } catch { /* offline or signed out meanwhile: the local list stays */ }
}

/**
 * The save and compare buttons that ride on a car. They carry the id only;
 * one listener for the whole document (bindPrefs) handles every copy, so
 * any number of cards for the same car stay in step.
 */
export function carActions(v, { labels = false } = {}) {
  const id = v.vehicleId;
  const s = saved.has(id);
  const c = compare.has(id);
  const name = esc(v.model || 'this car');
  return `<div class="car-acts${labels ? ' car-acts--labels' : ''}">
    <button type="button" class="car-act car-act--save" data-save="${id}" aria-pressed="${s}"
      aria-label="${s ? 'Remove' : 'Save'} ${name}" title="${s ? 'Saved' : 'Save'}">
      ${icon('heart', 16)}${labels ? `<span>${s ? 'Saved' : 'Save'}</span>` : ''}
    </button>
    <button type="button" class="car-act car-act--compare" data-compare="${id}" aria-pressed="${c}"
      aria-label="${c ? 'Remove' : 'Add'} ${name} ${c ? 'from' : 'to'} compare" title="${c ? 'In compare' : 'Compare'}">
      ${icon('layers', 16)}${labels ? `<span>${c ? 'Comparing' : 'Compare'}</span>` : ''}
    </button>
  </div>`;
}

function syncButtons() {
  const s = saved.list();
  const c = compare.list();
  $$('[data-save]').forEach((b) => {
    const on = s.includes(Number(b.dataset.save));
    b.setAttribute('aria-pressed', String(on));
    b.title = on ? 'Saved' : 'Save';
    const t = b.querySelector('span'); if (t) t.textContent = on ? 'Saved' : 'Save';
  });
  $$('[data-compare]').forEach((b) => {
    const on = c.includes(Number(b.dataset.compare));
    b.setAttribute('aria-pressed', String(on));
    b.title = on ? 'In compare' : 'Compare';
    const t = b.querySelector('span'); if (t) t.textContent = on ? 'Comparing' : 'Compare';
  });
}

let bound = false;
export function bindPrefs() {
  if (bound) return;
  bound = true;
  document.addEventListener('click', (e) => {
    const s = e.target.closest('[data-save]');
    if (s) {
      e.preventDefault(); e.stopPropagation();
      const on = saved.toggle(s.dataset.save);
      s.classList.remove('is-pop'); void s.offsetWidth; s.classList.add('is-pop');
      toast(on ? 'Saved — find it under Saved' : 'Removed from Saved', on ? 'success' : 'info');
      return;
    }
    const c = e.target.closest('[data-compare]');
    if (c) {
      e.preventDefault(); e.stopPropagation();
      const r = compare.toggle(c.dataset.compare);
      if (r === 'full') toast(`You can compare up to ${COMPARE_MAX} cars — remove one first`, 'error');
    }
  }, true);
  window.addEventListener('prefs:change', syncButtons);
  // Another tab changed the lists: follow it.
  window.addEventListener('storage', (e) => {
    const key = Object.keys(KEYS).find((k) => KEYS[k] === e.key);
    if (key) window.dispatchEvent(new CustomEvent('prefs:change', { detail: { key } }));
  });
}
