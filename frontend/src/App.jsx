import React, { useEffect, useMemo, useState } from 'react';
import { api, setToken } from './api.js';
import SeatMap from './SeatMap.jsx';

const CATEGORY_ICONS = {
  MOVIE: '🎬',
  CONCERT: '🎵',
  SPORTS: '🏟️',
  THEATRE: '🎭',
};

function formatDateTime(value) {
  return new Intl.DateTimeFormat(undefined, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(value));
}

function EventCard({ event, onOpen }) {
  return (
    <article className="event-card">
      <div className="event-art" aria-hidden="true">
        <span className="event-art-icon">{CATEGORY_ICONS[event.category] || '🎟️'}</span>
        <span className="event-art-label">{event.category || 'EVENT'}</span>
      </div>
      <div className="event-card-body">
        <div className="eyebrow">{event.language || 'Live event'}</div>
        <h3>{event.title}</h3>
        <p>Find shows, choose seats and book in a few clicks.</p>
        <button type="button" className="primary-button" onClick={() => onOpen(event)}>
          View shows
        </button>
      </div>
    </article>
  );
}

export default function App() {
  const [email, setEmail] = useState('user@tickethub.dev');
  const [password, setPassword] = useState('User1234!');
  const [loggedIn, setLoggedIn] = useState(false);
  const [events, setEvents] = useState([]);
  const [query, setQuery] = useState('');
  const [shows, setShows] = useState([]);
  const [show, setShow] = useState(null);
  const [hold, setHold] = useState(null);
  const [confirmed, setConfirmed] = useState(null);
  const [message, setMessage] = useState(null);
  const [loading, setLoading] = useState(true);
  const [searching, setSearching] = useState(false);
  const [loadingShows, setLoadingShows] = useState(false);
  const [authBusy, setAuthBusy] = useState(false);
  const [activeCategory, setActiveCategory] = useState('ALL');

  const categories = useMemo(() => (
    ['ALL', ...new Set(events.map((event) => event.category).filter(Boolean))]
  ), [events]);

  const visibleEvents = useMemo(() => (
    activeCategory === 'ALL'
      ? events
      : events.filter((event) => event.category === activeCategory)
  ), [activeCategory, events]);

  const loadEvents = async (search = '') => {
    setSearching(Boolean(search));
    try {
      const page = await api.events(search);
      setEvents(page.content);
      setMessage(null);
    } catch (e) {
      setMessage(e.message || 'Unable to load events.');
    } finally {
      setLoading(false);
      setSearching(false);
    }
  };

  useEffect(() => {
    loadEvents();
  }, []);

  const search = async (event) => {
    event?.preventDefault();
    setActiveCategory('ALL');
    await loadEvents(query.trim());
  };

  const login = async (event) => {
    event?.preventDefault();
    setAuthBusy(true);
    try {
      const tokens = await api.login({ email, password });
      setToken(tokens.accessToken);
      setLoggedIn(true);
      setMessage('Welcome back. You are ready to book.');
    } catch (e) {
      setMessage(e.message || 'Login failed.');
    } finally {
      setAuthBusy(false);
    }
  };

  const logout = () => {
    setToken(null);
    setLoggedIn(false);
    setHold(null);
    setConfirmed(null);
    setMessage('You have been signed out.');
  };

  const openEvent = async (event) => {
    setShow(null);
    setHold(null);
    setConfirmed(null);
    setLoadingShows(true);
    setMessage(null);
    try {
      const nextShows = await api.shows(event.id);
      setShows(nextShows);
    } catch (e) {
      setMessage(e.message || 'Unable to load shows.');
    } finally {
      setLoadingShows(false);
    }
  };

  const backToEvents = () => {
    setShow(null);
    setHold(null);
    setConfirmed(null);
    setShows([]);
    setMessage(null);
  };

  const pay = async () => {
    try {
      const payment = await api.startPayment(hold.bookingRef);
      try {
        await api.simulatePayment(hold.bookingRef);
        setConfirmed(await api.booking(hold.bookingRef));
        setMessage('Payment confirmed by the demo gateway.');
      } catch {
        setMessage(
          `Gateway order ${payment.gatewayOrderId} created. Confirmation arrives via the signed webhook.`,
        );
      }
    } catch (e) {
      setMessage(e.message || 'Payment could not be started.');
    }
  };

  return (
    <div className="app-shell">
      <header className="site-header">
        <div className="header-inner">
          <button className="brand" type="button" onClick={backToEvents} aria-label="Go to TicketHub home">
            <span className="brand-mark">T</span>
            <span>TicketHub</span>
          </button>

          <form className="header-search" onSubmit={search} role="search">
            <span className="search-icon" aria-hidden="true">⌕</span>
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search movies, concerts, events..."
              aria-label="Search events"
            />
            {query && (
              <button
                type="button"
                className="clear-search"
                onClick={() => {
                  setQuery('');
                  loadEvents();
                }}
                aria-label="Clear search"
              >
                ×
              </button>
            )}
            <button className="search-button" type="submit" disabled={searching}>
              {searching ? 'Searching…' : 'Search'}
            </button>
          </form>

          <div className="header-account">
            {loggedIn ? (
              <div className="account-chip">
                <span className="avatar">{email[0]?.toUpperCase() || 'U'}</span>
                <div className="account-copy">
                  <strong>{email}</strong>
                  <span>Signed in</span>
                </div>
                <button type="button" className="text-button" onClick={logout}>Sign out</button>
              </div>
            ) : (
              <details className="login-popover">
                <summary className="secondary-button">Log in</summary>
                <form className="login-panel" onSubmit={login}>
                  <div className="eyebrow">Welcome back</div>
                  <h2>Sign in to TicketHub</h2>
                  <label>
                    Email
                    <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" autoComplete="email" />
                  </label>
                  <label>
                    Password
                    <input value={password} onChange={(e) => setPassword(e.target.value)} type="password" autoComplete="current-password" />
                  </label>
                  <button className="primary-button full-width" type="submit" disabled={authBusy}>
                    {authBusy ? 'Signing in…' : 'Sign in'}
                  </button>
                  <p className="helper-text">Demo user: user@tickethub.dev / User1234!</p>
                </form>
              </details>
            )}
          </div>
        </div>
      </header>

      <main className="page">
        <section className="hero">
          <div className="hero-copy">
            <div className="eyebrow">BOOK • WATCH • EXPERIENCE</div>
            <h1>Everything you want to experience, in one place.</h1>
            <p>Discover events, choose the best seats and book your next outing without the clutter.</p>
            <div className="hero-actions">
              <button className="primary-button" type="button" onClick={() => document.getElementById('events')?.scrollIntoView({ behavior: 'smooth' })}>
                Explore events
              </button>
              <span className="hero-note">Fast booking · Live seat availability</span>
            </div>
          </div>
          <div className="hero-ticket" aria-hidden="true">
            <div className="ticket-glow" />
            <div className="ticket-card">
              <span>YOUR NEXT</span>
              <strong>GREAT<br />OUTING</strong>
              <small>TicketHub</small>
            </div>
          </div>
        </section>

        {message && (
          <div className="alert" role="status">
            <span>{message}</span>
            <button type="button" className="alert-close" onClick={() => setMessage(null)} aria-label="Dismiss message">×</button>
          </div>
        )}

        {show ? (
          <section className="flow-section">
            <div className="section-heading">
              <div>
                <span className="eyebrow">SEAT SELECTION</span>
                <h2>{show.eventTitle}</h2>
              </div>
              <button type="button" className="secondary-button" onClick={backToEvents}>← Back to events</button>
            </div>
            <SeatMap show={show} loggedIn={loggedIn} onHeld={setHold} />
          </section>
        ) : hold ? (
          <section className="flow-section narrow-section">
            <div className="section-heading">
              <div>
                <span className="eyebrow">REVIEW BOOKING</span>
                <h2>Ready to reserve your seats?</h2>
              </div>
            </div>
            <div className="booking-card">
              <div>
                <div className="booking-ref">{hold.bookingRef}</div>
                <h3>{hold.seats.map((seat) => seat.label).join(', ')}</h3>
                <p>Expires at {new Date(hold.expiresAt).toLocaleTimeString()}</p>
              </div>
              <div className="booking-price">
                <span>Total</span>
                <strong>₹{Number(hold.totalAmount).toFixed(2)}</strong>
              </div>
              <div className="booking-actions">
                <button type="button" className="primary-button" onClick={pay}>Pay now</button>
                <button type="button" className="secondary-button" onClick={backToEvents}>Cancel</button>
              </div>
            </div>
          </section>
        ) : confirmed ? (
          <section className="flow-section narrow-section">
            <div className="success-card">
              <div className="success-icon">✓</div>
              <span className="eyebrow">BOOKING CONFIRMED</span>
              <h2>Your tickets are ready.</h2>
              <p>{confirmed.event.title} · {confirmed.seats.join(', ')} · ₹{confirmed.totalAmount}</p>
              {confirmed.qrCode && <img className="qr-code" src={confirmed.qrCode} alt="Ticket QR code" />}
              <button type="button" className="primary-button" onClick={backToEvents}>Book another experience</button>
            </div>
          </section>
        ) : (
          <section id="events" className="events-section">
            <div className="section-heading">
              <div>
                <span className="eyebrow">DISCOVER</span>
                <h2>Events you’ll love</h2>
              </div>
              <span className="result-count">
                {loading ? 'Loading…' : `${events.length} event${events.length === 1 ? '' : 's'}`}
              </span>
            </div>

            <div className="category-bar" aria-label="Filter events by category">
              {categories.map((category) => (
                <button
                  type="button"
                  key={category}
                  className={activeCategory === category ? 'category-pill active' : 'category-pill'}
                  onClick={() => setActiveCategory(category)}
                >
                  {category === 'ALL' ? 'All' : (CATEGORY_ICONS[category] || '🎟️') + ' ' + category}
                </button>
              ))}
            </div>

            {loading ? (
              <div className="event-grid">
                {[1, 2].map((item) => <div className="event-card skeleton-card" key={item} />)}
              </div>
            ) : loadingShows ? (
              <div className="empty-state">Loading shows…</div>
            ) : visibleEvents.length ? (
              <div className="event-grid">
                {visibleEvents.map((event) => (
                  <EventCard key={event.id} event={event} onOpen={openEvent} />
                ))}
              </div>
            ) : (
              <div className="empty-state">
                <div className="empty-icon">⌕</div>
                <h3>No events found</h3>
                <p>Try another search or clear the current filters.</p>
                <button type="button" className="secondary-button" onClick={() => { setQuery(''); setActiveCategory('ALL'); loadEvents(); }}>
                  Clear search
                </button>
              </div>
            )}
          </section>
        )}
      </main>

      <footer className="site-footer">
        <div className="footer-inner">
          <strong>TicketHub</strong>
          <span>Simple booking. Better experiences.</span>
        </div>
      </footer>
    </div>
  );
}
