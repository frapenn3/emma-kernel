package it.emma.kernel.core;
import it.emma.kernel.dto.AuditEntry;
public interface AuditLog { void record(AuditEntry e); }
