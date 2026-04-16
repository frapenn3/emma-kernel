package it.emma.kernel.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import it.emma.kernel.policy.Action;
import it.emma.kernel.policy.Decision;
import it.emma.kernel.policy.PolicyEnforcer;

class PolicyHttpClientTest {

  private PolicyHttpClient client;
  private RecordingPolicyEnforcer enforcer;

  @BeforeEach
  void setUp() throws Exception {
    client = new PolicyHttpClient();
    enforcer = new RecordingPolicyEnforcer(
        new Decision(Decision.Effect.ALLOW, "network ok"),
        new Decision(Decision.Effect.DENY, "explicit approval required"));
    inject(client, "enforcer", enforcer);
  }

  @Test
  void sendMarksNetworkAndQuotaChecksAsRequireExplicitApproval() {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/test"))
        .GET()
        .build();

    PolicyHttpClient.PolicyDeniedException error = assertThrows(
        PolicyHttpClient.PolicyDeniedException.class,
        () -> client.send(request, BodyHandlers.ofString()));

    assertEquals("Quota DENY: explicit approval required", error.getMessage());
    assertEquals(2, enforcer.actions.size());

    Action netAction = enforcer.actions.get(0);
    assertEquals(Action.Type.NET_CONNECT, netAction.type);
    assertEquals("true", netAction.getString("require_explicit_approval"));
    assertEquals("policy-http-client:net-connect", netAction.getString("subject"));
    assertEquals("localhost", netAction.getString("host"));
    assertEquals(Integer.valueOf(8080), netAction.getInt("port"));

    Action quotaAction = enforcer.actions.get(1);
    assertEquals(Action.Type.QUOTA_CONSUME, quotaAction.type);
    assertEquals("true", quotaAction.getString("require_explicit_approval"));
    assertEquals("policy-http-client:quota", quotaAction.getString("subject"));
    assertEquals(Integer.valueOf(1), quotaAction.getInt("net_requests"));
  }

  @Test
  void sendStopsBeforeQuotaWhenNetworkPolicyDenies() {
    enforcer = new RecordingPolicyEnforcer(new Decision(Decision.Effect.DENY, "blocked host"));
    injectQuietly(client, "enforcer", enforcer);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://google.com"))
        .GET()
        .build();

    PolicyHttpClient.PolicyDeniedException error = assertThrows(
        PolicyHttpClient.PolicyDeniedException.class,
        () -> client.send(request, BodyHandlers.ofString()));

    assertEquals("Network policy DENY: blocked host", error.getMessage());
    assertEquals(1, enforcer.actions.size());
    assertTrue(enforcer.actions.stream().allMatch(action -> action.type != Action.Type.QUOTA_CONSUME));
  }

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static void injectQuietly(Object target, String fieldName, Object value) {
    try {
      inject(target, fieldName, value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static final class RecordingPolicyEnforcer extends PolicyEnforcer {
    final List<Action> actions = new ArrayList<Action>();
    final List<Decision> scripted;
    int index = 0;

    RecordingPolicyEnforcer(Decision... scripted) {
      this.scripted = List.of(scripted);
    }

    @Override
    public Decision check(Action action) {
      actions.add(action);
      if (index < scripted.size()) {
        return scripted.get(index++);
      }
      return new Decision(Decision.Effect.ALLOW, "ok");
    }
  }
}
