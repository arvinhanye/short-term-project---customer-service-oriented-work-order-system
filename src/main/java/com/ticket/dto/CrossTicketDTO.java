package com.ticket.dto;

import com.ticket.model.Category;
import com.ticket.model.Comment;
import com.ticket.model.Item;
import com.ticket.model.ItemDetail;
import com.ticket.model.Order;
import com.ticket.model.Profile;
import com.ticket.model.User;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;

public class CrossTicketDTO {
    private Item item;
    private Order order;
    private Category category;
    private User user;
    private Profile profile;
    private ItemDetail itemDetail;
    private List<Comment> comments = new ArrayList<>();
    private List<Document> actionLogs = new ArrayList<>();
    private long commentCount;
    private long internalNoteCount;
    private long actionCount;
    private Double averageRating;
    private List<String> consistencyWarnings = new ArrayList<>();

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

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
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

    public List<Document> getActionLogs() {
        return actionLogs;
    }

    public void setActionLogs(List<Document> actionLogs) {
        this.actionLogs = actionLogs;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }

    public long getInternalNoteCount() {
        return internalNoteCount;
    }

    public void setInternalNoteCount(long internalNoteCount) {
        this.internalNoteCount = internalNoteCount;
    }

    public long getActionCount() {
        return actionCount;
    }

    public void setActionCount(long actionCount) {
        this.actionCount = actionCount;
    }

    public Double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(Double averageRating) {
        this.averageRating = averageRating;
    }

    public List<String> getConsistencyWarnings() {
        return consistencyWarnings;
    }

    public void setConsistencyWarnings(List<String> consistencyWarnings) {
        this.consistencyWarnings = consistencyWarnings;
    }
}
