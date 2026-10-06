// ============================================================
// Console: fleet, maintenance, damage reports, insurance claims.
// ============================================================
import { api } from '../api.js';
import { getBranches, getFleet, fleetMap, userMap, invalidate, isAdmin } from '../store.js';
import {
  $, esc, icon, money, number, fmtDate, isoDate, humanize, statusChip, vehicleVisual, carArt,
  field, checkbox, openDialog, confirmDialog, promptDialog, runAction, toast, empty, skeletons, debounce,
} from '../ui.js';
import {
  consoleHead, segmented, searchBox, dataList, row, cell, actionsCell, vehicleCell,
  vehicleOptions, branchSelectOptions, SEVERITIES, filterByText, pager, fetchAllPages, downloadCsv, exportButtons,
  printTable,
} from './console-kit.js';

// Mirrors VehicleServiceImpl.isValidTransition on the backend.
// Manual moves only. RENTED and RESERVED are set by handovers and approvals,
// never by hand, so they are not offered here.
const VEHICLE_TRANSITIONS = {
  AVAILABLE: ['UNDER_MAINTENANCE', 'UNAVAILABLE'],
  RESERVED: ['UNDER_MAINTENANCE', 'UNAVAILABLE'],
  RENTED: [],
  UNDER_MAINTENANCE: ['AVAILABLE', 'UNAVAILABLE'],
  UNAVAILABLE: ['AVAILABLE', 'UNDER_MAINTENANCE'],
};
const VEHICLE_STATUSES = ['AVAILABLE', 'RESERVED', 'RENTED', 'UNDER_MAINTENANCE', 'UNAVAILABLE'];
// These are the categories the backend accepts (VehicleServiceImpl.CATEGORIES).
// Anything else is rejected, so the list is not free text.
const CATEGORIES = [
  { value: 'CAR', label: 'Car' },
  { value: 'SUV', label: 'SUV' },
  { value: 'VAN', label: 'Van' },
  { value: 'BUS', label: 'Bus' },
  { value: 'LUXURY', label: 'Luxury' },
  { value: 'PICKUP', label: 'Pickup' },
];
const FUELS = ['Petrol', 'Diesel', 'Hybrid', 'Electric'].map((f) => ({ value: f, label: f }));

// A vehicle only reaches the customer search when its branch is active, its
// status is on the road, and an ACTIVE policy covers the dates. The first two
// are visible on this screen already; cover was not, so a newly added vehicle
// looked fine here and never appeared to customers.
const OFF_ROAD_STATUSES = ['UNDER_MAINTENANCE', 'UNAVAILABLE'];

/** Vehicle ids with an ACTIVE policy in force today. null = could not load. */
function coveredIds(policies) {
  if (!policies) return null;
  const today = isoDate();
  const ids = new Set();
  policies.forEach((p) => {
    if (p.status !== 'ACTIVE') return;
    if (p.startDate && p.startDate > today) return;
    if (p.expiryDate && p.expiryDate < today) return;
    ids.add(p.vehicleId);
  });
  return ids;
}

/** Why customers cannot see this vehicle, or null when they can. */
function hiddenReason(v, branches, covered) {
  const branch = branches.find((b) => b.branchId === v.branchId);
  if (branch && branch.status !== 'ACTIVE') return `${branch.name} is closed`;
  if (OFF_ROAD_STATUSES.includes(v.status)) return null;   // the status chip already says it
  if (covered && !covered.has(v.vehicleId)) return 'No insurance cover';
  return null;
}

