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
package edu.gatech.sqltutor.rules.lang;

import static edu.gatech.sqltutor.rules.datalog.iris.IrisUtil.literal;

import java.util.EnumSet;

import org.deri.iris.api.basics.IQuery;
import org.deri.iris.api.basics.ITuple;
import org.deri.iris.api.terms.ITerm;
import org.deri.iris.factory.Factory;
import org.deri.iris.storage.IRelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akiban.sql.parser.OrNode;
import com.akiban.sql.parser.QueryTreeNode;

import edu.gatech.sqltutor.rules.ITranslationRule;
import edu.gatech.sqltutor.rules.Markers;
import edu.gatech.sqltutor.rules.TranslationPhase;
import edu.gatech.sqltutor.rules.datalog.iris.IrisUtil;
import edu.gatech.sqltutor.rules.datalog.iris.RelationExtractor;
import edu.gatech.sqltutor.rules.datalog.iris.SQLPredicates;
import edu.gatech.sqltutor.rules.datalog.iris.SymbolicFacts.NodeMap;
import edu.gatech.sqltutor.rules.datalog.iris.SymbolicPredicates;
import edu.gatech.sqltutor.rules.symbolic.SymbolicType;
import edu.gatech.sqltutor.rules.symbolic.tokens.ISymbolicToken;
import edu.gatech.sqltutor.rules.symbolic.tokens.SQLToken;

/**
 * Preprocessing rule that assigns conjunct scopes to all SQL tokens.
 */
public class ConjunctScopeComputationRule extends StandardSymbolicRule
		implements ITranslationRule {
	
	private static final Logger _log = LoggerFactory.getLogger(DefaultTableLabelRule.class);
	// there is an SQL token without an assigned conjunct-scope
	private static final IQuery QUERY = Factory.BASIC.createQuery(
		literal(SymbolicPredicates.type, "?token", SymbolicType.SQL_AST),
		literal(false, SymbolicPredicates.conjunctScope, "?token", "?cscope"),
		// this is just to get a reference to the SELECT node
		literal(SQLPredicates.nodeHasType, "?select", "SelectNode")
	);
	
	public ConjunctScopeComputationRule() {
	}

	public ConjunctScopeComputationRule(int precedence) {
		super(precedence);
	}
	
	@Override
	protected boolean handleResult(IRelation relation, RelationExtractor ext) {
		final boolean debug = _log.isDebugEnabled(Markers.SYMBOLIC);
		boolean applied = false;
		
		while( ext.nextTuple() ) {
			SQLToken selectToken = ext.getToken("?select");
			QueryTreeNode cscope = selectToken.getAstNode();
			processToken(cscope, selectToken);
			
			applied = true;
		}
		
		return applied;
	}
	
	protected void processToken(QueryTreeNode cscope, SQLToken token) {
		QueryTreeNode astNode = token.getAstNode();
		token.setConjunctScope(cscope);
		// FIXME others, e.g. NOT?
		if( astNode instanceof OrNode ) {
			NodeMap scopeMap = state.getSymbolicFacts().getScopeMap();
			ITerm parentId = IrisUtil.asTerm(scopeMap.getObjectId(cscope));
			for( ISymbolicToken c: token.getChildren() ) {
				SQLToken child = (SQLToken)c;
				QueryTreeNode nextScope = child.getAstNode();
				ITuple fact = IrisUtil.asTuple(parentId, scopeMap.getObjectId(nextScope));
				state.addFact(SymbolicPredicates.conjunctScopeParent, fact);
				processToken(nextScope, child);
			}
		} else {
			for( ISymbolicToken c: token.getChildren() ) {
				SQLToken child = (SQLToken)c;
				processToken(cscope, child);
			}
		}
	}
	
	@Override
	protected IQuery getQuery() {
		return QUERY;
	}
	
	@Override
	protected EnumSet<TranslationPhase> getDefaultPhases() {
		return EnumSet.of(TranslationPhase.PREPROCESSING);
	}
}
