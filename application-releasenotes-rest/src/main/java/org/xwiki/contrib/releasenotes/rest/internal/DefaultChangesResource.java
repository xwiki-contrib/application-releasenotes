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

import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
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
import org.xwiki.contrib.releasenotes.ChangeSearchResult;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.rest.ChangesResource;
import org.xwiki.contrib.releasenotes.rest.model.ChangeRepresentation;
import org.xwiki.contrib.releasenotes.rest.model.ChangesRepresentation;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rest.XWikiRestComponent;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;

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

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Override
    public ChangesRepresentation getChanges(String wikiName, String product, String version, String audience,
        String category, String importance, String containsScreenshots, String containsMigrationNotes,
        String released, boolean aggregated, String limit, String offset) throws ReleaseNotesException
    {
        return inWiki(wikiName, () -> {
            if (StringUtils.isBlank(product) || StringUtils.isBlank(version)) {
                throw new WebApplicationException(refuse(Response.Status.BAD_REQUEST, NO_RELEASE_NOTE_IN_URL));
            }

            Map<String, Object> parameters = new HashMap<>();
            parameters.put(ChangeQueryParser.AUDIENCE, audience);
            parameters.put(ChangeQueryParser.CATEGORIES, category);
            parameters.put(ChangeQueryParser.IMPORTANCE, importance);
            parameters.put(ChangeQueryParser.CONTAINS_SCREENSHOTS, containsScreenshots);
            parameters.put(ChangeQueryParser.CONTAINS_MIGRATION_NOTES, containsMigrationNotes);
            parameters.put(ChangeQueryParser.RELEASED, released);
            parameters.put(ChangeQueryParser.LIMIT, limit);
            parameters.put(ChangeQueryParser.OFFSET, offset);
            // The product and the version are the release note of the URL, and not something a client filters: the
            // changes of another release note are reached through the URL of that release note.
            parameters.put(ChangeQueryParser.PRODUCTS, exactly(product));
            parameters.put(ChangeQueryParser.VERSIONS, getVersions(product, version, aggregated));

            ChangeQuery query = this.changeQueryParser.parse(parameters);
            ChangeSearchResult result = this.changeManager.search(query);
            ChangesRepresentation representation = new ChangesRepresentation();
            representation.setHasMore(result.hasMore());

            for (DocumentReference reference : result.getChanges()) {
                representation.getChanges()
                    .add(this.representationFactory.toRepresentation(this.changeManager.getChange(reference),
                        reference));
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

            DocumentReference noteReference = this.releaseNoteManager.getReleaseNoteReference(product, version);

            if (!exists(noteReference)) {
                return refuse(Response.Status.NOT_FOUND, String
                    .format("There is no release note for the version [%s] of [%s].", version, product),
                    noteReference);
            }

            if (change == null || StringUtils.isBlank(change.getTitle())) {
                return refuse(Response.Status.BAD_REQUEST, "A change needs a title.");
            }

            Change created;

            try {
                created = this.representationFactory.toChange(change, product, version);
            } catch (IllegalArgumentException e) {
                return refuse(Response.Status.BAD_REQUEST, e.getMessage());
            }

            DocumentReference reference = this.changeManager.createChange(created);

            // The change is read back rather than echoed, because creation is template-driven: a property the client
            // left out holds the value the change template gives it, and not the null the request carried.
            return Response.created(getPageUri(uriInfo, reference))
                .entity(this.representationFactory.toRepresentation(this.changeManager.getChange(reference),
                    reference))
                .build();
        });
    }

    /**
     * @param product the product of the release note of the URL
     * @param version the version of that release note, in its long form
     * @param aggregated whether the changes of the milestones and of the release candidates of that version are
     *            asked for too
     * @return the version filter that release note is read with
     */
    private String getVersions(String product, String version, boolean aggregated)
    {
        if (!aggregated) {
            return exactly(version);
        }

        // A final version gathers the changes of its milestones and of its release candidates, which are matched as
        // patterns, so this filter is left to mean what it says rather than made exact.
        return String.join(",",
            this.releaseNoteManager.getAggregatedVersions(
                this.releaseNoteManager.getReleaseNoteReference(product, version)));
    }

    /**
     * @param value a value from the URL
     * @return the filter matching that value and nothing else, since a value taken from the URL names one release
     *         note rather than a pattern of them
     */
    private static String exactly(String value)
    {
        return ChangeFilter.Operator.EQUALS.getSyntax() + value;
    }

    /**
     * @param reference the page of a release note
     * @return whether that page exists
     * @throws ReleaseNotesException when the store could not be asked
     */
    private boolean exists(DocumentReference reference) throws ReleaseNotesException
    {
        XWikiContext xcontext = this.xcontextProvider.get();

        try {
            return xcontext.getWiki().exists(reference, xcontext);
        } catch (XWikiException e) {
            throw new ReleaseNotesException(String.format("Failed to look up the page [%s].", reference), e);
        }
    }
}
