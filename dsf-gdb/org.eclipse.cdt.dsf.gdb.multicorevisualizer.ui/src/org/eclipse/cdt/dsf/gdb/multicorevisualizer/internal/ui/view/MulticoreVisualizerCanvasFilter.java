/*******************************************************************************
 * Copyright (c) 2013 Ericsson
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Marc Dumais (Ericsson) - Initial API and implementation (Bug 405390)
 *     Marc Dumais (Ericsson) - Bug 407673
 *******************************************************************************/

package org.eclipse.cdt.dsf.gdb.multicorevisualizer.internal.ui.view;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.cdt.dsf.gdb.multicorevisualizer.internal.ui.MulticoreVisualizerUIPlugin;
import org.eclipse.cdt.dsf.gdb.multicorevisualizer.internal.ui.model.IVisualizerModelObject;
import org.eclipse.cdt.dsf.gdb.multicorevisualizer.internal.ui.model.VisualizerCPU;
import org.eclipse.cdt.dsf.gdb.multicorevisualizer.internal.ui.model.VisualizerCore;
import org.eclipse.cdt.dsf.gdb.multicorevisualizer.internal.ui.model.VisualizerModel;
import org.eclipse.cdt.dsf.gdb.multicorevisualizer.internal.ui.model.VisualizerThread;
import org.eclipse.cdt.visualizer.ui.util.SelectionUtils;
import org.eclipse.jface.viewers.ISelection;


/**
 * White-list Filter for the graphical objects displayed in the multicore 
 * visualizer canvas. 
 */
public class MulticoreVisualizerCanvasFilter {
		
	/** The white list */ 
	List<IVisualizerModelObject> m_filterList = null;
	/** the dynamically expanded list, containing elements in the */
	/** white list and their parents - recalculated as required */
	/** since some elements can move around and change parent */
	List<IVisualizerModelObject> m_dynamicFilterList = null;
	/** reference to the canvas */
	private MulticoreVisualizerCanvas m_canvas = null;
	
	/** is the filter is active/set */
	private boolean m_filterActive = false;	
	
	/** for stats */
	private int m_shownCpu = 0;
	/** for stats */
	private int m_shownCore = 0;
	/** for stats */
	private int m_shownThread = 0;
	/** for stats */
	private int m_totalCpu = 0;
	/** for stats */
	private int m_totalCore = 0;
	/** for stats */
	private int m_totalThread = 0;
	
	/** String constant used in this class */
	private static final String STR_FILTER_NOT_ACTIVE = MulticoreVisualizerUIPlugin.getString("MulticoreVisualizer.view.CanvasFilter.NotActive.text"); //$NON-NLS-1$
	/** String constant used in this class */
	private static final String STR_FILTER_ACTIVE = MulticoreVisualizerUIPlugin.getString("MulticoreVisualizer.view.CanvasFilter.Active.text"); //$NON-NLS-1$
	/** String constant used in this class */
	private static final String STR_CPU = MulticoreVisualizerUIPlugin.getString("MulticoreVisualizer.view.CanvasFilter.cpu.text"); //$NON-NLS-1$
	/** String constant used in this class */
	private static final String STR_CORE = MulticoreVisualizerUIPlugin.getString("MulticoreVisualizer.view.CanvasFilter.core.text"); //$NON-NLS-1$
	/** String constant used in this class */
	private static final String STR_THREAD = MulticoreVisualizerUIPlugin.getString("MulticoreVisualizer.view.CanvasFilter.thread.text"); //$NON-NLS-1$

	// --- constructors/destructors ---

	/** Constructor. */
	public MulticoreVisualizerCanvasFilter(MulticoreVisualizerCanvas canvas) {
		m_canvas = canvas;
	}
	
	/** Dispose	method */
	public void dispose() {
		clearFilter();
		m_canvas = null;
	}

	
	// --- filter methods ---
	
	/**
     * Set-up a canvas white-list filter.  Any applicable selected object is added to 
     * the filter. 
     */
    public void applyFilter() {
    	// replace current filter? Clear old one first.
        if (isFilterActive()) {
            clearFilter();
        }

        m_filterList = new ArrayList<IVisualizerModelObject>();
        m_dynamicFilterList = new ArrayList<IVisualizerModelObject>();

        m_filterActive = true;

        // get list of selected objects the filter applies-for
        ISelection selection = m_canvas.getSelection();
        List<Object> selectedObjects = SelectionUtils.getSelectedObjects(selection);
       
        for (Object obj : selectedObjects) {
        	if (obj instanceof IVisualizerModelObject) {
        		m_filterList.add((IVisualizerModelObject)obj);
        	}
        }
    }
    
	/** Removes any canvas filter currently in place */
	public void clearFilter() {
		if (m_filterList != null) {
			m_filterList.clear();
			m_filterList = null;
		}
		
		if (m_dynamicFilterList != null) {
			m_dynamicFilterList.clear();
			m_dynamicFilterList = null;
		}
		resetCounters();
		m_filterActive = false;
		
	}

