/*
 * (C) Copyright 2009 Nuxeo SA (http://nuxeo.com/) and contributors.
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
package org.nuxeo.ecm.core.storage.sql.management;

import org.nuxeo.runtime.management.AbstractResourceFactory;

/**
 * @author Florent Guillaume
 */
public class RepositoryStatusFactory extends AbstractResourceFactory {

    public void registerResources() {
        RepositoryStatus instance = new RepositoryStatus();
        service.registerResource("SQLRepositoryStatus",
                "nx:service=RepositoryStatus,type=SQLStorage",
                RepositoryStatusMBean.class, instance);
    }

}