package it.emma.kernel.net;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.time.Duration;
import java.util.Map;

import it.emma.kernel.policy.Action;
import it.emma.kernel.policy.Decision;
import it.emma.kernel.policy.PolicyEnforcer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Wrapper di HttpClient che applica automaticamente:
 *  - Policy di rete (NET_CONNECT) prima della connessione
 *  - Quota net_requests (QUOTA_CONSUME) prima dell'invio
 * 
 * In caso di DENY solleva PolicyDeniedException.
 * In caso di ASK la decisione passa dal ConsentGate tramite PolicyEnforcer.
 */
@ApplicationScoped
public class PolicyHttpClient {

  @Inject PolicyEnforcer enforcer;

  private final HttpClient client;

  public PolicyHttpClient() {
    this.client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  // ---------- Convenience APIs ----------

  public HttpResponse<String> get(String url) throws IOException, InterruptedException {
    return get(url, null);
  }

  public HttpResponse<String> get(String url, Map<String,String> headers)
      throws IOException, InterruptedException {
    HttpRequest.Builder b = baseRequest(url, headers);
    HttpRequest req = b.GET().build();
    return send(req, HttpResponse.BodyHandlers.ofString());
  }

  public HttpResponse<String> postJson(String url, String json)
      throws IOException, InterruptedException {
    return postJson(url, json, null);
  }

  public HttpResponse<String> postJson(String url, String json, Map<String,String> headers)
      throws IOException, InterruptedException {
    HttpRequest.Builder b = baseRequest(url, headers);
    if (headers == null || !headersContainsIgnoreCase(headers, "Content-Type")) {
      b.header("Content-Type", "application/json");
    }
    HttpRequest req = b
        .POST(HttpRequest.BodyPublishers.ofString(json != null ? json : ""))
        .build();
    return send(req, HttpResponse.BodyHandlers.ofString());
  }

  /**
   * Metodo generico: applica policy + quota, poi invia con l'handler indicato.
   */
  public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> handler)
      throws IOException, InterruptedException {

    URI uri = request.uri();
    String host = (uri.getHost() != null) ? uri.getHost() : "";
    Integer port = Integer.valueOf(resolvePort(uri));

    // 1) Policy rete: NET_CONNECT
    java.util.HashMap<String,Object> pNet = new java.util.HashMap<String,Object>();
    pNet.put("host", host);
    pNet.put("port", port);
    pNet.put("subject", "policy-http-client:net-connect");
    pNet.put("require_explicit_approval", true);
    Decision d1 = enforcer.check(new Action(Action.Type.NET_CONNECT, pNet));
    if (d1.effect == Decision.Effect.DENY) {
      throw new PolicyDeniedException("Network policy DENY: " + d1.reason);
    }

    // 2) Quota: net_requests +1
    java.util.HashMap<String,Object> pQuota = new java.util.HashMap<String,Object>();
    pQuota.put("subject", "policy-http-client:quota");
    pQuota.put("net_requests", Integer.valueOf(1));
    pQuota.put("require_explicit_approval", true);
    Decision d2 = enforcer.check(new Action(Action.Type.QUOTA_CONSUME, pQuota));
    if (d2.effect == Decision.Effect.DENY) {
      throw new PolicyDeniedException("Quota DENY: " + d2.reason);
    }

    // 3) Invia realmente
    return client.send(request, handler);
  }

  // ---------- Helpers ----------

  private HttpRequest.Builder baseRequest(String url, Map<String,String> headers) {
    HttpRequest.Builder b = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(30));
    if (headers != null) {
      for (Map.Entry<String,String> e : headers.entrySet()) {
        if (e.getKey() != null && e.getValue() != null) {
          b.header(e.getKey(), e.getValue());
        }
      }
    }
    return b;
  }

  private static int resolvePort(URI uri) {
    int port = uri.getPort();
    if (port > 0) return port;
    String scheme = uri.getScheme();
    if ("https".equalsIgnoreCase(scheme)) return 443;
    if ("http".equalsIgnoreCase(scheme))  return 80;
    return 0; // sconosciuto: va comunque in NET_CONNECT con 0
  }

  private static boolean headersContainsIgnoreCase(Map<String,String> headers, String key){
    if (headers == null || key == null) return false;
    for (String k : headers.keySet()) {
      if (k != null && k.equalsIgnoreCase(key)) return true;
    }
    return false;
  }

  // Eccezione specifica per blocchi policy/quota
  public static final class PolicyDeniedException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public PolicyDeniedException(String msg) { super(msg); }
  }
}
