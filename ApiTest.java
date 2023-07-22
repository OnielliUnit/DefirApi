package com.yourcompany.apitests;

import io.github.cdimascio.dotenv.Dotenv;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ApiTest {
    Dotenv dotenv = Dotenv.load();

    private Response response;
    private String report;
    private String clientId = dotenv.get("CLIENT_ID");
    private String clientKey = dotenv.get("CLIENT_KEY");

    private StringBuilder testResults = new StringBuilder(); // Строка для хранения результатов тестов

    @BeforeEach
    public void setUp() {
        RestAssured.baseURI = "https://api.cloudpbx.rt.ru";
        RestAssured.useRelaxedHTTPSValidation();
    }

    @Test
    @Order(1)
    @DisplayName(value = "1. Совершение исходящего вызова")
    public void testOutgoingCall() throws NoSuchMethodException {
        String requestNumber = dotenv.get("REQUEST_NUMBER");
        String fromSipuri = dotenv.get("FROM_SIPURI");

        Logger LOGGER = LoggerFactory.getLogger(ApiTest.class);
        Marker REQUEST_MARKER = MarkerFactory.getMarker("REQUEST");

        String requestBody = "{\"request_number\":\"" + requestNumber + "\",\"from_sipuri\":\"" + fromSipuri + "\"}";
        String clientSign = computeSHA256(clientId + requestBody + clientKey);

        RequestSpecification requestSpec = RestAssured.given()
                .header("Accept", "application/json")
                .header("User-Agent", "QA-APP")
                .header("X-Client-ID", clientId)
                .header("X-Client-Sign", clientSign)
                .header("Content-Type", "application/json")
                .body(requestBody);

        String httpMethod = "POST";
        this.response = requestSpec.post("/call_back");

        String uuid = UUID.randomUUID().toString();
        LOGGER.info(REQUEST_MARKER, "UUID: {} | HTTP Request: {} {}", uuid, httpMethod, RestAssured.baseURI + "/call_back");
        LOGGER.info(REQUEST_MARKER, "UUID: {} | HTTP Request: {}", uuid, requestBody);
        LOGGER.info(REQUEST_MARKER, "UUID: {} | HTTP Response: {} {}", uuid, response.getStatusLine(), response.getStatusCode());
        LOGGER.info(REQUEST_MARKER, "UUID: {} | HTTP Response Headers: {}", uuid, response.getHeaders().toString());
        LOGGER.info(REQUEST_MARKER, "UUID: {} | HTTP Response Body: {}", uuid, response.getBody().asString());

        assertEquals(200, response.getStatusCode());
        String resultValue = response.jsonPath().getString("result");
        assertEquals("0", resultValue);

        Method testMethod = getClass().getMethod("testOutgoingCall");
//        String testName = testMethod.getAnnotation(DisplayName.class).value(); Рефлексия аннотации имени теста
        String report = generateAllureReport(uuid, requestBody, response.getBody().asString());
        setReport(report);
    }

    @Test
    @Order(2)
    @DisplayName(value = "2. Экспорт адресной книги домена")
    public void testExportAddressBook() throws NoSuchMethodException {
        String domain = dotenv.get("DOMAIN");

        Logger LOGGER = LoggerFactory.getLogger(ApiTest.class);
        Marker REQUEST_MARKER = MarkerFactory.getMarker("REQUEST");

        String requestBody = "{\"domain\":\"" + domain + "\"}";
        String clientSign = computeSHA256(clientId + requestBody + clientKey);
        RequestSpecification requestSpec = RestAssured.given()
                .header("Accept", "application/json")
                .header("User-Agent", "QA-APP")
                .header("X-Client-ID", clientId)
                .header("X-Client-Sign", clientSign)
                .header("Content-Type", "application/json")
                .body(requestBody);

        String httpMethod = "POST";
        this.response = requestSpec.post("/users_info");

        String uuid = UUID.randomUUID().toString();
        LOGGER.info(REQUEST_MARKER, "UUID: {} | HTTP Request: {} {}", uuid, httpMethod, RestAssured.baseURI + "/users_info");
        LOGGER.info(REQUEST_MARKER, "UUID: {} | HTTP Request: {}", uuid, requestBody);
        LOGGER.info(REQUEST_MARKER, "UUID: {} | HTTP Response: {} {}", uuid, response.getStatusLine(), response.getStatusCode());
        LOGGER.info(REQUEST_MARKER, "UUID: {} | HTTP Response Headers: {}", uuid, response.getHeaders().toString());
        LOGGER.info(REQUEST_MARKER, "UUID: {} | HTTP Response Body: {}", uuid, response.getBody().asString());

        assertEquals(200, response.getStatusCode());

        Method testMethod = getClass().getMethod("testExportAddressBook");
//        String testName = testMethod.getAnnotation(DisplayName.class).value(); Рефлексия аннотации имени теста
        String report = generateAllureReport(uuid, requestBody, response.getBody().asString());
        setReport(report);
    }

    @AfterEach
    public void tearDown() {
        // Nothing to tear down in this example
    }

    @Step("Generating Allure Report for test: {testName}")
    private String generateAllureReport(String uuid, String requestBody, String responseBody) {
        if (response == null) {
            return "Тест не выполнился. Ответ от сервера не получен.";
        }

        long timeOfRequestMillis = System.currentTimeMillis();
        String timeOfRequest = formatDateToISO8601Moscow(timeOfRequestMillis);
        String path = RestAssured.baseURI;
        String method = "POST";
        int statusCode = response.getStatusCode();

        String report = "Время запроса: " + timeOfRequest + "\n" +
                "ID запроса: " + uuid + "\n" +
                "Путь: " + path + "\n" +
                "Метод: " + method + "\n" +
                "Код ответа: " + statusCode + "\n\n" +
                "Request: " + requestBody + "\n\n" +
                "Response: " + responseBody + "\n\n" +
                "------------------------------------";

        Allure.parameter("Время запроса", timeOfRequest);
        Allure.parameter("Путь", path);
        Allure.parameter("Метод", method);
        Allure.parameter("Код ответа", statusCode);

        Allure.attachment("Request", requestBody);
        Allure.attachment("Response", responseBody);

        Allure.step("Тест выполнен успешно");

        return report;
    }

    private String formatDateToISO8601Moscow(long millis) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
        return dateFormat.format(new Date(millis));
    }

    private void setReport(String report) {
        this.report = report;
    }

    public String getReport() {
        return report;
    }

    private String computeSHA256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available.");
        }
    }
}
