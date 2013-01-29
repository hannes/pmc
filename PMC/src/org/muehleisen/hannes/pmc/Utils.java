package org.muehleisen.hannes.pmc;

import java.net.URL;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;

public class Utils {
	public static final String HOSTNAME = "poor-mans-cron.appspot.com";

	public static final String FROM = "noreply@poor-mans-cron.appspotmail.com";

	private static DatastoreService datastore = DatastoreServiceFactory
			.getDatastoreService();

	public static boolean sendMail(String to, String content) {
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);

		try {
			MimeMessage msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress(FROM));
			msg.addRecipient(MimeMessage.RecipientType.TO, new InternetAddress(
					to));
			msg.setSubject("Message from \"Poor Man's cron service\"");
			msg.setText(content);
			Transport.send(msg);

		} catch (Exception e) {
			return false;
		}
		return true;
	}

	public static String createJob(URL url, int intervalSeconds,
			InternetAddress adminEmail) {
		Entity job = new Entity("Job");
		job.setProperty("url", url);
		job.setProperty("intervalSeconds", intervalSeconds);
		job.setProperty("adminEmail", adminEmail);
		String id = UUID.randomUUID().toString();
		job.setProperty("id", id);

		job.setProperty("lastRun", "never");

		datastore.put(job);
		return id;
	}

	public static boolean isValidEmailAddress(String email) {
		try {
			InternetAddress emailAddr = new InternetAddress(email);
			emailAddr.validate();
		} catch (Exception ex) {
			return false;
		}
		return true;
	}

	public static boolean isValidUrl(String parameter) {
		try {
			new URL(parameter);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	public static boolean isValidInterval(String parameter,
			Set<Integer> intervals) {
		int i = -1;
		try {
			i = Integer.parseInt(parameter);
		} catch (Exception e) {
			return false;
		}
		return intervals.contains(i);
	}

	public static boolean isValidId(String enableid) {
		try {
			UUID.fromString(enableid);
		} catch (Exception e) {
			return false;
		}
		return true;
	}
}
