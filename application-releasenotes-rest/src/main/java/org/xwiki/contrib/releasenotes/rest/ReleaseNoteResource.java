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
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.rest.model.ReleaseNoteRepresentation;
import org.xwiki.stability.Unstable;

/**
 * The release note of one version of one product.
 * <p>
 * The version in the path is the version itself, in its long form, e.g. {@code 8.3-milestone-1}, and not the name of
 * the page the release note lives in. A client writes both the product and the version URL encoded.
 *
 * @version $Id$
 * @since 2.8
 */
@Path("/wikis/{wikiName}/releasenotes/{product}/{version}")
@Unstable
public interface ReleaseNoteResource
{
    /**
     * Reads one release note, which is the release note a {@code PUT} to the same URL replaces.
     *
     * @param wikiName the wiki holding the release note
     * @param product the product the release note is about
     * @param version the version the release note is about, in its long form
     * @return the release note, or {@code 404} when that product and version have none
     * @throws ReleaseNotesException when the release note could not be read
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    ReleaseNoteRepresentation getReleaseNote(@PathParam("wikiName") String wikiName,
        @PathParam("product") String product, @PathParam("version") String version) throws ReleaseNotesException;

    /**
     * Replaces one release note: it becomes exactly what the request says, and a property the request leaves out is
     * emptied rather than kept. This is how a version is marked released on the day it ships.
     * <p>
     * Only the release date, whether the version has been released, and whether Manual QA has validated it and on
     * which day are replaced. The product and the version locate the release note rather than being replaced, since
     * they are what its page is named after, and neither the content nor the title nor the template is touched: they
     * are what a release note is created with, and an administrator is free to have edited the content since.
     * </p>
     *
     * @param wikiName the wiki holding the release note
     * @param product the product the release note is about
     * @param version the version the release note is about, in its long form
     * @param note the release note that page is to hold
     * @return {@code 200} with the stored release note, {@code 400} when the release note is not usable, {@code 401}
     *         or {@code 403} when it may not be written, or {@code 404} when that product and version have none
     * @throws ReleaseNotesException when the release note could not be replaced
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response updateReleaseNote(@PathParam("wikiName") String wikiName, @PathParam("product") String product,
        @PathParam("version") String version, ReleaseNoteRepresentation note) throws ReleaseNotesException;
}
