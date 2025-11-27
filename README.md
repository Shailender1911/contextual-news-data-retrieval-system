# Contextual News Data Retrieval System

Context-aware backend that:

- Ingests ~2K news articles from JSON into PostgreSQL.
- Uses an LLM (Ollama by default, OpenAI-compatible optionally) to understand user queries.
- Supports direct “virtual” endpoints (category, score, source, search, nearby).
- Computes an **event-driven trending feed** using simulated user activity and serves it via `/api/v1/news/trending`.
- Enriches top articles with LLM-generated summaries and key entities.
- Caches LLM outputs and trending feeds for predictable latency even when the model is slow or offline.

---

## 1. Architecture Overview

| Layer | Responsibilities | Key Types |
|-------|------------------|-----------|
| **API (`api.*`)** | REST controllers, DTOs, exception handlers. | `NewsController`, `NewsQueryRequest`, `NewsQueryResponse`, `TrendingResponse` |
| **Service (`service.*`)** | LLM delegation, retrieval strategies, ranking, enrichment, trending/event processing. | `NewsQueryService`, `ArticleResponseAssembler`, `TrendingService`, retrieval strategy implementations |
| **Domain (`domain.*`)** | JPA entities and repositories for articles, categories, and trending aggregates. | `NewsArticle`, `ArticleTrendAggregate`, repositories |
| **Infrastructure** | App configuration, caching, WebClient configuration, bootstrap loader. | `AppConfiguration`, `AppProperties`, `NewsDataLoader`, Flyway-style SQL scripts |

### Retrieval & Ranking
- Five strategies implement `ArticleRetrievalStrategy` (`category`, `score`, `source`, `search`, `nearby`).
- `ArticleRankingService` blends relevance score, recency, semantic boost, and proximity.

### LLM Integration & Fallback
- `DelegatingLLMClient` chooses Ollama (`/api/chat`) or OpenAI `/responses` endpoints based on config.
- 10 second client-side timeout; failures trigger `RuleBasedLLMClient` fallback so responses stay consistent.
- Summaries & query understanding cached via Caffeine.

### Trending Feed
- Simulated user events (VIEW/CLICK/SHARE) update `article_trend_aggregate`.
- Scores decay exponentially (half-life 6h) and are bucketed by geohash-like tiles.
- `/api/v1/news/trending` composes top articles near the requested location, caches per geo bucket.

---

## 2. Data Model

| Table | Purpose | Notes |
|-------|---------|-------|
| `news_article` | Main article catalog (title, description, relevance score, lat/lon, text search vector). | Indexed by `publication_date`, `relevance_score`, and full-text `tsvector`. |
| `article_category` | Join table mapping UUID → categories. | Many-to-many simplified as `text[]`. |
| `article_trend_aggregate` | Stores decayed trending score per `(bucket_id, article_id)`. | Updated on every event; queried when building feeds. |

Flyway-style migrations live under `src/main/resources/db/migration/`:
- `V1__create_article_tables.sql`
- `V2__create_trending_tables.sql`

---

## 3. Getting Started

```bash
# 1. Database bootstrap (PostgreSQL 14+)
createdb contextual_news
psql -d contextual_news -c "CREATE USER contextual_news WITH PASSWORD 'contextual_news';"
psql -d contextual_news -c "GRANT ALL PRIVILEGES ON DATABASE contextual_news TO contextual_news;"

# 2. Run the service
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
./mvnw spring-boot:run

# 3. Health check
curl http://localhost:8080/actuator/health
```

On first startup `NewsDataLoader` ingests the JSON fixture into the database.  
By default the app uses local **Ollama** (`llama3.1`); set `APP_LLM_ENABLED=false` to force rule-based parsing.

---

## 4. Configuration Cheat Sheet

```properties
# application.properties (defaults)
app.llm.provider=${APP_LLM_PROVIDER:ollama}
app.llm.base-url=${APP_LLM_BASE_URL:http://localhost:11434}
app.llm.model=${APP_LLM_MODEL:llama3.1}
app.llm.enabled=${APP_LLM_ENABLED:true}
app.llm.request-timeout=${APP_LLM_TIMEOUT:PT10S}

app.enrichment.top-n=${APP_ENRICH_MAX:5}
app.enrichment.cache-ttl=PT15M

app.trending.simulation-delay-ms=${APP_TRENDING_SIM_DELAY:30000}
```

- **OpenAI-compatible mode**  
  `export APP_LLM_PROVIDER=openai`  
  `export APP_LLM_BASE_URL=https://api.openai.com/v1`  
  `export APP_LLM_MODEL=gpt-4o-mini`  
  `export APP_LLM_API_KEY=<token>`  
  `export APP_LLM_ENABLED=true`
