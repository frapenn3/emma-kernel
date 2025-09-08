package it.emma.kernel.policy;

public final class QuotaSnapshot {
	  public long time_min;     // minuti consumati da start/reset
	  public int  cpu_cores;    // cores “allocati” (se usato)
	  public int  net_requests; // richieste di rete consumate
	  public long uptime_sec;   // solo info
	}