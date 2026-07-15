package com.ticket.dto;

import com.ticket.model.Comment;
import com.ticket.model.Item;
import com.ticket.model.ItemDetail;
import com.ticket.model.Order;
import com.ticket.model.Profile;
import com.ticket.model.User;
import com.ticket.model.TicketHistory;
import java.util.ArrayList;
import java.util.List;

public class ItemDetailDTO {
    private Item item;
    private Order order;
    private User user;
    private Profile profile;
    private ItemDetail itemDetail;
    private List<Comment> comments = new ArrayList<>();
    private List<TicketHistory> histories = new ArrayList<>();

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public ItemDetail getItemDetail() {
        return itemDetail;
    }

    public void setItemDetail(ItemDetail itemDetail) {
        this.itemDetail = itemDetail;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }

    public List<TicketHistory> getHistories() {
        return histories;
    }

    public void setHistories(List<TicketHistory> histories) {
        this.histories = histories;
    }
}
