package it.emma.kernel.core;
import jakarta.enterprise.context.ApplicationScoped;
@ApplicationScoped
public class VersioningStub implements Versioning {
  @Override public String snapshot(String label){ return "snap-"+System.currentTimeMillis(); }
  @Override public void rollback(String id){ /* TODO */ }
}
