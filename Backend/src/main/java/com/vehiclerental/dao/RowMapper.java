package com.vehiclerental.dao;

import java.sql.ResultSet;
import java.sql.SQLException;

// Turns one ResultSet row into one object of type T.
// Every entity gets its own RowMapper implementation.
public interface RowMapper<T> {
    T map(ResultSet rs) throws SQLException;
}
