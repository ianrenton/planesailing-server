# Plane Sailing Server

*The home situational awareness display nobody wanted or needed!*

![Plane Sailing Banner](./banner.png)

This is the server-side code for Plane/Sailing version 2.

This software receives data from local ADS-B, AIS and APRS receiving software for tracking planes, ships and amateur radio stations respectively. It combines them into one large "track table", and provides a web interface by which the [Plane/Sailing client](https://github.com/ianrenton/planesailing) can fetch data via JSON.

For more information on the Plane/Sailing project, please see https://ianrenton.com/hardware/planesailing

## Features

* Receives NMEA-0183 format AIS messages (e.g. from rtl_ais)
* Receives SBS-format messages derived from ADS-B (e.g. from Dump1090)
* Receives APRS messages via TCP (e.g. from Direwolf)
* Includes support for config-based addition of extra tracks for the base station, airports and seaports
* Includes look-up tables to determine aircraft operators, types, and the correct MIL-STD2525C symbols to use for a variety of tracks
* Persists data to disk so the content of the track table is not lost on restart
* Customisable times to drop tracks etc.

## Setup

In order to use this software, you should be running some combination of software to provide the data to it, e.g. rtl_ais, Dump1090, Direwolf etc. To run Plane/Sailing Server:

1. Ensure your machine has Java 11 or later installed, e.g. `sudo apt install openjdk-11-jre-headless`
2. [Download the software from the Releases area](https://github.com/ianrenton/planesailing-server/releases/) and unpack it, or build it yourself using Maven and a JDK. You should have a JAR file and an `application.conf` file.
3. Edit `application.conf` and set the IP addresses and ports as required. If you don't have a particular server, e.g. you don't do APRS, set `enabled: false` for that section.
4. Set the base station position, and any airports and seaports you'd like to appear in your data.
5. Save `application.conf` and run the application, e.g. `chmod +x run.sh`, `./run.sh`
6. Hopefully you should see log messages indicating that it has started up and loaded data! Every 10 seconds it will print out a summary of what's in its track table.

### Automatic Run on Startup

Depending on your use case you may wish to have the software run automatically on startup. How to do this is system-dependent, on most Linux systems that use systemd, like Ubuntu and Raspbian, you will want to create a file like `/etc/systemd/system/plane-sailing-server.service` with the contents similar to this:

```
[Unit]
Description=Plane/Sailing Server
After=network.target

[Service]
ExecStart=/home/pi/plane-sailing-server/run.sh
WorkingDirectory=/home/pi/plane-sailing-server
StandardOutput=inherit
StandardError=inherit
Restart=always
User=pi

[Install]
WantedBy=multi-user.target
```

Then to complete the setup by running:

```
sudo systemctl daemon-reload
sudo systemctl enable plane-sailing-server
sudo systemctl start plane-sailing-server
```

You can achieve similar things on Windows using NSSM to install Plane/Sailing Server as a service, or with Scheduled Tasks, a shortcut in your Startup items, etc.

### Reverse Proxy Setup

The client can quite happily connect to the Plane/Sailing Server on its default port of 8000. However, you may wish to use a "proper" web server such as Lighttpd providing a reverse proxy setup. There are several reasons you might want to do this:

* It allows the use HTTPS (with certificates, e.g. from Let's Encrypt), so the client can connect securely
* It allows Plane/Sailing Server to run on a port that Linux will let it open with normal user privileges (by default 8000) while still making it accessible to the internet on port 80 and/or 443
* You can host other software on the same machine, e.g. Plane/Sailing Client, Dump1090, AIS Dispatcher etc. via the same public port.

An example Lighttp config could be placed at `/etc/lighttpd/conf-available/90-plane-sailing-server.conf` and would forward all incoming requests to Plane/Sailing Server, if that's the only thing the machine will run:

```
server.modules += ( "mod_setenv", "mod_proxy" )

$HTTP["url"] =~ "(^.*)" {
  proxy.server  = ( "" => ("" => ( "host" => "127.0.0.1", "port" => 8000 )))
  setenv.set-response-header = ( "Access-Control-Allow-Origin" => "*" )
}
```

Or you could use a URL rewriter like this to make it look like your copy of Plane/Sailing Server called "pss", so you could put Dump1090 in its own "folder" alongside it, etc.

```
server.modules += ( "mod_setenv", "mod_proxy" )

$HTTP["url"] =~ "(^/pss/.*)" {
  url.rewrite-once = ( "^/pss/(.*)$" => "/$1" )
  proxy.server  = ( "" => ("" => ( "host" => "127.0.0.1", "port" => 8000 )))
  setenv.set-response-header = ( "Access-Control-Allow-Origin" => "*" )
}

$HTTP["url"] =~ "(^/dump1090/.*)" {
  url.rewrite-once = ( "^/dump1090/(.*)$" => "/$1" )
  proxy.server  = ( "" => ("" => ( "host" => "127.0.0.1", "port" => 8080 )))
}
```

There are guides on the web that will show you how to extend these to support HTTPS, and anything else you'd like to do with Lighttpd.

Make sure you `sudo lighttpd-enable-mod plane-sailing-server` and `sudo service lighttpd force-reload` to enable and reload the config when you're done. If things aren't connecting the way you expect, don't forget to check firewalls etc. If you get stuck, let me know and I'll see if I can help!

## Client Usage

The Plane/Sailing client, or any other client you write, should do the following:

1. At the start of a user session, make a call to `/first`. The response will be a JSON map of ID to track parameters. All tracks will be included, and if they have a position history, that will be included too. This information should be stored locally, and used to render the tactical picture.
2. Every few seconds (suggested: 10), make a call to `/update`. The response will be the same JSON map, which should be merged with the original one. There are a few complications to this merge:
    * The new data won't include position history, to save bandwidth. Rather than overwriting the client's track with the new one, it should preserve the old position history, appending the new position in the track data to the position history list.
    * Any track the client has that *isn't* in the update data set should be dropped, *except* if it has the `createdByConfig` flag set true. These are the immutable tracks that represent the base station, airports and seaports, and they are only sent in the `/first` call, again to save bandwidth. Their absence in the `/update` call doesn't mean they should be deleted.
3. Rather than just reporting tracks' last known locations, clients may wish to "dead reckon" their current position and update the display with that more often than every 10 seconds. Because the server and client's clocks may not match, the server provides a "time" field (milliseconds since UNIX epoch) in every response. The position data for each track has a time field too. The combination of these can be used to determine how old the track's data is without having to use the local system clock.

Clients are of course free to set their own policies about what tracks to show and hide, how to present the data, etc. If you are writing your own client or fork of Plane/Sailing, I am happy to receive pull requests to add new data into the API.

## To Do List

* Finish APRS support - may require additions to javAPRSlib to support course, speed etc. Currently hampered by low volume of APRS traffic here and lack of a handheld transmitter I can use for testing
