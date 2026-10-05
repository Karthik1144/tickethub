import React, { useEffect, useMemo, useState } from 'react';
import { api, clearSession, hasSession, setTokens } from './api.js';
import SeatMap from './SeatMap.jsx';
import AuthPage from './AuthPage.jsx';
import AdminDashboard from './AdminDashboard.jsx';

const CATEGORY_ICONS = {
  MOVIE: '🎬',
  CONCERT: '🎵',
  SPORTS: '🏟️',
  THEATRE: '🎭',
};

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
        <p>{event.description || 'Find shows, choose seats and book in a few clicks.'}</p>
        <button type="button" className="primary-button" onClick={() => onOpen(event)}>View shows</button>
      </div>
    </article>
  );
}

function ShowPicker({ event, shows, loading, onBack, onSelect }) {
  return (
    <section className="flow-section">
      <div className="section-heading">
        <div>
          <span className="eyebrow">CHOOSE A SHOW</span>
          <h2>{event.title}</h2>
          <p className="muted-copy">{event.language || 'Live event'} · Select a showtime to continue.</p>
        </div>
        <button type="button" className="secondary-button" onClick={onBack}>← Back</button>
      </div>
      {loading ? <div className="empty-state">Loading showtimes…</div> : shows.length ? (
        <div className="show-list">
          {shows.map((show) => (
            <article className="show-row" key={show.id}>
              <div>
                <span className="eyebrow">{show.venueName} · {show.city}</span>
                <h3>{show.hallName}</h3>
                <p>{new Date(show.startTime).toLocaleString()} · From ₹{Number(show.basePrice).toFixed(2)}</p>
              </div>
              <div className="show-status">{show.status}</div>
              <button type="button" className="primary-button" onClick={() => onSelect(show)}>Select seats</button>
            </article>
          ))}
        </div>
      ) : (
        <div className="empty-state">
          <div className="empty-icon">⏱</div>
          <h3>No upcoming shows</h3>
          <p>This event does not have an upcoming show yet.</p>
        </div>
      )}
    </section>
  );
}

