package com.alexdamolidis.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alexdamolidis.client.BrightspaceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Disabled;

public class BrightspaceClientIT {
	private static final Logger logger = LoggerFactory.getLogger(BrightspaceClientIT.class);
	BrightspaceClient client = new BrightspaceClient();
	String currentApiBeingUsed = EndpointBuilder.getApiVersion();

	/**
		Brightspace limits api calls by using a credit system. Brightspace documentation states that a each bucket contains
		50,000 credits and each api call costs 10 credits. If "X-Rate-Limit-Remaining" is missing from the this response, 
		the back-end service has not yet had the rate-limiting scheme activated. If so, please be mindful of api calls.
	*/
	@Disabled("Only run this to check remaining Brightspace API tokens/ request costs/ reset time. Requires valid cookies.")
	@Test
	public void checkApiCredits() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder()
										 .uri(URI.create("https://slate.sheridancollege.ca/d2l/api/lp/" + currentApiBeingUsed + "/users/whoami"))
										 .header("Accept", "application/json")
										 .GET()
										 .build();
    	HttpResponse<String> response = client.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

		HttpValidator.validate(response, "Brightspace API");

		HttpHeaders headers  = response.headers(); 

		Optional<String> remaining   = headers.firstValue("X-Rate-Limit-Remaining");
		Optional<String> requestCost = headers.firstValue("X-Request-Cost");
		Optional<String> reset       = headers.firstValue("X-Rate-Limit-Reset");

		if(remaining.isPresent()){
			logger.info("remaining credits: {}", remaining.get());
			logger.info("request cost: {}",  requestCost.orElse("Not present"));
			logger.info("reset timestamp: {}", reset.orElse("Not present"));
		}else{
			logger.info("This host does not have the rate limiting scheme active");
			//If you see this message it means your school has no rate limiting active.
			//Still, please be mindful and do not hammer the endpoints.
		}
	}

	/**
	 * If you are encountering any undocumented behaviour, please check that you are using a valid 
	 * API version. You are currently using: {@value EndpointBuilder#apiVersion}
	 * this variable is initialized at: {@link EndpointBuilder#apiVersion}.
	 * <br>
	 * The Brightspace API version must be supported for both Learning Environment(le) and Platform(lp).
	 */
	@Disabled("Only run this to check if you are using a valid Brightspace API version") 
	@Test
	public void checkValidApiVersions() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder()
								 .uri(URI.create("https://slate.sheridancollege.ca/d2l/api/versions/"))
								 .header("Accept", "application/json")
								 .GET()
								 .build();
    	HttpResponse<String> response = client.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

		HttpValidator.validate(response, "Brightspace API");

		ObjectMapper mapper = new ObjectMapper();
		JsonNode root = mapper.readTree(response.body());

		boolean leSupported = false;
		boolean lpSupported = false;

		String leBody = "";
		String lpBody = "";
		
		for(JsonNode node : root){
			String pdCode = node.get("ProductCode").asText();
				if(pdCode.equals("le")){
					leBody = node.get("SupportedVersions").toString();

					if(leBody.contains("\"" + currentApiBeingUsed + "\"")){
						leSupported = true;
					}
				}
				if(pdCode.equals("lp")){
					lpBody = node.get("SupportedVersions").toString();

					if(lpBody.contains("\"" + currentApiBeingUsed + "\"")){
						lpSupported = true;
					}
				}
		}
		assertTrue(leSupported && lpSupported,
		    "\nAPI version " + currentApiBeingUsed + " is not valid for Learning Environment(le) and Platform(lp)." +
		    "\nSupported le versions: " + leBody +
		    "\nSupported lp versions: " + lpBody);
	}
}