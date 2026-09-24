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

import java.util.ArrayList;
import java.util.List;

import org.xwiki.stability.Unstable;

/**
 * What a change search looks for: the filters each property of a change has to match, and the page of the result to
 * return.
 * <p>
 * Every property starts out with the filter matching every value, so that a query only says what it restricts. An
 * empty filter list is not the same thing: it matches no change at all, which is what a caller asking for the
 * changes of, say, no version means.
 *
 * @version $Id$
 * @since 2.7
 */
@Unstable
public class ChangeQuery
{
    /**
     * How many changes a search returns when the caller asks for no particular number. The filters of a search
     * default to matching everything, so an unbounded search can return every change the wiki holds, and each change
     * returned costs a document load in the pages displaying them.
     */
    public static final int DEFAULT_LIMIT = 100;

    /**
     * The filter matching every value of a property, which is what every property of a new query is filtered with.
     */
    private static final ChangeFilter ANY = new ChangeFilter(ChangeFilter.Operator.LIKE, "%");

    private List<ChangeFilter> products = anyValue();

    private List<ChangeFilter> versions = anyValue();

    private List<ChangeFilter> audiences = anyValue();

    private List<ChangeFilter> categories = anyValue();

    private List<ChangeFilter> importances = anyValue();

    private Boolean containsScreenshots;

    private List<ChangeFilter> types;

    private Boolean released;

    private int limit = DEFAULT_LIMIT;

    private int offset;

    /**
     * @return the filters on the product the changes are of
     */
    public List<ChangeFilter> getProducts()
    {
        return this.products;
    }

    /**
     * @param products the filters on the product the changes are of
     */
    public void setProducts(List<ChangeFilter> products)
    {
        this.products = products;
    }

    /**
     * @return the filters on the version the changes were made in, which are the only ones a comparison operator may
     *         be used on
     */
    public List<ChangeFilter> getVersions()
    {
        return this.versions;
    }

    /**
     * @param versions the filters on the version the changes were made in
     */
    public void setVersions(List<ChangeFilter> versions)
    {
        this.versions = versions;
    }

    /**
     * @return the filters on the audience the changes are written for, whose values are the stored, lower cased ones
     */
    public List<ChangeFilter> getAudiences()
    {
        return this.audiences;
    }

    /**
     * @param audiences the filters on the audience the changes are written for
     */
    public void setAudiences(List<ChangeFilter> audiences)
    {
        this.audiences = audiences;
    }

    /**
     * @return the filters on the category the changes belong to
     */
    public List<ChangeFilter> getCategories()
    {
        return this.categories;
    }

    /**
     * @param categories the filters on the category the changes belong to
     */
    public void setCategories(List<ChangeFilter> categories)
    {
        this.categories = categories;
    }

    /**
     * @return the filters on how important the changes are, whose values are the stored numbers
     */
    public List<ChangeFilter> getImportances()
    {
        return this.importances;
    }

    /**
     * @param importances the filters on how important the changes are
     */
    public void setImportances(List<ChangeFilter> importances)
    {
        this.importances = importances;
    }

    /**
     * @return {@code true} to return only the changes illustrated by a screenshot or a video, {@code false} to return
     *         only the ones illustrated by neither, and {@code null} to return both
     */
    public Boolean getContainsScreenshots()
    {
        return this.containsScreenshots;
    }

    /**
     * @param containsScreenshots whether the changes are illustrated, or {@code null} to not ask
     */
    public void setContainsScreenshots(Boolean containsScreenshots)
    {
        this.containsScreenshots = containsScreenshots;
    }

    /**
     * Unlike the other properties of a change, the type is not filtered at all until a caller says otherwise, rather
     * than filtered with a filter matching every value: filtering a property is joining it in, which would leave out
     * an entry holding no type at all from every search of the wiki, and not only from the ones asking about the type.
     *
     * @return the filters on the type of the changes, whose values are the stored ones, e.g. {@code Migration}, or
     *         {@code null} to return the changes of every type
     * @see ChangeType#getStoredValue()
     * @since 2.8
     */
    public List<ChangeFilter> getTypes()
    {
        return this.types;
    }

    /**
     * @param types the filters on the type of the changes, or {@code null} to not filter on it
     * @since 2.8
     */
    public void setTypes(List<ChangeFilter> types)
    {
        this.types = types;
    }

    /**
     * A change is released when the release note of its product and of its version is marked released. A change
     * whose version has no release note is not released, since nothing says that version ever shipped.
     *
     * @return {@code true} to return only the released changes, {@code false} to return only the ones that are not,
     *         and {@code null} to return both
     * @since 2.8
     */
    public Boolean getReleased()
    {
        return this.released;
    }

    /**
     * @param released whether the changes are released, or {@code null} to not ask
     * @since 2.8
     */
    public void setReleased(Boolean released)
    {
        this.released = released;
    }

    /**
     * @return how many changes the search returns at most
     */
    public int getLimit()
    {
        return this.limit;
    }

    /**
     * @param limit how many changes the search returns at most
     */
    public void setLimit(int limit)
    {
        this.limit = limit;
    }

    /**
     * @return how many of the matching changes the search leaves out before the ones it returns
     */
    public int getOffset()
    {
        return this.offset;
    }

    /**
     * @param offset how many of the matching changes the search leaves out before the ones it returns
     */
    public void setOffset(int offset)
    {
        this.offset = offset;
    }

    /**
     * @return the list a property is filtered with as long as the caller does not say otherwise, which is modifiable
     *         so that a caller may add its own filters to it
     */
    private static List<ChangeFilter> anyValue()
    {
        List<ChangeFilter> filters = new ArrayList<>();
        filters.add(ANY);

        return filters;
    }
}
