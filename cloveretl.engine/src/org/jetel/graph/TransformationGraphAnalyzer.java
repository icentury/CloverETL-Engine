/*
 * jETeL/CloverETL - Java based ETL application framework.
 * Copyright (c) Javlin, a.s. (info@cloveretl.com)
 *  
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jetel.graph;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetel.enums.EdgeTypeEnum;
import org.jetel.enums.EnabledEnum;
import org.jetel.exception.ConfigurationStatus;
import org.jetel.exception.ConfigurationStatus.Priority;
import org.jetel.exception.ConfigurationStatus.Severity;
import org.jetel.exception.GraphConfigurationException;
import org.jetel.exception.JetelRuntimeException;
import org.jetel.exception.RecursiveSubgraphException;
import org.jetel.graph.analyse.FastPropagateSubgraphInspector;
import org.jetel.graph.analyse.GraphCycleInspector;
import org.jetel.graph.analyse.LoopsInspector;
import org.jetel.graph.analyse.SingleGraphProvider;
import org.jetel.graph.modelview.MVComponent;
import org.jetel.graph.modelview.MVEdge;
import org.jetel.graph.modelview.MVGraph;
import org.jetel.graph.modelview.MVMetadata;
import org.jetel.graph.modelview.impl.MVEngineGraph;
import org.jetel.graph.modelview.impl.MetadataPropagationResolver;
import org.jetel.graph.runtime.GraphRuntimeContext;
import org.jetel.graph.runtime.SingleThreadWatchDog;
import org.jetel.metadata.DataRecordMetadata;
import org.jetel.metadata.MetadataUtils;
import org.jetel.util.GraphUtils;
import org.jetel.util.Pair;
import org.jetel.util.SubgraphUtils;

/*
 *  import org.apache.log4j.Logger;
 *  import org.apache.log4j.BasicConfigurator;
 */
/**
 * A class that analyzes relations between Nodes and Edges of the Transformation Graph
 * 
 * @author D.Pavlis
 * @since April 2, 2002
 * @see OtherClasses
 */

public class TransformationGraphAnalyzer {

	static Log logger = LogFactory.getLog(TransformationGraphAnalyzer.class);

	static PrintStream log = System.out;// default info messages to stdout

	/**
	 * Several pre-execution steps is performed in this graph analysis.
	 * - disable nodes are removed from graph
	 * - subgraph related updates are performed
	 * - automatic metadata propagation is performed
	 * - correct edge types are detected
	 */
	public static void analyseGraph(TransformationGraph graph, GraphRuntimeContext runtimeContext, boolean propagateMetadata) {
		// analyze blockers and blocked components before we edit the graph
		TransformationGraphAnalyzer.computeBlockedComponents(graph);
		
        //remove disabled components and their edges
		try {
			TransformationGraphAnalyzer.disableNodesInPhases(graph);
		} catch (GraphConfigurationException e) {
			throw new JetelRuntimeException("Removing disabled nodes failed.", e);
		}

		//consolidate subgraph ports - create missing subgraph ports
		consolidateSubgraphPorts(graph);

		//remove optional input and output edges in subgraphs
		removeOptionalEdges(graph);
		
		//analyse subgraph - check layout and removes debug components if necessary
		boolean subJobRuntime = runtimeContext.getJobType().isSubJob();
		boolean subJobFile = runtimeContext.getJobType().isSubJob() || graph.getStaticJobType().isSubJob();
		if (subJobRuntime || subJobFile) {
			try {
				boolean removeDebugNodes = subJobRuntime;
				boolean layoutChecking = subJobFile;
				TransformationGraphAnalyzer.analyseSubgraph(graph, removeDebugNodes, layoutChecking);
			} catch (Exception e) {
				throw new JetelRuntimeException("Subgraph analysis failed.", e);
			}
		}
		
		//perform automatic metadata propagation
		if (propagateMetadata) {
			//create model view for the graph
			MVGraph mvGraph = new MVEngineGraph(graph, null);
			//first analyse subgraphs calling hierarchy - cannot be recursive
			TransformationGraphAnalyzer.analyseSubgraphCallingHierarchy(mvGraph);
			try {
				TransformationGraphAnalyzer.analyseMetadataPropagation(mvGraph);
			} catch (Exception e) {
				throw new JetelRuntimeException("Metadata propagation analysis failed.", e);
			}
			//compare implicit metadata with persisted implicit metadata
			//this validation is now temporary turned off - will be enabled in future releases (maybe) - see CLO-4144
			//validateImplicitMetadata(mvGraph);
		}
		
		try {
			TransformationGraphAnalyzer.removeBlockedNodes(graph);
		} catch (GraphConfigurationException e) {
			throw new JetelRuntimeException("Removing blocked nodes failed.", e);
		}

        //analyze type of edges - specially buffered and phase edges
        try {
        	TransformationGraphAnalyzer.analyseEdgeTypes(graph, runtimeContext);
		} catch (Exception e) {
			throw new JetelRuntimeException("Edge type analysis failed.", e);
		}
        
        graph.setAnalysed(true);
	}
	
