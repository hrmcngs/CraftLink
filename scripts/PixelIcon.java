import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Native 32x32 artwork. All geometry uses whole pixels and a fixed palette. */
public final class PixelIcon {
    private static final int EDGE=0x141711, BACK=0x292d27, BEZEL=0x94998a,
        LIGHT=0xd0d4c2, SHADE=0x60695b, SCREEN=0x294831,
        LINK=0x819b4c, LINK_LIGHT=0xb1c77a, LINK_DARK=0x4b602d,
        TOP=0xb6bcaa, LEFT=0x8d9783, RIGHT=0x626f59;
    private final BufferedImage image=new BufferedImage(32,32,BufferedImage.TYPE_INT_RGB);
    private void pixel(int x,int y,int color){if(x>=0&&x<32&&y>=0&&y<32)image.setRGB(x,y,color);}
    private void rect(int x,int y,int width,int height,int color){for(int j=y;j<y+height;j++)for(int i=x;i<x+width;i++)pixel(i,j,color);}
    private void line(int x,int y,int endX,int endY,int color){
        int dx=Math.abs(endX-x),sx=x<endX?1:-1,dy=-Math.abs(endY-y),sy=y<endY?1:-1,error=dx+dy;
        while(true){pixel(x,y,color);if(x==endX&&y==endY)return;int twice=2*error;if(twice>=dy){error+=dy;x+=sx;}if(twice<=dx){error+=dx;y+=sy;}}
    }
    private void face(int color,int... points){
        for(int y=0;y<32;y++)for(int x=0;x<32;x++){
            boolean inside=false;double px=x+.5,py=y+.5;
            for(int i=0,j=points.length-2;i<points.length;j=i,i+=2){
                int ax=points[i],ay=points[i+1],bx=points[j],by=points[j+1];
                if((ay>py)!=(by>py)&&px<(double)(bx-ax)*(py-ay)/(by-ay)+ax)inside=!inside;
            }
            if(inside)pixel(x,y,color);
        }
    }
    private void draw(){
        rect(0,0,32,32,EDGE);rect(1,1,30,30,BACK);
        line(1,1,30,1,SHADE);line(1,1,1,30,SHADE);
        // A single square link, behind the phone and cube.
        rect(12,15,11,8,EDGE);rect(13,16,9,6,LINK_DARK);
        rect(13,16,8,5,LINK);rect(15,17,5,3,BACK);
        line(14,16,20,16,LINK_LIGHT);line(14,20,20,20,LINK_DARK);
        // True three-face cube: symmetric top and equal-height side faces.
        face(LEFT,18,12,24,15,24,22,18,19);
        face(RIGHT,24,15,30,12,30,19,24,22);
        face(TOP,18,12,24,9,30,12,24,15);
        int[] outline={18,12,24,9,30,12,30,19,24,22,18,19,18,12};
        for(int i=0;i<outline.length-2;i+=2)line(outline[i],outline[i+1],outline[i+2],outline[i+3],EDGE);
        line(19,12,24,15,LIGHT);line(24,15,29,12,SHADE);line(24,16,24,21,EDGE);
        // Phone: parallel sides and a centered, undistorted five-pixel D-pad.
        rect(3,4,10,24,EDGE);rect(2,5,12,22,EDGE);
        rect(3,5,10,22,BEZEL);line(3,5,12,5,LIGHT);line(3,6,3,26,LIGHT);line(12,6,12,26,SHADE);
        rect(4,8,8,16,EDGE);rect(5,9,6,14,SCREEN);
        rect(6,15,5,3,EDGE);rect(7,14,3,5,EDGE);
        rect(6,16,5,1,LIGHT);rect(8,14,1,5,LIGHT);pixel(8,16,BEZEL);
        rect(6,6,4,1,EDGE);rect(7,25,3,1,EDGE);
    }
    public static void main(String[] args)throws IOException{
        Path directory=Path.of(args.length==0?"publishing/curseforge":args[0]);Files.createDirectories(directory);
        PixelIcon art=new PixelIcon();art.draw();ImageIO.write(art.image,"png",directory.resolve("icon-32.png").toFile());
        BufferedImage upload=new BufferedImage(512,512,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<512;y++)for(int x=0;x<512;x++)upload.setRGB(x,y,art.image.getRGB(x/16,y/16));
        ImageIO.write(upload,"png",directory.resolve("icon.png").toFile());
        System.out.println("Created native 32x32 pixel art and exact 16x enlargement in "+directory);
    }
}
