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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.ChangeFilter;
import org.xwiki.contrib.releasenotes.ChangeQuery;
import org.xwiki.contrib.releasenotes.ChangeSearchResult;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.extension.version.Version;
import org.xwiki.extension.version.internal.DefaultVersion;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

/**
 * Looks for the changes matching a query, by asking the database for one page of them.
 *
 * @version $Id$
 * @since 2.7
 */
@Component(roles = ChangeSearcher.class)
@Singleton
public class ChangeSearcher
{
    /**
     * The name the entry of a change is joined under, whose class holds where a change belongs.
     */
    private static final String ENTRY_ALIAS = "entries";

    /**
     * The name the change itself is joined under, whose class holds what a change says.
     */
    private static final String CHANGE_ALIAS = "changes";

    /**
     * The condition matching the changes that are illustrated. The media of a change are stored as a large string,
     * which some databases give back as null rather than as the empty string when it was never set, so both are
     * asked about.
     */
    private static final String ILLUSTRATED_FORMAT = "(%1$s.%2$s <> '' or (%1$s.%2$s is not null and '' is null))";

    /**
     * The condition matching no change at all, which is what the filters of a property that has none match. It is
     * spelled out because joining an empty list of conditions would generate "()" and make the whole query invalid.
     */
    private static final String MATCHES_NOTHING = "1 = 0";

    /**
     * The condition keeping, of the objects of one class a page holds, only the one numbered 0.
     */
    private static final String FIRST_OBJECT_FORMAT = "%s.number = 0";

    /**
     * The most rows read from the database at once. A page of changes and the change beyond it fit in one batch
     * when nothing is skipped; the rows an offset skips are read in as many batches as they take.
     */
    private static final int MAX_BATCH_SIZE = ChangeQuery.MAX_LIMIT + 1;

    @Inject
    private QueryManager queryManager;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localEntityReferenceSerializer;

    /**
     * @param query the changes to look for
     * @param filter tells, for the page of a matching change, whether that change is part of the result
     * @return the page of the matching changes the query asks for, and whether more of them matched
     * @throws ReleaseNotesException when the changes could not be looked up
     */
    public ChangeSearchResult search(ChangeQuery query, Predicate<DocumentReference> filter)
        throws ReleaseNotesException
    {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> bindings = new LinkedHashMap<>();

        // A change is one entry object and one change object of its page, and only the objects numbered 0 are
        // joined, which is where the API, the change template and the 1.x migration put them: joining every entry
        // object of a page with every change object of it would match a page holding several of each once per pair
        // of objects. The number is compared to a constant so that the database leaves the other objects out
        // before it joins them.
        conditions.add(String.format(FIRST_OBJECT_FORMAT, ENTRY_ALIAS));
        conditions.add(String.format(FIRST_OBJECT_FORMAT, CHANGE_ALIAS));
        addFilters(conditions, bindings, ENTRY_ALIAS, ChangeXObjects.PRODUCT, query.getProducts());
        addFilters(conditions, bindings, CHANGE_ALIAS, ChangeXObjects.AUDIENCE, query.getAudiences());
        List<ChangeFilter> versionFilters = query.getVersions();
        addFilters(conditions, bindings, ENTRY_ALIAS, ChangeXObjects.VERSION,
            versionFilters.stream().filter(version -> !version.getOperator().isComparison()).toList(),
            resolveVersions(versionFilters));
        addFilters(conditions, bindings, CHANGE_ALIAS, ChangeXObjects.CATEGORY, query.getCategories());
        addFilters(conditions, bindings, CHANGE_ALIAS, ChangeXObjects.IMPORTANCE, query.getImportances());

        if (query.getContainsScreenshots() != null) {
            String illustrated = String.format(ILLUSTRATED_FORMAT, CHANGE_ALIAS, ChangeXObjects.SCREENSHOTS);
            conditions.add(query.getContainsScreenshots() ? illustrated : "not " + illustrated);
        }

        // Both classes of a change are joined: the entry says which release note the change belongs to, and the
        // change itself says what it is about.
        // The changes are ordered on the page they live in on top of their importance: importance only has three
        // values, so it leaves most of the result set unordered, and a page of an unordered result set may repeat
        // or skip changes.
        String statement = String.format(
            "from doc.object(%s) as %s, doc.object(%s) as %s where %s order by %s.%s desc, doc.fullName",
            serialize(ReleaseNotesReferences.ENTRY_CLASS), ENTRY_ALIAS,
            serialize(ReleaseNotesReferences.CHANGE_CLASS), CHANGE_ALIAS, String.join(" and ", conditions),
            CHANGE_ALIAS, ChangeXObjects.IMPORTANCE);

        // The excluded changes are left out along with the ones the filter refuses, i.e. before the page is cut, so
        // that an excluded change neither takes the place of a change that follows it nor makes the search report a
        // next page that holds nothing but excluded changes.
        Set<DocumentReference> exclusions = query.getExclusions();
        Predicate<DocumentReference> accepted =
            exclusions.isEmpty() ? filter : filter.and(reference -> !exclusions.contains(reference));

        return executeSearch(statement, bindings, query, accepted);
    }

