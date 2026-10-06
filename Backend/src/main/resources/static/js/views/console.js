// ============================================================
// Console: overview dashboard, bookings, handovers, payments.
// ============================================================
import { api } from '../api.js';
import { fleetMap, userMap, branchMap, invalidate, firstName, isAdmin } from '../store.js';
import {
  $, $$, esc, icon, money, number, fmtDate, fmtDateTime, isoDate, relativeTime, humanize, statusChip, statusColor,
  vehicleVisual, openDialog, confirmDialog, promptDialog, runAction, toast, empty, skeletons, debounce, rentalDays,
} from '../ui.js';
import {
  consoleHead, segmented, searchBox, dataList, row, cell, actionsCell, vehicleCell, personCell,
  datesCell, pickupDialog, returnDialog, pager, fetchAllPages, downloadCsv, exportButtons, printTable,
  printHandoverSheet,
} from './console-kit.js';

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
}

const todayLabel = () => new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' });

// ==================================================================
// Overview
// ==================================================================
export async function renderOverview({ el, query = {} }) {
  // An administrator can look at one branch at a time (C6); staff always see
  // their own branch - the server decides that, not this picker.
  const branchId = isAdmin() && query.branch ? Number(query.branch) : null;
  el.innerHTML = `${consoleHead({ eyebrow: todayLabel(), title: `${greeting()}, ${firstName()}` })}
    <div class="kpi-grid">${'<div class="skeleton" style="min-height:142px"></div>'.repeat(5)}</div>`;

  const [d, pendingAll, reminders, expiring, fleet, users, branches] = await Promise.all([
    api.dashboard(branchId || undefined),
    api.bookings.pending(),
    api.maintenance.reminders(30).catch(() => []),
    api.insurance.expiring(30).catch(() => []),
    fleetMap(),
    userMap(),
    isAdmin() ? branchMap() : Promise.resolve(new Map()),
  ]);
  const inBranch = (vehicleId) => !branchId || fleet.get(vehicleId)?.branchId === branchId;
  const pending = branchId ? pendingAll.filter((b) => b.pickupBranchId === branchId) : pendingAll;
  const branchPicker = isAdmin() && branches.size ? `
    <label class="branch-pick">${icon('pin', 16)}
      <select data-branch aria-label="Branch">
        <option value="">All branches</option>
        ${[...branches.values()].map((b) => `<option value="${b.branchId}" ${b.branchId === branchId ? 'selected' : ''}>${esc(b.name)}</option>`).join('')}
      </select>
    </label>` : '';

  const vehicleStatus = Object.entries(d.vehiclesByStatus || {});
  const totalVehicles = vehicleStatus.reduce((s, [, n]) => s + n, 0);
  const bookingOrder = ['PENDING_APPROVAL', 'APPROVED', 'ACTIVE_RENTAL', 'COMPLETED', 'REJECTED', 'CANCELLED'];
  const bookingStatus = bookingOrder.map((k) => [k, d.bookingsByStatus?.[k] || 0]);
  const maxBooking = Math.max(1, ...bookingStatus.map(([, n]) => n));

  el.innerHTML = `
    ${consoleHead({
      eyebrow: todayLabel(),
      title: `${greeting()}, ${firstName()}`,
      text: `${branchId ? `${branches.get(branchId)?.name || 'This branch'}: ` : ''}${d.pendingApprovals ? `${d.pendingApprovals} request${d.pendingApprovals === 1 ? '' : 's'} waiting for a decision.` : 'No requests waiting — the queue is clear.'}`,
      actions: `${branchPicker}<a class="btn btn--tonal" href="#/console/maintenance">${icon('wrench', 18)} Log service</a>
                <a class="btn" href="#/console/bookings">${icon('ticket', 18)} Review bookings</a>`,
    })}

    <div class="kpi-grid">
      ${kpi({ label: 'Awaiting approval', value: d.pendingApprovals, iconName: 'ticket', href: 'console/bookings', dark: true })}
      ${kpi({ label: 'On the road', value: d.activeRentals, iconName: 'route', href: 'console/handovers' })}
      ${kpi({ label: 'Available now', value: d.availableVehicles, unit: `of ${totalVehicles}`, iconName: 'car', href: 'console/fleet' })}
      ${kpi({ label: 'In the workshop', value: d.vehiclesUnderMaintenance, iconName: 'wrench', href: 'console/maintenance' })}
      ${kpi({ label: 'Revenue collected', value: money(d.totalRevenuePaid), iconName: 'wallet', href: 'console/payments', accent: true, small: true })}
    </div>

    ${attentionRow(d)}

    <div class="dash-grid">
      <section class="panel col-7">
        <div class="panel__head">
          <h3>Fleet status</h3>
          <span class="muted" style="font-size:13px">${totalVehicles} vehicle${totalVehicles === 1 ? '' : 's'}</span>
        </div>
        ${totalVehicles ? `
          <div class="stack-bar">${vehicleStatus.map(([k, n]) => `<span title="${esc(humanize(k))}: ${n}" style="flex-grow:${n};background:${statusColor(k)}"></span>`).join('')}</div>
          <div class="legend">${vehicleStatus.map(([k, n]) => `<div><i style="background:${statusColor(k)}"></i>${esc(humanize(k))}<b>${n}</b></div>`).join('')}</div>`
          : empty({ icon: 'car', title: 'No vehicles yet', action: isAdmin() ? '<a class="btn btn--sm" href="#/console/fleet">Add a vehicle</a>' : '' })}
      </section>

      <section class="panel col-5">
        <div class="panel__head">
          <h3>Booking pipeline</h3>
          <span class="muted" style="font-size:13px">${number(d.totalBookings)} total</span>
        </div>
        <div class="bars">${bookingStatus.map(([k, n]) => `
          <div class="bars__row">
            <span>${esc(humanize(k))}</span>
            <div class="bars__track"><div class="bars__fill" style="width:${(n / maxBooking) * 100}%;background:${statusColor(k)}"></div></div>
            <b>${n}</b>
          </div>`).join('')}</div>
      </section>

      <section class="panel col-7">
        <div class="panel__head">
          <h3>Waiting for a decision</h3>
          <a class="kpi__link" href="#/console/bookings">See all ${icon('arrowRight', 14)}</a>
        </div>
        <div id="pending-list">${pendingList(pending, fleet, users)}</div>
      </section>

      <section class="panel col-5">
        <div class="panel__head"><h3>Coming up · 30 days</h3></div>
        ${upcomingList(reminders.filter((m) => inBranch(m.vehicleId)), expiring.filter((x) => inBranch(x.vehicleId)), fleet, d.expiredInsurancePolicies)}
      </section>
    </div>`;

  if (el.dataset.bound) return;   // the panel re-renders itself after a decision
  el.dataset.bound = 'overview';
  const again = () => {
    const b = new URLSearchParams(location.hash.split('?')[1] || '').get('branch');
    return renderOverview({ el, query: b ? { branch: b } : {} });
  };
  el.addEventListener('change', (e) => {
    if (!e.target.matches('[data-branch]')) return;
    const v = e.target.value;
    history.replaceState(null, '', `#/console${v ? `?branch=${v}` : ''}`);
    again();
  });
  el.addEventListener('click', async (e) => {
    const btn = e.target.closest('[data-decide]');
    if (!btn) return;
    const id = Number(btn.dataset.id);
    const approve = btn.dataset.decide === 'approve';
    let reason = null;
    if (!approve) {
      reason = await promptDialog({
        title: `Reject booking #${id}?`,
        label: 'Reason (the customer sees this)',
        placeholder: 'e.g. Licence not verified',
        confirmLabel: 'Reject request',
        tone: 'danger',
      });
      if (reason === null) return;
    }
    const ok = await runAction(btn,
      () => (approve ? api.bookings.approve(id) : api.bookings.reject(id, reason)),
      approve ? `Booking #${id} approved` : `Booking #${id} rejected`);
    if (ok) again();
  });
}

