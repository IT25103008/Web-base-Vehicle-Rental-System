// ============================================================
// Customer screens: My trips, Notifications (all roles).
// ============================================================
import { api } from '../api.js';
import { store, branchMap, getVehicle, isCustomer, refreshUnread } from '../store.js';
import { paintBadge } from '../app.js';
import { tripClock } from './extras.js';
import {
  $, esc, icon, money, fmtDate, fmtShortDate, relativeTime, rentalDays, addDays, isoDate, humanize,
  statusChip, vehicleVisual, field, openDialog, confirmDialog, runAction, toast, empty, skeletons,
} from '../ui.js';

const UPCOMING = ['PENDING_APPROVAL', 'APPROVED', 'ACTIVE_RENTAL'];
const STEPS = ['Requested', 'Approved', 'Picked up', 'Returned'];
const DONE_COUNT = { PENDING_APPROVAL: 1, APPROVED: 2, ACTIVE_RENTAL: 3, COMPLETED: 4 };

// ==================================================================
// My trips
// ==================================================================
export async function renderTrips({ el, query }) {
  const state = { filter: ['upcoming', 'past', 'all'].includes(query.filter) ? query.filter : 'upcoming', trips: [] };

  el.innerHTML = `
    <div class="section-head" style="margin-top:24px">
      <div>
        <p class="eyebrow">Your reservations</p>
        <h1 class="display" style="font-size:clamp(36px,5vw,64px);margin-top:12px">My trips</h1>
        <p id="trip-summary">Loading your trips…</p>
      </div>
      <div class="console-head__actions">
        <div class="seg" id="trip-filter" role="tablist"></div>
        <a class="btn" href="#/explore">${icon('plus', 18)} New trip</a>
      </div>
    </div>
    <div id="trips">${skeletons(3, 'trip-list')}</div>`;

  async function load() {
    const [bookings, branches] = await Promise.all([api.bookings.mine(), branchMap().catch(() => new Map())]);
    const vehicleIds = [...new Set(bookings.map((b) => b.vehicleId))];
    const vehicles = new Map(await Promise.all(vehicleIds.map(async (id) => [id, await getVehicle(id).catch(() => null)])));
    const payments = await Promise.all(bookings.map((b) => api.bookings.payment(b.bookingId).catch(() => null)));
    state.trips = bookings.map((b, i) => ({
      booking: b,
      vehicle: vehicles.get(b.vehicleId),
      branch: branches.get(b.pickupBranchId),
      payment: payments[i],
    }));
    draw();
  }

  function draw() {
    const upcoming = state.trips.filter((t) => UPCOMING.includes(t.booking.status));
    const past = state.trips.filter((t) => !UPCOMING.includes(t.booking.status));
    const due = state.trips
      .filter((t) => t.payment && t.payment.status === 'PENDING'
        && ['PENDING_APPROVAL', 'APPROVED', 'ACTIVE_RENTAL', 'COMPLETED'].includes(t.booking.status))
      .reduce((sum, t) => sum + Number(t.payment.amount || 0), 0);

    $('#trip-summary', el).textContent = state.trips.length
      ? `${upcoming.length} upcoming · ${past.length} past${due > 0 ? ` · ${money(due)} to settle at the counter` : ''}`
      : 'No trips yet.';

    $('#trip-filter', el).innerHTML = [
      ['upcoming', 'Upcoming', upcoming.length],
      ['past', 'Past', past.length],
      ['all', 'All', state.trips.length],
    ].map(([key, label, count]) => `<button role="tab" data-filter="${key}" class="${state.filter === key ? 'is-active' : ''}" aria-selected="${state.filter === key}">${label} <span class="count">${count}</span></button>`).join('');

    const list = state.filter === 'upcoming' ? upcoming : state.filter === 'past' ? past : state.trips;
    const host = $('#trips', el);
    if (!list.length) {
      host.innerHTML = empty({
        icon: 'route',
        title: state.filter === 'past' ? 'No past trips' : 'No trips planned',
        text: 'Find a vehicle for your dates and send a request — it shows up here straight away.',
        action: '<a class="btn" href="#/explore">Explore vehicles</a>',
      });
      return;
    }
    host.innerHTML = `<div class="trip-list">${list.map(tripCard).join('')}</div>`;
  }

  el.addEventListener('click', async (e) => {
    const filterBtn = e.target.closest('[data-filter]');
    if (filterBtn) {
      state.filter = filterBtn.dataset.filter;
      history.replaceState(null, '', `#/trips?filter=${state.filter}`);
      draw();
      return;
    }
    const action = e.target.closest('[data-trip-action]');
    if (!action) return;
    const trip = state.trips.find((t) => String(t.booking.bookingId) === action.dataset.id);
    if (!trip) return;

    if (action.dataset.tripAction === 'cancel') {
      const paid = trip.payment && trip.payment.status === 'PAID';
      const ok = await confirmDialog({
        title: `Cancel trip #${trip.booking.bookingId}?`,
        text: `${trip.vehicle?.model || 'This vehicle'} · ${fmtDate(trip.booking.pickupDate)} → ${fmtDate(trip.booking.returnDate)}. `
          + (paid ? 'Your payment will be marked for refund. ' : 'Nothing will be charged. ')
          + "This can't be undone.",
        confirmLabel: 'Cancel trip',
        tone: 'danger',
      });
      if (!ok) return;
      if (await runAction(null, () => api.bookings.cancel(trip.booking.bookingId, 'Cancelled by the customer'), 'Trip cancelled')) await load();
    }

    if (action.dataset.tripAction === 'dates') {
      const changed = await changeDatesDialog(trip);
      if (changed) { toast('Dates updated', 'success'); await load(); }
    }
  });

  await load();
}

