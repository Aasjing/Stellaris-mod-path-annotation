package com.stellaris.modmanager;

import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.*;

public class WorkshopPreview {

    private static final Logger LOG = Logger.getInstance(WorkshopPreview.class);

    private static final String API_URL =
            "https://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1/";
    private static final String USER_AGENT = "StellarisModManager/1.0";
    private static final String UNAVAILABLE_PREFIX = "none:";
    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 3;
    private static final long[] ATTEMPT_DELAYS_MS = {400, 1200};
    private static final long BATCH_GATHER_MS = 300;
    private static final long DELAYED_RETRY_MS = TimeUnit.SECONDS.toMillis(60);
    private static final long UNAVAILABLE_RETRY_MS = TimeUnit.DAYS.toMillis(30);
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private static final Pattern ITEM_ID_PATTERN =
            Pattern.compile("\"publishedfileid\"\\s*:\\s*\"(\\d+)\"");
    private static final Pattern PREVIEW_URL_PATTERN =
            Pattern.compile("\"preview_url\"\\s*:\\s*\"([^\"]*)\"");

    private static final Path PREVIEW_DIR;
    private static final Path INDEX_FILE;

    private static final Map<String, String> RESOLVED = new ConcurrentHashMap<>();
    private static final Map<String, Long> UNAVAILABLE = new ConcurrentHashMap<>();
    private static final Map<String, List<Consumer<String>>> WAITERS = new ConcurrentHashMap<>();
    private static final Set<String> QUEUED = ConcurrentHashMap.newKeySet();
    private static final Set<String> DELAYED_RETRIED = ConcurrentHashMap.newKeySet();
    private static final BlockingQueue<String> QUEUE = new LinkedBlockingQueue<>();

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final ExecutorService DOWNLOADER = newFixedPool("WorkshopPreviewDownload", 4);
    private static final ScheduledExecutorService DELAYED = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "WorkshopPreviewRetry");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile boolean networkDisabled;
    private static int consecutiveFailures;

    static {
        Path home = Paths.get(System.getProperty("user.home"), ".stellaris-mod-manager");
        PREVIEW_DIR = home.resolve("previews");
        INDEX_FILE = home.resolve("workshop_previews.properties");
        try {
            Files.createDirectories(PREVIEW_DIR);
        } catch (IOException ignored) {
        }
        loadIndex();
        Thread worker = new Thread(WorkshopPreview::work, "WorkshopPreview");
        worker.setDaemon(true);
        worker.start();
    }

    private static ExecutorService newFixedPool(String name, int size) {
        return Executors.newFixedThreadPool(size, r -> {
            Thread thread = new Thread(r, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    public static void resolveAsync(String publishedFileId, Consumer<String> callback) {
        if (publishedFileId == null || publishedFileId.isEmpty()
                || !publishedFileId.chars().allMatch(Character::isDigit)) {
            SwingUtilities.invokeLater(() -> callback.accept(null));
            return;
        }

        String cached = RESOLVED.get(publishedFileId);
        if (cached != null) {
            if (Files.isRegularFile(Paths.get(cached))) {
                SwingUtilities.invokeLater(() -> callback.accept(cached));
                return;
            }
            RESOLVED.remove(publishedFileId);
        }

        if (recentlyUnavailable(publishedFileId) || networkDisabled) {
            SwingUtilities.invokeLater(() -> callback.accept(null));
            return;
        }

        WAITERS.computeIfAbsent(publishedFileId, id -> new CopyOnWriteArrayList<>()).add(callback);
        if (QUEUED.add(publishedFileId)) {
            QUEUE.add(publishedFileId);
        }
    }

    private static boolean recentlyUnavailable(String publishedFileId) {
        Long since = UNAVAILABLE.get(publishedFileId);
        if (since == null) {
            return false;
        }
        if (System.currentTimeMillis() - since < UNAVAILABLE_RETRY_MS) {
            return true;
        }
        UNAVAILABLE.remove(publishedFileId);
        return false;
    }

    private static void work() {
        while (true) {
            try {
                String first = QUEUE.poll(1, TimeUnit.SECONDS);
                if (first == null) {
                    continue;
                }
                List<String> batch = new ArrayList<>();
                batch.add(first);
                long deadline = System.currentTimeMillis() + BATCH_GATHER_MS;
                while (batch.size() < MAX_BATCH_SIZE) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        break;
                    }
                    String next = QUEUE.poll(remaining, TimeUnit.MILLISECONDS);
                    if (next == null) {
                        break;
                    }
                    batch.add(next);
                }
                process(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable error) {
                LOG.warn("Workshop preview worker failed", error);
            }
        }
    }

    private static void process(List<String> ids) {
        Map<String, String> previewUrls = fetchPreviewUrls(ids);
        for (String id : ids) {
            QUEUED.remove(id);
        }

        if (previewUrls == null) {
            handleNetworkFailure(ids);
            return;
        }

        consecutiveFailures = 0;
        for (String id : ids) {
            String url = previewUrls.get(id);
            if (url == null || url.isEmpty()) {
                markUnavailable(id);
                deliver(id, null);
                continue;
            }
            DOWNLOADER.execute(() -> {
                String localPath = download(id, url);
                if (localPath == null) {
                    markUnavailable(id);
                    deliver(id, null);
                    return;
                }
                RESOLVED.put(id, localPath);
                saveIndex();
                deliver(id, localPath);
            });
        }
    }

    private static void handleNetworkFailure(List<String> ids) {
        boolean disabled = ++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES;
        if (disabled) {
            networkDisabled = true;
            LOG.info("Steam workshop preview lookup disabled for this session after repeated failures");
        }

        List<String> retryLater = new ArrayList<>();
        for (String id : ids) {
            if (!disabled && DELAYED_RETRIED.add(id)) {
                retryLater.add(id);
            } else {
                deliver(id, null);
            }
        }

        if (!retryLater.isEmpty()) {
            LOG.info("Steam workshop preview lookup failed, retrying " + retryLater.size()
                    + " item(s) in " + DELAYED_RETRY_MS / 1000 + "s");
            DELAYED.schedule(() -> {
                if (networkDisabled) {
                    for (String id : retryLater) {
                        deliver(id, null);
                    }
                    return;
                }
                for (String id : retryLater) {
                    if (QUEUED.add(id)) {
                        QUEUE.add(id);
                    }
                }
            }, DELAYED_RETRY_MS, TimeUnit.MILLISECONDS);
        }
    }

    private static Map<String, String> fetchPreviewUrls(List<String> ids) {
        StringBuilder body = new StringBuilder("itemcount=").append(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            body.append("&publishedfileids[").append(i).append("]=").append(ids.get(i));
        }

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                sleep(ATTEMPT_DELAYS_MS[Math.min(attempt - 1, ATTEMPT_DELAYS_MS.length - 1)]);
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("User-Agent", USER_AGENT)
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parsePreviewUrls(response.body());
                }
                LOG.info("Workshop preview query returned HTTP " + response.statusCode()
                        + ", attempt " + (attempt + 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (IOException e) {
                LOG.info("Failed to query workshop preview urls, attempt " + (attempt + 1) + ": " + e);
            }
        }
        return null;
    }

    private static Map<String, String> parsePreviewUrls(String json) {
        List<Integer> idPositions = new ArrayList<>();
        List<String> idValues = new ArrayList<>();
        Matcher idMatcher = ITEM_ID_PATTERN.matcher(json);
        while (idMatcher.find()) {
            idPositions.add(idMatcher.start());
            idValues.add(idMatcher.group(1));
        }

        Map<String, String> previewUrls = new HashMap<>();
        Matcher urlMatcher = PREVIEW_URL_PATTERN.matcher(json);
        while (urlMatcher.find()) {
            String url = urlMatcher.group(1).replace("\\/", "/");
            int owner = -1;
            for (int i = 0; i < idPositions.size() && idPositions.get(i) < urlMatcher.start(); i++) {
                owner = i;
            }
            if (owner >= 0) {
                previewUrls.putIfAbsent(idValues.get(owner), url);
            }
        }
        return previewUrls;
    }

    private static String download(String id, String url) {
        for (int attempt = 0; attempt < 2; attempt++) {
            if (attempt > 0) {
                sleep(500);
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(REQUEST_TIMEOUT)
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();
                HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) {
                    LOG.info("Workshop preview image returned HTTP " + response.statusCode() + " for " + id);
                    continue;
                }

                byte[] data = response.body();
                String extension = imageExtension(data);
                if (extension == null) {
                    LOG.info("Workshop preview for " + id + " is not a supported image");
                    return null;
                }

                Path target = PREVIEW_DIR.resolve(id + extension);
                Path tempFile = Files.createTempFile(PREVIEW_DIR, id, ".tmp");
                try {
                    Files.write(tempFile, data);
                    Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(tempFile);
                }
                removeOtherVariants(id, extension);
                return target.toString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (IOException | RuntimeException e) {
                LOG.info("Failed to download workshop preview for " + id + ": " + e);
            }
        }
        return null;
    }

    private static String imageExtension(byte[] data) {
        if (data.length < 12) {
            return null;
        }
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return ".png";
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            return ".jpg";
        }
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
            return ".gif";
        }
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') {
            return ".webp";
        }
        return null;
    }

    private static void removeOtherVariants(String id, String keepExtension) {
        for (String extension : new String[]{".png", ".jpg", ".gif"}) {
            if (extension.equals(keepExtension)) {
                continue;
            }
            try {
                Files.deleteIfExists(PREVIEW_DIR.resolve(id + extension));
            } catch (IOException ignored) {
            }
        }
    }

    private static void markUnavailable(String id) {
        UNAVAILABLE.put(id, System.currentTimeMillis());
        saveIndex();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void deliver(String id, String localPath) {
        List<Consumer<String>> callbacks = WAITERS.remove(id);
        if (callbacks == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            for (Consumer<String> callback : callbacks) {
                callback.accept(localPath);
            }
        });
    }

    private static void loadIndex() {
        if (!Files.isRegularFile(INDEX_FILE)) {
            return;
        }
        Properties properties = new Properties();
        try (var in = Files.newInputStream(INDEX_FILE)) {
            properties.load(in);
        } catch (IOException e) {
            LOG.info("Failed to read workshop preview index", e);
            return;
        }
        for (String id : properties.stringPropertyNames()) {
            String value = properties.getProperty(id);
            if (value.startsWith(UNAVAILABLE_PREFIX)) {
                try {
                    UNAVAILABLE.put(id, Long.parseLong(value.substring(UNAVAILABLE_PREFIX.length())));
                } catch (NumberFormatException ignored) {
                }
            } else {
                RESOLVED.put(id, value);
            }
        }
    }

    private static synchronized void saveIndex() {
        Properties properties = new Properties();
        for (Map.Entry<String, String> entry : RESOLVED.entrySet()) {
            properties.setProperty(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Long> entry : UNAVAILABLE.entrySet()) {
            properties.setProperty(entry.getKey(), UNAVAILABLE_PREFIX + entry.getValue());
        }
        try (var out = Files.newOutputStream(INDEX_FILE)) {
            properties.store(out, "Stellaris mod manager - workshop previews");
        } catch (IOException e) {
            LOG.info("Failed to save workshop preview index", e);
        }
    }
}
