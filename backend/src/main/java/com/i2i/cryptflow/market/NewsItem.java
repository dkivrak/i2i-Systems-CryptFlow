package com.i2i.cryptflow.market;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "NewsItem", description = "One external cryptocurrency news article.")
public record NewsItem(
    @Schema(description = "Stable article identifier derived from its URL.", example = "4c8f5f6a") String id,
    @Schema(description = "Article headline.", example = "Crypto market update") String title,
    @Schema(description = "Plain-text article summary from the feed.", example = "A concise feed summary.") String body,
    @Schema(description = "Feed publisher.", example = "CoinDesk") String source,
    @Schema(description = "Original article URL.", example = "https://example.com/article", format = "uri") String url,
    @Schema(description = "Article or topic image URL.", example = "https://example.com/image.jpg", format = "uri")
    @JsonProperty("imageurl") String imageurl,
    @Schema(description = "Publication time as Unix epoch seconds.", example = "1784462400")
    @JsonProperty("published_on") long publishedOn
) {}
