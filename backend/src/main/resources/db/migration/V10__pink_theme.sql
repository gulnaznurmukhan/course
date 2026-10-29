-- Third interface theme ("pink") next to light and dark.
ALTER TABLE users DROP CONSTRAINT users_theme_check;
ALTER TABLE users ADD CONSTRAINT users_theme_check CHECK (theme IN ('LIGHT', 'DARK', 'PINK', 'SYSTEM'));
