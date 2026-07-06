package com.ticket.dao;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.ticket.util.MongoDBUtil;
import org.bson.Document;

public abstract class MongoBaseDAO {
    protected MongoDatabase database() {
        return MongoDBUtil.getDatabase();
    }

    protected MongoCollection<Document> collection(String name) {
        return database().getCollection(name);
    }
}
