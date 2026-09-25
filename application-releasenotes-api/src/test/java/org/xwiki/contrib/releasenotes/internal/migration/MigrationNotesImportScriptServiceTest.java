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
package org.xwiki.contrib.releasenotes.internal.migration;

import java.util.List;

import jakarta.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MigrationNotesImportScriptService}.
 *
 * @version $Id$
 */
@ComponentTest
class MigrationNotesImportScriptServiceTest
{
    private static final WikiReference WIKI = new WikiReference("xwiki");

    @InjectMockComponents
    private MigrationNotesImportScriptService service;

    @MockComponent
    private MigrationNotesImporter importer;

    @MockComponent
    private ContextualAuthorizationManager authorization;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @Mock
    private XWikiContext xcontext;

    @BeforeEach
    void setUp()
    {
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getWikiId()).thenReturn("xwiki");
    }

    @Test
    void anAdministratorPreviewsAndRunsTheImport() throws Exception
    {
        when(this.authorization.hasAccess(Right.ADMIN, WIKI)).thenReturn(true);
        MigrationNotesImportReport preview = new MigrationNotesImportReport("XWiki", List.of(), 0);
        MigrationNotesImportReport run = new MigrationNotesImportReport("XWiki", List.of(), 0);
        when(this.importer.preview("XWiki")).thenReturn(preview);
        when(this.importer.importNotes("XWiki")).thenReturn(run);

        assertSame(preview, this.service.preview("XWiki"));
        assertSame(run, this.service.importNotes("XWiki"));
    }

    /**
     * The import rewrites pages across the wiki, so a user who does not administer it can neither run it nor see what
     * it would do.
     */
    @Test
    void aUserWhoDoesNotAdministerTheWikiCannotUseTheImport() throws Exception
    {
        assertThrows(ReleaseNotesException.class, () -> this.service.preview("XWiki"));
        assertThrows(ReleaseNotesException.class, () -> this.service.importNotes("XWiki"));

        verify(this.importer, never()).preview(any());
        verify(this.importer, never()).importNotes(any());
    }
}
