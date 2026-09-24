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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.contrib.releasenotes.Audience;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.ChangeQuery;
import org.xwiki.contrib.releasenotes.ChangeSearchResult;
import org.xwiki.contrib.releasenotes.Importance;
import org.xwiki.contrib.releasenotes.ReleaseNotesAccessDeniedException;
import org.xwiki.contrib.releasenotes.ReleaseNotesConfiguration;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.ReleaseNotesNotFoundException;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.observation.ObservationManager;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.security.authorization.Right;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultChangeManager}.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
// Locating the release note, taking the page of a new entry, defaulting the product, checking the rights and
// saving are part of what creating a change is, so the components performing them are the real ones.
@ComponentList({ DefaultReleaseNoteManager.class, EntryPageAllocator.class, ProductResolver.class,
    ReleaseNotesDocumentWriter.class })
class DefaultChangeManagerTest
{
    private static final String PRODUCT = "XWiki";

    private static final String VERSION = "8.3-milestone-1";

    /** The page name of the release note the changes are added to, which the version carrying a dot is short for. */
    private static final String SHORT_VERSION = "8.3M1";

    private static final DocumentReference AUTHOR = new DocumentReference("xwiki", "XWiki", "Author");

    @InjectMockComponents
    private DefaultChangeManager manager;

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    // Creating the release note a change belongs to is not what these tests are about, but the manager doing it is
    // a real component here, and it titles a release note with a translation.
    @MockComponent
    private ContextualLocalizationManager localization;

    @MockComponent
    private ReleaseNotesConfiguration configuration;

    @MockComponent
    private QueryManager queryManager;

    /**
     * Looking for changes is a search of its own, and this is the component performing it.
     */
    @MockComponent
    private ChangeSearcher changeSearcher;


    @MockComponent
    private Query query;

    /**
     * Saving a page gives the listeners that watch what a user writes a chance to cancel it, which needs an
     * observation manager to notify.
     */
    @MockComponent
    private ObservationManager observationManager;

    @BeforeEach
    void setUp() throws Exception
    {
        ReleaseNotesXClasses.install(this.oldcore);
        installChangeTemplate();

        DocumentAccessBridge documentAccessBridge = this.oldcore.getMocker().getInstance(DocumentAccessBridge.class);
        when(documentAccessBridge.getCurrentAuthorReference()).thenReturn(AUTHOR);
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(any(Right.class), any()))
            .thenReturn(true);
        when(this.oldcore.getMockAuthorizationManager().hasAccess(any(Right.class), any(), any())).thenReturn(true);

