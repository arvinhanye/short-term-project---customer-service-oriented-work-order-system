package com.ticket.integration;

import com.ticket.service.CategoryService;
import com.ticket.service.CrossDatabaseQueryService;
import com.ticket.service.CrossDatabaseQueryService.AssignmentScope;
import com.ticket.service.UserService;
import com.ticket.util.MongoDBUtil;
import com.ticket.util.MySQLDBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** 覆盖 ADMIN 工单页的 MySQL + MongoDB 实际加载链路。 */
@EnabledIfSystemProperty(named = "ticket.integration", matches = "true")
class RoleAccessIntegrationTest {
    @AfterAll
    static void closeConnections() {
        MongoDBUtil.close();
        MySQLDBUtil.close();
    }

    @Test
    void adminCanLoadTicketListAndReadableCategories() {
        var admin = new UserService().login("admin04", "ServiceAgent#742");

        Assertions.assertEquals("ADMIN", admin.getRole());
        Assertions.assertDoesNotThrow(() -> new CategoryService().listAvailableCategories(admin));
        Assertions.assertDoesNotThrow(() -> new CrossDatabaseQueryService().pageAdminTickets(
            admin, null, "", AssignmentScope.ALL, null, 1, 50));
    }
}