// ==================================================================
// Fleet
// ==================================================================
export async function renderFleet({ el, query }) {
  const state = {
    status: VEHICLE_STATUSES.includes(query.status) || query.status === 'hidden' ? query.status : 'all',
    branch: '', text: '', page: 1, data: null, fleet: [], branches: [], covered: new Set(), hidden: [],
  };
  const admin = isAdmin();

  el.innerHTML = `
    ${consoleHead({
      eyebrow: 'Vehicles', title: 'Fleet',
      text: admin ? 'Add, edit and take vehicles in or out of service.' : 'Check vehicle state and take vehicles in or out of service.',
      actions: `${exportButtons()}${admin ? `<button class="btn" data-fleet="add">${icon('plus', 18)} Add vehicle</button>` : ''}`,
    })}
    <div class="toolbar">
      <div id="tabs"></div>
      <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:center">
        <div id="branch-filter" style="min-width:200px"></div>
        ${searchBox('Search model or plate')}
      </div>
    </div>
    <div id="grid">${skeletons(6, 'fleet-grid')}</div>
    <div id="pager"></div>`;

  const query_ = () => ({
    status: state.status === 'hidden' ? 'all' : state.status,
    q: state.text.trim() || undefined,
    branchId: state.branch || undefined,
    allBranches: !state.branch,
  });

  // A page of cards from the server (C1). "Not listed" needs the insurance
  // picture as well, so that one tab is worked out here from the full list.
  async function load() {
    invalidate('fleet');
    // Policies come along too: a vehicle without cover is invisible to
    // customers, and until now nothing on this screen said so.
    const [data, branches, policies, everything] = await Promise.all([
      api.vehicles.page({ ...query_(), page: state.status === 'hidden' ? 1 : state.page, size: 24 }),
      getBranches(), api.insurance.list().catch(() => null), getFleet(),
    ]);
    const covered = coveredIds(policies);
    const inBranch = state.branch ? everything.filter((v) => String(v.branchId) === String(state.branch)) : everything;
    const q = state.text.trim().toLowerCase();
    const hidden = filterByText(inBranch.filter((v) => hiddenReason(v, branches, covered)), q, (v) => [v.model, v.plateNumber, v.category]);
    if (data.page > data.pages && state.page > 1) { state.page = data.pages; return load(); }
    Object.assign(state, { data, branches, covered, hidden, fleet: state.status === 'hidden' ? hidden : data.items });
    $('#branch-filter', el).innerHTML = field({
      name: 'branchFilter', label: 'Branch', type: 'select', compact: true, value: state.branch,
      options: branchSelectOptions(branches, { placeholder: 'All branches' }),
    });
    draw();
  }

  async function exportRows(kind) {
    const all = state.status === 'hidden' ? state.hidden : await fetchAllPages(api.vehicles.page, query_());
    const branchName = (id) => state.branches.find((b) => b.branchId === id)?.name || `#${id}`;
    if (kind === 'csv') {
      downloadCsv('fleet', ['ID', 'Model', 'Plate', 'Category', 'Branch', 'Status', 'Daily rate', 'Odometer', 'Fuel', 'Seats', 'Year', 'Listed'],
        all.map((v) => [v.vehicleId, v.model, v.plateNumber, v.category, branchName(v.branchId), v.status, v.rentalPricePerDay,
          v.mileage, v.fuelType, v.passengerCapacity, v.manufactureYear, hiddenReason(v, state.branches, state.covered) || 'yes']));
    } else {
      printTable('Fleet', state.branch ? branchName(Number(state.branch)) : 'All branches',
        ['Vehicle', 'Plate', 'Branch', 'Status', 'Rate / day', 'Odometer'],
        all.map((v) => [esc(v.model), esc(v.plateNumber), esc(branchName(v.branchId)), esc(humanize(v.status)),
          money(v.rentalPricePerDay), `${number(v.mileage)} km`]));
    }
  }

  function draw() {
    const counts = state.data?.counts || {};
    const hiddenCount = state.hidden.length;
    $('#tabs', el).innerHTML = segmented([
      ['all', 'All', counts.all || 0],
      ...VEHICLE_STATUSES.filter((s) => counts[s] > 0 || s === state.status).map((s) => [s, humanize(s), counts[s] || 0]),
      ...(hiddenCount || state.status === 'hidden' ? [['hidden', 'Not listed', hiddenCount]] : []),
    ], state.status);
    $('#pager', el).innerHTML = state.status === 'hidden' ? '' : pager(state.data);

    const list = state.fleet;
    const branchName = (id) => state.branches.find((b) => b.branchId === id)?.name || `Branch #${id}`;

    const host = $('#grid', el);
    if (!list.length) {
      const none = !counts.all && !state.text && !state.branch;
      host.innerHTML = empty({
        icon: 'car', title: none ? 'The fleet is empty' : 'No vehicles match',
        text: none ? 'Vehicles added by an administrator appear here.' : 'Change the filters to see more.',
        action: admin && none ? `<button class="btn" data-fleet="add">${icon('plus', 18)} Add the first vehicle</button>` : '',
      });
      return;
    }
    host.innerHTML = `<div class="fleet-grid">${list.map((v, i) => `
      <article class="fcard" style="animation-delay:${Math.min(i, 12) * 35}ms">
        <div class="fcard__row"><span class="tag">${esc(v.category || 'Vehicle')}</span>${statusChip(v.status)}</div>
        <div class="fcard__art">${vehicleVisual(v)}</div>
        <div>
          <h3>${esc(v.model)}</h3>
          <p class="fcard__meta">${esc(v.plateNumber)} · ${esc(branchName(v.branchId))}</p>
        </div>
        <div class="fcard__row" style="font-size:13px;color:var(--ink-2)">
          <span>${money(v.rentalPricePerDay)}<span class="muted"> / day</span></span>
          <span>${number(v.mileage)} km</span>
          <span>${esc(v.fuelType || '—')}</span>
        </div>
        ${hiddenReason(v, state.branches, state.covered) ? `
          <div class="notice notice--amber">${icon('alert', 16)}<span><b>Not shown to customers</b> — ${esc(hiddenReason(v, state.branches, state.covered).toLowerCase())}.</span></div>` : ''}
        <div class="fcard__actions">
          <button class="btn btn--sm btn--tonal" data-fleet="status" data-id="${v.vehicleId}">${icon('refresh', 16)} Status</button>
          <button class="icon-btn icon-btn--sm" data-fleet="photo" data-id="${v.vehicleId}" aria-label="Photo" title="${v.imageUrl ? 'Replace photo' : 'Add a photo'}">${icon('camera', 16)}</button>
          <button class="icon-btn icon-btn--sm" data-fleet="history" data-id="${v.vehicleId}" aria-label="History" title="Service &amp; damage history">${icon('history', 16)}</button>
          ${admin ? `
            <button class="icon-btn icon-btn--sm" data-fleet="edit" data-id="${v.vehicleId}" aria-label="Edit" title="Edit">${icon('edit', 16)}</button>
            <button class="icon-btn icon-btn--sm" data-fleet="delete" data-id="${v.vehicleId}" aria-label="Delete" title="Delete">${icon('trash', 16)}</button>` : ''}
        </div>
      </article>`).join('')}</div>`;
  }

  el.addEventListener('click', async (e) => {
    const tab = e.target.closest('[data-seg]');
    if (tab) { state.status = tab.dataset.seg; state.page = 1; await load(); return; }
    const pg = e.target.closest('[data-page]');
    if (pg) { state.page = Number(pg.dataset.page); await load(); return; }
    const exp = e.target.closest('[data-export]');
    if (exp) {
      exp.disabled = true;
      try { await exportRows(exp.dataset.export); } catch (err) { toast(err.message || 'Export failed', 'error'); }
      exp.disabled = false;
      return;
    }
    const btn = e.target.closest('[data-fleet]');
    if (!btn) return;
    const v = state.fleet.find((x) => x.vehicleId === Number(btn.dataset.id));
    const action = btn.dataset.fleet;

    if (action === 'add' || action === 'edit') {
      const saved = await vehicleDialog(action === 'edit' ? v : null, state.branches);
      if (!saved) return;
      await load();
      // Only on add: an edit leaves cover exactly as it was, and the card
      // already carries the warning.
      const added = action === 'add' && saved.vehicleId;
      if (added && state.covered && !state.covered.has(saved.vehicleId)) {
        const done = await coverDialog(saved);
        if (done) { toast('Cover added — the vehicle is now listed', 'success'); await load(); }
        else toast('Added, but not shown to customers until it has insurance cover', 'error');
      }
    }
    if (action === 'photo' && v) {
      const saved = await photoDialog(v);
      if (saved) await load();
    }
    if (action === 'status' && v) {
      const options = (VEHICLE_TRANSITIONS[v.status] || []).map((s) => ({ value: s, label: humanize(s) }));
      if (!options.length) {
        toast('This vehicle is out with a customer. Record the return to release it.', 'error');
        return;
      }
      const ok = await openDialog({
        title: 'Change vehicle status',
        subtitle: `${v.model} · currently ${humanize(v.status).toLowerCase()}`,
        submitLabel: 'Update status',
        body: `${field({ name: 'status', label: 'New status', type: 'select', options, required: true })}
          ${field({ name: 'reason', label: 'Reason', type: 'textarea', span: true, hint: 'Recorded in the audit trail' })}
          <div class="notice">${icon('info', 18)}<span>Taking a vehicle off the road is refused while any booking still holds it — cancel those first. Rented vehicles are released by recording the return.</span></div>`,
        onSubmit: (values) => api.vehicles.setStatus(v.vehicleId, values.status, values.reason),
      });
      if (ok) { await load(); toast('Status updated', 'success'); }
    }
    if (action === 'history' && v) await vehicleHistory(v);
    if (action === 'delete' && v) {
      const ok = await confirmDialog({
        title: `Delete ${v.model}?`,
        text: 'Vehicles with bookings, service or insurance records cannot be deleted — mark them unavailable instead.',
        confirmLabel: 'Delete vehicle', tone: 'danger',
      });
      if (ok && await runAction(null, () => api.vehicles.remove(v.vehicleId), 'Vehicle deleted')) await load();
    }
  });
  el.addEventListener('change', (e) => {
    if (e.target.name === 'branchFilter') { state.branch = e.target.value; state.page = 1; load(); }
  });
  el.addEventListener('input', debounce((e) => {
    if (e.target.matches('[data-search]')) { state.text = e.target.value; state.page = 1; load(); }
  }, 250));

  await load();
}

