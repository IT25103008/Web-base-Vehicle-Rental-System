package com.vehiclerental.dao;

import com.vehiclerental.exception.DataAccessException;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public abstract class AbstractJdbcDao<T, ID> {

    protected final DataSource dataSource;

    protected AbstractJdbcDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ---------- SELECT many rows ----------
    protected List<T> queryList(String sql, RowMapper<T> mapper, Object... params) {
        List<T> results = new ArrayList<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            bindParams(ps, params);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
            }

        } catch (SQLException e) {
            throw new DataAccessException("SELECT failed: " + sql, e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return results;
    }

    // ---------- SELECT rows of any other shape (counts, projections) ----------
    protected <R> List<R> queryRows(String sql, RowMapper<R> mapper, Object... params) {
        List<R> results = new ArrayList<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            bindParams(ps, params);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
            }

        } catch (SQLException e) {
            throw new DataAccessException("SELECT failed: " + sql, e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return results;
    }

    protected long queryCount(String sql, Object... params) {
        List<Long> rows = queryRows(sql, rs -> rs.getLong(1), params);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    // ---------- SELECT one row (or none) ----------
    protected Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> results = queryList(sql, mapper, params);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.get(0));
    }

    // ---------- INSERT / UPDATE / DELETE ----------
    protected int executeUpdate(String sql, Object... params) {
        Connection conn = DataSourceUtils.getConnection(dataSource);

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            bindParams(ps, params);
            return ps.executeUpdate();

        } catch (SQLException e) {
            throw new DataAccessException("UPDATE failed: " + sql, e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    // ---------- INSERT that returns the auto-generated primary key ----------
    protected int executeInsertReturnId(String sql, Object... params) {
        Connection conn = DataSourceUtils.getConnection(dataSource);

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            bindParams(ps, params);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new DataAccessException("INSERT returned no generated key: " + sql);
            }

        } catch (SQLException e) {
            throw new DataAccessException("INSERT failed: " + sql, e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    // ---------- helper: bind ?-parameters by index ----------
    private void bindParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
