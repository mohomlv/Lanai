package com.landata.shared.api;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, long total) {}
