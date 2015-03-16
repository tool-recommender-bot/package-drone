/*******************************************************************************
 * Copyright (c)  2015 IBH SYSTEMS GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *******************************************************************************/
package de.dentrassi.pm.storage.service.jpa;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.persistence.EntityManager;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;

import de.dentrassi.pm.common.MetaKey;
import de.dentrassi.pm.common.XmlHelper;
import de.dentrassi.pm.storage.jpa.ArtifactEntity;
import de.dentrassi.pm.storage.jpa.AttachedArtifactEntity;
import de.dentrassi.pm.storage.jpa.ChannelEntity;
import de.dentrassi.pm.storage.jpa.GeneratorArtifactEntity;
import de.dentrassi.pm.storage.jpa.PropertyEntity;
import de.dentrassi.pm.storage.jpa.RootArtifactEntity;
import de.dentrassi.pm.storage.jpa.StoredArtifactEntity;

/**
 * A handler for exporting and importing channel data
 */
public class TransferHandler extends AbstractHandler implements StreamServiceHelper
{

    public static class PropertyComparator implements Comparator<PropertyEntity>
    {
        public static final Comparator<PropertyEntity> COMPARATOR = new PropertyComparator ();

        @Override
        public int compare ( final PropertyEntity o1, final PropertyEntity o2 )
        {
            final int rc = o1.getNamespace ().compareTo ( o2.getNamespace () );
            if ( rc != 0 )
            {
                return rc;
            }
            return o1.getKey ().compareTo ( o2.getKey () );
        }
    }

    private final XmlHelper xml;

    public TransferHandler ( final EntityManager em, final LockManager<String> lockManager )
    {
        super ( em, lockManager );

        this.xml = new XmlHelper ();
    }

    public ChannelEntity importChannel ( final StorageHandlerImpl handler, final InputStream inputStream ) throws IOException
    {
        final Path tmp = Files.createTempFile ( "imp", null );
        try
        {
            try ( OutputStream tmpStream = new BufferedOutputStream ( new FileOutputStream ( tmp.toFile () ) ) )
            {
                ByteStreams.copy ( inputStream, tmpStream );
            }

            return processImport ( handler, tmp );
        }
        finally
        {
            Files.deleteIfExists ( tmp );
        }
    }

    private ChannelEntity processImport ( final StorageHandlerImpl storage, final Path tmp ) throws IOException
    {
        try ( final ZipFile zip = new ZipFile ( tmp.toFile () ) )
        {
            final String version = getData ( zip, "version" );
            if ( !"1".equals ( version ) )
            {
                throw new IllegalArgumentException ( String.format ( "Version '%s' is not supported", version ) );
            }

            // read basic channel data

            final String description = getData ( zip, "description" );
            final Map<MetaKey, String> properties = getProperties ( zip, "properties.xml" );
            final Set<String> aspects = getAspects ( zip );

            // create the channel

            final ChannelEntity channel = storage.createChannel ( null, description, properties );
            this.em.flush (); // we need the channel id

            this.lockManager.modifyRun ( channel.getId (), ( ) -> {
                storage.addChannelAspects ( channel, aspects, false );

                // process artifacts
                processArtifacts ( channel, storage, zip );
            } );

            storage.runChannelAggregators ( channel );

            return channel;
        }
    }

    class Entry
    {
        private final Map<String, Entry> children = new HashMap<> ();

        private ZipEntry zipEntry;

        private final List<String> ids;

        public Entry ()
        {
            this.ids = Collections.emptyList ();
        }

        public Entry ( final List<String> ids )
        {
            this.ids = ids;
        }

        public void addChild ( final LinkedList<String> path, final ZipEntry zipEntry )
        {
            final String seg = path.pop ();

            Entry child = this.children.get ( seg );
            if ( child == null )
            {
                final List<String> ids = new ArrayList<> ( this.ids );
                ids.add ( seg );
                child = new Entry ( ids );
                this.children.put ( seg, child );
            }

            if ( path.isEmpty () )
            {
                child.zipEntry = zipEntry;
            }
            else
            {
                child.addChild ( path, zipEntry );
            }
        }

