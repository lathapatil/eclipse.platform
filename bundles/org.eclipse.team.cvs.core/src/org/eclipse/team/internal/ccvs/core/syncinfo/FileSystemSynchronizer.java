package org.eclipse.team.internal.ccvs.core.syncinfo;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.core.TeamPlugin;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.resources.*;
import org.eclipse.team.internal.ccvs.core.util.Assert;
import org.eclipse.team.internal.ccvs.core.util.FileUtil;
import org.eclipse.team.internal.ccvs.core.util.ResourceDeltaVisitor;
import org.eclipse.team.internal.ccvs.core.util.SyncFileUtil;

/**
 * The FileSystemSynchronizer stores sync information to be compatible with CVS command line
 * clients. It is also responsible for synchronizing the workbench when changes are made to CVS
 * metafiles (CVS/Entries, CVS/Root..) from outside of the workbench by 3rd party tools.
 * 
 * @see ICVSSynchronizer
 */
public class FileSystemSynchronizer implements ICVSSynchronizer {
	
	// caches for resource and folder sync. These are required to provide timely access to
	// sync information that may be shown in the UI.
	private ICache resourceSyncCache;
	private ICache folderSyncCache;
	
	// a specialized resource listener required to interpret metafile changes made to CVS managed
	// resources.
	private SyncResourceChangeListener resourceListener;
	
	// time in minutes that a cached sync object should remain in the cache. A time of 0 indicates
	// that the cache object will never expire.
	private final static int CACHE_EXPIRATION_MINUTES = 0;
	
	/**
	 * Initialize the caches and register as a resource listener.
	 */
	public FileSystemSynchronizer() {
		resourceSyncCache = new SimpleCache();
		resourceSyncCache.registerLoader(new ResourceSyncCacheLoader());
		
		folderSyncCache = new SimpleCache();
		folderSyncCache.registerLoader(new FolderSyncCacheLoader());
		
		resourceListener = new SyncResourceChangeListener();
		resourceListener.register();
	}
	
	/**
	 * For every get request from the cache, load the entire entries and permissions file.
	 */
	private class ResourceSyncCacheLoader implements ICacheLoader {
		/*
		 * @see ICacheLoader#load(Object, ICache)
		 */
		public CacheData load(Object id, ICache cache) {
			CacheData idInfo = null;
			try {
				File file = (File)id;
				File parent = file.getParentFile();
				Assert.isTrue(parent.exists());
				
				ResourceSyncInfo infos[] = SyncFileUtil.readEntriesFile(parent);
				// if null then, entries file does not exist 
				if(infos!=null) {
					for (int i = 0; i < infos.length; i++) {
						ResourceSyncInfo info = infos[i];				
						CacheData cacheInfo = new CacheData(new File(parent, info.getName()), info, CACHE_EXPIRATION_MINUTES);
						if(file.getName().equals(info.getName())) {
							idInfo = cacheInfo;
						} else {
							cache.put(cacheInfo);
						}
					}
				}
			} catch(CVSException e) {
				TeamPlugin.log(IStatus.ERROR, "Error loading from CVS/Entries file", e);
				return null;
			}
			return idInfo;
		}
	}
	
	/**
	 * For every get request from the cache that fails, load the files that contain the folder sync info.
	 */
	private class FolderSyncCacheLoader implements ICacheLoader {
		/*
		 * @see ICacheLoader#load(Object, ICache)
		 */
		public CacheData load(Object id, ICache cache) {
			try {
				File folder = (File)id;
				Assert.isTrue(folder.exists());
				FolderSyncInfo info = SyncFileUtil.readFolderConfig(folder);
			
				// no CVS sub-directory
				if(info==null) {
					return null;
				} else {
					return new CacheData(folder, info, CACHE_EXPIRATION_MINUTES);
				}
			} catch(CVSException e) {
				TeamPlugin.log(IStatus.ERROR, "Error loading from CVS/Entries file", e);
				return null;
			}
		}
	}
	
	/**
	 * Handle changes made to meta files.
	 * 1. 
	 */
	private class SyncResourceChangeListener extends ResourceDeltaVisitor {
		private Set delta = new HashSet();

		protected void handleAdded(IResource[] resources) {
			handleDefault(resources);
		}
		
