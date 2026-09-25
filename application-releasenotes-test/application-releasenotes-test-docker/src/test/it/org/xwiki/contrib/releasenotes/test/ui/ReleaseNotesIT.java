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
package org.xwiki.contrib.releasenotes.test.ui;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.livedata.test.po.LiveDataElement;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.ObjectPropertyReference;
import org.xwiki.model.reference.ObjectReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.rest.model.jaxb.Page;
import org.xwiki.rest.model.jaxb.Property;
import org.xwiki.contrib.releasenotes.test.ui.po.ChangeCardElement;
import org.xwiki.contrib.releasenotes.test.ui.po.ChangeInlinePage;
import org.xwiki.contrib.releasenotes.test.ui.po.ChangeViewPage;
import org.xwiki.contrib.releasenotes.test.ui.po.ChangesGridElement;
import org.xwiki.contrib.releasenotes.test.ui.po.PropertiesPanelElement;
import org.xwiki.contrib.releasenotes.test.ui.po.ReleaseNotePage;
import org.xwiki.contrib.releasenotes.test.ui.po.ReleaseNotesAdministrationSectionPage;
import org.xwiki.contrib.releasenotes.test.ui.po.ScrollableTableLayoutElement;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.InlinePage;
import org.xwiki.test.ui.po.SuggestInputElement;
import org.xwiki.test.ui.po.ViewPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI tests for the Release Notes Application.
 *
 * @version $Id$
 */
@UITest
class ReleaseNotesIT
{
    /**
     * The product of the two changes shared by the tests that need changes only in order to render them.
     */
    private static final String DISPLAY_PRODUCT = "DisplayProduct";

    /**
     * The product of the changes used to check how the version filters compare versions.
     */
    private static final String VERSION_PRODUCT = "VersionProduct";

    /**
     * Whether the changes of {@link #DISPLAY_PRODUCT} have already been created, so that the first of the tests
     * sharing them builds them and the next ones reuse them.
     */
    private static boolean sharedChangesCreated;

