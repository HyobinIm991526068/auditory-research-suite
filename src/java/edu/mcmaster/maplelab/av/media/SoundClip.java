package edu.mcmaster.maplelab.av.media;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;

import javax.sound.sampled.*;

import com.jogamp.openal.AL;
import com.jogamp.openal.ALFactory;
import com.jogamp.openal.sound3d.AudioSystem3D;
import com.jogamp.openal.util.ALut;
import com.jogamp.openal.util.WAVData;
import com.jogamp.openal.util.WAVLoader;

import edu.mcmaster.maplelab.common.LogContext;
import edu.mcmaster.maplelab.common.ResourceLoader;
import edu.mcmaster.maplelab.common.datamodel.Session;

/**
 * Wrapper around a {@link Clip}
 *
 */
public class SoundClip implements Playable {
	static {
		AudioSystem3D.init();
        ALut.alutInit();
	}
	
    private static Map<String, Playable> _soundCache = new HashMap<String, Playable>();
    private static int[] _bufferIDs = null;
    private static int[] _sourceIDs = null;
    private static int _index;
    private static int _pollWaitMillis = 0;
    
    /**
     * Initialize the buffer and source id arrays according to the expected
     * count.
     */
    public static void initializeSoundCount(int count) {
    	_index = 0;
    	_bufferIDs = new int[count];
    	_sourceIDs = new int[count];
    	
    	AL al = ALFactory.getAL();
        al.alGenBuffers(count, _bufferIDs, 0);
        al.alGenSources(count, _sourceIDs, 0);
    }
    
    /**
     * Initialize wait time between polling for playback finished state.
     */
    public static void initializePlayPollWait(int waitMillis) {
    	_pollWaitMillis = waitMillis;
    }
    
    
    
    private final Clip _clip; // javax.media version
    private final Integer _sourceID; // open al version
    private final Integer _bufferID; // open al version
    private float _sourceVol = 1.0f;
    private final String _name;
    private int _calculatedDur = Integer.MIN_VALUE;
    private ArrayList<PlayableListener> _listeners = new ArrayList<PlayableListener>();
    
    public SoundClip(String name, int source, int buffer) {
    	_name = name;
    	_sourceID = source;
    	_bufferID = buffer;
    	_clip = null;
    }

    public SoundClip(String name, Clip clip) {
        _name = name;
        _clip = clip;
        _sourceID = null;
        _bufferID = null;
    }

    public int durationMillis() {
    	if (_bufferID != null) {
    		if (_calculatedDur == Integer.MIN_VALUE) {
            	/* Frequency is in terms of samples/second.
            	 * Size is in terms of bytes.
            	 * Bit-depth is sample size in bits (bits/sample).
            	 * We want bytes * bits/byte * sample/bits * seconds/sample * milliseconds/second
            	 */
        		
        		/*Buffer b = _sourceID.getBuffer();
        		double bytes = b.getSize();
        		double bitsPerByte = 8;
        		double samplePerBits = 1d / (double) b.getBitDepth();
        		double secondsPerSample = 1d / (double) b.getFrequency();*/
        		AL al = ALFactory.getAL();
        		int[] val = new int[1];
        		al.alGetBufferi(_bufferID, AL.AL_SIZE, val, 0);
        		double bytes = val[0];
        		double bitsPerByte = 8;
        		al.alGetBufferi(_bufferID, AL.AL_BITS, val, 0);
        		double samplePerBits = 1d / (double) val[0];
        		al.alGetBufferi(_bufferID, AL.AL_FREQUENCY, val, 0);
        		double secondsPerSample = 1d / (double) val[0];
        		double millisPerSec = 1000;
        		_calculatedDur = (int) Math.round(bytes * samplePerBits * bitsPerByte * millisPerSec * secondsPerSample);
    		}
    		return _calculatedDur;
    	}
    	
        long dur = _clip.getMicrosecondLength();
        return dur != AudioSystem.NOT_SPECIFIED ? (int) (dur / 1000) : 0;
    }
    
    /**
     * Get playback position, in samples.
     */
    public float getCurrentPlaybackPosition() {
    	AL al = ALFactory.getAL();
    	int[] offset = new int[1];
		al.alGetSourcei(_sourceID, AL.AL_SAMPLE_OFFSET, offset, 0);
		return offset[0];
    }
    
    public void play() {
    	play(null);
    }

    public void play(CountDownLatch latch) {
    	AL al = ALFactory.getAL();
    	if (_sourceID != null) {
    		al.alSourceRewind(_sourceID);
    		if (latch != null) {
    			try {
    				latch.await();
    			} 
        		catch (InterruptedException e) {}
        	}
    		al.alSourcePlay(_sourceID);
    	}
    	else {
            _clip.setFramePosition(0);
            if (latch != null) {
    			try {
    				latch.await();
    			} 
        		catch (InterruptedException e) {}
        	}
            _clip.start();
    	}
    	
        
        // The call _clip.drain() below takes too long, messing up synchronization.
        // Just using Thread.sleep() gives us the accuracy we want. Required
        // to support blocking semantics of method.
        Session.sleep(durationMillis());
        
        if (_sourceID != null) {
        	int[] val = new int[1];
    		al.alGetSourcei(_sourceID, AL.AL_SOURCE_STATE, val, 0);
        	while (val[0] == AL.AL_PLAYING) {
        		Session.sleep(_pollWaitMillis);
        		al.alGetSourcei(_sourceID, AL.AL_SOURCE_STATE, val, 0);
        	}
        	//al.alSourceStop(_sourceID);
        }
        else _clip.stop();
        
        synchronized (_listeners) {
            if (!_listeners.isEmpty()) {
            	for (PlayableListener pl : _listeners) {
            		pl.playableEnded();
            	}
            }
        }
    }

