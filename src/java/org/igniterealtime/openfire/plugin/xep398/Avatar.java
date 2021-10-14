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

    private byte[] image    = null;
    private String avatar_base64 = null;
    private String hash;
    private Metadata metadata = null; 

    public Avatar() {
        this.metadata = new Metadata();
    }

    public Metadata getMetadata() {
        return this.metadata;
    }

    public byte[] getImageBytes()
    {
        return image;
    }

    public String getImageString()
    {
        return avatar_base64;
    }

    public void setImage(byte[] image)
    {
        setImage(image,false,true);
    }
    
    public void setImage(byte[] image, boolean b64urldecode, boolean retry)
    {
        int params = 0;//Base64.DONT_BREAK_LINES;
        if (b64urldecode)
        {
            params|=Base64.URL_SAFE;
        }
        
        this.avatar_base64 = Base64.encodeBytes(image,params);
        this.image = image;
        try {
            this.hash=getSHA1Hash(this.image);
        }
        catch (Exception e)
        {
            Log.error("Could not calc hash of image");
        }

        BufferedImage img = Avatar.getImageFromBytes(image);
        if (img==null&&retry)
        {
            setImage(image,true,false);
        }
        
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

    public void setImage(String binval)
    {
        setImage(binval,false,true);
    }

    public void setImage(String binval, boolean b64urldecode, boolean retry)
    {
        this.avatar_base64 = binval;
        int params = 0;//Base64.DONT_BREAK_LINES;
        if (b64urldecode)
        {
            params|=Base64.URL_SAFE;
        }
        this.image= Base64.decode(binval,params);

        try {
            this.hash=getSHA1Hash(this.image);
        }
        catch (Exception e)
        {
            Log.error("Could not calc hash of image");
        }

        BufferedImage img = Avatar.getImageFromBytes(image);
        if (img==null&&retry)
        {
            setImage(binval,true,false);
        }

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

    public String getCalculatedHash() {
        return this.hash;
    }

    public String getSHA1FromShrinkedImage()
    {
        String mimetype = getMetadata().getType();
        if (getImageBytes()==null||mimetype==null)
            return null;
        
        final Iterator it = ImageIO.getImageWritersByMIMEType( mimetype );
        if ( !it.hasNext() )
        {
            Log.warn("getSHA1FromShrinkedImage: Cannot resize avatar. No writers available for MIME type {}.", mimetype );
            return null;
        }
        final ImageWriter iw = (ImageWriter) it.next();

        final int targetDimension = JiveGlobals.getIntProperty( PhotoResizer.PROPERTY_TARGETDIMENSION, PhotoResizer.PROPERTY_TARGETDIMENSION_DEFAULT );
        final byte[] resized = PhotoResizer.cropAndShrink( image, targetDimension, iw );
        if (resized!=null)
        {
            try {
                return getSHA1Hash(resized);
            } catch (NoSuchAlgorithmException e) {
                Log.warn("getSHA1FromShrinkedImage: Cannot resize avatar. "+e.getMessage());
                return null;
            }
        }
        else
        {
            Log.warn("getSHA1FromShrinkedImage: Cannot resize avatar. PhotoResizer.cropAndShrink failed!");
            return null;
        }
    }
    
    public String getShrinkedImage()
    {
        String mimetype = getMetadata().getType();
        if (getImageBytes()==null||mimetype==null)
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
            try {
                return Base64.encodeBytes(resized);
            } catch (Exception e) {
                Log.warn("getShrinkedImage: Cannot resize avatar. "+e.getMessage());
                return null;
            }
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
