import React, { useEffect, useState } from 'react';
import { api } from './api.js';
import './auth-admin.css';

const CATEGORIES = ['MOVIE', 'CONCERT', 'SPORTS', 'THEATRE'];

const initialVenue = { name: '', city: '', address: '' };
const initialEvent = {
  title: '',
  description: '',
  category: 'MOVIE',
  language: 'English',
  durationMin: 120,
  posterUrl: '',
};
const initialHall = { name: '', rows: 8, seatsPerRow: 12 };
const initialShow = { eventId: '', venueId: '', hallId: '', startTime: '', basePrice: 500 };

function toIsoDateTime(value) {
  return value ? new Date(value).toISOString() : '';
}

export default function AdminDashboard({ user, onNavigate, onLogout }) {
  const [venues, setVenues] = useState([]);
  const [halls, setHalls] = useState([]);
  const [events, setEvents] = useState([]);
  const [sales, setSales] = useState(null);
  const [venueForm, setVenueForm] = useState(initialVenue);
  const [eventForm, setEventForm] = useState(initialEvent);
  const [hallForm, setHallForm] = useState(initialHall);
  const [showForm, setShowForm] = useState(initialShow);
  const [showId, setShowId] = useState('');
  const [occupancy, setOccupancy] = useState(null);
  const [message, setMessage] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = async () => {
    try {
      const [venueRows, eventPage, salesReport] = await Promise.all([
        api.adminVenues(),
        api.events(''),
        api.adminSalesReport(),
      ]);
      setVenues(venueRows);
      setEvents(eventPage.content);
      setSales(salesReport);
    } catch (error) {
      setMessage({ type: 'error', text: error.message || 'Unable to load admin data.' });
    }
  };

  useEffect(() => {
    load();
  }, []);

  const loadHalls = async (venueId) => {
    setShowForm((current) => ({ ...current, venueId, hallId: '' }));
    if (!venueId) {
      setHalls([]);
      return;
    }
    try {
      setHalls(await api.adminHalls(venueId));
    } catch (error) {
      setMessage({ type: 'error', text: error.message });
    }
  };

  const submit = async (action, successMessage) => {
    setBusy(true);
    setMessage(null);
    try {
      await action();
      setMessage({ type: 'success', text: successMessage });
      await load();
    } catch (error) {
      setMessage({ type: 'error', text: error.message || 'Request failed.' });
    } finally {
      setBusy(false);
    }
  };

  const createVenue = () => submit(
    () => api.adminCreateVenue(venueForm),
    'Venue created successfully.',
  ).then(() => setVenueForm(initialVenue));

  const createEvent = () => submit(
    () => api.adminCreateEvent({
      ...eventForm,
      durationMin: Number(eventForm.durationMin),
    }),
    'Event created successfully.',
  ).then(() => setEventForm(initialEvent));

  const createHall = () => submit(
    () => api.adminCreateHall(showForm.venueId, {
      ...hallForm,
      rows: Number(hallForm.rows),
      seatsPerRow: Number(hallForm.seatsPerRow),
    }),
    'Hall created and seats generated.',
  ).then(async () => {
    setHallForm(initialHall);
    await loadHalls(showForm.venueId);
  });

  const scheduleShow = () => submit(
    () => api.adminScheduleShow({
      eventId: Number(showForm.eventId),
      hallId: Number(showForm.hallId),
      startTime: toIsoDateTime(showForm.startTime),
      basePrice: Number(showForm.basePrice),
      priceMultipliers: {
        STANDARD: 1,
        PREMIUM: 1.25,
        VIP: 1.5,
      },
    }),
    'Show scheduled and seats generated.',
  ).then(() => setShowForm(initialShow));

  const archiveEvent = (eventId) => submit(
    () => api.adminArchiveEvent(eventId),
    'Event archived.',
  );

  const checkOccupancy = async () => {
    setBusy(true);
    try {
      setOccupancy(await api.adminOccupancy(showId));
      setMessage({ type: 'success', text: 'Occupancy report loaded.' });
    } catch (error) {
      setMessage({ type: 'error', text: error.message });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="admin-page">
      <header className="admin-header">
        <div>
          <span className="eyebrow">TICKETHUB ADMIN</span>
          <h1>Operations dashboard</h1>
          <p>Manage the catalogue, shows and reporting from one place.</p>
        </div>
        <div className="admin-header-actions">
          <span className="admin-user">{user.fullName} · {user.email}</span>
          <button type="button" className="secondary-button" onClick={() => onNavigate('home')}>View site</button>
          <button type="button" className="secondary-button" onClick={onLogout}>Sign out</button>
        </div>
      </header>

      {message && <div className={message.type === 'error' ? 'alert admin-alert error-alert' : 'alert admin-alert'}>{message.text}</div>}

      <section className="admin-grid">
        <div className="admin-card">
          <div className="admin-card-heading">
            <div><span className="eyebrow">VENUES</span><h2>Create venue</h2></div>
            <strong>{venues.length}</strong>
          </div>
          <div className="admin-form-grid">
            <input value={venueForm.name} onChange={(e) => setVenueForm({ ...venueForm, name: e.target.value })} placeholder="Venue name" />
            <input value={venueForm.city} onChange={(e) => setVenueForm({ ...venueForm, city: e.target.value })} placeholder="City" />
            <input className="wide-field" value={venueForm.address} onChange={(e) => setVenueForm({ ...venueForm, address: e.target.value })} placeholder="Address (optional)" />
          </div>
          <button className="primary-button" type="button" disabled={busy || !venueForm.name.trim() || !venueForm.city.trim()} onClick={createVenue}>Create venue</button>
        </div>

        <div className="admin-card">
          <div className="admin-card-heading">
            <div><span className="eyebrow">EVENTS</span><h2>Create event</h2></div>
            <strong>{events.length}</strong>
          </div>
          <div className="admin-form-grid">
            <input className="wide-field" value={eventForm.title} onChange={(e) => setEventForm({ ...eventForm, title: e.target.value })} placeholder="Event title" />
            <select value={eventForm.category} onChange={(e) => setEventForm({ ...eventForm, category: e.target.value })}>
              {CATEGORIES.map((category) => <option key={category}>{category}</option>)}
            </select>
            <input value={eventForm.language} onChange={(e) => setEventForm({ ...eventForm, language: e.target.value })} placeholder="Language" />
            <input type="number" min="1" max="1000" value={eventForm.durationMin} onChange={(e) => setEventForm({ ...eventForm, durationMin: e.target.value })} placeholder="Duration (min)" />
            <input className="wide-field" value={eventForm.posterUrl} onChange={(e) => setEventForm({ ...eventForm, posterUrl: e.target.value })} placeholder="Poster URL (optional)" />
            <textarea className="wide-field" value={eventForm.description} onChange={(e) => setEventForm({ ...eventForm, description: e.target.value })} placeholder="Description" rows="3" />
          </div>
          <button className="primary-button" type="button" disabled={busy || !eventForm.title.trim()} onClick={createEvent}>Create event</button>
        </div>

        <div className="admin-card">
          <div className="admin-card-heading">
            <div><span className="eyebrow">HALLS</span><h2>Create hall</h2></div>
          </div>
          <div className="admin-form-grid">
            <select value={showForm.venueId} onChange={(e) => loadHalls(e.target.value)}>
              <option value="">Select venue</option>
              {venues.map((venue) => <option key={venue.id} value={venue.id}>{venue.name} · {venue.city}</option>)}
            </select>
            <input value={hallForm.name} onChange={(e) => setHallForm({ ...hallForm, name: e.target.value })} placeholder="Hall name" />
            <input type="number" min="1" max="26" value={hallForm.rows} onChange={(e) => setHallForm({ ...hallForm, rows: e.target.value })} placeholder="Rows" />
            <input type="number" min="1" max="60" value={hallForm.seatsPerRow} onChange={(e) => setHallForm({ ...hallForm, seatsPerRow: e.target.value })} placeholder="Seats / row" />
          </div>
          <button className="primary-button" type="button" disabled={busy || !showForm.venueId || !hallForm.name.trim()} onClick={createHall}>Create hall</button>
        </div>

        <div className="admin-card">
          <div className="admin-card-heading">
            <div><span className="eyebrow">SHOWS</span><h2>Schedule show</h2></div>
          </div>
          <div className="admin-form-grid">
            <select value={showForm.eventId} onChange={(e) => setShowForm({ ...showForm, eventId: e.target.value })}>
              <option value="">Select event</option>
              {events.map((event) => <option key={event.id} value={event.id}>{event.title}</option>)}
            </select>
            <select value={showForm.venueId} onChange={(e) => loadHalls(e.target.value)}>
              <option value="">Select venue</option>
              {venues.map((venue) => <option key={venue.id} value={venue.id}>{venue.name} · {venue.city}</option>)}
            </select>
            <select value={showForm.hallId} onChange={(e) => setShowForm({ ...showForm, hallId: e.target.value })} disabled={!halls.length}>
              <option value="">Select hall</option>
              {halls.map((hall) => <option key={hall.id} value={hall.id}>{hall.name} · {hall.totalSeats} seats</option>)}
            </select>
            <input type="datetime-local" value={showForm.startTime} onChange={(e) => setShowForm({ ...showForm, startTime: e.target.value })} />
            <input type="number" min="0" step="0.01" value={showForm.basePrice} onChange={(e) => setShowForm({ ...showForm, basePrice: e.target.value })} placeholder="Base price" />
          </div>
          <button className="primary-button" type="button" disabled={busy || !showForm.eventId || !showForm.hallId || !showForm.startTime} onClick={scheduleShow}>Schedule show</button>
          <p className="helper-text">Seat pricing uses Standard ×1, Premium ×1.25 and VIP ×1.5.</p>
        </div>

        <div className="admin-card admin-card-wide">
          <div className="admin-card-heading">
            <div><span className="eyebrow">REPORTS</span><h2>Sales & occupancy</h2></div>
          </div>
          {sales && (
            <div className="report-summary">
              <div><span>Total revenue</span><strong>₹{Number(sales.totalRevenue || 0).toFixed(2)}</strong></div>
              <div><span>Events with sales</span><strong>{sales.rows?.length || 0}</strong></div>
            </div>
          )}
          <div className="report-list">
            {(sales?.rows || []).map((row) => (
              <div className="report-row" key={row.eventId}>
                <div><strong>{row.eventTitle}</strong><span>{row.bookings} bookings · {row.seats} seats</span></div>
                <strong>₹{Number(row.revenue).toFixed(2)}</strong>
              </div>
            ))}
          </div>
          <div className="occupancy-row">
            <input value={showId} onChange={(e) => setShowId(e.target.value)} placeholder="Show ID for occupancy" />
            <button type="button" className="secondary-button" disabled={busy || !showId} onClick={checkOccupancy}>Check occupancy</button>
          </div>
          {occupancy && (
            <div className="occupancy-card">
              <strong>{occupancy.eventTitle}</strong>
              <span>{occupancy.bookedSeats}/{occupancy.totalSeats} booked · {occupancy.heldSeats} held</span>
              <strong>{Number(occupancy.occupancyPercent).toFixed(1)}%</strong>
            </div>
          )}
        </div>

        <div className="admin-card admin-card-wide">
          <div className="admin-card-heading">
            <div><span className="eyebrow">CATALOGUE</span><h2>Active events</h2></div>
          </div>
          <div className="admin-event-list">
            {events.map((event) => (
              <div className="admin-event-row" key={event.id}>
                <div>
                  <strong>{event.title}</strong>
                  <span>{event.category} · {event.language || '—'} · ID {event.id}</span>
                </div>
                <button type="button" className="danger-button" disabled={busy} onClick={() => archiveEvent(event.id)}>Archive</button>
              </div>
            ))}
            {!events.length && <p className="helper-text">No catalogue events found.</p>}
          </div>
        </div>
      </section>
    </div>
  );
}
