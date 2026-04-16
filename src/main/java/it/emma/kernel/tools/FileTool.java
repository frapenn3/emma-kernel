package it.emma.kernel.tools;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import it.emma.kernel.core.KernelGuard;
import it.emma.kernel.core.WorkDirs;
import it.emma.kernel.dto.Approval;
import it.emma.kernel.policy.Action;
import it.emma.kernel.policy.Decision;
import it.emma.kernel.policy.PolicyEnforcer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FileTool {

  @Inject PolicyEnforcer enforcer;
  @Inject KernelGuard    guard;
  @Inject WorkDirs       dirs;

  public static final class WriteResult {
    public String path;
    public long bytes;
    public String status; // "OK" | "DENY"
    public String reason;
  }

  /** Scrive content in work/relPath (crea sottocartelle). Policy: FS_WRITE. */
  public WriteResult writeInWork(String relPath, String content) throws Exception {
    return writeInWork(relPath, content, null);
  }

  /** Scrive content in work/relPath (crea sottocartelle). Policy: FS_WRITE + consenso esplicito. */
  public WriteResult writeInWork(String relPath, String content, Approval approval) throws Exception {
    WriteResult r = new WriteResult();
    if (guard.isStopped()) {
      r.status = "DENY";
      r.path = relPath;
      r.reason = "kernel stopped by killswitch";
      return r;
    }

    ResolvedWorkPath resolved;
    try {
      resolved = resolveWorkPath(relPath);
    } catch (SecurityException se) {
      r.status = "DENY";
      r.path = relPath;
      r.reason = se.getMessage();
      return r;
    }
    java.nio.file.Path target = resolved.absolutePath;
    // Policy check
    HashMap<String,Object> p = new HashMap<String,Object>();
    p.put("path", resolved.policyPath);
    p.put("subject", "filetool:write");
    p.put("require_explicit_approval", true);
    if (approval != null) p.put("approval", approval.name());
    Decision d = enforcer.check(new Action(Action.Type.FS_WRITE, p));
    if (d.effect == Decision.Effect.DENY) {
      r.status = "DENY";
      r.path = target.toString();
      r.reason = d.reason;
      return r;
    }

    java.nio.file.Path parent = target.getParent();
    if (parent != null && !java.nio.file.Files.exists(parent)) {
      java.nio.file.Files.createDirectories(parent);
    }

    byte[] data = (content != null ? content : "").getBytes(StandardCharsets.UTF_8);
    java.nio.file.Files.write(target, data,
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

    r.status = "OK";
    r.path = target.toString();
    r.bytes = data.length;
    r.reason = "written";
    return r;
  }

  /** Legge un file da work/relPath. Policy: FS_READ. */
  public String readFromWork(String relPath) throws Exception {
    if (guard.isStopped()) throw new IllegalStateException("Kernel stopped by killswitch");

    ResolvedWorkPath resolved = resolveWorkPath(relPath);
    java.nio.file.Path target = resolved.absolutePath;
    HashMap<String,Object> p = new HashMap<String,Object>();
    p.put("path", resolved.policyPath);
    Decision d = enforcer.check(new Action(Action.Type.FS_READ, p));
    if (d.effect == Decision.Effect.DENY) {
      throw new SecurityException("Policy DENY: " + d.reason);
    }
    if (!java.nio.file.Files.exists(target)) return null;
    byte[] data = java.nio.file.Files.readAllBytes(target);
    return new String(data, StandardCharsets.UTF_8);
  }

  private ResolvedWorkPath resolveWorkPath(String relPath) {
    java.nio.file.Path workRoot = dirs.work().toAbsolutePath().normalize();
    java.nio.file.Path target = workRoot.resolve(relPath).normalize();
    if (!target.startsWith(workRoot)) {
      throw new SecurityException("path escapes work directory");
    }
    java.nio.file.Path relative = workRoot.relativize(target);
    return new ResolvedWorkPath(target, "work/" + relative.toString().replace('\\', '/'));
  }

  private static final class ResolvedWorkPath {
    final java.nio.file.Path absolutePath;
    final String policyPath;

    ResolvedWorkPath(java.nio.file.Path absolutePath, String policyPath) {
      this.absolutePath = absolutePath;
      this.policyPath = policyPath;
    }
  }
}
