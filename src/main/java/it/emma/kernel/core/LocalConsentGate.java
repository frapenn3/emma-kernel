package it.emma.kernel.core;

import it.emma.kernel.dto.Approval;
import it.emma.kernel.dto.ProposalSummary;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Implementazione locale/dev del gate di consenso.
 * Estende ConsentGate così KernelResource può continuare a dipendere da ConsentGate
 * ma, grazie a CDI, useremo questa implementazione concreta.
 */
@ApplicationScoped
public class LocalConsentGate extends ConsentGate {

  @Override
  public Approval request(String id, ProposalSummary summary, Approval requested) {
    // Qui puoi mettere la logica “reale” per l’ambiente locale:
    // log, prompt su console, regole ad hoc, ecc.
    // Per ora: se non è già specificato, approviamo (YES) e lasciamo una traccia nei log.
    System.out.printf("[ConsentGate] id=%s objective=%s -> %s%n",
        id, summary != null ? summary.objective : "n/a",
        requested != null ? requested : Approval.YES);

    return (requested != null) ? requested : Approval.YES;
  }
}
