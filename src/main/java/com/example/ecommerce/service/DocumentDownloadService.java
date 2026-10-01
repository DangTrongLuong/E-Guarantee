package com.example.ecommerce.service;

import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

@Service
public class DocumentDownloadService {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public byte[] download(String url) throws Exception {
        var uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || !"res.cloudinary.com".equals(uri.getHost()) || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) throw new java.io.IOException("Only Cloudinary HTTPS URLs are allowed");
        var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) throw new java.io.IOException("Download failed: HTTP " + response.statusCode());
            byte[] bytes = body.readNBytes((int) DocumentValidator.MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > DocumentValidator.MAX_BYTES) throw new java.io.IOException("Invalid download size");
            return bytes;
        }
    }
}
