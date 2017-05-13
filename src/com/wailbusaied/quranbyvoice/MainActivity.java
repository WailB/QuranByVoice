package com.wailbusaied.quranbyvoice;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;

import com.wailbusaied.quranbyvoice.MediaService.MediaBinder;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.Toast;

public class MainActivity extends Activity implements MediaService.Callbacks, Observer {

	static final int START_REQUEST_CODE=0;
	static final int SURA_REQUEST_CODE=1;
	static final int AYA_REQUEST_CODE=2;
	static final int SETTINGS_REQUEST_CODE=3;
	static final int QARI_REQUEST_CODE=4;
	static final int REPEAT_REQUEST_CODE=5;
	static final int RECITEMETHOD_REQUEST_CODE=6;
	static final int EXIT_REQUEST_CODE=100;
	static final int FILENOTFOUND_REQUEST_CODE=200;
		
	String TAG="RUPAM";
//	EditText edWords;
	TextToSpeech tts;
	RecognitionService rs;
	SpeechRecognizer sr;
	ImageButton mainSpeechButton;

	
	// Media Player Service
	private MediaService mediaSrv;
	private Intent playIntent;
	private boolean mediaBound=false;
		
	MenuItem item;
	String fileName;
	String filePath;
	String fname;
	int sequence = 0;	//controls different options
						//0: Start Menu
						//1: Sura choice
						//2: Aya choice
						//3: Settings Menu
						//4: Qari choice
						//5: Repeat choice
						//6: Recite method choice
	int firstPlay = 0;
	int aya = 1;
	int sura = 1;
	int qari = 0;
	boolean playQuran = false;
	boolean wrongInstruction = false;
	boolean newSora = false;
	boolean appStart = false;
	
//	public String baseDir = "";
	public Integer iDelay = 0;
	public Integer iReciter = 0;
	public boolean iAudioPlay = false;
	Boolean repeat = false;			// repeats the last sound clip based on the options
	public Integer iReciteMethod = 0;	//controls different recitation methods.
										//0: from current location until end of Quran
										//1: from current location until end of Sora
										//2: Current page only
										//3: current aya only
	public boolean iPrayer = false;		// true to play prayer reciter, false to play quran reciter
	public int iPrayerReciter = 0;		// contains which prayer reciter to be played
	public boolean appStartComplete = false;
	
	SharedPreferences SP;
	
	public Handler handler = new Handler();
	
	private String strCurrentAudioFileName = "";
	private String strCurrentAudioFilePath = "";
	
	private void showToast(String message){
		Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT)
					.show();	
	}	
	
	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