	/**
	 * Creates missing subgraph input and output ports based on edges attached to SubgraphInput/Output components.
	 * This is necessary for backward compatibility with subgraphs created in previous version (rel-4-0), where
	 * subgraph ports do not have model in TransformationGraph.
	 * @param graph
	 */
	private static void consolidateSubgraphPorts(TransformationGraph graph) {
		if (graph.getStaticJobType().isSubJob()) {
			for (int i = graph.getSubgraphInputPorts().getPorts().size(); i <= graph.getSubgraphInputComponent().getOutputPortsMaxIndex(); i++) {
				graph.getSubgraphInputPorts().getPorts().add(
						new SubgraphInputPort(graph.getSubgraphInputPorts(), i, true, true, true));
			}
			for (int i = graph.getSubgraphOutputPorts().getPorts().size(); i <= graph.getSubgraphOutputComponent().getInputPortsMaxIndex(); i++) {
				graph.getSubgraphOutputPorts().getPorts().add(
						new SubgraphOutputPort(graph.getSubgraphOutputPorts(), i, true, true, true));
			}
		}
	}

	/**
	 * This method removes all edges which are connected to subgraph's ports,
	 * which are optional, where the related edge should be removed (SubgraphPort.isKeptEdge() == false).
	 */
	private static void removeOptionalEdges(TransformationGraph graph) {
		try {
			for (SubgraphPort subgraphPort : graph.getSubgraphInputPorts().getPorts()) {
				if (!subgraphPort.isRequired() && !subgraphPort.isKeptEdge() && !subgraphPort.isConnected()) {
					//remove the edge
					OutputPort outputPort = graph.getSubgraphInputComponent().getOutputPort(subgraphPort.getIndex());
					if (outputPort != null) {
						Edge edge = outputPort.getEdge();
						graph.deleteEdge(edge);
						edge.getReader().removeInputPort(edge);
						edge.getWriter().removeOutputPort(edge);
					}
				}
			}
	
			for (SubgraphPort subgraphPort : graph.getSubgraphOutputPorts().getPorts()) {
				if (!subgraphPort.isRequired() && !subgraphPort.isKeptEdge() && !subgraphPort.isConnected()) {
					InputPort inputPort = graph.getSubgraphOutputComponent().getInputPort(subgraphPort.getIndex());
					if (inputPort != null) {
						Edge edge = inputPort.getEdge();
						graph.deleteEdge(edge);
						edge.getReader().removeInputPort(edge);
						edge.getWriter().removeOutputPort(edge);
					}
				}
			}
		} catch (Exception e) {
			throw new JetelRuntimeException("Subgraph port optional edges cannot be removed.", e);
		}
	}

	/**
	 * Check whether subgraph calling hierarchy of the given graph is not recursive.
	 * @param graph
	 */
	private static void analyseSubgraphCallingHierarchy(MVGraph graph) {
		analyseSubgraphCallingHierarchy(graph, null, new ArrayList<String>());
	}
	
	private static void analyseSubgraphCallingHierarchy(MVGraph graph, MVComponent causedComponent, List<String> urlStack) {
		boolean topLevel = urlStack.isEmpty();
		String url = graph.getModel().getRuntimeContext().getJobUrl();
		if (urlStack.contains(url)) {
			// CLO-4930:
			throw new RecursiveSubgraphException("Recursive subgraph hierarchy detected in " + url, causedComponent.getModel());
		} else {
			urlStack.add(url);
		}
		for (Entry<MVComponent, MVGraph> subgraph : graph.getMVSubgraphs().entrySet()) {
			if (topLevel) {
				causedComponent = subgraph.getKey();
			}
			analyseSubgraphCallingHierarchy(subgraph.getValue(), causedComponent, urlStack);
		}
		urlStack.remove(url);
	}

	/**
	 * Performs automatic metadata propagation on the given graph.
	 */
	private static void analyseMetadataPropagation(MVGraph mvGraph) {
		//craete metatadata propagation resolver
		MetadataPropagationResolver metadataPropagationResolver = new MetadataPropagationResolver(mvGraph);
		//analyse the graph
		metadataPropagationResolver.analyseGraph();
		//copy propagated metadata into transformation graph
		for (Edge edge : mvGraph.getModel().getEdges().values()) {
			MVMetadata metadata = metadataPropagationResolver.getMVEdge(edge).getMetadata();
			if (metadata != null) {
				edge.setMetadata(metadata.getModel());
			}
		}
		//store complete resolver into graph for further usage (mainly in designer)
		mvGraph.getModel().setMetadataPropagationResolver(metadataPropagationResolver);
	}

