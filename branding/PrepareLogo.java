import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Deterministic app-asset export; original logo drawing and colors are preserved. */
public final class PrepareLogo {
    static boolean ink(int pixel) {
        return (pixel>>>24)!=0 && (((pixel>>>16)&255)<250 || ((pixel>>>8)&255)<250 || (pixel&255)<250);
    }
    static BufferedImage scale(BufferedImage image,int width) {
        int height=(int)Math.round(image.getHeight()*(double)width/image.getWidth());
        BufferedImage out=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=out.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(Color.WHITE);g.fillRect(0,0,width,height);g.drawImage(image,0,0,width,height,null);g.dispose();return out;
    }
    public static void main(String[] args) throws Exception {
        BufferedImage source=ImageIO.read(new File(args[0]));
        int minX=source.getWidth(),minY=source.getHeight(),maxX=-1,maxY=-1;
        int[] row=new int[source.getWidth()];
        for(int y=0;y<source.getHeight();y++) {
            source.getRGB(0,y,row.length,1,row,0,row.length);
            for(int x=0;x<row.length;x++) if(ink(row[x])) {minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}
        }
        if(maxX<minX)throw new IllegalStateException("Logo has no visible content");
        BufferedImage full=scale(source.getSubimage(minX,minY,maxX-minX+1,maxY-minY+1),1200);
        File out=new File(args[1]);out.mkdirs();
        // Match the application's theme when exporting a header resource.
        if(args.length>2) {
            Color background=Color.decode(args[2]);
            for(int y=0;y<full.getHeight();y++) for(int x=0;x<full.getWidth();x++) {
                int pixel=full.getRGB(x,y),red=(pixel>>>16)&255,green=(pixel>>>8)&255,blue=pixel&255;
                double alpha;int fr,fg,fb;
                if(red-green>15 && red-blue>15) {
                    alpha=(255-blue)/255.0;fr=255;fg=102;fb=0;
                } else {
                    alpha=Math.max(0,Math.min(1,(255-(red+green+blue)/3.0)/127.0));
                    fr=fg=fb=128;
                }
                int rr=(int)Math.round(fr*alpha+background.getRed()*(1-alpha));
                int gg=(int)Math.round(fg*alpha+background.getGreen()*(1-alpha));
                int bb=(int)Math.round(fb*alpha+background.getBlue()*(1-alpha));
                full.setRGB(x,y,0xFF000000|(rr<<16)|(gg<<8)|bb);
            }
        }
        ImageIO.write(full,"png",new File(out,"climagold_full.png"));
        // First blank column after the leading orange symbol separates it from the wordmark.
        BufferedImage originalSmall=scale(source.getSubimage(minX,minY,maxX-minX+1,maxY-minY+1),1200);
        int end=1;
        for(int x=1;x<originalSmall.getWidth();x++) {
            boolean any=false;for(int y=0;y<full.getHeight();y++)if(ink(originalSmall.getRGB(x,y))){any=true;break;}
            if(!any){end=x;break;}
        }
        if(end<20 || end>full.getWidth()/3)throw new IllegalStateException("Cannot identify original logo symbol");
        BufferedImage mark=scale(source.getSubimage(minX,minY,maxX-minX+1,maxY-minY+1),1200).getSubimage(0,0,end,full.getHeight());
        BufferedImage icon=new BufferedImage(512,512,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=icon.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,512,512);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        double factor=300.0/Math.max(mark.getWidth(),mark.getHeight());int w=(int)(mark.getWidth()*factor),h=(int)(mark.getHeight()*factor);
        g.drawImage(mark,(512-w)/2,(512-h)/2,w,h,null);g.dispose();ImageIO.write(icon,"png",new File(out,"climagold_mark.png"));
        System.out.println("Source "+source.getWidth()+"x"+source.getHeight()+"; bounds "+minX+","+minY+"-"+maxX+","+maxY+"; full "+full.getWidth()+"x"+full.getHeight()+"; symbol width "+end);
    }
}
