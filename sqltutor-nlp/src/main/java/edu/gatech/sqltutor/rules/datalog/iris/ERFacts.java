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
package edu.gatech.sqltutor.rules.datalog.iris;

import static edu.gatech.sqltutor.QueryUtils.splitKeyParts;

import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.gatech.sqltutor.rules.Markers;
import edu.gatech.sqltutor.rules.er.ERAttribute;
import edu.gatech.sqltutor.rules.er.ERAttribute.DescriptionType;
import edu.gatech.sqltutor.rules.er.ERCompositeAttribute;
import edu.gatech.sqltutor.rules.er.ERDiagram;
import edu.gatech.sqltutor.rules.er.EREdgeConstraint;
import edu.gatech.sqltutor.rules.er.EREntity;
import edu.gatech.sqltutor.rules.er.ERObjectMetadata;
import edu.gatech.sqltutor.rules.er.ERRelationship;
import edu.gatech.sqltutor.rules.er.ERRelationship.ERRelationshipEdge;
import edu.gatech.sqltutor.rules.er.ERRelationshipMetadata;
import edu.gatech.sqltutor.rules.er.mapping.ERForeignKeyJoin;
import edu.gatech.sqltutor.rules.er.mapping.ERJoinMap;
import edu.gatech.sqltutor.rules.er.mapping.ERJoinMap.ERKeyPair;
import edu.gatech.sqltutor.rules.er.mapping.ERJoinMap.MapType;
import edu.gatech.sqltutor.rules.er.mapping.ERLookupTableJoin;
import edu.gatech.sqltutor.rules.er.mapping.ERMapping;
import edu.gatech.sqltutor.rules.util.NLUtil;
import edu.gatech.sqltutor.util.Pair;

/** Dynamic ER diagram facts. */
public class ERFacts extends DynamicFacts {
	private static final Logger _log = LoggerFactory.getLogger(ERFacts.class);
	
	public ERFacts() { }
	
	public void generateFacts(ERDiagram erDiagram) {
		long duration = -System.currentTimeMillis();
		for( EREntity entity: erDiagram.getEntities() )
			addEntity(entity);
		for( ERRelationship rel: erDiagram.getRelationships() )
			addRelationship(rel);
		_log.debug(Markers.TIMERS_FINE, "er-diagram facts generated in {} ms.", duration += System.currentTimeMillis());
	}
	
	public void generateFacts(ERMapping erMapping) {
		long duration = -System.currentTimeMillis();
		addAttributeMappings(erMapping);
		addJoinMappings(erMapping);
		_log.debug(Markers.TIMERS_FINE, "er-mapping facts generated in {} ms.", duration += System.currentTimeMillis());
	}

	private void addAttributeMappings(ERMapping erMapping) {
		for( String attr: erMapping.getAttributes() ) {
			String col = erMapping.getColumnName(attr);
			if( col == null ) {
				_log.error("No column for attribute: " + attr);
			}
			Pair<String,String> attrParts = splitKeyParts(attr),
				colParts = splitKeyParts(col);
			
			addFact(ERPredicates.erAttributeMapsTo, 
				attrParts.getFirst(), attrParts.getSecond(),
				colParts.getFirst(),  colParts.getSecond());
		}
	}
	
	private void addJoinMappings(ERMapping erMapping) {
		Set<ERJoinMap> joins = erMapping.getJoins();
		for( ERJoinMap join: joins ) {
			MapType type = join.getMapType();
			addFact(ERPredicates.erRelationshipJoinType, 
				join.getRelationship(), type.toString().toLowerCase());
			switch( join.getMapType() ) {
				case FOREIGN_KEY:
					addFKJoin((ERForeignKeyJoin)join);
					break;
				case LOOKUP_TABLE:
					addLookupTableJoin((ERLookupTableJoin)join);
					break;
				case MERGED:
					_log.warn("FIXME: Merged type not handled yet."); // FIXME
			}
		}
	}
	
	private void addFKJoin(ERForeignKeyJoin join) {
		String rel = join.getRelationship();
		ERKeyPair keys = join.getKeyPair();
		addTableJoinKeys(rel, 0, keys);
	}
	
	private void addLookupTableJoin(ERLookupTableJoin join) {
		String rel = join.getRelationship();
		addTableJoinKeys(rel, 0, join.getLeftKeyPair());
		addTableJoinKeys(rel, 1, join.getRightKeyPair());
	}
	
