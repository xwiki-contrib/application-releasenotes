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
package org.xwiki.contrib.releasenotes.internal;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import jakarta.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.xwiki.contrib.releasenotes.ChangeFilter;
import org.xwiki.contrib.releasenotes.ChangeFilter.Operator;
import org.xwiki.contrib.releasenotes.ChangeQuery;
import org.xwiki.contrib.releasenotes.ChangeSearchResult;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChangeSearcher}, covering what the page test of the {@code getChanges} macro cannot reach:
 * the query the versions of the wiki are read with, and the page the result is cut into.
 *
 * @version $Id$
 */
@ComponentTest
class ChangeSearcherTest
{
    /**
     * The statement the versions the wiki holds a release note for are read with. Only the version of each release
     * note is selected, and each of them only once.
     */
    private static final String EXISTING_VERSIONS_STATEMENT =
        "select distinct note.version from Document doc, doc.object(ReleaseNotes.Code.ReleaseNoteClass) as note";

    private static final String CHANGE = "ReleaseNotes.Data.XWiki.8.3.Entry001.WebHome";

    @InjectMockComponents
    private ChangeSearcher searcher;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @MockComponent
    @Named("local")
    private EntityReferenceSerializer<String> localEntityReferenceSerializer;

    /**
     * The pages the filter of a search refuses, which is how a caller leaves out the changes it cannot view.
     */
    private final Set<DocumentReference> refused = new HashSet<>();

    private final Predicate<DocumentReference> filter = reference -> !this.refused.contains(reference);

    @Mock
    private Query query;

