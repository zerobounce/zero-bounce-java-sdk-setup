package net.zerobounce;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ErrorResponseTest {

    @Test
    public void parseError_ContainsOnlyError() {
        ArrayList<String> errors = new ArrayList<>();
        errors.add("Invalid API Key or your account ran out of credits");

        ErrorResponse expectedResponse = new ErrorResponse();
        expectedResponse.setSuccess(null);
        expectedResponse.setErrors(errors);

        String responseJson = "{\"error\":[\"Invalid API Key or your account ran out of credits\"]}";
        ErrorResponse actualResponse = ErrorResponse.parseError(responseJson);
        assertEquals(expectedResponse, actualResponse);

        String responseJsonVariant = "{\"error\":\"Invalid API Key or your account ran out of credits\"}";
        ErrorResponse actualResponseVariant = ErrorResponse.parseError(responseJsonVariant);
        assertEquals(expectedResponse, actualResponseVariant);
    }

    @Test
    public void parseError_ContainsOnlyErrors() {
        ArrayList<String> errors = new ArrayList<>();
        errors.add("Invalid API Key or your account ran out of credits");

        ErrorResponse expectedResponse = new ErrorResponse();
        expectedResponse.setSuccess(null);
        expectedResponse.setErrors(errors);

        String responseJson = "{\"errors\":[\"Invalid API Key or your account ran out of credits\"]}";
        ErrorResponse actualResponse = ErrorResponse.parseError(responseJson);
        assertEquals(expectedResponse, actualResponse);

        String responseJsonVariant = "{\"errors\":\"Invalid API Key or your account ran out of credits\"}";
        ErrorResponse actualResponseVariant = ErrorResponse.parseError(responseJsonVariant);
        assertEquals(expectedResponse, actualResponseVariant);
    }

    @Test
    public void parseError_ContainsOnlyMessage() {
        ArrayList<String> errors = new ArrayList<>();
        errors.add("Invalid API Key or your account ran out of credits");

        ErrorResponse expectedResponse = new ErrorResponse();
        expectedResponse.setSuccess(null);
        expectedResponse.setErrors(errors);

        String responseJson = "{\"message\":[\"Invalid API Key or your account ran out of credits\"]}";
        ErrorResponse actualResponse = ErrorResponse.parseError(responseJson);
        assertEquals(expectedResponse, actualResponse);

        String responseJsonVariant = "{\"message\":\"Invalid API Key or your account ran out of credits\"}";
        ErrorResponse actualResponseVariant = ErrorResponse.parseError(responseJsonVariant);
        assertEquals(expectedResponse, actualResponseVariant);
    }

    @Test
    public void parseError_ContainsOnlyMessages() {
        ArrayList<String> errors = new ArrayList<>();
        errors.add("Invalid API Key or your account ran out of credits");

        ErrorResponse expectedResponse = new ErrorResponse();
        expectedResponse.setSuccess(null);
        expectedResponse.setErrors(errors);

        String responseJson = "{\"messages\":[\"Invalid API Key or your account ran out of credits\"]}";
        ErrorResponse actualResponse = ErrorResponse.parseError(responseJson);
        assertEquals(expectedResponse, actualResponse);

        String responseJsonVariant = "{\"messages\":\"Invalid API Key or your account ran out of credits\"}";
        ErrorResponse actualResponseVariant = ErrorResponse.parseError(responseJsonVariant);
        assertEquals(expectedResponse, actualResponseVariant);
    }

    @Test
    public void parseError_ContainsOnlySuccess() {
        ErrorResponse expectedResponse = new ErrorResponse();
        expectedResponse.setSuccess(true);

        String responseJson = "{\"success\": true}";
        ErrorResponse actualResponse = ErrorResponse.parseError(responseJson);
        assertEquals(expectedResponse, actualResponse);

        String responseJsonVariant = "{\"success\":[true]}";
        ErrorResponse actualResponseVariant = ErrorResponse.parseError(responseJsonVariant);
        assertNotEquals(expectedResponse, actualResponseVariant);
    }

    @Test
    public void parseError_ContainsMixedData() {
        ArrayList<String> errors = new ArrayList<>();
        errors.add("Invalid API Key or your account ran out of credits");
        errors.add("Error messages");
        errors.add("More data to look into");

        ErrorResponse expectedResponse = new ErrorResponse();
        expectedResponse.setSuccess(false);
        expectedResponse.setErrors(errors);

        String responseJson = "{\n" +
                "    \"success\": false,\n" +
                "    \"error\": \"Invalid API Key or your account ran out of credits\"\n," +
                "    \"data\": \"More data to look into\"\n," +
                "    \"message\": \"Error messages\"\n" +
                "}";
        ErrorResponse actualResponse = ErrorResponse.parseError(responseJson);
        assertEquals(expectedResponse, actualResponse);

        String responseJsonVariant = "{\n" +
                "    \"success\": false,\n" +
                "    \"error\": [\"Invalid API Key or your account ran out of credits\"\n]," +
                "    \"data\": \"More data to look into\"\n," +
                "    \"message\": \"Error messages\"\n" +
                "}";
        ErrorResponse actualResponseVariant = ErrorResponse.parseError(responseJsonVariant);
        assertEquals(expectedResponse, actualResponseVariant);

        String responseJsonVariant2 = "{\n" +
                "    \"success\": false,\n" +
                "    \"error\": [\"Invalid API Key or your account ran out of credits\"\n]," +
                "    \"data\": \"More data to look into\"\n," +
                "    \"message\": [\"Error messages\"\n]" +
                "}";
        ErrorResponse actualResponseVariant2 = ErrorResponse.parseError(responseJsonVariant2);
        assertEquals(expectedResponse, actualResponseVariant2);

        String responseJsonVariant3 = "{\n" +
                "    \"success\": false,\n" +
                "    \"error\": \"Invalid API Key or your account ran out of credits\"\n," +
                "    \"data\": \"More data to look into\"\n," +
                "    \"message\": [\"Error messages\"\n]" +
                "}";
        ErrorResponse actualResponseVariant3 = ErrorResponse.parseError(responseJsonVariant3);
        assertEquals(expectedResponse, actualResponseVariant3);

        errors.clear();
        errors.add("Invalid API Key or your account ran out of credits");
        errors.add("Error messages");
        errors.add("More errors");
        errors.add("More data to look into");

        expectedResponse.setSuccess(true);
        expectedResponse.setErrors(errors);

        String responseJsonVariant4 = "{\n" +
                "    \"success\": true,\n" +
                "    \"error\": \"Invalid API Key or your account ran out of credits\"\n," +
                "    \"data\": \"More data to look into\"\n," +
                "    \"message\": [\"Error messages\", \"More errors\"\n]" +
                "}";
        ErrorResponse actualResponseVariant4 = ErrorResponse.parseError(responseJsonVariant4);
        assertEquals(expectedResponse, actualResponseVariant4);
    }
}