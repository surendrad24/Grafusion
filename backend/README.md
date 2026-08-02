# Grafana Mobile Push Relay

Receives Grafana alert webhooks and forwards them to registered mobile devices via Firebase Cloud Messaging.

## Env vars

| Var                    | Default    | Purpose                                                   |
|------------------------|------------|-----------------------------------------------------------|
| `LISTEN_ADDR`          | `:8080`    | HTTP bind address                                         |
| `DB_PATH`              | `relay.db` | SQLite file path                                          |
| `FCM_CREDENTIALS_JSON` | *(empty)*  | Path to Firebase service-account JSON. Empty = noop mode. |

## Endpoints

- `POST /v1/devices` — register/refresh an FCM token
  ```json
  { "fcm_token": "...", "grafana_url": "https://grafana.example.com", "grafana_user": "alice" }
  ```
- `POST /v1/webhook/grafana` — configure this URL as a Grafana webhook contact point
- `GET  /healthz`

## Run

```
go run ./cmd/relay
```

## Grafana setup

1. Alerting -> Contact points -> New -> Type: Webhook
2. URL: `https://your-relay.example.com/v1/webhook/grafana`
3. Save, then attach to a notification policy.