    /**
     * Walks the contributors flow of a release note holding both application macros, the way the pages created from
     * {@code ReleaseNotes.Code.ReleaseNoteTemplate} do: the macro warns while no list exists and offers an "Add
     * contributors" button, that button opens the Contributors child page in inline edit mode, and saving there
     * renders the names back on the release note, sorted ignoring case and with their wiki syntax escaped. Ends with
     * adding the first change of that version, which must be numbered Entry001 even though the version space already
     * holds a Contributors entry.
     */
    @Test
    @Order(1)
    void contributorsListAndChangeNumbering(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        String product = "ContribProduct";
        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", product, "1.0"), "WebHome");
        setup.rest().delete(releaseNote);
        DocumentReference contributorsEntry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", product, "1.0", "Contributors"), "WebHome");
        setup.rest().delete(contributorsEntry);
        // Adding a change takes the page of the new entry, so a page left by an earlier run of this test would be
        // seen as taken and the new change would be numbered Entry002.
        DocumentReference firstChange = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", product, "1.0", "Entry001"), "WebHome");
        setup.rest().delete(firstChange);

        // Both macros, in the order the shipped release note template holds them.
        setup.createPage(releaseNote, "= New and Noteworthy =\n\n{{releasenotechanges/}}\n\n= Credits =\n\n"
            + "{{releasenotecontributors/}}", "RN 1.0");
        // The changes macro only offers its "Add ... Change" forms on a note that is not released yet.
        setup.addObject(releaseNote, "ReleaseNotes.Code.ReleaseNoteClass",
            "product", product, "version", "1.0", "released", "0");

        // Before any contributors list exists, the macro shows the warning and offers the button to an editor.
        setup.gotoPage(releaseNote);
        ReleaseNotePage beforePage = new ReleaseNotePage();
        assertTrue(beforePage.getContent().contains("The list of contributors has not been generated yet."),
            "Expected the not-generated-yet warning before the contributors list exists.");

        // Click "Add contributors": lands on the Contributors page in inline edit mode. The names are typed unsorted
        // and with mixed case to exercise the case-insensitive alphabetical ordering, and one of them carries bold
        // wiki syntax to exercise the escaping.
        InlinePage contributorsEditor = beforePage.clickAddContributors();
        contributorsEditor.setValue("contributors", "bob jones\nAlice Smith\nCarol Nguyen\n**Robert Tables**");
        // Save through the page object, which waits for the asynchronous save to complete: navigating away straight
        // after a click on the save button races it and can abort the save.
        contributorsEditor.clickSaveAndView();

        // Back on the release note: every name renders, sorted alphabetically ignoring case, and the warning is gone.
        ViewPage afterPage = setup.gotoPage(releaseNote);
        String content = afterPage.getContent();
        assertTrue(content.contains("Alice Smith"), "Expected the first contributor to be rendered.");
        assertTrue(content.contains("bob jones"), "Expected the second contributor to be rendered.");
        assertTrue(content.contains("Carol Nguyen"), "Expected the third contributor to be rendered.");
        assertTrue(content.indexOf("Alice Smith") < content.indexOf("bob jones")
            && content.indexOf("bob jones") < content.indexOf("Carol Nguyen"),
            "Contributors must be sorted alphabetically ignoring case, got: " + content);
        assertFalse(content.contains("has not been generated yet"),
            "Warning must disappear once the contributors list has been saved.");
        // A name carrying bold wiki syntax: if escaped, the asterisks survive in the rendered text; if interpreted,
        // the name would render as bold and the asterisks would be gone.
        assertTrue(content.contains("**Robert Tables**"),
            "Wiki syntax in a contributor name must be escaped and rendered literally, got: " + content);

        // The Contributors page created from the macro is a technical child page: it must be hidden.
        Page savedEntry = setup.rest().get(contributorsEntry);
        assertTrue(savedEntry.isHidden(), "The Contributors child page must be created as a hidden page.");

        // Kept last because it only redirects: a Contributors entry now exists in the version space but no change
        // entry does, and "Add User Change" must still number the new change Entry001, i.e. the change-numbering
        // query must not count the Contributors entry.
        setup.gotoPage(releaseNote, "view",
            "action=useradd&template=ReleaseNotes.Code.Change.ChangeTemplate&product=" + product
                + "&version=1.0&audience=user&form_token=" + setup.getSecretToken());
        ChangeInlinePage changeEditor = new ChangeInlinePage();
        String currentUrl = changeEditor.getPageURL();
        assertTrue(currentUrl.contains("/edit/ReleaseNotes/Data/" + product + "/1.0/Entry001/WebHome"),
            "The new change must be numbered Entry001 despite the Contributors entry, landed on: " + currentUrl);
        // The redirect must go through the "edit" action and the inline editor, and not through the deprecated
        // "inline" action, which only exists in the legacy module this test's wiki does not have: with that action
        // the URL still names Entry001 but resolves to a view of a missing page in a space named "inline".
        assertTrue(currentUrl.contains("editor=inline"),
            "The new change must be opened with the inline editor, landed on: " + currentUrl);
        assertTrue(changeEditor.hasScreenshotsPicker(),
            "The redirect must land on the edit form of the new change, filled in from the change template.");

        // Saved without touching the Importance field, the change must still be displayed on its release note: the
        // sections of the note filter on importance, so a change left without one is stored and shown nowhere.
        changeEditor.setValue("title", "A change of no stated importance");
        changeEditor.clickSaveAndView();

        String noteContent = setup.gotoPage(releaseNote).getContent();
        assertTrue(noteContent.contains("A change of no stated importance"),
            "A change saved without choosing an importance must be displayed on its release note, got: "
                + noteContent);
    }

    /**
     * Checks that the configuration is reachable from the wiki Administration (thanks to the ConfigurableClass
     * xobject), with its two fields, their hints and the right administration category.
     */
    @Test
    @Order(2)
    void configureFromAdministration(TestUtils setup)
    {
        setup.loginAsSuperAdmin();

        // The section displays the fields of the configuration xobject, bound to the configuration page.
        ReleaseNotesAdministrationSectionPage section = ReleaseNotesAdministrationSectionPage.gotoSection();
        assertEquals("", section.getProduct(),
            "No product name must be shipped: the administrator has to choose one.");
        assertEquals("ReleaseNotes.Code.ReleaseNoteTemplate", section.getTemplate(),
            "Expected the default template reference to be displayed.");

        // Both fields explain what they are for.
        List<String> hints = section.getHints();
        assertEquals(2, hints.size(), "Expected a hint under each of the two configuration fields, got: " + hints);
        assertTrue(hints.stream().anyMatch(hint -> hint.startsWith("The product name pre-filled")),
            "Missing the hint for the product field, got: " + hints);
        assertTrue(hints.stream().anyMatch(hint -> hint.startsWith("The page whose title and content are copied")),
            "Missing the hint for the template field, got: " + hints);

        // The application is not bundled with XWiki Standard and thus its section belongs to the "Other" category.
        assertTrue(section.isListedInCategory("other"),
            "The administration section must be registered in the \"Other\" category.");
    }

