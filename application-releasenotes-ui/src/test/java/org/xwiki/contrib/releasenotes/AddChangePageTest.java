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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.localization.macro.internal.TranslationMacro;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.query.Query;
import org.xwiki.query.QueryManager;
import org.xwiki.rendering.internal.macro.message.ErrorMessageMacro;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.web.XWikiServletResponseStub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Page test for the {@code handleAddAction} macro of {@code ReleaseNotes.Code.EntryVelocityMacros}, which hands the
 * author of a new change the page that change is going to live in.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
// The macro displays its error messages with the error and translation macros, and takes the page of a new entry
// through the application's Java API.
@ReleaseNotesApiComponentList
@ComponentList({
    ErrorMessageMacro.class,
    TranslationMacro.class
})
class AddChangePageTest extends PageTest
{
    private static final DocumentReference ENTRY_VELOCITY_MACROS =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "EntryVelocityMacros");

    private static final DocumentReference TEST_PAGE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data"), "TestPage");

    /**
     * The spaces of the release note the changes are added to. The last one is the short form of the
     * {@code 8.3-milestone-1} version the macro is passed, and it carries a dot, so that the escaping the pages of
     * the release note are looked up with is exercised.
     */
    private static final List<String> VERSION_SPACES = List.of("ReleaseNotes", "Data", "XWiki", "8.3M1");

    @Mock
    private Query query;

    @Mock
    private QueryManager queryManager;

    /**
     * The location the author is sent to, as passed to {@code $response.sendRedirect}.
     */
    private String redirect;

    @BeforeEach
    void setUp() throws Exception
    {
        this.componentManager.registerComponent(QueryManager.class, this.queryManager);
        when(this.queryManager.createQuery(anyString(), anyString())).thenReturn(this.query);
        when(this.query.bindValue(anyString(), any())).thenReturn(this.query);
        when(this.query.execute()).thenReturn(List.of());

        // Taking the page of a new entry saves it, which both its author and the author of the calling page need the
        // edit right for.
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(eq(Right.EDIT), any()))
            .thenReturn(true);
        when(this.oldcore.getMockAuthorizationManager().hasAccess(eq(Right.EDIT), any(), any())).thenReturn(true);

        this.context.setResponse(new XWikiServletResponseStub()
        {
            @Override
            public void sendRedirect(String location)
            {
                AddChangePageTest.this.redirect = location;
            }
        });

        loadPage(ENTRY_VELOCITY_MACROS);

        // A change is only added to a release note that exists.
        this.xwiki.saveDocument(releaseNotePage(), this.context);
    }

    /**
     * The page of a new entry is created before its author is sent to the editor, so that a second author adding a
     * change to the same release note at the same time is handed another page rather than that same one.
     */
    @Test
    void entryPageIsCreatedBeforeItsAuthorIsSentToTheEditor() throws Exception
    {
        addChange();

        XWikiDocument entryPage = entryPage("Entry001");
        assertFalse(entryPage.isNew(), "Expected the page of the new entry to have been created.");
        // The entry template is applied by the "edit" action the author is sent to, which only applies it to a page
        // without content.
        assertEquals("", entryPage.getContent());
        assertNotNull(this.redirect, "Expected the author to be sent to the page of the new entry.");
        assertTrue(this.redirect.contains("Entry001"), this.redirect);
    }

    /**
     * A second author adding a change while the first one is still filling theirs in is handed the page after the one
     * the first author was handed. The first change is only saved at the end of the editing session, so the pages the
     * query sees are still the ones saved before either author started.
     */
    @Test
    void twoAuthorsAddingAChangeAtTheSameTimeAreHandedTwoPages() throws Exception
    {
        addChange();
        String firstRedirect = this.redirect;
        addChange();

        assertFalse(entryPage("Entry001").isNew());
        assertFalse(entryPage("Entry002").isNew(), "Expected the second author to be handed another page.");
        assertTrue(firstRedirect.contains("Entry001"), firstRedirect);
        assertTrue(this.redirect.contains("Entry002"), this.redirect);
    }

    /**
     * The number of a new entry is one past the highest number in use, compared as a number: sorted as strings,
     * {@code Entry999} comes out above {@code Entry1000} and the number 1000 is handed out over and over.
     */
    @Test
    void entryNumbersAreComparedAsNumbersAndNotAsStrings() throws Exception
    {
        existingEntryPages("Entry999", "Entry1000");

        addChange();

        assertFalse(entryPage("Entry1001").isNew(), "Expected the new entry to be numbered 1001.");
    }

    /**
     * The form of the home page lets its author type any version, and a change added to a release note that does not
     * exist would be listed nowhere. No page is taken and the author is told why, rather than shown the generic
     * failure or an error trace.
     */
    @Test
    void noChangeIsAddedToAReleaseNoteThatDoesNotExist() throws Exception
    {
        this.xwiki.deleteDocument(releaseNotePage(), this.context);

        String result = addChange();

        assertTrue(entryPage("Entry001").isNew(), "Expected no page to be taken for the new change.");
        assertNull(this.redirect, "Expected the author to stay on the page of the form.");
        assertTrue(result.contains("releasenotes.entry.releaseNoteNotFound"), result);
        assertFalse(result.contains("releasenotes.entry.addFailed"), result);
    }

    /**
     * Renders a {@code handleAddAction} call, which is what the "Add Change" buttons of the application do.
     *
     * @return the rendered page, where the macro reports what it could not do
     */
    private String addChange() throws Exception
    {
        XWikiDocument testPage = this.xwiki.getDocument(TEST_PAGE, this.context);
        testPage.setSyntax(Syntax.XWIKI_2_1);
        testPage.setContent("{{include reference=\"ReleaseNotes.Code.EntryVelocityMacros\"/}}\n\n"
            + "{{velocity}}\n"
            + "#handleAddAction('XWiki', '8.3-milestone-1', 'template=ReleaseNotes.Code.Change.ChangeTemplate')\n"
            + "{{/velocity}}");
        this.xwiki.saveDocument(testPage, this.context);
        this.context.setDoc(testPage);
        return testPage.getRenderedContent(this.context);
    }

    /**
     * Creates the passed entry pages and makes the query looking for the pages of the release note return them, as it
     * does once they are saved.
     */
    private void existingEntryPages(String... names) throws Exception
    {
        List<Object> references = new ArrayList<>();
        for (String name : names) {
            XWikiDocument entryPage = entryPage(name);
            this.xwiki.saveDocument(entryPage, this.context);
            references.add(serialize(entryPage.getDocumentReference()));
        }
        when(this.query.execute()).thenReturn(references);
    }

    private XWikiDocument releaseNotePage() throws Exception
    {
        return this.xwiki.getDocument(new DocumentReference("xwiki", VERSION_SPACES, "WebHome"), this.context);
    }

    private XWikiDocument entryPage(String name) throws Exception
    {
        List<String> spaces = new ArrayList<>(VERSION_SPACES);
        spaces.add(name);
        return this.xwiki.getDocument(new DocumentReference("xwiki", spaces, "WebHome"), this.context);
    }

    private String serialize(DocumentReference reference) throws Exception
    {
        return this.componentManager.<EntityReferenceSerializer<String>>getInstance(
            EntityReferenceSerializer.TYPE_STRING).serialize(reference);
    }
}
