package org.muehleisen.hannes.pmc;

import static com.google.appengine.api.datastore.FetchOptions.Builder.withLimit;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;

@SuppressWarnings("serial")
public class PMCServlet extends HttpServlet {
	private static Logger log = Logger.getLogger(PMCServlet.class.getName());

	private DatastoreService datastore = DatastoreServiceFactory
			.getDatastoreService();

	private enum Action {
		submit, enable, view, delete
	};

	Set<Integer> intervals = new HashSet<Integer>() {
		{
			add(3600);
			add(86400);
			add(604800);
			add(2419200);
		}
	};

	// hourly, daily, weekly, monthly

	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		Action ac = null;
		try {
			ac = Action.valueOf(req.getParameter("action"));
		} catch (Exception e) {
			resp.sendError(
					400,
					"'action' parameter required, can be "
							+ Arrays.toString(Action.values()));
			return;
		}
		// okay, we now know what we want to do
		switch (ac) {
		case submit:
			// 1st: validate inputs
			String email = req.getParameter("email");
			String url = req.getParameter("url");
			String intervalStr = req.getParameter("interval");

			if (!Utils.isValidEmailAddress(email)) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
						"invalid 'email' parameter");
				return;
			}
			if (!Utils.isValidUrl(url)) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
						"invalid 'url' parameter");
				return;
			}
			if (!Utils.isValidInterval(intervalStr, intervals)) {
				resp.sendError(
						HttpServletResponse.SC_BAD_REQUEST,
						"invalid 'interval' parameter, can be "
								+ intervals.toString());
				return;
			}

			// 2nd: put our task in the data store, but not enable it yet
			Entity job = new Entity("Job");
			job.setProperty("url", url);
			job.setProperty("intervalSeconds", Integer.parseInt(intervalStr));
			job.setProperty("email", email);
			job.setProperty("enabled", false);
			String id = UUID.randomUUID().toString();
			job.setProperty("id", id);

			datastore.put(job);

			log.info("New request: url=" + url + ",email=" + email
					+ ",interval=" + intervalStr + ",id=" + id);

			// 3rd: send email to admin asking to confirm
			String confirmUrl = "http://" + Utils.HOSTNAME
					+ "/pmc?action=enable&id=" + id;

			Utils.sendMail(
					email,
					"Hello.\n\nThis is the \"Poor Man's cron service\" at "
							+ Utils.HOSTNAME
							+ ".\nI have been asked (probably by you) to send a request to the URL "
							+ url
							+ " every "
							+ intervalStr
							+ " seconds.\nIf you think that's okay, please visit the following URL: "
							+ confirmUrl + "\n\nBest, PMC");

			// 4th: display some message on response stream.
			resp.getWriter()
					.write("Okay, I have received your job. Please check your eMail for further instructions. ");
			resp.getWriter().close();
			break;
		case enable:
			String enableid = req.getParameter("id");
			// 1st: validate inputs

			if (!Utils.isValidId(enableid)) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
						"invalid 'id' parameter, should be a UUID");
				return;
			}

			// 2nd: look if we know a job with that id
			List<Entity> jobs = datastore.prepare(
					new Query("Job").setFilter(new FilterPredicate("id",
							FilterOperator.EQUAL, enableid))).asList(
					withLimit(1));
			if (jobs.size() < 1) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
						"invalid 'id' parameter");
				return;
			}
			// 3rd: set job to be enabled
			Entity enableJob = jobs.get(0);
			enableJob.setProperty("enabled", true);
			datastore.put(enableJob);

			log.info("Enabled: id=" + enableid);

			// 4th: send confirmation mail
			String deleteLink = "http://" + Utils.HOSTNAME
					+ "/pmc?action=delete&id=" + enableid;

			String viewLink = "http://" + Utils.HOSTNAME
					+ "/pmc?action=view&id=" + enableid;
			Utils.sendMail(
					(String) enableJob.getProperty("email"),
					"Hello again!\n\nThis is the \"Poor Man's cron service\" at "
							+ Utils.HOSTNAME
							+ ".\nI have enabled your job to send a request to the URL "
							+ enableJob.getProperty("url")
							+ " every "
							+ enableJob.getProperty("intervalSeconds")
							+ " seconds.\nIf you want to see how your job is doing, and what your URL has to say, visit this page: "
							+ viewLink
							+ "\n\nIf you want to cancel this job again, please click on the following link: "
							+ deleteLink
							+ "\n\nIt would be advisable to keep this message, as it would otherwise be hard to stop me from accessing your URL.\n\nBest, PMC");

			// 5th: display some message on response stream.
			resp.getWriter()
					.write("Okay, I have enabled your job. Please check your eMail for further instructions. ");
			resp.getWriter().close();
			break;
		case view:
			// TODO: implement me, pleaaase
			resp.getWriter().write("Not Implemented Yet.");
			resp.getWriter().close();
			break;

		case delete:
			String deleteid = req.getParameter("id");
			// 1st: validate inputs

			if (!Utils.isValidId(deleteid)) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
						"invalid 'id' parameter, should be a UUID");
				return;
			}

			// 2nd: look if we know a job with that id
			List<Entity> jobsd = datastore.prepare(
					new Query("Job").setFilter(new FilterPredicate("id",
							FilterOperator.EQUAL, deleteid))).asList(
					withLimit(1));
			if (jobsd.size() < 1) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
						"invalid 'id' parameter");
				return;
			}

			// 3rd: delete job
			datastore.delete(jobsd.get(0).getKey());

			log.info("Deleted: id=" + deleteid);

			// 4th: display some message on response stream.
			resp.getWriter().write("Okay, I have deleted your job. Bye! ");
			resp.getWriter().close();
			break;
		}
	}
}