		protected void handleRemoved(IResource[] resources) {
			for (int i = 0; i < resources.length; i++) {
				IResource resource = resources[i];
				File file = resource.getLocation().toFile();
				// if a resource is deleted, then clear the cache, there is not need for a delta.
				if(!SyncFileUtil.isMetaFile(file)) {
					clearCache(file, IResource.DEPTH_INFINITE);
				} else {
					if(resource.getName().equals("CVS")) {
						handleMetaChange(file, resource);
					}
				}
			}
		}
		
		protected void handleChanged(IResource[] resources) {
			handleDefault(resources);
		}
		
		protected void finished() {
			TeamPlugin.getManager().broadcastResourceStateChanges((IResource[]) delta.toArray(new IResource[delta.size()]));
			delta.clear();
		}
		
		/**
		 * If a meta file has changed the cache will be out-of-date. It will be cleared and subsequent access
		 * will force a reload of the sync information when needed by a client.
		 */
		private void handleMetaChange(File cvsdir, IResource resource) {
			File parent = cvsdir.getParentFile();			
			clearCache(parent, IResource.DEPTH_ONE);
			
			// generate deltas for children of the parent because their state may of changed.		
			// it is safe to get the parent two up from the metafile because we have already
			// confirmed that this is a meta directory.
			if(resource.exists()) {
				IContainer resourceParent = resource.getParent();
				delta.add(resourceParent);
				try {
					IResource[] children = resourceParent.members();
					for (int i = 0; i < children.length; i++) {
						delta.add(children[i]);
					}
				} catch(CoreException e) {
					// XXX what can you do in a resource listener when an exception occurs???
				}
			}
		}
		
		/**
		 * Canonical handling of a resource change
		 */
		private void handleDefault(IResource[] resources) {
			for (int i = 0; i < resources.length; i++) {
				File file = resources[i].getLocation().toFile();
				if(SyncFileUtil.isMetaFile(file)) {
					handleMetaChange(file.getParentFile(), resources[i].getParent());
					// add all parents children to delta
				} else {
					if(!file.getName().equals("CVS")) {
						delta.add(resources[i]);
					}
				}
			}
		}
	}
	
	/*
	 * @see ICVSSynchronizer#getFolderSync(File)
	 */
	public FolderSyncInfo getFolderSync(File file) throws CVSException {
		if(file.exists() && file.isDirectory()) {
			LocalFolder folder = new LocalFolder(file);
			if(SyncFileUtil.getCVSSubdirectory(file).exists()) {
				CacheData data = (CacheData)folderSyncCache.get(file, null);
				if(data!=null) {
					return (FolderSyncInfo)data.getData();
				}
			}
		}
		return null;
	}
	
	/*
	 * @see ICVSSynchronizer#getResourceSync(File)
	 */
	public ResourceSyncInfo getResourceSync(File file) throws CVSException {
		LocalFolder parent = new LocalFolder(file.getParentFile());
		if(parent.exists() && parent.isCVSFolder()) {
			CacheData data = (CacheData)resourceSyncCache.get(file, null);
			if(data!=null) {
				return (ResourceSyncInfo)data.getData();
			}
		}
		return null;
	}
	
	/*
	 * @see ICVSSynchronizer#setFolderSync(File, FolderSyncInfo)
	 */
	public void setFolderSync(File file, FolderSyncInfo info) throws CVSException {
		Assert.isNotNull(info);
		Assert.isTrue(file.exists());
		
		SyncFileUtil.writeFolderConfig(file, info);
		folderSyncCache.put(new CacheData(file, info, CACHE_EXPIRATION_MINUTES));
	
		// the server won't add directories as sync info, therefore it must be done when
		// a directory is shared with the repository.
		setResourceSync(file, new ResourceSyncInfo(file.getName()));
				
	}
	
	/*
	 * @see ICVSSynchronizer#setResourceSync(File, ResourceSyncInfo)
	 */
	public void setResourceSync(File file, ResourceSyncInfo info) throws CVSException {
		Assert.isNotNull(info);
		Assert.isTrue(file.getParentFile().exists());
		Assert.isTrue(file.getName().equals(info.getName()));
		
		try {
			LocalFolder parent = new LocalFolder(file.getParentFile());
			if(parent.exists() && parent.isCVSFolder()) {
				SyncFileUtil.writeResourceSync(file, info);
			}
		} catch(CVSException e) {
			// XXX Bad eating of exception
		}
		resourceSyncCache.put(new CacheData(file, info, CACHE_EXPIRATION_MINUTES));
	}
	
