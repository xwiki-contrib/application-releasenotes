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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Checks that every object of an application class carries, in the XAR, the same property definitions as the class
 * page itself.
 * <p>
 * Each object in a XAR page embeds a copy of its class definition, and that copy is what types the object's values
 * when the page is loaded from the XAR, for instance while merging the page on upgrade. A property missing from the
 * copy is loaded as a string, whatever its real type, and a boolean stored as a string can no longer be displayed:
 * the Release Notes administration section then fails to render.
 *
 * @version $Id$
 */
class EmbeddedClassDefinitionTest
{
    private static final String TOP_SPACE = "ReleaseNotes";

    private static final String CLASS = "class";

    private static final String NAME = "name";

    @Test
    void embeddedClassesMatchTheirClassPage() throws Exception
    {
        List<Document> pages = loadPages();

        Map<String, Set<String>> classProperties = new HashMap<>();
        for (Document page : pages) {
            Element pageClass = child(page.getDocumentElement(), CLASS);
            if (pageClass != null) {
                classProperties.put(text(pageClass, NAME), propertyDefinitions(pageClass));
            }
        }
        assertFalse(classProperties.isEmpty(), "No class page found");

        List<String> mismatches = new ArrayList<>();
        for (Document page : pages) {
            for (Element object : children(page.getDocumentElement(), "object")) {
                String className = text(object, "className");
                Set<String> expected = classProperties.get(className);
                if (expected != null) {
                    Set<String> embedded = propertyDefinitions(child(object, CLASS));
                    if (!embedded.equals(expected)) {
                        mismatches.add(String.format("%s embeds %s with %s instead of %s",
                            page.getDocumentElement().getAttribute("reference"), className, embedded, expected));
                    }
                }
            }
        }
        assertEquals(List.of(), mismatches);
    }

    private List<Document> loadPages() throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        List<Document> pages = new ArrayList<>();
        Path root = Path.of(getClass().getClassLoader().getResource(TOP_SPACE).toURI());
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".xml")).toList()) {
                pages.add(factory.newDocumentBuilder().parse(path.toFile()));
            }
        }
        return pages;
    }

    /**
     * @return the names of the properties a class definition declares: its child elements that hold a
     *     {@code classType}
     */
    private static Set<String> propertyDefinitions(Element classElement)
    {
        Set<String> names = new TreeSet<>();
        for (Element property : children(classElement, null)) {
            if (child(property, "classType") != null) {
                names.add(property.getTagName());
            }
        }
        return names;
    }

    private static List<Element> children(Element parent, String tagName)
    {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && (tagName == null || tagName.equals(element.getTagName()))) {
                result.add(element);
            }
        }
        return result;
    }

    private static Element child(Element parent, String tagName)
    {
        List<Element> matches = children(parent, tagName);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static String text(Element parent, String tagName)
    {
        Element element = child(parent, tagName);
        return element == null ? null : element.getTextContent().trim();
    }
}
