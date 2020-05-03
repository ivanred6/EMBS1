package embs;

import com.ibm.saguaro.system.*;

/**
 * @author: Y384****
 * @update: 2020-01-28
 */
public class Source {
	
	

	// Initialise Source variables
	private static int activeSink = 1;
	private static int lastActiveSink;
	private static long moteJitterBound = 5; // constraint on jitter for all sinks, to try and mitigate risk of sync errors
	private static byte[] xmit; // Xmit: byte array sent during transmission
	// Assessment FAQ hard-coded constraints
	private static long minimum_t = 250;
	private static long maximum_t = 1500;
	
	// Set up timers and the sink/channel array
	private static Timer timerForSinkA;
	private static Timer timerForSinkB; 
	private static Timer timerForSinkC;
	
	
	// Array of Sink objects, extensible prior to runtime
	private static SinkObject[] arrayOfReceivingSinks = 
	{
		new SinkObject((byte) 0, (byte) 0x11, (byte) 0x11), // Sink A
		new SinkObject((byte) 1, (byte) 0x12, (byte) 0x12), // Sink B
		new SinkObject((byte) 2, (byte) 0x13, (byte) 0x13) // Sink C
	};
	// Initialise Source's radio
	private static Radio radio = new Radio(); 	
	
	static {
		// Initialise all timers
		timerForSinkA = new Timer();
		timerForSinkB = new Timer();
		timerForSinkC = new Timer();
		// Get params set for all sinktimers
		timerForSinkA.setParam((byte) 0);
		timerForSinkB.setParam((byte) 1);
		timerForSinkC.setParam((byte) 2);
		
		// Open the default radio
		radio.open(Radio.DID, null, 0, 0);
		
		// Sink timer callbacks
		timerForSinkA.setCallback(new TimerEvent(null){
			@Override
			public void invoke(byte param, long time){Source.sinkBroadcaster(param, time);}});

		timerForSinkB.setCallback(new TimerEvent(null){
			@Override
			public void invoke(byte param, long time){Source.sinkBroadcaster(param, time);}});

		timerForSinkC.setCallback(new TimerEvent(null){
			@Override
			public void invoke(byte param, long time){Source.sinkBroadcaster(param, time);}});
		
		// Calibrate Source radio given first active sink (chosen arbitrarily)
		byte initChannel = arrayOfReceivingSinks[activeSink].getChannel();
		radio.setChannel(initChannel);
		byte initPiD = arrayOfReceivingSinks[activeSink].getPanid();
		radio.setPanId(initPiD, true);
		byte initSAdr = arrayOfReceivingSinks[activeSink].getAddress();
		radio.setShortAddr(initSAdr);
		
		// Build the frame to be transmitted as far as is (definitely) known
		xmit = new byte[12];
		xmit[0] = Radio.FCF_BEACON;
		xmit[1] = Radio.FCA_SRC_SADDR|Radio.FCA_DST_SADDR;
		Util.set16le(xmit, 9, arrayOfReceivingSinks[activeSink].getAddress()); // own short address
		xmit[11] = (byte)0xEE; // Payload to be received by Leandro's sinks! (We can adjust this if we want)
		
		// Transmission Callback for sinks
		radio.setTxHandler(new DevCallback(null){
		@Override
		public int invoke(int flags, byte[] data, int len, int info, long time) {
			return Source.txCallback(flags, data, len, info, time);
		}});
		
		// Receive callback handling beacons from our set of sinks
		radio.setRxHandler(new DevCallback(null){
		@Override
		public int invoke (int flags, byte[] data, int len, int info, long time) {
			return  Source.onReceive(flags, data, len, info, time);
		}});
		
		// Start Receiving (listening) on Source radio as calibrated above
		radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
	}
	

	/**
	* Calculate broadcast time and schedule callback to broadcast
	* @param beaconN: latest beacon's payload value
	* @param sink: index of sink (in arrayOfReceivingSinks array) for which to schedule broadcast
	* @param t: interval between beacon arrivals (in milliseconds) of channel
	*/
	private static void buildBroadC(int beaconN, int sink, long t) {
		long broadC = beaconN * t;
		setupBroadcastCallback(broadC, sink); // setup broadcast and callback
		arrayOfReceivingSinks[activeSink].broadcastSetter(true); // set to avoid creating multiple callbacks for the same receive period
	}
	
	
	/**
	 * Update beacon attributes for a specific sink
	 * @param n = best estimate for 'n' of sink
	 * @param time = current time when procedure called
	 * @param sink = ID of sink, referencing array storing sink objects
	 */
	private static void sinkBeaconUpdate(int n, long time, int sink) {
		arrayOfReceivingSinks[sink].setBeaconN(n);
		arrayOfReceivingSinks[sink].setBeaconT(time);
	}
	
