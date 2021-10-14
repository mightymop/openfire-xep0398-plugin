package org.igniterealtime.openfire.plugin.xep398;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;

import org.jivesoftware.openfire.vcard.PhotoResizer;
import org.jivesoftware.util.Base64;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Avatar {

    private static final Logger Log = LoggerFactory.getLogger(Avatar.class);

    private String avatar_base64 = null;
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
            result = Base64.decode(this.avatar_base64);
        }
        catch (Exception e)
        {
            result = Base64.decode(this.avatar_base64,Base64.DONT_BREAK_LINES);
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
        byte[] raw = Base64.decode(this.avatar_base64);
        byte[] shrinked = getShrinkedImage(Base64.decode(this.avatar_base64));

        try {
            rawHash=getSHA1Hash(raw);
        }
        catch (Exception e)
        {
            Log.error("Error while calculating Hashes (Index 0): ",e);
            return false;
        }

        try {
            shrinkedHash=getSHA1Hash(shrinked);
        }
        catch (Exception e)
        {
            Log.error("Error while calculating Hashes (Index 4): ",e);
        }

        return true;
    }

    public void setImage(String binval)
    {
        this.avatar_base64 = binval.trim();

        if (calHashes())
        {
            byte[] image;
            image =  Base64.decode(this.avatar_base64);

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
        }
    }
    
    public String getShrinkedImage() {
        return Base64.encodeBytes(getShrinkedImage(Base64.decode(this.avatar_base64)));
    }

    private byte[] getShrinkedImage(byte[] image)
    {
        String mimetype = getMetadata().getType();
        if (image==null||mimetype==null)
            return null;
        
        final Iterator it = ImageIO.getImageWritersByMIMEType( mimetype );
        if ( !it.hasNext() )
        {
            Log.warn("getShrinkedImage: Cannot resize avatar. No writers available for MIME type {}.", mimetype );
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
            Log.warn("getShrinkedImage: Cannot resize avatar. PhotoResizer.cropAndShrink failed!");
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

    @Override
    public String toString() {
        return "{\"base64\":\""+avatar_base64+"\",\"metadata\":"+metadata.toString()+"}";
    }
}