	/** tells if a canvas filter is currently in place */
	public boolean isFilterActive() {
		return m_filterActive;
	}
    
    /**
     * Updates the filter to contain the up-to-date parent objects,
     * for all filter objects. 
     */
    public void updateFilter() {
    	if (m_filterList == null || m_canvas == null)
    		return;
    	
    	VisualizerModel model = m_canvas.getModel();
    	
    	resetCounters();
    	m_dynamicFilterList.clear();
    	
    	for (IVisualizerModelObject elem : m_filterList) {
    		// element still in current model? 
    		if (isElementInCurrentModel(elem)) {
    			// add element to list
    			addElementToFilterList(elem);

    			// also add all its ancestors
    			IVisualizerModelObject parent;
    			
    			// Bug 407673 - if element is a thread, lookup the parent (core) 
    			// from the current model, to be sure it's up-to-date
    			if (elem instanceof VisualizerThread && model != null) {
    				parent = model.getThread(((VisualizerThread) elem).getGDBTID()).getParent();
    			}
    			else {
    				parent = elem.getParent();
    			}
    			while (parent != null) {
    				addElementToFilterList(parent);
    				parent = parent.getParent();
    			}
    		}
    	}
    }

	/**
	 * Tells if a candidate model object should be displayed, according to the 
	 * filter in place.  
	 */
	public boolean displayObject(final IVisualizerModelObject candidate) {
		// filter not active? Let anything be displayed 
		if (!m_filterActive) {
			return true;
		}
					
		// Candidate is in white list?  
		if (isElementInFilterList(candidate)) {
			return true;
		}
		
		return false;
	}
	
	
	// --- filter list management ---
	
	/**
	 * Adds an element to the dynamic filter list, if an equivalent 
	 * element is not already there.
	 */
	private void addElementToFilterList(final IVisualizerModelObject elem) {
		if (!isElementInFilterList(elem)) {
			m_dynamicFilterList.add(elem);
			stepStatsCounter(elem);
		}
	}
	
	/**
	 * Checks if an element already has an equivalent in the 
	 * dynamic filter list.
	 */
	private boolean isElementInFilterList(final IVisualizerModelObject candidate) {
		// is the candidate in the dynamic filter list?
		for (IVisualizerModelObject elem : m_dynamicFilterList) {
			// Note: we are comparing the content (IDs), not references.  
			if (candidate.compareTo(elem) == 0) {
				return true;
			}
		}
		return false;
	}
	
	
	/** Used to check if model elements in the filter still exist in the current model.	 */
	private boolean isElementInCurrentModel(IVisualizerModelObject element) {
		VisualizerModel model = m_canvas.getModel();
		if (model != null) {
			if (element instanceof VisualizerThread) {
				VisualizerThread thread = model.getThread(((VisualizerThread) element).getGDBTID());
				if (thread != null) {
					// Note: we are comparing the content (IDs), not references.
					if (thread.compareTo(element) == 0) {
						return true;
					}
				}
			}
			else if (element instanceof VisualizerCore) {
				VisualizerCore core = model.getCore(element.getID());
				if (core != null) {
					// Note: we are comparing the content (IDs), not references.
					if (core.compareTo(element) == 0) {
						return true;
					}
				}
			}
			else if (element instanceof VisualizerCPU) {
				VisualizerCPU cpu = model.getCPU(element.getID());
				if (cpu != null) {
					// Note: we are comparing the content (IDs), not references.
					if (cpu.compareTo(element) == 0) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	// --- Stats counters ---
	
    /**	
     * Used to step the filtered counters for a given type of 
     * model object.
     */
	private void stepStatsCounter(IVisualizerModelObject modelObj) {
		if (modelObj instanceof VisualizerCPU) {
			m_shownCpu++;
		}
		else if (modelObj instanceof VisualizerCore) {
			m_shownCore++;
		}
		else if (modelObj instanceof VisualizerThread) {
			m_shownThread++;
		}
	}
	
	/**	Reset the filtering counters */
	private void resetCounters() {
		m_shownCpu = 0;
		m_shownCore = 0;
		m_shownThread = 0;
		// refresh total counts since the model can change
		if (m_canvas != null) {
			VisualizerModel model = m_canvas.getModel();
			if (model != null) {
				m_totalCpu = model.getCPUCount();
				m_totalCore = model.getCoreCount();
				m_totalThread = model.getThreadCount();
			}
		}
	}
	

	/**	returns a String giving the current filtering stats */
	private String getStats() {
		
		
		return STR_FILTER_ACTIVE + " " + STR_CPU + " " + m_shownCpu + "/" + m_totalCpu + ", " + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				STR_CORE + " " + m_shownCore + "/" + m_totalCore + ", " + //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-1$
				STR_THREAD + " " + m_shownThread + "/" + m_totalThread;    //$NON-NLS-1$//$NON-NLS-2$
	}
	
	@Override
	public String toString() {
		if (isFilterActive()) {
			return getStats();
		}
		else {
			return STR_FILTER_NOT_ACTIVE;
		}
	}
}





