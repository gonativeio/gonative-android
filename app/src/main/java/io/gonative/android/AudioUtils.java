package io.gonative.android;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.util.Log;

public class AudioUtils {
    private static final String TAG = AudioUtils.class.getName();
    private static AudioFocusRequest initialFocusRequest;
    private static AudioFocusRequest focusRequest;
    private static AudioManager.OnAudioFocusChangeListener initialAudioFocusChangeListener;
    private static AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    
    /**
     * @param mode - Accepts int for speaker mode:
     *             0 - phone speaker (default)
     *             1 - headset / wired device
     *             2 - bluetooth
     */
    public static void setUpAudioDevice(MainActivity mainActivity, int mode) {
        AudioManager mAudioManager = (AudioManager) mainActivity.getSystemService(Context.AUDIO_SERVICE);
        if (mode == 2) {
            // bluetooth device
            mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            mAudioManager.startBluetoothSco();
            mAudioManager.setBluetoothScoOn(true);
        } else if (mode == 1) {
            // wired device
            mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            mAudioManager.stopBluetoothSco();
            mAudioManager.setBluetoothScoOn(false);
            mAudioManager.setSpeakerphoneOn(false);
        } else {
            // phone speaker
            mAudioManager.setMode(AudioManager.MODE_NORMAL);
            mAudioManager.stopBluetoothSco();
            mAudioManager.setBluetoothScoOn(false);
            mAudioManager.setSpeakerphoneOn(true);
        }
    }
    
    public static void reconnectToBluetooth(MainActivity mainActivity, AudioManager audioManager) {
        if (audioManager.isBluetoothScoAvailableOffCall() && !audioManager.isBluetoothScoOn()) {
            Log.d(TAG, "Resetting audio to bluetooth device");
            setUpAudioDevice(mainActivity, 2);
        }
    }
    
    /**
     * Listen to the first AUDIOFOCUS_GAIN before taking the audio input/output priority through requestAudioFocus()
     *
     * @param mainActivity
     */
    public static void initAudioFocusListener(MainActivity mainActivity) {
        int result;
        final Object focusLock = new Object();
        
        AudioManager audioManager = (AudioManager) mainActivity.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            Log.w(TAG, "AudioManager is null. Aborting initAudioFocusListener()");
        }
        
        initialAudioFocusChangeListener = focusChange -> {
            if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                synchronized (focusLock) {
                    Log.d(TAG, "AudioFocusListener GAINED. Try to request audio focus");
                    requestAudioFocus(mainActivity);
                    abandonFocusRequest(mainActivity);
                }
            }
        };
        
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            result = audioManager.requestAudioFocus(initialAudioFocusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        } else {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build();
            initialFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(initialAudioFocusChangeListener)
                    .build();
            result = audioManager.requestAudioFocus(initialFocusRequest);
        }
        
        synchronized (focusLock) {
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(TAG, "AudioFocusListener REQUEST GRANTED");
            }
        }
    }
    
    /**
     * Prioritizes the bluetooth device if available.
     * Reconnects the bluetooth device when the audio focus is lost as a workaround for aborted connections
     * due to AudioRecord.AUDIO_INPUT_FLAG_FAST denial.
     *
     * @param mainActivity
     */
    public static void requestAudioFocus(MainActivity mainActivity) {
        int result;
        final Object focusLock = new Object();
        
        AudioManager audioManager = (AudioManager) mainActivity.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            Log.w(TAG, "AudioManager is null. Aborting requestAudioFocus()");
        }
        audioFocusChangeListener = focusChange -> {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    synchronized (focusLock) {
                        Log.d(TAG, "AudioFocus GAINED. Try to connect bluetooth device");
                        reconnectToBluetooth(mainActivity, audioManager);
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                case AudioManager.AUDIOFOCUS_LOSS:
                    synchronized (focusLock) {
                        Log.d(TAG, "AudioFocus LOST. Try to reconnect bluetooth device");
                        reconnectToBluetooth(mainActivity, audioManager);
                    }
                    break;
            }
        };
        
        abandonFocusRequest(mainActivity);
        
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            result = audioManager.requestAudioFocus(audioFocusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        } else {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build();
            result = audioManager.requestAudioFocus(focusRequest);
        }
        
        synchronized (focusLock) {
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(TAG, "AudioFocus REQUEST GRANTED");
                reconnectToBluetooth(mainActivity, audioManager);
            }
        }
    }
    
    public static void abandonFocusRequest(MainActivity mainActivity) {
        AudioManager audioManager = (AudioManager) mainActivity.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            Log.w(TAG, "AudioManager is null. Aborting abandonFocusRequest()");
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            if (initialAudioFocusChangeListener != null) {
                audioManager.abandonAudioFocus(initialAudioFocusChangeListener);
                initialAudioFocusChangeListener = null;
            }
            if (audioFocusChangeListener != null) {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
                audioFocusChangeListener = null;
            }
        } else {
            if (initialFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(initialFocusRequest);
                initialFocusRequest = null;
            }
            if (focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest);
                focusRequest = null;
            }
        }
    }
}
