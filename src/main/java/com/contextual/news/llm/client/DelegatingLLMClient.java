package com.contextual.news.llm.client;

import com.contextual.news.config.AppProperties;
import com.contextual.news.llm.model.ArticleEnrichment;
import com.contextual.news.llm.model.ParsedQuery;
import com.contextual.news.service.dto.EnrichmentRequest;
import com.contextual.news.service.dto.QueryUnderstandingContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@Primary
public class DelegatingLLMClient implements LLMClient {

    private static final Logger log = LoggerFactory.getLogger(DelegatingLLMClient.class);
    private static final String PROVIDER_OLLAMA = "ollama";

    private final AppProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final RuleBasedLLMClient fallback;

    public DelegatingLLMClient(AppProperties properties,
                               WebClient llmWebClient,
                               ObjectMapper objectMapper,
                               RuleBasedLLMClient fallback) {
        this.properties = properties;
        this.webClient = llmWebClient;
        this.objectMapper = objectMapper;
        this.fallback = fallback;
    }

    @Override
    @Cacheable(value = "query-understanding", key = "#context.query() + ':' + #context.latitude() + ':' + #context.longitude()")
    public ParsedQuery parseQuery(QueryUnderstandingContext context) {
        if (!properties.llm().isEnabled()) {
            log.debug("LLM disabled or API key missing; using rule-based parser");
            return fallback.parseQuery(context);
        }
        try {
            PromptParts prompt = buildQueryPromptParts(context);
            JsonNode content = executeForJson(prompt, buildQuerySchema());
            ParsedQuery parsed = parseQueryContent(content);
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception ex) {
            log.warn("{} query parsing failed, falling back", properties.llm().getProvider(), ex);
        }
        return fallback.parseQuery(context).withFallback();
    }

    @Override
    @Cacheable(value = "article-enrichment", key = "#request.article().id")
    public ArticleEnrichment generateEnrichment(EnrichmentRequest request) {
        if (!properties.llm().isEnabled()) {
            return fallback.generateEnrichment(request);
        }
        try {
            PromptParts prompt = buildEnrichmentPromptParts(request);
            JsonNode content = executeForJson(prompt, buildEnrichmentSchema());
            ArticleEnrichment enrichment = parseEnrichmentContent(content);
            if (enrichment != null && !enrichment.isEmpty()) {
                return enrichment;
            }
        } catch (Exception ex) {
            log.warn("{} enrichment failed, using fallback", properties.llm().getProvider(), ex);
        }
        return fallback.generateEnrichment(request);
    }

    private ArticleEnrichment parseEnrichmentContent(JsonNode content) {
        if (content == null) {
            return null;
        }
        JsonNode summary = content.get("summary");
        JsonNode keyEntities = content.get("key_entities");
        JsonNode whyRelevant = content.get("why_relevant");
        List<String> entities = keyEntities != null && keyEntities.isArray()
            ? objectMapper.convertValue(keyEntities, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
            : List.of();
        return new ArticleEnrichment(summary != null ? summary.asText(null) : null, entities,
            whyRelevant != null ? whyRelevant.asText(null) : null);
    }

    private ParsedQuery parseQueryContent(JsonNode content) {
        if (content == null) {
            return null;
        }
        List<String> entities = extractStringArray(content.get("entities"));
        List<String> concepts = extractStringArray(content.get("concepts"));
        String intentString = content.path("intent").asText(null);
        EnumSet<com.contextual.news.llm.model.QueryIntent> intents = EnumSet.noneOf(com.contextual.news.llm.model.QueryIntent.class);
        if (intentString != null) {
            intents.add(com.contextual.news.llm.model.QueryIntent.fromString(intentString));
        }
        if (content.has("additional_intents")) {
            List<String> additional = extractStringArray(content.get("additional_intents"));
            if (!CollectionUtils.isEmpty(additional)) {
                for (String value : additional) {
                    intents.add(com.contextual.news.llm.model.QueryIntent.fromString(value));
                }
            }
        }
        JsonNode filterNode = content.get("filters");
        ParsedQuery.Filters filters = filterNode != null
            ? new ParsedQuery.Filters(
            filterNode.path("category").asText(null),
            filterNode.path("source").asText(null),
            filterNode.path("score_threshold").isNumber() ? filterNode.get("score_threshold").asDouble() : null,
            filterNode.path("radius_km").isNumber() ? filterNode.get("radius_km").asDouble() : null,
            filterNode.path("latitude").isNumber() ? filterNode.get("latitude").asDouble() : null,
            filterNode.path("longitude").isNumber() ? filterNode.get("longitude").asDouble() : null,
            null,
            null)
            : ParsedQuery.Filters.empty();
        String searchQuery = content.path("search_query").asText(null);
        if (intents.isEmpty()) {
            intents.add(com.contextual.news.llm.model.QueryIntent.SEARCH);
        }
        return ParsedQuery.create(entities, concepts, intents, filters, searchQuery, false);
    }

    private JsonNode executeForJson(PromptParts prompt, ObjectNode schema) {
        String raw = executeForString(prompt, schema);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
                    } catch (Exception ex) {
            log.warn("Failed to parse LLM content as JSON: {}", raw, ex);
            return null;
        }
    }

