import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
public class ImgInfo {
  public static void main(String[] args) throws Exception {
    String[] files = {"assets/images/ch_player.png","assets/images/ch_yang_jiyeong.png","assets/images/ch_kim_hyeonjin.png","assets/images/ch_ju_dayeong.png","assets/images/ch_kim_junyeong.png"};
    for (String f : files) {
      BufferedImage img = ImageIO.read(new File(f));
      int minX = img.getWidth(), minY = img.getHeight(), maxX = -1, maxY = -1;
      for (int y=0;y<img.getHeight();y++) for (int x=0;x<img.getWidth();x++) {
        int a = (img.getRGB(x,y) >>> 24) & 0xff;
        if (a > 8) { if (x<minX) minX=x; if (y<minY) minY=y; if (x>maxX) maxX=x; if (y>maxY) maxY=y; }
      }
      System.out.println(f + " size=" + img.getWidth() + "x" + img.getHeight() + " content=" + minX + "," + minY + " to " + maxX + "," + maxY);
    }
  }
}