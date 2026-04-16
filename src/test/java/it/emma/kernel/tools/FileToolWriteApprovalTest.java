package it.emma.kernel.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import it.emma.kernel.core.ConsentGate;
import it.emma.kernel.core.KernelGuard;
import it.emma.kernel.core.WorkDirs;
import it.emma.kernel.dto.Approval;
import it.emma.kernel.dto.ProposalSummary;
import it.emma.kernel.policy.PolicyEnforcer;

class FileToolWriteApprovalTest {

  private final Path writtenFile = Path.of("work", "tests", "approval-write.txt");
  private FileTool fileTool;

  @BeforeEach
  void setUp() throws Exception {
    PolicyEnforcer enforcer = new PolicyEnforcer();
    inject(enforcer, "consentGate", new DirectConsentGate());
    invokeNoArg(enforcer, "init");

    WorkDirs dirs = new WorkDirs();
    invokeNoArg(dirs, "init");

    fileTool = new FileTool();
    inject(fileTool, "enforcer", enforcer);
    inject(fileTool, "guard", new KernelGuard());
    inject(fileTool, "dirs", dirs);

    Files.deleteIfExists(writtenFile);
  }

  @AfterEach
  void tearDown() throws Exception {
    Files.deleteIfExists(writtenFile);
  }

  @Test
  void writeWithoutApprovalIsDenied() throws Exception {
    FileTool.WriteResult result = fileTool.writeInWork("tests/approval-write.txt", "denied", null);

    assertEquals("DENY", result.status);
    assertEquals("explicit approval required", result.reason);
    assertFalse(Files.exists(writtenFile));
  }

  @Test
  void writeWithApprovalYesSucceeds() throws Exception {
    FileTool.WriteResult result = fileTool.writeInWork("tests/approval-write.txt", "Emma online", Approval.YES);

    assertEquals("OK", result.status);
    assertEquals("written", result.reason);
    assertTrue(Files.exists(writtenFile));
    assertEquals("Emma online", Files.readString(writtenFile, StandardCharsets.UTF_8));
  }

  @Test
  void writeWithApprovalNoIsDenied() throws Exception {
    FileTool.WriteResult result = fileTool.writeInWork("tests/approval-write.txt", "blocked", Approval.NO);

    assertEquals("DENY", result.status);
    assertEquals("rejected by consent gate", result.reason);
    assertFalse(Files.exists(writtenFile));
  }

  @Test
  void writeOutsideWorkIsDeniedEvenWithApproval() throws Exception {
    FileTool.WriteResult result = fileTool.writeInWork("../escape.txt", "blocked", Approval.YES);

    assertEquals("DENY", result.status);
    assertEquals("path escapes work directory", result.reason);
    assertFalse(Files.exists(Path.of("escape.txt")));
  }

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static void invokeNoArg(Object target, String methodName) throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName);
    method.setAccessible(true);
    method.invoke(target);
  }

  static final class DirectConsentGate extends ConsentGate {
    @Override
    public Approval request(String id, ProposalSummary summary, Approval requested) {
      return requested != null ? requested : Approval.YES;
    }
  }
}
