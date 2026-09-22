-- TicketHub initial schema (MySQL 8 / InnoDB)

CREATE TABLE users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  email         VARCHAR(255) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  full_name     VARCHAR(120) NOT NULL,
  phone         VARCHAR(20)  NULL,
  role          VARCHAR(10)  NOT NULL DEFAULT 'USER',
  enabled       TINYINT(1)   NOT NULL DEFAULT 1,
  created_at    DATETIME(6)  NOT NULL,
  updated_at    DATETIME(6)  NOT NULL,
  UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_tokens (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT NOT NULL,
  token_hash CHAR(64)    NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  revoked    TINYINT(1)  NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_rt_hash (token_hash),
  KEY idx_rt_user (user_id),
  CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE venues (
  id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  name    VARCHAR(150) NOT NULL,
  city    VARCHAR(80)  NOT NULL,
  address VARCHAR(255) NULL,
  KEY idx_venues_city (city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE halls (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  venue_id    BIGINT NOT NULL,
  name        VARCHAR(80)  NOT NULL,
  total_seats INT          NOT NULL,
  UNIQUE KEY uk_hall_venue_name (venue_id, name),
  CONSTRAINT fk_hall_venue FOREIGN KEY (venue_id) REFERENCES venues(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE seats (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  hall_id     BIGINT NOT NULL,
  row_label   VARCHAR(4)  NOT NULL,
  seat_number SMALLINT    NOT NULL,
  seat_type   VARCHAR(10) NOT NULL DEFAULT 'STANDARD',
  UNIQUE KEY uk_seat_position (hall_id, row_label, seat_number),
  CONSTRAINT fk_seat_hall FOREIGN KEY (hall_id) REFERENCES halls(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE events (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  title        VARCHAR(200) NOT NULL,
  description  TEXT         NULL,
  category     VARCHAR(20)  NOT NULL,
  language     VARCHAR(30)  NULL,
  duration_min SMALLINT     NULL,
  poster_url   VARCHAR(500) NULL,
  status       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
  created_at   DATETIME(6)  NOT NULL,
  KEY idx_events_cat_status (category, status),
  KEY idx_events_title (title)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE shows (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id   BIGINT NOT NULL,
  hall_id    BIGINT NOT NULL,
  start_time DATETIME(6)    NOT NULL,
  end_time   DATETIME(6)    NOT NULL,
  base_price DECIMAL(10,2)  NOT NULL,
  status     VARCHAR(12)    NOT NULL DEFAULT 'SCHEDULED',
  UNIQUE KEY uk_show_hall_start (hall_id, start_time),
  KEY idx_shows_event_start (event_id, start_time),
  CONSTRAINT fk_show_event FOREIGN KEY (event_id) REFERENCES events(id),
  CONSTRAINT fk_show_hall  FOREIGN KEY (hall_id)  REFERENCES halls(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE show_seats (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  show_id            BIGINT NOT NULL,
  seat_id            BIGINT NOT NULL,
  status             VARCHAR(10)    NOT NULL DEFAULT 'AVAILABLE',
  price              DECIMAL(10,2)  NOT NULL,
  held_by_booking_id BIGINT NULL,
  hold_expires_at    DATETIME(6)    NULL,
  version            INT            NOT NULL DEFAULT 0,
  UNIQUE KEY uk_showseat (show_id, seat_id),
  KEY idx_showseat_holder (held_by_booking_id),
  CONSTRAINT fk_ss_show FOREIGN KEY (show_id) REFERENCES shows(id),
  CONSTRAINT fk_ss_seat FOREIGN KEY (seat_id) REFERENCES seats(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bookings (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  booking_ref  CHAR(12)       NOT NULL,
  user_id      BIGINT NOT NULL,
  show_id      BIGINT NOT NULL,
  status       VARCHAR(20)    NOT NULL,
  total_amount DECIMAL(10,2)  NOT NULL,
  expires_at   DATETIME(6)    NOT NULL,
  created_at   DATETIME(6)    NOT NULL,
  updated_at   DATETIME(6)    NOT NULL,
  version      INT            NOT NULL DEFAULT 0,
  UNIQUE KEY uk_booking_ref (booking_ref),
  KEY idx_booking_show (show_id),
  CONSTRAINT fk_b_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_b_show FOREIGN KEY (show_id) REFERENCES shows(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE booking_items (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  booking_id          BIGINT NOT NULL,
  show_seat_id        BIGINT NOT NULL,
  price               DECIMAL(10,2)  NOT NULL,
  is_active           TINYINT(1)     NOT NULL DEFAULT 1,
  -- MySQL has no partial unique index: a generated column makes inactive rows NULL,
  -- and a unique index tolerates many NULLs. This is the duplicate-booking safety net.
  active_show_seat_id BIGINT
      GENERATED ALWAYS AS (IF(is_active = 1, show_seat_id, NULL)) STORED,
  UNIQUE KEY uk_active_seat (active_show_seat_id),
  KEY idx_bi_booking (booking_id),
  CONSTRAINT fk_bi_booking  FOREIGN KEY (booking_id)   REFERENCES bookings(id),
  CONSTRAINT fk_bi_showseat FOREIGN KEY (show_seat_id) REFERENCES show_seats(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payments (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  booking_id         BIGINT NOT NULL,
  gateway            VARCHAR(20)   NOT NULL,
  gateway_order_id   VARCHAR(80)   NOT NULL,
  gateway_payment_id VARCHAR(80)   NULL,
  idempotency_key    VARCHAR(80)   NOT NULL,
  amount             DECIMAL(10,2) NOT NULL,
  currency           CHAR(3)       NOT NULL DEFAULT 'INR',
  status             VARCHAR(10)   NOT NULL,
  created_at         DATETIME(6)   NOT NULL,
  updated_at         DATETIME(6)   NOT NULL,
  UNIQUE KEY uk_pay_order (gateway_order_id),
  UNIQUE KEY uk_pay_idem (idempotency_key),
  KEY idx_pay_booking (booking_id),
  CONSTRAINT fk_pay_booking FOREIGN KEY (booking_id) REFERENCES bookings(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE webhook_events (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id    VARCHAR(100) NOT NULL,
  received_at DATETIME(6)  NOT NULL,
  UNIQUE KEY uk_webhook_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
