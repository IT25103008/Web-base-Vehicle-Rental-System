// ============================================================
// Public screens: explore/search, vehicle detail + reserve,
// branch directory, sign in, create account.
// ============================================================
import { api } from '../api.js';
import { store, getBranches, getVehicle, isCustomer, isStaff } from '../store.js';
import { navigate, homeFor, setUser } from '../app.js';
import { carActions, recent } from '../prefs.js';
import {
  quickRow, mountSearchExtras, composerSection, mountComposer, pulseSection, mountPulse,
  torchLayer, mountTorch, sectionRail, mountSectionRail, mountHeroLight, mountMagnets,
  mountStepsGlow, faqSearch, mountFaqSearch,
} from './landing-fx.js';
import {
  $, $$, esc, icon, money, number, fmtDate, fmtTime, isoDate, addDays, rentalDays, humanize,
  statusChip, vehicleVisual, carArt, field, checkbox, readForm, formError, toast, empty, errorState,
  skeletons, debounce, validateForm, showFieldErrors, errorSummary, markField,
} from '../ui.js';

const today = () => isoDate();
const validDate = (v) => (/^\d{4}-\d{2}-\d{2}$/.test(v || '') ? v : null);

// These mirror util/RentalPolicy.java on the backend. Keeping them in step
// means the form stops an impossible booking before the request is sent.
const MIN_DRIVER_AGE = 18;
export const MAX_RENTAL_DAYS = 30;
export const MAX_ADVANCE_DAYS = 180;

// A vehicle in the workshop or written off cannot be booked at all. One that is
// RENTED or RESERVED today is still bookable for dates it is free — the backend
// decides that from the calendar, not from the status.
export const OFF_ROAD = ['UNDER_MAINTENANCE', 'UNAVAILABLE'];

function latestDobForDriving() {
  const d = new Date();
  d.setFullYear(d.getFullYear() - MIN_DRIVER_AGE);
  return isoDate(d);
}

// The public footer. It takes a branch so the contact column is a real
// address and a real phone number rather than invented boilerplate; where a
// page has no branch in hand it points at the directory instead.
export function footer(branch) {
  const contact = [
    branch?.name ? [icon('building', 16), esc(branch.name)] : null,
    branch && (branch.street || branch.city)
      ? [icon('pin', 16), esc([branch.street, branch.city, branch.district].filter(Boolean).join(', '))]
      : null,
    branch?.contactNumber ? [icon('phone', 16), esc(branch.contactNumber)] : null,
    branch?.openTime ? [icon('clock', 16), `${fmtTime(branch.openTime)} – ${fmtTime(branch.closeTime)}`] : null,
  ].filter(Boolean);

  return `<footer class="site-foot">
    <div class="site-foot__grid">
      <div class="site-foot__brand">
        <span class="brand-mark">AXLE<i></i></span>
        <p>A deliberately small fleet of luxury cars, kept, serviced and handed over by the branch that owns them.</p>
      </div>
      <div>
        <h4>Explore</h4>
        <ul>
          <li><a href="#/explore">The collection</a></li>
          <li><a href="#/vehicles">All vehicles</a></li>
          <li><a href="#/branches">Branches</a></li>
          <li><a href="#/saved">Saved cars</a></li>
          <li><a href="#/compare">Compare</a></li>
          <li><a href="#/login">Sign in</a></li>
          <li><a href="#/register">Create an account</a></li>
        </ul>
      </div>
      <div>
        <h4>Contact</h4>
        <ul class="site-foot__contact">
          ${contact.length
            ? contact.map(([ic, text]) => `<li>${ic}<span>${text}</span></li>`).join('')
            : `<li>${icon('building', 16)}<span><a href="#/branches">See all branches and their hours</a></span></li>`}
        </ul>
      </div>
      <div class="site-foot__cta">
        <b>Ready when you are</b>
        <p>Create an account once, and every request after that takes under a minute.</p>
        <a class="btn btn--sm" href="#/register" style="justify-self:start">Create an account ${icon('arrowRight', 15)}</a>
      </div>
    </div>
    <div class="site-foot__bar">
      <span>© ${new Date().getFullYear()} Axle — Vehicle Rental Management System</span>
      <nav>
        <a href="#/explore">Collection</a>
        <a href="#/branches">Branches</a>
        <button type="button" class="site-foot__search" data-spotlight>${icon('search', 13)} Search <kbd>/</kbd></button>
        <span>SE2030 · Group 9</span>
      </nav>
    </div>
  </footer>`;
}

function branchOptions(branches, selected, { any = false, activeOnly = true } = {}) {
  const list = branches.filter((b) => !activeOnly || b.status === 'ACTIVE');
  const opts = list.map((b) => ({ value: b.branchId, label: `${b.name}${b.city ? ` — ${b.city}` : ''}` }));
  return any ? [{ value: '', label: 'All branches' }, ...opts] : opts;
}

// ==================================================================
// Landing page. It is also the search surface: everything a visitor
// reads on the way down is drawn from the live fleet, so nothing here
// is decoration standing in for data that does not exist.
// ==================================================================

// The five promises are not marketing: each one is a rule the backend
// actually enforces, which is why they can be stated this plainly.
const PROMISES = [
  {
    key: 'review', tab: 'Reviewed', title: 'Reviewed by people',
    text: 'No request is confirmed by a machine. The branch team reads every one, checks the car is free and answers you directly.',
    cta: { label: 'See the collection', target: '#results-section' },
  },
  {
    key: 'licence', tab: 'Verified', title: 'Verified licences only',
    text: 'Your driving licence is checked against your account before any key changes hands, and drivers must be 18 or over.',
    cta: { label: 'Create an account', href: '#/register' },
  },
  {
    key: 'insured', tab: 'Insured', title: 'Never uninsured',
    text: 'A car is listed here only while it holds an active insurance policy. The day cover lapses it leaves the collection.',
    cta: { label: 'See the collection', target: '#results-section' },
  },
  {
    key: 'handover', tab: 'Documented', title: 'Documented handovers',
    text: 'Odometer, fuel level and condition are recorded in front of you at collection, and again on return. Nothing is decided later.',
    cta: { label: 'How it works', target: '#steps-section' },
  },
  {
    key: 'price', tab: 'Fixed price', title: 'The rate you were quoted',
    text: 'The daily rate is held against your dates the moment you send the request. It does not move while you wait for approval.',
    cta: { label: 'Check your dates', target: '#search' },
  },
];

const STEPS = [
  ['calendar', 'Choose your dates', 'Pick-up and return. The fleet immediately narrows to the cars that are genuinely free.'],
  ['car', 'Pick your car', 'Compare the collection by body style, seats, powertrain and daily rate.'],
  ['ticket', 'Send the request', 'Add anything we should know. Your estimate is locked to those dates.'],
  ['users', 'We review it', 'The branch that holds the car checks it over and you get a notification either way.'],
  ['key', 'Collect at the branch', 'Bring your licence. We walk the car with you and record its condition.'],
  ['wallet', 'Drive, return, settle', 'Bring it back to the same branch. Payment is settled at the counter.'],
];

const FAQ = [
  ['How does a booking actually work?',
   'You send a <b>request</b>, not an instant booking. The branch holding the car reviews it, and you are notified the moment it is approved or declined. Nothing is charged while you wait.'],
  ['How far ahead can I book?',
   `Up to <b>${MAX_ADVANCE_DAYS} days</b> in advance, and a single booking can run for up to <b>${MAX_RENTAL_DAYS} days</b>. For anything longer, talk to the branch directly.`],
  ['Do I need a verified driving licence?',
   `Yes. Drivers must be <b>${MIN_DRIVER_AGE} or over</b> with a licence that is still valid, and our team verifies it against your account. Bring the physical licence when you collect.`],
  ['Can I change or cancel?',
   'While a request is still awaiting approval you can move its dates from <b>My trips</b>. You can cancel any time before pick-up, at no cost.'],
  ['Where do I collect the car?',
   'From the branch that holds it — shown on every car in the collection — and it comes back to the same branch. Collection is only during that branch’s opening hours.'],
  ['Is insurance included?',
   'Every car on this page is covered by an active policy for the whole of your rental. A car whose cover has lapsed is withdrawn from the collection automatically.'],
  ['How do I pay?',
   'At the counter when you collect. The estimate you see is the daily rate multiplied by your nights — there is no booking fee and no deposit taken online.'],
];

/**
 * A progressive gaussian blur: five stacked backdrop blurs, each masked to its
 * own band, so the picture underneath goes soft by degrees instead of behind
 * a hard edge. Place it over a photograph; size it with top/height in CSS.
 */
const PBLUR = `<span class="pblur" aria-hidden="true">${'<i></i>'.repeat(5)}</span>`;

// The marque ticker. Each logo was trimmed to its artwork, recoloured to one
// ink and sized to the same visible area (px at 1x), so a long wordmark and a
// round emblem read as the same size. Files are drawn at 2x.
const MARQUES = [
  ['porsche', 'Porsche', 167, 12],
  ['mercedes', 'Mercedes-Benz', 44, 44],
  ['supra', 'Toyota Supra', 87, 22],
  ['mclaren', 'McLaren', 116, 17],
  ['koenigsegg', 'Koenigsegg', 116, 17],
  ['koenigsegg-crest', 'Koenigsegg', 34, 48],
  ['aston-martin', 'Aston Martin', 75, 26],
  ['lamborghini', 'Lamborghini', 91, 21],
  ['ferrari', 'Ferrari', 94, 21],
  ['toyota', 'Toyota', 110, 18],
  ['bmw-m', 'BMW M', 62, 24],
];
const MARQUE_SLOT = 214;   // px - every logo sits in an identical slot
const MARQUE_SPEED = 40;   // px per second

function marqueTicker() {
  const one = MARQUES.map(([key, , w, h]) => `<span class="marque">
      <img src="assets/marques/${key}.webp" width="${w}" height="${h}" alt="" decoding="async">
    </span>`).join('');
  const names = [...new Set(MARQUES.map(([, name]) => name))].join(', ');
  // Written twice: sliding the track by exactly half its width loops with no seam.
  return `<div class="lp-marques is-ticker" id="marques" role="img" aria-label="Marques we rent: ${esc(names)}"
      style="--slot:${MARQUE_SLOT}px; --dur:${Math.round((MARQUES.length * MARQUE_SLOT) / MARQUE_SPEED)}s">
    <div class="lp-marques__track" aria-hidden="true">${one}${one}</div>
  </div>`;
}

const SORTS = [
  ['featured', 'Featured'],
  ['price-asc', 'Price \u2191'],
  ['price-desc', 'Price \u2193'],
  ['newest', 'Newest'],
];

const CARD_SKELETONS = skeletons(6, 'skeleton-grid skeleton-grid--cards');

/** Wrap each word so the headline can rise into place one word at a time. */
function splitWords(words, offset = 0) {
  return words.map((w, i) => `<span class="w"><span style="--i:${i + offset}">${esc(w)}</span></span>`).join(' ');
}

