package it.emma.kernel.core;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
@ApplicationScoped
public class StateMachineImpl implements ImprovementFSM {
  private final Map<String, State> states = Collections.synchronizedMap(new HashMap<>());
  private static final Map<State, Set<State>> ALLOWED = Map.of(
    State.IDLE, Set.of(State.PROPOSE),
    State.PROPOSE, Set.of(State.RESEARCH),
    State.RESEARCH, Set.of(State.BUILD, State.REVIEW),
    State.BUILD, Set.of(State.TEST, State.REVIEW),
    State.TEST, Set.of(State.REVIEW),
    State.REVIEW, Set.of(State.APPLY_WAIT, State.REVERT),
    State.APPLY_WAIT, Set.of(State.APPLY, State.REVERT),
    State.APPLY, Set.of(State.MONITOR),
    State.MONITOR, Set.of(State.DONE, State.REVERT),
    State.REVERT, Set.of(State.DONE)
  );
  @Override public State current(String id){ return states.getOrDefault(id, State.IDLE); }
  @Override public void advance(String id, State target){
    var cur = current(id);
    var allowed = ALLOWED.getOrDefault(cur, Set.of());
    if(!allowed.contains(target)) throw new IllegalStateException("Invalid transition: "+cur+" -> "+target);
    states.put(id, target);
  }
}
