import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Extract the native 32x32 grid without modifying the selected upload artwork. */
public final class PixelIcon {
    public static void main(String[] args)throws IOException{
        Path directory=Path.of(args.length==0?"publishing/curseforge":args[0]);
        Path master=directory.resolve("icon.png");
        BufferedImage upload=ImageIO.read(master.toFile());
        if(upload==null||upload.getWidth()!=512||upload.getHeight()!=512)throw new IOException("icon.png must be the selected 512x512 artwork");
        BufferedImage small=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<32;y++)for(int x=0;x<32;x++)small.setRGB(x,y,upload.getRGB(x*16,y*16));
        for(int y=0;y<512;y++)for(int x=0;x<512;x++){
            if(upload.getRGB(x,y)!=small.getRGB(x/16,y/16))throw new IOException("Artwork is not an exact 32x32 grid; no files were changed");
        }
        Files.createDirectories(directory);
        ImageIO.write(small,"png",directory.resolve("icon-32.png").toFile());
        System.out.println("Extracted exact 32x32 grid; icon.png and logo.png remain unchanged");
    }
}
