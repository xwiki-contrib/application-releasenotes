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
package org.xwiki.contrib.releasenotes;

import java.util.Map;

import org.xwiki.component.annotation.Role;
import org.xwiki.stability.Unstable;

/**
 * Reads a change search written as text into the query the search is run from.
 * <p>
 * This is the lenient half of the search: a filter is a comma-separated list of values, each of them a
 * {@code like} pattern by default or prefixed with the operator to compare it with, e.g.
 * {@code versions=">=9.0,8.3%"}. It is what the pages of the application and the REST resources hand their callers,
 * who write their filters as the URL and the wiki syntax they come from.
 *
 * @version $Id$
 * @since 2.7
 */
@Role
@Unstable
public interface ChangeQueryParser
{
    /**
     * The parameter holding the filters on the product the changes are of.
     */
    String PRODUCTS = "products";

    /**
     * The parameter holding the filters on the version the changes were made in.
     */
    String VERSIONS = "versions";

    /**
     * The parameter holding the filters on the audience the changes are written for, which are matched lower cased
     * since that is how they are stored.
     */
    String AUDIENCE = "audience";

    /**
     * The parameter holding the filters on the category the changes belong to.
     */
    String CATEGORIES = "categories";

    /**
     * The parameter holding the filters on how important the changes are, which accept the names {@code high},
     * {@code medium} and {@code low} on top of the numbers those are stored as.
     */
    String IMPORTANCE = "importance";

    /**
     * The parameter asking for the changes that are illustrated by a screenshot or a video, spelled {@code true}, or
     * for the ones that are not, spelled {@code false}.
     */
    String CONTAINS_SCREENSHOTS = "containsScreenshots";

    /**
     * The parameter holding the filters on the type of the changes, which accept the names {@code change} and
     * {@code migration} whatever their case.
     *
     * @since 2.8
     */
    String TYPES = "types";

    /**
     * The parameter asking for the changes of the versions marked released, spelled {@code true}, or for the ones of
     * the versions that are not, spelled {@code false}.
     *
     * @since 2.8
     */
    String RELEASED = "released";

    /**
     * The parameter holding how many changes to return at most.
     */
    String LIMIT = "limit";

    /**
     * The parameter holding how many of the matching changes to leave out before the ones to return.
     */
    String OFFSET = "offset";

    /**
     * Reads the passed parameters into a query.
     * <p>
     * A parameter that is absent or {@code null} leaves the property it filters unrestricted, whereas a parameter
     * that is present and empty restricts it to nothing: the caller that says it wants the changes of no version at
     * all gets none, rather than every change of the wiki.
     * <p>
     * A value that cannot be read as what it filters is ignored rather than reported, since these parameters come
     * from a URL or from a macro call: an unusable {@code limit} falls back to the default one, and an unusable
     * {@code containsScreenshots} or {@code released} filters nothing.
     *
     * @param parameters the filters and the page to return, keyed by the parameter names of this interface. Values
     *            are read as text, so both a string and the value it stands for are accepted.
     * @return the query those parameters ask for
     */
    ChangeQuery parse(Map<String, ?> parameters);
}
