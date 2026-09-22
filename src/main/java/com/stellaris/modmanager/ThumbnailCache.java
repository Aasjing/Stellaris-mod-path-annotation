package com.stellaris.modmanager;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class ThumbnailCache {

    private static final Path CACHE_DIR;

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())),
            r -> {
                Thread t = new Thread(r, "ThumbCache");
                t.setDaemon(true);
                return t;
            }
    );

    private static final Map<String, BufferedImage> SCALED_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<BufferedImage>> SCALED_TASKS = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Animation>> FRAME_TASKS = new ConcurrentHashMap<>();
    private static final Map<String, Animation> STILL_CACHE = new ConcurrentHashMap<>();
    private static volatile String animatedKey;
    private static volatile Animation animatedFrames;

    private static final String GIF_IMAGE_METADATA_FORMAT = "javax_imageio_gif_image_1.0";
    private static final int DEFAULT_FRAME_DELAY_MS = 100;
    private static final int MIN_FRAME_DELAY_MS = 40;
    private static final int MAX_SCALED_CACHE_ENTRIES = 256;
    private static final int MAX_STILL_CACHE_ENTRIES = 32;

    static {
        CACHE_DIR = Paths.get(System.getProperty("user.home"), ".stellaris-mod-manager", "thumbnails");
        try {
            Files.createDirectories(CACHE_DIR);
        } catch (IOException ignored) {
        }
    }

    public interface LoadCallback {
        void onLoaded(BufferedImage image);
    }

    public interface FramesCallback {
        void onLoaded(Animation animation);
    }

    public record Animation(List<BufferedImage> frames, int[] delaysMs) {
    }

    public static void loadAsync(String sourcePath, String modFolderName, int targetSize, LoadCallback callback) {
        if (sourcePath == null) {
            SwingUtilities.invokeLater(() -> callback.onLoaded(null));
            return;
        }
        scaledTask(sourcePath, modFolderName, targetSize)
                .thenAccept(image -> SwingUtilities.invokeLater(() -> callback.onLoaded(image)));
    }

    public static void loadFramesAsync(String sourcePath, String modFolderName, int maxSize,
                                       FramesCallback callback) {
        if (sourcePath == null) {
            SwingUtilities.invokeLater(() -> callback.onLoaded(null));
            return;
        }
        frameTask(sourcePath, modFolderName, maxSize)
                .thenAccept(animation -> SwingUtilities.invokeLater(() -> callback.onLoaded(animation)));
    }

    public static BufferedImage getScaled(String sourcePath, String modFolderName, int targetSize) {
        if (sourcePath == null) {
            return null;
        }
        try {
            return scaledTask(sourcePath, modFolderName, targetSize).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            return null;
        }
    }

    private static CompletableFuture<BufferedImage> scaledTask(String sourcePath, String modFolderName,
                                                               int targetSize) {
        String key = modFolderName + "_" + sourceName(sourcePath) + "_" + targetSize;
        BufferedImage cached = SCALED_CACHE.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<BufferedImage> created = new CompletableFuture<>();
        CompletableFuture<BufferedImage> running = SCALED_TASKS.putIfAbsent(key, created);
        if (running != null) {
            return running;
        }

        EXECUTOR.execute(() -> {
            try {
                BufferedImage image = loadScaled(sourcePath, key, targetSize);
                if (image != null) {
                    remember(key, image);
                }
                created.complete(image);
            } catch (Throwable error) {
                created.complete(null);
            } finally {
                SCALED_TASKS.remove(key, created);
            }
        });
        return created;
    }

    private static String sourceName(String sourcePath) {
        return Paths.get(sourcePath).getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static CompletableFuture<Animation> frameTask(String sourcePath, String modFolderName, int maxSize) {
        String key = modFolderName + "_" + sourceName(sourcePath) + "_frames_" + maxSize;
        Animation cached = STILL_CACHE.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        if (key.equals(animatedKey) && animatedFrames != null) {
            return CompletableFuture.completedFuture(animatedFrames);
        }

        CompletableFuture<Animation> created = new CompletableFuture<>();
        CompletableFuture<Animation> running = FRAME_TASKS.putIfAbsent(key, created);

        if (running == null) {
            EXECUTOR.execute(() -> {
                try {
                    Animation animation = loadFrames(sourcePath, maxSize);
                    rememberAnimation(key, animation);
                    created.complete(animation);
                } catch (Throwable error) {
                    created.complete(null);
                } finally {
                    FRAME_TASKS.remove(key, created);
                }
            });
        }
        return running != null ? running : created;
    }

    private static void rememberAnimation(String key, Animation animation) {
        if (animation == null || animation.frames().isEmpty()) {
            return;
        }
        if (animation.frames().size() > 1) {
            animatedKey = key;
            animatedFrames = animation;
            return;
        }
        if (STILL_CACHE.size() >= MAX_STILL_CACHE_ENTRIES) {
            STILL_CACHE.clear();
        }
        STILL_CACHE.put(key, animation);
    }

    private static void remember(String key, BufferedImage image) {
        if (SCALED_CACHE.size() >= MAX_SCALED_CACHE_ENTRIES) {
            SCALED_CACHE.clear();
        }
        SCALED_CACHE.put(key, image);
    }

    private static BufferedImage loadScaled(String sourcePath, String cacheKey, int targetSize) {
        Path sourceFile = Paths.get(sourcePath);
        if (!Files.isRegularFile(sourceFile)) {
            return null;
        }

        Path cachedFile = CACHE_DIR.resolve(cacheKey + ".png");
        try {
            long originalModified = Files.getLastModifiedTime(sourceFile).toMillis();
            if (Files.isRegularFile(cachedFile)
                    && Files.getLastModifiedTime(cachedFile).toMillis() == originalModified) {
                BufferedImage cached = ImageIO.read(cachedFile.toFile());
                if (cached != null) {
                    return cached;
                }
            }

            BufferedImage original = ImageIO.read(sourceFile.toFile());
            if (original == null) {
                return null;
            }

            BufferedImage scaled = scaleImage(original, targetSize);
            writeCache(cachedFile, scaled, originalModified);
            return scaled;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void writeCache(Path cachedFile, BufferedImage image, long originalModified) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(CACHE_DIR, "thumb", ".tmp");
            ImageIO.write(image, "png", tempFile.toFile());
            Files.setLastModifiedTime(tempFile, FileTime.fromMillis(originalModified));
            Files.move(tempFile, cachedFile, StandardCopyOption.REPLACE_EXISTING);
            tempFile = null;
        } catch (IOException ignored) {
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static Animation loadFrames(String sourcePath, int maxSize) {
        Path sourceFile = Paths.get(sourcePath);
        if (!Files.isRegularFile(sourceFile)) {
            return null;
        }

        try (ImageInputStream stream = ImageIO.createImageInputStream(sourceFile.toFile())) {
            if (stream == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, false, false);
                BufferedImage canvas = new BufferedImage(
                        Math.max(1, reader.getWidth(0)),
                        Math.max(1, reader.getHeight(0)),
                        BufferedImage.TYPE_INT_ARGB);
                List<BufferedImage> frames = new ArrayList<>();
                List<Integer> delays = new ArrayList<>();
                BufferedImage restoreCanvas = null;

                for (int index = 0; index < frameCount(reader); index++) {
                    BufferedImage frame = reader.read(index);
                    if (frame == null) {
                        break;
                    }
                    IIOMetadata metadata = reader.getImageMetadata(index);
                    int left = metadataInt(metadata, "ImageDescriptor", "imageLeftPosition", 0);
                    int top = metadataInt(metadata, "ImageDescriptor", "imageTopPosition", 0);
                    int disposal = metadataInt(metadata, "GraphicControlExtension", "disposalMethod", 0);
                    int delay = metadataInt(metadata, "GraphicControlExtension", "delayTime",
                            DEFAULT_FRAME_DELAY_MS / 10) * 10;

                    if (disposal == 3) {
                        restoreCanvas = copy(canvas);
                    }

                    Graphics2D graphics = canvas.createGraphics();
                    graphics.drawImage(frame, left, top, null);
                    graphics.dispose();

                    frames.add(scaleImage(canvas, maxSize));
                    delays.add(Math.max(MIN_FRAME_DELAY_MS, delay));

                    if (disposal == 2) {
                        Graphics2D clear = canvas.createGraphics();
                        clear.setComposite(AlphaComposite.Clear);
                        clear.fillRect(left, top, frame.getWidth(), frame.getHeight());
                        clear.dispose();
                    } else if (disposal == 3 && restoreCanvas != null) {
                        canvas = restoreCanvas;
                        restoreCanvas = null;
                    }
                }

                if (frames.isEmpty()) {
                    return null;
                }
                int[] delayArray = new int[delays.size()];
                for (int i = 0; i < delayArray.length; i++) {
                    delayArray[i] = delays.get(i);
                }
                return new Animation(frames, delayArray);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static int frameCount(ImageReader reader) {
        try {
            return Math.max(1, reader.getNumImages(true));
        } catch (IOException e) {
            return 1;
        }
    }

    private static int metadataInt(IIOMetadata metadata, String nodeName, String attribute, int defaultValue) {
        if (metadata == null) {
            return defaultValue;
        }
        try {
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(GIF_IMAGE_METADATA_FORMAT);
            for (int i = 0; i < root.getLength(); i++) {
                if (!nodeName.equals(root.item(i).getNodeName())) {
                    continue;
                }
                String value = ((IIOMetadataNode) root.item(i)).getAttribute(attribute);
                if (value != null && !value.isEmpty()) {
                    return Integer.parseInt(value.trim());
                }
            }
        } catch (RuntimeException ignored) {
        }
        return defaultValue;
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return copy;
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
