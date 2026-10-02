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
package org.xwiki.contrib.releasenotes.internal;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.model.document.DocumentAuthors;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.user.CurrentUserReference;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Loads the pages of the application, and writes them as the current user.
 * <p>
 * No right is checked here, as {@code XWiki#saveDocument} checks none: the entry points of the application, its REST
 * endpoints and its script service, check the edit right of their caller before anything is written.
 *
 * @version $Id$
 * @since 2.7
 */
@Component(roles = ReleaseNotesDocumentStore.class)
@Singleton
public class ReleaseNotesDocumentStore
{
    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private UserReferenceResolver<CurrentUserReference> currentUserResolver;

    /**
     * @param reference the page to load
     * @return that page, which is a new page when it does not exist, and is otherwise the instance the store holds in
     *         its cache, which a caller about to modify it must clone first
     * @throws ReleaseNotesException when the page could not be loaded
     */
    public XWikiDocument load(DocumentReference reference) throws ReleaseNotesException
    {
        XWikiContext xcontext = this.xcontextProvider.get();

        try {
            return xcontext.getWiki().getDocument(reference, xcontext);
        } catch (XWikiException e) {
            throw new ReleaseNotesException(String.format("Failed to load the page [%s].", reference), e);
        }
    }

    /**
     * Saves the passed page as the current user.
     * <p>
     * The content author is set along with the author because the content of a release note is copied from a
     * template that scripts, and the content author is who that script runs as.
     *
     * @param document the page to save
     * @param comment the comment to record the new version of that page with
     * @throws ReleaseNotesException when a listener refused the save, or the save failed
     */
    public void save(XWikiDocument document, String comment) throws ReleaseNotesException
    {
        DocumentReference reference = document.getDocumentReference();

        UserReference user = this.currentUserResolver.resolve(CurrentUserReference.INSTANCE);
        DocumentAuthors authors = document.getAuthors();
        authors.setEffectiveMetadataAuthor(user);
        authors.setOriginalMetadataAuthor(user);
        authors.setContentAuthor(user);

        if (document.isNew()) {
            authors.setCreator(user);
        }

        XWikiContext xcontext = this.xcontextProvider.get();

        try {
            // Let the listeners that watch what a user writes have their say, and cancel the save, as they do when a
            // page is saved from a script or from the editor.
            xcontext.getWiki().checkSavingDocument(xcontext.getUserReference(), document, comment, false, xcontext);
            xcontext.getWiki().saveDocument(document, comment, xcontext);
        } catch (XWikiException e) {
            throw new ReleaseNotesException(String.format("Failed to save the page [%s].", reference), e);
        }
    }
}
