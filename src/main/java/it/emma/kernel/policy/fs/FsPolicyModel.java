package it.emma.kernel.policy.fs;

import java.util.List;

public class FsPolicyModel {
	public String version;
	public List<Rule> rules;

	public static class Rule {
		public List<String> op; // READ/WRITE/DELETE o "*"
		public String path; // glob
		public String effect; // ALLOW/DENY/ASK
		public String reason;
	}
}