        when(this.queryManager.createQuery(anyString(), anyString())).thenReturn(this.query);
        when(this.query.bindValue(anyString(), any())).thenReturn(this.query);
        when(this.query.execute()).thenReturn(List.of());
    }

    /**
     * The page of a new entry is taken before its author starts editing, so that a second author adding a change to
     * the same release note at the same time is handed another page rather than that same one.
     */
    @Test
    void theFirstEntryOfAReleaseNoteIsNumberedOne() throws Exception
    {
        assertEquals(entry("Entry001"), this.manager.reserveNextEntry(PRODUCT, VERSION));
        assertFalse(load(entry("Entry001")).isNew(), "Expected the page of the new entry to have been taken.");
    }

    /**
     * The page is left empty on purpose: the {@code edit} action its author is sent to applies the change template
     * to any page without content, so the objects of the change are created there, once.
     */
    @Test
    void aReservedEntryIsLeftEmpty() throws Exception
    {
        this.manager.reserveNextEntry(PRODUCT, VERSION);

        XWikiDocument reserved = load(entry("Entry001"));
        assertEquals("", reserved.getContent());
        assertNull(reserved.getXObject(ReleaseNotesReferences.ENTRY_CLASS));
    }

    /**
     * Every page of the release note is looked at, and not only the entries holding a change, so that a page taken
     * by an author who has not saved their change yet is seen as taken.
     */
    @Test
    void theEntryPagesOfTheReleaseNoteAreWhatIsLookedUp() throws Exception
    {
        this.manager.reserveNextEntry(PRODUCT, VERSION);

        verify(this.queryManager).createQuery("where doc.space like :space escape '!'", Query.XWQL);
        // MySQL uses "\" as an escape character, and that character is what separates the spaces of the reference,
        // so another escape character is asked for and the wildcards of the space names are escaped with it.
        verify(this.query).bindValue("space", "ReleaseNotes.Data.XWiki.8\\.3M1.%");
    }

    /**
     * The number of a new entry is one past the highest number in use, compared as a number: sorted as strings,
     * {@code Entry999} comes out above {@code Entry1000} and the number 1000 is handed out over and over.
     */
    @Test
    void entryNumbersAreComparedAsNumbersAndNotAsStrings() throws Exception
    {
        existingEntries("Entry999", "Entry1000");

        assertEquals(entry("Entry1001"), this.manager.reserveNextEntry(PRODUCT, VERSION));
    }

    /**
     * Only the pages named after an entry are counted, so that the contributors of a release note, which live in a
     * page of their own next to the entries, do not push the numbering along.
     */
    @Test
    void thePagesThatAreNoEntryAreNotCounted() throws Exception
    {
        existingEntries("Contributors");

        assertEquals(entry("Entry001"), this.manager.reserveNextEntry(PRODUCT, VERSION));
    }

    /**
     * Every page of the release note has just been looked at, so the page of the next number is free unless another
     * author took it in the meantime. A few numbers are therefore tried, and not just one.
     */
    @Test
    void aPageTakenSinceTheLookupIsSkipped() throws Exception
    {
        // Taken, but not by the lookup: this is the page another author was handed a moment ago.
        this.oldcore.getSpyXWiki().saveDocument(load(entry("Entry001")), this.oldcore.getXWikiContext());

        assertEquals(entry("Entry002"), this.manager.reserveNextEntry(PRODUCT, VERSION));
    }

    @Test
    void noFreePageIsReportedByAnAbsentPage() throws Exception
    {
        for (int number = 1; number <= 10; number++) {
            this.oldcore.getSpyXWiki().saveDocument(load(entry(String.format("Entry%03d", number))),
                this.oldcore.getXWikiContext());
        }

        assertNull(this.manager.reserveNextEntry(PRODUCT, VERSION));
    }

    /**
     * A change is created complete or not at all, so a release note whose page names are all taken is reported
     * rather than left with an entry holding no change.
     */
    @Test
    void aChangeIsNotCreatedWhenNoPageIsFree() throws Exception
    {
        for (int number = 1; number <= 10; number++) {
            this.oldcore.getSpyXWiki().saveDocument(load(entry(String.format("Entry%03d", number))),
                this.oldcore.getXWikiContext());
        }

        ReleaseNotesException exception =
            assertThrows(ReleaseNotesException.class, () -> this.manager.createChange(change()));

        assertEquals("No page was free for a new change of the version [8.3-milestone-1] of [XWiki].",
            exception.getMessage());
    }

    @Test
    void aFailureToLookUpTheEntriesIsReported() throws Exception
    {
        when(this.query.execute()).thenThrow(new QueryException("Down", null, null));

        ReleaseNotesException exception =
            assertThrows(ReleaseNotesException.class, () -> this.manager.reserveNextEntry(PRODUCT, VERSION));

        assertEquals("Failed to look up the entries of the release note "
            + "[ReleaseNotes.Data.XWiki.8\\.3M1].", exception.getMessage());
    }

    @Test
    void aChangeWithoutAVersionIsRefused()
    {
        Change change = change();
        change.setVersion(" ");

        ReleaseNotesException exception =
            assertThrows(ReleaseNotesException.class, () -> this.manager.createChange(change));

        assertEquals("A change needs the version it was made in.", exception.getMessage());
    }

    /**
     * A change with no title is displayed as an empty line by every displayer, which makes it look like the change
     * is missing rather than like its title is.
     */
    @Test
    void aChangeWithoutATitleIsRefused()
    {
        Change change = change();
        change.setTitle(null);

        ReleaseNotesException exception =
            assertThrows(ReleaseNotesException.class, () -> this.manager.createChange(change));

        assertEquals("A change needs a title.", exception.getMessage());
    }

    @Test
    void aChangeWithoutAProductAndWithoutAConfiguredOneIsRefused()
    {
        Change change = change();
        change.setProduct(null);

        ReleaseNotesException exception =
            assertThrows(ReleaseNotesException.class, () -> this.manager.createChange(change));

        assertEquals("No product was given, and this wiki has no default product configured.",
            exception.getMessage());
    }

    /**
     * A change is created complete and saved once, and is therefore never left half created: an entry carrying no
     * entry xobject is invisible to every query the application runs.
     */
    @Test
    void creatingAChangeSavesItsTwoObjectsAtOnce() throws Exception
    {
        Change change = change();
        change.setSummary("Starting a wiki now takes half the time.");
        change.setDescription("The long story.");
        change.setMigrationNotes("Delete the Solr cache before upgrading.");
        change.setAudience(Audience.DEVELOPER);
        change.setImportance(Importance.HIGH);
        change.setCategory("Performance");
        change.setScreenshots(List.of("before.png", "after.png"));

        assertEquals(entry("Entry001"), this.manager.createChange(change));

        Change created = this.manager.getChange(entry("Entry001"));
        assertEquals(PRODUCT, created.getProduct());
        assertEquals(VERSION, created.getVersion());
        assertEquals("Faster startup", created.getTitle());
        assertEquals("Starting a wiki now takes half the time.", created.getSummary());
        assertEquals("The long story.", created.getDescription());
        assertEquals("Delete the Solr cache before upgrading.", created.getMigrationNotes());
        assertEquals(Audience.DEVELOPER, created.getAudience());
        assertEquals(Importance.HIGH, created.getImportance());
        assertEquals("Performance", created.getCategory());
        assertEquals(List.of("before.png", "after.png"), created.getScreenshots());
    }

    /**
     * The entry xobject is what identifies and locates a change, and its type is what keeps the contributors of a
     * release note from being counted among its changes.
     */
    @Test
    void aCreatedChangeIsAnEntryOfTypeChange() throws Exception
    {
        this.manager.createChange(change());

        BaseObject entry = load(entry("Entry001")).getXObject(ReleaseNotesReferences.ENTRY_CLASS);
        assertEquals("Change", entry.getStringValue("type"));
        assertEquals(PRODUCT, entry.getStringValue("product"));
        assertEquals(VERSION, entry.getStringValue("version"));
    }

    /**
     * The objects of a change are created by the change template, so that a template an administrator has
     * customised is what a change is made of, whichever way that change was created. The values the change leaves
     * out therefore keep the default the template gives them.
     */
    @Test
    void aChangeIsFilledFromTheChangeTemplate() throws Exception
    {
        this.manager.createChange(change());

        Change created = this.manager.getChange(entry("Entry001"));
        assertEquals(Audience.USER, created.getAudience(), "Expected the audience the template defaults to.");
        assertNull(created.getImportance());
        assertEquals("", created.getMigrationNotes(), "Expected a change to need no migration by default.");
        assertTrue(created.getScreenshots().isEmpty());
        assertTrue(load(entry("Entry001")).isEnforceRequiredRights(),
            "Expected the change to enforce its required rights, as its template does.");
    }

    @Test
    void aUserWhoCannotEditThePageCreatesNoChange() throws Exception
    {
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(any(Right.class), any()))
            .thenReturn(false);

        assertThrows(ReleaseNotesException.class, () -> this.manager.createChange(change()));

        assertTrue(load(entry("Entry001")).isNew());
    }

    @Test
    void aScriptAuthorWhoCannotEditThePageCreatesNoChange() throws Exception
    {
        when(this.oldcore.getMockAuthorizationManager().hasAccess(any(Right.class), any(), any())).thenReturn(false);

        assertThrows(ReleaseNotesException.class, () -> this.manager.createChange(change()));

        assertTrue(load(entry("Entry001")).isNew());
    }

    /**
     * Replacing a change writes every property, and not only the ones the passed change carries: what the caller
     * left out is emptied rather than kept, and the template has no say in a replacement either.
     */
    @Test
    void replacingAChangeEmptiesThePropertiesItLeavesOut() throws Exception
    {
        Change change = change();
        change.setSummary("Starting a wiki now takes half the time.");
        change.setDescription("The long story.");
        change.setMigrationNotes("Delete the Solr cache before upgrading.");
        change.setCategory("Performance");
        change.setAudience(Audience.DEVELOPER);
        change.setImportance(Importance.HIGH);
        this.manager.createChange(change);

        Change replacement = new Change();
        replacement.setTitle("Even faster startup");

        Change stored = this.manager.updateChange(entry("Entry001"), replacement);

        assertEquals("Even faster startup", stored.getTitle());
        assertEquals("", stored.getSummary());
        assertEquals("", stored.getDescription());
        assertEquals("", stored.getMigrationNotes());
        assertEquals("", stored.getCategory());
        assertNull(stored.getAudience());
        assertNull(stored.getImportance());
        assertTrue(stored.getScreenshots().isEmpty());
        // What was replaced is what the wiki now holds, and not only what was answered.
        assertEquals("Even faster startup", this.manager.getChange(entry("Entry001")).getTitle());
    }

    /**
     * The media of a change name attachments of its own page, which cannot exist before that page does, so naming
     * them is what a replacement is needed for.
     */
    @Test
    void theMediaOfAChangeCanBeNamedAfterItWasCreated() throws Exception
    {
        this.manager.createChange(change());

        Change replacement = change();
        replacement.setScreenshots(List.of("before.png", "after.png"));

        assertEquals(List.of("before.png", "after.png"),
            this.manager.updateChange(entry("Entry001"), replacement).getScreenshots());
        assertEquals("before.png,after.png", load(entry("Entry001"))
            .getXObject(ReleaseNotesReferences.CHANGE_CLASS).getStringValue("screenshots"));
    }

    /**
     * The release note a change belongs to is the page tree it lives in, so a replacement leaves the entry xobject
     * saying what it said: moving a change to another release note is a move of its page.
     */
    @Test
    void replacingAChangeLeavesItAnEntryOfTheSameReleaseNote() throws Exception
    {
        this.manager.createChange(change());
        this.manager.updateChange(entry("Entry001"), change());

        BaseObject entry = load(entry("Entry001")).getXObject(ReleaseNotesReferences.ENTRY_CLASS);
        assertEquals("Change", entry.getStringValue("type"));
        assertEquals(PRODUCT, entry.getStringValue("product"));
        assertEquals(VERSION, entry.getStringValue("version"));
    }

    /**
     * An entry that holds no change is reported as such, and not filled with one: the contributors of a release
     * note are an entry too, and a page that was never created is one more thing a caller can name.
     */
    @Test
    void anEntryHoldingNoChangeIsNotReplaced()
    {
        ReleaseNotesNotFoundException exception = assertThrows(ReleaseNotesNotFoundException.class,
            () -> this.manager.updateChange(entry("Entry001"), change()));

        assertEquals(entry("Entry001"), exception.getReference());
        assertEquals("The page [xwiki:ReleaseNotes.Data.XWiki.8\\.3M1.Entry001.WebHome] holds no change.",
            exception.getMessage());
    }

    @Test
    void aChangeReplacedWithoutATitleIsRefused() throws Exception
    {
        this.manager.createChange(change());

        Change replacement = new Change();
        replacement.setTitle(" ");

        ReleaseNotesException exception = assertThrows(ReleaseNotesException.class,
            () -> this.manager.updateChange(entry("Entry001"), replacement));

        assertEquals("A change needs a title.", exception.getMessage());
        assertEquals("Faster startup", this.manager.getChange(entry("Entry001")).getTitle());
    }

    @Test
    void aUserWhoCannotEditThePageReplacesNoChange() throws Exception
    {
        this.manager.createChange(change());
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(any(Right.class), any()))
            .thenReturn(false);

        Change replacement = change();
        replacement.setTitle("Even faster startup");

        assertThrows(ReleaseNotesAccessDeniedException.class,
            () -> this.manager.updateChange(entry("Entry001"), replacement));
        assertEquals("Faster startup", this.manager.getChange(entry("Entry001")).getTitle());
    }

    @Test
    void aPageHoldingNoChangeIsReported()
    {
        ReleaseNotesException exception =
            assertThrows(ReleaseNotesException.class, () -> this.manager.getChange(entry("Entry001")));

        assertEquals("The page [xwiki:ReleaseNotes.Data.XWiki.8\\.3M1.Entry001.WebHome] holds no change.",
            exception.getMessage());
    }

    /**
     * Looking for changes is the one thing this hands over as it is: the search has its own component, and a rule
     * added here would be a rule a REST caller escapes.
     */
    @Test
    void aSearchIsHandedToTheSearcher() throws Exception
    {
        ChangeQuery query = new ChangeQuery();
        ChangeSearchResult result = new ChangeSearchResult(List.of(), List.of(), false);
        when(this.changeSearcher.search(query)).thenReturn(result);

        assertSame(result, this.manager.search(query));
    }

    /**
     * The media of a change are stored as one comma-separated value, with the spaces around the commas ignored, the
     * way the screenshot displayer reads them back.
     */
    @Test
    void theMediaOfAChangeAreReadBackAsAList() throws Exception
    {
        this.manager.createChange(change());
        XWikiDocument entryPage = load(entry("Entry001"));
        entryPage.getXObject(ReleaseNotesReferences.CHANGE_CLASS)
            .setStringValue("screenshots", "one.png ,  two.mp4 ,");
        this.oldcore.getSpyXWiki().saveDocument(entryPage, this.oldcore.getXWikiContext());

        assertEquals(List.of("one.png", "two.mp4"), this.manager.getChange(entry("Entry001")).getScreenshots());
    }

    private Change change()
    {
        Change change = new Change();
        change.setProduct(PRODUCT);
        change.setVersion(VERSION);
        change.setTitle("Faster startup");

        return change;
    }

    private DocumentReference entry(String name)
    {
        return new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", PRODUCT, SHORT_VERSION, name), "WebHome");
    }

    private XWikiDocument load(DocumentReference reference) throws Exception
    {
        return this.oldcore.getSpyXWiki().getDocument(reference, this.oldcore.getXWikiContext());
    }

    /**
     * Creates the passed entry pages and makes the query looking for the pages of the release note give them back,
     * as it does once they are saved.
     */
    private void existingEntries(String... names) throws Exception
    {
        EntityReferenceSerializer<String> serializer =
            this.oldcore.getMocker().getInstance(EntityReferenceSerializer.TYPE_STRING);
        List<Object> pages = new ArrayList<>();

        for (String name : names) {
            XWikiDocument entryPage = load(entry(name));
            this.oldcore.getSpyXWiki().saveDocument(entryPage, this.oldcore.getXWikiContext());
            pages.add(serializer.serialize(entryPage.getDocumentReference()));
        }

        when(this.query.execute()).thenReturn(pages);
    }

    private void installChangeTemplate() throws Exception
    {
        DocumentReference reference =
            ReleaseNotesXClasses.reference(this.oldcore, ReleaseNotesReferences.CHANGE_TEMPLATE);
        XWikiDocument template = load(reference);
        template.setEnforceRequiredRights(true);
        BaseObject entry = template.newXObject(ReleaseNotesReferences.ENTRY_CLASS, this.oldcore.getXWikiContext());
        entry.setStringValue("type", "Change");
        BaseObject change = template.newXObject(ReleaseNotesReferences.CHANGE_CLASS, this.oldcore.getXWikiContext());
        change.setStringValue("audience", "user");
        this.oldcore.getSpyXWiki().saveDocument(template, this.oldcore.getXWikiContext());
    }
}
