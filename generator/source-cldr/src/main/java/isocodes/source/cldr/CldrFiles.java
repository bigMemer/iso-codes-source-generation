package isocodes.source.cldr;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Function;

/** Downloads files of one CLDR release from GitHub, caching them on disk. */
public final class CldrFiles implements Function<String, String> {

    private static final String RAW_BASE = "https://raw.githubusercontent.com/unicode-org/cldr/";

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final String tag;
    private final Path cacheDir;

    /**
     * @param version  a CLDR release, e.g. {@code 48.2} (tag {@code release-48-2}) or {@code 48} ({@code release-48})
     * @param cacheDir where downloaded files are kept
     */
    public CldrFiles(String version, Path cacheDir) {
        this.tag = "release-" + version.replace('.', '-');
        this.cacheDir = cacheDir;
    }

    /** Returns the file at {@code path} within the release, e.g. {@code common/validity/region.xml}. */
    @Override
    public String apply(String path) {
        Path cached = cacheDir.resolve(path);
        try {
            if (Files.exists(cached)) {
                return Files.readString(cached);
            }
            String url = RAW_BASE + tag + "/" + path;
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("GET " + url + " returned HTTP " + response.statusCode());
            }
            Files.createDirectories(cached.getParent());
            Files.writeString(cached, response.body());
            return response.body();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("Interrupted fetching " + path, e));
        }
    }
}
