# Plane Sailing Server

*The home situational awareness display nobody wanted or needed!*

![Plane Sailing Banner](./banner.png)

This is the server-side code for Plane/Sailing version 2.

This software receives data from local ADS-B, AIS and APRS receiving software for tracking planes, ships and amateur radio stations respectively. It combines them into one large "track table", and provides a web interface by which the [Plane/Sailing client](https://github.com/ianrenton/planesailing) can fetch data via JSON.

For more information on the Plane/Sailing project, please see https://ianrenton.com/hardware/planesailing

## Features

* Receives NMEA-0183 format AIS messages (e.g. from rtl_ais)
* Receives ADS-B Mode S messages (e.g. from Dump1090)
* Receives APRS messages via KISS TCP (e.g. from Direwolf)
* Includes support for config-based addition of extra tracks for the base station, airports and seaports
* Includes support for config-based addition of AIS track names, to cover the period between getting a position message and a details message
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

```bash
sudo systemctl daemon-reload
sudo systemctl enable plane-sailing-server
sudo systemctl start plane-sailing-server
```

If you want to check it's working, use e.g. `systemctl status plane-sailing-server` to check it's alive, and `journalctl -u plane-sailing-server.service -f` to see its logs in real time. You should see it successfully start up its interfaces to Dump1090, AIS Dispatcher and Direwolf (assuming they are all enabled) and the regular track table messages should indicate several ships, aircraft and APRS tracks depending on what's around you.

If you are running Plane/Sailing Server on Windows you can make it start automatically by using NSSM to install Plane/Sailing Server as a service, or with Scheduled Tasks, a shortcut in your Startup items, etc.

### Reverse Proxy Setup

The client can quite happily connect to the Plane/Sailing Server directly on its default port of 8090. However, you may wish to add a "proper" web server providing a reverse proxy setup. There are several reasons you might want to do this:

* It allows the use HTTPS (with certificates, e.g. from Let's Encrypt), so the client can connect securely
* It allows Plane/Sailing Server to run on a port that Linux will let it open with normal user privileges (by default 8090) while still making it accessible to the internet on port 80 and/or 443
* You can host other software on the same machine, e.g. Plane/Sailing Client, Dump1090, AIS Dispatcher etc. via the same public port.
* You can [enable caching in the web server](https://docs.nginx.com/nginx/admin-guide/content-cache/content-caching/), so that no matter how many visitors your site gets, the server itself sees a stable number of requests and doesn't get overloaded.

In this example, we will use Nginx due to its relatively easy config and support in Let's Encrypt Certbot, but you could use Apache, Lighttpd, HAProxy or anything you like that supports a reverse proxy configuration.

Start off by installing Nginx, e.g. `sudo apt install nginx`. You should then be able to enter the IP address of the server using a web browser and see the "Welcome to nginx" page. Next we need to change that default setup to instead provide a reverse proxy pointing at Plane/Sailing Server. Create a file like `/etc/nginx/sites-available/plane-sailing-server.conf` with contents similar to the following:

```
server {
    listen 80;
    listen 443 ssl;
    server_name planesailingserver.ianrenton.com;

    location / {
        proxy_pass http://127.0.0.1:8090;
    }
}
```

If you don't plan on enabling HTTPS, you can skip "listen 443 ssl" and the "server_name" line. If you *do* intend to enable HTTPS, you'll be generating a certificate for a certain domain or subdomain that points at your server, and that (sub)domain must be in the "server_name" line.

Once you're finished creating that filte, delete the default site, enable the new one, and restart nginx:

```bash
sudo rm /etc/nginx/sites-enabled/default
sudo ln -s /etc/nginx/sites-available/plane-sailing-server.conf /etc/nginx/sites-enabled/plane-sailing-server.conf
sudo systemctl restart nginx
```

When you now visit the IP address of your server using a web browser, you should instead see "Plane/Sailing Server is up and running!". Your reverse proxy setup is now complete, and a client pointed at the default HTTP port (80) on that PC will now be able to communicate with Plane/Sailing Server.

If nginx didn't restart properly, you may have mistyped your configuration. Try `sudo nginx -t` to find out what the problem is.

You may wish to add extra features to this configuration. For example if you wanted Plane/Sailing Server accessible in the root directory as normal, but then have Dump1090 on `/dump1090-fa` and AIS Dispatcher's web interface on `/aisdispatcher` too, all on port 80, you can do that by tweaking the adding new "location" parameters as follows. They are handled in order so Plane/Sailing Server comes *last*, only if the URL doesn't match any of the other locations.

```
server {   
    listen 80;
    listen 443 ssl;
    server_name planesailingserver.ianrenton.com;

    # Dump1090 web interface
    rewrite ^/dump1090-fa$ /dump1090-fa/ permanent;
    location /dump1090-fa/ {
        alias /usr/share/dump1090-fa/html/;
        # Allow CORS requests to / for UMID1090 - not necessarily required for your server!
        add_header Access-Control-Allow-Origin *;
    }
    location /dump1090-fa/data/ {
        alias /run/dump1090-fa/;
        # Allow CORS requests to /data/ for UMID1090 - not necessarily required for your server!
        add_header Access-Control-Allow-Origin *;
    }

    # AIS Dispatcher web interface
    rewrite ^/aisdispatcher$ /aisdispatcher/ permanent;
    location /aisdispatcher/ {
        proxy_pass http://127.0.0.1:8080;
    }

    # Plane/Sailing Server
    location / {
        proxy_pass http://127.0.0.1:8090;
    }
}

```

### HTTPS Setup

You can use Let's Encrypt to enable HTTPS on your Nginx server, so the client can request data securely. The easiest way is using Certbot, so following the [instructions here](https://certbot.eff.org/lets-encrypt/debianbuster-nginx):

```bash
sudo apt install snapd
sudo snap install core
sudo snap refresh core
sudo snap install --classic certbot
sudo ln -s /snap/bin/certbot /usr/bin/certbot
sudo certbot --nginx
```

At this stage you will be prompted to enter your contact details and domain information, but Certbot will take it from there&mdash;it will automatically validate the server as being reachable from the (sub)domain you specified, issue a certificate, install it, and reconfigure nginx to use it.

What you'll probably find is that Certbot has helpfully reconfigured your server to use *only* HTTPS, and HTTP now returns a 404 error. In my opinion it would be best for both versions to provide the same response. To re-enable both HTTP and HTTPS, you need to delete the extra "server" block in `/etc/nginx/sites-enabled/plane-sailing-server.conf` and add `listen 80` back into the main block. You should end up with something like this (with your own domain names in there):

```
server {
    listen 80;
    listen 443 ssl;
    server_name planesailingserver.ianrenton.com;

    location / {
        proxy_pass http://127.0.0.1:8090;
    }

    ssl_certificate /etc/letsencrypt/live/planesailingserver.ianrenton.com/full$
    ssl_certificate_key /etc/letsencrypt/live/planesailingserver.ianrenton.com/$
}

```

Finally, `sudo systemctl restart nginx` and you should now find that Plane/Sailing Server is accessible via both HTTP and HTTPS. The good news is, Certbot should automatically renew your certificate when required, and shouldn't mess with your config again.

Job. Done.

## Client Usage

The Plane/Sailing client, or any other client you write, should do the following:

1. At the start of a user session, make a call to `/first`. The response will be a JSON map of ID to track parameters. All tracks will be included, and if they have a position history, that will be included too. This information should be stored locally, and used to render the tactical picture.
2. Every few seconds (suggested: 10), make a call to `/update`. The response will be the same JSON map, which should be merged with the original one. There are a few complications to this merge:
    * The new data won't include position history, to save bandwidth. Rather than overwriting the client's track with the new one, it should preserve the old position history, appending the new position in the track data to the position history list.
    * Any track the client has that *isn't* in the update data set should be dropped, *except* if it has the `createdByConfig` flag set true. These are the immutable tracks that represent the base station, airports and seaports, and they are only sent in the `/first` call, again to save bandwidth. Their absence in the `/update` call doesn't mean they should be deleted.
3. Rather than just reporting tracks' last known locations, clients may wish to "dead reckon" their current position and update the display with that more often than every 10 seconds. Because the server and client's clocks may not match, the server provides a "time" field (milliseconds since UNIX epoch) in every response. The position data for each track has a time field too. The combination of these can be used to determine how old the track's data is without having to use the local system clock.

Clients are of course free to set their own policies about what tracks to show and hide, how to present the data, etc. If you are writing your own client or fork of Plane/Sailing, I am happy to receive pull requests to add new data into the API.
