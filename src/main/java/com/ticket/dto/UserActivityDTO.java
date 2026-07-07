package com.ticket.dto;

import com.ticket.model.Comment;
import com.ticket.model.Profile;
import com.ticket.model.User;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;

public class UserActivityDTO {
    private User user;
    private Profile profile;
    private List<CrossTicketDTO> recentTickets = new ArrayList<>();
    private List<Comment> recentComments = new ArrayList<>();
    private List<Document> recentActions = new ArrayList<>();

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

    public List<CrossTicketDTO> getRecentTickets() {
        return recentTickets;
    }

    public void setRecentTickets(List<CrossTicketDTO> recentTickets) {
        this.recentTickets = recentTickets;
    }

    public List<Comment> getRecentComments() {
        return recentComments;
    }

    public void setRecentComments(List<Comment> recentComments) {
        this.recentComments = recentComments;
    }

    public List<Document> getRecentActions() {
        return recentActions;
    }

    public void setRecentActions(List<Document> recentActions) {
        this.recentActions = recentActions;
    }
}
