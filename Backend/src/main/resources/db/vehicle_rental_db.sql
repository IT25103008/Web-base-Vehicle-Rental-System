-- ============================================================================
--  VEHICLE RENTAL MANAGEMENT SYSTEM — COMPLETE INSTALL
--  SE2030 · Group 9
DROP DATABASE IF EXISTS vehicle_rental_db;
CREATE DATABASE vehicle_rental_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE vehicle_rental_db;

SET NAMES utf8mb4;


-- ############################################################################
--  PART 1 — TABLES
-- ############################################################################

-- ---------- USERS (single-table inheritance) ----------
-- All three roles live in one table with a `role` column. Role-specific
-- columns are nullable and only filled in for the matching role.
CREATE TABLE users (
    user_id                 INT AUTO_INCREMENT PRIMARY KEY,
    email                   VARCHAR(120) NOT NULL UNIQUE,
    password_hash           VARCHAR(255) NOT NULL,
    first_name              VARCHAR(80)  NOT NULL,
    last_name               VARCHAR(80)  NOT NULL,
    phone_number            VARCHAR(20),
    address                 VARCHAR(255),
    role                    VARCHAR(20)  NOT NULL,
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,  -- disabled accounts cannot log in
    date_of_birth           DATE,
    registration_date       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Customer-only fields
    driving_license_number  VARCHAR(50),
    license_verified        BOOLEAN      DEFAULT FALSE,
    license_expiry_date     DATE,

    -- Staff-only fields
    employee_code           VARCHAR(30),
    hire_date               DATE,
    position                VARCHAR(80),
    supervisor_id           INT,                     -- recursive: staff supervises staff
    branch_id               INT,                     -- staff work at a branch

    -- Administrator-only fields
    admin_code              VARCHAR(30),
    date_of_appointment     DATE,
    admin_level             VARCHAR(30),

    -- Account security and personal data (gap fixes A2, A5, 3.10).
    -- Accounts created here count as verified; new sign-ups start unverified.
    email_verified          BOOLEAN      NOT NULL DEFAULT TRUE,
    privacy_consent_at      DATETIME     NULL,              -- when the privacy notice was accepted
    totp_secret             VARCHAR(64)  NULL,              -- two-step sign-in (staff and admins)
    totp_enabled            BOOLEAN      NOT NULL DEFAULT FALSE,
    anonymised_at           DATETIME     NULL,              -- set when a customer erases their account

    CONSTRAINT uq_user_employee_code UNIQUE (employee_code),
    CONSTRAINT uq_user_admin_code    UNIQUE (admin_code),
    CONSTRAINT ck_user_role CHECK (role IN ('CUSTOMER', 'STAFF', 'ADMINISTRATOR'))
);

-- ---------- BRANCHES ----------
CREATE TABLE branches (
    branch_id       INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    street          VARCHAR(120),
    city            VARCHAR(80),
    district        VARCHAR(80),
    contact_number  VARCHAR(20),
    open_time       TIME,
    close_time      TIME,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT ck_branch_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- The staff -> branch link, now that branches exists.
ALTER TABLE users
    ADD CONSTRAINT fk_user_branch FOREIGN KEY (branch_id) REFERENCES branches(branch_id),
    ADD CONSTRAINT fk_user_supervisor FOREIGN KEY (supervisor_id) REFERENCES users(user_id);

-- ---------- VEHICLES ----------
-- image_data holds the photograph itself, so photos travel with a dump of this
-- database instead of living in a folder that gets left behind.
CREATE TABLE vehicles (
    vehicle_id            INT AUTO_INCREMENT PRIMARY KEY,
    branch_id             INT NOT NULL,
    plate_number          VARCHAR(20) NOT NULL UNIQUE,
    model                 VARCHAR(80) NOT NULL,
    category              VARCHAR(40),                -- CAR / SUV / VAN / BUS / LUXURY
    manufacture_year      INT,
    rental_price_per_day  DECIMAL(10,2) NOT NULL,
    passenger_capacity    INT,
    fuel_type             VARCHAR(30),
    image_url             VARCHAR(255),   -- where the web app fetches the photo
    image_data            LONGBLOB,       -- the photo itself
    image_type            VARCHAR(40),    -- image/jpeg | image/png | image/webp
    status                VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    mileage               INT DEFAULT 0,
    CONSTRAINT fk_vehicle_branch FOREIGN KEY (branch_id) REFERENCES branches(branch_id),
    CONSTRAINT ck_vehicle_status CHECK (status IN
        ('AVAILABLE', 'RESERVED', 'RENTED', 'UNDER_MAINTENANCE', 'UNAVAILABLE')),
    CONSTRAINT ck_vehicle_price    CHECK (rental_price_per_day > 0),
    CONSTRAINT ck_vehicle_mileage  CHECK (mileage >= 0),
    CONSTRAINT ck_vehicle_capacity CHECK (passenger_capacity IS NULL OR passenger_capacity > 0),
    CONSTRAINT ck_vehicle_year     CHECK (manufacture_year IS NULL OR manufacture_year BETWEEN 1950 AND 2100)
);

-- ---------- INSURANCE (per vehicle) ----------
CREATE TABLE insurance_policies (
    policy_id      INT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id     INT NOT NULL,
    policy_number  VARCHAR(50) NOT NULL UNIQUE,
    provider       VARCHAR(120) NOT NULL,
    start_date     DATE NOT NULL,
    expiry_date    DATE NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_insurance_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id),
    CONSTRAINT ck_policy_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_policy_dates  CHECK (expiry_date > start_date)
);

-- ---------- VEHICLE TRANSFERS (between branches) ----------
CREATE TABLE vehicle_transfers (
    transfer_id     INT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id      INT NOT NULL,
    from_branch_id  INT NOT NULL,
    to_branch_id    INT NOT NULL,
    transfer_date   DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason          VARCHAR(255),
    CONSTRAINT fk_tr_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id),
    CONSTRAINT fk_tr_from    FOREIGN KEY (from_branch_id) REFERENCES branches(branch_id),
    CONSTRAINT fk_tr_to      FOREIGN KEY (to_branch_id)   REFERENCES branches(branch_id),
    CONSTRAINT ck_transfer_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_transfer_branches CHECK (from_branch_id <> to_branch_id)
);

-- ---------- BOOKINGS ----------
-- estimated_cost is the quote given when the booking is made.
-- final_cost is filled in at return: base + late-return surcharge + damage
--            charge - any credit from an approved insurance claim.
CREATE TABLE bookings (
    booking_id        INT AUTO_INCREMENT PRIMARY KEY,
    customer_id       INT NOT NULL,
    vehicle_id        INT NOT NULL,
    pickup_branch_id  INT NOT NULL,
    approved_by       INT,                             -- staff user_id, null until approved
    pickup_date       DATE NOT NULL,
    return_date       DATE NOT NULL,
    actual_return_date DATE,                           -- filled in at return
    special_requests  VARCHAR(500),
    estimated_cost    DECIMAL(10,2) NOT NULL,
    final_cost        DECIMAL(10,2),                   -- null until the vehicle comes back
    status            VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL',
    submitted_date    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bk_customer FOREIGN KEY (customer_id) REFERENCES users(user_id),
    CONSTRAINT fk_bk_vehicle  FOREIGN KEY (vehicle_id)  REFERENCES vehicles(vehicle_id),
    CONSTRAINT fk_bk_branch   FOREIGN KEY (pickup_branch_id) REFERENCES branches(branch_id),
    CONSTRAINT fk_bk_approver FOREIGN KEY (approved_by) REFERENCES users(user_id),
    CONSTRAINT ck_booking_status CHECK (status IN
        ('PENDING_APPROVAL', 'APPROVED', 'ACTIVE_RENTAL', 'COMPLETED', 'REJECTED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT ck_booking_dates CHECK (return_date > pickup_date),
    CONSTRAINT ck_booking_cost  CHECK (estimated_cost >= 0),
    CONSTRAINT ck_booking_final CHECK (final_cost IS NULL OR final_cost >= 0)
);

-- Supports the overlap query that prevents double bookings.
CREATE INDEX ix_booking_vehicle_window ON bookings (vehicle_id, status, pickup_date, return_date);
CREATE INDEX ix_booking_customer       ON bookings (customer_id, status);

-- ---------- HANDOVERS ----------
-- The EER identifier (booking_id, handover_type) is kept as a UNIQUE
-- constraint, which is also what stops a booking being picked up or returned
-- twice.
CREATE TABLE handovers (
    handover_id           INT AUTO_INCREMENT PRIMARY KEY,
    booking_id            INT NOT NULL,
    handover_type         VARCHAR(10) NOT NULL,
    handover_date         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_by_staff_id INT,
    mileage_at_event      INT,
    fuel_level            VARCHAR(20),
    condition_notes       VARCHAR(500),
    status                VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    CONSTRAINT uq_handover_booking_type UNIQUE (booking_id, handover_type),
    CONSTRAINT fk_ho_booking FOREIGN KEY (booking_id) REFERENCES bookings(booking_id),
    CONSTRAINT fk_ho_staff   FOREIGN KEY (processed_by_staff_id) REFERENCES users(user_id),
    CONSTRAINT ck_handover_type   CHECK (handover_type IN ('PICKUP', 'RETURN')),
    CONSTRAINT ck_handover_status CHECK (status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT ck_handover_mileage CHECK (mileage_at_event IS NULL OR mileage_at_event >= 0)
);

-- ---------- MAINTENANCE RECORDS ----------
-- service_date .. expected_end_date is the window during which the vehicle is
-- in the workshop. Bookings that overlap that window are refused.
CREATE TABLE maintenance_records (
    maintenance_id       INT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id           INT NOT NULL,
    handled_by_staff_id  INT,
    service_date         DATE NOT NULL,
    expected_end_date    DATE,                          -- null = single-day service
    repair_type          VARCHAR(120),
    cost                 DECIMAL(10,2),
    service_provider     VARCHAR(120),
    next_service_date    DATE,
    status               VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    description          VARCHAR(500),
    CONSTRAINT fk_mr_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id),
    CONSTRAINT fk_mr_staff   FOREIGN KEY (handled_by_staff_id) REFERENCES users(user_id),
    CONSTRAINT ck_maint_status CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_maint_cost   CHECK (cost IS NULL OR cost >= 0),
    CONSTRAINT ck_maint_window CHECK (expected_end_date IS NULL OR expected_end_date >= service_date)
);

CREATE INDEX ix_maint_vehicle_window ON maintenance_records (vehicle_id, status, service_date);

-- ---------- DAMAGE REPORTS ----------
-- damage_date   = when the damage actually happened (staff enters it)
-- reported_date = when the report was filed
CREATE TABLE damage_reports (
    damage_report_id  INT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id        INT NOT NULL,
    handover_id       INT,
    reported_by       INT,
    description       VARCHAR(500) NOT NULL,
    damage_severity   VARCHAR(20) NOT NULL,
    estimated_repair_cost DECIMAL(10,2),
    damage_date       DATE,
    reported_date     DATE NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'UNDER_REVIEW',
    CONSTRAINT fk_dr_vehicle  FOREIGN KEY (vehicle_id)  REFERENCES vehicles(vehicle_id),
    CONSTRAINT fk_dr_handover FOREIGN KEY (handover_id) REFERENCES handovers(handover_id),
    CONSTRAINT fk_dr_reporter FOREIGN KEY (reported_by) REFERENCES users(user_id),
    CONSTRAINT ck_damage_status   CHECK (status IN ('UNDER_REVIEW', 'RESOLVED')),
    CONSTRAINT ck_damage_severity CHECK (damage_severity IN ('MINOR', 'MODERATE', 'SEVERE')),
    CONSTRAINT ck_damage_cost     CHECK (estimated_repair_cost IS NULL OR estimated_repair_cost >= 0)
);

CREATE INDEX ix_damage_vehicle_status ON damage_reports (vehicle_id, status);

-- ---------- INSURANCE CLAIMS ----------
CREATE TABLE insurance_claims (
    claim_id           INT AUTO_INCREMENT PRIMARY KEY,
    damage_report_id   INT NOT NULL,
    insurance_provider VARCHAR(120),
    claim_amount       DECIMAL(10,2),
    submitted_date     DATE NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    CONSTRAINT fk_cl_damage FOREIGN KEY (damage_report_id) REFERENCES damage_reports(damage_report_id),
    CONSTRAINT ck_claim_status CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_claim_amount CHECK (claim_amount IS NULL OR claim_amount >= 0)
);

-- ---------- PAYMENTS (status tracking only; no gateway) ----------
CREATE TABLE payments (
    payment_id    INT AUTO_INCREMENT PRIMARY KEY,
    booking_id    INT NOT NULL UNIQUE,
    amount        DECIMAL(10,2) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    updated_by    INT,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    refund_amount DECIMAL(10,2) NULL,                -- what goes back when a paid booking is cancelled late
    note          VARCHAR(255)  NULL,                -- e.g. "No-show charge 7000.00"
    CONSTRAINT fk_pay_booking FOREIGN KEY (booking_id) REFERENCES bookings(booking_id),
    CONSTRAINT fk_pay_updater FOREIGN KEY (updated_by) REFERENCES users(user_id),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'PAID', 'REFUNDED', 'CANCELLED')),
    CONSTRAINT ck_payment_amount CHECK (amount >= 0)
);

-- ---------- NOTIFICATIONS ----------
CREATE TABLE notifications (
    notification_id  INT AUTO_INCREMENT PRIMARY KEY,
    user_id          INT NOT NULL,
    type             VARCHAR(50),
    message          VARCHAR(500),
    sent_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    channel          VARCHAR(20) NOT NULL DEFAULT 'WEBSITE',
    read_status      BOOLEAN NOT NULL DEFAULT FALSE,
    entity_type      VARCHAR(30) NULL,               -- what the notification links to (BOOKING, ...)
    entity_id        INT NULL,
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT ck_notif_channel CHECK (channel IN ('EMAIL', 'SMS', 'WEBSITE'))
);

CREATE INDEX ix_notif_user_read ON notifications (user_id, read_status);

-- ---------- AUDIT LOG ----------
-- Every status change in the system writes one row here, so "who cancelled
-- this booking, when, and why?" has an answer.
CREATE TABLE audit_log (
    audit_id      INT AUTO_INCREMENT PRIMARY KEY,
    entity_type   VARCHAR(40)  NOT NULL,     -- BOOKING / PAYMENT / VEHICLE / ...
    entity_id     INT          NOT NULL,
    action        VARCHAR(40)  NOT NULL,     -- CREATE / STATUS_CHANGE / UPDATE / DELETE
    from_status   VARCHAR(30),
    to_status     VARCHAR(30),
    actor_user_id INT,
    reason        VARCHAR(500),
    changed_at    DATETIME     NOT NULL,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users(user_id)
);

CREATE INDEX ix_audit_entity ON audit_log (entity_type, entity_id, changed_at);


-- ---------- ONE-TIME TOKENS (password reset, email verification) ----------
-- Only a SHA-256 hash of each token is stored, so a leaked table cannot be
-- used to take over an account.
CREATE TABLE user_tokens (
    token_id    INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT          NOT NULL,
    purpose     VARCHAR(20)  NOT NULL,
    token_hash  CHAR(64)     NOT NULL,
    expires_at  DATETIME     NOT NULL,
    used_at     DATETIME     NULL,
    created_at  DATETIME     NOT NULL,
    CONSTRAINT uq_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_token_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT ck_token_purpose CHECK (purpose IN ('PASSWORD_RESET', 'EMAIL_VERIFY'))
);
CREATE INDEX ix_token_user ON user_tokens (user_id, purpose);

-- ---------- SIGN-IN THROTTLING ----------
-- Keyed by "email|address", so one attacker cannot lock someone else out,
-- and kept in the database so a restart does not reset it.
CREATE TABLE login_attempts (
    attempt_key   VARCHAR(200) PRIMARY KEY,
    failures      INT          NOT NULL,
    last_failure  DATETIME     NOT NULL,
    locked_until  DATETIME     NULL
);

-- ---------- EMAIL PREFERENCES ----------
-- Per person and category (BOOKINGS / PAYMENTS / REMINDERS / ACCOUNT). No row
-- means the default: bookings and payments emailed, the rest in-app only.
CREATE TABLE notification_preferences (
    user_id   INT          NOT NULL,
    category  VARCHAR(20)  NOT NULL,
    email     BOOLEAN      NOT NULL DEFAULT FALSE,
    sms       BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, category),
    CONSTRAINT fk_pref_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- ---------- SAVED CARS ----------
CREATE TABLE favourites (
    user_id     INT      NOT NULL,
    vehicle_id  INT      NOT NULL,
    created_at  DATETIME NOT NULL,
    PRIMARY KEY (user_id, vehicle_id),
    CONSTRAINT fk_fav_user    FOREIGN KEY (user_id)    REFERENCES users(user_id),
    CONSTRAINT fk_fav_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id)
);

-- ---------- EXTRA VEHICLE PHOTOS ----------
-- The cover photo stays in vehicles.image_data; these are the extra views
-- shown as a gallery on the car page (up to 8 per car).
CREATE TABLE vehicle_images (
    image_id      INT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id    INT          NOT NULL,
    image_data    LONGBLOB     NOT NULL,
    content_type  VARCHAR(50)  NOT NULL,
    sort_order    INT          NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL,
    CONSTRAINT fk_vimg_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id)
);
CREATE INDEX ix_vimg_vehicle ON vehicle_images (vehicle_id, sort_order);


-- ############################################################################
--  PART 2 — DEMO DATA
--
--  Rows are inserted with explicit ids so the foreign keys below line up and
--  the file reads the same every time it is run.
-- ############################################################################

-- ---------- BRANCHES ----------
INSERT INTO branches (branch_id, name, street, city, district, contact_number, open_time, close_time, status) VALUES
(1, 'Colombo Main',   '45 Galle Road',                  'Colombo', 'Colombo', '0112345678', '08:00:00', '20:00:00', 'ACTIVE'),
(2, 'Kandy Branch',   '12 Peradeniya Road',             'Kandy',   'Kandy',   '0812234455', '08:00:00', '18:00:00', 'ACTIVE'),
(3, 'Galle Branch',   '78 Matara Road',                 'Galle',   'Galle',   '0912233445', '08:30:00', '18:30:00', 'ACTIVE'),
(4, 'Negombo Branch', 'No. 17 Kurudduwatta Road, Amandoluwa', 'Negombo', 'Gampaha', '0701912678', '08:00:00', '20:00:00', 'ACTIVE');


-- ---------- USERS ----------
-- Every password_hash below is BCrypt (strength 10) of: password123
INSERT INTO users
    (user_id, email, password_hash, first_name, last_name, phone_number, address, role, active,
     date_of_birth, registration_date,
     driving_license_number, license_verified, license_expiry_date,
     employee_code, hire_date, position, supervisor_id, branch_id,
     admin_code, date_of_appointment, admin_level)
VALUES
-- Administrator
(1, 'admin@rental.lk', '$2a$10$JsI/CLgJ1t50rgvV9LFhxOA9eaNlrbKp7zvlPiJroO.kA/3bRS32S',
    'Admin', 'One', '0112345000', '45 Galle Road, Colombo', 'ADMINISTRATOR', TRUE,
    '1985-04-12', DATE_SUB(NOW(), INTERVAL 400 DAY),
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL,
    'ADM001', '2024-01-01', 'SENIOR'),

-- Staff
(2, 'staff@rental.lk', '$2a$10$JsI/CLgJ1t50rgvV9LFhxOA9eaNlrbKp7zvlPiJroO.kA/3bRS32S',
    'Staff', 'One', '0112345001', '45 Galle Road, Colombo', 'STAFF', TRUE,
    '1994-07-03', DATE_SUB(NOW(), INTERVAL 380 DAY),
    NULL, NULL, NULL,
    'EMP001', '2024-01-15', 'Counter Staff', NULL, 1,
    NULL, NULL, NULL),

(3, 'kandy.staff@rental.lk', '$2a$10$JsI/CLgJ1t50rgvV9LFhxOA9eaNlrbKp7zvlPiJroO.kA/3bRS32S',
    'Dilani', 'Perera', '0812234400', '12 Peradeniya Road, Kandy', 'STAFF', TRUE,
    '1996-02-20', DATE_SUB(NOW(), INTERVAL 300 DAY),
    NULL, NULL, NULL,
    'EMP002', '2024-06-01', 'Branch Coordinator', 2, 2,
    NULL, NULL, NULL),

(4, 'galle.staff@rental.lk', '$2a$10$JsI/CLgJ1t50rgvV9LFhxOA9eaNlrbKp7zvlPiJroO.kA/3bRS32S',
    'Rukshan', 'Silva', '0912233400', '78 Matara Road, Galle', 'STAFF', TRUE,
    '1992-11-09', DATE_SUB(NOW(), INTERVAL 250 DAY),
    NULL, NULL, NULL,
    'EMP003', '2024-09-10', 'Counter Staff', 2, 3,
    NULL, NULL, NULL),

-- Customers
(5, 'nimal@example.lk', '$2a$10$JsI/CLgJ1t50rgvV9LFhxOA9eaNlrbKp7zvlPiJroO.kA/3bRS32S',
    'Nimal', 'Jayasuriya', '0771234567', '22 Temple Road, Nugegoda', 'CUSTOMER', TRUE,
    '1995-06-18', DATE_SUB(NOW(), INTERVAL 200 DAY),
    'B1234567', TRUE, DATE_ADD(CURDATE(), INTERVAL 3 YEAR),
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL),

(6, 'samanthi@example.lk', '$2a$10$JsI/CLgJ1t50rgvV9LFhxOA9eaNlrbKp7zvlPiJroO.kA/3bRS32S',
    'Samanthi', 'Gunawardena', '0719876543', '8 Lake Drive, Battaramulla', 'CUSTOMER', TRUE,
    '1990-03-05', DATE_SUB(NOW(), INTERVAL 150 DAY),
    'B7654321', TRUE, DATE_ADD(CURDATE(), INTERVAL 2 YEAR),
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL),

-- Licence not verified yet, so this one cannot book until staff verify it.
-- Use this account to demo the verification flow on the People screen.
(7, 'ravi@example.lk', '$2a$10$JsI/CLgJ1t50rgvV9LFhxOA9eaNlrbKp7zvlPiJroO.kA/3bRS32S',
    'Ravi', 'Wickramasinghe', '0765550101', '114 Main Street, Kandy', 'CUSTOMER', TRUE,
    '2001-12-01', DATE_SUB(NOW(), INTERVAL 20 DAY),
    'B9988776', FALSE, DATE_ADD(CURDATE(), INTERVAL 4 YEAR),
    NULL, NULL, NULL, NULL, NULL,
    NULL, NULL, NULL);


-- ---------- VEHICLES ----------
-- Photographs for vehicles 9 and 10 are loaded at the end of this file.
INSERT INTO vehicles
    (vehicle_id, branch_id, plate_number, model, category, manufacture_year,
     rental_price_per_day, passenger_capacity, fuel_type, image_url, status, mileage)
