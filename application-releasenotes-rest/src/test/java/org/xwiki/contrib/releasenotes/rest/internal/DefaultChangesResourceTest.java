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
import java.util.Map;

import jakarta.inject.Provider;

import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.contrib.releasenotes.Audience;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.ChangeManager;
import org.xwiki.contrib.releasenotes.ChangeQuery;
import org.xwiki.contrib.releasenotes.ChangeQueryParser;
import org.xwiki.contrib.releasenotes.ChangeSearchResult;
import org.xwiki.contrib.releasenotes.Importance;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.rest.model.ChangeRepresentation;
import org.xwiki.contrib.releasenotes.rest.model.ChangesRepresentation;
import org.xwiki.contrib.releasenotes.rest.model.ErrorRepresentation;
import org.xwiki.model.ModelContext;
import org.xwiki.model.internal.reference.DefaultSymbolScheme;
import org.xwiki.model.internal.reference.LocalStringEntityReferenceSerializer;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultChangesResource}.
 *
 * @version $Id$
 */
@ComponentTest
// Reading what a client posted and writing back what it reads is part of what the endpoint answers, so the factory
// and the serializer it uses are the real ones.
@ComponentList({ RepresentationFactory.class, LocalStringEntityReferenceSerializer.class, DefaultSymbolScheme.class })
class DefaultChangesResourceTest
{
    private static final String NO_RELEASE_NOTE_IN_URL =
        "A change belongs to the release note of one version of one product, and the URL names neither.";

    private static final String PRODUCT = "XWiki";

    private static final String VERSION = "8.3";

    private static final DocumentReference RELEASE_NOTE = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", PRODUCT, VERSION), "WebHome");

    private static final DocumentReference ENTRY = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", PRODUCT, VERSION, "Entry001"), "WebHome");

    @InjectMockComponents
    private DefaultChangesResource resource;

    @MockComponent
    private ChangeManager changeManager;

    @MockComponent
    private ReleaseNoteManager releaseNoteManager;

    @MockComponent
    private ChangeQueryParser changeQueryParser;

    @MockComponent
    private ModelContext modelContext;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    private XWiki wiki;

    private UriInfo uriInfo;

    @BeforeEach
    void setUp() throws Exception
    {
        this.uriInfo = mock(UriInfo.class);
        when(this.uriInfo.getBaseUri()).thenReturn(URI.create("http://localhost:8080/xwiki/rest"));

        XWikiContext xcontext = mock(XWikiContext.class);
        this.wiki = mock(XWiki.class);
        when(xcontext.getWiki()).thenReturn(this.wiki);
        when(this.xcontextProvider.get()).thenReturn(xcontext);
        when(this.wiki.exists(RELEASE_NOTE, xcontext)).thenReturn(true);

        when(this.releaseNoteManager.getReleaseNoteReference(PRODUCT, VERSION)).thenReturn(RELEASE_NOTE);
        when(this.changeQueryParser.parse(any())).thenReturn(new ChangeQuery());
        when(this.changeManager.search(any())).thenReturn(new ChangeSearchResult(List.of(), List.of(), false));
    }

    /**
     * The release note the URL names is not something a client filters on: it is asked for exactly, so that the
     * changes of {@code 8.3} are the changes of {@code 8.3} and not of everything its version is a pattern of.
     */
    @Test
    void theChangesOfOneReleaseNoteAreAskedForExactly() throws Exception
    {
        when(this.changeManager.search(any())).thenReturn(searchResult(true));
        when(this.changeManager.getChange(ENTRY)).thenReturn(change());

        ChangesRepresentation representation =
            this.resource.getChanges("xwiki", PRODUCT, VERSION, "user", "Performance", "high", "true", "false",
                "true", false, "10", "20");

        Map<String, ?> parameters = capturedParameters();

        assertEquals("=XWiki", parameters.get(ChangeQueryParser.PRODUCTS));
        assertEquals("=8.3", parameters.get(ChangeQueryParser.VERSIONS));
        assertEquals("user", parameters.get(ChangeQueryParser.AUDIENCE));
        assertEquals("Performance", parameters.get(ChangeQueryParser.CATEGORIES));
        assertEquals("high", parameters.get(ChangeQueryParser.IMPORTANCE));
        assertEquals("true", parameters.get(ChangeQueryParser.CONTAINS_SCREENSHOTS));
        assertEquals("false", parameters.get(ChangeQueryParser.CONTAINS_MIGRATION_NOTES));
        assertEquals("true", parameters.get(ChangeQueryParser.RELEASED));
        assertEquals("10", parameters.get(ChangeQueryParser.LIMIT));
        assertEquals("20", parameters.get(ChangeQueryParser.OFFSET));

        assertEquals(1, representation.getChanges().size());
        assertEquals("The title", representation.getChanges().get(0).getTitle());
        assertEquals("ReleaseNotes.Data.XWiki.8\\.3.Entry001.WebHome",
            representation.getChanges().get(0).getReference());
        assertTrue(representation.isHasMore(), "The client is told that a next page of changes exists.");
    }

