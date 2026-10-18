import java.io.File;
import javax.imageio.ImageIO;
/** Dev-only: Crop in.png out.png x y w h [scale] */
public class Crop {
  public static void main(String[] a) throws Exception {
    var img = ImageIO.read(new File(a[0]));
    var sub = img.getSubimage(Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4]), Integer.parseInt(a[5]));
    double s = a.length > 6 ? Double.parseDouble(a[6]) : 1;
    var out = new java.awt.image.BufferedImage((int) (sub.getWidth() * s), (int) (sub.getHeight() * s), java.awt.image.BufferedImage.TYPE_INT_RGB);
    var g = out.createGraphics();
    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.drawImage(sub, 0, 0, out.getWidth(), out.getHeight(), null);
    ImageIO.write(out, "png", new File(a[1]));
  }
}
