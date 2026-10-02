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

import java.util.List;
import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.ChangeManager;
import org.xwiki.contrib.releasenotes.ChangeQuery;
import org.xwiki.contrib.releasenotes.ChangeSearchResult;
import org.xwiki.contrib.releasenotes.LoadedChangeSearchResult;
import org.xwiki.contrib.releasenotes.ReleaseNote;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesAccessDeniedException;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.ReleaseNotesNotFoundException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

/**
 * Where a call enters the application, whether it comes from a script or from a REST request: it locates the page the
 * call is about, checks that the caller may read or write it, checks that a change is added to a release note that
 * exists, and only then hands the call to {@link ReleaseNoteManager} or {@link ChangeManager}.
 * <p>
 * The managers read and write a page whoever asks them to. The rights are checked here, once, and not in the managers,
 * so that a Java caller that has already decided what it may do is not refused by a check made for somebody else,
 * and so that the script service and the REST endpoints cannot drift apart: they translate what they are given into a
 * call of this component, and what it throws into what their caller is told.
 * </p>
 * <ul>
 * <li>A read is checked for the current user only, as the platform does when a script or a request reads a page.</li>
 * <li>A write is checked for the current user and, when the caller is a script, for the author of that script too:
 * the script service is reachable with the script right alone, so a check made for the user only would let a script
 * write on behalf of whoever happens to be viewing the page it is on.</li>
 * </ul>
 *
 * @version $Id$
 * @since 2.8
 */
@Component(roles = ReleaseNotesEntryPoint.class)
@Singleton
public class ReleaseNotesEntryPoint
{
    @Inject
    private ReleaseNoteManager releaseNoteManager;

    @Inject
    private ChangeManager changeManager;

    @Inject
    private ProductResolver productResolver;

    @Inject
    private ReleaseNotesDocumentStore documentStore;

    @Inject
    private ContextualAuthorizationManager authorization;

    @Inject
    private AuthorizationManager authorAuthorization;

    /**
     * @param note the release note to create
     * @param caller who the release note is created for
     * @return the page the release note was created in
     * @throws ReleaseNotesAccessDeniedException when the caller may not edit the page of the release note
     * @throws ReleaseNotesException when it could not be created, e.g. because it has no version, or no product while
     *             the wiki has no default one
     * @see ReleaseNoteManager#createReleaseNote(ReleaseNote)
     */
    public DocumentReference createReleaseNote(ReleaseNote note, ReleaseNotesCaller caller)
        throws ReleaseNotesException
    {
        checkEditRight(note.getProduct(), note.getVersion(), caller);

        return this.releaseNoteManager.createReleaseNote(note);
    }

    /**
     * @param note the release note to replace, located by its product and its version
     * @param caller who the release note is replaced for
     * @return the release note as it is stored once replaced
     * @throws ReleaseNotesAccessDeniedException when the caller may not edit the page of the release note
     * @throws ReleaseNotesException when it could not be replaced, e.g. because it has no version
     * @see ReleaseNoteManager#updateReleaseNote(ReleaseNote)
     */
    public ReleaseNote updateReleaseNote(ReleaseNote note, ReleaseNotesCaller caller) throws ReleaseNotesException
    {
        checkEditRight(note.getProduct(), note.getVersion(), caller);

        return this.releaseNoteManager.updateReleaseNote(note);
    }

    /**
     * @param reference the page of a release note
     * @param caller who the release note is read for
     * @return the release note that page holds
     * @throws ReleaseNotesAccessDeniedException when the current user may not view that page
     * @throws ReleaseNotesException when that page holds no release note
     * @see ReleaseNoteManager#getReleaseNote(DocumentReference)
     */
    public ReleaseNote getReleaseNote(DocumentReference reference, ReleaseNotesCaller caller)
        throws ReleaseNotesException
    {
        checkViewRight(reference);

        return this.releaseNoteManager.getReleaseNote(reference);
    }

    /**
     * @param product the product to list the release notes of, or {@code null} to list them all
     * @param caller who the release notes are listed for
     * @return the release notes of that product the current user may view
     * @throws ReleaseNotesException when they could not be looked up
     * @see ReleaseNoteManager#getReleaseNotes(String, java.util.function.Predicate)
     */
    public List<ReleaseNote> getReleaseNotes(String product, ReleaseNotesCaller caller) throws ReleaseNotesException
    {
        return this.releaseNoteManager.getReleaseNotes(product, this::canView);
    }

