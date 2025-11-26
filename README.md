# Contextual News Data Retrieval System

## Overview

This Spring Boot 3 (Java 17) service ingests a corpus of news articles, understands natural‑language queries with an LLM, retrieves relevant documents using multiple strategies, ranks the results, enriches them, and returns a structured JSON response.  
It was built with Maven, PostgreSQL (with PostGIS-like geo queries expressed in JPA), Caffeine caching, and Flyway-style SQL bootstrapping.

## Architecture Highlights

- **Hexagonal layering**
  - `api`: request/response DTOs, controller, global exception handler
  - `service`: query orchestration, retrieval strategies, ranking, enrichment
  - `domain`: JPA entities, repositories, specifications
  - `infrastructure`: configuration, data bootstrap, LLM clients
- **LLM integration**: `DelegatingLLMClient` with a rule-based fallback parses intents (category, score, search, source, nearby), extracts entities/concepts, and generates enrichment snippets.
- **Retrieval strategies** (`ArticleRetrievalStrategy`) implement the required “virtual endpoints”: category, score, search, source, nearby.
- **Ranking**: blends stored relevance score, recency, proximity, and semantic hints into a final score.
- **Caching**: Caffeine caches query understanding and enrichment calls.
- **Data bootstrap**: `NewsDataLoader` loads `src/main/resources/data/news_data.json` on startup when the database is empty.

## Prerequisites

- Java 17 (`export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`)
- Maven Wrapper (`./mvnw`)
- Docker (optional for PostgreSQL via Compose; we verified against a local Postgres 14 instance)
- PostgreSQL 14+ with the `contextual_news` database and user/password `contextual_news`

## Configuration

All defaults live in `src/main/resources/application.properties`. Key settings:

```properties
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/contextual_news}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:contextual_news}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:contextual_news}
spring.sql.init.mode=always
app.data.file-path=classpath:data/news_data.json
app.data.bootstrap-enabled=true
app.llm.provider=${APP_LLM_PROVIDER:openai}
app.llm.base-url=${APP_LLM_BASE_URL:https://api.openai.com/v1}
app.llm.model=${APP_LLM_MODEL:gpt-4o-mini}
app.llm.api-key=${APP_LLM_API_KEY:}
app.llm.enabled=${APP_LLM_ENABLED:false}
```

- Set configuration overrides via environment variables (shown above with `${…}`) or a profile-specific `application-*.properties`.
- Keep all API tokens or device keys secret. Never commit them to git.
- To use OpenAI (or any OpenAI-compatible cloud), export `APP_LLM_PROVIDER=openai`, `APP_LLM_BASE_URL=https://api.openai.com/v1`, `APP_LLM_API_KEY=<token>`, and `APP_LLM_ENABLED=true`.

### Enabling a local Ollama model (no API key required)