/**
 * The things somebody has to do something about today. These used to be
 * invisible: a car a week overdue looked exactly like one due back tomorrow.
 */
function attentionRow(d) {
  const items = [
    d.overdueReturns ? ['alert', `${d.overdueReturns} vehicle${d.overdueReturns === 1 ? '' : 's'} overdue`, 'Past the agreed return date', 'console/handovers'] : null,
    d.missedPickups ? ['clock', `${d.missedPickups} pick-up${d.missedPickups === 1 ? '' : 's'} missed`, 'Approved but never collected', 'console/bookings'] : null,
    d.unpaidReadyForPickup ? ['wallet', `${d.unpaidReadyForPickup} unpaid, due out`, 'Payment is required before handover', 'console/payments'] : null,
    d.openDamageReports ? ['wrench', `${d.openDamageReports} damage report${d.openDamageReports === 1 ? '' : 's'} open`, 'Resolve to put the vehicle back on the road', 'console/damage'] : null,
    d.policiesExpiringSoon ? ['shield', `${d.policiesExpiringSoon} polic${d.policiesExpiringSoon === 1 ? 'y' : 'ies'} expiring`, 'Renew within 30 days', 'console/insurance'] : null,
    d.uninsuredVehicles ? ['shield', `${d.uninsuredVehicles} vehicle${d.uninsuredVehicles === 1 ? '' : 's'} uninsured`, 'Cannot be rented until covered', 'console/insurance'] : null,
    Number(d.outstandingPayments) > 0 ? ['receipt', `${money(d.outstandingPayments)} outstanding`, 'Still to collect', 'console/payments'] : null,
  ].filter(Boolean);

  if (!items.length) {
    return `<div class="notice" style="margin-bottom:18px">${icon('check', 18)}<span>Nothing needs attention: no overdue returns, missed pick-ups, unpaid handovers, open damage or expiring cover.</span></div>`;
  }
  return `<div class="attention">${items.map(([ic, title, text, href]) => `
    <a class="attention__card" href="#/${href}">
      <span class="attention__icon">${icon(ic, 18)}</span>
      <div><b>${esc(title)}</b><span>${esc(text)}</span></div>
      ${icon('arrowRight', 16)}
    </a>`).join('')}</div>`;
}

function kpi({ label, value, unit = '', iconName, href, dark = false, accent = false, small = false }) {
  return `<a class="kpi${dark ? ' kpi--dark' : ''}${accent ? ' kpi--accent' : ''}" href="#/${href}">
    <div class="kpi__top">
      <span class="kpi__icon">${icon(iconName, 18)}</span>
      ${icon('arrowUpRight', 16)}
    </div>
    <span class="kpi__label">${esc(label)}</span>
    <div class="kpi__value" ${small ? 'style="font-size:28px"' : ''}>${esc(value ?? 0)}${unit ? `<small>${esc(unit)}</small>` : ''}</div>
  </a>`;
}

function pendingList(pending, fleet, users) {
  if (!pending.length) {
    return empty({ icon: 'check', title: 'All caught up', text: 'New booking requests will appear here.' });
  }
  return `<div style="display:grid;gap:10px">${pending.slice(0, 5).map((b) => {
    const v = fleet.get(b.vehicleId);
    const u = users.get(b.customerId);
    return `<div class="notice" style="background:var(--surface-2);justify-content:space-between;flex-wrap:wrap">
      <div class="with-art" style="flex:1;min-width:220px">
        <div class="mini-car">${v ? vehicleVisual(v) : ''}</div>
        <div>
          <b style="display:block;color:var(--ink)">${esc(u?.fullName || `Customer #${b.customerId}`)}</b>
          <small class="muted">${esc(v?.model || `Vehicle #${b.vehicleId}`)} · ${fmtDate(b.pickupDate)} → ${fmtDate(b.returnDate)} · ${money(b.estimatedCost)}</small>
        </div>
      </div>
      <div style="display:flex;gap:6px">
        <button class="btn btn--sm btn--outline" data-decide="reject" data-id="${b.bookingId}">Reject</button>
        <button class="btn btn--sm" data-decide="approve" data-id="${b.bookingId}">${icon('check', 16)} Approve</button>
      </div>
    </div>`;
  }).join('')}</div>`;
}

