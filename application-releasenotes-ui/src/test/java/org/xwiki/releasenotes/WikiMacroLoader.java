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
package org.xwiki.releasenotes;

import org.xwiki.bridge.event.DocumentCreatedEvent;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.observation.EventListener;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.mockito.Mockito.when;

/**
 * Registers the wiki macro held by a page of this application so that a page test can call it. XWiki provides the
 * same helper as {@code org.xwiki.test.page.WikiMacroSetup}, but only since XWiki 15.2 while this application
 * supports XWiki 14.10.
 *
 * @version $Id$
 */
public final class WikiMacroLoader
{
    private WikiMacroLoader()
    {
        // Utility class and thus no public constructor.
    }

    /**
     * Registers the wiki macro defined by the passed page, previously loaded with
     * {@code org.xwiki.test.page.PageTest#loadPage}. The test must be annotated with {@link WikiMacroComponentList}.
     *
     * @param componentManager the component manager of the page test
     * @param macroDocument the page defining the wiki macro
     * @throws Exception in case the macro cannot be registered
     */
    public static void registerWikiMacro(MockitoComponentManager componentManager, XWikiDocument macroDocument)
        throws Exception
    {
        // Make the wiki component manager point to the default component manager.
        componentManager.registerComponent(ComponentManager.class, "wiki",
            componentManager.getInstance(ComponentManager.class));

        // The macros of this application have a "Current Wiki" visibility, which requires their author to have admin
        // right on the wiki holding them.
        AuthorizationManager authorization = componentManager.getInstance(AuthorizationManager.class);
        when(authorization.hasAccess(Right.ADMIN, macroDocument.getAuthorReference(),
            macroDocument.getDocumentReference().getWikiReference())).thenReturn(true);

        // Simulate the event the wiki macro listener reacts to when a macro page is added to the wiki.
        componentManager.<EventListener>getInstance(EventListener.class, "wikimacrolistener")
            .onEvent(new DocumentCreatedEvent(), macroDocument, null);
    }
}
