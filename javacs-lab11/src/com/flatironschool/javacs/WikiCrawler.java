
package com.flatironschool.javacs;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import redis.clients.jedis.Jedis;


public class WikiCrawler {
	// keeps track of where we started
	private final String source;
	
	// the index where the results go
	private JedisIndex index;
	
	// queue of URLs to be indexed
	private Queue<String> queue = new LinkedList<String>();
	
	// fetcher used to get pages from Wikipedia
	final static WikiFetcher wf = new WikiFetcher();
	
	private boolean testing = false;

	/**
	 * Constructor.
	 * 
	 * @param source
	 * @param index
	 */

	public WikiCrawler(String source, JedisIndex index) {
		this.source = source;
		this.index = index;
		queue.offer(source);
	}

	/**
	 * Returns the number of URLs in the queue.
	 * 
	 * @return
	 */


	public int queueSize() {
		return queue.size();	
	}
	
	public String crawl(boolean testing) throws IOException {
		// get url from queue
		String url = queue.poll(); // receives element (head) and removes from queue (FIFO)
		// if testing is false and url is indexed --> return null
		if (!testing && index.isIndexed(url)) 
		{
			// do not index url again and return null
			return null;
		}
		// testing is true
		Elements pageContent;
		if (testing) {
			// read cached contents of page 
			//System.out.println("testing true");
			pageContent = wf.readWikipedia(url);
		}
		// testing is false && url is not indexed
		else {
			// read current contents of page
			//System.out.println("testing false");
			pageContent = wf.fetchWikipedia(url);
		}
		// index page
		index.indexPage(url, pageContent);
		// find all internal links and add them to queue
		queueInternalLinks(pageContent);
		// return URL of the page it indexed
		return url;
	}
	
	/**
	 * Parses paragraphs and adds internal links to the queue.
	 * Links are in mw-context and begin with href=/wiki/
	 * 
	 * @param paragraphs
	 */
	// NOTE: absence of access level modifier means package-level

	public void queueInternalLinks(Elements paragraphs) {
		//parse paragraph
		for (Element paragraph: paragraphs) { // go through each individual paragraph in paragraphs (the entire body of page)
			Elements paras = paragraph.select("a"); // find links (a tag with href attributes)
			for (Element text: paras) { // go through each TextNode in paragraph
				String relativeURL = text.attr("href");
				
				if (relativeURL.startsWith("/wiki/")) {
					// get absoulate URL 
					String absoluteURL = text.absUrl("abs:href");
					//if (testing) {
					//queue.offer(relativeURL);
					//} 
					//else {

					queue.offer(absoluteURL);
						//}
					//System.out.println("relativeURL is: " + relativeURL);
					
					//System.out.println("absoluteURL is: " + absoluteURL);
					//queue.offer(relativeURL);//queue.offer(absoluteURL);
				}
			}
		}
	}

	public static void main(String[] args) throws IOException {
		
		// make a WikiCrawler
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis); 
		String source = "https://en.wikipedia.org/wiki/Java_(programming_language)";
		//String source = "https://en.wikipedia.org/w/index.php?title=Java_(programming_language)&oldid=715811047";
		WikiCrawler wc = new WikiCrawler(source, index);
		
		// for testing purposes, load up the queue
		Elements paragraphs = wf.fetchWikipedia(source);
		wc.queueInternalLinks(paragraphs);

		// loop until we index a new page
		String res;
		do {
			res = wc.crawl(false);

            // REMOVE THIS BREAK STATEMENT WHEN crawl() IS WORKING
            break;
		} while (res == null);
		
		Map<String, Integer> map = index.getCounts("the");
		for (Entry<String, Integer> entry: map.entrySet()) {
			System.out.println(entry);
		}
	}
}

