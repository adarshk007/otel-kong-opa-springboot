# Distributed Tracing Demo: Spring Boot (Java 17) + Kong + OPA + OpenTelemetry + Jaeger

This stack shows end‑to‑end distributed tracing through:
- Kong Gateway (OpenTelemetry plugin)
- Spring Boot service (Java 17) with OpenTelemetry Java agent
- OPA (authorization as a service) invoked by the app
- OpenTelemetry Collector (OTLP)
- Jaeger UI for exploring traces

## Quick start

```bash
# 1) Build the app image
docker compose build app

# 2) Start the stack
docker compose up -d

# 3) Hit the API through Kong (goes Kong -> app -> OPA)
curl -i http://localhost:8000/api/hello -H 'X-User: alice'

# Optional unauthorized example (OPA denies)
curl -i http://localhost:8000/api/secure

# 4) Open Jaeger UI
open http://localhost:16686  # (or just visit in your browser)
# Service names to look for: 'kong', 'spring-app'
```

### What you'll see
- Kong generates gateway spans via its OpenTelemetry plugin and **propagates W3C trace context** downstream.
- The Spring Boot app is auto‑instrumented by the OpenTelemetry Java agent and **calls OPA**; that client HTTP call is traced too.
- The OpenTelemetry Collector receives spans from Kong and the app, then exports to Jaeger.

## Layout

```
otel-kong-opa-springboot/
├─ docker-compose.yml
├─ kong/
│  └─ kong.yml                  # DB-less declarative config (service, route, OpenTelemetry plugin)
├─ otel-collector/
│  └─ config.yaml               # Receives OTLP, exports to Jaeger
├─ opa/
│  ├─ policy.rego               # Simple authz policy
│  └─ data.json                 # Example data (roles/users)
└─ app/
   ├─ Dockerfile
   ├─ pom.xml
   └─ src/main/java/com/example/demo/
      ├─ DemoApplication.java
      ├─ web/HelloController.java
      └─ opa/OpaClient.java
```

## Notes

- Kong config uses the **OpenTelemetry plugin** with `header_type = w3c` so it will forward `traceparent` to the upstream app.  
- The Spring app uses the **OpenTelemetry Java Agent** (downloaded in the Dockerfile). No code changes are required for basic tracing.
- The app explicitly calls OPA for authorization **inside the request flow**, so that call shows up as a child span.
- Everything exports traces via OTLP to the **OpenTelemetry Collector**, which forwards to **Jaeger**.

## Tear down
```bash
docker compose down -v
```


```bash
cd otel-kong-opa-springboot
docker compose build app
docker compose up -d

# through Kong (Kong -> app -> OPA). Allowed:
curl -i http://localhost:8000/api/hello -H 'X-User: alice'

# denied example (no X-User on /secure):
curl -i http://localhost:8000/api/secure

# open Jaeger UI to see traces
# http://localhost:16686

```

## 🔎 End-to-end Trace Flow

**When you send a request through Kong, here’s what happens:**

- Kong Gateway receives the request
- OpenTelemetry plugin creates a root span like kong /api/hello.
- Trace context (traceparent, tracestate) headers are injected.
- Spring Boot App receives the request
- OTel Java agent creates a server span GET /api/hello.
- The trace context is continued from Kong.
- Spring Boot calls OPA for authorization
- A child span HTTP POST /v1/data/http/authz/allow appears.
- This shows the request to OPA and its latency.
- OPA evaluates the policy(OPA itself isn’t instrumented, so you don’t see spans inside OPA, but the HTTP call span is there).
- Response bubbles back, trace completes.

## OUTPUT
```
Trace ID: 9f3d1a2c7b4e8f12...

kong /api/hello             [span]
└── spring-app GET /api/hello   [span]
    └── HTTP POST OPA /v1/data/http/authz/allow   [span]
```

## SPAN DETAILS

Span: kong /api/hello
Service: kong
Duration: ~5ms
Attributes:
http.method = GET
http.route = /api/hello
net.peer.ip = 172.x.x.x

Span: GET /api/hello
Service: spring-app
Duration: ~20ms
Attributes:
http.method = GET
http.route = /api/hello
user_agent = curl/7.88
http.status_code = 200

Span: HTTP POST /v1/data/http/authz/allow
Service: spring-app
Kind: CLIENT
Duration: ~3ms
Attributes:
http.method = POST
http.url = http://opa:8181/v1/data/http/authz/allow
http.status_code = 200
