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
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.ChangeManager;
import org.xwiki.contrib.releasenotes.ChangeQuery;
import org.xwiki.contrib.releasenotes.ChangeSearchResult;
import org.xwiki.contrib.releasenotes.ReleaseNote;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesAccessDeniedException;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.ReleaseNotesNotFoundException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReleaseNotesEntryPoint}, which is where the rights of a script call and of a REST request are
 * checked: the managers below it read and write a page whoever asks them to.
 *
 * @version $Id$
 */
@ComponentTest
class ReleaseNotesEntryPointTest
{
    private static final DocumentReference RELEASE_NOTE = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", "XWiki", "8.3"), "WebHome");

    private static final DocumentReference AUTHOR = new DocumentReference("xwiki", "XWiki", "Author");

    private static final DocumentReference ENTRY = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", "XWiki", "8.3", "Entry001"), "WebHome");

    private static final ReleaseNotesCaller SCRIPT = ReleaseNotesCaller.currentUserAndAuthor(AUTHOR);

    private static final ReleaseNotesCaller REQUEST = ReleaseNotesCaller.currentUser();

    @InjectMockComponents
    private ReleaseNotesEntryPoint entryPoint;

    @MockComponent
    private ReleaseNoteManager releaseNoteManager;

    @MockComponent
    private ChangeManager changeManager;

    @MockComponent
    private ContextualAuthorizationManager authorization;

    @MockComponent
    private AuthorizationManager authorAuthorization;

    @MockComponent
    private ProductResolver productResolver;

    @MockComponent
    private ReleaseNotesDocumentStore documentStore;

