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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.localization.macro.internal.TranslationMacro;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.script.ModelScriptService;
import org.xwiki.rendering.internal.macro.gallery.GalleryMacro;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.script.service.ScriptService;
import org.xwiki.rendering.wikimacro.internal.WikiMacroFactoryComponentClass;
import org.xwiki.skinx.SkinExtension;
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

/**
 * Page test for the {@code displayChanges} wiki macro, defined in
 * {@code ReleaseNotes.Code.Change.DisplayChangesMacro}, and for the displayers it selects.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@WikiMacroFactoryComponentClass
// The pages under test display their strings with the translation macro, and the screenshots of a change in a
// gallery, whose references they resolve through the model script service.
@ComponentList({
    TranslationMacro.class,
    GalleryMacro.class,
    ModelScriptService.class
})
class DisplayChangesMacroPageTest extends PageTest
{
    private static final List<String> CHANGE_SPACE = List.of("ReleaseNotes", "Code", "Change");

    private static final DocumentReference DISPLAY_CHANGES_MACRO =
        new DocumentReference("xwiki", CHANGE_SPACE, "DisplayChangesMacro");

    private static final DocumentReference CHANGE_CLASS =
        new DocumentReference("xwiki", CHANGE_SPACE, "ChangeClass");

    private static final List<String> VERSION_SPACE = List.of("ReleaseNotes", "Data", "TestProduct", "8.3");

    private static final DocumentReference TEST_PAGE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data"), "TestPage");

    private static final String TITLE = "A first change";

    private static final String SUMMARY = "What the first change brings";

    private static final String MIGRATION_NOTE_SUMMARY = "Delete the Solr cache before upgrading";

    /**
     * The translation key of the label of the link towards the page of a change, which is what a page test displays
     * in place of the label itself since it registers no translation bundle.
     */
    private static final String MORE_DETAILS_KEY = "releasenotes.changes.display.moreDetails";

    /**
     * A paragraph opened inside another one, which is what a displayer emits when it lays a block into the
     * paragraph the summary of a change is rendered in.
     */
    private static final Pattern NESTED_PARAGRAPH = Pattern.compile("<p>\\s*<p>");

    /**
     * Serializes a change reference the way the getChanges query returns it, i.e. as the local reference of the page,
     * with the dots of the version space escaped.
     */
    private EntityReferenceSerializer<String> localSerializer;

    @BeforeEach
    void setUp() throws Exception
    {
        this.localSerializer = this.componentManager.getInstance(EntityReferenceSerializer.TYPE_STRING, "local");

        // A PageTest does not register $services.rendering, which the displayers escape the change title with;
        // stand one in that leaves a plain title untouched so the assertions below see it unchanged.
        this.componentManager.registerComponent(ScriptService.class, "rendering", new RenderingScriptServiceStub());
        // The gallery macro that holds the screenshots of a change pulls the skin extensions of its slide-show.
        this.componentManager.registerMockComponent(SkinExtension.class, "jsfx");
        this.componentManager.registerMockComponent(SkinExtension.class, "ssfx");

        loadPage(CHANGE_CLASS);
        for (String displayer : List.of("Simple", "List", "Grid", "Flow", "MigrationNotes")) {
            loadPage(new DocumentReference("xwiki", CHANGE_SPACE, "ChangeDisplayer" + displayer));
        }
        loadPage(new DocumentReference("xwiki", CHANGE_SPACE, "ChangeDisplayerVelocityMacros"));
        WikiMacroSetup.loadWikiMacro(this, this.componentManager, DISPLAY_CHANGES_MACRO);
    }

    /**
     * Only the default displayer is exercised by the functional tests, but every displayer is macro API and must
     * display at least the title and the summary of each change it is given.
     */
    @ParameterizedTest
    @ValueSource(strings = { "simple", "list", "grid", "flow" })
    void everyDisplayerDisplaysTheChange(String displayer) throws Exception
    {
        DocumentReference change = createChange("Entry001", TITLE, SUMMARY, "");

        String text = render(String.format("displayer=\"%s\"", displayer), change).text();

        assertTrue(text.contains(TITLE), String.format("The \"%s\" displayer dropped the title: %s", displayer, text));
        assertTrue(text.contains(SUMMARY),
            String.format("The \"%s\" displayer dropped the summary: %s", displayer, text));
    }

    /**
     * The {@code simple} displayer gives each change a section of its own, and links to the change page when the
     * change has a description that the summary alone doesn't carry.
     */
    @Test
    void simpleDisplayerGivesEachChangeItsOwnSection() throws Exception
    {
        DocumentReference first = createChange("Entry001", TITLE, SUMMARY, "The long story");
        DocumentReference second = createChange("Entry002", "A second change", "What the second change brings", "");

        Document html = render("displayer=\"simple\"", first, second);

        assertEquals(List.of(TITLE, "A second change"), html.select("h3").eachText());
        // Only the change that has a description is worth opening. A page test registers no translation bundle, so
        // the label of the link renders as its own key.
        assertEquals(List.of(MORE_DETAILS_KEY), html.select("a").eachText());
    }

    /**
     * The link towards the page of a change is one of the strings the application displays, so its label comes from
     * the translation bundle rather than being hardcoded in English.
     */
    @ParameterizedTest
    @ValueSource(strings = { "simple", "grid", "flow" })
    void theMoreDetailsLabelComesFromTheTranslationBundle(String displayer) throws Exception
    {
        DocumentReference change = createChange("Entry001", TITLE, SUMMARY, "The long story");

        Document html = render(String.format("displayer=\"%s\"", displayer), change);

        assertEquals(List.of(MORE_DETAILS_KEY), html.select("a").eachText(),
            String.format("The \"%s\" displayer does not take the label from the bundle: %s", displayer,
                html.body().html()));
    }

    /**
     * The {@code list} displayer is the one the "Miscellaneous" parts of a release note use, so it must pack each
     * change into a single list item, with its title introducing its summary.
     */
    @Test
    void listDisplayerPacksEachChangeInAListItem() throws Exception
    {
        DocumentReference first = createChange("Entry001", TITLE, SUMMARY, "The long story");
        DocumentReference second = createChange("Entry002", "", "A change with no title", "");

        Elements items = render("displayer=\"list\"", first, second).select("ul > li");

        assertEquals(2, items.size(), "Expected one list item per change.");
        assertEquals(TITLE + ": " + SUMMARY, items.get(0).text());
        // A change with no title is nothing but its summary, and must not be prefixed with a stray colon.
        assertEquals("A change with no title", items.get(1).text());
    }

    /**
     * The {@code flow} displayer lays a change out as its media next to its text, which is what its two half-width
     * columns are for. They are halves from the medium breakpoint up only: a narrower screen stacks them rather
     * than shrink the screenshot to a thumbnail.
     */
    @Test
    void flowDisplayerLaysTheMediaNextToTheText() throws Exception
    {
        DocumentReference change = createChange("Entry001", TITLE, SUMMARY, "", "a.png");

        Document html = render("displayer=\"flow\"", change);

        Elements rows = html.select("div.row");
        assertEquals(1, rows.size(), "Expected one row per change.");
        assertEquals(2, rows.get(0).select("div.col-md-6").size(),
            "Expected the media and the text to share the row.");
        assertEquals(1, rows.get(0).select("div.rn-change-media div.gallery").size(),
            "Expected the screenshots of the change in the media column: " + html.body().html());
        assertEquals(List.of(TITLE), rows.get(0).select("div.rn-change-text h3").eachText());
    }

    /**
     * A change with no screenshot must still be given a media block, so that the screenshot of the change displayed
     * next to it cannot be read as illustrating it.
     */
    @ParameterizedTest
    @ValueSource(strings = { "grid", "flow" })
    void changeWithNoScreenshotStillGetsAMediaBlock(String displayer) throws Exception
    {
        DocumentReference change = createChange("Entry001", TITLE, SUMMARY, "");

        Document html = render(String.format("displayer=\"%s\"", displayer), change);

        assertEquals(1, html.select("div.gallery").size(),
            String.format("The \"%s\" displayer left the change with no media: %s", displayer, html.body().html()));
        assertTrue(html.select("div.gallery").html().contains("no_image_thumb.png"),
            String.format("The \"%s\" displayer displayed no placeholder: %s", displayer, html.body().html()));
    }

    /**
     * The {@code grid} displayer makes each change a card of a CSS grid, and the number of columns of that grid
     * reaches the stylesheet as a custom property.
     */
    @Test
    void gridDisplayerLaysTheChangesOutInCards() throws Exception
    {
        DocumentReference first = createChange("Entry001", TITLE, SUMMARY, "");
        DocumentReference second = createChange("Entry002", "A second change", "What the second change brings", "");

        Elements grids = render("displayer=\"grid\" columns=\"3\"", first, second).select("div.rn-changes-grid");

        assertEquals(1, grids.size(), "Expected the cards to share a single grid.");
        assertTrue(grids.get(0).attr("style").contains("--rn-changes-grid-columns: 3"),
            "Expected the column count to reach the stylesheet, got: " + grids.get(0).attr("style"));
        assertEquals(2, grids.get(0).select("div.rn-change-card").size(), "Expected one card per change.");
    }

    /**
     * The displayer name becomes part of the name of the page that is included, so a name that is not a plain name
     * must fall back to the default displayer rather than include whatever it points at.
     */
    @ParameterizedTest
    @ValueSource(strings = { "", "../../XWiki/XWikiPreferences" })
    void displayerThatIsNotAPlainNameFallsBackToTheDefaultOne(String displayer) throws Exception
    {
        DocumentReference change = createChange("Entry001", TITLE, SUMMARY, "");

        Document html = render(String.format("displayer=\"%s\"", displayer), change);

        assertEquals(1, html.select("div.rn-changes-grid").size(),
            "Expected the default displayer, got: " + html.body().html());
    }

    /**
     * The macro is given the name of the variable to display, and a name that no variable matches must display the
     * same "no changes" message as an empty list, and never a leftover value.
     */
    @Test
    void unknownContextVariableDisplaysNoChanges() throws Exception
    {
        DocumentReference change = createChange("Entry001", TITLE, SUMMARY, "");

        String text = render("", "otherChangeDocs", change).text();

        assertEquals("releasenotes.changes.display.nochanges", text);
        assertFalse(text.contains(TITLE), "Expected no change at all to be displayed.");
    }

    /**
     * The change title is plain text, but the displayers place it into rendered wiki syntax, so it must be escaped
     * first: a title carrying a macro would otherwise be executed when the change is listed, with the rights of the
     * page rendering the list. Here the escaping neutralises the wiki braces, so the macro in the title stays inert.
     */
    @Test
    void theChangeTitleIsEscapedBeforeItIsRenderedAsWikiSyntax() throws Exception
    {
        // Escape the way the platform does for the braces that open and close a macro, so a title that looks like a
        // macro call renders as text rather than running.
        this.componentManager.registerComponent(ScriptService.class, "rendering",
            new RenderingScriptServiceStub(
                content -> content.replace("~", "~~").replace("{", "~{").replace("}", "~}")));

        DocumentReference change = createChange("Entry001", "{{html}}<b>injected</b>{{/html}}", SUMMARY, "");

        Document html = render("displayer=\"simple\"", change);

        assertTrue(html.select("b").isEmpty(),
            "A macro in the change title must not be executed when the change is displayed: " + html.body().html());
        assertTrue(html.text().contains("{{html}}"),
            "The title must still be displayed, as inert text: " + html.text());
    }

    /**
     * The title of a change is optional, and a change that has none is nothing but its summary. Displaying it under
     * a heading holding no text at all gives the release note an empty section, which the table of contents of the
     * release note template then lists as an empty entry.
     */
    @ParameterizedTest
    @ValueSource(strings = { "simple", "list", "grid", "flow" })
    void changeWithNoTitleGetsNoHeadingAtAll(String displayer) throws Exception
    {
        DocumentReference change = createChange("Entry001", "", SUMMARY, "");

        Document html = render(String.format("displayer=\"%s\"", displayer), change);

        assertTrue(html.select("h3").isEmpty(),
            String.format("The \"%s\" displayer gave a change with no title a heading: %s", displayer,
                html.body().html()));
        assertTrue(html.text().contains(SUMMARY), "The summary must still be displayed: " + html.body().html());
    }

    /**
     * The summary of a change is rendered into blocks of its own, so a displayer must close it before displaying
     * anything else: what follows the summary would otherwise be laid into the paragraph the summary ends with,
     * where the screenshots of the change cannot be displayed at all, the gallery holding them being a standalone
     * macro, and where the link towards the change page would nest a paragraph inside another one.
     */
    @ParameterizedTest
    @ValueSource(strings = { "simple", "grid", "flow" })
    void whatFollowsTheSummaryIsDisplayedOutsideOfIt(String displayer) throws Exception
    {
        DocumentReference change = createChange("Entry001", TITLE, SUMMARY, "The long story", "a.png");

        String html = renderHtml(String.format("displayer=\"%s\"", displayer), "changeDocs", change);
        Document document = Jsoup.parseBodyFragment(html);

        assertTrue(document.select(".xwikirenderingerror").isEmpty(),
            String.format("The \"%s\" displayer failed to display the change: %s", displayer, html));
        assertEquals(1, document.select("div.gallery").size(),
            String.format("The \"%s\" displayer dropped the screenshots of the change: %s", displayer, html));
        assertFalse(NESTED_PARAGRAPH.matcher(html).find(),
            String.format("The \"%s\" displayer left the summary sharing its paragraph: %s", displayer, html));
    }

    /**
     * Someone reading migration notes is about to upgrade and needs to know both what a note is about and what to do:
     * the migration notes displayer gives the title of the note, linking to its page, and its summary.
     */
    @Test
    void theMigrationNotesDisplayerDisplaysTheLinkedTitleAndTheSummary() throws Exception
    {
        DocumentReference note = createChange("Entry001", TITLE, MIGRATION_NOTE_SUMMARY, "");

        Document html = render("displayer=\"migrationNotes\"", note);

        assertTrue(html.select(".xwikirenderingerror").isEmpty(), html.body().html());
        Elements links = html.select("li .rn-migration-change a");
        assertEquals(1, links.size(), html.body().html());
        assertEquals(TITLE, links.text());
        assertTrue(links.attr("href").contains("Entry001"), links.attr("href"));
        assertTrue(html.text().contains(MIGRATION_NOTE_SUMMARY), html.text());
        assertFalse(html.text().contains(MORE_DETAILS_KEY), "A note with no description has no details to link to.");
    }

    /**
     * The steps of a migration can be long, so a note may keep them in its description, which its page displays and
     * which the list links to.
     */
    @Test
    void theMigrationNotesDisplayerLinksToTheDescription() throws Exception
    {
        DocumentReference note = createChange("Entry001", TITLE, MIGRATION_NOTE_SUMMARY, "The long procedure");

        Document html = render("displayer=\"migrationNotes\"", note);

        assertEquals(List.of(TITLE, MORE_DETAILS_KEY), html.select("li a").eachText(), html.body().html());
        assertFalse(html.text().contains("The long procedure"), "The description is displayed on its page only.");
    }

    /**
     * The migration notes displayer places the title of a change into a link label, which is wiki syntax, so the
     * title is escaped there as well.
     */
    @Test
    void theMigrationNotesDisplayerEscapesTheTitle() throws Exception
    {
        this.componentManager.registerComponent(ScriptService.class, "rendering",
            new RenderingScriptServiceStub(RenderingScriptServiceStub.xwikiSyntaxEscaper()));
        DocumentReference change =
            createChange("Entry001", "{{html}}<b>injected</b>{{/html}}", MIGRATION_NOTE_SUMMARY, "");

        Document html = render("displayer=\"migrationNotes\"", change);

        assertTrue(html.select("b").isEmpty(),
            "A macro in the change title must not be executed when the change is displayed: " + html.body().html());
        assertTrue(html.text().contains("{{html}}"), "The title must still be displayed, as inert text: " + html.text());
    }

    private Document render(String macroParameters, DocumentReference... changes) throws Exception
    {
        return render(macroParameters, "changeDocs", changes);
    }

    /**
     * Renders a page calling the {@code displayChanges} macro on the passed changes.
     *
     * @param macroParameters the macro parameters, besides the context variable
     * @param variable the name under which the changes are published, i.e. the {@code contextVariable} parameter
     * @param changes the changes to display
     */
    private Document render(String macroParameters, String variable, DocumentReference... changes) throws Exception
    {
        return Jsoup.parseBodyFragment(renderHtml(macroParameters, variable, changes));
    }

    /**
     * Renders the same page as {@link #render(String, String, DocumentReference...)} but returns the HTML as it
     * stands, since parsing it repairs the very nesting some of the assertions are about.
     *
     * @param macroParameters the macro parameters, besides the context variable
     * @param variable the name under which the changes are published, i.e. the {@code contextVariable} parameter
     * @param changes the changes to display
     */
    private String renderHtml(String macroParameters, String variable, DocumentReference... changes) throws Exception
    {
        String changeList = Stream.of(changes)
            .map(change -> String.format("'%s'", this.localSerializer.serialize(change)))
            .collect(Collectors.joining(", "));

        XWikiDocument page = new XWikiDocument(TEST_PAGE);
        page.setSyntax(Syntax.XWIKI_2_1);
        // The list is published under a name of the caller's choosing, the way the getChanges macro publishes it.
        page.setContent(String.format("{{velocity}}%n#set ($changeDocs = [%s])%n{{/velocity}}%n%n"
            + "{{displayChanges %s contextVariable=\"%s\"/}}", changeList, macroParameters, variable));
        this.xwiki.saveDocument(page, this.context);
        // The displayers resolve the top level space of the application from the document being rendered.
        this.context.setDoc(page);

        return page.getRenderedContent(this.context);
    }

    private DocumentReference createChange(String name, String title, String summary, String description)
        throws Exception
    {
        return createChange(name, title, summary, description, "");
    }

    private DocumentReference createChange(String name, String title, String summary, String description,
        String screenshots) throws Exception
    {
        List<String> spaces = new ArrayList<>(VERSION_SPACE);
        spaces.add(name);
        XWikiDocument change = new XWikiDocument(new DocumentReference("xwiki", spaces, "WebHome"));
        change.setSyntax(Syntax.XWIKI_2_1);
        BaseObject changeObject = change.newXObject(CHANGE_CLASS, this.context);
        changeObject.setStringValue("title", title);
        changeObject.setLargeStringValue("summary", summary);
        changeObject.setLargeStringValue("description", description);
        changeObject.setStringValue("screenshots", screenshots);
        this.xwiki.saveDocument(change, this.context);

        return change.getDocumentReference();
    }
}
