package dev.sivalabs.helpdesk.tickets;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The rules for images attached to a ticket. */
final class TicketImages {
    static final int MAX_IMAGES = 3;
    static final long MAX_BYTES = 5L * 1024 * 1024;

    private TicketImages() {}

    /**
     * Checks the images and returns the content type of each, detected from its bytes rather than trusted from the
     * browser, so only real PNG, JPEG, GIF and WebP images are stored and served.
     */
    static List<String> validate(List<ImageUpload> images) {
        if (images.size() > MAX_IMAGES) {
            throw new InvalidAttachmentException("Attach at most " + MAX_IMAGES + " images");
        }
        return images.stream()
                .map(image -> {
                    if (image.data().length > MAX_BYTES) {
                        throw new InvalidAttachmentException(image.fileName() + " is larger than 5 MB");
                    }
                    return detectType(image.data())
                            .orElseThrow(() -> new InvalidAttachmentException(
                                    image.fileName() + " is not a PNG, JPEG, GIF or WebP image"));
                })
                .toList();
    }

    static Optional<String> detectType(byte[] d) {
        if (startsWith(d, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of("image/png");
        }
        if (startsWith(d, 0xFF, 0xD8, 0xFF)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(d, 'G', 'I', 'F', '8')) {
            return Optional.of("image/gif");
        }
        if (d.length >= 12
                && startsWith(d, 'R', 'I', 'F', 'F')
                && Arrays.equals(Arrays.copyOfRange(d, 8, 12), new byte[] {'W', 'E', 'B', 'P'})) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** A file name safe to show and to put in a header: no path, no control characters, at most 200 characters. */
    static String cleanFileName(String name) {
        if (name == null || name.isBlank()) {
            return "image";
        }
        var base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}\"]", "")
                .trim();
        if (base.isEmpty()) {
            return "image";
        }
        return base.length() > 200 ? base.substring(0, 200) : base;
    }
}
