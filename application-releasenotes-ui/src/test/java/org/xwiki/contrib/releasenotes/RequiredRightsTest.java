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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.security.authorization.Right;
import org.xwiki.security.authorization.requiredrights.DocumentRequiredRight;
import org.xwiki.security.authorization.requiredrights.DocumentRequiredRights;
import org.xwiki.security.authorization.requiredrights.DocumentRequiredRightsManager;
import org.xwiki.test.page.PageTest;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Checks the rights the application's pages declare.
 * <p>
 * A page that enforces its required rights refuses edit to a user who does not hold them, which is what keeps a code
 * page from being rewritten by a user who merely holds edit right — and a right check written in a page's content
 * only lasts as long as nobody rewrites that content.
 * <p>
 * The rights are read back the way XWiki itself reads them, through {@link DocumentRequiredRightsManager}, so that
 * what is asserted is the right the platform will actually enforce rather than the shape of the XML that carries it.
 * The expectation is spelled out page by page rather than derived, so that adding a page to the application fails
 * this test until someone decides what that page requires.
 *
 * @version $Id$
 */
class RequiredRightsTest extends PageTest
{
    private static final String TOP_SPACE = "ReleaseNotes";

    /**
     * Enforcing with no required right at all is the strongest setting: it stops a later editor from introducing
     * script into the page.
     */
    private static final DocumentRequiredRights NOTHING = new DocumentRequiredRights(true, Set.of());

    private static final DocumentRequiredRights SCRIPT =
        new DocumentRequiredRights(true, Set.of(new DocumentRequiredRight(Right.SCRIPT, EntityType.DOCUMENT)));

    private static final DocumentRequiredRights WIKI_ADMIN =
        new DocumentRequiredRights(true, Set.of(new DocumentRequiredRight(Right.ADMIN, EntityType.WIKI)));

    private static final LocalDocumentReference GLOBAL_RIGHTS_CLASS =
        new LocalDocumentReference("XWiki", "XWikiGlobalRights");

    private static final Map<String, DocumentRequiredRights> EXPECTED_RIGHTS = new LinkedHashMap<>();

    static {
        // Registering a wiki-visible macro, contributing a wiki-scoped UI extension and declaring a wiki-scoped
        // translation bundle are wiki-administration-level acts in themselves, performed with the document author's
        // rights — this is not about the script those pages carry. For the five macro pages the required-rights
        // analyzer only reports script, because it looks at the macro body and not at what registering the macro
        // costs; capping the author at script leaves the macro unregistered and every page using it renders
        // "Unknown macro".
        EXPECTED_RIGHTS.put("Code/Change/DisplayChangesMacro", WIKI_ADMIN);
        EXPECTED_RIGHTS.put("Code/Change/GetChangesMacro", WIKI_ADMIN);
        EXPECTED_RIGHTS.put("Code/Change/ReleaseNotesChangesMacro", WIKI_ADMIN);
        EXPECTED_RIGHTS.put("Code/HTML5Video", WIKI_ADMIN);
        EXPECTED_RIGHTS.put("Code/ReleaseNotesContributorsMacro", WIKI_ADMIN);
        EXPECTED_RIGHTS.put("Code/ReleaseNotesTranslatorsMacro", WIKI_ADMIN);
        EXPECTED_RIGHTS.put("Code/ApplicationsPanelEntry", WIKI_ADMIN);
        EXPECTED_RIGHTS.put("Code/Translations", WIKI_ADMIN);
        // The analyzer computes script for the migration, seeing only a Velocity macro. It stays at wiki_admin: the
        // page tests for wiki administration in its own content and then writes across the whole wiki, and script
        // right is no protection against the user who can rewrite that check — one who holds script right.
        EXPECTED_RIGHTS.put("Code/MigrationFrom1x", WIKI_ADMIN);

        EXPECTED_RIGHTS.put("Code/Change/ChangeClass", SCRIPT);
        EXPECTED_RIGHTS.put("Code/Change/ChangeDisplayerFlow", SCRIPT);
        EXPECTED_RIGHTS.put("Code/Change/ChangeDisplayerGrid", SCRIPT);
        EXPECTED_RIGHTS.put("Code/Change/ChangeDisplayerList", SCRIPT);
        EXPECTED_RIGHTS.put("Code/Change/ChangeDisplayerSimple", SCRIPT);
        EXPECTED_RIGHTS.put("Code/Change/ChangeDisplayerVelocityMacros", SCRIPT);
        EXPECTED_RIGHTS.put("Code/Change/ChangeSheet", SCRIPT);
        EXPECTED_RIGHTS.put("Code/ContributorsSheet", SCRIPT);
        EXPECTED_RIGHTS.put("Code/TranslatorsSheet", SCRIPT);
        EXPECTED_RIGHTS.put("Code/EntryVelocityMacros", SCRIPT);
        EXPECTED_RIGHTS.put("Code/HomeCustomReport", SCRIPT);
        EXPECTED_RIGHTS.put("Code/HomeReleaseChanges", SCRIPT);
        EXPECTED_RIGHTS.put("Code/HomeReleaseNotes", SCRIPT);
        EXPECTED_RIGHTS.put("Code/Report", SCRIPT);
        EXPECTED_RIGHTS.put("Data/WebHome", SCRIPT);

        EXPECTED_RIGHTS.put("Code/Change/ChangeTemplate", NOTHING);
        EXPECTED_RIGHTS.put("Code/Change/WebHome", NOTHING);
        EXPECTED_RIGHTS.put("Code/ContributorsClass", NOTHING);
        EXPECTED_RIGHTS.put("Code/ContributorsTemplate", NOTHING);
        EXPECTED_RIGHTS.put("Code/TranslatorsClass", NOTHING);
        EXPECTED_RIGHTS.put("Code/TranslatorsTemplate", NOTHING);
        EXPECTED_RIGHTS.put("Code/EntryClass", NOTHING);
        EXPECTED_RIGHTS.put("Code/ReleaseNoteClass", NOTHING);
        // The template is copied onto every release note created from it, so what it asks for is what a release note
        // asks for: a template written in Velocity would make the script right a condition of a release note
        // displaying its own title. It is plain text, and so are the notes made from it.
        EXPECTED_RIGHTS.put("Code/ReleaseNoteTemplate", NOTHING);
        EXPECTED_RIGHTS.put("Code/ReleaseNotesConfig", NOTHING);
        EXPECTED_RIGHTS.put("Code/ReleaseNotesConfigClass", NOTHING);
        EXPECTED_RIGHTS.put("Code/WebHome", NOTHING);
        EXPECTED_RIGHTS.put("Code/WebPreferences", NOTHING);
        // The application home only includes the page below it, and an included page is judged on the rights it
        // declares itself rather than on those of the page including it, so nothing here has to run.
        EXPECTED_RIGHTS.put("WebHome", NOTHING);
    }

