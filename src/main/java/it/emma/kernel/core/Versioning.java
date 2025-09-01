package it.emma.kernel.core;
public interface Versioning {
  String snapshot(String label);
  void rollback(String snapshotId);
}