/** Ease a set of figures up from zero once they are on screen. */
function countUp(root) {
  const cells = [...root.querySelectorAll('[data-count]')];
  const still = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  cells.forEach((b) => {
    const target = Number(b.dataset.count);
    const fmt = b.dataset.fmt === 'money' ? (n) => money(n) : (n) => String(n);
    if (still || !target) return;
    const start = performance.now();
    const dur = 1400;
    const step = (now) => {
      const t = Math.min(1, (now - start) / dur);
      const eased = 1 - Math.pow(1 - t, 4);
      b.textContent = fmt(t === 1 ? target : Math.round(target * eased));
      if (t < 1 && b.isConnected) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  });
}

// The dial behind the promise car: an instrument gauge in ink. Five
// stations (one per promise), a pointer blade that swings from behind the
// car, ticks that light as it passes, and a readout inside the dial.
const ARC = { cx: 450, cy: 486, r: 366, from: 202, to: 338 };

const polar = (r, deg) => {
  const t = (deg * Math.PI) / 180;
  return [ARC.cx + r * Math.cos(t), ARC.cy + r * Math.sin(t)];
};
const f = (n) => n.toFixed(1);

function arcPath(r, a0, a1) {
  const [x0, y0] = polar(r, a0);
  const [x1, y1] = polar(r, a1);
  return `M ${f(x0)} ${f(y0)} A ${r} ${r} 0 0 1 ${f(x1)} ${f(y1)}`;
}

/** The angle at which promise `i` sits on the dial. */
const stationAngle = (i) => ARC.from + ((i + 0.5) / PROMISES.length) * (ARC.to - ARC.from);

function arcSvg() {
  const ticks = [];
  for (let a = ARC.from, k = 0; a <= ARC.to + 0.01; a += 2, k += 1) {
    const major = k % 5 === 0;
    const [x0, y0] = polar(ARC.r + 7, a);
    const [x1, y1] = polar(ARC.r + (major ? 22 : 13), a);
    ticks.push(`<line class="tick${major ? ' tick--major' : ''}" data-a="${a}" x1="${f(x0)}" y1="${f(y0)}" x2="${f(x1)}" y2="${f(y1)}"/>`);
  }

  const stations = PROMISES.map((p, i) => {
    const ang = stationAngle(i);
    const [x, y] = polar(ARC.r, ang);
    const [lx, ly] = polar(ARC.r + 44, ang);
    const anchor = Math.abs(lx - ARC.cx) < 30 ? 'middle' : lx < ARC.cx ? 'end' : 'start';
    return `<g class="stop" data-promise="${i}">
      <circle class="stop__hit" cx="${f(x)}" cy="${f(y)}" r="22"/>
      <circle class="stop__dot" cx="${f(x)}" cy="${f(y)}" r="5.5"/>
      <text class="stop__label" x="${f(lx)}" y="${f(ly)}" text-anchor="${anchor}" dominant-baseline="middle">${esc(p.tab.toUpperCase())}</text>
    </g>`;
  }).join('');

  // The blade is drawn pointing straight up (270deg) and rotated about the hub.
  const [bx0, by0] = polar(ARC.r - 68, 270);
  const [bx1, by1] = polar(ARC.r - 10, 270);
  const [hx, hy] = polar(ARC.r, 270);

  return `<svg class="lp-why__arc" viewBox="0 0 900 452" preserveAspectRatio="xMidYMin meet" aria-hidden="true" focusable="false">
    <defs>
      <linearGradient id="dialSweep" gradientUnits="userSpaceOnUse" x1="80" y1="0" x2="820" y2="0">
        <stop offset="0" stop-color="#1d1d1f" stop-opacity=".18"/>
        <stop offset=".55" stop-color="#1d1d1f" stop-opacity=".75"/>
        <stop offset="1" stop-color="#1d1d1f"/>
      </linearGradient>
      <filter id="dialGlow" x="-10%" y="-10%" width="120%" height="120%"><feGaussianBlur stdDeviation="5"/></filter>
    </defs>

    <path class="bezel" d="${arcPath(ARC.r + 15, ARC.from - 2, ARC.to + 2)}"/>
    <path class="ring" d="${arcPath(ARC.r, ARC.from - 2, ARC.to + 2)}"/>
    <path class="ring ring--outer" d="${arcPath(ARC.r + 30, ARC.from + 4, ARC.to - 4)}"/>
    <path class="ring ring--dash" d="${arcPath(ARC.r - 72, ARC.from + 10, ARC.to - 10)}"/>
    ${ticks.join('')}

    <path class="sweep-glow" id="arc-glow" pathLength="1" stroke-dasharray="1 1" stroke-dashoffset="1"
          d="${arcPath(ARC.r, ARC.from, ARC.to)}" filter="url(#dialGlow)"/>
    <path class="sweep" id="arc-live" pathLength="1" stroke-dasharray="1 1" stroke-dashoffset="1"
          d="${arcPath(ARC.r, ARC.from, ARC.to)}"/>

    <g class="blade" id="arc-blade">
      <path d="M ${f(bx0 - 0.8)} ${f(by0)} L ${f(bx1 - 2)} ${f(by1)} L ${f(bx1 + 2)} ${f(by1)} L ${f(bx0 + 0.8)} ${f(by0)} Z"/>
      <circle class="blade__halo" cx="${f(hx)}" cy="${f(hy)}" r="14"/>
    </g>

    ${stations}

    <g class="readout" id="arc-readout">
      <text class="readout__num" x="${ARC.cx}" y="238" text-anchor="middle"><tspan id="arc-num">01</tspan><tspan class="readout__of" dx="6">/ ${String(PROMISES.length).padStart(2, '0')}</tspan></text>
      <text class="readout__label" id="arc-label" x="${ARC.cx}" y="266" text-anchor="middle">${esc(PROMISES[0].tab.toUpperCase())}</text>
    </g>
  </svg>`;
}

// ==================================================================
// Explore / landing
// ==================================================================
export async function renderExplore({ el, query }) {
  let pickup = validDate(query.pickup) || addDays(1);
  if (pickup < today()) pickup = today();
  let ret = validDate(query.return) || addDays(3, new Date(`${pickup}T00:00:00`));
  if (ret <= pickup) ret = addDays(1, new Date(`${pickup}T00:00:00`));
  const branchId = query.branch || '';

  const branches = await getBranches().catch(() => []);
  const activeBranches = branches.filter((b) => b.status === 'ACTIVE');
  const byId = new Map(branches.map((b) => [b.branchId, b]));

  // `list` follows whatever dates the visitor picks. `collection` is the first
  // answer the server gave and never changes, so the marque row, the figures
  // and the marquee rail stay populated even after a search that finds nothing.
  const state = { list: [], collection: [], category: 'All', sort: 'featured', pickup, ret, branchId, promise: 0,
    f: { q: '', fuel: 'All', seats: 0, max: null, lo: 0, hi: 0 } };

  el.innerHTML = `
    <section class="lp-hero" id="top">
      <div class="lp-scene" data-under-nav="dark">
        <span class="lp-scene__lattice" aria-hidden="true"></span>
        <div class="lp-scene__media">
          <img class="lp-scene__img" src="assets/studio-rear-2000.webp"
               srcset="assets/studio-rear-1200.webp 1200w, assets/studio-rear-2000.webp 2000w"
               sizes="(max-width: 620px) 160vw, 118vw" alt="" decoding="async" fetchpriority="high">
          <span class="lp-scene__tail" aria-hidden="true"></span>
          <span class="lp-scene__spill" aria-hidden="true"></span>
          <span class="lp-scene__scan" aria-hidden="true"></span>
          <span class="lp-scene__sweep" aria-hidden="true"></span>
          <div class="lp-tag lp-tag--left" style="--x:25.5%; --y:51%" aria-hidden="true">
            <i class="lp-tag__dot"></i><i class="lp-tag__line"></i>
            <span class="lp-tag__box"><small>Self drive</small>Keys at the branch</span>
          </div>
          <div class="lp-tag lp-tag--right" style="--x:79.4%; --y:46%" aria-hidden="true">
            <i class="lp-tag__dot"></i><i class="lp-tag__line"></i>
            <span class="lp-tag__box"><small>Price held</small>Fixed at booking</span>
          </div>
        </div>

        <span class="lp-scene__light" aria-hidden="true"></span>
        <div class="lp-scene__copy">
          <p class="eyebrow lp-hero__eyebrow">The luxury collection${activeBranches.length ? ` · ${activeBranches.length} location${activeBranches.length === 1 ? '' : 's'}` : ''}</p>
          <h1 class="display">${splitWords(['Drive', 'something'])} <em>${splitWords(['extraordinary.'], 2)}</em></h1>
          <p class="lede">A curated fleet of luxury cars, held at our branches across the island. Self drive, collected at the branch, price held at booking.</p>
          <div class="lp-hero__cta">
            <button class="btn btn--lg" type="button" data-scroll="#search" data-center data-magnetic>Reserve your car ${icon('arrowRight', 17)}</button>
            <button class="btn btn--lg btn--tonal" type="button" data-scroll="#composer-section" data-magnetic>Compose your trip</button>
          </div>
        </div>

        <div class="lp-scene__hud">
          <div class="lp-stage__live" id="stage-live" hidden><i aria-hidden="true"></i><span></span></div>
          <span class="lp-stage__index" aria-hidden="true">Nº 01 — The collection</span>
        </div>
      </div>

      <form class="lp-searchbar" id="search" novalidate>
        ${field({ name: 'pickup', label: 'Pick-up date', type: 'date', value: pickup, min: today(), max: addDays(MAX_ADVANCE_DAYS) })}
        ${field({ name: 'return', label: 'Return date', type: 'date', value: ret, min: addDays(1, new Date(`${pickup}T00:00:00`)), max: addDays(MAX_RENTAL_DAYS, new Date(`${pickup}T00:00:00`)) })}
        ${field({ name: 'branch', label: 'Collection branch', type: 'select', value: branchId, options: branchOptions(branches, branchId, { any: true }) })}
        <button class="btn btn--lg" type="submit">${icon('search', 16)} Search</button>
      </form>
      ${quickRow()}

      ${marqueTicker()}
    </section>

    <section class="section" id="intro-section" data-rail-label="House view">
      <div class="lp-intro" data-reveal>
        <div>
          <p class="eyebrow rule"><span class="eyebrow__n">01</span> The house view</p>
          <h2 class="title-lg" style="margin-top:18px">Drive luxury.<br>Live freedom.</h2>
        </div>
        <div class="lp-intro__body">
          <p>We keep a small fleet on purpose. Every car is bought for how it drives, kept on a service schedule, photographed as it actually is, and handed over by someone who knows it.</p>
          <p><b>What you see is what is free.</b> This page is not a brochure — it is the live fleet, filtered to the dates you chose. If a car is out, in the workshop, or out of insurance cover, it is not shown to you at all.</p>
        </div>
      </div>
      <div class="lp-stats" id="stats" data-reveal style="--d:1"></div>
    </section>

    ${composerSection('02')}

    <section class="section" id="results-section" data-rail-label="Collection">
      <div class="section-head" data-reveal>
        <div>
          <p class="eyebrow rule"><span class="eyebrow__n">03</span> Find your perfect ride</p>
          <h2 class="title-lg" style="margin-top:16px">Available for your dates</h2>
          <p id="result-meta"></p>
        </div>
      </div>
      <div class="results-bar">
        <div class="chip-row" id="categories"></div>
        <div class="seg" id="sort" role="radiogroup" aria-label="Order">
          ${SORTS.map(([key, label]) => `<button type="button" role="radio" data-sort="${key}"
            class="${key === 'featured' ? 'is-active' : ''}" aria-checked="${key === 'featured'}">${label}</button>`).join('')}
        </div>
      </div>
      <div class="filters" id="filters"></div>
      <div id="results">${CARD_SKELETONS}</div>
      <div id="recent-strip"></div>
    </section>

    ${pulseSection('04')}

    <section class="section" id="picks-section" data-rail-label="Marquee cars" hidden>
      <div class="section-head" data-reveal>
        <div>
          <p class="eyebrow rule"><span class="eyebrow__n">05</span> Top of the collection</p>
          <h2 class="title-lg" style="margin-top:16px">The marquee cars</h2>
          <p>The highest-specified cars free to reserve right now.</p>
        </div>
        <div class="lp-navbtns">
          <button class="icon-btn" type="button" data-rail="picks" data-dir="-1" aria-label="Scroll left">${icon('chevronLeft', 18)}</button>
          <button class="icon-btn" type="button" data-rail="picks" data-dir="1" aria-label="Scroll right">${icon('chevronRight', 18)}</button>
        </div>
      </div>
      <div class="lp-rail" id="picks"></div>
    </section>

    <section class="section" id="why-section" data-rail-label="The standard">
      <div class="lp-why" data-reveal>
        <div class="section-head" style="margin-bottom:0">
          <div>
            <p class="eyebrow rule"><span class="eyebrow__n">06</span> The standard</p>
            <h2 class="title-lg" style="margin-top:16px">Luxury meets reliability</h2>
            <p>Five promises, each one a rule the system enforces rather than a line of copy.</p>
          </div>
          <div class="seg" id="why-tabs" role="tablist" aria-label="Our promises">
            ${PROMISES.map((p, i) => `<button type="button" role="tab" data-promise="${i}"
              class="${i === 0 ? 'is-active' : ''}" aria-selected="${i === 0}">${esc(p.tab)}</button>`).join('')}
          </div>
        </div>
        <div class="lp-why__stage">
          ${arcSvg()}
          <div class="lp-why__card" id="why-card"></div>
        </div>
      </div>
    </section>

    <section class="section" id="steps-section" data-rail-label="How it works">
      <div class="section-head" data-reveal>
        <div>
          <p class="eyebrow rule"><span class="eyebrow__n">07</span> How it works</p>
          <h2 class="title-lg" style="margin-top:16px">Simple. Fast. Hassle-free.</h2>
          <p>Six steps from an idea to a set of keys. Nothing hidden in between.</p>
        </div>
        <a class="btn btn--tonal" href="#/register">Create an account ${icon('arrowRight', 15)}</a>
      </div>
      <div class="lp-steps">
        ${STEPS.map(([ic, title, text], i) => `
          <article class="lp-step" data-reveal style="--d:${i % 3}">
            <span class="lp-step__n" aria-hidden="true">${i + 1}</span>
            <i>${icon(ic, 18)}</i>
            <h3>${esc(title)}</h3>
            <p>${esc(text)}</p>
          </article>`).join('')}
        <aside class="lp-steps__art" data-reveal style="--d:2">
          <img src="assets/motion-portrait-1200.jpg"
               srcset="assets/motion-portrait-700.jpg 700w, assets/motion-portrait-1200.jpg 1200w"
               sizes="(max-width: 1180px) 100vw, 320px" alt="" loading="lazy" decoding="async">
          ${PBLUR}
          <h3>Not sure yet?</h3>
          <p>Browse the whole collection first. You only need an account when you are ready to send a request.</p>
          <button class="btn btn--sm" type="button" data-scroll="#results-section">Browse the fleet</button>
        </aside>
      </div>
    </section>

    <section class="section" id="band-section" data-rail-label="Handovers">
      <div class="lp-band" data-reveal>
        <img class="lp-band__img" src="assets/silhouette-side-2000.jpg"
             srcset="assets/silhouette-side-1000.jpg 1000w, assets/silhouette-side-2000.jpg 2000w"
             sizes="(max-width: 1340px) 100vw, 1260px" alt="" loading="lazy" decoding="async">
        ${torchLayer('assets/silhouette-side-1000.jpg 1000w, assets/silhouette-side-2000.jpg 2000w', '(max-width: 1340px) 100vw, 1260px', 'assets/silhouette-side-2000.jpg')}
        <span class="lp-band__shade" aria-hidden="true"></span>
        <p class="eyebrow"><span class="eyebrow__n">08</span> Held to a standard</p>
        <h2>Every handover is <em>documented.</em></h2>
        <p class="lp-band__lede">${icon('sparkle', 14)} Point anywhere on the car — or on a number — to walk it the way we do at the counter.</p>
        <div class="lp-band__grid">
          <div>
            <i>${icon('gauge', 18)}</i>
            <h3>Recorded, not remembered</h3>
            <p>Odometer and fuel level are written down at collection and again on return, with you standing there.</p>
          </div>
          <div>
            <i>${icon('shieldAlert', 18)}</i>
            <h3>Damage logged the same day</h3>
            <p>Anything found on a car is raised against that car, with an estimate, before it goes back out.</p>
          </div>
          <div>
            <i>${icon('wrench', 18)}</i>
            <h3>Off the road when it should be</h3>
            <p>A car due for service disappears from the collection until the work is signed off. It is never quietly rented out.</p>
          </div>
        </div>
      </div>
    </section>

    <section class="section" id="branch-section" data-rail-label="Branches" hidden>
      <div class="section-head" data-reveal>
        <div>
          <p class="eyebrow rule"><span class="eyebrow__n">09</span> Where to collect</p>
          <h2 class="title-lg" style="margin-top:16px">Our branches</h2>
          <p>Every car is collected from — and returned to — the branch that keeps it.</p>
        </div>
        <div class="lp-navbtns">
          <button class="icon-btn" type="button" data-rail="branch-rail" data-dir="-1" aria-label="Scroll left">${icon('chevronLeft', 18)}</button>
          <button class="icon-btn" type="button" data-rail="branch-rail" data-dir="1" aria-label="Scroll right">${icon('chevronRight', 18)}</button>
        </div>
      </div>
      <div class="lp-rail" id="branch-rail"></div>
    </section>

    <section class="section" id="faq-section" data-rail-label="Questions">
      <div class="lp-faq" data-reveal>
        <div>
          <p class="eyebrow rule"><span class="eyebrow__n">10</span> Questions</p>
          <h2 class="title-lg" style="margin-top:16px">Got questions?<br>We've got answers.</h2>
          <div class="lp-faq__aside" style="margin-top:26px">
            ${faqSearch()}
            <p class="muted" style="font-size:14px;max-width:38ch">Anything not covered here, the branch team will answer in person — their hours and numbers are on the branches page.</p>
            <a class="btn btn--tonal btn--sm" href="#/branches" style="justify-self:start">Find a branch ${icon('arrowRight', 15)}</a>
          </div>
        </div>
        <div class="lp-faq__list">
          ${FAQ.map(([q, a], i) => `
            <details name="faq"${i === 0 ? ' open' : ''}>
              <summary>${esc(q)}<span class="icon-btn icon-btn--ghost" aria-hidden="true">${icon('chevronDown', 16)}</span></summary>
              <div class="lp-faq__body">${a}</div>
            </details>`).join('')}
        </div>
      </div>
    </section>
    <section class="section" id="finale-section" data-rail-label="Your drive">
      <div class="lp-finale" data-reveal>
        <img class="lp-finale__img" src="assets/taillight-rear-2000.jpg"
             srcset="assets/taillight-rear-1000.jpg 1000w, assets/taillight-rear-2000.jpg 2000w"
             sizes="(max-width: 1340px) 100vw, 1260px" alt="" loading="lazy" decoding="async">
        <div class="lp-finale__body">
          <p class="eyebrow"><span class="eyebrow__n">11</span> Your next drive</p>
          <h2>The keys are <em>waiting.</em></h2>
          <p id="finale-line">Choose a car tonight. The branch that keeps it reviews your request and tells you either way.</p>
          <div class="lp-finale__cta">
            <button class="btn btn--lg" type="button" data-scroll="#results-section" data-magnetic>See what's free ${icon('arrowRight', 17)}</button>
            <a class="btn btn--lg lp-finale__ghost" href="#/register">Create an account</a>
          </div>
        </div>
      </div>
    </section>
    ${sectionRail()}
    ${footer(activeBranches[0])}`;

  const form = $('#search', el);
  const results = $('#results', el);
  const hero = $('.lp-hero', el);
  const stageImg = $('.lp-scene__img', hero);
  const returnInput = form.elements.return;

  const reveal = () => hero.classList.add('is-loaded');
  if (stageImg.complete && stageImg.naturalWidth) reveal();
  else stageImg.addEventListener('load', reveal, { once: true });
  stageImg.addEventListener('error', reveal, { once: true });

  // The car is placed under the copy by CSS, which needs to know how tall the
  // copy actually set - it changes with the width, the wrap and the font.
  const scene = $('.lp-scene', hero);
  const copy = $('.lp-scene__copy', hero);
  const fitScene = () => {
    if (!scene.isConnected) { window.removeEventListener('resize', fitScene); return; }
    scene.style.setProperty('--rt', `${Math.ceil(copy.offsetTop + copy.offsetHeight)}px`);
    // On a screen too short for everything, the tags give way to the copy.
    scene.classList.remove('is-crowded');
    const lines = [...copy.querySelectorAll('.lede, .lp-hero__cta > *')].map((n) => n.getBoundingClientRect());
    const hit = $$('.lp-tag__box', scene).some((t) => {
      const r = t.getBoundingClientRect();
      return r.width && lines.some((c) => r.top < c.bottom + 10 && r.right > c.left - 10 && r.left < c.right + 10);
    });
    scene.classList.toggle('is-crowded', hit);
  };
  fitScene();
  // The shell's context bar re-checks what is under it on scroll; the scene
  // has only just arrived, so ask it once now.
  window.dispatchEvent(new Event('scroll'));
  if ('ResizeObserver' in window) new ResizeObserver(fitScene).observe(copy);
  window.addEventListener('resize', fitScene, { passive: true });
  document.fonts?.ready.then(fitScene);

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const v = readForm(form);
    if (!v.pickup || !v.return) { toast('Choose both a pick-up and a return date', 'error'); return; }
    if (v.pickup < today()) { toast('Pick-up cannot be in the past', 'error'); return; }
    if (v.return <= v.pickup) { toast('Return must be after pick-up', 'error'); return; }
    if (rentalDays(v.pickup, v.return) > MAX_RENTAL_DAYS) {
      toast(`A single booking cannot be longer than ${MAX_RENTAL_DAYS} days`, 'error');
      return;
    }
    if (v.pickup > addDays(MAX_ADVANCE_DAYS)) {
      toast(`Bookings can only be made up to ${MAX_ADVANCE_DAYS} days in advance`, 'error');
      return;
    }
    state.pickup = v.pickup;
    state.ret = v.return;
    state.branchId = v.branch || '';
    load(true);
  });

  // Moving the pick-up date moves the whole allowed return window with it.
  form.addEventListener('change', (e) => {
    if (e.target.name !== 'pickup' || !e.target.value) return;
    const from = new Date(`${e.target.value}T00:00:00`);
    returnInput.min = addDays(1, from);
    returnInput.max = addDays(MAX_RENTAL_DAYS, from);
    if (!returnInput.value || returnInput.value < returnInput.min) returnInput.value = returnInput.min;
    if (returnInput.value > returnInput.max) returnInput.value = returnInput.max;
  });

  el.addEventListener('click', (e) => {
    const chip = e.target.closest('[data-category]');
    if (chip) { state.category = chip.dataset.category; draw(); return; }
    if (e.target.closest('[data-action="reload-results"]')) { load(); return; }

    const scroll = e.target.closest('[data-scroll]');
    if (scroll) {
      $(scroll.dataset.scroll, el)?.scrollIntoView({
        behavior: 'smooth',
        block: scroll.hasAttribute('data-center') ? 'center' : 'start',
      });
      return;
    }

    const rail = e.target.closest('[data-rail]');
    if (rail) {
      const track = $(`#${rail.dataset.rail}`, el);
      const step = (track?.firstElementChild?.getBoundingClientRect().width || 320) + 16;
      track?.scrollBy({ left: step * Number(rail.dataset.dir), behavior: 'smooth' });
      return;
    }

    const tab = e.target.closest('[data-promise]');
    if (tab) { state.promise = Number(tab.dataset.promise); drawPromise(); }
  });

  // Filters: typed, tapped and dragged - the grid follows at once.
  const typed = debounce(() => draw(), 120);
  el.addEventListener('input', (e) => {
    if (e.target.id === 'f-q') { state.f.q = e.target.value; typed(); }
    if (e.target.id === 'f-max') {
      state.f.max = Number(e.target.value);
      $('#f-max-out', el).textContent = money(state.f.max);
      e.target.style.setProperty('--p', `${(((state.f.max - state.f.lo) / (state.f.hi - state.f.lo)) * 100).toFixed(1)}%`);
      draw();
    }
  });
  el.addEventListener('click', (e) => {
    const fuel = e.target.closest('[data-fuel]');
    const seats = e.target.closest('[data-seats]');
    if (fuel) state.f.fuel = fuel.dataset.fuel;
    if (seats) state.f.seats = Number(seats.dataset.seats);
    if (e.target.closest('[data-f-reset]')) Object.assign(state.f, { q: '', fuel: 'All', seats: 0, max: state.f.hi });
    if (e.target.closest('[data-recent-clear]')) {
      try { localStorage.removeItem('axle.recent.v1'); } catch { /* ignore */ }
      $('#recent-strip', el).innerHTML = '';
      return;
    }
    if (fuel || seats || e.target.closest('[data-f-reset]')) { paintFilters(); draw(); }
  });

  // Sort order.
  $('#sort', el).addEventListener('click', (e) => {
    const b = e.target.closest('[data-sort]');
    if (!b) return;
    state.sort = b.dataset.sort;
    $$('#sort [data-sort]', el).forEach((x) => {
      const on = x === b;
      x.classList.toggle('is-active', on);
      x.setAttribute('aria-checked', String(on));
    });
    draw();
  });

  // The spotlight on each card follows the pointer.
  results.addEventListener('pointermove', (e) => {
    const card = e.target.closest('.vcard');
    if (!card) return;
    const r = card.getBoundingClientRect();
    card.style.setProperty('--mx', `${e.clientX - r.left}px`);
    card.style.setProperty('--my', `${e.clientY - r.top}px`);
  });

  // Blocks rise into place as they arrive. Hidden only once we know the
  // observer exists, so without it nothing is ever left invisible.
  let statsIn = false;
  if ('IntersectionObserver' in window && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    el.classList.add('can-reveal');
    const io = new IntersectionObserver((entries) => {
      entries.forEach((en) => {
        if (!en.isIntersecting) return;
        en.target.classList.add('is-in');
        io.unobserve(en.target);
        if (en.target.id === 'stats') { statsIn = true; countUp(en.target); }
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -6% 0px' });
    $$('[data-reveal]', el).forEach((n) => io.observe(n));
  }

  // The stage photograph drifts slower than the page.
  const media = $('.lp-scene__media', el);
  if (media && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    let ticking = false;
    const onScroll = () => {
      if (!media.isConnected) { window.removeEventListener('scroll', onScroll); return; }
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(() => {
        ticking = false;
        const r = media.parentElement.getBoundingClientRect();
        if (r.bottom < 0 || r.top > innerHeight) return;
        const shift = Math.max(-1, Math.min(1, (r.top + r.height / 2 - innerHeight / 2) / innerHeight));
        media.style.transform = `translate3d(0, ${(shift * -5).toFixed(2)}%, 0)`;
      });
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
  }

  // ---------- the interactive pieces (landing-fx.js) ----------
  const setDates = (p, r) => {
    form.elements.pickup.value = p;
    const from = new Date(`${p}T00:00:00`);
    returnInput.min = addDays(1, from);
    returnInput.max = addDays(MAX_RENTAL_DAYS, from);
    returnInput.value = r;
  };
  const extras = mountSearchExtras(el, form, { onPick: setDates });
  const qsNow = () => {
    const qs = new URLSearchParams({ pickup: state.pickup, return: state.ret });
    if (state.branchId) qs.set('branch', state.branchId);
    return qs.toString();
  };
  const composer = mountComposer(el, {
    list: () => state.list,
    nights: () => rentalDays(state.pickup, state.ret),
    qs: qsNow,
    branch: (id) => byId.get(id),
    // "See the cars that fit": the composer's answers become the grid's filters.
    apply({ seats, fuel, max }) {
      state.category = 'All';
      Object.assign(state.f, {
        q: '', seats: seats > 1 ? seats : 0, fuel: fuel === 'Any' ? 'All' : fuel,
        max: Math.ceil(max / 500) * 500,
      });
      paintFilters();
      draw();
      $('#results-section', el).scrollIntoView({ behavior: 'smooth', block: 'start' });
    },
  });
  const pulse = mountPulse(el, {
    branchId: () => state.branchId,
    onSearch(p, r) {
      setDates(p, r);
      state.pickup = p;
      state.ret = r;
      state.branchId = form.elements.branch.value || '';
      extras.check();
      load(true);
    },
  });
  mountTorch(el);
  const railNav = mountSectionRail(el);
  mountHeroLight(el);
  mountMagnets(el);
  mountStepsGlow(el);
  mountFaqSearch(el);

  drawPromise();
  load();

  // ---------- data ----------
  async function load(scroll = false) {
    const qs = new URLSearchParams({ pickup: state.pickup, return: state.ret });
    if (state.branchId) qs.set('branch', state.branchId);
    history.replaceState(null, '', `#/explore?${qs}`);
    results.innerHTML = CARD_SKELETONS;
    if (scroll) $('#results-section', el).scrollIntoView({ behavior: 'smooth', block: 'start' });
    try {
      state.list = await api.vehicles.search(state.pickup, state.ret, state.branchId || undefined);
      state.category = 'All';
      try { sessionStorage.setItem('axle.dates.v1', JSON.stringify({ pickup: state.pickup, return: state.ret })); } catch { /* ignore */ }
      paintFilters();
      if (!state.collection.length) {
        // If the first answer was empty the visitor asked for awkward dates —
        // fall back to a default window so the rest of the page still has a
        // fleet to describe.
        state.collection = state.list.length
          ? state.list
          : await api.vehicles.search(addDays(1), addDays(3)).catch(() => []);
        paintCollection();
      }
      draw();
      paintRecent();
      composer.refresh();
      pulse.refresh();
      railNav.refresh();
    } catch (err) {
      results.innerHTML = errorState(err, 'reload-results');
    }
  }

  // ---------- the parts that describe the fleet as a whole ----------
  function paintCollection() {
    const list = state.collection;

    const stats = $('#stats', el);
    if (list.length) {
      const prices = list.map((v) => Number(v.rentalPricePerDay)).filter((n) => n > 0);
      const cells = [
        [list.length, 'Cars in the collection'],
        [activeBranches.length, `Pick-up ${activeBranches.length === 1 ? 'branch' : 'branches'}`],
        [new Set(list.map((v) => v.category).filter(Boolean)).size, 'Body styles'],
        [new Set(list.map((v) => v.fuelType).filter(Boolean)).size, 'Powertrains'],
        [prices.length ? Math.min(...prices) : 0, 'Daily rate from', 'money'],
      ];
      // Written at their final values, so the figures are right even if the
      // count-up never runs; the animation only replays them from zero.
      stats.innerHTML = cells.map(([value, label, fmt]) => `<div class="lp-stat">
        <b data-count="${value}"${fmt ? ` data-fmt="${fmt}"` : ''}>${esc(fmt === 'money' ? (value ? money(value) : '\u2014') : value)}</b>
        <span>${esc(label)}</span></div>`).join('');
      if (statsIn) countUp(stats);
    } else {
      stats.hidden = true;
    }

    const picks = [...list]
      .sort((a, b) => Number(b.rentalPricePerDay) - Number(a.rentalPricePerDay))
      .slice(0, 8);
    if (picks.length > 2) {
      $('#picks-section', el).hidden = false;
      $('#picks', el).innerHTML = picks.map((v, i) => pickCard(v, byId.get(v.branchId), i)).join('');
    }

    if (activeBranches.length) {
      $('#branch-section', el).hidden = false;
      $('#branch-rail', el).innerHTML = activeBranches.map((b) => branchCard(b)).join('');
    }
  }

  function drawPromise() {
    const p = PROMISES[state.promise];
    $$('#why-tabs [data-promise]', el).forEach((b) => {
      const on = Number(b.dataset.promise) === state.promise;
      b.classList.toggle('is-active', on);
      b.setAttribute('aria-selected', String(on));
    });

    const card = $('#why-card', el);
    card.innerHTML = `
      <div class="lp-why__art">
        <img src="assets/sportscar-side-1200.webp"
             srcset="assets/sportscar-side-700.webp 700w, assets/sportscar-side-1200.webp 1200w"
             sizes="(max-width: 620px) 86vw, 380px" alt="" loading="lazy" decoding="async">
      </div>
      <h3>${esc(p.title)}</h3>
      <p>${esc(p.text)}</p>
      ${p.cta.href
        ? `<a class="btn btn--sm" href="${esc(p.cta.href)}">${esc(p.cta.label)} ${icon('arrowRight', 15)}</a>`
        : `<button class="btn btn--sm" type="button" data-scroll="${esc(p.cta.target)}">${esc(p.cta.label)} ${icon('arrowRight', 15)}</button>`}`;
    card.classList.remove('is-swapping');
    void card.offsetWidth;   // restart the entry animation
    card.classList.add('is-swapping');

    // Swing the dial to the active promise.
    const angle = stationAngle(state.promise);
    const progress = (angle - ARC.from) / (ARC.to - ARC.from);
    ['#arc-live', '#arc-glow'].forEach((id) => {
      const path = $(id, el);
      if (path) path.style.strokeDashoffset = String(1 - progress);
    });
    const blade = $('#arc-blade', el);
    if (blade) blade.style.transform = `rotate(${(angle - 270).toFixed(2)}deg)`;
    $$('.lp-why__arc .tick', el).forEach((t) => t.classList.toggle('is-on', Number(t.dataset.a) <= angle + 0.01));
    $$('.lp-why__arc .stop', el).forEach((s) => {
      const i = Number(s.dataset.promise);
      s.classList.toggle('is-on', i === state.promise);
      s.classList.toggle('is-past', i < state.promise);
    });
    const num = $('#arc-num', el);
    if (num) num.textContent = String(state.promise + 1).padStart(2, '0');
    const label = $('#arc-label', el);
    if (label) label.textContent = p.tab.toUpperCase();
    const readout = $('#arc-readout', el);
    if (readout) {
      readout.classList.remove('is-swapping');
      void readout.getBoundingClientRect();
      readout.classList.add('is-swapping');
    }
  }

  // ---------- the results grid ----------
  function draw() {
    const nights = rentalDays(state.pickup, state.ret);
    const branchName = state.branchId ? byId.get(Number(state.branchId))?.name : null;
    $('#result-meta', el).textContent =
      `${state.list.length} car${state.list.length === 1 ? '' : 's'} · ${fmtDate(state.pickup)} → ${fmtDate(state.ret)} · ${nights} night${nights === 1 ? '' : 's'}${branchName ? ` · ${branchName}` : ' · all branches'}`;

    const categories = ['All', ...new Set(state.list.map((v) => v.category).filter(Boolean))];
    $('#categories', el).innerHTML = categories.length > 2
      ? categories.map((c) => `<button class="chip${c === state.category ? ' is-active' : ''}" data-category="${esc(c)}">${esc(c)}</button>`).join('')
      : '';

    const live = $('#stage-live', el);
    live.hidden = false;
    $('span', live).textContent = `${state.list.length} car${state.list.length === 1 ? '' : 's'} free for your dates`;
    if (state.list.length) {
      const where = new Set(state.list.map((v) => v.branchId)).size;
      $('#finale-line', el).textContent = `${state.list.length} car${state.list.length === 1 ? ' is' : 's are'} free for your dates across ${where} branch${where === 1 ? '' : 'es'}. Choose one now \u2014 the branch that keeps it reviews your request and tells you either way.`;
    }

    const inCategory = state.category === 'All' ? state.list : state.list.filter((v) => v.category === state.category);
    const list = orderList(inCategory.filter(passes));
    const filtered = filtersOn();
    const reset = $('#f-reset', el);
    if (reset) reset.hidden = !filtered;
    if (filtered) $('#result-meta', el).textContent = `Showing ${list.length} of ${state.list.length} \u00b7 ${fmtDate(state.pickup)} \u2192 ${fmtDate(state.ret)} \u00b7 ${nights} night${nights === 1 ? '' : 's'}`;
    if (!list.length) {
      results.innerHTML = filtered && state.list.length
        ? empty({
          icon: 'filter', title: 'No cars match these filters',
          text: `${state.list.length} car${state.list.length === 1 ? ' is' : 's are'} free for your dates \u2014 loosen a filter to see ${state.list.length === 1 ? 'it' : 'them'}.`,
          action: '<button class="btn btn--tonal" type="button" data-f-reset>Reset filters</button>',
        })
        : empty({
          icon: 'car',
          title: 'No cars available for these dates',
          text: 'Try different dates or another branch — the collection opens up as journeys end.',
        });
      return;
    }
    const qs = new URLSearchParams({ pickup: state.pickup, return: state.ret });
    if (state.branchId) qs.set('branch', state.branchId);
    // With the default order and enough cars to fill a row, the dearest one
    // leads, set across two columns.
    const feature = state.sort === 'featured' && list.length >= 3 && !filtered;
    results.innerHTML = `<div class="vehicle-grid">${list.map((v, i) =>
      vehicleCard(v, byId.get(v.branchId), nights, qs, i, list.length, feature && i === 0)).join('')}</div>`;
  }

  // ---------- filters ----------
  function filtersOn() {
    const f = state.f;
    return Boolean(f.q.trim() || f.fuel !== 'All' || f.seats || (f.max !== null && f.max < f.hi));
  }
  function passes(v) {
    const f = state.f;
    const q = f.q.trim().toLowerCase();
    if (q && !`${v.model} ${v.category} ${v.fuelType} ${byId.get(v.branchId)?.name || ''}`.toLowerCase().includes(q)) return false;
    if (f.fuel !== 'All' && v.fuelType !== f.fuel) return false;
    if (f.seats && (Number(v.passengerCapacity) || 0) < f.seats) return false;
    if (f.max !== null && Number(v.rentalPricePerDay) > f.max) return false;
    return true;
  }
  function paintFilters() {
    const src = state.list;
    const host = $('#filters', el);
    if (!src.length) { host.innerHTML = ''; return; }
    const f = state.f;
    const fuels = [...new Set(src.map((v) => v.fuelType).filter(Boolean))].sort();
    const prices = src.map((v) => Number(v.rentalPricePerDay)).filter((n) => n > 0);
    f.lo = prices.length ? Math.floor(Math.min(...prices) / 500) * 500 : 0;
    f.hi = prices.length ? Math.ceil(Math.max(...prices) / 500) * 500 : 0;
    if (f.max === null || f.max > f.hi || f.max < f.lo) f.max = f.hi;
    if (!fuels.includes(f.fuel)) f.fuel = 'All';
    if (f.seats && !src.some((v) => (Number(v.passengerCapacity) || 0) >= f.seats)) f.seats = 0;
    const seats = [...new Set([0, 4, 5, 7, f.seats])].sort((a, b) => a - b)
      .filter((n) => !n || src.some((v) => (Number(v.passengerCapacity) || 0) >= n));
    host.innerHTML = `
      <label class="filters__search">${icon('search', 16)}
        <input type="search" id="f-q" placeholder="Search model or branch" value="${esc(f.q)}" aria-label="Search by model or branch" autocomplete="off">
      </label>
      ${fuels.length > 1 ? `<div class="seg seg--sm" role="radiogroup" aria-label="Powertrain">${['All', ...fuels].map((x) => `
        <button type="button" role="radio" data-fuel="${esc(x)}" class="${f.fuel === x ? 'is-active' : ''}" aria-checked="${f.fuel === x}">${x === 'All' ? 'Any power' : esc(x)}</button>`).join('')}</div>` : ''}
      ${seats.length > 1 ? `<div class="seg seg--sm" role="radiogroup" aria-label="Seats">${seats.map((n) => `
        <button type="button" role="radio" data-seats="${n}" class="${f.seats === n ? 'is-active' : ''}" aria-checked="${f.seats === n}">${n ? `${n}+ seats` : 'Any seats'}</button>`).join('')}</div>` : ''}
      ${f.hi > f.lo ? `<label class="filters__range">
        <span>Up to <b id="f-max-out">${money(f.max)}</b></span>
        <input type="range" id="f-max" min="${f.lo}" max="${f.hi}" step="500" value="${f.max}" aria-label="Highest daily rate" style="--p:${(((f.max - f.lo) / (f.hi - f.lo)) * 100).toFixed(1)}%">
      </label>` : ''}
      <button class="btn btn--sm btn--text" type="button" id="f-reset" data-f-reset hidden>${icon('close', 14)} Reset</button>`;
  }

  // ---------- recently viewed ----------
  async function paintRecent() {
    const host = $('#recent-strip', el);
    const ids = recent.list().slice(0, 6);
    if (!ids.length) { host.innerHTML = ''; return; }
    const cars = (await Promise.all(ids.map((id) => getVehicle(id).catch(() => null)))).filter(Boolean);
    if (!cars.length || !host.isConnected) return;
    const qs = new URLSearchParams({ pickup: state.pickup, return: state.ret });
    const free = new Set(state.list.map((v) => v.vehicleId));
    host.innerHTML = `<div class="recent-strip">
      <div class="recent-strip__head"><p class="eyebrow">${icon('history', 14)} Recently viewed</p>
        <button class="btn btn--sm btn--text" type="button" data-recent-clear>Clear</button></div>
      <div class="recent-strip__row">${cars.map((v) => `
        <a class="recent-car" href="#/vehicle/${v.vehicleId}?${qs}">
          <span class="recent-car__art">${vehicleVisual(v)}</span>
          <span class="recent-car__text"><b>${esc(v.model)}</b><small>${money(v.rentalPricePerDay)} / day</small>
            <em class="${free.has(v.vehicleId) ? 'is-free' : ''}">${free.has(v.vehicleId) ? 'Free for your dates' : 'Not free these dates'}</em></span>
        </a>`).join('')}</div>
    </div>`;
  }

  function orderList(list) {
    const price = (v) => Number(v.rentalPricePerDay) || 0;
    const out = [...list];
    if (state.sort === 'price-asc') return out.sort((a, b) => price(a) - price(b));
    if (state.sort === 'price-desc') return out.sort((a, b) => price(b) - price(a));
    if (state.sort === 'newest') return out.sort((a, b) => (b.manufactureYear || 0) - (a.manufactureYear || 0));
    const top = out.reduce((best, v) => (price(v) > price(best) ? v : best), out[0]);
    return top ? [top, ...out.filter((v) => v !== top)] : out;
  }
}

/**
 * The collection card. It is an article with one stretched link (the name),
 * so the save and compare buttons can sit on it as real buttons.
 * `avail`: 'free' (default), 'booked', or 'unknown' when there are no dates.
 */
export function vehicleCard(v, branch, nights, qs, index, total, feature = false, { avail = 'free' } = {}) {
  const n = String(index + 1).padStart(2, '0');
  const rate = Number(v.rentalPricePerDay);
  const live = avail === 'free' ? '<span class="vcard__live"><i aria-hidden="true"></i>Free</span>'
    : avail === 'booked' ? '<span class="vcard__live vcard__live--off"><i aria-hidden="true"></i>Booked</span>' : '';
  return `<article class="vcard${feature ? ' vcard--feature' : ''}${avail === 'booked' ? ' is-booked' : ''}"
      style="animation-delay:${Math.min(index, 12) * 55}ms">
    <div class="vcard__stage">
      <div class="vcard__badges">
        <span class="vcard__cap">${esc(v.category || 'Vehicle')}${v.manufactureYear ? ` \u00b7 ${esc(v.manufactureYear)}` : ''}</span>
        ${live}
      </div>
      <div class="vcard__visual">${vehicleVisual(v)}</div>
      <span class="vcard__glint" aria-hidden="true"></span>
      <span class="vcard__index" aria-hidden="true">${n} / ${String(total).padStart(2, '0')}</span>
    </div>
    <div class="vcard__body">
      <div class="vcard__head">
        ${feature ? '<span class="vcard__flag">Flagship of the collection</span>' : ''}
        <small>${icon('pin', 13)} ${esc(branch?.name || 'Branch')}${branch?.city && feature ? ` \u00b7 ${esc(branch.city)}` : ''}</small>
        <h3><a class="vcard__link" href="#/vehicle/${v.vehicleId}${qs ? `?${qs}` : ''}">${esc(v.model)}</a></h3>
        ${feature ? `<p class="vcard__blurb">The highest-specified car free for your dates, collected from ${esc(branch?.name || 'its branch')} and handed over by the team that keeps it.</p>` : ''}
      </div>
      <dl class="vcard__spec">
        <div><dt>${icon('seat', 12)} Seats</dt><dd>${esc(v.passengerCapacity ?? '\u2014')}</dd></div>
        <div><dt>${icon('fuel', 12)} Power</dt><dd>${esc(v.fuelType || '\u2014')}</dd></div>
        <div><dt>${icon('gauge', 12)} Odo</dt><dd>${number(v.mileage)}<small>km</small></dd></div>
        ${feature ? `<div><dt>${icon('calendar', 12)} Year</dt><dd>${esc(v.manufactureYear || '\u2014')}</dd></div>` : ''}
      </dl>
      <div class="vcard__foot">
        <div class="vcard__price">
          <b>${money(rate)}</b><span>/ day</span>
          ${nights ? `<small>${money(rate * nights)} for ${nights} night${nights === 1 ? '' : 's'}</small>` : ''}
        </div>
        <span class="vcard__cta" aria-hidden="true">${avail === 'booked' ? 'View' : 'Reserve'} <i>${icon('arrowRight', 15)}</i></span>
      </div>
    </div>
    ${carActions(v)}
  </article>`;
}

/** The marquee rail card: ink, with the car on a lit plinth. */
function pickCard(v, branch, index) {
  return `<a class="pcard" href="#/vehicle/${v.vehicleId}">
    <div class="pcard__media">
      <div class="pcard__art">${vehicleVisual(v)}</div>
      <div class="pcard__mirror" aria-hidden="true">${vehicleVisual(v)}</div>
    </div>
    ${PBLUR}
    <span class="pcard__tint" aria-hidden="true"></span>
    <span class="pcard__rank">N\u00ba ${String(index + 1).padStart(2, '0')}</span>
    <span class="pcard__go" aria-hidden="true">${icon('arrowUpRight', 16)}</span>
    <div class="pcard__body">
      <div style="min-width:0">
        <h3>${esc(v.model)}</h3>
        <small>${esc(branch?.name || v.category || 'Collection')}</small>
      </div>
      <div class="pcard__rate"><b>${money(v.rentalPricePerDay)}</b><span>per day</span></div>
    </div>
  </a>`;
}

function branchCard(b) {
  return `<article class="branch-card">
    <span class="branch-card__glyph" aria-hidden="true">${esc((b.name || '?').charAt(0))}</span>
    <div>${statusChip(b.status)}</div>
    <h3>${esc(b.name)}</h3>
    <div class="branch-lines">
      <div>${icon('pin', 17)} <span>${esc([b.street, b.city].filter(Boolean).join(', ') || 'Address not listed')}</span></div>
      <div>${icon('clock', 17)} <span>${b.openTime ? `${fmtTime(b.openTime)} – ${fmtTime(b.closeTime)}` : 'Hours not listed'}</span></div>
    </div>
    <footer>
      <a class="btn btn--sm" href="#/explore?branch=${b.branchId}">Browse vehicles ${icon('arrowRight', 15)}</a>
    </footer>
  </article>`;
}

// ==================================================================
// Vehicle detail + reserve
// ==================================================================
export async function renderVehicle({ el, params, query }) {
  el.innerHTML = `<div class="skeleton" style="min-height:70vh;margin-top:26px"></div>`;
  const id = Number(params.id);
  const [v, branches] = await Promise.all([api.vehicles.get(id), getBranches().catch(() => [])]);
  const branch = branches.find((b) => b.branchId === v.branchId);
  recent.push(v.vehicleId);

  let pickup = validDate(query.pickup) || addDays(1);
  if (pickup < today()) pickup = today();
  let ret = validDate(query.return) || addDays(3, new Date(`${pickup}T00:00:00`));
  if (ret <= pickup) ret = addDays(1, new Date(`${pickup}T00:00:00`));
  // The vehicle is collected from the branch that holds it — the backend
  // refuses any other branch, so this is shown, not chosen.
  const bookable = !OFF_ROAD.includes(v.status);
  const outNow = v.status === 'RENTED' || v.status === 'RESERVED';
  const backQs = new URLSearchParams({ pickup, return: ret });
  if (query.branch) backQs.set('branch', query.branch);

  const ctaLabel = !store.user ? 'Sign in to reserve' : isCustomer() ? 'Request reservation' : 'Customer accounts only';

  el.innerHTML = `
    <article class="detail">
      <div class="detail__main">
        <a class="back-link" href="#/explore?${backQs}">${icon('arrowLeft', 16)} Back to results</a>
        <div class="detail__head">
          <div>
            <p class="eyebrow rule">${esc(v.category || 'Vehicle')} · ${esc(v.plateNumber)}</p>
            <h1 class="display display--model" style="margin-top:12px">${esc(v.model)}</h1>
            <div class="detail__meta">
              ${v.manufactureYear ? `<span>${esc(v.manufactureYear)}</span>` : ''}
              ${v.fuelType ? `<span>${esc(v.fuelType)}</span>` : ''}
              ${v.passengerCapacity ? `<span>${esc(v.passengerCapacity)} seats</span>` : ''}
              ${statusChip(v.status)}
            </div>
            <div class="detail__acts">
              ${carActions(v, { labels: true })}
              <button class="car-act car-act--share" type="button" data-share>${icon('share', 16)}<span>Share</span></button>
            </div>
          </div>
          <div class="detail__price">
            <b>${money(v.rentalPricePerDay)}</b>
            <span>Per day</span>
          </div>
        </div>
        <div class="detail__stage" id="stage">${vehicleVisual(v, { sizes: '(max-width: 1024px) 100vw, 780px' })}</div>
        <div class="gallery" id="gallery" hidden></div>
        <div class="spec-strip">
          ${spec('seat', 'Passengers', v.passengerCapacity ?? '—', v.passengerCapacity ? 'seats' : '')}
          ${spec('fuel', 'Powertrain', v.fuelType || '—')}
          ${spec('gauge', 'Odometer', number(v.mileage), 'km')}
          ${spec('calendar', 'Model year', v.manufactureYear ?? '—', v.manufactureYear ? `${v.vehicleAge} yr old` : '')}
        </div>
        <section class="avail" id="avail" aria-label="Availability">
          <div class="avail__head">
            <div><p class="eyebrow">Availability</p><h2 class="title-md">When it's free</h2></div>
            <div class="avail__legend" aria-hidden="true">
              <span><i class="is-free"></i>Free</span><span><i class="is-busy"></i>Taken</span><span><i class="is-pick"></i>Your dates</span>
            </div>
          </div>
          <div class="avail__months" id="avail-months"><div class="skeleton" style="height:260px"></div></div>
          <p class="avail__note muted small">Tap a free day to start your trip there. Days in grey are booked, in the workshop, or otherwise unavailable.</p>
        </section>
      </div>

      <aside class="detail__panel">
        <div>
          <div class="panel-title"><span>Details</span></div>
          <div class="kv-grid" style="margin-top:18px">
            <div class="kv"><span>Plate</span><b>${esc(v.plateNumber)}</b></div>
            <div class="kv"><span>Category</span><b>${esc(v.category || '—')}</b></div>
            <div class="kv"><span>Status</span><b>${esc(humanize(v.status))}</b></div>
            <div class="kv"><span>Home branch</span><b>${esc(branch?.name || '—')}</b></div>
            <div class="kv"><span>City</span><b>${esc(branch?.city || '—')}</b></div>
            <div class="kv"><span>Hours</span><b>${branch ? `${fmtTime(branch.openTime)}–${fmtTime(branch.closeTime)}` : '—'}</b></div>
          </div>
        </div>

        <form class="reserve" id="reserve" novalidate>
          <div class="panel-title"><span>Reserve</span><span id="availability"></span></div>
          ${bookable ? '' : `<div class="notice notice--amber">${icon('info', 18)}<span>This vehicle is ${esc(humanize(v.status).toLowerCase())} and can't be reserved until it is back in service.</span></div>`}
          ${bookable && outNow ? `<div class="notice">${icon('info', 18)}<span>This vehicle is out with a customer at the moment. You can still reserve it for any dates it is free — pick your dates and we'll check.</span></div>` : ''}
          <div class="notice">${icon('pin', 18)}<span>Collected from and returned to <b style="color:var(--ink)">${esc(branch?.name || `branch #${v.branchId}`)}</b>${branch?.city ? `, ${esc(branch.city)}` : ''}.</span></div>
          <div class="form-grid">
            ${field({ name: 'pickupDate', label: 'Pick-up', type: 'date', value: pickup, min: today(), max: addDays(MAX_ADVANCE_DAYS), required: true })}
            ${field({ name: 'returnDate', label: 'Return', type: 'date', value: ret, min: addDays(1, new Date(`${pickup}T00:00:00`)), max: addDays(MAX_RENTAL_DAYS, new Date(`${pickup}T00:00:00`)), required: true })}
            ${field({ name: 'specialRequests', label: 'Special requests (optional)', type: 'textarea', span: true })}
          </div>
          <div class="quote" id="quote"></div>
          <div id="reserve-error"></div>
          <button class="btn btn--lg btn--block" type="submit" ${!bookable || isStaff() ? 'disabled' : ''}>
            ${ctaLabel} ${icon('arrowRight', 18)}
          </button>
        </form>

        <div class="accordion">
          <details open>
            <summary>How it works <span class="icon-btn icon-btn--ghost">${icon('chevronDown', 16)}</span></summary>
            <div class="acc-body steps-mini">
              <div><i>1</i><span>Send your request — the estimate is locked to the dates you chose.</span></div>
              <div><i>2</i><span>Our branch team reviews it and you get a notification.</span></div>
              <div><i>3</i><span>Collect the vehicle at your pick-up branch and settle payment.</span></div>
            </div>
          </details>
          <details>
            <summary>Changes &amp; cancellation <span class="icon-btn icon-btn--ghost">${icon('chevronDown', 16)}</span></summary>
            <div class="acc-body">While a request is awaiting approval you can change its dates from <b>My trips</b>. You can cancel any time before pick-up.</div>
          </details>
        </div>
      </aside>
    </article>
    <section class="section" id="similar" hidden>
      <div class="section-head">
        <div>
          <p class="eyebrow rule">You might also like</p>
          <h2 class="title-lg" style="margin-top:16px">Similar cars, free on your dates</h2>
        </div>
      </div>
      <div id="similar-grid"></div>
    </section>
    <div class="mbar" id="mbar">
      <div class="mbar__price"><b>${money(v.rentalPricePerDay)}</b><span>per day</span><small id="mbar-total"></small></div>
      <button class="btn" type="button" id="mbar-go" ${!bookable || isStaff() ? 'disabled' : ''}>${bookable ? 'Reserve' : 'Unavailable'} ${icon('arrowRight', 16)}</button>
    </div>
    ${footer(branch)}`;

  const form = $('#reserve', el);
  const quote = $('#quote', el);
  const availability = $('#availability', el);
  const pickupInput = form.elements.pickupDate;
  const returnInput = form.elements.returnDate;

  function drawQuote() {
    const p = pickupInput.value;
    const r = returnInput.value;
    if (!p || !r || r <= p) {
      quote.innerHTML = '<div class="quote__row"><span>Choose a return date after pick-up</span></div>';
      return;
    }
    const n = rentalDays(p, r);
    const mt = $('#mbar-total', el);
    if (mt) mt.textContent = `${money(Number(v.rentalPricePerDay) * n)} for ${n} night${n === 1 ? '' : 's'}`;
    quote.innerHTML = `
      <div class="quote__row"><span>${money(v.rentalPricePerDay)} × ${n} night${n === 1 ? '' : 's'}</span><span>${money(Number(v.rentalPricePerDay) * n)}</span></div>
      <div class="quote__row"><span>${fmtDate(p)} → ${fmtDate(r)}</span></div>
      <div class="quote__row quote__total"><span>Estimated total</span><b>${money(Number(v.rentalPricePerDay) * n)}</b></div>`;
  }

  const checkAvailability = debounce(async () => {
    const p = pickupInput.value;
    const r = returnInput.value;
    if (!bookable || !p || !r || r <= p || p < today()
        || rentalDays(p, r) > MAX_RENTAL_DAYS || p > addDays(MAX_ADVANCE_DAYS)) {
      availability.innerHTML = '';
      return;
    }
    availability.innerHTML = '<span class="status">Checking…</span>';
    try {
      const free = await api.vehicles.search(p, r);
      const ok = free.some((x) => x.vehicleId === v.vehicleId);
      availability.innerHTML = ok
        ? '<span class="status status--green">Free on these dates</span>'
        : '<span class="status status--red">Booked on these dates</span>';
      if (!ok) {
        const n = rentalDays(p, r);
        const next = await api.vehicles.nextFree(v.vehicleId, n, p).catch(() => null);
        if (next?.nextFree && availability.isConnected) {
          availability.innerHTML += ` <button type="button" class="link-btn" data-jump="${next.nextFree}">Free from ${fmtDate(next.nextFree)} ${icon('arrowRight', 13)}</button>`;
        }
      }
      paintCalendar();
    } catch {
      availability.innerHTML = '';
    }
  }, 250);

  pickupInput.addEventListener('change', () => {
    if (pickupInput.value) {
      const from = new Date(`${pickupInput.value}T00:00:00`);
      const minReturn = addDays(1, from);
      const maxReturn = addDays(MAX_RENTAL_DAYS, from);
      returnInput.min = minReturn;
      returnInput.max = maxReturn;
      if (!returnInput.value || returnInput.value < minReturn) returnInput.value = minReturn;
      if (returnInput.value > maxReturn) returnInput.value = maxReturn;
    }
    drawQuote();
    checkAvailability();
  });
  returnInput.addEventListener('change', () => { drawQuote(); checkAvailability(); loadSimilar(); paintCalendar(); });
  pickupInput.addEventListener('change', () => loadSimilar());
  drawQuote();
  checkAvailability();

  // Share: the system sheet where there is one, the clipboard otherwise.
  $('[data-share]', el).addEventListener('click', async () => {
    const url = `${location.origin}${location.pathname}#/vehicle/${v.vehicleId}`;
    try {
      if (navigator.share) await navigator.share({ title: `${v.model} \u2014 Axle`, text: `${v.model}, ${money(v.rentalPricePerDay)} a day`, url });
      else { await navigator.clipboard.writeText(url); toast('Link copied', 'success'); }
    } catch (err) {
      if (err?.name !== 'AbortError') toast(url);
    }
  });
  // On a phone the form is a long way down: the bar takes you there.
  $('#mbar-go', el).addEventListener('click', () => {
    form.scrollIntoView({ behavior: 'smooth', block: 'center' });
    setTimeout(() => pickupInput.focus({ preventScroll: true }), 500);
  });
  if ('IntersectionObserver' in window) {
    const bar = $('#mbar', el);
    new IntersectionObserver(([en]) => bar.classList.toggle('is-hidden', en.isIntersecting), { threshold: 0.2 }).observe(form);
  }

  // Similar cars: same body first, then closest in price, free on these dates.
  const similar = debounce(async () => {
    const p = pickupInput.value;
    const r = returnInput.value;
    const host = $('#similar', el);
    if (!p || !r || r <= p || p < today()) return;
    const free = await api.vehicles.search(p, r).catch(() => []);
    if (!host.isConnected) return;
    const rate = Number(v.rentalPricePerDay);
    const picks = free.filter((x) => x.vehicleId !== v.vehicleId)
      .map((x) => ({ x, score: (x.category === v.category ? 0 : 1) + Math.abs(Number(x.rentalPricePerDay) - rate) / Math.max(rate, 1) }))
      .sort((a, b) => a.score - b.score).slice(0, 3).map((s) => s.x);
    host.hidden = !picks.length;
    const qs = new URLSearchParams({ pickup: p, return: r });
    const n = rentalDays(p, r);
    $('#similar-grid', el).innerHTML = `<div class="vehicle-grid">${picks.map((x, i) =>
      vehicleCard(x, branches.find((b) => b.branchId === x.branchId), n, qs, i, picks.length)).join('')}</div>`;
  }, 300);
  function loadSimilar() { similar(); }
  loadSimilar();

  // ---------- availability calendar (E1) ----------
  // Two months from today. Busy days come from the server (bookings, workshop,
  // transfers, uninsured days) without any customer details.
  let busyDays = new Map();
  async function loadBusy() {
    try {
      const data = await api.vehicles.busy(v.vehicleId, { from: today(), to: addDays(62) });
      busyDays = new Map();
      (data.busy || []).forEach((r) => {
        for (let d = r.from; d <= r.to; d = addDays(1, new Date(`${d}T00:00:00`))) busyDays.set(d, r.kind);
      });
    } catch { busyDays = new Map(); }
    paintCalendar();
  }
  function paintCalendar() {
    const host = $('#avail-months', el);
    if (!host) return;
    const p = pickupInput.value;
    const r = returnInput.value;
    const start = new Date(`${today()}T00:00:00`);
    const months = [0, 1].map((k) => new Date(start.getFullYear(), start.getMonth() + k, 1));
    const kindLabel = { BOOKED: 'booked', WORKSHOP: 'in the workshop', MOVING: 'moving branch', UNINSURED: 'not insured yet', OFF_ROAD: 'off the road' };
    host.innerHTML = months.map((m) => {
      const first = (m.getDay() + 6) % 7;          // Monday first
      const days = new Date(m.getFullYear(), m.getMonth() + 1, 0).getDate();
      const cells = [];
      for (let i = 0; i < first; i++) cells.push('<span></span>');
      for (let d = 1; d <= days; d++) {
        const iso = isoDate(new Date(m.getFullYear(), m.getMonth(), d));
        const past = iso < today();
        const busy = busyDays.get(iso);
        const picked = p && r && iso >= p && iso <= r;
        const cls = past ? 'is-past' : busy ? 'is-busy' : 'is-free';
        cells.push(`<button type="button" class="cal__day ${cls}${picked ? ' is-pick' : ''}${iso === p ? ' is-start' : ''}${iso === r ? ' is-end' : ''}"
          ${past || busy ? 'disabled' : `data-day="${iso}"`} aria-label="${fmtDate(iso)}${busy ? `, ${kindLabel[busy] || 'unavailable'}` : past ? '' : ', free'}">${d}</button>`);
      }
      return `<div class="cal">
        <p class="cal__title">${m.toLocaleDateString('en-GB', { month: 'long', year: 'numeric' })}</p>
        <div class="cal__dow" aria-hidden="true"><span>M</span><span>T</span><span>W</span><span>T</span><span>F</span><span>S</span><span>S</span></div>
        <div class="cal__grid">${cells.join('')}</div>
      </div>`;
    }).join('');
  }
  function jumpTo(day) {
    const nights = Math.max(1, rentalDays(pickupInput.value, returnInput.value) || 1);
    pickupInput.value = day;
    pickupInput.dispatchEvent(new Event('change', { bubbles: true }));
    returnInput.value = addDays(nights, new Date(`${day}T00:00:00`));
    returnInput.dispatchEvent(new Event('change', { bubbles: true }));
    form.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }
  el.addEventListener('click', (e) => {
    const day = e.target.closest('[data-day]');
    if (day) { jumpTo(day.dataset.day); return; }
    const jump = e.target.closest('[data-jump]');
    if (jump) jumpTo(jump.dataset.jump);
  });
  loadBusy();

  // ---------- more photos (E5) ----------
  api.vehicles.gallery(v.vehicleId).then((photos) => {
    const host = $('#gallery', el);
    if (!host || !photos?.length) return;
    const cover = v.imageUrl && /^\/api\//.test(v.imageUrl) ? [{ url: v.imageUrl, cover: true }] : [];
    const all = [...cover, ...photos];
    if (all.length < 2 && !cover.length) all.unshift({ drawn: true });
    host.hidden = false;
    host.innerHTML = all.map((ph, i) => `<button type="button" class="gallery__thumb${i === 0 ? ' is-on' : ''}" data-photo="${i}" aria-label="Photo ${i + 1} of ${all.length}">
        ${ph.drawn ? carArt(v) : `<img src="${esc(ph.url)}${ph.url.includes('?') ? '&' : '?'}w=320" alt="" loading="lazy">`}
      </button>`).join('');
    host.addEventListener('click', (e) => {
      const b = e.target.closest('[data-photo]');
      if (!b) return;
      const ph = all[Number(b.dataset.photo)];
      $$('.gallery__thumb', host).forEach((x) => x.classList.toggle('is-on', x === b));
      $('#stage', el).innerHTML = ph.drawn ? vehicleVisual({ ...v, imageUrl: null })
        : `<img class="vehicle-img" src="${esc(ph.url)}${ph.url.includes('?') ? '&' : '?'}w=1280" alt="${esc(v.model)}">`;
    });
  }).catch(() => {});

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const errorBox = $('#reserve-error', el);
    errorBox.innerHTML = '';
    if (!store.user) {
      const back = `/vehicle/${v.vehicleId}?pickup=${pickupInput.value}&return=${returnInput.value}`;
      navigate(`/login?next=${encodeURIComponent(back)}`);
      return;
    }
    if (!isCustomer()) return;
    if (!validateForm(form)) return;
    const values = readForm(form);
    if (values.returnDate <= values.pickupDate) {
      errorBox.innerHTML = formError('Return date must be after the pick-up date.');
      return;
    }
    if (rentalDays(values.pickupDate, values.returnDate) > MAX_RENTAL_DAYS) {
      errorBox.innerHTML = formError(`A single booking cannot be longer than ${MAX_RENTAL_DAYS} days.`);
      return;
    }
    if (values.pickupDate > addDays(MAX_ADVANCE_DAYS)) {
      errorBox.innerHTML = formError(`Bookings can only be made up to ${MAX_ADVANCE_DAYS} days in advance.`);
      return;
    }
    const btn = form.querySelector('[type=submit]');
    btn.disabled = true;
    btn.classList.add('is-busy');
    try {
      const booking = await api.bookings.create({
        vehicleId: v.vehicleId,
        pickupBranchId: v.branchId,
        pickupDate: values.pickupDate,
        returnDate: values.returnDate,
        specialRequests: values.specialRequests,
      });
      toast(`Request #${booking.bookingId} sent — we'll notify you once it's reviewed`, 'success');
      navigate('/trips');
    } catch (err) {
      errorBox.innerHTML = formError(errorSummary(err, showFieldErrors(form, err)));
      checkAvailability();
    } finally {
      if (btn.isConnected) { btn.disabled = false; btn.classList.remove('is-busy'); }
    }
  });
}

