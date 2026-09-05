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
package org.xwiki.releasenotes;

import java.util.List;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.localization.macro.internal.TranslationMacro;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.internal.ScriptQuery;
import org.xwiki.query.script.QueryManagerScriptService;
import org.xwiki.rendering.internal.macro.message.ErrorMessageMacro;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Page test for {@code ReleaseNotes.Code.MigrationFrom1x}, which converts the 1.x change objects of the whole wiki
 * into their 2.x form.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
// The page tells a user who may not run the migration so, through the translation and error message macros.
@ComponentList({
    TranslationMacro.class,
    ErrorMessageMacro.class
})
class MigrationFrom1xPageTest extends PageTest
{
    private static final DocumentReference MIGRATION =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "MigrationFrom1x");

    private static final DocumentReference CHANGES_CLASS =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "ChangesClass");

    private static final DocumentReference ENTRY_CLASS =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "EntryClass");

    /**
     * The page the migration query returns. A dot in a page name is escaped when a reference is serialized, so
     * this version page name deliberately has none: the name has to resolve back to the document below.
     */
    private static final String CHANGE_PAGE = "ReleaseNotes.Data.XWiki.83M1.Change001.WebHome";

    /**
     * The message the page displays over the list of the pages it has just converted. A PageTest renders a
     * translation as its key, which is what tells this run of the page from the one that only lists them.
     */
    private static final String MIGRATED_MESSAGE = "releasenotes.migration.migrated";

    private static final String VALID_TOKEN = "valid-token";

    @Mock
    private ScriptQuery query;

    @Mock
    private QueryManagerScriptService queryManagerScriptService;

    @BeforeEach
    void setUp() throws Exception
    {
        this.componentManager.registerComponent(ScriptService.class, "query", this.queryManagerScriptService);
        when(this.queryManagerScriptService.xwql(anyString())).thenReturn(this.query);
        when(this.query.bindValue(anyString(), any())).thenReturn(this.query);
        when(this.query.execute()).thenReturn(List.of(CHANGE_PAGE));
        this.componentManager.registerComponent(ScriptService.class, "csrf",
            new CSRFTokenScriptServiceStub(VALID_TOKEN));
        // Everything the conversion needs other than the right being tested is granted, so that a refusal below
        // is the right check refusing and not the conversion failing for an unrelated reason.
        when(this.oldcore.getMockRightService().hasAccessLevel(anyString(), anyString(), anyString(), any()))
            .thenReturn(true);
        when(this.oldcore.getMockRightService().hasProgrammingRights(any())).thenReturn(true);
        loadPage(ENTRY_CLASS);
        loadPage(new DocumentReference("xwiki", List.of("ReleaseNotes", "Code", "Change"), "ChangeClass"));
        // The page the query returns has to exist and carry the 1.x object, since that object is what the
        // migration reads, copies and then removes.
        XWikiDocument changePage = new XWikiDocument(new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "XWiki", "83M1", "Change001"), "WebHome"));
        changePage.newXObject(CHANGES_CLASS, this.context);
        this.xwiki.saveDocument(changePage, this.context);
    }

    /**
     * The migration rewrites every page holding a 1.x change, across the whole wiki, and cannot be undone. A user
     * who may not administer the wiki must not be offered it, and must not be shown the pages it would touch
     * either.
     */
    @Test
    void nonAdministratorIsRefusedTheMigration() throws Exception
    {
        registerSecurity(false);

        Document html = renderHTMLPage(MIGRATION);

        assertFalse(html.select(".errormessage").isEmpty(),
            "A user who may not administer the wiki must be told so, got: " + html.html());
        assertFalse(html.html().contains("confirm=1"),
            "The migration must not be offered to a user who may not run it, got: " + html.html());
        assertFalse(html.html().contains(CHANGE_PAGE),
            "The pages the migration would touch must not be listed either, got: " + html.html());
    }

    /**
     * The page answers the request that performs the migration, so hiding the link is not by itself what keeps
     * that request from performing it: the right check has to gate the conversion too.
     */
    @Test
    void nonAdministratorRequestingTheMigrationRunsNothing() throws Exception
    {
        registerSecurity(false);
        this.request.put("confirm", "1");
        this.request.put("form_token", VALID_TOKEN);

        Document html = renderHTMLPage(MIGRATION);

        assertFalse(html.select(".errormessage").isEmpty(),
            "The request must be refused, got: " + html.html());
        assertFalse(html.html().contains(MIGRATED_MESSAGE),
            "The migration must not report having run, got: " + html.html());
        assertFalse(html.html().contains(CHANGE_PAGE),
            "No page must have been converted, and the conversion loop is what lists them, got: " + html.html());
    }

    /**
     * An administrator is shown the pages the migration would touch, and the way to start it.
     */
    @Test
    void administratorIsOfferedTheMigration() throws Exception
    {
        registerSecurity(true);

        Document html = renderHTMLPage(MIGRATION);

        assertTrue(html.html().contains(CHANGE_PAGE),
            "An administrator must see the pages the migration would touch, got: " + html.html());
        assertTrue(html.html().contains("confirm=1"),
            "An administrator must be offered the migration, got: " + html.html());
    }

    /**
     * The counterpart of the refusal above, with everything else identical: the same request from an
     * administrator does run the migration, which is what makes that refusal a refusal.
     */
    @Test
    void administratorRequestingTheMigrationRunsIt() throws Exception
    {
        registerSecurity(true);
        this.request.put("confirm", "1");
        this.request.put("form_token", VALID_TOKEN);

        Document html = renderHTMLPage(MIGRATION);

        assertTrue(html.select(".errormessage").isEmpty(),
            "An administrator must not be refused, got: " + html.html());
        assertTrue(html.html().contains(MIGRATED_MESSAGE),
            "The migration must report having run, got: " + html.html());
        assertTrue(html.html().contains(CHANGE_PAGE),
            "The converted page must be listed, got: " + html.html());
    }

    private void registerSecurity(boolean allowed) throws Exception
    {
        this.componentManager.registerComponent(ScriptService.class, "security",
            new SecurityScriptServiceStub(allowed));
    }

}
