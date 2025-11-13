DROP TABLE IF EXISTS article_category;
DROP TABLE IF EXISTS news_article;

CREATE TABLE news_article (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    url TEXT,
    publication_date TIMESTAMP WITH TIME ZONE,
    source_name TEXT,
    relevance_score DOUBLE PRECISION,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    tsv tsvector GENERATED ALWAYS AS (
        to_tsvector('english', coalesce(title, '') || ' ' || coalesce(description, ''))
    ) STORED,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE article_category (
    article_id UUID NOT NULL REFERENCES news_article(id) ON DELETE CASCADE,
    category VARCHAR(80) NOT NULL,
    PRIMARY KEY (article_id, category)
);

CREATE INDEX idx_news_article_publication_date ON news_article (publication_date DESC NULLS LAST);
CREATE INDEX idx_news_article_relevance ON news_article (relevance_score DESC NULLS LAST);
CREATE INDEX idx_news_article_source ON news_article (source_name);
CREATE INDEX idx_news_article_tsv ON news_article USING GIN (tsv);
CREATE INDEX idx_article_category_category ON article_category (category);

