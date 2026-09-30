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
package org.xwiki.contrib.releasenotes.rest.internal;

import java.util.List;

import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.contrib.releasenotes.Audience;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.ChangeManager;
import org.xwiki.contrib.releasenotes.Importance;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesAccessDeniedException;
import org.xwiki.contrib.releasenotes.ReleaseNotesConfiguration;
import org.xwiki.contrib.releasenotes.ReleaseNotesNotFoundException;
import org.xwiki.contrib.releasenotes.internal.ProductResolver;
import org.xwiki.contrib.releasenotes.internal.ReleaseNotesDocumentStore;
import org.xwiki.contrib.releasenotes.internal.ReleaseNotesEntryPoint;
import org.xwiki.contrib.releasenotes.rest.model.ChangeRepresentation;
import org.xwiki.contrib.releasenotes.rest.model.ErrorRepresentation;
import org.xwiki.model.ModelContext;
import org.xwiki.model.internal.reference.DefaultSymbolScheme;
import org.xwiki.model.internal.reference.LocalStringEntityReferenceSerializer;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultChangeResource}.
 *
 * @version $Id$
 */
@ComponentTest
// Reading what a client sent and writing back what it reads is part of what the endpoint answers, so the factory and
// the serializer it uses are the real ones.
@ComponentList({ RepresentationFactory.class, LocalStringEntityReferenceSerializer.class, DefaultSymbolScheme.class,
    ReleaseNotesEntryPoint.class, ProductResolver.class })
class DefaultChangeResourceTest
{
    private static final String NO_CHANGE_IN_URL =
        "A change lives in an entry page of the release note of one version of one product, and the URL does not "
            + "name all three.";

    private static final String PRODUCT = "XWiki";

    private static final String VERSION = "8.3";

    private static final String ENTRY_NAME = "Entry001";

    private static final DocumentReference RELEASE_NOTE = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", PRODUCT, VERSION), "WebHome");

    private static final DocumentReference ENTRY = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", PRODUCT, VERSION, ENTRY_NAME), "WebHome");

    @InjectMockComponents
    private DefaultChangeResource resource;

    @MockComponent
    private ChangeManager changeManager;

    @MockComponent
    private ReleaseNoteManager releaseNoteManager;

    @MockComponent
    private AuthorizationManager authorAuthorization;

    @MockComponent
    private ReleaseNotesDocumentStore documentStore;

    @MockComponent
    private ReleaseNotesConfiguration configuration;

    @MockComponent
    private ModelContext modelContext;

    @MockComponent
    private ContextualAuthorizationManager authorization;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @BeforeEach
    void setUp()
    {
        when(this.authorization.hasAccess(any(Right.class), any())).thenReturn(true);
        when(this.releaseNoteManager.getReleaseNoteReference(PRODUCT, VERSION)).thenReturn(RELEASE_NOTE);
    }

    /**
     * The entry of the URL is the last space of the page the change lives in, which is what the endpoint that
     * created it answered: that is how a client addresses one change out of the ones a release note holds.
     */
    @Test
    void aChangeIsReadFromTheEntryTheUrlNames() throws Exception
    {
        when(this.changeManager.getChange(ENTRY)).thenReturn(change());

        ChangeRepresentation representation = this.resource.getChange("xwiki", PRODUCT, VERSION, ENTRY_NAME);

        assertEquals("The title", representation.getTitle());
        assertEquals(ENTRY_NAME, representation.getEntry());
        assertEquals("ReleaseNotes.Data.XWiki.8\\.3.Entry001.WebHome", representation.getReference());
    }

    @Test
    void aChangeTheCurrentUserCannotViewIsNotRead() throws Exception
    {
        when(this.authorization.hasAccess(Right.VIEW, ENTRY)).thenReturn(false);

        ReleaseNotesAccessDeniedException exception = assertThrows(ReleaseNotesAccessDeniedException.class,
            () -> this.resource.getChange("xwiki", PRODUCT, VERSION, ENTRY_NAME));

        assertEquals("The current user is not allowed to view the page "
            + "[xwiki:ReleaseNotes.Data.XWiki.8\\.3.Entry001.WebHome].", exception.getMessage());
        verify(this.changeManager, never()).getChange(any());
    }

    @Test
    void aUserWhoCannotEditThePageReplacesNoChange() throws Exception
    {
        when(this.authorization.hasAccess(Right.EDIT, ENTRY)).thenReturn(false);
        ChangeRepresentation sent = new ChangeRepresentation();
        sent.setTitle("The title");

        ReleaseNotesAccessDeniedException exception = assertThrows(ReleaseNotesAccessDeniedException.class,
            () -> this.resource.updateChange("xwiki", PRODUCT, VERSION, ENTRY_NAME, sent));

        assertEquals(ENTRY, exception.getReference());
        verify(this.changeManager, never()).updateChange(any(), any());
    }