VALUES
(1,  2, 'CAB-1001', 'Toyota Aqua',      'CAR', 2021,   5500.00,  5, 'Hybrid', NULL, 'AVAILABLE',         15000),
(2,  1, 'CAB-1002', 'Suzuki Wagon R',   'CAR', 2020,   4500.00,  5, 'Petrol', NULL, 'RENTED',            15200),
(3,  1, 'CAB-1003', 'Toyota Prius',     'CAR', 2019,   6000.00,  5, 'Hybrid', NULL, 'UNDER_MAINTENANCE', 48200),
(4,  1, 'CAB-1004', 'Honda Vezel',      'CAR', 2022,   7000.00,  5, 'Hybrid', NULL, 'AVAILABLE',         22450),
(5,  2, 'CAB-1005', 'Nissan Caravan',   'VAN', 2018,   9500.00, 12, 'Diesel', NULL, 'AVAILABLE',         81000),
(6,  3, 'CAB-1006', 'KIA Picanto',      'CAR', 2021,   4000.00,  4, 'Petrol', NULL, 'AVAILABLE',         19800),
(7,  3, 'CAB-1007', 'Toyota Axio',      'CAR', 2020,   5000.00,  5, 'Petrol', NULL, 'RESERVED',          31000),
(8,  1, 'CAB-1008', 'Micro Panda',      'CAR', 2017,   3500.00,  4, 'Petrol', NULL, 'UNAVAILABLE',        8320),
(9,  1, 'KGG-3651', 'Koenigsegg Jesko', 'CAR', 2020, 200000.00,  3, 'Hybrid', '/api/vehicles/9/image?v=1',  'AVAILABLE', 2300000),
(10, 1, 'BMW-3362', 'BMW',              'CAR', 2020,  23000.00,  4, 'Petrol', '/api/vehicles/10/image?v=1', 'AVAILABLE',  223668);


-- ---------- INSURANCE POLICIES ----------
-- The app hides any vehicle that has no active cover for the dates a customer
-- searches (rental.require-insurance=true in application.properties). Every
-- vehicle therefore needs a live policy or it will not appear in the
-- collection at all. That is the single most common reason a fresh database
-- looks empty, so it is seeded properly here.
INSERT INTO insurance_policies (policy_id, vehicle_id, policy_number, provider, start_date, expiry_date, status) VALUES
(1,  1,  'SLIC-2024-0001', 'Sri Lanka Insurance',  DATE_SUB(CURDATE(), INTERVAL  90 DAY), DATE_ADD(CURDATE(), INTERVAL 275 DAY), 'ACTIVE'),
(2,  2,  'SLIC-2024-0002', 'Sri Lanka Insurance',  DATE_SUB(CURDATE(), INTERVAL 120 DAY), DATE_ADD(CURDATE(), INTERVAL 245 DAY), 'ACTIVE'),
(3,  3,  'CEYL-2024-0031', 'Ceylinco General',     DATE_SUB(CURDATE(), INTERVAL  60 DAY), DATE_ADD(CURDATE(), INTERVAL 305 DAY), 'ACTIVE'),
(4,  4,  'CEYL-2024-0032', 'Ceylinco General',     DATE_SUB(CURDATE(), INTERVAL  45 DAY), DATE_ADD(CURDATE(), INTERVAL 320 DAY), 'ACTIVE'),
(5,  5,  'AIA-2024-0107',  'Allianz Lanka',        DATE_SUB(CURDATE(), INTERVAL 150 DAY), DATE_ADD(CURDATE(), INTERVAL 215 DAY), 'ACTIVE'),
(6,  6,  'AIA-2024-0108',  'Allianz Lanka',        DATE_SUB(CURDATE(), INTERVAL  30 DAY), DATE_ADD(CURDATE(), INTERVAL 335 DAY), 'ACTIVE'),
(7,  7,  'SLIC-2024-0007', 'Sri Lanka Insurance',  DATE_SUB(CURDATE(), INTERVAL  75 DAY), DATE_ADD(CURDATE(), INTERVAL 290 DAY), 'ACTIVE'),
(8,  8,  'CEYL-2024-0038', 'Ceylinco General',     DATE_SUB(CURDATE(), INTERVAL 100 DAY), DATE_ADD(CURDATE(), INTERVAL 265 DAY), 'ACTIVE'),
(9,  9,  'UNIO-2025-9001', 'Union Assurance',      DATE_SUB(CURDATE(), INTERVAL  20 DAY), DATE_ADD(CURDATE(), INTERVAL 345 DAY), 'ACTIVE'),
(10, 10, 'UNIO-2025-9002', 'Union Assurance',      DATE_SUB(CURDATE(), INTERVAL  20 DAY), DATE_ADD(CURDATE(), INTERVAL 345 DAY), 'ACTIVE'),
-- Last year's cover on vehicle 1, kept so the Insurance screen shows history.
(11, 1,  'SLIC-2023-0001', 'Sri Lanka Insurance',  DATE_SUB(CURDATE(), INTERVAL 455 DAY), DATE_SUB(CURDATE(), INTERVAL  91 DAY), 'EXPIRED');


-- ---------- BOOKINGS ----------
-- Each one matches the status of its vehicle above:
--   #1 finished last month          #2 is out now      (vehicle 2 = RENTED)
--   #3 approved, collects tomorrow  (vehicle 7 = RESERVED)
--   #4 waiting for staff to approve #5 rejected        #6 cancelled
--   #7 a no-show: approved, never collected, one night kept as a charge
INSERT INTO bookings
    (booking_id, customer_id, vehicle_id, pickup_branch_id, approved_by,
     pickup_date, return_date, actual_return_date, special_requests,
     estimated_cost, final_cost, status, submitted_date)
VALUES
(1, 5, 1, 2, 2,
   DATE_SUB(CURDATE(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 17 DAY), DATE_SUB(CURDATE(), INTERVAL 17 DAY),
   'Child seat if available',
   16500.00, 16500.00, 'COMPLETED',    DATE_SUB(NOW(), INTERVAL 24 DAY)),

(2, 6, 2, 1, 2,
   DATE_SUB(CURDATE(), INTERVAL 2 DAY), DATE_ADD(CURDATE(), INTERVAL 3 DAY), NULL,
   NULL,
   22500.00, NULL, 'ACTIVE_RENTAL',    DATE_SUB(NOW(), INTERVAL 6 DAY)),

(3, 5, 7, 3, 4,
   DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 4 DAY), NULL,
   'Collecting at 9am',
   15000.00, NULL, 'APPROVED',         DATE_SUB(NOW(), INTERVAL 3 DAY)),

(4, 6, 4, 1, NULL,
   DATE_ADD(CURDATE(), INTERVAL 6 DAY), DATE_ADD(CURDATE(), INTERVAL 9 DAY), NULL,
   'Please confirm by Friday',
   21000.00, NULL, 'PENDING_APPROVAL', DATE_SUB(NOW(), INTERVAL 1 DAY)),

(5, 7, 9, 1, 2,
   DATE_ADD(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 12 DAY), NULL,
   NULL,
   400000.00, NULL, 'REJECTED',        DATE_SUB(NOW(), INTERVAL 2 DAY)),

(6, 5, 6, 3, NULL,
   DATE_ADD(CURDATE(), INTERVAL 14 DAY), DATE_ADD(CURDATE(), INTERVAL 16 DAY), NULL,
   NULL,
   8000.00, NULL, 'CANCELLED',         DATE_SUB(NOW(), INTERVAL 5 DAY)),

(7, 6, 4, 1, 2,
   DATE_SUB(CURDATE(), INTERVAL 5 DAY), DATE_SUB(CURDATE(), INTERVAL 3 DAY), NULL,
   NULL,
   14000.00, NULL, 'NO_SHOW',          DATE_SUB(NOW(), INTERVAL 9 DAY));


-- ---------- PAYMENTS ----------
-- The app raises a PENDING payment the moment a booking is created, marks it
-- PAID at the counter, and CANCELLED if the booking never goes ahead.
-- A no-show keeps one night: the amount drops to that charge and the note
-- says why, which is what lets staff still collect it on a closed booking.
INSERT INTO payments (payment_id, booking_id, amount, status, updated_by, updated_at, refund_amount, note) VALUES
(1, 1,  16500.00, 'PAID',      2,    DATE_SUB(NOW(), INTERVAL 17 DAY), NULL, NULL),
(2, 2,  22500.00, 'PENDING',   NULL, DATE_SUB(NOW(), INTERVAL  6 DAY), NULL, NULL),
(3, 3,  15000.00, 'PENDING',   NULL, DATE_SUB(NOW(), INTERVAL  3 DAY), NULL, NULL),
(4, 4,  21000.00, 'PENDING',   NULL, DATE_SUB(NOW(), INTERVAL  1 DAY), NULL, NULL),
(5, 5, 400000.00, 'CANCELLED', 2,    DATE_SUB(NOW(), INTERVAL  2 DAY), NULL, NULL),
(6, 6,   8000.00, 'CANCELLED', NULL, DATE_SUB(NOW(), INTERVAL  4 DAY), NULL, NULL),
(7, 7,   7000.00, 'PENDING',   2,    DATE_SUB(NOW(), INTERVAL  3 DAY), NULL, 'No-show charge 7000.00');


-- ---------- HANDOVERS ----------
INSERT INTO handovers
    (handover_id, booking_id, handover_type, handover_date, processed_by_staff_id,
     mileage_at_event, fuel_level, condition_notes, status)
VALUES
(1, 1, 'PICKUP', DATE_SUB(NOW(), INTERVAL 20 DAY), 3, 14700, 'Full', 'Clean, no visible damage. Spare tyre present.', 'COMPLETED'),
(2, 1, 'RETURN', DATE_SUB(NOW(), INTERVAL 17 DAY), 3, 15000, '3/4', 'Small scuff on the rear bumper, logged as a damage report.', 'COMPLETED'),
(3, 2, 'PICKUP', DATE_SUB(NOW(), INTERVAL  2 DAY), 2, 15200, 'Full', 'Handed over with full tank. Customer shown the fuel policy.', 'COMPLETED');


-- ---------- MAINTENANCE RECORDS ----------
-- Vehicle 3 is in the workshop right now, which is why its status above is
-- UNDER_MAINTENANCE and why it does not appear in customer search.
INSERT INTO maintenance_records
    (maintenance_id, vehicle_id, handled_by_staff_id, service_date, expected_end_date,
     repair_type, cost, service_provider, next_service_date, status, description)
VALUES
(1, 3, 2, DATE_SUB(CURDATE(), INTERVAL 2 DAY), DATE_ADD(CURDATE(), INTERVAL 4 DAY),
   'Hybrid battery service', 48000.00, 'Toyota Lanka Service', DATE_ADD(CURDATE(), INTERVAL 180 DAY),
   'IN_PROGRESS', 'Battery cooling fan replaced; awaiting diagnostic sign-off.'),

(2, 1, 3, DATE_SUB(CURDATE(), INTERVAL 40 DAY), DATE_SUB(CURDATE(), INTERVAL 40 DAY),
   'Routine service', 12500.00, 'AutoMiraj Kandy', DATE_ADD(CURDATE(), INTERVAL 140 DAY),
   'COMPLETED', 'Oil, filters and brake fluid changed at 14,500 km.'),

(3, 5, 3, DATE_ADD(CURDATE(), INTERVAL 21 DAY), DATE_ADD(CURDATE(), INTERVAL 22 DAY),
   'Scheduled service', NULL, 'AutoMiraj Kandy', NULL,
   'SCHEDULED', 'Due at 82,000 km. Booked in advance so the van stays bookable until then.');


-- ---------- DAMAGE REPORTS ----------
INSERT INTO damage_reports
    (damage_report_id, vehicle_id, handover_id, reported_by, description,
     damage_severity, estimated_repair_cost, damage_date, reported_date, status)
VALUES
(1, 1, 2, 3, 'Scuff to the rear bumper, paint only, noticed at return.',
   'MINOR', 8500.00, DATE_SUB(CURDATE(), INTERVAL 17 DAY), DATE_SUB(CURDATE(), INTERVAL 17 DAY), 'RESOLVED'),

-- Unresolved and severe, which is why vehicle 8 is UNAVAILABLE above: the app
-- keeps a car off the road while moderate or severe damage is open.
(2, 8, NULL, 2, 'Front offside panel dented in the car park. Wing needs replacing.',
   'SEVERE', 96000.00, DATE_SUB(CURDATE(), INTERVAL 9 DAY), DATE_SUB(CURDATE(), INTERVAL 8 DAY), 'UNDER_REVIEW');


-- ---------- INSURANCE CLAIMS ----------
INSERT INTO insurance_claims (claim_id, damage_report_id, insurance_provider, claim_amount, submitted_date, status) VALUES
(1, 2, 'Ceylinco General', 96000.00, DATE_SUB(CURDATE(), INTERVAL 7 DAY), 'SUBMITTED');


-- ---------- NOTIFICATIONS ----------
-- entity_type / entity_id make each one a link to the booking it is about.
INSERT INTO notifications (notification_id, user_id, type, message, sent_at, channel, read_status, entity_type, entity_id) VALUES
(1, 5, 'BOOKING_APPROVED', 'Your booking #3 for the Toyota Axio is approved. Collect it from Galle Branch.', DATE_SUB(NOW(), INTERVAL 3 DAY), 'WEBSITE', FALSE, 'BOOKING', 3),
(2, 5, 'BOOKING_COMPLETED','Booking #1 is closed. Thank you for returning the Toyota Aqua on time.',        DATE_SUB(NOW(), INTERVAL 17 DAY), 'WEBSITE', TRUE,  'BOOKING', 1),
(3, 6, 'BOOKING_RECEIVED', 'We have your request #4 for the Honda Vezel. The branch will review it shortly.', DATE_SUB(NOW(), INTERVAL 1 DAY), 'WEBSITE', FALSE, 'BOOKING', 4),
(4, 6, 'RETURN_REMINDER',  'Booking #2 is due back in 3 days. Please return the Suzuki Wagon R to Colombo Main.', DATE_SUB(NOW(), INTERVAL 2 HOUR), 'WEBSITE', FALSE, 'BOOKING', 2),
(5, 7, 'BOOKING_REJECTED', 'Request #5 could not be approved: your driving licence is not verified yet.',    DATE_SUB(NOW(), INTERVAL 2 DAY), 'WEBSITE', FALSE, 'BOOKING', 5),
(6, 5, 'BOOKING_CANCELLED','Booking #6 for the KIA Picanto has been cancelled as you asked.',               DATE_SUB(NOW(), INTERVAL 4 DAY), 'WEBSITE', TRUE,  'BOOKING', 6),
(7, 6, 'BOOKING_NO_SHOW',  'Booking #7 for the Honda Vezel was closed because the car was not collected. One night (7000.00) is payable at the branch.', DATE_SUB(NOW(), INTERVAL 3 DAY), 'WEBSITE', FALSE, 'BOOKING', 7);


-- ---------- AUDIT LOG ----------
INSERT INTO audit_log (audit_id, entity_type, entity_id, action, from_status, to_status, actor_user_id, reason, changed_at) VALUES
(1, 'BOOKING', 1, 'STATUS_CHANGE', 'PENDING_APPROVAL', 'APPROVED',      2,    NULL,                                    DATE_SUB(NOW(), INTERVAL 23 DAY)),
(2, 'BOOKING', 1, 'STATUS_CHANGE', 'ACTIVE_RENTAL',    'COMPLETED',     3,    'Returned on time',                      DATE_SUB(NOW(), INTERVAL 17 DAY)),
(3, 'PAYMENT', 1, 'STATUS_CHANGE', 'PENDING',          'PAID',          2,    'Settled at the counter',                DATE_SUB(NOW(), INTERVAL 17 DAY)),
(4, 'BOOKING', 2, 'STATUS_CHANGE', 'APPROVED',         'ACTIVE_RENTAL', 2,    'Vehicle collected',                     DATE_SUB(NOW(), INTERVAL  2 DAY)),
(5, 'BOOKING', 3, 'STATUS_CHANGE', 'PENDING_APPROVAL', 'APPROVED',      4,    NULL,                                    DATE_SUB(NOW(), INTERVAL  3 DAY)),
(6, 'BOOKING', 5, 'STATUS_CHANGE', 'PENDING_APPROVAL', 'REJECTED',      2,    'Driving licence not verified',          DATE_SUB(NOW(), INTERVAL  2 DAY)),
(7, 'BOOKING', 6, 'STATUS_CHANGE', 'PENDING_APPROVAL', 'CANCELLED',     5,    'Customer cancelled: plans changed',     DATE_SUB(NOW(), INTERVAL  4 DAY)),
(8, 'VEHICLE', 3, 'STATUS_CHANGE', 'AVAILABLE',        'UNDER_MAINTENANCE', 2, 'Hybrid battery service booked in',     DATE_SUB(NOW(), INTERVAL  2 DAY)),
(9, 'BOOKING', 7, 'STATUS_CHANGE', 'APPROVED',         'NO_SHOW',       2,    'Not collected within the grace period', DATE_SUB(NOW(), INTERVAL  3 DAY));


-- ---------- ACCOUNT EXTRAS ----------
-- Customers accepted the privacy notice when they registered.
-- Temporarily disable Safe Update Mode for this bulk update, then restore
-- whatever setting the session had before the script started this section.
SET @OLD_SQL_SAFE_UPDATES = @@SQL_SAFE_UPDATES;
SET SQL_SAFE_UPDATES = 0;

UPDATE users
SET privacy_consent_at = registration_date
WHERE role = 'CUSTOMER'
  AND user_id > 0;

SET SQL_SAFE_UPDATES = @OLD_SQL_SAFE_UPDATES;

-- Nimal has saved two cars (they show under Saved on any device he signs in on).
INSERT INTO favourites (user_id, vehicle_id, created_at) VALUES
(5, 9,  DATE_SUB(NOW(), INTERVAL 6 DAY)),
(5, 10, DATE_SUB(NOW(), INTERVAL 2 DAY));

-- Nimal's email choices: everything except the account category.
-- Everyone else uses the defaults.
INSERT INTO notification_preferences (user_id, category, email, sms) VALUES
(5, 'BOOKINGS',  TRUE,  FALSE),
(5, 'PAYMENTS',  TRUE,  FALSE),
(5, 'REMINDERS', TRUE,  FALSE),
(5, 'ACCOUNT',   FALSE, FALSE);


-- ############################################################################
--  PART 3 — VEHICLE PHOTOGRAPHS
--
--  The two cars that have a real photograph, stored as bytes so they arrive
--  with this file and need no separate upload. Everything else in the fleet
--  is drawn by the web app instead.
--
--  This is the only unreadable part of the file. If you would rather not have
--  it, delete from here to the end of PART 3 — the app simply draws those two
--  cars as well, and nothing breaks.
-- ############################################################################

