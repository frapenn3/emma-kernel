package it.emma.kernel.api;

import java.util.HashMap;

import it.emma.kernel.policy.Action;
import it.emma.kernel.policy.Decision;
import it.emma.kernel.policy.PolicyEnforcer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/kernel/policy")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class PolicyResource {

  @Inject PolicyEnforcer enforcer;

  public static final class CheckResult {
    public String effect;
    public String reason;
    public CheckResult() {}
    public CheckResult(String effect, String reason){ this.effect = effect; this.reason = reason; }
  }

  // Esempio: GET /kernel/policy/check/fs?op=READ&path=./work/a.txt
  @GET
  @Path("/check/fs")
  public Response checkFs(
      @QueryParam("op") @DefaultValue("READ") String op,
      @QueryParam("path") String path) {

    if (path == null || path.isBlank()) {
      return Response.status(400).entity(new ErrorResponse("Missing 'path'")).build();
    }

    Action.Type t;
    if ("READ".equalsIgnoreCase(op))      t = Action.Type.FS_READ;
    else if ("WRITE".equalsIgnoreCase(op)) t = Action.Type.FS_WRITE;
    else if ("DELETE".equalsIgnoreCase(op))t = Action.Type.FS_DELETE;
    else return Response.status(400).entity(new ErrorResponse("Invalid 'op' (READ|WRITE|DELETE)")).build();

    HashMap<String,Object> p = new HashMap<String,Object>();
    p.put("path", path);
    Decision d = enforcer.check(new Action(t, p));
    return Response.ok(new CheckResult(d.effect.name(), d.reason)).build();
  }

  // Esempio: GET /kernel/policy/check/net?op=CONNECT&host=localhost&port=27017
  @GET
  @Path("/check/net")
  public Response checkNet(
      @QueryParam("op") @DefaultValue("CONNECT") String op,
      @QueryParam("host") String host,
      @QueryParam("port") Integer port) {

    if (host == null || host.isBlank()) {
      return Response.status(400).entity(new ErrorResponse("Missing 'host'")).build();
    }

    Action.Type t;
    if ("CONNECT".equalsIgnoreCase(op)) t = Action.Type.NET_CONNECT;
    else if ("DNS".equalsIgnoreCase(op)) t = Action.Type.NET_DNS;
    else return Response.status(400).entity(new ErrorResponse("Invalid 'op' (CONNECT|DNS)")).build();

    HashMap<String,Object> p = new HashMap<String,Object>();
    p.put("host", host);
    if (port != null) p.put("port", port);

    Decision d = enforcer.check(new Action(t, p));
    return Response.ok(new CheckResult(d.effect.name(), d.reason)).build();
  }

  // Esempio: POST /kernel/policy/check/quota  body: {"net_requests":1}
  public static final class QuotaConsume {
    public Integer net_requests;
    public Integer cpu_cores;
    public Integer time_min;
  }

  @POST
  @Path("/check/quota")
  public Response checkQuota(QuotaConsume consume){
    HashMap<String,Object> p = new HashMap<String,Object>();
    if (consume != null) {
      if (consume.net_requests != null) p.put("net_requests", consume.net_requests);
      if (consume.cpu_cores   != null) p.put("cpu_cores",   consume.cpu_cores);
      if (consume.time_min    != null) p.put("time_min",    consume.time_min);
    }
    Decision d = enforcer.check(new Action(Action.Type.QUOTA_CHECK, p));
    return Response.ok(new CheckResult(d.effect.name(), d.reason)).build();
  }
  
  @POST
  @Path("/reload")
  public Response reload() {
    enforcer.reload();
    return Response.ok(new CheckResult("OK", "policy reloaded")).build();
  }
  
  @GET
  @Path("/quotas/snapshot")
  public Response quotasSnapshot() {
    return Response.ok(enforcer.getQuotaTracker().snapshot()).build();
  }

  @POST
  @Path("/quotas/reset")
  public Response quotasReset() {
    enforcer.getQuotaTracker().reset();
    return Response.ok().build();
  }


  // Reuse dall’altro resource
  public static final class ErrorResponse {
    public String error;
    public ErrorResponse() {}
    public ErrorResponse(String error) { this.error = error; }
  }
}
