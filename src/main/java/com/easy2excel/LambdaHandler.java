
package com.easy2excel;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.FaceMatch;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.SearchFacesByImageRequest;
import com.amazonaws.services.rekognition.model.SearchFacesByImageResult;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.Item;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;

public class LambdaHandler implements RequestHandler<S3Event, String> {

	private final AmazonRekognition rekognition = AmazonRekognitionClientBuilder.standard().withRegion("us-east-1")
			.build();
	private final AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.standard().withRegion("us-east-1")
			.build();
	private final DynamoDB dynamoDB = new DynamoDB(dynamoDBClient);
	private final Table employeeTable = dynamoDB.getTable("facerecognition");
	private final String bucketName = "userenvrollment";

	@Override
	public String handleRequest(S3Event s3Event, Context context) {
		try {
			String objectKey = s3Event.getRecords().get(0).getS3().getObject().getKey();
			ByteBuffer imageBytes = getImageBytes(objectKey);
			SearchFacesByImageRequest request = new SearchFacesByImageRequest().withCollectionId("imageComparision")
					.withImage(new Image().withBytes(imageBytes));

			context.getLogger().log("imageBytes of the image: " + imageBytes);
			context.getLogger().log("SearchFacesByImageRequest: " + request);

			SearchFacesByImageResult response = rekognition.searchFacesByImage(request);
			context.getLogger().log("SearchFacesByImageResult:  resopnse" + response);

			context.getLogger().log("for loop started");

			for (FaceMatch match : response.getFaceMatches()) {
				String faceId = match.getFace().getFaceId();
				float confidence = match.getFace().getConfidence();
				Map<String, Object> faceItem = getFaceItem(faceId);
				if (faceItem != null) {
					context.getLogger().log("Person Found: " + faceItem);
					return buildResponse(200, "Success", faceItem.get("FacePrintsName"));
				} else {
					context.getLogger().log("face Id is null: " + getFaceItem(faceId));

				}
			}
			context.getLogger().log("Person could not be recognized.");
			return buildResponse(403, "Person Not Found", null);
		} catch (Exception e) {
			e.printStackTrace();
			return buildResponse(500, "Internal Server Error", null);
		}
	}

	private ByteBuffer getImageBytes(String objectKey) {
		try {
			AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withRegion("us-east-1").build();
			S3Object s3Object = s3Client.getObject(new GetObjectRequest(bucketName, objectKey));
			InputStream inputStream = s3Object.getObjectContent();
			byte[] bytes = IOUtils.toByteArray(inputStream);
			return ByteBuffer.wrap(bytes);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private Map<String, Object> getFaceItem(String faceId) {
		Item item = employeeTable.getItem("RekognitionId", faceId);
		return item != null ? item.asMap() : null;
	}

	private String buildResponse(int statusCode, String message, Object FacePrintsName) {
		Map<String, Object> response = Map.of("statusCode", statusCode, "headers",
				Map.of("Content-Type", "application/json", "Access-Control-Allow-Origin", "*"), "body",
				Map.of("Message", message, "FacePrintsName", FacePrintsName));

		return null;
	}
}