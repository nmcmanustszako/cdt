/*******************************************************************************
 * Copyright (c) 2009 Alena Laskavaia 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia  - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.codan.internal.core.cfg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import org.eclipse.cdt.codan.provisional.core.model.cfg.IBasicBlock;
import org.eclipse.cdt.codan.provisional.core.model.cfg.IConnectorNode;
import org.eclipse.cdt.codan.provisional.core.model.cfg.IControlFlowGraph;
import org.eclipse.cdt.codan.provisional.core.model.cfg.IDecisionArc;
import org.eclipse.cdt.codan.provisional.core.model.cfg.IDecisionNode;
import org.eclipse.cdt.codan.provisional.core.model.cfg.IExitNode;
import org.eclipse.cdt.codan.provisional.core.model.cfg.ISingleOutgoing;
import org.eclipse.cdt.codan.provisional.core.model.cfg.IStartNode;

/**
 * TODO: add description
 */
public class ControlFlowGraph implements IControlFlowGraph {
	private List<IExitNode> exitNodes;
	private List<IBasicBlock> deadNodes = new ArrayList<IBasicBlock>();
	private IStartNode start;

	public ControlFlowGraph(IStartNode start, Collection<IExitNode> exitNodes) {
		setExitNodes(exitNodes);
		this.start = start;
	}

	public Iterator<IExitNode> getExitNodeIterator() {
		return exitNodes.iterator();
	}

	public int getExitNodeSize() {
		return exitNodes.size();
	}

	public void setExitNodes(Collection<IExitNode> exitNodes) {
		if (this.exitNodes != null)
			throw new IllegalArgumentException(
					"Cannot modify already exiting connector"); //$NON-NLS-1$
		this.exitNodes = Collections.unmodifiableList(new ArrayList<IExitNode>(
				exitNodes));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @seeorg.eclipse.cdt.codan.provisional.core.model.cfg.IControlFlowGraph#
	 * getStartNode()
	 */
	public IStartNode getStartNode() {
		return start;
	}

	void setStartNode(IStartNode start) {
		this.start = start;
	}

	public void print(IBasicBlock node) {
		System.out.println(node.getClass().getSimpleName() + ": "
				+ ((AbstractBasicBlock) node).toStringData());
		if (node instanceof IConnectorNode)
			if (((IConnectorNode) node).hasBackwardIncoming() == false)
				return;
		if (node instanceof IDecisionNode) {
			// todo
			Iterator<IDecisionArc> decisionArcs = ((IDecisionNode) node)
					.getDecisionArcs();
			for (; decisionArcs.hasNext();) {
				IDecisionArc arc = decisionArcs.next();
				System.out.println("{" + arc.getIndex() + ":");
				print(arc.getOutgoing());
				System.out.println("}");
			}
			print(((IDecisionNode) node).getConnectionNode());
		} else if (node instanceof ISingleOutgoing) {
			print(((ISingleOutgoing) node).getOutgoing());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @seeorg.eclipse.cdt.codan.provisional.core.model.cfg.IControlFlowGraph#
	 * getUnconnectedNodeIterator()
	 */
	public Iterator<IBasicBlock> getUnconnectedNodeIterator() {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @seeorg.eclipse.cdt.codan.provisional.core.model.cfg.IControlFlowGraph#
	 * getUnconnectedNodeSize()
	 */
	public int getUnconnectedNodeSize() {
		// TODO Auto-generated method stub
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.cdt.codan.provisional.core.model.cfg.IControlFlowGraph#getNodes
	 * ()
	 */
	public Collection<IBasicBlock> getNodes() {
		Collection<IBasicBlock> result = new LinkedHashSet<IBasicBlock>();
		getNodes(getStartNode(), result);
		for (Iterator iterator = deadNodes.iterator(); iterator.hasNext();) {
			IBasicBlock d = (IBasicBlock) iterator.next();
			getNodes(d, result);
		}
		return result;
	}

	/**
	 * @param d
	 * @param result
	 */
	private void getNodes(IBasicBlock start, Collection<IBasicBlock> result) {
		if (result.contains(start))
			return;
		result.add(start);
		for (Iterator<IBasicBlock> outgoingIterator = start
				.getOutgoingIterator(); outgoingIterator.hasNext();) {
			IBasicBlock b = outgoingIterator.next();
			getNodes(b, result);
		}
	}
}