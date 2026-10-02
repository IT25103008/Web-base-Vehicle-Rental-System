package com.vehiclerental.model;

/**
 * The stored bytes of a vehicle photo.
 *
 * Deliberately separate from {@link Vehicle}: the photo is a LONGBLOB, and if
 * it hung off the Vehicle model every fleet listing would drag every photo out
 * of the database. It is only ever loaded when someone actually asks for the
 * image.
 */
public class VehicleImage {

    private final byte[] data;
    private final String contentType;

    public VehicleImage(byte[] data, String contentType) {
        this.data = data;
        this.contentType = contentType;
    }

    public byte[] getData() {
        return data;
    }

    public String getContentType() {
        return contentType;
    }
}