function spec(iconName, label, value, unit = '', sub = '') {
  return `<div class="spec">
    <span class="spec__label">${icon(iconName, 16)} ${esc(label)}</span>
    <span class="spec__value">${esc(value)}${unit ? `<small>${esc(unit)}</small>` : ''}</span>
    ${sub ? `<span class="spec__sub">${esc(sub)}</span>` : ''}
  </div>`;
}

// ==================================================================
// Branch directory
// ==================================================================
// Where each town sits, so a branch can be placed on the island. The branch
// table holds an address, not coordinates; a branch in a town missing from
// here is still listed, it just has no pin.
const TOWNS = {
  colombo: [6.93, 79.86], negombo: [7.21, 79.84], negambo: [7.21, 79.84], kandy: [7.29, 80.63],
  galle: [6.05, 80.22], jaffna: [9.66, 80.02], trincomalee: [8.57, 81.23], batticaloa: [7.71, 81.69],
  anuradhapura: [8.31, 80.4], kurunegala: [7.49, 80.36], matara: [5.95, 80.55], 'nuwara eliya': [6.95, 80.78],
  ratnapura: [6.68, 80.4], badulla: [6.99, 81.06], ella: [6.87, 81.05], hambantota: [6.12, 81.12],
  dambulla: [7.86, 80.65], sigiriya: [7.95, 80.76], kalutara: [6.58, 79.96], bentota: [6.42, 80.0],
  mirissa: [5.95, 80.46], polonnaruwa: [7.94, 81.0], chilaw: [7.58, 79.8], puttalam: [8.03, 79.83],
  mannar: [8.98, 79.9], vavuniya: [8.75, 80.5], ampara: [7.3, 81.67], kegalle: [7.25, 80.35],
  gampaha: [7.09, 79.99], moratuwa: [6.77, 79.88], dehiwala: [6.85, 79.87], 'mount lavinia': [6.83, 79.86],
  'arugam bay': [6.84, 81.83], katunayake: [7.17, 79.88], wattala: [6.99, 79.89], panadura: [6.71, 79.9],
};

