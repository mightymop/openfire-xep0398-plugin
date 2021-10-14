package org.igniterealtime.openfire.plugin.xep398;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.dom4j.Element;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.pep.PEPService;
import org.jivesoftware.openfire.pep.PEPServiceManager;
import org.jivesoftware.openfire.pubsub.DefaultNodeConfiguration;
import org.jivesoftware.openfire.pubsub.LeafNode;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.PublishedItem;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.IQ.Type;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

public class XEP398IQHandler implements PacketInterceptor
{
    private static final Logger Log = LoggerFactory.getLogger(XEP398IQHandler.class);

    //XEP-0084
    public static String NAMESPACE_PUBSUB = "http://jabber.org/protocol/pubsub";
    public static String NAMESPACE_METADATA = "urn:xmpp:avatar:metadata";
    public static String NAMESPACE_DATA = "urn:xmpp:avatar:data";

    //XEP-0153
    public static String NAMESPACE_VCARD_TEMP = "vcard-temp";
    public static String NAMESPACE_VCARD_TEMP_X_UPDATE="vcard-temp:x:update";

    private PEPServiceManager pepmgr;

    //Constructors
    public XEP398IQHandler()
    {
        pepmgr = XMPPServer.getInstance().getIQPEPHandler().getServiceManager();
    }

    public PEPService getPEPFromUser(JID userjid)
    {
        try
        {
            PEPService pep = pepmgr.getPEPService(userjid);
            if (pep!=null)
            {
                Log.debug("PEPService from "+userjid.toBareJID()+" loaded successfully");
                return pep;
            }
            else
            {
                Log.error("getPEPFromUser: PEPService from user \""+userjid.toBareJID()+"\" could not be loaded");
                return null;
            }
        }
        catch (Exception e1)
        {
            Log.error("getPEPFromUser: "+e1.getMessage());
            return null;
        }
    }

    public Avatar getAvatarWithInfotag(JID user,Element info)
    {
        Log.debug("Read Avatar from PEPService ("+user.toBareJID()+")");
        Avatar result = null;
        PEPService pep = getPEPFromUser(user);
        if (pep!=null)
        {
            //Search for relevant nodes
            Node avatarNode = pep.getNode(NAMESPACE_DATA);

            //Check for pep nodes
            if (info!=null&&avatarNode!=null)
            {
                //META
                if (info.attribute("url")!=null)
                {
                    return getAvatar(user);
                }
                result = new Avatar();
                result.getMetadata().setHeight(Integer.parseInt(info.attributeValue("height")));
                result.getMetadata().setWidth(Integer.parseInt(info.attributeValue("width")));
                result.getMetadata().setType(info.attributeValue("type"));
                result.getMetadata().setId(info.attributeValue("id"));
                Log.debug("Metadata loaded ("+user.toBareJID()+")",result.getMetadata().toString());

                List<PublishedItem> items = avatarNode.getPublishedItems();
                if (items!=null)
                {
                    boolean founddata = false;
                    for (PublishedItem itm : items)
                    {
                        if (itm.getID().equals(info.attributeValue("id")))
                        {
                            Element payload = itm.getPayload();
                            if (payload!=null)
                            {
                                String img = payload.getText();
                                if (img!=null)
                                {
                                    result.setImage(img);
                                    Log.debug("Avatarimage loaded ("+user.toBareJID()+")",result.getMetadata().toString());
                                    founddata=true;
                                    break;
                                }
                            }
                        }
                    }

                    if (!founddata)
                    {
                        Log.debug("Node ("+NAMESPACE_DATA+") does not have a data tag ("+user.toBareJID()+")");
                        return null;
                    }
                }
                else {
                    Log.debug("Node ("+NAMESPACE_DATA+") does not have any children ("+user.toBareJID()+")");
                    return null;
                }

                return result;
            }
            else {
                Log.debug("One of the following nodes were not found in PEP: "+NAMESPACE_DATA+" or "+NAMESPACE_METADATA+" ("+user.toBareJID()+")");
                return null;
            }
        }
        else {
            Log.debug("Avatar not loaded");
            return null;
        }
    }

