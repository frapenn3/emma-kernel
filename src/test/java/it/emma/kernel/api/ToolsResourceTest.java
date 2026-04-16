package it.emma.kernel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import it.emma.kernel.dto.Approval;
import it.emma.kernel.tools.FileTool;
import jakarta.ws.rs.core.Response;

class ToolsResourceTest {

  private ToolsResource resource;
  private StubFileTool files;

  @BeforeEach
  void setUp() throws Exception {
    resource = new ToolsResource();
    files = new StubFileTool();
    inject(resource, "files", files);
  }

  @Test
  void writeReturns400WhenPathIsMissing() {
    ToolsResource.WriteRequest req = new ToolsResource.WriteRequest();
    req.content = "Emma online";

    Response response = resource.write(req);

    assertEquals(400, response.getStatus());
    ToolsResource.ErrorResponse error = assertInstanceOf(ToolsResource.ErrorResponse.class, response.getEntity());
    assertEquals("Missing 'path'", error.error);
  }

  @Test
  void writeReturns423WithReasonWhenToolDenies() {
    files.writeResult.status = "DENY";
    files.writeResult.reason = "explicit approval required";

    ToolsResource.WriteRequest req = new ToolsResource.WriteRequest();
    req.path = "tests/approval.txt";
    req.content = "Emma online";

    Response response = resource.write(req);

    assertEquals(423, response.getStatus());
    ToolsResource.ErrorResponse error = assertInstanceOf(ToolsResource.ErrorResponse.class, response.getEntity());
    assertEquals("explicit approval required", error.error);
  }

  @Test
  void writeReturns423WhenToolThrowsSecurityException() {
    files.writeSecurityException = new SecurityException("path escapes work directory");

    ToolsResource.WriteRequest req = new ToolsResource.WriteRequest();
    req.path = "../escape.txt";
    req.content = "blocked";
    req.approval = Approval.YES;

    Response response = resource.write(req);

    assertEquals(423, response.getStatus());
    ToolsResource.ErrorResponse error = assertInstanceOf(ToolsResource.ErrorResponse.class, response.getEntity());
    assertEquals("path escapes work directory", error.error);
  }

  @Test
  void writeReturns500WhenToolThrowsUnexpectedException() {
    files.writeException = new IllegalStateException("boom");

    ToolsResource.WriteRequest req = new ToolsResource.WriteRequest();
    req.path = "tests/failure.txt";

    Response response = resource.write(req);

    assertEquals(500, response.getStatus());
    ToolsResource.ErrorResponse error = assertInstanceOf(ToolsResource.ErrorResponse.class, response.getEntity());
    assertEquals("java.lang.IllegalStateException: boom", error.error);
  }

  @Test
  void writeReturnsOkPayloadWhenToolSucceeds() {
    files.writeResult.status = "OK";
    files.writeResult.reason = "written";
    files.writeResult.path = "work/tests/ok.txt";
    files.writeResult.bytes = 11L;

    ToolsResource.WriteRequest req = new ToolsResource.WriteRequest();
    req.path = "tests/ok.txt";
    req.content = "Emma online";
    req.approval = Approval.YES;

    Response response = resource.write(req);

    assertEquals(200, response.getStatus());
    FileTool.WriteResult result = assertInstanceOf(FileTool.WriteResult.class, response.getEntity());
    assertEquals("OK", result.status);
    assertEquals("written", result.reason);
    assertEquals("work/tests/ok.txt", result.path);
    assertEquals(11L, result.bytes);
    assertEquals("tests/ok.txt", files.lastWritePath);
    assertEquals("Emma online", files.lastWriteContent);
    assertEquals(Approval.YES, files.lastApproval);
  }

  @Test
  void readReturns400WhenPathIsMissing() {
    Response response = resource.read(" ");

    assertEquals(400, response.getStatus());
    ToolsResource.ErrorResponse error = assertInstanceOf(ToolsResource.ErrorResponse.class, response.getEntity());
    assertEquals("Missing 'path'", error.error);
  }

  @Test
  void readReturns423WhenToolThrowsSecurityException() {
    files.readSecurityException = new SecurityException("Policy DENY: default deny");

    Response response = resource.read("tests/blocked.txt");

    assertEquals(423, response.getStatus());
    ToolsResource.ErrorResponse error = assertInstanceOf(ToolsResource.ErrorResponse.class, response.getEntity());
    assertEquals("Policy DENY: default deny", error.error);
  }

  @Test
  void readReturnsPayloadWhenToolSucceeds() {
    files.readContent = "Emma online";

    Response response = resource.read("tests/hello.txt");

    assertEquals(200, response.getStatus());
    ToolsResource.ReadResult result = assertInstanceOf(ToolsResource.ReadResult.class, response.getEntity());
    assertEquals("tests/hello.txt", result.path);
    assertEquals("Emma online", result.content);
  }

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  static final class StubFileTool extends FileTool {
    final WriteResult writeResult = new WriteResult();
    String readContent;
    String lastWritePath;
    String lastWriteContent;
    Approval lastApproval;
    SecurityException writeSecurityException;
    Exception writeException;
    SecurityException readSecurityException;
    Exception readException;

    @Override
    public WriteResult writeInWork(String relPath, String content, Approval approval) throws Exception {
      lastWritePath = relPath;
      lastWriteContent = content;
      lastApproval = approval;
      if (writeSecurityException != null) throw writeSecurityException;
      if (writeException != null) throw writeException;
      return writeResult;
    }

    @Override
    public String readFromWork(String relPath) throws Exception {
      if (readSecurityException != null) throw readSecurityException;
      if (readException != null) throw readException;
      return readContent;
    }
  }
}
