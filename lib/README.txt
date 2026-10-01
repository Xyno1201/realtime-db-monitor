Put the team-approved MySQL Connector/J 8.x JAR in this folder (master context v7, sections 0.5 and 69.1).

It is not included in the repository. The classpath uses the wildcard  lib/*  so the exact
file name does not matter, but there must be exactly ONE Connector/J jar here.

Everything that talks to MySQL needs it: the server, the Admin, and the checks
(Phase12Check Part C reports BLOCKED without it).

Download: dev.mysql.com/downloads/connector/j -> Archives -> an 8.x version ->
Platform Independent -> ZIP. Copy only the mysql-connector-j-8.x.x.jar file here.
