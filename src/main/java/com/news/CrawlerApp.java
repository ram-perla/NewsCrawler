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

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.news.model.Feed;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

@SpringBootApplication(scanBasePackages = { "com.news" })
public class CrawlerApp implements CommandLineRunner {

	@Resource
	private NewsRepository newsRepository;

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
//				if (!isExisting(urlEn)) {
					System.out.println("**********************");
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
					newsFeed.setContent(doc.getElementsByTag("p").text());
					newsFeed.setSource(link);
					newsFeed.setSourceEn(urlEn);
					newsFeed.setPubDate(entry.getPublishedDate());
					newsFeed.setTags(getTags(doc));
					newsFeed.setImage(getImage(entry.getDescription().getValue()));
					newsFeed.setId(UUID.randomUUID().toString());
					newsFeed.setHost(getHostName(link));
					newsRepository.save(newsFeed);
//				}
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
		QueryBuilder query = QueryBuilders.queryStringQuery("sourceEn:'" + link + "'");
		Iterable<Feed> news = newsRepository.search(query);
		if (news.iterator().hasNext()) {
			flag = true;
		}

		return flag;
	}

	private String getImage(String value) {
		String url = "";

		try {
			Document doc = Jsoup.parse(value);
			url = doc.getElementsByTag("img").attr("src");
			int len = url.indexOf("itok");
			url = url.substring(0, len - 1);
		} catch (Exception e) {
			System.out.println("Excepting in getting Image");
		}

		return url;
	}

	private String getTags(Document doc) {
		Element element = doc.getElementById("block-relatedtopicsbeneathcontent");
		try {
			if (element != null) {
				ListIterator<Element> tags = element.select("ul").attr("class", "tags").listIterator();

				List<String> tagsList = new ArrayList<String>();
				while (tags.hasNext()) {
					tagsList.add(tags.next().text());
				}
				return String.join(",", tagsList);
			}
		} catch (Exception e) {
			System.out.println("Excepting in getting Tags");
		}
		return "";
	}
}
