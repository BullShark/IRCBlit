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

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.JGitUtils;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
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

com.gitblit.models.UserModel userModel = user

// Indicate we have started the script
logger.info("IRCBlit hook triggered by ${user.username} for ${repository.name}");

//Repository r = gitblit.getRepository(repository.name)

// reuse existing repository config settings, if available
//Config config = r.getConfig()
// define the summary and commit urls
def repo = repository.name
def summaryUrl = url + "/summary?r=$repo"
def baseCommitUrl = url + "/commit?r=$repo&h="
def baseBlobDiffUrl = url + "/blobdiff/?r=$repo&h="
def baseCommitDiffUrl = url + "/commitdiff/?r=$repo&h="
def forwardSlashChar = gitblit.getString(Keys.web.forwardSlashCharacter, '/')

if (gitblit.getBoolean(Keys.web.mountParameters, true)) {
	repo = repo.replace('/', forwardSlashChar).replace('/', '%2F')
	summaryUrl = url + "/summary/$repo"
	baseCommitUrl = url + "/commit/$repo/"
	baseBlobDiffUrl = url + "/blobdiff/$repo/"
	baseCommitDiffUrl = url + "/commitdiff/$repo/"
}

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
	 * Gives all the global variables default values
	 * @param logger
	 */
	IRCBlit(logger) {
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
		//quitMsg = "GitBlit Service Hook by BullShark"
	}

	/**
	 * The constructor calls many helper methods
	 * From setting up the irc connection to closing the connection
	 * @param logger Used for logging info messages to Apache Tomcat's server logs
	 */
	def start(content) {
		createIRCSocket();
		createIOStreams();
		createReceivedThread()
		sendNickAndUserMessages();
		waitFor001();
		joinChannel();
		waitForChannelJoined();
		gitBlitChannel(content);
		// Send a test message to the chan
		msgChannel(chan, ".wr");
		msgChannel(chan, "ftl");
		// Send a test notice to the chan
		noticeChannel(chan, "Hello ${chan}");
		gitBlitChannel();
		quitAndCloseStreams();
	}

	/**
	 * Attempts to create the socket connection to the IRC server on a specified port
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
	 * 
	 */
	def gitBlitChannel(content) {
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

		/*
		 * IRC message formatting. For reference:
		 * \002 bold \003 color \017 reset \026 italic/reverse \037 underline
		 * 0 white 1 black 2 dark blue 3 dark green
		 * 4 dark red 5 brownish 6 dark purple 7 orange
		 * 8 yellow 9 light green 10 dark teal 11 light teal
		 * 12 light blue 13 light purple 14 dark gray 15 light gray
		 */

		noticeChannel(chan, content);
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
			sent = true;
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

	/*
	 * Used for Git Summary Message
	 */
	Repository repository
	def url
	def baseCommitUrl
	def baseCommitDiffUrl
	def baseBlobDiffUrl
	def mountParameters
	def forwardSlashChar
	def includeGravatar
	def shortCommitIdLength
	def commitCount = 0
	def commands
	def writer = new StringWriter();
	def builder = new MarkupBuilder(writer)

	def writeStyle() {
		builder.style(type:"text/css", '''
    .table td {
        vertical-align: middle;
    }
    tr.noborder td {
        border: none;
        padding-top: 0px;
    }
    .gravatar-column {
        width: 5%; 
    }
    .author-column {
        width: 20%; 
    }
    .commit-column {
        width: 5%; 
    }
    .status-column {
        width: 10%;
    }
    .table-disable-hover.table tbody tr:hover td,
    .table-disable-hover.table tbody tr:hover th {
        background-color: inherit;
    }
    .table-disable-hover.table-striped tbody tr:nth-child(odd):hover td,
    .table-disable-hover.table-striped tbody tr:nth-child(odd):hover th {
      background-color: #f9f9f9;
    }
    ''')
	}

	def writeBranchTitle(type, name, action, number) {
		builder.div('class' : 'pageTitle') {
			builder.span('class':'project') {
				mkp.yield "$type "
				span('class': 'repository', name )
				if (number > 0) {
					mkp.yield " $action ($number commits)"
				} else {
					mkp.yield " $action"
				}
			}
		}
	}

	def writeBranchDeletedTitle(type, name) {
		builder.div('class' : 'pageTitle', 'style':'color:red') {
			builder.span('class':'project') {
				mkp.yield "$type "
				span('class': 'repository', name )
				mkp.yield " deleted"
			}
		}
	}

	def commitUrl(RevCommit commit) {
		"${baseCommitUrl}$commit.id.name"
	}

	def commitDiffUrl(RevCommit commit) {
		"${baseCommitDiffUrl}$commit.id.name"
	}

	def encoded(String path) {
		path.replace('/', forwardSlashChar).replace('/', '%2F')
	}

	def blobDiffUrl(objectId, path) {
		if (mountParameters) {
			// REST style
			"${baseBlobDiffUrl}${objectId.name()}/${encoded(path)}"
		} else {
			"${baseBlobDiffUrl}${objectId.name()}&f=${path}"
		}

	}

	def writeCommitTable(commits, includeChangedPaths=true) {
		// Write commits table
		builder.table('class':"table table-disable-hover") {
			thead {
				tr {
					th(colspan: includeGravatar ? 2 : 1, "Author")
					th( "Commit" )
					th( "Message" )
				}
			}
			tbody() {

				// Write all the commits
				for (commit in commits) {
					writeCommit(commit)

					if (includeChangedPaths) {
						// Write detail on that particular commit
						tr('class' : 'noborder') {
							td (colspan: includeGravatar ? 3 : 2)
							td (colspan:2) { writeStatusTable(commit) }
						}
					}
				}
			}
		}
	}

	def writeCommit(commit) {
		def abbreviated = repository.newObjectReader().abbreviate(commit.id, shortCommitIdLength).name()
		def author = commit.authorIdent.name
		def email = commit.authorIdent.emailAddress
		def message = commit.shortMessage
		builder.tr {
			if (includeGravatar) {
				td('class':"gravatar-column") {
					img(src:gravatarUrl(email), 'class':"gravatar")
				}
			}
			td('class':"author-column", author)
			td('class':"commit-column") {
				a(href:commitUrl(commit)) {
					span('class':"label label-info",  abbreviated )
				}
			}
			td {
				mkp.yield message
				a('class':'link', href:commitDiffUrl(commit), " [commitdiff]" )
			}
		}
	}

	def writeStatusLabel(style, tooltip) {
		builder.span('class' : style,  'title' : tooltip )
	}

	def writeAddStatusLine(ObjectId id, FileHeader header) {
		builder.td('class':'changeType') { writeStatusLabel("addition", "addition") }
		builder.td {
			a(href:blobDiffUrl(id, header.newPath), header.newPath)
		}
	}

	def writeCopyStatusLine(ObjectId id, FileHeader header) {
		builder.td('class':'changeType') { writeStatusLabel("rename", "rename") }
		builder.td() {
			a(href:blobDiffUrl(id, header.newPath), header.oldPath + " copied to " + header.newPath)
		}
	}

	def writeDeleteStatusLine(ObjectId id, FileHeader header) {
		builder.td('class':'changeType') { writeStatusLabel("deletion", "deletion") }
		builder.td() {
			a(href:blobDiffUrl(id, header.oldPath), header.oldPath)
		}
	}

	def writeModifyStatusLine(ObjectId id, FileHeader header) {
		builder.td('class':'changeType') { writeStatusLabel("modification", "modification") }
		builder.td() {
			a(href:blobDiffUrl(id, header.oldPath), header.oldPath)
		}
	}

	def writeRenameStatusLine(ObjectId id, FileHeader header) {
		builder.td('class':'changeType') { writeStatusLabel("rename", "rename") }
		builder.td() {
			mkp.yield header.oldPath
			mkp.yieldUnescaped "<b> -&rt; </b>"
			a(href:blobDiffUrl(id, header.newPath),  header.newPath)
		}
	}

	def writeStatusLine(ObjectId id, FileHeader header) {
		builder.tr {
			switch (header.changeType) {
				case ChangeType.ADD:
					writeAddStatusLine(id, header)
					break;
				case ChangeType.COPY:
					writeCopyStatusLine(id, header)
					break;
				case ChangeType.DELETE:
					writeDeleteStatusLine(id, header)
					break;
				case ChangeType.MODIFY:
					writeModifyStatusLine(id, header)
					break;
				case ChangeType.RENAME:
					writeRenameStatusLine(id, header)
					break;
			}
		}
	}

	def writeStatusTable(RevCommit commit) {
		DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)
		formatter.setRepository(repository)
		formatter.setDetectRenames(true)
		formatter.setDiffComparator(RawTextComparator.DEFAULT);

		def diffs
		RevWalk rw = new RevWalk(repository)
		if (commit.parentCount > 0) {
			RevCommit parent = rw.parseCommit(commit.parents[0].id)
			diffs = formatter.scan(parent.tree, commit.tree)
		} else {
			diffs = formatter.scan(new EmptyTreeIterator(),
					new CanonicalTreeParser(null, rw.objectReader, commit.tree))
		}
		rw.dispose()
		// Write status table
		builder.table('class':"plain") {
			tbody() {
				for (DiffEntry entry in diffs) {
					FileHeader header = formatter.toFileHeader(entry)
					writeStatusLine(commit.id, header)
				}
			}
		}
	}


	def md5(text) {

		def digest = MessageDigest.getInstance("MD5")

		//Quick MD5 of text
		def hash = new BigInteger(1, digest.digest(text.getBytes()))
				.toString(16)
				.padLeft(32, "0")
		hash.toString()
	}

	def gravatarUrl(email) {
		def cleaned = email.trim().toLowerCase()
		"http://www.gravatar.com/avatar/${md5(cleaned)}?s=30"
	}

	def writeNavbar() {
		builder.div('class':"navbar navbar-fixed-top") {
			div('class':"navbar-inner") {
				div('class':"container") {
					a('class':"brand", href:"${url}", title:"GitBlit") {
						img(src:"${url}/gitblt_25_white.png",
						width:"79",
						height:"25",
						'class':"logo")
					}
				}
			}
		}
	}

	def write() {
		builder.html {
			head {
				link(rel:"stylesheet", href:"${url}/bootstrap/css/bootstrap.css")
				link(rel:"stylesheet", href:"${url}/gitblit.css")
				link(rel:"stylesheet", href:"${url}/bootstrap/css/bootstrap-responsive.css")
				writeStyle()
			}
			body {

				writeNavbar()

				div('class':"container") {

					for (command in commands) {
						def ref = command.refName
						def refType = 'Branch'
						if (ref.startsWith('refs/heads/')) {
							ref  = command.refName.substring('refs/heads/'.length())
						} else if (ref.startsWith('refs/tags/')) {
							ref  = command.refName.substring('refs/tags/'.length())
							refType = 'Tag'
						}

						switch (command.type) {
							case ReceiveCommand.Type.CREATE:
								def commits = JGitUtils.getRevLog(repository, command.oldId.name, command.newId.name).reverse()
								commitCount += commits.size()
								if (refType == 'Branch') {
									// new branch
									writeBranchTitle(refType, ref, "created", commits.size())
									writeCommitTable(commits, true)
								} else {
									// new tag
									writeBranchTitle(refType, ref, "created", 0)
									writeCommitTable(commits, false)
								}
								break
							case ReceiveCommand.Type.UPDATE:
								def commits = JGitUtils.getRevLog(repository, command.oldId.name, command.newId.name).reverse()
								commitCount += commits.size()
							// fast-forward branch commits table
							// Write header
								writeBranchTitle(refType, ref, "updated", commits.size())
								writeCommitTable(commits)
								break
							case ReceiveCommand.Type.UPDATE_NONFASTFORWARD:
								def commits = JGitUtils.getRevLog(repository, command.oldId.name, command.newId.name).reverse()
								commitCount += commits.size()
							// non-fast-forward branch commits table
							// Write header
								writeBranchTitle(refType, ref, "updated [NON fast-forward]", commits.size())
								writeCommitTable(commits)
								break
							case ReceiveCommand.Type.DELETE:
							// deleted branch/tag
								writeBranchDeletedTitle(refType, ref)
								break
							default:
								break
						}
					}
				}
			}
		}
		writer.toString()
	}
}

