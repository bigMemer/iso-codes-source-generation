package isocodes.source.isocodes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Downloads data files for one iso-codes release from Debian's GitLab (salsa.debian.org), caching them on disk. */
public final class SalsaFiles implements UpstreamFiles {

    private static final String RAW_BASE = "https://salsa.debian.org/iso-codes-team/iso-codes/-/raw/";

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final String version;
    private final Path cacheDir;
    private String tag;

    public SalsaFiles(String version, Path cacheDir) {
        this.version = version;
        this.cacheDir = cacheDir;
    }

    /** Upstream renamed its tags from {@code iso-codes-X.Y} to {@code vX.Y.Z} at 4.8.0. */
    private List<String> candidateTags() {
        return List.of("v" + version, "iso-codes-" + version);
    }

    @Override
    public Optional<String> fetch(String fileName) {
        Path cached = cacheDir.resolve(fileName);
        try {
            if (Files.exists(cached)) {
                return Optional.of(Files.readString(cached));
            }
            for (String candidate : tag != null ? List.of(tag) : candidateTags()) {
                Optional<String> body = get(RAW_BASE + candidate + "/data/" + fileName);
                if (body.isPresent()) {
                    tag = candidate;
                    Files.createDirectories(cacheDir);
                    Files.writeString(cached, body.get());
                    return body;
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<String> get(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() != 200) {
                throw new IOException("GET " + url + " returned HTTP " + response.statusCode());
            }
            return Optional.of(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + url, e);
        }
    }
}
