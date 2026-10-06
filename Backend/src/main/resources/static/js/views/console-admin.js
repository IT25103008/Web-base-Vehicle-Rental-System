// ============================================================
// Console: people, branches, transfers, insurance policies.
// ============================================================
import { api } from '../api.js';
import { DISTRICTS } from '../rules.js';
import { store, getBranches, getFleet, fleetMap, invalidate, isAdmin } from '../store.js';
import {
  $, esc, icon, fmtDate, fmtTime, isoDate, initials, statusChip,
  field, openDialog, confirmDialog, promptDialog, runAction, toast, empty, skeletons, debounce,
} from '../ui.js';
import {
  consoleHead, segmented, searchBox, dataList, row, cell, actionsCell, vehicleCell,
  vehicleOptions, branchSelectOptions, filterByText, pager, fetchAllPages, downloadCsv, exportButtons, printTable,
} from './console-kit.js';

/** Sri Lanka's 25 districts. A saved value that is not one of them stays visible, marked, so it can be corrected. */
function districtOptions(current) {
  const opts = [{ value: '', label: 'Choose a district' }, ...DISTRICTS.map((d) => ({ value: d, label: d }))];
  if (current && !DISTRICTS.includes(current)) {
    opts.splice(1, 0, { value: current, label: `${current} (not a district - choose one)` });
  }
  return opts;
}

const ROLE_TABS = [['all', 'Everyone'], ['CUSTOMER', 'Customers'], ['STAFF', 'Staff'], ['ADMINISTRATOR', 'Administrators']];

// ==================================================================
// People
// ==================================================================
/** Licence state, which is what decides whether a customer can book at all. */
function licenceCell(u) {
  if (u.role !== 'CUSTOMER') return '<span class="muted">&mdash;</span>';
  const expired = u.licenseExpiryDate && u.licenseExpiryDate <= isoDate();
  if (expired) {
    return `<b style="color:var(--danger)">Expired</b><small>${esc(u.licenseExpiryDate)}</small>`;
  }
  return u.licenseVerified
    ? `<b>Verified</b><small>${u.licenseExpiryDate ? `valid to ${esc(u.licenseExpiryDate)}` : ''}</small>`
    : '<b style="color:var(--warn-ink,#8A5200)">Not verified</b><small>cannot book yet</small>';
}

function peopleActions(u, admin) {
  const parts = [];
  if (u.role === 'CUSTOMER') {
    parts.push(u.licenseVerified
      ? `<button class="btn btn--sm btn--text" data-people="unverify" data-id="${u.userId}">Withdraw</button>`
      : `<button class="btn btn--sm btn--tonal" data-people="verify" data-id="${u.userId}">${icon('shield', 16)} Verify licence</button>`);
  }
  if (admin && u.userId !== store.user?.userId) {
    parts.push(u.active === false
      ? `<button class="btn btn--sm btn--success" data-people="enable" data-id="${u.userId}">Enable</button>`
      : `<button class="btn btn--sm btn--outline" data-people="disable" data-id="${u.userId}">Disable</button>`);
  }
  return parts.join('');
}