// What the number under the price actually means, now that a trip can end up
// costing more (late return, damage) or less (insurance paid part of it).
function priceNote(b, payment) {
  if (!payment) return b.finalCost != null ? 'Final total' : 'Estimated total';
  switch (payment.status) {
    case 'PAID': return 'Paid in full';
    case 'REFUNDED': return 'Refund being processed';
    case 'CANCELLED': return 'Not charged';
    default: return b.finalCost != null ? 'Balance to settle' : 'Payable at the counter';
  }
}

function tripCard({ booking: b, vehicle: v, branch, payment }, index) {
  const nights = rentalDays(b.pickupDate, b.returnDate);
  const closed = b.status === 'REJECTED' || b.status === 'CANCELLED' || b.status === 'NO_SHOW';
  const done = DONE_COUNT[b.status] ?? 0;

  const progress = closed
    ? `<div class="notice ${b.status === 'REJECTED' ? 'notice--red' : ''}">${icon(b.status === 'REJECTED' ? 'alert' : 'info', 18)}<span>${b.status === 'REJECTED' ? 'This request was declined by the branch. You can request another vehicle for these dates.' : b.status === 'NO_SHOW' ? 'The car was not collected, so this booking was closed. See the trip for any charge.' : 'This trip was cancelled.'}</span></div>`
    : `<div class="stepper" aria-label="Progress">${STEPS.map((label, i) => `
        <div class="stepper__step${i < done ? ' is-done' : ''}${i === done ? ' is-current' : ''}">
          <span class="stepper__dot">${icon('check', 12)}</span><span class="stepper__label">${label}</span>
          <span class="stepper__bar"></span>
        </div>`).join('')}</div>`;

  const actions = [
    `<a class="btn btn--sm" href="#/trip/${b.bookingId}">Details ${icon('arrowRight', 15)}</a>`,
    b.status === 'PENDING_APPROVAL' ? `<button class="btn btn--sm btn--tonal" data-trip-action="dates" data-id="${b.bookingId}">${icon('calendar', 16)} Change dates</button>` : '',
    ['PENDING_APPROVAL', 'APPROVED'].includes(b.status) && b.pickupDate > isoDate()
      ? `<button class="btn btn--sm btn--outline" data-trip-action="cancel" data-id="${b.bookingId}">Cancel</button>`
      : ['PENDING_APPROVAL', 'APPROVED'].includes(b.status)
        ? `<span class="muted" style="font-size:12px">Call the branch to cancel</span>`
        : '',
    v ? `<a class="btn btn--sm btn--text" href="#/vehicle/${v.vehicleId}">View vehicle</a>` : '',
  ].join('');

  return `<article class="trip" style="animation-delay:${Math.min(index, 10) * 50}ms">
    <div class="trip__art">${v ? vehicleVisual(v) : ''}</div>
    <div class="trip__body">
      <div class="trip__title">
        <span class="id-pill">#${b.bookingId}</span>
        <h3><a class="trip__link" href="#/trip/${b.bookingId}">${esc(v?.model || `Vehicle ${b.vehicleId}`)}</a></h3>
        ${statusChip(b.status)}
      </div>
      <div class="trip__facts">
        <span>${icon('calendar', 16)} ${fmtDate(b.pickupDate)} → ${fmtDate(b.returnDate)} · ${nights} night${nights === 1 ? '' : 's'}</span>
        <span>${icon('pin', 16)} ${esc(branch?.name || 'Branch')}</span>
        ${v ? `<span>${icon('car', 16)} ${esc(v.plateNumber)}</span>` : ''}
        ${UPCOMING.includes(b.status) ? (() => { const c = tripClock(b); return `<span class="trip__clock">${icon('clock', 16)} <b>${esc(c.big)}</b> ${esc(c.unit)}</span>`; })() : ''}
      </div>
      ${progress}
      ${b.specialRequests ? `<p class="muted" style="font-size:13px">“${esc(b.specialRequests)}”</p>` : ''}
    </div>
    <div class="trip__side">
      <div class="price">
        <b>${money(b.finalCost != null ? b.finalCost : b.estimatedCost)}</b>
        <small>${priceNote(b, payment)}</small>
      </div>
      ${payment ? statusChip(payment.status) : ''}
      ${b.finalCost != null && Number(b.finalCost) !== Number(b.estimatedCost)
        ? `<span class="muted" style="font-size:11px">Quoted ${money(b.estimatedCost)}${b.actualReturnDate && b.actualReturnDate > b.returnDate ? ' · returned late' : ''}</span>`
        : ''}
      <div class="trip__actions">${actions}</div>
    </div>
  </article>`;
}

