# Plane Sailing Server

Server-side code for Plane/Sailing version 2.

This software receives data from local ADS-B, AIS and APRS receiving software for tracking planes, ships and amateur radio stations respectively. It combines them into one large "track table", and provides a web interface by which the Plane/Sailing client can fetch data via JSON.

For more information on the Plane/Sailing project, please see https://ianrenton.com/hardware/planesailing

**NOTE: The Plane/Sailing client does not currently connect to this server. Version 2 of the client is still under development at this time!**

## Features

* Receives NMEA-0183 format AIS messages (e.g. from rtl_ais)
* Receives SBS-format messages derived from ADS-B (e.g. from Dump1090)
* Receives APRS messages via TCP (e.g. from Direwolf)
* Includes support for config-based addition of extra tracks for the base station, airports and seaports
* Includes look-up tables to determine aircraft operators, types, and the correct MIL-STD2525C symbols to use for a variety of tracks
* Persists data to disk so the content of the track table is not lost on restart
* Customisable times to drop tracks etc.

## Usage

In order to use this software, you should be running some combination of software to provide the data to it, e.g. rtl_ais, Dump1090, Direwolf etc. To run Plane/Sailing Server:
1. Download and unpack the software, or build it yourself. You should have a JAR file and an `application.conf` file.
2. Ensure your machine has Java 15 installed.
3. Edit `application.conf` and set the IP addresses and ports as required. If you don't have a particular server, e.g. you don't do APRS, delete that section from the file.
4. Set the base station position, and any airports and seaports you'd like to appear in your data.
5. Save `application.conf` and run the application, e.g. `java -jar plane-sailing-server-[VERSION].jar`
6. Hopefully you should see log messages indicating that it has started up and loaded data! Every 10 seconds it will print out a summary of what's in its track table.
7. Depending on your use case you may wish to have the software run automatically on startup. How to do this is system-dependent, but my setup instructions for the full system at https://ianrenton.com/hardware/planesailing contain my setup for a Raspberry Pi.

## To Do List

* Finish APRS support - may require additions to javAPRSlib to support course, speed etc. Currently hampered by low volume of APRS traffic here and lack of a handheld transmitter I can use for testing