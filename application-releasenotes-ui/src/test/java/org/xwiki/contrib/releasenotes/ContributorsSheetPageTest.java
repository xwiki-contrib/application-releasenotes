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
import org.xwiki.edit.EditConfiguration;
import org.xwiki.edit.internal.DefaultEditorDescriptorBuilder;
import org.xwiki.edit.internal.DefaultEditorManager;
import org.xwiki.edit.internal.TextSyntaxContentEditor;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Page test for {@code ReleaseNotes.Code.ContributorsSheet} in edit mode.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
// The contributors list is a textarea property, edited through the editor components.
@ComponentList({
    DefaultEditorManager.class,
    DefaultEditorDescriptorBuilder.class,
    TextSyntaxContentEditor.class
})
class ContributorsSheetPageTest extends PageTest
{
    private static final List<String> CODE_SPACE = List.of("ReleaseNotes", "Code");

    private static final DocumentReference CONTRIBUTORS_CLASS =
        new DocumentReference("xwiki", CODE_SPACE, "ContributorsClass");

    private static final DocumentReference CONTRIBUTORS =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "XWiki", "8.3M1", "Contributors"), "WebHome");

    private static final DocumentReference ENTRY_CLASS = new DocumentReference("xwiki", CODE_SPACE, "EntryClass");

    @BeforeEach
    void setUp() throws Exception
    {
        this.componentManager.registerMockComponent(EditConfiguration.class);

        loadPage(ENTRY_CLASS);
        loadPage(CONTRIBUTORS_CLASS);
        loadPage(new DocumentReference("xwiki", CODE_SPACE, "EntryVelocityMacros"));
        loadPage(new DocumentReference("xwiki", CODE_SPACE, "ContributorsSheet"));
    }

    /**
     * The sheet lays its single field out as a definition list. A term is only text, so it gives the textarea it sits
     * next to no accessible name and does not move the focus into it when clicked: the term has to hold a label bound
     * to the identifier that XWiki generates for the field.
     */
    @Test
    void theEditedFieldIsNamedByALabelBoundToIt() throws Exception
    {
        XWikiDocument contributors = new XWikiDocument(CONTRIBUTORS);
        contributors.newXObject(CONTRIBUTORS_CLASS, this.context);
        // The sheet is included with the contributors entry as the current document, which is what the sheet
        // mechanism does and what the sheet relies on to find its objects.
        contributors.setContent("{{include reference=\"ReleaseNotes.Code.ContributorsSheet\" context=\"current\"/}}");
        this.xwiki.saveDocument(contributors, this.context);
        this.context.setDoc(contributors);
        this.context.setAction("edit");

        Document html = renderHTMLPage(contributors);

        String fieldId = "ReleaseNotes.Code.ContributorsClass_0_contributors";
        assertEquals(1, html.select("dt label[for='" + fieldId + "']").size(),
            "Expected the contributors label to be bound to the contributors field.");
        assertEquals("textarea", html.select("#" + fieldId.replace(".", "\\.")).get(0).tagName());
    }

    /**
     * The "Add contributors" button passes the release note product, version and type as request parameters. The
     * page being rebuilt on save from its template and the submitted fields only, the form has to submit them for the
     * created entry to hold them.
     */
    @Test
    void theEntryFieldsTakenFromTheRequestAreSubmitted() throws Exception
    {
        XWikiDocument contributors = new XWikiDocument(CONTRIBUTORS);
        contributors.newXObject(ENTRY_CLASS, this.context);
        contributors.newXObject(CONTRIBUTORS_CLASS, this.context);
        contributors.setContent("{{include reference=\"ReleaseNotes.Code.ContributorsSheet\" context=\"current\"/}}");
        this.xwiki.saveDocument(contributors, this.context);
        this.context.setDoc(contributors);
        this.context.setAction("edit");
        this.request.put("product", "XWiki");
        this.request.put("version", "8.3M1");
        this.request.put("type", "Contributors");

        Document html = renderHTMLPage(contributors);

        assertEquals("XWiki", hiddenFieldValue(html, "product"));
        assertEquals("8.3M1", hiddenFieldValue(html, "version"));
        assertEquals("Contributors", hiddenFieldValue(html, "type"));
    }

    private static String hiddenFieldValue(Document html, String propertyName)
    {
        return html.select("input[type='hidden'][name='ReleaseNotes.Code.EntryClass_0_" + propertyName + "']")
            .attr("value");
    }
}
