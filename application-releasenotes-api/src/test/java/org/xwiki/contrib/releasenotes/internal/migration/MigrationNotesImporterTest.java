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
package org.xwiki.contrib.releasenotes.internal.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Named;
import jakarta.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.ChangeManager;
import org.xwiki.contrib.releasenotes.ChangeType;
import org.xwiki.contrib.releasenotes.ReleaseNote;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesAccessDeniedException;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.internal.ProductResolver;
import org.xwiki.contrib.releasenotes.internal.ReleaseNotesDocumentWriter;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MigrationNotesImporter}. The content of the release notes is read by a mocked parser, whose
 * own tests cover how that content is read: these tests are about which notes become entries, and in which order the
 * import writes them.
 *
 * @version $Id$
 */
@ComponentTest
class MigrationNotesImporterTest
{
    private static final String PRODUCT = "XWiki";

    @InjectMockComponents
    private MigrationNotesImporter importer;

    @MockComponent
    private ReleaseNoteManager releaseNoteManager;

    @MockComponent
    private ChangeManager changeManager;

    @MockComponent
    private ProductResolver productResolver;

    @MockComponent
    private MigrationSectionParser sectionParser;

    @MockComponent
    private ReleaseNotesDocumentWriter documentWriter;

    @MockComponent
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @Mock
    private XWikiContext xcontext;

    @Mock
    private XWiki wiki;

    private final List<ReleaseNote> releaseNotes = new ArrayList<>();

