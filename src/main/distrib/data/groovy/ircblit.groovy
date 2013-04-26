/*
 * Copyright 2013 Christopher Lemire
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
	// Create global variables
	def server;
	def port;
	def chan;
	def nick;
	def received;
	def first;
	def last;
	def socket;
	def bWriter;
	def bReader;
	def logger;
	def receivedT;
	def pollTime;
	def received001;
	def joined;

	/**
	 * The constructor calls many helper methods
	 * From setting up the irc connection to closing the connection
	 * @param logger Used for logging info messages to Apache Tomcat's server logs
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
		gitBlitChannel();
		// Send a test message to the chan
		//		msgChannel(chan, ".wr");
		// Send a test notice to the chan
		noticeChannel(chan, "Hello ${chan}");
		gitBlitChannel();
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
		//identd = ""
		//realName = ""
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
		//quitMsg = "GitBlit Service Hook by BullShark"
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
						while(receiveln()) {
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
	 * 
	 */
	def gitBlitChannel() {
		/*
		 * Produce similar messages to these here in this method:
		 * 
		 --> | GitHubbed (GitHubbed@protectedhost-8DD5CCAF.rs.github.com) has  
		 | joined #blackhats                                               
		 -- | Mode #blackhats [+v GitHubbed] by BHBot                         
		 -- | Notice(GitHubbed): [JRobo] BullShark pushed 2 new commits to    
		 | master: http://git.io/JthFbQ                                    
		 -- | Notice(GitHubbed): JRobo/master b2ca398 BullShark: lol          
		 -- | Notice(GitHubbed): JRobo/master 919dab3 BullShark: Attempting to
		 | fix bug preventing wr not working and failure to respond to ping
		 | while getUsers() is being called                                
		 <-- | GitHubbed (GitHubbed@protectedhost-8DD5CCAF.rs.github.com) has  
		 | left #blackhats
		 *                                                 
		 */

	}

	/**
	 * Sends a raw line to the irc server
	 * @param line The line to send to the server
	 * @return
	 */
	def boolean sendln(line) {
		def sent = false;
		try {
			if(socket == null || bWriter == null) {
				throw new SocketException("Socket or ouptut stream is null");
			}
			bWriter.write(line);
			bWriter.newLine();
			bWriter.flush();
			logger.info("Sent:\t${line}");
			return sent;
		} catch (SocketException ex) {
			logger.info("No connection to the server, exiting");
			quitAndCloseStreams(false);
			return sent;
		}
	}

	/**
	 * Attempt to receive a line from the server connection
	 * @return True if a line was received, false otherwise
	 */
	def boolean receiveln() {
		try {
			received = bReader.readLine();
			logger.info("Received:\t${received}");
			if(received == null) {
				return false;
			} else {
				return true;
			}
		} catch (IOException ex) {
			logger.info("Failed to get I/O streams with the server");
			return false;
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
	def noticeChannel(chan, msg) {
		if( !sendln("NOTICE " + chan + " :" + msg) ) {
			logger.info("Failed to send notice: \"${msg}\" to chan ${chan}");
		}
	}

	/**
	 * Closes all the I/O streams,
	 * Stops the received thread if it is still running
	 * And optionall sends a QUIT message to the server
	 * Optionally because the streams might already be null and writing to them would fail
	 * @param sendQuit Whether to send QUIT to servers input connection stream
	 * @return
	 */
	def quitAndCloseStreams(sendQuit) {
		// Leave IRC
		if(sendQuit) {
			sendln("QUIT");
		}

		//TODO Kill Received Thread
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