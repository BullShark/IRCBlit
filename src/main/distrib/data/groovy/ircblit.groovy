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

import java.text.SimpleDateFormat

import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.ReceiveCommand

import com.gitblit.Keys
import com.gitblit.utils.JGitUtils

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
	def debug;
	def quitMsg;
	def commands;
	def repository;
	def gitblit;
	def url;
	def r;
	def user;
	def sendDelay;

	/**
	 * The constructor calls many helper methods
	 * From setting up the irc connection to closing the connection
	 * @param logger Used for logging info messages to Apache Tomcat's server logs
	 */
	IRCBlit(logger, debug, commands, repository, gitblit, url, r, user) {
		initialize(logger, debug, commands, repository, gitblit, url, r, user);
		if(!createIRCSocket()) {
			return;
		}
		if(!createIOStreams()) {
			return;
		}
		createReceivedThread()
		sendNickAndUserMessages();
		waitFor001();
		joinChannel();
		waitForChannelJoined();
		gitBlitChannel();
		quitAndCloseStreams();
	}

	/**
	 * Gives all the global variables default values
	 * @return
	 */
	def initialize(logger, debug, commands, repository, gitblit, url, r, user) {
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
		pollTime = 100; // Time in ms between checks for server messages
		received001 = false;
		joined = false;
		this.debug = debug; // Suppresses irc sent/received logger messages when set to false
		quitMsg = "IRCBlit by BullShark";
		this.commands = commands;
		this.repository = repository;
		this.gitblit = gitblit;
		this.url = url;
		this.r = r;
		this.user = user;
		sendDelay = 350; // Time between sending messages
	}

	/**
	 * Attempts to create the socket connection to the IRC server on a specified port
	 * @return False if an exception occurred
	 */
	def createIRCSocket() {
		try {
			socket = new Socket(server, port);
			logger.info("Made connection to ${server}/${port}");
		} catch (IOException ex) {
			logger.info("Failed to connect to ${server}/${port}");
			socket.close();
			return false;
		} catch (UnknownHostException ex) {
			logger.info("Host ${server} not known");
			socket.close();
			return false;
		}
		return true;
	}

	/**
	 * Attempts to get streams for reading and writing to the server connection
	 * @return False if exception occured
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
			return true;
		} catch(IOException ex) {
			logger.info("Failed to get I/O streams with the server");
			return false;
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
						while(receiveln() != false) {
							divideTwo();

							if(first.equals("PING")) {
								sendln("PONG " + last);
							} else if(first.contains("001")) {
								received001 = true;
							} else if(received.contains("JOIN :${chan}")) {
								joined = true;
							}
						}
						logger.info("Thread exiting: ${receivedT}");
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
	 * Splits a line received by the irc connection into two parts
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
	 * Generate the git messages
	 * And send them to the channel by notice
	 * @return
	 */
	def gitBlitChannel() {
		/*
		 * Produce similar messages to these here in this method:
		 * 
		 | GitHubbed (GitHubbed@protectedhost-8DD5CCAF.rs.github.com) has  
		 | joined #blackhats                                               
		 | Mode #blackhats [+v GitHubbed] by BHBot                         
		 | Notice(GitHubbed): [JRobo] BullShark pushed 2 new commits to    
		 | master: http://git.io/JthFbQ                                    
		 | Notice(GitHubbed): JRobo/master b2ca398 BullShark: Fixed expression never evaulating to true
		 | Notice(GitHubbed): JRobo/master 919dab3 BullShark: Attempting to
		 | fix bug preventing wr not working and failure to respond to ping
		 | while getUsers() is being called                                
		 | GitHubbed (GitHubbed@protectedhost-8DD5CCAF.rs.github.com) has  
		 | left #blackhats
		 *                                                 
		 */

		/*
		 * IRC message formatting. For reference:
		 * \002 bold \003 color \017 reset \026 italic/reverse \037 underline
		 * 0 white 1 black 2 dark blue 3 dark green
		 * 4 dark red 5 brownish 6 dark purple 7 orange
		 * 8 yellow 9 light green 10 dark teal 11 light teal
		 * 12 light blue 13 light purple 14 dark gray 15 light gray
		 */

		def repo = repository.name
		def summaryUrl
		def commitUrl
		if (gitblit.getBoolean(Keys.web.mountParameters, true)) {
			repo = repo.replace('/', gitblit.getString(Keys.web.forwardSlashCharacter, '/')).replace('/', '%2F')
			summaryUrl = url + "/summary/$repo"
			commitUrl = url + "/commit/$repo/"
		} else {
			summaryUrl = url + "/summary?r=$repo"
			commitUrl = url + "/commit?r=$repo&h="
		}

		// Tinyurl the links
		def tinySummary = tinyUrl(summaryUrl);
		def tinyCommit = tinyUrl(commitUrl);

		// construct a simple text summary of the changes contained in the push
		def branchBreak = '>---------------------------------------\n'
		def commitBreak = '\n\n ----\n'
		def commitCount = 0
		def changes = ''
		SimpleDateFormat df = new SimpleDateFormat(gitblit.getString(Keys.web.datetimestampLongFormat, 'EEEE, MMMM d, yyyy h:mm a z'))
		def table = { "\n ${JGitUtils.getDisplayName(it.authorIdent)}\n ${df.format(JGitUtils.getCommitDate(it))}\n\n $it.shortMessage\n\n $commitUrl$it.id.name" }
		for (command in commands) {
			def ref = command.refName
			def refType = 'branch'
			if (ref.startsWith('refs/heads/')) {
				ref  = command.refName.substring('refs/heads/'.length())
			} else if (ref.startsWith('refs/tags/')) {
				ref  = command.refName.substring('refs/tags/'.length())
				refType = 'tag'
			}

			switch (command.type) {
				case ReceiveCommand.Type.CREATE:
					def commits = JGitUtils.getRevLog(r, command.oldId.name, command.newId.name).reverse()
					commitCount += commits.size()
				// new branch
					changes += "\n$branchBreak new $refType $ref created ($commits.size commits)\n$branchBreak"
					changes += commits.collect(table).join(commitBreak)
					changes += '\n'
					break
				case ReceiveCommand.Type.UPDATE:
					def commits = JGitUtils.getRevLog(r, command.oldId.name, command.newId.name).reverse()
					commitCount += commits.size()
				// fast-forward branch commits table
					changes += "\n$branchBreak $ref $refType updated ($commits.size commits)\n$branchBreak"
					changes += commits.collect(table).join(commitBreak)
					changes += '\n'
					break
				case ReceiveCommand.Type.UPDATE_NONFASTFORWARD:
					def commits = JGitUtils.getRevLog(r, command.oldId.name, command.newId.name).reverse()
					commitCount += commits.size()
				// non-fast-forward branch commits table
					changes += "\n$branchBreak $ref $refType updated [NON fast-forward] ($commits.size commits)\n$branchBreak"
					changes += commits.collect(table).join(commitBreak)
					changes += '\n'
					break
				case ReceiveCommand.Type.DELETE:
				// deleted branch/tag
					changes += "\n$branchBreak $ref $refType deleted\n$branchBreak"
					break
				default:
					break
			}
		}

		def msgs = [
			"[GitBlit] ${user.username} pushed ${commitCount} commits =>",
			"${repository.name} ${tinySummary}"
		] as String[];

		msgs.each { msg ->
			noticeChannel(msg);
			Thread.sleep(sendDelay);
		}

		logger.info("commitUrl: ${commitUrl}");
		changes.eachLine { line, lineNum ->
			// Match if the line from the start has 0 or more whitespace characters to the end
			if(line.matches("^\\s*\$")) {
				return;
			} else if(line.contains(commitUrl)) {
				line = " ${tinyCommit}";
			}
			noticeChannel(line);
			Thread.sleep(sendDelay);
		}
	}

	/**
	 * Gives a tinyurl short link in return for the link passed to it
	 * @param link The link that will be used to obtain the tinyurl
	 * @return Tinyurl link
	 */
	def tinyUrl(link) {
		return "http://tinyurl.com/api-create.php?url=${link}".toURL().text;
	}

	/**
	 * Sends a raw line to the irc server
	 * @param line The line to send to the server
	 * @return False if an exception occurred
	 */
	def sendln(line) {
		def sent = false;
		try {
			if(socket == null || bWriter == null) {
				throw new SocketException("Socket or ouptut stream is null");
			}
			bWriter.write(line);
			bWriter.newLine();
			bWriter.flush();
			sent = true;
			if(debug) {
				logger.info("Sent:\t${line}");
			}
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
	def receiveln() {
		try {
			received = bReader.readLine();
			if(debug) {
				logger.info("Received:\t${received}");
			}
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
	 * @param msg Message to be sent to the channel
	 * @return
	 */
	def msgChannel(msg) {
		if( !sendln("PRIVMSG " + chan + " :" + msg) ) {
			logger.info("Failed to send message: \"${msg}\" to chan ${chan}");
		}
	}

	/**
	 * Sends a notice to a channel
	 * @param msg Notice to be sent to the channel
	 * @return
	 */
	def noticeChannel(msg) {
		if( !sendln("NOTICE " + chan + " :" + msg) ) {
			logger.info("Failed to send notice: \"${msg}\" to chan ${chan}");
		}
	}

	/**
	 * Sends QUIT to the IRC connection
	 * Closes all the I/O streams
	 * Stops the received thread if it is still running
	 * @param sendQuit Whether to send QUIT to servers input connection stream
	 * @return
	 */
	def quitAndCloseStreams() {
		// Give the server a second before sending QUIT
		Thread.sleep(1000);

		// Leave IRC
		if(bWriter != null) {
			sendln("QUIT :${quitMsg}");
		} else {
			logger.info("socket is null, not sending QUIT")
		}

		// Close I/O
		bWriter.close();
		bReader.close();
		socket.close();
		logger.info("Closed all I/O streams");
	}
}

def fogbugzUrl = repository.customFields.fogbugzUrl
def fogbugzRepositoryId = repository.customFields.fogbugzRepositoryId
def bugIdRegex = repository.customFields.fogbugzCommitMessageRegex

Repository r = gitblit.getRepository(repository.name)


// TODO Get debug value from Gitblit Custom Fields
def debug = true;
new IRCBlit(logger, debug, commands, repository, gitblit, url, r, user);

// close the repository reference
r.close()

//TODO QUIT is never being sent to the irc connection
//TODO Commit message and hash are missing from chanMsg
//TODO Get Info by Accessing Gitblit Custom Fields
//TODO And if the fields do not exist, use some defaults
//TODO Organize imports
//TODO Support SSL
//TODO Add IRC Quit Message