export function changeDatesDialog({ booking: b, vehicle: v }) {
  const today = isoDate();
  const pickup = b.pickupDate < today ? today : b.pickupDate;
  return openDialog({
    title: 'Change trip dates',
    subtitle: `${v?.model || 'Vehicle'} · request #${b.bookingId}`,
    submitLabel: 'Update dates',
    body: `
      <div class="form-grid">
        ${field({ name: 'pickup', label: 'Pick-up', type: 'date', value: pickup, min: today, required: true })}
        ${field({ name: 'return', label: 'Return', type: 'date', value: b.returnDate > pickup ? b.returnDate : addDays(1, new Date(`${pickup}T00:00:00`)), min: addDays(1, new Date(`${pickup}T00:00:00`)), required: true })}
      </div>
      <div class="quote" id="dates-quote"></div>`,
    onOpen(form) {
      const q = form.querySelector('#dates-quote');
      const paint = () => {
        const p = form.elements.pickup.value;
        const r = form.elements.return.value;
        if (!p || !r || r <= p || !v) { q.innerHTML = '<div class="quote__row"><span>Return must be after pick-up</span></div>'; return; }
        const n = rentalDays(p, r);
        q.innerHTML = `<div class="quote__row quote__total"><span>New estimate · ${n} night${n === 1 ? '' : 's'}</span><b>${money(Number(v.rentalPricePerDay) * n)}</b></div>`;
      };
      form.elements.pickup.addEventListener('change', () => {
        const min = addDays(1, new Date(`${form.elements.pickup.value}T00:00:00`));
        form.elements.return.min = min;
        if (form.elements.return.value < min) form.elements.return.value = min;
        paint();
      });
      form.elements.return.addEventListener('change', paint);
      paint();
    },
    async onSubmit(values) {
      if (values.return <= values.pickup) throw new Error('Return date must be after the pick-up date.');
      await api.bookings.changeDates(b.bookingId, values.pickup, values.return);
    },
  });
}

// ==================================================================
// Notifications
// ==================================================================
const NOTIF_ICON = (type = '') => {
  if (type.startsWith('BOOKING_APPROVED')) return 'check';
  if (type.startsWith('BOOKING_REJECTED')) return 'alert';
  if (type.startsWith('BOOKING')) return 'ticket';
  if (type.startsWith('VEHICLE')) return 'key';
  return 'bell';
};

function dayLabel(value) {
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return 'Earlier';
  const start = (x) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const diff = Math.round((start(new Date()) - start(d)) / 86400000);
  if (diff <= 0) return 'Today';
  if (diff === 1) return 'Yesterday';
  if (diff < 7) return d.toLocaleDateString('en-GB', { weekday: 'long' });
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: d.getFullYear() === new Date().getFullYear() ? undefined : 'numeric' });
}

