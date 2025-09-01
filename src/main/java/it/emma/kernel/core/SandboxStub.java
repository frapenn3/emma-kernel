package it.emma.kernel.core;
import jakarta.enterprise.context.ApplicationScoped;
@ApplicationScoped
public class SandboxStub implements Sandbox {
  @Override public ExecResult run(ExecSpec spec){
    return new ExecResult(0, "[stub] would run "+spec.tool()+" with "+String.join(" ", spec.args()), "");
  }
}
