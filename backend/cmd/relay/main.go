package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/scaleminds/grafana-mobile-relay/internal/devices"
	"github.com/scaleminds/grafana-mobile-relay/internal/fcm"
	"github.com/scaleminds/grafana-mobile-relay/internal/store"
	"github.com/scaleminds/grafana-mobile-relay/internal/webhook"
)

func main() {
	dbPath := envOr("DB_PATH", "relay.db")
	addr := envOr("LISTEN_ADDR", ":8080")
	fcmCreds := os.Getenv("FCM_CREDENTIALS_JSON") // path to service-account JSON

	db, err := store.Open(dbPath)
	if err != nil {
		log.Fatalf("open store: %v", err)
	}
	defer db.Close()

	sender, err := fcm.New(context.Background(), fcmCreds)
	if err != nil {
		log.Fatalf("fcm init: %v", err)
	}

	registry := devices.NewRegistry(db)
	hook := webhook.NewHandler(registry, sender)

	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	})
	mux.Handle("/v1/devices", registry.HTTPHandler())
	mux.Handle("/v1/webhook/grafana", hook)

	srv := &http.Server{
		Addr:              addr,
		Handler:           mux,
		ReadHeaderTimeout: 10 * time.Second,
	}
	log.Printf("relay listening on %s", addr)
	if err := srv.ListenAndServe(); err != nil {
		log.Fatal(err)
	}
}

func envOr(k, d string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return d
}
