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

import java.net.URI;
import java.util.List;
import java.util.function.Predicate;

import javax.inject.Named;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.xwiki.contrib.releasenotes.ChangeManager;
import org.xwiki.contrib.releasenotes.ReleaseNote;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesAccessDeniedException;
import org.xwiki.contrib.releasenotes.ReleaseNotesConfiguration;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.internal.ProductResolver;
import org.xwiki.contrib.releasenotes.internal.ReleaseNotesDocumentStore;
import org.xwiki.contrib.releasenotes.internal.ReleaseNotesEntryPoint;
import org.xwiki.contrib.releasenotes.rest.model.ErrorRepresentation;
import org.xwiki.contrib.releasenotes.rest.model.ReleaseNoteRepresentation;
import org.xwiki.contrib.releasenotes.rest.model.ReleaseNotesRepresentation;
import org.xwiki.model.ModelContext;
import org.xwiki.model.internal.reference.DefaultSymbolScheme;
import org.xwiki.model.internal.reference.LocalStringEntityReferenceSerializer;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultReleaseNotesResource}.
 *
 * @version $Id$
 */
@ComponentTest
// Reading what a client posted and writing back what it reads is part of what the endpoint answers, so the factory
// and the serializer it uses are the real ones.
@ComponentList({ RepresentationFactory.class, LocalStringEntityReferenceSerializer.class, DefaultSymbolScheme.class,
    ReleaseNotesEntryPoint.class, ProductResolver.class })
class DefaultReleaseNotesResourceTest
{
    private static final DocumentReference RELEASE_NOTE = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", "XWiki", "8.3M1"), "WebHome");

    @InjectMockComponents
    private DefaultReleaseNotesResource resource;

    @MockComponent
    private ReleaseNoteManager releaseNoteManager;

    @MockComponent
    private ReleaseNotesConfiguration configuration;

    @MockComponent
    private AuthorizationManager authorAuthorization;

    @MockComponent
    private ReleaseNotesDocumentStore documentStore;

    @MockComponent
    private ChangeManager changeManager;

    @MockComponent
    private ModelContext modelContext;

    @MockComponent
    private ContextualAuthorizationManager authorization;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    private UriInfo uriInfo;

    @BeforeEach
    void setUp()
    {
        when(this.authorization.hasAccess(any(Right.class), any())).thenReturn(true);
        this.uriInfo = mock(UriInfo.class);
        when(this.uriInfo.getBaseUri()).thenReturn(URI.create("http://localhost:8080/xwiki/rest"));
        when(this.configuration.getDefaultProduct()).thenReturn("XWiki");
        when(this.releaseNoteManager.getReleaseNoteReference("XWiki", "8.3-milestone-1")).thenReturn(RELEASE_NOTE);
    }

    @Test
    void theReleaseNotesOfAProductAreListedWithThePageEachOfThemLivesIn() throws Exception
    {
        when(this.releaseNoteManager.getReleaseNotes(eq("XWiki"), any()))
            .thenReturn(List.of(note("XWiki", "8.3-milestone-1")));

        ReleaseNotesRepresentation representation = this.resource.getReleaseNotes("xwiki", "XWiki");

        assertEquals(1, representation.getReleaseNotes().size());
        assertEquals("8.3-milestone-1", representation.getReleaseNotes().get(0).getVersion());
        assertEquals("ReleaseNotes.Data.XWiki.8\\.3M1.WebHome", representation.getReleaseNotes().get(0).getReference());
    }

