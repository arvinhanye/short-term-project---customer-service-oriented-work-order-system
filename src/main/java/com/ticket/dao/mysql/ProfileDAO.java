package com.ticket.dao.mysql;

import com.ticket.dao.BaseDAO;
import com.ticket.exception.DBException;
import com.ticket.model.Profile;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

public class ProfileDAO extends BaseDAO {
    public Profile findByUserId(Long userId) {
        return queryOne("SELECT * FROM profiles WHERE user_id = ?",
            statement -> statement.setLong(1, userId), this::mapProfile);
    }

    public long insert(Connection connection, Profile profile) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO profiles (user_id, real_name, id_card, address, notes) VALUES (?, ?, ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setLong(1, profile.getUserId());
            statement.setString(2, profile.getRealName());
            statement.setString(3, profile.getIdCard());
            statement.setString(4, profile.getAddress());
            statement.setString(5, profile.getNotes());
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new DBException("Failed to get generated profile_id");
        }
    }

    public void upsert(Profile profile) {
        Profile existing = findByUserId(profile.getUserId());
        if (existing == null) {
            executeTransactionCallback(connection -> insert(connection, profile));
            return;
        }
        update("UPDATE profiles SET real_name = ?, id_card = ?, address = ?, notes = ? WHERE user_id = ?",
            statement -> {
                statement.setString(1, profile.getRealName());
                statement.setString(2, profile.getIdCard());
                statement.setString(3, profile.getAddress());
                statement.setString(4, profile.getNotes());
                statement.setLong(5, profile.getUserId());
            });
    }

    private Profile mapProfile(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        Profile profile = new Profile();
        profile.setProfileId(resultSet.getLong("profile_id"));
        profile.setUserId(resultSet.getLong("user_id"));
        profile.setRealName(resultSet.getString("real_name"));
        profile.setIdCard(resultSet.getString("id_card"));
        profile.setAddress(resultSet.getString("address"));
        profile.setNotes(resultSet.getString("notes"));
        return profile;
    }
}