    /**
     * Asking for the aggregated changes of a final version is asking the question its release note answers: its own
     * changes plus the ones of its milestones and of its release candidates.
     */
    @Test
    void theChangesOfTheMilestonesAreAskedForWhenTheyAreAskedFor() throws Exception
    {
        when(this.releaseNoteManager.getAggregatedVersions(RELEASE_NOTE))
            .thenReturn(List.of("8.3", "8.3-milestone%", "8.3-rc%"));

        this.resource.getChanges("xwiki", PRODUCT, VERSION, null, null, null, null, null, null, true, null, null);

        assertEquals("8.3,8.3-milestone%,8.3-rc%", capturedParameters().get(ChangeQueryParser.VERSIONS));
    }

    @Test
    void aFilterThatIsNotAskedForIsNotPassedOn() throws Exception
    {
        this.resource.getChanges("xwiki", PRODUCT, VERSION, null, null, null, null, null, null, false, null, null);

        Map<String, ?> parameters = capturedParameters();

        assertNull(parameters.get(ChangeQueryParser.AUDIENCE));
        assertNull(parameters.get(ChangeQueryParser.CONTAINS_MIGRATION_NOTES));
        assertNull(parameters.get(ChangeQueryParser.RELEASED));
        assertNull(parameters.get(ChangeQueryParser.LIMIT));
    }

    @Test
    void theChangesOfAReleaseNoteThatNamesNoProductAreRefused()
    {
        WebApplicationException exception = assertThrows(WebApplicationException.class,
            () -> this.resource.getChanges("xwiki", " ", VERSION, null, null, null, null, null, null, false, null, null));

        assertRefusal(exception.getResponse(), Response.Status.BAD_REQUEST, NO_RELEASE_NOTE_IN_URL);
    }

    @Test
    void aCreatedChangeIsAnsweredWithThePageItLivesIn() throws Exception
    {
        when(this.changeManager.createChange(any())).thenReturn(ENTRY);
        when(this.changeManager.getChange(ENTRY)).thenReturn(change());

        ChangeRepresentation posted = new ChangeRepresentation();
        posted.setTitle("The title");
        posted.setAudience("user");

        Response response = this.resource.createChange(this.uriInfo, "xwiki", PRODUCT, VERSION, posted);

        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        assertEquals("http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/ReleaseNotes/spaces/Data/spaces/XWiki/"
            + "spaces/8.3/spaces/Entry001/pages/WebHome", response.getLocation().toString());

        ChangeRepresentation created = (ChangeRepresentation) response.getEntity();

        assertEquals("The title", created.getTitle());
        assertEquals("ReleaseNotes.Data.XWiki.8\\.3.Entry001.WebHome", created.getReference());

        ArgumentCaptor<Change> captor = ArgumentCaptor.forClass(Change.class);
        verify(this.changeManager).createChange(captor.capture());
        // The change is stored against the release note of the URL, whatever the posted change says about it.
        assertEquals(PRODUCT, captor.getValue().getProduct());
        assertEquals(VERSION, captor.getValue().getVersion());
    }

    /**
     * Creation is template-driven, so a property the client left out is stored with the value the change template
     * gives it. A client that recorded what it posted would hold a value the wiki does not.
     */
    @Test
    void aCreatedChangeIsAnsweredWithWhatWasStoredAndNotWithWhatWasPosted() throws Exception
    {
        Change stored = change();
        stored.setImportance(Importance.MEDIUM);

        when(this.changeManager.createChange(any())).thenReturn(ENTRY);
        when(this.changeManager.getChange(ENTRY)).thenReturn(stored);

        ChangeRepresentation posted = new ChangeRepresentation();
        posted.setTitle("The title");

        Response response = this.resource.createChange(this.uriInfo, "xwiki", PRODUCT, VERSION, posted);

        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        assertNull(posted.getImportance(), "The change was posted without an importance.");
        assertEquals("medium", ((ChangeRepresentation) response.getEntity()).getImportance());

        ArgumentCaptor<Change> captor = ArgumentCaptor.forClass(Change.class);
        verify(this.changeManager).createChange(captor.capture());
        // The importance is left unset rather than defaulted here, so that the template is what decides it.
        assertNull(captor.getValue().getImportance());
    }