    /**
     * The release notes are listed with a filter accepting the pages the current user can view, which the manager
     * applies to the page each release note actually lives in.
     */
    @Test
    void theReleaseNotesAreListedWithTheViewRightOfTheCurrentUser() throws Exception
    {
        DocumentReference hidden = new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "XWiki", "9.0"),
            "WebHome");
        when(this.authorization.hasAccess(Right.VIEW, hidden)).thenReturn(false);

        this.resource.getReleaseNotes("xwiki", "XWiki");

        ArgumentCaptor<Predicate<DocumentReference>> filter = ArgumentCaptor.captor();
        verify(this.releaseNoteManager).getReleaseNotes(eq("XWiki"), filter.capture());
        assertTrue(filter.getValue().test(RELEASE_NOTE));
        assertFalse(filter.getValue().test(hidden));
    }

    /**
     * A release note holding no product or no version lives nowhere the application can name, so it is listed
     * without a page rather than making the whole listing fail.
     */
    @Test
    void aReleaseNoteWithNoVersionIsListedWithNoPage() throws Exception
    {
        when(this.releaseNoteManager.getReleaseNotes(isNull(), any())).thenReturn(List.of(note("XWiki", "")));

        ReleaseNotesRepresentation representation = this.resource.getReleaseNotes("xwiki", null);

        assertNull(representation.getReleaseNotes().get(0).getReference());
        verify(this.releaseNoteManager, never()).getReleaseNoteReference("XWiki", "");
    }

    /**
     * The manager writes a page whoever asks it to, so the endpoint is where the edit right of a request is checked,
     * on the page of the release note of the configured product when the request names none.
     */
    @Test
    void aUserWhoCannotEditThePageCreatesNoReleaseNote() throws Exception
    {
        when(this.authorization.hasAccess(Right.EDIT, RELEASE_NOTE)).thenReturn(false);

        ReleaseNotesAccessDeniedException exception = assertThrows(ReleaseNotesAccessDeniedException.class,
            () -> this.resource.createReleaseNote(this.uriInfo, "xwiki", posted("8.3-milestone-1")));

        assertEquals(RELEASE_NOTE, exception.getReference());
        verify(this.releaseNoteManager, never()).createReleaseNote(any());
    }

    @Test
    void aCreatedReleaseNoteIsAnsweredWithThePageItLivesIn() throws Exception
    {
        when(this.releaseNoteManager.createReleaseNote(any()))
            .thenReturn(RELEASE_NOTE);
        when(this.releaseNoteManager.getReleaseNote(RELEASE_NOTE)).thenReturn(note("XWiki", "8.3-milestone-1"));

        Response response = this.resource.createReleaseNote(this.uriInfo, "xwiki", posted("8.3-milestone-1"));

        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        // The created release note is pointed at through the generic page resource of the wiki, which is where a
        // client goes on to read it or to change it.
        assertEquals("http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/ReleaseNotes/spaces/Data/spaces/XWiki/"
            + "spaces/8.3M1/pages/WebHome", response.getLocation().toString());

        ReleaseNoteRepresentation created = (ReleaseNoteRepresentation) response.getEntity();

        assertEquals("8.3-milestone-1", created.getVersion());
        assertEquals("ReleaseNotes.Data.XWiki.8\\.3M1.WebHome", created.getReference());
    }

    /**
     * A release note posted without a product is created with the product configured for the wiki, so that is the
     * product it is answered with. A client that recorded what it posted would hold no product at all.
     */
    @Test
    void aCreatedReleaseNoteIsAnsweredWithWhatWasStoredAndNotWithWhatWasPosted() throws Exception
    {
        when(this.releaseNoteManager.createReleaseNote(any())).thenReturn(RELEASE_NOTE);
        when(this.releaseNoteManager.getReleaseNote(RELEASE_NOTE)).thenReturn(note("XWiki", "8.3-milestone-1"));

        ReleaseNoteRepresentation posted = new ReleaseNoteRepresentation();
        posted.setVersion("8.3-milestone-1");

        Response response = this.resource.createReleaseNote(this.uriInfo, "xwiki", posted);

        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        assertNull(posted.getProduct(), "The release note was posted without a product.");
        assertEquals("XWiki", ((ReleaseNoteRepresentation) response.getEntity()).getProduct());
    }

    @Test
    void aReleaseNoteWithNoVersionIsRefused() throws Exception
    {
        Response response = this.resource.createReleaseNote(this.uriInfo, "xwiki", posted(null));

        assertRefusal(response, Response.Status.BAD_REQUEST, "A release note needs the version it is about.");
        verify(this.releaseNoteManager, never()).createReleaseNote(any());
    }

    @Test
    void noReleaseNoteAtAllIsRefused() throws Exception
    {
        Response response = this.resource.createReleaseNote(this.uriInfo, "xwiki", null);

        assertRefusal(response, Response.Status.BAD_REQUEST, "A release note needs the version it is about.");
    }

    /**
     * The product may be left out only when the wiki has one configured: the page a release note lives in is named
     * after its product, so a release note without one cannot be located.
     */
    @Test
    void aReleaseNoteWithNoProductAndNoDefaultProductIsRefused() throws Exception
    {
        when(this.configuration.getDefaultProduct()).thenReturn(" ");

        ReleaseNoteRepresentation note = new ReleaseNoteRepresentation();
        note.setVersion("8.3-milestone-1");

        Response response = this.resource.createReleaseNote(this.uriInfo, "xwiki", note);

        assertRefusal(response, Response.Status.BAD_REQUEST,
            "A release note needs the product it is about, since this wiki has no default product configured.");
        verify(this.releaseNoteManager, never()).createReleaseNote(any());
    }

    @Test
    void aReleaseNoteWithAnUnusableDateIsRefused() throws Exception
    {
        ReleaseNoteRepresentation note = posted("8.3-milestone-1");
        note.setDate("tomorrow");

        Response response = this.resource.createReleaseNote(this.uriInfo, "xwiki", note);

        assertRefusal(response, Response.Status.BAD_REQUEST,
            "The date [tomorrow] is not a day written yyyy-MM-dd.");
    }

    /**
     * The components of the application read and write the current wiki, so the wiki of the URL is made the current
     * one for the request and put back afterwards: a REST request is served on a thread that goes on to serve
     * others.
     */
    @Test
    void theWikiOfTheRequestIsTheCurrentOneForTheRequestOnly() throws Exception
    {
        WikiReference previousWiki = new WikiReference("previous");
        when(this.modelContext.getCurrentEntityReference()).thenReturn(previousWiki);

        this.resource.getReleaseNotes("subwiki", null);

        InOrder order = inOrder(this.modelContext);
        order.verify(this.modelContext).setCurrentEntityReference(new WikiReference("subwiki"));
        order.verify(this.modelContext).setCurrentEntityReference(previousWiki);
    }

    /**
     * The wiki is put back even when the endpoint failed, since a request that failed leaves the thread it ran on
     * behind just the same.
     */
    @Test
    void theWikiOfTheRequestIsPutBackWhenTheEndpointFails() throws Exception
    {
        ReleaseNotesException failure = new ReleaseNotesException("Failed.");
        when(this.releaseNoteManager.getReleaseNotes(isNull(), any())).thenThrow(failure);

        ReleaseNotesException thrown =
            assertThrows(ReleaseNotesException.class, () -> this.resource.getReleaseNotes("subwiki", null));

        assertSame(failure, thrown);
        verify(this.modelContext).setCurrentEntityReference(null);
    }

    private void assertRefusal(Response response, Response.Status status, String message)
    {
        assertEquals(status.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity() instanceof ErrorRepresentation,
            "A refused request is answered with why it was refused.");
        assertEquals(message, ((ErrorRepresentation) response.getEntity()).getMessage());
    }

    private static ReleaseNote note(String product, String version)
    {
        ReleaseNote note = new ReleaseNote();
        note.setProduct(product);
        note.setVersion(version);

        return note;
    }

    private static ReleaseNoteRepresentation posted(String version)
    {
        ReleaseNoteRepresentation note = new ReleaseNoteRepresentation();
        note.setProduct("XWiki");
        note.setVersion(version);

        return note;
    }
}