    /**
     * Creates a change in a new entry page of its release note. That page is not known until it is taken, so the right
     * checked is the right to edit the release note, as the pages of the application do before offering to add a
     * change.
     *
     * @param change the change to create
     * @param caller who the change is created for
     * @return the page the change was created in
     * @throws ReleaseNotesAccessDeniedException when the caller may not edit the page of the release note
     * @throws ReleaseNotesNotFoundException when there is no release note for the version of the change
     * @throws ReleaseNotesException when it could not be created, e.g. because it has no version or no title, or no
     *             product while the wiki has no default one
     * @see ChangeManager#createChange(Change)
     */
    public DocumentReference createChange(Change change, ReleaseNotesCaller caller) throws ReleaseNotesException
    {
        return createChange(change.getProduct(), change.getVersion(), () -> change, caller);
    }

    /**
     * Creates a change in a new entry page of a release note, reading the change only once the caller is known to be
     * allowed to add one to that release note and the release note is known to exist: a caller that may not add a
     * change is told so before it is told what is wrong with the change it sent.
     *
     * @param product the product of the release note to add the change to, or {@code null} for the default one
     * @param version the version of that release note, in its long form
     * @param change what gives the change to create, which may refuse the request by throwing an unchecked exception
     * @param caller who the change is created for
     * @return the page the change was created in
     * @throws ReleaseNotesAccessDeniedException when the caller may not edit the page of the release note
     * @throws ReleaseNotesNotFoundException when there is no release note for that version
     * @throws ReleaseNotesException when it could not be created
     * @see #createChange(Change, ReleaseNotesCaller)
     */
    public DocumentReference createChange(String product, String version, Supplier<Change> change,
        ReleaseNotesCaller caller) throws ReleaseNotesException
    {
        DocumentReference noteReference = getReleaseNoteReference(product, version);

        if (noteReference != null) {
            checkEditRight(noteReference, caller);
            checkReleaseNoteExists(noteReference, product, version);
        }

        return this.changeManager.createChange(change.get());
    }

    /**
     * @param reference the page of the change to replace
     * @param change the change that page is to hold
     * @param caller who the change is replaced for
     * @return the change as it is stored once replaced
     * @throws ReleaseNotesAccessDeniedException when the caller may not edit that page
     * @throws ReleaseNotesException when it could not be replaced, e.g. because it has no title
     * @see ChangeManager#updateChange(DocumentReference, Change)
     */
    public Change updateChange(DocumentReference reference, Change change, ReleaseNotesCaller caller)
        throws ReleaseNotesException
    {
        checkEditRight(reference, caller);

        return this.changeManager.updateChange(reference, change);
    }

    /**
     * Takes the page of a new entry of a release note. That page is not known until it is taken, so the right checked
     * is the right to edit the release note.
     *
     * @param product the product of the release note to add an entry to, or {@code null} for the default one
     * @param version the version of the release note to add an entry to, in its long form
     * @param caller who the page is taken for
     * @return the page that was taken, or {@code null} when no page name was free
     * @throws ReleaseNotesAccessDeniedException when the caller may not edit the page of the release note
     * @throws ReleaseNotesNotFoundException when there is no release note for that version
     * @throws ReleaseNotesException when the page could not be taken, e.g. because no product was given while the
     *             wiki has no default one
     * @see ChangeManager#reserveNextEntry(String, String)
     */
    public DocumentReference reserveNextEntry(String product, String version, ReleaseNotesCaller caller)
        throws ReleaseNotesException
    {
        DocumentReference noteReference = getReleaseNoteReference(product, version);

        if (noteReference != null) {
            checkEditRight(noteReference, caller);
            checkReleaseNoteExists(noteReference, product, version);
        }

        return this.changeManager.reserveNextEntry(product, version);
    }

    /**
     * @param reference the page of a change
     * @param caller who the change is read for
     * @return the change that page holds
     * @throws ReleaseNotesAccessDeniedException when the current user may not view that page
     * @throws ReleaseNotesException when that page holds no change
     * @see ChangeManager#getChange(DocumentReference)
     */
    public Change getChange(DocumentReference reference, ReleaseNotesCaller caller) throws ReleaseNotesException
    {
        checkViewRight(reference);

        return this.changeManager.getChange(reference);
    }

