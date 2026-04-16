package it.emma.kernel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import it.emma.kernel.dto.Approval;

class ConsentGateTest {

  @Test
  void baseGateDeniesWhenApprovalIsMissing() {
    ConsentGate gate = new ConsentGate();

    assertEquals(Approval.NO, gate.request("proposal-1", null, null));
  }

  @Test
  void localGateDeniesWhenApprovalIsMissing() {
    LocalConsentGate gate = new LocalConsentGate();

    assertEquals(Approval.NO, gate.request("proposal-1", null, null));
  }
}
