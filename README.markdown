GitBlit IRC Service Hook 
==================================

IRCBlit is an IRC Service Hook for Gitblit similar to Github's.

Here is an example of GitHub's IRC Serivce Hook being called. Note, the messages sent to irc are not limited to showing only commits. The hook also shows information about branch merging and more git information.

[http://expectusafterlun.ch:8888/github-irc-hook-triggered.png](http://expectusafterlun.ch:8888/github-irc-hook-triggered.png)

This project is one service hook. Gitblit has been included with it to make all the import statements work for testing. The file you are looking for is this one.

[https://github.com/BullShark/IRCBlit/blob/master/src/main/distrib/data/groovy/ircblit.groovy](https://github.com/BullShark/IRCBlit/blob/master/src/main/distrib/data/groovy/ircblit.groovy)

GitBlit Sample Hooks
--------------------

[https://code.google.com/p/gitblit/source/browse/src/main/distrib/data/groovy/](https://code.google.com/p/gitblit/source/browse/src/main/distrib/data/groovy/)

GitHub IRC Service Hook
-----------------------

This is included under the examples/ directory of the project.

[https://github.com/github/github-services/blob/master/lib/services/irc.rb](https://github.com/github/github-services/blob/master/lib/services/irc.rb)

All of GitHub's Service Hooks
-----------------------------

[https://github.com/github/github-services](https://github.com/github/github-services)

More Info that may be Useful in Coding the Hook
------------------------------------------------

[http://www.gitblit.com/rpc.html](http://www.gitblit.com/rpc.html)

[http://expectusafterlun.ch:8888/gbapi/1.2.1/](http://expectusafterlun.ch:8888/gbapi/1.2.1/)

Gitblit
=================

Gitblit is an open source, pure Java Git solution for managing, viewing, and serving [Git](http://git-scm.com) repositories.<br/>
More information about Gitblit can be found [here](http://gitblit.com).

License
-------

Gitblit is distributed under the terms of the [Apache Software Foundation license, version 2.0](http://www.apache.org/licenses/LICENSE-2.0).<br/>
The text of the license is included in the file LICENSE in the root of the project.

Java Runtime Requirement
------------------------------------

Gitblit requires at Java 6 Runtime Environment (JRE) or a Java 6 Development Kit (JDK).

Getting help
------------

Read the online documentation available at the [Gitblit website](http://gitblit.com)<br/>
Issues, binaries, & sources @ [Google Code](http://code.google.com/p/gitblit)

Building Gitblit
----------------
[Eclipse](http://eclipse.org) is recommended for development as the project settings are preconfigured.

1. Import the gitblit project into your Eclipse workspace.<br/>
*There will be lots of build errors.*
2. Using Ant, execute the `build.xml` script in the project root.<br/>
*This will download all necessary build dependencies and will also generate the Keys class for accessing settings.*
3. Select your gitblit project root and **Refresh** the project, this should correct all build problems.
4. Using JUnit, execute the `com.gitblit.tests.GitBlitSuite` test suite.<br/>
*This will clone some repositories from the web and run through the unit tests.*
5. Execute the *com.gitblit.GitBlitServer* class to start Gitblit GO.

Building Tips & Tricks
----------------------
1. If you are running Ant from an ANSI-capable console, consider setting the `MX_COLOR` ennvironment variable before executing Ant.<pre>set MX_COLOR=true</pre>
2. The build script will honor your Maven proxy settings.  If you need to fine-tune this, please review the [settings.moxie](http://gitblit.github.io/moxie/settings.html) documentation.
