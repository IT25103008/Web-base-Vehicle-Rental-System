// ============================================================
// App shell: hash router, role guards, and the two-row chrome —
// a thin black global bar over a frosted contextual bar, with a
// glass rail on console screens.
// ============================================================
import { api } from './api.js';
import { store, loadUser, refreshUnread, invalidate, isStaff, isAdmin, isCustomer } from './store.js';
import { esc, icon, initials, toast, errorState, humanize } from './ui.js';
import { renderExplore, renderVehicle, renderBranches, renderLogin, renderRegister } from './views/public.js';
import { renderTrips, renderNotifications } from './views/customer.js';
import { renderSaved, renderCompare, renderTrip, renderAccount, renderNotFound } from './views/extras.js';
import { renderVehicles } from './views/vehicles.js';
import { renderForgot, renderReset, renderVerify, renderPrivacy } from './views/auth-extra.js';
import { renderTimeline } from './views/console-timeline.js';
import { bindPrefs, saved as savedCars, syncSaved } from './prefs.js';
import { bindSpotlight, openSpotlight } from './spotlight.js';
import { bindTray } from './tray.js';
import { renderOverview, renderBookings, renderHandovers, renderPayments } from './views/console.js';
import { renderFleet, renderMaintenance, renderDamage, renderClaims } from './views/console-ops.js';
import { renderPeople, renderBranchAdmin, renderTransfers, renderInsurance } from './views/console-admin.js';

const STAFF = ['STAFF', 'ADMINISTRATOR'];
const ADMIN = ['ADMINISTRATOR'];

const routes = [
  { path: 'explore', view: renderExplore, title: 'Collection', sub: 'Luxury vehicle rental' },
  { path: 'vehicle/:id', view: renderVehicle, nav: 'explore', title: 'Collection', sub: 'Vehicle detail' },
  { path: 'vehicles', view: renderVehicles, title: 'Vehicles', sub: 'The whole fleet' },
  { path: 'branches', view: renderBranches, title: 'Branches', sub: 'Where to collect' },
  { path: 'login', view: renderLogin, guest: true, title: 'Sign in', sub: 'Welcome back' },
  { path: 'register', view: renderRegister, guest: true, title: 'Create account', sub: 'New customer' },
  { path: 'forgot', view: renderForgot, guest: true, title: 'Password help', sub: 'Reset your password' },
  { path: 'reset', view: renderReset, title: 'Password help', sub: 'Choose a new password' },
  { path: 'verify', view: renderVerify, title: 'Your account', sub: 'Confirm your email' },
  { path: 'privacy', view: renderPrivacy, title: 'Privacy', sub: 'How we use your details' },
  { path: 'trips', view: renderTrips, roles: ['CUSTOMER'], title: 'My trips', sub: 'Requests and history' },
  { path: 'notifications', view: renderNotifications, auth: true, title: 'Notifications', sub: 'Your updates' },
  { path: 'saved', view: renderSaved, title: 'Saved', sub: 'Your shortlist' },
  { path: 'compare', view: renderCompare, nav: 'saved', title: 'Compare', sub: 'Side by side' },
  { path: 'trip/:id', view: renderTrip, nav: 'trips', roles: ['CUSTOMER'], title: 'Trip', sub: 'Trip detail' },
  { path: 'account', view: renderAccount, auth: true, title: 'Account', sub: 'Your details' },

  { path: 'console', view: renderOverview, roles: STAFF, console: true, title: 'Console', sub: 'Overview' },
  { path: 'console/timeline', view: renderTimeline, roles: STAFF, console: true, title: 'Console', sub: 'Fleet timeline' },
  { path: 'console/bookings', view: renderBookings, roles: STAFF, console: true, title: 'Console', sub: 'Bookings' },
  { path: 'console/handovers', view: renderHandovers, roles: STAFF, console: true, title: 'Console', sub: 'Handovers' },
  { path: 'console/payments', view: renderPayments, roles: STAFF, console: true, title: 'Console', sub: 'Payments' },
  { path: 'console/fleet', view: renderFleet, roles: STAFF, console: true, title: 'Console', sub: 'Fleet' },
  { path: 'console/maintenance', view: renderMaintenance, roles: STAFF, console: true, title: 'Console', sub: 'Service' },
  { path: 'console/damage', view: renderDamage, roles: STAFF, console: true, title: 'Console', sub: 'Damage' },
  { path: 'console/insurance', view: renderInsurance, roles: STAFF, console: true, title: 'Console', sub: 'Insurance' },
  { path: 'console/people', view: renderPeople, roles: STAFF, console: true, title: 'Console', sub: 'People' },
  { path: 'console/claims', view: renderClaims, roles: ADMIN, console: true, title: 'Console', sub: 'Claims' },
  { path: 'console/branches', view: renderBranchAdmin, roles: ADMIN, console: true, title: 'Console', sub: 'Branches' },
  { path: 'console/transfers', view: renderTransfers, roles: ADMIN, console: true, title: 'Console', sub: 'Transfers' },
];

