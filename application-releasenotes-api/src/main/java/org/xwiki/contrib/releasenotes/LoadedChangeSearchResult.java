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

import java.util.List;

import org.xwiki.stability.Unstable;

/**
 * One page of the changes a search matched, with the changes themselves on top of the pages they live in.
 * <p>
 * This is what a caller answering with the content of the changes asks for, such as the REST endpoint listing the
 * changes of a release note: it gets the changes and their pages in one call, rather than reading each change back
 * from the page the search named and checking again what the search has already checked.
 *
 * @version $Id$
 * @since 2.8
 */
@Unstable
public class LoadedChangeSearchResult extends ChangeSearchResult
{
    private final List<Change> loadedChanges;

    /**
     * @param result the page of the result, as the pages of its changes
     * @param loadedChanges the changes those pages hold, in the same order
     */
    public LoadedChangeSearchResult(ChangeSearchResult result, List<Change> loadedChanges)
    {
        super(result.getChangeNames(), result.getChanges(), result.hasMore());

        this.loadedChanges = loadedChanges;
    }

    /**
     * @return the changes of this page of the result, in the order of {@link #getChanges()}: the change at one index
     *         is the one the page at that same index holds
     */
    public List<Change> getLoadedChanges()
    {
        return this.loadedChanges;
    }
}
