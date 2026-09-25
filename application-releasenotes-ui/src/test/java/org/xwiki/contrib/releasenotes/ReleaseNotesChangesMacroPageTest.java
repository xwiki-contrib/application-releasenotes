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
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.xwiki.localization.macro.internal.TranslationMacro;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.script.ModelScriptService;
import org.xwiki.query.Query;
import org.xwiki.query.QueryManager;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.security.authorization.Right;
import org.xwiki.rendering.wikimacro.internal.WikiMacroFactoryComponentClass;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.WikiMacroSetup;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.web.XWikiServletResponseStub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Page test for the {@code releasenotechanges} wiki macro, defined in
 * {@code ReleaseNotes.Code.Change.ReleaseNotesChangesMacro}.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@WikiMacroFactoryComponentClass
// The pages under test display their strings with the translation macro, address the media of a change through
// $services.model, and take the page of a new change through the application's Java API.
@ReleaseNotesApiComponentList
@ComponentList({ TranslationMacro.class, ModelScriptService.class })
class ReleaseNotesChangesMacroPageTest extends PageTest
{
    private static final DocumentReference RELEASE_NOTES_CHANGES_MACRO =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code", "Change"), "ReleaseNotesChangesMacro");

    private static final DocumentReference GET_CHANGES_MACRO =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code", "Change"), "GetChangesMacro");

