package com.i2i.cryptflow.market;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NewsItem(
    String id,
    String title,
    String body,
    String source,
    String url,
    @JsonProperty("imageurl") String imageurl,
    @JsonProperty("published_on") long publishedOn
) {}
