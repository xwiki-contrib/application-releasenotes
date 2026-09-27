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
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.edit.EditConfiguration;
import org.xwiki.edit.internal.DefaultEditorDescriptorBuilder;
import org.xwiki.edit.internal.DefaultEditorManager;
import org.xwiki.edit.internal.TextSyntaxContentEditor;
import org.xwiki.localization.macro.internal.TranslationMacro;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Page test for {@code ReleaseNotes.Code.Change.ChangeSheet}.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
// The textarea properties of a change are edited through the editor components, and the sheet displays its
// conventions with the translation macro.
@ComponentList({
    DefaultEditorManager.class,
    DefaultEditorDescriptorBuilder.class,
    TextSyntaxContentEditor.class,
    TranslationMacro.class
})
class ChangeSheetPageTest extends PageTest
{
    private static final List<String> CODE_SPACE = List.of("ReleaseNotes", "Code");

    private static final List<String> CHANGE_SPACE = List.of("ReleaseNotes", "Code", "Change");

    private static final DocumentReference ENTRY_CLASS = new DocumentReference("xwiki", CODE_SPACE, "EntryClass");

    private static final DocumentReference CHANGE_CLASS = new DocumentReference("xwiki", CHANGE_SPACE, "ChangeClass");

    private static final DocumentReference CHANGE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "XWiki", "8.3M1", "Entry001"), "WebHome");

    @BeforeEach
    void setUp() throws Exception
    {
        // The textarea properties ask for the configured default editor before rendering themselves.
        this.componentManager.registerMockComponent(EditConfiguration.class);

        loadPage(ENTRY_CLASS);
        loadPage(CHANGE_CLASS);
        loadPage(new DocumentReference("xwiki", CODE_SPACE, "EntryVelocityMacros"));
        loadPage(new DocumentReference("xwiki", CHANGE_SPACE, "ChangeDisplayerVelocityMacros"));
        loadPage(new DocumentReference("xwiki", CHANGE_SPACE, "MigrationNoteEditor"));
        loadPage(new DocumentReference("xwiki", CHANGE_SPACE, "ChangeSheet"));
    }

    /**
     * The sheet lays its fields out as a definition list. A term is only text, so it gives the input it sits next to
     * no accessible name and does not move the focus into it when clicked: each term must hold a label bound to the
     * identifier that XWiki generates for the field.
     */
    @Test
    void everyEditedFieldIsNamedByALabelBoundToIt() throws Exception
    {
        Document html = renderChangeInEditMode();

        Elements labels = html.select("dt label");
        assertEquals(9, labels.size(), "Expected a label for each of the nine edited fields.");
        for (Element label : labels) {
            String target = label.attr("for");
            assertFalse(target.isEmpty(), "The '" + label.text() + "' label is bound to no field.");
            assertEquals(1, html.select("#" + escapeCssIdentifier(target)).size(),
                "Expected exactly one field with the '" + target + "' identifier, named by the '" + label.text()
                    + "' label.");
        }
    }

    /**
     * The screenshots displayer names its picker apart from the property, the property value itself being carried by
     * a hidden input. The label must point at the picker, which is the control the author interacts with, and not at
     * the hidden input, which no one can focus.
     */
    @Test
    void screenshotsLabelIsBoundToThePickerAndNotToTheHiddenValue() throws Exception
    {
        Document html = renderChangeInEditMode();

        String pickerId = "ReleaseNotes.Code.Change.ChangeClass_0_screenshots_picker";
        Elements labels = html.select("dt label[for='" + pickerId + "']");
        assertEquals(1, labels.size(), "Expected the screenshots label to be bound to the picker.");
        assertEquals("select", html.select("#" + escapeCssIdentifier(pickerId)).get(0).tagName());
    }

    /**
     * The form names each field with the pretty name of the class field itself, and not with a label of its own: a
     * label declared here is a label free to end up saying one thing on this form and another one in the object
     * editor or in the Live Data of the home page.
     */
    @Test
    void everyEditedFieldIsNamedByTheLabelOfItsClassField() throws Exception
    {
        assertEquals(List.of(
            "ReleaseNotes.Code.EntryClass_product",
            "ReleaseNotes.Code.EntryClass_version",
            "ReleaseNotes.Code.Change.ChangeClass_title",
            "ReleaseNotes.Code.Change.ChangeClass_audience",
            "ReleaseNotes.Code.Change.ChangeClass_category",
            "ReleaseNotes.Code.Change.ChangeClass_importance",
            "ReleaseNotes.Code.Change.ChangeClass_summary",
            "ReleaseNotes.Code.Change.ChangeClass_screenshots",
            "ReleaseNotes.Code.Change.ChangeClass_description"),
            renderChangeInEditMode().select("dt label").eachText());
    }

    /**
     * The conventions the form states are prose an author reads, so they go through the translation bundle like the
     * rest of the form.
     */
    @Test
    void theConventionsOfTheFormAreTranslated() throws Exception
    {
        assertEquals(List.of(
            "releasenotes.change.conventions.noDeveloperScreenshots",
            "releasenotes.change.conventions.userMiscellaneous",
            "releasenotes.change.conventions.developerMiscellaneous",
            "releasenotes.change.conventions.optionalTitle"),
            renderChangeInEditMode().select("ul li").eachText());
    }

    /**
     * A migration note is added from its own button, which asks the form for a migration note entry: the form carries
     * that type along, since saving it only stores the fields it holds, and the entry would otherwise be saved as the
     * plain change its template makes it.
     */
    @Test
    void theTypeTheEntryWasAddedWithIsCarriedByTheForm() throws Exception
    {
        this.request.put("type", "Migration");

        Elements typeInputs =
            renderChangeInEditMode().select("input[type=hidden][name=ReleaseNotes.Code.EntryClass_0_type]");

        assertEquals(List.of("Migration"), typeInputs.eachAttr("value"));
    }

    /**
     * A migration note is only a title and a description: its form holds nothing else an author fills, and carries
     * the product, the version and the type of its entry along so that saving it stores them.
     */
    @Test
    void aMigrationNoteIsEditedWithItsTitleAndItsDescriptionOnly() throws Exception
    {
        this.request.put("type", "Migration");
        this.request.put("product", "XWiki");
        this.request.put("version", "8.3-milestone-1");

        Document html = renderChangeInEditMode();

        assertEquals(List.of(
            "ReleaseNotes.Code.Change.ChangeClass_title",
            "ReleaseNotes.Code.Change.ChangeClass_description"),
            html.select("dt label").eachText());
        assertEquals(List.of("XWiki"),
            html.select("input[type=hidden][name=ReleaseNotes.Code.EntryClass_0_product]").eachAttr("value"));
        assertEquals(List.of("8.3-milestone-1"),
            html.select("input[type=hidden][name=ReleaseNotes.Code.EntryClass_0_version]").eachAttr("value"));
        assertTrue(html.select("ul li").isEmpty(), "The conventions of a change do not apply to a migration note.");
    }

    private Document renderChangeInEditMode() throws Exception
    {
        XWikiDocument change = createChange();
        this.xwiki.saveDocument(change, this.context);
        this.context.setDoc(change);
        this.context.setAction("edit");

        return renderHTMLPage(change);
    }

    private XWikiDocument createChange() throws Exception
    {
        XWikiDocument change = new XWikiDocument(CHANGE);
        change.newXObject(ENTRY_CLASS, this.context);
        change.newXObject(CHANGE_CLASS, this.context);
        // The sheet is included rather than applied so that the change stays the current document, which is what the
        // sheet mechanism does and what the sheet relies on to find its objects.
        change.setContent("{{include reference=\"ReleaseNotes.Code.Change.ChangeSheet\" context=\"current\"/}}");

        return change;
    }

    /**
     * The generated field identifiers hold the dots of the class reference, which a CSS identifier selector reads as
     * class names.
     */
    private String escapeCssIdentifier(String identifier)
    {
        return identifier.replace(".", "\\.");
    }
}
