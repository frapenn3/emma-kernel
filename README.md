# Emma Kernel

Backend Quarkus per gestione proposte, policy e strumenti kernel.

## Run (dev)

```bash
mvn quarkus:dev
```

## Endpoint principali

- `GET /kernel/status`
- `POST /kernel/proposals`
- `POST /kernel/proposals/{id}/approve` (body: `"YES"` o `"NO"`)
- `POST /kernel/killswitch`
- `POST /kernel/resume`
- `GET /kernel/audit/tail?n=20`

## Policy

- `GET /kernel/policy/check/fs?op=READ&path=work/a.txt`
- `GET /kernel/policy/check/net?op=CONNECT&host=localhost&port=8080`
- `POST /kernel/policy/check/quota`
- `POST /kernel/policy/reload`

## Tools file

Per le regole FS con effetto `ASK`, la scrittura richiede consenso esplicito.

`POST /kernel/tools/write`:

```json
{
  "path": "hello/hello.txt",
  "content": "Emma online",
  "approval": "YES"
}
```
