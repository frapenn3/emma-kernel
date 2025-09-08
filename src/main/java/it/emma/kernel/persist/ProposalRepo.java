package it.emma.kernel.persist;

import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProposalRepo implements PanacheMongoRepositoryBase<ProposalDoc, String> {} 