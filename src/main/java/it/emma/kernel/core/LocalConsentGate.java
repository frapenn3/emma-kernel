package it.emma.kernel.core;
import it.emma.kernel.dto.*; import jakarta.enterprise.context.ApplicationScoped;
@ApplicationScoped
public class LocalConsentGate implements ConsentGate {
  @Override public Approval request(String id, ProposalSummary s){
    return (s != null && s.objective != null && !s.objective.isBlank()) ? Approval.YES : Approval.NO; // MVP
  }
}
