// ============================================================
// Console: the fleet timeline (C2). One row per vehicle, one column per
// day - bookings, workshop visits and branch moves laid out side by side,
// so a gap for a walk-in or a clash is visible at a glance.
// ============================================================
import { api } from '../api.js';
import { userMap, branchMap, isAdmin } from '../store.js';
import {
  $, esc, icon, fmtDate, isoDate, addDays, humanize, statusColor, empty, errorState, debounce,
} from '../ui.js';
import { consoleHead, searchBox, printHtml } from './console-kit.js';

const SPANS = [[14, '2 weeks'], [28, '4 weeks'], [56, '8 weeks']];
const dayMs = 86400000;
const dateOf = (iso) => new Date(`${iso}T00:00:00`);
const diff = (a, b) => Math.round((dateOf(a) - dateOf(b)) / dayMs);

/** Lay bars in lanes so two that overlap (say, two pending requests) never cover each other. */
function lanes(bars) {
  const ends = [];
  bars.sort((a, b) => a.from.localeCompare(b.from) || b.to.localeCompare(a.to));
  bars.forEach((bar) => {
    let lane = ends.findIndex((end) => end < bar.from);
    if (lane === -1) { lane = ends.length; ends.push(bar.to); } else ends[lane] = bar.to;
    bar.lane = lane;
  });
  return Math.max(1, ends.length);
}

