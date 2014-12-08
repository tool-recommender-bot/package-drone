/*******************************************************************************
 * Copyright (c) 2014 Jens Reimann.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.pm.aspect.virtual;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

import de.dentrassi.pm.storage.ArtifactInformation;
import de.dentrassi.pm.storage.MetaKey;

public interface Virtualizer
{
    public interface Context
    {
        public ArtifactInformation getArtifactInformation ();

        public Path getFile ();

        public void createVirtualArtifact ( String name, InputStream stream, Map<MetaKey, String> providedMetaData );
    }

    public void virtualize ( Context context );
}