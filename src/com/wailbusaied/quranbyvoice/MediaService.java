package com.wailbusaied.quranbyvoice;

import java.io.IOException;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

/*
 * This is demo code to accompany the Mobiletuts+ series:
 * Android SDK: Creating a Music Player
 * 
 * Sue Smith - February 2014
 */

public class MediaService extends Service implements 
MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
MediaPlayer.OnCompletionListener, OnAudioFocusChangeListener {

	//media player
	private MediaPlayer player;
	//song list
//	private ArrayList<Song> songs;
	//current position
//	private int songPosn;
	//File to play
	private String fileName;
	//binder
	private final IBinder mediaBind = new MediaBinder();
	
	Callbacks activity;
	Handler handler = new Handler();
	
	Runnable serviceRunnable = new Runnable() {
    	@Override
    	public void run() {	
    		activity.updateClient(true);
    	}
	};
	
	int iDelay = 0;
	boolean playQuran = false;
	boolean iAudioPlay = false;
	int firstPlay = 0;
	boolean iNewSora = false;
	int iReciter = 0;

	public void onCreate(){
		//create the service
		super.onCreate();
		//initialize position
//		songPosn=0;
		//initialize file name
		fileName = "";

		//initialize
		initMediaPlayer();
		
		AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
		    AudioManager.AUDIOFOCUS_GAIN);

		if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
		    // could not get audio focus.
		}
	}

	public void initMediaPlayer(){
		//create player
		player = new MediaPlayer();
		//set player properties
		player.setWakeMode(getApplicationContext(), 
				PowerManager.PARTIAL_WAKE_LOCK);
		player.setAudioStreamType(AudioManager.STREAM_MUSIC);
		//set listeners
		player.setOnPreparedListener(this);
		player.setOnCompletionListener(this);
		player.setOnErrorListener(this);
	}

	//pass song list
