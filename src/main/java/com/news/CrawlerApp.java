package com.news;

import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

import javax.annotation.Resource;

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
				if (!isExisting(link)) {
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
					newsFeed.setPubDate(entry.getPublishedDate());
					newsFeed.setTags(getTags(doc));
					newsFeed.setImage(getImage(entry.getDescription().getValue()));

					newsRepository.save(newsFeed);
				}
			}
		}
	}

	private boolean isExisting(String link) {
		boolean flag = false;
		Optional<Feed> news = newsRepository.findById(link);
		if (news.isPresent()) {
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
