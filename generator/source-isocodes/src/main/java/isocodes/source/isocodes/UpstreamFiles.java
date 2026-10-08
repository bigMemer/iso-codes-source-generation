package isocodes.source.isocodes;

import java.util.Optional;

/** Access to the {@code data/} directory of one iso-codes release. */
@FunctionalInterface
public interface UpstreamFiles {

    /** Returns the file's contents, or empty if the release has no such file. */
    Optional<String> fetch(String fileName);
}
