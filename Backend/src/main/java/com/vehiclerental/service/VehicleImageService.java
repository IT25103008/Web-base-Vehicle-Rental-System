package com.vehiclerental.service;

import com.vehiclerental.model.Vehicle;
import com.vehiclerental.model.VehicleImage;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * Photos for a vehicle. The bytes live in vehicles.image_data so they travel
 * with a database dump, and image_url is pointed at the endpoint that serves
 * them back; the web app already prefers a real photo over the drawn
 * illustration whenever one is set.
 */
public interface VehicleImageService {

    /** Stores the upload and points the vehicle at it, replacing any previous photo. */
    Vehicle store(int vehicleId, MultipartFile file, int actorUserId);

    /** Clears the photo, so the drawn illustration comes back. */
    Vehicle remove(int vehicleId, int actorUserId);

    /** The stored bytes, for serving the image itself. */
    Optional<VehicleImage> find(int vehicleId);

    // ---- more than one photo (E5) ----
    record GalleryPhoto(int imageId, String url) { }

    List<GalleryPhoto> gallery(int vehicleId);
    List<GalleryPhoto> addToGallery(int vehicleId, MultipartFile file, int actorUserId);
    List<GalleryPhoto> removeFromGallery(int vehicleId, int imageId, int actorUserId);
    Optional<VehicleImage> findGalleryImage(int vehicleId, int imageId);

    /**
     * The same picture scaled down to (about) `width` pixels wide, so a card
     * does not download a 4 MB original. Falls back to the original for a
     * format that cannot be resized here (WebP) or when it is already small.
     */
    VehicleImage resized(VehicleImage original, Integer width);
}
