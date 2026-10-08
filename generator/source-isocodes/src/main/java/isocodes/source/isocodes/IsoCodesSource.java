package isocodes.source.isocodes;

import isocodes.model.SourceData;
import java.util.List;

/** Reads one iso-codes release into {@link SourceData}. */
public final class IsoCodesSource {

    private IsoCodesSource() {}

    /**
     * @param version the iso-codes release, e.g. {@code 4.20.1}
     * @param files   the release's data files
     * @throws UnknownShapeException if a file doesn't fit any known shape
     */
    public static SourceData read(String version, UpstreamFiles files) {
        return new SourceData(
                "Debian iso-codes",
                version,
                "LGPL-2.1-or-later",
                parse(IsoCodesShapes.COUNTRIES, files),
                parse(IsoCodesShapes.SUBDIVISIONS, files));
    }

    private static <R> List<R> parse(StandardFile<R> file, UpstreamFiles files) {
        String json = files.fetch(file.fileName())
                .orElseThrow(() -> new IllegalStateException("Release has no data/" + file.fileName()));
        return file.parse(json);
    }
}
