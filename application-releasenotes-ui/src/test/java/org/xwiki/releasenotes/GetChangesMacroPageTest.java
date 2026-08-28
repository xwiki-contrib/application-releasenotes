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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.internal.ScriptQuery;
import org.xwiki.query.script.QueryManagerScriptService;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Page test for the {@code getChanges} wiki macro, defined in {@code ReleaseNotes.Code.Change.GetChangesMacro}.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@WikiMacroComponentList
class GetChangesMacroPageTest extends PageTest
{
    private static final DocumentReference GET_CHANGES_MACRO =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code", "Change"), "GetChangesMacro");

    private static final DocumentReference TEST_PAGE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data"), "TestPage");

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
        when(this.query.execute()).thenReturn(List.of());

        WikiMacroLoader.registerWikiMacro(this.componentManager, loadPage(GET_CHANGES_MACRO));
    }

    /**
     * The {@code >=} and {@code <=} version filters must map to the matching XWQL operators and bind the version
     * without their two-character prefix, so that the boundary version is part of the result. The strict {@code >}
     * and {@code <} filters keep excluding it, and a filter without any prefix stays a {@code like}.
     */
    @ParameterizedTest
    @CsvSource({
        ">=2.0, >=, 2.0",
        "<=2.0, <=, 2.0",
        ">2.0,  >,  2.0",
        "<2.0,  <,  2.0",
        "2.0,   like, 2.0"
    })
    void versionFilterOperator(String versions, String expectedOperator, String expectedBoundValue) throws Exception
    {
        renderGetChanges(versions);

        ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
        verify(this.queryManagerScriptService).xwql(statement.capture());
        assertTrue(statement.getValue().contains(String.format("entries.version %s :version1", expectedOperator)),
            String.format("Expected the \"%s\" filter to use the \"%s\" operator, got: %s", versions,
                expectedOperator, statement.getValue()));
        verify(this.query).bindValue("version1", expectedBoundValue);
    }

    /**
     * A comma-separated filter combines its items with an {@code or}, each one getting its own query parameter.
     */
    @Test
    void versionFilterWithSeveralItems() throws Exception
    {
        renderGetChanges(">=1.0,<3.0");

        ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
        verify(this.queryManagerScriptService).xwql(statement.capture());
        assertTrue(statement.getValue()
                .contains("(entries.version >= :version1 or entries.version < :version2)"),
            "Expected one clause per filter item, got: " + statement.getValue());
        verify(this.query).bindValue("version1", "1.0");
        verify(this.query).bindValue("version2", "3.0");
    }

    /**
     * Renders a page calling the {@code getChanges} macro with the passed {@code versions} filter.
     */
    private void renderGetChanges(String versions) throws Exception
    {
        XWikiDocument page = new XWikiDocument(TEST_PAGE);
        page.setSyntax(Syntax.XWIKI_2_1);
        page.setContent(String.format(
            "{{getChanges products=\"TestProduct\" versions=\"%s\" contextVariable=\"changeDocs\"/}}", versions));
        this.xwiki.saveDocument(page, this.context);
        page.getRenderedContent(this.context);
    }
}
