fraud-detection-service
This service does the following.
1.  It reads the message from SQS
2.  Then it pull the related file from S3
3.  Perform AI/ML fraud detection using SageMaker
4.  Perform fraud check in legacy system (via REST API)
5.  Perform content-specific fraud check
6.  Update NoSQL with metadata and fraud detection results
7.  If fraud detected, update SQL database with user fraud status
8.  Send an update to the SQS queue with the result of the fraud check