    private static final DocumentReference RELEASE_NOTE_CLASS =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "ReleaseNoteClass");

    private static final String PRODUCT = "XWiki";

    private static final String VALID_TOKEN = "valid-token";

    /** The clause selecting the changes that do have a screenshot. */
    private static final String HAS_SCREENSHOTS =
        "(changes.screenshots <> '' or (changes.screenshots is not null and '' is null))";

    /**
     * The beginning of the statement of the query looking for the changes, which tells it apart from every other
     * query the rendering of the release note runs.
     */
    private static final String CHANGES_STATEMENT = "from doc.object(ReleaseNotes.Code.EntryClass)";

    private static final String WITH_SCREENSHOTS = "and " + HAS_SCREENSHOTS;

    private static final String WITHOUT_SCREENSHOTS = "and not " + HAS_SCREENSHOTS;

    /** No screenshot clause at all, i.e. the section displays a change whether it has a screenshot or not. */
    private static final String ANY_SCREENSHOTS = "";

    /** The three importance values, i.e. the section displays a change whatever its importance. */
    private static final List<String> ANY_IMPORTANCE = List.of("0", "1", "2");

    /**
     * The macro splits its changes over one section per audience, and each section is what a warning is about.
     */
    private static final int AUDIENCE_COUNT = 3;

    @Mock
    private Query query;

    /** Every other query the rendering runs, e.g. the pages of the release note, when a new entry is taken. */
    @Mock
    private Query otherQuery;

    @Mock
    private QueryManager queryManager;

    /** The statement of each query the rendered release note built, in the order the sections built them. */
    private final List<String> statements = new ArrayList<>();

    /** The values bound to each of those queries, by parameter name. */
    private final List<Map<String, String>> bindings = new ArrayList<>();

    /** Where a submitted add-change action redirected the author, i.e. the page it reserved, or {@code null}. */
    private String redirect;

    @BeforeEach
    void setUp() throws Exception
    {
        this.componentManager.registerComponent(QueryManager.class, this.queryManager);
        when(this.queryManager.createQuery(anyString(), anyString())).thenAnswer(invocation -> {
            String statement = invocation.getArgument(0);

            if (!statement.startsWith(CHANGES_STATEMENT)) {
                return this.otherQuery;
            }

            this.statements.add(statement);
            this.bindings.add(new LinkedHashMap<>());
            return this.query;
        });
        when(this.otherQuery.bindValue(anyString(), any())).thenReturn(this.otherQuery);
        // A query is built and then bound before the next one is built, so a bound value belongs to the last
        // statement recorded above. That is what makes a filter attributable to the section that applied it.
        when(this.query.bindValue(anyString(), any())).thenAnswer(invocation -> {
            Object value = invocation.getArgument(1);
            this.bindings.get(this.bindings.size() - 1).put(invocation.getArgument(0), String.valueOf(value));
            return this.query;
        });

        // A PageTest does not register $services.rendering, which the macro escapes the values it places into the
        // getChanges calls with. The stand-in escapes the way the platform does, so that what the parser gets back
        // is what these tests assert on.
        this.componentManager.registerComponent(ScriptService.class, "rendering",
            new RenderingScriptServiceStub(RenderingScriptServiceStub.xwikiSyntaxEscaper()));

        WikiMacroSetup.loadWikiMacro(this, this.componentManager, GET_CHANGES_MACRO);
        WikiMacroSetup.loadWikiMacro(this, this.componentManager, RELEASE_NOTES_CHANGES_MACRO);
    }

    /**
     * A release note that displays only a page of its changes looks complete while it is not, so it has to say that
     * some of its changes are missing: this warning is the only sign the author gets that the limit needs raising.
     */
    @Test
    void sectionLeavingChangesOutIsReported() throws Exception
    {
        // One change more than the sections are allowed to display.
        when(this.query.execute()).thenReturn(changes(3));

        assertEquals(AUDIENCE_COUNT, renderReleaseNote(2).select("div.warningmessage").size(),
            "Each section of the release note left a change out, so each of them must report it.");
    }

    @Test
    void sectionDisplayingAllItsChangesIsNotReported() throws Exception
    {
        when(this.query.execute()).thenReturn(changes(2));

        assertEquals(0, renderReleaseNote(2).select("div.warningmessage").size(),
            "No change was left out, so the release note must not claim otherwise.");
    }

    /**
     * The limit exists to keep a release note from loading every change of the product it aggregates, so it has to
     * bound each of the queries the macro runs, not just the first one.
     */
    @Test
    void limitBoundsEveryQueryOfTheReleaseNote() throws Exception
    {
        when(this.query.execute()).thenReturn(changes(2));

        renderReleaseNote(50);

        // Two queries per audience section, each asking for one row beyond the limit.
        verify(this.query, times(2 * AUDIENCE_COUNT)).setLimit(51);
    }

    /**
     * A release note gets the version it displays from the name of its page, and a final release note aggregates
     * the changes of the milestones and release candidates that led to it. A milestone or release candidate
     * displays only its own changes, under the version those changes were stored with.
     */
    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "8.3M1;  8.3-milestone-1",
        "8.3M12; 8.3-milestone-12",
        "8.3RC1; 8.3-rc-1",
        "8.3;    8.3, 8.3-milestone%, 8.3-rc%",
        "8.3.1;  8.3.1, 8.3.1-milestone%, 8.3.1-rc%"
    })
    void versionsQueriedByTheReleaseNote(String shortVersion, String expectedVersions) throws Exception
    {
        when(this.query.execute()).thenReturn(changes(0));

        renderReleaseNote(shortVersion, 100);

        assertEquals(List.of(expectedVersions.split("\\s*,\\s*")), boundValues(0, "version"));
    }

    /**
     * A release note has one section per audience, and each section splits its changes in two: the user and admin
     * sections lead with the changes illustrated by a screenshot and list the others under "Miscellaneous", while
     * the developer section, whose changes rarely have one, leads with the important ones instead. Every change of
     * the audience must fall in exactly one of the two queries, otherwise the release note silently loses it.
     */
    @Test
    void eachAudienceSectionSplitsItsChangesInTwo() throws Exception
    {
        when(this.query.execute()).thenReturn(changes(0));

        renderReleaseNote(100);

        assertEquals(2 * AUDIENCE_COUNT, this.statements.size(), "Expected two queries per audience section.");
        assertSectionQuery(0, "user", WITH_SCREENSHOTS, ANY_IMPORTANCE);
        assertSectionQuery(1, "user", WITHOUT_SCREENSHOTS, ANY_IMPORTANCE);
        assertSectionQuery(2, "administrator", WITH_SCREENSHOTS, ANY_IMPORTANCE);
        assertSectionQuery(3, "administrator", WITHOUT_SCREENSHOTS, ANY_IMPORTANCE);
        assertSectionQuery(4, "developer", ANY_SCREENSHOTS, List.of("1", "2"));
        assertSectionQuery(5, "developer", ANY_SCREENSHOTS, List.of("0"));
    }

    /**
     * Asked for its migration notes, a release note displays them instead of its changes: one query for all of them,
     * whatever their audience, keeping only the migration note entries, and none of the queries of the changes
     * sections.
     */
    @Test
    void theMigrationNotesAreQueriedInsteadOfTheChanges() throws Exception
    {
        when(this.query.execute()).thenReturn(changes(0));

        Document html = renderReleaseNote("8.3", "8.3", PRODUCT, "migrationNotes=\"true\" limit=\"100\"");

        assertEquals(1, this.statements.size(), "Expected a single query for the migration notes: " + this.statements);
        assertEquals(List.of("user", "administrator", "developer"), boundValues(0, "audience"),
            "The migration notes of every audience are asked for at once.");
        assertEquals(List.of(PRODUCT), boundValues(0, "product"));
        assertEquals(List.of("Migration"), boundValues(0, "type"));
        assertFalse(this.statements.get(0).contains("changes.screenshots"), this.statements.get(0));
        assertTrue(html.text().contains("releasenotes.changes.migrationNotes.none"), html.body().html());
    }

    /**
     * The migration notes of a release note are displayed as one list, whatever the audience each of them is
     * written for, with no heading per audience.
     */
    @Test
    void theMigrationNotesAreDisplayedAsOneList() throws Exception
    {
        List<String> changeSpace = List.of("ReleaseNotes", "Code", "Change");
        loadPage(new DocumentReference("xwiki", changeSpace, "ChangeClass"));
        loadPage(new DocumentReference("xwiki", changeSpace, "ChangeDisplayerVelocityMacros"));
        loadPage(new DocumentReference("xwiki", changeSpace, "ChangeDisplayerMigrationNotes"));
        WikiMacroSetup.loadWikiMacro(this, this.componentManager,
            new DocumentReference("xwiki", changeSpace, "DisplayChangesMacro"));
        List<Object> notes = List.of(createMigrationNote("Entry001", "An admin note", "administrator"),
            createMigrationNote("Entry002", "A developer note", "developer"));
        when(this.query.execute()).thenReturn(notes);

        Document html = renderReleaseNote("8.3", "8.3", PRODUCT, "migrationNotes=\"true\" limit=\"100\"");

        assertTrue(html.select(".xwikirenderingerror").isEmpty(), html.body().html());
        assertEquals(1, html.select("ul").size(), "Expected a single list of notes: " + html.body().html());
        assertEquals(List.of("An admin note", "A developer note"), html.select(".rn-migration-change a").eachText());
        assertTrue(html.select("h2").isEmpty(), "Expected no heading per audience: " + html.body().html());
    }

    /**
     * The sections of the changes only display the changes, and leave the migration notes to their own section:
     * otherwise every migration note would be displayed twice in a release note.
     */
    @Test
    void theChangesSectionsOnlyAskForChanges() throws Exception
    {
        when(this.query.execute()).thenReturn(changes(0));

        renderReleaseNote(100);

        for (int index = 0; index < this.statements.size(); index++) {
            assertEquals(List.of("Change"), boundValues(index, "type"));
        }
    }

    /**
     * A migration note is added from the migration notes section, whose button asks for an entry of the migration
     * type, while the buttons of the changes sections leave the type to the change template.
     */
    @Test
    void theMigrationNotesSectionOffersToAddAMigrationNote() throws Exception
    {
        registerVelocityTool("hasEdit", true);
        when(this.query.execute()).thenReturn(changes(0));

        Document html = renderReleaseNote("8.3", "8.3", PRODUCT, "migrationNotes=\"true\" limit=\"100\"");

        assertEquals(List.of("migrationadd"), html.select("form input[name=action]").eachAttr("value"));
        assertEquals(List.of("Migration"), html.select("form input[name=type]").eachAttr("value"));
    }

    /**
     * The product is not part of the page name, it comes from the release note xobject, and every section must
     * query that product only.
     */
    @Test
    void productQueriedComesFromTheReleaseNoteObject() throws Exception
    {
        when(this.query.execute()).thenReturn(changes(0));

        renderReleaseNote(100);

        for (int index = 0; index < this.statements.size(); index++) {
            assertEquals(List.of(PRODUCT), boundValues(index, "product"));
        }
    }

    /**
     * The product and version an author typed reach the "Add Change" forms as the value of a hidden input, so a
     * value carrying an attribute delimiter must be emitted escaped: left raw, it would break out of the input and
     * inject markup that runs for everyone who later views the release note.
     */
    @Test
    void addChangeFormsEscapeTheStoredVersion() throws Exception
    {
        // The creation forms are only rendered for a user who can edit the release note.
        registerVelocityTool("hasEdit", true);
        when(this.query.execute()).thenReturn(changes(0));

        String payload = "\"><script>alert(1)</script>";
        Document html = renderReleaseNote("8.3", payload, 100);

        Elements versionInputs = html.select("form input[type=hidden][name=version]");
        assertFalse(versionInputs.isEmpty(), "Expected the add-change forms to be rendered for an editor.");
        for (Element input : versionInputs) {
            // jsoup decodes the attribute, so an escaped value round-trips to the payload; an unescaped one would
            // have been truncated at the first quote.
            assertEquals(payload, input.attr("value"),
                "The stored version must be carried as a single attribute value, not broken out of it.");
        }
        assertTrue(html.select("script").isEmpty(),
            "The stored version must not be able to inject a script element into the release note.");
    }

    /**
     * The product is plain text stored in the release note xobject, but the macro places it into the parameters of
     * the getChanges calls it builds, which are re-parsed as wiki syntax, so it must be emitted escaped: left raw,
     * a product carrying a double quote would close the parameter and the rest of it would be parsed as wiki
     * syntax of its own, macros included.
     */
    @Test
    void productIsEscapedBeforeItIsRenderedAsWikiSyntax() throws Exception
    {
        when(this.query.execute()).thenReturn(changes(0));

        String product = "XWiki\" x=\"1\"/}}{{html}}<b>escaped</b>{{/html}}";
        Document html = renderReleaseNote("8.3", "8.3", product, 100);

        assertTrue(html.select("b").isEmpty(),
            "The product must not close the getChanges call and have the rest of it rendered as wiki syntax: "
                + html.body().html());
        assertEquals(2 * AUDIENCE_COUNT, this.statements.size(), "Expected two queries per audience section.");
        for (int index = 0; index < this.statements.size(); index++) {
            assertEquals(List.of(product), boundValues(index, "product"),
                "The product must reach the query as a single filter, with its own value.");
        }
    }

    /**
     * The version comes from the name of the space holding the release note, and the macro places the versions it
     * derives from it into the parameters of the getChanges calls, so those too must be emitted escaped: left raw,
     * a space name carrying a double quote would close the parameter and the rest of it would be parsed as wiki
     * syntax of its own, macros included.
     */
    @Test
    void versionsAreEscapedBeforeTheyAreRenderedAsWikiSyntax() throws Exception
    {
        when(this.query.execute()).thenReturn(changes(0));

        // A space name with neither an "M" nor an "RC" in it, so that it is read as a final version and goes
        // through the aggregation branch.
        String shortVersion = "8.3\" x=\"1\"/}}{{html}}<b>escaped</b>{{/html}}";
        Document html = renderReleaseNote(shortVersion, "8.3", PRODUCT, 100);

        assertTrue(html.select("b").isEmpty(),
            "The space name must not close the getChanges call and have the rest of it rendered as wiki syntax: "
                + html.body().html());
        assertEquals(2 * AUDIENCE_COUNT, this.statements.size(), "Expected two queries per audience section.");
        for (int index = 0; index < this.statements.size(); index++) {
            assertEquals(
                List.of(shortVersion, shortVersion + "-milestone%", shortVersion + "-rc%"),
                boundValues(index, "version"),
                "The aggregated versions must reach the query as their own filters, with their own values.");
        }
    }

    /**
     * A release note only offers its "Add Change" button to an editor while it is not released, so a request to add
     * a change to a released note is one its own pages never send. The button being hidden is not on its own what
     * stops the request from being honoured: the action must be handled under the same condition that shows it.
     */
    @Test
    void aReleasedNoteDoesNotHandleTheAddChangeAction() throws Exception
    {
        assertNull(submitAddChangeAction("1"),
            "A released release note must not reserve a change from a submitted add-change action.");
    }

    /**
     * The counterpart of the above: an editor's add-change action on a note that is not released is handled, which
     * is what tells the guard above apart from one that would refuse every request.
     */
    @Test
    void anEditableUnreleasedNoteHandlesTheAddChangeAction() throws Exception
    {
        assertNotNull(submitAddChangeAction("0"),
            "An editor's add-change action on a note that is not released must reserve the change it redirects to.");
    }

    /**
     * Renders a release note while a {@code useradd} action, carrying a valid form token, is submitted against it
     * by a user who can edit it, i.e. the request the note's own "Add Change" button sends, and returns where the
     * note redirected the author, that is the page it reserved for the new change, or {@code null} when the action
     * was not handled.
     *
     * @param released the value stored in the {@code released} field of the release note xobject
     */
    private String submitAddChangeAction(String released) throws Exception
    {
        // The action is submitted the way the note's own button submits it: by an editor, with a valid token.
        registerVelocityTool("hasEdit", true);
        this.componentManager.registerComponent(ScriptService.class, "csrf",
            new CSRFTokenScriptServiceStub(VALID_TOKEN));
        this.request.put("action", "useradd");
        this.request.put("form_token", VALID_TOKEN);
        // Reserving the page of a new change saves it, which the application is allowed to do on the author's behalf.
        when(this.oldcore.getMockRightService().hasAccessLevel(anyString(), anyString(), anyString(), any()))
            .thenReturn(true);
        when(this.oldcore.getMockRightService().hasProgrammingRights(any())).thenReturn(true);
        this.redirect = null;
        this.context.setResponse(new XWikiServletResponseStub()
        {
            @Override
            public void sendRedirect(String location)
            {
                ReleaseNotesChangesMacroPageTest.this.redirect = location;
            }
        });
        // No change exists yet, so a handled action reserves the first one.
        when(this.query.execute()).thenReturn(List.of());
        when(this.otherQuery.execute()).thenReturn(List.of());
        // Taking the page of a new change saves it, which both its author and the author of the calling page need
        // the edit right for.
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(eq(Right.EDIT), any())).thenReturn(true);
        when(this.oldcore.getMockAuthorizationManager().hasAccess(eq(Right.EDIT), any(), any())).thenReturn(true);

        // The action is handled by #handleAddAction, which the macro includes from this page.
        loadPage(new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "EntryVelocityMacros"));
        loadPage(RELEASE_NOTE_CLASS);
        XWikiDocument releaseNote = new XWikiDocument(new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", PRODUCT, "8.3"), "WebHome"));
        releaseNote.setSyntax(Syntax.XWIKI_2_1);
        BaseObject releaseNoteObject = releaseNote.newXObject(RELEASE_NOTE_CLASS, this.context);
        releaseNoteObject.setStringValue("product", PRODUCT);
        releaseNoteObject.setStringValue("version", "8.3");
        releaseNoteObject.setStringValue("released", released);
        releaseNote.setContent("{{releasenotechanges limit=\"100\"/}}");
        this.xwiki.saveDocument(releaseNote, this.context);
        this.context.setDoc(releaseNote);

        renderHTMLPage(releaseNote);
        return this.redirect;
    }

    private void assertSectionQuery(int index, String expectedAudience, String expectedScreenshotClause,
        List<String> expectedImportance)
    {
        assertEquals(List.of(expectedAudience), boundValues(index, "audience"));
        assertEquals(expectedImportance, boundValues(index, "importance"));

        String statement = this.statements.get(index);
        if (ANY_SCREENSHOTS.equals(expectedScreenshotClause)) {
            assertFalse(statement.contains("changes.screenshots"),
                "Expected no screenshot filter, got: " + statement);
        } else {
            assertTrue(statement.contains(expectedScreenshotClause),
                String.format("Expected the \"%s\" filter, got: %s", expectedScreenshotClause, statement));
        }
    }

    /**
     * @param index the position of the query among the ones the rendered release note built
     * @param prefix the prefix shared by the names of the query parameters to return
     * @return the values bound to those parameters, in binding order
     */
    private List<String> boundValues(int index, String prefix)
    {
        return this.bindings.get(index).entrySet().stream()
            .filter(binding -> binding.getKey().startsWith(prefix))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }

    /**
     * Renders a release note whose body is the macro under test.
     *
     * @param limit the value of the {@code limit} macro parameter
     * @return the rendered release note
     */
    private Document renderReleaseNote(int limit) throws Exception
    {
        return renderReleaseNote("8.3", limit);
    }

    /**
     * Renders a release note whose body is the macro under test.
     *
     * @param shortVersion the name of the space holding the release note, which is where the macro reads the
     *            version it displays
     * @param limit the value of the {@code limit} macro parameter
     * @return the rendered release note
     */
    private Document renderReleaseNote(String shortVersion, int limit) throws Exception
    {
        // The version stored in the xobject is what the page name resolves to for a milestone/RC/final; the two
        // are the same here since these tests are not about the escaping of the version.
        return renderReleaseNote(shortVersion, shortVersion, limit);
    }

    /**
     * Renders a release note whose body is the macro under test, storing an arbitrary version in its xobject
     * independently of the space it is filed under, so that a version carrying markup can be exercised.
     *
     * @param shortVersion the name of the space holding the release note
     * @param version the value stored in the {@code version} field of the release note xobject
     * @param limit the value of the {@code limit} macro parameter
     * @return the rendered release note
     */
    private Document renderReleaseNote(String shortVersion, String version, int limit) throws Exception
    {
        return renderReleaseNote(shortVersion, version, PRODUCT, limit);
    }

    /**
     * Renders a release note whose body is the macro under test, storing an arbitrary product and version in its
     * xobject, so that a product carrying markup can be exercised as well.
     *
     * @param shortVersion the name of the space holding the release note
     * @param version the value stored in the {@code version} field of the release note xobject
     * @param product the value stored in the {@code product} field of the release note xobject
     * @param limit the value of the {@code limit} macro parameter
     * @return the rendered release note
     */
    private Document renderReleaseNote(String shortVersion, String version, String product, int limit)
        throws Exception
    {
        return renderReleaseNote(shortVersion, version, product, String.format("limit=\"%s\"", limit));
    }

    /**
     * Renders a release note whose body is the macro under test, called with the passed parameters.
     *
     * @param shortVersion the name of the space holding the release note
     * @param version the value stored in the {@code version} field of the release note xobject
     * @param product the value stored in the {@code product} field of the release note xobject
     * @param macroParameters the parameters of the macro call
     * @return the rendered release note
     */
    private Document renderReleaseNote(String shortVersion, String version, String product, String macroParameters)
        throws Exception
    {
        loadPage(RELEASE_NOTE_CLASS);

        XWikiDocument releaseNote = new XWikiDocument(new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", PRODUCT, shortVersion), "WebHome"));
        releaseNote.setSyntax(Syntax.XWIKI_2_1);
        BaseObject releaseNoteObject = releaseNote.newXObject(RELEASE_NOTE_CLASS, this.context);
        releaseNoteObject.setStringValue("product", product);
        releaseNoteObject.setStringValue("version", version);
        releaseNote.setContent(String.format("{{releasenotechanges %s/}}", macroParameters));
        this.xwiki.saveDocument(releaseNote, this.context);
        // The macro reads the version off the page it is on, so that page has to be the one in the context.
        this.context.setDoc(releaseNote);

        return renderHTMLPage(releaseNote);
    }

    /**
     * @return the local reference of a new migration note page of the {@code 8.3} release note, the way the search
     *         returns it
     */
    private String createMigrationNote(String entry, String title, String audience) throws Exception
    {
        DocumentReference reference =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", PRODUCT, "8.3", entry), "WebHome");
        XWikiDocument note = new XWikiDocument(reference);
        note.setSyntax(Syntax.XWIKI_2_1);
        BaseObject noteObject = note.newXObject(
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Code", "Change"), "ChangeClass"), this.context);
        noteObject.setStringValue("title", title);
        noteObject.setLargeStringValue("summary", title + " summary");
        noteObject.setStringValue("audience", audience);
        this.xwiki.saveDocument(note, this.context);

        EntityReferenceSerializer<String> localSerializer =
            this.componentManager.getInstance(EntityReferenceSerializer.TYPE_STRING, "local");

        return localSerializer.serialize(reference);
    }

    /**
     * @param count the number of rows the query must return
     * @return as many change document names as asked for, which is all the macro needs from the query in order to
     *         count them
     */
    private List<Object> changes(int count)
    {
        List<Object> changes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            changes.add(String.format("ReleaseNotes.Data.XWiki.8.3.Entry%03d.WebHome", i + 1));
        }
        return changes;
    }
}
