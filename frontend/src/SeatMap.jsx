import React, { useEffect, useMemo, useState } from 'react';
import { api, subscribeToSeats } from './api.js';

const MAX_SEATS = 6;

export default function SeatMap({ show, onHeld, loggedIn }) {
  const [seats, setSeats] = useState([]);
  const [selected, setSelected] = useState([]);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = () => api.seatMap(show.id).then((data) => setSeats(data.seats)).catch((e) => setError(e.message));

  useEffect(() => {
    load();
    // Live updates: the server pushes only committed changes, so the map never lies.
    const unsubscribe = subscribeToSeats(show.id, (update) => {
      setSeats((current) => current.map((seat) =>
        update.seatIds.includes(seat.id) ? { ...seat, status: update.status } : seat));
      setSelected((current) => current.filter((id) => !update.seatIds.includes(id)));
    });
    return unsubscribe;
  }, [show.id]);

  const rows = useMemo(() => {
    const grouped = new Map();
    seats.forEach((seat) => {
      if (!grouped.has(seat.row)) grouped.set(seat.row, []);
      grouped.get(seat.row).push(seat);
    });
    return [...grouped.entries()].sort(([a], [b]) => a.localeCompare(b));
  }, [seats]);

  const toggle = (seat) => {
    if (seat.status !== 'AVAILABLE') return;
    setError(null);
    setSelected((current) => current.includes(seat.id)
      ? current.filter((id) => id !== seat.id)
      : current.length >= MAX_SEATS ? current : [...current, seat.id]);
  };

  const total = seats.filter((s) => selected.includes(s.id))
    .reduce((sum, s) => sum + Number(s.price), 0);

  const hold = async () => {
    setBusy(true);
    setError(null);
    try {
      onHeld(await api.hold(show.id, selected));
      setSelected([]);
    } catch (e) {
      setError(e.code === 'SEAT_UNAVAILABLE'
        ? 'Someone just took one of those seats. The map has been refreshed.'
        : e.message);
      await load();
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="card">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <strong>{show.eventTitle}</strong>
        <span className="muted">{new Date(show.startTime).toLocaleString()} · {show.hallName}</span>
      </div>

      <div className="screen">SCREEN / STAGE</div>

      {rows.map(([rowLabel, rowSeats]) => (
        <div className="seat-row" key={rowLabel}>
          {rowSeats.sort((a, b) => a.number - b.number).map((seat) => (
            <button
              key={seat.id}
              type="button"
              title={`${seat.row}${seat.number} · ₹${seat.price}`}
              onClick={() => toggle(seat)}
              disabled={seat.status !== 'AVAILABLE'}
              className={`seat ${seat.status} ${selected.includes(seat.id) ? 'selected' : ''}`}>
              {seat.number}
            </button>
          ))}
        </div>
      ))}

      <div className="legend muted">
        <span><i className="swatch" style={{ background: 'var(--available)' }} />Available</span>
        <span><i className="swatch" style={{ background: 'var(--held)' }} />Held</span>
        <span><i className="swatch" style={{ background: 'var(--booked)' }} />Booked</span>
      </div>

      {error && <p className="error">{error}</p>}

      <div className="row" style={{ marginTop: 14, justifyContent: 'space-between' }}>
        <span className="muted">{selected.length} selected (max {MAX_SEATS}) · ₹{total.toFixed(2)}</span>
        <button onClick={hold} disabled={!selected.length || busy || !loggedIn}>
          {loggedIn ? (busy ? 'Holding…' : 'Hold seats') : 'Log in to book'}
        </button>
      </div>
    </div>
  );
}