    /**
     * @param query the changes to look for
     * @param caller who the changes are looked for
     * @return the page of the matching changes the current user may view that the query asks for, and whether more
     *         of them matched
     * @throws ReleaseNotesException when the changes could not be looked up
     * @see ChangeManager#search(ChangeQuery, java.util.function.Predicate)
     */
    public ChangeSearchResult search(ChangeQuery query, ReleaseNotesCaller caller) throws ReleaseNotesException
    {
        return this.changeManager.search(query, this::canView);
    }

    /**
     * @param query the changes to look for
     * @param caller who the changes are looked for
     * @return the page of the matching changes the current user may view that the query asks for, the changes
     *         themselves, and whether more of them matched
     * @throws ReleaseNotesException when the changes could not be looked up or read
     * @see ChangeManager#searchAndLoad(ChangeQuery, java.util.function.Predicate)
     */
    public LoadedChangeSearchResult searchAndLoad(ChangeQuery query, ReleaseNotesCaller caller)
        throws ReleaseNotesException
    {
        // The view right is checked once, by the filter of the search, which is what leaves a change the user may not
        // view out of the page before it is cut: the changes read afterwards are the ones it accepted.
        return this.changeManager.searchAndLoad(query, this::canView);
    }

    /**
     * @param product the product that was asked for, or {@code null} for the default one
     * @param version the version that was asked for, in its long form
     * @return the page of the release note of that product and that version, whether it exists or not, or
     *         {@code null} when no version was asked for: that locates no page, there is then no right to check, and
     *         the manager refuses the call itself
     * @throws ReleaseNotesException when no product was asked for and the wiki has no default one
     */
    private DocumentReference getReleaseNoteReference(String product, String version) throws ReleaseNotesException
    {
        String trimmedVersion = StringUtils.trimToNull(version);

        if (trimmedVersion == null) {
            return null;
        }

        return this.releaseNoteManager.getReleaseNoteReference(this.productResolver.resolve(product),
            trimmedVersion);
    }

    /**
     * @param noteReference the page of the release note a change is added to
     * @param product the product of that release note, as it was asked for
     * @param version the version of that release note, as it was asked for
     * @throws ReleaseNotesNotFoundException when that page does not exist: the manager would otherwise write the
     *             change, or take its page, under a release note that does not exist, where nothing lists it
     * @throws ReleaseNotesException when that page could not be loaded
     */
    private void checkReleaseNoteExists(DocumentReference noteReference, String product, String version)
        throws ReleaseNotesException
    {
        if (this.documentStore.load(noteReference).isNew()) {
            throw new ReleaseNotesNotFoundException(
                String.format("There is no release note for the version [%s] of [%s].", version.trim(),
                    this.productResolver.resolve(product)),
                noteReference);
        }
    }

    /**
     * @param reference a page the caller asked to read
     * @return whether the current user may view that page, the only one checked for a read whoever the caller is, as
     *         the platform does when a script reads a page
     */
    private boolean canView(DocumentReference reference)
    {
        return this.authorization.hasAccess(Right.VIEW, reference);
    }

    private void checkViewRight(DocumentReference reference) throws ReleaseNotesAccessDeniedException
    {
        if (!canView(reference)) {
            throw new ReleaseNotesAccessDeniedException(
                String.format("The current user is not allowed to view the page [%s].", reference), reference);
        }
    }

    /**
     * @param product the product of the release note the caller asked to write, or {@code null} for the default one
     * @param version the version of that release note, in its long form, or {@code null} when none was given, which
     *            locates no page to check and is refused by the manager itself
     * @param caller who asked
     * @throws ReleaseNotesAccessDeniedException when the caller may not edit the page of that release note
     * @throws ReleaseNotesException when no product was given and the wiki has no default one
     */
    private void checkEditRight(String product, String version, ReleaseNotesCaller caller) throws ReleaseNotesException
    {
        DocumentReference reference = getReleaseNoteReference(product, version);

        if (reference != null) {
            checkEditRight(reference, caller);
        }
    }

    private void checkEditRight(DocumentReference reference, ReleaseNotesCaller caller)
        throws ReleaseNotesAccessDeniedException
    {
        if (!this.authorization.hasAccess(Right.EDIT, reference)) {
            throw new ReleaseNotesAccessDeniedException(
                String.format("The current user is not allowed to edit the page [%s].", reference), reference);
        }

        if (caller.isScripted() && !this.authorAuthorization.hasAccess(Right.EDIT, caller.getAuthor(), reference)) {
            throw new ReleaseNotesAccessDeniedException(
                String.format("The author [%s] of the calling script is not allowed to edit the page [%s].",
                    caller.getAuthor(), reference),
                reference);
        }
    }
}
