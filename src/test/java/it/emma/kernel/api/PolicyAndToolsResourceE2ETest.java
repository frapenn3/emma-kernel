package it.emma.kernel.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import it.emma.kernel.policy.Action;
import it.emma.kernel.policy.PolicyEnforcer;
import jakarta.inject.Inject;

@QuarkusTest
class PolicyAndToolsResourceE2ETest {

  private static final Path WRITTEN_FILE = Path.of("work", "e2e", "hello.txt");

  @Inject
  PolicyEnforcer enforcer;

  @BeforeEach
  void setUp() throws Exception {
    Files.deleteIfExists(WRITTEN_FILE);
    enforcer.getQuotaTracker().reset();
  }

  @AfterEach
  void tearDown() throws Exception {
    Files.deleteIfExists(WRITTEN_FILE);
    enforcer.getQuotaTracker().reset();
  }

  @Test
  void writeEndpointReturns423WithoutApproval() {
    given()
        .contentType("application/json")
        .body("""
            {
              "path": "e2e/hello.txt",
              "content": "Emma online"
            }
            """)
    .when()
        .post("/kernel/tools/write")
    .then()
        .statusCode(423)
        .body("error", equalTo("explicit approval required"));
  }

  @Test
  void writeAndReadEndpointsWorkWithExplicitApproval() {
    given()
        .contentType("application/json")
        .body("""
            {
              "path": "e2e/hello.txt",
              "content": "Emma online",
              "approval": "YES"
            }
            """)
    .when()
        .post("/kernel/tools/write")
    .then()
        .statusCode(200)
        .body("status", equalTo("OK"))
        .body("reason", equalTo("written"));

    given()
        .queryParam("path", "e2e/hello.txt")
    .when()
        .get("/kernel/tools/read")
    .then()
        .statusCode(200)
        .body("path", equalTo("e2e/hello.txt"))
        .body("content", equalTo("Emma online"));
  }

  @Test
  void quotaCheckEndpointDoesNotConsumeQuotaState() {
    given()
        .contentType("application/json")
        .body("""
            {
              "net_requests": 3
            }
            """)
    .when()
        .post("/kernel/policy/check/quota")
    .then()
        .statusCode(200)
        .body("effect", equalTo("ALLOW"))
        .body("reason", equalTo("quota check ok"));

    given()
    .when()
        .get("/kernel/policy/quotas/snapshot")
    .then()
        .statusCode(200)
        .body("net_requests", equalTo(0));
  }

  @Test
  void quotasResetEndpointClearsConsumedState() {
    HashMap<String, Object> params = new HashMap<String, Object>();
    params.put("net_requests", Integer.valueOf(4));
    enforcer.check(new Action(Action.Type.QUOTA_CONSUME, params));

    given()
    .when()
        .get("/kernel/policy/quotas/snapshot")
    .then()
        .statusCode(200)
        .body("net_requests", equalTo(4));

    given()
    .when()
        .post("/kernel/policy/quotas/reset")
    .then()
        .statusCode(200);

    given()
    .when()
        .get("/kernel/policy/quotas/snapshot")
    .then()
        .statusCode(200)
        .body("net_requests", equalTo(0));
  }
}