function vehicleDialog(v, branches) {
  const editing = Boolean(v);
  return openDialog({
    title: editing ? `Edit ${v.model}` : 'Add a vehicle',
    subtitle: editing ? 'Branch changes go through a transfer, and status through the fleet actions.' : 'New vehicles start as available.',
    submitLabel: editing ? 'Save changes' : 'Add vehicle',
    wide: true,
    body: `<div class="form-grid">
      ${field({ name: 'model', label: 'Make & model', value: v?.model, required: true })}
      ${field({ name: 'plateNumber', label: 'Plate number', value: v?.plateNumber, required: true })}
      ${field({ name: 'category', label: 'Category', type: 'select', value: v?.category || 'CAR', options: categoryOptions(v?.category), required: true, hint: isLegacyCategory(v?.category) ? 'This vehicle has an old category — choose one from the list to save' : '' })}
      ${field({ name: 'branchId', label: 'Home branch', type: 'select', value: v?.branchId, options: branchSelectOptions(branches, { activeOnly: !editing }), required: true, disabled: editing })}
      ${field({ name: 'rentalPricePerDay', label: 'Daily rate (LKR)', type: 'number', value: v?.rentalPricePerDay, min: 1, step: '0.01', required: true })}
      ${field({ name: 'manufactureYear', label: 'Model year', type: 'number', value: v?.manufactureYear, min: 1980, max: new Date().getFullYear() + 1 })}
      ${field({ name: 'passengerCapacity', label: 'Seats', type: 'number', value: v?.passengerCapacity, min: 1, max: 60 })}
      ${field({ name: 'fuelType', label: 'Fuel', type: 'select', value: v?.fuelType || 'Petrol', options: withCurrent(FUELS, v?.fuelType) })}
      ${field({ name: 'mileage', label: 'Odometer (km)', type: 'number', value: v?.mileage ?? 0, min: 0, required: true })}
      ${photoField(v)}
    </div>`,
    onOpen: (form) => bindPhotoField(form, v),
    async onSubmit(values, form) {
      const file = form.querySelector('#photo-file').files[0];
      const cleared = form.dataset.photoCleared === 'true';

      let saved;
      if (editing) {
        // branchId and status are left out on purpose: the backend rejects a
        // branch change here (it belongs to a transfer) and owns the status.
        const { branchId, ...editable } = values;
        saved = await api.vehicles.update(v.vehicleId, editable);
      } else {
        saved = await api.vehicles.create(values);
      }
      const id = saved?.vehicleId ?? v?.vehicleId;

      // The photo is a second request because a new vehicle has no id until it
      // is saved. A failure here must not look like the save failed, and must
      // not be retryable by resubmitting — the vehicle already exists.
      if (file) {
        try {
          await api.vehicles.uploadImage(id, file);
        } catch (err) {
          toast(`Vehicle saved, but the photo was not uploaded: ${err.message}`, 'error');
        }
      } else if (cleared && v?.imageUrl) {
        try {
          await api.vehicles.removeImage(id);
        } catch (err) {
          toast(`Vehicle saved, but the photo was not removed: ${err.message}`, 'error');
        }
      }
      runAction(null, async () => {}, editing ? 'Vehicle updated' : 'Vehicle added to the fleet');
      return saved;
    },
  });
}

/**
 * A new vehicle is invisible to customers until an active policy covers it.
 * Rather than let the admin discover that days later, offer the policy here.
 */
function coverDialog(v) {
  const today = isoDate();
  const nextYear = isoDate(new Date(new Date().setFullYear(new Date().getFullYear() + 1)));
  return openDialog({
    title: 'Add insurance cover',
    subtitle: `${v.model} · ${v.plateNumber}`,
    submitLabel: 'Add cover',
    body: `<div class="notice notice--amber">${icon('alert', 18)}<span>Customers cannot see this vehicle until an active policy covers the dates they search. Add it now, or later from the Insurance screen.</span></div>
      <div class="form-grid" style="margin-top:14px">
        ${field({ name: 'policyNumber', label: 'Policy number', required: true })}
        ${field({ name: 'provider', label: 'Provider', required: true })}
        ${field({ name: 'startDate', label: 'Cover starts', type: 'date', value: today, required: true })}
        ${field({ name: 'expiryDate', label: 'Cover expires', type: 'date', value: nextYear, required: true, after: 'startDate', strictAfter: true, afterMsg: 'Cover must end after it starts' })}
      </div>`,
    onSubmit: (values) => api.insurance.create({ ...values, vehicleId: v.vehicleId, status: 'ACTIVE' }),
  });
}

/**
 * Photo on its own. Editing a vehicle is administrator-only, but taking its
 * picture is not — staff are the ones standing next to the car — so the photo
 * gets its own dialog that touches nothing else on the record.
 */
const MAX_GALLERY = 8;

