/*
 *   Copyright (c) 2014 Program Analysis Group, Georgia Tech
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package edu.gatech.sqltutor.rules;

import java.util.EnumSet;
import java.util.List;

import org.deri.iris.api.basics.IRule;


/**
 * Rule that matches on portions of an 
 * SQL query and produces some annotation 
 * or output for a natural language description.
 */
public interface ITranslationRule {
	public static final int TYPE_SQL = 1;
	public static final int TYPE_SYMBOLIC = 2;
	
	public boolean apply(SymbolicState state);
	
	/** 
	 * Returns the precedence of this rule.  
	 * Rules should be applied in order of precedence.
	 * @return the precedence.
	 */
	public int getPrecedence();
	
	/**
	 * Returns the phases the rule should operate in.
	 * @return the phases as a bit vector
	 */
	public EnumSet<TranslationPhase> getPhases();
	
	/**
	 * Overrides the default phases this rule should operate in.
	 * @param phases the phases to use or <code>null</code> to use the default
	 */
	public void setPhases(EnumSet<TranslationPhase> phases);
	
	/**
	 * Returns the type of this translation rule, for determining 
	 * sub-interfaces.
	 * 
	 * @return the translation rule type
	 */
	public int getType();
	
	/**
	 * Returns a unique id for this rule.
	 * @return the rule id
	 */
	public String getRuleId();
	
	/**
	 * Returns a list of static datalog rules used by this meta-rule.
	 * @return the static rules, which may be empty but should not be <code>null</code>
	 */
	public List<IRule> getDatalogRules();
}