def IRCBlit = new IRCBlit(logger);
IRCBlit.repository = r
IRCBlit.baseCommitUrl = baseCommitUrl
IRCBlit.baseBlobDiffUrl = baseBlobDiffUrl
IRCBlit.baseCommitDiffUrl = baseCommitDiffUrl
IRCBlit.forwardSlashChar = forwardSlashChar
IRCBlit.commands = commands
IRCBlit.url = url
IRCBlit.mountParameters = GitBlit.getBoolean(Keys.web.mountParameters, true)
IRCBlit.includeGravatar = GitBlit.getBoolean(Keys.web.allowGravatar, true)
IRCBlit.shortCommitIdLength = GitBlit.getInteger(Keys.web.shortCommitIdLength, 8)

IRCBlit.start(IRCBlit.write());

// close the repository reference
r.close()

// tell Gitblit to send the message (Gitblit filters duplicate addresses)
//def repositoryName = repository.name.substring(0, repository.name.length() - 4)
//gitblit.sendHtmlMail("${emailprefix} ${userModel.displayName} pushed ${mailWriter.commitCount} commits => $repositoryName",
//					 content,
//					 toAddresses)

//TODO Get Info by Accessing Gitblit Custom Fields
//TODO And if the fields do not exist, use some defaults
//TODO Organize imports
//TODO Support SSL
//TODO Add debug variable for suppressing irc messages when it is set to false