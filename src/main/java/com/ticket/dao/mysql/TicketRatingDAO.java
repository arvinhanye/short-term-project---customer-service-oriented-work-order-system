package com.ticket.dao.mysql;

import com.ticket.dao.BaseDAO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

public class TicketRatingDAO extends BaseDAO {
    public TicketRatingDAO() { super(); }
    TicketRatingDAO(DataSource dataSource) { super(dataSource); }

    public boolean reserve(Connection connection, Long itemId, Long userId, String eventId, int rating)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ticket_ratings (item_id, user_id, event_id, rating) VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, itemId);
            statement.setLong(2, userId);
            statement.setString(3, eventId);
            statement.setInt(4, rating);
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            if ((ex.getSQLState() != null && ex.getSQLState().startsWith("23")) || ex.getErrorCode() == 1062) return false;
            throw ex;
        }
    }
}