-- Koenigsegg Jesko (vehicle 9) — image/jpeg, 24.3 KB
UPDATE vehicles
   SET image_data = 0xFFD8FFE000104A46494600010101004800480000FFDB00430006040506050406060506070706080A100A0A09090A140E0F0C1017141818171416161A1D251F1A1B231C1616202C20232627292A29191F2D302D283025282928FFDB0043010707070A080A130A0A13281A161A2828282828282828282828282828282828282828282828282828282828282828282828282828282828282828282828282828FFC200110801D202E003012200021101031101FFC4001B00010001050100000000000000000000000001020304050607FFC4001801010101010100000000000000000000000001020304FFDA000C03010002100310000001F4700000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000B25E6BEA3398B78B80000000000000000000000000000000000000008A0B8A6B2172A2CC64D661B364C1674186CBA4C66452595525098000000005740CBA757826FA8D7E59AED574DE4877F91E2959EDD77C4B34F68B9E5FE8666B1AB2F22400000000000000000000000000000582FD9E5B924F4FE078FAF737B9DCF75095576B81CDF52AB88CE9CBB2CAE46E49D965701457A564F9446AFAF4F91E4B5EA6F36C99BED72F51B09ABF4D42CD9CC1CF6ABB2C0396B599A732A9D7A5CFA70A0CD61526DB4DB5C4312AB306CF2B4639AD96E2F59A58DFDC307BAE7F7A63ED2AC812000000000000000000000000001ACD39D4F2DA6B697AD64EBEF3B585B5DCB1CEEEF6B4DBADE8B4D9F3A6FB8EA35A62DFCACDDF8ECD39B2D6155985C5AB2656C5DBB6A6ADD793D4E7AE462E5E2CE9936C25125C4060E6C1C8E2F6F49C56B3D1C79B55E8E3CEAE7A14471B9DD1C1A9CDC9A08A222A22F498D7EA000000000000000000000000A8A55D242BA8D7F05E836CF33DBF736D396D4F6F4B1E63D975145BAFB5936570751D572272BD9F0DEB32EA27ADBDAE7CC3B1A9790B9D5A5E66EF425D1DCDBC4BAECE8B66530685D8E361E39BC68E0DE344378D0C1BF73B49D1D1CDDA3A5B5CCDBAE9ADF372744E2F5A7A45EF2FCA3D32392D99B8B562D97E751A2C7A3B7B9E699D9D7A0B9FDC74F2E40000000000000000002692AAB1F546D741C45A3698518667ECB99CF3BEDBF8D6E4F54B9CEE4D6F2CE0604559D72933EF6BAD9B3B18105FA2CD066308665389064D1620BB4514934A09849115D458C7CBBE635DB56CCCAB12F17E6C6A4DD5AE43AF2ED17292D5177CE4DB7356AE9B19B590516E60C3CAC6C63D1F63E45E985DD176B731ECF39D27B0467A705DA5E9E9E1D831F22E400000000000001029B34972D45A30FCBEFEE85C7212F591E75D51393C3D47776B53B74B3E8DE51D2577142E114D15991162B2A5124C02245315410910A844CC8AB1AC1B0B3AAD69B9A39AC53ABE76BC33194688CD637A8193B3AA0455AF38EC1D47486A765674D181ABEA3555ADCF651D56165EA972F4BD0516779B0F36F428C8AACD45EA69D115F57E77E8901400000000000B65562DD04A9D446E39DBBC8D68FA7E7BA55E7B96EE79D4D7741A2DC4BA39CCA88DC6C28CE74172FE2EEFAE5FE0BB2E5BCA62646B34DFB191B91335144DDA8B3391263D574515C0AEAB505FD5670D25AE82D1A4CCC916AD4E0987E6BBDE7CAEF51E9455D3CC94C55491C077BE526D6CE5690C6DCE97B0F1F1E571F1361EBEF81D16BAD181ABCEC2B3D06BE6F7A739EC5E47E8C6F2AA6B1CD749E3E7AAEFF00CEFD1000000000000061E60E0773A3D41D568FA1D894797FAA7929B5D0745C798DB8D0DE3A2B317A55DB3625ECB55D2F1FE4F36AF65897BDBDF69DC79F77C5F8B62FB41946EA70F24BB55AA4C85817A2DC17671065CE1C99718B3572DD505566D62956B2ED88F39CAEDBA9359D15AB8548114CD251E45EB9E466EF9BD867983D1E1DEE38E53272ACF4EB8FAFE875C6AF5DD2E7EB3C8F4FB3D2993D7713DE9D0553262F87FBB7989BAF48F2EF5100000009109109109C1336DE96D8F36DA684F4CBDE5BBAE79EEBCCB77C81D1F3F1BCE9AE2F23659661E5B5D2ED6AD47409BEE2737038E373A8DEF2BDF7D6F6DE77967735737D11CE61F5FA189CFC1CFAAEFAF1455724B75D505FAB120CDA712A2FDBB70574DBA0C8B985499ED6D06DAAD2C9BBAB4970DC4EA46D9A1B52743E61D8E9F2E4AFE9FA5DDD26C30F0319C8B197AFBADC58D8F3DA5FAF07A430EFEC6B355EA9C36DCEA6789C43D0B07CEEDE66C7D73CAFD5203740000AD5C14C56285429C3CBB2737ABEBB1CE0B45EAD44793D5EA5665F37BBDDEBEB79C4C7389B2E838CDC999958564EB72386CCAA71F02C13AABB967456399D86B39F9755C5DDDEE632FCDD73B16D626A6C1AE9E99CEAB046CEBD5D667C6BA93671ADA8BDABCEBA73D63A9A4E6AE74970E4E3AC93939EB072B3D4D93437F616CBD730F37128B94682B0361A98DBBAE76DF4273583D6E0191A7DFE39391958467E4E0E5442E5E5C5C9CBD82466D193899B91877B76FCDB9AB8A2A2502417D00088A85BC6CD834989D2C1C9D8EC69388B5DDD31C061FA5D2BE4F85EC94D9E373EC43CB2F7A6C9E5DADF63A4F1EBFEB43C7EE7AE0F1BC9F5B93CC6AF4EAA5F2F9F5093CB67D44797D5E9E3CC28F5383CBEE7A68F319F4D93CC2E7A58F35ABD2079BD3E943CCE7D289E6B1E965F37A7D2A0F35A7D3079947A70F31AFD2C799617ACC278E5BF668AF197B341E3F3EBE3C7F3BD524F2BD87A20F3DBDDF49C3E4763272995D149A7C9CF931ABBE2D2F416D745B5C8295421024900125315C1445C82DAE4169760B4BA2D45D169705B5C142B828542252262499A64A94C95299250250266991348A94C9300000448022440213042452AA4A158A550A6664A664448004099A454A44A04C409402249449331200022600213022440112222A8113022600004C4152992A522A9A454A454A454A455348A94C928130098128128128000904801221200800089808910000048854226426442442452AA084888911150A52222A14A452AA088AA08891008000240004800481242400000000026249001200008000808989200012131509491212000044884C10914AA821221222244455044550445504455052914A444824004812004C0900089800000482624000900004104C4C0040250240484A4025124A24000104C00002000402260102004080204A04A04A24944928124128913026204A04A04A04A04CD22A4099A64940A9025025004134CC130825104A054A64AA699266992502502A9A24A948A948AA104A04A20AA204A04A04C40984130826101104C2094415204A04A054A6499A454A454A455148A948A948A948AA29152915A815CD02B9A057344952915A9154522A8882A8A455148AA291529154D02B9A05C9B725C80484800480100020000080402010100804000000484820004000000402024090484824120804022020004480090487FFFC400331000010402020103020504010403000000020001030411120514131015210620162223314024303250352533344142A0B0FFDA0008010100010502FF00F08B919F0C44B624D226317FF7995965959FE28AC32D5968B0AE4835C4793A8E86E5624C6049B664D2BB21312FF67BB2FCEEBC64BC4BC42B41586FB30B0CB415A32D13365F57FEFD89CE011E5A1743C8C0486CC64A43848397A3D6917CB20965CC57E713E32F589AEB112F22F2326767FF00572C811059B31D70B1CFCE2EDF514998FEA595D7E2495937D4C87EA2176FC438517D41012F7DA68799A249B92A8E86E5624D2C65F6C1EAECCEB444CECD25A2045CD422EFCF40BF1040BF10C2BF10C4BF10C6BF10C6BF1082FC402A3E6BCCE76AA6FD9A4BCD454376BC453DE1B914ED2D554AB938355086E4BC765F818586F131212B02ED1BA66C37FA89A58E08E6E54E56B32B3958B16252A9090957A6533C51C43095707095BCB3568354EFF31C5F1E265E165D6175D315D34D58D908DA14335E14D779016A7E67AB196C3F6108929AA048D371EE28E3D48F504C00EBC2CBC42BC209E08D71F042E572B45258F6E85D7B644BDB225EDF0B27E39A58DE09557A1223E2E4DA8D46A71C7239268FFD41130AB9CE578145C9F2169CC018E59889F0EEA1AE7228AB04488189EAD48C19E6165CAC311CAEDF15A3658585AAC2C2C7A33235042F624651FF00DE7F85BB6766C6CDF64B0310CB4CC1A1E32591E4E36C468DE5853482EB6F416774156624341D0D28990C603E84224BC40BC42BC209999BFD3DCBD5AA31F22EF1C876AD28685681E491DD38BBA8EB948F15300585A2F3C5159908999E6125D482071809D4716AB55AAD56AB55AAD5118B28EB4B33C110C31A6FFC8D530B32D5930B37DB85865A8AC0AC0AF85F0BE17C2F85864EB65B8A67CFFA8B3622AC16B9396CBD682427AD4E1AC8C9DD5A9DA018FB969EAD5D43E198A48C51DC8454D6A6955A9C40A2A8D1D7D5DD0C6D979235B0BAC13A68E674D5EC3A6A93AE89A6A029A8C082300FB08BF5B2B3F7E56CC9E414F346BB112ECC2BB50AED42BB30A194096565961938AD197CB2F226367FF418585904CE0EB5164552B9BF42AE7A90BA7A15DD3F1755D37155B6E9C68AAD78C7C75E44FC6B11B71A19E723681C489CF8DAA2757A55D356805308B7DF965E405E68D76225DA8D76C13DB673ED92ED48BB122F34ABC922DE45925F2B09C5382705E35E352DBAB122E5EB32F7981072550D412ECC165D6E9E4562E042A5BD392DB9051DAB71BC3654726DFCD39C455BE486252F311BBFBA4A8794350731166BDEF2304E2498D9312CA9A50863ECD9B2F1520171FCCBF659646F1BAFD265E4165D85E775E725E635E69179245B1A7CAC2D596197C7A97C3313030609B0B55AAD1689D859310117C7ABBBABDCB8C4EE362D9FB7042D8A629FA4EBA95645371F3D755F969612AF606400F248ADF1A160E5E1E66474AF8B1D0B1BD394601ACFB37F2729CD5BB4314767939ACBC1C71C832B56843F51D1118A783CB50E096B494F95FCC0794C498DD59FD49E326169A4CB6EB6595959595959595959595959FB30B0B558D8FE1918C24BE5931489B6F4B16EA4286FF6A688340F475C9F2276A411181A3795886ABECF0C60A31864138019491C9022F96865978F968DB0B5101E5B3E9864F10128E108D04A04FF00C6D9653BAB7602BC334B2F213B47151128A49CA49ABD455ADF6230E4C70DE1B0BB060372A8E38DBC54E4674CA442EA57FCB95959FEE616163D653D44E568984B769A786069F9B8C57BB5D34FC8DC0525B9E450F9653A155AAC3EBF505E7CD0AA2C211BC936BAD39AECC6BE5FD22B72C4B87092779608DDAB40D3C304C7C7DA8251923CFD866C011423297F15FF0074E9D72B6BBD72066A55A18FC6ACF206801DD70FF11AC3B2A97B6438AD2591025C05CDC597EECC89B618CB60FEEE1D6A49CC191588D915D8197B8C2EF2DE124D2484A2A0A4BD1D7966E52790100B9171741AA47EB7AC355AB4C4A43B9FA436A51A15E84D3D59395AA311B33A93E171F51ECCDCBDB1AB0F1B6CA4293F4A4E5211907E9CB2EC41F67327AD1E23F43F88EE89FD7E573963AD429E01A322925E66D7C8ADD715B31B464E3955621351C82E3F2C7B1413466C60C8BD03E0FEEC2D568B55AB2F8595BBA9AB9484F432BDAABAE856636AD00AC30A235C859F0562FDD0B65F88E3BAC3F67D513AE307C2AAB6668BFABB7C94210D5A56C7C51D337B3761F0BD39FA54A6172767C3C06D6A1A8FE7A644F5EE0BB3FD9CA60A5E365949FF00844F84E5EB3DD68E4AD6659E6FAA25DAD0B60E23A5D59B888655678EB100C60D8AB2E258F22821EC3C7C67821ECD2AAADBE6C1FC8715727EA33BAFD5F2B23F87F4C32F85F0B2B2B659595959595959F490F5116D59DD13A375CBCDBD87FDC05C9F89E37AFF007736FE4E5FFC78F7935E37868CA59B9E8B6AF2880C707F5513C600E65E7623CA3FF2E2E6D438C3D793E423D5B893F271ADE9FF00BBF65A4BFF004F59925B3FC2B39609EF983D0B0D66BF216FC03569C93B80B00F30FB7356C59AAD899D82332678EFCCA61066F34912909E58BF3343C7431CB5B986FE87FCE5E41986F7D365EAFF00BFEE31BE47D3E561D61D61FF00B27230A167CA7746488FE653DE5AD5A5B3271BC705466FB5D5EFF9995FFE972FFC7F0F3800DC32B91744C5860CD4841CA568DD15598409941967E3FF00F2B97FF2E03FE31BD2C169078DC9FE9A678A7FE1FD415B6938033608EBC609BF65CC7C73367F3717610BFA45331411F8A6574F687CF1495288E9C7F252BF5EA0EF6ECC9B5AFA77FEF37A16751E410DA416366F22F2B2F32F23AD9653BE179417959791D6D22C4AEBC6998419CD91488A544D21A6AB33BC1C2C225146318B7DCEB96FD3E5E53776A54BB31B0F194D4BC9CBE0A6E1217240D5248243B1372030BCB56ECB59BB14ACA878B8C900942FC813943C206BC77A482C6138F424E16779394FE16E2CB929E1F1D5B1141C80CA2EB75E465F5136BC8512EC5131D989B5217C8B405141C7D86815CB6D30DAA80F582EC6F1D93F35AE3BF219FC45F4E0E47D79187492325196131BA62274DB2FCCB0EB464D10A61585F0B765BACAC2D19683F665656565656565653AFA963D6E816F16CFA0CC55AD4E5E68213271932E533491D97CA77C217CA876077CB94E2F62E42CD1C7BAF22DD95918678EA3057E63F813CBABB9A7365C9990CB03F9ACF4CDCE1A900B782A2BECFD9E3EC1569EF44D1CB341BB715C7BCD2DF69EC9EB85AFCF1A650B5DA92D59A38F5569BAD5EC9FCF1EC4157C92B3D6BFE84CC4274885C2195904469A35AB2FCAB665E45E475B3FAE56CB75E465E615E705D9893DA8576E05DD81776BAEEC0BB70AECC6BB408AF032EE1121B12E79E0F351AE7A94354E58E41631023AE535A8DE3626797957D5CA67740CE4F040D14704272CB6CA289F8C01ACC7C8C4C8B958D172CE8B939CDDE3B76150E3FF005FF8162B8CCD271F659495B91152F7C54AD2B90B6AB6426C99E11B054691421FD39066392E4D1F5F8FE4ADD58A56E36C0434F8D865BBC9C2F5FB7FF4FA520C4A791347B10E1A32CFA57B12C4A39AC9A72BA8EC5C15DCB4EBB171D14B7933F204CC3C8BA6AFC83AEA5C5D2B0BA122E8327A300A3869B290604E31AD4178B29E9C8CBAC4BAE4BAE6BC122F04ABAF226A923A1AC02F1CB1C69EE2EE2EC818941A1549CA229620B6271FCBD362668480F9B89DD43C799BC63040804A79A491A245C3B133F082EBD8C57B10A8F8281947C656061A1599451C71367F80E58452A7957957993CE9EC229D94B69854DC94AA7B52CCE24AB44F618BB102F33AF31E5A9CE6D33331EDF06F92FF00E3E42CC4523A899F062EB574D248CCF34ACB6337F95858F4F85F956CCB6659F4CA624E4B775BADB2B65BAF232F20AD991616593E1328C94A4A777459439650CC40FDA86CC73578FABB12B165E660079DDE06858AD398D666ACDD824D61D3587416104F94D22134CE99FF00BEEC8A377475CD3C132786C228ACA78AD2F1594F0D97474AD13C9C5D974FC45A5ED36D97B7DB517BA42CF3724BCDCA624AF7E476A169D3F1F69D7B559CBF1B6B1ED5690F1F699055B4CBAD6DD756DAEA5B5D4B6BA76974EDAE95A5D2B4BA36D746D2E8DA5D0B4BA16D7B7DC5EDD697B7DA5EDF6D7B7DA5EDD6D7B7DB4DC75A5EDF697B7DA5D1B2BA36574ACAE9585D1B0BA56174ECA6AB65155B2E8F8DB249F87B4BD9ADAF68BABDB2F0A6A77C574ADAE8DA51C1C8F8C38B35D49B0D4664DC7CA9B8E91371E49B8F434B086AB26805346CB5585858FE16161616ACB565A32D1968CB465A32D1968CB465E35A2D168B45AAD56AB55AAD56AB55AAD56AB55AAD56AB55AAD56AB55AAD56AB55AAD56AB55AAD56AB55AAD56AB45A2D168B55AAD56AB55AAC2C2C2C2C2C7FF4B8FFC400281100030001020405050100000000000000000111021240030413301020213141223250608090FFDA0008010301013F01FF0019A8D9A84E8F8D1C3AC75CEBA3AA8D4BB33754799EAC8628CFD88693490D2638F9E9777A4D26920D1D33A68D08D288BBFC3E5B89C45521F2B924353F19C2E778BC251333E79E6BD5193BFA07CF8638D1A9B2CBEEDCFBBDAE95B36E09DF2AC23D9D5E0D79DB484EECFEA26463A7E4697C1084210842108421A513B3A5776F6294A5294A5294A5294A5FE52FFFC40026110002020005030403000000000000000000110102031012134022303120214150428090FFDA0008010201013F01FE33D6ACB42CFDC52693448BB2F9E98B363267B084217358C7C19BC41B91F5B387126D223E9DF27E72C4C58A7C14B6A87C192EF73CE4F8EDE247124DB86F876C48A95BC5BD08C3C2D32F8533106B89CA6BF247AA6F15F256F16F1C3E93A0BEB7D32526DF90C6318C79318C7969822B1D9DBA8976D0BB084210842108421084210BF547FFC4004910000102030307080607070401050000000100020311211231320422334151719110132023526181A140426292C1D114727382A2B1E1243034505393F0436383B260A0A3B0C2F1FFDA0008010100063F02FF00E08BCD2AF2AF550AFF00FC52AAEE8738E0EB1AE42E5A596F0A91E1F159AF61F159AB382A1FE674AEE546F155701B955C55CAE0AE1D1B82BB92F440D5E816ACDA66BDA16CE2B10F7950A21E45937CD4DA0F34EC27928E2A4C7BA69B6E2C4B13A806A98DB64B27AF62BD547253F95DA8AE0C6ED2897B84F53677AA36001BC959D0D87C0FCD49B0A1FBA7E6AB0A179ACE86CE254F99CDEE7ACEC8E2859D0A337EEAA98837B569BC97F10C548F0FDE59B11A7C7A310FB67A14574D57278A7EAD548C3880F78581EB46F5A372D1B968CAD115A32B465596402F3B110F80F6BB58B7258227F79685DFDE56A1C2703F6B34E851215A0EF6C27C07E1749D75E8BDEC327364D9C39F8A97D361B0B5B6EDEC3B37A83CCC5E74C67481953BCDEA3186FB6D86241D74D66BA478AEB18C78DACA7E7FCAEDC67B58DDA57EC50B37FAD1683C06B56E398994C4DAE19A370585F5EE45F19933AA68BE2F570CFABAD0930008BF997B9A3B2C9A2EB1CDB2E00205C2BA86C57ACF256272BF885EA7BAB0B15DC1CB35D106E72CD8F182FE20FDE0B1423BD31C4B6D3B3A52DAB61B88E8E70057CEAA8DF77E47E6A44439EC70B07CD7590AC7D66AA36195A362D135689AB4413D96645DDE9D8B62C5116922792D2C5F25A58AACFED0E1B76200E59104A82641F8270195652C999CE42AAD1CBA3974ACCFB910C712499CD488AF72AFF28CE324432711C3C029C1870E142ED387F935CE477BA3C6ED44D5B87255668A6D2A6739DB551AA7124E77E4AF4DCA192B773BE6A8A66FFDD086305EF3F0E48A371E435BBA44791B93BE8CD734F65A734F82FDA214287DF0DD5F92EA62078D8EA2EBE1399DFA950F2E6827705865BD67C4E01566EDEB35AD1E1C99C01572D6AE5412FE4FD7C500F6454A11223864B04E1D711DF252C9C181075C488738AB4E9C689B5FF2E5CD13537E71F254E486C2E16C9E0A64D91B4D14985D17EA5DC51309CF739F8A6E9A9A33BFF712B426B35B61BDA70F820C65DC8FFAA3E2AF2B5F253A772B82B874EFE4D8A9FCA2D467591E65110E28C9E0F17153C9A1D8DB1A2D5DFA2B5A489DA77256AE3705996A5ECE6B575CEB6548502CE72CDB4E3DC1499D5B7BAF5CDC116E26D4D644EB1D7B8BEB5572CE94BBD636F15499DCD548514FDD5480FF001202D1B06F7ABE10E255637062CE8B14F8C95596BEB1256635ADDC3A10C6A33FDD5E1621C5636F15A46F15A46AD2058D6902CD734F8F4B1725FF00C8B1378AA16F15729BA0B09EF0B40CE0B0799580FBC5617FBE55A2D738FB4E9ABDFEF29C4710DF69EBAA82F8BDF320299B0CF65B55FAA0D646E6F6D095285162BCFB10933E9022DBF6DCB440EFAAA4287EEAA003A778589BC5630B12D7C15CE530C3458071585AB52BFC96358CAC4EE2AF3D3CE8CD27636AA8D88568DFE4AAE2DDED538118387719ACFE3CB9E6BA9A2F5D5340F1AA98118FDF0808D0DEAB36FA74854AEB2286FB2DA95990DCFF00AC553276F9ACEC98784D48F3908F153696C46FB2A87A05F10C9A1752DE661F69D572B714988FED3CCD528DE4BC2CEB2553C82A4D612B0AC217AAAF1C16358DCB11E3D39ACF05BDE429B483BBA552149A67B95CAE1C97A2C83D6BF7D029E5111F33EA37E4BAFE6A17DB3A67805A673BECE07CD69238DF042CC8F009F6818655B65B6FB578E2158CADB686DD6ADE4F6A237B8517A8C1EF156ED39AFE2B322433E1252CE9773D4DCC76F9A10DE6203EDA12F4C2F88EB2CFCD1664E39B86B9C8920CEDBCC829437988FEE165AA8DF25512DE24B9F3648024E9D4CE772CDB70E20D4558CAE87B617C79618761026A81346A2E015FE89DCDFCF92AC13DA28B362C41BF39697F005A47F92C4FF7967C464F64ED15CD647004FB4EB820D1C76F43E8F9268F6F6BF454ABF6ACC7BC176A6DE5758E0D76CC4E59C227DE786A9B61441FF209AD23A19D915BF159D3603AC1CD2ACC4136AE72099B0DE36EF5CE4235D6DD9D1AB42CC12566DB6D0D5E94E8B14E68F356E261B834216DA1F1F543D4DDFF00241F95BDD338582F5273A1B0EC95B72CA9C0DA0DB366D34037A94673ED6B36010BAA2DB5EC50F056329EB616A77655B0EE721BB03F5F8AE6728D17FD7941F0E4076107D12988D02B33A053172EBE2B5BDD352C9E15AEF72CC03C18A4E8D9FB2428B3E2B9C9B0E189B9DAA4ACCED3CE276DE87D120DFEBCBF24EB664C667457FC119486B33B981448B90C42E734F5931523B91687586F6594E4AAEA9EEB3D93509CF8928793CAAC1727BB24717C36DEC3780A235B58805A03B635A1121D58756D09B16199B1C27D12E7190154D751C1C6731E9561A7A96507CD3624BAE78CCF646DDE8BE24B9C95A25D7431F35121E4F122736E74EDBB1395565806C67E7C930B9BCA75D2D9F8AB1145AC95E6A3627734E2E6839A4DEBE8D10D5B566ED9C92E42DDB44D3E815883C2AAF7705AF8A2EB276059B0478ACC11E5B21364AD1C9803FEE3EA9D0DB934225B49CD59606C21ECDFC80013254DD58CEBFBBBBA11231F54537A7C67E73E79BDEE4CC92154B4D7DA89FA20D65629B8F7F6BE4B9D20987EB26C78358112EF64EC5785A96C68BCEC43258143EB777726C373BAD1A279FF00A950F288198D73AEEC3F626C68624C8D59765DAC2764AFD756EFE8BC76F36F50A1C36629139D327D25F2C6FCC0839C2733727478C6767CDDA97D198682B10F69DC9451C1120581C67B0156E59BC911EFC2C5F468A4EC61EE55DC532332F699A6B9B85C263927C8F6FDEFDDDCAE5705A913CFC51C1676539411F5A4ABCE3B7BD596C113BC9D8A90618FBAA800F0E473862346EFE873B1475C75767A30607DF2AD9BA0339CFBDA93E23CE0129F79BCA891DF81B70FC94A10B36889A7E4D1F44FBA7A93E05496D69AC2689384F514DCDEB1F51F03B93AD19BEF9AA5EABFEBB6BDCF0A3C13880E71BBC5EA1C567D641CDB8D7A10DA4C0CDAF5933E4AC39B363463B167C3D1CB18DB45B799D026FF004F5C94283A809F14DEE4C859517073B3A75BD1764B1C3917164D9B4554CBABB11738D2C16230EB68DC45C5064264A20BEA8B72BCA190E193688D6A793C0311DDB888BE522F1688EF4677A86C870AD863AC93B02BCA6CAB0FD60791AED943BBD0769D436AAD5C6A4F4043D50C79F20004CA11630EB750ECF49E3649AA29EDC66B7828A75B89566D110C548DAAD3696581CB6BA57CD303A76D973E72937BD5ACD8E05F9D72E70BA4E3424F296F65E1E137B25F678D17D4796A807BA5CB251E2E6993B3675537B2CC37B4B6635B857D0ED344CA939EC86764907874EB22AC4305D15D701A9031CE6F645DFAA934513FB881E4B2470BDCC23F12735B4A00A61C50B710996D4F2F830AD8ECBA9C158846CCAF32BD3633852A1E98FB6C853A80D64D18D15969F6679CBEABD429EB594B7D50E2B286EE3D020DC556F143FBF95EED82F569F8B577745EEDAE2ACC26CFE0AD1CF8BDAD9BBA71BED137EDCFE4A5DFF146D5242F45B938B73659A2222E6CB69514C0A90E14EE41E1A5AC689B8FC116B5E2F9C95AB04B768E432508FF00B9F15947DB266F3CB11DB1A4AAA0C373838FE5E895B889B4FE6145810DCD699DA9913E089ABDF792EBCF2C5FACB267761EE6FC5152D5C8FE7339E68DDB7202236239E04A70D7310DA58D656C2B2ECD7B07152FAA1737DB74D406FB4D0B288835B8A8FF00547E7CA657EA580044894CA9F4EAB1B78AA5A3B9AA90DDE416168DE56268DCD59CF79F192CD00743358EE0BD51BCA9C5739FDD705661B435BB07EE236F9A0C3867693ED90218559457EC19CA70326B10B517FC9174716CF640A28717278ADAF6689CD8EFA304EB55D4B0C3F8AB365AE054A343B0EDA11393458711A756B42789BF9A0E37C4885CA177D795CD3738495830C17EA71D6A117778F2F43BD00F225B4A69856AC1A1242A381E5B5DA682B2881EB4B9D6EF17A3C7E7CBCF3E42D0CDDA9DFE4D35CD05B11A69AE89994C2024E19D66E0AC879DC89F55AA2C777FA2C27EF157D5477F781D0E75B73AFDFC94587CD611C57AAB1792C6E5599DE5601C16CE5D4AF5795AD5DE6AE6F0FDEB626A7B50DA28A5EAEB1B573A2ED89F1DCE9F6580D02FFEC83535D879C6D25B15F552D6AA81C04ECD9FE514B59BD43810FD5A78EB4D60B809742CC66CDBF9286213EDB03C48FA0D917F25E8B8C29EC4C39490D86362CD74396D2E0BADCA267D974952344F08851635CF8805D3D89AE189867BD08908F53133E19D9FE5CA6DA7C139D1C59850F17C973BCD3FE8FEABACE6ABB91CD8AD3F447D0BA546945ACC2EC2577FC53325F5B1C5DFA82B2A8E709D68552244F78AB3947BFF003E42D70982BABCE1E6B01550AF0AF5AD615A95EAF2B5ABBA1785887158DBC56919EF2D2C3F7969A1F15A66715A66715A66715A567158DBC55FC998C25611C55BF5A199F82ADD714F7427B6D4316A5AFC14E54D9B16D6A6358C96B2810B2527FA7F12A94526DE8448B77AA3B650909C48869B3FFC08F315642A03FD47231A3B873CEF258C2A4CACD6F12A4CBFB82EB1C40F68A6597173819EC03D06A5CD3DA6AEAB2963BED19F25A284FF00AAF55C95E3F12EB21C407ECC85848FBBC97A6458D273451CDDA1398D830DB68626B6A9F90E59A3266D7F64EDDCA7759354CC9B2436862892BCFCD73519823C0BACBA865BD010846C8DF3BCB0B826C6E7DC40A8114D13D90E1F390C8912ECD62E61F68BAE6BAECDFF0035A746907446D218D40EDF046B326F9AACD00C04892BBCC2F57DF0A50DCC9764BA61521C03BA2AD141F78AAB18DFBAE5430BDC2AF6FF006D5FFF00B6A60BA5F502C6F1E0169C8F155CA8F155CB1DC5572C7F155CB1FEF2CECADFFDC5A48AEFF9152D7F716BF795DE6AEAEF5564BC55CDF795CDE2AE6AB82C21616ABD8167C49EE749668863799AD23169871527C5041A1CE547B48424644617231A049B180EB18ED7F3441067ADA6F538678213BA6B2468BF9BF8AADDC149839E7EC187C4AAF5B1CD0346AF92393E4EEE723BF362446FFD5A99FB496102E68B9572A71DEC5FC49F717F12EF7167C67BBC14817F8192C04EF715286D0D1E8BAF935AB951ADE0A8B3EAA6D526F364F66D0078152896DBF683E7F35A3847C3F559821B4FB2D0B9C899A3B714CBF3520F6B86D6DCA9CB88AC4554F92D5C17E8A911F25A57A9B9C67DEB1792D7C15CAE0AE0B085802C01601C1565C17E8BF45FA746E5855CAE62C30B82C30B805743F25859E4B033805819E4B0B794104D35EB08FD2A1DA7CA9119F14C8AC896E20682F1D99EC2A9138D7F350B0B0C3129D0FC14A71231ECB6ABF6A88CC9DBD919CF4606410F9A84713A79CEDE566D5FB57EAAFF0048A10AE1C560F35A35A35A372D1B968DDC568CAC0B465560BD49832896CBD57279FF00C0166B22B7EA43929BE0C671DAEAAAC07F05A172D115A32B44F5A188B43116862AD1455A388B471382D1445A17AD0BF8AD03B8AD0BB8AD13F8AD13F8AD09F7968BF12C1F8960FC4B07E25A3FC4B463DE5807BCB47F89603EFAC0EF79607715A27715A27F15A17AD03D685EB44F5A17F05A17AD0BD68DEB02D1F9AD19E2A905CBF867F82FE123705FC246E0B9B63224266CBBF253890E33CEE9293603C0D8B46E5855CAE1D0BBFF00489FFFC4002E10000201020405040203000301000000000001112131415161718191A1B1D110C1E1F020F1304050607080B0FFDA0008010100013F21FF00E116EC989AC1627C8859D1AB1363246FFEDC90CD1A8886688668959AFEA3A4E8A1A41B308D86D831B2BA24358930CDED54771913A7228F70F81DC1543199E5FE93692AB82AC49B409603B84DE801262788939B7625F88FD610B2441088648FD61A037D7123830C6C55C31A70E4C72AE9AFE65258B854EA6C5E1F124F63C4631676A910A910C2685CF1563E8C4960DAE24D602E920B244D812AB4CB41D378D32974097909F018B1E5082A9FF2EAEB625845208CDC0775A3981496E446529B284AE97E447D9A3D84575963F00D6C2FBA08DBDB2426F514C0AB772C5C4705C3F01D309589CDBF0ADE774217B7ADD121AE819ABD835745D3DB5E83A27308044D80FD0D9FA3864FA5D55F1599092447903E44BF67614F702024338B931D672D2A79A228292469659A780AF57A8569E0AAB110AEAA43C0BBB8656869A4A31135865B6CAAA8DC4F71E4E5D45020321BAD970F218BB502A25FE4BC28F1606C5B57DD0444BB724344333536A144114570F38C5EE2C26C4AB9848E42443684B7E014EA87055DB71CC912FDFA8ECED912CE4BC13B0F039C3767D963BC4FB97FB0435C23BA74F30F32DEE589DF4D4606D5556F074289F1B33E29CF52BCD40DE41FE296374447BA2C7AFB0E5C51A931366BC124EC7C18AE494EED453E2747C5EA9325FA062FA248328AD0958A1BCE51124C78439139A0248F7D782982554D1EC2CDA40C92C84C66B9E8DB98ED7064559617B1367C1D40C2B730C6E5124942B7F8E89344AD38905698F3F77E09F4D8D6B84D47162476287A89E24F96660770C47EF2C36463E1E5889CB47076D88A5C10B872EE9BB0922862186EB2D87A3D28228A097A555790BE9C1AC125EE624285629D773547B0C925D84D42AAED055D31BA139C26E76F481A950EA2F0943BA258A3CF95C06DA84897E7972AA8AE64E65CC711B32798434DFA2715323586D94864DEB830A2D0AD6DC1D273D1D5111911D86FC3984BCFBB16C29345FE3CAB738F01608651B4AE695BA9EC299DAE5E72037012D852D385A0E0883F33C1101B386CD424235D4740608E89C862473080E5BF3A38C271C2141DB4A2A8C27110A8A739B2AD732FCBCA427C8A92761351524D0DCBC84F1462EEDE6F5F4A6851D5E2238B376668DD8F2DCE7352DAEA4927138B219BE63C79E66888EDCA20B2B81B513A491B7D390C48B20E1872339F108B89EDFE4446E02EC215E1D29C142AB7B535192B18266D87531849BE59B19B8B0D1E91E2F4165BD5BD071C44268FCBE4C008C116196E2CE05EF32892666EE1A67EE1C6527A66493275A305362DA70B1CCABBA266811B9977DC6242768747D541B9F425C10D5F6EBC02661ED2EF27B107621169C8FA92C431B24BD2491C90B13E4BD4924924927D0D1E411BAF8042E668332BEC334BC989DEEC9D013D1906C1599A152715C5C893187B7F8244C52AB4DC8AE4B82D8251C2A935A2D231B655F480EFBEADCB87D3D49C854E5E728EA5158731B961E483967331213FC69CE5890A5632F53242534A693371C856658CD1DFC084C1DDEAC7043D2AA6D6709C2162EE8FB8B46E014C6D8BD649F491AAFCC1AAEA0D77E78D5E298D366FB0786FE028348B5578B1E1924B23992E5E04D8506FC4367946FB91CAFCE1EE340D23404EF0270DBA2576C7B081F4C0D7A6A5EE26DD7C439A9FDAC6DA076458AC681494A69A21D58854F3AD4B78283A1C7ADF8135F3A430C93A561924419D0B477EFF00DB8212B94074D0C5A7E03C49E7E11B2D96C24A91B02123790E46AA53686B8087A0C6B110C41226F19455B3024E1644571CC1781DA4A64E6C85A06BBF30430DB8A4788804CDCE06E0CAEB1E17307869380F250DF8B82437DF9C33DDE1CAEDBE3E8699190845079E12F059BC8494EB1909BDD506841CDA4453644F2244CB0EDD918EEAAF21A65B8F4E40DBD391810DC5D9994DEE5669D14DB7B60148411654DC211CC627797DC7738C39E2C436988C25F7F97FB31310D686C6DC54564BCD25FA5752C44436E87C0B24EAC6A1B213FB91A4C9480BCFA13D79FECEE34440404FE7A10EED6255DEEFC131313FB19C109873AB13ACD6B398CBAA044C0EC4382A3854E3CA0B9328D197EF79212AA73548E8C7E667876E582EA27A512CDD1B5FD86EF10E4342230FF84713EB213FA52B7F6FC3DCCF6349E2039A26CE64EBB90F62E00D8B850F61A4D559C4484CD6722A394F1AEB4939C2135C67089664104526DB492AB6C6863951AD1F80AF8BA7D8D7836FA05D8D5A1BF80BAAFEDC3625482C974D253242D093EC1569A941086FD2C66D78B68F215E3251D77C9889B1205093042C8716C48473C86D465272AFF59B4AF88F20FD2C6C2C2576C91578A7822D10A0FBB3E2C37DB39C345821DC0F879B8A21512378821B21A9F6617851978D6273E23D7B90391BA5B64F9F51C4BB3D822B6F8925E6713DCB4D09927338A6B118597962891FEEE7CFE027F860817A0851128C1ADD0EA26D1C478EA3D55DB1265ACAAE57249A7DB5C895084D94E4ED52FE36FD873CE9D3910E4C42409B8C331E3D59599CAFC597994C6D0BA2EB811ADF7F9B097AA7C4712D743970638E2F89380D8DB6D43A0C613B1B64E0C4B8E4281DE2EB6CD8F2CD286F9668616BA425BAB939970D6C8651144FB99989244C4884DB378244D6AC94A7FD56E07536AEF1246186E22F573E218184C58E5F3605624C44CC3CC5D8037229C168555A108AD22B49BA8984D86AC484C4DEE1A79DC7A0073D66364709230CB518C90B91E38B83D1126629A12F61624DBB55DF1FE4A90F21649425D118E9CBC05CFA3EF03486F893DA4A9DA59AE99F1F625D33ACDF625D26F8C4F2524ECC8E6B8F04925A2790F0C5BB5EEF47AEA0492AB6443485793211EB788A1CF01204C86A311E96594E37B078EABD98E27EC2BBD21EF1398B8D5562CCFAC0710492AD8515171059989494118A1F52F523CF3E633D065187C015F88860A6A981F4E23DDC53E0975CBB0D48131324976551350858D449A273638CC2A25FD483D5921B01F291D52EF9772066B278E85475ABD9B44D90FB8B62FD21114E5A963D81F3E02701D3EE232A491C369D88650C416125C5B1F68D0CABB5A761525C98527AE2398EEE50DA630958622671443677EB3CFF001823D45B84B9317ED0926128B1E42E41B99B82A56D423C64449D884BAAB35C6BB1D06DC39E3E4ED303B6E80E58B18D5DE9F64D4910D5249B6DE0445114C9F225EAC8921D1CF657B8BA397DB56F27D0BC8750B943C4FA0642FF00295372C56D6466707F751F75432E99CD8A886522FA7D79DD5BE825788E4987BA396D9A4772478C5D6F8A1DD062A7CC8B8526825B6D26CC42F478DB59A56CBC9164306462DA8B2FEA7AA372C93164CA0CC8879A4B58E1320A776DDBC2458D09279133034F32F4B3E0662455CF6F781A1917A6E687F80B72121B731111B414FE8E6AA6371342C6B59B1162A46ACD58778B429A249721794C450284654531496EF2EEC7BD842BFA64A62BA3439A7DB88F9820490812C82D08864BF8AF9222689288BB644C4931AA7E3D6AAB23678CDDF11E8B8131C24B112889DF81F323D58CCBDEDD792DDED292350BF7D697618EC87B642CC522B16456D2534F2249F0DA471208F26C9D66F80BB02EC8A305921E8B3542F6A494FF0064E82B325B693142CC0733AB5D371E845D617C4A852255AA28A9C08E542DA63D8FFA7290AA46033475309BEA4E4299CD44B43D1AA329989E496B85DF30BD1908C7C020B42199F1791EE34B4DD90E96B14CA126B5A37D996CEB9741DEA26341A1124B476167D549250DAB3DC98608223716F1E22725EEAAB742746AD406DA18097362609414411BC3F3A13131A27831A56810C7A7CE17A548C8C591EB4D7AC9249248DEE65AB30CD3604565FB88FD2889E231EDDCEA485BD25D96E62242DE0FA6227E0FD0F337DC91371F6E51A2FD82D617CD8CB278A4B259698D405995D702463607A6AFE4761134A15BF4202F621C43DC7C7DF25CD0E9AA1C233CC34655717DD956877C4F46AFBACE457F112494E6ABE5FD4B1126D758AB9D1952E9698AD462F712C0C54A59A904892859123CCD2FA21E2713E8849D5AAE4C7AD61A62A70FB3503B2448655AD7C947695AEF36CA0D404E6B054EA55D46DF445A569AC05486D5FDC3EE5EC26FA97606A098CD166F9665786D633342569D04A4AE2108E8F412E2C88D28949BC44CC4DC4B77035FC827B37025B9DDFB8496DE2FB2311C49DD937CB687423887414139887665F009ABFAB90FD5CCC7F708EA4B2617A13249247E946DAA4AAF24C6017D16AD2F08462189EB8A8B21E558247E9DCA54B0DDF6F80CFB3CD1A66E177330929A5CB02987119AD6EE2C48E849AC1EA68604B921437B8D8ABEEA21142E5445B614D43D4F50B754A3EF11CBBBCB998BD2E861F6635B74139499A1B8D5AE7FF48DA57A0EF94A7932F8114271CEA4287442684BA8B307B56CAFB7B0BBD42BE0A7264FB18E9A744318C4D4342020E62EBA5C72BD457269A99733570F725522AA1990B3051C8DB335A4D1AC1E8C408499A718B8A6D31C70C447F1A992E6FA0C9D8BD46C05FDC90D224919015B87E43331CD2F7BA763169C3E25911BFC46D3E0D8B4782925F82E09FBA58A584D5611C59460C83E43D0E4367E02ABF592DE5BB095F3054B38DA8531AEE4A56F4208AFCB86E1C79AA7838B57B0AE8E55C963D2BC05D4D4E1A5A32264953CCD1D1561512F624C4AE306E0DA9829EC333753CC2DDA6E11445A5990A4DD26AC71DDB8E62BAABA7732A9D04FD42C3290890290D4A6759EA4F020A8ADDFDFF45CA6C3296C96EE7718B878A4747BA81B56754521B7555B22F2514654220744069AD61DDB78854F803E65C84FA8E5DB43DAADA0A82C90EADB27F6AA1E63A0554E96FAEA40A9B10CA16A099204F40F437CA76FD276766213A951DB41C8B19EF393877842642F9FECA2EAC73AC9C08959BAD5A15E499564F4B2450B83DC4E8B5A89C09434C9EBE8B709E4BA0E4DDD8E57471126FCA851CC4764E2C96094970F224B94E6DB89410C8701A2C50FE6069BF247E9E345F9612F10CEF42E45936C2585CB89F67EC834E7D8C5CC24663354220A295BB351FB156317A158DD9311942C79B978E45110F76767F235945B464FDC915BAB8B5F6D2253563D457436278DBC437C085977BD854D807D713890915B1AB6EFEE05F145CBFA93D9A10B3617A44A71D1332E33FBC12A15E76AC1DCC7F8608208FC11E236643456926503E6303BB7DCAF5B527D84BD8F05ED0A89DD32F622AEC96A9E62EE7E99AD48ECFA0B19C109264D32A244056C25D58A2211A64295551E31960D550AA23DC16AD4CADD1C108D1257419159C190D906D8CB2A36874AC823EE9A5C8933C298D6EEFC115BB5384149E4AD5256C50CB5FB8FC032EE6366E673B9B1EAC4C90CB5844A91DC7D3DD7EE36C8C52BFB2602AFB66C28A9B537B1DF7B3DA0F38ADF82FE4414B82D3E6499F3257B9ECA12E1C37C197FEF424F791B0D5E2AF25439416573804FDE66617AC3A957171250906C423CD7C91F887E907D042C2E99FA944ABC1E4415DCB8278E6C8486A59C8E6D9A6F145783B20D928503B9155D5E25DAF507D06DF552492717C59EE51AACB66D37EFB8ED2E2F746EAE866420954334D4BDE2FC6CA39BF02C892E99F53ED0455F70D933C73C8D739AA4BE92C64BC5689B79194B1CD0FDCFD3BC92F89E4789F09A45207923B11DF92266E5E31F4493FC3047A31BF4DAE6631A21B2C79862C5F33787CA247B400A4D3C18A2D922B263D432C4BD953707D2C4C68C08942496F5C49D069D6777D889715A1D6DE0866CB31D5C6AC59FBDC6551524A53DC869E60F3C43340F75F26121B2F9311B5CBE444509947913536E50D8EAA836CD4C659B84B373DE0A2F88557B4518DC10D3E3462AE5A24ED26C2B81364362E422C1721F581ACF909F3628314CAB162866E067F41A55BA2657F75F035DD3781F84F00DF1BC06584DB780F4ABF434245446C84B7D0825648B26B14F11498EC4FA314A4BA10DBFAC445CA0A66A4665F030D9C4BB0CC3D2D5B1EB5A0DB371F4DBA165A33F5924AC352527C43D913E8B28E76D115EE336E66686E2204D8FE0627FCB04A5B18C817B34657C10FDB19C0FE091F0D9ED0879150D59F997BC86852556A90D0B34A1C98C63B36DF61D323D0ED435B9701F70F93E30240964346470399B647C5C78347E20231B7F78A796CD07E9840C9EF06E0691C025F827E8A4983C064372123ABF21BADD0D3FDEE50B3949D7B13F441FEA447C22B7B4127C133D9C239BDB320C5E436868E35DB96883C411B7284564F7990ED9F98F07A0456E504F4C8D1A262ACDF3132AF09060D3CE2A2AB7C420E4ABA50F96288A7B2243787BC0FDDD7131A9CCC78395C56211B8B6012B212FE120820820924924927F06882081123E83F58B48D0340D0469234881120444844891224489122448E4448912247222448912244810224489122472224322040891204322043217A5123EB6D17E388FC60820823D249249FE3A952A54A952A54AFE15FF005649249F49FEC411FC5047FC9A7FF4B3FF005D7FD9524FAC924FF5E49249FF00427F0924927D64924924924924927D67F39F4924924924927D64924927FA33FD9927F9A7F09249249249249FCE49249249249F4927D24924927FCD5FE87FFFDA000C03010002000300000010F3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CD38F3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CE3EC0821053CC30F3CF3CF3C72E183DF2C34F3CF3CF3CF3CF3CF3CF3CF3CF3CF3CC4C510A49CACDEE72CB1055E7BA67A8998C339CF3CF3CF3CF3CF3CF3CF3CF3CF302C91AFEB2C5293D73A2142287BE85DDB40BC13CF3CF3CF3CF3CF3CF3CF3CF3CE38A824F942D64713682A8EE2A2ABAA8661C91053C50857CF3CF3CF3CF3CF3CF34F96283B886AEBAA579FF00DF7D659EEF653BD5B46BDC9CF034A3CF3CF3CF3CF3CF3CA308E23AABED2D506974DF5D85FA70E31840541BFDC3B315FEE0BFF3CF3CF3CF3CF30CBD60588AC0181786DD62EA2D865ACA7CB3C85947BC3B49D3CA79DF3CF3CF3CF3CB2803485886ED6864D379075CAEA98230E2F0AA391CE73B6B6B68609DF3CF3CE30C342206E3C4DB8FB04879DE906649669A5B20AA4AA14E5D7889A27314408F3CF3C118E3C8072BC1CF9DDD7985A76396D24D64D015E48D296D710245D7D3BA1E8ED08D3C33CD3C2657E6C72FA667F95A02EF961AA8824AF786E8248B4829BFB519B6A9451050E3B0F285169A689AFBA0BE29AE1820C7BABBACB208A092BAE1BAC86EA6890A2C23873C618934E1CA34F2C434F1CF3893852033E3A020042050C33CF3873CD3CF3C73C73CF3CC24814A2420C40863CD3460CA10F14F20130630008034F3CB04F14F38E30538F3CC58A2C73CF10128D3881C534414A3CF20F24414F04334F3CF2C214F14F2831083C730C30D18E3CC0473CD1CA1CD0460460C30CE38104E2073CE0042C528700C20914D04E18838810B0C314F2C63001C534024D04C30C1891C01CF3C81CC14924A3C01C41841CC2CD0872073C820F00F3CF1C71C720800F0000003C83CF3C01C71C83C71C02083CF3CF3C03CFFC400221100030001040301000300000000000000000111102021303140415171618090FFDA0008010301013F10FF00199A2EC4A5473E86F642A044FC23E09BE989A27F4F4EC248843A10997E2B45D9EA44EC149B14649E32DBDF0412A15744777C237F3C975F45B7BB2704A39AD8DDDBC096257A12BD134C21084C4263A441F1D431A3F0A134CF02132B1B622137EC2BBCF214A7E72C26B6D2DB374BE748824D53786ADA592F0DE1ADCD896E3587A1CF5CFDF438569F110ED5896D46A2BE0A47C5882706EF66F589A2978DB484ED93C537426DF7ABB46256AE3841974B7E90FC448DBCF00004AD71129FC049C34A5C2E8A5132B25158514570006EE9A5FEA5FF00FFC4002411000300010402020203000000000000000001111020213031404151615070718190FFDA0008010201013F10FF0019A0A78DC12D13A8834FD13D10BE013B18375A9B294EC52A2AF2661B1740912C5E1084286DE8FBB122F1A65328DA427BE365159BE9B8A5294A5D169CE4F0E971345E7A8B883FA2A54C49CC8A420BEF9294A5D2869EC84C77A12E578A2624D3B6F6C3149B3BF0A91A1AFE7861381B56F629B1725CD6E9B3BC27DE1EBC43BD9F637BC2B6F0B9FB91EC49AC51AA74825F8A795CBDA313D133B2CE3B19B55A98A4CAAAD0537A70D2E28A933D6129DE97EC2EF2A7DA3E0BFAD605D20D8DFDA10E963B23373AEC6DB28D550A6ED09122E2992133084C91920820823580A09A213C7BF96BFBFBFFC4002B1001000201020504030003010101000000010011213141105161718191A1B1C120D1F03040E1F150B0FFDA0008010100013F10FF00F08BDC3252A9FB8A0F9102D9E56E6957560CD22C76C26D7B73FF00ED50D50F311D7D627FEDCFFD69FF00AD3FF70823A23E7FD3A782B74D223FA273E39E109A4F7C4D7C3AC6D6A566BE69774F3885539555ECCD43F4FB1504C0BCFF00DCC8D3EAB229571DC7A4C0546AB09E3FFA57221CD6A596392466CE79D5ED3D2E2DF98BFAB1ED3565D48CD03CD681685E103D03C4A723D2740F49FF008D16D5BC229B5DB11D83B4A3B4FBD31DE90B0ACA0FC24D9AF920F770823A23DBFCA658F56B91A0E76632768A957F273488146BD1F94810A07903ECB0791A4886A365416BA588C7C86DCCCCBBE8D185808DC8D4355E806594B0A6CD3912A0F5489FCF014934B5DB304327D50BA0746A37477CB97A33D3FF0096CAAC53DCE85F38BE25840DB1D2F9B2D5230563AB8F622C5FD61F0811B68801DDD130C8E76FD91598DFC5947320BCDF93BCC9EC6556CE78704A4216C7DC27B726FB8E00D795FA83146F9DF22602CBA7F2CA8C9FF40C016ACE670B383C73896EF4E2B56CF3ACCA993D0B644910686EF4BF887846A563ECA0882294A3C3394DD4207462174F324763CEFF00D9B1F5BFEC7FF07F71AF57E3F72FD3D63F71AC72BCF7BAF91DE2524889F2934C3539B6B04652F770CD1E45517AA412B5589B66D93FE6F2A7275045B50DC88357AED2F764004D7C6A30E4D61BC43C3AE130C32D50DE2252C8432442B52D5759B2338AA02DA28A674896C4C3EB063E65E75A1749D56AAED0A8839C6EFB400741FF00C9112769C78E7D8842858B1DFF00F89103BAD217F80DC751472DCF9D608D2574E81CA5D185A28D547090D39EABA5EEEBC820EE63A61AB96F5EEC5DBC582F42B361BB571474D5A8CF335AE21AF2AD9A6EECFB0EF2FABA6A7F3168E6C53A75C6B28507B866BA6008F450F88846FEDF5C6A25D40F662A2975DF44A6C66977F0840E9C297EB00DBD40006AB8AF880E33BE2D35963C25DDC506E8692F7393C93F06561A69535DB9442318C03D3BA7911315CC07B87A0EC4389F81BB63DD33EB6E4E687C301103A1732614EC7EA20D3E1AFD4495E8FF00489309E87E9000D80BA57426FA31B67287A50CE6B39C78972F8EBFA86D1F1FA41D747F3A431D672058685B52E4DD177184D1F112B008506000A8395C0D690CBF5716BEE04778C0546B1D8B70D61B77084D14146C1F2C3EE4C51679369722E393F701000D03FF008E487C2DAF0357C4BF7768C41A4AADF1B359916831C73BAC3A01CE54EAC8D55D0C7EB182CEAEFDA3644735C468BB41443FBF13C083FF0082506D6AC92CCA9E2E0B7E5A16736120AB801CFB448ABB70C28A81A5056AAA970146A799F474997D386C5EFDE61C53A933DB8149A31394526D1321AC5AAB4E05E84A4850E3527D0E59826000A03408AAF4697952A5406AB0A221751AEE741E932943A83E8E616B01794787187A6B2B80EA41C99B181A778388756800B3A89BE752EFD5A8DBD49F4896E1FA37D358FB4AA70C7D76CF7972F2501BE7C4035406441C4EB4E82F8818A5B73F66B194F520FBB1917FB47A153D5D4AFD62AEAB2A70FA56D4765EE24D6C3B32256AEFD9D3CE11FF00C7562059E9039F5A848E4D0460160ED881D9CE940BAB97179590D0BB650B73D03CDCD9C1418626204C100E5C77985240E4A0F437F300004A28AAED326EEA0E752D8A68D9E966AB78068EB8E77688344140138CB8F85602A2C2CAB0D0B5EF931001A85515D600A08872077DE6AD20AA61A9E84B72C42A6973602F680CE1DE58918836F4198A5CEE10736CAF60778CF8CAA5AB54DD3ABC02A19A770A60BEC9EB0BEF138D8EA75EB1F11A36548A4BA7B59EABB9A9146969AEDCA2BA4B7322BB7B63992BABF94D99775FB8B6ADDF3022007537405E84496342EC4E87A23C923FF9C47B245CCA42D4BA337B6EE8594EBA6BDE0762F55FFF001E9E4C54E1D74E5ACBEDD499B4AC6A5D9941B506F0E60DCCB6D6ADDDD3A8C05C80AD1B0360964B87687FCDA9C835474EAF044E3BD0F92F3EA7B419C6F1A0C1857395E5E7049A300E0F040AE9F571F98B305A513D8206E035D88EAF4F151F4D80486C3775871511174068B00E445AB3A6A98C36ED2A7251A0768EA19E407E26BEFE2F04301DFE66A1975FFAAEDED04C64737E4894A26818DFE614203B386F623E30FF00C9A3DA5793CBE1497D594EB2B3E7F0021F7C0FE0F5E72911106A9EB08C87713DC4BF64D1578CBEBF040B5F02BF52B757B1FA4E5FF7749B61D83E48816AEC17E90A6B1D484DFABB40E0B778AB74398C302E04E443DE1B0CB9E134603D608E88F6FF007A98A88B1839AA8E395EA7F73088BB03F72B80D5AE7DE1BD12D3DCADCD40154E8FD08D6BD1460AEDB20CEAF9045A15D49BE5430330AD51A503E608113812076A3103AC6DF5A1495429FDE864EC31FCAAA0F5F278A8B43FA8A750007A0B2BDD96B64F25E2911A48671658C367B4A0BF5D1AD16787C432599BFCD19EB025FEA92802E467C4B0E7EB2CDC94E0B4DA34D71DE6883B827BCE09F1A6D36EBDD7D4F7E5613E1A7DCA4A9359545F3D83D629C6EE9FA9FB449B6F17FDC7E0C13288F603EA6A936A03CA6F8F7719D55DD586C64C1F48A9A7D2685E925547AA141DD7047BAF2157D3DE280137CFF585ABAB0DF70809B76A3CDA351996881DD6498965C9B3C91CDE7725CA2FA50C80747439F21D58DD9DCD5E9B28D1E228195DCBE854702204FDF7E9108ECF7D46D2CE3C6C4D07FB673621AAE7946DD91AF9EAC0C8F67D5AD3CD41CDBABCF8B7CC7EFAC1BEC83AB1C141FB83461A42A77C1F663917689DD7FC12F943B852789A01F59A024077835C72655D8032AEC1961B14C10B99F36D810597E885E0760862D5CB43CFA1EFDA00A284330BB89462721FAC608479507B04FA56C0851E504BBEDFF99B1FDDB1FF00287EE334ED07DCD683B2FA9FA64F89EED4A0FB9931DD2E21B27491D8089C8411AB15CD3079344D70FAE132B9B573A9D088A1ED33622345E90E6A0BB435D1AECCF6874FB8B05D6A10F35FADE202AD7355EC466CFD5F2C0980F633701D8085DCB7ED25997D0C758FD0054B1A5181DC8349C62FD1DB27A841A94B0A6F148282A3724F4869DA0BD3D50FD246C95ED7079A832B62800731D1F7EB1535D2D13BA201CB352840DCA53CA1E9466CC38A26978557481284C821ED728E0540E95DAC97B656FD08BDE66F64EEB92701D0651721B11DA3FEC506A3B413150B3BC1031B2AF57E46ABD3D66B3B84A2E9FC7BA670560EDE9795D863666C0F7CDDBC61DE0DD2437D4B7575A3E492A7339821A506716B06BAC375AE444D2FA98E57D6A314DDA157A4D4E8F2447D505A87647783D231B28841AC5A2D4BF015E587095BB96329C33CECA3CE1E66B1EA42D4ACB51D883038B58CB69762E2A5D96B29609CE2B682DDC4ADB2D5DD33E86BBF44AB901EF2C3BCB7C23EF1C1E5017CDEF300A1CC1145B3B3E18489FBA81ED5298436B1E0FBAA66383BF2DD8D1C94B688D828CB05B8834EDB188E118432250A00D55D8EB08A5576A9AB7FFAFB414F41B1D2763AEB1C956E0BB96D3BC06E887A81175E664981A80FA93D267585A473AAC3D4E71EA6A0777205FBA0A0EBEA07A98F58EC05A2EDD56D0A2541925C8E7C878E51175753EE03B3A301030C2728A6B1B4DE247E85A88484D055A6245AE18703D9FF005AB6D4E01BBDA50200777795477046C186ACBD3ACFB16C4C9AC2D0B83F9756382E1BA8E987A6C1BC1C0F837F20E3D23AB17F2CC3EC437758DB9A0EAAA4C696999DC981651B28CDEF329E0C9E64729E1802B7D51004444154E2994570460A06A15CD1906B810084C35761A952B56FFECD9AC1200410B03A23B92B95BEB65DB53EE25332CA70FF00608FB28A1A75D230D98CAACBE0C654A95C270A18302084A4C448D2FBBD0057A11934994CBD55D56DEEC3A5760665D3F565A7D8E5E91ED3B9EBC1CBE526AE66A63A66E396580183D7E1F2CE040DC0D8EFA18F686BB1745BABD832DED2952852AE886C340F3BF0A98748AB3536329D0F67C0E72A4C1D492FCB3875576253BF1D69B6753580DF5963000A008E914DD732B20765AADEDDC7A898A56C3AB56BE61C1898B68B2FB494AEF712748CF124A3974D801A15A86AC6F9851696B19A1B5F4653825DB29C27481CCB8D0E09D4A73D6E4ECD32AD309BD6E3C8362731944038606134CD2D8130155F04201250586D449DB4FF0050BBA0F2454A6F1C8E446FBCC3ACC9561D56A3D7D941A3EE231D020E6D5596C1CC5C1DB2C7276B058E54753D57A4CCF2A940D1619C381E7945ED3670658CB36C4BD27DCC099622155E548F4769521001D3A1CC39696F6410DA57A0987185054BB1C35754BA82AEC74737B7A32CB2A8643DCD474BE51D832886CC77DA255B680A28B476B2AFDEE3E107AF2181EA3165CBFC2A35C3C43B4E8182E8A2B452AD01BB82315B558AF40CB0B15BD8FAB8CE9222CB50987537C191E839CBE3393ED2FB83E176FAF22FB88EE622D07D80BD5972040DAF50C56B4BE70474A278721B678A61778FECC585C5F91A01CE557541C9CE3E5DDE84D12B8560398EFE0FAE7B0C3FAFF39539EE2FDE2001E3A079BE618F0C6AEA9179A89BD360E2C5D88484CB1AF41E5B1D94DE5A7CE8C3511E59B1DD1C9802B42F07C4BD11D39C3FEA1AC06A8DF603750940230B28362E6DDF31720843D69FA9CAF7DBE4F88556826E564E8AF1A3E6138D10185D22F21CA14CD506C18BB81EF196DC993B7E003580D40D6D62C687BC24A955B1456158B40579B1D71FE9977C7AA1CB2FAC70892F0C68BAC41BE5A3900BE2B11666934A09A2ACD304132000C0934050034CCB1AB286A669E76E9CFB47D163A464321BE6618D98882A37B408C4B4D16FA9AD75D264A6860B324A13B335AD20F41943924474C7A9E8F26B6877E2EF9BF9224B1D85E4383D12CF32C8C22F62CF99935948D2D5D198A3B4C091F45E0FA11E0420985F942FBC2EEFE8C0753D107AFB101BDEE268DE7581D0BC2C0743D8CB989B0D7443891596352CC1AE6529BF4A983463BEDC6AB28A3A0B68A7A73FC9005006957C086D2DD1656BF07EE782FC22AADB0CAEABBAF56601D2FE204A0002D5BD0378014E459B36EF3576D39CA23A462A88BE508FE79A1C1C70E9896F17A23B7EA17417BC035DE33485D20143D8A7BCBFEB3AE05B6DBAE20182A4B50E83B53BF50E1C100EAF156914190992DCCA4AF240E6EF197158CC4006AA26990D8D85D55E68978267CD75F12CC8A03488E1984C060D9D879257E084A696EC13CBE91D869F5BA393C97EB1DCB49E6027CF13420BBB028C2290E1BF384E900C438010D1A283CFFD3101BA5CBB9A673A4692B73FAA3D9D541B6B4BAD65BDEDD48A68B729759B8D88585CC0DEAD5A6283EBA46A151C602C1BD1699CB9C78055A50EB9B37D91AF46502736C0F35100C36D2E06F7A6738331EF970586901CA85F48049945987345A35B3AEA72BA9DC146EE998380DEE60EB33395956D7A690704A86BB418F78A52715574460CEC6310B78D894F6F485006990A60BA71476813931869BF68AA099501ABC7BB129371E902AB726A1AB1F64BF01FAC39B58AD566E2FD675AF329FD501D3D095986F2B137C37969696E1B15206A93A0FB762D8EBACC34E874141D0E053BCBF36348D716036AC5F4A3D62BE6D253C2DEAA7400D58F19E5E43E1CF5DB6850E0F038ACE6BCE464F7514C33D66B3D53D239C32DFDBA88153375B8013AB5E086884F8551AFADB00245052B3929190D3159D669305AAEE52A5AD575D08FB5508ABCDEA4C6B68597AC652821A61611A25007996362BEA80B8EA9872A03CC6BD5620B8E10D98047AD3D100A7D44272E7BC3D826820990157D91E3A04B3A0D15AADE7039F21A39A366EC74FF004CD43546D772EB50A91D925D6B62E29C2B554334637864392A7515C1D17BED958CF4BF56F2737E738BCC3484F579BD6234D84DFF002CCEB181AA23D9469A6B60D5DF2B1E28B0C11EF045DA5D20E11414793641F31CAD2017DA9AB141D48873090285E6B1A003057363BDAE5D23AB85485062B2D41B595CC5C8D850673E8981D56E634D4BB035D605E6DE0501A6876630CA2AF45185C41F20026D2937340FE3A4C53AF2A0F57BEF0F3B47692A042B75F9E0BF253E60C04E061FF222374392FACA35886B52E5F587114E102DD09FA0D8EAD1D62BBC852B46A0EEBBEED0A356D65845E618605370B4C49ED4DF5C3DA1B3FE4D734C11F20B34AB7503A7B9E902AA54489C0F5A86E7544BE4521A480B8E7A11AA1CD82F5C9F7310B560176EBBE91F6A3050670155E496311494E6E0BE918A16F4AD4026B9BC1E45648F80C598A324D57037604E4847A0258F0EAF297F0BA1F38409B0C5692DE2280FAC6EB07336AB3021CC8342E822704A960FE7C1D7BC5504751A5CA061B7A699CEF4B3FE9ED5B424C3D034006F581D69E91280E442D556E2E051865E10F721AFA283622001181A11198AC9B2ECCAD2611F2B7D92C243567EEBF490A6CDA29D4D21CB0DA28A05B1A942F306BC43DDB3D58A0288D1401306465051D2D4F75A2D6C602F7B860798F2831B1B1B59B15988FB21F202C355A7114A5C8EB598B783E16CF892A8556EB85FAE089E53DEB4C2E19010142A11A1E74F32DA4AD04946A43098720B4D77AC5F6848D0B1012B5ACE79636E7F5872A768EBBDD10C5BE20BE74BE530EDDC80BDAE1EEAC9EA813F844758D1F5A53D3EC8691BA8BD7E888701B80DE09F31FBC6A1ABEEEFE60BA90D7A25C02BC8CBE840B1EEF60F783CC6EF13ED66587564281569ACBD49A665D43C1BF5D66350EF32B196145AC4AAD846A305A2094E48BE90354E0C76B8327735DE6EA6068724D7A692BE07A0149C0150BDB644A29DA5EB434E8CDAF3ADC06E315868D53AAB735F689F6AD401613490CE68CE6F46F34BDA6F17A00BBBC51989A8858F8432F59734DAA04EB5BFACB39E28D6C9028ABC343874630D4CAB745B53DD8671573C8B7DE0ABA0AFB8C04A834B8B75A29FEE908783A0145C3F4CC50080019315DC3FD206D83AB53E31B7140AF4196EC0EAD8E65BC90135EB8BD2618DE6EBF46561F84BA0D2FA4361129370BFBC0787AC80A5DEEF10BA54F003F9D0C5E88D1EF2F149CD39CA363156C8A36549DC18D6E25D6C4805841D5D3461778F286E005B956E69A6CD79C170CF61290723C866ACA0C253C7AAE80F66FBD4A4D5456C0BF2DBBA436CF0197BA28965B9A87238D373581348721C87EC4A8B2A1942F0656A80F53B7C8EB31AD545832D7969E7CC7E634147A109075A47E23587F239913D43B4F958632FB27D821ED4FD9547ACCF3A2FAA42340E98F8813ACCE7A7B7CC7414EE7EA3A607B27EA6AC9DABF2C79C7813EE01F712B0BAD29EB705694749F088DC9EE5C118076808640E2CE0F7443084259A7355ED07615D475C6AF4F8824DADD500514DD0037CB9CB10332966A2F6AA480E3D101902AE152ED8336CE9594BB7894FC1259A2B8FB818AF1415A97D5DB1296A8BEB1C8B4E5CA05404AD2E83561302450CCB55DD201E90C08A8DADB665A2DB5B09B3C143DA069461D02BEA05BCEAC52CB0ABFC96B1EA1FCC2767AA36608A614B16728158E58FF0D4A952A54A953AC80003A54256EB9AB62358B940F4994D6D33B76C4434A86742C3CB502812B20EF9CBA542AD2734BED956368A753F04CF90CD6005BCAD3C5C61D82D21A29C9B0C0E4366B52FBE43CD7B4B1A42E980CD5AA7C05C2B896FDB2756CA897B02DA51E35A28E16AA0A00E8134B1B0402ADAAF1EB0E3A306149AAA3072CEA11792F2C17596EE8253B88EF2EE1DAECDF50D179AE64AC309BD748CAF5ADEA434341108CF7CA16DE0D09B12BCEB444F654F69D385E881A7663984250441446C4E6732332C0D848D84ED840726F1DD3003F07EE24043D03EE577BD7E255E7E89F712E9DF421EEA5B2BF8DB8ED8EC23AE7D9A8DFDC941D767B90070CF10756205687921752EE6689FBFEE89EA3DFF007CD0177FDB02C87F1CE25A2F18847B8FEFDA6317E31AE5C84BEA8AFB86607A935A77D710CDE8421EAE204AB6D2919FB80657F18DA59D0B295CD3A3E203D05C4A53415EAE74DE2C0221B1E4E85DAFBBC905A0158586E53A53108500685966B596F7D656E4D0D8DC4827C18BB85F8D354CAF3B788AD5668D8DD57439AE0865A1477AC0DE45EACB02C11D4068AD1BD8AFA9BDC442CAE2D86C394B280C03AC181540ACADA776AFA44DB54D95FC4B9BAEA9F352B387A81EC131AAB027DEBF88B1CA0A31DBFA21E29E43836D5AC73CC5B4F37FC818A952A0611D17D16444E892F365DBEA55ED14687DEDBE091B5AE6A657B604B3184BE967D6626F9C7DF4523086C89F536C7D4901652B562AE9D91F08BF1E7783AA58D3D749505A3FB495CDE336B3B93043464CD239252FA22CD0804EE57485D25082A50EAC1FD59B7C53AC157A9CB108F7A9386CF3833D20F7FA2A290513CED0CBD88C05680D3500EF0BDA3410365AD90081200E2BAB70D0BAE85746AD8EEC6A8DA672DCDBDF5E770DC9B02D946ED1C88A13869806351325A7444D9B409B3F5AC4025D15BD0AF624096DD5B7C5A284F5B1FE808CB546E97D7496FDEAF985331E5F7628DF9C3F71D3008BDE30A5EDD8F47AEA3F1030BD91FD42A69BD3F44C830E9FB315B7E85032A86A367B30840DD222481F5856DAD79A9F72C52FD9FD895B74702EFDD848E98248751E5FD93957F7A5267C4A89FA7FB429CF9FF6C5C711C8FAC6317D5F8329F563037DED655C86F5BC8653CA72C3EAC1AA0DD17C44AE8194853BF2586E20A55B25D53E372349519C3ADD9E4C437740280B798EA9435629000A9AAC9CB069D9A3EA89A365ABE4B4DDE03EC1534B71639349690641762F8013BCA3D86FD1458180D1DBAD771D9BE85CC3B3243C172957DD2DB7041840002C9F31C8D7B4AC3EAAAA6528576BF48E9E770FAB36E83DA5359E285F0C6A7DB43236F16F7C9373BCFE212387A58395D572C042412E5FE352B818A82110C351450A46753D62986BB334CF5989947D74AC70DDC8B338F3445AEE1DCF88E843AC141EB42E8750628858C9749FB8EF0BAD9E62817AA1EB09DB29543D9C9F0A02EE7A7AD2F99704145AF15581AA25F96DC8F1BD61098B642DD92275606894CB9C36EC935C35ED2874030884F48D295BDEDF2C1975F65F2C2D95D2DC06BCE1C9781BD623AF0A0C0748719F5A097DEA5E3BA16F4C622724EC60A641CA91017AE116D943AFFC45ACE72ABF50751DCDFD715428E75FD46DC8F7FEA1A7BB2FF54C0159D681F100380ADFFE1115E2E777D474EBAE87D42EAF4C1F507BA6B903EA16A7607EA0F01BB87EA53D21793F5305A89B87F51B406787E2211FC3E71A303BDAC106E0358ABB95337FD3B0401177BD90887F22DA2000BE4B4D6B072C4D292C6583FB78B3AADD7DCE4FE6056986D61458A47743A00013710A8D011AC2977B077850E03062396ABDE3A186C675C9156BA56B2BFD45BB77C3D704B4D0639B151EF4746659062C5CF407F4C48A7D120371D877757DA2D938D0B67942D5A7BCB47D98196A40A6119DF12EACCB380305E07F813945440E612D2778B2B7BCF6EF8358245410BF8CA4DD1D0BFB87BD5E947CC3AACE5812CD8AB91F7025A1EADF712BF2C9F70C65A6979F981456681FDC26138BFC9083B9FDD51A55D55EE613701720F2A63C16C349F58AAC4E5415EF1F7E35FB8EAD63A4688DE87EE305E2E47ED042FB10C5FB898989E7998605E68FC996C353512BCDCB75FE9DE6CBDB3EE615BFF1CE1E801D47EE5F83FB9CE3D6AF6FD9060B6E9FF68B54375FDD2ABF57FB26944FEF78186A7F2D65FAB273100648F5FD9110C1DCFB8EC09D3F745A8E2EE7EE835517C967EB0BF736CFF9E71274ADCFD913669E9F722C2FBA7F68673BB5FCCB8CF3555FB8A7CBE636CF7BE61743F03EE51BC60FDC41A37A5FB8AD8FB93EE0751FEB9CDCAEDFBA5D039ACBDF398DE820541073A698EA17A8FB88D07C7F72ED7EA56F3A2D77636CA0B17BE5F230DA4D801353FE9739EF443F7364FBC0349E65560440B52B69728EA45E020348576E122FF9569D2784EC2764A7297D234C420C4F289E4479445274C8B6A139223CA2740806C816CF49D17A45FF00547FE74E50F49D1274E3CB2767D20363D21D2F485509F4887289D2274A74E74C9D34E990E593A24E993A69D0274274274A7409D1274C9D39D127409D027409D027413A0709D28F2A29B479534F13A69D23841D881BC87A4E81E91E5101C881AD081E481C894E903D257A4070D4C4A254A4A4A7394E9C152D0E89D92DCA5CB96CB62B15E51BAD237CA679447944E48909C92B922724AE49515C9C14CCF2998C170810A8542A5931FE33F0A951E3898E3897D25C782C5E92DE52DE53333099E50B85C2F94CF283C33D25BCBF0045F05CB65B2EE2D6F3CC1EBC2E09CA09C713C4A8C663944394A394A892918A3944952A51288912571298711839E032FF0001972E5CB972E5CBE37C31CF8799E6626239E17C2A5100952A554A952A04A952A54A95DA54AE17197C1E36CBE172F80CBFC5FC1FC5E0FE0FE5752CE72E5C1972F82E5CB972E5CB972E5CB832E5CB65CB972E5CB972FF002AE15FE37F0B8FE7508421C0E2FE15F93C595C589F811780CB972E5C1972E5CB97C2E5C1972FF23FC47E55F8DF163C6FF0BFC2A54A810254A952A54A99952A544952A54A952A54A952A544E0E211FC0E37C6E5FE07038DC3F1B97C6FF0381C4FC96E570BE0CDF83AF16070212A54A810254A952A54A952A544892A544952BA44952A54A951254495189E92BF1AE152A570AE010254A952A54A952A54A95F99C0E27E2CC12DE39E0C23C2E6B0262042540812A12A54A952A270A951224A95F85449512570A952A24A952A54087E152B854A95C2A570A952A57F8AA543F13F17F03AF0654312FF000A95081C0381AFE6FE351254AE2F17F063C1383F8EDF83687E67E0FE37F99F81F8BC0E0C23C2E592E5CDF86F0871B843FCCF17F078B1D3831D63F857031C08C1AD7F1BE372E5CB972E5F125CB972E5CBE37065FE0F0A8F158F0B970612F80C1E1708425CB972F8DCBFC2FF002783C6F832F8AF0B972E5CB964B83D65BD20E21C2E5CB97165CB972E5F12E5FE02E0CB972E0CB972E5CB972F85C5971712E5C597C0610E00CB8B2E5C1832E5CB972E5CB972E5CB972E5CB972E5CB8B2F82C712E2CB97165CB972E5F0B832E0CBE0B9717F10B97F883C03F105C1E0B97065CB97C0BF8061FC02E0F01C02085972E0C1CCB972E5C1E172E2CB97C172E5CB97165CB8B165C5971785C5972E5CB972E5C38172E5CB972E5CB972E5CB97C172E5FE00E01C038172E0CB972F81E218788BE25F108208789C0FC0E0F0783F9BC58F17F07F07F1DE1C4F17FC2F0781C4E2703487E0F07831FF0001AF1359FFD9,
       image_type = 'image/jpeg',
       image_url  = '/api/vehicles/9/image?v=1'
 WHERE vehicle_id = 9;

