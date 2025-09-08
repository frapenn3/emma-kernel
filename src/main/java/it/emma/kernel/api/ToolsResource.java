package it.emma.kernel.api;

import it.emma.kernel.tools.FileTool;
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
  }

  // POST /kernel/tools/write  body: {"path":"hello.txt","content":"Emma online"}
  @POST
  @Path("/write")
  public Response write(WriteRequest req) {
    try {
      if (req == null || req.path == null || req.path.isBlank()) {
        return Response.status(400).entity(new ErrorResponse("Missing 'path'")).build();
      }
      FileTool.WriteResult r = files.writeInWork(req.path, req.content);
      if (!"OK".equals(r.status)) {
        return Response.status(423).entity(new ErrorResponse("Policy DENY or kernel stopped")).build();
      }
      return Response.ok(r).build();
    } catch (SecurityException se) {
      return Response.status(423).entity(new ErrorResponse(se.getMessage())).build();
    } catch (Exception e) {
      return Response.status(500).entity(new ErrorResponse(e.toString())).build();
    }
  }

  // GET /kernel/tools/read?path=hello.txt
  @GET
  @Path("/read")
  public Response read(@QueryParam("path") String path) {
    try {
      if (path == null || path.isBlank()) {
        return Response.status(400).entity(new ErrorResponse("Missing 'path'")).build();
      }
      String data = files.readFromWork(path);
      return Response.ok(new ReadResult(path, data)).build();
    } catch (SecurityException se) {
      return Response.status(423).entity(new ErrorResponse(se.getMessage())).build();
    } catch (Exception e) {
      return Response.status(500).entity(new ErrorResponse(e.toString())).build();
    }
  }

  // DTO risposta/error
  public static final class ReadResult {
    public String path;
    public String content;
    public ReadResult() {}
    public ReadResult(String path, String content) { this.path = path; this.content = content; }
  }
  public static final class ErrorResponse {
    public String error;
    public ErrorResponse() {}
    public ErrorResponse(String error) { this.error = error; }
  }
}
