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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.localization.macro.internal.TranslationMacro;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.script.ModelScriptService;
import org.xwiki.query.Query;
import org.xwiki.query.QueryManager;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.wikimacro.internal.WikiMacroFactoryComponentClass;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.WikiMacroSetup;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Page test for {@code ReleaseNotes.Code.UpgradeNotes}, which lists the migration notes of the released versions
 * between the version upgraded from and the version upgraded to.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@WikiMacroFactoryComponentClass
// The page looks for the release notes and the changes through the application's Java API, displays its strings
// with the translation macro, and links the changes and the release notes through $services.model.
@ReleaseNotesApiComponentList
@ComponentList({ TranslationMacro.class, ModelScriptService.class })
class UpgradeNotesPageTest extends PageTest
{
    private static final List<String> CODE_SPACE = List.of("ReleaseNotes", "Code");

    private static final List<String> CHANGE_SPACE = List.of("ReleaseNotes", "Code", "Change");

    private static final DocumentReference UPGRADE_NOTES = new DocumentReference("xwiki", CODE_SPACE, "UpgradeNotes");

    private static final DocumentReference RELEASE_NOTE_CLASS =
        new DocumentReference("xwiki", CODE_SPACE, "ReleaseNoteClass");

    private static final DocumentReference ENTRY_CLASS = new DocumentReference("xwiki", CODE_SPACE, "EntryClass");

    private static final DocumentReference CHANGE_CLASS = new DocumentReference("xwiki", CHANGE_SPACE, "ChangeClass");

    private static final String PRODUCT = "XWiki";

    /**
     * The beginning of the statement of the query looking for the release notes of a product.
     */
    private static final String RELEASE_NOTES_STATEMENT = "from doc.object(ReleaseNotes.Code.ReleaseNoteClass)";

    /**
     * The beginning of the statement of the query looking for the changes.
     */
    private static final String CHANGES_STATEMENT = "from doc.object(ReleaseNotes.Code.EntryClass)";

    @Mock
    private QueryManager queryManager;

    @Mock
    private Query releaseNotesQuery;

    @Mock
    private Query changesQuery;

    @Mock
    private Query otherQuery;

    private EntityReferenceSerializer<String> localSerializer;

    /** The names of the pages of the release notes the wiki holds. */
    private final List<Object> releaseNotes = new ArrayList<>();

    /** The names of the pages of the changes the search finds. */
    private final List<Object> foundChanges = new ArrayList<>();

    /** The statements of the queries looking for the changes. */
    private final List<String> changesStatements = new ArrayList<>();

    /** The values bound to the query looking for the changes, by parameter name. */
    private final Map<String, String> changesBindings = new LinkedHashMap<>();

