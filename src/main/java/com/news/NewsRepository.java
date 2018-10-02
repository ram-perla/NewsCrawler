package com.news;

import java.util.List;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import com.news.model.Feed;

@Repository
public interface NewsRepository extends ElasticsearchRepository<Feed, String> {
	List<Feed> findBySourceEn(String sourceEn);
}
