package expectusafterlun.ch.irc

import com.gitblit.GitBlit
import com.gitblit.Keys
import com.gitblit.models.RepositoryModel
import com.gitblit.models.UserModel
import com.gitblit.utils.JGitUtils
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.transport.ReceiveCommand.Result
import org.slf4j.Logger

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
logger.info("IRCBlit hook triggered by ${user.username} for ${repository.name}")

//TODO Get Info by Accessing Gitblit Custom Fields
//TODO And if the fields do not exist, use some defaults
server = "frequency.windfyre.net"
port = "6667"
channel = "#blackhats"
nick = "GitBlit"

final timeToSleep = 4000;

try {
	sock = new Socket(server, port)
} catch (IOException ex) {
	logger.info("Failed to connect to ${server} on ${port}")
	System.exit(-1)
} catch (UnknownHostException ex) {
	logger.info("Host ${server} not known")
	System.exit(-1)
} finally {
	sock.close()
}

try {
	bwriter = 
	new BufferedWriter(
		new OutputStreamWriter(sock.getOutputStream()))
	
	breader = BufferedReader(
		new InputStreamReader(sock.getInputStream()))
} catch(IOException ex) {
	logger.info("Failed to get I/O streams with the server")
}

//TODO Make a separate thread for responding to pings?

try {
}



