    /**
     * A change posted to a release note that does not exist would be created in a page tree no release note gathers,
     * so it is refused rather than left there.
     */
    @Test
    void aChangePostedToAReleaseNoteThatDoesNotExistIsRefused() throws Exception
    {
        when(this.wiki.exists(any(DocumentReference.class), any(XWikiContext.class))).thenReturn(false);

        ChangeRepresentation posted = new ChangeRepresentation();
        posted.setTitle("The title");

        Response response = this.resource.createChange(this.uriInfo, "xwiki", PRODUCT, "9.0", posted);

        assertRefusal(response, Response.Status.NOT_FOUND, "There is no release note for the version [9.0] of "
            + "[XWiki].");
        verify(this.changeManager, never()).createChange(any());
    }

    @Test
    void aChangePostedToNoProductAtAllIsRefused() throws Exception
    {
        ChangeRepresentation posted = new ChangeRepresentation();
        posted.setTitle("The title");

        Response response = this.resource.createChange(this.uriInfo, "xwiki", " ", VERSION, posted);

        assertRefusal(response, Response.Status.BAD_REQUEST, NO_RELEASE_NOTE_IN_URL);
        verify(this.changeManager, never()).createChange(any());
    }

    /**
     * A store that will not say whether the release note exists is a failure of the wiki, and not a release note that
     * does not exist: telling the two apart is what keeps a client from taking a broken wiki for an empty one.
     */
    @Test
    void aStoreThatWillNotSayWhetherTheReleaseNoteExistsFails() throws Exception
    {
        when(this.wiki.exists(any(DocumentReference.class), any(XWikiContext.class)))
            .thenThrow(new XWikiException(XWikiException.MODULE_XWIKI_STORE,
                XWikiException.ERROR_XWIKI_STORE_HIBERNATE_READING_DOC, "The store would not answer."));

        ChangeRepresentation posted = new ChangeRepresentation();
        posted.setTitle("The title");

        ReleaseNotesException exception = assertThrows(ReleaseNotesException.class,
            () -> this.resource.createChange(this.uriInfo, "xwiki", PRODUCT, VERSION, posted));

        assertEquals("Failed to look up the page [xwiki:ReleaseNotes.Data.XWiki.8\\.3.WebHome].",
            exception.getMessage());
    }

    @Test
    void aChangeWithNoTitleIsRefused() throws Exception
    {
        Response response =
            this.resource.createChange(this.uriInfo, "xwiki", PRODUCT, VERSION, new ChangeRepresentation());

        assertRefusal(response, Response.Status.BAD_REQUEST, "A change needs a title.");
        verify(this.changeManager, never()).createChange(any());
    }

    @Test
    void noChangeAtAllIsRefused() throws Exception
    {
        Response response = this.resource.createChange(this.uriInfo, "xwiki", PRODUCT, VERSION, null);

        assertRefusal(response, Response.Status.BAD_REQUEST, "A change needs a title.");
    }

    @Test
    void aChangeWithAnUnusableImportanceIsRefused() throws Exception
    {
        ChangeRepresentation posted = new ChangeRepresentation();
        posted.setTitle("The title");
        posted.setImportance("huge");

        Response response = this.resource.createChange(this.uriInfo, "xwiki", PRODUCT, VERSION, posted);

        assertRefusal(response, Response.Status.BAD_REQUEST, "The importance [huge] is none of [low, medium, high].");
        verify(this.changeManager, never()).createChange(any());
    }

    private Map<String, ?> capturedParameters()
    {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, ?>> captor = ArgumentCaptor.forClass(Map.class);
        verify(this.changeQueryParser).parse(captor.capture());

        return captor.getValue();
    }

    private void assertRefusal(Response response, Response.Status status, String message)
    {
        assertEquals(status.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity() instanceof ErrorRepresentation,
            "A refused request is answered with why it was refused.");
        assertEquals(message, ((ErrorRepresentation) response.getEntity()).getMessage());
    }

    private static ChangeSearchResult searchResult(boolean hasMore)
    {
        return new ChangeSearchResult(List.of("ReleaseNotes.Data.XWiki.8\\.3.Entry001.WebHome"), List.of(ENTRY),
            hasMore);
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
