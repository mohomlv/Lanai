package com.landata.shared.log;

import java.util.regex.Pattern;

public final class LogScrubber {
  private static final Pattern SECRET = Pattern.compile(
      "(?i)(password|passwd|api[_-]?key|secret|authorization|bearer)\\s*([:=])\\s*\\S+"
  );

  private LogScrubber() {}

  public static String scrub(String raw) {
    if (raw == null) return null;
    return SECRET.matcher(raw).replaceAll("$1$2 ***");
  }
}
