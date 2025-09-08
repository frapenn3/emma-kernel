package it.emma.kernel.core;

import it.emma.kernel.dto.Approval;
import it.emma.kernel.dto.ProposalSummary;

/**
 * Gate di consenso “base”.
 * Espone un metodo request(...) usato da KernelResource.
 * La versione base fa semplicemente auto-approve (YES) se non viene passato nulla.
 * LocalConsentGate può estendere questa classe e personalizzare il comportamento.
 */
public class ConsentGate {

  /**
   * Richiede un consenso per una proposta.
   *
   * @param id        identificativo della proposta (può coincidere con summary.id)
   * @param summary   riassunto (objective, id, ecc.)
   * @param requested valore richiesto/pre-compilato (può essere null)
   * @return          decisione finale (YES/NO)
   */
  public Approval request(String id, ProposalSummary summary, Approval requested) {
    // Comportamento di default: se il chiamante non ha già deciso, approviamo.
    return (requested != null) ? requested : Approval.YES;
  }
}
