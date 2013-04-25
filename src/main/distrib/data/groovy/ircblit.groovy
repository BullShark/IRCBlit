//package expectusafterlun.ch.irc;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.JGitUtils;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import java.io.BufferedReader;
import org.slf4j.Logger;

import sun.org.mozilla.javascript.tools.shell.QuitAction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.net.SocketException;

/**
 * Sample Gitblit Post-Receive Hook: ircblit
 *
 * Bound Variables:
 *  gitblit			Gitblit Server	 			com.gitblit.GitBlit
 *  repository		Gitblit Repository			com.gitblit.models.RepositoryModel
 *  receivePack		JGit Receive Pack			org.eclipse.jgit.transport.ReceivePack
 *  user			Gitblit User				com.gitblit.models.UserModel
 *  commands		JGit commands 				Collection<org.eclipse.jgit.transport.ReceiveCommand>
 *	url				Base url for Gitblit		String
 *  logger			Logs messages to Gitblit 	org.slf4j.Logger
 *  clientLogger	Logs messages to Git client	com.gitblit.utils.ClientLogger
 *
 * Accessing Gitblit Custom Fields:
 *   def myCustomField = repository.customFields.myCustomField
 */

// Indicate we have started the script
logger.info("IRCBlit hook triggered by ${user.username} for ${repository.name}");

class IRCBlit {
	// Create variables
	def server;
	def port;
	def chan;
	def nick;
	def received;
	def first;
	def last;
	Socket socket;
	BufferedWriter bWriter;
	BufferedReader bReader;
	def logger;
	Thread receivedT;
	def pollTime;

	/**
	 * 
	 * @param logger
	 */
	IRCBlit(logger) {
		initialize(logger);
		createIRCSocket();
		createIOStreams();

		receivedT = new Thread() {
					def received001 = false;
					
					public void run() {
						//TODO Can we remove the assigning since receiveln() already does that?
						def received001 = false;
						while(( received = recieveln()) != null ) {
							divideTwo();

							if(first.equals("PING")) {
								logger.info("Pinging server: ${last}");
								sendln("PONG " + last);
							}
							if(first.contains("001")) {
								logger.info("Received IRC Code 001");
								received001 = true;
							}
						}
					}
					def boolean receivedWelcomeCode() {
						return received001;
					}
				};

		waitFor001();
		sendNickAndUserMessages();
		joinChannel();
		// Send a test message the chan
		msgChannel(chan, "Hello ${chan}");
		quitAndCloseStreams();
	}

	/**
	 * 
	 * @return
	 */
	def initialize(logger) {
		server = "frequency.windfyre.net";
		port = 6667;
		chan = "#blackhats";
		nick = "GitBlit";
		received = "";
		first = "";
		last = "";
		socket = null;
		bWriter = null;
		bReader = null;
		this.logger = logger;
		pollTime = 500; // Time in ms between checks for 001 welcome message 
	}

	/**
	 * 
	 * @return
	 */
	def createIRCSocket() {
		try {
			socket = new Socket(server, port)
		} catch (IOException ex) {
			logger.info("Failed to connect to ${server} on ${port}");
			socket.close();
			System.exit(-1);
		} catch (UnknownHostException ex) {
			logger.info("Host ${server} not known");
			socket.close();
			System.exit(-1);
		}
	}

	/**
	 * 
	 * @return
	 */
	def createIOStreams() {
		try {
			//	bWriter =
			//			new BufferedWriter(
			//			new OutputStreamWriter(socket.getOutputStream()));

			OutputStream sockOut = socket.getOutputStream();
			OutputStreamWriter osw = new OutputStreamWriter(sockOut);
			bWriter = new BufferedWriter(osw)


			//	bReader = BufferedReader(
			//			new InputStreamReader(socket.getInputStream()));

			InputStream sockIn = socket.getInputStream();
			InputStreamReader isr = new InputStreamReader(sockIn);
			bReader = new BufferedReader(isr);

			logger.info("Set up I/O streams with the server");

		} catch(IOException ex) {
			logger.info("Failed to get I/O streams with the server");
			System.exit(-1);
		}
	}

	def sendNickAndUserMessages() {
		logger.info("Sending bot's nick to the server");

		sendln("Nick ${nick}");
		sendln("USER ${nick} 8 * :IRCBlit Service Hook for GitBlit");
	}

	def joinChannel() {
		// Attempt to join the IRC chan
		sendln("JOIN ${chan}");
	}

	/**
	 * 
	 * @return
	 */
	def divideTwo() {
		try {
			first = received.split(" :", 2)[0];
			last = received.split(" :", 2)[1];
		} catch(ArrayIndexOutOfBoundsException ex) {
			first = "";
			last = "";
		}
	}

	/**
	 * Wait for the server to respond with 001
	 * Before attempting to join a chan
	 * @return
	 */
	def waitFor001() {

		//		while(( received = recieveln()) != null ) {
		//			divideTwo();
		//
		//			if(first.equals("PING")) {
		//				logger.info("Pinging server: ${last}");
		//				sendln("PONG " + last);
		//			}
		//
		//			if(first.contains("001")) {
		//				logger.info("Received IRC Code 001");
		//				break;
		//			}
		//		}
		
		//TODO Add timer that breaks this loop after X seconds if the message wasn't received?
		while(true) {
			if(receivedT.receivedWelcomeCode()) {
				break;
			}
			Thread.sleep(pollTime);
		}

	}

	/**
	 * 
	 * @param line
	 * @return
	 */
	def sendln(line) {
		try {
			if(socket == null || bWriter == null) {
				throw new SocketException("Socket or ouptut stream is null");
			}
			bWriter.write(line);
			bWriter.newLine();
			bWriter.flush();
			logger.info("Sent:\t${line}");
		} catch (SocketException ex) {
			logger.info("No connection to the server, exiting");
			quitAndCloseStreams(false);
		}
	}

	/**
	 * TODO Set return type to void and remove all received = receiveln() code
	 * @return
	 */
	def String recieveln() {
		try {
			received = bReader.readLine();
			logger.info("Received:\t${received}");
			return received;
		} catch (IOException ex) {
			logger.info("Failed to get I/O streams with the server");
			return null;
		}
	}

	/**
	 * 
	 * @param chan
	 * @param msg
	 * @return
	 */
	def msgChannel(chan, msg) {
		if( !sendln("PRIVMSG " + chan + " :" + msg) ) {
			logger.info("Failed to send message: \"${msg}\" to chan ${chan}");
		}
		//TODO Redundant?
		logger.info("Sent:\tmessage: \"${msg}\" to chan ${chan}");
	}

	/**
	 * 
	 * @return
	 */
	def quitAndCloseStreams(sendQuit) {
		// Leave IRC
		if(sendQuit) {
			sendln("QUIT");
		}

		// Kill Ping Thread TODO

		// Close I/O
		bWriter.close();
		bReader.clone();
		socket.close();
		logger.info("Closed all I/O streams");
	}

	/**
	 * Wrapper class to quitAndCloseStreams(boolean sendQuit)
	 * Defaults to sending QUIT to the IRC server
	 * @return
	 */
	def quitAndCloseStreams() {
		quitAndCloseStreams(true);
	}
}

new IRCBlit(logger);

//TODO Get Info by Accessing Gitblit Custom Fields
//TODO And if the fields do not exist, use some defaults
//TODO Make a separate thread for responding to pings?
//XXX What does the 0 and * mean? What is the difference between 0 and 8?