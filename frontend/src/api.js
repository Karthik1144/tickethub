// Empty in development (the Vite dev proxy forwards /api to the backend).
// In production it is the deployed backend origin, injected at build time.
const API_ORIGIN = (import.meta.env.VITE_API_URL || '').replace(/\/+$/, '');
const BASE = `${API_ORIGIN}/api/v1`;

let accessToken = null;
export const setToken = (token) => { accessToken = token; };
export const getToken = () => accessToken;

async function request(path, { method = 'GET', body, auth = false } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`;

  const response = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  });

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
  events: (q = '') => request(`/events?q=${encodeURIComponent(q)}&size=20`),
  shows: (eventId) => request(`/events/${eventId}/shows`),
  seatMap: (showId) => request(`/shows/${showId}/seats`),
  hold: (showId, seatIds) => request(`/shows/${showId}/holds`, { method: 'POST', body: { seatIds }, auth: true }),
  startPayment: (ref) => request(`/bookings/${ref}/payments`, { method: 'POST', auth: true }),
  simulatePayment: (ref) => request(`/dev/bookings/${ref}/simulate-payment`, { method: 'POST', auth: true }),
  myBookings: () => request('/bookings', { auth: true }),
  booking: (ref) => request(`/bookings/${ref}`, { auth: true })
};

/** Live seat updates. Returns an unsubscribe function. */
export function subscribeToSeats(showId, onUpdate) {
  const source = new EventSource(`${BASE}/shows/${showId}/seats/stream`);
  source.addEventListener('seat-update', (event) => onUpdate(JSON.parse(event.data)));
  source.onerror = () => source.close();
  return () => source.close();
}