        public void store ( final ZipFile zip, final ChannelEntity channel, final ArtifactEntity parent, final StorageHandlerImpl storage, final RegenerateTracker tracker ) throws Exception
        {
            for ( final Entry child : this.children.values () )
            {
                final String baseName = String.format ( "%s%s/", "artifacts/", child.ids.stream ().collect ( Collectors.joining ( "/" ) ) );

                final String name = getData ( zip, baseName + "name" );

                final String generator = getData ( zip, baseName + "generator" );

                final ArtifactEntity result;
                try ( final InputStream stream = zip.getInputStream ( child.zipEntry ) )
                {
                    final Map<MetaKey, String> providedMetaData = getProperties ( zip, baseName + "properties.xml" );

                    final Supplier<ArtifactEntity> creator;
                    if ( parent == null && generator != null )
                    {
                        // import a generator
                        creator = ( ) -> {
                            final GeneratorArtifactEntity ent = new GeneratorArtifactEntity ();
                            ent.setGeneratorId ( generator );
                            return ent;
                        };
                    }
                    else if ( parent == null )
                    {
                        // import a simple root artifact
                        creator = StoredArtifactEntity::new;
                    }
                    else
                    {
                        // import an attached child
                        creator = ( ) -> {
                            final AttachedArtifactEntity ent = new AttachedArtifactEntity ();
                            ent.setParent ( parent );
                            return ent;
                        };
                    }

                    // do the import
                    result = storage.performStoreArtifact ( channel, name, stream, creator, providedMetaData, tracker, false, false );
                }

                child.store ( zip, channel, result, storage, tracker );
            }
        }
    }

    public void processArtifacts ( final ChannelEntity channel, final StorageHandlerImpl storage, final ZipFile zip ) throws IOException
    {
        // first gather a artifacts

        final Enumeration<? extends ZipEntry> entries = zip.entries ();

        final Entry root = new Entry ();
        while ( entries.hasMoreElements () )
        {
            final ZipEntry ze = entries.nextElement ();

            final String name = ze.getName ();
            if ( name.startsWith ( "artifacts/" ) && name.endsWith ( "/data" ) )
            {
                final List<String> segs = Arrays.asList ( name.split ( "\\/" ) );
                root.addChild ( new LinkedList<> ( segs.subList ( 1, segs.size () - 1 ) ), ze );
            }
        }

        // now import them hierarchically

        final RegenerateTracker tracker = new RegenerateTracker ();
        try
        {
            root.store ( zip, channel, null, storage, tracker );
            tracker.process ( storage );
        }
        catch ( final Exception e )
        {
            throw new IOException ( "Failed to import artifacts", e );
        }
    }

    private Map<MetaKey, String> getProperties ( final ZipFile zip, final String name ) throws IOException
    {
        final ZipEntry ze = zip.getEntry ( name );
        if ( ze == null )
        {
            return null;
        }

        try ( InputStream stream = zip.getInputStream ( ze ) )
        {
            return readProperties ( stream );
        }
    }

    private String getData ( final ZipFile zip, final String name ) throws IOException
    {
        final ZipEntry ze = zip.getEntry ( name );
        if ( ze == null )
        {
            return null;
        }

        try ( Reader reader = new InputStreamReader ( zip.getInputStream ( ze ), StandardCharsets.UTF_8 ) )
        {
            return CharStreams.toString ( reader );
        }
    }

    /**
     * Read in a map of properties
     *
     * @param stream
     *            input stream
     * @return the map of properties
     * @throws IOException
     *             if anything goes wrong reading the file
     */
    private Map<MetaKey, String> readProperties ( final InputStream stream ) throws IOException
    {
        try
        {
            final Document doc = this.xml.parse ( new FilterInputStream ( stream ) {
                @Override
                public void close ()
                {
                    // do nothing
                }
            } );

            final Element root = doc.getDocumentElement ();
            if ( !"properties".equals ( root.getNodeName () ) )
            {
                throw new IllegalStateException ( String.format ( "Root element must be of type '%s'", "properties" ) );
            }

            final Map<MetaKey, String> result = new HashMap<> ();

            for ( final Element ele : XmlHelper.iterElement ( root, "property" ) )
            {
                final String namespace = ele.getAttribute ( "namespace" );
                final String key = ele.getAttribute ( "key" );
                final String value = ele.getTextContent ();

                if ( namespace.isEmpty () || key.isEmpty () )
                {
                    continue;
                }

                result.put ( new MetaKey ( namespace, key ), value );
            }

            return result;
        }
        catch ( final Exception e )
        {
            throw new IOException ( "Failed to read properties", e );
        }
    }

    private Set<String> getAspects ( final ZipFile zip ) throws IOException
    {
        final ZipEntry ze = zip.getEntry ( "aspects" );
        if ( ze == null )
        {
            return Collections.emptySet ();
        }

        try ( InputStream stream = zip.getInputStream ( ze ) )
        {
            final List<String> lines = CharStreams.readLines ( new InputStreamReader ( stream, StandardCharsets.UTF_8 ) );
            return new HashSet<> ( lines );
        }
    }

