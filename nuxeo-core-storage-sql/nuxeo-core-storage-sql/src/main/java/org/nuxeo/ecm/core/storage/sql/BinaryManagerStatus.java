/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.storage.sql;

/**
 * Status of a BinaryManager, including files that may have just been deleted by
 * GC
 */
public class BinaryManagerStatus {

    /**
     * The number of binaries.
     */
    public long numBinaries;

    /**
     * The cumulated size of the binaries.
     */
    public long sizeBinaries;

    /**
     * The number of garbage collected binaries.
     */
    public long numBinariesGC;

    /**
     * The cumulated size of the garbage collected binaries.
     */
    public long sizeBinariesGC;

}
