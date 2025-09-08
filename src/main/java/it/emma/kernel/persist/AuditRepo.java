package it.emma.kernel.persist;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;

/**
 * Repository Panache su String (coerente con _id String).
 */
@ApplicationScoped
public class AuditRepo implements PanacheMongoRepositoryBase<AuditDoc, String> {
}
