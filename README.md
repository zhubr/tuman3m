To try the server as a tomcat servlet, use aq2db, aq2net, aq3host, aq2j, aq2ws packages (in server/ directory) and web.xml example (in server-examples/ directory). To actually make use of a websocket interface, tomcat will need to be run behind some https server like nginx.

To try the server as a standalone application, use aq2db, aq2net, aq3host, aq2j, aq2con packages (in server/ directory) and 0runme.sh example (in server-examples/ directory).

Some small set of usable configuration files can be found in server-examples/, however all filesystem paths in *.properties will need to be adjusted according to actual files location.

In any case, TUM3CONF environment variable should be set to contain a path to *.properties location. For tomcat, such assignment can normally be added to /etc/tomcat/tomcat.conf configuration file.

And yes, it can also run on Windows.
