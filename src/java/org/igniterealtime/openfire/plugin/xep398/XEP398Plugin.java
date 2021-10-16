/*
 * Copyright (C) 2017 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.igniterealtime.openfire.plugin.xep398;

import java.io.File;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * An Openfire plugin that integrates XEP-0398.
 *
 * @author 
 */
public class XEP398Plugin implements Plugin
{
    private static final Logger Log = LoggerFactory.getLogger( XEP398Plugin.class );

    private XEP398IQHandler xep398Handler = null;

    //XEP-0398
    public static String NAMESPACE_XEP398="urn:xmpp:pep-vcard-conversion:0";

    public static final SystemProperty<Boolean> XMPP_AVATARCONVERSION_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
            .setKey("xmpp.xep0398.enabled")
            .setPlugin( "xep398" )
            .setDefaultValue(false)
            .setDynamic(true)
            .build();

    public static final SystemProperty<Boolean> XMPP_DELETEOTHERAVATAR_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
            .setKey("xmpp.xep0398.onlypep")
            .setPlugin( "xep398" )
            .setDefaultValue(false)
            .setDynamic(true)
            .build();
    
    public static final SystemProperty<Boolean> XMPP_SHRINKVCARDIMG_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
            .setKey("xmpp.xep0398.shrinkvcardimg")
            .setPlugin( "xep398" )
            .setDefaultValue(false)
            .setDynamic(true)
            .build();

    private Cache<String, String> cache = null;

    @Override
    public void initializePlugin( PluginManager manager, File pluginDirectory )
    {
        SystemProperty.removePropertiesForPlugin("xep398");
        Log.info("Initialize XEP-0398 Plugin enabled:"+XMPP_AVATARCONVERSION_ENABLED.getDisplayValue()+" store only in pep="+XMPP_DELETEOTHERAVATAR_ENABLED.getDisplayValue());
        this.xep398Handler = new XEP398IQHandler(this);
        InterceptorManager.getInstance().addInterceptor(this.xep398Handler);
        if (JiveGlobals.getLongProperty("cache.XEP398.maxLifetime", 0)==0)
        {
            JiveGlobals.setProperty("cache.XEP398.maxLifetime","3600000");
        }
        if (JiveGlobals.getLongProperty("cache.XEP398.size", 0)==0)
        {
            JiveGlobals.setProperty("cache.XEP398.size","20971520");
        }
        cache = CacheFactory.createCache("XEP398");

        XMPPServer.getInstance().getIQDiscoInfoHandler().addServerFeature(NAMESPACE_XEP398);
    }

    @Override
    public void destroyPlugin()
    {
        Log.info("Destroy XEP-0398 Plugin");
        XMPPServer.getInstance().getIQDiscoInfoHandler().removeServerFeature(NAMESPACE_XEP398);
        InterceptorManager.getInstance().removeInterceptor(this.xep398Handler);
        this.xep398Handler = null;
    }

    public Cache<String, String> getCache() {
        return cache;
    }

}