export default function App() {
  const [user, setUser] = useState(null);
  const [authView, setAuthView] = useState(null);
  const [adminOpen, setAdminOpen] = useState(false);
  const [authReady, setAuthReady] = useState(false);

  const [events, setEvents] = useState([]);
  const [query, setQuery] = useState('');
  const [shows, setShows] = useState([]);
  const [selectedEvent, setSelectedEvent] = useState(null);
  const [show, setShow] = useState(null);
  const [hold, setHold] = useState(null);
  const [confirmed, setConfirmed] = useState(null);

  const [message, setMessage] = useState(null);
  const [loading, setLoading] = useState(true);
  const [searching, setSearching] = useState(false);
  const [loadingShows, setLoadingShows] = useState(false);
  const [activeCategory, setActiveCategory] = useState('ALL');

  const categories = useMemo(
    () => ['ALL', ...new Set(events.map((event) => event.category).filter(Boolean))],
    [events],
  );

  const visibleEvents = useMemo(
    () => (activeCategory === 'ALL' ? events : events.filter((event) => event.category === activeCategory)),
    [activeCategory, events],
  );

  useEffect(() => {
    const restore = async () => {
      try {
        if (hasSession()) {
          await api.refresh();
          setUser(await api.me());
        }
      } catch {
        clearSession();
        setUser(null);
      } finally {
        setAuthReady(true);
      }
    };
    restore();
  }, []);

  const loadEvents = async (search = '') => {
    setSearching(Boolean(search));
    try {
      const page = await api.events(search);
      setEvents(page.content);
      setMessage(null);
    } catch (error) {
      setMessage({ type: 'error', text: error.message || 'Unable to load events.' });
    } finally {
      setLoading(false);
      setSearching(false);
    }
  };

  useEffect(() => { loadEvents(); }, []);

  const logout = async () => {
    await api.logout();
    setUser(null);
    setAdminOpen(false);
    setAuthView(null);
    setHold(null);
    setConfirmed(null);
    setShow(null);
    setSelectedEvent(null);
    setMessage({ type: 'success', text: 'You have been signed out.' });
  };

  const search = async (event) => {
    event?.preventDefault();
    setActiveCategory('ALL');
    await loadEvents(query.trim());
  };

  const openEvent = async (event) => {
    setSelectedEvent(event);
    setShow(null);
    setHold(null);
    setConfirmed(null);
    setShows([]);
    setLoadingShows(true);
    setMessage(null);
    try {
      setShows(await api.shows(event.id));
    } catch (error) {
      setMessage({ type: 'error', text: error.message || 'Unable to load showtimes.' });
    } finally {
      setLoadingShows(false);
    }
  };

  const backToEvents = () => {
    setSelectedEvent(null);
    setShows([]);
    setShow(null);
    setHold(null);
    setConfirmed(null);
    setMessage(null);
  };

  const pay = async () => {
    try {
      const payment = await api.startPayment(hold.bookingRef);
      try {
        await api.simulatePayment(hold.bookingRef);
        setConfirmed(await api.booking(hold.bookingRef));
        setHold(null);
        setShow(null);
        setMessage({ type: 'success', text: 'Payment confirmed by the demo gateway.' });
      } catch {
        setMessage({
          type: 'info',
          text: 'Gateway order ' + payment.gatewayOrderId + ' created. Confirmation arrives via the signed webhook.',
        });
      }
    } catch (error) {
      setMessage({ type: 'error', text: error.message || 'Payment could not be started.' });
    }
  };

  const handleAuthenticated = (nextUser) => {
    setUser(nextUser);
    setAuthView(null);
    setMessage({ type: 'success', text: 'Welcome, ' + nextUser.fullName + '.' });
  };

  if (!authReady) return <div className="page-loader">Checking your session…</div>;

  if (authView) {
    return (
      <AuthPage
        mode={authView}
        initialEmail=""
        onAuthenticated={handleAuthenticated}
        onNavigate={(target) => {
          if (target === 'home') setAuthView(null);
          else setAuthView(target);
        }}
      />
    );
  }

  if (adminOpen) {
    if (!user) {
      setAuthView('login');
    } else if (user.role !== 'ADMIN') {
      return (
        <div className="centered-state">
          <div className="success-card">
            <div className="success-icon">!</div>
            <span className="eyebrow">ADMIN AREA</span>
            <h2>Admin access required</h2>
            <p>Your account does not have the ADMIN role.</p>
            <button type="button" className="primary-button" onClick={() => setAdminOpen(false)}>Back to TicketHub</button>
          </div>
        </div>
      );
    } else {
      return <AdminDashboard user={user} onNavigate={() => setAdminOpen(false)} onLogout={logout} />;
    }
  }

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
                onClick={() => { setQuery(''); loadEvents(); }}
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
            {user ? (
              <div className="account-menu">
                <div className="account-chip">
                  <span className="avatar">{user.fullName?.[0]?.toUpperCase() || 'U'}</span>
                  <div className="account-copy">
                    <strong>{user.fullName}</strong>
                    <span>{user.role === 'ADMIN' ? 'Administrator' : 'Customer'}</span>
                  </div>
                </div>
                <div className="account-actions">
                  {user.role === 'ADMIN' && (
                    <button type="button" className="text-button" onClick={() => setAdminOpen(true)}>Admin</button>
                  )}
                  <button type="button" className="text-button" onClick={logout}>Sign out</button>
                </div>
              </div>
            ) : (
              <div className="auth-actions">
                <button type="button" className="secondary-button" onClick={() => setAuthView('login')}>Log in</button>
                <button type="button" className="primary-button" onClick={() => setAuthView('signup')}>Sign up</button>
              </div>
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
              <span className="hero-note">
                {user ? 'Signed in as ' + user.fullName : 'Fast booking · Live seat availability'}
              </span>
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
          <div className={message.type === 'error' ? 'alert error-alert' : 'alert'} role="status">
            <span>{message.text}</span>
            <button type="button" className="alert-close" onClick={() => setMessage(null)} aria-label="Dismiss message">×</button>
          </div>
        )}

        {confirmed ? (
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
        ) : hold ? (
          <section className="flow-section narrow-section">
            <div className="section-heading">
              <div><span className="eyebrow">REVIEW BOOKING</span><h2>Ready to reserve your seats?</h2></div>
            </div>
            <div className="booking-card">
              <div>
                <div className="booking-ref">{hold.bookingRef}</div>
                <h3>{hold.seats.map((seat) => seat.label).join(', ')}</h3>
                <p>Expires at {new Date(hold.expiresAt).toLocaleTimeString()}</p>
              </div>
              <div className="booking-price"><span>Total</span><strong>₹{Number(hold.totalAmount).toFixed(2)}</strong></div>
              <div className="booking-actions">
                <button type="button" className="primary-button" onClick={pay}>Pay now</button>
                <button type="button" className="secondary-button" onClick={backToEvents}>Cancel</button>
              </div>
            </div>
          </section>
        ) : show ? (
          <section className="flow-section">
            <div className="section-heading">
              <div><span className="eyebrow">SEAT SELECTION</span><h2>{show.eventTitle}</h2></div>
              <button type="button" className="secondary-button" onClick={() => setShow(null)}>← Back to showtimes</button>
            </div>
            <SeatMap
              show={show}
              loggedIn={Boolean(user)}
              onHeld={(booking) => { setHold(booking); setShow(null); }}
            />
          </section>
        ) : selectedEvent ? (
          <ShowPicker
            event={selectedEvent}
            shows={shows}
            loading={loadingShows}
            onBack={backToEvents}
            onSelect={setShow}
          />
        ) : (
          <section id="events" className="events-section">
            <div className="section-heading">
              <div><span className="eyebrow">DISCOVER</span><h2>Events you’ll love</h2></div>
              <span className="result-count">{loading ? 'Loading…' : events.length + ' event' + (events.length === 1 ? '' : 's')}</span>
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
              <div className="event-grid">{[1, 2].map((item) => <div className="event-card skeleton-card" key={item} />)}</div>
            ) : visibleEvents.length ? (
              <div className="event-grid">{visibleEvents.map((event) => <EventCard key={event.id} event={event} onOpen={openEvent} />)}</div>
            ) : (
              <div className="empty-state">
                <div className="empty-icon">⌕</div>
                <h3>No events found</h3>
                <p>Try another search or clear the current filters.</p>
                <button type="button" className="secondary-button" onClick={() => { setQuery(''); setActiveCategory('ALL'); loadEvents(); }}>Clear search</button>
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
