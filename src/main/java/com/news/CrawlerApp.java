package com.news;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;

import javax.annotation.Resource;
import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.news.model.Feed;
import com.rometools.rome.feed.synd.SyndCategory;
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

	@Value("#{'${feed.urls}'.split(',')}")
	private List<String> feedUrls;
	private static final Logger LOGGER = LogManager.getLogger(CrawlerApp.class);

	public static void main(String[] args) {
		long startTime = System.currentTimeMillis();
		SpringApplication.run(CrawlerApp.class, args);
		long endTime = System.currentTimeMillis();
		LOGGER.error("Time taken for indexing in (Seconds) is" + (endTime - startTime) / 1000);
	}

	@Override
	public void run(String... args) throws Exception {
		for (Iterator<String> iterator = feedUrls.iterator(); iterator.hasNext();) {
			String url = iterator.next();
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
						newsFeed.setUrl(link);
						newsFeed.setUrlEn(urlEn);
						newsFeed.setPubDate(entry.getPublishedDate());
						List<String> tags = getTags(entry.getCategories());
						if (tags.isEmpty()) {
							tags = getTags(doc);
						}
						if (tags != null && !tags.isEmpty()) {
							newsFeed.setTags(tags);
						}
						newsFeed.setImage(getImage(entry.getDescription().getValue(), uid));
						newsFeed.setId(uid);
						newsFeed.setSource(getHostName(link));
						newsRepository.save(newsFeed);
					}
				}
			}

		}
	}

	private List<String> getTags(List<SyndCategory> categories) {
		List<String> list = new ArrayList<>();
		for (Iterator<SyndCategory> iterator = categories.iterator(); iterator.hasNext();) {
			list.add(iterator.next().getName());
		}
		return list;
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
		List<Feed> news = newsRepository.findByUrlEn(link);
		if (!news.isEmpty()) {
			flag = true;
		}
		return flag;
	}

	private String getImage(String value, String uid) {
		String fileName = "noimage.png";

		try {
			Document doc = Jsoup.parse(value);
			String urlStr = doc.getElementsByTag("img").attr("src");

			URL url = new URL(urlStr);
			URLConnection urlConn = url.openConnection();
			urlConn.addRequestProperty("User-Agent",
					"Mozilla/5.0 (Windows NT 6.1; WOW64; rv:25.0) Gecko/20100101 Firefox/25.0");
			InputStream is = urlConn.getInputStream();
			BufferedImage imBuff = ImageIO.read(is);
			fileName = uid + ".png";
			ImageIO.write(imBuff, "png", new File(filePath + fileName));
		} catch (Exception e) {
			fileName = "noimage.png";
			System.out.println("Exception in getting Image");
		}
		return fileName;
	}

	private List<String> getTags(Document doc) {
		Element element = doc.getElementById("block-relatedtopicsbeneathcontent");
		List<String> tagsList = null;
		try {
			if (element != null) {
				List<Element> ulElements = element.select("ul").attr("class", "tags");
				if (!ulElements.isEmpty()) {
					ListIterator<Element> tags = ulElements.get(0).select("li").listIterator();
					tagsList = new ArrayList<String>();
					while (tags.hasNext()) {
						tagsList.add(tags.next().text());
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Excepting in getting Tags");
		}
		return tagsList;
	}
}
