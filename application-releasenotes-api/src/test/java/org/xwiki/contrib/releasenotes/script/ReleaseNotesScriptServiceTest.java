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
package org.xwiki.contrib.releasenotes.script;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.ChangeQuery;
import org.xwiki.contrib.releasenotes.ChangeQueryParser;
import org.xwiki.contrib.releasenotes.ChangeSearchResult;
import org.xwiki.contrib.releasenotes.ReleaseNote;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesConfiguration;
import org.xwiki.contrib.releasenotes.internal.ReleaseNotesCaller;
import org.xwiki.contrib.releasenotes.internal.ReleaseNotesEntryPoint;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReleaseNotesScriptService}, which only translates a script call into a call of
 * {@link ReleaseNotesEntryPoint}: a rule it enforced here would be a rule a REST caller escapes. What it adds is who
 * the caller is, a script whose author is checked along with the current user.
 *
 * @version $Id$
 */
@ComponentTest
class ReleaseNotesScriptServiceTest
{
    private static final DocumentReference RELEASE_NOTE = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", "XWiki", "8.3"), "WebHome");

    private static final DocumentReference AUTHOR = new DocumentReference("xwiki", "XWiki", "Author");

    private static final DocumentReference ENTRY = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", "XWiki", "8.3", "Entry001"), "WebHome");

    @InjectMockComponents
    private ReleaseNotesScriptService service;

    @MockComponent
    private ReleaseNotesEntryPoint entryPoint;

    @MockComponent
    private ReleaseNoteManager releaseNoteManager;

    @MockComponent
    private ChangeQueryParser changeQueryParser;

    @MockComponent
    private ReleaseNotesConfiguration configuration;

    @MockComponent
    private DocumentAccessBridge documentAccessBridge;

    @BeforeEach
    void setUp()
    {
        when(this.documentAccessBridge.getCurrentAuthorReference()).thenReturn(AUTHOR);
    }

    @Test
    void theReleaseNotesAreHandedToTheEntryPoint() throws Exception
    {
        ReleaseNote note = new ReleaseNote();
        List<ReleaseNote> notes = List.of(note);
        when(this.entryPoint.createReleaseNote(eq(note), any())).thenReturn(RELEASE_NOTE);
        when(this.entryPoint.getReleaseNote(eq(RELEASE_NOTE), any())).thenReturn(note);
        when(this.entryPoint.getReleaseNotes(eq("XWiki"), any())).thenReturn(notes);
        when(this.entryPoint.updateReleaseNote(eq(note), any())).thenReturn(note);
        when(this.releaseNoteManager.getReleaseNoteReference("XWiki", "8.3")).thenReturn(RELEASE_NOTE);
        when(this.releaseNoteManager.getAggregatedVersions(RELEASE_NOTE)).thenReturn(List.of("8.3"));

        assertEquals(RELEASE_NOTE, this.service.createReleaseNote(note));
        assertSame(note, this.service.updateReleaseNote(note));
        assertSame(note, this.service.getReleaseNote(RELEASE_NOTE));
        assertSame(notes, this.service.getReleaseNotes("XWiki"));
        // Naming a page, and naming the versions a release note displays, read nothing a right protects.
        assertEquals(RELEASE_NOTE, this.service.getReleaseNoteReference("XWiki", "8.3"));
        assertEquals(List.of("8.3"), this.service.getAggregatedVersions(RELEASE_NOTE));
    }

    @Test
    void theChangesAreHandedToTheEntryPoint() throws Exception
    {
        Change change = new Change();
        Map<String, String> parameters = Map.of("versions", "8.3");
        ChangeQuery query = new ChangeQuery();
        ChangeSearchResult result = new ChangeSearchResult(List.of(), List.of(), false);
        when(this.entryPoint.createChange(eq(change), any())).thenReturn(ENTRY);
        when(this.entryPoint.reserveNextEntry(eq("XWiki"), eq("8.3"), any())).thenReturn(ENTRY);
        when(this.entryPoint.getChange(eq(ENTRY), any())).thenReturn(change);
        when(this.entryPoint.updateChange(eq(ENTRY), eq(change), any())).thenReturn(change);
        when(this.changeQueryParser.parse(parameters)).thenReturn(query);
        when(this.entryPoint.search(eq(query), any())).thenReturn(result);

        assertEquals(ENTRY, this.service.createChange(change));
        assertSame(change, this.service.updateChange(ENTRY, change));
        assertEquals(ENTRY, this.service.reserveNextEntry("XWiki", "8.3"));
        assertSame(change, this.service.getChange(ENTRY));
        assertSame(query, this.service.parseQuery(parameters));
        assertSame(result, this.service.search(query));
    }

    /**
     * The service is reachable with the script right alone, so the entry point is told the caller is a script, whose
     * author it checks along with the current user: a check done for the user only would let a script write on behalf
     * of whoever reads the page it is on.
     */
    @Test
    void theCallerIsTheScriptAndItsAuthor() throws Exception
    {
        Change change = new Change();

        this.service.updateChange(ENTRY, change);

        ArgumentCaptor<ReleaseNotesCaller> caller = ArgumentCaptor.captor();
        verify(this.entryPoint).updateChange(eq(ENTRY), eq(change), caller.capture());
        assertTrue(caller.getValue().isScripted());
        assertEquals(AUTHOR, caller.getValue().getAuthor());
    }

    /**
     * A script with no author is still a script, and its author is checked, as the guest user.
     */
    @Test
    void aScriptWithoutAnAuthorIsStillCheckedForIt() throws Exception
    {
        when(this.documentAccessBridge.getCurrentAuthorReference()).thenReturn(null);
        ReleaseNote note = new ReleaseNote();

        this.service.createReleaseNote(note);

        ArgumentCaptor<ReleaseNotesCaller> caller = ArgumentCaptor.captor();
        verify(this.entryPoint).createReleaseNote(eq(note), caller.capture());
        assertTrue(caller.getValue().isScripted());
        assertNull(caller.getValue().getAuthor());
    }

    @Test
    void theDefaultsAreReadFromTheConfiguration()
    {
        when(this.configuration.getDefaultProduct()).thenReturn("XWiki");
        when(this.configuration.getDefaultTemplate()).thenReturn(RELEASE_NOTE);

        assertEquals("XWiki", this.service.getDefaultProduct());
        assertEquals(RELEASE_NOTE, this.service.getDefaultTemplate());
    }
}
