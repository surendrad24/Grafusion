// Package store is a thin SQLite wrapper for the relay's persistent state:
// device registrations and (later) per-user alert subscriptions.
package store

import (
	"database/sql"

	_ "github.com/mattn/go-sqlite3"
)

type Store struct {
	*sql.DB
}

func Open(path string) (*Store, error) {
	db, err := sql.Open("sqlite3", path+"?_journal=WAL&_busy_timeout=5000")
	if err != nil {
		return nil, err
	}
	if err := migrate(db); err != nil {
		_ = db.Close()
		return nil, err
	}
	return &Store{DB: db}, nil
}

func migrate(db *sql.DB) error {
	_, err := db.Exec(`
CREATE TABLE IF NOT EXISTS devices (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    fcm_token     TEXT    NOT NULL UNIQUE,
    grafana_url   TEXT    NOT NULL,
    grafana_user  TEXT    NOT NULL,
    created_at    INTEGER NOT NULL,
    last_seen_at  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_devices_user ON devices(grafana_url, grafana_user);
`)
	return err
}
