package it.emma.kernel.core;
public interface PolicyService {
  boolean canAccessUrl(String url);
  boolean canWritePath(String path);
  boolean isToolAllowed(String toolName);
}