	/**
	 * Compares calculated implicit metadata with persisted implicit metadata,
	 * should be identical.
	 * @param mvGraph
	 */
	private static void validateImplicitMetadata(MVGraph mvGraph) {
		for (MVEdge mvEdge : mvGraph.getMVEdges().values()) {
			if (mvEdge.hasImplicitMetadata()) {
				MVMetadata mvImplicitMetadata = mvEdge.getMetadata();
				if (mvImplicitMetadata != null) {
					DataRecordMetadata implicitMetadata = mvImplicitMetadata.getModel();
					DataRecordMetadata persistedImplicitMetadata = mvEdge.getModel().getPersistedImplicitMetadata();
					if (implicitMetadata != null && persistedImplicitMetadata != null
							&& !MetadataUtils.equals(implicitMetadata, persistedImplicitMetadata)) {
						//TODO improve exception message
						throw new JetelRuntimeException("Metadata conflict detected");
					}
				}
			}
		}
		
	}
	
	private static final class SubgraphAnalyzer {
		private final TransformationGraph subgraph;

		public SubgraphAnalyzer(TransformationGraph subgraph) {
			this.subgraph = subgraph;
		}
		
		public SubgraphAnalysisResult analyzeForRuntime() {
			return analyze(false);
		}
		
		public SubgraphAnalysisResult analyzeForValidation() {
			return analyze(true);
		}
		
		private SubgraphAnalysisResult analyze(boolean layoutChecking) {
			SubgraphAnalysisResult result = new SubgraphAnalysisResult();
			
			result.setSubgraph(subgraph);
			Collection<Node> nodes = subgraph.getNodes().values();
			for (Node component : nodes) {
				if (SubgraphUtils.isSubJobInputComponent(component.getType())) {
					result.setSubgraphInput(component);
					result.setSubgraphInputPrecedentNodes(TransformationGraphAnalyzer.findPrecedentNodesRecursive(component, null));
					if (layoutChecking) {
						result.setSubgraphInputFollowingNodes(TransformationGraphAnalyzer.findFollowingNodesRecursive(component, null));
					}
				}
				if (SubgraphUtils.isSubJobOutputComponent(component.getType())) {
					result.setSubgraphOutput(component);
					result.setSubgraphOutputFollowingNodes(TransformationGraphAnalyzer.findFollowingNodesRecursive(component, null));
					if (layoutChecking) {
						result.setSubgraphOutputPrecedentNodes(TransformationGraphAnalyzer.findPrecedentNodesRecursive(component, null));
					}
				}
			}
			
			detectInputOutputComponents(result, nodes);
			
			return result;
		}

		private void detectInputOutputComponents(SubgraphAnalysisResult result, Collection<Node> nodes) {
			Set<Node> debugInputNodes = new HashSet<>();
			Set<Node> debugOutputNodes = new HashSet<>();
			Set<Node> activeNodes = new HashSet<>();
			
			for (Node component : nodes) {
				if (component.isPartOfDebugInput()) {
					debugInputNodes.add(component);
				} else if (component.isPartOfDebugOutput()) {
					debugOutputNodes.add(component);
				} else {
					activeNodes.add(component);
				}
			}
			
			result.setDebugInputNodes(debugInputNodes);
			result.setDebugOutputNodes(debugOutputNodes);
			result.setActiveNodes(activeNodes);
		}
	}
	
	private static class SubgraphAnalysisValidationException extends Exception {
		private static final long serialVersionUID = -8502212649423859074L;

		public SubgraphAnalysisValidationException(String message) {
			super(message);
		}
	}
	
	private static class SubgraphAnalysisResult {
		private TransformationGraph subgraph;
		private Node subgraphInput;
		private Node subgraphOutput;
		
		private List<Node> subgraphInputPrecedentNodes;
		private List<Node> subgraphInputFollowingNodes;
		
		private List<Node> subgraphOutputPrecedentNodes;
		private List<Node> subgraphOutputFollowingNodes;
		private Set<Node> debugInputNodes;
		private Set<Node> debugOutputNodes;
		private Set<Node> activeNodes;
		
