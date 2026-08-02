// Package fcm wraps Firebase Cloud Messaging for the relay.
package fcm

import (
	"context"
	"errors"
	"fmt"

	firebase "firebase.google.com/go/v4"
	"firebase.google.com/go/v4/messaging"
	"google.golang.org/api/option"
)

type Sender struct {
	client *messaging.Client
}

// New returns a Sender. If credsPath is empty, returns a no-op sender that logs.
func New(ctx context.Context, credsPath string) (*Sender, error) {
	if credsPath == "" {
		return &Sender{}, nil
	}
	app, err := firebase.NewApp(ctx, nil, option.WithCredentialsFile(credsPath))
	if err != nil {
		return nil, fmt.Errorf("firebase init: %w", err)
	}
	client, err := app.Messaging(ctx)
	if err != nil {
		return nil, fmt.Errorf("firebase messaging: %w", err)
	}
	return &Sender{client: client}, nil
}

type Notification struct {
	Title string
	Body  string
	Data  map[string]string
}

func (s *Sender) Send(ctx context.Context, tokens []string, n Notification) error {
	if len(tokens) == 0 {
		return nil
	}
	if s.client == nil {
		// no-op mode for local dev without FCM credentials
		fmt.Printf("[fcm noop] would send to %d tokens: %q / %q\n", len(tokens), n.Title, n.Body)
		return nil
	}
	msg := &messaging.MulticastMessage{
		Tokens: tokens,
		Notification: &messaging.Notification{
			Title: n.Title,
			Body:  n.Body,
		},
		Data: n.Data,
	}
	resp, err := s.client.SendEachForMulticast(ctx, msg)
	if err != nil {
		return err
	}
	if resp.FailureCount > 0 {
		return errors.New("some FCM sends failed; see individual responses")
	}
	return nil
}
