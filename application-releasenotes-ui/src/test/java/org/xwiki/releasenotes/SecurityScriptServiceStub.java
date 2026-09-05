/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.releasenotes;

import org.xwiki.model.reference.EntityReference;
import org.xwiki.script.service.ScriptService;

/**
 * A stand-in for {@code $services.security}, which a {@code PageTest} does not register, answering every right
 * check with the same decision. It exposes only the {@code authorization.hasAccess} path the pages use.
 *
 * @version $Id$
 */
public class SecurityScriptServiceStub implements ScriptService
{
    private final Authorization authorization;

    /**
     * @param allowed the answer every right check gets
     */
    public SecurityScriptServiceStub(boolean allowed)
    {
        this.authorization = new Authorization(allowed);
    }

    /**
     * @return the sub-service reached as {@code $services.security.authorization}
     */
    public Authorization getAuthorization()
    {
        return this.authorization;
    }

    /**
     * The {@code authorization} sub-service.
     */
    public static class Authorization
    {
        private final boolean allowed;

        Authorization(boolean allowed)
        {
            this.allowed = allowed;
        }

        /**
         * @param right the name of the right being checked (ignored by this stand-in)
         * @param entity the entity the right is checked on (ignored by this stand-in)
         * @return the decision this stand-in was built with
         */
        public boolean hasAccess(String right, EntityReference entity)
        {
            return this.allowed;
        }
    }
}
