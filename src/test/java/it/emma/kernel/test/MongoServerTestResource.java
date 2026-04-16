package it.emma.kernel.test;

import java.util.Map;

import de.bwaldvogel.mongo.InMemoryMongoServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class MongoServerTestResource implements QuarkusTestResourceLifecycleManager {

  private InMemoryMongoServer server;

  @Override
  public Map<String, String> start() {
    server = new InMemoryMongoServer();
    String connectionString = server.bindAndGetConnectionString();
    return Map.of(
        "quarkus.mongodb.connection-string", connectionString,
        "quarkus.mongodb.database", "emma");
  }

  @Override
  public void stop() {
    if (server != null) {
      server.shutdownNow();
      server = null;
    }
  }
}
