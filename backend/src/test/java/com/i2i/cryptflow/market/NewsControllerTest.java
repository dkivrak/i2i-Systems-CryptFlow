package com.i2i.cryptflow.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class NewsControllerTest {
    private final NewsService newsService = mock(NewsService.class);
    private final NewsController controller = new NewsController(newsService);

    @Test
    void returnsNewsFromService() {
        List<NewsItem> expected = List.of(
            new NewsItem("n1", "Title 1", "Body 1", "Source 1", "https://url1", "https://image1", 123456789L)
        );
        when(newsService.getNews()).thenReturn(expected);

        List<NewsItem> result = controller.getNews();

        assertEquals(expected, result);
        assertEquals(1, result.size());
        assertEquals("Title 1", result.get(0).title());
    }
}
