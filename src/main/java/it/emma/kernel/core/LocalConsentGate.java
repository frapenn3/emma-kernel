package it.emma.kernel.core;

import it.emma.kernel.dto.Approval;
import it.emma.kernel.dto.ProposalSummary;
import org.jboss.logging.Logger;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Implementazione locale/dev del gate di consenso.
 * Estende ConsentGate così KernelResource può continuare a dipendere da ConsentGate
 * ma, grazie a CDI, useremo questa implementazione concreta.
 */
@ApplicationScoped
public class LocalConsentGate extends ConsentGate {
  private static final Logger LOG = Logger.getLogger(LocalConsentGate.class);

  @Override
  public Approval request(String id, ProposalSummary summary, Approval requested) {
    // Qui puoi mettere la logica “reale” per l’ambiente locale:
    // log, prompt su console, regole ad hoc, ecc.
    // Per ora: se non è già specificato, approviamo (YES) e lasciamo una traccia nei log.
    Approval finalDecision = (requested != null) ? requested : Approval.NO;
    LOG.infof("Consent decision id=%s objective=%s -> %s",
        id, summary != null ? summary.objective : "n/a",
        finalDecision);

    return finalDecision;
  }
}
