package com.landata.shared.log;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LogScrubberTest {
  @Test
  void masksSecrets() {
    String scrubbed = LogScrubber.scrub("login failed password=s3cret api_key: abc.def");
    assertFalse(scrubbed.contains("s3cret"));
    assertFalse(scrubbed.contains("abc.def"));
    assertTrue(scrubbed.contains("password= ***"));
  }
}
