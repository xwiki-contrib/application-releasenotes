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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.ChangeFilter;
import org.xwiki.contrib.releasenotes.ChangeManager;
import org.xwiki.contrib.releasenotes.ChangeQuery;
import org.xwiki.contrib.releasenotes.ChangeQueryParser;
import org.xwiki.contrib.releasenotes.LoadedChangeSearchResult;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.ReleaseNotesNotFoundException;
import org.xwiki.contrib.releasenotes.rest.ChangesResource;
import org.xwiki.contrib.releasenotes.rest.model.ChangeRepresentation;
import org.xwiki.contrib.releasenotes.rest.model.ChangesRepresentation;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rest.XWikiRestComponent;

/**
 * Default implementation of {@link ChangesResource}.
 *
 * @version $Id$
 * @since 2.7
 */
@Component
@Named("org.xwiki.contrib.releasenotes.rest.internal.DefaultChangesResource")
@Singleton
public class DefaultChangesResource extends AbstractReleaseNotesResource
    implements ChangesResource, XWikiRestComponent
{
    @Inject
    private ChangeManager changeManager;

    @Inject
    private ReleaseNoteManager releaseNoteManager;

    @Inject
    private ChangeQueryParser changeQueryParser;

    @Inject
    private RepresentationFactory representationFactory;

    @Override
    public ChangesRepresentation getChanges(String wikiName, String product, String version, String audience,
        String category, String importance, String containsScreenshots, boolean aggregated, String limit,
        String offset) throws ReleaseNotesException
    {
        return inWiki(wikiName, () -> {
            if (StringUtils.isBlank(product) || StringUtils.isBlank(version)) {
                throw new WebApplicationException(refuse(Response.Status.BAD_REQUEST, NO_RELEASE_NOTE_IN_URL));
            }

            // The filters a client passes are written in the syntax of the getChanges wiki macro, which the parser
            // reads for both.
            Map<String, Object> filters = new HashMap<>();
            filters.put(ChangeQueryParser.AUDIENCE, audience);
            filters.put(ChangeQueryParser.CATEGORIES, category);
            filters.put(ChangeQueryParser.IMPORTANCE, importance);
            filters.put(ChangeQueryParser.CONTAINS_SCREENSHOTS, containsScreenshots);
            filters.put(ChangeQueryParser.LIMIT, limit);
            filters.put(ChangeQueryParser.OFFSET, offset);
            ChangeQuery query = this.changeQueryParser.parse(filters);
            // The product and the version are the release note of the URL, and not something a client filters: the
            // changes of another release note are reached through the URL of that release note.
            query.setProducts(List.of(exactly(product)));
            query.setVersions(getVersions(product, version, aggregated));

            // The changes are read by the search that found them, which has already checked that the current user may
            // view them: reading each of them back here would check that again, once per change.
            LoadedChangeSearchResult result = this.entryPoint.searchAndLoad(query, getCaller());
            ChangesRepresentation representation = new ChangesRepresentation();
            representation.setHasMore(result.hasMore());

            for (int i = 0; i < result.getChanges().size(); i++) {
                representation.getChanges().add(this.representationFactory
                    .toRepresentation(result.getLoadedChanges().get(i), result.getChanges().get(i)));
            }

            return representation;
        });
    }

    @Override
    public Response createChange(UriInfo uriInfo, String wikiName, String product, String version,
        ChangeRepresentation change) throws ReleaseNotesException
    {
        return inWiki(wikiName, () -> {
            if (StringUtils.isBlank(product) || StringUtils.isBlank(version)) {
                return refuse(Response.Status.BAD_REQUEST, NO_RELEASE_NOTE_IN_URL);
            }

            DocumentReference reference;

            try {
                // The change is read only once the current user is known to be allowed to add one to the release note
                // of the URL and that release note is known to exist, so that a client that may not add a change is
                // told so before it is told what is wrong with the change it sent.
                reference = this.entryPoint.createChange(product, version, () -> toChange(change, product, version),
                    getCaller());
            } catch (WebApplicationException e) {
                // What was posted is not a change that can be created.
                return e.getResponse();
            } catch (ReleaseNotesNotFoundException e) {
                if (!this.releaseNoteManager.getReleaseNoteReference(product, version).equals(e.getReference())) {
                    throw e;
                }

                // The release note of the URL does not exist, which is answered as the other mistakes of the request
                // are, in the representation the client asked for.
                return refuse(Response.Status.NOT_FOUND, String
                    .format("There is no release note for the version [%s] of [%s].", version, product),
                    e.getReference());
            }

            // The change is read back rather than echoed, because creation is template-driven: a property the client
            // left out holds the value the change template gives it, and not the null the request carried.
            return Response.created(getPageUri(uriInfo, reference))
                .entity(this.representationFactory.toRepresentation(this.changeManager.getChange(reference),
                    reference))
                .build();
        });
    }

    /**
     * @param change the change the client sent
     * @param product the product of the release note of the URL
     * @param version the version of that release note, in its long form
     * @return the change to create
     * @throws WebApplicationException answering the client with a 400 when the change has no title, or holds a value
     *             that cannot be read
     */
    private Change toChange(ChangeRepresentation change, String product, String version)
    {
        if (change == null || StringUtils.isBlank(change.getTitle())) {
            throw new WebApplicationException(refuse(Response.Status.BAD_REQUEST, "A change needs a title."));
        }

        try {
            return this.representationFactory.toChange(change, product, version);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(refuse(Response.Status.BAD_REQUEST, e.getMessage()));
        }
    }

    /**
     * @param product the product of the release note of the URL
     * @param version the version of that release note, in its long form
     * @param aggregated whether the changes of the milestones and of the release candidates of that version are
     *            asked for too
     * @return the version filters that release note is read with
     */
    private List<ChangeFilter> getVersions(String product, String version, boolean aggregated)
    {
        if (!aggregated) {
            return List.of(exactly(version));
        }

        // A final version gathers the changes of its milestones and of its release candidates, which are matched as
        // patterns, so these filters are left to mean what they say rather than made exact.
        List<ChangeFilter> filters = new ArrayList<>();
        for (String aggregatedVersion : this.releaseNoteManager
            .getAggregatedVersions(this.releaseNoteManager.getReleaseNoteReference(product, version))) {
            filters.add(new ChangeFilter(ChangeFilter.Operator.LIKE, aggregatedVersion));
        }

        return filters;
    }

    /**
     * @param value a value from the URL
     * @return the filter matching that value and nothing else, since a value taken from the URL names what it names
     *         rather than a pattern of it
     */
    private static ChangeFilter exactly(String value)
    {
        return new ChangeFilter(ChangeFilter.Operator.EQUALS, value);
    }
}
