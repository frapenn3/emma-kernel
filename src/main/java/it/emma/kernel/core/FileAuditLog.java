package it.emma.kernel.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

import org.jboss.logging.Logger;

import it.emma.kernel.dto.AuditEntry;
import it.emma.kernel.policy.Action;
import it.emma.kernel.policy.Decision;
import it.emma.kernel.policy.PolicyEnforcer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * AuditLog che scrive su file di testo locale (audit.log).
 * Integra la policy: controlla FS_WRITE sul path prima della scrittura.
 * In caso di DENY, evita la scrittura ma non lancia eccezioni (best-effort).
 */
@ApplicationScoped
public class FileAuditLog implements AuditLog {
  private static final Logger LOG = Logger.getLogger(FileAuditLog.class);

  private static final String AUDIT_FILE = "audit.log";

  private static final DateTimeFormatter ISO_INSTANT =
      DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

  @Inject
  PolicyEnforcer enforcer;

  @Override
  public synchronized void record(AuditEntry entry) {
    if (entry == null) return;

    // 1) Policy FS_WRITE
    HashMap<String, Object> params = new HashMap<>();
    params.put("path", AUDIT_FILE);
    Decision dec = enforcer.check(new Action(Action.Type.FS_WRITE, params));
    if (dec.effect == Decision.Effect.DENY) {
      LOG.warnf("Audit write denied: %s (skip write)", dec.reason);
      return;
    }

    // 2) Format riga audit
    String line = formatLine(entry);

    // 3) Append su file (crea se non esiste)
    try {
      java.nio.file.Path p = java.nio.file.Path.of(AUDIT_FILE);
      java.nio.file.Files.createDirectories(p.getParent() == null
          ? java.nio.file.Path.of(".") : p.getParent());

      java.nio.file.Files.writeString(
          p,
          line + System.lineSeparator(),
          StandardCharsets.UTF_8,
          java.nio.file.StandardOpenOption.CREATE,
          java.nio.file.StandardOpenOption.APPEND
      );
    } catch (IOException ioe) {
      LOG.errorf(ioe, "Audit file write failed");
    }
  }

  private String formatLine(AuditEntry e) {
	    // ts è opzionale nel tuo DTO, se manca lo genero ora
	    String ts = (e.ts != null && !e.ts.isEmpty())
	        ? e.ts
	        : ISO_INSTANT.format(Instant.now());

	    String ev  = safe(e.phase);           // era: e.event
	    String sub = safe(e.improvement_id);  // era: e.subject
	    String det = safe(e.detail);
	    return ts + " [" + ev + "] " + sub + " - " + det;
	  }

	  private static String safe(String s){
	    return (s == null) ? "" : s;
	  }
}