const RAIL = [
  [
    ['console', 'Overview', 'grid'],
    ['console/bookings', 'Bookings', 'ticket'],
    ['console/timeline', 'Timeline', 'calendar'],
    ['console/handovers', 'Handovers', 'key'],
    ['console/payments', 'Payments', 'wallet'],
  ],
  [
    ['console/fleet', 'Fleet', 'car'],
    ['console/maintenance', 'Service', 'wrench'],
    ['console/damage', 'Damage', 'shieldAlert'],
    ['console/insurance', 'Insurance', 'shield'],
    ['console/people', 'People', 'users'],
  ],
  [
    ['console/claims', 'Claims', 'receipt', true],
    ['console/branches', 'Branches', 'building', true],
    ['console/transfers', 'Transfers', 'swap', true],
  ],
];

// ---------- routing ----------
function parseHash() {
  const raw = window.location.hash.replace(/^#/, '') || '/explore';
  const [pathPart, qs] = raw.split('?');
  return {
    path: pathPart.replace(/^\/+|\/+$/g, '') || 'explore',
    query: Object.fromEntries(new URLSearchParams(qs || '')),
  };
}

function matchRoute(path) {
  const parts = path.split('/');
  for (const route of routes) {
    const rParts = route.path.split('/');
    if (rParts.length !== parts.length) continue;
    const params = {};
    const ok = rParts.every((seg, i) => {
      if (seg.startsWith(':')) { params[seg.slice(1)] = decodeURIComponent(parts[i]); return true; }
      return seg === parts[i];
    });
    if (ok) return { route, params };
  }
  return null;
}

export function navigate(path) {
  const target = `#${path.startsWith('/') ? path : `/${path}`}`;
  if (window.location.hash === target) render();
  else window.location.hash = target;
}

export function homeFor() {
  if (isStaff()) return '/console';
  return '/explore';
}

let renderToken = 0;
let lastPath = null;

async function render() {
  const token = ++renderToken;
  const { path, query } = parseHash();
  const match = matchRoute(path);
  const { route, params } = match || { route: { path: '*', view: renderNotFound, title: 'Not found' }, params: {} };

  // A dialog left open must not follow the user to another page.
  document.querySelector('#dialog[open] [data-close]')?.click();

  if (route.guest && store.user) { navigate(homeFor()); return; }
  if ((route.auth || route.roles) && !store.user) {
    navigate(`/login?next=${encodeURIComponent(`/${path}`)}`);
    return;
  }
  if (route.roles && !route.roles.includes(store.user.role)) { navigate(homeFor()); return; }

  renderShell(route, path);
  if (path !== lastPath) window.scrollTo({ top: 0 });
  lastPath = path;

  const el = document.getElementById('view');
  const ctx = {
    el,
    params,
    query,
    path,
    navigate,
    isCurrent: () => token === renderToken,
    rerender: render,
  };
  el.addEventListener('click', (e) => {
    if (e.target.closest('[data-action="retry"]')) render();
  });

  try {
    await route.view(ctx);
  } catch (err) {
    if (token === renderToken) {
      el.innerHTML = `<div class="${route.console ? '' : 'section'}">${errorState(err)}</div>`;
    }
  }
}

// ---------- shell ----------
function subLink(href, label, active, count) {
  return `<a class="subnav__link${active ? ' is-active' : ''}" href="#/${href}"${active ? ' aria-current="page"' : ''}>${esc(label)}${count !== undefined ? `<span class="subnav__count" data-count="${esc(href)}"${count ? '' : ' hidden'}>${count}</span>` : ''}</a>`;
}

function renderShell(route, path) {
  const section = route.nav || path.split('/')[0];
  const user = store.user;

  const links = [
    subLink('explore', 'Collection', section === 'explore'),
    subLink('vehicles', 'Vehicles', section === 'vehicles'),
    subLink('branches', 'Branches', section === 'branches'),
    isStaff() ? '' : subLink('saved', 'Saved', section === 'saved' || section === 'compare', savedCars.list().length),
    isCustomer() ? subLink('trips', 'My trips', section === 'trips') : '',
    isStaff() ? subLink('console', isAdmin() ? 'Admin console' : 'Staff console', section === 'console') : '',
    user ? subLink('notifications', 'Notifications', section === 'notifications') : '',
  ].join('');

  const actions = user
    ? `<a class="icon-btn" href="#/notifications" aria-label="Notifications" id="bell">
         ${icon('bell', 18)}<span class="badge" id="bell-badge" hidden></span>
       </a>
       <div class="account">
         <button class="account__btn" id="account-btn" aria-haspopup="menu" aria-expanded="false">
           <span class="avatar">${esc(initials(user.fullName))}</span>
           <span class="account__name">${esc(user.fullName)}</span>
           ${icon('chevronDown', 14)}
         </button>
         <div class="menu" id="account-menu" role="menu" hidden>
           <div class="menu__head">
             <span class="avatar avatar--lg">${esc(initials(user.fullName))}</span>
             <div><b>${esc(user.fullName)}</b><span>${esc(user.email)}</span></div>
           </div>
           <a class="menu__item" href="#/account" role="menuitem">${icon('user')} Account</a>
           ${isCustomer() ? `<a class="menu__item" href="#/trips" role="menuitem">${icon('route')} My trips</a>` : ''}
           ${isStaff() ? `<a class="menu__item" href="#/console" role="menuitem">${icon('grid')} ${isAdmin() ? 'Admin console' : 'Staff console'}</a>` : ''}
           <a class="menu__item" href="#/notifications" role="menuitem">${icon('bell')} Notifications</a>
           <div class="divider" style="margin:6px 8px"></div>
           <button class="menu__item" id="logout-btn" role="menuitem">${icon('logout')} Sign out</button>
         </div>
       </div>`
    : `<a class="btn btn--outline btn--sm" href="#/register">Create account</a>
       <a class="btn btn--sm" href="#/login">Sign in</a>`;

  const main = route.console
    ? `<div class="console">
         <nav class="rail" aria-label="Console">${railHtml(path)}</nav>
         <section class="console__main" id="view"></section>
       </div>`
    : '<main class="page" id="view"></main>';

  document.getElementById('app').innerHTML = `
    <header class="global-nav">
      <div class="global-nav__inner">
        <a class="brand" href="#/${isStaff() ? 'console' : 'explore'}" aria-label="AXLE home">
          <span class="brand-mark">AXLE<i></i></span>
          <span class="brand-sub">Luxury<br>Rental</span>
        </a>
        <div class="topbar__actions">
          <button class="icon-btn" type="button" data-spotlight aria-label="Search (press /)" title="Search  /">${icon('search', 18)}</button>
          ${actions}
        </div>
      </div>
    </header>
    <div class="subnav${route.console ? '' : ' subnav--centred'}" id="subnav">
      <div class="subnav__inner">
        ${route.console ? `<div class="subnav__title">${esc(route.title || 'Axle')}<small>${esc(route.sub || '')}</small></div>` : ''}
        <nav class="subnav__links" aria-label="Sections">${links}</nav>
      </div>
    </div>
    ${main}`;

  bindShell();
  updateChromeShadow();
  if (user) refreshUnread().then(paintBadge);
}

function railHtml(path) {
  return RAIL.map((group, i) => {
    const items = group
      .filter(([, , , adminOnly]) => !adminOnly || isAdmin())
      .map(([href, label, iconName]) => `
        <a class="rail__item${path === href ? ' is-active' : ''}" href="#/${href}"${path === href ? ' aria-current="page"' : ''}>
          <i>${icon(iconName, 19)}</i><span>${esc(label)}</span>
        </a>`).join('');
    if (!items) return '';
    return `${i > 0 ? '<div class="rail__sep"></div>' : ''}<div class="rail__group">${items}</div>`;
  }).join('');
}

export function paintBadge() {
  const badge = document.getElementById('bell-badge');
  if (!badge) return;
  badge.hidden = store.unread === 0;
  badge.textContent = store.unread > 9 ? '9+' : String(store.unread);
}

function bindShell() {
  const btn = document.getElementById('account-btn');
  const menu = document.getElementById('account-menu');
  if (btn && menu) {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      menu.hidden = !menu.hidden;
      btn.setAttribute('aria-expanded', String(!menu.hidden));
    });
    menu.addEventListener('click', (e) => { if (e.target.closest('a')) menu.hidden = true; });
  }
  const logout = document.getElementById('logout-btn');
  if (logout) logout.addEventListener('click', signOut);
}