export async function renderPeople({ el, query }) {
  const admin = isAdmin();
  const state = { tab: ROLE_TABS.some(([k]) => k === query.tab) ? query.tab : 'CUSTOMER', text: '', page: 1, data: null, users: [] };

  el.innerHTML = `
    ${consoleHead({
      eyebrow: 'Accounts', title: 'People',
      text: 'Customers, branch staff and administrators.',
      actions: admin
        ? `<button class="btn btn--tonal" data-people="add-admin">${icon('shield', 18)} Add administrator</button>
           <button class="btn" data-people="add">${icon('plus', 18)} Add staff member</button>${exportButtons()}`
        : exportButtons(),
    })}
    <div class="toolbar"><div id="tabs"></div>${searchBox('Search name or email')}</div>
    <div id="list">${skeletons(1, '')}</div>
    <div id="pager"></div>`;

  const query_ = () => ({ role: state.tab, q: state.text.trim() || undefined });

  // One page at a time from the server (C1).
  async function load() {
    invalidate('users');
    const data = await api.users.page({ ...query_(), page: state.page, size: 25 });
    if (data.page > data.pages && state.page > 1) { state.page = data.pages; return load(); }
    Object.assign(state, { data, users: data.items });
    draw();
  }

  async function exportRows(kind) {
    const all = await fetchAllPages(api.users.page, query_());
    const label = ROLE_TABS.find(([k]) => k === state.tab)?.[1] || 'Everyone';
    if (kind === 'csv') {
      downloadCsv(`people-${state.tab.toLowerCase()}`,
        ['ID', 'Name', 'Email', 'Role', 'Active', 'Licence verified', 'Licence expiry'],
        all.map((u) => [u.userId, u.fullName, u.email, u.role, u.active !== false ? 'yes' : 'no',
          u.role === 'CUSTOMER' ? (u.licenseVerified ? 'yes' : 'no') : '', u.licenseExpiryDate || '']));
    } else {
      printTable(`People · ${label}`, state.text ? `Matching "${state.text}"` : '',
        ['ID', 'Name', 'Email', 'Role', 'Licence'],
        all.map((u) => [`#${u.userId}`, `${esc(u.fullName)}${u.active === false ? ' (disabled)' : ''}`, esc(u.email), esc(u.role),
          u.role === 'CUSTOMER' ? (u.licenseVerified ? 'Verified' : 'Not verified') : '']));
    }
  }

  function draw() {
    const counts = state.data?.counts || {};
    $('#tabs', el).innerHTML = segmented(ROLE_TABS.map(([k, l]) => [k, l, counts[k] || 0]), state.tab);
    $('#pager', el).innerHTML = pager(state.data);
    const list = state.users;

    const host = $('#list', el);
    if (!list.length) {
      host.innerHTML = empty({ icon: 'users', title: 'Nobody here', text: state.text ? 'Try a different search.' : 'Accounts appear here once created.' });
      return;
    }
    host.innerHTML = dataList({
      cols: 'minmax(210px,1.6fr) minmax(105px,.7fr) minmax(150px,.9fr) minmax(70px,.4fr) minmax(225px,auto)',
      headers: ['Person', 'Role', 'Licence', 'ID', ''],
      rows: list.map((u, i) => row([
        cell('Person', `<div class="with-art"><span class="avatar avatar--lg" style="background:${u.role === 'CUSTOMER' ? 'var(--accent)' : 'var(--ink)'}">${esc(initials(u.fullName))}</span>
          <div><b>${esc(u.fullName)}${u.userId === store.user?.userId ? ' <span class="muted">(you)</span>' : ''}${u.active === false ? ' <span class="muted">&middot; disabled</span>' : ''}</b><small>${esc(u.email)}</small></div></div>`, 'cell--wide'),
        cell('Role', statusChip(u.role)),
        cell('Licence', licenceCell(u)),
        cell('ID', `<span class="id-pill">#${u.userId}</span>`),
        actionsCell(peopleActions(u, admin)),
      ], i)),
    });
  }

  el.addEventListener('click', async (e) => {
    const tab = e.target.closest('[data-seg]');
    if (tab) { state.tab = tab.dataset.seg; state.page = 1; history.replaceState(null, '', `#/console/people?tab=${state.tab}`); await load(); return; }
    const pg = e.target.closest('[data-page]');
    if (pg) { state.page = Number(pg.dataset.page); await load(); return; }
    const exp = e.target.closest('[data-export]');
    if (exp) {
      exp.disabled = true;
      try { await exportRows(exp.dataset.export); } catch (err) { toast(err.message || 'Export failed', 'error'); }
      exp.disabled = false;
      return;
    }
    const btn = e.target.closest('[data-people]');
    if (!btn) return;
    const target = state.users.find((x) => x.userId === Number(btn.dataset.id));

    if (btn.dataset.people === 'verify') {
      // Lists only carry the last four digits. The full number, to compare
      // against the card, comes from a single lookup that is audited.
      const full = await api.users.get(target.userId).catch(() => target);
      const expired = full?.licenseExpiryDate && full.licenseExpiryDate <= isoDate();
      const ok = await confirmDialog({
        title: 'Verify driving licence?',
        text: expired
          ? `This licence expired on ${full.licenseExpiryDate}. It cannot be verified — ask the customer to update their details first.`
          : `Check ${full?.fullName || 'this customer'}’s physical licence against the number on file: ${full?.drivingLicenseNumber || 'not on file'}${full?.licenseExpiryDate ? `, valid until ${full.licenseExpiryDate}` : ''}. Confirm only if the card matches and is valid.`,
        confirmLabel: 'Mark verified',
      });
      if (ok && await runAction(null, () => api.users.verifyLicense(target.userId), 'Licence verified')) await load();
    }
    if (btn.dataset.people === 'unverify') {
      const reason = await promptDialog({
        title: 'Withdraw licence verification?',
        text: `${target?.fullName || 'This customer'} will not be able to book until it is verified again.`,
        label: 'Reason (required)',
        placeholder: 'e.g. Licence expired, or the document could not be confirmed',
        confirmLabel: 'Withdraw',
        tone: 'danger',
        required: true,
      });
      if (reason && await runAction(null, () => api.users.unverifyLicense(target.userId, reason), 'Verification withdrawn')) await load();
    }
    if (btn.dataset.people === 'disable') {
      const reason = await promptDialog({
        title: `Disable ${target?.fullName || 'this account'}?`,
        text: 'They will not be able to sign in at all until the account is enabled again.',
        label: 'Reason (required)',
        placeholder: 'e.g. Left the company',
        confirmLabel: 'Disable account',
        tone: 'danger',
        required: true,
      });
      if (reason && await runAction(null, () => api.users.disable(target.userId, reason), 'Account disabled')) await load();
    }
    if (btn.dataset.people === 'enable') {
      if (await runAction(btn, () => api.users.enable(target.userId, 'Account re-enabled'), 'Account enabled')) await load();
    }
    if (btn.dataset.people === 'add-admin') {
      const created = await openDialog({
        title: 'Add an administrator',
        subtitle: 'Administrators manage the fleet, branches, insurance and other accounts.',
        submitLabel: 'Create account',
        wide: true,
        body: `<div class="form-grid">
          ${field({ name: 'firstName', label: 'First name', required: true })}
          ${field({ name: 'lastName', label: 'Last name', required: true })}
          ${field({ name: 'email', label: 'Work email', type: 'email', required: true })}
          ${field({ name: 'password', label: 'Temporary password', type: 'password', required: true, minlength: 8, maxlength: 72, hint: 'At least 8 characters, with letters and numbers', autocomplete: 'new-password' })}
          ${field({ name: 'adminCode', label: 'Admin code', required: true })}
          ${field({ name: 'adminLevel', label: 'Level', type: 'select', value: 'STANDARD', options: [{ value: 'STANDARD', label: 'Standard' }, { value: 'SENIOR', label: 'Senior' }] })}
          ${field({ name: 'phoneNumber', label: 'Phone', type: 'tel' })}
          ${field({ name: 'dateOfAppointment', label: 'Appointed on', type: 'date', value: isoDate(), max: isoDate() })}
        </div>`,
        onSubmit: (values) => api.users.createAdministrator(values),
      });
      if (created) { toast('Administrator created', 'success'); state.tab = 'ADMINISTRATOR'; await load(); }
    }
    if (btn.dataset.people === 'add') {
      const branches = await getBranches();
      const staff = await api.users.list('STAFF').catch(() => []);
      const ok = await openDialog({
        title: 'Add a staff member',
        subtitle: 'They can sign in straight away with this email and password.',
        submitLabel: 'Create account',
        wide: true,
        body: `<div class="form-grid">
          ${field({ name: 'firstName', label: 'First name', required: true })}
          ${field({ name: 'lastName', label: 'Last name', required: true })}
          ${field({ name: 'email', label: 'Work email', type: 'email', required: true })}
          ${field({ name: 'password', label: 'Temporary password', type: 'password', required: true, minlength: 8, maxlength: 72, hint: 'At least 8 characters, with letters and numbers', autocomplete: 'new-password' })}
          ${field({ name: 'employeeCode', label: 'Employee code', required: true })}
          ${field({ name: 'position', label: 'Position' })}
          ${field({ name: 'branchId', label: 'Branch', type: 'select', options: branchSelectOptions(branches, { placeholder: 'Select a branch', activeOnly: true }), required: true })}
          ${field({ name: 'hireDate', label: 'Hire date', type: 'date', value: isoDate(), max: isoDate() })}
          ${field({ name: 'phoneNumber', label: 'Phone', type: 'tel' })}
          ${field({ name: 'supervisorId', label: 'Supervisor (optional)', type: 'select', options: [{ value: '', label: 'No supervisor' }, ...staff.map((s) => ({ value: s.userId, label: s.fullName }))] })}
        </div>`,
        onSubmit: (values) => api.users.createStaff({
          ...values,
          branchId: Number(values.branchId),
          supervisorId: values.supervisorId ? Number(values.supervisorId) : null,
        }),
      });
      if (ok) { toast('Staff account created', 'success'); state.tab = 'STAFF'; await load(); }
    }
  });
  el.addEventListener('input', debounce((e) => {
    if (e.target.matches('[data-search]')) { state.text = e.target.value; state.page = 1; load(); }
  }, 250));

  await load();
}

