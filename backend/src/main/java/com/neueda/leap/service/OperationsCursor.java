package com.neueda.leap.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

@Component
public class OperationsCursor {
    public record Position(Instant time, int id) {}

    private final SecretKey key;

    public OperationsCursor(SecretKey jwtSigningKey) {
        this.key = jwtSigningKey;
    }

    public String encode(Instant time, int id, String binding) {
        String payload = time + "|" + id + "|" + binding;
        return Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
    }

    public Position decode(String value, String binding) {
        try {
            if (value.length() > 800) throw new IllegalArgumentException();
            String[] token = value.split("\\.", -1);
            if (token.length != 2) throw new IllegalArgumentException();
            String payload =
                    new String(Base64.getUrlDecoder().decode(token[0]), StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(sign(payload), Base64.getUrlDecoder().decode(token[1])))
                throw new IllegalArgumentException();
            String[] parts = payload.split("\\|", -1);
            if (parts.length != 3 || !parts[2].equals(binding))
                throw new IllegalArgumentException();
            int id = Integer.parseInt(parts[1]);
            if (id <= 0) throw new IllegalArgumentException();
            return new Position(Instant.parse(parts[0]), id);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cursor expired or filters/scope changed; restart search");
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