// The coastline, clockwise from Point Pedro, as [lat, lon]. Coarse on
// purpose: it is only ever drawn as a field of dots.
const COAST = [
  [9.83, 80.24], [9.55, 80.55], [9.27, 80.82], [8.98, 81.0], [8.72, 81.13], [8.57, 81.24], [8.38, 81.32],
  [8.15, 81.45], [7.95, 81.55], [7.72, 81.7], [7.45, 81.83], [7.15, 81.87], [6.84, 81.84], [6.55, 81.72],
  [6.35, 81.5], [6.2, 81.25], [6.12, 81.12], [6.02, 80.82], [5.93, 80.6], [5.97, 80.43], [6.03, 80.22],
  [6.18, 80.08], [6.42, 79.99], [6.58, 79.96], [6.8, 79.87], [6.95, 79.84], [7.21, 79.82], [7.45, 79.8],
  [7.6, 79.79], [7.9, 79.8], [8.23, 79.72], [8.4, 79.82], [8.6, 79.9], [8.85, 79.92], [9.0, 79.88],
  [9.05, 79.98], [9.3, 80.08], [9.5, 80.1], [9.62, 79.98], [9.7, 79.93], [9.8, 80.0], [9.83, 80.12],
];
const MAP = { lon0: 78.35, lat0: 9.98, k: 100, w: 380, h: 420 };
const project = ([lat, lon]) => [(lon - MAP.lon0) * MAP.k, (MAP.lat0 - lat) * MAP.k];

