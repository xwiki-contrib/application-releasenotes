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
package org.xwiki.contrib.releasenotes.rest.internal;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import javax.inject.Named;

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.releasenotes.Audience;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.Importance;
import org.xwiki.contrib.releasenotes.ReleaseNote;
import org.xwiki.contrib.releasenotes.rest.model.ChangeRepresentation;
import org.xwiki.contrib.releasenotes.rest.model.ReleaseNoteRepresentation;
import org.xwiki.model.internal.reference.DefaultSymbolScheme;
import org.xwiki.model.internal.reference.LocalStringEntityReferenceSerializer;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RepresentationFactory}.
 *
 * @version $Id$
 */
@ComponentTest
// Serializing the page a release note or a change lives in is part of what a representation holds, so the serializer
// is the real one.
@ComponentList({ LocalStringEntityReferenceSerializer.class, DefaultSymbolScheme.class })
class RepresentationFactoryTest
{
    private static final DocumentReference ENTRY = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", "XWiki", "8.3"), "WebHome");

    @InjectMockComponents
    private RepresentationFactory factory;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Test
    void aPostedReleaseNoteIsReadWithItsDateAsADayAndItsTemplateAsAPage()
    {
        DocumentReference template = new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "MyTemplate");
        when(this.documentReferenceResolver.resolve("ReleaseNotes.Code.MyTemplate")).thenReturn(template);

        ReleaseNoteRepresentation representation = new ReleaseNoteRepresentation();
        representation.setProduct("XWiki");
        representation.setVersion("8.3-milestone-1");
        representation.setDate("2026-09-09");
        representation.setReleased(true);
        representation.setTemplate("ReleaseNotes.Code.MyTemplate");

        ReleaseNote note = this.factory.toReleaseNote(representation);

