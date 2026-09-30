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

import java.util.function.Predicate;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.ChangeManager;
import org.xwiki.contrib.releasenotes.ChangeQuery;
import org.xwiki.contrib.releasenotes.ChangeSearchResult;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.internal.ChangeXObjects.WriteMode;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.stability.Unstable;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Default implementation of {@link ChangeManager}, which holds the changes of a release note in the {@code Entry###}
 * pages under it.
 *
 * @version $Id$
 * @since 2.7
 */
@Component
@Singleton
@Unstable
public class DefaultChangeManager implements ChangeManager
{
    /**
     * What a change with no title is refused with, both when it is created and when it is replaced: a change with no
     * title is displayed as an empty line by every displayer, which makes it look like the change is missing rather
     * than like its title is.
     */
    private static final String NO_TITLE = "A change needs a title.";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private ProductResolver productResolver;

    @Inject
    private ReleaseNotesDocumentStore documentStore;

    @Inject
    private EntryPageAllocator entryPageAllocator;

    @Inject
    private ChangeSearcher changeSearcher;

    @Override
    public DocumentReference createChange(Change change) throws ReleaseNotesException
    {
        String version = StringUtils.trimToNull(change.getVersion());

        if (version == null) {
            throw new ReleaseNotesException("A change needs the version it was made in.");
        }

        if (StringUtils.isBlank(change.getTitle())) {
            throw new ReleaseNotesException(NO_TITLE);
        }

        String product = this.productResolver.resolve(change.getProduct());
        XWikiContext xcontext = this.xcontextProvider.get();
        XWikiDocument document = this.entryPageAllocator.takeNextEntryPage(product, version, xcontext);

        if (document == null) {
            throw new ReleaseNotesException(
                String.format("No page was free for a new change of the version [%s] of [%s].", version, product));
        }

        try {
            // The objects of a change are created by the change template, and not here, so that a template an
            // administrator has customised is what a change is made of, whichever way it was created.
            DocumentReference templateReference = new DocumentReference(ReleaseNotesReferences.CHANGE_TEMPLATE,
                document.getDocumentReference().getWikiReference());
            document.readFromTemplate(templateReference, xcontext);
            // A change enforces its required rights when its template does. That is set here because applying a
            // template does not carry the setting over on every XWiki version the application supports.
            document.setEnforceRequiredRights(this.documentStore.load(templateReference).isEnforceRequiredRights());

            ChangeXObjects.writeEntry(document, product, version, xcontext);
            ChangeXObjects.writeChange(document, change, WriteMode.OVER_TEMPLATE, xcontext);
        } catch (XWikiException e) {
            throw new ReleaseNotesException(
                String.format("Failed to fill the page [%s] of the new change.", document.getDocumentReference()), e);
        }

        this.documentStore.save(document, "New change");

        return document.getDocumentReference();
    }

    @Override
    public Change updateChange(DocumentReference reference, Change change) throws ReleaseNotesException
    {
        if (StringUtils.isBlank(change.getTitle())) {
            throw new ReleaseNotesException(NO_TITLE);
        }

        // The page exists, and the instance the store answers with for a page that exists is the one it holds in its
        // cache, which a caller must not write into: what is modified here is a copy of it, and the save is what
        // makes the wiki hold it.
        XWikiDocument document = this.documentStore.load(reference).clone();

        try {
            ChangeXObjects.writeChange(document, change, WriteMode.REPLACE, this.xcontextProvider.get());
        } catch (XWikiException e) {
            throw new ReleaseNotesException(
                String.format("Failed to write the change of the page [%s].", reference), e);
        }

        this.documentStore.save(document, "Updated change");

        return ChangeXObjects.read(document);
    }

    @Override
    public DocumentReference reserveNextEntry(String product, String version) throws ReleaseNotesException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        XWikiDocument document = this.entryPageAllocator
            .takeNextEntryPage(this.productResolver.resolve(product), version, xcontext);

        if (document == null) {
            return null;
        }

        this.documentStore.save(document, "Take the page of a new release note entry");

        return document.getDocumentReference();
    }

    @Override
    public Change getChange(DocumentReference reference) throws ReleaseNotesException
    {
        return ChangeXObjects.read(this.documentStore.load(reference));
    }

    @Override
    public ChangeSearchResult search(ChangeQuery query, Predicate<DocumentReference> filter)
        throws ReleaseNotesException
    {
        return this.changeSearcher.search(query, filter);
    }
}