function galleryManager(photos) {
  return `${photos.map((ph) => `<figure class="gal-item">
      <img src="${esc(ph.url)}?w=320" alt="" loading="lazy">
      <button type="button" class="icon-btn icon-btn--sm" data-gal-remove="${ph.imageId}" aria-label="Remove this photo" title="Remove">${icon('trash', 15)}</button>
    </figure>`).join('')}
    ${photos.length < MAX_GALLERY ? `<button type="button" class="gal-add" data-gal-add>${icon('plus', 20)}<span>Add photo</span></button>` : ''}`;
}

function photoDialog(v) {
  return openDialog({
    title: `Photos — ${v.model}`,
    subtitle: `${v.plateNumber}${v.imageUrl ? '' : ' · currently using the drawn illustration'}`,
    submitLabel: 'Save cover photo',
    wide: true,
    body: `<div class="form-grid">${photoField(v)}</div>
      <div class="gal-manage">
        <div class="gal-manage__head"><b>More photos</b><small class="muted">Shown on the car page, up to ${MAX_GALLERY}. Added and removed straight away.</small></div>
        <div class="gal-grid" id="gal-grid"><div class="skeleton" style="height:96px"></div></div>
        <input type="file" id="gal-file" accept="image/jpeg,image/png,image/webp" hidden>
      </div>`,
    onOpen: (form) => {
      bindPhotoField(form, v);
      const grid = form.querySelector('#gal-grid');
      const file = form.querySelector('#gal-file');
      const paint = (photos) => { grid.innerHTML = galleryManager(photos || []); };
      api.vehicles.gallery(v.vehicleId).then(paint).catch(() => paint([]));
      grid.addEventListener('click', async (e) => {
        if (e.target.closest('[data-gal-add]')) { file.click(); return; }
        const rm = e.target.closest('[data-gal-remove]');
        if (!rm) return;
        rm.disabled = true;
        try { paint(await api.vehicles.removeGalleryImage(v.vehicleId, rm.dataset.galRemove)); toast('Photo removed', 'success'); }
        catch (err) { toast(err.message || 'Could not remove the photo', 'error'); rm.disabled = false; }
      });
      file.addEventListener('change', async () => {
        const f = file.files[0];
        file.value = '';
        if (!f) return;
        if (f.size > MAX_PHOTO_BYTES) { toast('The photo must be 4 MB or smaller', 'error'); return; }
        grid.classList.add('is-busy');
        try { paint(await api.vehicles.addGalleryImage(v.vehicleId, f)); toast('Photo added', 'success'); }
        catch (err) { toast(err.message || 'Could not add the photo', 'error'); }
        grid.classList.remove('is-busy');
      });
    },
    async onSubmit(values, form) {
      const file = form.querySelector('#photo-file').files[0];
      const cleared = form.dataset.photoCleared === 'true';
      if (file) {
        await api.vehicles.uploadImage(v.vehicleId, file);
        toast('Photo updated', 'success');
      } else if (cleared && v.imageUrl) {
        await api.vehicles.removeImage(v.vehicleId);
        toast('Photo removed', 'success');
      }
    },
  });
}

const MAX_PHOTO_BYTES = 4 * 1024 * 1024;

/** A file picker with a live preview, falling back to the drawn illustration. */
function photoField(v) {
  const current = v?.imageUrl || '';
  return `<div class="photo span-2">
    <div class="photo__preview" id="photo-preview">
      ${vehicleVisual(v || { category: 'CAR' }, { compact: true })}
    </div>
    <div class="photo__body">
      <b>Photo</b>
      <small>JPEG, PNG or WebP, up to 4&nbsp;MB. Without one the drawn illustration is used.</small>
      <div class="photo__actions">
        <button type="button" class="btn btn--sm btn--tonal" data-photo="pick">${icon('camera', 16)} Choose photo</button>
        <button type="button" class="btn btn--sm btn--text" data-photo="clear"${current ? '' : ' hidden'}>Remove</button>
      </div>
    </div>
    <input type="file" id="photo-file" accept="image/jpeg,image/png,image/webp" hidden>
    <input type="hidden" name="imageUrl" value="${esc(current)}">
  </div>`;
}

function bindPhotoField(form, v) {
  const fileInput = form.querySelector('#photo-file');
  const preview = form.querySelector('#photo-preview');
  const hidden = form.querySelector('[name="imageUrl"]');
  const clearBtn = form.querySelector('[data-photo="clear"]');
  if (!fileInput) return;

  form.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-photo]');
    if (!btn) return;
    if (btn.dataset.photo === 'pick') {
      fileInput.click();
    } else {
      fileInput.value = '';
      hidden.value = '';
      form.dataset.photoCleared = 'true';
      preview.innerHTML = carArt(v || { category: 'CAR' }, { compact: true });
      clearBtn.hidden = true;
    }
  });

  fileInput.addEventListener('change', () => {
    const file = fileInput.files[0];
    if (!file) return;
    // Checked here as well as on the server so an oversized pick is refused
    // before it is uploaded.
    if (file.size > MAX_PHOTO_BYTES) {
      toast('The photo must be 4 MB or smaller', 'error');
      fileInput.value = '';
      return;
    }
    delete form.dataset.photoCleared;
    const img = document.createElement('img');
    img.alt = '';
    img.src = URL.createObjectURL(file);
    img.addEventListener('load', () => URL.revokeObjectURL(img.src), { once: true });
    preview.replaceChildren(img);
    clearBtn.hidden = false;
  });
}

function withCurrent(options, current) {
  if (!current || options.some((o) => o.value === current)) return options;
  return [{ value: current, label: current }, ...options];
}

/**
 * Categories are now a fixed list on the backend. A vehicle created before that
 * keeps its old value, so it is offered but clearly marked: saving without
 * choosing a valid one would be rejected.
 */
function categoryOptions(current) {
  if (!current || CATEGORIES.some((o) => o.value === current)) return CATEGORIES;
  return [{ value: current, label: `${current} — no longer valid, pick another` }, ...CATEGORIES];
}

function isLegacyCategory(current) {
  return Boolean(current) && !CATEGORIES.some((o) => o.value === current);
}

