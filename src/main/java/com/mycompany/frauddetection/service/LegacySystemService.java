package com.mycompany.frauddetection.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class LegacySystemService {

    @Value("${legacy.system.url}")
    private String legacySystemUrl;

    RestTemplate restTemplate;
    public LegacySystemService(final RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean checkUserFraud(String userId) {
        String url = legacySystemUrl + "/checkFraud?userId=" + userId;
        Boolean isFraud = restTemplate.getForObject(url, Boolean.class);
        return Boolean.TRUE.equals(isFraud);
    }
}