		public TransformationGraph getSubgraph() {
			return subgraph;
		}
		public void setSubgraph(TransformationGraph subgraph) {
			this.subgraph = subgraph;
		}
		public Node getSubgraphInput() {
			return subgraphInput;
		}
		public Set<Node> getActiveNodes() {
			return activeNodes;
		}
		public void setActiveNodes(Set<Node> activeNodes) {
			this.activeNodes = activeNodes;
		}
		public Set<Node> getDebugInputNodes() {
			return debugInputNodes;
		}
		public void setDebugInputNodes(Set<Node> debugInputNodes) {
			this.debugInputNodes = debugInputNodes;
		}
		public Set<Node> getDebugOutputNodes() {
			return debugOutputNodes;
		}
		public void setDebugOutputNodes(Set<Node> debugOutputNodes) {
			this.debugOutputNodes = debugOutputNodes;
		}
		public void setSubgraphInput(Node subgraphInput) {
			this.subgraphInput = subgraphInput;
		}
		public Node getSubgraphOutput() {
			return subgraphOutput;
		}
		public void setSubgraphOutput(Node subgraphOutput) {
			this.subgraphOutput = subgraphOutput;
		}
		public List<Node> getSubgraphInputPrecedentNodes() {
			return subgraphInputPrecedentNodes;
		}
		public void setSubgraphInputPrecedentNodes(List<Node> subgraphInputPrecedentNodes) {
			this.subgraphInputPrecedentNodes = subgraphInputPrecedentNodes;
		}
		public List<Node> getSubgraphInputFollowingNodes() {
			return subgraphInputFollowingNodes;
		}
		public void setSubgraphInputFollowingNodes(List<Node> subgraphInputFollowingNodes) {
			this.subgraphInputFollowingNodes = subgraphInputFollowingNodes;
		}
		public List<Node> getSubgraphOutputPrecedentNodes() {
			return subgraphOutputPrecedentNodes;
		}
		public void setSubgraphOutputPrecedentNodes(List<Node> subgraphOutputPrecedentNodes) {
			this.subgraphOutputPrecedentNodes = subgraphOutputPrecedentNodes;
		}
		public List<Node> getSubgraphOutputFollowingNodes() {
			return subgraphOutputFollowingNodes;
		}
		public void setSubgraphOutputFollowingNodes(List<Node> subgraphOutputFollowingNodes) {
			this.subgraphOutputFollowingNodes = subgraphOutputFollowingNodes;
		}
		public void validate() throws SubgraphAnalysisValidationException {
			ConfigurationStatus subgraphStatus = subgraph.getPreCheckConfigStatus();

			if (getSubgraphInput() == null) {
				subgraphStatus.add("Missing SubgraphInput component.", Severity.ERROR, getSubgraph(), Priority.NORMAL);
				return;
			}

			if (getSubgraphOutput() == null) {
				subgraphStatus.add("Missing SubgraphOutput component.", Severity.ERROR, getSubgraph(), Priority.NORMAL);
				return;
			}

			//phase order check
			if (getSubgraphInput().getPhaseNum() > getSubgraphOutput().getPhaseNum() &&  !GraphUtils.hasEdge(getSubgraphInput(), getSubgraphOutput())) {
				subgraphStatus.add("Invalid phase order. Phase number of SubgraphInput is greater than SubgraphOutput's phase number.", Severity.ERROR, getSubgraphInput(), Priority.NORMAL);
				subgraphStatus.add("Invalid phase order. Phase number of SubgraphInput is greater than SubgraphOutput's phase number.", Severity.ERROR, getSubgraphOutput(), Priority.NORMAL);
			}
			for (Node component : getSubgraph().getNodes().values()) {
				if (component.isPartOfDebugInput()) {
					if (component.getPhaseNum() > getSubgraphInput().getPhaseNum() && !GraphUtils.hasEdge(component, getSubgraphOutput())) {
						subgraphStatus.add("Invalid phase order. Phase number of component " + component + " is greater than SubgraphInput's phase number.", Severity.ERROR, component, Priority.NORMAL);
					}
				} else if (component.isPartOfDebugOutput()) {
					if (component.getPhaseNum() < getSubgraphOutput().getPhaseNum() && !GraphUtils.hasEdge(getSubgraphOutput(), component)) {
						subgraphStatus.add("Invalid phase order. Phase number of component " + component + " is less than SubgraphOutput's phase number.", Severity.ERROR, component, Priority.NORMAL);
					}
				} else {
					if (component.getPhaseNum() < getSubgraphInput().getPhaseNum() && !GraphUtils.hasEdge(getSubgraphInput(), component)) {
						subgraphStatus.add("Invalid phase order. Phase number of component " + component + " is less than SubgraphInput's phase number.", Severity.ERROR, component, Priority.NORMAL);
					}
					if (component.getPhaseNum() > getSubgraphOutput().getPhaseNum() && !GraphUtils.hasEdge(component, getSubgraphOutput())) {
						subgraphStatus.add("Invalid phase order. Phase number of component " + component + " is greater than SubgraphOutput's phase number.", Severity.ERROR, component, Priority.NORMAL);
					}
				}
			}
			
			//graph layout - edges routing
			for (Edge edge : getSubgraph().getEdges().values()) {
				Node reader = edge.getReader();
				Node writer = edge.getWriter();
				
				if (reader != null && writer != null) {
					validateEdge(subgraphStatus, writer, reader);
				}
			}
		}
		
