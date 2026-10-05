const API_ORIGIN = (import.meta.env.VITE_API_URL || '').replace(/\/+$/, '');
const BASE = `${API_ORIGIN}/api/v1`;
const SESSION_KEY = 'tickethub.session';

function readSession() {
  try {
    return JSON.parse(sessionStorage.getItem(SESSION_KEY) || 'null');
  } catch {
    return null;
  }
}

const storedSession = readSession();
let accessToken = storedSession?.accessToken || null;
let refreshToken = storedSession?.refreshToken || null;

function persistSession() {
  if (accessToken || refreshToken) {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify({ accessToken, refreshToken }));
  } else {
    sessionStorage.removeItem(SESSION_KEY);
  }
}

export function setTokens(tokens) {
  accessToken = tokens.accessToken;
  refreshToken = tokens.refreshToken;
  persistSession();
}

export function setToken(token) {
  accessToken = token;
  if (!token) {
    refreshToken = null;
    persistSession();
  } else {
    persistSession();
  }
}

export function clearSession() {
  accessToken = null;
  refreshToken = null;
  persistSession();
}

export function hasSession() {
  return Boolean(accessToken || refreshToken);
}

async function refreshSession() {
  if (!refreshToken) return false;

  try {
    const response = await fetch(`${BASE}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });

    if (!response.ok) {
      clearSession();
      return false;
    }

    setTokens(await response.json());
    return true;
  } catch {
    clearSession();
    return false;
  }
}

async function request(path, { method = 'GET', body, auth = false, retry = true } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`;

  const response = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 401 && auth && retry && refreshToken) {
    const refreshed = await refreshSession();
    if (refreshed) {
      return request(path, { method, body, auth, retry: false });
    }
  }

  if (response.status === 204) return null;

  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(payload.detail || 'Request failed');
    error.code = payload.code;
    error.unavailableSeatIds = payload.unavailableSeatIds || [];
    error.status = response.status;
    throw error;
  }
  return payload;
}

export const api = {
  register: (data) => request('/auth/register', { method: 'POST', body: data }),
  login: (data) => request('/auth/login', { method: 'POST', body: data }),
  refresh: refreshSession,
  me: () => request('/users/me', { auth: true }),
  logout: async () => {
    try {
      if (accessToken) {
        await request('/auth/logout', { method: 'POST', auth: true, retry: false });
      }
    } finally {
      clearSession();
    }
  },

  events: (q = '') => request(`/events?q=${encodeURIComponent(q)}&size=20`),
  shows: (eventId) => request(`/events/${eventId}/shows`),
  seatMap: (showId) => request(`/shows/${showId}/seats`),
  hold: (showId, seatIds) =>
    request(`/shows/${showId}/holds`, { method: 'POST', body: { seatIds }, auth: true }),
  startPayment: (ref) =>
    request(`/bookings/${ref}/payments`, { method: 'POST', auth: true }),
  simulatePayment: (ref) =>
    request(`/dev/bookings/${ref}/simulate-payment`, { method: 'POST', auth: true }),
  myBookings: () => request('/bookings', { auth: true }),
  booking: (ref) => request(`/bookings/${ref}`, { auth: true }),

  adminVenues: () => request('/admin/venues', { auth: true }),
  adminHalls: (venueId) => request(`/admin/venues/${venueId}/halls`, { auth: true }),
  adminCreateVenue: (data) =>
    request('/admin/venues', { method: 'POST', body: data, auth: true }),
  adminCreateHall: (venueId, data) =>
    request(`/admin/venues/${venueId}/halls`, { method: 'POST', body: data, auth: true }),
  adminCreateEvent: (data) =>
    request('/admin/events', { method: 'POST', body: data, auth: true }),
  adminUpdateEvent: (id, data) =>
    request(`/admin/events/${id}`, { method: 'PUT', body: data, auth: true }),
  adminArchiveEvent: (id) =>
    request(`/admin/events/${id}`, { method: 'DELETE', auth: true }),
  adminScheduleShow: (data) =>
    request('/admin/shows', { method: 'POST', body: data, auth: true }),
  adminCancelShow: (id) =>
    request(`/admin/shows/${id}/cancel`, { method: 'POST', auth: true }),
  adminSalesReport: () => request('/admin/reports/sales', { auth: true }),
  adminOccupancy: (showId) =>
    request(`/admin/reports/occupancy?showId=${encodeURIComponent(showId)}`, { auth: true }),
};

export function subscribeToSeats(showId, onUpdate) {
  const source = new EventSource(`${BASE}/shows/${showId}/seats/stream`);
  source.addEventListener('seat-update', (event) => onUpdate(JSON.parse(event.data)));
  source.onerror = () => source.close();
  return () => source.close();
}
