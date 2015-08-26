Akka IO Blog Project
====================

A simple project that uses akka-io to serve device information over a custom protocol.

To start the server:

```
./activator run
```

Then connect using telnet:

```
telnet localhost 12000
```

To read register 1 of device 1, send `1,1,1`. You should receive `1,1,1,100`
back.
