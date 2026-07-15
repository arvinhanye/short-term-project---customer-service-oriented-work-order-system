package com.ticket.service;

import com.ticket.dto.CrossTicketDTO;
import com.ticket.model.ItemDetail;
import com.ticket.model.Item;
import com.ticket.model.Order;
import java.time.LocalDateTime;
import com.ticket.service.CrossDatabaseQueryService.AssignmentScope;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CrossDatabaseQueryServiceTest {
    @Test
    void shouldFilterByAssignmentBeforePagingAndReturnFilteredTotal() {
        List<CrossTicketDTO> tickets = List.of(
            ticketAssignedTo("10001"),
            ticketAssignedTo(null),
            ticketAssignedTo("10002"),
            ticketAssignedTo("10001"),
            new CrossTicketDTO()
        );

        var firstPage = CrossDatabaseQueryService.filterAndPageByAssignment(
            tickets, AssignmentScope.ASSIGNED_TO, "10001", 1, 1);
        var secondPage = CrossDatabaseQueryService.filterAndPageByAssignment(
            tickets, AssignmentScope.ASSIGNED_TO, "10001", 2, 1);

        Assertions.assertEquals(2, firstPage.getTotal());
        Assertions.assertEquals(1, firstPage.getRecords().size());
        Assertions.assertEquals(2, secondPage.getTotal());
        Assertions.assertEquals(1, secondPage.getRecords().size());
    }

    @Test
    void shouldTreatBlankOrMissingAssignmentAsUnassigned() {
        List<CrossTicketDTO> tickets = List.of(
            ticketAssignedTo("10001"),
            ticketAssignedTo(null),
            ticketAssignedTo(" "),
            new CrossTicketDTO()
        );

        var result = CrossDatabaseQueryService.filterAndPageByAssignment(
            tickets, AssignmentScope.UNASSIGNED, null, 1, 50);

        Assertions.assertEquals(3, result.getTotal());
        Assertions.assertEquals(3, result.getRecords().size());
    }

    @Test
    void shouldFilterPendingTransfersForTargetAdmin() {
        CrossTicketDTO first = ticketAssignedTo("10001");
        first.getItemDetail().getMetadata().setTransferTargetAdminId("10002");
        CrossTicketDTO second = ticketAssignedTo(null);
        second.getItemDetail().getMetadata().setTransferTargetAdminId("10003");
        CrossTicketDTO mysqlOnly = new CrossTicketDTO();
        Order mysqlWorkflow = new Order();
        mysqlWorkflow.setTransferTargetAdminId(10002L);
        mysqlOnly.setOrder(mysqlWorkflow);

        var result = CrossDatabaseQueryService.filterAndPageByAssignment(
            List.of(first, second, mysqlOnly), AssignmentScope.PENDING_TRANSFER_TO, "10002", 1, 50);

        Assertions.assertEquals(2, result.getTotal());
        Assertions.assertSame(first, result.getRecords().get(0));
    }

    @Test
    void shouldCountOnlyStatusesAssignedToCurrentAdmin() {
        CrossTicketDTO awaitingConfirmation = ticketAssignedTo("10002", 1);
        awaitingConfirmation.getItemDetail().getMetadata().setTransferTargetAdminId("10001");
        List<CrossTicketDTO> tickets = List.of(
            ticketAssignedTo("10001", 0),
            ticketAssignedTo("10001", 0),
            ticketAssignedTo("10001", 1),
            ticketAssignedTo("10001", 2),
            ticketAssignedTo("10002", 0),
            ticketAssignedTo(null, 1),
            awaitingConfirmation
        );

        var counts = CrossDatabaseQueryService.countStatusesByAssignment(tickets, "10001");

        Assertions.assertEquals(2L, counts.get(0));
        Assertions.assertEquals(1L, counts.get(1));
        Assertions.assertEquals(1L, counts.get(2));
        Assertions.assertEquals(1L, counts.get(CrossDatabaseQueryService.STATUS_PENDING_CONFIRMATION));
    }

    @Test
    void shouldPutIncomingTransferAtFrontOfRiskList() {
        CrossTicketDTO awaitingConfirmation = riskTicket(
            2006L, "10002", 1, "LOW", LocalDateTime.of(2026, 7, 4, 9, 0));
        awaitingConfirmation.getItemDetail().getMetadata().setTransferTargetAdminId("10001");

        var overview = CrossDatabaseQueryService.buildAssignedWorkOverview(List.of(
            riskTicket(2001L, "10001", 0, "URGENT", LocalDateTime.of(2026, 7, 1, 9, 0)),
            awaitingConfirmation), "10001", 2);

        Assertions.assertEquals(1L,
            overview.statusCounts().get(CrossDatabaseQueryService.STATUS_PENDING_CONFIRMATION));
        Assertions.assertEquals(List.of(2006L, 2001L), overview.riskTickets().stream()
            .map(ticket -> ticket.getItem().getItemId()).toList());
    }

    @Test
    void shouldBuildRiskListByPriorityThenWaitingTime() {
        List<CrossTicketDTO> tickets = List.of(
            riskTicket(2001L, "10001", 0, "HIGH", LocalDateTime.of(2026, 7, 1, 9, 0)),
            riskTicket(2002L, "10001", 1, "URGENT", LocalDateTime.of(2026, 7, 3, 9, 0)),
            riskTicket(2003L, "10001", 0, "URGENT", LocalDateTime.of(2026, 7, 2, 9, 0)),
            riskTicket(2004L, "10002", 0, "URGENT", LocalDateTime.of(2026, 6, 1, 9, 0)),
            riskTicket(2005L, "10001", 2, "URGENT", LocalDateTime.of(2026, 5, 1, 9, 0))
        );

        var overview = CrossDatabaseQueryService.buildAssignedWorkOverview(tickets, "10001", 2);

        Assertions.assertEquals(4L, overview.statusCounts().values().stream().mapToLong(Long::longValue).sum());
        Assertions.assertEquals(List.of(2003L, 2002L), overview.riskTickets().stream()
            .map(ticket -> ticket.getItem().getItemId()).toList());
    }

    @Test
    void shouldBuildUserOverviewWithCountsAndNewestTickets() {
        List<CrossTicketDTO> tickets = List.of(
            riskTicket(2001L, null, 0, "MEDIUM", LocalDateTime.of(2026, 7, 1, 9, 0)),
            riskTicket(2002L, null, 1, "HIGH", LocalDateTime.of(2026, 7, 3, 9, 0)),
            riskTicket(2003L, null, 2, "LOW", LocalDateTime.of(2026, 7, 2, 9, 0))
        );

        var overview = CrossDatabaseQueryService.buildUserWorkOverview(tickets, 2);

        Assertions.assertEquals(1L, overview.statusCounts().get(0));
        Assertions.assertEquals(1L, overview.statusCounts().get(1));
        Assertions.assertEquals(1L, overview.statusCounts().get(2));
        Assertions.assertEquals(List.of(2002L, 2003L), overview.recentTickets().stream()
            .map(ticket -> ticket.getItem().getItemId()).toList());
    }

    private CrossTicketDTO ticketAssignedTo(String adminId) {
        ItemDetail detail = new ItemDetail();
        detail.getMetadata().setAssignedAdminId(adminId);
        CrossTicketDTO ticket = new CrossTicketDTO();
        ticket.setItemDetail(detail);
        return ticket;
    }

    private CrossTicketDTO ticketAssignedTo(String adminId, int status) {
        CrossTicketDTO ticket = ticketAssignedTo(adminId);
        Order order = new Order();
        order.setStatus(status);
        ticket.setOrder(order);
        return ticket;
    }

    private CrossTicketDTO riskTicket(Long itemId, String adminId, int status, String priority,
                                      LocalDateTime createdAt) {
        CrossTicketDTO ticket = ticketAssignedTo(adminId, status);
        ticket.getItemDetail().getMetadata().setPriority(priority);
        Item item = new Item();
        item.setItemId(itemId);
        item.setTitle("工单 " + itemId);
        ticket.setItem(item);
        ticket.getOrder().setItemId(itemId);
        ticket.getOrder().setCreatedAt(createdAt);
        return ticket;
    }
}
