package com.alexdamolidis.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

public class BrightspaceClientIT {
	// "/d2l/api/lp/" + EndpointBuilder.getApiVersion() + "/users/whoami"
	BrightspaceClient client = new BrightspaceClient();

	/**
		Brightspace limits api calls by using a credit system. Brightspace documentation states that a each bucket contains
		50,000 credits and each api call costs 10 credits. If "X-Rate-Limit-Remaining" is missing from the this response, 
		the back-end service has not yet had the rate-limiting scheme activated. If so, please be mindful of api calls.
	*/
	@Disabled("Only run this to check remaining Brightspace API tokens/ request costs/ reset time. Requires valid cookies.")
	@Test
	public void checkApiCredits() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder()
										 .uri(URI.create("https://slate.sheridancollege.ca/d2l/api/lp/" + EndpointBuilder.getApiVersion() + "/users/whoami"))
										 .header("Accept", "application/json")
										 .GET()
										 .build();
    	HttpResponse<String> response = client.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
		HttpHeaders headers  = response.headers(); 

		Optional<String> remaining   = headers.firstValue("X-Rate-Limit-Remaining");
		Optional<String> requestCost = headers.firstValue("X-Request-Cost");
		Optional<String> reset       = headers.firstValue("X-Rate-Limit-Reset");

		if(remaining.isPresent()){
			System.out.println("remaining credits: " + remaining.get());
			System.out.println("request cost: "      + requestCost.orElse("Not present"));
			System.out.println("reset timestamp: "   + reset.orElse("Not present"));
		}else{
			System.out.println("This host does not have the rate limiting scheme active");
		}
	}
	
}
