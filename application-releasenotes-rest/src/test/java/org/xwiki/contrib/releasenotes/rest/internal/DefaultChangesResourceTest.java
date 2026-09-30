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
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.contrib.releasenotes.Audience;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.ChangeFilter;
import org.xwiki.contrib.releasenotes.ChangeManager;
import org.xwiki.contrib.releasenotes.ChangeQuery;
import org.xwiki.contrib.releasenotes.ChangeSearchResult;
import org.xwiki.contrib.releasenotes.Importance;
import org.xwiki.contrib.releasenotes.LoadedChangeSearchResult;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesAccessDeniedException;
import org.xwiki.contrib.releasenotes.ReleaseNotesConfiguration;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.ReleaseNotesNotFoundException;
import org.xwiki.contrib.releasenotes.internal.DefaultChangeQueryParser;
import org.xwiki.contrib.releasenotes.internal.ProductResolver;
import org.xwiki.contrib.releasenotes.internal.ReleaseNotesDocumentStore;
import org.xwiki.contrib.releasenotes.internal.ReleaseNotesEntryPoint;
import org.xwiki.contrib.releasenotes.rest.model.ChangeRepresentation;
import org.xwiki.contrib.releasenotes.rest.model.ChangesRepresentation;
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

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
// Reading what a client posted and writing back what it reads is part of what the endpoint answers, so the factory,
// the filter parser and the serializer it uses are the real ones.
@ComponentList({ RepresentationFactory.class, LocalStringEntityReferenceSerializer.class, DefaultSymbolScheme.class,
    ReleaseNotesEntryPoint.class, ProductResolver.class, DefaultChangeQueryParser.class })
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

    private UriInfo uriInfo;

    private XWikiDocument noteDocument;

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.authorization.hasAccess(any(Right.class), any())).thenReturn(true);
        this.uriInfo = mock(UriInfo.class);
        when(this.uriInfo.getBaseUri()).thenReturn(URI.create("http://localhost:8080/xwiki/rest"));

        this.noteDocument = mock(XWikiDocument.class);
        when(this.documentStore.load(RELEASE_NOTE)).thenReturn(this.noteDocument);

        when(this.releaseNoteManager.getReleaseNoteReference(PRODUCT, VERSION)).thenReturn(RELEASE_NOTE);
        when(this.changeManager.searchAndLoad(any(), any())).thenReturn(
            new LoadedChangeSearchResult(new ChangeSearchResult(List.of(), List.of(), false), List.of()));
    }

    /**
     * The changes are searched with a filter accepting the pages the current user can view, which the search applies
     * before cutting the result into pages, so that a page is never short of the changes the user may see.
     */
    @Test
    void theChangesAreSearchedWithTheViewRightOfTheCurrentUser() throws Exception
    {
        when(this.authorization.hasAccess(Right.VIEW, ENTRY)).thenReturn(false);

        this.resource.getChanges("xwiki", PRODUCT, VERSION, null, null, null, null, false, null, null);

        ArgumentCaptor<Predicate<DocumentReference>> filter = ArgumentCaptor.captor();
        verify(this.changeManager).searchAndLoad(any(), filter.capture());
        assertTrue(filter.getValue().test(RELEASE_NOTE));
        assertFalse(filter.getValue().test(ENTRY));
    }

    /**
     * The release note the URL names is not something a client filters on: it is asked for exactly, so that the
     * changes of {@code 8.3} are the changes of {@code 8.3} and not of everything its version is a pattern of.
     */
    @Test
    void theChangesOfOneReleaseNoteAreAskedForExactly() throws Exception
    {
        when(this.changeManager.searchAndLoad(any(), any())).thenReturn(searchResult(true));

        ChangesRepresentation representation =
            this.resource.getChanges("xwiki", PRODUCT, VERSION, "User", "Performance", "high", "true", false, "10",
                "20");

        ChangeQuery query = capturedQuery();

        assertEquals(List.of(exactly(PRODUCT)), query.getProducts());
        assertEquals(List.of(exactly(VERSION)), query.getVersions());
        // The filters a client passes are read as the ones of the getChanges wiki macro, where a value with no
        // operator is a pattern.
        assertEquals(List.of(like("user")), query.getAudiences());
        assertEquals(List.of(like("Performance")), query.getCategories());
        assertEquals(List.of(like("2")), query.getImportances());
        assertEquals(Boolean.TRUE, query.getContainsScreenshots());
        assertEquals(10, query.getLimit());
        assertEquals(20, query.getOffset());

        assertEquals(1, representation.getChanges().size());
        assertEquals("The title", representation.getChanges().get(0).getTitle());
        assertEquals("ReleaseNotes.Data.XWiki.8\\.3.Entry001.WebHome",
            representation.getChanges().get(0).getReference());
        assertTrue(representation.isHasMore(), "The client is told that a next page of changes exists.");
        // The changes come with the result of the search, which has checked the view right on them already.
        verify(this.changeManager, never()).getChange(any());
    }

    /**
     * The filters are written in the language of the getChanges wiki macro, which the REST page of the application
     * documents: comma separated values, {@code %} wildcards and comparison operators.
     */
    @Test
    void aFilterIsWrittenInTheLanguageOfTheGetChangesMacro() throws Exception
    {
        this.resource.getChanges("xwiki", PRODUCT, VERSION, "=User, dev%", "Perf%", ">=medium", null, false, null,
            null);

        ChangeQuery query = capturedQuery();

        assertEquals(List.of(new ChangeFilter(ChangeFilter.Operator.EQUALS, "user"), like("dev%")),
            query.getAudiences());
        assertEquals(List.of(like("Perf%")), query.getCategories());
        assertEquals(List.of(new ChangeFilter(ChangeFilter.Operator.GTE, "1")), query.getImportances());
    }

    /**
     * Asking for the aggregated changes of a final version is asking the question its release note answers: its own
     * changes plus the ones of its milestones and of its release candidates, which are patterns.
     */
    @Test
    void theChangesOfTheMilestonesAreAskedForWhenTheyAreAskedFor() throws Exception
    {
        when(this.releaseNoteManager.getAggregatedVersions(RELEASE_NOTE))
            .thenReturn(List.of("8.3", "8.3-milestone%", "8.3-rc%"));

        this.resource.getChanges("xwiki", PRODUCT, VERSION, null, null, null, null, true, null, null);

        assertEquals(List.of(like("8.3"), like("8.3-milestone%"), like("8.3-rc%")), capturedQuery().getVersions());
    }

    /**
     * A filter the client leaves out filters nothing, whereas a filter it leaves empty lists no value and keeps no
     * change, as the getChanges wiki macro does.
     */
    @Test
    void aFilterThatIsNotAskedForIsNotPassedOn() throws Exception
    {
        this.resource.getChanges("xwiki", PRODUCT, VERSION, null, "", null, null, false, null, "");

        ChangeQuery query = capturedQuery();
        ChangeQuery unfiltered = new ChangeQuery();

        assertEquals(unfiltered.getAudiences(), query.getAudiences());
        assertEquals(List.of(), query.getCategories());
        assertEquals(unfiltered.getImportances(), query.getImportances());
        assertNull(query.getContainsScreenshots());
        assertEquals(ChangeQuery.DEFAULT_LIMIT, query.getLimit());
        assertEquals(0, query.getOffset());
    }

    /**
     * A filter value that names nothing that exists is a filter matching no change, and a paging value that is not
     * usable gets the default one, as in the getChanges wiki macro: nothing is refused.
     */
    @Test
    void anUnreadableFilterIsNotRefused() throws Exception
    {
        this.resource.getChanges("xwiki", PRODUCT, VERSION, "nobody", null, "huge", "maybe", false, "ten", "-3");

        ChangeQuery query = capturedQuery();

        assertEquals(List.of(like("nobody")), query.getAudiences());
        assertEquals(List.of(like("huge")), query.getImportances());
        assertNull(query.getContainsScreenshots());
        assertEquals(ChangeQuery.DEFAULT_LIMIT, query.getLimit());
        assertEquals(0, query.getOffset());
    }

    @Test
    void theChangesOfAReleaseNoteThatNamesNoProductAreRefused()
    {
        WebApplicationException exception = assertThrows(WebApplicationException.class,
            () -> this.resource.getChanges("xwiki", " ", VERSION, null, null, null, null, false, null, null));

        assertRefusal(exception.getResponse(), Response.Status.BAD_REQUEST, NO_RELEASE_NOTE_IN_URL);
    }

    /**
     * A new change is written to an entry page of its release note that is only known once taken, so the right
     * checked is the right to edit the release note.
     */
    @Test
    void aUserWhoCannotEditTheReleaseNoteCreatesNoChange() throws Exception
    {
        when(this.authorization.hasAccess(Right.EDIT, RELEASE_NOTE)).thenReturn(false);
        ChangeRepresentation posted = new ChangeRepresentation();
        posted.setTitle("The title");

        ReleaseNotesAccessDeniedException exception = assertThrows(ReleaseNotesAccessDeniedException.class,
            () -> this.resource.createChange(this.uriInfo, "xwiki", PRODUCT, VERSION, posted));

        assertEquals(RELEASE_NOTE, exception.getReference());
        // The right is checked before what was posted is read: a user who may not add a change is told so, and not
        // what is wrong with the change.
        assertThrows(ReleaseNotesAccessDeniedException.class,
            () -> this.resource.createChange(this.uriInfo, "xwiki", PRODUCT, VERSION, new ChangeRepresentation()));
        verify(this.changeManager, never()).createChange(any());
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
        when(this.noteDocument.isNew()).thenReturn(true);

        ChangeRepresentation posted = new ChangeRepresentation();
        posted.setTitle("The title");

        Response response = this.resource.createChange(this.uriInfo, "xwiki", PRODUCT, VERSION, posted);

        assertRefusal(response, Response.Status.NOT_FOUND,
            String.format("There is no release note for the version [%s] of [XWiki].", VERSION));
        assertEquals("ReleaseNotes.Data.XWiki.8\\.3.WebHome",
            ((ErrorRepresentation) response.getEntity()).getReference());
        // The release note is looked for before the change is read, as it always was.
        assertRefusal(this.resource.createChange(this.uriInfo, "xwiki", PRODUCT, VERSION, new ChangeRepresentation()),
            Response.Status.NOT_FOUND, String.format("There is no release note for the version [%s] of [XWiki].",
                VERSION));
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
        when(this.documentStore.load(RELEASE_NOTE)).thenThrow(new ReleaseNotesException(
            "Failed to load the page [xwiki:ReleaseNotes.Data.XWiki.8\\.3.WebHome]."));

        ChangeRepresentation posted = new ChangeRepresentation();
        posted.setTitle("The title");

        ReleaseNotesException exception = assertThrows(ReleaseNotesException.class,
            () -> this.resource.createChange(this.uriInfo, "xwiki", PRODUCT, VERSION, posted));

        assertFalse(exception instanceof ReleaseNotesNotFoundException, "A broken store is not an empty one.");
        verify(this.changeManager, never()).createChange(any());
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

    private ChangeQuery capturedQuery() throws Exception
    {
        ArgumentCaptor<ChangeQuery> captor = ArgumentCaptor.forClass(ChangeQuery.class);
        verify(this.changeManager).searchAndLoad(captor.capture(), any());

        return captor.getValue();
    }

    private static ChangeFilter exactly(String value)
    {
        return new ChangeFilter(ChangeFilter.Operator.EQUALS, value);
    }

    private static ChangeFilter like(String value)
    {
        return new ChangeFilter(ChangeFilter.Operator.LIKE, value);
    }

    private void assertRefusal(Response response, Response.Status status, String message)
    {
        assertEquals(status.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity() instanceof ErrorRepresentation,
            "A refused request is answered with why it was refused.");
        assertEquals(message, ((ErrorRepresentation) response.getEntity()).getMessage());
    }

    private static LoadedChangeSearchResult searchResult(boolean hasMore)
    {
        return new LoadedChangeSearchResult(new ChangeSearchResult(
            List.of("ReleaseNotes.Data.XWiki.8\\.3.Entry001.WebHome"), List.of(ENTRY), hasMore), List.of(change()));
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
