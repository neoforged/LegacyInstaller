/*
 * Installer
 * Copyright (c) 2016-2018.
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
package net.minecraftforge.installer.ui;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraftforge.installer.SimpleInstaller;

final class Images {
    private Images() {}

    static List<Image> getWindowIcons(String legacyBase64Icon) {
        if (legacyBase64Icon != null) {
            try {
                return Collections.singletonList(ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(legacyBase64Icon))));
            } catch (UnsupportedEncodingException | IOException ignored) {} // Use the defaults
        }
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
