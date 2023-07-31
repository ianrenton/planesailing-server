package com.ianrenton.planesailing.utils;

import com.ianrenton.planesailing.app.TrackTable;
import com.ianrenton.planesailing.comms.APRSTCPClient;
import com.ianrenton.planesailing.data.AISTrack;
import com.ianrenton.planesailing.data.Track;
import com.ianrenton.planesailing.data.TrackType;
import net.ab0oo.aprs.parser.APRSPacket;
import net.ab0oo.aprs.parser.Parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * Hacky util to read Direwolf-style ASCII dumps of APRS packets
 * extracted from syslog. Used once to recover knowledge of APRS
 * base stations after the track data store was unintentionally
 * wiped.
 */
public class APRSASCIIDumpParser {

    public static void main(String[] args) throws IOException {
        DataMaps.initialise();
        File f = new File("/home/ian/Documents/all2.txt");
        BufferedReader br = new BufferedReader(new FileReader(f));
        TrackTable tt = new TrackTable();
        APRSTCPClient dummyClient = new APRSTCPClient("", 0, tt);

        // Read in file, convert to APRS packets and load into temporary track table
        String s;
        while ((s = br.readLine()) != null) {
            //System.out.println(s);
            try {
                APRSPacket p = Parser.parse(s);
                dummyClient.addDataToTrack(p);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        // Remove mobile tracks as they are not really current,
        // leaving only base stations
        for (Iterator<Entry<String, Track>> it = tt.entrySet().iterator(); it.hasNext(); ) {
            Track t = it.next().getValue();
            try {
                if (t.getTrackType() == TrackType.APRS_MOBILE) {
                    it.remove();
                }
            } catch (Exception ex) {
                // This is fine, carry on
            }
        }

        // Add a couple of AIS tracks I've struggled to get back via radio
        AISTrack jobourg = new AISTrack(2275200);
        jobourg.setTrackType(TrackType.AIS_SHORE_STATION);
        jobourg.setFixed(true);
        jobourg.setShoreStation(true);
        jobourg.setName("VTS JOBOURG");
        jobourg.addPosition(49.684, -1.9075);
        tt.put("2275200", jobourg);
        AISTrack ch1 = new AISTrack(992271202);
        ch1.setTrackType(TrackType.AIS_ATON);
        ch1.setFixed(true);
        ch1.setAtoN(true);
        ch1.setName("CH1");
        ch1.addPosition(49.72, -1.701667);
        tt.put("992271202", ch1);

        tt.printStatusData();

        // Load a track data store, merge the new data in, and re-save
        TrackTable newTT = new TrackTable();
        newTT.loadFromFile(new File("/home/ian/code/planesailing-server/target/track_data_store.dat"));
        newTT.printStatusData();
        newTT.copy(tt);
        newTT.printStatusData();
        newTT.saveToFile(new File("/home/ian/code/planesailing-server/target/track_data_store_merged.dat"));

    }
}
