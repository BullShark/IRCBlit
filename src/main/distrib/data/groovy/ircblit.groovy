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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.HashMap;

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

//TODO Get Info by Accessing Gitblit Custom Fields
//TODO And if the fields do not exist, use some defaults
def server = "frequency.windfyre.net";
def port = 6667;
def channel = "#blackhats";
def nick = "GitBlit";
def first;
def last;
Socket socket;
BufferedWriter bWriter;
BufferedReader bReader;

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

} catch(IOException ex) {
	logger.info("Failed to get I/O streams with the server");
	System.exit(-1);
}

//socket.withStreams { inStream, outStream ->
//	def reader = inStream.newReader()
//	def
//}

//TODO Make a separate thread for responding to pings?

sendln("Nick ${nick}");
//XXX What does the 0 and * mean? What is the difference between 0 and 8?
sendln("USER ${nick} 8 * :IRCBlit Service Hook for GitBlit");

/**
 * Wait for the server to respond with 001
 * Before attempting to join a channel
 */
while(( received = recieveln()) != null ) {
	divideTwo();

	if(first.equals("PING")) {
		sendln("PONG " + last);
	}

	if(first.contains("001")) {
		break;
	}
}

// Attempt to join the IRC channel
sendln("JOIN ${chan}");

// Message the channel
msgChannel(chan, "Hello ${chan}");

// Leave IRC
sendln("QUIT");

// Close I/O
bWriter.close();
bReader.clone();
socket.close();

/**************************************
 * All methods below
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

def sendln(line) {
	bWriter.write(line);
	bWriter.newLine();
	bWriter.flush();
	logger.info("Sent:\t${line}");
}

def String recieveln() {
	try {
		received = bReader.readLine();
		logger.info("Received:\t${line}");
		return received;
	} catch (IOException ex) {
		logger.info("Failed to get I/O streams with the server");
		return null;
	}
}

def msgChannel(chan, msg) {
	if( !sendln("PRIVMSG " + chan + " :" + msg) ) {
		logger.info("Failed to send message: \"${msg}\" to channel ${chan}");
	}
	logger.info("Sent:\tmessage: \"${msg}\" to channel ${chan}");
}

