    /**
     * The change is replaced by exactly what the request carries, and the page it is written to is the entry of the
     * URL rather than anything the request says.
     */
    @Test
    void aChangeIsReplacedByWhatTheRequestCarries() throws Exception
    {
        when(this.changeManager.updateChange(eq(ENTRY), any())).thenReturn(change());

        ChangeRepresentation sent = new ChangeRepresentation();
        sent.setTitle("The title");
        sent.setAudience("user");
        sent.setImportance("high");
        sent.setScreenshots(List.of("shot.png"));

        Response response = this.resource.updateChange("xwiki", PRODUCT, VERSION, ENTRY_NAME, sent);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        ArgumentCaptor<Change> captor = ArgumentCaptor.forClass(Change.class);
        verify(this.changeManager).updateChange(eq(ENTRY), captor.capture());
        assertEquals("The title", captor.getValue().getTitle());
        assertEquals(Audience.USER, captor.getValue().getAudience());
        assertEquals(Importance.HIGH, captor.getValue().getImportance());
        assertEquals(List.of("shot.png"), captor.getValue().getScreenshots());
        // The release note of the URL is what a change belongs to, and not what the request says it belongs to.
        assertEquals(PRODUCT, captor.getValue().getProduct());
        assertEquals(VERSION, captor.getValue().getVersion());
    }

    /**
     * The change is answered as it was stored and not as it was sent, which is what the endpoint that creates one
     * does too: a client that recorded what it sent would hold values the wiki does not.
     */
    @Test
    void aReplacedChangeIsAnsweredWithWhatWasStored() throws Exception
    {
        Change stored = change();
        stored.setSummary("What the wiki holds.");

        when(this.changeManager.updateChange(eq(ENTRY), any())).thenReturn(stored);

        ChangeRepresentation sent = new ChangeRepresentation();
        sent.setTitle("The title");

        Response response = this.resource.updateChange("xwiki", PRODUCT, VERSION, ENTRY_NAME, sent);

        ChangeRepresentation answered = (ChangeRepresentation) response.getEntity();

        assertEquals("What the wiki holds.", answered.getSummary());
        assertEquals(ENTRY_NAME, answered.getEntry());
        assertEquals("ReleaseNotes.Data.XWiki.8\\.3.Entry001.WebHome", answered.getReference());
    }

    /**
     * An entry that holds no change is left to the exception mapper, which is the one place the application turns a
     * failure into a status code.
     */
    @Test
    void anEntryThatHoldsNoChangeIsNotReplaced() throws Exception
    {
        when(this.changeManager.updateChange(eq(ENTRY), any()))
            .thenThrow(new ReleaseNotesNotFoundException("The page [x] holds no change.", ENTRY));

        ChangeRepresentation sent = new ChangeRepresentation();
        sent.setTitle("The title");

        ReleaseNotesNotFoundException exception = assertThrows(ReleaseNotesNotFoundException.class,
            () -> this.resource.updateChange("xwiki", PRODUCT, VERSION, ENTRY_NAME, sent));

        assertEquals(ENTRY, exception.getReference());
    }

    @Test
    void aChangeReplacedWithNoTitleIsRefused() throws Exception
    {
        Response response =
            this.resource.updateChange("xwiki", PRODUCT, VERSION, ENTRY_NAME, new ChangeRepresentation());

        assertRefusal(response, Response.Status.BAD_REQUEST, "A change needs a title.");
        verify(this.changeManager, never()).updateChange(any(), any());
    }

    @Test
    void noChangeAtAllIsRefused() throws Exception
    {
        Response response = this.resource.updateChange("xwiki", PRODUCT, VERSION, ENTRY_NAME, null);

        assertRefusal(response, Response.Status.BAD_REQUEST, "A change needs a title.");
        verify(this.changeManager, never()).updateChange(any(), any());
    }

    @Test
    void aChangeWithAnUnusableAudienceIsRefused() throws Exception
    {
        ChangeRepresentation sent = new ChangeRepresentation();
        sent.setTitle("The title");
        sent.setAudience("everybody");

        Response response = this.resource.updateChange("xwiki", PRODUCT, VERSION, ENTRY_NAME, sent);

        assertRefusal(response, Response.Status.BAD_REQUEST,
            "The audience [everybody] is none of [user, administrator, developer].");
        verify(this.changeManager, never()).updateChange(any(), any());
    }

    @Test
    void aReplacementAtAnEmptyEntryIsRefused() throws Exception
    {
        ChangeRepresentation sent = new ChangeRepresentation();
        sent.setTitle("The title");

        Response response = this.resource.updateChange("xwiki", PRODUCT, VERSION, " ", sent);

        assertRefusal(response, Response.Status.BAD_REQUEST, NO_CHANGE_IN_URL);
        verify(this.changeManager, never()).updateChange(any(), any());
    }

    @Test
    void aReadOfAnEmptyEntryIsRefused()
    {
        WebApplicationException exception = assertThrows(WebApplicationException.class,
            () -> this.resource.getChange("xwiki", PRODUCT, VERSION, " "));

        assertRefusal(exception.getResponse(), Response.Status.BAD_REQUEST, NO_CHANGE_IN_URL);
    }

    private void assertRefusal(Response response, Response.Status status, String message)
    {
        assertEquals(status.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity() instanceof ErrorRepresentation,
            "A refused request is answered with why it was refused.");
        assertEquals(message, ((ErrorRepresentation) response.getEntity()).getMessage());
    }

    private static Change change()
    {
        Change change = new Change();
        change.setProduct(PRODUCT);
        change.setVersion(VERSION);
        change.setTitle("The title");
        change.setAudience(Audience.USER);

        return change;
    }
}
