package com.mycompany.frauddetection;

import com.mycompany.frauddetection.record.FileContent;
import com.mycompany.frauddetection.service.FraudDetectionService;
import com.mycompany.frauddetection.service.LegacySystemService;
import com.mycompany.frauddetection.service.NoSQLDatabaseService;
import com.mycompany.frauddetection.service.SQLDatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FraudDetectionServiceTests {

    @InjectMocks
    private FraudDetectionService fraudDetectionService;

    @Mock
    private S3Client s3Client;

    @Mock
    private SqsClient sqsClient;

    @Mock
    private SageMakerRuntimeClient sageMakerRuntimeClient;

    @Mock
    private LegacySystemService legacySystemService;

    @Mock
    private NoSQLDatabaseService noSQLDatabaseService;

    @Mock
    private SQLDatabaseService sqlDatabaseService;

    InvokeEndpointResponse invokeResponse;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        fraudDetectionService = new FraudDetectionService();
        ReflectionTestUtils.setField(fraudDetectionService, "s3Client", s3Client);
        ReflectionTestUtils.setField(fraudDetectionService, "sqsClient", sqsClient);
        ReflectionTestUtils.setField(fraudDetectionService, "sageMakerRuntimeClient", sageMakerRuntimeClient);
        ReflectionTestUtils.setField(fraudDetectionService, "legacySystemService", legacySystemService);
        ReflectionTestUtils.setField(fraudDetectionService, "noSQLDatabaseService", noSQLDatabaseService);
        ReflectionTestUtils.setField(fraudDetectionService, "sqlDatabaseService", sqlDatabaseService);
        ReflectionTestUtils.setField(fraudDetectionService, "fraudDetectionEndpoint", "test-endpoint");
        ReflectionTestUtils.setField(fraudDetectionService, "sqsQueueUrl", "test-queue-url");

        invokeResponse = mock(InvokeEndpointResponse.class);
        SdkHttpResponse sdkHttpResponse = mock(SdkHttpResponse.class);
        SdkBytes sdkBytes = mock(SdkBytes.class);
        when(invokeResponse.sdkHttpResponse()).thenReturn(sdkHttpResponse);
        when(invokeResponse.sdkHttpResponse().statusCode()).thenReturn(200);
        when(invokeResponse.body()).thenReturn(sdkBytes);
    }

    @Test
    void testGetFileFromS3WithContentType() throws IOException {
        // Mock S3 client behavior
        String bucketName = "test-bucket";
        String fileKey = "test-file.txt";
        String contentType = "text/plain";
        String fileContent = "Test file content";
        byte[] contentBytes = fileContent.getBytes(StandardCharsets.UTF_8);

        GetObjectResponse getObjectResponse = GetObjectResponse.builder()
                .contentType(contentType)
                .contentLength((long) contentBytes.length)
                .build();

        try (ResponseInputStream<GetObjectResponse> responseInputStream = new ResponseInputStream<>(getObjectResponse,
                new ByteArrayInputStream(contentBytes))) {
            when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);

            // Call the method being tested
            FileContent result = fraudDetectionService.getFileFromS3WithContentType(bucketName, fileKey);

            // Verify the results
            assertNotNull(result);
            assertEquals(contentType, result.contentType());
            assertArrayEquals(contentBytes, result.fileContent());
        }
    }

    @Test
    void testConvertInputStreamToByteArray() throws IOException {
        // Test data
        String testString = "This is a test string.";
        InputStream inputStream = new ByteArrayInputStream(testString.getBytes(StandardCharsets.UTF_8));

        // Call the method being tested
        byte[] result = fraudDetectionService.convertInputStreamToByteArray(inputStream);

        // Verify the results
        assertNotNull(result);
        assertEquals(testString, new String(result, StandardCharsets.UTF_8));
    }

    @Test
    void testDetectFraud_AllChecksPass() throws Exception {
        // Test data
        byte[] contentBytes = "test-content".getBytes(StandardCharsets.UTF_8);
        String contentType = "text/plain";
        String userId = "test-user";


        when(invokeResponse.body().asUtf8String()).thenReturn("No Issues Detected");
        when(sageMakerRuntimeClient.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(invokeResponse);
        when(legacySystemService.checkUserFraud(userId)).thenReturn(false);

        // Call the method being tested
        fraudDetectionService.detectFraud(contentBytes, contentType, userId);

        // Verify interactions with dependencies
        verify(sageMakerRuntimeClient, times(1)).invokeEndpoint(any(InvokeEndpointRequest.class));
        verify(legacySystemService, times(1)).checkUserFraud(userId);
        verify(noSQLDatabaseService, times(1)).updateContentMetadata(eq(userId), eq(contentBytes), eq(contentType), eq(false), eq(false), eq(false));
        verify(sqlDatabaseService, times(0)).updateUserFraudStatus(anyString(), anyBoolean());
        verify(sqsClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testDetectFraud_AIFraudDetected() throws Exception {
        // Test data
        byte[] contentBytes = "test-content".getBytes(StandardCharsets.UTF_8);
        String contentType = "text/plain";
        String userId = "test-user";


        when(invokeResponse.body().asUtf8String()).thenReturn("fraud detected");
        when(sageMakerRuntimeClient.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(invokeResponse);
        when(legacySystemService.checkUserFraud(userId)).thenReturn(false);

        // Call the method being tested
        fraudDetectionService.detectFraud(contentBytes, contentType, userId);

        // Verify interactions with dependencies
        verify(sageMakerRuntimeClient, times(1)).invokeEndpoint(any(InvokeEndpointRequest.class));
        verify(legacySystemService, times(1)).checkUserFraud(userId);
        verify(noSQLDatabaseService, times(1)).updateContentMetadata(eq(userId), eq(contentBytes), eq(contentType), eq(true), eq(false), eq(false));
        verify(sqlDatabaseService, times(1)).updateUserFraudStatus(eq(userId), eq(true));
        verify(sqsClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testDetectFraud_LegacyFraudDetected() throws Exception {
        // Test data
        byte[] contentBytes = "test-content".getBytes(StandardCharsets.UTF_8);
        String contentType = "text/plain";
        String userId = "test-user";


        when(invokeResponse.body().asUtf8String()).thenReturn("fraud detected");
        when(sageMakerRuntimeClient.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(invokeResponse);

        // Call the method being tested
        fraudDetectionService.detectFraud(contentBytes, contentType, userId);

        // Verify interactions with dependencies
        verify(sageMakerRuntimeClient, times(1)).invokeEndpoint(any(InvokeEndpointRequest.class));
        verify(legacySystemService, times(1)).checkUserFraud(userId);
        verify(noSQLDatabaseService, times(1)).updateContentMetadata(eq(userId), eq(contentBytes), eq(contentType), eq(true), eq(false), eq(false));
        verify(sqlDatabaseService, times(1)).updateUserFraudStatus(eq(userId), eq(true));
        verify(sqsClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testDetectFraud_ContentFraudDetected() throws Exception {
        // Test data
        byte[] contentBytes = "fraudulent content".getBytes(StandardCharsets.UTF_8);
        String contentType = "text/plain";
        String userId = "test-user";


        when(invokeResponse.body().asUtf8String()).thenReturn("fraud detected");
        when(sageMakerRuntimeClient.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(invokeResponse);

        // Call the method being tested
        fraudDetectionService.detectFraud(contentBytes, contentType, userId);

        // Verify interactions with dependencies
        verify(sageMakerRuntimeClient, times(1)).invokeEndpoint(any(InvokeEndpointRequest.class));
        verify(legacySystemService, times(1)).checkUserFraud(userId);
        verify(noSQLDatabaseService, times(1)).updateContentMetadata(eq(userId), eq(contentBytes), eq(contentType), eq(true), eq(false), eq(true));
        verify(sqlDatabaseService, times(1)).updateUserFraudStatus(eq(userId), eq(true));
        verify(sqsClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testPerformAIFraudCheck_FraudDetected() throws Exception {
        // Test data
        byte[] contentBytes = "test-content".getBytes(StandardCharsets.UTF_8);
        String contentType = "text/plain";

        when(invokeResponse.body().asUtf8String()).thenReturn("fraud detected");
        when(sageMakerRuntimeClient.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(invokeResponse);

        // Call the method being tested
        boolean result = fraudDetectionService.performAIFraudCheck(contentBytes, contentType);

        // Verify the results
        assertTrue(result);
    }

    @Test
    void testPerformAIFraudCheck_FraudNotDetected() throws Exception {
        // Test data
        byte[] contentBytes = "test-content".getBytes(StandardCharsets.UTF_8);
        String contentType = "text/plain";

        when(invokeResponse.body().asUtf8String()).thenReturn("No Issues Detected");
        when(sageMakerRuntimeClient.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(invokeResponse);

        // Call the method being tested
        boolean result = fraudDetectionService.performAIFraudCheck(contentBytes, contentType);

        // Verify the results
        assertFalse(result);
    }

    @Test
    void testCheckContentFraud_FraudDetected() {
        // Test data
        byte[] contentBytes = "This content is fraudulent".getBytes(StandardCharsets.UTF_8);
        String contentType = "text/plain";

        // Call the method being tested
        boolean result = fraudDetectionService.checkContentFraud(contentBytes, contentType);

        // Verify the results
        assertTrue(result);
    }

    @Test
    void testCheckContentFraud_FraudNotDetected() {
        // Test data
        byte[] contentBytes = "This is harmless content".getBytes(StandardCharsets.UTF_8);
        String contentType = "text/plain";

        // Call the method being tested
        boolean result = fraudDetectionService.checkContentFraud(contentBytes, contentType);

        // Verify the results
        assertFalse(result);
    }

    @Test
    void testSendFraudDetectionResultToSQS() {
        // Test data
        String userId = "test-user";
        boolean aiFraud = true;
        boolean legacyFraud = false;
        boolean contentFraud = true;

        // Mock SQS client behavior
        SendMessageResponse response = mock(SendMessageResponse.class);
        doReturn(response).when(sqsClient).sendMessage(any(SendMessageRequest.class));

        // Call the method being tested
        fraudDetectionService.sendFraudDetectionResultToSQS(userId, aiFraud, legacyFraud, contentFraud);

        // Verify interaction with SQS client
        verify(sqsClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testReceiveMessage() throws Exception {
        // Test data
        String bucketName = "test-bucket";
        String fileKey = "test-file.txt";
        String userId = "userId";
        String message = "{\"bucket\":\"" + bucketName + "\",\"key\":\"" + fileKey + "\",\"userId\":\"" + userId + "\"}";
        String contentType = "text/plain";
        String fileContent = "Test file content";
        byte[] contentBytes = fileContent.getBytes(StandardCharsets.UTF_8);

        // Mock dependencies
        GetObjectResponse getObjectResponse = GetObjectResponse.builder()
                .contentType(contentType)
                .contentLength((long) contentBytes.length)
                .build();

        try (ResponseInputStream<GetObjectResponse> responseInputStream = new ResponseInputStream<>(getObjectResponse,
                new ByteArrayInputStream(contentBytes))) {
            when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);
        }

        when(invokeResponse.body().asUtf8String()).thenReturn("fraud detected");
        when(sageMakerRuntimeClient.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(invokeResponse);
        when(legacySystemService.checkUserFraud(userId)).thenReturn(false);

        // Call the method being tested
        fraudDetectionService.receiveMessage(message);

        // Verify interactions with dependencies
        verify(s3Client, times(1)).getObject(any(GetObjectRequest.class));
        verify(sageMakerRuntimeClient, times(1)).invokeEndpoint(any(InvokeEndpointRequest.class));
        verify(legacySystemService, times(1)).checkUserFraud(userId);
        verify(noSQLDatabaseService, times(1)).updateContentMetadata(eq(userId), eq(contentBytes), eq(contentType), eq(true), eq(false), eq(false));
        verify(sqsClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testExtractBucketName() {
        String sqsMessage = "{\"bucket\":\"test-bucket\",\"key\":\"test-file.txt\",\"userId\":\"test-user\"}";
        String expectedBucketName = "my-bucket";
        String actualBucketName = fraudDetectionService.extractBucketName(sqsMessage);
        assertEquals(expectedBucketName, actualBucketName);
    }

    @Test
    void testExtractFileKey() {
        String sqsMessage = "{\"bucket\":\"test-bucket\",\"key\":\"test-file.txt\",\"userId\":\"test-user\"}";
        String expectedFileKey = "myfile.txt";
        String actualFileKey = fraudDetectionService.extractFileKey(sqsMessage);
        assertEquals(expectedFileKey, actualFileKey);
    }

    @Test
    void testExtractUserId() {
        String sqsMessage = "{\"bucket\":\"test-bucket\",\"key\":\"test-file.txt\",\"userId\":\"test-user\"}";
        String expectedUserId = "userId";
        String actualUserId = fraudDetectionService.extractUserId(sqsMessage);
        assertEquals(expectedUserId, actualUserId);
    }
}