	/**
	* Verify whether estimate for period satisfies system constraints
	* including the hard coded jitter variable
	* @param period: 'best guess' period to check
	* @return boolean inferring validity
	*/
	protected static boolean periodValidChecker(long period){
		boolean minJitter = minimum_t-moteJitterBound<=period;
		boolean maxJitter = period <= maximum_t+moteJitterBound;
		boolean valid = minJitter && maxJitter;  
		return valid;
	}
	
	/**
	* Send message to sink
	* @param sink: Reference to desired sink
	* @param time: current (Source) clock time (MS)
	*/
	protected static void sinkBroadcaster(byte sink, long time){
		if (radio.getState()==Device.S_RXEN)
		{
		radio.stopRx();
		}
		lastActiveSink = activeSink; 
		
		// As we know the sink, finish building frame
		Util.set16le(xmit, 3, arrayOfReceivingSinks[sink].getPanid()); // set destination PAN id
		Util.set16le(xmit, 5, 0xFFFF); // set broadcast address
		Util.set16le(xmit, 7, arrayOfReceivingSinks[sink].getPanid()); // set own PAN address
		
		radioCalibrate(sink);
		// Fire at sink, using an offset to hopefully get within the period
		long offset = (long)0.5 * arrayOfReceivingSinks[sink].getT(); 
		long firetime = time + Time.toTickSpan(Time.MILLISECS, offset);
		radio.transmit(Device.TIMED, xmit, 0, 12, firetime); 
	}

	
	