1. Install [Ollama](https://ollama.com/) and ensure the service is running (`ollama serve`).  
2. Pull a JSON-friendly chat model, for example:
   ```bash
   ollama pull llama3.1
   ```
3. Launch the Spring Boot app with the Ollama profile (recommended):
   ```bash
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
   export PATH="$JAVA_HOME/bin:$PATH"
   SPRING_PROFILES_ACTIVE=ollama ./mvnw spring-boot:run
   ```
   The `application-ollama.properties` profile sets `app.llm.provider=ollama`, `app.llm.base-url=http://localhost:11434`, and `app.llm.model=llama3.1`. You can still override any of them via environment variables (e.g. `APP_LLM_MODEL`).

   Alternatively, set the overrides explicitly without using profiles:
   ```bash
   export APP_LLM_PROVIDER=ollama
   export APP_LLM_BASE_URL=http://localhost:11434
   export APP_LLM_MODEL=llama3.1
   export APP_LLM_ENABLED=true
   ./mvnw spring-boot:run
   ```
4. The application now routes query understanding and article enrichment to Ollama’s `/api/chat` endpoint.  
   The rule-based fallback still kicks in automatically if Ollama is unavailable or returns malformed JSON.

## Startup Instructions

1. Ensure PostgreSQL is running and accessible at `localhost:5432` with the credentials above.
2. (Optional) Create the DB/user if missing:
   ```bash
   createdb contextual_news
   psql -d contextual_news -c "CREATE USER contextual_news WITH PASSWORD 'contextual_news';"
   psql -d contextual_news -c "GRANT ALL PRIVILEGES ON DATABASE contextual_news TO contextual_news;"
   ```
3. From the project root:
   ```bash
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
   export PATH="$JAVA_HOME/bin:$PATH"
   ./mvnw clean package   # optional full build
   ./mvnw spring-boot:run
   ```
4. On first launch, the loader ingests ~2000 articles and logs `Ingested 2000 news articles`.
5. Verify health: `curl http://localhost:8080/actuator/health`

## API Documentation

Single public endpoint: `POST /api/v1/news/query`  
Content-Type: `application/json`

### Request Body

```json
{
  "query": "natural language text describing desired news",
  "userLocation": {
    "latitude": 37.7749,
    "longitude": -122.4194
  },
  "maxResults": 10,
  "radiusKm": 25,
  "scoreThreshold": 0.7
}
```

- `query` (required): free text the LLM uses to derive intents, entities, concepts.
- `userLocation` (optional): enables nearby intent.
- `maxResults` default 10 (hard capped at 50).
- `radiusKm` optional; defaults to 10 km when nearby intent is detected.
- `scoreThreshold` optional; activates score filter when provided or inferred.

### Response Body (excerpt)

```json
{
  "metadata": {
    "intents": ["category", "search"],
    "entities": ["Reuters"],
    "concepts": ["technology", "news", "from", "reuters"],
    "filters": {
      "category": "technology",
      "source": "Reuters",
      "scoreThreshold": null,
      "radiusKm": null,
      "latitude": null,
      "longitude": null,
      "dateFrom": null,
      "dateTo": null
    },
    "llmFallbackUsed": false
  },
  "articles": [
    {
      "id": "52d7b2b7-bb29-4549-a0a7-bd2e19da71cc",
      "title": "Secretive Chinese tech firm trying to recruit ...",
      "sourceName": "Reuters",
      "categories": ["world", "technology"],
      "relevanceScore": 0.58,
      "finalScore": 0.2244,
      "distanceKm": null,
      "matchReason": "category",
      "enrichment": {
        "summary": "Short LLM-generated synopsis …",
        "keyEntities": ["Secretive Chinese", "Report", "Chinese"],
        "whyRelevant": "Matched by category. Highlights: …"
      }
    }
  ]
}
```

- `metadata` captures LLM output and the actual filters in play.
- `articles` sorted by `finalScore`, include enrichment data and optional `distanceKm`.

### Sample cURL Requests

```bash
# Technology category
curl -s -X POST http://localhost:8080/api/v1/news/query \
  -H "Content-Type: application/json" \
  -d '{"query":"Latest technology news","maxResults":3}' | jq

# Source-specific
curl -s -X POST http://localhost:8080/api/v1/news/query \
  -H "Content-Type: application/json" \
  -d '{"query":"news from Reuters","maxResults":2}' | jq

# Score threshold
curl -s -X POST http://localhost:8080/api/v1/news/query \
  -H "Content-Type: application/json" \
  -d '{"query":"high scoring articles","scoreThreshold":0.85,"maxResults":3}' | jq

# Nearby (bonus)
curl -s -X POST http://localhost:8080/api/v1/news/query \
  -H "Content-Type: application/json" \
  -d '{"query":"latest news near my location","userLocation":{"latitude":16.2,"longitude":77.1},"radiusKm":50,"maxResults":3}' | jq
```

## Testing

- Unit + integration tests run via `./mvnw test`
- A Testcontainers-based integration test (`NewsQueryIntegrationTest`) spins up PostgreSQL (requires Docker).
- JaCoCo is configured for coverage reporting during `mvn verify`.

## Deployment Notes

- Dockerfile and docker-compose examples are included (see `docker/` if present) to launch Postgres + app together.
- Enable external LLM usage by supplying a key; otherwise the RuleBased LLM client provides deterministic parsing/enrichment.

## What Was Implemented

1. **Database schema & ingestion**: Flyway-style SQL script creates tables and indexes; bootstrap loader ingests JSON on first run.
2. **LLM parsing and enrichment**: Delegating client calls OpenAI-compatible API or uses fallback; enriched summaries cached via Caffeine.
3. **Retrieval strategies**: category, score, source, search, nearby strategies selectable based on parsed intent.
4. **Ranking**: `ArticleRankingService` mixes relevance score, recency decay, semantic hints, and location proximity.
5. **REST API**: single `/api/v1/news/query` endpoint returning aggregated metadata + article list.
6. **Documentation & samples**: this README, cURL examples, and optional Postman/cURL snippets for manual testing.

## Next Steps / Manual Push

All code and documentation are local. To push to your GitHub repository:

```bash
git add .
git commit -m "Complete contextual news data retrieval system"
git push origin <branch>
```

Provide credentials/personal access token when prompted.