export async function renderNotifications({ el }) {
  const state = { items: [], filter: 'all' };
  el.innerHTML = `
    <div class="section-head" style="margin-top:24px;max-width:820px">
      <div>
        <p class="eyebrow">Inbox</p>
        <h1 class="display" style="font-size:clamp(36px,5vw,64px);margin-top:12px">Updates</h1>
        <p id="notif-summary">Loading…</p>
      </div>
      <div class="console-head__actions">
        <div class="seg" id="notif-filter"></div>
        <button class="btn btn--tonal" id="mark-all" hidden>${icon('check', 18)} Mark all read</button>
      </div>
    </div>
    <div id="notifs">${skeletons(3, 'notif-list')}</div>`;

  async function load() {
    state.items = await api.notifications.mine();
    draw();
  }

  function draw() {
    const unread = state.items.filter((n) => !n.readStatus);
    store.unread = unread.length;
    paintBadge();
    $('#notif-summary', el).textContent = unread.length
      ? `${unread.length} unread of ${state.items.length}`
      : state.items.length ? "You're all caught up." : 'Nothing here yet.';
    $('#mark-all', el).hidden = unread.length === 0;
    $('#notif-filter', el).innerHTML = [['all', 'All', state.items.length], ['unread', 'Unread', unread.length]]
      .map(([k, l, c]) => `<button data-nfilter="${k}" class="${state.filter === k ? 'is-active' : ''}">${l} <span class="count">${c}</span></button>`).join('');

    const list = state.filter === 'unread' ? unread : state.items;
    const host = $('#notifs', el);
    if (!list.length) {
      host.innerHTML = `<div style="max-width:820px">${empty({
        icon: 'bell',
        title: state.filter === 'unread' ? 'No unread updates' : 'No updates yet',
        text: isCustomer() ? 'Booking approvals, pick-ups and returns will appear here.' : 'Account activity will appear here.',
      })}</div>`;
      return;
    }
    let lastDay = '';
    host.innerHTML = `<div class="notif-list">${list.map((n, i) => {
      const day = dayLabel(n.sentAt);
      const head = day !== lastDay ? `<p class="notif-day">${esc(day)}</p>` : '';
      lastDay = day;
      // The server records what each update is about; old ones fall back to the wording.
      const trip = !isCustomer() ? null
        : n.entityType === 'BOOKING' ? n.entityId : /booking #(\d+)/i.exec(n.message || '')?.[1];
      return `${head}
      <article class="notif${n.readStatus ? '' : ' is-unread'}" style="animation-delay:${Math.min(i, 10) * 35}ms">
        <span class="notif__icon">${icon(NOTIF_ICON(n.type), 20)}</span>
        <div class="notif__body">
          <b>${esc(humanize(n.type || 'Update'))}</b>
          <p>${esc(n.message)}</p>
          <time datetime="${esc(n.sentAt)}">${esc(relativeTime(n.sentAt))} · ${esc(fmtShortDate(n.sentAt))}</time>
        </div>
        <div class="notif__acts">
          ${trip ? `<a class="btn btn--sm btn--tonal" href="#/trip/${trip}">View trip</a>` : ''}
          ${n.readStatus ? '' : `<button class="btn btn--sm btn--text" data-read="${n.notificationId}">Mark read</button>`}
          <button class="icon-btn icon-btn--ghost icon-btn--sm" type="button" data-delete="${n.notificationId}" aria-label="Delete this update" title="Delete">${icon('trash', 15)}</button>
        </div>
      </article>`;
    }).join('')}</div>`;
  }

  el.addEventListener('click', async (e) => {
    const f = e.target.closest('[data-nfilter]');
    if (f) { state.filter = f.dataset.nfilter; draw(); return; }

    const one = e.target.closest('[data-read]');
    if (one) {
      const id = Number(one.dataset.read);
      if (await runAction(one, () => api.notifications.markRead(id))) {
        const item = state.items.find((n) => n.notificationId === id);
        if (item) item.readStatus = true;
        draw();
      }
      return;
    }

    const gone = e.target.closest('[data-delete]');
    if (gone) {
      const id = Number(gone.dataset.delete);
      if (await runAction(gone, () => api.notifications.remove(id))) {
        state.items = state.items.filter((n) => n.notificationId !== id);
        draw();
        refreshUnread().then(paintBadge);
      }
      return;
    }

    const all = e.target.closest('#mark-all');
    if (all) {
      const unread = state.items.filter((n) => !n.readStatus);
      const ok = await runAction(all, () => api.notifications.markAllRead(), 'All caught up');
      if (ok) {
        unread.forEach((n) => { n.readStatus = true; });
        draw();
        refreshUnread().then(paintBadge);
      }
    }
  });

  await load();
}
