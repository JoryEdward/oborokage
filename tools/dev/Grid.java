import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
/** Dev-only: Grid out.png cols in1 in2 ... (same-size tiles) */
public class Grid {
  public static void main(String[] a) throws Exception {
    int cols = Integer.parseInt(a[1]); var first = ImageIO.read(new File(a[2]));
    int w = first.getWidth(), h = first.getHeight(), n = a.length - 2, rows = (n + cols - 1) / cols;
    var out = new BufferedImage(w * cols, h * rows, BufferedImage.TYPE_INT_RGB);
    var g = out.createGraphics();
    for (int i = 0; i < n; i++) g.drawImage(ImageIO.read(new File(a[i + 2])), (i % cols) * w, (i / cols) * h, null);
    ImageIO.write(out, "png", new File(a[0]));
  }
}
