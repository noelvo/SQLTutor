package edu.gatech.sqltutor.rules.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DirectedMaskSubgraph;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.graph.MaskFunctor;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akiban.sql.StandardException;
import com.akiban.sql.parser.FromBaseTable;
import com.akiban.sql.parser.FromTable;
import com.akiban.sql.parser.NodeTypes;
import com.akiban.sql.parser.QueryTreeNode;
import com.akiban.sql.parser.SelectNode;
import com.akiban.sql.parser.Visitable;
import com.akiban.sql.parser.Visitor;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import edu.gatech.sqltutor.SQLTutorException;

public class TranslationGraph 
		extends DirectedMultigraph<LabelNode, TranslationEdge> {
	private static final long serialVersionUID = 1L;
	private static final Logger log = LoggerFactory.getLogger(TranslationGraph.class);
	
	private Map<String, LabelNode> tableAliasMap = 
		new HashMap<String, LabelNode>();
	
	private Map<QueryTreeNode, LabelNode> astToVertex = 
		new HashMap<QueryTreeNode, LabelNode>();
	
	private LabelNode selectNode;
	private LabelNode resultListNode;

	public TranslationGraph() {
		super(new TranslationEdgeFactory());
	}

	public TranslationGraph(SelectNode select) throws SQLTutorException {
		this();
		initializeFromSelect(select);
	}

	public void initializeFromSelect(SelectNode select) 
			throws SQLTutorException {
		selectNode = new LabelNode();
		selectNode.setAstNode(select);
		this.addVertex(selectNode);
		
		// add a parent node for the entire result list
		resultListNode = new ListFormatNode();
		resultListNode.setAstNode(select.getResultColumns());
		this.addVertex(resultListNode);
		
		// add a node for each table
		this.addTableNodes(select);
	}
	
	public void testPullTerms() {
		DirectedMaskSubgraph<LabelNode, TranslationEdge> sub = new DirectedMaskSubgraph<LabelNode, TranslationEdge>(
			(DirectedGraph<LabelNode, TranslationEdge>)this,
			new MaskFunctor<LabelNode, TranslationEdge>() {
				@Override
				public boolean isVertexMasked(LabelNode vertex) {
					return false;
				}
				
				@Override
				public boolean isEdgeMasked(TranslationEdge edge) {
					return !edge.isChildEdge();
				}
			}
		);
		
		TopologicalOrderIterator<LabelNode, TranslationEdge> topoIter =
			new TopologicalOrderIterator<LabelNode, TranslationEdge>(sub);
		Stack<LabelNode> vertexes = new Stack<LabelNode>();
		while( topoIter.hasNext() )
			vertexes.push(topoIter.next());
		
		
		while( !vertexes.isEmpty() ) {
			LabelNode next = vertexes.pop();

			log.info("Visting node: {}", next);
			List<LabelNode> children = this.getChildrenOf(next);
			if( children.isEmpty() ) {
				next.setChildChoices(Collections.<List<String>>emptyList());
			} else {
				List<List<String>> childChoices = expandTemplates(children.get(0));
				for( int i = 1; i < children.size(); ++i ) {
					childChoices = GraphUtils.mergeLists(childChoices, expandTemplates(children.get(i)));
				}
				next.setChildChoices(childChoices);
			}
			log.info("Children: {}", children);
			log.info("Choices: {}", next.getChoices());
		}
		
		List<List<String>> selectOutput = GraphUtils.mergeLists(selectNode.getChoices(), resultListNode.getChoices());
		log.info("selectOutput: {}", selectOutput);
		
		for( List<String> parts: selectOutput ) {
			log.info("output: {}", Joiner.on(' ').join(parts));
		}
	}
	
	private static final Pattern templatePattern = Pattern.compile("\\$\\{([^}]+)\\}");
	private static final Function<List<String>, String> firstItem = new Function<List<String>, String>() {
		public String apply(List<String> arg) {
			return arg.get(0);
		}
	};
	
	private List<String> asFlatOrNull(List<List<String>> choices) {
		for( List<String> pieces: choices ) {
			if( pieces.size() > 1 )
				return null;
		}
		return Lists.transform(choices, firstItem);
	}
	
	private List<List<String>> expandTemplates(LabelNode vertex) {
		
		// map template edge keys/labels to the choices generated by the node
		Map<String, List<String>> templateToChoices = null;
		for( TranslationEdge edge: outgoingEdgesOf(vertex) ) {
			if( !(edge instanceof TemplateEdge) ) 
				continue;
			
			LabelNode target = getEdgeTarget(edge);
			List<String> templateChoices = asFlatOrNull(expandTemplates(target));
			if( templateChoices == null ) {
				log.warn("Template had non-flat expansion. arg={}, target={}", edge.getLabel(), target);
				continue;
			}
			
			if( templateToChoices == null ) {
				templateToChoices = new LinkedHashMap<String, List<String>>();
			}
			templateToChoices.put(edge.getLabel(), templateChoices);
		}
		
		// no template args then nothing to do
		List<List<String>> choices = vertex.getChoices();
		if( templateToChoices == null )
			return choices;
		
		List<String> flat = asFlatOrNull(choices);
		if( flat == null ) {
			log.debug("Choices are not flat, returning as is: {}", choices);
		}
		
		
		List<String> temp = new ArrayList<String>(flat.size());
		boolean wasExpanded = false;
		do {
			wasExpanded = false;
			for( String toExpand: flat) {
				Matcher m = templatePattern.matcher(toExpand);
				if( !m.find() ) {
					temp.add(toExpand);
					continue;
				}
				wasExpanded = true;
				List<String> subs = templateToChoices.get(m.group(1));
				for( String sub: subs ) {
					temp.add(
						toExpand.substring(0, m.start()) +
						sub +
						toExpand.substring(m.end())
					);
				}
			}
			flat = temp; temp = new ArrayList<String>(flat.size());
		} while(wasExpanded);
		
		return GraphUtils.singletonLists(flat);
	}
	
	public List<LabelNode> getChildrenOf(LabelNode node) {
		if( !this.containsVertex(node) ) {
			throw new IllegalArgumentException("node is not in graph");
		}
		
		ArrayList<LabelNode> children = new ArrayList<LabelNode>();
		Set<TranslationEdge> edges = this.edgesOf(node);
		for( TranslationEdge edge: edges ) {
			if( edge.isChildEdge() && this.getEdgeSource(edge).equals(node) ) {
				children.add(this.getEdgeTarget(edge));
			}
		}
		return children;
	}
	
	protected void addTableNodes(SelectNode select) {
		try {
			select.getFromList().accept(new Visitor() {
				@Override
				public Visitable visit(Visitable node) throws StandardException {
					QueryTreeNode astNode = (QueryTreeNode)node;
					if( NodeTypes.FROM_BASE_TABLE == astNode.getNodeType() ) {
						FromBaseTable fbt = (FromBaseTable)astNode;
						LabelNode vertex = new LabelNode();
						vertex.setAstNode(fbt);
						addVertex(vertex);
					}
					return node;
				}
				
				@Override
				public boolean visitChildrenFirst(Visitable node) {
					return false;
				}
				
				@Override
				public boolean skipChildren(Visitable node) throws StandardException {
					return false;
				}
				
				@Override
				public boolean stopTraversal() {
					return false;
				}
			});
		} catch( StandardException e ) {
			throw new SQLTutorException(e);
		}
	}
	
	/**
	 * Returns the label node for a particular table, given its 
	 * exposed name.
	 * <p>
	 * For example, in <code>... FROM employee</code>, "employee" is the exposed name.  
	 * In <code>... FROM employee e1</code>, "e1" is the exposed name.
	 * </p>
	 * 
	 * @param exposedName the table's exposed name
	 * @return the label node if present, null if no such node has been added
	 */
	public LabelNode getTableVertex(String exposedName) {
		if( exposedName == null ) throw new NullPointerException("exposedName is null");
		
		LabelNode node = tableAliasMap.get(exposedName);
		if( !this.containsVertex(node) ) {
			log.warn("Removing alias '{}', vertex no longer present.", exposedName);
			tableAliasMap.remove(exposedName);
			return null;
		}
		return node;
	}
	
	public LabelNode getVertexForAST(QueryTreeNode node) {
		return astToVertex.get(node);
	}
	
	@Override
	public boolean addVertex(LabelNode v) {
		boolean retVal = super.addVertex(v);
		if( retVal ) {
			QueryTreeNode astNode = v.getAstNode();

			// maintain AST and table alias maps
			if( astNode != null ) {
				astToVertex.put(v.getAstNode(), v);
				try {
					if( astNode instanceof FromTable )
						tableAliasMap.put(((FromTable)astNode).getExposedName(), v);
				} catch( StandardException e ) {
					log.warn("Could not get exposed name of node: {}", astNode);
				}
			}
		}
		return retVal;
	}
	
	@Override
	public boolean removeVertex(LabelNode v) {
		boolean retVal = super.removeVertex(v);
		if( retVal ) {

			// maintain AST and table alias maps
			QueryTreeNode astNode = v.getAstNode();
			if( astNode != null ) {
				astToVertex.remove(astNode);
				try {
					if( astNode instanceof FromTable )
						tableAliasMap.remove(((FromTable)astNode).getExposedName());
				} catch( StandardException ignore ) { }
			}
		}
		return retVal;
	}
	
	@Override
	public TranslationEdge addEdge(LabelNode sourceVertex, LabelNode targetVertex) {
		// TODO Auto-generated method stub
		return super.addEdge(sourceVertex, targetVertex);
	}
}