//		edWords=(EditText)findViewById(R.id.edWords);
		item=(MenuItem)findViewById(R.id.menuVoiceRecog);

		////////////////////////////////////////////////
		
		sr= SpeechRecognizer.createSpeechRecognizer(this);
		sr.setRecognitionListener(new listener());
		Log.i(TAG,"Initialized");
		
		SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		

		//////////////// Preparing Directory////////
		try
        {
            Log.d("Starting", "Checking up directory");
 //           File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "QuranByVoice");
            // This location works best if you want the created images to be shared
            // between applications and persist after your app has been uninstalled.
            File mediaStorageDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/QuranByVoice/");
            // Create the storage directory if it does not exist
            if (! mediaStorageDir.exists()){
               if (! mediaStorageDir.mkdir()){
                   Log.e("Directory Creation Failed",mediaStorageDir.toString());
               } else {
                   Log.i("Directory Creation","Success");
               }
            }
        } 
		catch(Exception ex){
           Log.e("Directory Creation",ex.getMessage());    
        }
		filePath=Environment.getExternalStorageDirectory().getAbsolutePath()+"/QuranByVoice/";
		CreateFolders();
		readPreferences();

		/////////////////////////////////////////////
		
		speechButtonListener();
		
	}
	
	@Override
	protected void onStart() {
	  super.onStart();
	  if(playIntent==null){
	    playIntent = new Intent(this, MediaService.class);
	    getApplicationContext().bindService(playIntent, mediaConnection, Context.BIND_AUTO_CREATE);
	    startService(playIntent);
	  }
	}
	
	@Override
	public void onRestart() {
	    super.onRestart();

	    if(appStart){
	    	readPreferences();
	    	mediaSrv.setDelay(iDelay);
	    }
	}
	
	@Override
	protected void onPause(){
		super.onPause();
		
	}
	
	@Override
	protected void onResume(){
		super.onResume();
	}
	
	@Override
	protected void onStop(){
		super.onStop();
		appStart = true;
		SharedPreferences.Editor editor = SP.edit();
		editor.putString("SuraPref", Integer.toString(sura));
		editor.putString("AyaPref", Integer.toString(aya));
		editor.commit();

	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();

		if(mediaSrv != null){
			if(mediaSrv.isPlaying())
				mediaSrv.stopMedia();
			mediaSrv.resetMedia();
		}
		phoneStateListener = null; 

		stopService(playIntent);
		mediaSrv=null;
		System.gc();

	}
	
	void readPreferences(){
		iDelay = Integer.parseInt(SP.getString("Delay", "0"));
		repeat = SP.getBoolean("Repeat",false);
		iReciter = Integer.parseInt(SP.getString("Reciter","0"));
		iReciteMethod = Integer.parseInt(SP.getString("ReciteMethod", "0"));
		aya = Integer.parseInt(SP.getString("AyaPref", "1"));
		sura = Integer.parseInt(SP.getString("SuraPref", "1"));
		
	}
	
	PhoneStateListener phoneStateListener = new PhoneStateListener() {
		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			if (state == TelephonyManager.CALL_STATE_RINGING) {
				// Incoming call: Pause recitation
//				if (stateMediaPlayer == stateMP_Playing) {
				if (mediaSrv.isPlaying()) {
//					player.pause();
					mediaSrv.pause();
//					stateMediaPlayer = stateMP_Pausing;
					return;
				}
			} else if (state == TelephonyManager.CALL_STATE_IDLE) {
				// Not in call: Play recitation
			} else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
				// A call is dialing, active or on hold
				// Incoming call: Pause recitation
//				if (stateMediaPlayer == stateMP_Playing) {
				if (mediaSrv.isPlaying()) {
//					player.pause();
					mediaSrv.pause();
//					stateMediaPlayer = stateMP_Pausing;
					return;
				}
			}
			super.onCallStateChanged(state, incomingNumber);
		}
	};

	public void speechButtonListener() {
		mainSpeechButton = (ImageButton) findViewById(R.id.mainSpeechButton);
		mainSpeechButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
//				if (stateMediaPlayer == stateMP_Playing) {
				if (mediaSrv.isPlaying()) {
//					StopRecitation(true);
					mediaSrv.StopRecitation(true);
				}			
				sequence = 0;
				instructionsPlayer();
			}
		});
	}

	public void instructionsPlayer(){	 
		if (sequence == 0){
//			playInstruction("start.mp3");
			mediaSrv.setFileName("start.mp3");
		} else if(sequence == 1){
//			playInstruction("sora.mp3");
			mediaSrv.setFileName("sora.mp3");
		} else if(sequence == 2){
//			playInstruction("aya.mp3");
			mediaSrv.setFileName("aya.mp3");
		} else if (sequence == 3){
//			playInstruction("settings.mp3");
			mediaSrv.setFileName("settings.mp3");
		} else if (sequence == 4){
//			playInstruction("qari.mp3");
			mediaSrv.setFileName("qari.mp3");
		} else if (sequence == 5){
//			playInstruction("repeat.mp3");
			mediaSrv.setFileName("repeat.mp3");
		} else if (sequence == 6){
//			playInstruction("recitemethod.mp3");
			mediaSrv.setFileName("recitemethod.mp3");
		} else if (sequence == 100){
//			playInstruction("exit.mp3");
			mediaSrv.setFileName("exit.mp3");
		}
		
		mediaSrv.playInstruction();
		
		Handler myHandler = new Handler();
		if(sequence == 0 || sequence == 3)
			myHandler.postDelayed(mRunnable, 2500);//Message will be delivered after short delay
		else if(sequence == 100 || sequence == 200)
			myHandler.postDelayed(mRunnable, 3700);//Message will be delivered after short delay
		else
			myHandler.postDelayed(mRunnable, 2000);//Message will be delivered after short delay
	}

	//Here's a runnable/handler combo
	private Runnable mRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			String choice = "";
			int code = START_REQUEST_CODE;
			if (sequence == 0){
				choice = "اذكر الامر المطلوب";
				code = START_REQUEST_CODE;
			} else if(sequence == 1){
				choice = "اذكر اسم السورة";
				code = SURA_REQUEST_CODE;
			} else if(sequence == 2){
				choice = "اذكر رقم الايه";
				code = AYA_REQUEST_CODE;
			} else if(sequence == 3){
				choice = "الاعدادات. اذكر الاختيار المطلوب";
				code = SETTINGS_REQUEST_CODE;
			} else if(sequence == 4){
				choice = "اذكر اسم القاريء";
				code = QARI_REQUEST_CODE;
			} else if(sequence == 5){
				choice = "تفعيل اعادة القراءة";
				code = REPEAT_REQUEST_CODE;
			} else if(sequence == 6){
				choice = "طريقة التلاوة";
				code = RECITEMETHOD_REQUEST_CODE;
			} else if(sequence == 100){
				choice = "هل تريد الخروج من البرنامج";
				code = EXIT_REQUEST_CODE;
			}
			
			
			Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
			intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
		    intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getClass().getPackage().getName());
		    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA");
