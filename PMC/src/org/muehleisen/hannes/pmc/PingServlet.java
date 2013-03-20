package org.muehleisen.hannes.pmc;

import static com.google.appengine.api.urlfetch.FetchOptions.Builder.withDeadline;

import java.io.IOException;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;

@SuppressWarnings("serial")
public class PingServlet extends HttpServlet {
	private static final long TIME_LIMIT_SECS = 300;
	private static Logger log = Logger.getLogger(PingServlet.class.getName());
	private DatastoreService datastore = DatastoreServiceFactory
			.getDatastoreService();

	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		log.info("Starting ping run...");

		// load all activated entities

		Query q = new Query("Job").setFilter(new FilterPredicate("enabled",
				FilterOperator.EQUAL, true));
		URLFetchService fetcher = URLFetchServiceFactory.getURLFetchService();
		Map<Entity, Future<HTTPResponse>> asyncResponses = new HashMap<Entity, Future<HTTPResponse>>();
		PreparedQuery pq = datastore.prepare(q);

		// fire off async requests
		for (Entity job : pq.asIterable()) {
			URL url = new URL((String) job.getProperty("url"));
			long intervalSeconds = (Long) job.getProperty("intervalSeconds");
			Calendar timeLimit = Calendar.getInstance();
			timeLimit.add(Calendar.SECOND, (int) (-1 * intervalSeconds));

			if (!job.hasProperty("lastResponseTime")
					|| ((Date) job.getProperty("lastResponseTime"))
							.before(timeLimit.getTime())) {

				Future<HTTPResponse> responseFuture = null;
				if (url.getUserInfo() != null
						&& url.getUserInfo().contains(":")) {
					String authorizationString = "Basic "
							+ Base64.encodeBase64String(url.getUserInfo()
									.getBytes());

					if (url.getPort() != -1) {
						url = new URL(url.getProtocol(), url.getHost(),
								url.getPort(), url.getFile());
					} else {
						url = new URL(url.getProtocol(), url.getHost(),
								url.getFile());
					}

					log.info("Requesting " + url + " with basic authentication");
					HTTPRequest fetchRequest = new HTTPRequest(url,
							HTTPMethod.GET, withDeadline(TIME_LIMIT_SECS));
					fetchRequest.addHeader(new HTTPHeader("Authorization",
							authorizationString));
					responseFuture = fetcher.fetchAsync(fetchRequest);
				} else {
					log.info("Requesting " + url);
					HTTPRequest fetchRequest = new HTTPRequest(url,
							HTTPMethod.GET, withDeadline(TIME_LIMIT_SECS));
					responseFuture = fetcher.fetchAsync(fetchRequest);
				}

				asyncResponses.put(job, responseFuture);
			}
		}

		// now poll on futures and store result
		boolean pending = true;
		long start = System.currentTimeMillis();

		do {
			pending = false;
			for (Entry<Entity, Future<HTTPResponse>> jobE : asyncResponses
					.entrySet()) {
				if (jobE.getValue().isDone()) {
					try {
						HTTPResponse response = jobE.getValue().get();
						// store the response in the entity and store it
						jobE.getKey().setProperty("lastResponseCode",
								response.getResponseCode());
						// TODO: send mail on failure code?
						jobE.getKey().setProperty("lastResponse",
								new Text(new String(response.getContent())));
						jobE.getKey().setProperty("lastResponseTime",
								Calendar.getInstance().getTime());
						// todo: store timestamp
						datastore.put(jobE.getKey());
					} catch (Exception e) {
						log.warning(e.toString());
					}
				} else {
					pending = true;
				}
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// whatever
			}
		} while (pending
				&& (System.currentTimeMillis() - start) / 1000 < TIME_LIMIT_SECS);
		// if any requests are still pending, cancel them!
		for (Entry<Entity, Future<HTTPResponse>> jobE : asyncResponses
				.entrySet()) {
			if (!jobE.getValue().isDone()) {
				jobE.getValue().cancel(true);
			}
		}
	}
}
