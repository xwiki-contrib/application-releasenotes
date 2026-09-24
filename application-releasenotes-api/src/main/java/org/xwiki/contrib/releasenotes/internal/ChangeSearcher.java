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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    private static final String PRODUCT = "product";

    private static final String VERSION = "version";

    private static final String AUDIENCE = "audience";

    private static final String CATEGORY = "category";

    private static final String IMPORTANCE = "importance";

    private static final String SCREENSHOTS = "screenshots";

    private static final String MIGRATION_NOTES = "migrationNotes";

    /**
     * The condition matching the changes that carry migration notes. The notes are a large string, which some
     * databases cannot compare with {@code <>} and give back as null rather than as the empty string, and whose length
     * is null, and thus not greater than zero, in both cases.
     */
    private static final String NOTED_FORMAT = "length(%s.%s) > 0";

    /**
     * How many page names a single {@code not in} list holds at most, below the thousand elements some databases
     * accept in such a list.
     */
    private static final int NAMES_PER_LIST = 500;

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

    private static final String NOT = "not ";

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
     * @return the page of the matching changes the query asks for, and whether more of them matched
     * @throws ReleaseNotesException when the changes could not be looked up
     */
    public ChangeSearchResult search(ChangeQuery query) throws ReleaseNotesException
    {
        List<String> conditions = new ArrayList<>();
        Map<String, String> bindings = new LinkedHashMap<>();

        addFilters(conditions, bindings, ENTRY_ALIAS, PRODUCT, query.getProducts());
        addFilters(conditions, bindings, CHANGE_ALIAS, AUDIENCE, query.getAudiences());
        addFilters(conditions, bindings, ENTRY_ALIAS, VERSION, resolveVersions(query.getVersions()));
        addFilters(conditions, bindings, CHANGE_ALIAS, CATEGORY, query.getCategories());
        addFilters(conditions, bindings, CHANGE_ALIAS, IMPORTANCE, query.getImportances());

        if (query.getContainsScreenshots() != null) {
            String illustrated = String.format(ILLUSTRATED_FORMAT, CHANGE_ALIAS, SCREENSHOTS);
            conditions.add(query.getContainsScreenshots() ? illustrated : NOT + illustrated);
        }

        if (query.getContainsMigrationNotes() != null) {
            addMigrationNotesFilter(conditions, bindings, query.getContainsMigrationNotes());
        }

        if (query.getReleased() != null) {
            addReleasedFilter(conditions, bindings, query.getReleased());
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
            CHANGE_ALIAS, IMPORTANCE);

        return executeSearch(statement, bindings, query);
    }

    /**
     * Filters on whether the changes carry migration notes. Asking for the ones carrying some is a condition on the
     * notes themselves. Asking for the ones carrying none cannot be the negation of that condition: a property is
     * joined in rather than read, so a change saved before its class had migration notes, which holds no such
     * property at all, would match neither. The pages carrying notes are therefore looked up first, and left out.
     */
    private void addMigrationNotesFilter(List<String> conditions, Map<String, String> bindings,
        boolean containsMigrationNotes) throws ReleaseNotesException
    {
        if (containsMigrationNotes) {
            conditions.add(String.format(NOTED_FORMAT, CHANGE_ALIAS, MIGRATION_NOTES));
            return;
        }

        String statement = String.format("select doc.fullName from Document doc, doc.object(%s) as %s where %s",
            serialize(ReleaseNotesReferences.CHANGE_CLASS), CHANGE_ALIAS,
            String.format(NOTED_FORMAT, CHANGE_ALIAS, MIGRATION_NOTES));
        List<String> notedNames = executeLookup(statement, "the changes carrying migration notes");
        int index = 0;

        for (int start = 0; start < notedNames.size(); start += NAMES_PER_LIST) {
            List<String> parameters = new ArrayList<>();

            for (String name : notedNames.subList(start, Math.min(notedNames.size(), start + NAMES_PER_LIST))) {
                index++;
                String parameter = "noted" + index;
                parameters.add(":" + parameter);
                bindings.put(parameter, name);
            }

            conditions.add(String.format("doc.fullName not in (%s)", String.join(", ", parameters)));
        }
    }

    /**
     * Filters on whether the release note of the product and of the version of each change is marked released. The
     * release notes marked released are looked up first rather than joined in, since a change whose version has no
     * release note is not released, and a join would leave such a change out of both answers. Both the product and
     * the version are matched, since two products may have released the same version number.
     */
    private void addReleasedFilter(List<String> conditions, Map<String, String> bindings, boolean released)
        throws ReleaseNotesException
    {
        String statement = String.format(
            "select distinct note.%1$s, note.%2$s from Document doc, doc.object(%3$s) as note where note.released = 1",
            PRODUCT, VERSION, serialize(ReleaseNotesReferences.RELEASE_NOTE_CLASS));
        List<Object[]> releasedNotes = executeLookup(statement, "the released versions");
        List<String> noteConditions = new ArrayList<>();
        int index = 0;

        for (Object[] releasedNote : releasedNotes) {
            index++;
            String productParameter = "releasedProduct" + index;
            String versionParameter = "releasedVersion" + index;
            noteConditions.add(String.format("(%1$s.%2$s = :%3$s and %1$s.%4$s = :%5$s)", ENTRY_ALIAS, PRODUCT,
                productParameter, VERSION, versionParameter));
            bindings.put(productParameter, Objects.toString(releasedNote[0], ""));
            bindings.put(versionParameter, Objects.toString(releasedNote[1], ""));
        }

        if (noteConditions.isEmpty()) {
            // No version is released, so every change is an unreleased one.
            if (released) {
                conditions.add(MATCHES_NOTHING);
            }
            return;
        }

        String releasedCondition = anyOf(noteConditions);
        conditions.add(released ? releasedCondition : NOT + releasedCondition);
    }

    /**
     * @return the condition matching what any of the passed conditions matches
     */
    private static String anyOf(List<String> conditions)
    {
        return "(" + String.join(" or ", conditions) + ")";
    }

    private <T> List<T> executeLookup(String statement, String lookedUp) throws ReleaseNotesException
    {
        try {
            return this.queryManager.createQuery(statement, Query.XWQL).execute();
        } catch (QueryException e) {
            throw new ReleaseNotesException(String.format("Failed to look up %s of this wiki.", lookedUp), e);
        }
    }

    /**
     * Runs the search and cuts the page of changes out of what it returned.
     */
    private ChangeSearchResult executeSearch(String statement, Map<String, String> bindings, ChangeQuery query)
        throws ReleaseNotesException
    {
        List<String> rows;

        try {
            Query databaseQuery = this.queryManager.createQuery(statement, Query.XWQL);
            bindings.forEach(databaseQuery::bindValue);
            // One row beyond the page is asked for, which tells whether a next page exists without a second,
            // counting query. It is never part of the result.
            databaseQuery.setLimit(query.getLimit() + 1);
            databaseQuery.setOffset(query.getOffset());
            rows = databaseQuery.execute();
        } catch (QueryException e) {
            throw new ReleaseNotesException("Failed to look up the changes of this wiki.", e);
        }

        int pageSize = Math.min(rows.size(), query.getLimit());
        // The page is copied out rather than the result trimmed in place, so that the caller may modify the list it
        // is given: the pages displaying the changes of a release note take their own exclusions out of it.
        List<String> names = new ArrayList<>(rows.subList(0, pageSize));
        List<DocumentReference> references = new ArrayList<>(pageSize);

        for (String name : names) {
            references.add(this.documentReferenceResolver.resolve(name));
        }

        return new ChangeSearchResult(names, references, rows.size() > query.getLimit());
    }

    /**
     * Turns the filters of one property into the condition matching them, which the query tests as a whole, and into
     * the values that condition compares to.
     */
    private void addFilters(List<String> conditions, Map<String, String> bindings, String alias, String property,
        List<ChangeFilter> filters)
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

        if (filterConditions.isEmpty()) {
            filterConditions.add(MATCHES_NOTHING);
        }

        conditions.add(anyOf(filterConditions));
    }

    /**
     * Replaces every version filter comparing versions by the list of the existing versions it matches, so that the
     * query itself only ever tests a version for equality.
     * <p>
     * The comparison cannot be left to the database: the version of an entry is stored in a string column, so
     * {@code >=9.0} would leave 10.0 out and the outcome would depend on the collation of the database on top of
     * that. It is made here with the extension version scheme, which knows that 9.0 comes before 10.0 and that the
     * milestones and the release candidates of a version come before that version.
     *
     * @param versions the version filters a caller asked for
     * @return those filters, with the ones comparing versions resolved
     */
    private List<ChangeFilter> resolveVersions(List<ChangeFilter> versions) throws ReleaseNotesException
    {
        if (versions.stream().noneMatch(filter -> filter.getOperator().isComparison())) {
            return versions;
        }

        List<String> existingVersions = getExistingVersions();
        List<ChangeFilter> resolved = new ArrayList<>();

        for (ChangeFilter filter : versions) {
            if (!filter.getOperator().isComparison()) {
                resolved.add(filter);
                continue;
            }

            Version bound = new DefaultVersion(filter.getValue());

            for (String existingVersion : existingVersions) {
                if (matches(new DefaultVersion(existingVersion).compareTo(bound), filter.getOperator())) {
                    resolved.add(new ChangeFilter(ChangeFilter.Operator.EQUALS, existingVersion));
                }
            }
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
        String statement = String.format("select distinct note.%s from Document doc, doc.object(%s) as note", VERSION,
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
