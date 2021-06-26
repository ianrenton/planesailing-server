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

## To Do List

* Finish APRS support - may require additions to javAPRSlib to support course, speed etc. Currently hampered by low volume of APRS traffic here and lack of a handheld transmitter I can use for testing