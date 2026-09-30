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

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.internal.ReleaseNotesReferences;
import org.xwiki.contrib.releasenotes.rest.ChangeResource;
import org.xwiki.contrib.releasenotes.rest.model.ChangeRepresentation;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rest.XWikiRestComponent;

/**
 * Default implementation of {@link ChangeResource}.
 *
 * @version $Id$
 * @since 2.8
 */
@Component
@Named("org.xwiki.contrib.releasenotes.rest.internal.DefaultChangeResource")
@Singleton
public class DefaultChangeResource extends AbstractReleaseNotesResource
    implements ChangeResource, XWikiRestComponent
{
    /**
     * What a client is told when the URL it called names no change, which takes a release note and an entry of it.
     */
    private static final String NO_CHANGE_IN_URL =
        "A change lives in an entry page of the release note of one version of one product, and the URL does not "
            + "name all three.";

    @Inject
    private ReleaseNoteManager releaseNoteManager;

    @Inject
    private RepresentationFactory representationFactory;

    @Override
    public ChangeRepresentation getChange(String wikiName, String product, String version, String entry)
        throws ReleaseNotesException
    {
        return inWiki(wikiName, () -> {
            if (!namesChange(product, version, entry)) {
                throw new WebApplicationException(refuse(Response.Status.BAD_REQUEST, NO_CHANGE_IN_URL));
            }

            DocumentReference reference = getChangeReference(product, version, entry);

            return this.representationFactory.toRepresentation(this.entryPoint.getChange(reference, getCaller()),
                reference);
        });
    }

    @Override
    public Response updateChange(String wikiName, String product, String version, String entry,
        ChangeRepresentation change) throws ReleaseNotesException
    {
        return inWiki(wikiName, () -> {
            if (!namesChange(product, version, entry)) {
                return refuse(Response.Status.BAD_REQUEST, NO_CHANGE_IN_URL);
            }

            if (change == null || StringUtils.isBlank(change.getTitle())) {
                return refuse(Response.Status.BAD_REQUEST, "A change needs a title.");
            }

            Change replacement;

            try {
                replacement = this.representationFactory.toChange(change, product, version);
            } catch (IllegalArgumentException e) {
                return refuse(Response.Status.BAD_REQUEST, e.getMessage());
            }

            // A page that may not be edited and an entry that holds no change are answered by the exception mapper,
            // which is where every endpoint of the application turns a failure into a status code.
            DocumentReference reference = getChangeReference(product, version, entry);

            return Response.ok(this.representationFactory
                .toRepresentation(this.entryPoint.updateChange(reference, replacement, getCaller()), reference))
                .build();
        });
    }

    /**
     * @param product the product of the release note of the URL
     * @param version the version of that release note, in its long form
     * @param entry the name of the entry of the URL
     * @return the page that entry lives in
     */
    private DocumentReference getChangeReference(String product, String version, String entry)
    {
        return ReleaseNotesReferences.entry(this.releaseNoteManager.getReleaseNoteReference(product, version), entry);
    }

    /**
     * @return whether the URL names the three things it takes to locate a change, which an empty path segment does
     *         not
     */
    private static boolean namesChange(String product, String version, String entry)
    {
        return StringUtils.isNoneBlank(product, version, entry);
    }
}
