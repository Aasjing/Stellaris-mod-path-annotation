package com.stellaris.modmanager;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

public class ThumbnailCache {

    private static final Path CACHE_DIR;

    static {
        CACHE_DIR = Paths.get(System.getProperty("user.home"), ".stellaris-mod-manager", "thumbnails");
        try {
            Files.createDirectories(CACHE_DIR);
        } catch (IOException ignored) {
        }
    }

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            r -> {
                Thread t = new Thread(r, "ThumbCache");
                t.setDaemon(true);
                return t;
            }
    );

    public interface LoadCallback {
        void onLoaded(BufferedImage image);
    }

    public static void loadAsync(String originalPath, String modFolderName, int targetSize, LoadCallback callback) {
        EXECUTOR.submit(() -> {
            BufferedImage img = getScaled(originalPath, modFolderName, targetSize);
            SwingUtilities.invokeLater(() -> callback.onLoaded(img));
        });
    }

    public static BufferedImage getScaled(String originalPath, String modFolderName, int targetSize) {
        if (originalPath == null) {
            return null;
        }
        Path originalFile = Paths.get(originalPath);
        if (!Files.isRegularFile(originalFile)) {
            return null;
        }

        String cacheKey = modFolderName + "_" + targetSize + ".png";
        Path cachedFile = CACHE_DIR.resolve(cacheKey);

        try {
            long origModified = Files.getLastModifiedTime(originalFile).toMillis();

            if (Files.exists(cachedFile)) {
                long cacheModified = Files.getLastModifiedTime(cachedFile).toMillis();
                if (cacheModified >= origModified) {
                    BufferedImage cached = ImageIO.read(cachedFile.toFile());
                    if (cached != null) {
                        return cached;
                    }
                }
            }

            BufferedImage original = ImageIO.read(originalFile.toFile());
            if (original == null) {
                return null;
            }

            BufferedImage scaled = scaleImage(original, targetSize);

            Files.deleteIfExists(cachedFile);
            ImageIO.write(scaled, "png", cachedFile.toFile());
            Files.setLastModifiedTime(cachedFile, FileTime.fromMillis(origModified));

            return scaled;
        } catch (IOException e) {
            return null;
        }
    }

    private static BufferedImage scaleImage(BufferedImage source, int targetSize) {
        int w = source.getWidth();
        int h = source.getHeight();
        double scale = Math.min((double) targetSize / w, (double) targetSize / h);
        int newW = Math.max(1, (int) (w * scale));
        int newH = Math.max(1, (int) (h * scale));

        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(source, 0, 0, newW, newH, null);
        g2.dispose();

        return scaled;
    }
}
