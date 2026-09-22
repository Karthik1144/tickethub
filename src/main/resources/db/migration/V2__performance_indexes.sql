-- Indexes added deliberately after the performance baseline (see Doc 06, tuning log).
-- Step 1: seat map / availability lookups
CREATE INDEX idx_showseat_show_status ON show_seats (show_id, status);
-- Step 2: hold-expiry sweeper and "my bookings"
CREATE INDEX idx_booking_status_expiry ON bookings (status, expires_at);
CREATE INDEX idx_booking_user_created  ON bookings (user_id, created_at);
