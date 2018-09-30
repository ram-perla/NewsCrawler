package com.news;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import com.news.model.Feed;

@Repository
public interface NewsRepository extends ElasticsearchRepository<Feed, String> {
}
