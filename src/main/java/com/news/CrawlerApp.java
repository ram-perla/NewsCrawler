package com.news;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;

import javax.annotation.Resource;

import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.elasticsearch.core.query.GetQuery;

import com.news.model.Feed;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

@SpringBootApplication(scanBasePackages = { "com.news" })
public class CrawlerApp implements CommandLineRunner {

	@Resource
	private NewsRepository newsRepository;

	@Value("${file.path}")
	private String filePath;

	public static void main(String[] args) {
		SpringApplication.run(CrawlerApp.class, args);
	}

	@Override
	public void run(String... args) throws Exception {

		String url = "https://csnews.com/rss";
		URL feedUrl = new URL(url);

		SyndFeedInput feedInput = new SyndFeedInput();
		SyndFeed feed = null;
		String xmlStr = "";
		try {
			feedInput.setXmlHealerOn(true);
			feed = feedInput.build(new XmlReader(feedUrl));
			xmlStr = feedInput.toString();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(xmlStr);
		}

		if (feed != null) {
			List<SyndEntry> entries = feed.getEntries();
			Iterator<SyndEntry> it = entries.listIterator();
			while (it.hasNext()) {

				Feed newsFeed = new Feed();
				SyndEntry entry = it.next();
				String link = entry.getLink();
				String urlEn = URLEncoder.encode(link, "UTF-8");
				if (!isExisting(urlEn)) {
					Document doc = Jsoup.connect(link).userAgent("Mozilla").timeout(0).get();
					String titleStr = doc.title();
					String[] titles = titleStr.split("[|]");
					if (titles != null && titles.length == 2) {
						newsFeed.setTitle(titles[0]);
						newsFeed.setSubTitle(titles[1]);
					} else {
						newsFeed.setTitle(titleStr);
						newsFeed.setSubTitle(titleStr);
					}
					String uid = UUID.randomUUID().toString();
					newsFeed.setContent(doc.getElementsByTag("p").text());
					newsFeed.setSource(link);
					newsFeed.setSourceEn(urlEn);
					newsFeed.setPubDate(entry.getPublishedDate());
					newsFeed.setTags(getTags(doc));
					newsFeed.setImage(getImage(entry.getDescription().getValue(), uid));
					newsFeed.setId(uid);
					newsFeed.setHost(getHostName(link));
					newsRepository.save(newsFeed);
				}
			}
		}
	}

	private String getHostName(String link) throws URISyntaxException {
		String hostname = new URI(link).getHost();

		if (hostname != null && hostname.startsWith("www.")) {
			hostname = hostname.substring(4);
		}

		int idx = hostname.lastIndexOf(".");
		hostname = hostname.substring(0, idx);
		return hostname;
	}

	private boolean isExisting(String link) {
		boolean flag = false;
		List<Feed> news = newsRepository.findBySourceEn(link);
		if (!news.isEmpty()) {
			flag = true;
		}
		return flag;
	}

	private String getImage(String value, String uid) {
		String urlStr = "";

		try {
			Document doc = Jsoup.parse(value);
			urlStr = doc.getElementsByTag("img").attr("src");
			int len = urlStr.indexOf("itok");
			urlStr = urlStr.substring(0, len - 1);
		} catch (Exception e) {
			System.out.println("Exception in getting Image");
		}
		return urlStr;
	}

	private String getTags(Document doc) {
		Element element = doc.getElementById("block-relatedtopicsbeneathcontent");
		try {
			if (element != null) {
				List<Element> ulElements = element.select("ul").attr("class", "tags");
				if (!ulElements.isEmpty()) {
					ListIterator<Element> tags = ulElements.get(0).select("li").listIterator();
					List<String> tagsList = new ArrayList<String>();
					while (tags.hasNext()) {
						tagsList.add(tags.next().text());
					}
					return String.join(", ", tagsList);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Excepting in getting Tags");
		}
		return "";
	}
}