    /**
     * Export the content of a channel
     *
     * @param channelId
     *            the channel to export
     * @param stream
     *            the stream to write the export file to
     * @throws IOException
     *             if the export cannot be performed
     */
    public void exportChannel ( final String channelId, final OutputStream stream ) throws IOException
    {
        this.lockManager.accessCall ( channelId, ( ) -> {
            final ChannelEntity channel = getCheckedChannel ( channelId );
            final ZipOutputStream zos = new ZipOutputStream ( stream );

            putDataEntry ( zos, "version", "1" );
            putDataEntry ( zos, "name", channel.getName () );
            putDataEntry ( zos, "description", channel.getDescription () );
            putDirEntry ( zos, "artifacts" );
            putProperties ( zos, "properties.xml", channel.getProvidedProperties () );
            putAspects ( zos, channel.getAspects () );
            putArtifacts ( zos, "artifacts/", channel.getArtifacts (), true );

            zos.finish ();

            return null;
        } );
    }

    private void putArtifacts ( final ZipOutputStream zos, final String baseName, final Collection<? extends ArtifactEntity> artifacts, final boolean onlyRoot ) throws IOException
    {
        for ( final ArtifactEntity art : artifacts )
        {
            if ( ! ( art instanceof RootArtifactEntity || art instanceof AttachedArtifactEntity ) )
            {
                continue;
            }

            if ( onlyRoot && ! ( art instanceof RootArtifactEntity ) )
            {
                // only root artifacts in this run
                continue;
            }

            final String name = String.format ( "%s%s/", baseName, art.getId () );

            // make a dir entry
            {
                final ZipEntry ze = new ZipEntry ( name );
                ze.setComment ( art.getName () );

                final FileTime timestamp = FileTime.fromMillis ( art.getCreationTimestamp ().getTime () );

                ze.setLastModifiedTime ( timestamp );
                ze.setCreationTime ( timestamp );
                ze.setLastAccessTime ( timestamp );

                zos.putNextEntry ( ze );
                zos.closeEntry ();
            }

            // put the provided properties
            putProperties ( zos, name + "properties.xml", art.getProvidedProperties () );
            putDataEntry ( zos, name + "name", art.getName () );

            if ( art instanceof GeneratorArtifactEntity )
            {
                putDataEntry ( zos, name + "generator", ( (GeneratorArtifactEntity)art ).getGeneratorId () );
            }

            // put the blob
            try
            {
                internalStreamArtifact ( this.em, art, ( in ) -> {
                    zos.putNextEntry ( new ZipEntry ( name + "data" ) );
                    ByteStreams.copy ( in, zos );
                    zos.closeEntry ();
                } );
            }
            catch ( final Exception e )
            {
                throw new IOException ( "Failed to export artifact", e );
            }

            // put children
            putArtifacts ( zos, name, art.getChildArtifacts (), false );
        }
    }

    private void putAspects ( final ZipOutputStream zos, final Set<String> aspects ) throws IOException
    {
        final List<String> list = new ArrayList<> ( aspects );
        Collections.sort ( list );

        final StringBuilder sb = new StringBuilder ();

        for ( final String aspect : list )
        {
            sb.append ( aspect ).append ( '\n' );
        }

        putDataEntry ( zos, "aspects", sb.toString () );
    }

    private void putProperties ( final ZipOutputStream zos, final String name, final Collection<? extends PropertyEntity> properties ) throws IOException
    {
        if ( properties.isEmpty () )
        {
            return;
        }

        zos.putNextEntry ( new ZipEntry ( name ) );

        final Document doc = this.xml.create ();
        final Element root = doc.createElement ( "properties" );
        doc.appendChild ( root );

        final List<PropertyEntity> list = new ArrayList<> ( properties );
        Collections.sort ( list, PropertyComparator.COMPARATOR );

        for ( final PropertyEntity pe : list )
        {
            final Element p = XmlHelper.addElement ( root, "property" );
            p.setAttribute ( "namespace", pe.getNamespace () );
            p.setAttribute ( "key", pe.getKey () );
            p.setTextContent ( pe.getValue () );
        }

        try
        {
            this.xml.write ( doc, zos );
            zos.closeEntry ();
        }
        catch ( final Exception e )
        {
            throw new IOException ( "Failed to serialize XML", e );
        }
    }

    private void putDirEntry ( final ZipOutputStream zos, String name ) throws IOException
    {
        if ( !name.endsWith ( "/" ) )
        {
            name = name + "/";
        }

        final ZipEntry entry = new ZipEntry ( name );
        zos.putNextEntry ( entry );
        zos.closeEntry ();
    }

    private void putDataEntry ( final ZipOutputStream stream, final String name, final String data ) throws IOException
    {
        if ( data == null )
        {
            return;
        }
        stream.putNextEntry ( new ZipEntry ( name ) );
        stream.write ( data.getBytes ( StandardCharsets.UTF_8 ) );
        stream.closeEntry ();
    }

}