function insideCoast(x, y, poly) {
  let inside = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const [xi, yi] = poly[i]; const [xj, yj] = poly[j];
    if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}

const TOWN_DOTS = (() => {
  const poly = COAST.map(project);
  let dots = '';
  for (let y = 4; y < MAP.h; y += 7) {
    for (let x = 4 + ((y / 7) % 2) * 3.5; x < MAP.w; x += 7) {
      if (insideCoast(x, y, poly)) dots += `<circle cx="${x.toFixed(1)}" cy="${y}" r="1.35"/>`;
    }
  }
  return { dots, outline: `M${poly.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join('L')}Z` };
})();

function townOf(b) {
  const keys = [b.city, b.district, ...(b.name || '').split(/\s+/)].map((s) => String(s || '').trim().toLowerCase());
  for (const k of keys) if (TOWNS[k]) return TOWNS[k];
  return null;
}

const NUMBER_WORDS = ['No', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten', 'Eleven', 'Twelve'];

// Branch hours are Sri Lanka time, wherever the visitor happens to be.
export function colomboMinutes() {
  const parts = new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Colombo', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
    .formatToParts(new Date());
  const get = (t) => Number(parts.find((p) => p.type === t)?.value || 0);
  return get('hour') * 60 + get('minute');
}
const toMinutes = (t) => { const [h, m] = String(t || '').split(':').map(Number); return Number.isFinite(h) ? h * 60 + (m || 0) : null; };
const span = (mins) => (mins >= 60 ? `${Math.floor(mins / 60)} h ${String(mins % 60).padStart(2, '0')} min` : `${mins} min`);

/** Open right now? And what the visitor should be told about it. */
export function branchClock(b, now = colomboMinutes()) {
  const open = toMinutes(b.openTime); const close = toMinutes(b.closeTime);
  if (b.status !== 'ACTIVE') return { open: false, known: true, line: 'Temporarily closed', short: 'Closed' };
  if (open === null || close === null) return { open: false, known: false, line: 'Hours not listed', short: 'Hours not listed' };
  const isOpen = close > open ? now >= open && now < close : now >= open || now < close;
  if (isOpen) {
    const left = (close - now + 1440) % 1440;
    return { open: true, known: true, line: `Open now · closes ${fmtTime(b.closeTime)}, in ${span(left)}`, short: `Open · till ${fmtTime(b.closeTime)}` };
  }
  const until = (open - now + 1440) % 1440;
  const when = now < open ? `today at ${fmtTime(b.openTime)}` : `tomorrow at ${fmtTime(b.openTime)}`;
  return { open: false, known: true, line: `Closed · opens ${when}, in ${span(until)}`, short: `Opens ${fmtTime(b.openTime)}` };
}

function branchMap(branches) {
  const placed = branches.map((b) => ({ b, at: townOf(b) })).filter((p) => p.at)
    .map((p) => { const [x, y] = project(p.at); return { ...p, x, y, side: p.at[1] < 80.4 ? 'left' : 'right' }; });

  // Labels on each side are pushed apart so neighbours never overlap; a
  // hairline leads from the pin to wherever its label ended up.
  const GAP = 52;
  for (const side of ['left', 'right']) {
    let last = -Infinity;
    placed.filter((p) => p.side === side).sort((a, c) => a.y - c.y).forEach((p) => {
      p.ly = Math.max(p.y, last + GAP);
      last = p.ly;
    });
  }
  placed.forEach((p) => { p.lx = p.side === 'left' ? Math.min(p.x - 22, 118) : Math.max(p.x + 22, 262); });

  const hub = placed.find((p) => /main/i.test(p.b.name)) || placed[0];
  const routes = hub ? placed.filter((p) => p !== hub).map((p) => {
    const mx = (hub.x + p.x) / 2; const my = (hub.y + p.y) / 2;
    const dx = p.x - hub.x; const dy = p.y - hub.y;
    return `<path d="M${hub.x.toFixed(1)},${hub.y.toFixed(1)} Q${(mx - dy * 0.28).toFixed(1)},${(my + dx * 0.28).toFixed(1)} ${p.x.toFixed(1)},${p.y.toFixed(1)}"/>`;
  }).join('') : '';

  const pct = (v, of) => `${((v / of) * 100).toFixed(2)}%`;
  return `<div class="br-map" aria-hidden="true">
    <svg viewBox="0 0 ${MAP.w} ${MAP.h}" preserveAspectRatio="xMidYMid meet">
      <defs><radialGradient id="brGlow"><stop offset="0" stop-color="#fff" stop-opacity=".16"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient></defs>
      <ellipse cx="258" cy="250" rx="150" ry="190" fill="url(#brGlow)"/>
      <path class="br-map__coast" d="${TOWN_DOTS.outline}"/>
      <g class="br-map__dots">${TOWN_DOTS.dots}</g>
      <g class="br-map__routes">${routes}</g>
      <g class="br-map__leads">${placed.map((p) => `<path data-lead="${p.b.branchId}" d="M${p.x.toFixed(1)},${p.y.toFixed(1)} L${(p.side === 'left' ? p.lx + 4 : p.lx - 4).toFixed(1)},${p.ly.toFixed(1)}"/>`).join('')}</g>
    </svg>
    ${placed.map((p) => `
      <button class="br-pin" type="button" data-pin="${p.b.branchId}" style="left:${pct(p.x, MAP.w)}; top:${pct(p.y, MAP.h)}" tabindex="-1"><i></i></button>
      <span class="br-label br-label--${p.side}" data-label="${p.b.branchId}" style="left:${pct(p.lx, MAP.w)}; top:${pct(p.ly, MAP.h)}">
        <b>${esc(p.b.name)}</b><small data-short></small>
      </span>`).join('')}
  </div>`;
}

function branchPanel(b, i, free) {
  const on = b.status === 'ACTIVE';
  const open = toMinutes(b.openTime); const close = toMinutes(b.closeTime);
  const place = [b.city, b.district].filter(Boolean).filter((v, k, a) => a.findIndex((x) => x.toLowerCase() === v.toLowerCase()) === k);
  const tel = String(b.contactNumber || '').replace(/[^\d+]/g, '');
  return `<article class="br-card${on ? '' : ' is-off'}" data-branch="${b.branchId}" data-reveal style="--d:${i % 2}">
    <header class="br-card__head">
      <span class="br-card__n">${String(i + 1).padStart(2, '0')}</span>
      <div class="br-card__title">
        <p class="eyebrow">${esc(place.join(' · ') || 'Branch')}</p>
        <h3>${esc(b.name)}</h3>
      </div>
      <span class="br-state" data-state><i></i><span></span></span>
    </header>

    ${open !== null && close !== null && on ? `
    <div class="br-hours" style="--from:${(open / 1440).toFixed(4)}; --to:${(close / 1440).toFixed(4)}">
      <div class="br-hours__track"><i class="br-hours__open"></i><i class="br-hours__now" data-now></i></div>
      <div class="br-hours__scale" aria-hidden="true"><span>00</span><span>06</span><span>12</span><span>18</span><span>24</span></div>
      <p class="br-hours__line" data-line></p>
    </div>` : `<p class="br-hours__line br-hours__line--solo" data-line></p>`}

    <dl class="br-card__facts">
      <div><dt>${icon('pin', 16)} Address</dt><dd>${esc([b.street, b.city].filter(Boolean).join(', ') || 'Address not listed')}</dd></div>
      <div><dt>${icon('clock', 16)} Hours</dt><dd>${b.openTime ? `${fmtTime(b.openTime)} – ${fmtTime(b.closeTime)} <small>Sri Lanka time</small>` : 'Not listed'}</dd></div>
      <div><dt>${icon('phone', 16)} Phone</dt><dd>${tel ? `<a href="tel:${esc(tel)}">${esc(b.contactNumber)}</a>` : '—'}</dd></div>
    </dl>

    <footer class="br-card__foot">
      ${on ? `<div class="br-free">${free === null ? '<span>Live availability unavailable</span>'
        : `<b>${free}</b><span>car${free === 1 ? '' : 's'} free tomorrow</span>`}</div>` : '<div class="br-free"><span>Not taking reservations</span></div>'}
      <div class="br-card__cta">
        ${tel && on ? `<a class="btn btn--sm btn--tonal" href="tel:${esc(tel)}">${icon('phone', 15)} Call</a>` : ''}
        ${on ? `<a class="btn btn--sm" href="#/explore?branch=${b.branchId}">Browse vehicles ${icon('arrowRight', 15)}</a>` : ''}
      </div>
    </footer>
  </article>`;
}

export async function renderBranches({ el }) {
  el.innerHTML = `
    <section class="br-hero" data-under-nav="dark">
      <span class="lp-scene__lattice" aria-hidden="true"></span>
      <div class="br-hero__grid">
        <div class="br-hero__copy">
          <p class="eyebrow lp-hero__eyebrow">Where to collect</p>
          <h1 class="display" id="br-title">Our branches.</h1>
          <p class="lede">Every car is collected from, and returned to, the branch that keeps it. Hours, addresses and what is free tomorrow — read live from the fleet.</p>
          <div class="br-stats" id="br-stats"></div>
        </div>
        <div class="br-hero__map" id="br-map"></div>
      </div>
    </section>
    <section class="section br-list">
      <div id="branch-list">${skeletons(2)}</div>
      <ul class="br-notes" data-reveal>
        <li>${icon('route', 18)}<span><b>Same branch, both ways.</b> A car is returned to the branch it was collected from.</span></li>
        <li>${icon('shield', 18)}<span><b>Bring your licence.</b> The branch checks it at handover before the keys change hands.</span></li>
        <li>${icon('clock', 18)}<span><b>Sri Lanka time.</b> Hours and the open-now status follow the island's clock, wherever you are.</span></li>
      </ul>
    </section>
    ${footer()}`;
  window.dispatchEvent(new Event('scroll'));

  const [list, free] = await Promise.all([
    api.branches.list(),
    api.vehicles.search(addDays(1), addDays(2)).catch(() => null),
  ]);
  const host = $('#branch-list', el);
  if (!list.length) {
    host.innerHTML = empty({ icon: 'building', title: 'No branches yet', text: 'Branches added by an administrator appear here.' });
    $('#br-map', el).innerHTML = branchMap([]);
    return;
  }
  const sorted = [...list].sort((a, b) => (a.status === b.status ? a.name.localeCompare(b.name) : a.status === 'ACTIVE' ? -1 : 1));
  const active = sorted.filter((b) => b.status === 'ACTIVE');
  const freeAt = (id) => (free ? free.filter((v) => v.branchId === id).length : null);

  const n = active.length;
  $('#br-title', el).innerHTML = `${esc(n < NUMBER_WORDS.length ? NUMBER_WORDS[n] : String(n))} branch${n === 1 ? '' : 'es'}. <em>One island.</em>`;
  const opens = active.map((b) => toMinutes(b.openTime)).filter((v) => v !== null);
  const closes = active.map((b) => toMinutes(b.closeTime)).filter((v) => v !== null);
  const hhmm = (m) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
  $('#br-stats', el).innerHTML = `
    <div><b>${n}</b><span>Branches</span></div>
    <div><b id="br-open-now">—</b><span>Open right now</span></div>
    <div><b>${free ? free.filter((v) => active.some((b) => b.branchId === v.branchId)).length : '—'}</b><span>Cars free tomorrow</span></div>
    <div><b>${opens.length ? `${hhmm(Math.min(...opens))}–${hhmm(Math.max(...closes))}` : '—'}</b><span>Earliest to latest</span></div>`;

  $('#br-map', el).innerHTML = branchMap(active);
  host.innerHTML = `<div class="br-grid">${sorted.map((b, i) => branchPanel(b, i, freeAt(b.branchId))).join('')}</div>`;

  // The clock: open-now states, countdowns and the "now" marker on every
  // timeline, refreshed each minute while the page is showing.
  const paint = () => {
    const now = colomboMinutes();
    let openCount = 0;
    sorted.forEach((b) => {
      const c = branchClock(b, now);
      if (c.open) openCount += 1;
      const card = $(`[data-branch="${b.branchId}"]`, el);
      if (card) {
        card.classList.toggle('is-open', c.open);
        $('[data-state] span', card).textContent = b.status !== 'ACTIVE' ? 'Closed' : c.open ? 'Open now' : c.known ? 'Closed now' : 'Hours not listed';
        $('[data-line]', card).textContent = c.line;
        $('[data-now]', card)?.style.setProperty('--now', (now / 1440).toFixed(4));
      }
      const label = $(`[data-label="${b.branchId}"]`, el);
      if (label) { label.classList.toggle('is-open', c.open); $('[data-short]', label).textContent = c.short; }
      $(`[data-pin="${b.branchId}"]`, el)?.classList.toggle('is-open', c.open);
    });
    const o = $('#br-open-now', el);
    if (o) o.textContent = `${openCount} of ${n}`;
  };
  paint();
  const timer = setInterval(() => (el.isConnected && $('.br-grid', el) ? paint() : clearInterval(timer)), 60000);

  // Card and pin light each other up; a pin takes you to its card.
  const hot = (id, on) => {
    $$(`[data-pin="${id}"], [data-label="${id}"], [data-branch="${id}"]`, el).forEach((n2) => n2.classList.toggle('is-hot', on));
    $(`[data-lead="${id}"]`, el)?.classList.toggle('is-hot', on);
  };
  el.addEventListener('pointerover', (e) => {
    const t = e.target.closest('[data-branch], [data-pin], [data-label]');
    if (!t) return;
    const id = t.dataset.branch || t.dataset.pin || t.dataset.label;
    hot(id, true);
    t.addEventListener('pointerleave', () => hot(id, false), { once: true });
  });
  el.addEventListener('click', (e) => {
    const t = e.target.closest('[data-pin], [data-label]');
    if (!t) return;
    $(`[data-branch="${t.dataset.pin || t.dataset.label}"]`, el)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  });

  // Rise-in, the same as the landing page.
  if ('IntersectionObserver' in window && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    el.classList.add('can-reveal');
    const io = new IntersectionObserver((entries) => entries.forEach((en) => {
      if (en.isIntersecting) { en.target.classList.add('is-in'); io.unobserve(en.target); }
    }), { threshold: 0.12 });
    $$('[data-reveal]', el).forEach((node) => io.observe(node));
  }
}

// ==================================================================
// Auth
// ==================================================================
export function safeNext(next) {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : null;
}

// The photograph behind each sign-in page. `light` means the copy sits on the
// bright part of the frame and is set in ink; otherwise it is set in white.
const AUTH_PHOTOS = {
  login: { name: 'spoiler-rear', sizes: [800, 1400], light: true, caption: 'Built for the long way home' },
  register: { name: 'taillight-rear', sizes: [1000, 2000], light: false, caption: 'Your first set of keys' },
};

export function authStage(title, text, photo) {
  const p = AUTH_PHOTOS[photo];
  const [small, large] = p.sizes;
  return `<div class="auth__stage auth__stage--${p.light ? 'light' : 'dark'} auth__stage--${photo}">
    <img class="auth__photo" src="assets/${p.name}-${large}.jpg"
         srcset="assets/${p.name}-${small}.jpg ${small}w, assets/${p.name}-${large}.jpg ${large}w"
         sizes="(max-width: 1024px) 100vw, 640px" alt="" decoding="async">
    <div><span class="brand-mark">AXLE<i></i></span></div>
    <div class="auth__copy">
      <h2 class="title-lg">${title}</h2>
      <p class="muted">${esc(text)}</p>
    </div>
    <p class="auth__caption"><span></span>${esc(p.caption)}</p>
  </div>`;
}

export async function renderLogin({ el, query }) {
  el.innerHTML = `
    <section class="auth">
      ${authStage('Your next drive<br>starts here.', 'Sign in to reserve from the collection, follow your requests and manage the fleet.', 'login')}
      <div class="auth__panel">
        <header>
          <p class="eyebrow">Welcome back</p>
          <h1 class="title-lg" style="margin-top:12px">Sign in</h1>
        </header>
        <form class="auth__form" id="login" novalidate>
          ${field({ name: 'email', label: 'Email', type: 'email', required: true, autocomplete: 'email' })}
          ${field({ name: 'password', label: 'Password', type: 'password', required: true, autocomplete: 'current-password' })}
          <p class="auth__forgot"><a href="#/forgot">Forgot your password?</a></p>
          <div id="login-error"></div>
          <button class="btn btn--lg btn--block" type="submit">Sign in ${icon('arrowRight', 18)}</button>
          <p class="auth__switch">New here? <a href="#/register${query.next ? `?next=${encodeURIComponent(query.next)}` : ''}">Create an account</a></p>
        </form>
      </div>
    </section>`;

  const form = $('#login', el);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!validateForm(form)) return;
    const { email, password } = readForm(form);
    const btn = form.querySelector('[type=submit]');
    const errorBox = $('#login-error', el);
    errorBox.innerHTML = '';
    btn.disabled = true;
    btn.classList.add('is-busy');
    try {
      const result = await api.auth.login(email, password);
      if (result && result.twoFactorRequired) {
        askForCode();
        return;
      }
      welcome(result);
    } catch (err) {
      errorBox.innerHTML = formError(errorSummary(err, showFieldErrors(form, err)));
      btn.disabled = false;
      btn.classList.remove('is-busy');
    }
  });

  function welcome(user) {
    setUser(user);
    toast(`Welcome back, ${user.fullName.split(' ')[0]}`, 'success');
    navigate(safeNext(query.next) || homeFor());
  }

  // Two-step sign-in: the password was right; now the code from the app.
  function askForCode() {
    const panel = $('.auth__panel', el);
    panel.innerHTML = `
      <header>
        <p class="eyebrow">Two-step sign-in</p>
        <h1 class="title-lg" style="margin-top:12px">Enter your code</h1>
        <p class="muted" style="margin-top:10px">Open your authenticator app and type the six-digit code for Axle.</p>
      </header>
      <form class="auth__form" id="code-form" novalidate>
        ${field({ name: 'code', label: 'Six-digit code', required: true, autocomplete: 'one-time-code', hint: 'The code changes every 30 seconds' })}
        <div id="code-error"></div>
        <button class="btn btn--lg btn--block" type="submit">Verify ${icon('arrowRight', 18)}</button>
        <p class="auth__switch"><a href="#/login">Start again</a></p>
      </form>`;
    const codeForm = $('#code-form', panel);
    const input = codeForm.elements.code;
    input.setAttribute('inputmode', 'numeric');
    input.setAttribute('maxlength', '7');
    input.focus();
    codeForm.addEventListener('submit', async (ev) => {
      ev.preventDefault();
      const b = codeForm.querySelector('[type=submit]');
      const box = $('#code-error', panel);
      box.innerHTML = '';
      if (!validateForm(codeForm)) return;
      b.disabled = true;
      b.classList.add('is-busy');
      try {
        welcome(await api.auth.loginCode(input.value.replace(/\s/g, '')));
      } catch (err) {
        box.innerHTML = formError(errorSummary(err, showFieldErrors(codeForm, err)));
        b.disabled = false;
        b.classList.remove('is-busy');
        input.select();
      }
    });
  }
}

