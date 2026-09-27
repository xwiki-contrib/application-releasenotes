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

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.ChangeManager;
import org.xwiki.contrib.releasenotes.ChangeQuery;
import org.xwiki.contrib.releasenotes.ChangeQueryParser;
import org.xwiki.contrib.releasenotes.ChangeSearchResult;
import org.xwiki.contrib.releasenotes.ReleaseNote;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesConfiguration;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReleaseNotesScriptService}, which is the one layer that must add nothing of its own: a rule
 * it enforced here would be a rule a REST or a Java caller escapes, since the components below are what all three
 * go through.
 *
 * @version $Id$
 */
@ComponentTest
class ReleaseNotesScriptServiceTest
{
    private static final DocumentReference RELEASE_NOTE = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", "XWiki", "8.3"), "WebHome");

    private static final DocumentReference ENTRY = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", "XWiki", "8.3", "Entry001"), "WebHome");

    @InjectMockComponents
    private ReleaseNotesScriptService service;

    @MockComponent
    private ReleaseNoteManager releaseNoteManager;

    @MockComponent
    private ChangeManager changeManager;

    @MockComponent
    private ChangeQueryParser changeQueryParser;

    @MockComponent
    private ReleaseNotesConfiguration configuration;

    @Test
    void theReleaseNotesAreHandedToTheReleaseNoteManager() throws Exception
    {
        ReleaseNote note = new ReleaseNote();
        List<ReleaseNote> notes = List.of(note);
        when(this.releaseNoteManager.createReleaseNote(note)).thenReturn(RELEASE_NOTE);
        when(this.releaseNoteManager.getReleaseNoteReference("XWiki", "8.3")).thenReturn(RELEASE_NOTE);
        when(this.releaseNoteManager.getReleaseNote(RELEASE_NOTE)).thenReturn(note);
        when(this.releaseNoteManager.getReleaseNotes("XWiki")).thenReturn(notes);
        when(this.releaseNoteManager.getAggregatedVersions(RELEASE_NOTE)).thenReturn(List.of("8.3"));
        when(this.releaseNoteManager.updateReleaseNote(note)).thenReturn(note);

        assertEquals(RELEASE_NOTE, this.service.createReleaseNote(note));
        assertSame(note, this.service.updateReleaseNote(note));
        assertEquals(RELEASE_NOTE, this.service.getReleaseNoteReference("XWiki", "8.3"));
        assertSame(note, this.service.getReleaseNote(RELEASE_NOTE));
        assertSame(notes, this.service.getReleaseNotes("XWiki"));
        assertEquals(List.of("8.3"), this.service.getAggregatedVersions(RELEASE_NOTE));
    }

    @Test
    void theChangesAreHandedToTheChangeManager() throws Exception
    {
        Change change = new Change();
        when(this.changeManager.createChange(change)).thenReturn(ENTRY);
        when(this.changeManager.reserveNextEntry("XWiki", "8.3")).thenReturn(ENTRY);
        when(this.changeManager.getChange(ENTRY)).thenReturn(change);
        when(this.changeManager.updateChange(ENTRY, change)).thenReturn(change);

        assertEquals(ENTRY, this.service.createChange(change));
        assertSame(change, this.service.updateChange(ENTRY, change));
        assertEquals(ENTRY, this.service.reserveNextEntry("XWiki", "8.3"));
        assertSame(change, this.service.getChange(ENTRY));
    }

    @Test
    void theSearchesAreHandedToTheParserAndToTheChangeManager() throws Exception
    {
        Map<String, String> parameters = Map.of("versions", "8.3");
        ChangeQuery query = new ChangeQuery();
        ChangeSearchResult result = new ChangeSearchResult(List.of(), List.of(), false);
        when(this.changeQueryParser.parse(parameters)).thenReturn(query);
        when(this.changeManager.search(query)).thenReturn(result);

        assertSame(query, this.service.parseQuery(parameters));
        assertSame(result, this.service.search(query));
    }

    /**
     * Versions are ordered the way they are released, and not alphabetically: a milestone comes before the release
     * candidate and the final version it leads to, and 11.10 comes after 11.4.
     */
    @Test
    void theVersionsAreSortedInTheOrderTheyAreReleasedIn()
    {
        List<String> versions = List.of("11.10", "11.4", "11.4-rc-1", "10.11.9", "11.4-milestone-1");

        assertEquals(List.of("10.11.9", "11.4-milestone-1", "11.4-rc-1", "11.4", "11.10"),
            this.service.sortVersions(versions));
    }

    @Test
    void theVersionsAreComparedInTheOrderTheyAreReleasedIn()
    {
        assertTrue(this.service.compareVersions("11.4-rc-1", "11.4") < 0);
        assertTrue(this.service.compareVersions("11.10", "11.4") > 0);
        assertEquals(0, this.service.compareVersions("11.4", "11.4"));
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
