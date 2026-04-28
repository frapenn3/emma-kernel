package it.emma.kernel.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import it.emma.kernel.core.KernelGuard;
import it.emma.kernel.persist.AuditRepo;
import it.emma.kernel.persist.ProposalRepo;
import it.emma.kernel.test.MongoServerTestResource;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoServerTestResource.class)
class KernelResourceMongoE2ETest {

  @Inject
  ProposalRepo proposalRepo;

  @Inject
  AuditRepo auditRepo;

  @Inject
  KernelGuard guard;

  @BeforeEach
  void setUp() {
    proposalRepo.deleteAll();
    auditRepo.deleteAll();
    guard.resume();
  }

  @Test
  void submitStatusApproveAndAuditFlowPersistsToMongo() {
    String proposalId = "imp-" + UUID.randomUUID();

    given()
        .contentType("application/json")
        .body("""
            {
              "id": "%s",
              "objective": "Improve coverage",
              "hypothesis": "More tests reduce regressions"
            }
            """.formatted(proposalId))
    .when()
        .post("/kernel/proposals")
    .then()
        .statusCode(200)
        .body("status", equalTo("OK"))
        .body("id", equalTo(proposalId));

    given()
    .when()
        .get("/kernel/status")
    .then()
        .statusCode(200)
        .body("openProposals", hasSize(1))
        .body("allProposals", hasSize(1))
        .body("openProposals[0].id", equalTo(proposalId))
        .body("proposalStates.%s".formatted(proposalId), equalTo("PROPOSE"));

    given()
    .when()
        .get("/kernel/proposals")
    .then()
        .statusCode(200)
        .body("proposals", hasSize(1))
        .body("proposals[0].id", equalTo(proposalId))
        .body("proposals[0].state", equalTo("PROPOSE"));

    given()
    .when()
        .get("/kernel/proposals/{id}", proposalId)
    .then()
        .statusCode(200)
        .body("id", equalTo(proposalId))
        .body("createdAt", notNullValue())
        .body("updatedAt", notNullValue());

    given()
        .contentType("application/json")
        .body("\"YES\"")
    .when()
        .post("/kernel/proposals/{id}/approve", proposalId)
    .then()
        .statusCode(200)
        .body("status", equalTo("YES"));

    given()
    .when()
        .get("/kernel/status")
    .then()
        .statusCode(200)
        .body("openProposals", hasSize(0))
        .body("allProposals", hasSize(1))
        .body("proposalStates.%s".formatted(proposalId), equalTo("RESEARCH"));

    given()
        .queryParam("state", "PROPOSE")
    .when()
        .get("/kernel/proposals")
    .then()
        .statusCode(200)
        .body("proposals", hasSize(0));

    given()
        .contentType("application/json")
        .body("{\"target\":\"BUILD\"}")
    .when()
        .post("/kernel/proposals/{id}/advance", proposalId)
    .then()
        .statusCode(200)
        .body("from", equalTo("RESEARCH"))
        .body("state", equalTo("BUILD"));

    given()
        .contentType("application/json")
        .body("{\"target\":\"DONE\"}")
    .when()
        .post("/kernel/proposals/{id}/advance", proposalId)
    .then()
        .statusCode(409)
        .body("code", equalTo("INVALID_TRANSITION"));

    given()
        .queryParam("limit", 20)
    .when()
        .get("/kernel/proposals/{id}/audit", proposalId)
    .then()
        .statusCode(200)
        .body("entries", hasSize(3))
        .body("entries[0].subject", equalTo(proposalId));

    given()
        .queryParam("n", 10)
    .when()
        .get("/kernel/audit/tail")
    .then()
        .statusCode(200)
        .body("lines", hasSize(3))
        .body("lines[0]", notNullValue())
        .body("lines[2]", notNullValue());
  }

