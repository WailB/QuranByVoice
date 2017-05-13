package com.wailbusaied.quranbyvoice;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;
import android.util.Log;

public class SimpleVoiceService extends RecognitionService {

    private SpeechRecognizer m_EngineSR;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("SimpleVoiceService", "Service started");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("SimpleVoiceService", "Service stopped");
    }

    @Override
    protected void onCancel(Callback listener) {
        m_EngineSR.cancel();
    }

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback listener) {
        m_EngineSR.setRecognitionListener((RecognitionListener) new VoiceResultsListener(listener));
        m_EngineSR.startListening(recognizerIntent);
        Log.i("onStartListening", "Started Listening");
    }

    @Override
    protected void onStopListening(Callback listener) {
        m_EngineSR.stopListening();
        Log.i("onStopListen", "Stopped");
    }


    /**
     * 
     */
    private class VoiceResultsListener implements RecognitionListener {

        private Callback m_UserSpecifiedListener;

        /**
         * 
         * @param userSpecifiedListener
         */
        public VoiceResultsListener(Callback userSpecifiedListener) 
        {
            m_UserSpecifiedListener = userSpecifiedListener;
            Log.i("VoiceResultListener", "===>");
        }

        @Override
        public void onBeginningOfSpeech() {
            try {
                m_UserSpecifiedListener.beginningOfSpeech();
                Log.i("Speech Started", "......");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            try {
                m_UserSpecifiedListener.bufferReceived(buffer);
                Log.i("Received Data Buffer", "some buffer received");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onEndOfSpeech() {
            try {
                m_UserSpecifiedListener.endOfSpeech();
                Log.i("Speech Ended", "END");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onError(int error) {
            try {
            	Log.i("Error Occoured", "Error");
                m_UserSpecifiedListener.error(error);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) { ; }

        @Override
        public void onPartialResults(Bundle partialResults) {
            try {
                m_UserSpecifiedListener.partialResults(partialResults);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onReadyForSpeech(Bundle params) {
            try {
                m_UserSpecifiedListener.readyForSpeech(params);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onResults(Bundle results) {
            try {
                m_UserSpecifiedListener.results(results);
                String s=results.getStringArrayList("RESULTS_RECOGNITION").get(0);
                Log.i("Recognized:",s);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            try {
                m_UserSpecifiedListener.rmsChanged(rmsdB);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

}