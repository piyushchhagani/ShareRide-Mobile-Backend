package com.shareride.shareride_mobile_backend.auth;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JwtService {

    private static final String SECRET =
            "ShareRideMobileBackendSecretKey2026VerySecure123456789";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long EXPIRATION_TIME_MS = 86_400_000L;
    private static final Pattern SUBJECT_PATTERN = Pattern.compile("\"sub\":\"([^\"]*)\"");
    private static final Pattern EXPIRATION_PATTERN = Pattern.compile("\"exp\":(\\d+)");

    private final SecretKeySpec key = new SecretKeySpec(
            SECRET.getBytes(StandardCharsets.UTF_8),
            HMAC_ALGORITHM
    );

    public String generateToken(String email) {
        long now = System.currentTimeMillis();
        long expiration = now + EXPIRATION_TIME_MS;

        String header = base64UrlEncode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64UrlEncode(
                "{\"sub\":\"" + jsonEscape(email) + "\",\"iat\":" + now + ",\"exp\":" + expiration + "}"
        );
        String signingInput = header + "." + payload;
        String signature = base64UrlEncode(sign(signingInput));

        return signingInput + "." + signature;
    }

    public String extractEmail(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT token format");
        }

        String header = parts[0];
        String payload = parts[1];
        String signature = parts[2];
        String signingInput = header + "." + payload;

        if (!constantTimeEquals(base64UrlEncode(sign(signingInput)), signature)) {
            throw new SecurityException("Invalid JWT signature");
        }

        String decodedPayload = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
        Matcher subjectMatcher = SUBJECT_PATTERN.matcher(decodedPayload);
        if (!subjectMatcher.find()) {
            throw new IllegalArgumentException("JWT payload is missing subject");
        }

        Matcher expirationMatcher = EXPIRATION_PATTERN.matcher(decodedPayload);
        if (expirationMatcher.find()) {
            long expiration = Long.parseLong(expirationMatcher.group(1));
            if (System.currentTimeMillis() >= expiration) {
                throw new SecurityException("JWT token has expired");
            }
        }

        return subjectMatcher.group(1);
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    private static String base64UrlEncode(String input) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64UrlEncode(byte[] input) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input);
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        if (left.length() != right.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\':
                    builder.append("\\\\");
                    break;
                case '"':
                    builder.append("\\\"");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
            }
        }
        return builder.toString();
    }
}