import React, { useEffect, useMemo, useState } from 'react';
import { api, subscribeToSeats } from './api.js';

const MAX_SEATS = 6;
const TYPE_META = {
  STANDARD: { label: 'Standard', short: 'Standard' },
  PREMIUM: { label: 'Premium', short: 'Premium' },
  VIP: { label: 'VIP', short: 'VIP' },
};

function formatMoney(value) {
  return `₹${Number(value || 0).toFixed(2)}`;
}

function seatLabel(seat) {
  return `${seat.row}${seat.number}`;
}

export default function SeatMap({ show, onHeld, loggedIn }) {
  const [seats, setSeats] = useState([]);
  const [selected, setSelected] = useState([]);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = () => api.seatMap(show.id)
    .then((data) => setSeats(data.seats))
    .catch((e) => setError(e.message || 'Unable to load the seat map.'));

  useEffect(() => {
    setSelected([]);
    setError(null);
    load();

    const unsubscribe = subscribeToSeats(show.id, (update) => {
      setSeats((current) => current.map((seat) => (
        update.seatIds.includes(seat.id)
          ? { ...seat, status: update.status }
          : seat
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
      .sort(([a], [b]) => a.localeCompare(b, undefined, { numeric: true }))
      .map(([row, rowSeats]) => [
        row,
        [...rowSeats].sort((a, b) => a.number - b.number),
      ]);
  }, [seats]);

  const selectedSeats = useMemo(
    () => seats
      .filter((seat) => selected.includes(seat.id))
      .sort((a, b) => {
        const rowCompare = a.row.localeCompare(b.row, undefined, { numeric: true });
        return rowCompare || a.number - b.number;
      }),
    [seats, selected],
  );

  const totals = useMemo(() => {
    const total = selectedSeats.reduce((sum, seat) => sum + Number(seat.price), 0);
    const byType = selectedSeats.reduce((acc, seat) => {
      acc[seat.type] = (acc[seat.type] || 0) + 1;
      return acc;
    }, {});

    return { total, byType };
  }, [selectedSeats]);

  const availability = useMemo(() => {
    const available = seats.filter((seat) => seat.status === 'AVAILABLE').length;
    const held = seats.filter((seat) => seat.status === 'HELD').length;
    const booked = seats.filter((seat) => seat.status === 'BOOKED').length;
    return { available, held, booked, total: seats.length };
  }, [seats]);

  const seatTypes = useMemo(
    () => [...new Set(seats.map((seat) => seat.type).filter(Boolean))],
    [seats],
  );

  const toggle = (seat) => {
    if (seat.status !== 'AVAILABLE') return;

    setError(null);
    setSelected((current) => {
      if (current.includes(seat.id)) {
        return current.filter((id) => id !== seat.id);
      }
      if (current.length >= MAX_SEATS) {
        setError(`You can select up to ${MAX_SEATS} seats per booking.`);
        return current;
      }
      return [...current, seat.id];
    });
  };

  const clearSelection = () => {
    setSelected([]);
    setError(null);
  };

  const hold = async () => {
    if (!loggedIn) return;
    if (!selected.length) {
      setError('Select at least one seat to continue.');
      return;
    }

    setBusy(true);
    setError(null);

    try {
      onHeld(await api.hold(show.id, selected));
      setSelected([]);
    } catch (e) {
      setError(e.code === 'SEAT_UNAVAILABLE'
        ? 'One or more selected seats just became unavailable. We refreshed the map for you.'
        : e.message || 'We could not reserve those seats. Please try again.');
      await load();
    } finally {
      setBusy(false);
    }
  };

  const renderSeat = (seat) => {
    const isSelected = selected.includes(seat.id);
    const status = seat.status.toLowerCase();
    const type = seat.type?.toLowerCase() || 'standard';
    const disabled = seat.status !== 'AVAILABLE';

    return (
      <button
        key={seat.id}
        type="button"
        className={[
          'booking-seat',
          `booking-seat--${status}`,
          `booking-seat--${type}`,
          isSelected ? 'booking-seat--selected' : '',
        ].filter(Boolean).join(' ')}
        onClick={() => toggle(seat)}
        disabled={disabled}
        aria-label={`${seatLabel(seat)}, ${TYPE_META[seat.type]?.label || seat.type}, ${seat.status.toLowerCase()}, ${formatMoney(seat.price)}`}
        aria-pressed={isSelected}
        title={`${seatLabel(seat)} · ${TYPE_META[seat.type]?.label || seat.type} · ${formatMoney(seat.price)}`}
      >
        <span>{seat.number}</span>
        {isSelected && <span className="booking-seat-check" aria-hidden="true">✓</span>}
      </button>
    );
  };

  if (!seats.length) {
    return (
      <div className="seat-layout-card">
        <div className="seat-map-empty">
          <div className="seat-empty-icon">◫</div>
          <h3>Seat map unavailable</h3>
          <p>This show does not have a seat map available right now.</p>
          <button type="button" className="secondary-button" onClick={load}>Try again</button>
        </div>
      </div>
    );
  }

  return (
    <div className="seat-layout-card">
      <div className="booking-seat-header">
        <div className="booking-seat-heading">
          <div>
            <span className="eyebrow">STEP 2 OF 3 · SELECT SEATS</span>
            <h3>{show.hallName}</h3>
            <p>
              {show.venueName || show.city}
              <span className="booking-seat-dot">•</span>
              {new Date(show.startTime).toLocaleString(undefined, {
                weekday: 'short',
                day: 'numeric',
                month: 'short',
                hour: 'numeric',
                minute: '2-digit',
              })}
            </p>
          </div>
          <div className="booking-seat-cap">
            <strong>{MAX_SEATS} max</strong>
            <span>per booking</span>
          </div>
        </div>

        <div className="booking-seat-status-strip" aria-label="Seat availability">
          <span><strong>{availability.available}</strong> available</span>
          <span><strong>{availability.held}</strong> held</span>
          <span><strong>{availability.booked}</strong> booked</span>
        </div>
      </div>

      <div className="booking-seat-main">
        <div className="booking-seat-stage">
          <div className="stage-screen">
            <span>SCREEN / STAGE</span>
          </div>
          <p className="stage-hint">Front of the venue</p>

          <div className="seat-map-scroll">
            <div className="seat-map">
              {rows.map(([rowLabel, rowSeats]) => (
                <div className="seat-row" key={rowLabel}>
                  <div className="seat-row-label" aria-hidden="true">{rowLabel}</div>
                  <div className="seat-row-items">
                    {rowSeats.map(renderSeat)}
                  </div>
                  <div className="seat-row-label" aria-hidden="true">{rowLabel}</div>
                </div>
              ))}
            </div>
          </div>

          <div className="booking-seat-legend">
            <span className="legend-heading">Seat status</span>
            <span><i className="booking-swatch booking-swatch--available" />Available</span>
            <span><i className="booking-swatch booking-swatch--selected" />Selected</span>
            <span><i className="booking-swatch booking-swatch--held" />Held</span>
            <span><i className="booking-swatch booking-swatch--booked" />Booked</span>
          </div>

          {!!seatTypes.length && (
            <div className="booking-seat-types">
              <span className="legend-heading">Seat category</span>
              {seatTypes.map((type) => {
                const firstSeat = seats.find((seat) => seat.type === type);
                return (
                  <span key={type}>
                    <i className={`booking-type-dot booking-type-dot--${type.toLowerCase()}`} />
                    {TYPE_META[type]?.label || type}
                    {firstSeat ? ` · ${formatMoney(firstSeat.price)}` : ''}
                  </span>
                );
              })}
            </div>
          )}
        </div>

        <aside className="booking-seat-summary" aria-label="Booking summary">
          <div className="booking-summary-top">
            <div>
              <span className="eyebrow">YOUR SELECTION</span>
              <h4>{selectedSeats.length ? `${selectedSeats.length} seat${selectedSeats.length === 1 ? '' : 's'} selected` : 'Pick your seats'}</h4>
            </div>
            {selectedSeats.length > 0 && (
              <button type="button" className="summary-clear" onClick={clearSelection}>Clear</button>
            )}
          </div>

          {selectedSeats.length ? (
            <>
              <div className="selected-seat-list">
                {selectedSeats.map((seat) => (
                  <div className="selected-seat-chip" key={seat.id}>
                    <div>
                      <strong>{seatLabel(seat)}</strong>
                      <span>{TYPE_META[seat.type]?.short || seat.type}</span>
                    </div>
                    <span>{formatMoney(seat.price)}</span>
                    <button
                      type="button"
                      aria-label={`Remove ${seatLabel(seat)}`}
                      onClick={() => toggle(seat)}
                    >
                      ×
                    </button>
                  </div>
                ))}
              </div>

              <div className="booking-price-breakdown">
                {Object.entries(totals.byType).map(([type, count]) => (
                  <div key={type}>
                    <span>{TYPE_META[type]?.label || type} × {count}</span>
                    <strong>
                      {formatMoney(
                        selectedSeats
                          .filter((seat) => seat.type === type)
                          .reduce((sum, seat) => sum + Number(seat.price), 0),
                      )}
                    </strong>
                  </div>
                ))}
              </div>

              <div className="booking-total">
                <span>Total payable</span>
                <strong>{formatMoney(totals.total)}</strong>
              </div>
            </>
          ) : (
            <div className="selection-placeholder">
              <div className="placeholder-icon">⌖</div>
              <strong>Choose up to {MAX_SEATS} seats</strong>
              <p>Tap any available seat. Your selection will appear here before you continue.</p>
            </div>
          )}

          {error && <p className="error-message booking-seat-error" role="alert">{error}</p>}

          <button
            type="button"
            className="primary-button booking-continue"
            onClick={hold}
            disabled={!selectedSeats.length || busy || !loggedIn}
          >
            {!loggedIn ? 'Log in to reserve seats' : busy ? 'Securing seats…' : 'Continue to checkout'}
          </button>

          {selectedSeats.length > 0 && (
            <p className="booking-security-note">
              Seats are secured only after the next step. We’ll show the hold expiry before payment.
            </p>
          )}
        </aside>
      </div>
    </div>
  );
}