    /**
     * Runs the search and cuts the page of changes out of what it returned.
     * <p>
     * The filter cannot be part of the statement, since it is typically a right check the database knows nothing of,
     * so the changes it refuses are left out here, and the offset and the limit of the query count the changes it
     * accepts. That is why the rows are read from the first one, in batches, until the page and the one change beyond
     * it are found: that change tells whether a next page exists without a second, counting query, and it is never
     * part of the result.
     */
    private ChangeSearchResult executeSearch(String statement, Map<String, Object> bindings, ChangeQuery query,
        Predicate<DocumentReference> filter) throws ReleaseNotesException
    {
        int batchSize = (int) Math.min(MAX_BATCH_SIZE, (long) query.getOffset() + query.getLimit() + 1);
        int skipped = 0;
        // The page is a list of its own rather than a view of the rows, so that the caller may modify the list it is
        // given: the getChanges macro publishes it to wiki pages, which may take changes out of it on their own.
        List<String> names = new ArrayList<>();
        List<DocumentReference> references = new ArrayList<>();

        for (int batchOffset = 0;; batchOffset += batchSize) {
            List<String> rows = executeBatch(statement, bindings, batchSize, batchOffset);

            for (String name : rows) {
                DocumentReference reference = this.documentReferenceResolver.resolve(name);

                if (!filter.test(reference)) {
                    continue;
                }

                if (skipped < query.getOffset()) {
                    skipped++;
                } else if (names.size() == query.getLimit()) {
                    return new ChangeSearchResult(names, references, true);
                } else {
                    names.add(name);
                    references.add(reference);
                }
            }

            if (rows.size() < batchSize) {
                return new ChangeSearchResult(names, references, false);
            }
        }
    }

    private List<String> executeBatch(String statement, Map<String, Object> bindings, int limit, int offset)
        throws ReleaseNotesException
    {
        try {
            Query databaseQuery = this.queryManager.createQuery(statement, Query.XWQL);
            bindings.forEach(databaseQuery::bindValue);
            databaseQuery.setLimit(limit);
            databaseQuery.setOffset(offset);

            return databaseQuery.execute();
        } catch (QueryException e) {
            throw new ReleaseNotesException("Failed to look up the changes of this wiki.", e);
        }
    }

    /**
     * Turns the filters of one property into the condition matching them, which the query tests as a whole, and into
     * the values that condition compares to.
     */
    private void addFilters(List<String> conditions, Map<String, Object> bindings, String alias, String property,
        List<ChangeFilter> filters)
    {
        addFilters(conditions, bindings, alias, property, filters, List.of());
    }