async function vehicleHistory(v) {
  const [services, damage] = await Promise.all([
    api.maintenance.byVehicle(v.vehicleId).catch(() => []),
    api.damage.byVehicle(v.vehicleId).catch(() => []),
  ]);
  const items = [
    ...services.map((m) => ({ date: m.eventDate, iconName: 'wrench', title: m.repairType || 'Service', text: `${m.serviceProvider || ''} · ${money(m.cost)}`, status: m.status })),
    ...damage.map((d) => ({ date: d.eventDate, iconName: 'shieldAlert', title: `Damage · ${humanize(d.damageSeverity)}`, text: d.description, status: d.status })),
  ].sort((a, b) => String(b.date).localeCompare(String(a.date)));

  await openDialog({
    title: v.model,
    subtitle: `${v.plateNumber} · service and damage history`,
    hideSubmit: true,
    cancelLabel: 'Close',
    body: items.length
      ? `<div style="display:grid;gap:8px">${items.map((it) => `
          <div class="notice" style="align-items:flex-start">
            <span class="notif__icon" style="width:36px;height:36px;background:var(--surface)">${icon(it.iconName, 18)}</span>
            <div style="flex:1;min-width:0">
              <b style="display:block;color:var(--ink)">${esc(it.title)}</b>
              <span>${esc(it.text)}</span>
              <small class="muted" style="display:block;margin-top:4px">${fmtDate(it.date)}</small>
            </div>
            ${statusChip(it.status)}
          </div>`).join('')}</div>`
      : empty({ icon: 'history', title: 'Clean record', text: 'No services or damage reports for this vehicle yet.' }),
  });
}

// ==================================================================
// Maintenance
// ==================================================================
export async function renderMaintenance({ el, query }) {
  const state = { tab: ['all', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'due'].includes(query.tab) ? query.tab : 'all', records: [], due: [], fleet: new Map(), users: new Map() };

  el.innerHTML = `
    ${consoleHead({
      eyebrow: 'Workshop', title: 'Service & maintenance',
      text: 'Track repairs and keep vehicles out of bookings while they are serviced.',
      actions: `<button class="btn" data-mnt="add">${icon('plus', 18)} Log service</button>`,
    })}
    <div class="toolbar"><div id="tabs"></div></div>
    <div id="list">${skeletons(1, '')}</div>`;

  async function load() {
    invalidate('fleet');
    const [records, due, fleet, users] = await Promise.all([
      api.maintenance.list(), api.maintenance.reminders(30).catch(() => []), fleetMap(), userMap(),
    ]);
    Object.assign(state, { records, due, fleet, users });
    draw();
  }

  function draw() {
    const by = (s) => state.records.filter((r) => r.status === s).length;
    $('#tabs', el).innerHTML = segmented([
      ['all', 'All', state.records.length],
      ['SCHEDULED', 'Scheduled', by('SCHEDULED')],
      ['IN_PROGRESS', 'In progress', by('IN_PROGRESS')],
      ['COMPLETED', 'Completed', by('COMPLETED')],
      ['CANCELLED', 'Cancelled', by('CANCELLED')],
      ['due', 'Due in 30 days', state.due.length],
    ], state.tab);

    const list = state.tab === 'all' ? state.records : state.tab === 'due' ? state.due : state.records.filter((r) => r.status === state.tab);
    const host = $('#list', el);
    if (!list.length) {
      host.innerHTML = empty({
        icon: 'wrench',
        title: state.tab === 'due' ? 'No services due soon' : 'No service records',
        text: state.tab === 'due' ? 'Records with a next service date in the coming 30 days show here.' : 'Log a service to start the vehicle history.',
      });
      return;
    }
    host.innerHTML = dataList({
      cols: 'minmax(165px,1.3fr) minmax(130px,1fr) minmax(130px,1fr) minmax(90px,.7fr) minmax(100px,.8fr) minmax(200px,auto)',
      headers: ['Vehicle', 'Work', 'Off the road', 'Cost', 'Next service', ''],
      rows: list.map((m, i) => row([
        cell('Vehicle', vehicleCell(state.fleet.get(m.vehicleId), m.vehicleId), 'cell--wide'),
        cell('Work', `<b>${esc(m.repairType || '—')}</b><small>${esc(m.serviceProvider || '')}${m.description ? ` · ${esc(m.description)}` : ''}</small>`),
        cell('Off the road', workshopWindow(m, state.users)),
        cell('Cost', `<b>${money(m.cost)}</b>`),
        cell('Next service', `<b>${fmtDate(m.nextServiceDate)}</b>`),
        actionsCell(`${statusChip(m.status)}${maintenanceActions(m)}`),
      ], i)),
    });
  }

  el.addEventListener('click', async (e) => {
    const tab = e.target.closest('[data-seg]');
    if (tab) { state.tab = tab.dataset.seg; draw(); return; }
    const btn = e.target.closest('[data-mnt]');
    if (!btn) return;
    const id = Number(btn.dataset.id);
    const action = btn.dataset.mnt;

    if (action === 'add') {
      const fleet = await getFleet();
      const ok = await maintenanceDialog(fleet);
      if (ok) await load();
    }
    if (action === 'start' && await runAction(btn, () => api.maintenance.setStatus(id, 'IN_PROGRESS'), 'Work started')) await load();
    if (action === 'complete') {
      const ok = await confirmDialog({ title: 'Complete this service?', text: 'If nothing else is holding the vehicle — another workshop booking or an unresolved damage report — it goes back on the road.', confirmLabel: 'Complete' });
      if (ok && await runAction(null, () => api.maintenance.complete(id), 'Service completed — vehicle released')) await load();
    }
    if (action === 'cancel') {
      const ok = await confirmDialog({ title: 'Cancel this service?', text: 'The workshop booking is dropped and the dates open up for customers again.', confirmLabel: 'Cancel service', tone: 'danger' });
      if (ok && await runAction(null, () => api.maintenance.setStatus(id, 'CANCELLED'), 'Service cancelled')) await load();
    }
    if (action === 'delete') {
      const ok = await confirmDialog({ title: 'Delete this record?', text: 'Only work that never happened can be removed. Completed services stay in the vehicle history.', confirmLabel: 'Delete', tone: 'danger' });
      if (ok && await runAction(null, () => api.maintenance.remove(id), 'Record deleted')) await load();
    }
  });

  await load();
}