	private void addTableJoinKeys(String rel, Integer pos, ERKeyPair keys) {
		assert pos != null && pos >= 0 : "pos should be a non-negative integer.";
		Pair<String,String> pk = splitKeyParts(keys.getPrimaryKey()),
				fk = splitKeyParts(keys.getForeignKey());
		
		addFact(ERPredicates.erJoinPK, rel, pos, pk.getFirst(), pk.getSecond());
		addFact(ERPredicates.erJoinFK, rel, pos, fk.getFirst(), fk.getSecond());
	}
	
	private void addEntity(EREntity entity) {
		String name = entity.getFullName();
		addFact(ERPredicates.erEntity, name);
		addFact(ERPredicates.erEntityType, name, 
			entity.getEntityType().toString().toLowerCase());
		
		ERObjectMetadata metadata = entity.getMetadata();
		if( metadata != null ) {
			if( metadata.getSingularLabel() != null )
				addFact(ERPredicates.erEntityLabelSingular, name, metadata.getSingularLabel());
			if( metadata.getPluralLabel() != null )
				addFact(ERPredicates.erEntityLabelPlural, name, metadata.getPluralLabel());
		}
		
		for( ERAttribute attr: entity.getAttributes() ) {
			addAttribute(name, attr);
		}
	}
	
	private void addRelationship(ERRelationship rel) {
		String name = rel.getFullName();
		addFact(ERPredicates.erRelationship, name);
		
		for( ERAttribute attr: rel.getAttributes() ) {
			addAttribute(name, attr);
		}
		
		addRelationshipEdge(name, 0, rel.getLeftEdge());
		addRelationshipEdge(name, 1, rel.getRightEdge());
		
		ERRelationshipMetadata metadata = rel.getMetadata();
		if( metadata.getAlternateSingularVerbForm() != null )
			addFact(ERPredicates.erRelationshipAlternateSingular, name, metadata.getAlternateSingularVerbForm());
		if( metadata.getAlternatePluralVerbForm() != null )
			addFact(ERPredicates.erRelationshipAlternatePlural, name, metadata.getAlternatePluralVerbForm());
		if( metadata.getNegatedSingularVerbForm() != null )
			addFact(ERPredicates.erRelationshipNegatedSingular, name, metadata.getNegatedSingularVerbForm());
		if( metadata.getNegatedPluralVerbForm() != null )
			addFact(ERPredicates.erRelationshipNegatedPlural, name, metadata.getNegatedPluralVerbForm());
	}
	
	private void addAttribute(String parent, ERAttribute attr) {
		String attrName = attr.getName();
		addFact(ERPredicates.erAttribute, parent, attrName);
		
		if( attr.isKey() )
			addFact(ERPredicates.erAttributeIsKey, parent, attrName);
		
		if( attr.isComposite() ) {
			addFact(ERPredicates.erAttributeIsComposite, parent, attrName);
			for( ERAttribute child: ((ERCompositeAttribute)attr).getAttributes() ) {
				addFact(ERPredicates.erAttributeParent, parent, attrName, child.getName());
			}
		}
		
		DescriptionType dtype = attr.getDescribesEntity();
		if( dtype != null && dtype != DescriptionType.NONE )
			addFact(ERPredicates.erAttributeDescribes, parent, attrName, dtype.toString().toLowerCase(Locale.ENGLISH));
		
		addFact(ERPredicates.erAttributeDataType, parent, attrName, attr.getDataType().toString());
		
		// get override labels if provided
		ERObjectMetadata metadata = attr.getMetadata();
		String singularLabel = null, pluralLabel = null;
		if( metadata != null ) {
			singularLabel = metadata.getSingularLabel();
			pluralLabel = metadata.getPluralLabel();
		}
		// otherwise default to the attribute name
		if( singularLabel == null )
			singularLabel = attr.getName().toLowerCase();
		if( pluralLabel == null )
			pluralLabel = NLUtil.pluralize(singularLabel);
		
		addFact(ERPredicates.erAttributeLabelSingular, parent, attrName, singularLabel);
		addFact(ERPredicates.erAttributeLabelPlural, parent, attrName, pluralLabel);
	}

	private void addRelationshipEdge(String rel, Integer pos, ERRelationshipEdge edge) {
		assert pos != null && pos >= 0 : "pos should be a non-negative int";
		addFact(ERPredicates.erRelationshipEdgeEntity, 
			rel, pos, edge.getEntity().getFullName());
		
		EREdgeConstraint constraint = edge.getConstraint();
		addFact(ERPredicates.erRelationshipEdgeLabel, 
			rel, pos, constraint.getLabel());
		addFact(ERPredicates.erRelationshipEdgeCardinality,
			rel, pos, constraint.getCardinality());
	}
}
