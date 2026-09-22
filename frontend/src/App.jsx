import React, { useEffect, useState } from 'react';
import { api, setToken } from './api.js';
import SeatMap from './SeatMap.jsx';

export default function App() {
  const [email, setEmail] = useState('user@tickethub.dev');
  const [password, setPassword] = useState('User1234!');
  const [loggedIn, setLoggedIn] = useState(false);
  const [events, setEvents] = useState([]);
  const [query, setQuery] = useState('');
  const [shows, setShows] = useState([]);
  const [show, setShow] = useState(null);
  const [hold, setHold] = useState(null);
  const [message, setMessage] = useState(null);
  const [confirmed, setConfirmed] = useState(null);

  useEffect(() => { api.events().then((page) => setEvents(page.content)).catch(() => {}); }, []);

  const search = async () => setEvents((await api.events(query)).content);

  const login = async () => {
    try {
      const tokens = await api.login({ email, password });
      setToken(tokens.accessToken);
      setLoggedIn(true);
      setMessage(null);
    } catch (e) {
      setMessage(e.message);
    }
  };

  const openEvent = async (event) => {
    setShow(null);
    setHold(null);
    setShows(await api.shows(event.id));
  };

  const pay = async () => {
    try {
      const payment = await api.startPayment(hold.bookingRef);
      try {
        // Exists only when the backend runs with the demo profile.
        await api.simulatePayment(hold.bookingRef);
        setConfirmed(await api.booking(hold.bookingRef));
        setMessage('Payment confirmed by the demo gateway.');
      } catch {
        setMessage(`Gateway order ${payment.gatewayOrderId} created. Confirmation arrives via the signed webhook.`);
      }
    } catch (e) {
      setMessage(e.message);
    }
  };

  return (
    <div className="wrap">
      <header>
        <h1>TicketHub</h1>
        {loggedIn
          ? <span className="muted">Signed in as {email}</span>
          : <div className="row">
              <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="email" />
              <input value={password} type="password" onChange={(e) => setPassword(e.target.value)} />
              <button onClick={login}>Log in</button>
            </div>}
      </header>

      {message && <div className="card muted">{message}</div>}

      <div className="card row">
        <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search events" />
        <button className="ghost" onClick={search}>Search</button>
      </div>

      {!shows.length && events.map((event) => (
        <div className="card row" key={event.id} style={{ justifyContent: 'space-between' }}>
          <div>
            <strong>{event.title}</strong>
            <div className="muted">{event.category} · {event.language}</div>
          </div>
          <button className="ghost" onClick={() => openEvent(event)}>View shows</button>
        </div>
      ))}

      {!!shows.length && !show && shows.map((s) => (
        <div className="card row" key={s.id} style={{ justifyContent: 'space-between' }}>
          <div>
            <strong>{new Date(s.startTime).toLocaleString()}</strong>
            <div className="muted">{s.venueName} · {s.hallName} · {s.city}</div>
          </div>
          <button onClick={() => setShow(s)}>Pick seats</button>
        </div>
      ))}

      {show && !hold && <SeatMap show={show} loggedIn={loggedIn} onHeld={setHold} />}

      {hold && !confirmed && (
        <div className="card">
          <strong>Booking {hold.bookingRef}</strong>
          <p className="muted">
            {hold.seats.map((s) => s.label).join(', ')} · ₹{hold.totalAmount} ·
            hold expires {new Date(hold.expiresAt).toLocaleTimeString()}
          </p>
          <div className="row">
            <button onClick={pay}>Pay now</button>
            <button className="ghost" onClick={() => { setHold(null); setShow(null); setShows([]); }}>
              Back to events
            </button>
          </div>
        </div>
      )}
      {confirmed && (
        <div className="card">
          <strong>Booking confirmed: {confirmed.bookingRef}</strong>
          <p className="muted">
            {confirmed.event.title} · {confirmed.seats.join(', ')} · ₹{confirmed.totalAmount}
          </p>
          {confirmed.qrCode && <img src={confirmed.qrCode} alt="Ticket QR code" width="160" height="160" />}
          <div className="row" style={{ marginTop: 10 }}>
            <button className="ghost" onClick={() => { setConfirmed(null); setHold(null); setShow(null); setShows([]); }}>
              Book another
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
