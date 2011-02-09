/*
 * (C) Copyright 2006-2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     bstefanescu
 */
package org.nuxeo.ecm.core.auth;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.nuxeo.common.utils.StringUtils;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 *
 */
@XObject("user")
public class SimpleNuxeoPrincipal implements NuxeoPrincipal {

    private static final long serialVersionUID = 1L;

    @XNode("@name")
    protected String name;
    @XNode("@password")
    protected String password;
    @XNode("firstName")
    protected String fname;
    @XNode("lastName")
    protected String lname;
    @XNode("company")
    protected String company;
    @XNode("isAdministrator")
    protected boolean isAdministrator;
    @XNode("isAnonymous")
    protected boolean isAnonymous;

    protected Set<String> groups = new HashSet<String>();

    @XNode("groups")
    protected void setGroups(String expr) {
        String[] ar = StringUtils.split(expr, ',', true);
        for (String g : ar) {
            groups.add(g);
        }
    }

    protected transient String originatingUser;


    public SimpleNuxeoPrincipal() {

    }

    public SimpleNuxeoPrincipal(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getFirstName() {
        return fname;
    }

    @Override
    public String getLastName() {
        return lname;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getCompany() {
        return company;
    }

    @Override
    public List<String> getGroups() {
        return null;
    }

    @Override
    public List<String> getAllGroups() {
        return getGroups();
    }

    @Override
    public boolean isMemberOf(String group) {
        return false;
    }

    @Override
    public List<String> getRoles() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void setFirstName(String firstName) {
        this.fname = firstName;
    }

    @Override
    public void setLastName(String lastName) {
        this.lname = lastName;
    }

    @Override
    public void setGroups(List<String> groups) {
        this.groups = new HashSet<String>(groups);
    }

    @Override
    public void setRoles(List<String> roles) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void setCompany(String company) {
        this.company = company;
    }

    @Override
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String getPrincipalId() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void setPrincipalId(String principalId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public DocumentModel getModel() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void setModel(DocumentModel model) throws ClientException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public boolean isAdministrator() {
        return isAdministrator;
    }

    @Override
    public boolean isAnonymous() {
        return isAnonymous;
    }

    @Override
    public String getOriginatingUser() {
        return originatingUser;
    }

    @Override
    public void setOriginatingUser(String originatingUser) {
        this.originatingUser = originatingUser;
    }

}