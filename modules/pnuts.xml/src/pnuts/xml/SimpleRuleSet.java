package pnuts.xml;

import java.util.*;

class SimpleRuleSet implements RuleSet {
	HashMap rules = new HashMap();

	public void add(String pattern, DigestAction action, String keyword){
		rules.put(pattern, new RuleTarget(action, keyword));
	}

	public void scan(String path, List paths, TargetHandler handler) throws Exception {
		RuleTarget target = (RuleTarget)rules.get(path);
		if (target != null){
			handler.handle(target.action, target.keyword);
		}
	}
}