		/**
		 * Checks all invalid combination of the edge routing.
		 */
		private void validateEdge(ConfigurationStatus subgraphStatus, Node from, Node to) {
			if (from == getSubgraphInput()) {
				if (to == getSubgraphInput()) {
					reportError(subgraphStatus, from, to);
				} else if (to != getSubgraphOutput()) {
					if (to.isPartOfDebugInput()) {
						reportError(subgraphStatus, from, to);
					} else if (to.isPartOfDebugOutput()) {
						reportError(subgraphStatus, from, to);
					}
				}
			} else if (from == getSubgraphOutput()) {
				if (to == getSubgraphOutput()) {
					reportError(subgraphStatus, from, to);
				} else if (to == getSubgraphInput()) {
					reportError(subgraphStatus, from, to);
				} else if (to.isPartOfDebugInput()) {
					reportError(subgraphStatus, from, to);
				} else if (!to.isPartOfDebugOutput()) {
					reportError(subgraphStatus, from, to);
				}
			} else if (to == getSubgraphInput()) {
				if (from.isPartOfDebugOutput()) {
					reportError(subgraphStatus, from, to);
				} else if (!from.isPartOfDebugInput()) {
					reportError(subgraphStatus, from, to);
				}
			} else if (to == getSubgraphOutput()) {
				if (from.isPartOfDebugInput()) {
					reportError(subgraphStatus, from, to);
				} else if (from.isPartOfDebugOutput()) {
					reportError(subgraphStatus, from, to);
				}
			} else if (from.isPartOfDebugInput()) {
				if (!to.isPartOfDebugInput()) {
					reportError(subgraphStatus, from, to);
				}
			} else if (from.isPartOfDebugOutput()) {
				if (!to.isPartOfDebugOutput()) {
					reportError(subgraphStatus, from, to);
				}
			} else if (!from.isPartOfDebugInput() && !from.isPartOfDebugOutput()) {
				if (to.isPartOfDebugInput() || to.isPartOfDebugOutput()) {
					reportError(subgraphStatus, from, to);
				}
			}
		}
		
		private void reportError(ConfigurationStatus subgraphStatus, Node from, Node to) {
			subgraphStatus.add("Invalid subgraph layout. Edge from " + from + " to " + to + " is not allowed.", Severity.ERROR, from, Priority.NORMAL);
			subgraphStatus.add("Invalid subgraph layout. Edge from " + from + " to " + to + " is not allowed.", Severity.ERROR, to, Priority.NORMAL);
		}
	}

	/**
	 * Checks subgraph layout and removes all components before SubgraphInput and after SubgraphOutput.
	 */
	public static void analyseSubgraph(TransformationGraph graph, boolean removeDebugNodes, boolean layoutChecking) {
		SubgraphAnalyzer analyzer = new SubgraphAnalyzer(graph);
		SubgraphAnalysisResult analysisResult = layoutChecking ? analyzer.analyzeForValidation() : analyzer.analyzeForRuntime();
		if (layoutChecking) {
			try {
				analysisResult.validate();
			} catch (SubgraphAnalysisValidationException e) {
				throw new JetelRuntimeException("Invalid subgraph layout.", e);
			}
		}
		
		if (removeDebugNodes) {
			removeDebugNodes(graph, analysisResult);
		}
	}

	private static void removeDebugNodes(TransformationGraph graph, SubgraphAnalysisResult analysisResult) {
		for (Node node : analysisResult.getDebugInputNodes()) {
			node.setEnabled(EnabledEnum.NEVER);
		}
		for (Node node : analysisResult.getDebugOutputNodes()) {
			node.setEnabled(EnabledEnum.NEVER);
		}

		try {
			TransformationGraphAnalyzer.disableNodesInPhases(graph);
		} catch (GraphConfigurationException e) {
			throw new JetelRuntimeException("Failed to remove disabled/pass-through nodes from subgraph.", e);
		}
	}

