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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.contrib.releasenotes.ChangeManager;
import org.xwiki.contrib.releasenotes.ReleaseNote;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesAccessDeniedException;
import org.xwiki.contrib.releasenotes.ReleaseNotesConfiguration;
import org.xwiki.contrib.releasenotes.ReleaseNotesNotFoundException;
import org.xwiki.contrib.releasenotes.internal.ProductResolver;
import org.xwiki.contrib.releasenotes.internal.ReleaseNotesDocumentStore;
import org.xwiki.contrib.releasenotes.internal.ReleaseNotesEntryPoint;
import org.xwiki.contrib.releasenotes.rest.model.ErrorRepresentation;
import org.xwiki.contrib.releasenotes.rest.model.ReleaseNoteRepresentation;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultReleaseNoteResource}.
 *
 * @version $Id$
 */
@ComponentTest
// Reading what a client sent and writing back what it reads is part of what the endpoint answers, so the factory and
// the serializer it uses are the real ones.
@ComponentList({ RepresentationFactory.class, LocalStringEntityReferenceSerializer.class, DefaultSymbolScheme.class,
    ReleaseNotesEntryPoint.class, ProductResolver.class })
class DefaultReleaseNoteResourceTest
{
    private static final String PRODUCT = "XWiki";

    private static final String VERSION = "8.3-milestone-1";

    private static final DocumentReference RELEASE_NOTE = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", PRODUCT, "8.3M1"), "WebHome");

    @InjectMockComponents
    private DefaultReleaseNoteResource resource;

    @MockComponent
    private ReleaseNoteManager releaseNoteManager;

    @MockComponent
    private AuthorizationManager authorAuthorization;

    @MockComponent
    private ReleaseNotesDocumentStore documentStore;

    @MockComponent
    private ReleaseNotesConfiguration configuration;

    @MockComponent
    private ChangeManager changeManager;

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

    @Test
    void aReleaseNoteIsReadWithThePageItLivesIn() throws Exception
    {
        when(this.releaseNoteManager.getReleaseNote(RELEASE_NOTE)).thenReturn(note());

        ReleaseNoteRepresentation representation = this.resource.getReleaseNote("xwiki", PRODUCT, VERSION);

        assertEquals(VERSION, representation.getVersion());
        assertEquals(PRODUCT, representation.getProduct());
        assertEquals("ReleaseNotes.Data.XWiki.8\\.3M1.WebHome", representation.getReference());
    }

    /**
     * The manager reads a page whoever asks for it, so the endpoint is where the view right of a request is checked.
     * The refusal is an exception, which the exception mapper answers with a 401 for a guest and a 403 for anyone
     * else.
     */
    @Test
    void aReleaseNoteTheCurrentUserCannotViewIsNotRead() throws Exception
    {
        when(this.authorization.hasAccess(Right.VIEW, RELEASE_NOTE)).thenReturn(false);

        ReleaseNotesAccessDeniedException exception = assertThrows(ReleaseNotesAccessDeniedException.class,
            () -> this.resource.getReleaseNote("xwiki", PRODUCT, VERSION));

        assertEquals(RELEASE_NOTE, exception.getReference());
        verify(this.releaseNoteManager, never()).getReleaseNote(any());
    }

    @Test
    void aUserWhoCannotEditThePageReplacesNoReleaseNote() throws Exception
    {
        when(this.authorization.hasAccess(Right.EDIT, RELEASE_NOTE)).thenReturn(false);

        ReleaseNotesAccessDeniedException exception = assertThrows(ReleaseNotesAccessDeniedException.class,
            () -> this.resource.updateReleaseNote("xwiki", PRODUCT, VERSION, new ReleaseNoteRepresentation()));

        assertEquals("The current user is not allowed to edit the page "
            + "[xwiki:ReleaseNotes.Data.XWiki.8\\.3M1.WebHome].", exception.getMessage());
        verify(this.releaseNoteManager, never()).updateReleaseNote(any());
    }