    @Test
    void everyPageDeclaresTheRightItRequires() throws Exception
    {
        DocumentRequiredRightsManager rightsManager =
            this.componentManager.getInstance(DocumentRequiredRightsManager.class);

        Map<String, DocumentRequiredRights> declared = new LinkedHashMap<>();
        for (String page : pages()) {
            DocumentReference reference = reference(page);
            loadPage(reference);
            declared.put(page, rightsManager.getRequiredRights(reference).orElseThrow());
        }

        assertEquals(EXPECTED_RIGHTS, declared,
            "Each page must enforce its required rights and declare the right it needs. A page missing from the "
                + "expected values is a new page whose required right nobody has decided yet.");
    }

    /**
     * The application's code is only as safe as the rule that says who may rewrite it, so the rule ships with it. An
     * allow rule is exclusive, which is why {@code view} is deliberately absent: listing it would deny view to
     * everyone outside the group.
     */
    @Test
    void onlyAdministratorsMayEditTheCodeSpace() throws Exception
    {
        XWikiDocument webPreferences = loadPage(reference("Code/WebPreferences"));

        List<BaseObject> rights = webPreferences.getXObjects(GLOBAL_RIGHTS_CLASS);
        assertEquals(1, rights.size(), "The code space's preferences page must carry exactly one rights object.");
        BaseObject rule = rights.get(0);
        assertNotNull(rule);
        assertEquals(1, rule.getIntValue("allow"), "The rule must allow rather than deny.");
        assertEquals("XWiki.XWikiAdminGroup", rule.getStringValue("groups"),
            "Editing the application's own pages is an administrator's business.");
        assertEquals("delete,edit", rule.getStringValue("levels"),
            "Only edit and delete are restricted: view must stay open, since an allow rule is exclusive.");
    }

    /**
     * @return the name of every page of the application, relative to its top-level space and without the
     *     {@code .xml} extension
     */
    private List<String> pages() throws Exception
    {
        Path root = Path.of(getClass().getClassLoader().getResource(TOP_SPACE).toURI());
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".xml"))
                .map(path -> root.relativize(path).toString().replace('\\', '/').replaceAll("\\.xml$", ""))
                .sorted()
                .toList();
        }
    }

    private DocumentReference reference(String page)
    {
        List<String> segments = List.of(page.split("/"));
        List<String> spaces = new ArrayList<>();
        spaces.add(TOP_SPACE);
        spaces.addAll(segments.subList(0, segments.size() - 1));
        return new DocumentReference("xwiki", spaces, segments.get(segments.size() - 1));
    }
}