	/**
	 * Detects suitable type of edges for the given graph. Edge types are preset
	 * directly to the graph instance.
	 */
	public static void analyseEdgeTypes(TransformationGraph graph, GraphRuntimeContext runtimeContext) {
		if (runtimeContext == null) {
			runtimeContext = graph.getRuntimeContext();
		}
		
		//first of all find the phase edges
		analysePhaseEdges(graph);

		//let's find cycles of relationships in the graph and interrupted them by buffered edges to avoid deadlocks
		GraphCycleInspector graphCycleInspector = new GraphCycleInspector(new SingleGraphProvider(graph));
		graphCycleInspector.inspectGraph();
		
		//make all edges around loop component fast propagate
		LoopsInspector.inspectEdgesInLoops(graph);
		
		//if the subgraph is executed in fast-propagate mode
		//all edges between SGI and SGO components have to fast-propagated
		//let's find all these edges and change edge type to a fast-propagate variant
		if (runtimeContext.isFastPropagateExecution()) {
			FastPropagateSubgraphInspector.inspectEdges(graph);
		}
		
		//update edge types around Subgraph components
		//real edge is combination of parent graph edge type and subgraph edge type
		//this is turned off - parent graph is not changed according child graph, at least for now
//		for (Node component : graph.getNodes().values()) {
//			if (component instanceof SubgraphComponent) {
//				SubgraphComponent subgraphComponent = (SubgraphComponent) component;
//				for (Entry<Integer, InputPort> inputPort : component.getInputPorts().entrySet()) {
//					Edge subgraphEdge = subgraphComponent.getSubgraphInputEdge(inputPort.getKey());
//					Edge parentGraphEdge = inputPort.getValue().getEdge();
//					//will be edge base shared between these two edges?
//					if (SubgraphUtils.isSubgraphInputEdgeShared(subgraphEdge, parentGraphEdge)) {
//						//so we need to combine both edge types to satisfy needs of both parent and subgraph
//						EdgeTypeEnum combinedEdgeType = GraphUtils.combineEdges(parentGraphEdge.getEdgeType(), subgraphEdge.getEdgeType());
//						parentGraphEdge.setEdgeType(combinedEdgeType);
//					}
//				}
//				for (Entry<Integer, OutputPort> outputPort : component.getOutputPorts().entrySet()) {
//					Edge subgraphEdge = subgraphComponent.getSubgraphOutputEdge(outputPort.getKey());
//					Edge parentGraphEdge = outputPort.getValue().getEdge();
//					//will be edge base shared between these two edges?
//					if (SubgraphUtils.isSubgraphOutputEdgeShared(subgraphEdge, parentGraphEdge)) {
//						//so we need to combine both edge types to satisfy needs of both parent and subgraph
//						EdgeTypeEnum combinedEdgeType = GraphUtils.combineEdges(parentGraphEdge.getEdgeType(), subgraphEdge.getEdgeType());
//						parentGraphEdge.setEdgeType(combinedEdgeType);
//					}
//				}
//			}
//		}
	}

	private static void analysePhaseEdges(TransformationGraph graph) {
		Phase readerPhase;
		Phase writerPhase;

		// analyse edges (whether they need to be buffered and put them into proper phases
		// edges connecting nodes from two different phases has to be put into both phases
		for (Edge edge : graph.getEdges().values()) {
			Node reader = edge.getReader(); //can be null for remote edges
			Node writer = edge.getWriter(); //can be null for remote edges
			readerPhase = reader != null ? reader.getPhase() : null;
			writerPhase = writer != null ? writer.getPhase() : null;
			if (readerPhase.getPhaseNum() > writerPhase.getPhaseNum()) {
				// edge connecting two nodes belonging to different phases
				// has to be buffered
				edge.setEdgeType(EdgeTypeEnum.PHASE_CONNECTION);
			}
		}
	}

	/**
	 * Apply disabled property of node to graph. Called in graph initial phase.
	 * 
	 * @throws GraphConfigurationException
	 */
	public static void disableNodesInPhases(TransformationGraph graph) throws GraphConfigurationException {
		Set<Node> nodesToRemove = new HashSet<Node>();
		Phase[] phases = graph.getPhases();

		for (int i = 0; i < phases.length; i++) {
			nodesToRemove.clear();
			for (Node node : phases[i].getNodes().values()) {
				if (node.getEnabled() == EnabledEnum.DISCARD) { // component and all related edges are removed
					nodesToRemove.add(node);
					disconnectAllEdges(node);
				} else if (!node.getEnabled().isEnabled()) { // component and related edges is substituted by 'passThrough' edge
					nodesToRemove.add(node);
					final InputPort inputPort = node.getInputPort(node.getPassThroughInputPort());
					final OutputPort outputPort = node.getOutputPort(node.getPassThroughOutputPort());
					if (inputPort == null || outputPort == null
					// if the component has an output edge which is directly connected into its input port
					// whole component is removed even with the edge
					// this is not normally possible however see issue #4960
					|| inputPort.getEdge() == outputPort.getEdge()) {
						disconnectAllEdges(node);
						continue;
					}
					final Edge inEdge = inputPort.getEdge();
					final Edge outEdge = outputPort.getEdge();
					final Node sourceNode = inEdge.getWriter();
					final Node targetNode = outEdge.getReader();
					final int sourceIdx = inEdge.getOutputPortNumber();
					final int targetIdx = outEdge.getInputPortNumber();
					disconnectAllEdges(node);
					sourceNode.addOutputPort(sourceIdx, inEdge);
					targetNode.addInputPort(targetIdx, inEdge);
					try {
						node.getGraph().addEdge(inEdge);
					} catch (GraphConfigurationException e) {
						logger.error(e);
					}
				}
			}
			for (Node node : nodesToRemove) {
				phases[i].deleteNode(node);
			}
		}
	}

