package it.emma.kernel.dto;
import it.emma.kernel.core.ImprovementFSM;
import java.util.List;
import java.util.Map;

public class KernelStatus {
  public ImprovementFSM.State state;
  public List<Proposal> openProposals;
  public List<ProposalSummary> allProposals;
  public Map<String, it.emma.kernel.core.ImprovementFSM.State> proposalStates;
  public boolean stopped;
}
