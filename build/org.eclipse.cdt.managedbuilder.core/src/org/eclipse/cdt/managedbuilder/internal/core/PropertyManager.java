/*******************************************************************************
 * Copyright (c) 2006, 2007 Intel Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Intel Corporation - Initial API and implementation
 * Baltasar Belyavsky (Texas Instruments) - [405744] PropertyManager causes many unnecessary file-writes into workspace metadata
 *******************************************************************************/
package org.eclipse.cdt.managedbuilder.internal.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.eclipse.cdt.managedbuilder.core.IBuildObject;
import org.eclipse.cdt.managedbuilder.core.IBuilder;
import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.core.IManagedBuildInfo;
import org.eclipse.cdt.managedbuilder.core.IManagedProject;
import org.eclipse.cdt.managedbuilder.core.IResourceConfiguration;
import org.eclipse.cdt.managedbuilder.core.IResourceInfo;
import org.eclipse.cdt.managedbuilder.core.ITool;
import org.eclipse.cdt.managedbuilder.core.IToolChain;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.cdt.managedbuilder.core.ManagedBuilderCorePlugin;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

/**
 * This class allows specifying BuildObject-specific persisted properties
 *
 */
public class PropertyManager {
//	private static final String PROPS_PROPERTY = "properties";	//$NON-NLS-1$
//	private static final QualifiedName propsSessionProperty = new QualifiedName(ManagedBuilderCorePlugin.getUniqueIdentifier(), PROPS_PROPERTY);

	private static final String NODE_NAME = "properties";	//$NON-NLS-1$
	
	private static PropertyManager fInstance;
	
	private LoaddedInfo fLoaddedInfo;
	
	private static class LoaddedInfo {
		private final IProject fProject;
		private final String fCfgId;
		// one of Map<String, String> or Map<String, Map<String, Properties>>
		private final Map<String, Object> fCfgPropertyMap;
		
		LoaddedInfo(IProject project, String cfgId, Map<String, Object> cfgPropertyMap){
			fProject = project;
			fCfgId = cfgId;
			fCfgPropertyMap = cfgPropertyMap;
		}
		
		public IConfiguration getConfiguration(){
			return PropertyManager.getConfigurationFromId(fProject, fCfgId);
		}

		public IProject getProject(){
			return fProject;
		}

		public String getConfigurationId(){
			return fCfgId;
		}

		public Map<String, Object> getProperties(){
			return fCfgPropertyMap;
		}
		
		public boolean cfgMatch(IConfiguration cfg){
			if(fCfgId == null || fProject == null)
				return false;
			
			if(!fCfgId.equals(cfg.getId()))
				return false;
			
			if(!fProject.equals(PropertyManager.getProject(cfg)))
				return false;
			
			return true;
		}
	}

	private PropertyManager(){
	}
	
	public static PropertyManager getInstance(){
		if(fInstance == null)
			fInstance = new PropertyManager();
		return fInstance;
	}

	protected void setProperty(IConfiguration cfg, IBuildObject bo, String prop, String value){
		if(((Configuration)cfg).isPreference())
			return;
		Properties props = getProperties(cfg, bo);
		if(props != null){
			props.setProperty(prop, value);
		}
	}

	protected String getProperty(IConfiguration cfg, IBuildObject bo, String prop){
		if(((Configuration)cfg).isPreference())
			return null;
		Properties props = getProperties(cfg, bo);
		if(props != null)
			return props.getProperty(prop);
		return null;
	}
	
	protected Properties getProperties(IConfiguration cfg, IBuildObject bo){
		return loadProperties(cfg, bo);
	}
	
	private LoaddedInfo getLoaddedInfo(){
		return fLoaddedInfo;
	}

	private synchronized void setLoaddedInfo(LoaddedInfo info){
		fLoaddedInfo = info;
	}
	protected Map<String, Object> getLoaddedData(IConfiguration cfg){
		LoaddedInfo info = getLoaddedInfo();
		if(info == null)
			return null;
		
		if(!info.cfgMatch(cfg))
			return null;
		
		return info.getProperties(); 
//		Map map = null;
//		IProject proj = null;
//		try {
//			if(!((Configuration)cfg).isPreference()){
//				proj = cfg.getOwner().getProject();
//				map = (Map)proj.getSessionProperty(propsSessionProperty);
//			}
//			if(map == null){
//				map = new HashMap();
//				if(proj != null){
//					proj.setSessionProperty(propsSessionProperty, map);
//				}
//			}
//			map = (Map)map.get(cfg.getId());
//		} catch (CoreException e) {
//		}
//		return map;
	}

	protected synchronized void clearLoaddedData(IConfiguration cfg){
		if(((Configuration)cfg).isPreference())
			return;

		LoaddedInfo info = getLoaddedInfo();
		if(info == null)
			return;
		
		if(info.cfgMatch(cfg))
			setLoaddedInfo(null);
//		IProject proj = cfg.getOwner().getProject();
//		try {
//			proj.setSessionProperty(propsSessionProperty, null);
//		} catch (CoreException e) {
//		}
	}
	
