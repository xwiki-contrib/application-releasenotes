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
package org.xwiki.contrib.releasenotes.internal.converter;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.xwiki.component.internal.ContextComponentManagerProvider;
import org.xwiki.contrib.releasenotes.QAValidation;
import org.xwiki.contrib.releasenotes.ReleaseNote;
import org.xwiki.properties.ConverterManager;
import org.xwiki.properties.converter.ConversionException;
import org.xwiki.properties.internal.DefaultBeanManager;
import org.xwiki.properties.internal.DefaultConverterManager;
import org.xwiki.properties.internal.converter.ConvertUtilsConverter;
import org.xwiki.properties.internal.converter.EnumConverter;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ReleaseNoteConverter}.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList({ ReleaseNoteConverter.class, ContextComponentManagerProvider.class, DefaultConverterManager.class,
    DefaultBeanManager.class, EnumConverter.class, ConvertUtilsConverter.class, QAValidationConverter.class })
class ReleaseNoteConverterTest
{
    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @Test
    void aReleaseNoteIsWrittenAsAMapOfItsProperties() throws Exception
    {
        ReleaseNote note = convert(Map.of("product", "XWiki", "version", "8.3-milestone-1", "released", "true"));

        assertEquals("XWiki", note.getProduct());
        assertEquals("8.3-milestone-1", note.getVersion());
        assertTrue(note.isReleased());
    }

    @Test
    void thePropertiesLeftOutAreLeftUnset() throws Exception
    {
        ReleaseNote note = convert(Map.of("version", "8.3"));

        assertNull(note.getProduct());
        assertNull(note.getDate());
        assertNull(note.getTemplate());
        assertFalse(note.isReleased());
        assertEquals(QAValidation.NOT_TESTED, note.getQaValidation());
    }

    /**
     * A script writes the Manual QA validation with the value a REST client reads, so that it can pass on what it
     * read; the name of the Java constant is accepted too, and an empty form field stands for not tested.
     */
    @Test
    void theManualQAValidationIsWrittenAsItsStoredValue() throws Exception
    {
        assertEquals(QAValidation.NOT_VALIDATED, qaValidationOf("notValidated"));
        assertEquals(QAValidation.VALIDATED, qaValidationOf("VALIDATED"));
        assertEquals(QAValidation.NOT_TESTED, qaValidationOf(""));
    }

    @Test
    void aManualQAValidationThatIsNoneOfItsValuesIsReported()
    {
        ConversionException exception = assertThrows(ConversionException.class, () -> qaValidationOf("ok"));

        assertEquals("The Manual QA validation [ok] is none of [notTested, validated, notValidated].",
            exception.getCause().getCause().getMessage());
    }

    /**
     * A script displaying the validation of a release note it read shows the value it would write.
     */
    @Test
    void theManualQAValidationIsDisplayedAsItsStoredValue() throws Exception
    {
        assertEquals("notValidated", this.componentManager.<ConverterManager>getInstance(ConverterManager.class)
            .convert(String.class, QAValidation.NOT_VALIDATED));
        assertEquals("notValidated", QAValidation.NOT_VALIDATED.toString());
    }

    private QAValidation qaValidationOf(String value) throws Exception
    {
        return convert(Map.of("version", "8.3", "qaValidation", value)).getQaValidation();
    }

    /**
     * The version is what the page of a release note is named after, so it is named as mandatory and its absence is
     * reported.
     */
    @Test
    void theMandatoryPropertiesAreReported()
    {
        Map<String, String> withoutAVersion = Map.of("product", "XWiki");

        ConversionException exception = assertThrows(ConversionException.class, () -> convert(withoutAVersion));

        assertEquals("Property [version] mandatory", exception.getCause().getMessage());
    }

    private ReleaseNote convert(Object value) throws Exception
    {
        return this.componentManager.<ConverterManager>getInstance(ConverterManager.class)
            .convert(ReleaseNote.class, value);
    }
}
