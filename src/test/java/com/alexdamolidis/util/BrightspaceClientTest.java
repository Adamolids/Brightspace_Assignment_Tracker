package com.alexdamolidis.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.http.*;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alexdamolidis.client.BrightspaceClient;
import com.alexdamolidis.service.SessionService;

@ExtendWith(MockitoExtension.class)
public class BrightspaceClientTest {

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private SessionService mockSessionService;

	@Mock
	private HttpResponse<String> mockResponse;

    @Mock
    private HttpHeaders mockHeaders;

	private BrightspaceClient client;

	@BeforeEach
	void setUp(){
		client = new BrightspaceClient(mockHttpClient, mockSessionService);
	}

	/**
	 * Ensures that sendGetRequest returns the response body as a String upon success.
	 */
	@Test
	void sendGetRequestReturnsBodyOnSuccess() throws IOException, InterruptedException {
	    String expectedJson = "{\"status\": \"success\"}";
	
	    when(mockResponse.statusCode()).thenReturn(200);
	    when(mockResponse.body()).thenReturn(expectedJson);
	    when(mockResponse.headers()).thenReturn(mockHeaders);
	    when(mockHeaders.firstValue("Content-Type")).thenReturn(Optional.of("application/json"));
	
		when(mockHttpClient.<String>send(any(), any())).thenReturn(mockResponse);

	    String result = client.sendGetRequest("https://api.test.com");

	    assertEquals(expectedJson, result);
	}

	/**
	 * Ensures that the correct error is thrown upon encountering a 401 status code.
	 */
	@Test
	void sendGetRequestThrowsExceptionOn401() throws IOException, InterruptedException{

		when(mockResponse.statusCode()).thenReturn(401);
	
		OngoingStubbing<HttpResponse<String>> stubbing = when(mockHttpClient.send(any(), any()));
		stubbing.thenReturn(mockResponse);

	    RuntimeException exception = assertThrows(RuntimeException.class, () -> {
	        client.sendGetRequest("https://test.com");
	    });

	    assertTrue(exception.getMessage().contains("401 Unauthorized"));
	}

	/**
	 * Ensures that sendGetRequest throws a RuntimeException when encountering 
	 * a response that is not content type JSON.
	 */
	@Test
	void sendGetRequestThrowsExceptionWhenNotJson() throws IOException, InterruptedException {

	    when(mockResponse.statusCode()).thenReturn(200);
	    when(mockResponse.headers()).thenReturn(mockHeaders);

		when(mockHeaders.firstValue("Content-Type")).thenReturn(Optional.of("text/html"));
	
		OngoingStubbing<HttpResponse<String>> stubbing = when(mockHttpClient.send(any(), any()));
		stubbing.thenReturn(mockResponse);

	    RuntimeException exception = assertThrows(RuntimeException.class, () -> {
	        client.sendGetRequest("https://api.test.com");
	    });

	    assertTrue(exception.getMessage().contains("Expected JSON but received: text/html"));
	}

	/**
	 * Ensures that a RuntimeException is thrown when network connection is lost.
	 */
	@Test
	void sendGetRequestThrowsExceptionWhenConnectionLost() throws IOException, InterruptedException {

		when(mockHttpClient.send(any(), any())).thenThrow(new IOException("Connection Reset"));

	    RuntimeException exception = assertThrows(RuntimeException.class, () -> {
	        client.sendGetRequest("https://api.test.com");
		});

	    assertTrue(exception.getMessage().contains("Network failure"));

		assertEquals(IOException.class, exception.getCause().getClass());
	}
}
