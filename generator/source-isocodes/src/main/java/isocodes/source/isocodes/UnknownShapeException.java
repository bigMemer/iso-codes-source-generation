package isocodes.source.isocodes;

/** Thrown when an iso-codes data file doesn't fit any known shape, e.g. because upstream added a field. */
public final class UnknownShapeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    UnknownShapeException(String message) {
        super(message);
    }
}
