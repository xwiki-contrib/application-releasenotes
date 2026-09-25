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

import java.util.List;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.releasenotes.internal.migration.MigrationLeftover;
import org.xwiki.contrib.releasenotes.internal.migration.MigrationNoteSource;
import org.xwiki.contrib.releasenotes.internal.migration.MigrationNotesImportReport;
import org.xwiki.contrib.releasenotes.internal.migration.MigrationNotesImportScriptService;
import org.xwiki.contrib.releasenotes.internal.migration.ReleaseNoteImport;
import org.xwiki.localization.macro.internal.TranslationMacro;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.script.ModelScriptService;
import org.xwiki.rendering.internal.macro.message.ErrorMessageMacro;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Page test for {@code ReleaseNotes.Code.MigrationNotesImport}, which previews and runs the import of the backward
 * compatibility and migration notes written in the content of the release notes.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
// The page displays its strings with the translation macro, tells a user who may not run the import so with the error
// macro, and links the release notes it reports on through $services.model.
@ComponentList({
    TranslationMacro.class,
    ErrorMessageMacro.class,
    ModelScriptService.class
})
class MigrationNotesImportPageTest extends PageTest
{
    private static final DocumentReference IMPORT =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "MigrationNotesImport");

    private static final DocumentReference RELEASE_NOTE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "XWiki", "147"), "WebHome");

    private static final String VALID_TOKEN = "valid-token";

    private static final String PRODUCT = "XWiki";

    private MigrationNotesImportScriptService importService;

    @BeforeEach
    void setUp() throws Exception
    {
        this.importService = mock(MigrationNotesImportScriptService.class);
        this.componentManager.registerComponent(ScriptService.class, "releasenotesMigrationImport",
            this.importService);
        this.componentManager.registerComponent(ScriptService.class, "csrf",
            new CSRFTokenScriptServiceStub(VALID_TOKEN));
        // The page escapes the data of the release notes it reports on. The stand-in escapes the way the platform
        // does, so that what the parser gets back is what these tests assert on.
        this.componentManager.registerComponent(ScriptService.class, "rendering",
            new RenderingScriptServiceStub(RenderingScriptServiceStub.xwikiSyntaxEscaper()));
        this.request.put("product", PRODUCT);

        MigrationNotesImportReport report = report();
        when(this.importService.preview(PRODUCT)).thenReturn(report);
        when(this.importService.importNotes(PRODUCT)).thenReturn(report);
    }

    /**
     * The import rewrites release notes across the whole wiki and cannot be undone: a user who does not administer
     * the wiki is told so, and is offered neither the preview nor the import.
     */
    @Test
    void aUserWhoDoesNotAdministerTheWikiIsRefusedTheImport() throws Exception
    {
        registerSecurity(false);
        this.request.put("action", "preview");

        Document html = renderHTMLPage(IMPORT);

        assertFalse(html.select(".errormessage").isEmpty(), html.html());
        assertTrue(html.select("form").isEmpty(), html.html());
        verify(this.importService, never()).preview(any());
    }

    /**
     * A preview reports, for each release note, the notes it would move, the ones it would leave out as duplicates,
     * and what it would leave in place, and writes nothing.
     */
    @Test
    void aPreviewReportsWhatTheImportWouldDo() throws Exception
    {
        registerSecurity(true);
        this.request.put("action", "preview");

        Document html = renderHTMLPage(IMPORT);

        verify(this.importService, never()).importNotes(any());
        assertEquals(List.of("14.7"), html.select("h2").eachText());
        assertEquals(List.of("A note", "A repeated note",
            "releasenotes.migrationImport.leftover.UNTITLED_TEXT: Some text"), html.select("li").eachText());
        assertTrue(html.text().contains("releasenotes.migrationImport.summary [1, 1, 2]"), html.text());
    }

    /**
     * The import only runs on a request carrying a valid form token, which is what the form of the page sends: any
     * other request only previews it.
     */
    @Test
    void theImportOnlyRunsWithAValidFormToken() throws Exception
    {
        registerSecurity(true);
        this.request.put("action", "import");
        this.request.put("form_token", "forged");

        renderHTMLPage(IMPORT);

        verify(this.importService, never()).importNotes(any());
        verify(this.importService).preview(PRODUCT);
    }

    @Test
    void theImportRunsFromTheForm() throws Exception
    {
        registerSecurity(true);
        this.request.put("action", "import");
        this.request.put("form_token", VALID_TOKEN);

        Document html = renderHTMLPage(IMPORT);

        verify(this.importService).importNotes(PRODUCT);
        assertTrue(html.text().contains("releasenotes.migrationImport.done [0, 0]"), html.text());
    }

    /**
     * The titles come from the content of the release notes, so a title carrying wiki syntax is displayed as text
     * rather than rendered.
     */
    @Test
    void theTitlesOfTheNotesAreEscaped() throws Exception
    {
        registerSecurity(true);
        this.request.put("action", "preview");
        ReleaseNoteImport item = item();
        when(item.getNotes()).thenReturn(List.of(new MigrationNoteSource("{{html}}<b>injected</b>{{/html}}", "")));
        when(this.importService.preview(PRODUCT))
            .thenReturn(new MigrationNotesImportReport(PRODUCT, List.of(item), 0));

        Document html = renderHTMLPage(IMPORT);

        assertTrue(html.select("b").isEmpty(), html.html());
        assertTrue(html.text().contains("{{html}}"), html.text());
    }

    private MigrationNotesImportReport report()
    {
        ReleaseNoteImport item = item();
        when(item.getNotes()).thenReturn(List.of(new MigrationNoteSource("A note", "Do this.")));
        when(item.getDuplicates()).thenReturn(List.of("A repeated note"));
        when(item.getLeftovers())
            .thenReturn(List.of(new MigrationLeftover(MigrationLeftover.Kind.UNTITLED_TEXT, "Some text")));
        when(item.isToRewrite()).thenReturn(true);

        return new MigrationNotesImportReport(PRODUCT, List.of(item), 2);
    }

    private ReleaseNoteImport item()
    {
        ReleaseNoteImport item = mock(ReleaseNoteImport.class);
        when(item.getReference()).thenReturn(RELEASE_NOTE);
        when(item.getVersion()).thenReturn("14.7");

        return item;
    }

    private void registerSecurity(boolean allowed) throws Exception
    {
        this.componentManager.registerComponent(ScriptService.class, "security",
            new SecurityScriptServiceStub(allowed));
    }
}
