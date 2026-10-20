import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/** Deterministic native-asset palette compiler; requires only the workspace JDK. */
class SculkPalette {
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("client.jar output-textures preview.png");
        Path output=Path.of(args[1]);Map<String,BufferedImage[]> previews=new HashMap<>();
        try(ZipFile zip=new ZipFile(args[0])) {
            var entries=zip.entries();
            while(entries.hasMoreElements()) {
                ZipEntry e=entries.nextElement();String path=e.getName();
                if(!path.startsWith("assets/minecraft/textures/block/sculk")||!path.endsWith(".png"))continue;
                BufferedImage source=ImageIO.read(zip.getInputStream(e));int width=source.getWidth();
                if(width!=16||source.getHeight()%width!=0)throw new IllegalStateException("Unexpected native texture geometry: "+path);
                String name=Path.of(path).getFileName().toString();BufferedImage[] stages=new BufferedImage[4];
                for(int stage=0;stage<4;stage++) {
                    BufferedImage image=new BufferedImage(width,width,BufferedImage.TYPE_INT_ARGB);
                    for(int y=0;y<width;y++)for(int x=0;x<width;x++)image.setRGB(x,y,palette(source.getRGB(x,y),stage));
                    stages[stage]=image;
                    if(stage>0){Path dest=output.resolve("stage_"+stage).resolve(name);Files.createDirectories(dest.getParent());ImageIO.write(image,"PNG",dest.toFile());}
                }
                previews.put(name,stages);
            }
        }
        BufferedImage preview=new BufferedImage(512,426,BufferedImage.TYPE_INT_RGB);Graphics2D g=preview.createGraphics();
        g.setColor(new Color(40,40,40));g.fillRect(0,0,512,426);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        String[] names={"sculk.png","sculk_vein.png","sculk_catalyst_side.png"};
        for(int row=0;row<names.length;row++)for(int stage=0;stage<4;stage++)g.drawImage(previews.get(names[row])[stage],stage*128,row*128,128,128,null);
        g.setColor(Color.WHITE);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,15));String[] labels={"Healthy","Dull","Fading","Exhausted"};
        for(int stage=0;stage<4;stage++)g.drawString(labels[stage],stage*128+12,410);g.dispose();
        Path target=Path.of(args[2]);Files.createDirectories(target.getParent());ImageIO.write(preview,"PNG",target.toFile());
        System.out.println("Compiled "+previews.size()*3+" static 16x16 palette textures.");
    }
    static int palette(int argb,int stage) {
        int a=argb>>>24;if(stage==0||a==0)return argb;
        int r=(argb>>>16)&255,g=(argb>>>8)&255,b=argb&255;
        double l=.2126*r+.7152*g+.0722*b;
        int[] dull={clamp(r*.5+l*.15+5),clamp(g*.55+l*.12+4),clamp(b*.55+l*.16+5)};
        int[] brown={clamp(22+l*190/255),clamp(13+l*140/255),clamp(7+l*83/255)};
        int[] rgb=stage==1?dull:brown;
        if(stage==2){double blend=g>55&&b>70&&r<g*.75?.45:.85;rgb=new int[3];for(int i=0;i<3;i++)rgb[i]=clamp(dull[i]*(1-blend)+brown[i]*blend);}
        return (a<<24)|(rgb[0]<<16)|(rgb[1]<<8)|rgb[2];
    }
    static int clamp(double v){return Math.clamp((int)Math.round(v),0,255);}
}
