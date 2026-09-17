import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

class LogoCleaner {
  static boolean neutral(int c) {
    int a = c >>> 24, r = (c >>> 16) & 255, g = (c >>> 8) & 255, b = c & 255;
    return a > 0 && Math.abs(r - g) <= 3 && Math.abs(g - b) <= 3;
  }
  static int gray(int c) { return (c >>> 16) & 255; }
  public static void main(String[] args) throws Exception {
    BufferedImage src = ImageIO.read(new File(args[0]));
    int w = src.getWidth(), h = src.getHeight();
    boolean[][] remove = new boolean[h][w];
    for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
      int c=src.getRGB(x,y); if(!neutral(c)) continue; int v=gray(c);
      int[][] n={{x-1,y},{x+1,y},{x,y-1},{x,y+1}};
      for(int[] p:n) if(p[0]>=0&&p[0]<w&&p[1]>=0&&p[1]<h) { int q=src.getRGB(p[0],p[1]); if(neutral(q)&&Math.abs(v-gray(q))>=20){remove[y][x]=true;break;} }
    }
    BufferedImage out=new BufferedImage(1920,1080,BufferedImage.TYPE_INT_ARGB);
    Graphics2D g=out.createGraphics(); g.setComposite(AlphaComposite.Clear); g.fillRect(0,0,1920,1080); g.setComposite(AlphaComposite.SrcOver); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC); g.drawImage(src,0,0,1920,1080,null); g.dispose();
    for(int y=0;y<1080;y++) for(int x=0;x<1920;x++) { int sx=x*w/1920, sy=y*h/1080; if(remove[sy][sx]) out.setRGB(x,y,0); }
    ImageIO.write(out,"png",new File(args[1]));
  }
}