    public Avatar getAvatar(JID user)
    {
        Log.debug("Read Avatar from PEPService ("+user.toBareJID()+")");
        Avatar result = null;
        PEPService pep = getPEPFromUser(user);
        if (pep!=null)
        {
            //Search for relevant nodes
            Node metaNode = pep.getNode(NAMESPACE_METADATA);
            Node avatarNode = pep.getNode(NAMESPACE_DATA);

            //Check for pep nodes
            if (metaNode!=null&&avatarNode!=null)
            {
                //META
                result = new Avatar();

                List<PublishedItem> items = metaNode.getPublishedItems();
                if (items!=null)
                {
                    //search publisheditems for info nodes
                    boolean founddata = false;
                    for (PublishedItem itm : items)
                    {
                        Element payload = itm.getPayload();
                        if (payload!=null)
                        {
                            Element info = payload.element("info");
                            //use the first info node that does not point to an external http image 
                            if (info!=null)
                            {
                                if (info.attribute("url")!=null)
                                {
                                    continue;
                                }
                                result.getMetadata().setHeight(Integer.parseInt(info.attributeValue("height")));
                                result.getMetadata().setWidth(Integer.parseInt(info.attributeValue("width")));
                                result.getMetadata().setType(info.attributeValue("type"));
                                result.getMetadata().setId(info.attributeValue("id"));
                                Log.debug("Metadata loaded ("+user.toBareJID()+")",result.getMetadata().toString());
                                founddata=true;
                                break;
                            }
                        }
                    }
                        
                    if (!founddata)
                    {
                        Log.debug("Node ("+NAMESPACE_METADATA+") does not have a metadata tag ("+user.toBareJID()+")");
                        return null;
                    }
                }
                else {
                    Log.debug("Node ("+NAMESPACE_METADATA+") does not have any children ("+user.toBareJID()+")");
                    return null;
                }

                items = avatarNode.getPublishedItems();
                if (items!=null)
                {
                    boolean founddata = false;
                    for (PublishedItem itm : items)
                    {
                        if (itm.getID().equals(result.getMetadata().getId()))
                        {
                            Element payload = itm.getPayload();
                            if (payload!=null)
                            {
                                String img = payload.getText();
                                if (img!=null)
                                {
                                    result.setImage(img);
                                    Log.debug("Avatarimage loaded ("+user.toBareJID()+")",result.getMetadata().toString());
                                    founddata=true;
                                    break;
                                }
                            }
                        }
                    }

                    if (!founddata)
                    {
                        Log.debug("Node ("+NAMESPACE_DATA+") does not have a data tag ("+user.toBareJID()+")");
                        return null;
                    }
                }
                else {
                    Log.debug("Node ("+NAMESPACE_DATA+") does not have any children ("+user.toBareJID()+")");
                    return null;
                }

                return result;
            }
            else {
                Log.debug("One of the following nodes were not found in PEP: "+NAMESPACE_DATA+" or "+NAMESPACE_METADATA+" ("+user.toBareJID()+")");
                return null;
            }
        }
        else {
            Log.debug("Avatar not loaded");
            return null;
        }
    }

    private Avatar getAvatarFromVcard(JID from)
    {
        Element vcard = XMPPServer.getInstance().getVCardManager().getVCard(from.getNode());
        if (vcard!=null)
        {
            Element vcardphoto = vcard.element("PHOTO");

            if (vcardphoto==null)
            {
                vcardphoto=vcard.addElement("PHOTO");
                vcardphoto.addElement("BINVAL");
                vcardphoto.addElement("TYPE");
            }

            Element binval = vcardphoto.element("BINVAL");
            Element type = vcardphoto.element("TYPE");
            if (binval!=null&&type!=null&&binval.getTextTrim().length()>0&&type.getTextTrim().length()>0)
            {
                return buildAvatar(binval.getTextTrim(),type.getTextTrim());
            }
            else
            {
                return null;
            }
        }
        else {
            return null;
        }
    }

    private Avatar buildAvatar(String base64data, String type)
    {
        Avatar avatar = new Avatar();
        avatar.getMetadata().setType(type);
        avatar.setImage(base64data);

        String hash = avatar.getMainHash();
        if (hash!=null)
        {
            avatar.getMetadata().setId(hash);
        }
        else {
            Log.error("buildAvatar: Could not calc. image hash!");
            avatar.getMetadata().setId(null);
        }

        if (type==null)
        {
            try
            {
                avatar.getMetadata().setType(URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(avatar.getImageBytes())));
            }
            catch (IOException e)
            {
                Log.error("buildAvatar: Could not set mime type of image");
            }
        }

