import React, { useEffect, useMemo, useState } from 'react';
import { api, subscribeToSeats } from './api.js';

const MAX_SEATS = 6;

export default function SeatMap({ show, onHeld, loggedIn }) {
  const [seats, setSeats] = useState([]);
  const [selected, setSelected] = useState([]);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = () => api.seatMap(show.id)
    .then((data) => setSeats(data.seats))
    .catch((e) => setError(e.message));

  useEffect(() => {
    load();
    const unsubscribe = subscribeToSeats(show.id, (update) => {
      setSeats((current) => current.map((seat) => (
        update.seatIds.includes(seat.id) ? { ...seat, status: update.status } : seat
      )));
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
    return [...grouped.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([row, rowSeats]) => [row, [...rowSeats].sort((a, b) => a.number - b.number)]);
  }, [seats]);

  const toggle = (seat) => {
    if (seat.status !== 'AVAILABLE') return;
    setError(null);
    setSelected((current) => current.includes(seat.id)
      ? current.filter((id) => id !== seat.id)
      : current.length >= MAX_SEATS ? current : [...current, seat.id]);
  };

  const total = seats
    .filter((seat) => selected.includes(seat.id))
    .reduce((sum, seat) => sum + Number(seat.price), 0);

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
    <div className="seat-layout-card">
      <div className="seat-layout-header">
        <div>
          <span className="eyebrow">{show.venueName || show.city}</span>
          <h3>{show.hallName}</h3>
          <p>{new Date(show.startTime).toLocaleString()}</p>
        </div>
        <div className="seat-price-note">Up to {MAX_SEATS} seats</div>
      </div>

      <div className="screen-stage">
        <span>SCREEN / STAGE</span>
      </div>

      <div className="seat-map-scroll">
        <div className="seat-map">
          {rows.map(([rowLabel, rowSeats]) => (
            <div className="seat-row" key={rowLabel}>
              <span className="row-label">{rowLabel}</span>
              <div className="seat-row-items">
                {rowSeats.map((seat) => (
                  <button
                    key={seat.id}
                    type="button"
                    title={`${seat.row}${seat.number} · ₹${seat.price}`}
                    onClick={() => toggle(seat)}
                    disabled={seat.status !== 'AVAILABLE'}
                    className={`seat seat--${seat.status.toLowerCase()} ${selected.includes(seat.id) ? 'seat--selected' : ''}`}>
                    {seat.number}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="seat-legend">
        <span><i className="swatch swatch--available" />Available</span>
        <span><i className="swatch swatch--selected" />Selected</span>
        <span><i className="swatch swatch--held" />Held</span>
        <span><i className="swatch swatch--booked" />Booked</span>
      </div>

      {error && <p className="error-message">{error}</p>}

      <div className="seat-summary-bar">
        <div>
          <strong>{selected.length} selected</strong>
          <span>Maximum {MAX_SEATS}</span>
        </div>
        <div className="seat-total">₹{total.toFixed(2)}</div>
        <button
          type="button"
          className="primary-button"
          onClick={hold}
          disabled={!selected.length || busy || !loggedIn}>
          {loggedIn ? (busy ? 'Holding…' : 'Continue') : 'Log in to book'}
        </button>
      </div>
    </div>
  );
}
