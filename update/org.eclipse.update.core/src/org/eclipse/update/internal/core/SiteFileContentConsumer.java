package org.eclipse.update.internal.core;
/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
import java.io.*;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.core.runtime.*;
import org.eclipse.update.core.*;
import org.eclipse.update.core.model.*;
import org.eclipse.update.core.model.ArchiveReferenceModel;
import org.eclipse.update.core.model.FeatureModel;

/**
 * Site on the File System
 */
public class SiteFileContentConsumer extends SiteContentConsumer {

	private String path;
	private IFeature feature;

	/**
	 * Constructor for FileSite
	 */
	public SiteFileContentConsumer(IFeature feature) {
		this.feature = feature;
	}

	/**
		 * return the path in whichh the Feature will be installed
		 */
	private String getFeaturePath() throws CoreException {
		String featurePath = null;
		try {
			VersionedIdentifier featureIdentifier = feature.getVersionedIdentifier();
			String path =
				Site.DEFAULT_INSTALLED_FEATURE_PATH
					+ featureIdentifier.toString()
					+ File.separator;
			URL newURL = new URL(getSite().getURL(), path);
			featurePath = newURL.getFile();
		} catch (MalformedURLException e) {
			throw Utilities.newCoreException(
				Policy.bind("SiteFileContentConsumer.UnableToCreateURL") + e.getMessage(),
				e);
			//$NON-NLS-1$
		}
		return featurePath;
	}

	/*
	 * @see ISiteContentConsumer#open(INonPluginEntry)
	 */
	public IContentConsumer open(INonPluginEntry nonPluginEntry)
		throws CoreException {
		return new SiteFileNonPluginContentConsumer(getFeaturePath());
	}

	/*
	 * @see ISiteContentConsumer#open(IPluginEntry)
	 */
	public IContentConsumer open(IPluginEntry pluginEntry) throws CoreException {
		return new SiteFilePluginContentConsumer(pluginEntry, getSite());
	}

	/*
	 * @see ISiteContentConsumer#store(ContentReference, IProgressMonitor)
	 */
	public void store(ContentReference contentReference, IProgressMonitor monitor)
		throws CoreException {
		InputStream inStream = null;
		String featurePath = getFeaturePath();
		String contentKey = contentReference.getIdentifier();
		featurePath += contentKey;
		try {
			inStream = contentReference.getInputStream();
			UpdateManagerUtils.copyToLocal(inStream, featurePath, null);
		} catch (IOException e) {
			throw Utilities.newCoreException(
				Policy.bind("GlobalConsumer.ErrorCreatingFile", featurePath),
				e);
			//$NON-NLS-1$
		} finally {
			try {
				// close stream
				inStream.close();
			} catch (Exception e) {
			}
		}

	}

	/*
	 * @see ISiteContentConsumer#close()
	 */
	public IFeatureReference close() throws CoreException {

		InternalFeatureReference ref = new InternalFeatureReference();
		ref.setSite(getSite());
		File file = null;

		try {
			file = new File(getFeaturePath());
			ref.setURL(file.toURL());
		} catch (MalformedURLException e) {
			throw Utilities.newCoreException(
				Policy.bind(
					"SiteFileContentConsumer.UnableToCreateURLForFile",
					file.getAbsolutePath()),
				e);
			//$NON-NLS-1$
		}

		if (ref != null){
			commitPlugins(ref);
			ref.markReadOnly();
		}
		
		return ref;
	}

	/*
	 * 
	 */
	public void abort() {
		// FIXME
	}

	/*
	 * commit the plugins installed as archive on the site
	 */
	 private void commitPlugins(IFeatureReference localFeatureReference) throws CoreException {

		((SiteFile)getSite()).addFeatureReferenceModel((FeatureReferenceModel) localFeatureReference);

		// add the installed plugins directories as archives entry
		SiteFileFactory archiveFactory = new SiteFileFactory();
		ArchiveReferenceModel archive = null;
		IPluginEntry[] pluginEntries =
			localFeatureReference.getFeature().getPluginEntries();
		for (int i = 0; i < pluginEntries.length; i++) {
			String versionId = pluginEntries[i].getVersionedIdentifier().toString();
			String pluginID =
				Site.DEFAULT_PLUGIN_PATH
					+ versionId
					+ FeaturePackagedContentProvider.JAR_EXTENSION;
			archive = archiveFactory.createArchiveReferenceModel();
			archive.setPath(pluginID);
			try {
				URL url =
					new URL(getSite().getURL(), Site.DEFAULT_PLUGIN_PATH + versionId + File.separator);
				archive.setURLString(url.toExternalForm());
				archive.resolve(url, null);
				((SiteFile)getSite()).addArchiveReferenceModel(archive);
			} catch (MalformedURLException e) {
				String id =
					UpdateManagerPlugin.getPlugin().getDescriptor().getUniqueIdentifier();
				String urlString = (getSite().getURL() != null) ? getSite().getURL().toExternalForm() : "";
				//$NON-NLS-1$
				urlString += Site.DEFAULT_PLUGIN_PATH + pluginEntries[i].toString();
				throw Utilities.newCoreException(
					Policy.bind("SiteFile.UnableToCreateURL", urlString),
					e);
				//$NON-NLS-1$
			}
		}
		return;
	 }

}