package com.ticket.dao.mysql;

import com.ticket.model.TicketHistory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TicketHistoryDAOTest {
    private JdbcDataSource dataSource;
    private TicketHistoryDAO historyDAO;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ticket_history_dao_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection()) {
            executeSql(connection, "DROP TABLE IF EXISTS ticket_history");
            executeSql(connection, """
                CREATE TABLE ticket_history (
                    history_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    event_id VARCHAR(36) NOT NULL UNIQUE,
                    item_id BIGINT NOT NULL,
                    order_id BIGINT NULL,
                    event_seq BIGINT NOT NULL,
                    event_type VARCHAR(50) NOT NULL,
                    visibility VARCHAR(20) NOT NULL,
                    actor_user_id BIGINT NULL,
                    actor_username VARCHAR(50) NULL,
                    actor_role VARCHAR(20) NULL,
                    target_user_id BIGINT NULL,
                    from_status TINYINT NULL,
                    to_status TINYINT NULL,
                    from_admin_id BIGINT NULL,
                    to_admin_id BIGINT NULL,
                    reason VARCHAR(500) NULL,
                    source_type VARCHAR(30) NULL,
                    source_id VARCHAR(64) NULL,
                    event_payload VARCHAR(2000) NULL,
                    occurred_at TIMESTAMP NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (item_id, event_seq)
                )
                """);
        }
        historyDAO = new TicketHistoryDAO(dataSource);
    }

    @Test
    void shouldKeepSequenceAndHideStaffEventsFromPublicQuery() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            historyDAO.insert(connection, event("event-public", 1, "TICKET_CREATED", "PUBLIC"));
            historyDAO.insert(connection, event("event-staff", 2, "TRANSFER_REQUESTED", "STAFF_ONLY"));
        }

        var staffView = historyDAO.findByItemId(3001L, true, 20);
        Assertions.assertEquals(2, staffView.size());
        Assertions.assertEquals(1, staffView.get(0).getEventSeq());
        Assertions.assertEquals(2, staffView.get(1).getEventSeq());

        var publicView = historyDAO.findByItemId(3001L, false, 20);
        Assertions.assertEquals(1, publicView.size());
        Assertions.assertEquals("TICKET_CREATED", publicView.get(0).getEventType());
    }

    @Test
    void shouldRejectDuplicateSequenceForSameTicket() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            historyDAO.insert(connection, event("event-first", 1, "TICKET_CREATED", "PUBLIC"));
            Assertions.assertThrows(Exception.class,
                () -> historyDAO.insert(connection, event("event-second", 1, "STATUS_CHANGED", "PUBLIC")));
        }
    }

    private TicketHistory event(String eventId, long sequence, String type, String visibility) {
        TicketHistory history = new TicketHistory();
        history.setEventId(eventId);
        history.setItemId(3001L);
        history.setOrderId(4001L);
        history.setEventSeq(sequence);
        history.setEventType(type);
        history.setVisibility(visibility);
        history.setActorUserId(9001L);
        history.setActorUsername("admin01");
        history.setActorRole("ADMIN");
        history.setEventPayload("{}");
        history.setOccurredAt(LocalDateTime.of(2026, 7, 15, 14, 0).plusMinutes(sequence));
        return history;
    }

    private void executeSql(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }
}
