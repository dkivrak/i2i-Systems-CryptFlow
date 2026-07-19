package com.i2i.cryptflow.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private final WebClient webClient;
    
    private List<NewsItem> cachedEnNews = null;
    private Instant cacheExpiryEn = Instant.MIN;
    
    private List<NewsItem> cachedTrNews = null;
    private Instant cacheExpiryTr = Instant.MIN;

    private final Object cacheLock = new Object();

    public NewsService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public List<NewsItem> getNews(String lang) {
        synchronized (cacheLock) {
            boolean isTr = "tr".equalsIgnoreCase(lang);
            List<NewsItem> cached = isTr ? cachedTrNews : cachedEnNews;
            Instant expiry = isTr ? cacheExpiryTr : cacheExpiryEn;

            if (cached != null && Instant.now().isBefore(expiry)) {
                return cached;
            }

            List<NewsItem> freshNews = fetchAndParseAllFeeds(isTr);
            if (!freshNews.isEmpty() || cached == null) {
                if (isTr) {
                    cachedTrNews = freshNews;
                    cacheExpiryTr = Instant.now().plus(Duration.ofMinutes(5));
                } else {
                    cachedEnNews = freshNews;
                    cacheExpiryEn = Instant.now().plus(Duration.ofMinutes(5));
                }
                return freshNews;
            }
            return cached;
        }
    }

    private List<NewsItem> fetchAndParseAllFeeds(boolean isTr) {
        List<NewsItem> aggregatedNews = new ArrayList<>();

        if (isTr) {
            // KoinFinans
            aggregatedNews.addAll(fetchAndParseFeed("https://www.koinfinans.com/feed/", "KoinFinans"));
            // Investing.com TR
            aggregatedNews.addAll(fetchAndParseFeed("https://tr.investing.com/rss/302.rss", "Investing.com"));
        } else {
            // Cointelegraph
            aggregatedNews.addAll(fetchAndParseFeed("https://cointelegraph.com/rss", "Cointelegraph"));
            // CoinDesk
            aggregatedNews.addAll(fetchAndParseFeed("https://www.coindesk.com/arc/outboundfeeds/rss/", "CoinDesk"));
        }

        // Sort descending by publication date
        aggregatedNews.sort(Comparator.comparingLong(NewsItem::publishedOn).reversed());

        return aggregatedNews;
    }

    private List<NewsItem> fetchAndParseFeed(String url, String sourceName) {
        List<NewsItem> feedItems = new ArrayList<>();
        String xml = fetchFeedXml(url);

        if (xml == null || xml.isBlank()) {
            return feedItems;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            doc.getDocumentElement().normalize();

            NodeList nList = doc.getElementsByTagName("item");
            for (int i = 0; i < nList.getLength(); i++) {
                Node node = nList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;

                    String title = getElementValue(element, "title");
                    String link = getElementValue(element, "link");
                    String description = getElementValue(element, "description");
                    String pubDate = getElementValue(element, "pubDate");

                    if (title.isBlank() || link.isBlank()) {
                        continue;
                    }

                    // Extract image URL
                    String imageUrl = getElementAttribute(element, "media:content", "url");
                    if (imageUrl == null || imageUrl.isBlank()) {
                        imageUrl = getElementAttribute(element, "enclosure", "url");
                    }
                    if (imageUrl == null || imageUrl.isBlank()) {
                        imageUrl = extractImageFromHtml(description);
                    }

                    // Fallback images
                    if (imageUrl == null || imageUrl.isBlank()) {
                        imageUrl = getDefaultImageForTopic(title);
                    }

                    String bodyText = cleanHtml(description);
                    long publishedOn = parsePubDate(pubDate);
                    String id = generateId(link);

                    feedItems.add(new NewsItem(
                        id,
                        title,
                        bodyText,
                        sourceName,
                        link,
                        imageUrl,
                        publishedOn
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse RSS feed from " + url, e);
        }

        return feedItems;
    }

    private String fetchFeedXml(String url) {
        try {
            return webClient.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/xml, text/xml, */*")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
        } catch (Exception e) {
            log.error("Failed to fetch RSS feed from URL: " + url, e);
            return null;
        }
    }

    private String getElementValue(Element element, String tagName) {
        NodeList nodeList = element.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return "";
    }

    private String getElementAttribute(Element element, String tagName, String attrName) {
        NodeList nodeList = element.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            Node node = nodeList.item(0);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                String attr = ((Element) node).getAttribute(attrName);
                if (attr != null && !attr.isBlank()) {
                    return attr;
                }
            }
        }
        return null;
    }

    private String extractImageFromHtml(String html) {
        if (html == null || html.isBlank()) return null;
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"]", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private String cleanHtml(String html) {
        if (html == null) return "";
        String noTags = html.replaceAll("<[^>]*>", "").trim();
        noTags = noTags.replace("&amp;", "&")
                       .replace("&lt;", "<")
                       .replace("&gt;", ">")
                       .replace("&quot;", "\"")
                       .replace("&apos;", "'")
                       .replace("&#39;", "'");
        return noTags;
    }

    private long parsePubDate(String pubDateStr) {
        if (pubDateStr == null || pubDateStr.isBlank()) {
            return Instant.now().getEpochSecond();
        }
        try {
            return ZonedDateTime.parse(pubDateStr, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond();
        } catch (Exception e) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
                return ZonedDateTime.parse(pubDateStr, formatter).toEpochSecond();
            } catch (Exception ex) {
                try {
                    // Handle Investing.com "Jul 13, 2026 13:09 GMT"
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm z", Locale.ENGLISH);
                    return ZonedDateTime.parse(pubDateStr, formatter).toEpochSecond();
                } catch (Exception ex2) {
                    try {
                        return Instant.parse(pubDateStr).getEpochSecond();
                    } catch (Exception ex3) {
                        return Instant.now().getEpochSecond();
                    }
                }
            }
        }
    }

    private String generateId(String url) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return java.util.UUID.randomUUID().toString();
        }
    }

    private String getDefaultImageForTopic(String title) {
        String lowerTitle = title.toLowerCase();
        if (lowerTitle.contains("bitcoin") || lowerTitle.contains("btc")) {
            return "https://images.unsplash.com/photo-1518546305927-5a555bb7020d?w=500&auto=format&fit=crop&q=60";
        } else if (lowerTitle.contains("ethereum") || lowerTitle.contains("eth")) {
            return "https://images.unsplash.com/photo-1622790698141-94e304bc7ef9?w=500&auto=format&fit=crop&q=60";
        } else if (lowerTitle.contains("solana") || lowerTitle.contains("sol")) {
            return "https://images.unsplash.com/photo-1642104704074-907c0698cbd9?w=500&auto=format&fit=crop&q=60";
        }
        return "https://images.unsplash.com/photo-1639762681485-074b7f938ba0?w=500&auto=format&fit=crop&q=60";
    }
}
