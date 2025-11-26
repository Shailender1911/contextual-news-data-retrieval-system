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
        private String filePath;

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
        @NotBlank
        private String provider;

        @NotBlank
        private String model;

        @NotBlank
        private String baseUrl;

        private String apiKey;

        @NotNull
        private Duration requestTimeout = Duration.ofSeconds(10);

        private boolean enabled = true;

        private List<String> intentsSchema;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
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
                return baseUrl != null && !baseUrl.isBlank();
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
