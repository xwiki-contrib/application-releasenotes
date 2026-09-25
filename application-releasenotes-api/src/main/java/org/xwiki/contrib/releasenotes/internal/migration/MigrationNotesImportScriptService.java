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
package org.xwiki.contrib.releasenotes.internal.migration;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.script.service.ScriptService;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.XWikiContext;

/**
 * Lets the page of the import of the backward compatibility and migration notes of xwiki.org run it. It is internal:
 * the import reads the release notes the way xwiki.org writes them, and is no API to build on.
 *
 * @version $Id$
 * @since 2.8
 */
@Component
@Named("releasenotesMigrationImport")
@Singleton
public class MigrationNotesImportScriptService implements ScriptService
{
    @Inject
    private MigrationNotesImporter importer;

    @Inject
    private ContextualAuthorizationManager authorization;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    /**
     * @param product the product to read the release notes of, or {@code null} for the product configured for the
     *            wiki
     * @return what the import would do
     * @throws ReleaseNotesException when the current user does not administer the wiki, or the release notes could
     *             not be read
     */
    public MigrationNotesImportReport preview(String product) throws ReleaseNotesException
    {
        checkAdministration();

        return this.importer.preview(product);
    }

    /**
     * @param product the product to migrate the release notes of, or {@code null} for the product configured for
     *            the wiki
     * @return what the import did
     * @throws ReleaseNotesException when the current user does not administer the wiki, or the release notes could
     *             not be read
     */
    public MigrationNotesImportReport importNotes(String product) throws ReleaseNotesException
    {
        checkAdministration();

        return this.importer.importNotes(product);
    }

    /**
     * The import rewrites pages across the wiki and cannot be undone, so administering the wiki is what it takes to
     * run it, and to see what it would do.
     */
    private void checkAdministration() throws ReleaseNotesException
    {
        if (!this.authorization.hasAccess(Right.ADMIN, new WikiReference(this.xcontextProvider.get().getWikiId()))) {
            throw new ReleaseNotesException("Only an administrator of the wiki can import the migration notes.");
        }
    }
}
