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
package edu.gatech.sqltutor.rules.symbolic.tokens;

import com.akiban.sql.parser.CharConstantNode;
import com.akiban.sql.parser.NumericConstantNode;
import com.akiban.sql.parser.QueryTreeNode;

import edu.gatech.sqltutor.rules.symbolic.ValueType;

public class SQLValueToken extends SQLToken implements IHasValueType {
	protected ValueType valueType = ValueType.UNKNOWN;

	public SQLValueToken(QueryTreeNode astNode) {
		super(astNode);
		if( astNode instanceof CharConstantNode )
			valueType = ValueType.STRING;
		else if( astNode instanceof NumericConstantNode )
			valueType = ValueType.NUMBER;
	}

	public SQLValueToken(SQLToken token) {
		super(token);
	}

	@Override
	public ValueType getValueType() {
		return valueType;
	}

	@Override
	public void setValueType(ValueType valueType) {
		if( valueType == null ) throw new NullPointerException("valueType is null");
		this.valueType = valueType;
	}

}