	/**
	* Set alarm on relevant timer (for the sink we want to listen to)
	* @param time: relative time in MS
	* @param sink: Desired sink
	*/
	private static void setupBroadcastCallback(long time, int sink){
		if (sink == 0)
		{ 
			timerForSinkA.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, time));
		} 
		if (sink == 1) 
		{
			timerForSinkB.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, time));
		} 
		if (sink == 2) 
		{
			timerForSinkC.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, time));
		}
	}
	
	
		/**
	* Recalibrate radio to next desired sink
	* @param sink: index of sink in arrayOfReceivingSinks array to retrieve radio configuration from
	*/
	private static void radioCalibrate(int sink){
		if (radio.getState()==Device.S_RXEN)
		{ 
			radio.stopRx();
		}
		radio.setChannel(arrayOfReceivingSinks[sink].getChannel()); // set channel
		radio.setPanId(arrayOfReceivingSinks[sink].getPanid(), true); // set destination pan id
		
		activeSink = sink;
	}
	
	
	/**
	 * Handles receipt of a frame (or end of reception period)
	 */
	private static int onReceive (int flags, byte[] data, int len, int info, long time) {	
		if (data == null) // End of receipt period
		{ 
			return 0; 
		}
		// Store payload and time
		long currentTime = Time.currentTime(Time.MILLISECS); 
		int n = data[11];
		int beacoN = arrayOfReceivingSinks[activeSink].getBeaconN();
		// If we get a beacon without having found one on the channel before,
		// switch the channel (chosen via EDF scheduling) and set the current channel to awake at (ideally) the 
		// next sync phase (10t + t ==> 11*minimum_t as we can't assume t yet)
		if (n == 1 && (beacoN == -1)) 
		{
			long minInterval = currentTime + (11 * minimum_t);
			arrayOfReceivingSinks[activeSink].setnextBeaconT(minInterval);	
			calibrateAndReceiveRadio();
			return 0;
		}
		
		// If a broadcast is scheduled for the sink, 
		// update the beacon values, and listen to a different channel
		// this should improve utilisation
		boolean fireChannelSet = arrayOfReceivingSinks[activeSink].getBroadcastSet();
		if (fireChannelSet) 
		{
			sinkBeaconUpdate(n, currentTime, activeSink);
			calibrateAndReceiveRadio();			
		} 
		else 
		{			// No Broadcast scheduled for current active channel
			long temp_t = arrayOfReceivingSinks[activeSink].getT();
			boolean tForChannel = temp_t != -1;
			if (tForChannel)
			{
				buildBroadC(n, activeSink, temp_t); 
				calibrateAndReceiveRadio();
			} 
			else 
			{ // Don't know t, let's work it out!
				long t_latest = arrayOfReceivingSinks[activeSink].getBeaconT();
				int n_latest =  arrayOfReceivingSinks[activeSink].getBeaconN();
				if (ntLatestValid(t_latest, n_latest))
				{
					int calculatedN = n_latest - n;
					long calculatedT = currentTime - t_latest;
					boolean jitterAndNT = calculatedN > 0 && (calculatedT-moteJitterBound <= (maximum_t*calculatedN));
					long t = calculatedT/calculatedN; // Our guess for period of this Sink
					if (jitterAndNT && periodValidChecker(t)) 
					{
						// If guess(t) and jitter consideration valid
						// Kick radio into action and observe our next channel
						arrayOfReceivingSinks[activeSink].setT(t);
						buildBroadC(n, activeSink, t); 
						calibrateAndReceiveRadio();
					}
				}
			}
			sinkBeaconUpdate(n, currentTime, activeSink); // Update Sink Object 
		}
		return 0; 
	}
	
	
	/**
	 * Helper function to calibrate and then get the radio receiving
	 */
	private static void calibrateAndReceiveRadio() {
		radioCalibrate(EDFSinkPicker(activeSink)); 
		radio.startRx(Device.TIMED, Time.currentTicks()+Time.toTickSpan(Time.MILLISECS, 10), Time.currentTicks()+0x7FFFFFFF);
	}
	
	
	/**
	 * Determines if latest n and t estimates are permissible
	 * @param t_latest
	 * @param n_latest
	 * @return a boolean if t and n are valid 
	 */
	private static boolean ntLatestValid(long t_latest, int n_latest) {
		return (t_latest != -1) && (n_latest != -1);
	}
	
	/**
	* Sink selection function, modelled off EDF scheduling to attempt a near-optimal scheduling
	* capability of source node. 
	* @param sink: current sink index to avoid switching channel to same sink
	* @return new sink index
	**/
	private static int EDFSinkPicker(int sink) {
		// Declare local working variables
		long nbt = -1;
		int nbSinkInd = -1;
		for (int y = 0; y < arrayOfReceivingSinks.length; y++) 
		{
			if (sink != y) 
			{
				long y_t = arrayOfReceivingSinks[y].getnextBeaconT();
				
				if (YTparamValid(y_t, nbt)) {
					nbt = y_t;
					nbSinkInd = y;
				}
			}
		}
		if (nbSinkInd < 0) 
		{
			return (sink + 1) % 3; 
		} 
		
		return nbSinkInd;
	}
	
	/**
	 * Validates y_t, as a supporting function for scheduling
	 */
	private static boolean YTparamValid(long y_t, long nbt) {
		if (nbt > y_t && nbt < 0) {
			return true;
		} else {
			return false;
		}
	}
	
	/**
	* Callback for post-transmission operations
	**/
	private static int txCallback(int flags, byte[] data, int len, int info, long time) {
		long active_t = arrayOfReceivingSinks[activeSink].getT();
		long active_beacon = active_t * 11; // relative beacon time
		active_beacon = active_beacon + Time.currentTime(Time.MILLISECS); // add to current time
		arrayOfReceivingSinks[activeSink].setnextBeaconT(active_beacon); 
		arrayOfReceivingSinks[activeSink].broadcastSetter(false); // Reset sink
		// Return to last channel if we don't know 't'
		long last_t = arrayOfReceivingSinks[lastActiveSink].getT(); 
		if (last_t < 0)
		{
			radioCalibrate(lastActiveSink);
		} 
		else 
		{
			// use standard (EDF) approach
			radioCalibrate(EDFSinkPicker(activeSink));
		}
		radio.startRx(Device.TIMED, Time.currentTicks()+Time.toTickSpan(Time.MILLISECS, 10), Time.currentTicks()+0x7FFFFFFF); // Restart radio
		return 0;
	}

}