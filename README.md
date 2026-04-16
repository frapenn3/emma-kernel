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
- `POST /kernel/policy/check/quota` (`check` non consuma le quote)
- `GET /kernel/policy/quotas/snapshot`
- `POST /kernel/policy/quotas/reset`
- `POST /kernel/policy/reload`

## Tools file

Per le regole con effetto `ASK`, il runtime usa una policy conservativa: senza consenso esplicito la decisione finale è `DENY`.

Per le regole FS con effetto `ASK`, la scrittura richiede consenso esplicito e in caso di rifiuto l'API restituisce `423 Locked` con il motivo nel payload.

`POST /kernel/tools/write`:

```json
{
  "path": "hello/hello.txt",
  "content": "Emma online",
  "approval": "YES"
}
```

Esempio risposta di rifiuto:

```json
{
  "code": "APPROVAL_REQUIRED",
  "error": "explicit approval required"
}
```

## Note API

- `POST /kernel/policy/check/quota` valuta i limiti senza incrementare i contatori.
- `POST /kernel/tools/write` e i controlli runtime di rete/quota richiedono `approval` esplicito quando una regola restituisce `ASK`.
- I payload errore espongono anche `code` per distinguere casi come `APPROVAL_REQUIRED`, `APPROVAL_REJECTED`, `POLICY_DENIED`, `KERNEL_STOPPED`.
