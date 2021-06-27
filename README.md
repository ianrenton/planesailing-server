# Plane Sailing Server

Server-side code for Plane/Sailing version 2.

This software receives data from local ADS-B, AIS and APRS receiving software for tracking planes, ships and amateur radio stations respectively. It combines them into one large "track table", and provides a web interface by which the Plane/Sailing client can fetch data via JSON.

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

1. Download and unpack the software, or build it yourself. You should have a JAR file and an `application.conf` file.
2. Ensure your machine has Java 11 or later installed.
3. Edit `application.conf` and set the IP addresses and ports as required. If you don't have a particular server, e.g. you don't do APRS, set `enabled: false` for that section.
4. Set the base station position, and any airports and seaports you'd like to appear in your data.
5. Save `application.conf` and run the application, e.g. `java -jar plane-sailing-server-[VERSION]-jar-with-dependencies.jar`
6. Hopefully you should see log messages indicating that it has started up and loaded data! Every 10 seconds it will print out a summary of what's in its track table.
7. Depending on your use case you may wish to have the software run automatically on startup. How to do this is system-dependent, but my setup instructions for the full system at https://ianrenton.com/hardware/planesailing contain my setup for a Raspberry Pi.
8. You may also wish to have clients not connect directly to Plane/Sailing Server but have them connect via a web server such as Lighttpd providing a reverse proxy setup. This allows use of things like HTTPS certificates, and hosting the client and server software - as well as other things - on the same PC. Instructions are at the link above.
9. If things aren't connecting the way you expect, don't forget to check firewalls etc.

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