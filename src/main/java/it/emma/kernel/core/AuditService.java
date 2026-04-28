package it.emma.kernel.core;

import java.util.ArrayList;
import java.util.List;

import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import it.emma.kernel.dto.AuditEntry;
import it.emma.kernel.persist.AuditDoc;
import it.emma.kernel.persist.AuditRepo;
import it.emma.kernel.policy.Action;
import it.emma.kernel.policy.Decision;
import it.emma.kernel.policy.PolicyEnforcer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.bind.DatatypeConverter;

@ApplicationScoped
public class AuditService {

  @Inject AuditLog audit;
  @Inject AuditRepo auditRepo;
  @Inject PolicyEnforcer enforcer;

  public void record(String event, String subject, String detail) {
    audit.record(AuditEntry.simple(event, subject, detail));
    auditRepo.persist(AuditDoc.of(event, subject, detail));
  }

  public void recordTransition(String event, String subject, String detail, String fromState, String toState) {
    audit.record(AuditEntry.simple(event, subject, detail));
    auditRepo.persist(AuditDoc.transition(event, subject, detail, fromState, toState));
  }

  public List<AuditDoc> search(String subject, String event, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 200));
    List<AuditDoc> docs = auditRepo.findAll(Sort.descending("ts"))
        .page(Page.of(0, Math.max(safeLimit * 4, safeLimit)))
        .list();
    return filter(docs, subject, event, safeLimit);
  }

  public List<String> tailLines(int n) {
    int safeN = Math.max(1, n);
    List<AuditDoc> docs = auditRepo.findAll(Sort.descending("ts"))
        .page(Page.of(0, safeN))
        .list();

    ArrayList<String> lines = new ArrayList<String>(docs.size());
    for (AuditDoc d : docs) {
      lines.add(formatLine(d));
    }
    java.util.Collections.reverse(lines);

    if (lines.isEmpty()) {
      return fileFallback(safeN);
    }
    return lines;
  }

  private List<AuditDoc> filter(List<AuditDoc> docs, String subject, String event, int limit) {
    ArrayList<AuditDoc> result = new ArrayList<AuditDoc>();
    String subjectFilter = (subject == null || subject.isBlank()) ? null : subject;
    String eventFilter = (event == null || event.isBlank()) ? null : event;

    for (AuditDoc d : docs) {
      if (subjectFilter != null && !subjectFilter.equals(d.subject)) continue;
      if (eventFilter != null && !eventFilter.equalsIgnoreCase(d.event)) continue;
      result.add(d);
      if (result.size() >= limit) break;
    }
    java.util.Collections.reverse(result);
    return result;
  }

  private String formatLine(AuditDoc d) {
    String when = (d.ts != null) ? DatatypeConverter.printDateTime(toCalendar(d.ts)) : "";
    String ev  = (d.event   != null) ? d.event   : "";
    String sub = (d.subject != null) ? d.subject : "";
    String det = (d.detail  != null) ? d.detail  : "";
    return when + " [" + ev + "] " + sub + " - " + det;
  }

  private List<String> fileFallback(int safeN) {
    try {
      java.util.HashMap<String,Object> p = new java.util.HashMap<String,Object>();
      p.put("path", "audit.log");
      Decision dec = enforcer.check(new Action(Action.Type.FS_READ, p));
      if (dec.effect == Decision.Effect.DENY) {
        throw new SecurityException("Policy DENY: " + dec.reason);
      }

      java.nio.file.Path f = java.nio.file.Path.of("audit.log");
      if (!java.nio.file.Files.exists(f)) {
        return new ArrayList<String>();
      }
      List<String> all = java.nio.file.Files.readAllLines(f);
      int from = Math.max(0, all.size() - safeN);
      return new ArrayList<String>(all.subList(from, all.size()));
    } catch (SecurityException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static java.util.Calendar toCalendar(java.util.Date date) {
    java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
    c.setTime(date);
    return c;
  }
}
