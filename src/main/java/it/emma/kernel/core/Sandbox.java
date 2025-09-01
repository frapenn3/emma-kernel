package it.emma.kernel.core;
public interface Sandbox {
  record ExecSpec(String tool, String[] args, String workdir, int timeoutSec) {}
  record ExecResult(int exitCode, String stdout, String stderr) {}
  ExecResult run(ExecSpec spec) throws Exception;
}
