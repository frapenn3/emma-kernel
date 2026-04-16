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
        .body("openProposals[0].id", equalTo(proposalId))
        .body("proposalStates.%s".formatted(proposalId), equalTo("PROPOSE"));

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
        .body("proposalStates.%s".formatted(proposalId), equalTo("RESEARCH"));

    given()
        .queryParam("n", 10)
    .when()
        .get("/kernel/audit/tail")
    .then()
        .statusCode(200)
        .body("lines", hasSize(2))
        .body("lines[0]", notNullValue())
        .body("lines[1]", notNullValue());
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
}