// ==================================================================
// Branches (administrator)
// ==================================================================
export async function renderBranchAdmin({ el }) {
  const state = { branches: [], fleet: [] };

  el.innerHTML = `
    ${consoleHead({
      eyebrow: 'Locations', title: 'Branches',
      text: 'Opening hours, contact details and which branches take bookings.',
      actions: `<button class="btn" data-branch="add">${icon('plus', 18)} New branch</button>`,
    })}
    <div id="grid">${skeletons(3, 'branch-grid')}</div>`;

  async function load() {
    invalidate('branches', 'fleet');
    const [branches, fleet] = await Promise.all([getBranches(), getFleet()]);
    Object.assign(state, { branches, fleet });
    draw();
  }

  function draw() {
    const host = $('#grid', el);
    if (!state.branches.length) {
      host.innerHTML = empty({ icon: 'building', title: 'No branches yet', action: `<button class="btn" data-branch="add">${icon('plus', 18)} Add the first branch</button>` });
      return;
    }
    host.innerHTML = `<div class="branch-grid">${state.branches.map((b, i) => {
      const cars = state.fleet.filter((v) => v.branchId === b.branchId);
      const free = cars.filter((v) => v.status === 'AVAILABLE').length;
      return `<article class="branch-card" style="animation-delay:${i * 45}ms">
        <span class="branch-card__glyph" aria-hidden="true">${esc((b.name || '?').charAt(0))}</span>
        <div style="display:flex;gap:8px;align-items:center">${statusChip(b.status)}<span class="id-pill">#${b.branchId}</span></div>
        <h3>${esc(b.name)}</h3>
        <div class="branch-lines">
          <div>${icon('pin', 18)} <span>${esc([b.street, b.city, b.district].filter(Boolean).join(', ') || '—')}</span></div>
          <div>${icon('clock', 18)} <span>${b.openTime ? `${fmtTime(b.openTime)} – ${fmtTime(b.closeTime)}` : '—'}</span></div>
          <div>${icon('phone', 18)} <span>${esc(b.contactNumber || '—')}</span></div>
          <div>${icon('car', 18)} <span>${cars.length} vehicle${cars.length === 1 ? '' : 's'} · ${free} available</span></div>
        </div>
        <footer>
          <button class="btn btn--sm btn--tonal" data-branch="edit" data-id="${b.branchId}">${icon('edit', 16)} Edit</button>
          ${b.status === 'ACTIVE'
            ? `<button class="btn btn--sm btn--text" data-branch="deactivate" data-id="${b.branchId}">Deactivate</button>`
            : `<button class="btn btn--sm btn--text" data-branch="activate" data-id="${b.branchId}">Reactivate</button>`}
          <button class="icon-btn icon-btn--sm" data-branch="delete" data-id="${b.branchId}" aria-label="Delete" title="Delete" style="margin-left:auto">${icon('trash', 16)}</button>
        </footer>
      </article>`;
    }).join('')}</div>`;
  }

  el.addEventListener('click', async (e) => {
    const btn = e.target.closest('[data-branch]');
    if (!btn) return;
    const b = state.branches.find((x) => x.branchId === Number(btn.dataset.id));
    switch (btn.dataset.branch) {
      case 'add':
      case 'edit':
        if (await branchDialog(btn.dataset.branch === 'edit' ? b : null)) await load();
        break;
      case 'deactivate': {
        const ok = await confirmDialog({ title: `Deactivate ${b.name}?`, text: 'Customers can no longer choose it as a pick-up branch, and its vehicles drop out of search. This is refused while bookings still collect from here.', confirmLabel: 'Deactivate' });
        if (ok && await runAction(null, () => api.branches.deactivate(b.branchId), 'Branch deactivated')) await load();
        break;
      }
      case 'activate':
        if (await runAction(btn, () => api.branches.activate(b.branchId), 'Branch reactivated')) await load();
        break;
      case 'delete': {
        const ok = await confirmDialog({ title: `Delete ${b.name}?`, text: 'A branch that still has vehicles, staff or bookings cannot be deleted — deactivate it instead.', confirmLabel: 'Delete branch', tone: 'danger' });
        if (ok && await runAction(null, () => api.branches.remove(b.branchId), 'Branch deleted')) await load();
        break;
      }
      default:
    }
  });

  await load();
}