//	public void setList(ArrayList<Song> theSongs){
//		songs=theSongs;
//	}
	
	//pass file to be played
	public void setFileName(String file){
		fileName = file;
	}

	//binder
	public class MediaBinder extends Binder {
		MediaService getService() { 
			return MediaService.this;
		}
	}

	//activity will bind to service
	@Override
	public IBinder onBind(Intent intent) {
		return mediaBind;
	}

	//release resources when unbind
	@Override
	public boolean onUnbind(Intent intent){
		player.stop();
		player.release();
		return false;
	}
	
	public boolean isPlaying(){
		if(player.isPlaying())
			return true;
		else
			return false;
	}
	
	public void pause(){
		player.pause();
	}

	//play Instruction
	public void playInstruction(){
		AssetFileDescriptor afd = null;
		//play
		player.reset();
		//get song
//		Song playSong = songs.get(songPosn);
		//get id
//		long currSong = playSong.getID();
		//set uri
//		Uri trackUri = ContentUris.withAppendedId(
//				android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
//				currSong);
		//set the data source
		try {
			afd = getAssets().openFd(fileName);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
			
		 try {
			player.setDataSource(afd.getFileDescriptor(),afd.getStartOffset(),afd.getLength());
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		try{ 
//			player.setDataSource(getApplicationContext(), trackUri);
//			player.setDataSource(fileName);
//		}
//		catch(Exception e){
//			Log.e("MEDIA SERVICE", "Error setting data source", e);
//		}
		player.prepareAsync(); 
	}

	//play Instruction
	public void playMedia(){
		//play
		player.reset();
		try {
			if(iNewSora){
				player.setDataSource(basmalahReader());
			} else{
				player.setDataSource(fileName);
			}
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		player.prepareAsync(); 
	}
	
	//Stop playing
	public void stopMedia(){
		player.stop();
	}
	
	//Reset player
		public void resetMedia(){
			player.reset();
		}
	
	//Set delay
	public void setDelay(int delay){
		iDelay = delay;
	}
	
	//Set delay
	public void setNewSora(boolean newSora,int reciter){
		iNewSora = newSora;
		iReciter = reciter;
	}
	
	//Media playing Quran Files
	public void setQuranPlay(boolean activityFlag){
		playQuran = activityFlag;
	}
	
	//Set first time playing media flag
	public void setFirstPlay(int activityFlag){
		firstPlay = activityFlag;
	}
	
	//Set iAudioPlay
	public void setAudioPlay(boolean activityFlag){
		iAudioPlay = activityFlag;
	}
	
	//set the song
//	public void setSong(int songIndex){
//		songPosn=songIndex;	
//	}

	
	//Here Activity register to the service as Callbacks client
    public void registerClient(Activity activity){
        this.activity = (Callbacks)activity;
    }
    
    void StopRecitation(Boolean bChangeCurrentAya) {
		if (bChangeCurrentAya) {
			playQuran = false;
			iAudioPlay = false;
			firstPlay = 0;
		}
		if (player != null){
			player.stop();
		}
		handler.removeCallbacks(serviceRunnable);
//		stateMediaPlayer = stateMP_Stop;
	}
    
	//callbacks interface for communication with service clients! 
    public interface Callbacks{
        public void updateClient(boolean completion);
    }

	@Override
	public void onCompletion(MediaPlayer mp) {
		// TODO Auto-generated method stub
		if(playQuran && iNewSora){
			iNewSora = false;
			playMedia();
		}
		else if(playQuran){
			handler.postDelayed(serviceRunnable, iDelay*1000);
		}
		else{
			StopRecitation(true);
		}
//		activity.updateClient(true);		
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		Log.e("MEDIAPLAYER ERRORS",
	            "what: " + what + "  extra: "   + extra);
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		//start playback
		mp.start();
	}
	
	public void onAudioFocusChange(int focusChange) {
	    switch (focusChange) {
	        case AudioManager.AUDIOFOCUS_GAIN:
	            // resume playback
	            if (player == null){
	            	initMediaPlayer();
	            }
	            else if (!player.isPlaying()){
	            	if(playQuran){
	            		playMedia();
	            	}
	            }
	            player.setVolume(1.0f, 1.0f);
	            break;

	        case AudioManager.AUDIOFOCUS_LOSS:
	            // Lost focus for an unbounded amount of time: stop playback and release media player
	            if (player.isPlaying()){
	            	player.stop();
	            }
	            player.release();
	            player = null;
	            break;

	        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
	            // Lost focus for a short time, but we have to stop
	            // playback. We don't release the media player because playback
	            // is likely to resume
	            if (player.isPlaying()){
	            	player.pause();
	            }
	            break;

	        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
	            // Lost focus for a short time, but it's ok to keep playing
	            // at an attenuated level
	            if (player.isPlaying()){
	            	player.setVolume(0.1f, 0.1f);
	            }
	            break;
	    }
	}
	
	String basmalahReader(){
		switch(iReciter){
			case 0:
				return Environment.getExternalStorageDirectory().getAbsolutePath()+"/QuranByVoice/" + "Audio/Shaatri/"
					+ "001001.aud";
			case 1:
				return Environment.getExternalStorageDirectory().getAbsolutePath()+"/QuranByVoice/" + "Audio/Ajmi/"
					+ "001001.aud";
			case 2:
				return Environment.getExternalStorageDirectory().getAbsolutePath()+"/QuranByVoice/" + "Audio/Basfar/"
					+ "001001.aud";
			case 3:
				return Environment.getExternalStorageDirectory().getAbsolutePath()+"/QuranByVoice/" + "Audio/Abdulbasit/"
					+ "001001.aud";
			case 4:
				return Environment.getExternalStorageDirectory().getAbsolutePath()+"/QuranByVoice/" + "Audio/Sudais/"
					+ "001001.aud";
			case 5:
				return Environment.getExternalStorageDirectory().getAbsolutePath()+"/QuranByVoice/" + "Audio/Hudaifi/"
					+ "001001.aud";
			case 6:
				return Environment.getExternalStorageDirectory().getAbsolutePath()+"/QuranByVoice/" + "Audio/Muaiqly/"
					+ "001001.aud";
			case 7:
				return Environment.getExternalStorageDirectory().getAbsolutePath()+"/QuranByVoice/" + "Audio/Minshawi/"
					+ "001001.aud";
			case 8:
				return Environment.getExternalStorageDirectory().getAbsolutePath()+"/QuranByVoice/" + "Audio/Husari/"
					+ "001001.aud";
			case 9:
				return Environment.getExternalStorageDirectory().getAbsolutePath()+"/QuranByVoice/" + "Audio/Meshary/"
					+ "001001.aud";
			case 10:
				return Environment.getExternalStorageDirectory().getAbsolutePath()+"/QuranByVoice/" + "Audio/Ghamdi/"
					+ "001001.aud";
			case 11:
				return Environment.getExternalStorageDirectory().getAbsolutePath()+"/QuranByVoice/" + "Audio/Shuraim/"
					+ "001001.aud";
			case 12:
				return Environment.getExternalStorageDirectory().getAbsolutePath()+"/QuranByVoice/" + "Audio/Rifai/"
					+ "001001.aud";
			default:
				return Environment.getExternalStorageDirectory().getAbsolutePath()+"/QuranByVoice/" + "Audio/Meshary/"
				+ "001001.aud";
		}
	}

}