    /**
     * Creates a release note through the home page form and checks that the configured template is applied: its
     * content and its required rights are copied over to the newly created page, which is titled after the product
     * and the version it is about.
     */
    @Test
    @Order(3)
    void createReleaseNoteFromTemplate(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "TplProduct", "9.0"), "WebHome");
        setup.rest().delete(releaseNote);

        DocumentReference template =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "ReleaseNoteTemplate");

        assertEquals("Release Note Template", setup.gotoPage(template).getDocumentTitle(),
            "The template must be titled by a plain name, since a title written in Velocity would need the script "
                + "right to display.");

        // Make the template require and enforce script right, the way a template holding scripts does.
        ObjectReference templateRight = new ObjectReference("XWiki.RequiredRightClass[0]", template);
        setup.addObject(template, "XWiki.RequiredRightClass", "level", "script");
        setEnforceRequiredRights(setup, template, true);

        try {
            // The creation form is a GET form passing the product, the version and the form token to the
            // application home page.
            setup.gotoPage("ReleaseNotes", "WebHome", "view",
                "action=addReleaseNotes&product=TplProduct&version=9.0&form_token=" + setup.getSecretToken());

            ViewPage createdPage = setup.gotoPage(releaseNote);
            assertTrue(createdPage.getContent().contains("New and Noteworthy"),
                "The content of the template must have been copied to the created release note.");
            assertTrue(createdPage.getContent().contains("Backward Compatibility and Migration Notes"),
                "The template must give a new release note a section for its migration notes.");

            // The title is written out at creation, so it displays whatever right the note's author holds.
            assertEquals("Release Notes for TplProduct 9.0", createdPage.getDocumentTitle(),
                "The created release note must be titled after the product and the version it is about.");

            Page createdRestPage = setup.rest().get(releaseNote);
            assertEquals(Boolean.TRUE, createdRestPage.isEnforceRequiredRights(),
                "The created release note must enforce required rights, like the template does.");
            Property level = setup.rest().get(new ObjectPropertyReference("level",
                new ObjectReference("XWiki.RequiredRightClass[0]", releaseNote)));
            assertEquals("script", level.getValue(),
                "The required right of the template must have been copied to the created release note.");
        } finally {
            // Leave the template as the application ships it for the other tests.
            setup.rest().delete(releaseNote);
            setup.rest().delete(templateRight);
            setEnforceRequiredRights(setup, template, true);
        }
    }

    /**
     * Checks that the two Live Data instances of the application home page list their entries: the release notes one,
     * whose properties all come from ReleaseNoteClass, and the release changes one, whose product and version columns
     * are read from EntryClass instead of from ChangeClass.
     */
    @Test
    @Order(4)
    void homeListsReleaseNotesAndChanges(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "ListProduct", "3.0"), "WebHome");
        setup.rest().delete(releaseNote);
        setup.createPage(releaseNote, "", "RN 3.0");
        setup.addObject(releaseNote, "ReleaseNotes.Code.ReleaseNoteClass",
            "product", "ListProduct", "version", "3.0", "released", "1", "date", "");

        DocumentReference change = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "ListProduct", "3.0", "Entry001"), "WebHome");
        setup.rest().delete(change);
        setup.createPage(change, "", "Listed Change");
        setup.addObject(change, "ReleaseNotes.Code.EntryClass",
            "product", "ListProduct", "type", "Change", "version", "3.0");
        setup.addObject(change, "ReleaseNotes.Code.Change.ChangeClass",
            "title", "Listed Change", "summary", "Listed change summary", "audience", "user", "importance", "2",
            "category", "development");

        setup.gotoPage(new DocumentReference("xwiki", List.of("ReleaseNotes", "Data"), "WebHome"));

        // The release notes list resolves its columns from ReleaseNoteClass.
        TableLayoutElement releaseNotes = new LiveDataElement("releasenotes").getTableLayout();
        releaseNotes.waitUntilReady();
        releaseNotes.filterColumn("Version", "3.0");
        releaseNotes.waitUntilRowCountEqualsTo(1);
        releaseNotes.assertRow("Product", "ListProduct");
        releaseNotes.assertRow("Version", "3.0");

        // The changes list reads product and version from EntryClass, which only works if the product_class and
        // version_class source parameters reach the results page.
        LiveDataElement changesLiveData = new LiveDataElement("releasenoteschanges");
        ScrollableTableLayoutElement changes = new ScrollableTableLayoutElement(changesLiveData);
        changes.waitUntilReady();
        changes.filterColumn("Title", "Listed Change");
        changes.waitUntilRowCountEqualsTo(1);
        changes.assertRow("Product", "ListProduct");
        changes.assertRow("Version", "3.0");
        // The title is the column that links to the change, and it keeps that link now that the creation date, which
        // used to carry it, is not displayed. The link of a non terminal page is the URL of its space.
        changes.assertCellWithLink("Title", "Listed Change", setup.getURL(change.getLastSpaceReference()));

        // The displayed columns are only the ones that fit the width of a page: the creation date and the free text
        // summary are hidden, since together they make the table wider than the content area of a standard page.
        assertFalse(changes.hasColumn("Summary"), "Summary must not be one of the displayed columns.");
        assertFalse(changes.hasColumn("Created"), "Created must not be one of the displayed columns.");

        // A column past the right edge of the table layout is simply invisible, since nothing indicates that the
        // layout scrolls sideways. The displayed columns must therefore fit their container.
        assertEquals(0L, changes.getHorizontalOverflow(), "The changes table must fit the width of the page.");

        // The two hidden columns are hidden, not dropped: the Properties panel offers exactly the properties the
        // macro declares, so they would be unreachable had they been left out of that list. They must be offered
        // there, unticked, and ticking one must display its column back.
        PropertiesPanelElement properties = PropertiesPanelElement.open(changesLiveData);
        assertTrue(properties.hasProperty("Summary"), "Summary must be offered by the properties panel.");
        assertTrue(properties.hasProperty("Created"), "Created must be offered by the properties panel.");
        assertFalse(properties.isPropertyDisplayed("Summary"), "Summary must be offered unticked.");
        assertFalse(properties.isPropertyDisplayed("Created"), "Created must be offered unticked.");
        assertTrue(properties.isPropertyDisplayed("Title"), "Title must be offered ticked.");

        properties.setPropertyDisplayed("Summary", true);
        properties.closePanel();
        assertTrue(changes.hasColumn("Summary"), "Ticking Summary must display its column.");
        changes.assertRow("Summary", "Listed change summary");
    }

    /**
     * The grid displayer renders each change as one card holding its title, then its media, then its summary, so that
     * a screenshot can only be read as illustrating the change it is enclosed with. A card holds a single medium, so
     * a change carrying several videos and no screenshot is displayed with its first video only: referencing more
     * videos cannot make one card taller than the card beside it.
     */
    @Test
    @Order(5)
    void gridDisplayerRendersEachChangeAsACard(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();
        createSharedChanges(setup);

        DocumentReference page =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", DISPLAY_PRODUCT), "WebHome");
        setup.rest().delete(page);
        setup.createPage(page,
            String.format("{{getChanges products=\"%s\" versions=\"1.0\" contextVariable=\"changeDocs\"/}}%n%n"
                + "{{displayChanges contextVariable=\"changeDocs\" displayer=\"grid\"/}}", DISPLAY_PRODUCT),
            "Grid page");
        setup.gotoPage(page);

        ChangesGridElement grid = new ChangesGridElement();
        List<ChangeCardElement> cards = grid.getCards();
        assertEquals(2, cards.size(), "Each change must be rendered as its own card.");

        // The card is the enclosure: a title and a medium belong to the same change because they are inside it, so
        // every card carries the title of its own change, above its own media.
        for (ChangeCardElement card : cards) {
            assertTrue(List.of("A grid change", "A videos change").contains(card.getTitle()),
                "A card must be titled after the change it displays, got: " + card.getTitle());
            assertTrue(card.isMediaBelowTitle(),
                "The media must be displayed after the title, in the card of: " + card.getTitle());
        }

        // Only one of the two changes carries videos, and its card displays the first of them alone.
        List<String> videos = grid.getVideoSources();
        assertEquals(1, videos.size(), "Only the first video of a change must be displayed.");
        assertTrue(videos.get(0).contains("video1.mp4"),
            "The displayed video must be the first one, got: " + videos.get(0));
    }

    /**
     * The displayer name is turned into the name of the page that renders the changes, so only a plain name selects
     * a displayer; anything else falls back to the default one instead of taking part in the page name.
     */
    @Test
    @Order(6)
    void unknownDisplayerNameFallsBackToTheDefaultDisplayer(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();
        createSharedChanges(setup);

        // A name that is not a plain name: were it used as-is it would resolve outside the Code.Change space,
        // where the displayer pages of this macro live.
        String notAName = "Foo.Bar";
        DocumentReference page = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", DISPLAY_PRODUCT, "FallbackDisplayer"), "WebHome");
        setup.rest().delete(page);
        setup.createPage(page,
            String.format("{{getChanges products=\"%s\" versions=\"1.0\" contextVariable=\"changeDocs\"/}}%n%n"
                + "{{displayChanges contextVariable=\"changeDocs\" displayer=\"%s\"/}}", DISPLAY_PRODUCT, notAName),
            "Displayer page");

        ViewPage viewPage = setup.gotoPage(page);
        assertTrue(viewPage.getContent().contains("A grid change"),
            "The changes must still be rendered, by the default displayer.");
        assertFalse(viewPage.getContent().contains("ChangeDisplayerFoo"),
            "The displayer name must not contribute anything to the rendered page.");
    }

    /**
     * The report page forwards the filter parameters it knows about to the macros that build the report, and each
     * value stays inside the macro parameter it is given to.
     */
    @Test
    @Order(7)
    void reportForwardsOnlyItsOwnFilterParameters(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();
        // The report takes the product and the version from the request, so it needs no fixture of its own.
        createSharedChanges(setup);

        // "columns" is a displayChanges parameter, but not one the report form submits. It must not reach the
        // macros just because the request happens to carry it: the changes must keep the default layout.
        Map<String, String> queryParameters = new LinkedHashMap<>();
        queryParameters.put("action", "report");
        queryParameters.put("products", DISPLAY_PRODUCT);
        queryParameters.put("versions", "1.0");
        queryParameters.put("columns", "1");
        setup.gotoPage(new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "Report"), "view",
            queryParameters);

        ViewPage reportPage = new ViewPage();
        assertTrue(reportPage.getContent().contains("A grid change"),
            "The report must render the changes, otherwise the layout below proves nothing.");
        // The grid displayer publishes its column count to its stylesheet as a custom property, so the default of
        // 2 columns is what must be found there rather than the 1 carried by the request.
        assertEquals(2, new ChangesGridElement().getColumnCount(),
            "The changes must keep the default column layout of the displayer.");
    }

    /**
     * Creates, once for the whole class, the two changes shared by the tests that need changes only in order to
     * render them: one carrying a screenshot, and one carrying two videos and no screenshot, so that the same grid
     * holds both a card whose medium is a gallery and a card whose medium is a video. Called by each of those tests
     * rather than from a {@code @BeforeAll} method, since {@link TestUtils} is injected per test method and since
     * each of them must also be runnable on its own.
     */
    private void createSharedChanges(TestUtils setup) throws Exception
    {
        if (sharedChangesCreated) {
            return;
        }

        DocumentReference screenshotChange = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", DISPLAY_PRODUCT, "1.0", "Entry001"), "WebHome");
        setup.rest().delete(screenshotChange);
        setup.createPage(screenshotChange, "", "A grid change");
        setup.attachFile(screenshotChange, "screenshot.png", getClass().getResourceAsStream("/screenshot.png"), false);
        setup.addObject(screenshotChange, "ReleaseNotes.Code.EntryClass",
            "product", DISPLAY_PRODUCT, "type", "Change", "version", "1.0");
        setup.addObject(screenshotChange, "ReleaseNotes.Code.Change.ChangeClass",
            "title", "A grid change", "summary", "A grid change summary", "audience", "user", "importance", "1",
            "category", "development", "screenshots", "screenshot.png");

        DocumentReference videoChange = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", DISPLAY_PRODUCT, "1.0", "Entry002"), "WebHome");
        setup.rest().delete(videoChange);
        setup.createPage(videoChange, "", "A videos change");
        setup.attachFile(videoChange, "video1.mp4", new ByteArrayInputStream(new byte[] {1}), false);
        setup.attachFile(videoChange, "video2.mp4", new ByteArrayInputStream(new byte[] {2}), false);
        setup.addObject(videoChange, "ReleaseNotes.Code.EntryClass",
            "product", DISPLAY_PRODUCT, "type", "Change", "version", "1.0");
        setup.addObject(videoChange, "ReleaseNotes.Code.Change.ChangeClass",
            "title", "A videos change", "summary", "A videos change summary", "audience", "user", "importance", "1",
            "category", "development", "screenshots", "video1.mp4,video2.mp4");

        sharedChangesCreated = true;
    }

    /**
     * The Screenshots field used to be a plain text input in which the author had to type, by hand, the exact name of
     * an attachment uploaded beforehand from the Attachments tab. It is now an attachment picker: it suggests the
     * media attached to the change, accepts several of them, and can upload new ones without leaving the form. Since
     * the picker is a multiple SELECT while the property is a String (of which XWiki only keeps the first submitted
     * value), what actually gets saved is a hidden input that the application keeps in sync with the picker. This test
     * covers that round trip, plus the link out to the change page that the form now offers.
     */
    @Test
    @Order(8)
    void screenshotsAreEditedThroughAnAttachmentPicker(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        String product = "PickerProduct";
        DocumentReference entry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", product, "1.0", "Entry001"), "WebHome");
        setup.rest().delete(entry);
        setup.createPage(entry, "", "An illustrated change");
        setup.addObject(entry, "ReleaseNotes.Code.EntryClass",
            "product", product, "type", "Change", "version", "1.0");
        setup.addObject(entry, "ReleaseNotes.Code.Change.ChangeClass",
            "title", "An illustrated change", "summary", "An illustrated change summary", "audience", "user",
            "importance", "1", "category", "development", "screenshots", "first.png");
        setup.attachFile(entry, "first.png", getClass().getResourceAsStream("/screenshot.png"), false);
        setup.attachFile(entry, "second.png", getClass().getResourceAsStream("/screenshot.png"), false);

        setup.gotoPage(entry, "edit", "editor=inline");
        ChangeInlinePage changeEditor = new ChangeInlinePage();

        // The form is reached from the release note and used to be a dead end towards the change's own page.
        assertTrue(changeEditor.hasViewChangeLink(),
            "The edit form must offer a link to view the change page.");

        assertEquals(List.of("first.png"), changeEditor.getScreenshotsPicker().getValues(),
            "The media already stored must be preselected in the picker.");

        // The attachments of the change are the suggestions offered without typing anything.
        SuggestInputElement picker = changeEditor.openScreenshotSuggestions();
        picker.selectByValue("second.png");
        picker.hideSuggestions();

        // Save through the page object, which waits for the asynchronous save to complete: reading the saved value
        // straight after a click on the button races it, and reads back the value from before the save.
        changeEditor.clickSaveAndView();

        Property screenshots = setup.rest().get(new ObjectPropertyReference("screenshots",
            new ObjectReference("ReleaseNotes.Code.Change.ChangeClass[0]", entry)));
        assertEquals("first.png,second.png", screenshots.getValue(),
            "The picker must save the comma separated list the release note displayers read back, not just its "
                + "first value.");

        // Reopening the form must show both media, which is what closes the loop: the value the picker saved is a
        // value the picker itself reads back.
        setup.gotoPage(entry, "edit", "editor=inline");
        assertEquals(List.of("first.png", "second.png"),
            new ChangeInlinePage().getScreenshotsPicker().getValues());

        // The saved value must also still be understood by the displayers, which render the media of a change.
        setup.gotoPage(entry);
        assertTrue(new ChangeViewPage().hasScreenshot("first.png"),
            "The change page must display the screenshots the picker saved.");
    }

    /**
     * A version is stored as text, so a comparison filter that the database evaluated would compare versions
     * alphabetically and quietly drop 10.0 from a {@code >=9.0} report. This test goes through the whole chain -
     * generated XWQL, database, rendering - which is what the page tests cannot do.
     */
    @Test
    @Order(9)
    void versionComparisonFiltersCompareVersionsNotStrings(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();
        createVersionChange(setup, "9.0", "A nine change");
        createVersionChange(setup, "10.0", "A ten change");

        String content = reportContent(setup, ">=9.0");
        assertTrue(content.contains("A nine change"), "The boundary version must be part of a \">=\" report, got: "
            + content);
        assertTrue(content.contains("A ten change"),
            "10.0 comes after 9.0 as a version, and must not be dropped because it comes before it as a string, "
                + "got: " + content);

        content = reportContent(setup, "<10.0");
        assertTrue(content.contains("A nine change"), "9.0 comes before 10.0 as a version, even though it comes "
            + "after it as a string, got: " + content);
        assertFalse(content.contains("A ten change"),
            "The boundary version must be left out of a \"<\" report, got: " + content);

        // A comparison that no existing version matches must render an empty report rather than build a query the
        // database rejects.
        content = reportContent(setup, ">=99.0");
        assertFalse(content.contains("A nine change"), "Got: " + content);
        assertFalse(content.contains("A ten change"), "Got: " + content);
        assertFalse(content.contains("Failed to execute"),
            "A filter matching no version must still produce a valid query, got: " + content);

        // The upgrade notes list the migration notes of the versions an upgrade goes through, in the order the
        // versions are released: 9.0 before 10.0, which the alphabetical order would swap.
        content = upgradeNotesContent(setup, "8.0", "10.0");
        assertTrue(content.contains("A nine change migration notes"), "Got: " + content);
        assertTrue(content.contains("A ten change migration notes"), "Got: " + content);
        assertTrue(content.indexOf("A nine change") < content.indexOf("A ten change"),
            "The notes of 9.0 must come before the ones of 10.0, got: " + content);
        // Only the migration notes are listed, and not the changes the versions bring.
        assertFalse(content.contains("A nine change summary"), "Got: " + content);

        // The version upgraded from is already installed, so its notes are not part of the upgrade.
        content = upgradeNotesContent(setup, "9.0", "10.0");
        assertFalse(content.contains("A nine change"), "Got: " + content);
        assertTrue(content.contains("A ten change migration notes"), "Got: " + content);
    }

    /**
     * @return the rendered content of the upgrade notes of {@link #VERSION_PRODUCT} between the passed versions
     */
    private String upgradeNotesContent(TestUtils setup, String from, String to)
    {
        Map<String, String> queryParameters = new LinkedHashMap<>();
        queryParameters.put("product", VERSION_PRODUCT);
        queryParameters.put("from", from);
        queryParameters.put("to", to);
        setup.gotoPage(new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "UpgradeNotes"), "view",
            queryParameters);
        return new ViewPage().getContent();
    }

    /**
     * Creates a release note of {@link #VERSION_PRODUCT} for the passed version holding one change, the way the
     * application does: the version page carries the release note, and its child entry carries the change.
     */
    private void createVersionChange(TestUtils setup, String version, String title) throws Exception
    {
        DocumentReference releaseNote = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", VERSION_PRODUCT, version), "WebHome");
        setup.rest().delete(releaseNote);
        setup.createPage(releaseNote, "", version);
        // The version filters are resolved against the versions the release notes declare, so the release note page
        // is part of the fixture and not just decoration around the entry.
        setup.addObject(releaseNote, "ReleaseNotes.Code.ReleaseNoteClass",
            "product", VERSION_PRODUCT, "version", version, "released", "1");

        DocumentReference entry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", VERSION_PRODUCT, version, "Entry001"), "WebHome");
        setup.rest().delete(entry);
        setup.createPage(entry, "", title);
        setup.addObject(entry, "ReleaseNotes.Code.EntryClass",
            "product", VERSION_PRODUCT, "type", "Change", "version", version);
        setup.addObject(entry, "ReleaseNotes.Code.Change.ChangeClass",
            "title", title, "summary", title + " summary", "audience", "user", "importance", "1",
            "category", "development");

        // A migration note is an entry of its own, of the migration type, which the reports of the changes and the
        // upgrade notes tell apart from the change by that type only.
        DocumentReference migrationNote = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", VERSION_PRODUCT, version, "Entry002"), "WebHome");
        setup.rest().delete(migrationNote);
        setup.createPage(migrationNote, "", title + " migration");
        setup.addObject(migrationNote, "ReleaseNotes.Code.EntryClass",
            "product", VERSION_PRODUCT, "type", "Migration", "version", version);
        // An object added this way only holds the properties it is given, and the changes are ordered on their
        // importance, which leaves out of every search a change holding none: the note is given one, as the change
        // template gives one to every change the application creates.
        setup.addObject(migrationNote, "ReleaseNotes.Code.Change.ChangeClass",
            "title", title + " migration", "description", title + " migration notes", "audience", "administrator",
            "importance", "1");
    }

    /**
     * @return the rendered content of the report of {@link #VERSION_PRODUCT} filtered by the passed versions filter
     */
    private String reportContent(TestUtils setup, String versions)
    {
        Map<String, String> queryParameters = new LinkedHashMap<>();
        queryParameters.put("action", "report");
        queryParameters.put("products", VERSION_PRODUCT);
        queryParameters.put("versions", versions);
        setup.gotoPage(new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "Report"), "view",
            queryParameters);
        return new ViewPage().getContent();
    }

    /**
     * Turns the enforcement of required rights on or off for the passed page, which the REST API exposes as a page
     * field rather than as an xobject property.
     */
    private void setEnforceRequiredRights(TestUtils setup, DocumentReference reference, boolean enforce)
        throws Exception
    {
        Page page = setup.rest().get(reference);
        page.setEnforceRequiredRights(enforce);
        // The REST API fills Page#title with the rendered title and stores whatever it is given back as the raw
        // title. A null title leaves the stored one alone.
        page.setTitle(null);
        setup.rest().save(page);
    }

    /**
     * The summary and the description of a change are rich text, written by whoever authors the change, which the
     * application lets any contributor do without granting them the Script right. Those fields are displayed
     * through the change document, so that they are rendered with the rights of the change author rather than with
     * the rights of the page that lists or displays the change: a macro a non-Script author writes there is thus
     * rendered inert, both where the change is listed (a release note, through the displayer) and on the change's
     * own page (through its sheet).
     */
    @Test
    @Order(10)
    void changeFieldsAreDisplayedWithTheChangeAuthorRights(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        String product = "InjectionProduct";
        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", product, "1.0"), "WebHome");
        DocumentReference change = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", product, "1.0", "Entry001"), "WebHome");
        DocumentReference contributor = new DocumentReference("xwiki", "XWiki", "RnContributor");
        setup.rest().delete(change);
        setup.rest().delete(releaseNote);
        setup.rest().delete(contributor);

        // The release note and the displayers it uses are set up by an administrator, i.e. with the Script right
        // that the change author below lacks.
        setup.createPage(releaseNote, "= New and Noteworthy =\n\n{{releasenotechanges/}}", "RN 1.0");
        setup.addObject(releaseNote, "ReleaseNotes.Code.ReleaseNoteClass",
            "product", product, "version", "1.0", "released", "0");

        // A contributor who may edit the version space but has no Script right, i.e. an ordinary registered user.
        setup.createUser("RnContributor", "rncontributorpass", "");
        setup.setRightsOnSpace(releaseNote.getLastSpaceReference(), "XWiki.RnContributor", "", "edit", true);

        // The change is authored by that contributor, logged in as themselves so that the change document's content
        // author is the contributor and not the administrator who set up the release note (createPage and addObject
        // save through the browser, i.e. as the logged-in user). Its summary and description each carry an html
        // macro that, rendered with the displaying page's rights, would inject an active event handler into every
        // viewer's page.
        String summary = "SUMMARYMARK "
            + "{{html clean=\"false\"}}<img src=\"x\" onerror=\"window.__rnInjected = 1\"/>{{/html}}";
        String description = "DESCRIPTIONMARK "
            + "{{html clean=\"false\"}}<b onmouseover=\"window.__rnInjected = 2\">boom</b>{{/html}}";
        setup.login("RnContributor", "rncontributorpass");
        setup.createPage(change, "", "An injected change");
        setup.addObject(change, "ReleaseNotes.Code.EntryClass",
            "product", product, "type", "Change", "version", "1.0");
        setup.addObject(change, "ReleaseNotes.Code.Change.ChangeClass",
            "title", "Injected change", "audience", "user", "importance", "1", "category", "development",
            "summary", summary, "description", description);

        // Back to an administrator, i.e. a viewer holding the Script right the change author lacks, to read the note.
        setup.loginAsSuperAdmin();

        // The release note lists the change through the list displayer (the change carries no screenshot), so its
        // summary is rendered there.
        setup.gotoPage(releaseNote);
        assertFieldRenderedInert(new ReleaseNotePage().getContentHtml(), "SUMMARYMARK");

        // The change's own page renders both the summary and the description through the change sheet.
        setup.gotoPage(change);
        String changeContent = new ChangeViewPage().getContentHtml();
        assertFieldRenderedInert(changeContent, "SUMMARYMARK");
        assertFieldRenderedInert(changeContent, "DESCRIPTIONMARK");

        setup.rest().delete(change);
        setup.rest().delete(releaseNote);
        setup.rest().delete(contributor);
    }

    /**
     * Checks that authoring a release note takes no more than the edit right: the note is titled after its product
     * and its version rather than by a script, and the macros of the template it is made from render on it even
     * though it enforces required rights and declares none. A note whose author holds only the edit right would
     * otherwise display the title it was given as source.
     */
    @Test
    @Order(11)
    void aReleaseNoteIsAuthoredWithTheEditRightAlone(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        String product = "NoScriptProduct";
        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", product, "7.0"), "WebHome");
        DocumentReference author = new DocumentReference("xwiki", "XWiki", "RnAuthor");
        SpaceReference dataSpace =
            new SpaceReference("Data", new SpaceReference("ReleaseNotes", new WikiReference("xwiki")));
        DocumentReference dataPreferences = new DocumentReference("WebPreferences", dataSpace);
        setup.rest().delete(releaseNote);
        setup.rest().delete(author);

        // An ordinary registered user, holding the edit right on the space the release notes are stored in and no
        // Script right anywhere.
        setup.createUser("RnAuthor", "rnauthorpass", "");
        setup.setRightsOnSpace(dataSpace, "XWiki.RnAuthor", "", "edit", true);

        try {
            setup.login("RnAuthor", "rnauthorpass");

            // The creation form is a GET form passing the product, the version and the form token to the
            // application home page.
            setup.gotoPage("ReleaseNotes", "WebHome", "view",
                "action=addReleaseNotes&product=" + product + "&version=7.0&form_token=" + setup.getSecretToken());

            ViewPage createdPage = setup.gotoPage(releaseNote);
            assertEquals("Release Notes for " + product + " 7.0", createdPage.getDocumentTitle(),
                "A release note must display its title whatever right its author holds.");

            String content = createdPage.getContent();
            assertTrue(content.contains("New and Noteworthy"),
                "The content of the template must have been copied to the created release note.");
            assertFalse(content.contains("Unknown macro"),
                "The macros of the release note must render on a note declaring no required right.");
            assertFalse(content.contains("$doc"),
                "The release note must hold no Velocity of its own.");
        } finally {
            setup.loginAsSuperAdmin();
            setup.rest().delete(releaseNote);
            setup.rest().delete(author);
            // The rule granted above allows and is therefore exclusive: left behind, it would deny the edit right on
            // the release notes to everybody else.
            setup.rest().delete(dataPreferences);
        }
    }

    /**
     * Asserts that the rendered content holds the given marker, i.e. the field it identifies is displayed, but no
     * inline event handler, i.e. the macro the field also holds was rendered inert rather than executed.
     *
     * @param content the rendered content of the page displaying the field, as HTML
     * @param marker the plain-text marker the field carries, to make sure the field itself is displayed
     */
    private void assertFieldRenderedInert(String content, String marker)
    {
        assertTrue(content.contains(marker),
            "Expected the change field carrying '" + marker + "' to be displayed, got: " + content);
        assertFalse(content.contains("onerror") || content.contains("onmouseover"),
            "A macro written by a non-Script change author must be rendered inert, got: " + content);
    }
}