function branchDialog(b) {
  const editing = Boolean(b);
  return openDialog({
    title: editing ? `Edit ${b.name}` : 'New branch',
    submitLabel: editing ? 'Save changes' : 'Create branch',
    wide: true,
    body: `<div class="form-grid">
      ${field({ name: 'name', label: 'Branch name', value: b?.name, required: true, span: true })}
      ${field({ name: 'street', label: 'Street', value: b?.street })}
      ${field({ name: 'city', label: 'City', value: b?.city })}
      ${field({ name: 'district', label: 'District', type: 'select', value: b?.district ?? '', options: districtOptions(b?.district) })}
      ${field({ name: 'contactNumber', label: 'Contact number', type: 'tel', value: b?.contactNumber })}
      ${field({ name: 'openTime', label: 'Opens', type: 'time', value: b?.openTime ? fmtTime(b.openTime) : '08:00' })}
      ${field({ name: 'closeTime', label: 'Closes', type: 'time', value: b?.closeTime ? fmtTime(b.closeTime) : '20:00', after: 'openTime', afterMsg: 'Closing time must be after opening time' })}
      ${field({ name: 'status', label: 'Status', type: 'select', value: b?.status || 'ACTIVE', options: [{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }], span: true })}
    </div>`,
    async onSubmit(values) {
      if (values.openTime && values.closeTime && values.closeTime <= values.openTime) {
        throw new Error('Closing time must be after opening time.');
      }
      if (editing) await api.branches.update(b.branchId, values);
      else await api.branches.create(values);
      toast(editing ? 'Branch updated' : 'Branch created', 'success');
    },
  });
}

