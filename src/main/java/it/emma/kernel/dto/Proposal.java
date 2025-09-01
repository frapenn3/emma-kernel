package it.emma.kernel.dto;
import java.util.List;
public class Proposal {
  public String id;
  public String objective;
  public String hypothesis;
  public List<String> scope;
  public List<String> risks;
  public List<PlanItem> plan;
  public SuccessMetrics success_metrics;
  public CostCaps cost_caps;
  public String rollback_plan;
  public List<String> evidence_requirements;
  public static class PlanItem { public String key; public String value; }
  public static class SuccessMetrics { public String quality_gain; public String latency_p95; public String errors; }
  public static class CostCaps { public int time_min; public int cpu_cores; public int net_requests; }
}
