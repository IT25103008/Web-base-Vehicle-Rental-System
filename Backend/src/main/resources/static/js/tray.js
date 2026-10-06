// ============================================================
// The compare tray: a small glass capsule along the foot of the
// screen whenever cars are waiting to be compared, on every public
// page except Compare itself.
// ============================================================
import { getVehicle } from './store.js';
import { esc, icon } from './ui.js';
import { compare, COMPARE_MAX } from './prefs.js';

let tray = null;
let token = 0;

async function paint() {
  const my = ++token;
  const ids = compare.list();
  const route = window.location.hash;
  const hide = !ids.length || route.startsWith('#/compare') || route.startsWith('#/console')
    || route.startsWith('#/login') || route.startsWith('#/register');
  if (!tray) {
    tray = document.createElement('div');
    tray.className = 'ctray';
    tray.setAttribute('role', 'region');
    tray.setAttribute('aria-label', 'Compare list');
    document.body.appendChild(tray);
    tray.addEventListener('click', (e) => {
      const x = e.target.closest('[data-tray-remove]');
      if (x) compare.remove(x.dataset.trayRemove);
      if (e.target.closest('[data-tray-clear]')) compare.clear();
    });
  }
  tray.classList.toggle('is-on', !hide);
  document.documentElement.classList.toggle('has-tray', !hide);
  if (hide) return;
  const cars = await Promise.all(ids.map((id) => getVehicle(id).catch(() => null)));
  if (my !== token) return;
  tray.innerHTML = `
    <span class="ctray__icon">${icon('layers', 17)}</span>
    <div class="ctray__cars">${cars.map((v, i) => `<span class="ctray__car">${esc(v?.model || `Car ${ids[i]}`)}
      <button type="button" data-tray-remove="${ids[i]}" aria-label="Remove ${esc(v?.model || 'car')}">${icon('close', 12)}</button></span>`).join('')}
      ${Array.from({ length: COMPARE_MAX - ids.length }, () => '<span class="ctray__slot" aria-hidden="true"></span>').join('')}
    </div>
    <button class="ctray__clear" type="button" data-tray-clear>Clear</button>
    <a class="btn btn--sm" href="#/compare">${ids.length > 1 ? `Compare ${ids.length}` : 'Add one more'} ${icon('arrowRight', 14)}</a>`;
}

export function bindTray() {
  window.addEventListener('prefs:change', (e) => { if (e.detail.key === 'compare') paint(); });
  window.addEventListener('hashchange', paint);
  paint();
}
