package it.emma.kernel.api;

import it.emma.kernel.tools.FileTool;
import it.emma.kernel.dto.Approval;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/kernel/tools")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ToolsResource {

  @Inject FileTool files;

  public static final class WriteRequest {
    public String path;     // es: "hello/hello.txt"
    public String content;  // es: "Emma online"
    public Approval approval; // YES/NO esplicito per policy ASK
  }

  // POST /kernel/tools/write  body: {"path":"hello.txt","content":"Emma online"}
  @POST
  @Path("/write")
  public Response write(WriteRequest req) {
    try {
      if (req == null || req.path == null || req.path.isBlank()) {
        return Response.status(400).entity(new ErrorResponse("MISSING_PATH", "Missing 'path'")).build();
      }
      FileTool.WriteResult r = files.writeInWork(req.path, req.content, req.approval);
      if (!"OK".equals(r.status)) {
        return Response.status(423).entity(new ErrorResponse(codeForReason(r.reason), r.reason)).build();
      }
      return Response.ok(r).build();
    } catch (SecurityException se) {
      return Response.status(423).entity(new ErrorResponse(codeForReason(se.getMessage()), se.getMessage())).build();
    } catch (Exception e) {
      return Response.status(500).entity(new ErrorResponse("INTERNAL_ERROR", e.toString())).build();
    }
  }

  // GET /kernel/tools/read?path=hello.txt
  @GET
  @Path("/read")
  public Response read(@QueryParam("path") String path) {
    try {
      if (path == null || path.isBlank()) {
        return Response.status(400).entity(new ErrorResponse("MISSING_PATH", "Missing 'path'")).build();
      }
      String data = files.readFromWork(path);
      return Response.ok(new ReadResult(path, data)).build();
    } catch (SecurityException se) {
      return Response.status(423).entity(new ErrorResponse(codeForReason(se.getMessage()), se.getMessage())).build();
    } catch (Exception e) {
      return Response.status(500).entity(new ErrorResponse("INTERNAL_ERROR", e.toString())).build();
    }
  }

  private static String codeForReason(String reason) {
    if (reason == null || reason.isBlank()) return "POLICY_DENIED";
    if ("explicit approval required".equalsIgnoreCase(reason)) return "APPROVAL_REQUIRED";
    if ("rejected by consent gate".equalsIgnoreCase(reason)) return "APPROVAL_REJECTED";
    if ("path escapes work directory".equalsIgnoreCase(reason)) return "PATH_OUTSIDE_WORKDIR";
    if (reason.startsWith("Policy DENY:")) return "POLICY_DENIED";
    return "POLICY_DENIED";
  }

  // DTO risposta/error
  public static final class ReadResult {
    public String path;
    public String content;
    public ReadResult() {}
    public ReadResult(String path, String content) { this.path = path; this.content = content; }
  }
  public static final class ErrorResponse {
    public String code;
    public String error;
    public ErrorResponse() {}
    public ErrorResponse(String error) { this.error = error; }
    public ErrorResponse(String code, String error) { this.code = code; this.error = error; }
  }
}
