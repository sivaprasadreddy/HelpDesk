package dev.sivalabs.helpdesk.tickets;

/** An attachment as listed on the ticket details page, without its bytes. */
public record AttachmentView(Long id, String fileName, String contentType, long sizeBytes) {

    /** Size for people, such as "820 KB" or "2.4 MB". */
    public String displaySize() {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        }
        if (sizeBytes < 1024 * 1024) {
            return Math.round(sizeBytes / 1024.0) + " KB";
        }
        return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
    }
}
