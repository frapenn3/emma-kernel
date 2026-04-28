# Emma Kernel

Backend Quarkus per gestione proposte, policy e strumenti kernel.

## Run (dev)

```bash
mvn quarkus:dev
```

Su un PC temporaneo senza JDK 21/Mongo locale configurati, usa il runtime isolato nel progetto:

```powershell
powershell -ExecutionPolicy Bypass -File .\.codex\start-dev.ps1
```

Questo avvia:

- JDK 21 portatile da `.codex/jdks/jdk-21`
- Maven con settings locale `.codex/maven-settings.xml`
- Mongo in-memory su `localhost:27017`
- Quarkus dev su `http://localhost:8080`

Sul PC fisso, se Mongo locale e JDK 21 sono già configurati, puoi continuare a usare il normale `mvn quarkus:dev`.

Console:

- `http://localhost:8080/console/`

Test con runtime isolato:

```powershell
powershell -ExecutionPolicy Bypass -File .\.codex\mvn-jdk21.ps1 -q test
```

## Endpoint principali

- `GET /kernel/status`
- `POST /kernel/proposals`
- `GET /kernel/proposals`
- `GET /kernel/proposals?state=PROPOSE`
- `GET /kernel/proposals/{id}`
- `POST /kernel/proposals/{id}/approve` (body: `"YES"` o `"NO"`)
- `POST /kernel/proposals/{id}/advance` (body: `{"target":"BUILD"}`)
- `POST /kernel/proposals/{id}/revert`
- `POST /kernel/proposals/{id}/complete`
- `POST /kernel/killswitch`
- `POST /kernel/resume`
- `GET /kernel/audit?subject=...&event=...&limit=50`
- `GET /kernel/proposals/{id}/audit`
- `GET /kernel/audit/tail?n=20`

## Proposal lifecycle

Transizioni consentite:

```text
PROPOSE -> RESEARCH | REVERT
RESEARCH -> BUILD | REVIEW | REVERT
BUILD -> TEST | REVIEW | REVERT
TEST -> REVIEW | REVERT
REVIEW -> APPLY_WAIT | REVERT
APPLY_WAIT -> APPLY | REVERT
APPLY -> MONITOR | REVERT
MONITOR -> DONE | REVERT
REVERT -> DONE
```

Le transizioni non consentite restituiscono `409 INVALID_TRANSITION`.

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