	private static IProject getProject(IConfiguration cfg){
		IResource rc = cfg.getOwner();
		return rc != null ? rc.getProject() : null;
	}

	protected Properties loadProperties(IConfiguration cfg, IBuildObject bo){
		Map<String, Object> map = getData(cfg);
		
		return getPropsFromData(map, bo);
	}
	
	protected Properties getPropsFromData(Map<String, Object> data, IBuildObject bo){
		synchronized (data) {
			Object oVal = data.get(bo.getId());
			Properties props = null;
			if(oVal instanceof String){
				props = stringToProps((String)oVal);
				data.put(bo.getId(), props);
			} else if (oVal instanceof Properties){
				props = (Properties)oVal;
			}
			
			if(props == null){
				props = new Properties();
				data.put(bo.getId(), props);
			} 

			return props;
		}
	}


	protected void storeData(IConfiguration cfg){
		Map<String, Object> map = getLoaddedData(cfg);

		if(map != null)
			storeData(cfg, map);
	}

	protected Properties mapToProps(Map<String, Object> map){
		Properties props = null;
		if(map != null){
			synchronized(map){
				if(map.size() > 0){
					props = new Properties();
					Set<Entry<String, Object>> entrySet = map.entrySet();
					for (Entry<String, Object> entry : entrySet) {
						String key = entry.getKey();
						String value = null;
						Object oVal = entry.getValue();
						if(oVal instanceof Properties){
							value = propsToString((Properties)oVal);
						} else if (oVal instanceof String){
							value = (String)oVal;
						}
						
						if(key != null && value != null)
							props.setProperty(key, value);
					}
				}
			}
		}
		
		return props;
	}
	
	protected String propsToString(Properties props){
		if(props == null || props.size() == 0)
			return null;
		
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		try {
			props.store(stream, null); 
		} catch (IOException e1) {
		}

		byte[] bytes= stream.toByteArray();
		
		String value = null;
		try {
			value= new String(bytes, "UTF-8"); //$NON-NLS-1$
		} catch (UnsupportedEncodingException e) {
			value= new String(bytes);
		}
		
		/* FIX for Bug 405744: Properties.store() always starts the serialzed string with 
		 * a timestamp comment. That constantly changing comment causes the preference-store 
		 * to perform many unnecessary file-writes into the workspace metadata, even when 
		 * the properties don't change. The comment is ignored by Properties.load(), so
		 * just remove it here.
		 */
		String sep = System.getProperty("line.separator"); //$NON-NLS-1$
		while(value.charAt(0) == '#') {
			value = value.substring(value.indexOf(sep) + sep.length());
		}
		
		return value;
	}
	
	protected Properties stringToProps(String str){
		Properties props = null;
		if(str != null){
			props = new Properties();
			byte[] bytes;
			try {
				bytes = str.getBytes("UTF-8"); //$NON-NLS-1$
			} catch (UnsupportedEncodingException e) {
				bytes = str.getBytes();
			}

			ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
			try {
				props.load(stream);
			} catch (IOException e) {
				props = null;
			}
		}
		return props;
	}
	
	protected void storeData(IConfiguration cfg, Map<String, Object> map){
		String str = null;
		Properties props = mapToProps(map);

		str = propsToString(props);

		storeString(cfg, str);
	}
	
	protected void storeString(IConfiguration cfg, String str){
		Preferences prefs = getNode(cfg.getManagedProject());
		if(prefs != null){
			if(str != null)
				prefs.put(cfg.getId(), str);
			else
				prefs.remove(cfg.getId());
			try {
				prefs.flush();
			} catch (BackingStoreException e) {
			}
		}
	}

	protected String loadString(IConfiguration cfg){
		String str = null;
		Preferences prefs = getNode(cfg.getManagedProject());
		if(prefs != null)
			str = prefs.get(cfg.getId(), null);
		return str;	
	}
	
	protected Preferences getNode(IManagedProject mProject){
//		return getProjNode(mProject);
		return getInstNode(mProject);
	}
	
	protected Preferences getProjNode(IManagedProject mProject){
		IProject project = mProject.getOwner().getProject();
		if(project == null || !project.exists() || !project.isOpen())
			return null;

		Preferences prefs = new ProjectScope(project).getNode(ManagedBuilderCorePlugin.getUniqueIdentifier());
		if(prefs != null)
			return prefs.node(NODE_NAME);
		return null;
	}
	
	protected Preferences getInstNode(IManagedProject mProject){
		Preferences prefs = new InstanceScope().getNode(ManagedBuilderCorePlugin.getUniqueIdentifier());
		if(prefs != null){
			prefs = prefs.node(NODE_NAME);
			if(prefs != null)
				prefs = prefs.node(mProject.getId());
		}
		return prefs;
	}

	
	protected Map<String, Object> getData(IConfiguration cfg){
		Map<String, Object> map = getLoaddedData(cfg);
		
		if(map == null){
			map = loadData(cfg);
			
			setLoaddedData(cfg, map);
		}
		
		return map;
	}
	