    @BeforeEach
    void setUp() throws Exception
    {
        this.localSerializer = this.componentManager.getInstance(EntityReferenceSerializer.TYPE_STRING, "local");

        this.componentManager.registerComponent(QueryManager.class, this.queryManager);
        when(this.queryManager.createQuery(anyString(), anyString())).thenAnswer(invocation -> {
            String statement = invocation.getArgument(0);
            if (statement.startsWith(RELEASE_NOTES_STATEMENT)) {
                return this.releaseNotesQuery;
            } else if (statement.startsWith(CHANGES_STATEMENT)) {
                this.changesStatements.add(statement);
                return this.changesQuery;
            }
            return this.otherQuery;
        });
        when(this.releaseNotesQuery.bindValue(anyString(), any())).thenReturn(this.releaseNotesQuery);
        when(this.releaseNotesQuery.execute()).thenAnswer(invocation -> this.releaseNotes);
        when(this.changesQuery.bindValue(anyString(), any())).thenAnswer(invocation -> {
            this.changesBindings.put(invocation.getArgument(0), String.valueOf((Object) invocation.getArgument(1)));
            return this.changesQuery;
        });
        when(this.changesQuery.execute()).thenAnswer(invocation -> this.foundChanges);
        when(this.otherQuery.bindValue(anyString(), any())).thenReturn(this.otherQuery);
        when(this.otherQuery.execute()).thenReturn(List.of());

        // The page escapes the versions it places into the getChanges call and the headings it links. The stand-in
        // escapes the way the platform does, so that what the parser gets back is what these tests assert on.
        this.componentManager.registerComponent(ScriptService.class, "rendering",
            new RenderingScriptServiceStub(RenderingScriptServiceStub.xwikiSyntaxEscaper()));

        loadPage(RELEASE_NOTE_CLASS);
        loadPage(ENTRY_CLASS);
        loadPage(CHANGE_CLASS);
        loadPage(new DocumentReference("xwiki", CHANGE_SPACE, "ChangeDisplayerVelocityMacros"));
        loadPage(new DocumentReference("xwiki", CHANGE_SPACE, "ChangeDisplayerMigrationNotes"));
        WikiMacroSetup.loadWikiMacro(this, this.componentManager,
            new DocumentReference("xwiki", CHANGE_SPACE, "GetChangesMacro"));
        WikiMacroSetup.loadWikiMacro(this, this.componentManager,
            new DocumentReference("xwiki", CHANGE_SPACE, "DisplayChangesMacro"));

        createReleaseNote("10.11.9", "10.11.9", true);
        createReleaseNote("11.4RC1", "11.4-rc-1", true);
        createReleaseNote("11.4", "11.4", true);
        createReleaseNote("11.10", "11.10", true);
        createReleaseNote("12.0", "12.0", false);
    }

    /**
     * The notes of an upgrade are the ones of the versions it goes through: the version upgraded from is already
     * installed and is left out, the release candidates on the way are part of it, and a version not released yet is
     * not one an upgrade reaches, since its notes may still change.
     */
    @Test
    void theNotesOfTheReleasedVersionsOfTheRangeAreAskedFor() throws Exception
    {
        renderUpgrade("10.11.9", "12.0");

        assertEquals(1, this.changesStatements.size(), "Expected one search for the notes of the whole range.");
        assertEquals(List.of("Migration"), boundValues("type"));
        assertEquals(List.of(PRODUCT), boundValues("product"));
        assertEquals(List.of("11.4-rc-1", "11.4", "11.10"), boundValues("version"));
        assertTrue(this.changesStatements.get(0).contains("entries.product = :product1"),
            "The product is asked for exactly: " + this.changesStatements.get(0));
    }

    /**
     * Asking for a version that is not released is not a mistake, but the reader has to be told that its notes are
     * missing from the list rather than left to believe it has none.
     */
    @Test
    void anUnreleasedVersionToUpgradeToIsReported() throws Exception
    {
        String text = renderUpgrade("10.11.9", "12.0").text();

        assertTrue(text.contains("releasenotes.upgrade.notReleased [12.0]"), text);
    }

    @Test
    void aReleasedVersionToUpgradeToIsNotReported() throws Exception
    {
        String text = renderUpgrade("10.11.9", "11.10").text();

        assertFalse(text.contains("releasenotes.upgrade.notReleased"), text);
    }

    /**
     * An upgrade goes through the versions in the order they are released, which is the order their notes are
     * displayed in whatever order the search returned them in: a release candidate before its final version, and
     * 11.4 before 11.10, which an alphabetical order would swap.
     */
    @Test
    void theNotesAreGroupedByVersionInTheOrderTheVersionsAreReleased() throws Exception
    {
        this.foundChanges.add(createChange("11.10", "Entry001", "Change of 11.10"));
        this.foundChanges.add(createChange("11.4", "Entry001", "Change of 11.4"));
        this.foundChanges.add(createChange("11.4RC1", "Entry001", "Change of 11.4-rc-1"));
        this.foundChanges.add(createChange("11.4", "Entry002", "Another change of 11.4"));

        Document html = renderUpgrade("10.11.9", "12.0");

        assertTrue(html.select(".xwikirenderingerror").isEmpty(), html.body().html());
        assertEquals(List.of("11.4-rc-1", "11.4", "11.10"), html.select("h2").eachText());
        assertEquals(List.of("Change of 11.4-rc-1", "Change of 11.4", "Another change of 11.4", "Change of 11.10"),
            html.select(".rn-migration-change a").eachText());
        assertTrue(html.text().contains("Notes of Change of 11.4"), html.text());
    }

