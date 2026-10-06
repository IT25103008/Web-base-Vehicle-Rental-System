// ============================================================
// Session + small in-memory cache for lookup data
// (branches, vehicles, users) used to label rows.
// ============================================================
import { api } from './api.js';

export const store = {
  user: null,
  unread: 0,
};

const cache = new Map();

function cached(key, loader) {
  if (!cache.has(key)) {
    const promise = loader().catch((err) => { cache.delete(key); throw err; });
    cache.set(key, promise);
  }
  return cache.get(key);
}

export function invalidate(...keys) {
  if (keys.length === 0) cache.clear();
  keys.forEach((k) => {
    Array.from(cache.keys()).forEach((existing) => {
      if (existing === k || existing.startsWith(`${k}:`)) cache.delete(existing);
    });
  });
}

export async function loadUser() {
  try {
    store.user = await api.auth.me();
  } catch {
    store.user = null;
  }
  return store.user;
}

export const role = () => store.user?.role || null;
export const isCustomer = () => role() === 'CUSTOMER';
export const isStaff = () => role() === 'STAFF' || role() === 'ADMINISTRATOR';
export const isAdmin = () => role() === 'ADMINISTRATOR';
export const firstName = () => (store.user?.fullName || '').split(' ')[0] || 'there';

export const getBranches = () => cached('branches', api.branches.list);

export async function branchMap() {
  const list = await getBranches();
  return new Map(list.map((b) => [b.branchId, b]));
}

/** Staff/admin: whole fleet. */
export const getFleet = () => cached('fleet', () => api.vehicles.list());

export async function fleetMap() {
  const list = await getFleet();
  return new Map(list.map((v) => [v.vehicleId, v]));
}

/** Any user: a single vehicle (public endpoint). */
export const getVehicle = (id) => cached(`vehicle:${id}`, () => api.vehicles.get(id));

/** Staff/admin: users by id. */
export async function userMap() {
  const list = await cached('users', () => api.users.list());
  return new Map(list.map((u) => [u.userId, u]));
}

export async function refreshUnread() {
  if (!store.user) { store.unread = 0; return 0; }
  try {
    const list = await api.notifications.unread();
    store.unread = list.length;
  } catch {
    store.unread = 0;
  }
  return store.unread;
}
