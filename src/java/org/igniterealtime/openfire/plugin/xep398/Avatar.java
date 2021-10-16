package org.igniterealtime.openfire.plugin.xep398;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;

import org.jivesoftware.openfire.vcard.PhotoResizer;
import org.jivesoftware.util.Base64;
import org.jivesoftware.util.JiveGlobals;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Avatar {

    private static final Logger Log = LoggerFactory.getLogger(Avatar.class);

    private String avatar_base64 = null;
    private String avatar_base64shrinked = null;
    private String rawHash = null;
    private String shrinkedHash = null;

    private AvatarMetadata metadata = null;

    public Avatar() {
        this.metadata = new AvatarMetadata();
    }

    public AvatarMetadata getMetadata() {
        return this.metadata;
    }

    public String getImageString()
    {
        return avatar_base64;
    }

    public byte[] getImageBytes() {
        byte[] result = null;
        try {
            result = Base64.decode(this.avatar_base64,Base64.DONT_BREAK_LINES);
        }
        catch (Exception e)
        {
            result = Base64.decode(this.avatar_base64);
        }

        return result;
    }

    public String getMainHash() {
        return rawHash;
    }

    public String getMainHashShrinked() {
        return shrinkedHash;
    }

    public boolean isValidHash(String test)
    {
        return test.equalsIgnoreCase(rawHash);
    }

    private boolean calHashes() {
        byte[] raw = getImageBytes();
        byte[] shrinked = getShrinkedImage(raw);

        try {
            rawHash=getSHA1Hash(raw);
        }
        catch (Exception e)
        {
            Log.error("Error while calculating Hashes (Index 0): ",e);
            return false;
        }

        try {
            if (shrinked!=null)
            {
                shrinkedHash=getSHA1Hash(shrinked);
            }
        }
        catch (Exception e)
        {
            Log.error("Error while calculating Hashes (Index 4): ",e);
        }

        return true;
    }

    public void setImage(String binval)
    {
        this.avatar_base64 = Base64.encodeBytes(Base64.decode(binval.trim(), Base64.DONT_BREAK_LINES),Base64.DONT_BREAK_LINES);

        if (calHashes())
        {
            byte[] image = getImageBytes();

            BufferedImage img = Avatar.getImageFromBytes(image);
            if (img!=null)
            {
                this.getMetadata().setHeight(img.getHeight());
                this.getMetadata().setWidth(img.getWidth());
            }
            else
            {
                Log.error("Could not set height/width of image");
            }
            
            String tmp = calcShrinkedImage();
            if (tmp!=null)
            {
                this.avatar_base64shrinked=tmp;
            }
        }
    }

    public String getShrinkedImage() {
        return avatar_base64shrinked;
    }

    private String calcShrinkedImage() {
        try {
            byte[] imgdata = getImageBytes();
            if (imgdata!=null)
            {
                byte[] shrinked = getShrinkedImage(imgdata);
                if (shrinked!=null)
                {
                    try {
                        return Base64.encodeBytes(shrinked,Base64.DONT_BREAK_LINES);
                    }
                    catch (Exception e)
                    {
                        return Base64.encodeBytes(shrinked);
                    }
                }
                else {
                    return null;
                }
            }
            else {
                return null;
            }
        }
        catch (Exception e)
        {
            Log.warn(e.getMessage(),e);
            return null;
        }
    }

    private byte[] getShrinkedImage(byte[] image)
    {
        String mimetype = getMetadata().getType();
        if (image==null||mimetype==null)
            return null;
        
        final Iterator it = ImageIO.getImageWritersByMIMEType( mimetype );
        if ( !it.hasNext() )
        {
            Log.debug("getShrinkedImage: Cannot resize avatar. No writers available for MIME type {}.", mimetype );
            return null;
        }
        final ImageWriter iw = (ImageWriter) it.next();

        final int targetDimension = JiveGlobals.getIntProperty( PhotoResizer.PROPERTY_TARGETDIMENSION, PhotoResizer.PROPERTY_TARGETDIMENSION_DEFAULT );
        final byte[] resized = PhotoResizer.cropAndShrink( image, targetDimension, iw );
        if (resized!=null)
        {
            return resized;
        }
        else
        {
            Log.debug("getShrinkedImage: Cannot resize avatar. PhotoResizer.cropAndShrink failed!");
            return null;
        }
    }

    public static BufferedImage getImageFromBytes(byte[] image)
    {
        try
        {
            if (image!=null)
            {
                BufferedInputStream bin = new BufferedInputStream(new ByteArrayInputStream(image));
                BufferedImage result = ImageIO.read(bin);
                bin.close();
                if (result==null)
                {
                    Log.error("getImageFromBytes: Error while converting bytes to BufferedImage: no image in buffer");
                    return null;
                }
                else {
                    return result;
                }
            }
            else
            {
                Log.error("Error while converting bytes to BufferedImage: no image in buffer");
                return null;
            }
        }
        catch (IOException e )
        {
            Log.error("getImageFromBytes: Error while converting bytes to BufferedImage: "+e.getMessage(),e);
            return null;
        }
    }

    public static String getSHA1Hash(byte[] convertme) throws NoSuchAlgorithmException
    {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return byteArray2Hex(md.digest(convertme));
    }

    public static String byteArray2Hex(final byte[] hash)
    {
        Formatter formatter = new Formatter();
        try
        {
            for (byte b : hash)
            {
                formatter.format("%02x", b);
            }
            return formatter.toString();
        }
        finally
        {
            formatter.close();
        }
    }
    
    public static Avatar parse(String jsonstr)
    {

        try { 
            JSONObject json = new JSONObject(jsonstr.trim());

            Avatar result = new Avatar();
            AvatarMetadata meta = new AvatarMetadata();

            result.avatar_base64 = json.has("base64")&&json.getString("base64")!=null&&!json.getString("base64").isEmpty()?json.getString("base64"):null;
            if (result.avatar_base64==null)
            {
                return null;
            }

            result.shrinkedHash = json.has("hashshrinked")&&json.getString("hashshrinked")!=null&&!json.getString("hashshrinked").isEmpty()?json.getString("hashshrinked"):null;
            result.avatar_base64shrinked=json.has("base64shrinked")&&json.getString("base64shrinked")!=null&&!json.getString("base64shrinked").isEmpty()?json.getString("base64shrinked"):null;

            result.metadata=meta;
            if (json.has("metadata"))
            {
                JSONObject metadata = json.getJSONObject("metadata");
                meta.setId(metadata.has("id")&&metadata.getString("id")!=null&&!metadata.getString("id").isEmpty()?metadata.getString("id"):null);
                meta.setWidth(metadata.has("height")&&metadata.getString("height")!=null&&!metadata.getString("height").isEmpty()?Integer.parseInt(metadata.getString("height")):0);
                meta.setHeight(metadata.has("width")&&metadata.getString("width")!=null&&!metadata.getString("width").isEmpty()?Integer.parseInt(metadata.getString("width")):0);
                meta.setType(metadata.has("type")&&metadata.getString("type")!=null&&!metadata.getString("type").isEmpty()?metadata.getString("type"):null);
                result.rawHash=meta.getId();
            }
            
            return result;
          }
        catch (JSONException e)
        {
            Log.error("Could not parse Avatar Json: {}\n{}", e.getMessage(),jsonstr);
            return null;
        }
    }

    @Override
    public String toString() {
        if (this.shrinkedHash!=null)
        {
            return "{\n"+
                   "\"base64\":\""+avatar_base64+"\",\n"+
                   "\"base64shrinked\":\""+this.avatar_base64shrinked+"\",\n"+
                   "\"hashshrinked\":\""+this.shrinkedHash+"\",\n"+
                   "\"metadata\":"+metadata.toString()+"\n"+
                   "}";
        }
        else {
            return "{\n"+
                    "\"base64\":\""+avatar_base64+"\",\n"+
                    "\"base64shrinked\":\"\",\n"+
                    "\"hashshrinked\":\"\",\n"+
                    "\"metadata\":"+metadata.toString()+"\n"+
                    "}";
        }
    }
}
