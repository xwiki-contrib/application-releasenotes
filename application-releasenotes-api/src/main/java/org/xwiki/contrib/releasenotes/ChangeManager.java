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
package org.xwiki.contrib.releasenotes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.xwiki.component.annotation.Role;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.stability.Unstable;

/**
 * Creates and reads the changes of a release note.
 * <p>
 * A change lives in a page named {@code Entry###}, where {@code ###} is a zero-padded number, under the page of the
 * release note it belongs to.
 * <p>
 * No right is checked: the entry points of the application, its REST endpoints and its script service, check the
 * rights of their caller before calling this, and a Java caller inside the wiki is trusted to have done the same.
 *
 * @version $Id$
 * @since 2.7
 */
@Role
@Unstable
public interface ChangeManager
{
    /**
     * Creates a change: allocates the page of a new entry, fills it from the change template and saves it once.
     * <p>
     * This is not what the "Add Change" buttons of the application do, which is {@link #reserveNextEntry(String,
     * String)}: a change created here is complete when it is saved, and is therefore never left half created.
     *
     * @param change the change to create, which needs at least a version and a title, and a product unless one is
     *            configured for the wiki
     * @return the page the change was created in
     * @throws ReleaseNotesException when the change carries no version or no title, no product could be determined,
     *             no page name was free, or the save failed
     */
    DocumentReference createChange(Change change) throws ReleaseNotesException;

    /**
     * Replaces a change: the change the page holds becomes the one passed, and a property the passed change leaves
     * out is emptied rather than kept. The change template has no say here, unlike in
     * {@link #createChange(Change)}: a template gives a new change the values its author has not written yet, and a
     * replacement is written in full.
     * <p>
     * The product and the version of the change are not replaced: they say which release note the change belongs to,
     * which is the page tree it lives in, so moving it there is a move of the page and not an update of its
     * properties.
     *
     * @param reference the page of the change to replace
     * @param change the change that page is to hold, which needs at least a title
     * @return the change as it is stored once replaced, which is not exactly the change passed: a title is trimmed,
     *         and the product and the version are the ones the change keeps
     * @throws ReleaseNotesNotFoundException when that page holds no change
     * @throws ReleaseNotesException when the change carries no title, or the save failed
     * @since 2.8
     */
    Change updateChange(DocumentReference reference, Change change) throws ReleaseNotesException;

    /**
     * Takes the page of a new entry of a release note, saving it empty, and returns it. This is what the "Add Change"
     * buttons of the application do before sending their author to the editor.
     * <p>
     * The page is taken before its author starts editing, and not left to the editor to create when that author
     * saves, because the name of a new entry is derived from the entries that already exist and an entry only starts
     * existing when it is saved: two authors adding a change to the same release note at the same time would
     * otherwise be handed that same page and the second save would silently overwrite the first.
     * <p>
     * It is left empty on purpose: the {@code edit} action the author is sent to applies the change template to any
     * page without content, so the objects of the change are created there, once.
     *
     * @param product the product of the release note to add an entry to
     * @param version the version of the release note to add an entry to, in its long form
     * @return the page that was taken, or {@code null} when no page name was free
     * @throws ReleaseNotesException when the existing entries could not be looked up, or the save failed
     */
    DocumentReference reserveNextEntry(String product, String version) throws ReleaseNotesException;

    /**
     * @param reference the page of a change
     * @return the change that page holds
     * @throws ReleaseNotesNotFoundException when that page holds no change
     * @throws ReleaseNotesException when that page could not be loaded
     */
    Change getChange(DocumentReference reference) throws ReleaseNotesException;

    /**
     * Looks for the changes matching a query, one page of them at a time: the filters of a query default to matching
     * everything, so a search can match every change the wiki holds, and each change it returns costs a document
     * load in the pages displaying them.
     * <p>
     * No right is checked: every matching change is returned. An entry point of the application answering a caller
     * who may not view every change uses {@link #search(ChangeQuery, Predicate)} instead.
     *
     * @param query the changes to look for
     * @return the page of the matching changes the query asks for, and whether more of them matched
     * @throws ReleaseNotesException when the changes could not be looked up
     */
    default ChangeSearchResult search(ChangeQuery query) throws ReleaseNotesException
    {
        return search(query, reference -> true);
    }

    /**
     * Looks for the changes matching a query that the passed filter also accepts, one page of them at a time.
     * <p>
     * The filter is applied before the result is cut into pages: the limit and the offset of the query count the
     * accepted changes only, and whether more changes follow the page is told of the accepted ones only. That is what
     * makes this, rather than a filter applied to the returned page, the way of leaving out the changes a caller
     * cannot view: the page is never shorter than the limit while more changes follow it, and a change that is left
     * out does not announce a next page.
     *
     * @param query the changes to look for
     * @param filter tells, for the page of a matching change, whether that change is part of the result
     * @return the page of the matching changes the query asks for, and whether more of them matched
     * @throws ReleaseNotesException when the changes could not be looked up
     * @since 2.8
     */
    ChangeSearchResult search(ChangeQuery query, Predicate<DocumentReference> filter) throws ReleaseNotesException;

    /**
     * Looks for the changes matching a query that the passed filter also accepts, one page of them at a time, as
     * {@link #search(ChangeQuery, Predicate)} does, and reads the changes of that page too, for a caller that answers
     * with what the changes say rather than with where they live.
     *
     * @param query the changes to look for
     * @param filter tells, for the page of a matching change, whether that change is part of the result
     * @return the page of the matching changes the query asks for, the changes themselves, and whether more of them
     *         matched
     * @throws ReleaseNotesException when the changes could not be looked up, or one of them could not be read
     * @since 2.8
     */
    default LoadedChangeSearchResult searchAndLoad(ChangeQuery query, Predicate<DocumentReference> filter)
        throws ReleaseNotesException
    {
        ChangeSearchResult result = search(query, filter);
        List<Change> changes = new ArrayList<>(result.getChanges().size());

        for (DocumentReference reference : result.getChanges()) {
            changes.add(getChange(reference));
        }

        return new LoadedChangeSearchResult(result, changes);
    }
}
