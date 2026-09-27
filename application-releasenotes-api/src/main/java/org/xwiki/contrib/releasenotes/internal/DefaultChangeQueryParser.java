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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.ChangeFilter;
import org.xwiki.contrib.releasenotes.ChangeQuery;
import org.xwiki.contrib.releasenotes.ChangeQueryParser;
import org.xwiki.contrib.releasenotes.ChangeType;
import org.xwiki.contrib.releasenotes.Importance;
import org.xwiki.stability.Unstable;

/**
 * Default implementation of {@link ChangeQueryParser}, which reads a filter as a comma-separated list of values,
 * each of them optionally prefixed with the operator to compare it with.
 *
 * @version $Id$
 * @since 2.7
 */
@Component
@Singleton
@Unstable
public class DefaultChangeQueryParser implements ChangeQueryParser
{
    /**
     * What separates the values of one filter.
     */
    private static final String VALUE_SEPARATOR = ",";

    /**
     * The operators a value may be prefixed with, longest first: {@code >=} and {@code <=} also start with the
     * character {@code >} and {@code <} stand alone as, so testing them in this order is what tells them apart. The
     * operator of a value carrying none of them is {@link ChangeFilter.Operator#LIKE}, which is why it is not here.
     */
    private static final List<ChangeFilter.Operator> PREFIX_OPERATORS = List.of(ChangeFilter.Operator.GTE,
        ChangeFilter.Operator.LTE, ChangeFilter.Operator.GT, ChangeFilter.Operator.LT,
        ChangeFilter.Operator.EQUALS);

    @Override
    public ChangeQuery parse(Map<String, ?> parameters)
    {
        ChangeQuery query = new ChangeQuery();

        setFilters(parameters, PRODUCTS, query::setProducts, UnaryOperator.identity());
        setFilters(parameters, VERSIONS, query::setVersions, UnaryOperator.identity());
        // The audience is stored lower cased, and the pages of the application document the capitalized spelling
        // that reads better in a macro call, so whatever spelling a filter uses is matched lower cased.
        setFilters(parameters, AUDIENCE, query::setAudiences, value -> StringUtils.lowerCase(value, Locale.ROOT));
        setFilters(parameters, CATEGORIES, query::setCategories, UnaryOperator.identity());
        setFilters(parameters, IMPORTANCE, query::setImportances, DefaultChangeQueryParser::parseImportance);
        setBoolean(parameters, CONTAINS_SCREENSHOTS, query::setContainsScreenshots);
        setFilters(parameters, TYPES, query::setTypes, DefaultChangeQueryParser::parseType);
        setBoolean(parameters, RELEASED, query::setReleased);

        int limit = getNumber(parameters, LIMIT, ChangeQuery.DEFAULT_LIMIT);
        // A limit is a bound: a value that would remove it, or that would make the search return nothing at all, is
        // not what a caller asking for a page of changes means.
        query.setLimit(limit < 1 ? ChangeQuery.DEFAULT_LIMIT : limit);
        query.setOffset(Math.max(0, getNumber(parameters, OFFSET, 0)));

        return query;
    }

    /**
     * Reads one filter, and leaves the query alone when the parameter holding it is absent.
     *
     * @param normalizer what turns a value into the form the property it filters is stored in
     */
    private void setFilters(Map<String, ?> parameters, String name, Consumer<List<ChangeFilter>> setter,
        UnaryOperator<String> normalizer)
    {
        String listAsString = getString(parameters, name);

        if (listAsString == null) {
            return;
        }

        List<ChangeFilter> filters = new ArrayList<>();

        for (String value : StringUtils.split(listAsString, VALUE_SEPARATOR)) {
            // The spacing a filter is written with belongs to the filter and not to the values it holds, and an
            // operator is read from the characters its value starts with, so a value is trimmed before its operator
            // is looked for rather than only after.
            String written = value.trim();
            ChangeFilter.Operator operator = getOperator(written);
            filters.add(new ChangeFilter(operator,
                normalizer.apply(written.substring(getPrefixLength(operator)).trim())));
        }

        setter.accept(filters);
    }

    /**
     * @param value one value of a filter, as it was written
     * @return the operator that value is prefixed with, or {@link ChangeFilter.Operator#LIKE} when it is prefixed
     *         with none, so that a pattern is what a value means by default
     */
    private static ChangeFilter.Operator getOperator(String value)
    {
        for (ChangeFilter.Operator operator : PREFIX_OPERATORS) {
            if (value.startsWith(operator.getSyntax())) {
                return operator;
            }
        }

        return ChangeFilter.Operator.LIKE;
    }

    /**
     * @return how many characters of a value the passed operator takes up, which is none for the operator a value
     *         carrying no prefix uses
     */
    private static int getPrefixLength(ChangeFilter.Operator operator)
    {
        return operator == ChangeFilter.Operator.LIKE ? 0 : operator.getSyntax().length();
    }

    /**
     * @param value one value of a type filter
     * @return the value that type is stored as when the value names a type whatever its case, and the value itself
     *         otherwise, so that a pattern stays a pattern
     */
    private static String parseType(String value)
    {
        ChangeType type = ChangeType.fromStoredValue(value);

        return type == null ? value : type.getStoredValue();
    }

    /**
     * @param value one value of an importance filter
     * @return the number that importance is stored as when the value names an importance, and the value itself
     *         otherwise, so that a filter may also be written with the stored numbers
     */
    private static String parseImportance(String value)
    {
        for (Importance importance : Importance.values()) {
            if (importance.name().equalsIgnoreCase(value)) {
                return importance.getStoredValue();
            }
        }

        return value;
    }

    /**
     * Reads a filter that is not a value to compare but a choice between the changes that have a trait and the ones
     * that do not, and which therefore only accepts the two words the pages of the application and the report form
     * spell it with.
     */
    private void setBoolean(Map<String, ?> parameters, String name, Consumer<Boolean> setter)
    {
        String value = getString(parameters, name);

        if (Boolean.TRUE.toString().equals(value) || Boolean.FALSE.toString().equals(value)) {
            setter.accept(Boolean.valueOf(value));
        }
    }

    private int getNumber(Map<String, ?> parameters, String name, int defaultValue)
    {
        String value = StringUtils.trimToNull(getString(parameters, name));

        // The parameter comes from a URL or from a macro call, so a value that is not a number at all is a value the
        // caller gets the default for, and not a failure.
        if (value == null || !NumberUtils.isCreatable(value)) {
            return defaultValue;
        }

        return NumberUtils.createNumber(value).intValue();
    }

    private String getString(Map<String, ?> parameters, String name)
    {
        return Objects.toString(parameters.get(name), null);
    }
}