	protected Map<String, Object> loadData(IConfiguration cfg){
		Map<String, Object> map = null;
		String str = loadString(cfg);

		Properties props = stringToProps(str);
			
		map = propsToMap(props);

		if(map == null)
			map = new LinkedHashMap<String, Object>();
		
		return map;
	}
	
	protected Map<String, Object> propsToMap(Properties props){
		if(props != null) {
			@SuppressWarnings({ "rawtypes", "unchecked" })
			Map<String, Object> map = new LinkedHashMap(props);
			return map;
		}
		return null;
	}

	private static IConfiguration getConfigurationFromId(IProject project, String id){
		if(project == null || id == null)
			return null;
		IManagedBuildInfo bInfo = ManagedBuildManager.getBuildInfo(project, false);
		IConfiguration cfg = null;
		if(bInfo != null){
			IManagedProject mProj = bInfo.getManagedProject();
			if(mProj != null){
				cfg = mProj.getConfiguration(id);
			}
		}

		return cfg;
	}
	
	protected void setLoaddedData(IConfiguration cfg, Map<String, Object> data){
		if(cfg.getOwner() == null)
			return;

		LoaddedInfo info = getLoaddedInfo();
		
		if(info != null){
			if(info.cfgMatch(cfg)){
				info = new LoaddedInfo(info.getProject(), info.getConfigurationId(), data);
				setLoaddedInfo(info);
				return;
			}
	
			IConfiguration oldCfg = info.getConfiguration();
			if(oldCfg != null){
				storeData(oldCfg, info.getProperties());
			}
		}

		IProject proj = cfg.getOwner().getProject();
		info = new LoaddedInfo(proj, cfg.getId(), data);
		setLoaddedInfo(info);
	}

	public void setProperty(IConfiguration cfg, String key, String value){
		setProperty(cfg, cfg, key, value);
	}

	public void setProperty(IResourceInfo rcInfo, String key, String value){
		setProperty(rcInfo.getParent(), rcInfo, key, value);
	}

	public void setProperty(IToolChain tc, String key, String value){
		setProperty(tc.getParent(), tc, key, value);
	}

	public void setProperty(ITool tool, String key, String value){
		Configuration cfg = (Configuration)getConfiguration(tool);
		if(cfg.isPreference())
			return;
		setProperty(cfg, tool, key, value);
	}
	
	public void setProperty(IBuilder builder, String key, String value){
		setProperty(getConfiguration(builder), builder, key, value);
	}

	public String getProperty(IConfiguration cfg, String key){
		return getProperty(cfg, cfg, key);
	}

	public String getProperty(IResourceInfo rcInfo, String key){
		return getProperty(rcInfo.getParent(), rcInfo, key);
	}

	public String getProperty(IToolChain tc, String key){
		return getProperty(tc.getParent(), tc, key);
	}

	public String getProperty(ITool tool, String key){
		return getProperty(getConfiguration(tool), tool, key);
	}
	
	public String getProperty(IBuilder builder, String key){
		return getProperty(getConfiguration(builder), builder, key);
	}

	public void clearProperties(IManagedProject mProject){
		if(mProject == null)
			return;
		
		IConfiguration cfgs[] = mProject.getConfigurations();
		for(int i = 0; i < cfgs.length; i++)
			clearLoaddedData(cfgs[i]);
		
		Preferences prefs = getNode(mProject);
		if(prefs != null){
			try {
				Preferences parent = prefs.parent();
				prefs.removeNode();
				if(parent != null)
					parent.flush();
			} catch (BackingStoreException e) {
			}
		}
	}

	public void clearProperties(IConfiguration cfg){
		if(cfg.getOwner() == null)
			return;
		
		clearLoaddedData(cfg);
		storeData(cfg, null);
	}

	private IConfiguration getConfiguration(IBuilder builder){
		IToolChain tc = builder.getParent();
		if(tc != null)
			return tc.getParent();
		return null;
	}

	private IConfiguration getConfiguration(ITool tool){
		IBuildObject p = tool.getParent();
		IConfiguration cfg = null;
		if(p instanceof IToolChain){
			cfg = ((IToolChain)p).getParent();
		} else if(p instanceof IResourceConfiguration){
			cfg = ((IResourceConfiguration)p).getParent();
		}
		return cfg;
	}

	public void serialize(IConfiguration cfg){
		if(cfg.isTemporary() || cfg.getOwner() == null)
			return;
		
		storeData(cfg);
	}
	
	public void serialize(){
		LoaddedInfo info = getLoaddedInfo();
		IConfiguration cfg = info.getConfiguration();
		if(cfg != null){
			serialize(cfg);
			
			clearLoaddedData(cfg);
		}
//		IProject projects[] = ResourcesPlugin.getWorkspace().getRoot().getProjects();
//		for(int i = 0; i < projects.length; i++){
//			IManagedBuildInfo info = ManagedBuildManager.getBuildInfo(projects[i], false);
//			if(info != null && info.isValid() && info.getManagedProject() != null){
//				IConfiguration cfgs[] = info.getManagedProject().getConfigurations();
//				for(int j = 0; j < cfgs.length; j++){
//					serialize(cfgs[j]);
//				}
//			}
//		}
	}

}
