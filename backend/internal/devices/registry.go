// Package devices manages FCM token registrations from the mobile app.
package devices

import (
	"encoding/json"
	"net/http"
	"time"

	"github.com/scaleminds/grafana-mobile-relay/internal/store"
)

type Registry struct {
	db *store.Store
}

func NewRegistry(db *store.Store) *Registry {
	return &Registry{db: db}
}

type Device struct {
	FCMToken    string `json:"fcm_token"`
	GrafanaURL  string `json:"grafana_url"`
	GrafanaUser string `json:"grafana_user"`
}

// Register upserts a device row. Called by the mobile app on FCM token refresh.
func (r *Registry) Register(d Device) error {
	now := time.Now().Unix()
	_, err := r.db.Exec(`
INSERT INTO devices (fcm_token, grafana_url, grafana_user, created_at, last_seen_at)
VALUES (?, ?, ?, ?, ?)
ON CONFLICT(fcm_token) DO UPDATE SET
    grafana_url = excluded.grafana_url,
    grafana_user = excluded.grafana_user,
    last_seen_at = excluded.last_seen_at
`, d.FCMToken, d.GrafanaURL, d.GrafanaUser, now, now)
	return err
}

// TokensForUser returns all FCM tokens registered for a given Grafana user.
func (r *Registry) TokensForUser(grafanaURL, user string) ([]string, error) {
	rows, err := r.db.Query(
		`SELECT fcm_token FROM devices WHERE grafana_url = ? AND grafana_user = ?`,
		grafanaURL, user,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []string
	for rows.Next() {
		var t string
		if err := rows.Scan(&t); err != nil {
			return nil, err
		}
		out = append(out, t)
	}
	return out, rows.Err()
}

// TokensForInstance returns every token registered against a Grafana URL.
// Used when an alert has no owning user (broadcast to all app installs).
func (r *Registry) TokensForInstance(grafanaURL string) ([]string, error) {
	rows, err := r.db.Query(`SELECT fcm_token FROM devices WHERE grafana_url = ?`, grafanaURL)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []string
	for rows.Next() {
		var t string
		if err := rows.Scan(&t); err != nil {
			return nil, err
		}
		out = append(out, t)
	}
	return out, rows.Err()
}

func (r *Registry) HTTPHandler() http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
		if req.Method != http.MethodPost {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		var d Device
		if err := json.NewDecoder(req.Body).Decode(&d); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		if d.FCMToken == "" || d.GrafanaURL == "" || d.GrafanaUser == "" {
			http.Error(w, "missing fields", http.StatusBadRequest)
			return
		}
		if err := r.Register(d); err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	})
}
