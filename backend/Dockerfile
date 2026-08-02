FROM golang:1.22-alpine AS build
WORKDIR /src
RUN apk add --no-cache gcc musl-dev
COPY go.mod go.sum* ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=1 go build -o /out/relay ./cmd/relay

FROM alpine:3.20
RUN apk add --no-cache ca-certificates sqlite-libs
COPY --from=build /out/relay /usr/local/bin/relay
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/relay"]