	/**
	 * Removes blocked components from the graph
	 * @param graph
	 * @throws GraphConfigurationException
	 */
	public static void removeBlockedNodes(TransformationGraph graph) throws GraphConfigurationException {
		graph.getKeptBlockedComponents().clear();
		Set<Node> nodesToRemove = new HashSet<Node>();
		Set<String> blockedIds = graph.getBlockedIds();
		Phase[] phases = graph.getPhases();

		for (int i = 0; i < phases.length; i++) {
			nodesToRemove.clear();
			for (Node node : phases[i].getNodes().values()) {
				if (node.getEnabled() == EnabledEnum.TRASH) {
					disconnectOutputEdges(node);
				} else if (blockedIds.contains(node.getId())) { // component and all related edges are removed
					boolean keep = false; // some blocked components need to be kept (when there's enabled non-blocked component writing to them)
					for (InputPort inPort : node.getInPorts()) {
						Node predecessor = inPort.getWriter();
						if (!predecessor.getEnabled().isBlocker() && !blockedIds.contains(predecessor.getId())) {
							graph.getKeptBlockedComponents().add(node);
							keep = true;
							break;
						}
					}
					if (!keep) {
						nodesToRemove.add(node);
						disconnectAllEdges(node);
					}
				}
			}
			for (Node node : nodesToRemove) {
				phases[i].deleteNode(node);
			}
		}
	}
	
	private static void disconnectInputEdges(Node node) throws GraphConfigurationException {
		for (Iterator<InputPort> it1 = node.getInPorts().iterator(); it1.hasNext();) {
			final Edge edge = it1.next().getEdge();
			final Node writer = edge.getWriter();
			if (writer != null)
				writer.removeOutputPort(edge);
			node.getGraph().deleteEdge(edge);
			it1.remove();
		}
	}
	
	private static void disconnectOutputEdges(Node node) throws GraphConfigurationException {
		for (Iterator<OutputPort> it1 = node.getOutPorts().iterator(); it1.hasNext();) {
			final Edge edge = it1.next().getEdge();
			final Node reader = edge.getReader();
			if (reader != null)
				reader.removeInputPort(edge);
			node.getGraph().deleteEdge(edge);
			it1.remove();
		}
	}

	/**
	 * Disconnect all edges connected to the given node.
	 * 
	 * @param node
	 * @throws GraphConfigurationException
	 */
	private static void disconnectAllEdges(Node node) throws GraphConfigurationException {
		disconnectInputEdges(node);
		disconnectOutputEdges(node);		
	}
	
	/**
	 * Goes through the graph and saves information about blockers and blocked components to the graph.
	 */
	public static void computeBlockedComponents(TransformationGraph graph) {
		Map<Node, Set<Node>> blockingComponentsInfo = graph.getBlockingComponentsInfo();
		blockingComponentsInfo.clear();
		
		// stack of: Pair<source blocker component, blocked component>
		Stack<Pair<Node, Node>> stack = new Stack<>();
		
		// add blockers
		for (Node node : graph.getNodes().values()) {
			if (node.getEnabled().isBlocker()) {
				blockingComponentsInfo.put(node, new HashSet<Node>());
				stack.push(new Pair<Node, Node>(node, node));
			}
		}
		
		// add downstream nodes
		while (!stack.isEmpty()) {
			Pair<Node, Node> blockerInfo = stack.pop();
			for (OutputPort outPort : blockerInfo.getSecond().getOutPorts()) {
				Node next = outPort.getEdge().getReader();
				if (!next.getEnabled().isBlocker() // "disabled as trash" components can't be blocked
						&& !blockingComponentsInfo.get(blockerInfo.getFirst()).contains(next) // component is blocked only once per each blocker
						&& !SubgraphUtils.isSubJobInputOutputComponent(next.getType())) { // subgraphInput and subgraphOutput can't be blocked
					stack.push(new Pair<Node, Node>(blockerInfo.getFirst(), next));
					blockingComponentsInfo.get(blockerInfo.getFirst()).add(next);
				}
			}
		}
	}

	/**
	 * @param node
	 * @param reflectedNodes
	 *            reflected set of nodes, typically nodes in phase; the resulted nodes will be only from this set of
	 *            nodes
	 * @return list of all precedent nodes for given node
	 */
	public static List<Node> findPrecedentNodes(Node node, Collection<Node> reflectedNodes) {
		List<Node> result = new ArrayList<Node>();

		for (InputPort inputPort : node.getInPorts()) {
			final Node writer = inputPort.getWriter();
			if (reflectedNodes == null || reflectedNodes.contains(writer)) {
				result.add(writer);
			}
		}

		return result;
	}

	/**
	 * Finds all components which precede given root component. Recursive version of {@link #findPrecedentNodes(Node, Collection)} method.
	 */
	public static List<Node> findPrecedentNodesRecursive(Node rootComponent, Collection<Node> reflectedNodes) {
		List<Node> result = new ArrayList<Node>();
		Queue<Node> toProcess = new LinkedList<Node>();
		
		toProcess.addAll(findPrecedentNodes(rootComponent, reflectedNodes));
		while (!toProcess.isEmpty()) {
			Node component = toProcess.poll();
			if (!result.contains(component) && component != rootComponent) {
				toProcess.addAll(findPrecedentNodes(component, reflectedNodes));
				toProcess.addAll(findFollowingNodes(component, reflectedNodes));
				result.add(component);
			}
		}
		
		return result;
	}
	