//		    intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,10);
		    intent.putExtra(RecognizerIntent.EXTRA_PROMPT, choice);
		    startActivityForResult(intent, code);
		}
	};	

	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
		if (requestCode == START_REQUEST_CODE && resultCode == RESULT_OK)
        {
        	int n = 0;
        	boolean checkStart = false;
        	String[] startName = getResources().getStringArray(R.array.Start_array);
            // Populate the wordsList with the String values the recognition engine thought it heard
            ArrayList<String> matches = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
			
            while(checkStart != true){
            	for(int i = 0; i < matches.size(); i++){
            		if(matches.get(i).equals(startName[n]))
            			checkStart = true;
            	}
            	n++;
            	if(n == 4)
            		break;
            }
          
            if(checkStart == true){
            	showToast(" تم تنفيذ الامر "+ startName[n-1]);
            	if(n == 1){
            		playQuran = true;
            		mediaSrv.setQuranPlay(true);
            		PlayRecitation();
            	}
            	else if(n == 2){
            		sequence = 1;
            		aya = 1;
            		sura = 1;
            		instructionsPlayer();
            	}
            	else if(n == 3){
            		sequence = 3;
            		instructionsPlayer();
            	}
            	else if(n == 4){
            		sequence = 100;
            		instructionsPlayer();
            	}
            }
            else{
            	showToast(" لا يوجد الامر " + matches.get(0)+ " الرجاء الاعادة ");   
 //           	instructionNotCorrect();
            }
        }
        if (requestCode == SURA_REQUEST_CODE && resultCode == RESULT_OK)
        {
        	int n = 0;
        	boolean checkSura = false;
        	String[] suraName = getResources().getStringArray(R.array.SoraName_array);
            // Populate the wordsList with the String values the recognition engine thought it heard
            ArrayList<String> matches = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
			
            while(checkSura != true){
            	for(int i = 0; i < matches.size(); i++){
            		if(matches.get(i).equals(suraName[n]))
            			checkSura = true;
            	}
            	n++;
            	if(n == 115)
            		break;
            }
          
            if(checkSura == true){
            	if(n <= 114){
            		showToast(suraName[n-1]+ " موجودة ");
            		sura = n;
            		sequence = 2;
    				instructionsPlayer();
            	}
            	else {
            		showToast("سيتم الرجوع الى القائمة السابقة");
            		sequence = 0;
            		instructionsPlayer();
            	}
            }
            else{
            	showToast(matches.get(0)+ " غير صحيحة ");
//            	instructionNotCorrect();
            	sequence = 1;
            	instructionsPlayer();
            }      
        }
        if (requestCode == AYA_REQUEST_CODE && resultCode == RESULT_OK)
        {
            // Populate the wordsList with the String values the recognition engine thought it heard
            ArrayList<String> matches = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            
        	int myNum = 0;
        	boolean checkNumbers = false;
        	boolean mReturn = false;
            try {
                myNum = Integer.parseInt(matches.get(0).toString());
  //              showToast(matches.get(0));
                aya = myNum;
                checkNumbers = true;
            } catch(NumberFormatException nfe) {
            	String[] numbers = getResources().getStringArray(R.array.Numbers_array);
            	
            	int n = 0;
            	while(checkNumbers != true){
                	for(int i = 0; i < matches.size(); i++){
                		if(matches.get(i).equals(numbers[n])){
                			if(n <= 9){
                				checkNumbers = true;
                				aya = n+1;
                			}
                			else if (n == 10)
                				mReturn = true;
                		}
                	}
                	n++;
                	if(n == 11)
                		break;
                }
            	if(!checkNumbers)
            		System.out.println("Could not parse " + nfe);
            }  
            
            if(checkNumbers == true){
            	showToast( " سيتم البدأ بتلاوة الايه رقم " + aya);
    			playQuran = true;
    			mediaSrv.setQuranPlay(true);
                PlayRecitation();
            }
            else{
            	if(mReturn){
            		showToast("سيتم الرجوع الى القائمة السابقة");
            		sequence = 1;
            		instructionsPlayer();
            	} else {
            		showToast(" الايه رقم " + matches.get(0)+ " غير موجودة ");
//            		instructionNotCorrect();
            		sequence = 2;
            		instructionsPlayer();
            	}
            }
        }
        if (requestCode == SETTINGS_REQUEST_CODE && resultCode == RESULT_OK)
        {
        	int n = 0;
        	boolean checkSettings = false;
        	String[] SettingsName = getResources().getStringArray(R.array.Settings);
            // Populate the wordsList with the String values the recognition engine thought it heard
            ArrayList<String> matches = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
			
            while(checkSettings != true){
            	for(int i = 0; i < matches.size(); i++){
            		if(matches.get(i).equals(SettingsName[n]))
            			checkSettings = true;
            	}
            	n++;
            	if(n == 5)
            		break;
            }
          
            if(checkSettings == true){
            	showToast(" اختيار " + SettingsName[n-1]+ " موجود ");
            	if(n == 1 || n == 2){
            		sequence = 4;
            		instructionsPlayer();
            	}
            	else if(n == 3){
            		sequence = 5;
            		instructionsPlayer();
            	} 
            	else if(n == 4){
            		sequence = 6;
            		instructionsPlayer();
            	}
            	else if(n == 5){
            		showToast("سيتم الرجوع الى القائمة السابقة");
            		sequence = 0;
            		instructionsPlayer();
            	}
            }
            else{
            	showToast(" اختيار " + matches.get(0)+ " غير موجود ");
//            	instructionNotCorrect();
            	sequence = 3;
            	instructionsPlayer();
            }        	
        }
        if (requestCode == QARI_REQUEST_CODE && resultCode == RESULT_OK)
        {
        	int n = 0;
        	boolean checkQari = false;
        	String[] qariName = getResources().getStringArray(R.array.Reciters);
            // Populate the wordsList with the String values the recognition engine thought it heard
            ArrayList<String> matches = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
			
            while(checkQari != true){
            	for(int i = 0; i < matches.size(); i++){
            		if(matches.get(i).equals(qariName[n]))
            			checkQari = true;
            	}
            	n++;
            	if(n == 14)
            		break;
            }
          
            if(checkQari == true){
            	if(n <= 13){
            		showToast(" القاريء " + qariName[n-1]+ " موجود ");
            		iReciter = n-1;
            		SharedPreferences.Editor editor = SP.edit();
            		editor.putString("Reciter", Integer.toString(iReciter));
            		editor.commit();

 //           		playInstruction("instructok.mp3");
            		sequence = 3;
        			instructionsPlayer();
            	}
            	else {
            		showToast("سيتم الرجوع الى القائمة السابقة");
            		sequence = 3;
            		instructionsPlayer();
            	}
            }
            else{
            	showToast(" القاريء " + matches.get(0)+ " غير موجود ");
//            	instructionNotCorrect();
            	sequence = 4;
            	instructionsPlayer();
            }        	
        }
        if (requestCode == REPEAT_REQUEST_CODE && resultCode == RESULT_OK)
        {
        	int n = 0;
        	boolean checkRepeat = false;
        	String[] RepeatName = getResources().getStringArray(R.array.YesNo);
            // Populate the wordsList with the String values the recognition engine thought it heard
            ArrayList<String> matches = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
			
            while(checkRepeat != true){
            	for(int i = 0; i < matches.size(); i++){
            		if(matches.get(i).equals(RepeatName[n]))
            			checkRepeat = true;
            	}
            	n++;
            	if(n == 2)
            		break;
            }
          
            if(checkRepeat == true){
            	SharedPreferences.Editor editor = SP.edit();
            	editor.putBoolean("Repeat", repeat);
            	editor.commit();
            	showToast(" اختيار " + RepeatName[n-1]+ " موجود ");
            	if(n == 1){
            		repeat = true;
  //          		playInstruction("instructok.mp3");
            		sequence = 3;
            		instructionsPlayer();
            	}
            	else if(n == 2){
            		repeat = false;
//            		playInstruction("instructok.mp3");
            		sequence = 3;
            		instructionsPlayer();
            	}
            }
            else{
            	showToast(" اختيار " + matches.get(0)+ " غير موجود ");
            	sequence = 5;
            	instructionsPlayer();
 //           	instructionNotCorrect();
            }        	
        }
        if (requestCode == RECITEMETHOD_REQUEST_CODE && resultCode == RESULT_OK)
        {
        	int n = 0;
        	boolean checkReciteMethod = false;
        	String[] ReciteMethodName = getResources().getStringArray(R.array.ReciteMethod);
            // Populate the wordsList with the String values the recognition engine thought it heard
            ArrayList<String> matches = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
			
            while(checkReciteMethod != true){
            	for(int i = 0; i < matches.size(); i++){
            		if(matches.get(i).equals(ReciteMethodName[n]))
            			checkReciteMethod = true;
            	}
            	n++;
            	if(n == 5)
            		break;
            }
          
            if(checkReciteMethod == true){
            	if(n <= 4){
            		showToast(" الامر " + ReciteMethodName[n-1]+ " موجود ");
            		iReciteMethod = n-1;
            		SharedPreferences.Editor editor = SP.edit();
            		editor.putString("ReciteMethod", Integer.toString(iReciteMethod));
                	editor.commit();
            		sequence = 3;
        			instructionsPlayer();
            	}
            	else{
            		showToast("سيتم الرجوع الى القائمة السابقة");
            		sequence = 3;
            		instructionsPlayer();
            	}
            }
            else{
            	showToast(" الامر " + matches.get(0)+ " غير موجود ");
//            	instructionNotCorrect();
            	sequence = 6;
            	instructionsPlayer();
            }        	
        }
        
        if (requestCode == EXIT_REQUEST_CODE && resultCode == RESULT_OK)
        {
        	int n = 0;
        	boolean checkExit = false;
        	String[] exitName = getResources().getStringArray(R.array.YesNo);
            // Populate the wordsList with the String values the recognition engine thought it heard
            ArrayList<String> matches = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
			
            while(checkExit != true){
            	for(int i = 0; i < matches.size(); i++){
            		if(matches.get(i).equals(exitName[n]))
            			checkExit = true;
            	}
            	n++;
            	if(n == 2)
            		break;
            }
          
            if(checkExit == true){
            	showToast(" اختيار " + exitName[n-1]+ " موجود ");
            	if(n == 1){
            		//exit
 //           		finish();
            		stopService(playIntent);
            		mediaSrv=null;
 //           		System.exit(0);
            		finish();
            	}
            	else if(n == 2){
            		sequence = 0;
            		instructionsPlayer();
            	}
            }
            else{
            	showToast(" اختيار " + matches.get(0)+ " غير موجود ");
//            	instructionNotCorrect();
            	sequence = 100;
        		instructionsPlayer();
            }        	
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		
		int id = item.getItemId();
		switch(id)
		{
			case R.id.menuVoiceRecog:
	        Intent i = new Intent(this, Settings.class);
	        startActivity(i);
	        break;

		}
		return super.onOptionsItemSelected(item);
	}

	////////////////////////////////////////////////////////
	//// Voice Recognition Listener Class
	////////////////////////////////////////////////////////
	
	class listener implements RecognitionListener          
	   {
	            public void onReadyForSpeech(Bundle params)
	            {
	                     Log.d(TAG, "onReadyForSpeech");
	            }
	            public void onBeginningOfSpeech()
	            {
	                     Log.d(TAG, "onBeginningOfSpeech");
	            }
	            public void onRmsChanged(float rmsdB)
	            {
	                     Log.d(TAG, "onRmsChanged");
	            }
	            public void onBufferReceived(byte[] buffer)
	            {
	                     Log.d(TAG, "onBufferReceived");
	            }
	            public void onEndOfSpeech()
	            {
	                     Log.d(TAG, "onEndofSpeech");
	            }
	            public void onError(int error)
	            {
	                     Log.d(TAG,  "error " +  error);
	             
	            }
	            public void onResults(Bundle results)                   
	            {
	                     String str = new String();
	                     Log.d(TAG, "onResults " + results);
	                     ArrayList data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
	                     str = data.get(0).toString();        
	            }
	            public void onPartialResults(Bundle partialResults)
	            {
	                     Log.d(TAG, "onPartialResults");
	            }
	            public void onEvent(int eventType, Bundle params)
	            {
	                     Log.d(TAG, "onEvent " + eventType);
	            }
	   }
	
	//////////////////////////////////////////////
	//// Media Player Functions
	//////////////////////////////////////////////

	public void PlayRecitation() {
		try {
			if(firstPlay == 0){			
//				if ((iReciteMethod == 0) || (iReciteMethod == 2)){
//					String strFirstAya = GetVerseByPage(iCurrentPage);
//					iCurrentAya = Integer.parseInt(strFirstAya);
//					if(iCurrentAya == 0 || iCurrentAya == 1)
//						iCurrenSura = GetSoraIndex(iCurrentPage);
//					else{
//						int k = 0;
//						String[] sorapages = getResources().getStringArray(
//								R.array.SoraValue_array);
//						for(k = 0; k < 114; k++){
//							if(Integer.parseInt(sorapages[k]) == AC.iCurrentPage){
//								AC.iCurrenSura = k;
//								break;
//							}
//						}
//						if(k == 114)
//							AC.iCurrenSura = AC.GetSoraIndex(AC.iCurrentPage);
//					}
					strCurrentAudioFileName = GetFirstRecitationFile(sura, aya);
//				} else if(AC.iReciteMethod == 3){
//					strCurrentAudioFileName = AC.GetFirstRecitationFile();
//				}
//				else {
//					AC.iCurrenSura = AC.GetSoraIndex(AC.iCurrentPage);
//					AC.iCurrentAya = 0;
//					strCurrentAudioFileName = AC
//							.GetFirstRecitationFile();
//				}
				firstPlay = 1;
				mediaSrv.setFirstPlay(1);
			} else {
				aya += 1;
				
	//			if(AC.iReciteMethod != 3)
//					AC.iCurrentPage = AC.getAyaPage(AC.iCurrenSura, AC.iCurrentAya);
				
				Integer iAyaCount = getAyaCount(sura);
				if (aya > iAyaCount) {
					if (sura == 114) {
						if(repeat == false && iReciteMethod == 0){
//							PlayPrayer();
//							StopRecitation(true);
							mediaSrv.StopRecitation(true);
							return;	
						} else if (repeat == false) {
							sura = 1;
							aya = 1;
//							StopRecitation(true);
							mediaSrv.StopRecitation(true);
							return;
						} else {
							switch (iReciteMethod){
								case 0:
									sura = 1;
									aya = 1;
									break;
								case 1:
									sura = 114;
									aya = 1;
									break;
								case 2:
									sura = 112;
									aya = 1;
									break;
								case 3:
									aya -= 1;
									break;
								default:
									sura = 1;
									aya = 1;
							}
						}
					} else {
						if(repeat == true){
							if (iReciteMethod == 1){
								aya = 1;		
							} else if(iReciteMethod == 3){
								aya -= 1;
							} else {
								sura += 1;
								aya = 1;
/*								if (AC.iCurrentPage != last_page && AC.iReciteMethod == 2){
									AC.iCurrentPage = last_page;
									String strFirstAya = AC.GetVerseByPage(AC.iCurrentPage);
									AC.iCurrentAya = Integer.parseInt(strFirstAya);
									AC.iCurrenSura -= 1;
								}*/
							}
						} else {
							switch (iReciteMethod){
								case 0:
									sura += 1;
									aya = 1;
									break;
								case 1:
//									StopRecitation(true);
									mediaSrv.StopRecitation(true);
									return;	
								case 2:
									sura += 1;
									aya = 1;
//									if (AC.iCurrentPage != last_page){
//										StopRecitation(true);
//										return;
//									}
									break;
								case 3:
//									StopRecitation(true);
									mediaSrv.StopRecitation(true);
									return;
								default:
									sura += 1;
									aya = 1;
							}
						}
					}
				} else {
					switch (iReciteMethod){
						case 0:
							break;
						case 1:
							break;
						case 2:
//							AC.iCurrentPage = AC.getAyaPage(AC.iCurrenSura,
//									AC.iCurrentAya);
//							if (AC.iCurrentPage != last_page){
//								if (repeat == true){
//									AC.iCurrentPage = last_page;
//									String strFirstAya = AC.GetVerseByPage(AC.iCurrentPage);
//									AC.iCurrentAya = Integer.parseInt(strFirstAya);
//								} else {
//									StopRecitation(true);
//									return;	
//								}
//							}
							break;
						case 3:
							if(repeat == true)
								aya -= 1;
							else{
//								StopRecitation(true);
								mediaSrv.StopRecitation(true);
								return;
							}
							break;
						default:
					}
				}
			}
			strCurrentAudioFileName = GetFirstRecitationFile(sura, aya);	


			// Choose the reciter audio folder based on the choice of the settings
			
			switch(iReciter){
				case 0:
					strCurrentAudioFilePath = filePath + "Audio/Shaatri/"
							+ strCurrentAudioFileName;
					break;
				case 1:
					strCurrentAudioFilePath = filePath + "Audio/Ajmi/"
							+ strCurrentAudioFileName;
					break;
				case 2:
					strCurrentAudioFilePath = filePath + "Audio/Basfar/"
							+ strCurrentAudioFileName;
					break;
				case 3:
					strCurrentAudioFilePath = filePath + "Audio/Abdulbasit/"
							+ strCurrentAudioFileName;
					break;
				case 4:
					strCurrentAudioFilePath = filePath + "Audio/Sudais/"
							+ strCurrentAudioFileName;
					break;
				case 5:
					strCurrentAudioFilePath = filePath + "Audio/Hudaifi/"
							+ strCurrentAudioFileName;
					break;
				case 6:
					strCurrentAudioFilePath = filePath + "Audio/Muaiqly/"
							+ strCurrentAudioFileName;
					break;
				case 7:
					strCurrentAudioFilePath = filePath + "Audio/Minshawi/"
							+ strCurrentAudioFileName;
					break;
				case 8:
					strCurrentAudioFilePath = filePath + "Audio/Husari/"
							+ strCurrentAudioFileName;
					break;
				case 9:
					strCurrentAudioFilePath = filePath + "Audio/Meshary/"
							+ strCurrentAudioFileName;
					break;
				case 10:
					strCurrentAudioFilePath = filePath + "Audio/Ghamdi/"
							+ strCurrentAudioFileName;
					break;
				case 11:
					strCurrentAudioFilePath = filePath + "Audio/Shuraim/"
							+ strCurrentAudioFileName;
					break;
				case 12:
					strCurrentAudioFilePath = filePath + "Audio/Rifai/"
							+ strCurrentAudioFileName;
					break;
				default:
					strCurrentAudioFilePath = filePath + "Audio/Meshary/"
							+ strCurrentAudioFileName;
			}
	
			File f = new File(strCurrentAudioFilePath);
			if (!f.exists()) {
//				StopRecitation(true);
				mediaSrv.StopRecitation(true);
				sequence = 0;
				playQuran = false;
				firstPlay = 0;
				mediaSrv.setFileName("filesNotFound.mp3");
				mediaSrv.playInstruction();
	//			startActivity(new Intent(this, DownloadActivity.class));
				return;
			} else {
				// It happenes
				if (f.length() == 0) {
//					StopRecitation(true);
					mediaSrv.StopRecitation(true);
					sequence = 0;
					playQuran = false;
					firstPlay = 0;
					mediaSrv.setFileName("filesNotFound.mp3");
					mediaSrv.playInstruction();
//					startActivity(new Intent(this,DownloadActivity.class));
					f.delete();
					return;
				}
				// Set to Readable and MODE_WORLD_READABLE
		        f.setReadable(true, false);
			}

			if(aya == 1 && sura != 9 && sura != 1)
				newSora = true;
			else
				newSora = false;
			
//			stateMediaPlayer = stateMP_PlayBack;
			mediaSrv.setNewSora(newSora, iReciter);
			mediaSrv.setFileName(strCurrentAudioFilePath);
			mediaSrv.playMedia();
//			initMediaPlayer();
		} catch (Throwable t) {
//			StopRecitation(false);
			mediaSrv.StopRecitation(false);
			Toast.makeText(this, "Request failed: " + t.toString(),
					Toast.LENGTH_LONG).show();
		}
	}

	//connect to the service
	private ServiceConnection mediaConnection = new ServiceConnection(){
	  @Override
	  public void onServiceConnected(ComponentName name, IBinder service) {
	    MediaBinder binder = (MediaBinder)service;
	    //get service
	    mediaSrv = binder.getService();
	    //pass list
	    mediaSrv.registerClient(MainActivity.this);
	    mediaBound = true;
	  }
	 
	  @Override
	  public void onServiceDisconnected(ComponentName name) {
		mediaBound = false;
	  }
	};
	
	@Override
    public void updateClient(boolean completion) {
		if(completion){
			PlayRecitation();
		}
	}

	public String GetFirstRecitationFile(Integer iSura, Integer iAya) {
		if(iAya <= 0 && iSura != 9 && iSura <= 0){
			String strFirstAya = String.format(Locale.US, "%03d", 1);
			String strSoraIndex = String.format(Locale.US, "%03d", 1);
			return strSoraIndex + strFirstAya + ".aud";
		} else {
			if ((iSura == 9 || iSura == 1) && iAya == 0){
				iAya = 1;
				aya = 1;
			}
			String strFirstAya = String.format(Locale.US, "%03d", iAya);
			String strSoraIndex = String.format(Locale.US, "%03d", iSura);
			return strSoraIndex + strFirstAya + ".aud";
		}
	}
	
	public Integer getAyaCount(Integer iSura) {
		int page = 0;
		int[] sorapages = {7,286,200,176,120,165,206,75,129,109,123,111,43,52,99,128,111,110,98,135,112,78,118,64,77,227,93,88,69,60,34,30,73,54,45,83,182,88,75,85,54,53,89,59,37,35,38,29,18,45,60,49,62,55,78,96,29,22,24,13,14,11,11,18,12,12,30,52,52,44,28,28,20,56,40,31,50,40,46,42,29,19,36,25,22,17,19,26,30,20,15,21,11,8,8,19,5,8,8,11,11,8,3,9,5,4,7,3,6,3,5,4,5,6};
		if(iSura <= 0)
			iSura = 1;
		page = sorapages[iSura - 1];
		sorapages = null;
		System.gc();

		return page;
	}

	///////////////////////////////////////////////////////
	/// Folder creation
	///////////////////////////////////////////////////////
	
	void CreateFolders() {
		String zipFilePath = "";
		File zipFile;
		String zFilePath = filePath + "Audio";
		String targetLocation = filePath + "Audio";
		
		File file = new File(filePath);
		if (!file.exists())
			file.mkdirs();

		file = new File(filePath + "Audio/");
		if (!file.exists())
			file.mkdirs();
			
		/// Create Shaatri directory and unzip files
		targetLocation = filePath + "Audio/Shaatri";
		file = new File(targetLocation);
		
		if (!file.exists())
			file.mkdirs();
		zipFilePath = filePath + "Audio/Alshaatri_part1.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Alshaatri_part1";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Alshaatri_part2.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Alshaatri_part2";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		
		/// Create Mashary directory and unzip files
		targetLocation = filePath + "Audio/Meshary";
		file = new File(targetLocation);

		if (!file.exists())
			file.mkdirs();
		zipFilePath = filePath + "Audio/Meshary_part1.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Meshary_part1";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Meshary_part2.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Meshary_part2";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Meshary_part3.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Meshary_part3";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		
		/// Create Basfar directory and unzip files
		targetLocation = filePath + "Audio/Basfar";
		file = new File(targetLocation);
		
		if (!file.exists())
			file.mkdirs();
		zipFilePath = filePath + "Audio/Basfar_part1.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Basfar_part1";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Basfar_part2.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Basfar_part2";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Basfar_part3.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Basfar_part3";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		
		/// Create Ajmi directory and unzip files
		targetLocation = filePath + "Audio/Ajmi";
		file = new File(targetLocation);
		
		if (!file.exists())
			file.mkdirs();
		zipFilePath = filePath + "Audio/Ajmi_part1.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Ajmi_part1";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Ajmi_part2.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Ajmi_part2";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		
		/// Create Abdulbasit directory and unzip files
		targetLocation = filePath + "Audio/Abdulbasit";
		file = new File(targetLocation);
		
		if (!file.exists())
			file.mkdirs();
		zipFilePath = filePath + "Audio/Abdulbasit_part1.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Abdulbasit_part1";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Abdulbasit_part2.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Abdulbasit_part2";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Abdulbasit_part3.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Abdulbasit_part3";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}	
		
		/// Create Sudais directory and unzip files
		targetLocation = filePath + "Audio/Sudais";
		file = new File(targetLocation);
		
		if (!file.exists())
			file.mkdirs();
		zipFilePath = filePath + "Audio/Sudais_part1.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Sudais_part1";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Sudais_part2.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Sudais_part2";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		
		/// Create Hudaifi directory and unzip files
		targetLocation = filePath + "Audio/Hudaifi";
		file = new File(targetLocation);
		
		if (!file.exists())
			file.mkdirs();
		zipFilePath = filePath + "Audio/Hudhaify_part1.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Hudhaify_part1";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Hudhaify_part2.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Hudhaify_part2";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		
		/// Create Muaiqly directory and unzip files
		targetLocation = filePath + "Audio/Muaiqly";
		file = new File(targetLocation);
		
		if (!file.exists())
			file.mkdirs();
		zipFilePath = filePath + "Audio/Muaiqly_part1.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Muaiqly_part1";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Muaiqly_part2.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Muaiqly_part2";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		
		/// Create Husari directory and unzip files
		targetLocation = filePath + "Audio/Husari";
		file = new File(targetLocation);
		
		if (!file.exists())
			file.mkdirs();
		zipFilePath = filePath + "Audio/Husari_part1.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Husari_part1";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Husari_part2.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Husari_part2";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Husari_part3.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Husari_part3";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Husari_part4.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Husari_part4";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Husari_part5.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Husari_part5";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		
		/// Create Minshawi directory and unzip files	
		targetLocation = filePath + "Audio/Minshawi";
		file = new File(targetLocation);
		
		if (!file.exists())
			file.mkdirs();
		zipFilePath = filePath + "Audio/Menshawi.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Menshawi";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		
		/// Create Ghamdi directory and unzip files
		targetLocation = filePath + "Audio/Ghamdi";
		file = new File(targetLocation);
		
		if (!file.exists())
			file.mkdirs();
		zipFilePath = filePath + "Audio/Ghamdi_part1.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Ghamdi_part1";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Ghamdi_part2.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Ghamdi_part2";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Ghamdi_part3.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Ghamdi_part3";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		
		/// Create Shuraim directory and unzip files
		targetLocation = filePath + "Audio/Shuraim";
		file = new File(targetLocation);
		
		if (!file.exists())
			file.mkdirs();
		zipFilePath = filePath + "Audio/Shuraim_part1.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Shuraim_part1";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Shuraim_part2.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Shuraim_part2";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		
		/// Create Rifai directory and unzip files
		targetLocation = filePath + "Audio/Rifai";
		file = new File(targetLocation);
		
		if (!file.exists())
			file.mkdirs();
		zipFilePath = filePath + "Audio/Rifai_part1.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Rifai_part1";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		zipFilePath = filePath + "Audio/Rifai_part2.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "Rifai_part2";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
		
		/// Create prayer directory and unzip files
		targetLocation = filePath + "Audio/prayer";
		file = new File(targetLocation);
		
		if (!file.exists())
			file.mkdirs();
		zipFilePath = filePath + "Audio/prayer.zip";
		zipFile = new File(zipFilePath);
		if (zipFile.exists()) {
				String fileName = "prayer";
				unzipWebFile(fileName, zFilePath , targetLocation);
		}
	}
	
	/////////////////////////////////////////////
	// Unzipping files
	///////////////////////////////////////////
	
	private void unzipWebFile(String filename, String zFilePath, String targetLocation) {
//	    String unzipLocation = getExternalFilesDir(null) + "/unzipped";
//	    String filePath = Environment.getExternalStorageDirectory().toString();

//	    Unzipper unzipper = new Unzipper(filename, filePath, unzipLocation);
		Unzipper unzipper = new Unzipper(filename, zFilePath, targetLocation);
	    unzipper.addObserver(this);
	    unzipper.unzip();
	}