-- BMW (vehicle 10) — image/jpeg, 20.8 KB
UPDATE vehicles
   SET image_data = 0xFFD8FFE000104A46494600010101004800480000FFE1008C4578696600004D4D002A000000080005011200030000000100010000011A0005000000010000004A011B0005000000010000005201280003000000010002000087690004000000010000005A00000000000000480000000100000048000000010003A00100030000000100010000A002000400000001000006D8A0030004000000010000049000000000FFDB00430006040506050406060506070706080A100A0A09090A140E0F0C1017141818171416161A1D251F1A1B231C1616202C20232627292A29191F2D302D283025282928FFDB0043010707070A080A130A0A13281A161A2828282828282828282828282828282828282828282828282828282828282828282828282828282828282828282828282828FFC200110801EA02DF03012200021101031101FFC4001C0001000105010100000000000000000000000401020305060708FFC4001501010100000000000000000000000000000001FFDA000C03010002100310000001F5400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001839B3AB7996A8F62C1E19AC3DFE2782E23DF29E008FA02BF3F56BE83CFF3ADC7D1F9FE6BCA7D24F9FB771ECCF2ED9D77CE4F646E91649500000000000000000000000000000000000000000000000804FC7C4F187A372DC1549DADD86D4E7B374F9CE62EEA251C857B5CA70CEEC7096F7961C2BB6C67158BAFC67239B7B34E5B077B08F3EC5DB6AE34BB58371DC76BE0FAE3EA4BFE62E86BDF1E55D49D623C80000000000000000000000000000000000000029A137FA5E139E3ABE635BB422E1DCE32BD073DDA91AEDE4B34D3E50ADD650BD8EB1918D57D71A322CA573F82B04C1DBF09DA958FB7B4D1E0D9E9E20F0DD06338EDB5F2C81ACED3057036763A021F47CD8F50EB7C06B1F4EE7F99FA73DC5E7DD556DD6DC000000000000000000005B0C9CD2423A871B18EEDE7988F48799633D45E5743D571F96443D5757E4C379A5C7888F076DAC2448D3DE6689B69269FB4D6CB3B5D3E9B027A453CECBE88F3C1E874F3EA1E84F3DAA7A153CFE8BE83CA69F09A5A6DB11AFDA3298F57D24D38B95D7A399DB49C558236CE547398FB2915C1D3D1B29E66F4EC679ADBE8D14E0F275B1E39DC9B78D566D3498CEE375E5994F4583C7D0EBBADF1FEC8EF40000000018F9F3A4B3CE7467A6E978B9C4ED66E76A7059BD3669E6123D3329E675F4BA1E6F77A2DA79ED7D00701777B4384AF754383CFD64938AAF6369C860ED6D3CF2CF44B0E42BD5D4E5B274D7472F675AAE42DEC6A7174ED47114EE070B4EEC706EDEA70D4EEC70B8FD06C382AF6D2CE0A57695398D86DEA469B68908B426D604D2B65D698F1E4B0C30657284FC1AEC46D706BB192F5D5D7976383B230C6939881E85C2F7076E000000C3C71D7723C96ACDAC385B12FCF5C84B9FA4D29DCC7F31887A36BF90BCE8A5F2310EAE26A25C48A42806EDA056FEBA2D913769B7E808F3B463A5BB99A9D2B9B1D2539DA9D0D79DA1D160D2DA6E9A3A1D1DFCD6637F5E7EA6FDA01BF6806EABA4A9BBAE9287451B4F18EAB373371D461E7719D25BCF8E86BA1CA6DF16B35E7417F9873E7AB6B7CE331DFEC3CBE39EBD1BCB77C75F8E34A31E2C988A595A14C77DA63ADF523F4FCE6E0F4E0000357B4F39353A6CD435D336128C359D79835D978432E2CD533530C6275634536F379DCA6C21CAB23B5BB8EED0C175B1AA663836936C8542564D75C4F41139044DA4412D104B44A929144A4512D104BAC412E9184947A922B1AA49A45CA4FBE0E7253054C996309D5D7EB891C5DB36B1CB858236166AA412A9ADA9B48786418FB1E5221E8F6EBA715B6942B454A324921E3CB69EAD3755B50001C177BCA9C9C6C31493335F38998EED59CAC68F30C582FC05DB18334A5B4944589D0474D6E7C562E5DEE9B19DCC5CB88C165F8CB54A16DD65C5CB45CB45EB2A5CB45D5C752F582F5952E5A2EAD82F5A2F582F5942ECB8EE33E78994CF760B8CCC341C76CA019A25F86B1D9964A597668C44A6EA1CB0A5E4C05EC598B3B6E0BA43754B6A5D5C75334DD6E8097D3703B53DF58730000E57AA8878562CF8495B0E3FA8363CFEE79823D97E3246BF24D3166BF016E4B46D61D91D24C7CB1D6B4AD4DDEC39EDD8B160B5694BB1DE5544554172DAD556D4AA942EAD82F582F582F582F631958A86647B893745B895745BC934C1532DB8AD39FCB17396DB4BC97930DA9334D9ADA97975F265CD0E984CD8F0492CCF8709DADF1EF33571C422E9F6B8483B7ECFD28BAEA5400003C8395F44F3735BB8D4653A7E7B7BA522DF864186FA62244AC52CB2B26F3577C9BD2DD5CE896D965D8A33F41CDF44294B4ADB6E133D23DA4AA46B8CD6DB796D325C60AC8B88A997106B3EF35ADA5C6A6BB7BCD337971A26FEE39FAF45539C74839BAF458CD15DB68E41ADD8CBB0648C6A6EC75325D7C5265B9B017CEC52489137380D462991CC72ADCC46892631D7562D099CFEC2D21DD2EE2DF59E27D50D800001AED8F3E711C47A0688E3E9D0C724C0DF6038E9317346E34DB4CC6689172D63C9497515975A4AC91A9186965A48E8347BE235F2F29124DC8BEEC5692AF834AD8E4D3D0DDDFCFDB1D2DDCB5B5D6DDC75876D770961E835F3AC67A4D7CCA87A65BE643D270F9E8EFB0F0D53B3C5C88EA71F3753A0A686F3711E0DE4CC38C69B3C7CE67B31EC883662944FAC29E429D6C32441C79A254593AEAB7164BCB8B8B6FCDB335FD9ED3691AFF00468D26800295A544691AF35BCC6DF9223695AC11ECC513AD82246EB9ECC48BA44FAD553693535D825EA8A44AD92DD65B42561C6ACB4C632B1562F5832312B2B1232B10CAC4ACCC233308CD4C5532310CAC432B10CAC432B10CAC432B10CB5C233DD1AA5EC624668790DD69F2ECCD2D77769A6CDD2DE682ED8694BA25B695BF10CF9A26436DBAE676F1DAF57E7FD31DB65D36D6B2800A5695290E6D0E6B9AF4B1E27A9FA0287CE587E92B63E6AB7E96A1F34BE95B2BE6BC7F47C43E7E7BD5A781BDF7347CFD4FA2B31F37D7E94B8F9A2BF4B0F9A5F4B0F9A69F4B8F9A2BF4B50F9AE9F4AD0F9A9F4AD4F9A29F4C0F99DF4C0F99EBF4BABE687D2E8F9A2BF4A8F9A9F4A8F9AA9F4B54F99E9F4CD4F995F4D0F996BF4C8F99A9F4C8F999F4C8F99ABF4C0F9A2BF4A8F9AEBF490F9B6BF47E33E74B7E81895E2197D7F11E4D87D8A51E1AF7F911F3B3E8FA9F36BE921F37E6FA2AFAF03DD7B2E48F33E87AFBACD56C73555500295A540000000000000000000000000000000000000000000000000000000000000000000295A540000000000000000000000000000000000000000000000000000000000000000000295B6E00000000000000000000000000000000000000000000000000000000000000000000A569500000000000000000000000000000000000000000000000000000000000000000000A569500000000000000000000000000000000000000000000000000000000000000000000A569500000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000A56DB800000000000000000000000000000000000000000000000000000000000000000003FFFC400321000010303030204050403000300000000000102030411120513141521102022310630324160233340502434421680A0FFDA0008010100010502FF00EA9DD2C6D16AE9DA2EA7488755A33AAD19D568CEA946752A512B69D44A885447B57F0D925644953ADD3C44BF10CAA4BAB563892BA551D552A9BD3A8BC853098DB98DB98C2642F321BD3A1CA9D06D7CED21D5A66AC7AC4E8335D9466BC47AD42A3353A570DA889E5FF027BDAC4A9D669E227D5AAE72A6A6EEDC73C4451ACB9B321C671C5388C38919C461C48CE1B0E1A1C5538CF1699E6CB906351C2C709252E63A9676994D18CAE99A43ACCCC283E246B569F57A19C6BDAE4FEF2A2AE180A9D61EE2AEA25905A8EEE6CD290D12A8DA21B4CC69B6361570DA29144D39E74D71D314E9874D53A7385D3E438128F8158BB64CDB148CF4AC68A3E82078FD35E84ADAC80DEA779C686524A29582A2A11544D0AC1F1057C453FC5653EBF413114D1CA9FDB556A54F4E556AF34A492D8BCB21C173D69283248A8E38CDB121570CA255194B1B44658B162C58B162C58B12B32936CA746C952949159D44D168DC3A9DE8605569F0D4A5769335310D64B18DA88271F431A925148D1CD56F83247C6B4DAED740537C5253EB945311CAC913FB0ABD52080ABD4A7A8247B588933A574713631648DA2EA0C89293576C9590D5D34CE44884962437986F30DE61BCD379A6F34DE69BCD37986F30DE60EA889ADEA742566A34CB0256D2B5B435B154C3B82CD624D4608C9B5BA4427D71F9B758A971534B255C94DA73621634163693D332449691EC36DE6DB8C1C62E422A89E25A6D76BA3293E224710EA34D288E45FEA5EF6B1B055435056EAB053957AACB502C884952AA64CBB65375511FBCE16192D4B1FEA5054F19D5E8AB47A1CCB232DF2F5CA8C5961DD8CCA3D464A5649AA55C83E695E2472386D24CA3291ED5639CC474AE15EE1554EE58B162C5BC5113C13B11CD2308B52A96116B4F23D5A9DC926B74ED17E218CEBE514AF9A0FE22B9107D5D3B076A948D1DAD5320ED763175D5175C985D6AA05D62A85D56ACEA756752AB3A85593D5544CB0C2E8858DCA6CB8E339565A5454585ED158F19148F5652542094D50474B25F8AE1D154D9B4D50C5C2B0DBAC30AC30AC30AC30AC36EB0DBAC36EB0DBAD36AB05A6A872F0E51D44F12806D13106D2C483636A18980B1A8B0A8DA491C374BA970DD12751BA0B84D05826870A1D169C5D1A9C5D1E11DA4463B484174B70BA6CA2D14C83A27B4B29DC6A48A632A7861FAB4FAACAB2FCF7391A92EA54D192EB8C424D6AA5C3EBAAE41771E329A570DD3EA946E9354A747A93A3D51D1AA4E8B52745A93A2D41D16A0E8D50746A83A2CE749991FD1E63A3CC7489CE93392E8D3BD1742A919A35631FC0A83815074FA83A7D41C0A838150702A0E0541C2A838750716738F31B329B721838C54B162C48DBB13BA62244F519493A8CD3A5519A6B465140D1B131BE65F05F0966630755B4E60EAC416A95474AAE1F346C1D58D439F2A0EAEAB79FE4C82C4F4342A36B3E6BDED62546AF0464FAC4EF1F2CB2AA315C4748E523A1611D2C0846D6B44708BE17432699B4DC61BAC376337A337A337A237E23798B51BCD3790DE378DE3794590CCCCDC370DC370DC370DC370CCCCCCCC599A8BBA86EB447B54F48EC71A646A53F63232323232323333124F579677E11B945F0552E2F7248D76929A5BE5516BCE24D3A0FDD91745AAF5FCA9A68E14AAD609AA24995CE6A16541A8831463AC2D4C6D1FA9603F5A441DAE0BAD91573E7497509E35EAB29D5A53AACC75598EAB507569CEAB507569CA6A9AF9DD4E9331FBC2486666666464645CC8596C6F1BC24B73333333333333317DEEE2EE117BE654C96818EF435C646E1B8A6F1BC6E8B21BA2CC883B51B365D651A2EB8C3ADB08B57C892A5655570ABE0BE55F17FD3A0B116A7E44B2B226D5EAE4B33E557CC88223A55899B6231CE313346951A831A49593CA6DBDE719C71145A47A0AC73152AA54489D1C84B02B47315BE1DD046AB88682A645D3F46489328A06BA5574B732323253253253353353353714575CB97323794DE53794DF537CDF390720E41C837CE4124D9BDB2B91375C6EB8DC12533C8BAB4475CCB127A86C6953ABAAAC92CF32B69DCA711C7154742E4239E684A7D508E54722AA2AAFC8725D3499B6EA7CF5D56DA58EA6AA4A87C92A34C9EF2086E232C988CECDA99D18DA8A97CCAC88F4B4DD375E6FB86D41B8D909A9FB43847347152BA35829CDAA742F1B4DF5437DC6F29BA6EFAF794DE53794DE53794DD53754DD53754DC53714DC53353714CD4CCCCCCCCC8C8C8C8C8C8C88DC23CC8B972E238475857A1595AD81257C93BD90976B05A937E4537DE6F21D9C3E2219A481D4B54D9DB7F92CECEA393769BCDAE3956B6CB22B68D55523441AD11A628554AD8D2795D3C8C6628E788DBB5CF728CF611C35D6239491114A2A95A692F7470BE37F0FFAF957F2DCB972E5CB972E5CB8AA2288A2297F2D654EC33BBDCD4C474AAA2A76551BF497116C3641ED470D73A29296749A3F2A25D6585D10A44D5967D3617414DE6D7615E4B51AC492A1A35C355C332CA7BB13519B27C4CB23DD717D9AE56AAB12744662322472BE9AC595156C31D71C86973DDAA2F82F8FDFF9285C452FDAE5CB971F2608F7ACF2A761EEC95546BB15DACCC6CD722A2A2785ACD6A9225D2966586545EDE44516B5591CB32C8BA2BD195BE7D7D5122930B5C6A8D5114AE9716C4DDC9657782A8C6961EE21FAC9512CF6AA25CF746BD62911C8E6A8A2F8AFF36FE373235093D31A587B8B0AD5228454ECE729EEC776548F27CAEBBFECD5244B2E9F2E50F92F649EA5AF5DD4434FCA69E26611F9BE2362BA81CAA6E4884136E08A238D51FE8A74B47F548F81F8E2B920CF52CF8E703D054B3557273B143368DEC3D0A07E502A8BE45FE4B7C972E64645C99772A17B235AAF748D734A76A6DB56C3DA8B1B8A4B4A471B3912E0D5D8EEAE634FBC9F4D03B19D17C9592AA963134D82A5D3A79EAA2DFA77A62E77B31EAD7B1D937EDA8AF76FEC219AA11CAF15EA39EA5D5CC8936E457AE371CC5BAB6CF5ECABF4E9EEB3BCAEF6F9D72E5D0BA19219219219990AE32323232322FE2E5B245DDCE3ECC95C24AE1D2F7748AA2B55EAD46E39AA9920E7A89DCFB2FD312DA42FE15536D328E38A446EDBEAB49A0A7AA95AD46A7C8D7E14875197E8289FDAE6A1F5A7EDB12E3D965CBB5C6B13265A359A474A3E3C558CB155FBC9DDEFFABED46BFE47957D8B9743243343710DC370CD4C9C5DC773BF92C58B16F0B16F953AFE8C3E16BA362C06DE415171567A237C2D6BD379D876264B3A24BABBDBEC9EF7ED733B24994AF462E291D889D246ED02AE7A98BE47C54CF54FF410BB090D4106FEDC57266D9222C318EDA4EC7651C88AA9E97CCAB24B0A5D5FF5149FECF8AB910590BBD4C5C6D9B66DA182181B66DA9B6E369E6C4871E538D29C494E1CA70A538321C079C071C0380701A705870E338911C4885A48C5A31D4AF4158A9E33FECC1EE31EE6B73F553BB07CCFB1FF003F6F774A8B835AAE7D5391D3D3276945F643233164437186F20D95CF7468B03A8A04A7A7F3D5D6454A9ACEA29591CBDDB8A96523EF15727E9308D6CAF6E6D6B1DBB5103E9A46B96EE572CEECC6BD474AF61C9721BD74517B2527FB0E5B199864360884644862C3D276F9F74371886FC4872A016B69C5D429C5D4601751885D45875143A829CF71CE94754C8F4C8B92FEDB16CABD9D1FBD432C4327644DC959DDCE776E448D5595AE379635BC4E58E48D1B2BBBBBD90DD53352EA21146E7AC30B6820F87A95649BE46A3A64758E9B4191A4BA5D5315D4EF8CF490BE9F1AED85A6622AAAA2B1699E54419A4934AF5564AC6C7FBA60AA8C51225C9EE107A942C574EB13446465E043769D0DFA73934E72E9CE6D39CE84EA111D41875243A99D4DC75390EA529D42639F39CC9C5AA994DF90DD719A972E5CB972E5CB972E5CC8C8C8C855EC7D4D6A8DB48C918B1B93D1147209670E8EE369A417B10C4A3911A8E5C9CBDCC4C4C46C6AA45452BD6834F58D1DA42CEFA789B045F21EFB1513589EA9C4B58E25A9B8E7B057210D4BA1279D6618EB0C9F18F07CAB79518922B5AD964C91D38F8FD37548D56E2AD9115059955B72E5CC8C8B9732323232323232323332323232333333333333335335333353353353353353353714DC71BAE375C64A35D655237F75731CC72B9EAC5B2AB951DBAF1A933CE33B181EE6A4AF2E5CCD4CD46C8A32552099C852D4B882A2E31D74F2A7B0A48551504BEEF17CAC7AB5629910CDAF4A4FD34595A3E5B23E54BB96EBFD6A2F835E46E4B3A2638A48A3735228DA2AA212CB62496FE64232129CA752151AB7F2A7B0A3DB72785CA5452C84B4729252482D2BC5A679C779B0F365E6D38C14EE866F371E7BF8594C54C14C1C60E36DC60A6DA9829829829829B6E3053053053153153153153153153153153053053053053053153152C58B162CA594B2962C58C54C14C14C1C6DB8DB71838C54F61277A0952A8729C3A77B8EEA62A62A62A62A59446A8D6291C4A431290C6A840C2368CF2A7B796C62860D36D86D30D98CD8885A6814769F48E1747A051742D3CE83A79D0B4F1346A04134BA341282950E1D39C580E2C0716038B01C580E2C0716038B01C580E2407129CE2539C4A738901C580E2C0716038B01C580E240712038901C4A73894E7129CE240712038901C584E2C2716138B09C580E2C0716038901C480E240712038901C3A73874E70E9CE1530BA75228ED1A85C3BE1FA0517E1AA153FF17A313E18A219F0ED0346E8B428269746874FA43814A70294E9F4A70294E1531C58048234369860D314F327B7E369EDF8DA7B7E369EDF8DA7B7E36DF6FC6D3DBF1B4F6FC6D3DBF1B4F6FC6D3DBF1B4FFDCC4F6FC6D3DBF1BFFFC400141101000000000000000000000000000000A0FFDA0008010301013F01325FFFC400141101000000000000000000000000000000A0FFDA0008010201013F01325FFFC4003C1000010203030A0404050305010000000001000211213103323310122241516171819192202330A11334404204505262824372B12460A0A2D1E1FFDA0008010100063F02FF00953E93DA39A9DB33AAC66AC60B182C66AC66AC66A95B33AA95AB3AA9387FB36368E0DE2B42368772F2ACDADF753B5CDF653B573950AFFE2FB97DCA856B5472FB955CAF9555324702B11FD55EEA14C33AAD269E455F8715A368D3CFFD87171006F50646D1DB91F85A0DDDFF00AB4ED73DDB968B569BA1C168B0954015FF006537B96BEAA8AEABAA87AA939C39AC472BF1E4A8C2A764D2A764EE4AF96F10BCBB469574F25578558AA9E4543F139C5BB75AD0FC4323B0C945A411BBF3DF31E01D888B06868FD4E59D6B691E2B406715E6BF35BB14984EF72D37726C949B92409577AAAB55F0B10745883A2C41D15E6AFB55D50709E40359478A98055C87092F26DDC3739799662D1BB42F32CB34EE5E4DA72548F05392F2AD5EDE0562E78FDE17FA8FC3F36158BF0CFEF1051B37B5DC0FE6F02ECE76C6A859E837728BDCBCB6E68DAE51B47972FD3B85568B67B72484569182A478FA64E4F88F9B2301C149B05225488531934D9A5FA85567D9F9967B4542AE70DEA1682077AD03052D20B48432458E2D3B943E3678D8F9AFF516238B4AC4CC3FB946CDED70DC7F318039EEDCA11CD6EC0A2F74166D9080FD45031CE76D5B54735AACFE200C66B2B36C9C5C7FB54CA964D6B5AD6A856B542A856B5AD6B45CE3001621ED4E16569A4654A200677459D671948AA29C02D2B66755ADFC02F26C9A1BBD4AC5A792CF6D836C49AA8BF49D96F414B482BA55D2AE9542B41CE0A64B87EE9A85BD891BDAB46D00E2A463F94E73C8036947E0DA35F0AC1100E7BF60502E83760535E5CB7A8BA2F72BA54AB922428B8287F4CD55AE6FE94FB271896CC7A82C1B5337648647B6C83748C66B148FED5A4F71E2549A4AA01C4A8978E8AFABCAA557D3915A2F216293C5798C69E0B4B39BC95DB43C94AC5EB03FEC9AFB466613ABE96660B4AD9839AC58F00A41E792D1B27732A5623AAC26290674556F4589ECB17D963158CE51B4B4898404451183CAAA92F326B45872C02D4BEC5A65BC949C143E328B2D5AD2BE657CD2F9B2BE6DCBE71CBE71CBE71CBE71CBE71CBE6DCBE64A8B9ED3C95E6A9B829BFD94DC4ABAA4D03C3A2C71E4AE438A9960E6B4AD87453B6774537BD56D3AAABFAAABD5E7A95A1E8AFF00B2A8545A408CB2055D77453083B7C534183F70FA08920053B48F09AF2EC89E2B4035BC9695AB94C92A4C71E4A562E5721CD51BD551BD57D9D57D9D556CFAABD67D55EB3EAAF59F557ACD5E620DCE64D5E62BCC55B355B3556291B3EA83B3592FDCAE0EE57475574755747557475573DD5CF7587EEB0CAC272C272C27F4586FE8AE3BA2BAEE8A87A780A0551498EE8B0CF35A45A1693CF25763C549A072F4A733B949855C0A4C5742980A6A4D5A12579CA7FE569397C68708FAB179006F50645E568418372D3713C724E01691256183C568B5A39782AAA15E1D55E6F557DBD55F6F557DBD55F6F5588DEAAFB7AA918C1BF4D0D6A8EE8A8EE8BFF42A0464159C85D1F404F8F3199958971A95ABAA8418392FB5518867417C1750D3D38DA38050B01CCACEB5793C56D5A4D2D3BF2CD6DE0A8D6FF7158C3F885896A57F54FF0025E5C09D85EB4EC21CD618571AAEB1518A8CE8A8CE8A8CE8AEB3A2859D8B7894ECF7B2828157D3AAAFA31D6AF957CA89249C8FE1042194E4AE58832C932A40712A76EC6F058EEE8B1DDD168DAB5CB4BD42A27ED6FA39CF300A16021BD45E541B33B56B728C06728E58374CFB2BD01BB2D72C098F1540D77B2D3616ADD924AE9E4B42CCF392CEFC4BD99FB02CD64392770F0D55555555555572D7D56B7F92915555C922A725B95555673C86850FC3B7F915E63C9F0E83DC142DDBCC20418B7685A34F4ACC9A1D13E844CDC68145EE5299C9A5450192A8B9D21FE5428DD8A7934412AEABAB5B579801DE145BA4D43E3373ACF5A058D7399FDCB063C4AF9762D1B2B31C94801C9572F2FAB276F8E4791530BF53F62CEB4312A6A6B4412AE29B54C43249689E4B464ED63D2239AB37EEF191B0050121B545C62B6F809FB4289E43268F559CDD33AE2A650196525B1CA2140E11AA88A7E430F47F79A2CE74DC7268F551BD91B96525A5922D910A22BAC78F48646B6CC171418FAF8DAFD4E0B4880B358A4D2AE8EA86766C1183E3B24B30505544A80C916C8A8C9B69EC508D42747515A2540D501AD6FC86C5D514F18FAA8FA049A045EEC9BB2E8C9DB1006BE0D2A99A81C91D5AFC7E641E37A8D06C0ACCED97A1651FD48E6C3C25DB029F12A5967937A76F9E48959DA947235E2A0A88F10FA987A21835D545414B244D540D57043203F68AA3E0CD356F866A19B10A4D4D03444535B12602113E3CE1F6BA2AAAAA1F76586D29EEDB2C916C1DC16908652186236A00D502B351CE713C166B5A20A1921B25F943BA208C14C1088A39D9220E92285993029D676B50B36CB5A8BDC02835B1DE571C90DBE1F86DE7959F02C9EE6C6B0F42D2CCFDC208B4D44B2670408C8C4D1CD1C92254D8C2A90599AA3140BDA1C36150264A4B494B2BC7E4E51396AA80F253B35BB62240802B587835532B47C0534EFF0007EE28BADAD3302A9F823FC2F880792CD47EE2A0D000F45D0A3F491C85991BC13515A4A029901744B75C11CEB3CE434035A35050043A3B1411DD2F5AB968A9F52FE1964A369DAA0D9044EA0818CF585A4D712A2D686EE5133F09F04517150C91B3241DC9FF001E79BF77A36169C46507234A0B44C0A8A864711A93634D6B62D8502EA27B9BB6289CA3C132B44454A014DEAAAB928A8A8AE9574AB855C2AE15715DF75ABAAFB55E6ABED57C2C4F6589ECAF9579CAAE5AD6B552A4E0B6A98CAFE08E4D1EAA6B8859A86F521131D88B4844335D501B53A1444E43E09ABAAE859AC6C494D6403EDDDEC9ACD7AF8FA1E619EC4D606C2063E069DCA3BF2EE28305E8C11B3B510705B9689A2AA8150054C34F247CB0DCADCB3791C17DC55DF758615C6ABA1507AD50AFB7AAC46F558AD588AFFB2FBBA2A395C72C3F7573DD5D0B5299CAEE1E08A86E5345DAA814A31C9167BA8E68573A15000FA000AACF33B634DC9DF89B4A0BBC7D1CE2E735DB97976AC3C42B81C3715A41CDFE2A768101F1004F0C70260B4412A608E2B34F2516DE51B6712E03366839EC21A75A7643050750A9A9505328D814DECEE58967D562B3AAC56AC51D15FF006578F45F7745472BAE570AC3F7587EEAE355D6AFB7A2A8E8AFAC42B11DD55F775578AAFD10390835D793F73BFC20A4A8B50E688C90D7AFC520A4D520DCEDAA36B6DC804DB3B31A23D3AE49B42B8321CC5A555B9694F6145C45566671CDD8840455D8A819053805075DCB459B41EBD153EA77641B512FD5F6A27248ABC57DD0C85B9B4501CCF8A4AA84FE964B44E6EE345A4DE6D4E0EDB2C929715FA94FF3083FAAD4E1B9491CF6C482A4D6E499CDE0A0D90FA5A2B855C774570F4574AA2A2A2A2A6492AAAF8E9F95515153255502A2AE5A78E8A9EBD1502BA15D1D15C6F4586CE8A76567DAA7F87B2ED5F2CC5F2E3AAC0F75803AAF9762F96B3E8B02CFB560D9F6AC1B3ED58567DAB0ACFB560D9F6AC2B3ED58567DAB0ACFB56159F6AC1B3ED58367DAB06CFB560D9F6AC1B3ED58367DAB06CFB560D9F6AC1B3ED58367DAB06CFB560D9F6AC1B3ED58367DAB06CFB560D9F6AC1B3ED58367DAB06CFB560D9F6AC2B3ED584CED584CED58567DAB0ACFB560D9F6AC1B3ED58367DAB06CFB560D9F6AC1B3ED58367DAB06CFB560D9F6AC1B3ED58167DAA7F87B3E8B0472586EEE5FD41FC95EB5EAAB6A7F92B8E3C5CB002F97B3E8BE5ECFA2C0B3ED5F2F65DABE5ECBB56059F6AC1B3E8B099D1498DE8AE854FF0093FF00FFC4002C1000020102050303050101010100000000000111213110415161718191F120A1B13060D1E1F0C15040A0FFDA0008010100013F21FF00EA99B4AE7BA0216C3D23087D220FC0CF16CFEE627126E05B34A8E92E07864FD991C36E827D76C2297CB0C86ED02767F69632A3391DBA7D03DD2DB75DC79136BB85821564317FA04649EC75423484743A85A2558CC94FEC2161703237DB1ED1412354AFD84C3AF4D04C37B07720EA95774475120D99D295DC995989AEC98CCFEB026FCF62CD87016B3D4D498FE2CD9773FA31E48181225458F49C87E081AADC2683E71D25D26A26910F54F6492C6CE5267B9A3FF98676464135A28FC900906CD97B9B8086927FEE29E00ABEC77430761EB679055F7B6235B47EA423DF185D864558D0816FE7575388D3B62347F60CDD1D45A4F4C0120334D76774322DBA8D391F0CA360E2444FE8236F029FCE22E0ADA94ABEB622AC919124B8DF05F42F515A48A18C236D193863BE88B4D3492CB4BF839323536D0F90439FAA9FFD66E09883EF137F24EE21AE9EB56C7FCB2D8A8B34252A955D3F23DED2AF0BC87088713445866D422C497D2FF3B52638221251AB667DCA1A6AC862D84EE06357A13CD0D912E089F91191DC440A7B128D22FE8EE56FA264938EC0CA18DBAC141399B4115227205B42113D021D7C05FF0041B494B64A6CBB7B939D9F210361BF86099926ED565C6AE48A27DEA5E0F2DB8B7717F6218A46E7B40A6985C1CFD8FE88FE88DBECC176F0E78C36BB4FE88FEC881DB96DA1B1BA5CB0EA4EE9350EEC6F714A1419D8CE91AAA64750A4981BB82C97B8C8982C3E8355B285C2B1A560289EF0339BE2467D1B22E83724C375B573C19E14F1226A89E82AB64B923525648205A753FC644CA6948A6549B3FF009123D22EEC842A75CF1258A0B59B45CB1DBECAC494BD0A541758BBB1584D9216592427BD3935D33A130EC21D6049666FCE8D372A353A9344E60D4D1E08FA1041393E13258192AEA6248B14B80924B1F596C81CF7919F1BD8241210F1221B5744319863F30CE61A13264C9122085804B71981B4BAD9C1910D2A15B2EAD0C96C9D1C86F4E0955C3DCB486D9439173B753CB2FF00CAAE549BBC36B96390F0832177C08FF7E3924F763F661976B82D645C28DF9BB097F0A2B7E219B6143746A4C146E94705D8382C914D33DAA885B4A3E46912D74177990DDCF27E56C9B5F71254814545CA187448888966E211266DBDCFE527F63C0BC09E37D0AA9FD4C7AA72D93036AE46B76D9715EE2C33F086813D9AB967B352128E561A183D9C0591F9123FDAD1913C1CE4E10BB3ED8310B1B23DE25D3DA11F9A13B2842CF32FBE18C7BA6153A975FD057510AFEB156DA849064514DA5157A0ADF5F7220DC16B5F42EFD6F05A1F691961D1381D95FDDC956E39CF952A0B92F253C7719AEE6193CE05FB41E7079862FD9B1D7711B9AD20FEF67956792679465452EB247A809555050D7B23C745FAF8BF5D3C547A6ED1E8FB069FD079E43021FC64373C57EF48BF3CD5EF086E6968FB12D19C084C56058C57524F517D9D459D1D87CB3C895F708354F749EC5C214589139F41E06866D36C05AF3964A6B1727E66CCAB16351781C44274C0859E26C288451519F51FE4C266CCDB51F247D5DD6534125C0A8BB8E1AF7877274FF748F2126D99293E2E5087F9020FA22143093369DC8BF31E3C78A1E1078B1E1C7871E2581149252FADDFE860F291C465B6A6E0D799C8E67213EA72391C8E5EB3AA437A12C7057ED25559407F0432B1C6937611295191B09E95DBD63F21EE224C58C621CBAD972501C6C40CBC1A968A30D3EC0A4411C5222896D9089D8A2A760769043C872913DBBFA7092FB8F530CBAC00A09EAE5A2B89C8D01719EA88C84543356DA042596A34C87AA7E25087376E43D5B9248B7227038BFDD8F2BDF3C733C714269605675309F9BD473B14349138475A44B32099435723A91D4E7E9F7A19261B21825DACE67339E0E47239107036B52C27FA511D2C45490D88EAE0E5D08916250556B438136EA5781A855E42131737584C5407BF001CA545984C715513EF70E273B5CB088F0D22537A2548B0A79E03C0C6319690C825D033C922F8FA2E29AF363EB19AEE3A3AE736CA63BCB13D72ABD61214D1D0BD05ADDC8990E643EA356C7ED86109B4A91312DB6F71663426D97B8A2CDF15258A67546C84A3E46B0DA64EEEB90F6F567915C52F541477EE852065467014A1CE60F281B9575CBA1976591C9650A77C09B527AB37B09BC3706F0DF9BB1B70E673141CA09399BE6F1B84B527A92D496C749D1864364A8AAFF040B225B6E72EE3CE0F58ECD5EE564BF60B3D23241AD540DBB86C60FE8E8749B4D0B1A62D81A4B09620D26821C447FDD09A3E0F8E7A27031E0C78C8A2CCBDF3E87CD008E8F3D1648A602B5B6E1D05B4AEC15130B0714B21BC70885274439D4286B08FC711FD58B33B591350D9B90BD92629B3F7335C935665138639D0B570418689A31992EC8BE004F81501EB86CBB184C9DAF4BF58EE9BA6E9B86F1B86E1BC6E1BC4F527A93D496A4F527A92D496A48913D46379B6703351393C370874710F263F38BF8A9BE366472E2A4FE495965969C115695A10E592420F8129239349E8C99D6E434AA433384EE2D5ACC98505DC8A90C9F5E9FAC0D7C7573EB601D200DA323BEA1819AB3508A1A1DA102098D46A64A2DA02AA5C9690DDB214C853C8B78292CE322AD526510C59D3465C3CB4C8A9783214B446A8912DB68D371A2589B553435061B1BC124FB09C249249249249C124924FD3003BEF401C8927091746B61FE893AF75151553BE6CA7D2B511B2BDEF3437367744D4E4489696C48E2ED441BEA2B384954929A0C6F09C6312BB125A19163898466D36E34F5C0996EA84AEA0C52A4EAD21B99E903F6472328B3C26C66A94B591935F29917799224A666465A092C68B34405A956FC2CF6A0C5FCE72066636652980A9A0572056C12F818C19BC742DC2FD078524E124FA64924924924927D523CB6C2C40B02648535E6333AAB2D10B1ADCA6799487CA9D468ADC35B67C09A954438CA68732608A927C2D081F6196FBA14F368BB13235675C249C2019A2AE460A878348D252EF42F5C8784E33D06A4A3E25F713156AF1D9B899BC721C09B1644CC9EA2A43A2B667A9D8160A112D5858EB099249914D87800A49B0D4FAA5DC89249249C2719F4C92493E892491B9E58213104C9C2E03E5AB5F0226CA14B5324E1213A33D88553D0B4266B01EAE7EE1B5215D10852A0A71A85AF3CF2D05512A38D670E3D04F09246923421D2455AA2C93D4673CE89BB9D69B07EBD344DC58974643B197222A512E87AC3615A98C948A5292D589503BE44A83392281282F05845DC54385A27FE134355CB429882124C6490870C17B8BC3DE1A96757C030DE123D1724FA27FF1B70AA592EEC91098B16C332AC938148EA518756238D51C083CE05FFC2C18A94C75438466933638CE2C356262469AE3A9B7555899B4DAA646AC922CF164264582909E2F7273FF008374E0945866302CA50BA96D7D6BB29A1ABB524343310EE48BB098E2B571ED5CB1A370C58E87981955DCD51FE5F81CA4D744E835EA925C8A89426E0630941B26685BE659F584C56870B5F03BC6363783FAC4924EE4089BC6F7A0A81C4638A509E826D04C711088F09568A49EA151A5B25AA09D099751B7EE46499D98F54CA4D341675898590941D43C3421504B37A9139A9EA5622B1108561B308488262283D01C2936A4C8EFA5AAE6C26D5D76BC1A0AE80B24BE8B85612BAF31E30A771DAA8B630E7F7872AA52E4D11190AD301322B94EC31E98F2ABA112C82A1697F455B0A06551E16B47622C0BC4889D6A9E0F0785C13A2371BC33693C08EA2436310E782190C87E9882182315E89C20E416B26E27A29244D0F24BB27C102984505A904A93376E8590A711BA4E835B4A36928A1235A45EC414BD91302C2615AE454041A39AC8D60DB6202541CD29317323DB40B448452CCDFD1A6346FC8F5991CAD5C3DC90DD0632B75843E536DE634B08B9A2BDCA8A8DABA138705CEC2749C798AB3694C682309960EE9C3CB1341FDDF8246C92C0210DD40FE5B27FF037B8935083784726136BEC2FD60B2FB478834013813FF625C816892D6EE102B3017EAC4F9A04B9B059FDB21B979C3FAB1EAF799059A9B911CA5D03BACB95848E0B0D89A8E14A6FA91252ADC897C59D1D49892622BC992AFF00128B28A9A3315A36ADBA23028B0469AB40C1CC85D0977850862B90A290D0DC1686D482C49885124916C306B7648880B3BEB9BE874024B8A84912B2C4711B06D327AD87B4D01EE8867A096C5822ED688B52127A5A9914653C46903BD440AA9EA7BD11511C48D2959A1EB038FCAF81295639512164468849AC9C96C7EA126C097E90B4764497EA170BB0BA09B14885382C11184AD48EEAEA66BB01DF19AAFDC12C9D988666322D864DE64BB83C860F214699E947A10CA7C12782B508B65621E99B3A1322A8C899D29A1318A4D4BB7B1B2943443D3D2DA350DA4E05AA04DAAAC8566F7A99D136106F64CCF5B0D11D4B8DC1BB32624C4B6DB14648C121097C4B3D5F450A3B89A93E855D2EF23AF3167BE60D292EC28EA4940F573CA0A94364455EDE10952B0EEFF0D0CD885AE266845329C985AA31B0AB70B217566A3D37C22A84EEA376E24B2470301440DB6DC2430E65F013B955C0BF01923FC83FAB1067183372D7F84D1F64D3EF9A2FD46C50F27BCCD040F441BECA0DBA784375C6E806FB825FCA375D87F43C664C98984C26244EE9E686A84EA0A8CA9161B64484772C98B172BD85283B518F419323DED8CCB493B8B8B4C514BBB1392F55688E24AC25222DA33AAC5EA1CB13AA176659BBA24434130BD4AD8D640D462322BEA42AFE50F647064E50A520A5CB211AD198DA5D5EE4E39AE467B3131E8A873C63E61E354AE48434ACA0890A8AC225042CEC46BF66AFF431A5940CC41D65C94A968AB618E270FA582E2380E28993264B425A12D0968896C4B6380E025B1C0707A4AF74DC370E212A772E175C587472B518A270B67AECC454B535E44053A8D621B1B970794282B91D86AD6C65A4EC6F214EB4E7A8C701360AC379B108D30803E122665EAB58583DCB599CB986F1E29922B1DD3CCD75DEB30EC8BD49432B15C847A48B8116E71C94B6DEC86CCD2C6C7FF320A3B1C0B7099564974651A8A5F544CB8CF662190D154792B3E0BA55EC6514D2E635419F42F483210F504217A2D6160FB04AE187A7E1259116E45FAE9A8761EB068C1210D7742CF34256737236DA5CB219B46D9B26D61360DB364DB364D936CD836CD9364DA360D8360D8368DA368D936CDB36CD936CD8264891336B1A912246C1B22D1368D8C225868BB09B695466A05B9A843C843422D110F9AB36CDF1B66C1B468065D8D214E1E216A831615E8B58C10421AE86D8F1C3FD4E1E93F00F012E8BA0BA4E82FBD15031F946DBBC4AFCC2DBD692D63B276C24DBB63C40F043C10F003C10F043C10F043C40F083C50F143C40F083C40F103C40F103C40F003C20F183C50F103C50F183C60F083C30F0E3C38F0C3C50F103C40F093C24F093C20F1C3C18F063C786EBF645FC9BCAB93431FF00416CE90F0FFC0ADFF8363E4A058DBCB65BC8956ECCF163C40F0812ADDB0956ED44AB23A4B32E8122DDB363D848B2124BD36BEDCB5F6E5AFB72D7DB96BEDCB1F6E5AFB72D7DB96BEDCB5F6E5AFB72CFB717DB8BEDC5F6E2FB72D7DB963EDCFFDA000C03010002000300000010F3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF34C1011A41C10ED30E34F3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CE3840CE2C60C70862C12A68E138D3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CE0451CE08704E8C98CF1CA1EAB681422ACB0F3CF3CF3CF3CF3CF3CF3CD34C3CD3853CA2023040960453D43091420AB3E914C109BC52CD10D3CF3CF3CC3C404C18F2433C028B34B08380E24938B3090862CC30710A30C2090041CF3CF3CD388103345044A2408638B3042482442813830432C226EA273CB3041063CA10F3CF38A0C604B00014B06818504B2C430E30F0E190C38012FAA688986F1413040841073CF3893CD38A3812DE18520330C30200A14E30930738E0C004C8CD75530328E0CD2873CF2CC10810C14E04810B2CF290F367C10000463C118B3821CC201493B8430808014F3CF3C304400910A35434F1CD04F4983C23003C534110D24C34528F02DB862032821C73CF2CB08122E94E00588F10F82C0CF98C30028A18624920A14F2C706936D04B088A0F3CD3C4005823989E7CB6B0C6B230E3BC30C63CF0CF0C728828738720F1430803879CD3C53CC081AE20853A5AAB9E6A61AADA3802FBEEBAC227BEEBA28A230C169BAF2E554F3C53CF3CF3CF3CF3CF3CF3CF1CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3C53CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3C73CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3C53CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3C53CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3C73CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3C53CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3C53CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3C73CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF3CF30F3CF3CF3CF3CF3CF3CF3CF3CF3CF3CFFFC4001E11000300010403000000000000000000000001114020213050318090FFDA0008010301013F10F9D3725B85CA8421E0BCED512C49AE947BF4B7AE9EF8FF00FFC4001F1100020202030003000000000000000000011100203040102150608090FFDA0008010201013F10FC1A10D478E2EBE00603A8A28B5CC191653E50A1F31EB0F25C7C086AE8F45C36780D1C261839545AE77D99DCEF2AFB47FFC4002D10010002010302060203010101000300000100112131415161718191A1B1C1F010D12060E1F150304080A0FFDA0008010100013F10FF00FAA4B96412D0779E92FBE6196749BA69D4FC4711FB9D267D0FA713FE17E93107A9FA9A778D7FA95773B93DE061278FDD3D2C560130DF696732C97FD25E87EE70D083C3D57F5106E1A2D5FA87AAAEC84F99705BADCFCDA2387804CD2EFD3789DAECD22994F5FF0072ED7C43FB86C797FDCC81E031D99F0603B3E8CF810FD478A6F27B42874E08006F3FE9180774DF695077744DF39DD06FEA66A0A7672BC63408BA0372C0C1C906FF00A02D41E77520F363652C5637EEF82688F94A8EBA11ACB7A82F1EBE12D8EA12F4C1A4A8F9EB085A8DEABCD87D227A9E845E92E9FCC072DF809C0EE2855ADEEE7FD64B3547644D91ECA7B1251ACEF14C0E86E0E075F2A8602CB2330AAA16362EB10E3975EE8B570D5ABCA3CA099B996E60DADF30021A70FD41C9D5B1E8C33A20A7F489FF0000C9548490B4007C480747FF006EE2F4E68D6F821413DC1F068F7864EBD6B0EC6D35B2F46DF2C49E7A71E0603C3BE3B3531E440E01F32BE8F02DE6CA45114EE46F0CF2885AC1E14FC4419F1486E780FF0073FEA7F73649DDFEE7BE20823DA1F136A5D189685A8C5C543A5F21ABEC78C4A6ABF40FF61888760612BC9E57A4BB30D3DC8853A82D17AD64F28DC2BA90D3CA675B345BAF0D6008DEEB3E51B9E390A61E26D3D806571EE43E253EB1F0EF343E9DE5F09DF8F259EB0EAD967B35963BFF00EA80B5C4108C0B2C3D5D081387C03CCEBFA4D636DCA7A1AAC5AACB7697A9030F39A60817DED5E74DAE7920161CF3BE2C092E0CE84AC26D8B651227D480519C054C74097FC6E4B45DEF3B09D845B888B2ED8E8A425746DA294F8ACF60844614E69AB07B5761A633F1ACAF2FA8B251665ED2D8568153DF7F19BC41690743DC8B52FD6F2759823651B4F48254B74013C980889CF3E4C565FB5506B499CA40B6789012D9C3D6E7D65A5F345478D37EF02174E09E65903B96E1ED823FF9EE8006AAC26BCC5D83D74795CD563A5A13ABAB195E3366D7C22A55940D3B1CC181F24B7B11DDA6BA23EA368052F68807C2855B1105F04881DED54EED510780B65480C6AB696ED67D18BC3069C4CFF907EE1FE113FE493FE4BF71DC90E3327245DA28E2803314B55F4D4A7363095564350BF1953A70D12B1BC0D4629D4373AC4FE68C805B97BA5960F62CF496052F4BD5A8C3B94BDD51A8E835E7FCC059AD75FEE61ED09D5F2354DD0DFC629C77DA1C6E9D9A8D040D12E793294A676F9225FB73FEB623FB32F8E72222A42C251F32098D4C179EBEB14A319B87D3AC1EC1DBBFAE21764DC93D3FF1ACE659C929C9024FD801E2CD52883B55C081C2D41E4781718CAE98BC5BB1257A5DBA46972E1D5F0800B7B2969E1AB1C813C3C86F1A68D32D1158A072812EE145E6DCC6A45250AB5953AC673F4AF68F6D291A535C3D48A180776E92761F796E3F0A3A4A251D2626215D251293B21118651B7CC73E1152A165FF00020C16C43FE18AA515DA3F16F60F6C50A4BB846AEDB715426D2F422B0CCD7CA966ADBA47A4CFF76B1186FCC4632BE2C76EF9C6D8F0CA769D2F58EF130D60CE4823448068A3568768286B77F64A5A2B84FAC7C37D2BE62207297AE7648B0E29A07CD99A8B46D230B12E9FF2865EC012F33C5EB5FF00E2593AA0C23DE228D1A885F225D64F1F112DEE3A12FCD969D37E12E3EEB70BBEC45DF9CFC93DD44FCC4547E0668BF40DA2981EDFA23A90EC1F129B167529ED00A92306FC20518296CA1235298711F69460D516DBD200ABE4C0784254CCBCA3FB9E309920FADB0FA8C5A69822D87B6DF887380AE511414632B18802472447B7914D468B852235E11AB25DA7A83B115193EC41FAC19D37C533FCA823F0B0FF0025FDCE3F25FDC5B548414977D44529FADA3E291BA38C98F2C2C5B441603AFF00D27863A8214B5F593CA1F5D81200A34952AE768F39B8BD16BD20E64B4B26544E6202365CAA79111E48CFBB01A874D0ACFDEC8F88919F2BFA8E7A81FA8453C9FF0050EA978AF8961E4460ADCEF497E86772377460D877A221CCCB44673B736098DD4BF52FB5E6B11443198C3749C43AF075ABC68DE2B1FF00DD50D16881E72EC0BB2AF4C7ACB11AE09F22E3E8BB4B19E2B0B51AE17D2A5FD472CD1877459F13437DAF7329EB9E2FCCBB42EF2389EE7F506D8FDF482EDF75FA837ECFEA7D43E26F3E87487D03DA0DAD5D3F54DCFD8E9144B97A829AE37B817EDFD207A7DCE902DEFA7128D1BEDC4A884E96F9690D90F173E2232644A64F29454DE890B3131E66000222722E6AC39476FDD372F87EC88EADD93E60F5F0ABF71D6F8778E8078A28C1EFFA23A83FA712BD1F7FD51D02EEBF53164FC539A8ED0EB443959A3D4C9ED3300104EB1471E011A04DD2A5435BCA3DCCA3A7DF25E84385DE03DD28FC43457E3B825000074942068FC3147882D97EB1A286A175E32D0EA044ABD49B9B8222500F163D03D463485EC0988CFCE22DA069184C70152C6AEDD912A1B1530D6FC602622E54EEF679C3FF009DC6853A807AC5683E3EAA5B9EC637E6470DCEAB1DA8D44C5F50DB074BFB941FB86D1DCDBDF02029C4F88C80ACC6AC97E104D7113D47B88EB0FB989EBF53AC475FB5D657AFDEEB3EE5F328FA5EB1FB57BC7EC5EF12DFEC18CF5502F3A300DEE1209D84474A26C4452074641001D50668BCE723E72F17820C29CCA733BE5F996E6534BF58A599B860FAF9F97EBE7617502DB0AE732FFC6962C12CEC305E5855C587E60B43EC202A8A21D50EB80358177A8D77898A60AE385A65C5FC46183305C15EEEC8022D55BB66285004E0B88A582ACD48A2F6957A695D074A82DA95821258368080B9BDBDC255A9F6C716BB5562345E65B341A1D13DBFF9BBDB0172EC6B319A30655EC6878C481970E07436801B568CCBC2502806B0A3D211B3D90A0500D887DA57565A870E5FACD2A1A02FCA5A0A5B2AF3A97A87691F798A7759ED15A6BDA31EE3F1156AD4245D930C6A6B0D55E6D4F757CC58B2BEE7CC76CFC7FB9459D75EBDE7179CFDCC164EE7EE300741A9EB04106285796DC4CB79E986EDAB998C429C432873D61C59D24A7099EF2FCCCB7F581DDF5855B91B0A7BC4A03030089D6103F017E583E52FCA5F947B9CACCC36FD0FD4C7F67A4602AA5460BBDBA90E68A3931F385EF0D65987815F12DCA5A595C91660F3350A0052A5F11C6C606085D60D9BBA60EEE06B0BDE236E8F10DD2728045C6453408D5B0DE11E05C322E7353DA17F2988C059DC2DF0730D79851AA6500DC75A205D58008FF0D5155CB38424A8039ADA6A517DF6ADE4B0D3FF0086E3F835EDCC7BB635E5D8DA5AA767DF313EBC1F41BF8C754CDE80F898DC3433DE2EAF597042E55CAC30BC42A929AA683AC324B71A4EFBF847885B7EED65C56DD2C7852EECDD4BB4226B1BE2F4816906C5A498D78600DF40EBEF151EE8547E9A9F7B253F6625AEA261977D27457478825469DBE48F20F73799423CB7E52B4254637AC46C176945697C770BEB104CE5335BD5DDEAC4B69A90ED70DDD604A1F9C3FD6956FF0038733CE1FEC4FF00B5387CE957EC9FF4233773D5888043620F460813D24E9A7209D14A764E8205BC0B98BF317E4873919F92F838F537E10A10190B836B91CB5C5479A875966918313A2AD25199630DD9DAFC6B0D01713A9DA09F38D125AAB1D4E7C3787D7EAEA76355E84E84106D7B7ED2E577C94EC188868B9AA892D478C0328F8C7172A8D5DAB9978388681A55E3BBFA41F8D325E3F73D7E12BAC7AC51625E628A1A4748E1EA8D427DE4530F46A0FF003C6F125DAB97A42A86C60380DA58073704B0D760501D2227E340C2B3143E4229976525AAB42514C68352E02344B586D7BBBC5CC670C1A6C73060D69AB83CE585E8EB3914A1C32CDE4D79C0131B84A7C49B918415DB7C989763DAC71C8F26B0184DDD3C978805C004F786797BDE0DA2695FAA244EC77B22AF17665D5EAF2C5DD8F7EA0F56752356B04DE75250D675B3AD9D645774B374EA27513AC9D44EB275D3AE9D47E0759E73AC976E87221C8C3616510C772F8069F2F8CAB886A173918D8EB2E612F056A683162BE76D3B7ED119036E4A8EC396DA75E07BC57DB60381B12B0966CD580DA0D9D61AE90DEA88F782455AA3C58575DEE2C9A7C3C41D5A788EDE57787D4F98F1AAE567A8E4EB1CE8A30B1B6533483372242781DAFF00D9743673B587D4FE6C7D287055BEF0E36BA15E80358715C8280ED1F1846F5412EAC00FC3DD11835B0CDDE0896CCA9E860640EF58A5701D17FA823E5EDB0E1BCEB9ED2C160D981E0437BA025E05A6614BF70794BE7286DBFD45C45ACB7B73069DB23A0F27EA75E2477E1D79E60E6110B11D1250A34CD2B9DD1BCC7BBE508B987E39FE4B732DCC3F9DE32CFE0478BF03AE5A0E12C80D717A1BC02830691799552CCD6F543831B7E2C6F1B15B83FBBA7BC6A4F965E2CB3A70B488AE1B9F8A3D8EDFF54E3AC408BDAE34A1403EF102BC660B6F04CC0E7D0F7254152D068CA3A56CA4652AB137FD92B8B54BE83B414D62CEE83F87256C01D6006E3AF483686A2460346F78DA9950DD174FE61330535D1AF248925B802163AA1BAA3E07EE5ED5B91EE606077DF4235610EA8AE9692ED869037AB7706DD6BFA7682431D97B11BB903DA634345C433D3B4523290A33811D3FE0F4848A45A42BBC772C4CAB133D5BB2C6DAEC0EF01135557232D4EB3AF58C3787E9356337EFBFC1A9D2E6BCD0C798BF0B51E6BD920F594E65399673290B4B972E0BF81D5FC4FB7F122E5C1972E633901A7699A230C152D420C609A4E9B2900163B0471DCB9E8D0868D39F48AC540E6BEB11A81406020D703167DD2519FE961BCF6ECF9C01B523BE608E737208A0CB2C9A42D402C1A9FAB8B637EA41746F12C1BE3CE5DCD60B3B0026E32F1F810351508E4955CD83B7B0EB159BB6680711379A2B8D0F7A997F328B9106A8EB9774A86E0DDB4BD634C60A8C2291979848082C643AEDEB5038D44E11AACC2A3250E095AFAEF318692F1A5B1294698D788D4DD3AB6CEB1E5E3FC4FC626EA55A30499B05365F88C8E7D64328C8626F589ADCDCF28AAD983C8964D1353534458B316FE20207F0B97F85F5972E5CEE9DDF90712FACBFE01183B9E0860022CCAA5654CB7F894C118399AC01E134F5F6823B483BEB12A1ECC1BA3113583AAB28D986BA2CEE89711EC7986AACAADE1134501CCA530A3F6180DD0D8EFA4C16AB47A3C20D773B4D47530CC906E11EC6D8CB77A79649D4967E04280016AED17AA51603D606B147ADE1E16869E32EE585BBA4AB5E7F9893CD7395AF725DAC74631785B0B8334E9CD1EA4ACCC40D73294AE8BD8CCBBDAEC16AFC42B56B1B246964E35E5AC3593646254634882C192D700E57694BBA408AB565AE2E3280B6DA1397C4CC5CE742B87F72A9792BA1DE5C7241B81DD9D72B6EFCD1D01B071315F0B979A778353DE6D4A26262C63C204D4CB972FF000197065CB972E5CB972E5CB972E0CB972C1541995347B5C1B12BB45F90C48F44B22246B7BC63831FB940E8A15DD8A57354007945AFD6F409C5C0458266406CE8BA995302AE81A039586806FAC032F668D2372C27E68E6DDB12F08815950D8941996F5BF09A3477E810C7CC14034D6572394FBEA4D0BB407486B2EA3071D8BF5DE091C5C1D5DA150618D7D5D012F5DD59FE656F9314C3E751A80F076469854FA0B142C2C9C9C44A2C849596A45C40AE04EF64A1B77775AF881DCAC8900B3BDD4A9046A7CC9EBD4BBD2508276D0F5E0546495AEB5D25CD05C9E24AE322B2E8E398B5519677314441F804C40C0E3FB82CAE23A5774B5F4409E35F315FC4B8FC165AD2ECBE7F172FF172FACBEB2F1165F58F54EF9487698727E27492ADE75277BCA3B23F895A1A375CC1F6C7EC9C320B7807518262236496DCB3773C84B0372A5A3C25D2B780C75C61E504978E07DA62271BB1E647F0809EE233600C45B63A4ADC008718FAAF372C5DFE80F732EEC7C5CC07482A40EACB646EE32450E918B72EF5856552F94DAB11E3AC41E2617736E3D4A375026E1CB2CF6AABB83AEA0FAB06FBD0C01D021FCDD1959D5034B63D43E72FE6A95131354AF27B9155B6912F05BDE607A7BA39651405DC125D756F7F52EC11B10ADE3B654B00E8B122EF525D1A6B0F0B7E2E8E5755882081D6C9759DCD1887584CC229E407C4146C330811C4D41D2515A1BD98AC4DC58F59733EC4A3B1106A09CC7CE0B51E71E4221B2C740859A2F397E848A7106DFD20AD629EAAFE1E9306D982FC4ACA712BC4136943F0A9507E060C22D8CC1CCB1ECC6D94A3225AA3788761324F1B8F785F6B91EB59CB29DA11E736C1020340014DEF79C41F1EC009EFAC5E13800B145EAD0F788BD002AE82542C28D66545375772E760CEAA8FA0CC17A3DE5C8F04CBAC66305B2D5B6A870D899249DA5499AAB812F39257E103A5B729DB3B38DE1FCDD2562614BC41EECC1F35F8703E1D8CBCD8E1D25EF5FD965E1BA12F3750835A9C984AB5DA2A6915EA18CF556254050DC1BA9958395D95BC7582ADD1AE8F7E3BC2210B217AB352E7400BAB2EE23C684814DDE6A22CCCB727B91C255172E9EF0CA07533040D01773036E7454B6EE53CC2DEAB00D3C0823EC19530FE29B0F3315A9F1C1B48AC723C2118F38270CEE61B67B88ADAEE22753EFF00E63F50F15F88BD0F61650C876717A0EDFEA7C327F701A8EC61AE1E04AF57F01F12BD57F7D250C46A1BCE5A6155F4C092BC8F5DC6C2FD88A9B45CB8A178A3056203F493C8E21938ED9778822CC7C52BD330950A93CDB10B5BAD0E4DEF8C2AE0726C3BD7B244065D686398953CA44DC1BA3A5D411D014E33281EB87C0A3DA5AA39A100012A6DAAA1B3EA4A1D8B501AB0B653716F3817C657C5EE4611059556181F942C781D6CE5869A0543532BE3C21FCEFB9A5E5AFD4667D4E056768E06B1812F80976E50B949B49F3C45AE72C8FB8AB0E7926BD6C09D6654564D5968CBCDC2FC2000A4B11DC968A2A782F32E6302B8D25D06740A230168EB9F394E74734047A67463992EA20BF28DC8B654CA6B52DA3C59717AB1F8BF24B080F58E85ABBED189CA29BE70013CE604A7F721191DD583705E6836BF0C21A7E198383F047D07812E7F92CDD963762E4D42F9879877996AB0C37801CF8CAB5078C7D761226C3EFF00BA6907C32FFC8DA6BEFD93E215E89407A27FA843D203E66E7770467B91FA9F3FA8AE8BC565AE3D9BC7B7DEC025ACB64A3CCA15BA896E85D3DA17A942A3F6910B6C3D180940945D66FCE6B60F44358E84A03804D6C8C3A2A5C5114A6674DC265B9122A1A2D788AAACB44F9810136D608826C749D76863A2F0DC0774DD9066B11622775A008D8DF227893B46CC86A6CD7C07AB02A1FCC80EB6801A5AF887215A08FA5C6B626EDE8D4D72B7B10F12E28BD284A60B7D80CD5120B711C4295AD99F68E846B08D30506FB73BC38156AB4B38842D86B0C65F3DE33C200C2A5D794028DAA53D121B8F9A46C5D51772CEE06A5B6F07B42A495B3784E6F89B9501CEB3E6D227B92B838255D84702AB2B8DE1B1ADBC2AEB274C063AACB789F11F8627C444C5FB4935F8051D07654F99A32F727CC41CAF27CC45B69D4913BBDF5E258D3F752B637748A7B32FCC4380ED1E8C466B4BB13E268CBB353507E226B3FC51D437773523DDB8AB95F3803594E655622E0657998E90FC51F8A733F10BB339081107868639AD878E68F892B4DCB47879844601B91E49625E57CC4E045D6D83C75ED515042D4EC9B31C549A866A5ED7608D3178C811763ADD82758A0F1E1E095B258FA196650D183B440D686238BCC36C619B9860EDC423ACEE413AB947F20DA5C3E1461F016CC7B28DDE57ABF83F83B0FE1688942D2278A7E2F2A1B901805DC5976885E64D87BCDCD4AC8ACC5CB4A83C7119A1D0072753860D04987E43661DFE02943DA25730AF609A25E9028AC94CD988DB5F1B388F7888EAC384AD21EA63694F283D3554E83C74DD3C2601A07040A4E5ACB90BEA804D8DE9F9276428D26B71006A4A43D3F8D236E67825E5F896E275931DC97FFACBF1769D027409D027493A49D37E33A71D1F24E87921C5E48F1F9274BC93EC276FCA7549D59D680FF8816921ECF29833E88002AB5592E2B7849C421C8160DC8981809A0FA5312735E28E8E6E65385D1A06C1E11906A5E7A4BCE39B53355F02E72D6C213657D6A5879568C6F7DA06A9AD8FB54A886B04E205813CA6DD3CA152D13B4256137A885ABBC4F23A468BF185920C3F87A0FC2A71A1AD51A6505C0E63C88AA397F1B6C4CA380160E123897321DEE9C4AA9D180788398FB175F5C308ACBA04A788EC7C2626BD115DA22C6273043AC4AE7F37FC9952A54AFF00E17F9A952BF857F03F152A57F0A9514193D91A4CAC852246C1719D4E8CB9387C4F725C2C3913D08E5ECB7D0D275B2545F59AF1DA0C12F5417E36D1A974CD5EEC684731FC1860CDC4E097C4D612D56CA88BF87A0FC0B801D4C3872F48351FB363D2078A1C7997EA585A3BB87FDE807E266F880658519F54297D489B27BD29352F3629FBE2554777306D0659BA5CEB9C6E7DACFB19F4B0FF00BE7D6C4FF3F87D27ED627FEA7DEC43F04F33F1BAA9D64EA275505D17E00BFEE7D6CFAD227FEE259479C66A76FC7D396E87E274A743F1F4C9D283691D445E8E7DAC1FFDCFB99BE9217603B41CA86E62140D2D8DC79435AA5971461BD558822D8E09BC0779B17F28BB0EB623BE75B11A380819308BB1C4C0186AB31841441403FC5E83F0CA5E929B845B5089D4F945354F08B6BE522BF5629AF968EB1FBFEA8B196FA710D431E57E25CE53A1ED1C503D4F6313B41E83F33E9BF33703BFEF8264789EECAEF3A58961FDB89A6EFBF13EE7F10FB5FB4FB5FC4FB9FC4FB5FC4C5F6FCA7DAFE27DAFE27DCFE25FAFDFE93ECFF13ECFF13E9FF138FEFF0049F77F89F73F89F73F89F73F89F73F896EBF4FA47EDFED3EBFF11FB3FB4FB7FC4FB3FC4FAFFC4FAFFC4034FAFD27DEFE27DF7E27DFFE273FD3E93E97F116FAFE92FD7EBF49F7DF89F7DF89F6DF88B7DFF49F77F8966BF4FA4FBDFC45BEDFA4D507D7884D7844F69F71DB862850BE31476D78CFB9157004147DB907B4222BCDD707AEE8BE634370DDBFBFE1040E9F73A4575FA7D23F72F69A13EDFA2692BB7EA9E9BDFA207437049BB076300D07B09A124D10FE3E83F0C3FACFA0FC30FEB3E83FAE7A0FEB8AFB5FD70019828FC9AFF59F41FD73D07F5CF41F87FAD7A0FEB9E83FAE0A07FF00A2868FEB991FD73D07F5CF487FE8BF93FF0023FFD9,
       image_type = 'image/jpeg',
       image_url  = '/api/vehicles/10/image?v=1'
 WHERE vehicle_id = 10;


