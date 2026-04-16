package it.emma.kernel.policy;

import java.util.List;

public class ConstitutionModel {
  public EmmaKernelConstitution emma_kernel_constitution;

  public static final class EmmaKernelConstitution {
    public String version;
    public Boolean immutable;
    public List<String> laws;
    public List<String> priorities;
  }
}
