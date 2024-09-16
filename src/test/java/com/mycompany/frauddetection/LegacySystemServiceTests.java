package com.mycompany.frauddetection;

import com.mycompany.frauddetection.service.LegacySystemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacySystemServiceTests {

    @InjectMocks
    private LegacySystemService legacySystemService;

    @Mock
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(legacySystemService, "legacySystemUrl", "http://legacy-system");
        ReflectionTestUtils.setField(legacySystemService, "restTemplate", restTemplate);
    }

    @Test
    void testCheckUserFraud_Fraudulent() {
        String userId = "userId";
        String expectedUrl = "http://legacy-system/checkFraud?userId=" + userId;
        when(restTemplate.getForObject(eq(expectedUrl), eq(Boolean.class)))
                .thenReturn(true);
        boolean result = legacySystemService.checkUserFraud(userId);
        assertTrue(result);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).getForObject(urlCaptor.capture(), eq(Boolean.class));
        assertEquals(expectedUrl, urlCaptor.getValue());
    }

    @Test
    void testCheckUserFraud_NotFraudulent() {
        String userId = "test-user";
        when(restTemplate.getForObject(eq("http://legacy-system/checkFraud?userId=" + userId), eq(Boolean.class)))
                .thenReturn(false);
        boolean result = legacySystemService.checkUserFraud(userId);
        assertFalse(result);
    }
}