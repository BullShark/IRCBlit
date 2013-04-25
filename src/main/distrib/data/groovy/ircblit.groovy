//package expectusafterlun.ch.irc;

//TODO Organize imports
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
	//TODO When this script is working, change all to def and test if it still works
	Socket socket;
	BufferedWriter bWriter;
	BufferedReader bReader;
	def logger;
	Thread receivedT;
	def pollTime;
	def received001;
	def joined;

	/**
	 * 
	 * @param logger
	 */
	IRCBlit(logger) {
		initialize(logger);
		createIRCSocket();
		createIOStreams();
		createReceivedThread()
		sendNickAndUserMessages();
		waitFor001();
		joinChannel();
		waitForChannelJoined();
		// Send a test message to the chan
		msgChannel(chan, "Hello ${chan}");
		// Send a test notice to the chan
		noticeChannel(chan, "c|c is a n00b!")
		quitAndCloseStreams();
	}

	/**
	 * Gives all the global variables default values
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
		pollTime = 500; // Time in ms between checks for server messages
		received001 = false;
		joined = false;
	}

	/**
	 * Attempts to create the socket connection to the IRC server on a specified port
	 * @return
	 */
	def createIRCSocket() {
		try {
			//TODO Support SSL
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
	 * Attempts to get streams for reading and writing to the server connection
	 * @return
	 */
	def createIOStreams() {
		try {
			OutputStream sockOut = socket.getOutputStream();
			OutputStreamWriter osw = new OutputStreamWriter(sockOut);
			bWriter = new BufferedWriter(osw)

			InputStream sockIn = socket.getInputStream();
			InputStreamReader isr = new InputStreamReader(sockIn);
			bReader = new BufferedReader(isr);

			logger.info("Set up I/O streams with the server");
		} catch(IOException ex) {
			logger.info("Failed to get I/O streams with the server");
			System.exit(-1);
		}
	}

	/**
	 * Create a thread for handling received messages from the server
	 * And start the thread
	 * @return
	 */
	def createReceivedThread() {
		receivedT = new Thread() {
					public void run() {
						logger.info("Thread started: ${receivedT}");
						//TODO Can we remove the assigning since receiveln() already does that?
						while(( received = recieveln()) != null ) {
							divideTwo();

							if(first.equals("PING")) {
								sendln("PONG " + last);
							} else if(first.contains("001")) {
								received001 = true;
							} else if(received.contains("JOIN :${chan}")) {
								joined = true;
							}
						}
					}
				};
		receivedT.start();
	}

	/**
	 * Sends the NICK line with the nick to be used
	 * And the USER line with identd and real name
	 * @return
	 */
	def sendNickAndUserMessages() {
		logger.info("Sending bot's nick to the server");

		sendln("Nick ${nick}");
		sendln("USER ${nick} 8 * :IRCBlit Service Hook for GitBlit");
	}

	/**
	 * Attempt to join the IRC chan
	 * @return
	 */
	def joinChannel() {
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
		while(true) {
			if(received001) {
				logger.info("Breaking the wait for 001 loop");
				break;
			}
			Thread.sleep(pollTime);
		}
	}

	/**
	 * Waits for the channel to be joined
	 * Useful to avoid sending messages to a channel before it's joined
	 * @return
	 */
	def waitForChannelJoined() {
		while(true) {
			if(joined) {
				logger.info("Channel joined: ${chan}");
				break;
			}
			Thread.sleep(pollTime);
		}
	}

	/**
	 * Sends a raw line to the irc server
	 * @param line The line to send to the server
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
	 * Sends a message to a channel
	 * @param chan Channel to send the message to
	 * @param msg Message to be sent to the channel
	 * @return
	 */
	def msgChannel(chan, msg) {
		if( !sendln("PRIVMSG " + chan + " :" + msg) ) {
			logger.info("Failed to send message: \"${msg}\" to chan ${chan}");
		}
	}
	

	/**
	 * Sends a notice to a channel
	 * @param chan Channel to send the notice to
	 * @param msg Notice to be sent to the channel
	 * @return
	 */
	noticeChannel(chan, msg) {
		if( !sendln("NOTICE " + chan + " :" + msg) ) {
			logger.info("Failed to send notice: \"${msg}\" to chan ${chan}");
		}
	}
	
	/**
	 * 
	 * @param sendQuit
	 * @return
	 */
	def quitAndCloseStreams(sendQuit) {
		// Leave IRC
		if(sendQuit) {
			sendln("QUIT :GitBlit Service Hook by BullShark");
		}

		// TODO Kill Received Thread
		//receivedT

		// Close I/O
		bWriter.close();
		bReader.close();
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