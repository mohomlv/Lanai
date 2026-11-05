package com.landata.datasource.infrastructure.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.landata.shared.api.ApiException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HostValidatorTest {
  @Test
  void blocksMetadataAddressEvenWhenPrivateNetworksAreAllowed() {
    HostValidator validator = new HostValidator(true, Set.of(5432));
    assertThrows(ApiException.class, () -> validator.check("169.254.169.254", 5432));
  }

  @Test
  void blocksLoopbackWhenPrivateNetworksAreDisabled() {
    HostValidator validator = new HostValidator(false, Set.of(5432));
    assertThrows(ApiException.class, () -> validator.check("127.0.0.1", 5432));
  }

  @Test
  void allowsPublicAddress() {
    HostValidator validator = new HostValidator(false, Set.of(5432));
    assertEquals("8.8.8.8", validator.check("8.8.8.8", 5432).address());
  }

  @Test
  void rejectsUnexpectedPort() {
    HostValidator validator = new HostValidator(true, Set.of(5432));
    assertThrows(ApiException.class, () -> validator.check("8.8.8.8", 22));
  }
}