/*	
	public static void unzip(File zipFile, File targetDirectory) throws IOException {
	    ZipInputStream zis = new ZipInputStream(
	            new BufferedInputStream(new FileInputStream(zipFile)));
	    try {
	        ZipEntry ze;
	        int count;
	        byte[] buffer = new byte[8192];
	        while ((ze = zis.getNextEntry()) != null) {
	            File file = new File(targetDirectory, ze.getName());
//	            File dir = ze.isDirectory() ? file : file.getParentFile();
	            File dir = targetDirectory;
	            if (!dir.isDirectory() && !dir.mkdirs())
	                throw new FileNotFoundException("Failed to ensure directory: " +
	                        dir.getAbsolutePath());
	            if (ze.isDirectory())
	                continue;
	            FileOutputStream fout = new FileOutputStream(file);
	            try {
	                while ((count = zis.read(buffer)) != -1)
	                    fout.write(buffer, 0, count);
	            } finally {
	                fout.close();
	            }
	            // if time should be restored as well
//	            long time = ze.getTime();
//	            if (time > 0)
//	                file.setLastModified(time);
	            
	        }
	    } finally {
	        zis.close();
	    }
	}
*/
	@Override
	public void update(Observable observable, Object data) {
		// TODO Auto-generated method stub
		
	}
	
/*	public static void unzip(){
	    String source = "some/compressed/file.zip";
	    String destination = "some/destination/folder";
	    String password = "password";

	    try {
	         ZipFile zipFile = new ZipFile(source);
	         if (zipFile.isEncrypted()) {
	            zipFile.setPassword(password);
	         }
	         zipFile.extractAll(destination);
	    } catch (ZipException e) {
	        e.printStackTrace();
	    }
	}*/
}
