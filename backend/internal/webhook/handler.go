// Package webhook receives Grafana alert webhook POSTs and fans them out to
// mobile devices via FCM.
package webhook

import (
	"context"
	"encoding/json"
	"net/http"
	"time"

	"github.com/scaleminds/grafana-mobile-relay/internal/devices"
	"github.com/scaleminds/grafana-mobile-relay/internal/fcm"
)

type Handler struct {
	registry *devices.Registry
	sender   *fcm.Sender
}

func NewHandler(r *devices.Registry, s *fcm.Sender) *Handler {
	return &Handler{registry: r, sender: s}
}

// Payload matches Grafana's unified-alerting webhook contact-point payload.
// Only fields the relay currently uses are declared.
type Payload struct {
	Receiver    string  `json:"receiver"`
	Status      string  `json:"status"`
	Alerts      []Alert `json:"alerts"`
	GroupLabels map[string]string `json:"groupLabels"`
	ExternalURL string  `json:"externalURL"`
}

type Alert struct {
	Status       string            `json:"status"`
	Labels       map[string]string `json:"labels"`
	Annotations  map[string]string `json:"annotations"`
	StartsAt     time.Time         `json:"startsAt"`
	EndsAt       time.Time         `json:"endsAt"`
	GeneratorURL string            `json:"generatorURL"`
	Fingerprint  string            `json:"fingerprint"`
}

func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var p Payload
	if err := json.NewDecoder(r.Body).Decode(&p); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	tokens, err := h.registry.TokensForInstance(p.ExternalURL)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	title, body := summarize(p)
	notif := fcm.Notification{
		Title: title,
		Body:  body,
		Data: map[string]string{
			"status":      p.Status,
			"externalURL": p.ExternalURL,
			"receiver":    p.Receiver,
		},
	}

	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
	defer cancel()
	if err := h.sender.Send(ctx, tokens, notif); err != nil {
		http.Error(w, err.Error(), http.StatusBadGateway)
		return
	}
	w.WriteHeader(http.StatusAccepted)
}

func summarize(p Payload) (string, string) {
	title := "Grafana alert"
	if name, ok := p.GroupLabels["alertname"]; ok {
		title = name
	}
	body := p.Status
	if len(p.Alerts) > 0 {
		if s, ok := p.Alerts[0].Annotations["summary"]; ok && s != "" {
			body = s
		}
	}
	return title, body
}
