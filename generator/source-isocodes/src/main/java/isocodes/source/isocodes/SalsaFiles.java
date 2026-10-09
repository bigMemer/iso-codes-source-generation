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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Downloads data files for one iso-codes release from Debian's GitLab (salsa.debian.org), caching them on disk. */
public final class SalsaFiles implements UpstreamFiles {

    private static final String RAW_BASE = "https://salsa.debian.org/iso-codes-team/iso-codes/-/raw/";
    private static final String TAGS_API =
            "https://salsa.debian.org/api/v4/projects/iso-codes-team%2Fiso-codes/repository/tags?per_page=100&page=";

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

    /**
     * Lists iso-codes releases, oldest first. Only the version strings are returned, normalised from both tag styles
     * ({@code iso-codes-3.67}, {@code v4.20.1}).
     */
    public static List<String> releases() {
        SalsaFiles client = new SalsaFiles("", Path.of("."));
        List<String> versions = new ArrayList<>();
        try {
            for (int page = 1; ; page++) {
                String body = client.get(TAGS_API + page).orElse("[]");
                List<String> names = new ArrayList<>();
                Matcher m = Pattern.compile("\"name\":\"(?:v|iso-codes-)(\\d+(?:\\.\\d+)+)\"").matcher(body);
                while (m.find()) {
                    names.add(m.group(1));
                }
                if (names.isEmpty()) {
                    break;
                }
                versions.addAll(names);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return versions.stream().distinct().sorted(Comparator.comparing(SalsaFiles::versionKey)).toList();
    }

    /** Sort key for dotted versions, comparing numerically. */
    public static String versionKey(String version) {
        StringBuilder key = new StringBuilder();
        for (String part : version.split("\\.")) {
            key.append(String.format("%06d.", Integer.parseInt(part)));
        }
        return key.toString();
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
