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
import java.util.Arrays;
import java.util.List;

import org.xwiki.stability.Unstable;

/**
 * One section of a release note: the changes written for one audience, split between the main ones, which the
 * release note displays in full, and the miscellaneous ones, which it only lists after them.
 * <p>
 * The split is not the same for every audience: a user or an administrator gets in full the changes a screenshot or
 * a video illustrates, whatever their importance, whereas a developer, whose changes are rarely illustrated, gets
 * in full the ones that matter, and only a list of the ones of low importance.
 *
 * @version $Id$
 * @since 2.8
 */
@Unstable
public final class ReleaseNoteSection
{
    private final Audience audience;

    private final ChangeQuery mainChanges;

    private final ChangeQuery miscellaneousChanges;

    private ReleaseNoteSection(Audience audience, ChangeQuery changes)
    {
        this.audience = audience;
        this.mainChanges = forAudience(changes);
        this.miscellaneousChanges = forAudience(changes);

        if (audience == Audience.DEVELOPER) {
            this.mainChanges.setImportances(filters(Importance.MEDIUM, Importance.HIGH));
            this.miscellaneousChanges.setImportances(filters(Importance.LOW));
        } else {
            // Whatever their importance, as long as they have one: a change whose importance is not one of the known
            // ones is left out, like a getChanges call asking for no importance in particular leaves it out.
            this.mainChanges.setImportances(filters(Importance.values()));
            this.miscellaneousChanges.setImportances(filters(Importance.values()));
            this.mainChanges.setContainsScreenshots(Boolean.TRUE);
            this.miscellaneousChanges.setContainsScreenshots(Boolean.FALSE);
        }
    }

    /**
     * @param changes the changes a release note displays, e.g. the ones of its product and of the versions it
     *            aggregates minus the ones it excludes, and how many of them each part of a section displays at most
     * @return the sections of a release note displaying those changes, in the order it displays them. Each part of a
     *         section is the passed query restricted to the audience of the section and to the changes of that part,
     *         which replaces whatever the passed query says about the audience, the screenshots and the importance
     */
    public static List<ReleaseNoteSection> of(ChangeQuery changes)
    {
        return List.of(new ReleaseNoteSection(Audience.USER, changes),
            new ReleaseNoteSection(Audience.ADMINISTRATOR, changes),
            new ReleaseNoteSection(Audience.DEVELOPER, changes));
    }

    /**
     * @return the audience the changes of this section are written for
     */
    public Audience getAudience()
    {
        return this.audience;
    }

    /**
     * @return the changes this section displays in full
     */
    public ChangeQuery getMainChanges()
    {
        return this.mainChanges;
    }

    /**
     * @return the changes this section only lists, after the main ones
     */
    public ChangeQuery getMiscellaneousChanges()
    {
        return this.miscellaneousChanges;
    }

    private ChangeQuery forAudience(ChangeQuery changes)
    {
        ChangeQuery query = new ChangeQuery(changes);
        // Matched the way the getChanges macro matches an audience it is asked for, i.e. as a pattern.
        query.setAudiences(filters(new ChangeFilter(ChangeFilter.Operator.LIKE, this.audience.getStoredValue())));

        return query;
    }

    private static ChangeFilter importance(Importance importance)
    {
        return new ChangeFilter(ChangeFilter.Operator.LIKE, importance.getStoredValue());
    }

    /**
     * @return the passed filters, in a list the caller of a section may add its own filters to, like the lists of a
     *         new query
     */
    private static List<ChangeFilter> filters(ChangeFilter... filters)
    {
        return new ArrayList<>(Arrays.asList(filters));
    }

    private static List<ChangeFilter> filters(Importance... importances)
    {
        return new ArrayList<>(Arrays.stream(importances).map(ReleaseNoteSection::importance).toList());
    }
}
