package com.vehiclerental.dao.impl;

import com.vehiclerental.dao.AbstractJdbcDao;
import com.vehiclerental.dao.BranchDao;
import com.vehiclerental.dao.impl.rowmapper.BranchRowMapper;
import com.vehiclerental.model.Branch;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Time;
import java.util.List;
import java.util.Optional;

@Repository
public class BranchDaoImpl extends AbstractJdbcDao<Branch, Integer> implements BranchDao {

    private final BranchRowMapper mapper = new BranchRowMapper();

    public BranchDaoImpl(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Branch save(Branch b) {
        String sql = "INSERT INTO branches (name, street, city, district, contact_number, " +
                     "open_time, close_time, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        Time open = b.getOpenTime() == null ? null : Time.valueOf(b.getOpenTime());
        Time close = b.getCloseTime() == null ? null : Time.valueOf(b.getCloseTime());

        int id = executeInsertReturnId(sql,
            b.getName(), b.getStreet(), b.getCity(), b.getDistrict(),
            b.getContactNumber(), open, close,
            b.getStatus() == null ? "ACTIVE" : b.getStatus());

        b.setBranchId(id);
        return b;
    }

    @Override
    public Optional<Branch> findById(int branchId) {
        String sql = "SELECT * FROM branches WHERE branch_id = ?";
        return queryOne(sql, mapper, branchId);
    }

    @Override
    public List<Branch> findAll() {
        String sql = "SELECT * FROM branches ORDER BY branch_id";
        return queryList(sql, mapper);
    }

    @Override
    public void update(Branch b) {
        String sql = "UPDATE branches SET name=?, street=?, city=?, district=?, " +
                     "contact_number=?, open_time=?, close_time=?, status=? WHERE branch_id=?";

        Time open = b.getOpenTime() == null ? null : Time.valueOf(b.getOpenTime());
        Time close = b.getCloseTime() == null ? null : Time.valueOf(b.getCloseTime());

        executeUpdate(sql,
            b.getName(), b.getStreet(), b.getCity(), b.getDistrict(),
            b.getContactNumber(), open, close, b.getStatus(),
            b.getBranchId());
    }

    @Override
    public void updateStatus(int branchId, String status) {
        String sql = "UPDATE branches SET status = ? WHERE branch_id = ?";
        executeUpdate(sql, status, branchId);
    }

    @Override
    public void delete(int branchId) {
        String sql = "DELETE FROM branches WHERE branch_id = ?";
        executeUpdate(sql, branchId);
    }
}
