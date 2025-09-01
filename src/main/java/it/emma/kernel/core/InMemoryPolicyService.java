package it.emma.kernel.core;
import jakarta.enterprise.context.ApplicationScoped;
@ApplicationScoped
public class InMemoryPolicyService implements PolicyService {
  @Override public boolean canAccessUrl(String url){ return false; } // TODO: leggere da YAML
  @Override public boolean canWritePath(String path){ return path.startsWith("/work/"); }
  @Override public boolean isToolAllowed(String tool){ return switch(tool){ case "python","node","javac","mvn" -> true; default -> false; }; }
}
