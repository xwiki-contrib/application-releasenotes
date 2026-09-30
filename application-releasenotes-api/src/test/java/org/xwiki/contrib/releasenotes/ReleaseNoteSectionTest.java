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
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.releasenotes.ChangeFilter.Operator;
import org.xwiki.model.reference.DocumentReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link ReleaseNoteSection}, i.e. for how a release note splits the changes it displays.
 *
 * @version $Id$
 */
class ReleaseNoteSectionTest
{
    private static final ChangeFilter ANY = new ChangeFilter(Operator.LIKE, "%");

    /**
     * A release note displays one section per audience, the users first and the developers last.
     */
    @Test
    void oneSectionPerAudienceInTheOrderTheyAreDisplayed()
    {
        List<ReleaseNoteSection> sections = ReleaseNoteSection.of(new ChangeQuery());

        assertEquals(List.of(Audience.USER, Audience.ADMINISTRATOR, Audience.DEVELOPER),
            sections.stream().map(ReleaseNoteSection::getAudience).toList());
        for (ReleaseNoteSection section : sections) {
            List<ChangeFilter> audience =
                List.of(new ChangeFilter(Operator.LIKE, section.getAudience().getStoredValue()));
            assertEquals(audience, section.getMainChanges().getAudiences());
            assertEquals(audience, section.getMiscellaneousChanges().getAudiences());
        }
    }

    /**
     * A user or an administrator gets in full the changes a screenshot or a video illustrates, whatever their
     * importance as long as they have a known one, and a list of the other ones.
     */
    @Test
    void theUsersAndTheAdministratorsSplitOnTheScreenshots()
    {
        for (ReleaseNoteSection section : ReleaseNoteSection.of(new ChangeQuery()).subList(0, 2)) {
            assertEquals(Boolean.TRUE, section.getMainChanges().getContainsScreenshots());
            assertEquals(Boolean.FALSE, section.getMiscellaneousChanges().getContainsScreenshots());
            List<ChangeFilter> everyImportance = List.of(new ChangeFilter(Operator.LIKE, "0"),
                new ChangeFilter(Operator.LIKE, "1"), new ChangeFilter(Operator.LIKE, "2"));
            assertEquals(everyImportance, section.getMainChanges().getImportances());
            assertEquals(everyImportance, section.getMiscellaneousChanges().getImportances());
        }
    }

    /**
     * A developer gets in full the changes that matter, whether they are illustrated or not, and a list of the ones of
     * low importance.
     */
    @Test
    void theDevelopersSplitOnTheImportance()
    {
        ReleaseNoteSection section = ReleaseNoteSection.of(new ChangeQuery()).get(2);

        assertEquals(List.of(new ChangeFilter(Operator.LIKE, "1"), new ChangeFilter(Operator.LIKE, "2")),
            section.getMainChanges().getImportances());
        assertEquals(List.of(new ChangeFilter(Operator.LIKE, "0")), section.getMiscellaneousChanges().getImportances());
        assertNull(section.getMainChanges().getContainsScreenshots());
        assertNull(section.getMiscellaneousChanges().getContainsScreenshots());
    }

    /**
     * Every part of a section keeps what the release note asks for, i.e. its product, its versions, its exclusions
     * and its page, and none of them modifies the query it was made from.
     */
    @Test
    void eachPartKeepsTheRestOfTheQuery()
    {
        ChangeQuery changes = new ChangeQuery();
        List<ChangeFilter> products = List.of(new ChangeFilter(Operator.LIKE, "XWiki"));
        List<ChangeFilter> versions = List.of(new ChangeFilter(Operator.LIKE, "8.3"));
        Set<DocumentReference> exclusions = Set.of(new DocumentReference("xwiki", "Space", "Excluded"));
        changes.setProducts(products);
        changes.setVersions(versions);
        changes.setCategories(List.of());
        changes.setExclusions(exclusions);
        changes.setLimit(20);
        changes.setOffset(40);

        for (ReleaseNoteSection section : ReleaseNoteSection.of(changes)) {
            for (ChangeQuery part : List.of(section.getMainChanges(), section.getMiscellaneousChanges())) {
                assertEquals(products, part.getProducts());
                assertEquals(versions, part.getVersions());
                assertEquals(List.of(), part.getCategories());
                assertEquals(exclusions, part.getExclusions());
                assertEquals(20, part.getLimit());
                assertEquals(40, part.getOffset());
            }
        }
        assertEquals(List.of(ANY), changes.getAudiences());
        assertEquals(List.of(ANY), changes.getImportances());
        assertNull(changes.getContainsScreenshots());
    }
}
