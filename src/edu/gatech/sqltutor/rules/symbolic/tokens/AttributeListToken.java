package edu.gatech.sqltutor.rules.symbolic.tokens;

import edu.gatech.sqltutor.rules.symbolic.PartOfSpeech;
import edu.gatech.sqltutor.rules.symbolic.SymbolicType;

public class AttributeListToken 
		extends ChildContainerToken implements ISymbolicToken {
	public AttributeListToken(AttributeListToken token) {
		super(token);
	}
	
	public AttributeListToken() {
		super(PartOfSpeech.NOUN_PHRASE);
	}
	
	@Override
	public SymbolicType getType() {
		return SymbolicType.ATTRIBUTE_LIST;
	}

	@Override
	protected boolean canAcceptChild(ISymbolicToken tok) {
		switch( tok.getType() ) {
			case ATTRIBUTE:
			case LITERAL:
			case ALL_ATTRIBUTES:
				return true;
			default:
				return false;
		}
	}
}
