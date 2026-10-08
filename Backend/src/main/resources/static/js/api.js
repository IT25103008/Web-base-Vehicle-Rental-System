// ============================================================
// API client — one function per backend endpoint.
// The frontend is served by Spring Boot itself, so every call is
// same-origin and the JSESSIONID cookie is sent automatically.
//
// Every request that changes something also carries the CSRF token:
// the server hands it over in the XSRF-TOKEN cookie, and it is echoed
// back here in the X-XSRF-TOKEN header. A page on another site can make
// the browser send the cookie, but it cannot read it - so it cannot
// forge the header.
// ============================================================

export class ApiError extends Error {
  constructor(status, message, body) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

const FALLBACK_MESSAGES = {
  0: 'Cannot reach the server. Is the backend running?',
  400: 'The request was not valid.',
  401: 'Please sign in to continue.',
  403: "You don't have permission for this action.",
  404: 'We could not find what you were looking for.',
  409: 'This conflicts with existing data.',
  413: 'That file is too large.',
  500: 'Something went wrong on the server.',
  503: 'The service is temporarily unavailable.',
};

const SAFE = new Set(['GET', 'HEAD', 'OPTIONS']);

function buildUrl(path, query) {
  const url = new URL(path, window.location.origin);
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, value);
    });
  }
  return url;
}

function csrfToken() {
  const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return m ? decodeURIComponent(m[1]) : null;
}

/** Makes sure the CSRF cookie exists (any GET issues it). */
async function ensureCsrf() {
  if (csrfToken()) return;
  try { await fetch(buildUrl('/api/auth/me'), { credentials: 'same-origin' }); } catch { /* offline */ }
}