/**
 * The dates a service keeps the vehicle out of the customer calendar.
 * A record with no end date is a single-day service.
 */
function workshopWindow(m, users) {
  const end = m.expectedEndDate && m.expectedEndDate !== m.eventDate ? m.expectedEndDate : null;
  const staff = users.get(m.handledByStaffId)?.fullName || '';
  return `<b>${fmtDate(m.eventDate)}${end ? ` &rarr; ${fmtDate(end)}` : ''}</b>`
    + `<small>${end ? 'blocks bookings for this window' : 'same-day service'}${staff ? ` · ${esc(staff)}` : ''}</small>`;
}

function maintenanceActions(m) {
  const id = m.eventId;
  const del = `<button class="icon-btn icon-btn--sm" data-mnt="delete" data-id="${id}" aria-label="Delete" title="Delete">${icon('trash', 16)}</button>`;
  if (m.status === 'SCHEDULED') {
    return `<button class="btn btn--sm btn--tonal" data-mnt="start" data-id="${id}">Start</button>
            <button class="btn btn--sm" data-mnt="complete" data-id="${id}">${icon('check', 16)} Complete</button>
            <button class="btn btn--sm btn--text" data-mnt="cancel" data-id="${id}">Cancel</button>${del}`;
  }
  if (m.status === 'IN_PROGRESS') {
    return `<button class="btn btn--sm" data-mnt="complete" data-id="${id}">${icon('check', 16)} Complete</button>
            <button class="btn btn--sm btn--text" data-mnt="cancel" data-id="${id}">Cancel</button>`;
  }
  // Completed and cancelled work is part of the vehicle's service history.
  return m.status === 'CANCELLED' ? del : '<span class="muted" style="font-size:12px">On record</span>';
}

function maintenanceDialog(fleet) {
  return openDialog({
    title: 'Log a service',
    subtitle: 'Booking a vehicle into the workshop reserves it for those dates.',
    submitLabel: 'Save record',
    wide: true,
    body: `<div class="form-grid">
      ${field({ name: 'vehicleId', label: 'Vehicle', type: 'select', options: vehicleOptions(fleet), required: true, span: true })}
      ${field({ name: 'repairType', label: 'Work type', required: true, hint: 'e.g. Full service, Brake pads' })}
      ${field({ name: 'serviceProvider', label: 'Service provider', required: true })}
      ${field({ name: 'eventDate', label: 'Service date', type: 'date', value: isoDate(), min: isoDate(), required: true })}
      ${field({ name: 'expectedEndDate', label: 'Back on the road by', type: 'date', min: isoDate(), after: 'eventDate', afterMsg: 'The vehicle cannot be back before the service date', hint: 'Leave empty for a same-day service' })}
      ${field({ name: 'cost', label: 'Cost (LKR)', type: 'number', min: 0, step: '0.01', required: true })}
      ${field({ name: 'nextServiceDate', label: 'Next service (optional)', type: 'date', after: 'expectedEndDate|eventDate', strictAfter: true, afterMsg: 'The next service must fall after this one finishes' })}
      ${field({ name: 'description', label: 'Notes', type: 'textarea', span: true })}
      ${checkbox({ name: 'startNow', label: 'Start the work now', description: 'Only for work happening today. The vehicle goes into the workshop immediately; otherwise it stays bookable until the service date.' })}
    </div>
    <div class="notice">${icon('info', 18)}<span>The service date and the “back on the road by” date block customer bookings for that window, so a car scheduled for next month stays rentable until then.</span></div>`,
    async onSubmit(values) {
      const end = values.expectedEndDate || values.eventDate;
      if (end < values.eventDate) {
        throw new Error('The end date cannot be before the service date.');
      }
      if (values.nextServiceDate && values.nextServiceDate <= end) {
        throw new Error('The next service reminder must fall after this service finishes.');
      }
      if (values.startNow && values.eventDate !== isoDate()) {
        throw new Error('Work can only be started immediately when the service date is today.');
      }
      await api.maintenance.create({
        ...values,
        vehicleId: Number(values.vehicleId),
        expectedEndDate: values.expectedEndDate || null,
      });
      toast('Service logged', 'success');
    },
  });
}