function upcomingList(reminders, expiring, fleet, expiredCount) {
  const items = [
    ...reminders.map((m) => ({
      date: m.nextServiceDate, iconName: 'wrench', title: `Service due · ${fleet.get(m.vehicleId)?.model || `Vehicle #${m.vehicleId}`}`,
      text: m.repairType || 'Scheduled service', href: 'console/maintenance',
    })),
    ...expiring.map((p) => ({
      date: p.expiryDate, iconName: 'shield', title: `Policy expires · ${fleet.get(p.vehicleId)?.model || `Vehicle #${p.vehicleId}`}`,
      text: `${p.provider} · ${p.policyNumber}`, href: 'console/insurance',
    })),
  ].sort((a, b) => String(a.date).localeCompare(String(b.date)));

  const expiredNote = expiredCount > 0
    ? `<a class="notice notice--red" href="#/console/insurance" style="margin-bottom:10px">${icon('shieldAlert', 18)}<span>${expiredCount} insurance polic${expiredCount === 1 ? 'y has' : 'ies have'} already expired</span></a>`
    : '';

  if (!items.length) {
    return expiredNote + empty({ icon: 'calendar', title: 'Nothing due soon', text: 'Upcoming services and policy renewals show here.' });
  }
  return `${expiredNote}<div style="display:grid;gap:8px">${items.slice(0, 6).map((it) => `
    <a class="notice" href="#/${it.href}" style="background:var(--surface-2)">
      <span class="notif__icon" style="width:38px;height:38px;background:var(--surface)">${icon(it.iconName, 18)}</span>
      <div style="flex:1;min-width:0">
        <b style="display:block;color:var(--ink)">${esc(it.title)}</b>
        <small class="muted">${esc(it.text)}</small>
      </div>
      <span class="tag">${esc(fmtDate(it.date))}</span>
    </a>`).join('')}</div>`;
}

// ==================================================================
// Bookings
// ==================================================================
const BOOKING_TABS = [
  ['PENDING_APPROVAL', 'Awaiting approval'],
  ['APPROVED', 'Ready for pick-up'],
  ['ACTIVE_RENTAL', 'On trip'],
  ['COMPLETED', 'Completed'],
  ['NO_SHOW', 'No-show'],
  ['CANCELLED', 'Cancelled'],
  ['all', 'All'],
];
const PAGE_SIZE = 20;