async function request(method, path, { body, query } = {}, retried = false) {
  if (!SAFE.has(method)) await ensureCsrf();
  const token = csrfToken();
  let res;
  try {
    res = await fetch(buildUrl(path, query), {
      method,
      credentials: 'same-origin',
      headers: {
        Accept: 'application/json, text/plain',
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(!SAFE.has(method) && token ? { 'X-XSRF-TOKEN': token } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, FALLBACK_MESSAGES[0]);
  }

  const text = await res.text();
  let data = text;
  if (text) {
    try { data = JSON.parse(text); } catch { /* plain-text body */ }
  } else {
    data = null;
  }

  if (!res.ok) {
    const message = (data && typeof data === 'object' && data.message)
      || FALLBACK_MESSAGES[res.status]
      || `Request failed (${res.status})`;
    // A token that went stale (the session changed under us): fetch a fresh
    // one and try once more before giving up.
    if (res.status === 403 && !retried && !SAFE.has(method) && /security token/i.test(message)) {
      document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
      await ensureCsrf();
      return request(method, path, { body, query }, true);
    }
    if (res.status === 401 && !path.startsWith('/api/auth/')) {
      window.dispatchEvent(new CustomEvent('auth:expired'));
    }
    throw new ApiError(res.status, message, data);
  }
  return data;
}

/**
 * Multipart upload. The Content-Type header is deliberately left unset: the
 * browser has to add it itself so it can include the multipart boundary.
 */
async function upload(path, file, field = 'file') {
  await ensureCsrf();
  const form = new FormData();
  form.append(field, file);
  const token = csrfToken();
  let res;
  try {
    res = await fetch(buildUrl(path), {
      method: 'POST', credentials: 'same-origin', body: form,
      headers: token ? { 'X-XSRF-TOKEN': token } : {},
    });
  } catch {
    throw new ApiError(0, FALLBACK_MESSAGES[0]);
  }
  const text = await res.text();
  let data = text || null;
  if (text) { try { data = JSON.parse(text); } catch { /* plain-text body */ } }
  if (!res.ok) {
    // A 404 here means the path reached no controller. The pages are read from
    // disk on every request but the endpoints are fixed when the server starts,
    // so a server left running across a rebuild serves the new screens with the
    // old API. Saying that is far more use than "not found".
    const message = res.status === 404
      ? 'The server has no upload endpoint. If the code was rebuilt while it was running, restart it — a running server keeps its old endpoints but still serves the new pages.'
      : (data && typeof data === 'object' && data.message)
        || FALLBACK_MESSAGES[res.status]
        || `Upload failed (${res.status})`;
    if (res.status === 401) window.dispatchEvent(new CustomEvent('auth:expired'));
    throw new ApiError(res.status, message, data);
  }
  return data;
}

const get = (path, query) => request('GET', path, { query });
const post = (path, body, query) => request('POST', path, { body, query });
const put = (path, body) => request('PUT', path, { body });
const patch = (path, query, body) => request('PATCH', path, { query, body });
const del = (path, body) => request('DELETE', path, { body });
/** Reasons go in the request body, never the URL (they would land in logs and history). */
const why = (reason) => ({ reason: reason || null });

export const api = {
  auth: {
    me: () => get('/api/auth/me'),
    login: (email, password) => post('/api/auth/login', { email, password }),
    loginCode: (code) => post('/api/auth/login/2fa', { code }),
    register: (payload) => post('/api/auth/register', payload),
    logout: () => post('/api/auth/logout'),
    forgotPassword: (email) => post('/api/auth/forgot-password', { email }),
    resetPassword: (token, password) => post('/api/auth/reset-password', { token, password }),
    verifyEmail: (token) => post('/api/auth/verify-email', { token }),
    twoFactorSetup: () => post('/api/auth/2fa/setup'),
    twoFactorEnable: (code) => post('/api/auth/2fa/enable', { code }),
    twoFactorDisable: (password) => post('/api/auth/2fa/disable', { password }),
  },

  // The signed-in person's own account.
  me: {
    get: () => get('/api/users/me'),
    update: (payload) => put('/api/users/me', payload),
    changePassword: (currentPassword, newPassword) => patch('/api/users/me/password', null, { currentPassword, newPassword }),
    sessions: () => get('/api/users/me/sessions'),
    endOtherSessions: () => post('/api/users/me/sessions/end-others'),
    resendVerification: () => post('/api/users/me/verification-email'),
    exportData: () => get('/api/users/me/export'),
    erase: (password) => del('/api/users/me', { password }),
    favourites: () => get('/api/users/me/favourites'),
    addFavourite: (vehicleId) => put(`/api/users/me/favourites/${vehicleId}`),
    removeFavourite: (vehicleId) => del(`/api/users/me/favourites/${vehicleId}`),
    mergeFavourites: (ids) => post('/api/users/me/favourites/merge', ids),
    preferences: () => get('/api/users/me/notification-preferences'),
    savePreferences: (prefs) => put('/api/users/me/notification-preferences', prefs),
  },

  users: {
    list: (role) => get('/api/users', { role }),
    page: (query) => get('/api/users/page', query),
    get: (id) => get(`/api/users/${id}`),
    createStaff: (payload) => post('/api/users/staff', payload),
    createAdministrator: (payload) => post('/api/users/administrators', payload),
    verifyLicense: (id) => patch(`/api/users/${id}/verify-license`),
    unverifyLicense: (id, reason) => patch(`/api/users/${id}/unverify-license`, null, why(reason)),
    disable: (id, reason) => patch(`/api/users/${id}/disable`, null, why(reason)),
    enable: (id, reason) => patch(`/api/users/${id}/enable`, null, why(reason)),
  },

  branches: {
    list: () => get('/api/branches'),
    get: (id) => get(`/api/branches/${id}`),
    create: (payload) => post('/api/branches', payload),
    update: (id, payload) => put(`/api/branches/${id}`, payload),
    deactivate: (id) => patch(`/api/branches/${id}/deactivate`),
    activate: (id) => patch(`/api/branches/${id}/activate`),
    remove: (id) => del(`/api/branches/${id}`),
  },

  transfers: {
    list: () => get('/api/branches/transfers'),
    pending: () => get('/api/branches/transfers/pending'),
    create: (payload) => post('/api/branches/transfers', payload),
    complete: (id) => patch(`/api/branches/transfers/${id}/complete`),
    cancel: (id) => patch(`/api/branches/transfers/${id}/cancel`),
  },

  vehicles: {
    list: (query) => get('/api/vehicles', query),
    page: (query) => get('/api/vehicles/page', query),
    catalogue: () => get('/api/vehicles/catalogue'),
    get: (id) => get(`/api/vehicles/${id}`),
    search: (pickup, ret, branchId, filters = {}) => get('/api/vehicles/search', { pickup, return: ret, branchId, ...filters }),
    busy: (id, query) => get(`/api/vehicles/${id}/busy`, query),
    nextFree: (id, nights, from) => get(`/api/vehicles/${id}/next-free`, { nights, from }),
    create: (payload) => post('/api/vehicles', payload),
    update: (id, payload) => put(`/api/vehicles/${id}`, payload),
    setStatus: (id, status, reason) => patch(`/api/vehicles/${id}/status`, { status }, why(reason)),
    uploadImage: (id, file) => upload(`/api/vehicles/${id}/image`, file),
    removeImage: (id) => del(`/api/vehicles/${id}/image`),
    gallery: (id) => get(`/api/vehicles/${id}/images`),
    addGalleryImage: (id, file) => upload(`/api/vehicles/${id}/images`, file),
    removeGalleryImage: (id, imageId) => del(`/api/vehicles/${id}/images/${imageId}`),
    remove: (id) => del(`/api/vehicles/${id}`),
  },

  bookings: {
    create: (payload) => post('/api/bookings', payload),
    get: (id) => get(`/api/bookings/${id}`),
    payment: (id) => get(`/api/bookings/${id}/payment`),
    charges: (id) => get(`/api/bookings/${id}/charges`),
    mine: () => get('/api/bookings/mine'),
    pending: () => get('/api/bookings/pending'),
    all: () => get('/api/bookings'),
    page: (query) => get('/api/bookings/page', query),
    timeline: (query) => get('/api/bookings/timeline', query),
    overdue: () => get('/api/bookings/overdue'),
    missedPickups: () => get('/api/bookings/missed-pickups'),
    approve: (id) => patch(`/api/bookings/${id}/approve`),
    reject: (id, reason) => patch(`/api/bookings/${id}/reject`, null, why(reason)),
    bulk: (action, ids, reason) => post('/api/bookings/bulk', { action, ids, reason }),
    cancel: (id, reason) => patch(`/api/bookings/${id}/cancel`, null, why(reason)),
    staffCancel: (id, reason) => patch(`/api/bookings/${id}/staff-cancel`, null, why(reason)),
    noShow: (id, reason) => patch(`/api/bookings/${id}/no-show`, null, why(reason)),
    changeDates: (id, pickup, ret) => patch(`/api/bookings/${id}/dates`, { pickup, return: ret }),
    extend: (id, ret) => patch(`/api/bookings/${id}/extend`, { return: ret }),
    remove: (id, reason) => del(`/api/bookings/${id}`, why(reason)),
  },

  handovers: {
    pickup: (bookingId, payload) => post(`/api/handovers/pickup/${bookingId}`, payload),
    returnVehicle: (bookingId, payload) => post(`/api/handovers/return/${bookingId}`, payload),
    active: () => get('/api/handovers/active'),
    byBooking: (bookingId) => get(`/api/handovers/booking/${bookingId}`),
  },

  maintenance: {
    list: () => get('/api/maintenance'),
    byVehicle: (vehicleId) => get(`/api/maintenance/vehicle/${vehicleId}`),
    reminders: (days = 30) => get('/api/maintenance/reminders', { days }),
    create: (payload) => post('/api/maintenance', payload),
    update: (id, payload) => put(`/api/maintenance/${id}`, payload),
    setStatus: (id, status) => patch(`/api/maintenance/${id}/status`, { status }),
    start: (id) => patch(`/api/maintenance/${id}/status`, { status: 'IN_PROGRESS' }),
    complete: (id) => patch(`/api/maintenance/${id}/complete`),
    remove: (id) => del(`/api/maintenance/${id}`),
  },

  insurance: {
    list: (vehicleId) => get('/api/insurance-policies', { vehicleId }),
    expiring: (days = 30) => get('/api/insurance-policies/expiring', { days }),
    create: (payload) => post('/api/insurance-policies', payload),
    update: (id, payload) => put(`/api/insurance-policies/${id}`, payload),
    remove: (id) => del(`/api/insurance-policies/${id}`),
  },

  damage: {
    list: (status) => get('/api/damage-reports', { status }),
    byVehicle: (vehicleId) => get(`/api/damage-reports/vehicle/${vehicleId}`),
    create: (payload) => post('/api/damage-reports', payload),
    setStatus: (id, status, reason) => patch(`/api/damage-reports/${id}/status`, { status, reason }),
    remove: (id) => del(`/api/damage-reports/${id}`),
  },

  claims: {
    list: (status) => get('/api/claims', { status }),
    create: (payload) => post('/api/claims', payload),
    setStatus: (id, status, reason) => patch(`/api/claims/${id}/status`, { status, reason }),
    remove: (id) => del(`/api/claims/${id}`),
  },

  payments: {
    list: (status) => get('/api/payments', { status }),
    page: (query) => get('/api/payments/page', query),
    byBooking: (bookingId) => get(`/api/payments/booking/${bookingId}`),
    markPaid: (id) => patch(`/api/payments/${id}/paid`),
    markPending: (id, reason) => patch(`/api/payments/${id}/pending`, null, why(reason)),
  },

  notifications: {
    mine: () => get('/api/notifications/mine'),
    unread: () => get('/api/notifications/mine/unread'),
    markRead: (id) => patch(`/api/notifications/${id}/read`),
    markAllRead: () => patch('/api/notifications/mine/read-all'),
    remove: (id) => del(`/api/notifications/${id}`),
  },

  audit: {
    history: (entityType, entityId) => get(`/api/audit/${entityType}/${entityId}`),
    recent: (limit = 100) => get('/api/audit', { limit }),
  },

  reminders: {
    run: () => post('/api/reminders/run'),
  },

  dashboard: (branchId) => get('/api/dashboard', { branchId }),
};
