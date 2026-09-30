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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.xwiki.contrib.releasenotes.ReleaseNote;
import org.xwiki.contrib.releasenotes.ReleaseNoteAlreadyExistsException;
import org.xwiki.contrib.releasenotes.ReleaseNotesConfiguration;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.ReleaseNotesNotFoundException;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.observation.ObservationManager;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultReleaseNoteManager}.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
// The product defaulting, the right checks and the save are part of what creating a release note is, so the
// components performing them are the real ones.
@ComponentList({ ProductResolver.class, ReleaseNotesDocumentStore.class })
class DefaultReleaseNoteManagerTest
{
    private static final String PRODUCT = "XWiki";

    private static final DocumentReference TEMPLATE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "ReleaseNoteTemplate");

    private static final String TEMPLATE_TITLE = "Release Note Template";

    private static final String TITLE_KEY = "releasenotes.releasenote.title";

    private static final String TEMPLATE_CONTENT = "{{releasenotechanges/}}";

    @InjectMockComponents
    private DefaultReleaseNoteManager manager;

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @MockComponent
    private ReleaseNotesConfiguration configuration;

    @MockComponent
    private QueryManager queryManager;

    /**
     * Saving a page gives the listeners that watch what a user writes a chance to cancel it, which needs an
     * observation manager to notify.
     */
    @MockComponent
    private ObservationManager observationManager;

    @MockComponent
    private ContextualLocalizationManager localization;

    @MockComponent
    private Query query;

    @BeforeEach
    void setUp() throws Exception
    {
        ReleaseNotesXClasses.install(this.oldcore);


        when(this.localization.getTranslationPlain(eq(TITLE_KEY), any(), any()))
            .thenAnswer(invocation -> String.format("Release Notes for %s %s", invocation.getArgument(1),
                invocation.getArgument(2)));

        when(this.queryManager.createQuery(anyString(), anyString())).thenReturn(this.query);
        when(this.query.bindValue(anyString(), any())).thenReturn(this.query);
        when(this.query.execute()).thenReturn(List.of());
    }

    /**
     * The page of a release note is named after the version with its separators taken out, and that name is what
     * the changes of a milestone or of a release candidate are aggregated from, so it is derived and never chosen.
     */
    @ParameterizedTest
    @CsvSource({
        "8.3-milestone-1, 8.3M1",
        "8.3-rc-1, 8.3RC1",
        "8.3, 8.3",
        "18.0-MILESTONE-2, 18.0M2",
        "8.3.1, 8.3.1"
    })
    void theVersionNamesThePageOfItsReleaseNote(String version, String expectedName)
    {
        assertEquals(new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", PRODUCT, expectedName),
            "WebHome"), this.manager.getReleaseNoteReference(PRODUCT, version));
    }

    @Test
    void aReleaseNoteIsNotLocatedWithoutAProductAndAVersion()
    {
        assertThrows(IllegalArgumentException.class, () -> this.manager.getReleaseNoteReference(PRODUCT, " "));
        assertThrows(IllegalArgumentException.class, () -> this.manager.getReleaseNoteReference("", "8.3"));
    }

    @Test
    void aReleaseNoteWithoutAVersionIsRefused()
    {
        ReleaseNotesException exception =
            assertThrows(ReleaseNotesException.class, () -> this.manager.createReleaseNote(note(PRODUCT, " ")));

        assertEquals("A release note needs the version it is about.", exception.getMessage());
    }

    /**
     * A release note that names no product uses the product the administrator has configured for the wiki, which is
     * what lets a wiki that is about one product only leave it out everywhere.
     */
    @Test
    void aReleaseNoteWithoutAProductUsesTheConfiguredProduct() throws Exception
    {
        when(this.configuration.getDefaultProduct()).thenReturn(PRODUCT);

        assertEquals(reference("8.3"), this.manager.createReleaseNote(note(null, "8.3")));
        assertEquals(PRODUCT, this.manager.getReleaseNote(reference("8.3")).getProduct());
    }

    /**
     * The page of a release note is named after its product, so a release note that names none and lands on a wiki
     * that configures none is refused rather than created under a nameless product.
     */
    @Test
    void aReleaseNoteWithoutAProductAndWithoutAConfiguredOneIsRefused()
    {
        ReleaseNotesException exception =
            assertThrows(ReleaseNotesException.class, () -> this.manager.createReleaseNote(note(null, "8.3")));

        assertEquals("No product was given, and this wiki has no default product configured.",
            exception.getMessage());
    }

    /**
     * A version that already has a release note is refused, and the release note that has it is named: returning it
     * instead would let a caller add its changes to a release note written for another product.
     */
    @Test
    void aVersionThatAlreadyHasAReleaseNoteIsRefused() throws Exception
    {
        this.manager.createReleaseNote(note(PRODUCT, "8.3"));

        ReleaseNoteAlreadyExistsException exception = assertThrows(ReleaseNoteAlreadyExistsException.class,
            () -> this.manager.createReleaseNote(note(PRODUCT, "8.3")));

        assertEquals(reference("8.3"), exception.getReleaseNoteReference());
    }

    /**
     * A release note is created with an empty release date, and not with no release date at all: the Live Data
     * listing the release notes sorts them on their date and leaves out the ones that have no value for it.
     */
    @Test
    void aReleaseNoteIsCreatedWithAnEmptyReleaseDate() throws Exception
    {
        this.manager.createReleaseNote(note(PRODUCT, "8.3"));

        BaseObject object = load("8.3").getXObject(ReleaseNotesReferences.RELEASE_NOTE_CLASS);
        assertNotNull(object.getField("date"), "The release date must be stored, so that the Live Data sees it.");
        assertNull(object.getDateValue("date"));
        assertNull(this.manager.getReleaseNote(reference("8.3")).getDate());
    }

    @Test
    void theReleaseDateAndTheReleasedFlagAreStoredWhenTheyAreGiven() throws Exception
    {
        Date date = new SimpleDateFormat("dd/MM/yyyy").parse("15/09/2016");
        ReleaseNote note = note(PRODUCT, "8.3");
        note.setDate(date);
        note.setReleased(true);

        this.manager.createReleaseNote(note);

        ReleaseNote created = this.manager.getReleaseNote(reference("8.3"));
        assertEquals(date, created.getDate());
        assertTrue(created.isReleased());
    }

    /**
     * The content of the template is copied raw, and the rights that content needs are copied along with it: content
     * copied without them would not execute.
     */
    @Test
    void creatingAReleaseNoteCopiesTheContentAndTheRequiredRightsOfItsTemplate() throws Exception
    {
        installTemplate();
        when(this.configuration.getDefaultTemplate()).thenReturn(TEMPLATE);

        this.manager.createReleaseNote(note(PRODUCT, "8.3-milestone-1"));

        XWikiDocument created = load("8.3M1");
        assertEquals(TEMPLATE_CONTENT, created.getContent());
        assertTrue(created.isEnforceRequiredRights());
        assertEquals("script", created.getXObject(ReleaseNotesReferences.REQUIRED_RIGHT_CLASS)
            .getStringValue("level"));
    }

    /**
     * The title names the product and the version, and is written out at creation rather than taken from the
     * template: a title taken from a template that writes it in Velocity would only display on a release note whose
     * author holds the script right.
     */
    @Test
    void aReleaseNoteIsTitledAfterItsProductAndItsVersion() throws Exception
    {
        installTemplate();
        when(this.configuration.getDefaultTemplate()).thenReturn(TEMPLATE);

        this.manager.createReleaseNote(note(PRODUCT, "8.3-milestone-1"));

        assertEquals("Release Notes for XWiki 8.3-milestone-1", load("8.3M1").getTitle());
    }

    /**
     * A wiki that has the jar of the application but not the pages holding its translations still gets a titled
     * release note.
     */
    @Test
    void aReleaseNoteIsTitledEvenWithoutTheTranslationOfItsTitle() throws Exception
    {
        when(this.localization.getTranslationPlain(eq(TITLE_KEY), any(), any())).thenReturn(null);

        this.manager.createReleaseNote(note(PRODUCT, "8.3"));

        assertEquals("Release Notes for XWiki 8.3", load("8.3").getTitle());
    }

    /**
     * A release note names the template it wants, and only falls back on the one configured for the wiki.
     */
    @Test
    void aReleaseNoteMayNameItsOwnTemplate() throws Exception
    {
        installTemplate();
        ReleaseNote note = note(PRODUCT, "8.3");
        note.setTemplate(TEMPLATE);

        this.manager.createReleaseNote(note);

        assertEquals(TEMPLATE_CONTENT, load("8.3").getContent());
    }

    /**
     * A template that is not there is reported, rather than silently creating an empty release note: an
     * administrator who has misspelled the configured template has to hear about it.
     */
    @Test
    void aTemplateThatDoesNotExistIsReported() throws Exception
    {
        when(this.configuration.getDefaultTemplate()).thenReturn(TEMPLATE);

        ReleaseNotesException exception =
            assertThrows(ReleaseNotesException.class, () -> this.manager.createReleaseNote(note(PRODUCT, "8.3")));

        assertEquals("The release note template [xwiki:ReleaseNotes.Code.ReleaseNoteTemplate] does not exist.",
            exception.getMessage());
        assertTrue(load("8.3").isNew(), "A release note whose template is missing must not have been created.");
    }

    /**
     * Marking a version released on the day it ships is what replacing a release note is for: neither value could
     * be written once its page existed.
     */
    @Test
    void aReleaseNoteIsMarkedReleasedOnTheDayItShips() throws Exception
    {
        Date date = new SimpleDateFormat("dd/MM/yyyy").parse("15/09/2016");
        this.manager.createReleaseNote(note(PRODUCT, "8.3"));

        ReleaseNote replacement = note(PRODUCT, "8.3");
        replacement.setDate(date);
        replacement.setReleased(true);

        ReleaseNote stored = this.manager.updateReleaseNote(replacement);

        assertEquals(date, stored.getDate());
        assertTrue(stored.isReleased());
        // What was replaced is what the wiki now holds, and not only what was answered.
        assertEquals(date, this.manager.getReleaseNote(reference("8.3")).getDate());
        assertTrue(this.manager.getReleaseNote(reference("8.3")).isReleased());
    }

    /**
     * The properties a replacement leaves out are emptied rather than kept, and the date is emptied the way it is
     * when a release note is created: with a value, so that the Live Data listing the release notes still sees it.
     */
    @Test
    void replacingAReleaseNoteEmptiesTheDateItLeavesOut() throws Exception
    {
        ReleaseNote note = note(PRODUCT, "8.3");
        note.setDate(new SimpleDateFormat("dd/MM/yyyy").parse("15/09/2016"));
        note.setReleased(true);
        this.manager.createReleaseNote(note);

        ReleaseNote stored = this.manager.updateReleaseNote(note(PRODUCT, "8.3"));

        assertNull(stored.getDate());
        assertFalse(stored.isReleased());
        assertNotNull(load("8.3").getXObject(ReleaseNotesReferences.RELEASE_NOTE_CLASS).getField("date"),
            "The release date must be stored, so that the Live Data sees it.");
    }

    /**
     * The content, the title and the template are what a release note is created with, and an administrator is free
     * to have edited that content since: a replacement is about the two properties that change over its life.
     */
    @Test
    void replacingAReleaseNoteLeavesItsContentAndItsTitleAlone() throws Exception
    {
        installTemplate();
        when(this.configuration.getDefaultTemplate()).thenReturn(TEMPLATE);
        this.manager.createReleaseNote(note(PRODUCT, "8.3"));

        ReleaseNote replacement = note(PRODUCT, "8.3");
        replacement.setReleased(true);
        this.manager.updateReleaseNote(replacement);

        assertEquals(TEMPLATE_CONTENT, load("8.3").getContent());
        assertEquals("Release Notes for XWiki 8.3", load("8.3").getTitle());
    }

    @Test
    void aVersionThatHasNoReleaseNoteIsNotReplaced()
    {
        ReleaseNotesNotFoundException exception = assertThrows(ReleaseNotesNotFoundException.class,
            () -> this.manager.updateReleaseNote(note(PRODUCT, "8.3")));

        assertEquals(reference("8.3"), exception.getReference());
        assertEquals("The page [xwiki:ReleaseNotes.Data.XWiki.8\\.3.WebHome] holds no release note.",
            exception.getMessage());
    }

    @Test
    void aReleaseNoteReplacedWithoutAVersionIsRefused()
    {
        ReleaseNotesException exception = assertThrows(ReleaseNotesException.class,
            () -> this.manager.updateReleaseNote(note(PRODUCT, " ")));

        assertEquals("A release note needs the version it is about.", exception.getMessage());
    }

    @Test
    void aPageHoldingNoReleaseNoteIsReported()
    {
        ReleaseNotesException exception =
            assertThrows(ReleaseNotesException.class, () -> this.manager.getReleaseNote(reference("8.3")));

        assertEquals("The page [xwiki:ReleaseNotes.Data.XWiki.8\\.3.WebHome] holds no release note.",
            exception.getMessage());
    }

    @Test
    void theReleaseNotesOfOneProductAreLookedUpByThatProduct() throws Exception
    {
        this.manager.createReleaseNote(note(PRODUCT, "8.3"));
        // The page names a query gives back are serialized references, in which the dot of a space name is escaped.
        when(this.query.execute()).thenReturn(List.of("ReleaseNotes.Data.XWiki.8\\.3.WebHome"));

        List<ReleaseNote> notes = this.manager.getReleaseNotes(PRODUCT);

        assertEquals(1, notes.size());
        assertEquals("8.3", notes.get(0).getVersion());
        verify(this.queryManager).createQuery(
            "from doc.object(ReleaseNotes.Code.ReleaseNoteClass) as note where note.product = :product order by "
                + "doc.fullName", Query.XWQL);
        verify(this.query).bindValue("product", PRODUCT);
    }

    /**
     * The filter is handed the page each release note lives in, and the release notes it refuses are left out.
     */
    @Test
    void theReleaseNotesTheFilterRefusesAreNotListed() throws Exception
    {
        this.manager.createReleaseNote(note(PRODUCT, "8.3"));
        this.manager.createReleaseNote(note(PRODUCT, "8.4"));
        when(this.query.execute())
            .thenReturn(List.of("ReleaseNotes.Data.XWiki.8\\.3.WebHome", "ReleaseNotes.Data.XWiki.8\\.4.WebHome"));

        List<ReleaseNote> notes =
            this.manager.getReleaseNotes(PRODUCT, reference -> !reference.equals(reference("8.3")));

        assertEquals(1, notes.size());
        assertEquals("8.4", notes.get(0).getVersion());
    }

    @Test
    void theReleaseNotesOfEveryProductAreLookedUpWithoutAProductFilter() throws Exception
    {
        assertTrue(this.manager.getReleaseNotes(null).isEmpty());

        verify(this.queryManager).createQuery(
            "from doc.object(ReleaseNotes.Code.ReleaseNoteClass) as note order by doc.fullName", Query.XWQL);
        verify(this.query, never()).bindValue(anyString(), any());
    }

    @Test
    void aFailureToLookUpTheReleaseNotesIsReported() throws Exception
    {
        when(this.query.execute()).thenThrow(new QueryException("Down", null, null));

        ReleaseNotesException exception =
            assertThrows(ReleaseNotesException.class, () -> this.manager.getReleaseNotes(PRODUCT));

        assertEquals("Failed to look up the release notes of this wiki.", exception.getMessage());
    }

    /**
     * A final release note also displays the changes of its milestones and of its release candidates, which it
     * matches by pattern since their numbers are not known; a milestone or a release candidate displays only its
     * own.
     */
    @ParameterizedTest
    @CsvSource({
        "8.3M1, 8.3-milestone-1",
        "8.3RC1, 8.3-rc-1",
        "8.3, '8.3|8.3-milestone%|8.3-rc%'"
    })
    void theVersionsAReleaseNoteDisplaysAreDerivedFromItsPageName(String pageName, String expectedVersions)
    {
        assertEquals(List.of(expectedVersions.split("\\|")),
            this.manager.getAggregatedVersions(reference(pageName)));
    }

    private ReleaseNote note(String product, String version)
    {
        ReleaseNote note = new ReleaseNote();
        note.setProduct(product);
        note.setVersion(version);

        return note;
    }

    private DocumentReference reference(String pageName)
    {
        return new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", PRODUCT, pageName), "WebHome");
    }

    private XWikiDocument load(String pageName) throws Exception
    {
        return this.oldcore.getSpyXWiki().getDocument(reference(pageName), this.oldcore.getXWikiContext());
    }

    private void installTemplate() throws Exception
    {
        XWikiDocument template =
            this.oldcore.getSpyXWiki().getDocument(TEMPLATE, this.oldcore.getXWikiContext());
        template.setTitle(TEMPLATE_TITLE);
        template.setContent(TEMPLATE_CONTENT);
        template.setEnforceRequiredRights(true);
        template.newXObject(ReleaseNotesReferences.REQUIRED_RIGHT_CLASS, this.oldcore.getXWikiContext())
            .setStringValue("level", "script");
        this.oldcore.getSpyXWiki().saveDocument(template, this.oldcore.getXWikiContext());
    }
}