-- ############################################################################
--  PART 4 — DID IT WORK?
--
--  Every row below should say PASS. If any says *** FAIL ***, something did
--  not run — scroll up in the output for the first error message.
-- ############################################################################

SELECT 'Tables created'        AS check_name, 18 AS expected, COUNT(*) AS found,
       IF(COUNT(*) = 18, 'PASS', '*** FAIL ***') AS result
  FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'vehicle_rental_db'
UNION ALL
SELECT 'Branches',            4,  COUNT(*), IF(COUNT(*) =  4, 'PASS', '*** FAIL ***') FROM branches
UNION ALL
SELECT 'Users',               7,  COUNT(*), IF(COUNT(*) =  7, 'PASS', '*** FAIL ***') FROM users
UNION ALL
SELECT 'Vehicles',           10,  COUNT(*), IF(COUNT(*) = 10, 'PASS', '*** FAIL ***') FROM vehicles
UNION ALL
SELECT 'Insurance policies', 11,  COUNT(*), IF(COUNT(*) = 11, 'PASS', '*** FAIL ***') FROM insurance_policies
UNION ALL
SELECT 'Bookings',            7,  COUNT(*), IF(COUNT(*) =  7, 'PASS', '*** FAIL ***') FROM bookings
UNION ALL
SELECT 'Payments',            7,  COUNT(*), IF(COUNT(*) =  7, 'PASS', '*** FAIL ***') FROM payments
UNION ALL
SELECT 'Handovers',           3,  COUNT(*), IF(COUNT(*) =  3, 'PASS', '*** FAIL ***') FROM handovers
UNION ALL
SELECT 'Service records',     3,  COUNT(*), IF(COUNT(*) =  3, 'PASS', '*** FAIL ***') FROM maintenance_records
UNION ALL
SELECT 'Damage reports',      2,  COUNT(*), IF(COUNT(*) =  2, 'PASS', '*** FAIL ***') FROM damage_reports
UNION ALL
SELECT 'Insurance claims',    1,  COUNT(*), IF(COUNT(*) =  1, 'PASS', '*** FAIL ***') FROM insurance_claims
UNION ALL
SELECT 'Notifications',       7,  COUNT(*), IF(COUNT(*) =  7, 'PASS', '*** FAIL ***') FROM notifications
UNION ALL
SELECT 'Audit entries',       9,  COUNT(*), IF(COUNT(*) =  9, 'PASS', '*** FAIL ***') FROM audit_log
UNION ALL
SELECT 'Saved cars',          2,  COUNT(*), IF(COUNT(*) =  2, 'PASS', '*** FAIL ***') FROM favourites
UNION ALL
SELECT 'Email preferences',   4,  COUNT(*), IF(COUNT(*) =  4, 'PASS', '*** FAIL ***') FROM notification_preferences
UNION ALL
SELECT 'No-show bookings',    1,  COUNT(*), IF(COUNT(*) =  1, 'PASS', '*** FAIL ***') FROM bookings WHERE status = 'NO_SHOW'
UNION ALL
-- Seeded accounts must be able to book straight away.
SELECT 'Emails confirmed',    7,  COUNT(*), IF(COUNT(*) =  7, 'PASS', '*** FAIL ***') FROM users WHERE email_verified = TRUE
UNION ALL
SELECT 'Notifications linked', 7, COUNT(*), IF(COUNT(*) =  7, 'PASS', '*** FAIL ***') FROM notifications WHERE entity_id IS NOT NULL
UNION ALL
SELECT 'Vehicle photos',      2,  COUNT(*), IF(COUNT(*) =  2, 'PASS', '*** FAIL ***')
  FROM vehicles WHERE image_data IS NOT NULL
UNION ALL
-- The check that decides whether the shopfront looks alive: a car with no
-- active cover for today is hidden from customers completely.
SELECT 'Vehicles insured today', 10, COUNT(*), IF(COUNT(*) = 10, 'PASS', '*** FAIL ***')
  FROM vehicles v WHERE EXISTS (
      SELECT 1 FROM insurance_policies p
       WHERE p.vehicle_id = v.vehicle_id AND p.status = 'ACTIVE'
         AND p.start_date <= CURDATE() AND p.expiry_date >= CURDATE())
UNION ALL
SELECT 'Bookings awaiting approval', 1, COUNT(*), IF(COUNT(*) = 1, 'PASS', '*** FAIL ***')
  FROM bookings WHERE status = 'PENDING_APPROVAL';

-- What a customer will actually see on the home page right now.
SELECT v.vehicle_id, v.plate_number, v.model, b.name AS branch,
       v.rental_price_per_day AS per_day, v.status
  FROM vehicles v JOIN branches b ON b.branch_id = v.branch_id
 WHERE v.status = 'AVAILABLE'
 ORDER BY v.rental_price_per_day DESC;

SELECT 'Database ready. Sign in as admin@rental.lk / password123' AS result;

