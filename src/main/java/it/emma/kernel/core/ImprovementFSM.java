package it.emma.kernel.core;
public interface ImprovementFSM {
  enum State { IDLE, PROPOSE, RESEARCH, BUILD, TEST, REVIEW, APPLY_WAIT, APPLY, MONITOR, DONE, REVERT }
  State current(String improvementId);
  void advance(String improvementId, State target) throws IllegalStateException;
}
