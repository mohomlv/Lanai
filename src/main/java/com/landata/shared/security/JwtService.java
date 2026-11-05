package com.landata.shared.security;

import com.landata.shared.config.LandataProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final byte[] secret;
  private final long accessSeconds;

  public JwtService(LandataProperties properties) {
    String raw = properties.getJwt().getSecret();
    if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException("landata.jwt.secret must be at least 32 bytes");
    }
    this.secret = raw.getBytes(StandardCharsets.UTF_8);
    this.accessSeconds = properties.getJwt().getAccessMinutes() * 60L;
  }

  public String accessToken(AuthPrincipal principal) {
    try {
      Instant now = Instant.now();
      JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
          .subject(principal.userId().toString())
          .jwtID(UUID.randomUUID().toString())
          .issueTime(Date.from(now))
          .expirationTime(Date.from(now.plusSeconds(accessSeconds)))
          .claim("email", principal.email());
      if (principal.companyId() != null) claims.claim("company_id", principal.companyId().toString());
      if (principal.role() != null) claims.claim("role", principal.role());
      if (principal.username() != null && !principal.username().isBlank()) claims.claim("username", principal.username());
      claims.claim("branch_ids", principal.branchIds());
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
      jwt.sign(new MACSigner(secret));
      return jwt.serialize();
    } catch (Exception ex) {
      throw new IllegalStateException("Could not sign access token", ex);
    }
  }

  public AuthPrincipal parse(String token) {
    try {
      SignedJWT jwt = SignedJWT.parse(token);
      if (!jwt.verify(new MACVerifier(secret))) return null;
      if (jwt.getJWTClaimsSet().getExpirationTime().before(new Date())) return null;
      JWTClaimsSet claims = jwt.getJWTClaimsSet();
      String company = claims.getStringClaim("company_id");
      return new AuthPrincipal(
          UUID.fromString(claims.getSubject()),
          company == null ? null : UUID.fromString(company),
          claims.getStringClaim("role"),
          claims.getStringClaim("email"),
          claims.getStringClaim("username"),
          integers(claims.getClaim("branch_ids"))
      );
    } catch (Exception ex) {
      return null;
    }
  }

  private static List<Integer> integers(Object raw) {
    List<Integer> ids = new ArrayList<>();
    if (!(raw instanceof List<?> list)) return ids;
    for (Object item : list) {
      if (item instanceof Number number) {
        ids.add(number.intValue());
      } else if (item != null) {
        try {
          ids.add(Integer.valueOf(String.valueOf(item).trim()));
        } catch (NumberFormatException ignored) {
          // skip non-integer claim values
        }
      }
    }
    return ids;
  }
}