// ==================================================================
// Damage reports
// ==================================================================
export async function renderDamage({ el, query }) {
  const admin = isAdmin();
  const state = { tab: ['UNDER_REVIEW', 'RESOLVED', 'all'].includes(query.tab) ? query.tab : 'UNDER_REVIEW', reports: [], fleet: new Map(), users: new Map() };

  el.innerHTML = `
    ${consoleHead({
      eyebrow: 'After the trip', title: 'Damage reports',
      text: admin ? 'Review reported damage and file insurance claims.' : 'Review reported damage and resolve it once repaired.',
      actions: `<button class="btn" data-dmg="add">${icon('plus', 18)} Report damage</button>`,
    })}
    <div class="toolbar"><div id="tabs"></div></div>
    <div id="list">${skeletons(1, '')}</div>`;

  async function load() {
    const [reports, fleet, users] = await Promise.all([api.damage.list(), fleetMap(), userMap()]);
    Object.assign(state, { reports, fleet, users });
    draw();
  }

  function draw() {
    const by = (s) => state.reports.filter((r) => r.status === s).length;
    $('#tabs', el).innerHTML = segmented([
      ['UNDER_REVIEW', 'Under review', by('UNDER_REVIEW')],
      ['RESOLVED', 'Resolved', by('RESOLVED')],
      ['all', 'All', state.reports.length],
    ], state.tab);
    const list = state.tab === 'all' ? state.reports : state.reports.filter((r) => r.status === state.tab);
    const host = $('#list', el);
    if (!list.length) {
      host.innerHTML = empty({ icon: 'shieldAlert', title: 'No damage reports here', text: 'Reports are created during a return handover or from this page.' });
      return;
    }
    host.innerHTML = dataList({
      cols: '56px minmax(175px,1.3fr) minmax(175px,1.5fr) minmax(105px,.8fr) minmax(100px,.8fr) minmax(215px,auto)',
      headers: ['#', 'Vehicle', 'Damage', 'Reported', 'Repair est.', ''],
      rows: list.map((d, i) => row([
        cell('Report', `<span class="id-pill">#${d.eventId}</span>`),
        cell('Vehicle', vehicleCell(state.fleet.get(d.vehicleId), d.vehicleId), 'cell--wide'),
        cell('Damage', `<b>${esc(d.description)}</b><small>${esc(humanize(d.damageSeverity))}${d.handoverId ? ` · handover #${d.handoverId}` : ''}</small>`, 'cell--wide'),
        cell('Reported', `<b>${fmtDate(d.eventDate)}</b><small>${d.damageDate && d.damageDate !== d.eventDate ? `happened ${fmtDate(d.damageDate)}` : esc(state.users.get(d.reportedBy)?.fullName || '')}</small>`),
        cell('Repair est.', `<b>${money(d.estimatedRepairCost)}</b>`),
        actionsCell(`${statusChip(d.status)}${d.status === 'UNDER_REVIEW'
          ? `${admin ? `<button class="btn btn--sm btn--tonal" data-dmg="claim" data-id="${d.eventId}">${icon('receipt', 16)} File claim</button>` : ''}
             <button class="btn btn--sm" data-dmg="resolve" data-id="${d.eventId}">${icon('check', 16)} Resolve</button>`
          : `<span class="muted" style="font-size:12px">Closed</span>
             <button class="icon-btn icon-btn--sm" data-dmg="delete" data-id="${d.eventId}" aria-label="Delete" title="Delete">${icon('trash', 16)}</button>`}`),
      ], i)),
    });
  }

  el.addEventListener('click', async (e) => {
    const tab = e.target.closest('[data-seg]');
    if (tab) { state.tab = tab.dataset.seg; draw(); return; }
    const btn = e.target.closest('[data-dmg]');
    if (!btn) return;
    const id = Number(btn.dataset.id);
    const report = state.reports.find((r) => r.eventId === id);

    switch (btn.dataset.dmg) {
      case 'add': {
        const fleet = await getFleet();
        if (await damageDialog(fleet)) await load();
        break;
      }
      case 'resolve': {
        const blocking = report && ['MODERATE', 'SEVERE'].includes(report.damageSeverity);
        const reason = await promptDialog({
          title: `Resolve report #${id}?`,
          text: blocking
            ? 'The vehicle goes back on the road, unless something else is still holding it.'
            : 'Closing this report puts it in the vehicle history.',
          label: 'How was it resolved?',
          placeholder: 'e.g. Bumper replaced at Colombo Motors',
          confirmLabel: 'Resolve report',
        });
        if (reason === null) break;
        if (await runAction(btn, () => api.damage.setStatus(id, 'RESOLVED', reason), 'Report resolved')) await load();
        break;
      }
      case 'delete': {
        const ok = await confirmDialog({ title: `Delete report #${id}?`, text: 'Only resolved reports with no claims and no link to a rental handover can be deleted.', confirmLabel: 'Delete', tone: 'danger' });
        if (ok && await runAction(null, () => api.damage.remove(id), 'Report deleted')) await load();
        break;
      }
      case 'claim':
        if (report && await claimDialog([report], state.fleet, report)) {
          toast('Claim submitted', 'success');
        }
        break;
      default:
    }
  });

  await load();
}

function damageDialog(fleet) {
  return openDialog({
    title: 'Report damage',
    subtitle: 'Log damage found outside a return handover.',
    submitLabel: 'File report',
    wide: true,
    body: `<div class="form-grid">
      ${field({ name: 'vehicleId', label: 'Vehicle', type: 'select', options: vehicleOptions(fleet), required: true, span: true })}
      ${field({ name: 'description', label: 'What is damaged?', type: 'textarea', required: true, span: true })}
      ${field({ name: 'damageSeverity', label: 'Severity', type: 'select', value: 'MINOR', options: SEVERITIES, required: true })}
      ${field({ name: 'estimatedRepairCost', label: 'Repair estimate (LKR)', type: 'number', min: 0, step: '0.01', required: true })}
      ${field({ name: 'damageDate', label: 'When it happened', type: 'date', value: isoDate(), max: isoDate(), required: true })}
      ${field({ name: 'handoverId', label: 'Handover # (optional)', type: 'number', min: 1, span: true, hint: 'Links the damage to a rental so the customer is notified and billed' })}
    </div>
    <div class="notice">${icon('info', 18)}<span>Moderate and severe damage takes the vehicle off the road until this report is resolved.</span></div>`,
    async onSubmit(values) {
      await api.damage.create({ ...values, vehicleId: Number(values.vehicleId) });
      toast('Damage report filed', 'success');
    },
  });
}

async function claimDialog(reports, fleet, preselected) {
  const policies = await api.insurance.list().catch(() => []);
  const providerFor = (vehicleId) => policies.find((p) => p.vehicleId === vehicleId && p.status === 'ACTIVE')?.provider || '';
  const options = [{ value: '', label: 'Select a damage report' }, ...reports.map((r) => ({
    value: r.eventId,
    label: `#${r.eventId} · ${fleet.get(r.vehicleId)?.model || `Vehicle ${r.vehicleId}`} · ${humanize(r.damageSeverity)}`,
  }))];
  return openDialog({
    title: 'File an insurance claim',
    subtitle: 'The provider is pre-filled from the vehicle’s active policy when there is one.',
    submitLabel: 'Submit claim',
    body: `<div class="form-grid">
      ${field({ name: 'damageReportId', label: 'Damage report', type: 'select', options, value: preselected?.eventId ?? '', required: true, span: true })}
      ${field({ name: 'insuranceProvider', label: 'Insurance provider', value: preselected ? providerFor(preselected.vehicleId) : '', required: true })}
      ${field({ name: 'claimAmount', label: 'Claim amount (LKR)', type: 'number', min: 0.01, step: '0.01', max: preselected?.estimatedRepairCost ?? undefined, value: preselected?.estimatedRepairCost ?? '', required: true, hint: preselected ? `Cannot exceed the repair estimate of ${money(preselected.estimatedRepairCost)}` : 'Cannot exceed the repair estimate' })}
    </div>`,
    onOpen(form) {
      form.elements.damageReportId.addEventListener('change', () => {
        const r = reports.find((x) => String(x.eventId) === form.elements.damageReportId.value);
        if (!r) return;
        form.elements.insuranceProvider.value = providerFor(r.vehicleId) || form.elements.insuranceProvider.value;
        if (r.estimatedRepairCost != null) form.elements.claimAmount.value = r.estimatedRepairCost;
      });
    },
    onSubmit: (values) => api.claims.create({ ...values, damageReportId: Number(values.damageReportId) }),
  });
}

// ==================================================================
// Insurance claims (administrator)
// ==================================================================
export async function renderClaims({ el, query }) {
  const TABS = ['SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED', 'all'];
  const state = { tab: TABS.includes(query.tab) ? query.tab : 'SUBMITTED', claims: [], reports: new Map(), fleet: new Map() };

  el.innerHTML = `
    ${consoleHead({
      eyebrow: 'Insurance', title: 'Claims',
      text: 'Track claims filed against damage reports through to a decision.',
      actions: `<button class="btn" data-claim="add">${icon('plus', 18)} New claim</button>`,
    })}
    <div class="kpi-grid" id="claim-kpis" style="margin-bottom:22px"></div>
    <div class="toolbar"><div id="tabs"></div></div>
    <div id="list">${skeletons(1, '')}</div>`;

  async function load() {
    const [claims, reports, fleet] = await Promise.all([api.claims.list(), api.damage.list(), fleetMap()]);
    Object.assign(state, { claims, fleet, reports: new Map(reports.map((r) => [r.eventId, r])) });
    draw();
  }

  function draw() {
    const by = (s) => state.claims.filter((c) => c.status === s);
    const sum = (list) => list.reduce((s, c) => s + Number(c.claimAmount || 0), 0);
    $('#claim-kpis', el).innerHTML = `
      <div class="kpi kpi--dark"><div class="kpi__top"><span class="kpi__label">${icon('clock', 18)} Open claims</span></div><div class="kpi__value">${by('SUBMITTED').length}<small>${money(sum(by('SUBMITTED')))}</small></div></div>
      <div class="kpi"><div class="kpi__top"><span class="kpi__label">${icon('check', 18)} Approved value</span></div><div class="kpi__value" style="font-size:30px">${money(sum(by('APPROVED')))}</div></div>
      <div class="kpi"><div class="kpi__top"><span class="kpi__label">${icon('receipt', 18)} All claims</span></div><div class="kpi__value">${state.claims.length}</div></div>`;
    $('#tabs', el).innerHTML = segmented(TABS.map((t) => [t, t === 'all' ? 'All' : humanize(t), t === 'all' ? state.claims.length : by(t).length]), state.tab);

    const list = state.tab === 'all' ? state.claims : by(state.tab);
    const host = $('#list', el);
    if (!list.length) {
      host.innerHTML = empty({ icon: 'receipt', title: 'No claims here', text: 'File a claim from a damage report under review.' });
      return;
    }
    host.innerHTML = dataList({
      cols: '56px minmax(185px,1.4fr) minmax(130px,1fr) minmax(105px,.8fr) minmax(100px,.8fr) minmax(215px,auto)',
      headers: ['#', 'Damage report', 'Provider', 'Amount', 'Submitted', ''],
      rows: list.map((c, i) => {
        const r = state.reports.get(c.damageReportId);
        return row([
          cell('Claim', `<span class="id-pill">#${c.claimId}</span>`),
          cell('Damage report', r
            ? `<div class="with-art"><div class="mini-car">${vehicleVisual(state.fleet.get(r.vehicleId) || { plateNumber: String(r.vehicleId) })}</div><div><b>#${r.eventId} · ${esc(state.fleet.get(r.vehicleId)?.model || `Vehicle ${r.vehicleId}`)}</b><small>${esc(r.description)}</small></div></div>`
            : `<b>Report #${c.damageReportId}</b>`, 'cell--wide'),
          cell('Provider', `<b>${esc(c.insuranceProvider || '—')}</b>`),
          cell('Amount', `<b>${money(c.claimAmount, { decimals: true })}</b>`),
          cell('Submitted', `<b>${fmtDate(c.submittedDate)}</b>`),
          actionsCell(`${statusChip(c.status)}${c.status === 'SUBMITTED'
            ? `<button class="btn btn--sm btn--text" data-claim="CANCELLED" data-id="${c.claimId}">Withdraw</button>
               <button class="btn btn--sm btn--outline" data-claim="REJECTED" data-id="${c.claimId}">Rejected</button>
               <button class="btn btn--sm btn--success" data-claim="APPROVED" data-id="${c.claimId}">Approved</button>`
            : ['REJECTED', 'CANCELLED'].includes(c.status)
              ? `<button class="icon-btn icon-btn--sm" data-claim="delete" data-id="${c.claimId}" aria-label="Delete" title="Delete">${icon('trash', 16)}</button>`
              : ''}`),
        ], i);
      }),
    });
  }

  el.addEventListener('click', async (e) => {
    const tab = e.target.closest('[data-seg]');
    if (tab) { state.tab = tab.dataset.seg; draw(); return; }
    const btn = e.target.closest('[data-claim]');
    if (!btn) return;
    const id = Number(btn.dataset.id);
    const action = btn.dataset.claim;

    if (action === 'add') {
      const open = [...state.reports.values()].filter((r) => r.status === 'UNDER_REVIEW');
      if (!open.length) { toast('There are no damage reports under review to claim against.', 'error'); return; }
      if (await claimDialog(open, state.fleet)) { toast('Claim submitted', 'success'); await load(); }
    } else if (action === 'delete') {
      const ok = await confirmDialog({ title: `Delete claim #${id}?`, text: 'The claim is removed permanently.', confirmLabel: 'Delete', tone: 'danger' });
      if (ok && await runAction(null, () => api.claims.remove(id), 'Claim deleted')) await load();
    } else {
      const reason = await promptDialog({
        title: `Mark claim #${id} as ${humanize(action).toLowerCase()}?`,
        text: action === 'APPROVED'
          ? "A decided claim is final. Whatever the insurer pays comes off the customer's bill for that rental."
          : 'A decided claim cannot move back to submitted.',
        label: 'Note for the record',
        placeholder: 'e.g. Insurer reference AB-2291',
        confirmLabel: `Mark ${humanize(action).toLowerCase()}`,
        tone: action === 'APPROVED' ? 'success' : action === 'REJECTED' ? 'danger' : '',
      });
      if (reason === null) return;
      if (await runAction(null, () => api.claims.setStatus(id, action, reason), 'Claim updated')) await load();
    }
  });

  await load();
}