    /**
     * Marking a version released on the day it ships is what this endpoint is for: neither value could be written
     * once the release note existed.
     */
    @Test
    void aReleaseNoteIsMarkedReleasedOnADay() throws Exception
    {
        ReleaseNote stored = note();
        stored.setReleased(true);
        stored.setDate(day());

        when(this.releaseNoteManager.updateReleaseNote(any())).thenReturn(stored);

        ReleaseNoteRepresentation sent = new ReleaseNoteRepresentation();
        sent.setReleased(true);
        sent.setDate("2026-09-10");

        Response response = this.resource.updateReleaseNote("xwiki", PRODUCT, VERSION, sent);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("2026-09-10", ((ReleaseNoteRepresentation) response.getEntity()).getDate());
        assertTrue(((ReleaseNoteRepresentation) response.getEntity()).getReleased());
        assertEquals("ReleaseNotes.Data.XWiki.8\\.3M1.WebHome",
            ((ReleaseNoteRepresentation) response.getEntity()).getReference());

        ArgumentCaptor<ReleaseNote> captor = ArgumentCaptor.forClass(ReleaseNote.class);
        verify(this.releaseNoteManager).updateReleaseNote(captor.capture());
        assertTrue(captor.getValue().isReleased());
        assertEquals(day(), captor.getValue().getDate());
        // The release note of the URL is the one that is replaced, and not whatever the request says it is about.
        assertEquals(PRODUCT, captor.getValue().getProduct());
        assertEquals(VERSION, captor.getValue().getVersion());
    }

    /**
     * The properties a replacement leaves out are emptied rather than kept, so an empty release note is a
     * meaningful request: it is what puts a version back to unreleased and takes its date off.
     */
    @Test
    void aReleaseNoteReplacedByAnEmptyOneIsPutBackToUnreleased() throws Exception
    {
        when(this.releaseNoteManager.updateReleaseNote(any())).thenReturn(note());

        Response response =
            this.resource.updateReleaseNote("xwiki", PRODUCT, VERSION, new ReleaseNoteRepresentation());

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        ArgumentCaptor<ReleaseNote> captor = ArgumentCaptor.forClass(ReleaseNote.class);
        verify(this.releaseNoteManager).updateReleaseNote(captor.capture());
        assertFalse(captor.getValue().isReleased());
        assertNull(captor.getValue().getDate());
    }

    /**
     * A product and a version that have no release note are left to the exception mapper, which is the one place the
     * application turns a failure into a status code.
     */
    @Test
    void aReleaseNoteThatDoesNotExistIsNotReplaced() throws Exception
    {
        when(this.releaseNoteManager.updateReleaseNote(any()))
            .thenThrow(new ReleaseNotesNotFoundException("The page [x] holds no release note.", RELEASE_NOTE));

        ReleaseNotesNotFoundException exception = assertThrows(ReleaseNotesNotFoundException.class, () -> this.resource
            .updateReleaseNote("xwiki", PRODUCT, VERSION, new ReleaseNoteRepresentation()));

        assertEquals(RELEASE_NOTE, exception.getReference());
    }

    @Test
    void noReleaseNoteAtAllIsRefused() throws Exception
    {
        Response response = this.resource.updateReleaseNote("xwiki", PRODUCT, VERSION, null);

        assertRefusal(response, Response.Status.BAD_REQUEST,
            "A release note is replaced by the release note to store, and the request carries none.");
        verify(this.releaseNoteManager, never()).updateReleaseNote(any());
    }

    @Test
    void aReleaseNoteWithAnUnusableDateIsRefused() throws Exception
    {
        ReleaseNoteRepresentation sent = new ReleaseNoteRepresentation();
        sent.setDate("the tenth");

        Response response = this.resource.updateReleaseNote("xwiki", PRODUCT, VERSION, sent);

        assertRefusal(response, Response.Status.BAD_REQUEST,
            "The date [the tenth] is not a day written yyyy-MM-dd.");
        verify(this.releaseNoteManager, never()).updateReleaseNote(any());
    }

    @Test
    void aReplacementAtNoVersionAtAllIsRefused() throws Exception
    {
        Response response =
            this.resource.updateReleaseNote("xwiki", PRODUCT, " ", new ReleaseNoteRepresentation());

        assertRefusal(response, Response.Status.BAD_REQUEST,
            "A change belongs to the release note of one version of one product, and the URL names neither.");
        verify(this.releaseNoteManager, never()).updateReleaseNote(any());
    }

    @Test
    void aReadOfNoVersionAtAllIsRefused()
    {
        WebApplicationException exception =
            assertThrows(WebApplicationException.class, () -> this.resource.getReleaseNote("xwiki", PRODUCT, " "));

        assertRefusal(exception.getResponse(), Response.Status.BAD_REQUEST,
            "A change belongs to the release note of one version of one product, and the URL names neither.");
    }

    private void assertRefusal(Response response, Response.Status status, String message)
    {
        assertEquals(status.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity() instanceof ErrorRepresentation,
            "A refused request is answered with why it was refused.");
        assertEquals(message, ((ErrorRepresentation) response.getEntity()).getMessage());
    }

    private static Date day()
    {
        return Date.from(LocalDate.parse("2026-09-10").atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static ReleaseNote note()
    {
        ReleaseNote note = new ReleaseNote();
        note.setProduct(PRODUCT);
        note.setVersion(VERSION);

        return note;
    }
}