    private final XWikiDocument noteDocument = mock();

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.authorization.hasAccess(any(Right.class), any())).thenReturn(true);
        when(this.authorAuthorization.hasAccess(any(Right.class), any(), any())).thenReturn(true);
        when(this.productResolver.resolve(any())).thenReturn("XWiki");
        when(this.releaseNoteManager.getReleaseNoteReference("XWiki", "8.3")).thenReturn(RELEASE_NOTE);
        when(this.documentStore.load(RELEASE_NOTE)).thenReturn(this.noteDocument);
    }

    @Test
    void theReleaseNotesAreHandedToTheReleaseNoteManager() throws Exception
    {
        ReleaseNote note = releaseNote();
        List<ReleaseNote> notes = List.of(note);
        when(this.releaseNoteManager.createReleaseNote(note)).thenReturn(RELEASE_NOTE);
        when(this.releaseNoteManager.getReleaseNote(RELEASE_NOTE)).thenReturn(note);
        when(this.releaseNoteManager.getReleaseNotes(eq("XWiki"), any())).thenReturn(notes);
        when(this.releaseNoteManager.updateReleaseNote(note)).thenReturn(note);

        assertEquals(RELEASE_NOTE, this.entryPoint.createReleaseNote(note, SCRIPT));
        assertSame(note, this.entryPoint.updateReleaseNote(note, SCRIPT));
        assertSame(note, this.entryPoint.getReleaseNote(RELEASE_NOTE, SCRIPT));
        assertSame(notes, this.entryPoint.getReleaseNotes("XWiki", SCRIPT));
    }

    @Test
    void theChangesAreHandedToTheChangeManager() throws Exception
    {
        Change change = change();
        ChangeQuery query = new ChangeQuery();
        ChangeSearchResult result = new ChangeSearchResult(List.of(), List.of(), false);
        when(this.changeManager.createChange(change)).thenReturn(ENTRY);
        when(this.changeManager.reserveNextEntry("XWiki", "8.3")).thenReturn(ENTRY);
        when(this.changeManager.getChange(ENTRY)).thenReturn(change);
        when(this.changeManager.updateChange(ENTRY, change)).thenReturn(change);
        when(this.changeManager.search(eq(query), any())).thenReturn(result);

        assertEquals(ENTRY, this.entryPoint.createChange(change, SCRIPT));
        assertSame(change, this.entryPoint.updateChange(ENTRY, change, SCRIPT));
        assertEquals(ENTRY, this.entryPoint.reserveNextEntry("XWiki", "8.3", SCRIPT));
        assertSame(change, this.entryPoint.getChange(ENTRY, SCRIPT));
        assertSame(result, this.entryPoint.search(query, SCRIPT));
    }

    @Test
    void aReleaseNoteTheCurrentUserCannotViewIsNotRead() throws Exception
    {
        when(this.authorization.hasAccess(Right.VIEW, RELEASE_NOTE)).thenReturn(false);

        ReleaseNotesAccessDeniedException exception = assertThrows(ReleaseNotesAccessDeniedException.class,
            () -> this.entryPoint.getReleaseNote(RELEASE_NOTE, REQUEST));

        assertEquals(RELEASE_NOTE, exception.getReference());
        verify(this.releaseNoteManager, never()).getReleaseNote(any());
    }

    /**
     * A read is checked for the current user only, whoever the caller is, as the platform does when a script reads a
     * page: the author of a script is not asked whether it may view what the script reads.
     */
    @Test
    void aChangeTheCurrentUserCannotViewIsNotRead() throws Exception
    {
        when(this.authorization.hasAccess(Right.VIEW, ENTRY)).thenReturn(false);

        ReleaseNotesAccessDeniedException exception =
            assertThrows(ReleaseNotesAccessDeniedException.class, () -> this.entryPoint.getChange(ENTRY, SCRIPT));

        assertEquals("The current user is not allowed to view the page "
            + "[xwiki:ReleaseNotes.Data.XWiki.8\\.3.Entry001.WebHome].", exception.getMessage());
        verify(this.changeManager, never()).getChange(any());
        verify(this.authorAuthorization, never()).hasAccess(any(), any(), any());
    }

    /**
     * The release notes and the changes are listed with a filter that accepts the pages the current user can view
     * and refuses the others, which the managers apply before cutting the result into pages.
     */
    @Test
    void theListingsLeaveOutWhatTheCurrentUserCannotView() throws Exception
    {
        when(this.authorization.hasAccess(Right.VIEW, ENTRY)).thenReturn(false);

        this.entryPoint.getReleaseNotes("XWiki", REQUEST);
        this.entryPoint.search(new ChangeQuery(), REQUEST);
        this.entryPoint.searchAndLoad(new ChangeQuery(), REQUEST);

        ArgumentCaptor<Predicate<DocumentReference>> noteFilter = ArgumentCaptor.captor();
        verify(this.releaseNoteManager).getReleaseNotes(eq("XWiki"), noteFilter.capture());
        ArgumentCaptor<Predicate<DocumentReference>> changeFilter = ArgumentCaptor.captor();
        verify(this.changeManager).search(any(), changeFilter.capture());
        ArgumentCaptor<Predicate<DocumentReference>> loadedChangeFilter = ArgumentCaptor.captor();
        verify(this.changeManager).searchAndLoad(any(), loadedChangeFilter.capture());

        for (Predicate<DocumentReference> filter : List.of(noteFilter.getValue(), changeFilter.getValue(),
            loadedChangeFilter.getValue())) {
            assertTrue(filter.test(RELEASE_NOTE));
            assertFalse(filter.test(ENTRY));
        }
    }

    /**
     * Every write of a release note, and every new change, which is written to an entry page of its release note that
     * is only known once taken, is checked on the page of that release note.
     */
    @Test
    void aUserWhoCannotEditTheReleaseNoteWritesNothingToIt() throws Exception
    {
        when(this.authorization.hasAccess(Right.EDIT, RELEASE_NOTE)).thenReturn(false);
        ReleaseNote note = releaseNote();
        Change change = change();

        assertThrows(ReleaseNotesAccessDeniedException.class, () -> this.entryPoint.createReleaseNote(note, REQUEST));
        assertThrows(ReleaseNotesAccessDeniedException.class, () -> this.entryPoint.updateReleaseNote(note, REQUEST));
        assertThrows(ReleaseNotesAccessDeniedException.class, () -> this.entryPoint.createChange(change, REQUEST));
        // A caller that may not add a change is told so before the change it sent is even read.
        assertThrows(ReleaseNotesAccessDeniedException.class,
            () -> this.entryPoint.createChange("XWiki", "8.3", () -> fail("The change was read"), REQUEST));
        ReleaseNotesAccessDeniedException exception = assertThrows(ReleaseNotesAccessDeniedException.class,
            () -> this.entryPoint.reserveNextEntry(null, "8.3", SCRIPT));

        // A release note without a product is the release note of the product configured for the wiki, which is the
        // page whose right is checked.
        assertEquals("The current user is not allowed to edit the page "
            + "[xwiki:ReleaseNotes.Data.XWiki.8\\.3.WebHome].", exception.getMessage());
        verify(this.releaseNoteManager, never()).createReleaseNote(any());
        verify(this.releaseNoteManager, never()).updateReleaseNote(any());
        verify(this.changeManager, never()).createChange(any());
        verify(this.changeManager, never()).reserveNextEntry(any(), any());
    }

    /**
     * The right of the author of the calling script is checked too: the script service is reachable with the script
     * right alone, so a check done for the user only would let a script write on behalf of whoever reads the page it
     * is on.
     */
    @Test
    void aScriptAuthorWhoCannotEditThePageWritesNothingToIt() throws Exception
    {
        when(this.authorAuthorization.hasAccess(Right.EDIT, AUTHOR, ENTRY)).thenReturn(false);

        ReleaseNotesAccessDeniedException exception = assertThrows(ReleaseNotesAccessDeniedException.class,
            () -> this.entryPoint.updateChange(ENTRY, change(), SCRIPT));

        assertEquals("The author [xwiki:XWiki.Author] of the calling script is not allowed to edit the page "
            + "[xwiki:ReleaseNotes.Data.XWiki.8\\.3.Entry001.WebHome].", exception.getMessage());
        assertEquals(ENTRY, exception.getReference());
        verify(this.changeManager, never()).updateChange(any(), any());
    }

    /**
     * A script with no author is checked as the guest user, and not let through for having nobody to check.
     */
    @Test
    void aScriptWithoutAnAuthorIsCheckedAsTheGuestUser() throws Exception
    {
        when(this.authorAuthorization.hasAccess(Right.EDIT, null, RELEASE_NOTE)).thenReturn(false);
        ReleaseNotesCaller caller = ReleaseNotesCaller.currentUserAndAuthor(null);
        ReleaseNote note = releaseNote();

        assertThrows(ReleaseNotesAccessDeniedException.class, () -> this.entryPoint.createReleaseNote(note, caller));

        verify(this.releaseNoteManager, never()).createReleaseNote(any());
    }

    /**
     * A REST request runs no script, so the current user is the only one whose right there is to check.
     */
    @Test
    void aRequestIsCheckedForTheCurrentUserOnly() throws Exception
    {
        Change change = change();

        this.entryPoint.createChange(change, REQUEST);

        verify(this.authorization).hasAccess(Right.EDIT, RELEASE_NOTE);
        verify(this.authorAuthorization, never()).hasAccess(any(), any(), any());
        verify(this.changeManager).createChange(change);
    }

    /**
     * A change is written under its release note, and would otherwise be written under one that does not exist, where
     * nothing lists it. This used to be checked by the REST endpoint only, and not for a script.
     */
    @Test
    void aChangeIsNotCreatedForAReleaseNoteThatDoesNotExist() throws Exception
    {
        when(this.noteDocument.isNew()).thenReturn(true);
        Change change = change();

        ReleaseNotesNotFoundException exception =
            assertThrows(ReleaseNotesNotFoundException.class, () -> this.entryPoint.createChange(change, SCRIPT));
        assertThrows(ReleaseNotesNotFoundException.class,
            () -> this.entryPoint.createChange("XWiki", "8.3", () -> fail("The change was read"), REQUEST));

        assertEquals("There is no release note for the version [8.3] of [XWiki].", exception.getMessage());
        assertEquals(RELEASE_NOTE, exception.getReference());
        verify(this.changeManager, never()).createChange(any());
    }

    /**
     * The page taken for a new entry is under its release note too, and the "add change" form of the home page lets
     * its author type any version. Taking a page under a release note that does not exist would leave an entry that
     * nothing lists.
     */
    @Test
    void anEntryIsNotReservedForAReleaseNoteThatDoesNotExist() throws Exception
    {
        when(this.noteDocument.isNew()).thenReturn(true);

        ReleaseNotesNotFoundException exception = assertThrows(ReleaseNotesNotFoundException.class,
            () -> this.entryPoint.reserveNextEntry("XWiki", "8.3", SCRIPT));

        assertEquals("There is no release note for the version [8.3] of [XWiki].", exception.getMessage());
        assertEquals(RELEASE_NOTE, exception.getReference());
        verify(this.changeManager, never()).reserveNextEntry(any(), any());
    }

    /**
     * A write with no version names no page whose right could be checked, so it is handed to the manager, which
     * refuses it, as the script service did before this component existed.
     */
    @Test
    void aWriteWithoutAVersionIsLeftToTheManagerToRefuse() throws Exception
    {
        ReleaseNote note = new ReleaseNote();
        Change change = new Change();
        ReleaseNotesException refusal = new ReleaseNotesException("A release note needs the version it is about.");
        when(this.releaseNoteManager.createReleaseNote(note)).thenThrow(refusal);

        assertSame(refusal, assertThrows(ReleaseNotesException.class,
            () -> this.entryPoint.createReleaseNote(note, SCRIPT)));
        this.entryPoint.updateReleaseNote(note, SCRIPT);
        this.entryPoint.createChange(change, SCRIPT);
        this.entryPoint.reserveNextEntry("XWiki", " ", SCRIPT);

        verify(this.authorization, never()).hasAccess(any(), any());
        verify(this.releaseNoteManager).updateReleaseNote(note);
        verify(this.changeManager).createChange(change);
        verify(this.changeManager).reserveNextEntry("XWiki", " ");
    }

    private static ReleaseNote releaseNote()
    {
        ReleaseNote note = new ReleaseNote();
        note.setVersion("8.3");

        return note;
    }

    private static Change change()
    {
        Change change = new Change();
        change.setVersion("8.3");
        change.setTitle("Faster startup");

        return change;
    }
}