    private String executeForString(PromptParts prompt, ObjectNode schema) {
        if (isOllama()) {
            JsonNode response = callOllamaChat(prompt);
            return extractOllamaContent(response);
        }
        JsonNode response = callOpenAi(buildOpenAiRequest(prompt, schema));
        return extractOpenAiContent(response);
    }

    private boolean isOllama() {
        return PROVIDER_OLLAMA.equalsIgnoreCase(properties.llm().getProvider());
    }

    private PromptParts buildQueryPromptParts(QueryUnderstandingContext context) {
        String systemPrompt = "You are an AI that extracts structured filters and intent from news search queries.";
        StringBuilder userPrompt = new StringBuilder("Query: \"").append(context.query()).append("\"\n");
        if (context.latitude() != null && context.longitude() != null) {
            userPrompt.append("User location: lat=").append(context.latitude()).append(", lon=").append(context.longitude()).append('\n');
        }
        if (context.radiusKm() != null) {
            userPrompt.append("Radius hint: ").append(context.radiusKm()).append(" km\n");
        }
        userPrompt.append("Return only compact JSON following the agreed schema.");
        return new PromptParts(systemPrompt, userPrompt.toString());
    }

    private PromptParts buildEnrichmentPromptParts(EnrichmentRequest request) {
        String systemPrompt = "You summarize news articles in concise bullet points.";
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Article Title: ").append(request.article().getTitle()).append('\n');
        userPrompt.append("Description: ").append(request.article().getDescription()).append('\n');
        userPrompt.append("Source: ").append(request.article().getSourceName()).append('\n');
        if (request.userLatitude() != null && request.userLongitude() != null) {
            userPrompt.append("User location available for relevance explanation.\n");
        }
        userPrompt.append("Return only JSON containing summary, key_entities, and why_relevant.");
        return new PromptParts(systemPrompt, userPrompt.toString());
    }

