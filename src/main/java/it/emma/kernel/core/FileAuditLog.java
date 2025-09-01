package it.emma.kernel.core;
import it.emma.kernel.dto.AuditEntry;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.*; import java.nio.file.*;
@ApplicationScoped
public class FileAuditLog implements AuditLog {
  private static final Path LOG = Paths.get("audit.log");
  @Override public void record(AuditEntry e){
    try { Files.writeString(LOG, e.toJson()+"\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
    catch(Exception ex){ ex.printStackTrace(); }
  }
}
