package com.ticket.util;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.ticket.config.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MongoDBUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoDBUtil.class);
    private static MongoClient mongoClient;

    private MongoDBUtil() {
    }

    public static synchronized MongoDatabase getDatabase() {
        if (mongoClient == null) {
            mongoClient = MongoClients.create(DBConfig.get("mongodb.uri"));
            LOGGER.info("Initialized MongoClient");
        }
        return mongoClient.getDatabase(DBConfig.get("mongodb.database"));
    }

    public static synchronized void close() {
        if (mongoClient != null) {
            mongoClient.close();
            mongoClient = null;
            LOGGER.info("Closed MongoClient");
        }
    }
}
