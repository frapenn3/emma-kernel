package it.emma.kernel.persist;

import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.codecs.pojo.annotations.BsonId;
import java.util.List;
import java.util.Map;

@MongoEntity(collection = "proposals", database = "emma")
public class ProposalDoc {
  @BsonId
  public String id;

  // campi essenziali dal DTO
  public String objective;
  public String hypothesis;
  public List<String> scope;
  public List<String> risks;
  public List<Map<String,String>> plan;

  // metriche / limiti (mantenuti come mappe semplici)
  public Map<String,String> success_metrics;
  public Map<String,Number> cost_caps;

  public String rollback_plan;
  public List<String> evidence_requirements;

  // stato corrente della FSM per la proposta
  public String state; // es. "PROPOSE", "RESEARCH", ...
}