    private int entryCount;

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getWiki()).thenReturn(this.wiki);
        when(this.productResolver.resolve(any())).thenReturn(PRODUCT);
        when(this.releaseNoteManager.getReleaseNotes(PRODUCT)).thenReturn(this.releaseNotes);
        when(this.releaseNoteManager.getReleaseNoteReference(eq(PRODUCT), anyString()))
            .thenAnswer(invocation -> reference(invocation.getArgument(1)));
        when(this.localSerializer.serialize(any())).thenAnswer(invocation -> invocation.getArgument(0).toString());
        when(this.changeManager.createChange(any())).thenAnswer(invocation -> reference("Entry" + ++this.entryCount));
        when(this.sectionParser.rewrite(anyString(), any())).thenAnswer(invocation -> "rewritten");
    }

    /**
     * A final release note displays the entries of its release candidates, so a note it repeats from one of them is
     * left out rather than migrated twice, and the earliest release note keeps it. The same note in another version
     * is about another upgrade, and is kept there too.
     */
    @Test
    void aNoteRepeatedFromAReleaseCandidateIsMigratedOnce() throws Exception
    {
        releaseNote("14.7", section("Removal of Formula Macro", "Only in the final"));
        releaseNote("14.7-rc-1", section("Removal of Formula Macro"));
        releaseNote("14.6.1", section("Removal of Formula Macro"));

        MigrationNotesImportReport report = this.importer.preview(null);

        assertEquals(List.of("14.6.1", "14.7-rc-1", "14.7"), versions(report));
        assertEquals(List.of("Removal of Formula Macro"), titles(report.getReleaseNotes().get(0)));
        assertEquals(List.of("Removal of Formula Macro"), titles(report.getReleaseNotes().get(1)));
        assertEquals(List.of("Only in the final"), titles(report.getReleaseNotes().get(2)));
        assertEquals(List.of("Removal of Formula Macro"), report.getReleaseNotes().get(2).getDuplicates());
        assertEquals(3, report.getNoteCount());
    }

    /**
     * A preview tells what the import would do, and writes nothing.
     */
    @Test
    void aPreviewWritesNothing() throws Exception
    {
        releaseNote("14.7", section("A note"));

        MigrationNotesImportReport report = this.importer.preview(null);

        assertEquals(1, report.getRewriteCount());
        verify(this.changeManager, never()).createChange(any());
        verify(this.documentWriter, never()).save(any(), anyString());
    }

    /**
     * The notes become migration note entries of their release note, created in the order they are written in, and
     * the release note is rewritten once they all exist, with a comment telling why in its history.
     */
    @Test
    void theNotesBecomeEntriesBeforeTheReleaseNoteIsRewritten() throws Exception
    {
        XWikiDocument document = releaseNote("14.7", section("First", "Second"));

        MigrationNotesImportReport report = this.importer.importNotes(null);

        ArgumentCaptor<Change> changes = ArgumentCaptor.forClass(Change.class);
        InOrder order = inOrder(this.documentWriter, this.changeManager);
        order.verify(this.documentWriter).checkEditRight(reference("14.7"));
        order.verify(this.changeManager, times(2)).createChange(changes.capture());
        order.verify(this.documentWriter).save(document, MigrationNotesImporter.SAVE_COMMENT);

        assertEquals(List.of("First", "Second"),
            changes.getAllValues().stream().map(Change::getTitle).collect(Collectors.toList()));
        Change first = changes.getAllValues().get(0);
        assertEquals(PRODUCT, first.getProduct());
        assertEquals("14.7", first.getVersion());
        assertEquals(ChangeType.MIGRATION, first.getType());
        assertEquals("First description", first.getDescription());
        verify(document).setContent("rewritten");
        ReleaseNoteImport item = report.getReleaseNotes().get(0);
        assertTrue(item.isRewritten());
        assertEquals(List.of(reference("Entry1"), reference("Entry2")), item.getCreatedEntries());
    }

    /**
     * A release note whose notes are all repeated from its release candidates is still rewritten, so that its
     * content stops repeating them: its migration notes section displays the entries of its release candidates.
     */
    @Test
    void aReleaseNoteHoldingOnlyRepeatedNotesIsRewritten() throws Exception
    {
        releaseNote("14.7-rc-1", section("A note"));
        XWikiDocument finalDocument = releaseNote("14.7", section("A note"));

        this.importer.importNotes(null);

        verify(this.documentWriter).save(finalDocument, MigrationNotesImporter.SAVE_COMMENT);
    }

    /**
     * A release note the user may not rewrite is reported as failed, and gets no entry its content would not display;
     * the other release notes are still migrated.
     */
    @Test
    void aReleaseNoteThatCannotBeRewrittenDoesNotStopTheOthers() throws Exception
    {
        releaseNote("14.6", section("Refused"));
        XWikiDocument allowed = releaseNote("14.7", section("Allowed"));
        doThrow(new ReleaseNotesAccessDeniedException("Denied", reference("14.6"))).when(this.documentWriter)
            .checkEditRight(reference("14.6"));

        MigrationNotesImportReport report = this.importer.importNotes(null);

        assertEquals("Denied", report.getReleaseNotes().get(0).getError());
        assertFalse(report.getReleaseNotes().get(0).isRewritten());
        verify(this.changeManager, never()).createChange(argThat(change -> "Refused".equals(change.getTitle())));
        verify(this.documentWriter).save(allowed, MigrationNotesImporter.SAVE_COMMENT);
    }

    /**
     * A release note already migrated, or with nothing to migrate nor to report, is left out of the report, which
     * only counts the ones already migrated.
     */
    @Test
    void onlyTheReleaseNotesWithSomethingToSayAreReported() throws Exception
    {
        MigrationSection migrated = new MigrationSection();
        migrated.setAlreadyMigrated(true);
        releaseNote("14.5", migrated);
        releaseNote("14.6", new MigrationSection());
        releaseNote("14.6.1", null);
        MigrationSection leftover = new MigrationSection();
        leftover.getLeftovers().add(new MigrationLeftover(MigrationLeftover.Kind.UNTITLED_TEXT, "Some text"));
        releaseNote("14.7", leftover);

        MigrationNotesImportReport report = this.importer.preview(null);

        assertEquals(List.of("14.7"), versions(report));
        assertEquals(1, report.getAlreadyMigrated());
        assertEquals(0, report.getRewriteCount());
    }

    /**
     * A release note whose content cannot be read is reported as failed rather than silently skipped.
     */
    @Test
    void aReleaseNoteThatCannotBeReadIsReported() throws Exception
    {
        releaseNote("14.7", null);
        when(this.sectionParser.parse(anyString(), anyString()))
            .thenThrow(new ReleaseNotesException("Failed to locate the heading [x] in the source."));

        MigrationNotesImportReport report = this.importer.preview(null);

        assertEquals("Failed to locate the heading [x] in the source.", report.getReleaseNotes().get(0).getError());
    }

    private XWikiDocument releaseNote(String version, MigrationSection section) throws Exception
    {
        ReleaseNote note = new ReleaseNote();
        note.setProduct(PRODUCT);
        note.setVersion(version);
        this.releaseNotes.add(note);

        XWikiDocument document = mock(XWikiDocument.class);
        String content = "content of " + version;
        when(document.getContent()).thenReturn(content);
        when(document.clone()).thenReturn(document);
        when(this.wiki.getDocument(reference(version), this.xcontext)).thenReturn(document);
        when(this.sectionParser.parse(content, reference(version).toString())).thenReturn(section);

        return document;
    }

    private static MigrationSection section(String... titles)
    {
        MigrationSection section = new MigrationSection();

        for (String title : titles) {
            section.getNotes().add(new MigrationNoteSource(title, title + " description"));
        }

        return section;
    }

    private static DocumentReference reference(String name)
    {
        return new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", PRODUCT, name), "WebHome");
    }

    private static List<String> versions(MigrationNotesImportReport report)
    {
        return report.getReleaseNotes().stream().map(ReleaseNoteImport::getVersion).collect(Collectors.toList());
    }

    private static List<String> titles(ReleaseNoteImport item)
    {
        return item.getNotes().stream().map(MigrationNoteSource::getTitle).collect(Collectors.toList());
    }
}
