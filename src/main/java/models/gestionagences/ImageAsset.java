package models.gestionagences;

import java.time.LocalDateTime;

/**
 * Image binaire en base (table {@code image_asset}), meme role que {@code ImageAsset} cote Symfony / integration.
 */
public class ImageAsset {

    private Long id;
    private String mimeType;
    private byte[] data;
    private LocalDateTime createdAt;

    public ImageAsset() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