    @Mock
    private Query existingVersionsQuery;

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.localEntityReferenceSerializer.serialize(ReleaseNotesReferences.ENTRY_CLASS))
            .thenReturn("ReleaseNotes.Code.EntryClass");
        when(this.localEntityReferenceSerializer.serialize(ReleaseNotesReferences.CHANGE_CLASS))
            .thenReturn("ReleaseNotes.Code.Change.ChangeClass");
        when(this.localEntityReferenceSerializer.serialize(ReleaseNotesReferences.RELEASE_NOTE_CLASS))
            .thenReturn("ReleaseNotes.Code.ReleaseNoteClass");

        when(this.queryManager.createQuery(anyString(), anyString())).thenAnswer(invocation -> {
            String statement = invocation.getArgument(0);
            return statement.startsWith("select") ? this.existingVersionsQuery : this.query;
        });
        when(this.query.bindValue(anyString(), any())).thenReturn(this.query);
        when(this.query.execute()).thenReturn(List.of());
        when(this.existingVersionsQuery.execute()).thenReturn(List.of());
    }

    /**
     * The versions the wiki holds a release note for are read in one query, which selects the version of each of
     * them and nothing else.
     */
    @Test
    void theVersionsOfTheWikiAreReadWithOneQuery() throws Exception
    {
        doReturn(Arrays.asList("8.3", "9.0", "10.0")).when(this.existingVersionsQuery).execute();

        this.searcher.search(versionQuery(new ChangeFilter(Operator.GTE, "9.0")), this.filter);

        verify(this.queryManager).createQuery(EXISTING_VERSIONS_STATEMENT, Query.XWQL);
        // The comparison is resolved into the existing versions it matches, so the search itself only tests
        // equality, and 10.0 is one of them since it comes after 9.0 as a version.
        verify(this.query).bindValue("version1", "9.0");
        verify(this.query).bindValue("version2", "10.0");
    }

    /**
     * A comparison is resolved with the version order and not the alphabetical one, so that {@code >=9.0} keeps
     * 10.0, which comes after 9.0 as a version but before it as a string. The bound is part of the {@code >=} and
     * {@code <=} results, and not of the {@code >} and {@code <} ones.
     */
    @ParameterizedTest
    @CsvSource({
        "GTE, '9.0,10.0'",
        "LTE, '8.3,9.0'",
        "GT,  '10.0'",
        "LT,  '8.3'"
    })
    void aComparisonIsResolvedWithTheVersionOrder(Operator operator, String expectedVersions) throws Exception
    {
        doReturn(Arrays.asList("8.3", "9.0", "10.0")).when(this.existingVersionsQuery).execute();

        this.searcher.search(versionQuery(new ChangeFilter(operator, "9.0")), this.filter);

        String[] versions = expectedVersions.split(",");

        for (int index = 0; index < versions.length; index++) {
            verify(this.query).bindValue("version" + (index + 1), versions[index]);
        }

        verify(this.query, never()).bindValue("version" + (versions.length + 1), null);
    }

    /**
     * A filter is bound to a parameter of its own property, so that the values of one property never restrict
     * another one.
     */
    @Test
    void aFilterOnEveryPropertyIsBoundToItsOwnParameter() throws Exception
    {
        ChangeQuery query = new ChangeQuery();
        query.setProducts(List.of(new ChangeFilter(Operator.LIKE, "XWiki")));
        query.setVersions(List.of(new ChangeFilter(Operator.EQUALS, "8.3")));
        query.setAudiences(List.of(new ChangeFilter(Operator.LIKE, "user")));
        query.setCategories(List.of(new ChangeFilter(Operator.LIKE, "UI")));
        query.setImportances(List.of(new ChangeFilter(Operator.LIKE, "2")));

        this.searcher.search(query, this.filter);

        verify(this.query).bindValue("product1", "XWiki");
        verify(this.query).bindValue("version1", "8.3");
        verify(this.query).bindValue("audience1", "user");
        verify(this.query).bindValue("category1", "UI");
        verify(this.query).bindValue("importance1", "2");
    }

    /**
     * Whether a change is illustrated is not a value to compare but a condition of its own, and the two values of
     * the filter must select complementary sets of changes: a release note leads with the changes illustrated by a
     * screenshot and lists all the others under "Miscellaneous", so a change missing from both would never be
     * displayed.
     */
    @ParameterizedTest
    @CsvSource({
        "true,  false",
        "false, true"
    })
    void theScreenshotFilterIsAConditionOfItsOwn(boolean containsScreenshots, boolean expectedNegation)
        throws Exception
    {
        ChangeQuery query = new ChangeQuery();
        query.setContainsScreenshots(containsScreenshots);

        this.searcher.search(query, this.filter);

        ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
        verify(this.queryManager).createQuery(statement.capture(), anyString());
        assertTrue(statement.getValue().contains("changes.screenshots"), statement.getValue());
        assertEquals(expectedNegation, statement.getValue().contains("and not ("), statement.getValue());
    }

    /**
     * A release note whose version was left empty says nothing about which versions the wiki holds, and must not
     * become a version a comparison matches.
     */
    @Test
    void aReleaseNoteWithoutAVersionIsLeftOut() throws Exception
    {
        doReturn(Arrays.asList("", "  ", "9.0")).when(this.existingVersionsQuery).execute();

        this.searcher.search(versionQuery(new ChangeFilter(Operator.GTE, "1.0")), this.filter);

        verify(this.query).bindValue("version1", "9.0");
        verify(this.query, never()).bindValue("version2", "");
    }

    /**
     * The versions of the wiki are only worth reading when a filter compares versions: a pattern and an exact
     * version are tested by the database itself.
     */
    @Test
    void theVersionsOfTheWikiAreOnlyReadWhenAFilterComparesThem() throws Exception
    {
        this.searcher.search(versionQuery(new ChangeFilter(Operator.LIKE, "8.3%")), this.filter);

        verify(this.queryManager, never()).createQuery(startsWith("select"), anyString());
    }

    /**
     * A comparison no existing version matches leaves the version filter with no value at all, which must return no
     * change rather than every one of them.
     */
    @Test
    void aComparisonMatchingNoExistingVersionMatchesNoChange() throws Exception
    {
        this.searcher.search(versionQuery(new ChangeFilter(Operator.GTE, "99.0")), this.filter);

        verify(this.queryManager).createQuery(startsWith("from doc.object(ReleaseNotes.Code.EntryClass)"),
            anyString());
        verify(this.query, never()).bindValue(startsWith("version"), any());
    }

    /**
     * The change beyond the page the caller asked for is what tells that a next page exists, and it is not part of
     * the page: a caller displaying it would display it twice, once here and once on the next page.
     */
    @Test
    void theChangeBeyondThePageIsReportedRatherThanReturned() throws Exception
    {
        doReturn(List.of("first", CHANGE, "second", "third")).when(this.query).execute();
        DocumentReference reference = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "XWiki", "8.3", "Entry001"), "WebHome");
        when(this.documentReferenceResolver.resolve(CHANGE)).thenReturn(reference);

        ChangeQuery changeQuery = new ChangeQuery();
        changeQuery.setLimit(2);
        changeQuery.setOffset(1);
        ChangeSearchResult result = this.searcher.search(changeQuery, this.filter);

        // The rows the offset skips are read too, since only the ones the filter accepts are counted.
        verify(this.query).setLimit(4);
        verify(this.query).setOffset(0);
        assertEquals(List.of(CHANGE, "second"), result.getChangeNames());
        assertEquals(reference, result.getChanges().get(0));
        assertTrue(result.hasMore());
    }

    /**
     * The changes the filter refuses are left out of the result, and neither the offset nor the limit counts them:
     * the page is still full, and the next batch of rows is read to fill it.
     */
    @Test
    void theChangesTheFilterRefusesAreLeftOut() throws Exception
    {
        Query secondBatch = mock(Query.class);
        when(this.queryManager.createQuery(anyString(), anyString())).thenReturn(this.query, secondBatch);
        when(this.query.bindValue(anyString(), any())).thenReturn(this.query);
        when(secondBatch.bindValue(anyString(), any())).thenReturn(secondBatch);
        doReturn(List.of("hidden1", "visible1", "hidden2")).when(this.query).execute();
        doReturn(List.of("visible2", "visible3")).when(secondBatch).execute();
        for (String name : List.of("hidden1", "hidden2", "visible1", "visible2", "visible3")) {
            DocumentReference reference = new DocumentReference("xwiki", "Space", name);
            when(this.documentReferenceResolver.resolve(name)).thenReturn(reference);
            if (name.startsWith("hidden")) {
                this.refused.add(reference);
            }
        }

        ChangeQuery changeQuery = new ChangeQuery();
        changeQuery.setLimit(1);
        changeQuery.setOffset(1);
        ChangeSearchResult result = this.searcher.search(changeQuery, this.filter);

        verify(secondBatch).setLimit(3);
        verify(secondBatch).setOffset(3);
        assertEquals(List.of("visible2"), result.getChangeNames());
        assertEquals(List.of(new DocumentReference("xwiki", "Space", "visible2")), result.getChanges());
        assertTrue(result.hasMore());
    }

    /**
     * A change the filter refuses is not a reason to announce a next page.
     */
    @Test
    void aChangeTheFilterRefusesDoesNotMakeANextPage() throws Exception
    {
        // The batch is full, so the next one is read, and the database has no more rows to give.
        doReturn(List.of(CHANGE, "hidden"), List.of()).when(this.query).execute();
        DocumentReference hidden = new DocumentReference("xwiki", "Space", "hidden");
        when(this.documentReferenceResolver.resolve("hidden")).thenReturn(hidden);
        this.refused.add(hidden);

        ChangeQuery changeQuery = new ChangeQuery();
        changeQuery.setLimit(1);
        ChangeSearchResult result = this.searcher.search(changeQuery, this.filter);

        assertEquals(List.of(CHANGE), result.getChangeNames());
        assertFalse(result.hasMore());
    }

    /**
     * An excluded change is left out before the page is cut: it takes the place of no change that follows it, and a
     * page left with nothing beyond it but excluded changes does not announce a next one.
     */
    @Test
    void theExcludedChangesAreLeftOutBeforeThePageIsCut() throws Exception
    {
        // The batch is full, so the next one is read, and the database has no more rows to give.
        doReturn(List.of("excluded", CHANGE, "second"), List.of()).when(this.query).execute();
        DocumentReference excluded = new DocumentReference("xwiki", "Space", "excluded");
        DocumentReference change = new DocumentReference("xwiki", "Space", "change");
        DocumentReference second = new DocumentReference("xwiki", "Space", "second");
        when(this.documentReferenceResolver.resolve("excluded")).thenReturn(excluded);
        when(this.documentReferenceResolver.resolve(CHANGE)).thenReturn(change);
        when(this.documentReferenceResolver.resolve("second")).thenReturn(second);

        ChangeQuery changeQuery = new ChangeQuery();
        changeQuery.setLimit(2);
        changeQuery.setExclusions(Set.of(excluded));
        ChangeSearchResult result = this.searcher.search(changeQuery, this.filter);

        assertEquals(List.of(CHANGE, "second"), result.getChangeNames());
        assertEquals(List.of(change, second), result.getChanges());
        assertFalse(result.hasMore());
    }

    /**
     * The getChanges macro publishes the list of the result to wiki pages, which may take changes out of it, so that
     * list has to be theirs to modify.
     */
    @Test
    void theChangesOfTheResultMayBeRemovedFromIt() throws Exception
    {
        doReturn(List.of(CHANGE, "second")).when(this.query).execute();

        ChangeSearchResult result = this.searcher.search(new ChangeQuery(), this.filter);

        assertTrue(result.getChangeNames().removeAll(List.of("second")));
        assertEquals(List.of(CHANGE), result.getChangeNames());
        assertFalse(result.hasMore());
    }

    /**
     * A search that cannot be run is not a search that found nothing, so it is reported rather than turned into an
     * empty release note.
     */
    @Test
    void aSearchThatCannotBeRunIsReported() throws Exception
    {
        when(this.query.execute()).thenThrow(new QueryException("Broken", null, null));

        assertThrows(ReleaseNotesException.class, () -> this.searcher.search(new ChangeQuery(), this.filter));
    }

    /**
     * The versions of the wiki are read to resolve a comparison, so a search that cannot read them is a search that
     * cannot be resolved, and is reported rather than answered with the wrong changes.
     */
    @Test
    void aSearchThatCannotReadTheVersionsIsReported() throws Exception
    {
        when(this.existingVersionsQuery.execute()).thenThrow(new QueryException("Broken", null, null));

        assertThrows(ReleaseNotesException.class,
            () -> this.searcher.search(versionQuery(new ChangeFilter(Operator.GTE, "9.0")), this.filter));
    }

    /**
     * @param filter the only version filter of the query
     * @return a query filtering nothing but the version
     */
    private ChangeQuery versionQuery(ChangeFilter filter)
    {
        ChangeQuery query = new ChangeQuery();
        query.setVersions(List.of(filter));

        return query;
    }
}