    /**
     * Turns the filters of one property, and the values it may be equal to on top of them, into the condition
     * matching them.
     * <p>
     * The values are tested by a single {@code in} condition rather than by one condition each: however many there
     * are, the statement keeps the same depth, whereas a long chain of {@code or} is compiled recursively and a few
     * thousand of them exhaust the stack of the query compiler.
     */
    private void addFilters(List<String> conditions, Map<String, Object> bindings, String alias, String property,
        List<ChangeFilter> filters, Collection<String> values)
    {
        List<String> filterConditions = new ArrayList<>();
        int index = 0;

        for (ChangeFilter filter : filters) {
            index++;
            String parameter = property + index;
            filterConditions
                .add(String.format("%s.%s %s :%s", alias, property, filter.getOperator().getSyntax(), parameter));
            bindings.put(parameter, filter.getValue());
        }

        if (!values.isEmpty()) {
            String parameter = property + "List";
            filterConditions.add(String.format("%s.%s in (:%s)", alias, property, parameter));
            bindings.put(parameter, new ArrayList<>(values));
        }

        if (filterConditions.isEmpty()) {
            filterConditions.add(MATCHES_NOTHING);
        }

        conditions.add("(" + String.join(" or ", filterConditions) + ")");
    }

    /**
     * Resolves the version filters comparing versions into the existing versions they match, so that the query itself
     * only ever tests a version for equality.
     * <p>
     * The comparison cannot be left to the database: the version of an entry is stored in a string column, so
     * {@code >=9.0} would leave 10.0 out and the outcome would depend on the collation of the database on top of
     * that. It is made here with the extension version scheme, which knows that 9.0 comes before 10.0 and that the
     * milestones and the release candidates of a version come before that version.
     * <p>
     * Each existing version is kept once, however many comparisons match it: the filters are alternatives, so a
     * version matched twice is no more matched than a version matched once.
     *
     * @param versions the version filters a caller asked for
     * @return the existing versions matched by the filters comparing versions, in the order the wiki gave them
     */
    private Set<String> resolveVersions(List<ChangeFilter> versions) throws ReleaseNotesException
    {
        List<ChangeFilter> comparisons =
            versions.stream().filter(filter -> filter.getOperator().isComparison()).toList();
        Set<String> resolved = new LinkedHashSet<>();

        if (comparisons.isEmpty()) {
            return resolved;
        }

        Map<String, Version> existingVersions = new LinkedHashMap<>();
        for (String existingVersion : getExistingVersions()) {
            existingVersions.put(existingVersion, new DefaultVersion(existingVersion));
        }

        for (ChangeFilter filter : comparisons) {
            Version bound = new DefaultVersion(filter.getValue());

            existingVersions.forEach((name, version) -> {
                if (matches(version.compareTo(bound), filter.getOperator())) {
                    resolved.add(name);
                }
            });
        }

        return resolved;
    }

    /**
     * @param comparison how an existing version compares to the value of a filter
     * @param operator the operator of that filter
     * @return whether that version is one the filter matches
     */
    private static boolean matches(int comparison, ChangeFilter.Operator operator)
    {
        switch (operator) {
            case GTE:
                return comparison >= 0;
            case LTE:
                return comparison <= 0;
            case GT:
                return comparison > 0;
            case LT:
                return comparison < 0;
            default:
                return false;
        }
    }

    /**
     * @return the versions the wiki holds a release note for, in the form the entries of a release note store them
     * @throws ReleaseNotesException when they could not be looked up
     */
    private List<String> getExistingVersions() throws ReleaseNotesException
    {
        // The versions are read from the release notes, which is where they are written by hand, and only the
        // version of each of them is selected since that is all a comparison needs.
        String statement = String.format("select distinct note.version from Document doc, doc.object(%s) as note",
            serialize(ReleaseNotesReferences.RELEASE_NOTE_CLASS));
        List<String> versions;

        try {
            versions = this.queryManager.createQuery(statement, Query.XWQL).execute();
        } catch (QueryException e) {
            throw new ReleaseNotesException("Failed to look up the versions this wiki holds a release note for.", e);
        }

        List<String> existingVersions = new ArrayList<>(versions.size());

        for (String version : versions) {
            if (StringUtils.isNotBlank(version)) {
                existingVersions.add(version);
            }
        }

        return existingVersions;
    }

    private String serialize(LocalDocumentReference reference)
    {
        return this.localEntityReferenceSerializer.serialize(reference);
    }
}
