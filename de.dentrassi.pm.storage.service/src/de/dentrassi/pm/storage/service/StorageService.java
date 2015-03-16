/*******************************************************************************
 * Copyright (c) 2014, 2015 IBH SYSTEMS GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *******************************************************************************/
package de.dentrassi.pm.storage.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;

import de.dentrassi.pm.common.MetaKey;
import de.dentrassi.pm.common.SimpleArtifactInformation;
import de.dentrassi.pm.storage.Artifact;
import de.dentrassi.pm.storage.ArtifactReceiver;
import de.dentrassi.pm.storage.Channel;

/**
 * The storage service provides access to the artifact storage system
 */
public interface StorageService
{
    public default Channel createChannel ()
    {
        return createChannel ( null, null );
    }

    public Channel createChannel ( String alias, String description );

    public Channel getChannel ( String channelId );

    public Channel getChannelWithAlias ( String channelIdOrName );

    /**
     * Create a new artifact in the channel
     * <p>
     * <em>Note:</em> This action might get vetoed by a channel aspect. In this
     * case the return value is <code>null</code>
     * </p>
     *
     * @param channelId
     *            the channel were to add the artifact
     * @param name
     *            the name of the artifact
     * @param stream
     *            the data stream
     * @param providedMetaData
     *            metadata that was initially provided
     * @return the newly created artifact or <code>null</code> if the creation
     *         was vetoed
     */
    public Artifact createArtifact ( String channelId, String name, InputStream stream, Map<MetaKey, String> providedMetaData );

    public Artifact createGeneratorArtifact ( String channelId, String name, String generatorId, InputStream stream, Map<MetaKey, String> providedMetaData );

    public Artifact createAttachedArtifact ( String parentArtifactId, String name, InputStream stream, Map<MetaKey, String> providedMetaData );

    public Collection<Channel> listChannels ();

    public void deleteChannel ( String channelId );

    public void streamArtifact ( String artifactId, ArtifactReceiver consumer ) throws FileNotFoundException;

    public SimpleArtifactInformation deleteArtifact ( String artifactId );

    /**
     * Add an aspect to the channel
     *
     * @param channelId
     *            the channel to process
     * @param aspectFactoryId
     *            the factory id of the aspect to add
     * @param withDependencies
     *            whether or not to add required dependent channel aspects
     */
    public void addChannelAspect ( String channelId, String aspectFactoryId, boolean withDependencies );

    public void removeChannelAspect ( String channelId, String aspectFactoryId );

    public SimpleArtifactInformation getArtifactInformation ( String artifactId );

    public Artifact getArtifact ( String artifactId );

    public void clearChannel ( String id );

    public void updateChannel ( String id, String name, String description );

    public void refreshChannelAspect ( String channelId, String aspectFactoryId );

    public void refreshAllChannelAspects ( String channelId );

    /**
     * Export the content of a channel
     * <p>
     * This will export this content of the channel to a ZIP file. The layout of
     * the ZIP file is considered an internal format which package drone itself
     * can re-import.
     * </p>
     * <p>
     * Only deployed content will be exported. Virtual artifacts, extracted meta
     * data, etc.. will <em>not</em> be exported, since these can be
     * re-generated. Also the channel aspects will be exported.
     * </p>
     * <p>
     * This only thing which does not get exported are the assignments to the
     * deploy keys.
     * </p>
     *
     * @param channelId
     *            the channel to export
     * @param stream
     *            the stream to write the exported zip file to
     * @throws IOException
     *             if there is an export error or an error writing to the output
     *             stream
     */
    public void exportChannel ( String channelId, OutputStream stream ) throws IOException;

    /**
     * Import a channel which was previously exported with
     * {@link #exportChannel(String, OutputStream)}
     *
     * @param inputStream
     *            a stream to the channel export file
     * @return the newly created channel
     */
    public Channel importChannel ( InputStream inputStream );
}
