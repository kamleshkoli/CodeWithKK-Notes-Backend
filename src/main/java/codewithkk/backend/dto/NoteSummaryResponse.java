package codewithkk.backend.dto;

import codewithkk.backend.entity.Note;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public shape of a note. Deliberately omits pdfUrl - the storage URL is never
 * sent to the browser. hasPdf is the only hint the storefront needs, and the
 * file itself is served by the authenticated /download endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteSummaryResponse {
    private String id;
    private String title;
    private String description;
    private String thumbnailUrl;
    private double price;
    private boolean active;
    private boolean hasPdf;

    public static NoteSummaryResponse from(Note note) {
        if (note == null) {
            return null;
        }
        return new NoteSummaryResponse(
                note.getId(),
                note.getTitle(),
                note.getDescription(),
                note.getThumbnailUrl(),
                note.getPrice(),
                note.isActive(),
                note.getPdfUrl() != null && !note.getPdfUrl().isBlank()
        );
    }
}