export async function renderRegister({ el, query }) {
  el.innerHTML = `
    <section class="auth">
      ${authStage('Join in<br>a minute.', 'Create a customer account. Bring your driving licence when you collect your first vehicle — our team verifies it at the counter, and a verified licence is required before you can book.', 'register')}
      <div class="auth__panel">
        <header>
          <p class="eyebrow">New customer</p>
          <h1 class="title-lg" style="margin-top:12px">Create your account</h1>
        </header>
        <form class="auth__form" id="register" novalidate>
          <div class="form-grid">
            ${field({ name: 'firstName', label: 'First name', required: true, autocomplete: 'given-name' })}
            ${field({ name: 'lastName', label: 'Last name', required: true, autocomplete: 'family-name' })}
            ${field({ name: 'email', label: 'Email', type: 'email', required: true, span: true, autocomplete: 'email' })}
            ${field({ name: 'password', label: 'Password', type: 'password', required: true, minlength: 8, maxlength: 72, span: true, hint: 'At least 8 characters, with letters and numbers', autocomplete: 'new-password' })}
            ${field({ name: 'phoneNumber', label: 'Phone', type: 'tel', autocomplete: 'tel' })}
            ${field({ name: 'dateOfBirth', label: 'Date of birth', type: 'date', required: true, max: latestDobForDriving(), hint: 'Drivers must be 18 or over' })}
            ${field({ name: 'address', label: 'Address', span: true, autocomplete: 'street-address' })}
            ${field({ name: 'drivingLicenseNumber', label: 'Driving licence no.', required: true })}
            ${field({ name: 'licenseExpiryDate', label: 'Licence expiry', type: 'date', required: true, min: addDays(1), hint: 'Must still be valid' })}
            ${checkbox({ name: 'privacyConsent', label: 'I have read the privacy notice', description: 'How we use your details, how long we keep them, and how to get a copy or have them erased.' })}
          </div>
          <p class="auth__privacy"><a href="#/privacy" target="_blank" rel="noopener">Read the privacy notice ${icon('external', 13)}</a></p>
          <div id="register-error"></div>
          <button class="btn btn--lg btn--block" type="submit">Create account ${icon('arrowRight', 18)}</button>
          <p class="auth__switch">Already registered? <a href="#/login${query.next ? `?next=${encodeURIComponent(query.next)}` : ''}">Sign in</a></p>
        </form>
      </div>
    </section>`;

  const form = $('#register', el);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!validateForm(form)) return;
    const values = readForm(form);
    const btn = form.querySelector('[type=submit]');
    const errorBox = $('#register-error', el);
    errorBox.innerHTML = '';
    if (!values.privacyConsent) {
      markField(form.elements.privacyConsent, 'Please read and accept the privacy notice to create an account.');
      form.elements.privacyConsent.focus();
      return;
    }
    btn.disabled = true;
    btn.classList.add('is-busy');
    try {
      await api.auth.register(values);
      const user = await api.auth.login(values.email, values.password);
      setUser(user);
      toast(`Welcome to Axle, ${values.firstName}. We emailed you a link to confirm your address.`, 'success');
      navigate(safeNext(query.next) || '/account');
    } catch (err) {
      errorBox.innerHTML = formError(errorSummary(err, showFieldErrors(form, err)));
      btn.disabled = false;
      btn.classList.remove('is-busy');
    }
  });
}