    @Test
    void aRangeWithNoNotesSaysSo() throws Exception
    {
        String text = renderUpgrade("10.11.9", "11.10").text();

        assertTrue(text.contains("releasenotes.upgrade.none"), text);
    }

    /**
     * A version to upgrade to that does not come after the version upgraded from is no upgrade at all.
     */
    @Test
    void aRangeGoingBackwardsIsRefused() throws Exception
    {
        String text = renderUpgrade("11.10", "11.4").text();

        assertTrue(text.contains("releasenotes.upgrade.invalidRange"), text);
        assertTrue(this.changesStatements.isEmpty(), "Expected no search for a range going backwards.");
    }

    /**
     * Every version with a release note is one an upgrade may start from, but only the released ones are versions an
     * upgrade may reach, so only those are suggested as the version to upgrade to.
     */
    @Test
    void onlyTheReleasedVersionsAreSuggestedToUpgradeTo() throws Exception
    {
        Document html = renderUpgrade("", "");

        assertEquals(List.of("10.11.9", "11.4-rc-1", "11.4", "11.10", "12.0"),
            html.select("#upgrade-from-versions option").eachAttr("value"));
        assertEquals(List.of("10.11.9", "11.4-rc-1", "11.4", "11.10"),
            html.select("#upgrade-to-versions option").eachAttr("value"));
        assertTrue(this.changesStatements.isEmpty(), "Expected no search before a range is given.");
    }

    private Document renderUpgrade(String from, String to) throws Exception
    {
        this.request.put("product", PRODUCT);
        this.request.put("from", from);
        this.request.put("to", to);

        return renderHTMLPage(UPGRADE_NOTES);
    }

    private void createReleaseNote(String shortVersion, String version, boolean released) throws Exception
    {
        XWikiDocument note = new XWikiDocument(releaseNoteReference(shortVersion));
        note.setSyntax(Syntax.XWIKI_2_1);
        BaseObject object = note.newXObject(RELEASE_NOTE_CLASS, this.context);
        object.setStringValue("product", PRODUCT);
        object.setStringValue("version", version);
        object.setIntValue("released", released ? 1 : 0);
        this.xwiki.saveDocument(note, this.context);

        this.releaseNotes.add(this.localSerializer.serialize(note.getDocumentReference()));
    }

    private String createChange(String shortVersion, String entry, String title) throws Exception
    {
        List<String> spaces = new ArrayList<>(releaseNoteReference(shortVersion).getSpaceReferences().stream()
            .map(space -> space.getName()).collect(Collectors.toList()));
        spaces.add(entry);
        XWikiDocument change = new XWikiDocument(new DocumentReference("xwiki", spaces, "WebHome"));
        change.setSyntax(Syntax.XWIKI_2_1);
        BaseObject entryObject = change.newXObject(ENTRY_CLASS, this.context);
        entryObject.setStringValue("product", PRODUCT);
        entryObject.setStringValue("version", versionOf(shortVersion));
        entryObject.setStringValue("type", "Migration");
        BaseObject changeObject = change.newXObject(CHANGE_CLASS, this.context);
        changeObject.setStringValue("title", title);
        changeObject.setLargeStringValue("summary", "Notes of " + title);
        this.xwiki.saveDocument(change, this.context);

        return this.localSerializer.serialize(change.getDocumentReference());
    }

    private static String versionOf(String shortVersion)
    {
        return shortVersion.replace("RC", "-rc-");
    }

    private static DocumentReference releaseNoteReference(String shortVersion)
    {
        return new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", PRODUCT, shortVersion), "WebHome");
    }

    /**
     * @param prefix the prefix shared by the names of the query parameters to return
     * @return the values bound to those parameters of the query looking for the changes, in binding order
     */
    private List<String> boundValues(String prefix)
    {
        return this.changesBindings.entrySet().stream()
            .filter(binding -> binding.getKey().startsWith(prefix))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }
}
