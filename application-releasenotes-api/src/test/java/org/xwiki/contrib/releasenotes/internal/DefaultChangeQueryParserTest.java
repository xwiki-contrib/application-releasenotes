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
package org.xwiki.contrib.releasenotes.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.xwiki.contrib.releasenotes.ChangeFilter;
import org.xwiki.contrib.releasenotes.ChangeFilter.Operator;
import org.xwiki.contrib.releasenotes.ChangeQuery;
import org.xwiki.contrib.releasenotes.ChangeQueryParser;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link DefaultChangeQueryParser}, i.e. for the filter language the pages of the application and the
 * REST resources let their callers write a change search in.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultChangeQueryParserTest
{
    /**
     * The filter every property of a query that says nothing about it is read as.
     */
    private static final ChangeFilter ANY = new ChangeFilter(Operator.LIKE, "%");

    @InjectMockComponents
    private DefaultChangeQueryParser parser;

    /**
     * A value carrying no operator is a pattern, which is what makes {@code 8.3%} the way a release note asks for
     * the changes of a version and of its milestones at once.
     */
    @Test
    void aValueWithoutAnOperatorIsAPattern()
    {
        assertEquals(List.of(new ChangeFilter(Operator.LIKE, "8.3%")),
            parse(ChangeQueryParser.VERSIONS, "8.3%").getVersions());
    }

    /**
     * The operator a value is compared with is read from the prefix it is written with. The two-character operators
     * are what {@code >=} and {@code <=} mean, and not the one-character operator they start with followed by a
     * value that starts with an equal sign.
     */
    @ParameterizedTest
    @CsvSource({
        "'>=9.0', GTE,    9.0",
        "'<=9.0', LTE,    9.0",
        "'>9.0',  GT,     9.0",
        "'<9.0',  LT,     9.0",
        "'=9.0',  EQUALS, 9.0",
        "'9.0',   LIKE,   9.0"
    })
    void theOperatorOfAValueIsReadFromItsPrefix(String written, Operator expectedOperator, String expectedValue)
    {
        assertEquals(List.of(new ChangeFilter(expectedOperator, expectedValue)),
            parse(ChangeQueryParser.VERSIONS, written).getVersions());
    }

    /**
     * A filter holds as many values as the caller separated with a comma, each of them with its own operator.
     */
    @Test
    void aFilterHoldsTheCommaSeparatedValuesItIsWrittenWith()
    {
        assertEquals(List.of(new ChangeFilter(Operator.LIKE, "8.3%"), new ChangeFilter(Operator.GTE, "9.0"),
            new ChangeFilter(Operator.EQUALS, "10.0")),
            parse(ChangeQueryParser.VERSIONS, "8.3%,>=9.0,=10.0").getVersions());
    }

    /**
     * The spacing a filter is written with belongs to the filter and not to the values it holds, which are compared
     * to what a change holds and would match nothing with a space of their own.
     */
    @Test
    void theSpacingAroundAValueIsNotPartOfIt()
    {
        assertEquals(List.of(new ChangeFilter(Operator.LIKE, "8.3%"), new ChangeFilter(Operator.GTE, "9.0")),
            parse(ChangeQueryParser.VERSIONS, " 8.3% , >=9.0 ").getVersions());
    }

    /**
     * The spacing a filter is written with is not part of the operator either: a value whose operator is preceded by
     * a space is the comparison it reads as, and not a pattern of the text of that comparison. That spelling is what
     * a reader listing the filters of a custom report naturally writes, and a pattern of it matches no change at all.
     * The operator is asserted on its own because a pattern of a comparison is written the same way as the
     * comparison itself.
     */
    @ParameterizedTest
    @CsvSource({
        "' >=9.0',  GTE,    9.0",
        "'>= 9.0',  GTE,    9.0",
        "' >= 9.0', GTE,    9.0",
        "' = 10.0', EQUALS, 10.0",
        "' 9.0',    LIKE,   9.0"
    })
    void theOperatorOfAValueIsReadThroughTheSpacingBeforeIt(String written, Operator expectedOperator,
        String expectedValue)
    {
        ChangeFilter filter = parse(ChangeQueryParser.VERSIONS, written).getVersions().get(0);

        assertEquals(expectedOperator, filter.getOperator());
        assertEquals(expectedValue, filter.getValue());
    }

    /**
     * A property no parameter is passed for is left unrestricted, so that a caller only says what it restricts,
     * whereas a parameter that is passed empty restricts it to nothing: a caller asking for the changes of no
     * version at all means none of them, and not every change of the wiki.
     */
    @Test
    void anAbsentFilterMatchesEverythingAndAnEmptyOneNothing()
    {
        ChangeQuery unrestricted = this.parser.parse(Map.of());

        assertEquals(List.of(ANY), unrestricted.getProducts());
        assertEquals(List.of(ANY), unrestricted.getVersions());
        assertEquals(List.of(ANY), unrestricted.getAudiences());
        assertEquals(List.of(ANY), unrestricted.getCategories());
        assertEquals(List.of(ANY), unrestricted.getImportances());
        assertNull(unrestricted.getContainsScreenshots());
        assertNull(unrestricted.getTypes());
        assertNull(unrestricted.getReleased());
        assertEquals(ChangeQuery.DEFAULT_LIMIT, unrestricted.getLimit());
        assertEquals(0, unrestricted.getOffset());

        assertEquals(List.of(), parse(ChangeQueryParser.VERSIONS, "").getVersions());
    }

    /**
     * A parameter a caller has no value for reaches this as a null value, e.g. the parameter a macro call leaves
     * out, and means the same thing as an absent one.
     */
    @Test
    void aNullValueLeavesThePropertyUnrestricted()
    {
        assertEquals(List.of(ANY), parse(ChangeQueryParser.VERSIONS, null).getVersions());
    }

    /**
     * The audience is stored lower cased, and the macros of the application document the capitalized spelling that
     * reads better in a macro call, so a filter is matched lower cased whichever spelling it uses.
     */
    @Test
    void theAudienceIsMatchedLowerCased()
    {
        assertEquals(List.of(new ChangeFilter(Operator.LIKE, "user"),
            new ChangeFilter(Operator.LIKE, "administrator"), new ChangeFilter(Operator.LIKE, "developer")),
            parse(ChangeQueryParser.AUDIENCE, "User,ADMINISTRATOR,developer").getAudiences());
    }

    /**
     * The importance is stored as a number, and the edit form of a change displays the names of those numbers, so a
     * filter may be written with either.
     */
    @ParameterizedTest
    @CsvSource({
        "High,     LIKE, 2",
        "Medium,   LIKE, 1",
        "low,      LIKE, 0",
        "HIGH,     LIKE, 2",
        "2,        LIKE, 2",
        "'>=high', GTE,  2"
    })
    void theImportanceIsMatchedAsTheNumberItIsStoredAs(String written, Operator expectedOperator,
        String expectedValue)
    {
        assertEquals(List.of(new ChangeFilter(expectedOperator, expectedValue)),
            parse(ChangeQueryParser.IMPORTANCE, written).getImportances());
    }

    /**
     * Only the names of the importance values are translated: a filter naming something else is a filter that
     * matches nothing rather than one that silently matches something else.
     */
    @Test
    void aValueNamingNoImportanceIsKeptAsItIs()
    {
        assertEquals(List.of(new ChangeFilter(Operator.LIKE, "highly")),
            parse(ChangeQueryParser.IMPORTANCE, "highly").getImportances());
    }

    /**
     * The screenshot filter is a choice between the changes that are illustrated and the ones that are not, and not
     * a value to compare, so it only accepts the two words the pages of the application spell it with.
     */
    @ParameterizedTest
    @CsvSource({
        "true,  true",
        "false, false",
        "True,  ",
        "yes,   ",
        "'',    "
    })
    void theScreenshotFilterAcceptsTheTwoWordsItIsWrittenWith(String written, Boolean expected)
    {
        assertEquals(expected, parse(ChangeQueryParser.CONTAINS_SCREENSHOTS, written).getContainsScreenshots());
    }

    /**
     * The released filter is a choice just like the screenshot filter, and accepts the same two words.
     */
    @ParameterizedTest
    @CsvSource({
        "true,  true",
        "false, false",
        "True,  ",
        "yes,   ",
        "'',    "
    })
    void theReleasedFilterAcceptsTheTwoWordsItIsWrittenWith(String written, Boolean expected)
    {
        assertEquals(expected, parse(ChangeQueryParser.RELEASED, written).getReleased());
    }

    /**
     * A type is stored capitalized, and is matched whatever the case it is written with, the way the audience is,
     * while a value naming no type is kept as it is written, so that it can still be a pattern.
     */
    @Test
    void aTypeIsReadWhateverItsCase()
    {
        assertEquals(List.of(new ChangeFilter(ChangeFilter.Operator.LIKE, "Migration"),
            new ChangeFilter(ChangeFilter.Operator.EQUALS, "Change"),
            new ChangeFilter(ChangeFilter.Operator.LIKE, "Mig%")),
            parse(ChangeQueryParser.TYPES, "migration, =CHANGE, Mig%").getTypes());
    }

    /**
     * A limit is a bound: a value that would remove it, or that would make the search return nothing at all, is not
     * what a caller asking for a page of changes means. Neither is a value that is not a number, which is what a
     * URL a reader edited by hand carries.
     */
    @ParameterizedTest
    @CsvSource({
        "10,          10",
        "1,           1",
        "0,           100",
        "-1,          100",
        "notANumber,  100",
        "'',          100"
    })
    void anUnusableLimitFallsBackToTheDefault(String written, int expected)
    {
        assertEquals(expected, parse(ChangeQueryParser.LIMIT, written).getLimit());
    }

    @ParameterizedTest
    @CsvSource({
        "20,          20",
        "0,           0",
        "-1,          0",
        "notANumber,  0",
        "'',          0"
    })
    void anUnusableOffsetFallsBackToTheFirstPage(String written, int expected)
    {
        assertEquals(expected, parse(ChangeQueryParser.OFFSET, written).getOffset());
    }

    /**
     * The parameters are read as text, so a caller that has the value itself rather than the text of it, e.g. a REST
     * resource, does not have to write it out first.
     */
    @Test
    void aParameterMayAlsoCarryTheValueItself()
    {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(ChangeQueryParser.CONTAINS_SCREENSHOTS, Boolean.TRUE);
        parameters.put(ChangeQueryParser.LIMIT, 10);

        ChangeQuery query = this.parser.parse(parameters);

        assertEquals(Boolean.TRUE, query.getContainsScreenshots());
        assertEquals(10, query.getLimit());
    }

    /**
     * @param name the parameter to pass
     * @param value the value to pass it with, which may be null
     * @return the query those alone are read into
     */
    private ChangeQuery parse(String name, String value)
    {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(name, value);

        return this.parser.parse(parameters);
    }
}