- **Disable LLM entirely**: `export APP_LLM_ENABLED=false` (rule-based parser & summarizer keep working).

---

## 5. API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/news/query` | LLM-assisted pipeline chooses the best retrieval strategy automatically. |
| `GET` | `/api/v1/news/category?category=Technology&limit=5` | Direct category filter (no LLM call). |
| `GET` | `/api/v1/news/source?source=Reuters&limit=5` | Filter by publisher/source. |
| `GET` | `/api/v1/news/score?threshold=0.8&limit=5` | Articles above a relevance score threshold. |
| `GET` | `/api/v1/news/search?query=Elon+Musk+Twitter&limit=5` | Full-text search across title + description. |
| `GET` | `/api/v1/news/nearby?lat=37.42&lon=-122.08&radiusKm=15&limit=5` | Geo filter using Haversine distance. |
| `GET` | `/api/v1/news/trending?lat=37.42&lon=-122.08&limit=5` | Location-sensitive trending feed derived from user events. |
| `POST` | `/api/v1/news/trending/events` | Ingest a user interaction event (VIEW/CLICK/SHARE). Accepts JSON body. |

### 5.1 LLM-Assisted Query

```bash
curl -s -X POST http://localhost:8080/api/v1/news/query \
  -H "Content-Type: application/json" \
  -d '{
        "query": "Latest developments in the Elon Musk Twitter acquisition near Palo Alto",
        "userLocation": { "latitude": 37.4, "longitude": -122.1 },
        "maxResults": 5
      }' | jq
```

### 5.2 Direct Endpoints (no LLM cost)

```bash
# Category
curl -s "http://localhost:8080/api/v1/news/category?category=Technology&limit=3" | jq

# Score
curl -s "http://localhost:8080/api/v1/news/score?threshold=0.9&limit=3" | jq

# Search
curl -s "http://localhost:8080/api/v1/news/search?query=IIT+Kanpur+latest+news&limit=3" | jq

# Nearby
curl -s "http://localhost:8080/api/v1/news/nearby?lat=28.61&lon=77.23&radiusKm=25&limit=3" | jq
```

### 5.3 Trending Feed

```bash
# What's trending near Mountain View?
curl -s "http://localhost:8080/api/v1/news/trending?lat=37.4220&lon=-122.0840&limit=5" | jq

# Inject a synthetic CLICK event (optional)
curl -s -X POST http://localhost:8080/api/v1/news/trending/events \
  -H "Content-Type: application/json" \
  -d '{
        "eventType": "CLICK",
        "articleId": "204f91d7-8dfe-4816-a6af-6ed9ebc53117",
        "userLocation": { "latitude": 37.42, "longitude": -122.08 }
      }'
```

Metadata in the response indicates whether the feed was served from cache and which geo bucket was used.

---

## 6. Trending Algorithm Primer

1. **Event ingestion**: `TrendingService.recordEvent` upserts `(bucketId, articleId)` rows in `article_trend_aggregate`, applying exponential decay (`λ = ln(2)/360min`).
2. **Scoring**: trending score = decayed sum of weighted events (VIEW=1, CLICK=3, SHARE=5).
3. **Geo bucketing**: coordinates are snapped to ~55 km tiles; `/trending` inspects the tile plus neighbours within the requested radius.
4. **Feed assembly**: top-N scored articles are enriched (summary/key entities) and cached for 60 s keyed by `(bucket, radius, limit)`.
5. **Simulation**: `TrendingEventSimulator` periodically generates random events so the feed always contains data even without real traffic. Disable or tune via `APP_TRENDING_SIM_DELAY`.

---

## 7. Tests

```bash
./mvnw test
```

- Lightweight unit tests run in-memory.
- `NewsQueryIntegrationTest` spins up PostgreSQL via Testcontainers if Docker is available; otherwise it is skipped with a warning.

---

## 8. Deployment & Future Enhancements

- Deploy alongside PostgreSQL (and optionally Redis if you move caches out of process).
- Replace simulated events with real analytics (web/mobile instrumentation).
- Extend trending buckets to use H3 or QuadKeys for finer control.
- Add pagination, OpenAPI/Swagger docs, and authentication hooks around `/api/v1/news/**`.

---

## 9. Contributing / Pushing

```bash
git add .
git commit -m "Implement contextual news retrieval + trending feed"
git push origin <branch>
```

Use a GitHub personal access token when prompted. All endpoints listed above are now implemented and covered by automated tests. Happy demoing!
