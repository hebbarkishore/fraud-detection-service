package com.mycompany.frauddetection.service;

import org.springframework.stereotype.Service;

@Service
public class SQLDatabaseService {

    // Simulating interaction with SQL database
    public void updateUserFraudStatus(String userId, boolean isFraud) {
        // Logic to update SQL database with user fraud status
        System.out.println("Updating SQL DB for user: " + userId + " with fraud status: " + isFraud);
    }
}