        assertEquals("XWiki", note.getProduct());
        assertEquals("8.3-milestone-1", note.getVersion());
        assertEquals(day("2026-09-09"), note.getDate());
        assertTrue(note.isReleased());
        assertEquals(template, note.getTemplate());
    }

    /**
     * What a release note says nothing about is what the wiki or the template decides, so it is left unset rather
     * than set to a value of this API's own choosing.
     */
    @Test
    void aPostedReleaseNoteSayingOnlyItsVersionIsReadAsThatVersionAlone()
    {
        ReleaseNoteRepresentation representation = new ReleaseNoteRepresentation();
        representation.setVersion("8.3");

        ReleaseNote note = this.factory.toReleaseNote(representation);

        assertNull(note.getProduct());
        assertNull(note.getDate());
        assertNull(note.getTemplate());
        assertFalse(note.isReleased());
    }

    /**
     * A client that has a full date and time at hand, such as one reading the date of a build, has its time of day
     * dropped: a release date is a day, which is the granularity the release notes are sorted by.
     */
    @Test
    void aPostedDateCarryingATimeOfDayIsReadAsItsDay()
    {
        ReleaseNoteRepresentation representation = new ReleaseNoteRepresentation();
        representation.setVersion("8.3");
        representation.setDate("2026-09-09T17:32:07Z");

        assertEquals(day("2026-09-09"), this.factory.toReleaseNote(representation).getDate());
    }

    @Test
    void aPostedDateThatIsNoDayIsRefused()
    {
        ReleaseNoteRepresentation representation = new ReleaseNoteRepresentation();
        representation.setVersion("8.3");
        representation.setDate("09/09/2026");

        IllegalArgumentException exception =
            assertThrows(IllegalArgumentException.class, () -> this.factory.toReleaseNote(representation));

        assertEquals("The date [09/09/2026] is not a day written yyyy-MM-dd.", exception.getMessage());
    }

    @Test
    void aReleaseNoteIsWrittenWithThePageItLivesIn()
    {
        ReleaseNote note = new ReleaseNote();
        note.setProduct("XWiki");
        note.setVersion("8.3");
        note.setDate(day("2026-09-09"));

        ReleaseNoteRepresentation representation = this.factory.toRepresentation(note, ENTRY);

        assertEquals("XWiki", representation.getProduct());
        assertEquals("8.3", representation.getVersion());
        assertEquals("2026-09-09", representation.getDate());
        assertEquals(Boolean.FALSE, representation.getReleased());
        assertEquals("ReleaseNotes.Data.XWiki.8\\.3.WebHome", representation.getReference());
    }

    /**
     * A release note whose day is not decided yet holds no date, and a release note that lives nowhere this API can
     * name holds no page: neither is reported as a value of its own.
     */
    @Test
    void aReleaseNoteWithNoDateAndNoPageIsWrittenWithNeither()
    {
        ReleaseNoteRepresentation representation = this.factory.toRepresentation(new ReleaseNote(), null);

        assertNull(representation.getDate());
        assertNull(representation.getReference());
    }

    @Test
    void aPostedChangeIsReadWithTheProductAndTheVersionOfItsReleaseNote()
    {
        ChangeRepresentation representation = new ChangeRepresentation();
        representation.setTitle("The title");
        representation.setSummary("The summary");
        representation.setDescription("The description");
        representation.setMigrationNotes("Delete the Solr cache before upgrading.");
        representation.setAudience("administrator");
        representation.setImportance("high");
        representation.setCategory("Performance");
        representation.setScreenshots(List.of("shot.png", "clip.mp4"));
        // A posted change naming another release note than the URL it is posted to is stored against the URL: that
        // is the release note the client asked to add a change to.
        representation.setProduct("Another product");
        representation.setVersion("1.0");

        Change change = this.factory.toChange(representation, "XWiki", "8.3-milestone-1");

        assertEquals("XWiki", change.getProduct());
        assertEquals("8.3-milestone-1", change.getVersion());
        assertEquals("The title", change.getTitle());
        assertEquals("The summary", change.getSummary());
        assertEquals("The description", change.getDescription());
        assertEquals("Delete the Solr cache before upgrading.", change.getMigrationNotes());
        assertEquals(Audience.ADMINISTRATOR, change.getAudience());
        assertEquals(Importance.HIGH, change.getImportance());
        assertEquals("Performance", change.getCategory());
        assertEquals(List.of("shot.png", "clip.mp4"), change.getScreenshots());
    }

    /**
     * The numbers an importance is stored as are accepted too, so that a change read out of one wiki can be posted
     * to another one as it stands.
     */
    @Test
    void aPostedImportanceIsReadFromItsNameOrFromTheNumberItIsStoredAs()
    {
        assertEquals(Importance.LOW, importanceOf("low"));
        assertEquals(Importance.MEDIUM, importanceOf("Medium"));
        assertEquals(Importance.HIGH, importanceOf("2"));
        assertNull(importanceOf(null));
        assertNull(importanceOf(" "));
    }

    @Test
    void aPostedImportanceThatIsNoImportanceIsRefused()
    {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> importanceOf("huge"));

        assertEquals("The importance [huge] is none of [low, medium, high].", exception.getMessage());
    }

    @Test
    void aPostedAudienceIsReadWhateverItsCase()
    {
        assertEquals(Audience.USER, audienceOf("User"));
        assertEquals(Audience.DEVELOPER, audienceOf("developer"));
        assertNull(audienceOf(null));
        assertNull(audienceOf(" "));
    }

    @Test
    void aPostedAudienceThatIsNoAudienceIsRefused()
    {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> audienceOf("nobody"));

        assertEquals("The audience [nobody] is none of [user, administrator, developer].", exception.getMessage());
    }

    /**
     * A change is written with the words a client reads and writes, and not with the values the database orders on:
     * an importance is {@code high}, not the {@code 2} it is stored as.
     */
    @Test
    void aChangeIsWrittenWithTheWordsItsValuesAreNamedBy()
    {
        Change change = new Change();
        change.setProduct("XWiki");
        change.setVersion("8.3");
        change.setTitle("The title");
        change.setAudience(Audience.DEVELOPER);
        change.setImportance(Importance.MEDIUM);
        change.setMigrationNotes("Delete the Solr cache before upgrading.");

        ChangeRepresentation representation = this.factory.toRepresentation(change, ENTRY);

        assertEquals("XWiki", representation.getProduct());
        assertEquals("8.3", representation.getVersion());
        assertEquals("The title", representation.getTitle());
        assertEquals("developer", representation.getAudience());
        assertEquals("medium", representation.getImportance());
        assertEquals("Delete the Solr cache before upgrading.", representation.getMigrationNotes());
        assertEquals("ReleaseNotes.Data.XWiki.8\\.3.WebHome", representation.getReference());
    }

    /**
     * The entry a change lives in is what addresses it: it is the last path segment of the URL a change is read
     * from and replaced at, so a client is given it rather than left to parse it out of the page reference.
     */
    @Test
    void aChangeIsWrittenWithTheEntryItLivesIn()
    {
        ChangeRepresentation representation = this.factory.toRepresentation(new Change(),
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "XWiki", "8.3", "Entry001"), "WebHome"));

        assertEquals("Entry001", representation.getEntry());
        assertEquals("ReleaseNotes.Data.XWiki.8\\.3.Entry001.WebHome", representation.getReference());

        ChangeRepresentation nowhere = this.factory.toRepresentation(new Change(), null);

        assertNull(nowhere.getEntry());
        assertNull(nowhere.getReference());
    }

    @Test
    void aChangeWithNoAudienceAndNoImportanceIsWrittenWithNeither()
    {
        ChangeRepresentation representation = this.factory.toRepresentation(new Change(), ENTRY);

        assertNull(representation.getAudience());
        assertNull(representation.getImportance());
    }

    /**
     * A release note that is replaced is the one the URL names, whatever the request says it is about: the product
     * and the version are what its page is named after, so they locate it rather than being written to it.
     */
    @Test
    void aReleaseNoteToReplaceIsReadWithTheProductAndTheVersionOfItsUrl()
    {
        ReleaseNoteRepresentation representation = new ReleaseNoteRepresentation();
        representation.setProduct("Another");
        representation.setVersion("9.9");
        representation.setReleased(true);

        ReleaseNote note = this.factory.toReleaseNote(representation, "XWiki", "8.3");

        assertEquals("XWiki", note.getProduct());
        assertEquals("8.3", note.getVersion());
        assertTrue(note.isReleased());
    }

    private Importance importanceOf(String importance)
    {
        ChangeRepresentation representation = new ChangeRepresentation();
        representation.setImportance(importance);

        return this.factory.toChange(representation, "XWiki", "8.3").getImportance();
    }

    private Audience audienceOf(String audience)
    {
        ChangeRepresentation representation = new ChangeRepresentation();
        representation.setAudience(audience);

        return this.factory.toChange(representation, "XWiki", "8.3").getAudience();
    }

    private static Date day(String day)
    {
        return Date.from(LocalDate.parse(day).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