    private JsonNode callOpenAi(Object body) {
        return webClient.post()
            .uri("/responses")
            .headers(headers -> {
                if (properties.llm().getApiKey() != null && !properties.llm().getApiKey().isBlank()) {
                    headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + properties.llm().getApiKey());
                }
            })
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(body))
            .retrieve()
            .bodyToMono(JsonNode.class)
            .onErrorResume(throwable -> Mono.error(new IllegalStateException("LLM call failed", throwable)))
            .block();
    }

    private JsonNode callOllamaChat(PromptParts prompt) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.llm().getModel());
        body.put("stream", false);
        var messages = objectMapper.createArrayNode();
        messages.add(chatMessage("system", prompt.systemPrompt()));
        messages.add(chatMessage("user", prompt.userPrompt()));
        body.set("messages", messages);

        return webClient.post()
            .uri("/api/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(body))
            .retrieve()
            .bodyToMono(JsonNode.class)
            .onErrorResume(throwable -> Mono.error(new IllegalStateException("Ollama call failed", throwable)))
            .block();
    }

    private Object buildOpenAiRequest(PromptParts prompt, ObjectNode schema) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.llm().getModel());
        if (schema != null) {
            root.set("response_format", schema);
        }
        var inputArray = objectMapper.createArrayNode();
        inputArray.add(roleContentNode("system", prompt.systemPrompt()));
        inputArray.add(roleContentNode("user", prompt.userPrompt()));
        root.set("input", inputArray);
        return root;
    }

    private ObjectNode roleContentNode(String role, String text) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", role);
        var contentArray = objectMapper.createArrayNode();
        ObjectNode textNode = objectMapper.createObjectNode();
        textNode.put("type", "text");
        textNode.put("text", text);
        contentArray.add(textNode);
        node.set("content", contentArray);
        return node;
    }

    private ObjectNode chatMessage(String role, String content) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", role);
        node.put("content", content);
        return node;
    }

    private ObjectNode buildQuerySchema() {
        ObjectNode schemaNode = objectMapper.createObjectNode();
        schemaNode.put("type", "json_schema");
        ObjectNode jsonSchema = objectMapper.createObjectNode();
        jsonSchema.put("name", "news_query_schema");

        ObjectNode schemaBody = objectMapper.createObjectNode();
        schemaBody.put("type", "object");
        ArrayNode required = objectMapper.createArrayNode();
        required.add("intent");
        schemaBody.set("required", required);

        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("intent", simpleType("string"));
        ObjectNode additionalIntents = objectMapper.createObjectNode();
        additionalIntents.put("type", "array");
        additionalIntents.set("items", simpleType("string"));
        properties.set("additional_intents", additionalIntents);
        properties.set("entities", arraySchema());
        properties.set("concepts", arraySchema());
        properties.set("search_query", simpleType("string"));

        ObjectNode filters = objectMapper.createObjectNode();
        filters.put("type", "object");
        ObjectNode filterProps = objectMapper.createObjectNode();
        filterProps.set("category", simpleType("string"));
        filterProps.set("source", simpleType("string"));
        filterProps.set("score_threshold", simpleType("number"));
        filterProps.set("radius_km", simpleType("number"));
        filterProps.set("latitude", simpleType("number"));
        filterProps.set("longitude", simpleType("number"));
        filters.set("properties", filterProps);

        properties.set("filters", filters);
        schemaBody.set("properties", properties);

        jsonSchema.set("schema", schemaBody);
        schemaNode.set("json_schema", jsonSchema);
        return schemaNode;
    }

    private ObjectNode buildEnrichmentSchema() {
        ObjectNode schemaNode = objectMapper.createObjectNode();
        schemaNode.put("type", "json_schema");
        ObjectNode jsonSchema = objectMapper.createObjectNode();
        jsonSchema.put("name", "news_enrichment_schema");

        ObjectNode schemaBody = objectMapper.createObjectNode();
        schemaBody.put("type", "object");
        ArrayNode required = objectMapper.createArrayNode();
        required.add("summary");
        schemaBody.set("required", required);

        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("summary", simpleType("string"));
        properties.set("key_entities", arraySchema());
        properties.set("why_relevant", simpleType("string"));

        schemaBody.set("properties", properties);
        jsonSchema.set("schema", schemaBody);
        schemaNode.set("json_schema", jsonSchema);
        return schemaNode;
    }

    private ObjectNode simpleType(String type) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", type);
        return node;
    }

    private ObjectNode arraySchema() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "array");
        node.set("items", simpleType("string"));
        return node;
    }

    private List<String> extractStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Iterator<JsonNode> iterator = node.elements();
        while (iterator.hasNext()) {
            JsonNode next = iterator.next();
            if (next.isTextual()) {
                values.add(next.asText());
            }
        }
        return values;
    }

    private String extractOpenAiContent(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode output = response.path("output");
        if (output.isArray() && output.size() > 0) {
            JsonNode first = output.get(0);
            JsonNode content = first.path("content");
            if (content.isArray() && content.size() > 0) {
                JsonNode firstContent = content.get(0);
                if (firstContent.has("text")) {
                    return firstContent.get("text").asText(null);
                }
            }
        }
        return null;
    }

    private String extractOllamaContent(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode message = response.path("message");
        if (message.has("content")) {
            return message.get("content").asText(null);
        }
        if (response.has("response")) {
            return response.get("response").asText(null);
        }
        return null;
    }

    private record PromptParts(String systemPrompt, String userPrompt) {
    }
}
