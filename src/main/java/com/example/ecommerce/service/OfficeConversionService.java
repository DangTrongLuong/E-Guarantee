package com.example.ecommerce.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

@Service
public class OfficeConversionService {
    private final URI endpoint;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public OfficeConversionService(@Value("${signing.converter-url}") String endpoint) { this.endpoint = URI.create(endpoint); }
    public byte[] convert(byte[] input, String sourceFormat) throws Exception {
        if (!sourceFormat.equals("doc") && !sourceFormat.equals("xls")) throw new IllegalArgumentException("No conversion needed");
        var request = HttpRequest.newBuilder(endpoint.resolve("/convert?format=" + sourceFormat))
                .timeout(Duration.ofSeconds(130)).header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(input)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) throw new java.io.IOException("Converter failed: HTTP " + response.statusCode());
            byte[] output = body.readNBytes((int) DocumentValidator.MAX_BYTES + 1);
            if (output.length == 0 || output.length > DocumentValidator.MAX_BYTES) throw new java.io.IOException("Invalid conversion size");
            return output;
        }
    }
}
