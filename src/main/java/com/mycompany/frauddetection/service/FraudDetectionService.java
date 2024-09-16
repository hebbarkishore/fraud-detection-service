package com.mycompany.frauddetection.service;

import com.mycompany.frauddetection.record.FileContent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class FraudDetectionService {

    @Autowired
    private SageMakerRuntimeClient sageMakerRuntimeClient;

    @Autowired
    private LegacySystemService legacySystemService;

    @Autowired
    private NoSQLDatabaseService noSQLDatabaseService;

    @Autowired
    private SQLDatabaseService sqlDatabaseService;

    @Autowired
    private SqsClient sqsClient;

    @Autowired
    private S3Client s3Client;

    private final String fraudDetectionEndpoint = "<my-sagemaker-endpoint>";
    private final String sqsQueueUrl = "<my-sqs-queue-url>";

    @JmsListener(destination = "my-sqs-queue-name")
    public void receiveMessage(String message) {
        try {
            // Parse SQS message to extract file details (bucket, key, userId)
            String bucketName = extractBucketName(message);
            String fileKey = extractFileKey(message);
            String userId = extractUserId(message);

            FileContent fileContent = getFileFromS3WithContentType(bucketName, fileKey);
            detectFraud(fileContent.fileContent(), fileContent.contentType(), userId);
        } catch (Exception e) {
            System.out.println("Error processing message: " + e.getMessage());
        }
    }

    public FileContent getFileFromS3WithContentType(String bucketName, String fileKey) throws IOException {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .build();

        // Retrieve the file along with its metadata
        try (ResponseInputStream<GetObjectResponse> s3ObjectResponse = s3Client.getObject(getObjectRequest)) {
            // Extract the content type from the response metadata
            String contentType = s3ObjectResponse.response().contentType();

            // Convert the file content to a byte array
            byte[] fileContent = convertInputStreamToByteArray(s3ObjectResponse);

            // Return the file content and its content type
            return new FileContent(fileContent, contentType);
        }
    }

    public byte[] convertInputStreamToByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, bytesRead);
        }
        return byteArrayOutputStream.toByteArray();
    }


    /**
     * Perform fraud detection on content using AI/ML models, legacy systems, and update databases.
     * @param contentBytes
     * @param contentType
     * @param userId
     * @throws Exception
     */
    public void detectFraud(byte[] contentBytes, String contentType, String userId) throws Exception {
        // 1. Perform AI/ML fraud detection using SageMaker
        boolean aiFraudCheck = performAIFraudCheck(contentBytes, contentType);

        // 2. Perform fraud check in legacy system (via REST API)
        boolean legacyFraudCheck = legacySystemService.checkUserFraud(userId);

        // 3. Perform content-specific fraud check (simple example)
        boolean contentFraudCheck = checkContentFraud(contentBytes, contentType);

        // 4. Update NoSQL with metadata and fraud detection results
        noSQLDatabaseService.updateContentMetadata(userId, contentBytes, contentType, aiFraudCheck, legacyFraudCheck, contentFraudCheck);

        // 5. If fraud detected, update SQL database with user fraud status
        if (aiFraudCheck || legacyFraudCheck || contentFraudCheck) {
            sqlDatabaseService.updateUserFraudStatus(userId, true);
        }

        // 6. Send an update to the SQS queue with the result of the fraud check
        sendFraudDetectionResultToSQS(userId, aiFraudCheck, legacyFraudCheck, contentFraudCheck);
    }


    public boolean performAIFraudCheck(byte[] contentBytes, String contentType) throws Exception {
        String base64Content = Base64.getEncoder().encodeToString(contentBytes);
        String payloadJson = "{\"input\":\"" + base64Content + "\"}";
        InvokeEndpointRequest request = InvokeEndpointRequest.builder()
                .endpointName(fraudDetectionEndpoint)
                .contentType("application/json")
                .body(SdkBytes.fromUtf8String(payloadJson))
                .build();
        InvokeEndpointResponse response = sageMakerRuntimeClient.invokeEndpoint(request);
        return response.sdkHttpResponse().statusCode() == 200 && response.body().asUtf8String().contains("fraud");
    }

    public boolean checkContentFraud(byte[] contentBytes, String contentType) {
        String contentString = new String(contentBytes);
        return contentString.contains("fraud") || contentString.contains("illegal");
    }

    public void sendFraudDetectionResultToSQS(String userId, boolean aiFraud, boolean legacyFraud, boolean contentFraud) {
        Map<String, String> messageMap = new HashMap<>();
        messageMap.put("userId", userId);
        messageMap.put("aiFraudCheck", String.valueOf(aiFraud));
        messageMap.put("legacyFraudCheck", String.valueOf(legacyFraud));
        messageMap.put("contentFraudCheck", String.valueOf(contentFraud));

        // Convert map to JSON string for SQS message
        String messageBody = messageMap.toString();

        // Send message to SQS
        SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                .queueUrl(sqsQueueUrl)
                .messageBody(messageBody)
                .build();

        sqsClient.sendMessage(sendMsgRequest);
    }

    public String extractBucketName(String sqsMessage) {
        return "my-bucket";
    }

    public String extractFileKey(String sqsMessage) {
        return "myfile.txt";
    }

    public String extractUserId(String sqsMessage) {
        return "userId";
    }
}