    public String name() {
        return _name;
    }
    
    @Override
    public void setVolume(float volume) {
    	if (_sourceID != null) {
    		ALFactory.getAL().alSourcef(_sourceID, AL.AL_GAIN, volume);
    		return;
    	}
    	
    	FloatControl c = null;
    	try {
    		c = (FloatControl) _clip.getControl(FloatControl.Type.VOLUME);
    	}
    	catch (IllegalArgumentException e) { 
    		try {
        		c = (FloatControl) _clip.getControl(FloatControl.Type.MASTER_GAIN);
        	}
        	catch (IllegalArgumentException ex) { } // no-op
    	} 
    	
    	float val = c.getMinimum() + volume*(c.getMaximum()-c.getMinimum()); 
    	val = val - (val % c.getPrecision());
    	if (c != null) c.setValue(val);
    }
    
    @Override
    public void setMute(boolean mute) {
    	if (_sourceID != null) {
    		AL al = ALFactory.getAL();
	        if (mute) {
    			float[] vol = new float[1];
    	        al.alGetSourcef(_sourceID, AL.AL_GAIN, vol, 0);
    	        _sourceVol = vol[0];
    	        al.alSourcef(_sourceID, AL.AL_GAIN, 0);
    		}
    		else {
    			al.alSourcef(_sourceID, AL.AL_GAIN, _sourceVol);
    		}
    		return;
    	}
    	
    	BooleanControl b = null;
    	try {
    		b = (BooleanControl) _clip.getControl(BooleanControl.Type.MUTE);
    	}
    	catch (IllegalArgumentException e) { } // no-op
        
    	if (b != null) b.setValue(mute);
    }

    /**
     * Get the auditory stimulus with the given name.
     *
     * @param name key name used in config file.
     * @return translated Playable type.
     */
    public static Playable findPlayable(String filename, File directory, boolean forceReload) {
    	return findPlayable(filename, directory, 1.0f, forceReload);
    }
    
    public static Playable findPlayable(String filename, File directory, float volume, 
    		boolean forceReload) {
        Playable p = forceReload ? null : _soundCache.get(filename);
        if (p == null) {
            if (filename != null) {
                Clip clip = null;
                Integer sourceID = null;
                Integer bufferID = null;
                try {
                    InputStream input = ResourceLoader.findAudioData(directory, filename);
                    if(input == null) {
                        throw new FileNotFoundException("Couldn't find " + filename);
                    }
                    
                    // load the source
                    AL al = ALFactory.getAL();
                    
                    if (_bufferIDs == null || _index >= _bufferIDs.length) {
                    	// generate on-the-fly
                        int[] id = new int[1];
                        al.alGenBuffers(1, id, 0);
                        bufferID = id[0];
                        al.alGenSources(1, id, 0);
                        sourceID = id[0];
                    }
                    else {
                        bufferID = _bufferIDs[_index];
                        sourceID = _sourceIDs[_index];
                        ++_index;
                    }
                    
                    if (sourceID != null) {
                    	// XXX: Must load buffer before assigning to source!!!!
                    	// Otherwise, sound will: (1) not work and (2) give no
                    	// hint as to why.
                        WAVData wd = WAVLoader.loadFromStream(input);
                        al.alBufferData(bufferID, wd.format, wd.data, wd.data.capacity(), wd.freq);
                        al.alSourcei(sourceID, AL.AL_BUFFER, bufferID);
                        //source = AudioSystem3D.loadSource(input);
                    }
                    else {
                    	// if source load failed, try clip
                    	input = ResourceLoader.findAudioData(directory, filename);
                        AudioInputStream stream = AudioSystem.getAudioInputStream(input);
                        AudioFormat format = stream.getFormat();
                        LogContext.getLogger().fine(String.format("%s -> %s", format, filename));
                        clip = (Clip) AudioSystem.getLine(new DataLine.Info(Clip.class, format));
                        clip.open(stream);
                    }
                }
                catch (Exception ex) {
                    LogContext.getLogger().log(Level.SEVERE, "Couldn't load audio resource " + filename, ex);
                    return null;
                }
                
                // create playable according to type
                if (sourceID != null) {
                	p = new SoundClip(filename, sourceID, bufferID);
                }
                else if (clip != null)  {
                    p = new SoundClip(filename, clip);
                    //(new Thread(new SoundPreparer((SoundClip) p))).start();
                }
                
                // set playable parameters and cache
                if (p != null) {
                    // don't change the volume unless necessary
                    if (Float.compare(1.0f, volume) != 0) p.setVolume(volume);
                    
                    _soundCache.put(filename, p);
                }
            }
        }
        return p;
    }

    @Override
    public String toString() {
        return name();
    }

	@Override
	public void addListener(PlayableListener listener) {
		synchronized (_listeners) {
			if (_sourceID != null) _listeners.add(listener);
			else _clip.addLineListener(listener);
		}
	}

	@Override
	public void removeListener(PlayableListener listener) {
		synchronized (_listeners) {
			if (_sourceID != null) _listeners.remove(listener);
			else _clip.removeLineListener(listener);
		}
	}
	
	/**
	 * Class that plays a sound clip once silently at load time. Intended to avoid 
	 * initialization "pop" on first play.  This is a bit of a hack and somewhat 
	 * imperfect - it would be better to find another way to deal w/ this issue.  
	 * Using this method, there is still a large "pop" before the first trial, 
	 * but no more for individual sound files played during a trial.
	 */
	/*private static class SoundPreparer implements Runnable {
		private final SoundClip _soundClip;

		public SoundPreparer(SoundClip clip) {
			_soundClip = clip;
		}
		
		@Override
		public void run() {
			_soundClip.setMute(true);
			_soundClip.play();
			_soundClip.setMute(false);
		}
		
	}*/
}
