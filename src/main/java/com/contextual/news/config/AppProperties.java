package com.contextual.news.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app")
@Validated
public class AppProperties {

    private final DataProperties data = new DataProperties();
    private final LlmProperties llm = new LlmProperties();
    private final EnrichmentProperties enrichment = new EnrichmentProperties();
    private final RankingProperties ranking = new RankingProperties();

    public DataProperties data() {
        return data;
    }

    public LlmProperties llm() {
        return llm;
    }

    public EnrichmentProperties enrichment() {
        return enrichment;
    }

    public RankingProperties ranking() {
        return ranking;
    }

    @Validated
    public static class DataProperties {
        @NotBlank
        private String filePath = "classpath:data/news_data.json";

        private boolean bootstrapEnabled = true;

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public boolean isBootstrapEnabled() {
            return bootstrapEnabled;
        }

        public void setBootstrapEnabled(boolean bootstrapEnabled) {
            this.bootstrapEnabled = bootstrapEnabled;
        }
    }

    @Validated
    public static class LlmProperties {
        private static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";
        private static final String DEFAULT_OLLAMA_PROVIDER = "ollama";
        private static final String DEFAULT_OLLAMA_MODEL = "llama3.1";

        @NotBlank
        private String provider = DEFAULT_OLLAMA_PROVIDER;

        @NotBlank
        private String model = DEFAULT_OLLAMA_MODEL;

        @NotBlank
        private String baseUrl = DEFAULT_OLLAMA_BASE_URL;

        private String apiKey;

        @NotNull
        private Duration requestTimeout = Duration.ofSeconds(10);

        private boolean enabled = true;

        private List<String> intentsSchema;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            if (provider == null || provider.isBlank()) {
                this.provider = DEFAULT_OLLAMA_PROVIDER;
            } else {
                this.provider = provider;
            }
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            if (model == null || model.isBlank()) {
                this.model = DEFAULT_OLLAMA_MODEL;
            } else {
                this.model = model;
            }
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            if (baseUrl == null || baseUrl.isBlank()) {
                this.baseUrl = null;
            } else {
                this.baseUrl = baseUrl;
            }
        }

        public String getResolvedBaseUrl() {
            if (baseUrl != null && !baseUrl.isBlank()) {
                return baseUrl;
            }
            if ("ollama".equalsIgnoreCase(provider)) {
                return DEFAULT_OLLAMA_BASE_URL;
            }
            return baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public Duration getRequestTimeout() {
            return requestTimeout;
        }

        public void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
        }

        public boolean isEnabled() {
            if (!enabled) {
                return false;
            }
            if ("ollama".equalsIgnoreCase(provider)) {
                String resolvedBaseUrl = getResolvedBaseUrl();
                return resolvedBaseUrl != null && !resolvedBaseUrl.isBlank();
            }
            return apiKey != null && !apiKey.isBlank();
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getIntentsSchema() {
            return intentsSchema;
        }

        public void setIntentsSchema(List<String> intentsSchema) {
            this.intentsSchema = intentsSchema;
        }
    }

    @Validated
    public static class EnrichmentProperties {
        private int topN = 5;
        @NotNull
        private Duration cacheTtl = Duration.ofMinutes(15);

        public int getTopN() {
            return topN;
        }

        public void setTopN(int topN) {
            this.topN = topN;
        }

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }
    }

    @Validated
    public static class RankingProperties {
        private double relevanceWeight = 0.35;
        private double recencyWeight = 0.25;
        private double semanticWeight = 0.30;
        private double proximityWeight = 0.10;
        private double recencyHalfLifeDays = 7;

        public double getRelevanceWeight() {
            return relevanceWeight;
        }

        public void setRelevanceWeight(double relevanceWeight) {
            this.relevanceWeight = relevanceWeight;
        }

        public double getRecencyWeight() {
            return recencyWeight;
        }

        public void setRecencyWeight(double recencyWeight) {
            this.recencyWeight = recencyWeight;
        }

        public double getSemanticWeight() {
            return semanticWeight;
        }

        public void setSemanticWeight(double semanticWeight) {
            this.semanticWeight = semanticWeight;
        }

        public double getProximityWeight() {
            return proximityWeight;
        }

        public void setProximityWeight(double proximityWeight) {
            this.proximityWeight = proximityWeight;
        }

        public double getRecencyHalfLifeDays() {
            return recencyHalfLifeDays;
        }

        public void setRecencyHalfLifeDays(double recencyHalfLifeDays) {
            this.recencyHalfLifeDays = recencyHalfLifeDays;
        }
    }
}
