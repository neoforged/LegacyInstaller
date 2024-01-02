package net.minecraftforge.installer;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

final class Images {
    private Images() {
    }

    static List<Image> getWindowIcons() {
        List<Image> result = new ArrayList<>();
        result.add(getImage("/icons/neoforged_background_16x16.png"));
        result.add(getImage("/icons/neoforged_background_32x32.png"));
        result.add(getImage("/icons/neoforged_background_128x128.png"));
        return result;
    }

    static BufferedImage getImage(String path) {
        return getImage(path, null);
    }

    static BufferedImage getImage(String path, String default_) {
        try {
            InputStream in = SimpleInstaller.class.getResourceAsStream(path);
            if (in == null && default_ != null)
                in = new ByteArrayInputStream(InstallerPanel.hexToByteArray(default_));
            return ImageIO.read(in);
        } catch (IOException e) {
            if (default_ == null)
                throw new RuntimeException(e);
            else
                return new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        }
    }
}
