package embs;

/**
 * @author: Y384****
 * @update: 2020-01-28
 */
/**
 * Object to represent a single Sink. Designed to make handling of sinks easier in the 
 * Source.java file so my code can be more efficient, use less power and
 * be easier to debug.
 */
public class SinkObject {
	// Calculation relevant attributes for Sink
    private long T = -1;
    private int beaconN = -1;
    private long beaconT = -1;
    private boolean broadcastSet = false;
	private long nextBeaconT;

	// Key attributes of Sink
    private byte channel;
    private byte panid;
    private byte address;
    
    public SinkObject(byte channel_in, byte panid_in, byte address_in) {
    	this.address = address_in;
    	this.channel =  channel_in;
    	this.panid =  panid_in;
    }    
    
    // 'Getters' for Sink object, condensed
    public byte getAddress() {return this.address;}
    public int getBeaconN() {return this.beaconN;}
	public long getBeaconT() {return this.beaconT;}
    public byte getChannel() {return this.channel;}
    public boolean getBroadcastSet(){return this.broadcastSet;}
    public long getnextBeaconT(){return this.nextBeaconT;}
    public byte getPanid() {return this.panid;}
	public long getT() {return this.T;}
	
	
	// 'Setters' for Sink object, condensed
	public void setBeaconN(int beaconN) {this.beaconN = beaconN;}
	public void setBeaconT(long beaconT) {this.beaconT = beaconT;}
	public void setT(long t) {this.T = t;}
	public void setnextBeaconT(long l) {this.nextBeaconT = l;}
	public void broadcastSetter(boolean value){this.broadcastSet = value;}
	public void addBeacon(int n_update, long t_update){
    	this.beaconN = n_update;
    	this.beaconT = t_update;
    }}