package it.emma.kernel.policy;

public final class Decision {

  public enum Effect { ALLOW, DENY, ASK }

  public Effect effect;
  public String reason;

  public Decision() {}

  public Decision(Effect effect, String reason) {
    this.effect = effect;
    this.reason = reason;
  }
}
