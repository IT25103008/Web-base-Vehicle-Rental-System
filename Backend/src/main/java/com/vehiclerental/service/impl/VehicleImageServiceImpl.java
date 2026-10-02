package com.vehiclerental.service.impl;

import com.vehiclerental.dao.VehicleDao;
import com.vehiclerental.dao.VehicleImageDao;
import com.vehiclerental.exception.ResourceNotFoundException;
import com.vehiclerental.model.Vehicle;
import com.vehiclerental.model.VehicleImage;
import com.vehiclerental.service.AuditService;
import com.vehiclerental.service.VehicleImageService;
import com.vehiclerental.util.AppClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.zip.CRC32;

@Service
public class VehicleImageServiceImpl implements VehicleImageService {

    private static final String ENTITY = "VEHICLE";

    /** Matches spring.servlet.multipart.max-file-size; checked here too so the
     *  message is ours rather than a raw container error. */
    private static final long MAX_BYTES = 4L * 1024 * 1024;

    /** How many extra photos one vehicle may carry. */
    private static final int MAX_GALLERY = 8;
    /** Widths a thumbnail may be asked for; anything else snaps to the next one up. */
    private static final int[] WIDTHS = { 160, 320, 480, 640, 960, 1280 };

    private final VehicleDao vehicleDao;
    private final VehicleImageDao imageDao;
    private final AuditService auditService;

    /** A small in-memory cache of resized pictures, least recently used out first. */
    private final Map<String, VehicleImage> thumbnails = Collections.synchronizedMap(
        new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, VehicleImage> eldest) {
                return size() > 150;
            }
        });

    public VehicleImageServiceImpl(VehicleDao vehicleDao, VehicleImageDao imageDao, AuditService auditService) {
        this.vehicleDao = vehicleDao;
        this.imageDao = imageDao;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public Vehicle store(int vehicleId, MultipartFile file, int actorUserId) {
        load(vehicleId);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a photo to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("The photo must be 4 MB or smaller");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded file");
        }

        // The browser's Content-Type header is supplied by the client, so the
        // format is decided from the bytes instead. Only raster formats are
        // accepted: an SVG served from our own origin can run scripts, which
        // would turn an upload into stored XSS against whoever opens the fleet.
        String type = sniffContentType(bytes);
        if (type == null) {
            throw new IllegalArgumentException("Only JPEG, PNG and WebP photos are accepted");
        }

        // The URL is stable for a vehicle, so without a changing marker a
        // browser would keep showing the photo it cached before the replacement.
        String url = "/api/vehicles/" + vehicleId + "/image?v=" + AppClock.now().toEpochSecond(java.time.ZoneOffset.UTC);

        vehicleDao.updateImage(vehicleId, bytes, type, url);
        auditService.record(ENTITY, vehicleId, "UPDATE", actorUserId, "Photo uploaded");
        return load(vehicleId);
    }

    @Override
    @Transactional
    public Vehicle remove(int vehicleId, int actorUserId) {
        load(vehicleId);
        vehicleDao.updateImage(vehicleId, null, null, null);
        auditService.record(ENTITY, vehicleId, "UPDATE", actorUserId, "Photo removed");
        return load(vehicleId);
    }

    @Override
    public Optional<VehicleImage> find(int vehicleId) {
        return vehicleDao.findImage(vehicleId);
    }

    // ============================================================
    // Gallery
    // ============================================================
    @Override
    public List<GalleryPhoto> gallery(int vehicleId) {
        return imageDao.findByVehicle(vehicleId).stream()
            .map(e -> new GalleryPhoto(e.imageId(), "/api/vehicles/" + vehicleId + "/images/" + e.imageId()))
            .toList();
    }

    @Override
    @Transactional
    public List<GalleryPhoto> addToGallery(int vehicleId, MultipartFile file, int actorUserId) {
        load(vehicleId);
        if (imageDao.countByVehicle(vehicleId) >= MAX_GALLERY) {
            throw new IllegalArgumentException("A vehicle can have up to " + MAX_GALLERY + " extra photos. Remove one first.");
        }
        byte[] bytes = readChecked(file);
        String type = sniffContentType(bytes);
        if (type == null) {
            throw new IllegalArgumentException("Only JPEG, PNG and WebP photos are accepted");
        }
        int id = imageDao.add(vehicleId, bytes, type);
        auditService.record(ENTITY, vehicleId, "UPDATE", actorUserId, "Gallery photo #" + id + " added");
        return gallery(vehicleId);
    }

    @Override
    @Transactional
    public List<GalleryPhoto> removeFromGallery(int vehicleId, int imageId, int actorUserId) {
        load(vehicleId);
        imageDao.delete(vehicleId, imageId);
        auditService.record(ENTITY, vehicleId, "UPDATE", actorUserId, "Gallery photo #" + imageId + " removed");
        return gallery(vehicleId);
    }

    @Override
    public Optional<VehicleImage> findGalleryImage(int vehicleId, int imageId) {
        return imageDao.findData(vehicleId, imageId);
    }

    // ============================================================
    // Thumbnails
    // ============================================================
    @Override
    public VehicleImage resized(VehicleImage original, Integer width) {
        if (original == null || width == null || width <= 0 || original.getData() == null) {
            return original;
        }
        String type = original.getContentType();
        if (!"image/jpeg".equals(type) && !"image/png".equals(type)) {
            return original;                  // ImageIO cannot write WebP; serve as stored
        }
        int target = WIDTHS[WIDTHS.length - 1];
        for (int w : WIDTHS) {
            if (w >= width) { target = w; break; }
        }
        CRC32 crc = new CRC32();
        crc.update(original.getData());
        String key = crc.getValue() + ":" + original.getData().length + ":" + target;
        VehicleImage cached = thumbnails.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(original.getData()));
            if (src == null || src.getWidth() <= target) {
                return original;
            }
            int h = Math.max(1, Math.round(src.getHeight() * (target / (float) src.getWidth())));
            boolean png = "image/png".equals(type);
            BufferedImage out = new BufferedImage(target, h, png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, target, h, null);
            g.dispose();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            if (png) {
                ImageIO.write(out, "png", buf);
            } else {
                ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.84f);
                try (ImageOutputStream ios = ImageIO.createImageOutputStream(buf)) {
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(out, null, null), param);
                } finally {
                    writer.dispose();
                }
            }
            VehicleImage small = new VehicleImage(buf.toByteArray(), type);
            thumbnails.put(key, small);
            return small;
        } catch (IOException | RuntimeException e) {
            return original;                  // a picture we cannot decode is still served whole
        }
    }

    private byte[] readChecked(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a photo to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("The photo must be 4 MB or smaller");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded file");
        }
    }

    private Vehicle load(int vehicleId) {
        return vehicleDao.findById(vehicleId).orElseThrow(
            () -> new ResourceNotFoundException("Vehicle " + vehicleId + " not found"));
    }

    /** The media type from the file signature, or null when we do not serve it. */
    private static String sniffContentType(byte[] b) {
        if (b.length >= 3
            && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8
            && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
            && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A
            && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return "image/png";
        }
        if (b.length >= 12
            && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
            && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
