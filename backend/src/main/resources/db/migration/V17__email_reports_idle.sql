-- =====================================================================
-- V17 (Step 43): email, password reset by email, attendance reports, idle detection.
--   email_log               every email the tool tries to send (who, what, sent or failed)
--   password_reset_tokens   one-time "reset your password" links (only a fingerprint is kept)
--   attendance_report_runs  the monthly report to Project Leads is sent once per project per month
--   attendance_idle         every 20 minute idle check and its answer
--   attendance_events       a new kind IDLE (no activity on the screen for a while)
--   permission ATTENDANCE_REPORTS (Super Admin always; can be given to any role)
-- The email password is NOT stored anywhere. It is typed when the backend starts.
-- Never edit this file after it has run.
-- =====================================================================

-- Email and report settings are NOT in the database: they are in config/mail-and-reports.properties.
INSERT INTO system_settings (setting_key, setting_value) VALUES
 ('attendance.idleMinutes', '20')
ON CONFLICT (setting_key) DO NOTHING;

CREATE TABLE email_log (
    id                BIGSERIAL PRIMARY KEY,
    kind              VARCHAR(40)   NOT NULL,
    to_addresses      VARCHAR(2000) NOT NULL,
    subject           VARCHAR(300)  NOT NULL,
    attachment_name   VARCHAR(200),
    status            VARCHAR(20)   NOT NULL CHECK (status IN ('SENT', 'FAILED', 'NOT_SET_UP')),
    error             VARCHAR(500),
    sent_by_user_id   BIGINT REFERENCES users (id),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_log_time ON email_log (created_at DESC);

CREATE TABLE password_reset_tokens (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users (id),
    token_hash    CHAR(64)    NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL,
    used_at       TIMESTAMPTZ,
    requested_ip  VARCHAR(64)
);
CREATE INDEX idx_reset_tokens_user ON password_reset_tokens (user_id, created_at DESC);
CREATE INDEX idx_reset_tokens_ip ON password_reset_tokens (requested_ip, created_at DESC);

CREATE TABLE attendance_report_runs (
    report_month  DATE        NOT NULL,
    project_id    BIGINT      NOT NULL REFERENCES projects (id),
    status        VARCHAR(20) NOT NULL CHECK (status IN ('SENDING', 'SENT', 'FAILED', 'NO_LEADS')),
    attempts      INTEGER     NOT NULL DEFAULT 1,
    last_error    VARCHAR(500),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (report_month, project_id)
);

-- the daily report email (when switched on) is sent once per day
CREATE TABLE attendance_daily_report_runs (
    report_date   DATE        PRIMARY KEY,
    status        VARCHAR(20) NOT NULL CHECK (status IN ('SENDING', 'SENT', 'FAILED')),
    attempts      INTEGER     NOT NULL DEFAULT 1,
    last_error    VARCHAR(500),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- every idle check: from when, until when, and what the person answered
CREATE TABLE attendance_idle (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    day_id      BIGINT      NOT NULL REFERENCES attendance_days (id),
    idle_from   TIMESTAMPTZ NOT NULL,
    idle_to     TIMESTAMPTZ NOT NULL,
    answer      VARCHAR(20) NOT NULL CHECK (answer IN ('WORKING', 'BREAK')),
    break_type  VARCHAR(20) CHECK (break_type IS NULL OR break_type IN ('TEA', 'LUNCH', 'DINNER', 'REFRESHMENT')),
    note        VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_attendance_idle_day ON attendance_idle (day_id);

ALTER TABLE attendance_events DROP CONSTRAINT attendance_events_kind_check;
ALTER TABLE attendance_events ADD CONSTRAINT attendance_events_kind_check
    CHECK (kind IN ('SIGN_IN', 'PUNCH_IN', 'BREAK_START', 'BREAK_END', 'PUNCH_OUT', 'MANAGER_CLOSE', 'IDLE'));

INSERT INTO permissions (code, module, name, description, extendable, sort_order) VALUES
 ('ATTENDANCE_REPORTS', 'Attendance', 'Attendance reports', 'View, download and email daily and monthly attendance reports of people on own projects.', TRUE, 247);

-- Guides for the Knowledge Base
INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES
('forgot-password-email', 'Getting started', 'Forgot your password? Reset it by email',
 'Get a reset link on your email and make a new password yourself.',
 'forgot password reset link email mail gmail outlook new password locked bhool gaya',
 26,
$kb$## Reset your password
1. On the sign-in page, click "Forgot password?".
2. Type your company email and press "Send reset link".
3. Open your email. Click the link "Reset my password". The link works for 30 minutes, one time only.
4. Type a new password two times and press "Save new password".
5. Sign in with the new password.

- Did not get the email? Check the Spam or Junk folder, then ask again after a few minutes.
- You can ask for a link at most 3 times in one hour.

Warning: Never forward the reset email to anyone. Anyone with the link can set your password.

Tip: If email is not working yet, the Super Admin can still give you a temporary password from Team Management.$kb$),
('attendance-reports', 'Reports', 'Daily and monthly attendance reports',
 'Download the attendance report as Excel, or send it by email to anyone in the team.',
 'attendance report daily monthly excel download email send mail login logout break report project lead 1st month',
 27,
$kb$## Open the reports
Go to Attendance and open the "Reports" tab. You see it only if you are allowed to (the Super Admin can give this to any role).

## Daily report
1. Pick a date.
2. Press "Download Excel" to save it, or "Send by email".
- It shows each person's login and logout time, working time, total break, every break with its timing, and alerts.

## Monthly report
1. Pick a month.
2. Press "Download Excel" or "Send by email".
- It shows days present, days not marked, average login and logout time, total and average break, and break over time.

## Send by email
1. Press "Send by email".
2. In "To", type a name or email and pick the person. Add more people if you like.
3. Check the subject and the message. The report is attached by itself.
4. Press "Send".

## Automatic monthly email
On the 1st of every month, the report of the last month is emailed to the Project Manager and Team Leader of each project.

Tip: The Super Admin can change the day, the hour and extra people in the file config/mail-and-reports.properties.$kb$),
('idle-check', 'Getting started', 'Are you there? The 20 minute idle check',
 'After 20 minutes with no mouse or keyboard use on Suma Tracking, the tool asks what you were doing.',
 'idle inactive away screen 20 minutes are you there break prompt activity',
 28,
$kb$## What happens
If you are punched in and there is no mouse or keyboard use for 20 minutes, a box comes up and a sound plays.

## What to press
- "I was working": the time stays as working time.
- "I was on a break": pick the type of break. That time is saved as a break.

- Every idle check is saved, so your manager can see it.
- The Super Admin can change 20 minutes in Settings.

Tip: In Chrome and Edge the tool asks once for permission to notice when you are away from the computer. Allow it. Without it, the tool can only see activity on its own page; if you were working in another program, just press "I was working".$kb$);

INSERT INTO kb_article_versions (article_id, version_no, category, title, summary, body, keywords, status)
SELECT id, 1, category, title, summary, body, keywords, status FROM kb_articles
WHERE slug IN ('forgot-password-email', 'attendance-reports', 'idle-check');