// ==================================================================
// Transfers (administrator)
// ==================================================================
export async function renderTransfers({ el }) {
  const state = { tab: 'PENDING', transfers: [], fleet: new Map(), branches: [] };

  el.innerHTML = `
    ${consoleHead({
      eyebrow: 'Logistics', title: 'Vehicle transfers',
      text: 'Move vehicles between branches. Completing a transfer changes the vehicle’s home branch.',
      actions: `<button class="btn" data-transfer="add">${icon('swap', 18)} New transfer</button>`,
    })}
    <div class="toolbar"><div id="tabs"></div></div>
    <div id="list">${skeletons(1, '')}</div>`;

  async function load() {
    invalidate('fleet');
    const [transfers, fleet, branches] = await Promise.all([api.transfers.list(), fleetMap(), getBranches()]);
    Object.assign(state, { transfers, fleet, branches });
    draw();
  }

  function draw() {
    const by = (s) => state.transfers.filter((t) => t.status === s).length;
    $('#tabs', el).innerHTML = segmented([
      ['PENDING', 'Pending', by('PENDING')], ['COMPLETED', 'Completed', by('COMPLETED')],
      ['CANCELLED', 'Cancelled', by('CANCELLED')], ['all', 'All', state.transfers.length],
    ], state.tab);
    const list = state.tab === 'all' ? state.transfers : state.transfers.filter((t) => t.status === state.tab);
    const branchName = (id) => state.branches.find((b) => b.branchId === id)?.name || `#${id}`;
    const host = $('#list', el);
    if (!list.length) {
      host.innerHTML = empty({ icon: 'swap', title: 'No transfers here', text: 'Transfers you schedule appear here until they are completed or cancelled.' });
      return;
    }
    host.innerHTML = dataList({
      cols: '56px minmax(175px,1.3fr) minmax(190px,1.4fr) minmax(95px,.7fr) minmax(190px,auto)',
      headers: ['#', 'Vehicle', 'Route', 'Date', ''],
      rows: list.map((t, i) => row([
        cell('Transfer', `<span class="id-pill">#${t.transferId}</span>`),
        cell('Vehicle', vehicleCell(state.fleet.get(t.vehicleId), t.vehicleId), 'cell--wide'),
        cell('Route', `<b style="display:flex;align-items:center;gap:8px">${esc(branchName(t.fromBranchId))} ${icon('arrowRight', 16)} ${esc(branchName(t.toBranchId))}</b><small>${esc(t.reason || 'No reason given')}</small>`, 'cell--wide'),
        cell('Date', `<b>${fmtDate(t.transferDate)}</b>`),
        actionsCell(`${statusChip(t.status)}${t.status === 'PENDING'
          ? `<button class="btn btn--sm btn--text" data-transfer="cancel" data-id="${t.transferId}">Cancel</button>
             <button class="btn btn--sm" data-transfer="complete" data-id="${t.transferId}">${icon('check', 16)} Complete</button>`
          : ''}`),
      ], i)),
    });
  }

  el.addEventListener('click', async (e) => {
    const tab = e.target.closest('[data-seg]');
    if (tab) { state.tab = tab.dataset.seg; draw(); return; }
    const btn = e.target.closest('[data-transfer]');
    if (!btn) return;
    const id = Number(btn.dataset.id);
    const action = btn.dataset.transfer;

    if (action === 'add') {
      const fleet = await getFleet();
      const movable = (v) => !['RENTED', 'RESERVED'].includes(v.status);
      const ok = await openDialog({
        title: 'Schedule a transfer',
        subtitle: 'Moving a vehicle is refused while any booking still collects it from its current branch.',
        submitLabel: 'Schedule transfer',
        body: `<div class="form-grid">
          ${field({ name: 'vehicleId', label: 'Vehicle', type: 'select', options: vehicleOptions(fleet, { filter: movable }), required: true, span: true })}
          <div class="span-2" id="from-branch"></div>
          ${field({ name: 'toBranchId', label: 'Destination branch', type: 'select', options: branchSelectOptions(state.branches, { placeholder: 'Select a branch', activeOnly: true }), required: true })}
          ${field({ name: 'transferDate', label: 'Transfer date', type: 'date', value: isoDate(), min: isoDate(), required: true })}
          ${field({ name: 'reason', label: 'Reason', type: 'textarea', span: true })}
        </div>
        <div class="notice">${icon('info', 18)}<span>From the transfer date the vehicle can only be collected at its new branch, so any booking running on or after that date has to be cancelled first.</span></div>`,
        onOpen(form) {
          const note = form.querySelector('#from-branch');
          const sync = () => {
            const v = fleet.find((x) => String(x.vehicleId) === form.elements.vehicleId.value);
            note.innerHTML = v ? `<div class="notice">${icon('pin', 18)}<span>Currently at <b>${esc(state.branches.find((b) => b.branchId === v.branchId)?.name || `#${v.branchId}`)}</b></span></div>` : '';
          };
          form.elements.vehicleId.addEventListener('change', sync);
        },
        async onSubmit(values) {
          const v = fleet.find((x) => String(x.vehicleId) === String(values.vehicleId));
          if (v && String(v.branchId) === String(values.toBranchId)) throw new Error('The vehicle is already at that branch.');
          await api.transfers.create({ ...values, vehicleId: Number(values.vehicleId), toBranchId: Number(values.toBranchId) });
        },
      });
      if (ok) { toast('Transfer scheduled', 'success'); state.tab = 'PENDING'; await load(); }
    }
    if (action === 'complete') {
      const ok = await confirmDialog({ title: `Complete transfer #${id}?`, text: 'The home branch changes to the destination. This cannot be done before the planned transfer date, or while the vehicle is out with a customer.', confirmLabel: 'Complete transfer' });
      if (ok && await runAction(null, () => api.transfers.complete(id), 'Transfer completed')) await load();
    }
    if (action === 'cancel') {
      const ok = await confirmDialog({ title: `Cancel transfer #${id}?`, text: 'The vehicle stays at its current branch.', confirmLabel: 'Cancel transfer', tone: 'danger' });
      if (ok && await runAction(null, () => api.transfers.cancel(id), 'Transfer cancelled')) await load();
    }
  });

  await load();
}

