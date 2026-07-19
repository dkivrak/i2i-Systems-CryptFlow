package com.i2i.cryptflow.market;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/news")
@Tag(name = "News", description = "Public cryptocurrency news aggregated from external RSS feeds.")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    @Operation(
        summary = "Get cryptocurrency news",
        description = "Returns English news by default. lang=tr requests Gemini translation and falls back to Turkish RSS feeds when Gemini is unavailable; any other value follows the English path."
    )
    @ApiResponse(
        responseCode = "200",
        description = "News items, possibly an empty array when upstream feeds are unavailable.",
        content = @Content(
            array = @ArraySchema(schema = @Schema(implementation = NewsItem.class)),
            examples = @ExampleObject(value = "[{\"id\":\"4c8f5f6a\",\"title\":\"Crypto market update\",\"body\":\"A concise feed summary.\",\"source\":\"CoinDesk\",\"url\":\"https://example.com/article\",\"imageurl\":\"https://example.com/image.jpg\",\"published_on\":1784462400}]")
        )
    )
    public List<NewsItem> getNews(
        @Parameter(description = "Language selector. Use tr for Turkish; en and all other values use English.", example = "en")
        @RequestParam(value = "lang", defaultValue = "en") String lang) {
        return newsService.getNews(lang);
    }
}
