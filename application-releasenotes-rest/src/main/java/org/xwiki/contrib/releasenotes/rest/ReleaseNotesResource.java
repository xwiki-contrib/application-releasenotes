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
import org.xwiki.contrib.releasenotes.rest.model.ReleaseNoteRepresentation;
import org.xwiki.contrib.releasenotes.rest.model.ReleaseNotesRepresentation;
import org.xwiki.stability.Unstable;

/**
 * The release notes of a wiki.
 *
 * @version $Id$
 * @since 2.7
 */
@Path("/wikis/{wikiName}/releasenotes")
@Unstable
public interface ReleaseNotesResource
{
    /**
     * Lists the release notes of a wiki, in the order of the pages they live in, which is the order of their product
     * and then of their version.
     *
     * @param wikiName the wiki to list the release notes of
     * @param product the product to list the release notes of, or {@code null} to list them all
     * @return the release notes
     * @throws ReleaseNotesException when the release notes could not be looked up
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    ReleaseNotesRepresentation getReleaseNotes(@PathParam("wikiName") String wikiName,
        @QueryParam("product") String product) throws ReleaseNotesException;

    /**
     * Creates the release note of one version of one product, from the template it names or, when it names none,
     * from the template configured for the wiki.
     * <p>
     * A release note that already exists is answered with a {@code 409}, carrying the page it lives in, rather than
     * returned: it may be about another product than the one asked for, and a client that took it for its own would
     * add its changes to somebody else's release note.
     * <p>
     * The release note is answered as it was stored, which is not necessarily as it was posted: one posted without a
     * product holds the product configured for the wiki.
     *
     * @param uriInfo where the created release note is pointed to from
     * @param wikiName the wiki to create the release note in
     * @param note the release note to create, which needs at least a version, and a product unless one is configured
     *            for the wiki
     * @return {@code 201} with the stored release note and the page it lives in, {@code 400} when the release note
     *         is not usable,
     *         {@code 401} or {@code 403} when the release note may not be written or the template it names may not
     *         be viewed, or {@code 409} when it already exists
     * @throws ReleaseNotesException when the release note could not be created
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response createReleaseNote(@Context UriInfo uriInfo, @PathParam("wikiName") String wikiName,
        ReleaseNoteRepresentation note) throws ReleaseNotesException;
}