export async function signOut() {
  try { await api.auth.logout(); } catch { /* session may already be gone */ }
  store.user = null;
  store.unread = 0;
  invalidate();
  toast('You have been signed out');
  navigate('/explore');
}

/** The frosted bar grows a hairline once the page moves under it. */
function updateChromeShadow() {
  const bar = document.getElementById('subnav');
  if (!bar) return;
  bar.classList.toggle('is-scrolled', window.scrollY > 4);
  // Dark while a dark hero (the landing studio, the branch map) is under the bar.
  const scene = document.querySelector('[data-under-nav="dark"]');
  bar.classList.toggle('is-over-dark', !!scene && scene.getBoundingClientRect().bottom > bar.getBoundingClientRect().bottom);
}

// ---------- global listeners ----------
document.addEventListener('click', (e) => {
  const menu = document.getElementById('account-menu');
  if (menu && !menu.hidden && !e.target.closest('.account')) {
    menu.hidden = true;
    document.getElementById('account-btn')?.setAttribute('aria-expanded', 'false');
  }
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    const menu = document.getElementById('account-menu');
    if (menu) menu.hidden = true;
  }
});
window.addEventListener('scroll', updateChromeShadow, { passive: true });
window.addEventListener('hashchange', render);
window.addEventListener('auth:expired', () => {
  if (!store.user) return;
  store.user = null;
  invalidate();
  toast('Your session has ended. Please sign in again.', 'error');
  const { path } = parseHash();
  navigate(`/login?next=${encodeURIComponent(`/${path}`)}`);
});

export function setUser(user) {
  store.user = user;
  invalidate();
  syncSaved();
}

export const roleLabel = (role) => (role === 'ADMINISTRATOR' ? 'Administrator' : humanize(role));

// ---------- boot ----------
(async function boot() {
  bindPrefs();
  bindSpotlight();
  bindTray();
  // The Saved count in the context bar follows the list without a re-render.
  window.addEventListener('prefs:change', (e) => {
    if (e.detail.key !== 'saved') return;
    const n = savedCars.list().length;
    document.querySelectorAll('[data-count="saved"]').forEach((c) => { c.textContent = n; c.hidden = n === 0; });
  });
  document.addEventListener('click', (e) => {
    if (e.target.closest('[data-spotlight]')) { e.preventDefault(); openSpotlight(); }
  });
  await loadUser();
  syncSaved();
  if (!window.location.hash) {
    history.replaceState(null, '', `#${homeFor()}`);
  }
  render();
}());