        return avatar;
    }

    /**
     * delete metadata node of a user
     * @param jid Jid from which the node will be deleted
     * */
    private void deleteOldMetadataNode(JID jid)
    {
        PEPService pep = getPEPFromUser(jid);
        if (pep!=null)
        {
            Node nmeta = pep.getNode(NAMESPACE_METADATA);

            if (nmeta!=null&&nmeta.getPublishedItems().size()>0)
            {
                nmeta.getPublishedItems().get(0).getNode().deleteItems(nmeta.getPublishedItems());
                pep.removeNode(nmeta.getUniqueIdentifier());
                nmeta.delete();
            }
        }
    }

    /**
     * delete metadata node of a user and build a new one with data of this object
     * @param jid the jid to which the node will be created
     * */
    public void routeMetaDataToServer(JID jid,Avatar avatar)
    {

        PEPService pep = getPEPFromUser(jid);
        if (pep!=null)
        {
            Node ndata = pep.getNode(NAMESPACE_METADATA);
            LeafNode newNode = null;
            if (ndata == null) {
                // Create the node
                final JID creator = jid.asBareJID();
                final DefaultNodeConfiguration defaultConfiguration = pep.getDefaultNodeConfiguration(true);
                newNode = new LeafNode(pep.getUniqueIdentifier(), pep.getRootCollectionNode(), NAMESPACE_METADATA, creator, defaultConfiguration);

                newNode.addOwner(creator);
                newNode.saveToDB();
            }
            else {
                newNode = (LeafNode) ndata;
            }

            newNode.deleteItems(newNode.getPublishedItems());

            IQ metadataiq = new IQ(Type.set);
            metadataiq.setFrom(jid);
            metadataiq.setID(avatar.getMainHash());
            Element metapubsub = metadataiq.setChildElement("pubsub", NAMESPACE_PUBSUB);
            Element metapublish = metapubsub.addElement("publish");
            metapublish.addAttribute("node", NAMESPACE_METADATA);
            Element metaitem = metapublish.addElement("item");
            metaitem.addAttribute("id",avatar.getMainHash());
            Element metadata = metaitem.addElement("metadata",NAMESPACE_METADATA);
            Element metainfo = metadata.addElement("info");
            metainfo.addAttribute("bytes",String.valueOf(avatar.getImageBytes().length));
            metainfo.addAttribute("id",avatar.getMainHash());
            metainfo.addAttribute("height",String.valueOf(avatar.getMetadata().getHeight()));
            metainfo.addAttribute("type",avatar.getMetadata().getType());
            metainfo.addAttribute("width",String.valueOf(avatar.getMetadata().getWidth()));

            ArrayList<Element> lItems = new ArrayList<Element>();
            lItems.add(metaitem);
            try
            {
                newNode.publishItems(jid, lItems);
            }
            catch (Exception e)
            {
                Log.error(e.getMessage(),e);
                XMPPServer.getInstance().getIQRouter().route(metadataiq);
            }

            newNode.saveToDB();
        }
    }

    /**
     * delete data node of a user
     * @param jid
     *            the jid from which the node will be deleted
     * */
    private void deleteOldDataNode(JID jid)
    {
        PEPService pep = getPEPFromUser(jid);
        if (pep!=null)
        {
            Node ndata = pep.getNode(NAMESPACE_DATA);

            if (ndata!=null&&ndata.getPublishedItems().size()>0)
            {
                ndata.getPublishedItems().get(0).getNode().deleteItems(ndata.getPublishedItems());
                pep.removeNode(ndata.getUniqueIdentifier());
                ndata.delete();
            }
        }
    }

    /**
     * deletes pep avatar of a user and send a presence broadcast
     * @param jid
     *            the jid from which the avatar will be deleted
     * */
    public void deletePEPAvatar(JID jid)
    {
        deleteOldDataNode(jid);

        deleteOldMetadataNode(jid);

        broadcastDeletePresenceUpdate(jid);
    }

    /**
     * delete data node of a user and build a new one with data of this object
     * @param jid
     *            the jid to which the node will be created
     * */
    public void routeDataToServer(JID jid,Avatar avatar)
    {

        PEPService pep = getPEPFromUser(jid);
        if (pep!=null)
        {
            Node ndata = pep.getNode(NAMESPACE_DATA);
            LeafNode newNode = null;
            if (ndata == null) {
                // Create the node
                final JID creator = jid.asBareJID();
                final DefaultNodeConfiguration defaultConfiguration = pep.getDefaultNodeConfiguration(true);
                newNode = new LeafNode(pep.getUniqueIdentifier(), pep.getRootCollectionNode(), NAMESPACE_DATA, creator, defaultConfiguration);

                newNode.addOwner(creator);
                newNode.saveToDB();
            }
            else {
                newNode = (LeafNode) ndata;
            }

            newNode.deleteItems(newNode.getPublishedItems());

            IQ imagedata = new IQ(Type.set);
            imagedata.setFrom(jid);
            imagedata.setID(UUID.randomUUID().toString());
            Element pubsub = imagedata.setChildElement("pubsub", NAMESPACE_PUBSUB);
            Element publish = pubsub.addElement("publish");
            publish.addAttribute("node", NAMESPACE_DATA);
            Element item = publish.addElement("item");
            item.addAttribute("id",avatar.getMainHash());
            Element data = item.addElement("data", NAMESPACE_DATA);
            data.setText(avatar.getImageString());

            ArrayList<Element> lItems = new ArrayList<Element>();
            lItems.add(item);
            try
            {
                newNode.publishItems(jid, lItems);
            }
            catch (Exception e)
            {
                Log.error(e.getMessage(),e);
                XMPPServer.getInstance().getIQRouter().route(imagedata);
            }

            newNode.saveToDB();
        }
    }
    
    /**
     * delete data node of a user and build a new one with data of this object
     * @param jid
     *            the jid to which the node will be created
     * */
    public void routeVCardUpdateToServer(JID jid,Avatar avatar)
    {
        IQ iq = new IQ(Type.set);
        iq.setFrom(jid);
        iq.setID(UUID.randomUUID().toString());
        Element vCard = iq.setChildElement("vCard", NAMESPACE_VCARD_TEMP);
        Element photo = vCard.addElement("PHOTO");

        if (avatar!=null)
        {
            Element type = photo.addElement("TYPE");
            Element binval = photo.addElement("BINVAL");
            type.setText(avatar.getMetadata().getType());

            String imgdata = null;
            if (XEP398Plugin.XMPP_SHRINKVCARDIMG_ENABLED.getValue())
            {
                imgdata = avatar.getShrinkedImage();
            }
            else
            {
                imgdata = avatar.getImageString();
            }

            if (imgdata!=null)
            {
                binval.setText(imgdata);
            }
            else
            {
                Log.error("Avatar image could not get shrinked!");
            }
        }

        try
        {
            XMPPServer.getInstance().getVCardManager().setVCard(jid.getNode(), vCard);
        }
        catch (Exception e)
        {
            XMPPServer.getInstance().getIQRouter().route(iq);
        }
    }

    public void broadcastDeletePresenceUpdate(JID jid)
    {
        broadcastPresenceUpdate(jid,null,false);
    }

    public void broadcastPublishPresenceUpdate(JID jid, Avatar avatar,boolean shrinked)
    {
        broadcastPresenceUpdate(jid,avatar,shrinked);
    }

    /**
    * send a Presence to the server, which routes it to subscribers
     * @param jid
     *            is the senders jid
     * @param publish whether a photo is published or not
     * */
    private void broadcastPresenceUpdate(JID jid, Avatar avatar,boolean shrinked)
    {
        User usr;
        try
        {
            usr = XMPPServer.getInstance().getUserManager().getUser(jid.getNode());
            Presence presenceStanza = XMPPServer.getInstance().getPresenceManager().getPresence(usr);
            presenceStanza.setID(UUID.randomUUID().toString());
            if (presenceStanza.getFrom()==null)
            {
                presenceStanza.setFrom(jid);
            }

            Element x = presenceStanza.getChildElement("x", NAMESPACE_VCARD_TEMP_X_UPDATE);
            if (x==null)
            {
                x=presenceStanza.addChildElement("x", NAMESPACE_VCARD_TEMP_X_UPDATE);
            }

            Element photo = x.element("photo");

            if (photo==null)
            {
                photo=x.addElement("photo");
            }

            if (avatar!=null)
            {
                if (!shrinked)
                {
                    if (avatar.getMainHash()!=null)
                    {
                        photo.setText(avatar.getMainHash());
                    }
                }
                else
                {
                    String sha1=avatar.getMainHashShrinked();
                    if (sha1!=null)
                    {
                        photo.setText(sha1);
                    }
                }
            }

            XMPPServer.getInstance().getPresenceRouter().route(presenceStanza);

        }
        catch (UserNotFoundException e)
        {
            Log.error("Could not send presence: "+e.getMessage());
        }
    }

    private void handleIncomingIQ(IQ iq, Session session) {

        if (iq.getType()!=Type.set)
            return;

        if (iq.getChildElement()!=null)
        {
            Element childElement = iq.getChildElement();
            String childns = childElement.getNamespaceURI();

            if (childns!=null)
            {
                if (childns.equalsIgnoreCase(NAMESPACE_PUBSUB)) // PUBUB Packet, check for XEP-0084
                {

                    Element publish = null;

                    if ((publish=childElement.element("publish"))!=null)
                    {
                        //Publish packet, check for XEP-84 NAMESPACE
                        if (publish.attribute("node")!=null&&publish.attributeValue("node").equalsIgnoreCase(NAMESPACE_METADATA))
                        {
                            Log.debug("Processing incoming pubsub / pep avatar publish (XEP-0084)");
                            Element item = publish.element("item");
                            if (item!=null)
                            {
                                Element metadata=item.element("metadata");
                                if (metadata!=null&&metadata.element("info")!=null)
                                {
                                    /*
                                     * Upon receiving a publication request to the 'urn:xmpp:avatar:metadata' node the
                                     * service MUST look up the corresponding item published in the 'urn:xmpp:avatar:data'
                                     * node and store the content of the data element as a photo in the vcard.
                                     * Services MUST consider the fact that the metadata node might 
                                     * contain multiple info elements and MUST pick the info element that does not
                                     * point to an exernal URL.
                                     * Services SHOULD verify that the SHA-1 hash of the image matches the id.
                                     * */
                                    
                                    Avatar avatar = getAvatarWithInfotag(iq.getFrom(),metadata.element("info"));
                                    if (avatar!=null)
                                    {
                                        if (avatar.isValidHash(avatar.getMetadata().getId()))
                                        {
                                            if (XEP398Plugin.XMPP_DELETEOTHERAVATAR_ENABLED.getValue())
                                            {
                                                broadcastPublishPresenceUpdate(iq.getFrom(),avatar,false);
                                            }
                                            else
                                            {
                                                routeVCardUpdateToServer(iq.getFrom(),avatar);
                                                broadcastPublishPresenceUpdate(iq.getFrom(),avatar,true);
                                            }
                                        }
                                        else {
                                            Log.error("Calculated Hash does not equal to Metadata id! ("+iq.getFrom()+")");
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (childElement.element("retract")!=null||childElement.element("delete")!=null)
                    {
                        //Delete an avatar here
                        Log.debug("Processing incoming pubsub / pep avatar retract/delete (XEP-0084)");
                        if (XEP398Plugin.XMPP_DELETEOTHERAVATAR_ENABLED.getValue())
                        {
                            deleteVCardAvatar(iq.getFrom());
                        }
                        broadcastDeletePresenceUpdate(iq.getFrom());
                    }
                }
                else
                if (childns.equalsIgnoreCase(NAMESPACE_VCARD_TEMP))
                {
                    Element vcard = null;
                    if ((vcard=iq.getElement().element("vCard"))!=null)
                    {
                        //We got a vcard, check if is empty or net
                        if (vcard.hasContent())
                        {
                            Log.debug("Processing incoming vcard, we have content and checking for an avatar now...(XEP-0153)");
                            Element photo = null;
                            if ((photo=vcard.element("PHOTO"))!=null) {
                                Element binval = null;
                                if ((binval=photo.element("BINVAL"))!=null) {
                                    if (binval.hasContent()) {
                                        Log.debug("We have a filled BINVAL Element, we will save the avatar to PEP storage too");
                                        Element type = null;
                                        if ((type=photo.element("TYPE"))!=null) {
                                            Log.debug("The avatar is of type "+type.getText());
                                        }

                                        /*
                                         * Upon receiving a vCard publication request with a valid photo attached to
                                         * it a service MUST first publish an item to the 'urn:xmpp:avatar:data'
                                         * node on behalf of the requesting entity. The id of that item MUST be
                                         * the SHA-1 hash of the image as described in XEP-0084. Afterwards the
                                         * service MUST publish a new item to the 'urn:xmpp:avatar:metadata' node
                                         * with one info element that represents the newly published image using
                                         * the type value from the vCard as a type attribute in the info element.

                                           After publication the service SHOULD send out notification messages to
                                           all subscribers of the metadata node.
                                         * */

                                         Avatar avatar = buildAvatar(binval.getText(), type.getText());

                                         routeDataToServer(iq.getFrom(), avatar);
                                         routeMetaDataToServer(iq.getFrom(), avatar);
                                         /*if (!XEP398Plugin.XMPP_DELETEOTHERAVATAR_ENABLED.getValue())
                                         {
                                             routeVCardUpdateToServer(iq.getFrom(), avatar);
                                         }*/
                                    }
                                }
                            }
                            else {
                                deleteVCardAvatar(iq.getFrom());
                                deletePEPAvatar(iq.getFrom());
                            }
                        }
                        else {
                            deleteVCardAvatar(iq.getFrom());
                            deletePEPAvatar(iq.getFrom());
                        }
                    }
                }
            }
            else {
                Log.warn("We received a packet without namespace attribute",iq.toXML());
            }
        }
    }

    private void handleOutgoingPresence(Presence p, Session session)
    {
        Element x = p.getChildElement("x", NAMESPACE_VCARD_TEMP_X_UPDATE);
        Avatar avatar = getAvatar(p.getFrom());

        if (avatar==null)
        {
            avatar = getAvatarFromVcard(p.getFrom());
        }

        if (avatar!=null)
        {
            /*
             * The “Business Rules” section of XEP-0153 tells entities to include a hash of the vCard
             * avatar in their presence. However this requires clients to retrieve the avatar on every
             * connect to calculate the hash. To avoid this, services MUST include the hash on behalf
             * of their users in every available presence that does not contain an empty photo element
             * wrapped in an x element qualified by the 'vcard-temp:x:update' namespace. Empty x elements
             * qualified by the 'vcard-temp:x:update' namespace (those without a photo element as child)
             * MUST be overwritten. Presences where the content of the photo element is not empty and not
             * equal to the hash calculated by the service MAY be overwritten.
             * */

            if (x==null)
            {
                x = p.addChildElement("x", NAMESPACE_VCARD_TEMP_X_UPDATE);
            }

            Element photo = x.element("photo");

            if (photo==null||!photo.getText().isEmpty())
            {
                if (photo==null)
                {
                    photo = x.addElement("photo");
                }

                if (XEP398Plugin.XMPP_DELETEOTHERAVATAR_ENABLED.getValue())
                {
                    photo.setText(avatar.getMainHash());
                }
                else 
                {
                    if (XEP398Plugin.XMPP_SHRINKVCARDIMG_ENABLED.getValue())
                    {
                        String hash = avatar.getMainHashShrinked();
                        Log.warn(hash);
                        if (hash!=null)
                        {
                            photo.setText(hash);
                        }
                        else
                        {
                            Log.error("Could not shrink avatar image.");
                        }
                    }
                    else
                    {
                        photo.setText(avatar.getMainHash());
                    }
                }
            }
        }
    }

    @Override
    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed)
            throws PacketRejectedException {

        if (XEP398Plugin.XMPP_AVATARCONVERSION_ENABLED.getValue()&&packet!=null)
        {
            if (packet instanceof IQ && incoming && processed)
            {
                if (packet.getFrom()!=null&&packet.getFrom().getDomain().equalsIgnoreCase(XMPPServer.getInstance().getServerInfo().getXMPPDomain()))
                {
                    handleIncomingIQ((IQ)packet,session);
                }
            }
            else 
            if (packet instanceof Presence && !incoming && processed) 
            {
                if (packet.getFrom()!=null&&packet.getFrom().getDomain().equalsIgnoreCase(XMPPServer.getInstance().getServerInfo().getXMPPDomain()))
                {
                    handleOutgoingPresence((Presence) packet,session);
                }
            }
        }
    }

    private void deleteVCardAvatar(JID from)
    {
        Element vcard = XMPPServer.getInstance().getVCardManager().getVCard(from.getNode());
        Element vcardphoto = vcard.element("PHOTO");

        if (vcardphoto==null)
        {
            vcardphoto=vcard.addElement("PHOTO");
            vcardphoto.addElement("BINVAL");
            vcardphoto.addElement("TYPE");
        }

        Element binval = vcardphoto.element("BINVAL");
        Element type = vcardphoto.element("TYPE");
        if (binval!=null)
        {
            binval.setText("");
        }
        if (type!=null)
        {
            type.setText("");
        }

        try
        {
            XMPPServer.getInstance().getVCardManager().setVCard(from.getNode(), vcard);
        }
        catch (Exception e)
        {
            Log.error("Could not update vcard: "+e.getMessage());
        }
    }
}