// ==================================================================
// Insurance policies (staff read, administrator write)
// ==================================================================
export async function renderInsurance({ el, query }) {
  const admin = isAdmin();
  const state = { tab: ['all', 'expiring', 'EXPIRED'].includes(query.tab) ? query.tab : 'all', policies: [], expiring: [], fleet: new Map() };

  el.innerHTML = `
    ${consoleHead({
      eyebrow: 'Cover', title: 'Insurance policies',
      text: 'A vehicle with no active cover for the requested dates cannot be booked, so lapsed policies take cars off the market.',
      actions: admin ? `<button class="btn" data-policy="add">${icon('plus', 18)} Add policy</button>` : '',
    })}
    <div class="toolbar"><div id="tabs"></div></div>
    <div id="list">${skeletons(1, '')}</div>`;

  async function load() {
    const [policies, expiring, fleet] = await Promise.all([
      api.insurance.list(), api.insurance.expiring(30).catch(() => []), fleetMap(),
    ]);
    Object.assign(state, { policies, expiring, fleet });
    draw();
  }

  const today = isoDate();
  const lapsed = (p) => p.status === 'EXPIRED' || p.expiryDate < today;

  function draw() {
    const expired = state.policies.filter(lapsed);
    $('#tabs', el).innerHTML = segmented([
      ['all', 'All', state.policies.length],
      ['expiring', 'Expiring in 30 days', state.expiring.length],
      ['EXPIRED', 'Lapsed', expired.length],
    ], state.tab);
    const list = state.tab === 'all' ? state.policies : state.tab === 'expiring' ? state.expiring : expired;
    const host = $('#list', el);
    if (!list.length) {
      host.innerHTML = empty({ icon: 'shield', title: state.tab === 'all' ? 'No policies recorded' : 'Nothing to act on', text: state.tab === 'all' ? 'Policies added by an administrator appear here.' : 'Every vehicle is covered for now.' });
      return;
    }
    host.innerHTML = dataList({
      cols: 'minmax(150px,1.1fr) minmax(175px,1.3fr) minmax(95px,.7fr) minmax(115px,.8fr) minmax(150px,auto)',
      headers: ['Policy', 'Vehicle', 'Starts', 'Expires', ''],
      rows: list.map((p, i) => {
        const days = Math.round((new Date(`${p.expiryDate}T00:00:00`) - new Date(`${today}T00:00:00`)) / 86400000);
        const status = lapsed(p) ? 'EXPIRED' : p.status;
        return row([
          cell('Policy', `<b>${esc(p.policyNumber)}</b><small>${esc(p.provider)}</small>`),
          cell('Vehicle', vehicleCell(state.fleet.get(p.vehicleId), p.vehicleId), 'cell--wide'),
          cell('Starts', `<b>${fmtDate(p.startDate)}</b>`),
          cell('Expires', `<b>${fmtDate(p.expiryDate)}</b><small>${days < 0 ? `${-days} days ago` : days === 0 ? 'Today' : `in ${days} day${days === 1 ? '' : 's'}`}</small>`),
          actionsCell(`${statusChip(status)}${admin ? `
            <button class="icon-btn icon-btn--sm" data-policy="edit" data-id="${p.policyId}" aria-label="Edit" title="Edit">${icon('edit', 16)}</button>
            <button class="icon-btn icon-btn--sm" data-policy="delete" data-id="${p.policyId}" aria-label="Delete" title="Delete">${icon('trash', 16)}</button>` : ''}`),
        ], i);
      }),
    });
  }

  el.addEventListener('click', async (e) => {
    const tab = e.target.closest('[data-seg]');
    if (tab) { state.tab = tab.dataset.seg; draw(); return; }
    const btn = e.target.closest('[data-policy]');
    if (!btn) return;
    const p = state.policies.find((x) => x.policyId === Number(btn.dataset.id));
    const action = btn.dataset.policy;
    if (action === 'add' || action === 'edit') {
      const fleet = await getFleet();
      const editing = action === 'edit';
      const ok = await openDialog({
        title: editing ? `Edit policy ${p.policyNumber}` : 'Add an insurance policy',
        submitLabel: editing ? 'Save changes' : 'Add policy',
        wide: true,
        body: `<div class="form-grid">
          ${field({ name: 'vehicleId', label: 'Vehicle', type: 'select', value: p?.vehicleId ?? '', options: vehicleOptions(fleet), required: true, span: true, disabled: editing, hint: editing ? 'A policy stays with the vehicle it was written for' : '' })}
          ${field({ name: 'policyNumber', label: 'Policy number', value: p?.policyNumber, required: true })}
          ${field({ name: 'provider', label: 'Provider', value: p?.provider, required: true })}
          ${field({ name: 'startDate', label: 'Start date', type: 'date', value: p?.startDate ?? isoDate(), required: true })}
          ${field({ name: 'expiryDate', label: 'Expiry date', type: 'date', value: p?.expiryDate ?? '', required: true, after: 'startDate', strictAfter: true, afterMsg: 'Expiry date must be after the start date' })}
          ${field({ name: 'status', label: 'Status', type: 'select', value: p?.status || 'ACTIVE', options: [{ value: 'ACTIVE', label: 'Active' }, { value: 'EXPIRED', label: 'Expired' }, { value: 'CANCELLED', label: 'Cancelled' }], span: true })}
        </div>
        <div class="notice">${icon('info', 18)}<span>A vehicle cannot be rented out for any day an active policy does not cover, and two active policies cannot overlap on the same vehicle.</span></div>`,
        async onSubmit(values) {
          if (values.expiryDate <= values.startDate) throw new Error('Expiry date must be after the start date.');
          if (values.status === 'ACTIVE' && values.expiryDate < isoDate()) {
            throw new Error('A policy that has already expired cannot be marked active. Extend the expiry date instead.');
          }
          const payload = { ...values, vehicleId: Number(values.vehicleId) };
          if (editing) await api.insurance.update(p.policyId, { ...payload, vehicleId: p.vehicleId });
          else await api.insurance.create(payload);
        },
      });
      if (ok) { toast(editing ? 'Policy updated' : 'Policy added', 'success'); await load(); }
    }
    if (action === 'delete' && p) {
      const ok = await confirmDialog({ title: `Delete policy ${p.policyNumber}?`, text: 'The record is removed permanently. Cover that a live booking relies on cannot be deleted.', confirmLabel: 'Delete', tone: 'danger' });
      if (ok && await runAction(null, () => api.insurance.remove(p.policyId), 'Policy deleted')) await load();
    }
  });

  await load();
}

