// ============================================================
// Spotlight: one search box over cars, branches and pages, from
// anywhere. Press "/" or Ctrl/Cmd+K, or the search button in the
// black bar. Cars come from the public search for the next couple
// of days, so everything it offers can actually be reserved.
// ============================================================
import { api } from './api.js';
import { store, getBranches, getVehicle, isCustomer, isStaff } from './store.js';
import { esc, icon, money, addDays, humanize } from './ui.js';
import { recent } from './prefs.js';

let root = null;
let items = [];
let active = 0;
let carsPromise = null;
let lastFocus = null;

const cars = () => {
  carsPromise = carsPromise || api.vehicles.search(addDays(1), addDays(3)).catch(() => []);
  return carsPromise;
};

function pages() {
  const list = [
    ['explore', 'The collection', 'Every car free for your dates', 'car'],
    ['vehicles', 'Vehicles', 'The whole fleet, by body, power and price', 'grid'],
    ['branches', 'Branches', 'Addresses, hours and the island map', 'building'],
    ['saved', 'Saved cars', 'Your shortlist on this device', 'heart'],
    ['compare', 'Compare', 'Up to three cars side by side', 'layers'],
  ];
  if (isCustomer()) list.push(['trips', 'My trips', 'Requests, pick-ups and returns', 'route']);
  if (store.user) list.push(['notifications', 'Notifications', 'Your updates', 'bell'], ['account', 'Account', 'Licence, stats and settings', 'user']);
  if (isStaff()) list.push(['console', 'Console', 'Staff workspace', 'grid']);
  if (!store.user) list.push(['login', 'Sign in', 'Welcome back', 'key'], ['register', 'Create an account', 'Takes a minute', 'user']);
  return list.map(([href, title, sub, ic]) => ({ kind: 'Pages', href: `#/${href}`, title, sub, icon: ic, hay: `${title} ${sub}`.toLowerCase() }));
}

function build() {
  root = document.createElement('div');
  root.className = 'spot';
  root.hidden = true;
  root.innerHTML = `
    <div class="spot__scrim" data-spot-close></div>
    <div class="spot__panel" role="dialog" aria-modal="true" aria-label="Search">
      <label class="spot__field">
        ${icon('search', 20)}
        <input type="search" placeholder="Search cars, branches and pages" autocomplete="off" spellcheck="false"
          role="combobox" aria-expanded="true" aria-controls="spot-list" aria-autocomplete="list">
        <kbd>esc</kbd>
      </label>
      <div class="spot__list" id="spot-list" role="listbox"></div>
      <div class="spot__foot"><span><kbd>↑</kbd><kbd>↓</kbd> move</span><span><kbd>↵</kbd> open</span><span><kbd>/</kbd> or <kbd>Ctrl K</kbd> anywhere</span></div>
    </div>`;
  document.body.appendChild(root);
  const input = root.querySelector('input');
  input.addEventListener('input', () => { active = 0; paint(input.value); });
  input.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); move(1); }
    if (e.key === 'ArrowUp') { e.preventDefault(); move(-1); }
    if (e.key === 'Enter') { e.preventDefault(); go(items[active]); }
    if (e.key === 'Escape') { e.preventDefault(); closeSpotlight(); }
  });
  root.addEventListener('click', (e) => {
    if (e.target.closest('[data-spot-close]')) { closeSpotlight(); return; }
    const row = e.target.closest('[data-spot-i]');
    if (row) go(items[Number(row.dataset.spotI)]);
  });
  root.addEventListener('mousemove', (e) => {
    const row = e.target.closest('[data-spot-i]');
    if (row && Number(row.dataset.spotI) !== active) { active = Number(row.dataset.spotI); mark(); }
  });
}

function move(d) {
  if (!items.length) return;
  active = (active + d + items.length) % items.length;
  mark();
}
function mark() {
  root.querySelectorAll('[data-spot-i]').forEach((r) => {
    const on = Number(r.dataset.spotI) === active;
    r.classList.toggle('is-active', on);
    r.setAttribute('aria-selected', String(on));
    if (on) r.scrollIntoView({ block: 'nearest' });
  });
}
function go(item) {
  if (!item) return;
  closeSpotlight();
  window.location.hash = item.href;
}

