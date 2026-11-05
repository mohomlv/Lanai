package com.landata.datasource.infrastructure.jdbc;

import com.landata.shared.api.ApiException;
import com.landata.shared.config.LandataProperties;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class HostValidator {
  private static final Set<String> BLOCKED_NAMES = Set.of(
      "metadata.google.internal", "metadata.google.com"
  );
  private final boolean allowPrivate;
  private final Set<Integer> allowedPorts;

  @Autowired
  public HostValidator(LandataProperties properties) {
    this(properties.getSecurity().isAllowPrivateNetworks(), parsePorts(properties.getDatasource().getAllowedPorts()));
  }

  public HostValidator(boolean allowPrivate, Set<Integer> allowedPorts) {
    this.allowPrivate = allowPrivate;
    this.allowedPorts = allowedPorts;
  }

  public Resolved check(String host, int port) {
    if (host == null || host.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_HOST", "Host is required");
    }
    if (!allowedPorts.contains(port)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_PORT", "Port is not allowed");
    }
    String trimmed = host.trim().toLowerCase(Locale.ROOT);
    if (trimmed.contains("://") || trimmed.contains("/") || trimmed.contains("@") || trimmed.contains("\\")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_HOST", "Host must be a hostname or address");
    }
    if (BLOCKED_NAMES.contains(trimmed)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "SSRF", "That host is not allowed");
    }
    InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(trimmed);
    } catch (UnknownHostException ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_HOST", "Host could not be resolved");
    }
    for (InetAddress address : addresses) {
      if (alwaysBlocked(address) || (!allowPrivate && nonPublic(address))) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "SSRF", "That host is not allowed");
      }
    }
    return new Resolved(addresses[0].getHostAddress(), port);
  }

  private static boolean alwaysBlocked(InetAddress address) {
    if (address.isAnyLocalAddress() || address.isMulticastAddress() || address.isLinkLocalAddress()) return true;
    byte[] bytes = address.getAddress();
    return bytes.length == 4 && (bytes[0] & 0xff) == 169 && (bytes[1] & 0xff) == 254;
  }

  private static boolean nonPublic(InetAddress address) {
    return address.isLoopbackAddress() || address.isSiteLocalAddress();
  }

  private static Set<Integer> parsePorts(String raw) {
    if (raw == null || raw.isBlank()) return Set.of();
    return Arrays.stream(raw.split(",")).map(String::trim).filter(part -> !part.isEmpty())
        .map(Integer::valueOf).collect(Collectors.toSet());
  }

  public record Resolved(String address, int port) {}
}
