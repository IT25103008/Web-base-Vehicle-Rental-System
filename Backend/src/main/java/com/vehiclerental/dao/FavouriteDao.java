package com.vehiclerental.dao;

import java.util.List;

/** Cars a customer has saved (the heart), kept on the account so they follow them across devices. */
public interface FavouriteDao {
    List<Integer> findVehicleIds(int userId);
    /** Adds the car if it is not already saved; returns true when it was new. */
    boolean add(int userId, int vehicleId);
    void remove(int userId, int vehicleId);
}