	/**
	 * @param node
	 * @param reflectedNodes
	 *            reflected set of nodes, typically nodes in phase; the resulted nodes will be only from this set of
	 *            nodes
	 * @return list of all following nodes for given node
	 */
	public static List<Node> findFollowingNodes(Node node, Collection<Node> reflectedNodes) {
		List<Node> result = new ArrayList<Node>();

		for (OutputPort outputPort : node.getOutPorts()) {
			final Node reader = outputPort.getReader();
			if (reflectedNodes == null || reflectedNodes.contains(reader)) {
				result.add(reader);
			}
		}

		return result;
	}

	/**
	 * Finds all components which follow given root component. Recursive version of {@link #findFollowingNodes(Node, Collection)} method.
	 */
	public static List<Node> findFollowingNodesRecursive(Node rootComponent, Collection<Node> reflectedNodes) {
		List<Node> result = new ArrayList<Node>();
		Queue<Node> toProcess = new LinkedList<Node>();
		
		toProcess.addAll(findFollowingNodes(rootComponent, reflectedNodes));
		while (!toProcess.isEmpty()) {
			Node component = toProcess.poll();
			if (!result.contains(component) && component != rootComponent) {
				toProcess.addAll(findPrecedentNodes(component, reflectedNodes));
				toProcess.addAll(findFollowingNodes(component, reflectedNodes));
				result.add(component);
			}
		}
		
		return result;
	}

	/**
	 * Components topological sorting based on Kahn algorithm.
	 * This algorithm is used for user friendly nodes visualisation
	 * and for single thread graph execution, see {@link SingleThreadWatchDog}.
	 * 
	 * @param givenNodes scope in which the topological sorting is performed - only mentioned components are considered
	 * @return given nodes in topological order 
	 * @note algorithm is described for example at http://en.wikipedia.org/wiki/Topological_sorting
	 */
	public static List<Node> nodesTopologicalSorting(List<Node> givenNodes) {
		List<Node> result = new ArrayList<Node>();
		Stack<Node> roots = new Stack<Node>();
		List<Node> loopComponents = new ArrayList<Node>(); 
		
		// find root nodes - nodes without precedent nodes in the given list of nodes
		for (Node givenNode : givenNodes) {
			if (findPrecedentNodes(givenNode, givenNodes).isEmpty()) {
				roots.add(givenNode);
			} else {
				if (givenNode.getType().equals(LoopsInspector.LOOP_COMPONENT_TYPE)) {
					loopComponents.add(givenNode);
				}
			}
		}

		//start with topological sorting using the roots
		processRoots(givenNodes, roots, result);
		
		//if some components are no in the result - an oriented cycle has been found
		//oriented cycle is now supported only for Loop component, which is natural root of these cycles
		//let's start with topological sorting with Loop components as roots
		if (result.size() < givenNodes.size()) {
			//find all Loop components which are not in result
			for (Node loopComponent : loopComponents) {
				if (!result.contains(loopComponent)) {
					roots.add(loopComponent);
				}
			}
			
			processRoots(givenNodes, roots, result);
		}

		//seems that the graph contains some oriented cycles without Loop component
		//so take one component by one and use them as root for sorting iterations
		while (result.size() < givenNodes.size()) {
			for (Node givenNode : givenNodes) {
				if (!result.contains(givenNode)) {
					roots.add(givenNode);
					processRoots(givenNodes, roots, result);
					break;
				}
			}
		}

		return result;
	}

	private static void processRoots(List<Node> givenNodes, Stack<Node> roots, List<Node> result) {
		// topological sorting
		while (!roots.isEmpty()) {
			Node root = roots.pop();
			if (!result.contains(root)) {
				result.add(root);
				List<OutputPort> outputPorts = new ArrayList<OutputPort>(root.getOutPorts());
				//let's reverse the output ports to get more logical output
				//Loop component is only exception where reversing is not desired
				if (!root.getType().equals(LoopsInspector.LOOP_COMPONENT_TYPE)) {
					Collections.reverse(outputPorts);
				}
				
				for (OutputPort outputPort : outputPorts) {
					Node followingComponent = outputPort.getReader();
					if (givenNodes.contains(followingComponent) && !result.contains(followingComponent)) {
						boolean isNewRoot = true;
						for (InputPort inputPort : followingComponent.getInPorts()) {
							if (!result.contains(inputPort.getEdge().getWriter())
									&& givenNodes.contains(inputPort.getEdge().getWriter())) {
								isNewRoot = false;
								break;
							}
						}
						if (isNewRoot) {
							roots.push(followingComponent);
						}
					}
				}
			}
		}
	}
	
}
/*
 * end class TransformationGraphAnalyzer
 */

