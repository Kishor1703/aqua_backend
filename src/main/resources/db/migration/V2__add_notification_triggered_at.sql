-- Tracks whether the automatic pre-release notification job (Module 5)
-- has already fired for a schedule, so it never double-sends.
ALTER TABLE water_schedules
    ADD COLUMN IF NOT EXISTS notification_triggered_at TIMESTAMPTZ;