	/*
	 * @see ICVSSynchronizer#deleteFolderSync(File, IProgressMonitor)
	 */
	public void deleteFolderSync(File file, IProgressMonitor monitor) throws CVSException {
		destroySyncDeep(file, monitor);
	}
	
	/*
	 * @see ICVSSynchronizer#deleteResourceSync(File)
	 */
	public void deleteResourceSync(File file) {
		try {
			SyncFileUtil.deleteSync(file);
		} catch(CVSException e) {
				// XXX Bad eating of exception		
		}
		resourceSyncCache.remove(file);
	}

	/*
	 * If the file no longer exists, then clear the cache, or else, refresh from local and allow the
	 * resource change listener to adapt to changes.
	 * 
	 * @see ICVSSynchronizer#reload(File, IProgressMonitor)
	 */
	public void reload(File file, IProgressMonitor monitor) throws CVSException {
		
		clearCache(file, IResource.DEPTH_INFINITE);
		
		if(!file.exists()) {
			// a non-existant file implies that there is no longer any meta information
			// on disk, we can safely clear the cache.
			// we can safely reload the parent if it exists.
			file = file.getParentFile();
			if(!file.exists()) {
				return;
			}
		}
		
		// the following is to refresh the workbench with the local file changes.
		if(file.equals(ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile())) {
			return;
		}
			
		IResource resource;
		if(file.isDirectory()) {
			resource = ResourcesPlugin.getWorkspace().getRoot().getContainerForLocation(new Path(file.getAbsolutePath()));
		} else {
			// reload a container always, or else sync info changes won't be loaded!
			resource = ResourcesPlugin.getWorkspace().getRoot().getContainerForLocation(new Path(file.getParentFile().getAbsolutePath()));
		}
		try {
			if(resource!=null) {
				resource.refreshLocal(IResource.DEPTH_INFINITE, monitor);
			}		
		} catch(CoreException e) {
			throw new CVSException(IStatus.ERROR, 0, "Error reloading sync information", e);
		}
	}
	
	/*
	 * Simply reload to absorb changes made to the underlying file system.
	 * 
	 * @see ICVSSynchronizer#save(File, IProgressMonitor)
	 */
	public void save(File file, IProgressMonitor monitor) throws CVSException {
		reload(file, monitor);
	}
	
	/*
	 * Answers if the caches are empty. 
	 * 
	 * @see ICVSSynchronizer#isEmpty()
	 */
	public boolean isEmpty() {
		return resourceSyncCache.isEmpty() && folderSyncCache.isEmpty();
	}
	
	/*
	 * @see ICVSSynchronizer#members(File)
	 */
	public ResourceSyncInfo[] members(File folder) throws CVSException {
		// read the entries file and cache if needed
		Assert.isTrue(folder.exists());
		ResourceSyncInfo[] infos = SyncFileUtil.readEntriesFile(folder);
		if(infos==null) {
			return new ResourceSyncInfo[0];
		} else {
			return infos;
		}
	}
	
	protected void destroySyncDeep(File file, IProgressMonitor monitor) {
		if (file.isDirectory()) {
			File[] fileList = file.listFiles();
			for (int i = 0; i < fileList.length; i++) {
				destroySyncDeep(fileList[i], monitor);
			}			
			folderSyncCache.remove(file);
			File metaDir = SyncFileUtil.getCVSSubdirectory(file);
			if(metaDir.exists()) {
				FileUtil.deepDelete(metaDir);
			}
		}
		deleteResourceSync(file);
	}
	
	protected void clearCache(File file, int depth) {
		if(file.exists()) {
			if (file.isDirectory()) {
				if(depth!=IResource.DEPTH_ZERO) {
					File[] fileList = file.listFiles();
					for (int i = 0; i < fileList.length; i++) {
						if(depth==IResource.DEPTH_ONE) {
							depth = IResource.DEPTH_ZERO;
						}					
						clearCache(fileList[i], depth);
					}
				}
				folderSyncCache.remove(file);
			}
			resourceSyncCache.remove(file);
		} else {
			clearCacheForChildren(file);
		}
	}
	
	protected void clearCacheForChildren(File file) {
		// XXX not optimal, could instead have implement the cache as a tree
		// and be able to traverse children. This is the safest for now.
		resourceSyncCache.clear();
		folderSyncCache.clear();
	}
}