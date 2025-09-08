package it.emma.kernel.persist;

import java.util.Date;
import java.util.UUID;

import org.bson.codecs.pojo.annotations.BsonId;

import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * Documento di audit (collection "audit") con _id String.
 */
@MongoEntity(collection = "audit", database = "emma")
public class AuditDoc {

  @BsonId
  public String id;       // _id String, NON ObjectId

  public Date   ts;       // timestamp evento
  public String event;    // es: PROPOSE, APPROVAL, KILL, RESUME, ...
  public String subject;  // es: proposalId ("imp-001") oppure "global"
  public String detail;   // es: "submitted", "YES", "NO", "manual"

  public static AuditDoc of(String event, String subject, String detail) {
    AuditDoc d = new AuditDoc();
    d.id      = UUID.randomUUID().toString();
    d.ts      = new Date();
    d.event   = event;
    d.subject = subject;
    d.detail  = detail;
    return d;
  }
}