  @Test
  void killswitchBlocksNewSubmissionsUntilResume() {
    String proposalId = "imp-" + UUID.randomUUID();

    given()
    .when()
        .post("/kernel/killswitch")
    .then()
        .statusCode(200)
        .body("status", equalTo("STOPPED"));

    given()
        .contentType("application/json")
        .body("""
            {
              "id": "%s",
              "objective": "Should be blocked"
            }
            """.formatted(proposalId))
    .when()
        .post("/kernel/proposals")
    .then()
        .statusCode(423)
        .body("code", equalTo("KERNEL_STOPPED"));

    given()
    .when()
        .post("/kernel/resume")
    .then()
        .statusCode(200)
        .body("status", equalTo("RESUMED"));

    given()
        .contentType("application/json")
        .body("""
            {
              "id": "%s",
              "objective": "Allowed again"
            }
            """.formatted(proposalId))
    .when()
        .post("/kernel/proposals")
    .then()
        .statusCode(200)
        .body("id", equalTo(proposalId));
  }

  @Test
  void lifecycleEndpointsRejectInvalidRequests() {
    String proposalId = "imp-" + UUID.randomUUID();

    given()
        .contentType("application/json")
        .body("""
            {
              "id": "%s",
              "objective": "Validate lifecycle edge cases"
            }
            """.formatted(proposalId))
    .when()
        .post("/kernel/proposals")
    .then()
        .statusCode(200);

    given()
        .contentType("application/json")
        .body("{}")
    .when()
        .post("/kernel/proposals/{id}/advance", proposalId)
    .then()
        .statusCode(400)
        .body("code", equalTo("MISSING_TARGET_STATE"));

    given()
        .contentType("application/json")
        .body("{\"target\":\"NOPE\"}")
    .when()
        .post("/kernel/proposals/{id}/advance", proposalId)
    .then()
        .statusCode(400)
        .body("code", equalTo("INVALID_STATE"));

    given()
        .contentType("application/json")
        .body("{\"target\":\"DONE\"}")
    .when()
        .post("/kernel/proposals/{id}/advance", proposalId)
    .then()
        .statusCode(409)
        .body("code", equalTo("INVALID_TRANSITION"));

    given()
        .contentType("application/json")
        .body("\"YES\"")
    .when()
        .post("/kernel/proposals/{id}/approve", proposalId)
    .then()
        .statusCode(200);

    given()
        .contentType("application/json")
        .body("\"YES\"")
    .when()
        .post("/kernel/proposals/{id}/approve", proposalId)
    .then()
        .statusCode(409)
        .body("code", equalTo("INVALID_TRANSITION"));
  }

  @Test
  void auditSearchFiltersBySubjectAndEvent() {
    String proposalId = "imp-" + UUID.randomUUID();

    given()
        .contentType("application/json")
        .body("""
            {
              "id": "%s",
              "objective": "Audit filtering"
            }
            """.formatted(proposalId))
    .when()
        .post("/kernel/proposals")
    .then()
        .statusCode(200);

    given()
        .queryParam("subject", proposalId)
        .queryParam("event", "PROPOSE")
    .when()
        .get("/kernel/audit")
    .then()
        .statusCode(200)
        .body("entries", hasSize(1))
        .body("entries[0].subject", equalTo(proposalId))
        .body("entries[0].event", equalTo("PROPOSE"));
  }

  @Test
  void completeIsAllowedOnlyFromTerminalLifecycleStates() {
    String proposalId = "imp-" + UUID.randomUUID();

    given()
        .contentType("application/json")
        .body("""
            {
              "id": "%s",
              "objective": "Completion edge cases"
            }
            """.formatted(proposalId))
    .when()
        .post("/kernel/proposals")
    .then()
        .statusCode(200);

    given()
    .when()
        .post("/kernel/proposals/{id}/complete", proposalId)
    .then()
        .statusCode(409)
        .body("code", equalTo("INVALID_TRANSITION"));

    given()
    .when()
        .post("/kernel/proposals/{id}/revert", proposalId)
    .then()
        .statusCode(200)
        .body("state", equalTo("REVERT"));

    given()
    .when()
        .post("/kernel/proposals/{id}/complete", proposalId)
    .then()
        .statusCode(200)
        .body("state", equalTo("DONE"));
  }
}