export async function renderTimeline({ el, query }) {
  const state = {
    from: /^\d{4}-\d{2}-\d{2}$/.test(query.from || '') ? query.from : addDays(-2),
    days: SPANS.some(([n]) => n === Number(query.days)) ? Number(query.days) : 28,
    branch: isAdmin() && query.branch ? Number(query.branch) : null,
    text: '',
    data: null, users: new Map(), branches: new Map(),
  };

  el.innerHTML = `
    ${consoleHead({
      eyebrow: 'Planning', title: 'Fleet timeline',
      text: 'Every car, day by day: who has it, when it is in the workshop, and where the gaps are.',
      actions: `<button class="btn btn--tonal btn--sm" type="button" data-tl="print">${icon('printer', 16)} Print</button>`,
    })}
    <div class="toolbar tl-toolbar">
      <div class="tl-nav">
        <button class="icon-btn icon-btn--sm" type="button" data-tl="prev" aria-label="Earlier">${icon('chevronLeft', 16)}</button>
        <button class="btn btn--sm btn--tonal" type="button" data-tl="today">Today</button>
        <button class="icon-btn icon-btn--sm" type="button" data-tl="next" aria-label="Later">${icon('chevronRight', 16)}</button>
        <span class="tl-range" id="tl-range"></span>
      </div>
      <div class="tl-controls">
        <div class="seg" role="tablist">${SPANS.map(([n, l]) => `<button type="button" role="tab" data-span="${n}" class="${n === state.days ? 'is-active' : ''}">${l}</button>`).join('')}</div>
        <span id="tl-branch"></span>
        ${searchBox('Filter model or plate')}
      </div>
    </div>
    <div class="tl-legend" aria-hidden="true">
      <span><i style="background:${statusColor('PENDING_APPROVAL')}"></i>Awaiting approval</span>
      <span><i style="background:${statusColor('APPROVED')}"></i>Approved</span>
      <span><i style="background:${statusColor('ACTIVE_RENTAL')}"></i>On the road</span>
      <span><i class="is-work"></i>Workshop</span>
      <span><i class="is-move"></i>Branch move</span>
    </div>
    <div id="tl-body"><div class="skeleton" style="height:420px"></div></div>`;

  const to = () => addDays(state.days - 1, dateOf(state.from));

  function syncUrl() {
    const q = new URLSearchParams({ from: state.from, days: state.days });
    if (state.branch) q.set('branch', state.branch);
    history.replaceState(null, '', `#/console/timeline?${q}`);
  }

  async function load() {
    syncUrl();
    $('#tl-range', el).textContent = `${fmtDate(state.from)} – ${fmtDate(to())}`;
    try {
      const [data, users, branches] = await Promise.all([
        api.bookings.timeline({ from: state.from, to: to(), branchId: state.branch || undefined }),
        userMap(), branchMap(),
      ]);
      Object.assign(state, { data, users, branches });
      if (isAdmin()) {
        $('#tl-branch', el).innerHTML = `<label class="branch-pick">${icon('pin', 16)}
          <select data-branch aria-label="Branch">
            <option value="">All branches</option>
            ${[...branches.values()].map((b) => `<option value="${b.branchId}" ${b.branchId === state.branch ? 'selected' : ''}>${esc(b.name)}</option>`).join('')}
          </select></label>`;
      }
      draw();
    } catch (err) {
      $('#tl-body', el).innerHTML = errorState(err);
    }
  }

  /** All bars for one vehicle, clipped to the window. */
  function barsFor(v) {
    const d = state.data;
    const clip = (from, toDay) => ({ from: from < d.from ? d.from : from, to: toDay > d.to ? d.to : toDay, cutStart: from < d.from, cutEnd: toDay > d.to });
    const bars = [];
    d.bookings.filter((b) => b.vehicleId === v.vehicleId).forEach((b) => {
      const who = state.users.get(b.customerId)?.fullName || `Customer #${b.customerId}`;
      bars.push({
        ...clip(b.pickupDate, b.returnDate), kind: 'booking', status: b.status,
        label: `#${b.bookingId} · ${who}`,
        title: `Booking #${b.bookingId} · ${who} · ${humanize(b.status)} · ${fmtDate(b.pickupDate)} → ${fmtDate(b.returnDate)}`,
        href: `#/console/bookings?tab=${b.status}`,
      });
    });
    d.maintenance.filter((m) => m.vehicleId === v.vehicleId).forEach((m) => {
      bars.push({
        ...clip(m.from, m.to), kind: 'work', label: m.label || 'Workshop',
        title: `${m.label || 'Workshop'} · ${humanize(m.status)} · ${fmtDate(m.from)} → ${fmtDate(m.to)}`,
        href: '#/console/maintenance',
      });
    });
    d.transfers.filter((x) => x.vehicleId === v.vehicleId).forEach((x) => {
      const where = state.branches.get(x.toBranchId)?.name || `branch #${x.toBranchId}`;
      bars.push({ ...clip(x.date, x.date), kind: 'move', label: `→ ${where}`, title: `Moving to ${where} on ${fmtDate(x.date)}`, href: '#/console/transfers' });
    });
    return bars;
  }

  function draw() {
    const d = state.data;
    const q = state.text.trim().toLowerCase();
    const vehicles = d.vehicles
      .filter((v) => !q || `${v.model} ${v.plateNumber} ${v.category}`.toLowerCase().includes(q))
      .sort((a, b) => a.model.localeCompare(b.model));
    const host = $('#tl-body', el);
    if (!vehicles.length) {
      host.innerHTML = empty({ icon: 'car', title: q ? 'No vehicles match' : 'No vehicles here', text: q ? 'Try a different filter.' : 'Vehicles at this branch appear here.' });
      return;
    }
    const n = diff(d.to, d.from) + 1;
    const days = Array.from({ length: n }, (_, i) => addDays(i, dateOf(d.from)));
    const today = isoDate();
    const dayCls = (iso) => {
      const wd = dateOf(iso).getDay();
      return `${wd === 0 || wd === 6 ? ' is-weekend' : ''}${iso === today ? ' is-today' : ''}`;
    };
    const busyDays = { total: 0, used: 0 };

    const rows = vehicles.map((v) => {
      const bars = barsFor(v);
      const laneCount = lanes(bars);
      const used = new Set();
      bars.filter((b) => b.kind !== 'move').forEach((b) => {
        for (let i = diff(b.from, d.from); i <= diff(b.to, d.from); i++) used.add(i);
      });
      busyDays.total += n;
      busyDays.used += used.size;
      const branch = state.branches.get(v.branchId)?.name || '';
      return `<div class="tl-row" style="--lanes:${laneCount}">
        <div class="tl-car">
          <b>${esc(v.model)}</b>
          <small>${esc(v.plateNumber)}${!state.branch && branch ? ` · ${esc(branch)}` : ''}</small>
          <small class="tl-util">${Math.round((used.size / n) * 100)}% booked</small>
        </div>
        <div class="tl-track" style="--days:${n}">
          ${days.map((iso, i) => `<span class="tl-cell${dayCls(iso)}" style="grid-column:${i + 1}"></span>`).join('')}
          ${bars.map((b) => {
            const start = diff(b.from, d.from) + 1;
            const span = diff(b.to, b.from) + 1;
            const color = b.kind === 'booking' ? `background:${statusColor(b.status)}` : '';
            return `<a class="tl-bar tl-bar--${b.kind}${b.status === 'PENDING_APPROVAL' ? ' is-pending' : ''}${b.cutStart ? ' cut-start' : ''}${b.cutEnd ? ' cut-end' : ''}"
              href="${b.href}" style="grid-column:${start} / span ${span};grid-row:${b.lane + 1};${color}" title="${esc(b.title)}">
              <span>${esc(b.label)}</span></a>`;
          }).join('')}
        </div>
      </div>`;
    }).join('');

    const monthLabel = (iso, i) => (i === 0 || iso.endsWith('-01')
      ? `<em>${dateOf(iso).toLocaleDateString('en-GB', { month: 'short' })}</em>` : '');
    host.innerHTML = `
      <p class="tl-summary muted small">${vehicles.length} vehicle${vehicles.length === 1 ? '' : 's'} · ${Math.round((busyDays.used / Math.max(1, busyDays.total)) * 100)}% of car-days taken in this window</p>
      <div class="tl" role="region" aria-label="Fleet timeline" tabindex="0" style="--days:${n}">
        <div class="tl-row tl-head">
          <div class="tl-car"><small>Vehicle</small></div>
          <div class="tl-track" style="--days:${n}">
            ${days.map((iso, i) => `<span class="tl-day${dayCls(iso)}" style="grid-column:${i + 1}">${monthLabel(iso, i)}<b>${dateOf(iso).getDate()}</b><small>${'SMTWTFS'[dateOf(iso).getDay()]}</small></span>`).join('')}
          </div>
        </div>
        ${rows}
      </div>`;
  }

  function printIt() {
    const d = state.data;
    if (!d) return;
    const n = diff(d.to, d.from) + 1;
    const days = Array.from({ length: n }, (_, i) => addDays(i, dateOf(d.from)));
    const rows = d.vehicles.map((v) => {
      const bars = barsFor(v);
      const cells = days.map((iso) => {
        const hit = bars.find((b) => b.kind !== 'move' && iso >= b.from && iso <= b.to);
        const mark = !hit ? '' : hit.kind === 'work' ? 'W' : hit.status === 'PENDING_APPROVAL' ? '?' : hit.status === 'ACTIVE_RENTAL' ? '●' : '■';
        return `<td style="text-align:center;padding:3px 0;${hit ? 'background:#eee' : ''}">${mark}</td>`;
      }).join('');
      return `<tr><td style="white-space:nowrap">${esc(v.model)}<br><span class="muted">${esc(v.plateNumber)}</span></td>${cells}</tr>`;
    }).join('');
    printHtml('Fleet timeline', `<header><div><h1>Fleet timeline</h1>
      <div class="muted">${fmtDate(d.from)} – ${fmtDate(d.to)}${state.branch ? ` · ${esc(state.branches.get(state.branch)?.name || '')}` : ''}</div></div>
      <div class="muted">■ approved · ● on the road · ? awaiting approval · W workshop</div></header>
      <table style="font-size:10px"><thead><tr><th>Vehicle</th>${days.map((iso) => `<th style="text-align:center;padding:3px 0">${dateOf(iso).getDate()}</th>`).join('')}</tr></thead>
      <tbody>${rows}</tbody></table>`);
  }

  el.addEventListener('click', (e) => {
    const nav = e.target.closest('[data-tl]');
    if (nav) {
      const step = Math.max(7, Math.round(state.days / 2));
      if (nav.dataset.tl === 'prev') state.from = addDays(-step, dateOf(state.from));
      if (nav.dataset.tl === 'next') state.from = addDays(step, dateOf(state.from));
      if (nav.dataset.tl === 'today') state.from = addDays(-2);
      if (nav.dataset.tl === 'print') { printIt(); return; }
      load();
      return;
    }
    const span = e.target.closest('[data-span]');
    if (span) {
      state.days = Number(span.dataset.span);
      el.querySelectorAll('[data-span]').forEach((b) => b.classList.toggle('is-active', b === span));
      load();
    }
  });
  el.addEventListener('change', (e) => {
    if (e.target.matches('[data-branch]')) { state.branch = e.target.value ? Number(e.target.value) : null; load(); }
  });
  el.addEventListener('input', debounce((e) => {
    if (e.target.matches('[data-search]') && state.data) { state.text = e.target.value; draw(); }
  }, 150));

  await load();
}