async function paint(q) {
  const query = q.trim().toLowerCase();
  const terms = query.split(/\s+/).filter(Boolean);
  const [fleet, branches] = await Promise.all([cars(), getBranches().catch(() => [])]);
  const byId = new Map(branches.map((b) => [b.branchId, b]));
  const match = (hay) => terms.every((t) => hay.includes(t));

  const carItems = fleet.map((v) => {
    const b = byId.get(v.branchId);
    return {
      kind: 'Cars', href: `#/vehicle/${v.vehicleId}`, title: v.model, icon: 'car',
      sub: [v.fuelType, v.passengerCapacity ? `${v.passengerCapacity} seats` : '', b?.name].filter(Boolean).join(' · '),
      right: money(v.rentalPricePerDay),
      hay: [v.model, v.category, v.fuelType, v.manufactureYear, b?.name, b?.city, v.passengerCapacity && `${v.passengerCapacity} seats`].join(' ').toLowerCase(),
    };
  });
  const branchItems = branches.filter((b) => b.status === 'ACTIVE').map((b) => ({
    kind: 'Branches', href: '#/branches', title: b.name, icon: 'pin',
    sub: [b.street, b.city].filter(Boolean).join(', '), hay: [b.name, b.city, b.district, b.street].join(' ').toLowerCase(),
  }));

  let groups;
  if (!terms.length) {
    const seen = await Promise.all(recent.list().slice(0, 4).map((id) => getVehicle(id).catch(() => null)));
    const recents = seen.filter(Boolean).map((v) => ({
      kind: 'Recently viewed', href: `#/vehicle/${v.vehicleId}`, title: v.model, icon: 'history',
      sub: [v.fuelType, humanize(v.category || '')].filter(Boolean).join(' · '), right: money(v.rentalPricePerDay),
    }));
    groups = [...recents, ...pages()];
  } else {
    groups = [
      ...carItems.filter((i) => match(i.hay)).slice(0, 6),
      ...branchItems.filter((i) => match(i.hay)).slice(0, 4),
      ...pages().filter((i) => match(i.hay)),
    ];
  }
  if (q !== root.querySelector('input').value) return; // a newer keystroke is on its way
  items = groups;
  const list = root.querySelector('.spot__list');
  if (!items.length) {
    list.innerHTML = `<div class="spot__empty">${icon('search', 22)}<b>No matches for “${esc(q)}”</b><span>Try a model, a town, “hybrid” or “7 seats”.</span></div>`;
    return;
  }
  let html = '';
  let last = '';
  items.forEach((it, i) => {
    if (it.kind !== last) { html += `<p class="spot__group">${esc(it.kind)}</p>`; last = it.kind; }
    html += `<a class="spot__row${i === active ? ' is-active' : ''}" href="${esc(it.href)}" data-spot-i="${i}" role="option" aria-selected="${i === active}">
      <span class="spot__icon">${icon(it.icon, 17)}</span>
      <span class="spot__text"><b>${esc(it.title)}</b>${it.sub ? `<small>${esc(it.sub)}</small>` : ''}</span>
      ${it.right ? `<span class="spot__right">${esc(it.right)}</span>` : `<span class="spot__go">${icon('arrowRight', 15)}</span>`}
    </a>`;
  });
  list.innerHTML = html;
}

export function openSpotlight() {
  if (!root) build();
  if (!root.hidden) return;
  lastFocus = document.activeElement;
  root.hidden = false;
  document.documentElement.classList.add('has-spot');
  const input = root.querySelector('input');
  input.value = '';
  active = 0;
  paint('');
  requestAnimationFrame(() => input.focus());
}

export function closeSpotlight() {
  if (!root || root.hidden) return;
  root.hidden = true;
  document.documentElement.classList.remove('has-spot');
  lastFocus?.focus?.();
}

export function bindSpotlight() {
  document.addEventListener('keydown', (e) => {
    const typing = e.target.closest?.('input, textarea, select, [contenteditable="true"]');
    if ((e.key === 'k' || e.key === 'K') && (e.ctrlKey || e.metaKey)) { e.preventDefault(); openSpotlight(); return; }
    if (e.key === '/' && !typing && !e.ctrlKey && !e.metaKey && !e.altKey) { e.preventDefault(); openSpotlight(); }
  });
  window.addEventListener('hashchange', closeSpotlight);
}
