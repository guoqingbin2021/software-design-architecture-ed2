-- UTC instants stored as DATETIME(6); [open_at,close_at) is left closed and right open.
ALTER TABLE teaching_class
  ADD COLUMN open_at DATETIME(6) NOT NULL DEFAULT '2020-01-01 00:00:00.000000',
  ADD COLUMN close_at DATETIME(6) NOT NULL DEFAULT '2100-01-01 00:00:00.000000',
  ADD CONSTRAINT ck_class_window CHECK (open_at < close_at);
