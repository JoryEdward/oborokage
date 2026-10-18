import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Dev-only: tiles screenshots into a labeled sheet. Usage: java ContactSheet.java out.png cols width label=file... */
public class ContactSheet {
  public static void main(String[] a) throws Exception {
    int cols = Integer.parseInt(a[1]), w = Integer.parseInt(a[2]);
    java.util.List<String[]> items = new ArrayList<>();
    for (int i = 3; i < a.length; i++) items.add(a[i].split("=", 2));
    BufferedImage first = ImageIO.read(new File(items.get(0)[1]));
    int h = w * first.getHeight() / first.getWidth();
    int rows = (items.size() + cols - 1) / cols;
    BufferedImage out = new BufferedImage(cols * w, rows * (h + 18), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.setFont(new Font("SansSerif", Font.BOLD, 13));
    for (int i = 0; i < items.size(); i++) {
      int x = (i % cols) * w, y = (i / cols) * (h + 18);
      g.drawImage(ImageIO.read(new File(items.get(i)[1])), x, y + 18, w, h, null);
      g.setColor(Color.WHITE);
      g.drawString(items.get(i)[0], x + 4, y + 14);
    }
    ImageIO.write(out, "png", new File(a[0]));
  }
}