export async function renderBookings({ el, query }) {
  const state = {
    tab: BOOKING_TABS.some(([k]) => k === query.tab) ? query.tab : 'PENDING_APPROVAL',
    text: '',
    page: 1,
    data: null,
    bookings: [], fleet: new Map(), users: new Map(), branches: new Map(), payments: new Map(),
    picked: new Set(),
  };

  el.innerHTML = `
    ${consoleHead({ eyebrow: 'Reservations', title: 'Bookings', text: 'Approve requests, hand vehicles over and close trips.', actions: exportButtons() })}
    <div class="toolbar"><div id="tabs"></div>${searchBox('Search customer, vehicle or #id')}</div>
    <div id="bulk"></div>
    <div id="list">${skeletons(1, '')}</div>
    <div id="pager"></div>`;

  const query_ = () => ({ status: state.tab, q: state.text.trim() || undefined });

  // One page from the server (C1). The lookups (fleet, people, branches) are
  // cached; the payment status is fetched for the rows on screen only.
  async function load() {
    invalidate('fleet');
    const [data, fleet, users, branches] = await Promise.all([
      api.bookings.page({ ...query_(), page: state.page, size: PAGE_SIZE }), fleetMap(), userMap(), branchMap(),
    ]);
    if (data.page > data.pages && state.page > 1) { state.page = data.pages; return load(); }
    const payments = await Promise.all(data.items.map((b) => api.bookings.payment(b.bookingId).catch(() => null)));
    Object.assign(state, {
      data, bookings: data.items, fleet, users, branches,
      payments: new Map(payments.filter(Boolean).map((x) => [x.bookingId, x])),
    });
    state.picked = new Set([...state.picked].filter((id) => data.items.some((b) => b.bookingId === id && b.status === 'PENDING_APPROVAL')));
    draw();
  }

  const selectable = () => state.tab === 'PENDING_APPROVAL';

  function drawBulk() {
    const host = $('#bulk', el);
    const pending = state.bookings.filter((b) => b.status === 'PENDING_APPROVAL');
    if (!selectable() || !pending.length) { host.innerHTML = ''; return; }
    const n = state.picked.size;
    const all = n === pending.length;
    host.innerHTML = `<div class="bulk-bar${n ? ' is-on' : ''}">
      <label class="bulk-bar__all"><input type="checkbox" data-pick-all ${all ? 'checked' : ''}> ${n ? `${n} selected` : 'Select all on this page'}</label>
      <div class="bulk-bar__actions">
        <button type="button" class="btn btn--sm btn--outline" data-bulk="reject" ${n ? '' : 'disabled'}>Reject selected</button>
        <button type="button" class="btn btn--sm" data-bulk="approve" ${n ? '' : 'disabled'}>${icon('check', 16)} Approve selected</button>
      </div>
    </div>`;
  }

  function draw() {
    const counts = state.data?.counts || {};
    $('#tabs', el).innerHTML = segmented(BOOKING_TABS.map(([k, l]) => [k, l, counts[k] || 0]), state.tab);
    drawBulk();
    $('#pager', el).innerHTML = pager(state.data);

    const list = state.bookings;
    const host = $('#list', el);
    if (!list.length) {
      host.innerHTML = empty({ icon: 'ticket', title: state.text ? 'No matches' : 'Nothing in this queue', text: state.text ? 'Try a different search.' : 'Bookings move here as their status changes.' });
      return;
    }
    const pick = selectable();
    host.innerHTML = dataList({
      cols: `${pick ? '34px ' : ''}56px minmax(130px,1.1fr) minmax(170px,1.4fr) minmax(130px,1fr) minmax(105px,.9fr) minmax(115px,.9fr) minmax(190px,auto)`,
      headers: [...(pick ? [''] : []), '#', 'Customer', 'Vehicle', 'Dates', 'Branch', 'Amount', ''],
      rows: list.map((b, i) => {
        const pay = state.payments.get(b.bookingId);
        return row([
          ...(pick ? [`<div class="cell cell--pick"><input type="checkbox" data-pick="${b.bookingId}" aria-label="Select booking #${b.bookingId}" ${state.picked.has(b.bookingId) ? 'checked' : ''}></div>`] : []),
          cell('Booking', `<span class="id-pill">#${b.bookingId}</span>`),
          cell('Customer', personCell(state.users.get(b.customerId), b.customerId)),
          cell('Vehicle', vehicleCell(state.fleet.get(b.vehicleId), b.vehicleId), 'cell--wide'),
          cell('Dates', datesCell(b.pickupDate, b.returnDate)),
          cell('Branch', `<b>${esc(state.branches.get(b.pickupBranchId)?.name || `#${b.pickupBranchId}`)}</b><small>${esc(relativeTime(b.submittedDate))}</small>`),
          cell('Amount', `<b>${money(b.estimatedCost)}</b><small>${pay ? `Payment ${esc(pay.status.toLowerCase())}` : '—'}</small>`),
          actionsCell(`${statusChip(b.status)}${bookingActions(b)}`),
        ], i);
      }),
    });
  }

  // Approve or reject every ticked request in one go (C3). Each is decided on
  // its own, so one clash does not stop the rest; the result says which failed.
  async function bulk(action, btn) {
    const ids = [...state.picked];
    if (!ids.length) return;
    let reason = null;
    if (action === 'reject') {
      reason = await promptDialog({
        title: `Reject ${ids.length} request${ids.length === 1 ? '' : 's'}?`,
        text: 'Each customer is notified that their request was declined.',
        label: 'Reason (the customers see this)',
        placeholder: 'e.g. Fully booked over the holiday weekend',
        confirmLabel: 'Reject all',
        tone: 'danger',
      });
      if (reason === null) return;
    } else {
      const ok = await confirmDialog({
        title: `Approve ${ids.length} request${ids.length === 1 ? '' : 's'}?`,
        text: 'Each is checked for clashes on its own. Any that cannot be approved are listed afterwards.',
        confirmLabel: 'Approve all',
      });
      if (!ok) return;
    }
    btn.disabled = true;
    try {
      const results = await api.bookings.bulk(action, ids, reason);
      const failed = results.filter((r) => !r.ok);
      const done = results.length - failed.length;
      state.picked.clear();
      if (done) toast(`${done} request${done === 1 ? '' : 's'} ${action === 'approve' ? 'approved' : 'rejected'}`, 'success');
      if (failed.length) {
        await openDialog({
          title: `${failed.length} could not be ${action === 'approve' ? 'approved' : 'rejected'}`,
          hideSubmit: true,
          cancelLabel: 'Close',
          body: `<ul class="bulk-fails">${failed.map((f) => `<li><span class="id-pill">#${f.bookingId}</span> ${esc(f.message || 'Refused')}</li>`).join('')}</ul>`,
        });
      }
      await load();
    } catch (err) {
      toast(err.message || 'Could not update the bookings', 'error');
      btn.disabled = false;
    }
  }

  // Everything that matches the tab and search, not just this page (C4).
  async function exportRows(kind) {
    const all = await fetchAllPages(api.bookings.page, query_());
    const label = BOOKING_TABS.find(([k]) => k === state.tab)?.[1] || 'All';
    const branch = (b) => state.branches.get(b.pickupBranchId)?.name || `#${b.pickupBranchId}`;
    if (kind === 'csv') {
      downloadCsv(`bookings-${state.tab.toLowerCase()}`,
        ['Booking', 'Status', 'Customer', 'Email', 'Vehicle', 'Plate', 'Branch', 'Pick-up', 'Return', 'Nights', 'Quoted', 'Final', 'Submitted'],
        all.map((b) => {
          const u = state.users.get(b.customerId);
          const v = state.fleet.get(b.vehicleId);
          return [b.bookingId, b.status, u?.fullName, u?.email, v?.model, v?.plateNumber, branch(b), b.pickupDate, b.returnDate,
            rentalDays(b.pickupDate, b.returnDate), b.estimatedCost, b.finalCost, b.submittedDate];
        }));
    } else {
      printTable(`Bookings · ${label}`, state.text ? `Matching "${state.text}"` : 'All branches you can see',
        ['#', 'Status', 'Customer', 'Vehicle', 'Branch', 'Dates', 'Amount'],
        all.map((b) => {
          const u = state.users.get(b.customerId);
          const v = state.fleet.get(b.vehicleId);
          return [`#${b.bookingId}`, esc(humanize(b.status)), esc(u?.fullName || `#${b.customerId}`),
            `${esc(v?.model || `#${b.vehicleId}`)}<br><span class="muted">${esc(v?.plateNumber || '')}</span>`,
            esc(branch(b)), `${fmtDate(b.pickupDate)} → ${fmtDate(b.returnDate)}`, money(b.finalCost ?? b.estimatedCost)];
        }));
    }
  }

  el.addEventListener('click', async (e) => {
    const tab = e.target.closest('[data-seg]');
    if (tab) {
      state.tab = tab.dataset.seg;
      state.page = 1;
      state.picked.clear();
      history.replaceState(null, '', `#/console/bookings?tab=${state.tab}`);
      await load();
      return;
    }
    const pg = e.target.closest('[data-page]');
    if (pg) { state.page = Number(pg.dataset.page); await load(); el.scrollIntoView({ block: 'start' }); return; }
    const exp = e.target.closest('[data-export]');
    if (exp) {
      exp.disabled = true;
      try { await exportRows(exp.dataset.export); } catch (err) { toast(err.message || 'Export failed', 'error'); }
      exp.disabled = false;
      return;
    }
    const bulkBtn = e.target.closest('[data-bulk]');
    if (bulkBtn) { await bulk(bulkBtn.dataset.bulk, bulkBtn); return; }
    const btn = e.target.closest('[data-booking]');
    if (!btn) return;
    const b = state.bookings.find((x) => x.bookingId === Number(btn.dataset.id));
    if (!b) return;
    const done = await handleBookingAction(btn, b, state);
    if (done) await load();
  });
  el.addEventListener('change', (e) => {
    if (e.target.matches('[data-pick]')) {
      const id = Number(e.target.dataset.pick);
      if (e.target.checked) state.picked.add(id); else state.picked.delete(id);
      drawBulk();
    } else if (e.target.matches('[data-pick-all]')) {
      const pending = state.bookings.filter((b) => b.status === 'PENDING_APPROVAL');
      state.picked = e.target.checked ? new Set(pending.map((b) => b.bookingId)) : new Set();
      $$('[data-pick]', el).forEach((c) => { c.checked = state.picked.has(Number(c.dataset.pick)); });
      drawBulk();
    }
  });
  el.addEventListener('input', debounce((e) => {
    if (e.target.matches('[data-search]')) { state.text = e.target.value; state.page = 1; load(); }
  }, 250));

  await load();
}

/**
 * What staff can do with a payment row.
 * A payment that was cancelled with its booking, or refunded, is closed:
 * there is nothing left to collect and nothing to undo.
 */
function payActions(p, booking, live) {
  if (p.status === 'CANCELLED') {
    return `<span class="status">Not charged</span><small class="muted">Booking ${esc(humanize(booking?.status || 'closed').toLowerCase())}</small>`;
  }
  if (p.status === 'REFUNDED') {
    return `${statusChip(p.status)}<small class="muted">Refund owed to the customer</small>`;
  }
  if (p.status === 'PENDING' && !live(p)) {
    if (p.note && Number(p.amount) > 0) {
      return `${statusChip(p.status)}<button class="btn btn--sm btn--success" data-pay="paid" data-id="${p.paymentId}" title="${esc(p.note)}">${icon('check', 16)} Collect charge</button>`;
    }
    return `<span class="status">Not charged</span><small class="muted">Trip ${esc(humanize(booking?.status || 'closed').toLowerCase())}</small>`;
  }
  if (p.status === 'PENDING') {
    return `${statusChip(p.status)}<button class="btn btn--sm btn--success" data-pay="paid" data-id="${p.paymentId}">${icon('check', 16)} Mark paid</button>`;
  }
  return `${statusChip(p.status)}<button class="btn btn--sm btn--text" data-pay="pending" data-id="${p.paymentId}">Undo</button>`;
}

export function bookingActions(b) {
  const id = b.bookingId;
  const details = `<button class="icon-btn icon-btn--sm" data-booking="details" data-id="${id}" aria-label="Details" title="Details">${icon('doc', 16)}</button>`;
  const sheet = (kind) => `<button class="icon-btn icon-btn--sm" data-booking="sheet" data-kind="${kind}" data-id="${id}" aria-label="Print ${kind} sheet" title="Print ${kind} sheet">${icon('printer', 16)}</button>`;
  switch (b.status) {
    case 'PENDING_APPROVAL':
      return `<button class="btn btn--sm btn--outline" data-booking="reject" data-id="${id}">Reject</button>
              <button class="btn btn--sm" data-booking="approve" data-id="${id}">Approve</button>${details}`;
    case 'APPROVED': {
      // The customer never came: close it and keep one night (B2).
      const missed = b.pickupDate < isoDate();
      return `<button class="btn btn--sm" data-booking="pickup" data-id="${id}">${icon('key', 16)} Hand over</button>
              ${missed ? `<button class="btn btn--sm btn--outline" data-booking="noshow" data-id="${id}" title="The customer did not collect the car">No-show</button>` : ''}
              <button class="icon-btn icon-btn--sm" data-booking="cancel" data-id="${id}" aria-label="Cancel booking" title="Cancel booking">${icon('close', 16)}</button>${sheet('pickup')}${details}`;
    }
    case 'ACTIVE_RENTAL':
      return `<button class="btn btn--sm btn--accent" data-booking="return" data-id="${id}">${icon('flag', 16)} Receive</button>${sheet('return')}${details}`;
    default:
      return details;
  }
}

/** Shared by bookings + handovers. Returns true when data changed. */
export async function handleBookingAction(btn, b, state) {
  const v = state.fleet.get(b.vehicleId);
  const u = state.users.get(b.customerId);
  switch (btn.dataset.booking) {
    case 'approve':
      return runAction(btn, () => api.bookings.approve(b.bookingId), `Booking #${b.bookingId} approved — customer notified`);
    case 'reject': {
      const reason = await promptDialog({
        title: `Reject booking #${b.bookingId}?`,
        text: `${u?.fullName || 'The customer'} will be notified that the request for ${v?.model || 'this vehicle'} was declined.`,
        label: 'Reason (the customer sees this)',
        placeholder: 'e.g. Vehicle needed for servicing',
        confirmLabel: 'Reject request',
        tone: 'danger',
      });
      if (reason === null) return false;
      return runAction(null, () => api.bookings.reject(b.bookingId, reason), `Booking #${b.bookingId} rejected`);
    }
    case 'cancel': {
      const reason = await promptDialog({
        title: `Cancel booking #${b.bookingId}?`,
        text: `${u?.fullName || 'The customer'} will be notified, and anything already paid is marked for refund.`,
        label: 'Reason (required)',
        placeholder: 'e.g. Customer did not collect the vehicle',
        confirmLabel: 'Cancel booking',
        tone: 'danger',
        required: true,
      });
      if (!reason) return false;
      return runAction(null, () => api.bookings.staffCancel(b.bookingId, reason), `Booking #${b.bookingId} cancelled`);
    }
    case 'noshow': {
      const reason = await promptDialog({
        title: `Mark booking #${b.bookingId} as a no-show?`,
        text: `${u?.fullName || 'The customer'} did not collect ${v?.model || 'the car'} (due ${fmtDate(b.pickupDate)}). The booking closes, the car is freed, and one night is kept as a charge.`,
        label: 'Note (optional)',
        placeholder: 'e.g. Called twice, no answer',
        confirmLabel: 'Mark no-show',
        tone: 'danger',
      });
      if (reason === null) return false;
      return runAction(null, () => api.bookings.noShow(b.bookingId, reason), `Booking #${b.bookingId} closed as a no-show`);
    }
    case 'sheet': {
      // The full record (phone, licence number) is an audited lookup.
      const [person, payment] = await Promise.all([
        api.users.get(b.customerId).catch(() => u),
        state.payments?.get(b.bookingId) || api.bookings.payment(b.bookingId).catch(() => null),
      ]);
      printHandoverSheet(btn.dataset.kind, b, v, person, state.branches?.get(b.pickupBranchId), payment);
      return false;
    }
    case 'pickup': {
      // The backend refuses an unpaid handover, so show the balance up front
      // rather than letting staff fill in the whole form and then fail.
      const payment = await api.bookings.payment(b.bookingId).catch(() => null);
      const ok = await pickupDialog(b, v, u, payment);
      if (ok) toast(`${v?.model || 'Vehicle'} handed over — trip started`, 'success');
      return Boolean(ok);
    }
    case 'return': {
      const ok = await returnDialog(b, v, u);
      if (ok) toast(`Trip #${b.bookingId} closed`, 'success');
      return Boolean(ok);
    }
    case 'details':
      await bookingDetails(b, v, u, state);
      return false;
    default:
      return false;
  }
}

async function bookingDetails(b, v, u, state) {
  const [handovers, payment, history] = await Promise.all([
    api.handovers.byBooking(b.bookingId).catch(() => []),
    Promise.resolve(state.payments.get(b.bookingId)),
    api.audit.history('BOOKING', b.bookingId).catch(() => []),
  ]);
  const staff = (id) => state.users.get(id)?.fullName || (id ? `Staff #${id}` : '—');
  await openDialog({
    title: `Booking #${b.bookingId}`,
    subtitle: `Submitted ${fmtDateTime(b.submittedDate)}`,
    wide: true,
    hideSubmit: true,
    cancelLabel: 'Close',
    body: `
      <div style="display:flex;justify-content:space-between;gap:16px;align-items:center;flex-wrap:wrap">
        ${vehicleCell(v, b.vehicleId)}
        ${statusChip(b.status)}
      </div>
      <div class="kv-grid">
        <div class="kv"><span>Customer</span><b>${esc(u?.fullName || `#${b.customerId}`)}</b></div>
        <div class="kv"><span>Email</span><b>${esc(u?.email || '—')}</b></div>
        <div class="kv"><span>Branch</span><b>${esc(state.branches.get(b.pickupBranchId)?.name || '—')}</b></div>
        <div class="kv"><span>Pick-up</span><b>${fmtDate(b.pickupDate)}</b></div>
        <div class="kv"><span>Return</span><b>${fmtDate(b.returnDate)}${b.actualReturnDate && b.actualReturnDate !== b.returnDate ? `<small>back ${fmtDate(b.actualReturnDate)}</small>` : ''}</b></div>
        <div class="kv"><span>Quoted</span><b>${money(b.estimatedCost)}</b></div>
        <div class="kv"><span>Final total</span><b>${b.finalCost != null ? money(b.finalCost) : '&mdash;'}</b></div>
        <div class="kv"><span>Payment</span><b>${payment ? esc(humanize(payment.status)) : '&mdash;'}</b></div>
        <div class="kv"><span>Approved by</span><b>${esc(b.approvedBy ? staff(b.approvedBy) : '&mdash;')}</b></div>
      </div>
      ${b.specialRequests ? `<div class="notice">${icon('info', 18)}<span>${esc(b.specialRequests)}</span></div>` : ''}
      <div>
        <p class="eyebrow" style="margin-bottom:10px">Handovers</p>
        ${handovers.length ? `<div style="display:grid;gap:8px">${handovers.map((h) => `
          <div class="notice" style="justify-content:space-between;flex-wrap:wrap">
            <span><b style="color:var(--ink)">${esc(humanize(h.handoverType))}</b> · ${fmtDateTime(h.handoverDate)} · ${esc(staff(h.processedByStaffId))}</span>
            <span>${number(h.mileageAtEvent)} km · fuel ${esc(h.fuelLevel || '—')}</span>
            ${h.conditionNotes ? `<span style="width:100%">${esc(h.conditionNotes)}</span>` : ''}
          </div>`).join('')}</div>` : '<p class="muted">No handovers recorded yet.</p>'}
      </div>
      <div>
        <p class="eyebrow" style="margin-bottom:10px">History</p>
        ${history.length ? `<ol class="trail">${history.map((a) => `
          <li>
            <b>${esc(a.toStatus ? humanize(a.toStatus) : humanize(a.action))}</b>
            <span>${fmtDateTime(a.changedAt)} · ${esc(a.actorUserId ? staff(a.actorUserId) : 'System')}</span>
            ${a.reason ? `<em>${esc(a.reason)}</em>` : ''}
          </li>`).join('')}</ol>` : '<p class="muted">Nothing recorded yet.</p>'}
      </div>`,
  });
}

// ==================================================================
// Handovers
// ==================================================================
export async function renderHandovers({ el }) {
  const state = { bookings: [], fleet: new Map(), users: new Map(), branches: new Map(), payments: new Map(), handovers: [] };

  el.innerHTML = `
    ${consoleHead({ eyebrow: 'Front desk', title: 'Handovers', text: 'Vehicles ready to leave, and vehicles out on the road.' })}
    <div id="handover-body">${skeletons(3, 'fleet-grid')}</div>`;

  async function load() {
    invalidate('fleet');
    const [bookings, handovers, fleet, users, branches] = await Promise.all([
      api.bookings.all(), api.handovers.active(), fleetMap(), userMap(), branchMap(),
    ]);
    Object.assign(state, { bookings, handovers, fleet, users, branches });
    draw();
  }

  function card(b, mode, i) {
    const v = state.fleet.get(b.vehicleId);
    const u = state.users.get(b.customerId);
    const pickup = state.handovers.find((h) => h.bookingId === b.bookingId && h.handoverType === 'PICKUP');
    const overdue = mode === 'out' && b.returnDate < isoDate();
    return `<article class="fcard" style="animation-delay:${i * 40}ms">
      <div class="fcard__row"><span class="id-pill">#${b.bookingId}</span>${overdue ? '<span class="status status--red">Overdue</span>' : statusChip(b.status)}</div>
      <div class="fcard__art">${v ? vehicleVisual(v) : ''}</div>
      <div>
        <h3>${esc(v?.model || `Vehicle #${b.vehicleId}`)}</h3>
        <p class="fcard__meta">${esc(v?.plateNumber || '')} · ${esc(state.branches.get(b.pickupBranchId)?.name || '')}</p>
      </div>
      <div class="notice" style="padding:10px 12px">
        ${icon('user', 18)}
        <span><b style="color:var(--ink)">${esc(u?.fullName || `Customer #${b.customerId}`)}</b><br>
        ${mode === 'out' && pickup
          ? `Out since ${fmtDateTime(pickup.handoverDate)} · ${number(pickup.mileageAtEvent)} km`
          : `${fmtDate(b.pickupDate)} → ${fmtDate(b.returnDate)}`}</span>
      </div>
      <div class="fcard__actions">
        ${mode === 'ready'
          ? `<button class="btn btn--block" data-booking="pickup" data-id="${b.bookingId}">${icon('key', 18)} Hand over</button>`
          : `<button class="btn btn--block btn--accent" data-booking="return" data-id="${b.bookingId}">${icon('flag', 18)} Receive · due ${fmtDate(b.returnDate)}</button>`}
        <button class="icon-btn" data-booking="sheet" data-kind="${mode === 'ready' ? 'pickup' : 'return'}" data-id="${b.bookingId}" aria-label="Print ${mode === 'ready' ? 'pick-up' : 'return'} sheet" title="Print sheet">${icon('printer', 18)}</button>
      </div>
    </article>`;
  }

  function draw() {
    const ready = state.bookings.filter((b) => b.status === 'APPROVED').sort((a, b) => a.pickupDate.localeCompare(b.pickupDate));
    const out = state.bookings.filter((b) => b.status === 'ACTIVE_RENTAL').sort((a, b) => a.returnDate.localeCompare(b.returnDate));
    $('#handover-body', el).innerHTML = `
      <section>
        <div class="section-head" style="margin-bottom:14px"><h2 class="title-md">Ready for pick-up <span class="muted">· ${ready.length}</span></h2></div>
        ${ready.length ? `<div class="fleet-grid">${ready.map((b, i) => card(b, 'ready', i)).join('')}</div>`
          : empty({ icon: 'key', title: 'No approved bookings waiting', text: 'Approved requests appear here until the customer collects the vehicle.' })}
      </section>
      <section class="section" style="margin-top:36px">
        <div class="section-head" style="margin-bottom:14px"><h2 class="title-md">On the road <span class="muted">· ${out.length}</span></h2></div>
        ${out.length ? `<div class="fleet-grid">${out.map((b, i) => card(b, 'out', i)).join('')}</div>`
          : empty({ icon: 'route', title: 'No vehicles out', text: 'Active rentals show here until they are returned.' })}
      </section>`;
  }

  el.addEventListener('click', async (e) => {
    const btn = e.target.closest('[data-booking]');
    if (!btn) return;
    const b = state.bookings.find((x) => x.bookingId === Number(btn.dataset.id));
    if (b && await handleBookingAction(btn, b, state)) await load();
  });

  await load();
}

// ==================================================================
// Payments
// ==================================================================
const PAY_TABS = [['PENDING', 'Pending'], ['PAID', 'Paid'], ['REFUNDED', 'Refunds'], ['CANCELLED', 'Not charged'], ['all', 'All']];

export async function renderPayments({ el, query }) {
  const state = {
    tab: PAY_TABS.some(([k]) => k === query.tab) ? query.tab : 'PENDING',
    text: '', page: 1, data: null, payments: [], bookings: new Map(), users: new Map(), fleet: new Map(),
  };

  el.innerHTML = `
    ${consoleHead({ eyebrow: 'Billing', title: 'Payments', text: 'Mark payments as received at the counter.', actions: exportButtons() })}
    <div class="kpi-grid" id="pay-kpis" style="margin-bottom:22px"></div>
    <div class="toolbar"><div id="tabs"></div>${searchBox('Search booking, customer or vehicle')}</div>
    <div id="list">${skeletons(1, '')}</div>
    <div id="pager"></div>`;

  const query_ = () => ({ status: state.tab, q: state.text.trim() || undefined });

  // The totals come from the dashboard, so nothing downloads every payment
  // just to add them up (C1).
  async function loadKpis() {
    const d = await api.dashboard().catch(() => null);
    if (!d) return;
    $('#pay-kpis', el).innerHTML = `
      <div class="kpi kpi--dark"><div class="kpi__top"><span class="kpi__label">${icon('wallet', 18)} Collected</span></div><div class="kpi__value" style="font-size:30px">${money(d.totalRevenuePaid)}</div></div>
      <div class="kpi"><div class="kpi__top"><span class="kpi__label">${icon('clock', 18)} Outstanding</span></div><div class="kpi__value" style="font-size:30px">${money(d.outstandingPayments)}</div></div>
      <div class="kpi"><div class="kpi__top"><span class="kpi__label">${icon('receipt', 18)} Unpaid, due out</span></div><div class="kpi__value">${number(d.unpaidReadyForPickup || 0)}</div></div>`;
  }

  async function load() {
    const [data, users, fleet] = await Promise.all([
      api.payments.page({ ...query_(), page: state.page, size: PAGE_SIZE }), userMap(), fleetMap(),
    ]);
    if (data.page > data.pages && state.page > 1) { state.page = data.pages; return load(); }
    const bookings = await Promise.all(data.items.map((x) => api.bookings.get(x.bookingId).catch(() => null)));
    Object.assign(state, {
      data, payments: data.items, users, fleet,
      bookings: new Map(bookings.filter(Boolean).map((b) => [b.bookingId, b])),
    });
    draw();
  }

  const live = (p) => {
    const b = state.bookings.get(p.bookingId);
    return b && !['CANCELLED', 'REJECTED', 'NO_SHOW'].includes(b.status);
  };

  function draw() {
    const counts = state.data?.counts || {};
    $('#tabs', el).innerHTML = segmented(PAY_TABS.map(([k, l]) => [k, l, counts[k] || 0]), state.tab);
    $('#pager', el).innerHTML = pager(state.data);

    const list = state.payments;
    const host = $('#list', el);
    if (!list.length) {
      host.innerHTML = empty({ icon: 'wallet', title: state.text ? 'No matches' : 'No payments here', text: 'A payment record is created automatically with every booking.' });
      return;
    }
    host.innerHTML = dataList({
      cols: '80px minmax(140px,1.2fr) minmax(165px,1.3fr) minmax(105px,.9fr) minmax(130px,1fr) minmax(180px,auto)',
      headers: ['Booking', 'Customer', 'Vehicle', 'Amount', 'Last update', ''],
      rows: list.map((p, i) => {
        const b = state.bookings.get(p.bookingId);
        const v = b && state.fleet.get(b.vehicleId);
        const u = b && state.users.get(b.customerId);
        return row([
          cell('Booking', `<span class="id-pill">#${p.bookingId}</span>${b ? `<small style="margin-top:4px">${esc(humanize(b.status))}</small>` : ''}`),
          cell('Customer', personCell(u, b?.customerId)),
          cell('Vehicle', b ? vehicleCell(v, b.vehicleId) : '—', 'cell--wide'),
          cell('Amount', `<b>${money(p.amount, { decimals: true })}</b>${p.refundAmount != null ? `<small>Refund ${money(p.refundAmount, { decimals: true })}</small>` : p.note ? `<small>${esc(p.note)}</small>` : ''}`),
          cell('Last update', `<b>${fmtDateTime(p.updatedAt)}</b><small>${p.updatedBy ? esc(state.users.get(p.updatedBy)?.fullName || `Staff #${p.updatedBy}`) : 'System'}</small>`),
          actionsCell(payActions(p, b, live)),
        ], i);
      }),
    });
  }

  async function exportRows(kind) {
    const all = await fetchAllPages(api.payments.page, query_());
    const label = PAY_TABS.find(([k]) => k === state.tab)?.[1] || 'All';
    if (kind === 'csv') {
      downloadCsv(`payments-${state.tab.toLowerCase()}`,
        ['Payment', 'Booking', 'Status', 'Amount', 'Refund', 'Note', 'Updated', 'Updated by'],
        all.map((p) => [p.paymentId, p.bookingId, p.status, p.amount, p.refundAmount, p.note, p.updatedAt,
          p.updatedBy ? state.users.get(p.updatedBy)?.fullName || `#${p.updatedBy}` : 'System']));
    } else {
      const total = all.reduce((s, p) => s + Number(p.amount || 0), 0);
      printTable(`Payments · ${label}`, `${all.length} payment${all.length === 1 ? '' : 's'} · ${money(total, { decimals: true })}`,
        ['Booking', 'Status', 'Amount', 'Refund / note', 'Updated'],
        all.map((p) => [`#${p.bookingId}`, esc(humanize(p.status)), money(p.amount, { decimals: true }),
          p.refundAmount != null ? money(p.refundAmount, { decimals: true }) : esc(p.note || ''), fmtDateTime(p.updatedAt)]));
    }
  }

  el.addEventListener('click', async (e) => {
    const tab = e.target.closest('[data-seg]');
    if (tab) { state.tab = tab.dataset.seg; state.page = 1; history.replaceState(null, '', `#/console/payments?tab=${state.tab}`); await load(); return; }
    const pg = e.target.closest('[data-page]');
    if (pg) { state.page = Number(pg.dataset.page); await load(); return; }
    const exp = e.target.closest('[data-export]');
    if (exp) {
      exp.disabled = true;
      try { await exportRows(exp.dataset.export); } catch (err) { toast(err.message || 'Export failed', 'error'); }
      exp.disabled = false;
      return;
    }
    const btn = e.target.closest('[data-pay]');
    if (!btn) return;
    const id = Number(btn.dataset.id);
    const paid = btn.dataset.pay === 'paid';
    let reason = null;
    if (!paid) {
      // Reversing a payment is an exception. The audit trail records who did it
      // and why, so the reason is required.
      reason = await promptDialog({
        title: 'Move back to pending?',
        text: 'Use this only if the payment was marked by mistake.',
        label: 'Reason (required)',
        placeholder: 'e.g. Marked paid on the wrong booking',
        confirmLabel: 'Mark pending',
        required: true,
      });
      if (!reason) return;
    }
    const ok = await runAction(paid ? btn : null,
      () => (paid ? api.payments.markPaid(id) : api.payments.markPending(id, reason)),
      paid ? 'Payment recorded' : 'Payment moved back to pending');
    if (ok) { await load(); loadKpis(); }
  });
  el.addEventListener('input', debounce((e) => {
    if (e.target.matches('[data-search]')) { state.text = e.target.value; state.page = 1; load(); }
  }, 250));

  loadKpis();
  await load();
}
