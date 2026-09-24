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
package org.xwiki.contrib.releasenotes.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.rest.model.ChangeRepresentation;
import org.xwiki.contrib.releasenotes.rest.model.ChangesRepresentation;
import org.xwiki.stability.Unstable;

/**
 * The changes of one release note.
 * <p>
 * The version in the path is the version itself, in its long form, e.g. {@code 8.3-milestone-1}, and not the name of
 * the page the release note lives in. A client writes both the product and the version URL encoded.
 *
 * @version $Id$
 * @since 2.7
 */
@Path("/wikis/{wikiName}/releasenotes/{product}/{version}/changes")
@Unstable
public interface ChangesResource
{
    /**
     * Lists the changes of one release note, the most important ones first, one page of them at a time.
     * <p>
     * Only the changes stored against the version in the path are returned: a client that has just added a change to
     * {@code 8.3} and asks what {@code 8.3} holds is asking about {@code 8.3}, and the changes of the milestones and
     * of the release candidates its release note also displays would be a different answer. Pass
     * {@code aggregated=true} to get that other answer, which is what the release note itself shows.
     *
     * @param wikiName the wiki holding the release note
     * @param product the product the release note is about
     * @param version the version the release note is about, in its long form
     * @param audience the audiences to keep, comma separated, or {@code null} to keep them all
     * @param category the categories to keep, comma separated, or {@code null} to keep them all
     * @param importance the importances to keep, comma separated, spelled either {@code low}, {@code medium} and
     *            {@code high} or with the numbers they are stored as, or {@code null} to keep them all
     * @param containsScreenshots {@code true} to keep only the changes illustrated by a screenshot or a video,
     *            {@code false} to keep only the ones illustrated by neither, and {@code null} to keep both
     * @param containsMigrationNotes {@code true} to keep only the changes carrying backward compatibility and
     *            migration notes, {@code false} to keep only the ones carrying none, and {@code null} to keep both
     * @param released {@code true} to keep only the changes of the versions marked released, {@code false} to keep
     *            only the ones of the versions that are not, and {@code null} to keep both. It matters when
     *            {@code aggregated} is set, since the milestones and the release candidates of a version are released
     *            one by one
     * @param aggregated whether to also return the changes of the milestones and of the release candidates of the
     *            version, the way its release note displays them
     * @param limit how many changes to return at most, 100 by default
     * @param offset how many of the matching changes to leave out before the ones to return
     * @return the changes of that page of the result, and whether more of them matched
     * @throws ReleaseNotesException when the changes could not be looked up
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    ChangesRepresentation getChanges(@PathParam("wikiName") String wikiName, @PathParam("product") String product,
        @PathParam("version") String version, @QueryParam("audience") String audience,
        @QueryParam("category") String category, @QueryParam("importance") String importance,
        @QueryParam("containsScreenshots") String containsScreenshots,
        @QueryParam("containsMigrationNotes") String containsMigrationNotes, @QueryParam("released") String released,
        @QueryParam("aggregated") @DefaultValue("false") boolean aggregated, @QueryParam("limit") String limit,
        @QueryParam("offset") String offset) throws ReleaseNotesException;

    /**
     * Adds a change to a release note: allocates the page of a new entry, fills it from the change template and
     * saves it once, so that a change is never left half created.
     * <p>
     * Changes are not deduplicated: posting the same change twice creates it twice. A client that may be re-running
     * asks what the release note already holds first.
     * <p>
     * The change is answered as it was stored, which is not necessarily as it was posted: a property the request
     * leaves out holds the value the change template gives it.
     *
     * @param uriInfo where the created change is pointed to from
     * @param wikiName the wiki holding the release note
     * @param product the product the release note is about
     * @param version the version the release note is about, in its long form, which is the version the change is
     *            stored against
     * @param change the change to add, which needs at least a title
     * @return {@code 201} with the stored change and the page it lives in, {@code 400} when the change is not
     *         usable, {@code 401} or
     *         {@code 403} when the change may not be written, or {@code 404} when that release note does not exist
     * @throws ReleaseNotesException when the change could not be created
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response createChange(@Context UriInfo uriInfo, @PathParam("wikiName") String wikiName,
        @PathParam("product") String product, @PathParam("version") String version, ChangeRepresentation change)
        throws ReleaseNotesException;
}
