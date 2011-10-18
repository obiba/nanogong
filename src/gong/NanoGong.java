/*
 * Copyright 2002-2011 The Gong Project (http://gong.ust.hk)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package gong;

import gong.audio.AudioData;
import gong.audio.AudioHandler;
import gong.audio.OlaBuffer;
import gong.audio.data.FlvPCMData;
import gong.audio.data.ImaADPCMData;
import gong.audio.data.SpeexData;
import gong.audio.data.WavePCMAudioData;
import gong.event.AudioDataListener;
import gong.event.AudioHandlerListener;
import gong.ui.plaf.NanoAmplitudeUI;
import gong.ui.plaf.NanoButtonUI;
import gong.ui.plaf.NanoSpeedButtonUI;
import gong.ui.plaf.NanoTimeUI;
import gong.ui.plaf.NanoTimeUI.TimeListener;
import gong.xml.gasi.Fault;
import gong.xml.gasi.Message;
import gong.xml.gasi.Request;
import gong.xml.gasi.Response;

import java.awt.FontMetrics;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.sound.sampled.AudioFormat;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

import netscape.javascript.JSException;
import netscape.javascript.JSObject;

/**
 * This applet is the base class of the NanoGong applet.
 * @author Gibson Lam
 * @version 3.1, 20/08/2008
 * @version 3.2, 11/09/2009
 * @version 4.0, 27/04/2010
 * @version 4.1, 24/02/2011
 * @version 4.2, 13/05/2011
 */
public class NanoGong extends javax.swing.JApplet implements AudioDataListener, AudioHandlerListener, TimeListener  {

    // The version number of the NanoGong applet
    static final String VERSION_NUMBER = "4.2";
    
    // Message bundle
    private ResourceBundle bundle;
    
    // Available audio formats: Ima ADPCM or Speex
    static final String IMA_ADPCM = "ImaADPCM";
    static final String SPEEX = "Speex";
    static final String PCM = "PCM";
    
    private AudioHandler handler = new AudioHandler();
    private URLLoader currentLoader = null;
    private ScriptHandler currentScriptHandler = null;
    private String scriptError = null;
    private boolean modified = false;
    private String audioFormat = SPEEX;
    private float samplingRate = 44100;
    private int speexQuality = 10;
    private long maxDuration = AudioHandler.MAX_DURATION;
    private javax.swing.JLabel lblTime;
    private javax.swing.JPanel panGlassPane;
    private boolean showTime = false;
    private long startTime = -1;
    private long endTime = -1;
    
    private Lock handlerLock = new ReentrantLock();

    /**
     * Gets a new URL loader
     * @param url the URL to load
     * @param startTime the start time to extract, 0 if extraction is not needed
     * @param endTime the end time to extract, 0 if extraction is not needed
     * @return the URL loader or null if another loader is in use
     */
    protected synchronized URLLoader getURLLoader(String url, long startTime, long endTime) {
        if (currentLoader != null) return null;
        currentLoader = new URLLoader(url, startTime, endTime);
        return currentLoader;
    }

    /**
     * Clears the current URL loader
     */
    protected synchronized void clearURLLoader() {
        currentLoader = null;
    }

    /**
     * Gets a new script handler
     * @param request the request XML or name
     * @param params the parameters or null if the request is XML
     * @return the script handler or null if another script handler or URL loader is in use
     */
    protected synchronized ScriptHandler getScriptHandler(String request, String[] params) {
    	handlerLock.lock();
        if (currentLoader != null || currentScriptHandler != null) {
        }
        if (params == null)
            currentScriptHandler = new ScriptHandler(request);
        else
            currentScriptHandler = new ScriptHandler(request, params);
        return currentScriptHandler;
    }

    /**
     * Clears the current script handler
     */
    protected synchronized void clearScriptHandler() {
        currentScriptHandler = null;
        handlerLock.unlock();
    }

    private String resolveURL(String url) throws MalformedURLException {
        if (url != null && url.trim().length() > 0) {
            if (url.startsWith("http://"))
                return new URL(url).toString();
            else
                return new URL(this.getCodeBase(), url).toString();
        }
        return null;
    }

    private String getMessageString(String key) {
        String message = bundle.getString(key);

        // Get the short version of message text if the applet is not width enough
        FontMetrics metrics = this.getGraphics().getFontMetrics(lblMessage.getFont());
        int width = metrics.stringWidth(message);
        if (width > getWidth() - 20) {
            message = bundle.getString(key + "_SHORT");
        }

        return message;
    }

    private class URLLoader extends Thread {

        private String url;
        private long startTime;
        private long endTime;

        public URLLoader(String url, long startTime, long endTime) {
            this.url = url;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public void run() {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    panPanel.setVisible(false);
                    lblMessage.setText(getMessageString("MESSAGE_LOADING"));
                    pbrWait.setIndeterminate(true);
                    panWait.setVisible(true);
                }
            });
            
            try {
                handler.setData(null);
                handler.downloadData(null, true, NanoGong.this);
                handler.getData().setListener(null);

                // Extract necessary section
                if ((startTime > 0 && startTime < handler.getDuration()) ||
                    (endTime > 0 && endTime < handler.getDuration())) {
                    AudioData source = handler.getData();
                    AudioData target;
                    if (audioFormat.equals(IMA_ADPCM))
                        target = new ImaADPCMData(source.getFormat());
                    else if(audioFormat.equals(PCM))
                        target = new WavePCMAudioData(source.getFormat());
                    else
                        target = new SpeexData(source.getFormat(), true, speexQuality);
                    
                    source.reset();
                    if (startTime > 0) source.setTime(startTime);
                    while (source.isAvailable()) {
                        target.write(source.read());
                        if (endTime > 0 && source.getTime() > endTime) break;
                    }
                    handler.setData(target);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(NanoGong.this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                handler.setURL(null);
            }

            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    panPanel.setVisible(true);
                    panWait.setVisible(false);
                }
            });

            clearURLLoader();
        }

    }
    
    /**
     * Initializes the NanoGong applet
     */
    public void init() {
        bundle = ResourceBundle.getBundle("gong/i18n/NanoGong");

        try {
            java.awt.EventQueue.invokeAndWait(new Runnable() {
                public void run() {
                    initComponents();

                    // Initialize the button UIs
                    btnPlay.setUI(new NanoButtonUI(NanoButtonUI.PLAY));
                    btnRecord.setUI(new NanoButtonUI(NanoButtonUI.RECORD));
                    btnStop.setUI(new NanoButtonUI(NanoButtonUI.STOP));
                    btnSave.setUI(new NanoButtonUI(NanoButtonUI.SAVE));
                    slrAmplitude.setUI(new NanoAmplitudeUI(slrAmplitude));
                    btnSlow.setUI(new NanoSpeedButtonUI(NanoSpeedButtonUI.SLOW));
                    btnFast.setUI(new NanoSpeedButtonUI(NanoSpeedButtonUI.FAST));
                    slrTime.setUI(new NanoTimeUI(slrTime, NanoGong.this));
                    panWait.setVisible(false);
/*
                    // Create the JLabel for time display
                    lblTime = new javax.swing.JLabel();
                    lblTime.setText("");
                    lblTime.setBackground(new java.awt.Color(1.0f, 1.0f, 1.0f, 0.8f));
                    lblTime.setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.WHITE));
                    lblTime.setBounds((getWidth() - 100) / 2, 20, 100, 12);
                    lblTime.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 10));
                    lblTime.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
                    lblTime.setOpaque(true);
                    lblTime.setPreferredSize(lblTime.getSize());

                    // Add the time label to the glass pane*/
                    panGlassPane = (javax.swing.JPanel) getGlassPane();
                    panGlassPane.setLayout(null);
//                    panGlassPane.add(lblTime);*/
                }
            });
        } catch (Exception e) {}
        
        handler.addListener(this);
        
        String value;
        
        // Read various parameters from the applet
        value = getParameter("ShowAudioLevel");
        if (value != null && value.equalsIgnoreCase("false")) slrAmplitude.setVisible(false);
        value = getParameter("ShowRecordButton");
        if (value != null && value.equalsIgnoreCase("false")) btnRecord.setVisible(false);
        value = getParameter("ShowSpeedButton");
        if (value != null && value.equalsIgnoreCase("false")) panSpeed.setVisible(false);
        value = getParameter("ShowSaveButton");
        if (value != null && value.equalsIgnoreCase("false")) btnSave.setVisible(false);
        value = getParameter("ShowTime");
        if (value != null && value.equalsIgnoreCase("true")) showTime = true;
        value = getParameter("AudioFormat");
        if (value != null && value.equals(IMA_ADPCM)) audioFormat = IMA_ADPCM;
        if (value != null && value.equals(PCM)) audioFormat = PCM;
        value = getParameter("SamplingRate");
        if (value != null) {
            try {
                int rate = Integer.parseInt(value);
                if (audioFormat.equals(IMA_ADPCM)) {
                    if (rate == 8000 || rate == 11025 || rate == 22050 || rate == 44100) samplingRate = rate;
                } else {
                    if (rate == 8000 || rate == 16000 || rate == 32000 || rate == 44100) samplingRate = rate;
                }
            } catch (NumberFormatException nfe) {}
        }
        value = getParameter("SpeexQuality");
        if (value != null) {
            try {
                int quality = Integer.parseInt(value);
                if (quality >= 0 && quality <= 10) speexQuality = quality;
            } catch (NumberFormatException nfe) {}
        }
        value = getParameter("Color");
        if (value != null && value.length() > 1) {
            try {
                java.awt.Color c = new java.awt.Color(Integer.parseInt(value.substring(1), 16));
                
                // Change the color of all components
                panPanel.setBackground(c);
                btnPlay.setBackground(c);
                btnRecord.setBackground(c);
                btnStop.setBackground(c);
                slrAmplitude.setBackground(c);
                panSpeed.setBackground(c);
                btnSlow.setBackground(c);
                btnFast.setBackground(c);
                lblSpeed.setBackground(c);
                btnSave.setBackground(c);
                slrTime.setBackground(c);
                panWait.setBackground(c);
                lblMessage.setBackground(c);
                pbrWait.setBackground(c);
            } catch (NumberFormatException nfe) {
                speexQuality = 0;
            }
        }
        value = getParameter("MaxDuration");
        if (value != null) {
            try {
                long duration = Long.parseLong(value);
                if (duration > 0 && duration <= 1200) maxDuration = duration * 1000;
            } catch (NumberFormatException nfe) {}
        }
        value = getParameter("StartTime");
        if (value != null) {
            try {
                double dvalue = Double.parseDouble(value);
                if (dvalue > 0) startTime = (long) (dvalue * 1000.0);
            } catch (NumberFormatException nfe) {}
        }
        value = getParameter("EndTime");
        if (value != null) {
            try {
                double dvalue = Double.parseDouble(value);
                if (dvalue > 0) endTime = (long) (dvalue * 1000.0);
            } catch (NumberFormatException nfe) {}
        }
        if (startTime > 0 && endTime > 0 && endTime <= startTime) {
            endTime = -1;
        }
        
        // Preload the sound file from the parameter
        String url = getParameter("SoundFileURL");
        try {
            url = resolveURL(url);
            handler.setURL(url);
        } catch (MalformedURLException ex) {
            handler.setURL(null);
        }
                
        String jsListenerName = getParameter("AudioHandlerListener");
        if(jsListenerName != null) {
        	JSObject window = JSObject.getWindow(this);
        	try {
	        	JSObject jsListener = (JSObject)window.getMember(jsListenerName);
		        handler.addListener(new JsAudioHandlerListener(jsListener));
        	} catch(JSException e) {
        		// specified object not found
        		
        	}
        }
        if (url != null) {
            URLLoader loader = getURLLoader(url, startTime, endTime);
            if (loader == null)
                JOptionPane.showMessageDialog(this, "NanoGong is busy at the moment.", "Error", JOptionPane.ERROR_MESSAGE);
            else
                loader.start();
        }
    }
    
    /**
     * Receives the updated time from the audio handler
     * @param handler the audio handler
     * @param time the updated time
     */
    public void timeUpdate(AudioHandler handler, long time) {
        slrTime.setValue((int) time);
        lblTime.setText(Utility.formatTime(time, false) + " / " + Utility.formatTime(handler.getDuration(), false));
        if (panGlassPane.isVisible()) panGlassPane.repaint();
    }
    
    /**
     * Receives the updated status from the audio handler
     * @param handler the audio handler
     * @param status the updated status
     */
    public void statusUpdate(final AudioHandler handler, final int status) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                btnPlay.setEnabled(handler.hasData() &&
                                   (status == AudioHandler.PLAYING || status == AudioHandler.PAUSED ||
                                    status == AudioHandler.STOPPED || status == AudioHandler.CLOSED));
                if (status == AudioHandler.PLAYING) {
                    ((NanoButtonUI) btnPlay.getUI()).setIconType(NanoButtonUI.PAUSE);
                    btnPlay.setToolTipText(bundle.getString("TOOLTIP_PLAY_PAUSE_BUTTON"));
                }
                else {
                    ((NanoButtonUI) btnPlay.getUI()).setIconType(NanoButtonUI.PLAY);
                    btnPlay.setToolTipText(bundle.getString("TOOLTIP_PLAY_BUTTON"));
                }
                btnPlay.repaint();
                btnRecord.setEnabled(status == AudioHandler.RECORDING || status == AudioHandler.PAUSED_RECORD ||
                                     status == AudioHandler.STOPPED || status == AudioHandler.CLOSED);
                if (status == AudioHandler.RECORDING) {
                    ((NanoButtonUI) btnRecord.getUI()).setIconType(NanoButtonUI.PAUSE);
                    btnRecord.setToolTipText(bundle.getString("TOOLTIP_RECORD_PAUSE_BUTTON"));
                }
                else {
                    ((NanoButtonUI) btnRecord.getUI()).setIconType(NanoButtonUI.RECORD);
                    btnRecord.setToolTipText(bundle.getString("TOOLTIP_RECORD_BUTTON"));
                }
                btnRecord.repaint();
                btnStop.setEnabled(status == AudioHandler.PLAYING || status == AudioHandler.RECORDING ||
                                   status == AudioHandler.PAUSED || status == AudioHandler.PAUSED_RECORD);
                btnSave.setEnabled(handler.hasData() && (status == AudioHandler.STOPPED || status == AudioHandler.CLOSED));
                slrTime.setEnabled(handler.hasData() && status != AudioHandler.RECORDING && status != AudioHandler.PAUSED_RECORD);
            }
        });
    }
    
    /**
     * Receives the updated duration from the audio handler
     * @param handler the audio handler
     * @param duration the updated duration
     */
    public void durationUpdate(AudioHandler handler, long duration) {
        slrTime.setMaximum((int) duration);
        lblTime.setText(Utility.formatTime(handler.getTime(), false) + " / " + Utility.formatTime(duration, false));
        if (panGlassPane.isVisible()) panGlassPane.repaint();
    }
    
    /**
     * Receives the updated amplitude level of the audio handler
     * @param handler the audio handler
     * @param amplitude the updated amplitude level
     */
    public void amplitudeUpdate(AudioHandler handler, float amplitude) {
        slrAmplitude.setValue((int) (amplitude * slrAmplitude.getMaximum()));
    }
    
    /**
     * Receives the modified time from the timeline UI
     * @param time the modified time
     */
    public void timeUpdate(int time) {
        handler.setTime(time);
    }
    
    /**
     * Receives the current duration of received data
     * @param audioData the audio data
     * @param duration the received duration of data
     */
    public void received(AudioData audioData, long duration) {
        if (audioData.isSupported(AudioData.FEATURE_RECEIVED_PROGRESS)) {
            final int percentage = (int) (100.0 * duration / audioData.getDuration());
            final String message = MessageFormat.format(getMessageString("MESSAGE_LOADING_WITH_PROGRESS"), new Object[] { new Integer(percentage) });

            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    if (pbrWait.isIndeterminate()) {
                        pbrWait.setMaximum(100);
                        pbrWait.setIndeterminate(false);
                    }
                    pbrWait.setValue(percentage);
                    if (panWait.isVisible()) lblMessage.setText(message);
                }
            });
        }
    }

    /**
     * Receives the current duration of sent data
     * @param audioData the audio data
     * @param duration the sent duration of data
     */
    public void sent(AudioData audioData, long duration) {
        if (audioData.isSupported(AudioData.FEATURE_SENT_PROGRESS)) {
            final int percentage = (int) (100.0 * duration / audioData.getDuration());
            final String message = MessageFormat.format(getMessageString("MESSAGE_SENDING_WITH_PROGRESS"), new Object[] { new Integer(percentage) });

            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    if (pbrWait.isIndeterminate()) {
                        pbrWait.setMaximum(100);
                        pbrWait.setIndeterminate(false);
                    }
                    pbrWait.setValue(percentage);
                    if (panWait.isVisible()) lblMessage.setText(message);
                }
            });
        }
    }

    /**
     * Receives the duration of the data
     * @param audioData the audio data
     * @param duration the duration of data
     */
    public void finish(AudioData audioData, long duration) {
    }

    /**
     * Gets the applet information
     * @return The applet information
     */
    public String getAppletInfo() {
        return "NanoGong applet version " + VERSION_NUMBER + " by the Gong Project (http://gong.ust.hk)";
    }
    
    /**
     * Makes a Gong applet scripting interface request
     * @param request the request XML
     * @return the response/fault XML
     */
    public String sendGongRequest(String request) {
        return processGongRequest(request);
    }
    
    /**
     * Makes a Gong applet scripting interface request
     * @param request the request name
     * @param param the request parameter
     * @return the response
     */
    public String sendGongRequest(String request, String param) {
        return processGongRequest(request, new String[] { param });
    }
    
    /**
     * Makes a Gong applet scripting interface request
     * @param request the request name
     * @param param1 the request parameter 1
     * @param param2 the request parameter 2
     * @return the response
     */
    public String sendGongRequest(String request, String param1, String param2) {
        return processGongRequest(request, new String[] { param1, param2 });
    }
    
    /**
     * Makes a Gong applet scripting interface request
     * @param request the request name
     * @param param1 the request parameter 1
     * @param param2 the request parameter 2
     * @param param3 the request parameter 3
     * @return the response
     */
    public String sendGongRequest(String request, String param1, String param2, String param3) {
        return processGongRequest(request, new String[] { param1, param2, param3 });
    }
    
    /**
     * Makes a Gong applet scripting interface request
     * @param request the request name
     * @param param1 the request parameter 1
     * @param param2 the request parameter 2
     * @param param3 the request parameter 3
     * @param param4 the request parameter 4
     * @return the response
     */
    public String sendGongRequest(String request, String param1, String param2, String param3, String param4) {
        return processGongRequest(request, new String[] { param1, param2, param3, param4 });
    }
    
    /**
     * Makes a Gong applet scripting interface request in XML form
     * @param request the request XML
     * @return the response/fault XML
     */
    protected String processGongRequest(String request) {
        // Get a new handler
        ScriptHandler scriptHandler = getScriptHandler(request, null);
        if (scriptHandler == null) {
            try {
                Fault fault = Message.newFault("", "NanoGong is busy at the moment.");
                return fault.toString();
            } catch (Exception e) {}
        }

        // Execute the request
        scriptHandler.execute();
        
        // Return the fault if there is any
        String response;
        Fault fault = (Fault) scriptHandler.getFault();
        if (fault != null)
            response = fault.toString();
        else
            response = scriptHandler.getResponse().toString();

        // Clear the handler
        clearScriptHandler();

        return response;
    }
    
    /**
     * Makes a Gong applet scripting interface request in plain text form
     * @param request the request name
     * @param params the request parameters
     * @return the response
     */
    protected String processGongRequest(String request, String[] params) {
        scriptError = null;

        // Check the request
        if (!Request.isTextRequest(request)) {
            scriptError = "You have made an invalid request.";
            return "";
        }
        
        // Get a new handler
        ScriptHandler scriptHandler = getScriptHandler(request, params);
        if (scriptHandler == null) {
            scriptError = "NanoGong is busy at the moment.";
            return "";
        }

        // Execute the request
        scriptHandler.execute();
        
        // Return the fault if there is any
        String response;
        Object fault = scriptHandler.getFault();
        if (fault != null) {
            scriptError = fault.toString();
            response = "";
        }
        else response = scriptHandler.getResponse().toString();
        
        // Clear the handler
        clearScriptHandler();

        return response;
    }
    
    /**
     * Returns the last error made from the Gong script request
     * @return the error string
     */
    public String getError() {
        if (scriptError == null) return "";
        return scriptError;
    }
    
    /**
     * Returns the modified status of the content
     * @return the status: 1 is modified, 0 otherwise
     */
    public String getModified() {
        if (modified)
            return "1";
        else
            return "0";
    }
    
    private class JsAudioHandlerListener implements AudioHandlerListener{
    	
    	private final JSObject jsListener;
		
		boolean hasTimeUpdate;
		boolean hasStatusUpdate;
		boolean hasDurationUpdate;
		boolean hasAmplitudeUpdate;
		
		JsAudioHandlerListener(JSObject listener) {
			this.jsListener = listener;
			hasTimeUpdate = hasMember("timeUpdate");
			hasStatusUpdate = hasMember("statusUpdate");
			hasDurationUpdate = hasMember("durationUpdate");
			hasAmplitudeUpdate = hasMember("amplitudeUpdate");
		}

		private boolean hasMember(String member) {
			try {
				return jsListener.getMember(member) != null;
			} catch (JSException e) {
				return false;
			}
		}
		
		@Override
		public void timeUpdate(AudioHandler handler, long time) {
			if(hasTimeUpdate) {
				// Javascript doesn't support long
				jsListener.call("timeUpdate", new Object[]{(int)time});
			}
		}
		
		@Override
		public void statusUpdate(AudioHandler handler, int status) {
			if(hasStatusUpdate) {
				jsListener.call("statusUpdate", new Object[]{status});
			}
		}
		
		@Override
		public void durationUpdate(AudioHandler handler, long duration) {
			if(hasDurationUpdate) {
				// Javascript doesn't support long
				jsListener.call("durationUpdate", new Object[]{(int)duration});
			}
		}
		
		@Override
		public void amplitudeUpdate(AudioHandler handler, float amplitude) {
			if(hasAmplitudeUpdate) {
				jsListener.call("amplitudeUpdate", new Object[]{amplitude});
			}
		}
	}
    /** The class for applying the script */
    private class ScriptHandler extends gong.xml.gasi.ScriptHandler {
        
        public ScriptHandler(String request) {
            super(request);
        }
        
        public ScriptHandler(String name, String[] params) {
            super(name, params);
        }
        
        protected void playMedia() throws Exception {
            // Check handler status
            if (handler.getStatus() != AudioHandler.PAUSED && handler.getStatus() != AudioHandler.STOPPED)
                throw new Exception("You are not allowed to play a message at the moment.");
            
            // Get the paramters
            long startTime, endTime;
            String param = getParameter("StartTime", 1);
            try {
                startTime = Long.parseLong(param);
            } catch (NumberFormatException nfe) {
                startTime = handler.getTime();
            }
            param = getParameter("EndTime", 2);
            try {
                endTime = Long.parseLong(param);
            } catch (NumberFormatException nfe) {
                endTime = 0;
            }
            
            // Play with the AudioHandler
            if (endTime > 0 && endTime > startTime)
                handler.play(startTime, endTime);
            else
                handler.play(startTime);
            
            // Construct the response
            if (isXML()) {
                Response response = Response.newResponse("PlayMediaResponse");
                response.setParameter("StartTime", String.valueOf(startTime), false);
                response.setParameter("EndTime", String.valueOf(endTime), false);
                setResponse(response);
            } else
                setResponse(String.valueOf(startTime) + DELIMITER + String.valueOf(endTime));
        }
        
        protected void recordMedia() throws Exception {
            // Check handler status
            if (handler.getStatus() != AudioHandler.PAUSED_RECORD &&
                handler.getStatus() != AudioHandler.CLOSED &&
                handler.getStatus() != AudioHandler.STOPPED)
                throw new Exception("You are not allowed to record a message at the moment.");

            // Get the parameter
            long duration;
            String param = getParameter("Duration", 1);
            try {
                duration = Long.parseLong(param);
            } catch (NumberFormatException nfe) {
                duration = AudioHandler.MAX_DURATION;
            }
            
            // Set the maximum length of the handler
            handler.setDuration(duration);
            
            // Start the recording
            AudioFormat format = new AudioFormat(samplingRate, 16, 1, true, true);
            handler.setDataFormat(format);
            if (audioFormat.equals(IMA_ADPCM))
                handler.setRecordData(new ImaADPCMData(format));
            else if(audioFormat.equals(PCM))
                handler.setRecordData(new WavePCMAudioData(format));
            else
                handler.setRecordData(new SpeexData(format, true, speexQuality));
            handler.record(duration);
            modified = true;
            
            // Construct the response
            if (isXML()) {
                Response response = Response.newResponse("RecordMediaResponse");
                response.setParameter("Duration", String.valueOf(duration), false);
                setResponse(response);
            } else
                setResponse(String.valueOf(duration));
        }
        
        protected void pauseMedia() throws Exception {
            // Check handler status
            if (handler.getStatus() != AudioHandler.PLAYING && handler.getStatus() != AudioHandler.RECORDING)
                throw new Exception("You are not allowed to pause a playback or recording at the moment.");
            
            // Pause the handler
            handler.pause();
            
            // Construct the response
            if (isXML()) {
                Response response = Response.newResponse("PauseMediaResponse");
                response.setParameter("Time", String.valueOf(handler.getTime()), false);
                setResponse(response);
            } else
                setResponse(String.valueOf(handler.getTime()));
        }
        
        protected void stopMedia() throws Exception {
            boolean isRecording = (handler.getStatus() == AudioHandler.RECORDING || handler.getStatus() == AudioHandler.PAUSED_RECORD);
            
            // Check handler status
            if ((handler.getStatus() != AudioHandler.PLAYING &&
                    handler.getStatus() != AudioHandler.RECORDING &&
                    handler.getStatus() != AudioHandler.PAUSED &&
                    handler.getStatus() != AudioHandler.PAUSED_RECORD)) {
                throw new Exception("You are not allowed to stop a playback or recording at the moment.");
            }
            
            // Stop the handler
            handler.stop();
            
            // Get the duration
            long duration = 0;
            if (isRecording) duration = handler.getDuration();
            
            // Construct the response
            if (isXML()) {
                Response response = Response.newResponse("StopMediaResponse");
                if (duration > 0) response.setParameter("Duration", String.valueOf(duration), false);
                setResponse(response);
            } else
                setResponse((duration > 0)? String.valueOf(duration) : "");
        }
        
        protected void clearMedia() throws Exception {
            // Check handler status
            if ((handler.getStatus() != AudioHandler.STOPPED && handler.getStatus() != AudioHandler.CLOSED)) {
                throw new Exception("You are not allowed to clear a recording at the moment.");
            }

            // Stop the handler
            handler.stop();
            
            // Clear the handler
            handler.setData(null);
            handler.setURL(null);
            
            // Construct the response
            if (isXML()) {
                setResponse(Response.newResponse("ClearMediaResponse"));
            } else
                setResponse("");
        }
        
        protected void setMediaTime() throws Exception {
            // Check the handler
            if (!handler.hasData()) throw new Exception("There is no voice recording at the moment.");
            
            // Get the parameter
            long time;
            String param = getParameter("Time", 1);
            try {
                time = Long.parseLong(param);
                if (time < 0 || time > handler.getDuration()) throw new Exception("The time is not within the duration of the recording.");
            } catch (NumberFormatException nfe) {
                throw new Exception("A valid time value must be provided.");
            }
            
            // Set the time
            handler.setTime(time);
            
            // Construct the response
            if (isXML()) {
                Response response = Response.newResponse("SetMediaTimeResponse");
                response.setParameter("Time", String.valueOf(time), false);
                setResponse(response);
            } else
                setResponse(String.valueOf(time));
        }
        
        protected void getMediaTime() throws Exception {
            // Check the handler
            if (!handler.hasData()) throw new Exception("There is no voice recording at the moment.");
            
            // Get the time
            long time = handler.getTime();
            
            // Construct the response
            if (isXML()) {
                Response response = Response.newResponse("GetMediaTimeResponse");
                response.setParameter("Time", String.valueOf(time), false);
                setResponse(response);
            } else
                setResponse(String.valueOf(time));
        }
        
        protected void getMediaDuration() throws Exception {
            // Check the handler
            if (!handler.hasData()) throw new Exception("There is no voice recording at the moment.");
            
            // Get the duration
            long duration = handler.getDuration();
            
            // Construct the response
            if (isXML()) {
                Response response = Response.newResponse("GetMediaDurationResponse");
                response.setParameter("Duration", String.valueOf(duration), false);
                setResponse(response);
            } else
                setResponse(String.valueOf(duration));
        }
        
        protected void setMediaRate() throws Exception {
            // Get the parameter
            float rate;
            String param = getParameter("Rate", 1);
            try {
                rate = Float.parseFloat(param);
                if (rate < 0.5f || rate > 1.5f) throw new Exception("The rate must be within 0.5 to 1.5.");
            } catch (NumberFormatException nfe) {
                throw new Exception("The valid rate value must be provided.");
            }
            
            // Set the rate
            setRate(rate);
            
            // Construct the response
            if (isXML()) {
                Response response = Response.newResponse("SetMediaRateResponse");
                response.setParameter("Rate", String.valueOf(rate), false);
                setResponse(response);
            } else
                setResponse(String.valueOf(rate));
        }
        
        protected void getMediaRate() throws Exception {
            // Get the rate
            float rate = handler.getRate();
            
            // Construct the response
            if (isXML()) {
                Response response = Response.newResponse("GetMediaRateResponse");
                response.setParameter("Rate", String.valueOf(rate), false);
                setResponse(response);
            } else
                setResponse(String.valueOf(rate));
        }
        
        protected void getMediaStatus() throws Exception {
            String status = "unknown";
            if (handler.getStatus() == AudioHandler.PLAYING) status = "playing";
            else if (handler.getStatus() == AudioHandler.RECORDING) status = "recording";
            else if (handler.getStatus() == AudioHandler.PAUSED) status = "paused";
            else if (handler.getStatus() == AudioHandler.PAUSED_RECORD) status = "paused recording";
            else if (handler.getStatus() == AudioHandler.STOPPING) status = "stopping";
            else if (handler.getStatus() == AudioHandler.STOPPED) status = "stopped";
            else if (handler.getStatus() == AudioHandler.CLOSING) status = "closing";
            else if (handler.getStatus() == AudioHandler.CLOSED) status = "closed";
            
            // Construct the response
            if (isXML()) {
                Response response = Response.newResponse("GetMediaStatusResponse");
                response.setParameter("Status", status, false);
                setResponse(response);
            } else
                setResponse(status);
        }
        
        protected void getAudioLevel() throws Exception {
            float value = handler.getAmplitude();
            String amplitude = new DecimalFormat("0.00").format(value);
            
            // Construct the response
            if (isXML()) {
                Response response = Response.newResponse("GetAudioLevelResponse");
                response.setParameter("Level", amplitude, false);
                setResponse(response);
            } else
                setResponse(amplitude);
        }
        
        protected void movePrevMessage() throws Exception {
            throw new Exception("Script function not implemented.");
        }
        
        protected void moveNextMessage() throws Exception {
            throw new Exception("Script function not implemented.");
        }
        
        protected void selectMessage() throws Exception {
            throw new Exception("Script function not implemented.");
        }
        
        protected void getCurrentMessageId() throws Exception {
            throw new Exception("Script function not implemented.");
        }
        
        protected void searchMessage() throws Exception {
            throw new Exception("Script function not implemented.");
        }
        
        protected void getMessage() throws Exception {
            throw new Exception("Script function not implemented.");
        }
        
        protected void getMessageContent() throws Exception {
            throw new Exception("Script function not implemented.");
        }
        
        protected void postMessage() throws Exception {
            throw new Exception("Script function not implemented.");
        }
        
        protected File saveMessage(String path, String filename, String type) throws Exception {
            // Ask for the rate to be saved
            float rate = handler.getRate();
            if (rate != 1.0f) {
                Object[] options = {"The original speed",
                "The adjusted speed"};
                int answer = JOptionPane.showOptionDialog(NanoGong.this,
                        "You have changed the playback speed of the recording.\nDo you want to save the recording in its original speed or the adjusted speed?",
                        "Question",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        options,
                        options[1]);
                if (answer == JOptionPane.YES_OPTION) rate = 1.0f;
            }
            
            File file = new File(path, filename);
            
            handler.downloadData(null, true);
            
            AudioData data = handler.getData();
            AudioData target = null;
            
            if (type.equals(TYPE_WAV_ADPCM))
                target = new ImaADPCMData(data.getFormat());
            else if (type.equals(TYPE_WAV_PCM))
                target = new WavePCMAudioData(data.getFormat());
            else if (type.equals(TYPE_SPEEX)) {
                if (speexQuality == 0) speexQuality = 10;
                target = new SpeexData(data.getFormat(), true, speexQuality);
            } else if (type.equals(TYPE_FLV_PCM))
                target = new FlvPCMData(data.getFormat());
            else
                throw new Exception("Invalid file format.");

            OlaBuffer olaBuffer = new OlaBuffer(data.getFormat(), rate);
            
            data.reset();
            while (data.isAvailable()) {
                olaBuffer.write(data.read());
                while (olaBuffer.isAvailable()) target.write(olaBuffer.read());
            }
            
            olaBuffer.drain();
            while (olaBuffer.isAvailable()) target.write(olaBuffer.read());
            
            FileOutputStream stream = new FileOutputStream(file);
            target.sendToStream(stream);
            stream.close();
            
            return file;
        }
        
        protected void saveMessageWithPrompt(final String type, final String path) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    try {
                        FileFilter imaADPCMFilter = new FileFilter() {
                            public boolean accept(File f) {
                                if (f.isDirectory()) return true;
                                String name = f.getName().toLowerCase();
                                if (name.endsWith(ImaADPCMData.FILE_EXTENSION)) return true;
                                return false;
                            }
                            
                            public String getDescription() {
                                return "Compressed WAV format (" + ImaADPCMData.FILE_EXTENSION + ")";
                            }
                        };
                        
                        FileFilter wavePCMFilter = new FileFilter() {
                            public boolean accept(File f) {
                                if (f.isDirectory()) return true;
                                String name = f.getName().toLowerCase();
                                if (name.endsWith(WavePCMAudioData.FILE_EXTENSION)) return true;
                                return false;
                            }
                            
                            public String getDescription() {
                                return "Uncompressed WAV format (" + WavePCMAudioData.FILE_EXTENSION + " PCM)";
                            }
                        };
                        
                        FileFilter speexFilter = new FileFilter() {
                            public boolean accept(File f) {
                                if (f.isDirectory()) return true;
                                String name = f.getName().toLowerCase();
                                if (name.endsWith(SpeexData.FILE_EXTENSION)) return true;
                                return false;
                            }
                            
                            public String getDescription() {
                                return "Speex format (" + SpeexData.FILE_EXTENSION + ")";
                            }
                        };
                        
                        FileFilter flvPCMFilter = new FileFilter() {
                            public boolean accept(File f) {
                                if (f.isDirectory()) return true;
                                String name = f.getName().toLowerCase();
                                if (name.endsWith(FlvPCMData.FILE_EXTENSION)) return true;
                                return false;
                            }
                            
                            public String getDescription() {
                                return "Raw FLV format (" + FlvPCMData.FILE_EXTENSION + ")";
                            }
                        };
                        
                        JFileChooser chooser = null;
                        if (path == null)
                            chooser = new JFileChooser();
                        else
                            chooser = new JFileChooser(new File(path));
                        chooser.setAcceptAllFileFilterUsed(false);
                        if (type != null && type.equals(TYPE_WAV_ADPCM)) {
                            chooser.addChoosableFileFilter(imaADPCMFilter);
                            chooser.setFileFilter(imaADPCMFilter);
                        } else if (type != null && type.equals(TYPE_WAV_PCM)) {
                            chooser.addChoosableFileFilter(wavePCMFilter);
                            chooser.setFileFilter(wavePCMFilter);
                        } else if (type != null && type.equals(TYPE_SPEEX)) {
                            chooser.addChoosableFileFilter(speexFilter);
                            chooser.setFileFilter(speexFilter);
                        } else if (type != null && type.equals(TYPE_FLV_PCM)) {
                            chooser.addChoosableFileFilter(flvPCMFilter);
                            chooser.setFileFilter(flvPCMFilter);
                        } else {
                            chooser.addChoosableFileFilter(imaADPCMFilter);
                            chooser.addChoosableFileFilter(wavePCMFilter);
                            chooser.addChoosableFileFilter(speexFilter);
                            chooser.addChoosableFileFilter(flvPCMFilter);
                            chooser.setFileFilter(imaADPCMFilter);
                        }
                        
                        int option = chooser.showSaveDialog(NanoGong.this);
                        if (option == JFileChooser.APPROVE_OPTION) {
                            String extension = ImaADPCMData.FILE_EXTENSION;
                            String selectedType = TYPE_WAV_ADPCM;
                            if (chooser.getFileFilter().equals(speexFilter)) {
                                extension = SpeexData.FILE_EXTENSION;
                                selectedType = TYPE_SPEEX;
                            } else if (chooser.getFileFilter().equals(flvPCMFilter)) {
                                extension = FlvPCMData.FILE_EXTENSION;
                                selectedType = TYPE_FLV_PCM;
                            } else if (chooser.getFileFilter().equals(wavePCMFilter)) {
                                extension = WavePCMAudioData.FILE_EXTENSION;
                                selectedType = TYPE_WAV_PCM;
                            }
                            String filename = chooser.getSelectedFile().getName();
                            if (!filename.endsWith(extension)) filename += extension;
                            
                            saveMessage(chooser.getSelectedFile().getParent(), filename, selectedType);
                        }
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(NanoGong.this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
        }
        
        protected void saveMessage() throws Exception {
            if (!handler.hasData()) throw new Exception("There is nothing available at the moment.");
            
            // Get the parameters
            String type = getParameter("Type", 0);
            String filename = getParameter("Filename", 1);
            String path = getParameter("Path", 2);
            
            if (filename == null || filename.length() == 0) {
                saveMessageWithPrompt(type, path);
                
                // Construct the response
                if (isXML())
                    setResponse(Response.newResponse("SaveMessageResponse"));
                else
                    setResponse("");
                return;
            }
            
            if (type == null || (!type.equals(TYPE_WAV_ADPCM) && !type.equals(TYPE_WAV_PCM) && !type.equals(TYPE_SPEEX) && !type.equals(TYPE_FLV_PCM)))
                throw new Exception("You must specify the requested file type.");
            
            File file = saveMessage(path, filename, type);
            
            // Construct the response
            if (isXML()) {
                Response response = Response.newResponse("SaveMessageResponse");
                response.setParameter("File", file.getAbsolutePath(), false);
                setResponse(response);
            } else
                setResponse(file.getAbsolutePath());
        }

        protected void postToForm() throws Exception {
            handler.stop();
            if (!handler.hasData()) throw new Exception("There is nothing available at the moment.");
            
            // Get the parameters
            String url = getParameter("URL", 0);
            String parameter = getParameter("Parameter", 1);
            String cookies = getParameter("Cookies", 2);
            String filename = getParameter("Filename", 3);
            
            if (url == null || url.trim().length() == 0)
                throw new Exception("You must provide a valid url.");
            
            if (parameter == null || parameter.trim().length() == 0)
                throw new Exception("You must provide a parameter name for the message.");
            
            if (filename == null || filename.trim().length() == 0)
                throw new Exception("You must provide a filename.");
            
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    panPanel.setVisible(false);
                    lblMessage.setText(getMessageString("MESSAGE_CONNECTING"));
                    pbrWait.setIndeterminate(true);
                    panWait.setVisible(true);
                }
            });

            URLConnection connection = null;
            String boundary;
            try {
                String ext = "";
                int len = filename.length();
                if (len > 4) ext = filename.substring(len - 4, len);
                if (!ext.equalsIgnoreCase(handler.getData().getFileExtension())) {
                    filename += handler.getData().getFileExtension();
                }
                
                // Create boundary
                MessageDigest md5 = null;
                try {
                    md5 = MessageDigest.getInstance("MD5");
                } catch (NoSuchAlgorithmException e) {
                    throw new Exception("Failed to create the request boundary.");
                }
                String time = String.valueOf(new Date().getTime());
                md5.update(time.getBytes(), 0, time.length());
                boundary = new BigInteger(1, md5.digest()).toString(16);
                
                // Create connection
                try {
                    connection = new URL(resolveURL(url)).openConnection();
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                } catch (MalformedURLException e) {
                    throw new Exception("URL is not a valid URL.");
                } catch (IOException e) {
                    throw new Exception("Failed to open the URL.");
                } catch (Exception e) {
                    throw new Exception("Unknown error.");
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        panPanel.setVisible(true);
                        panWait.setVisible(false);
                    }
                });
                throw e;
            }

            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    lblMessage.setText(getMessageString("MESSAGE_SENDING"));
                }
            });
            
            StringBuffer ret = new StringBuffer();
            try {
                // Post cookies
                if (cookies != null && cookies.length() > 0)
                    connection.setRequestProperty("Cookie", cookies);
                
                // Post file parameter
                OutputStream out = connection.getOutputStream();
                out.write(("--" + boundary + "\r\n").getBytes());
                out.write(("Content-Disposition: form-data; name=\"" + parameter + "\"; filename=\"" + filename + "\"\r\n").getBytes());
                out.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes());
                
                // Post the file content
                handler.downloadData(null, true);
                handler.getData().setListener(NanoGong.this);
                handler.getData().sendToStream(out);
                handler.getData().setListener(null);
                
                // End of transfer
                out.write(("\r\n").getBytes());
                out.write(("--" + boundary + "--").getBytes());
                out.close();
                
                // Get the return value
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) ret.append(line);
                    reader.close();
                } catch (IOException e) {
                    throw new Exception("Failed to connect to the destination.");
                }
            } catch (Exception e) {
                throw e;
            } finally {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        panPanel.setVisible(true);
                        panWait.setVisible(false);
                    }
                });
            }
            
            // Construct the response
            if (isXML()) {
                Response response = Response.newResponse("PostToFormResponse");
                response.setParameter("Return", ret.toString(), false);
                setResponse(response);
            } else
                setResponse(ret.toString());
        }
        
        protected void loadFromURL() throws Exception {
            handler.stop();
            
            // Get the parameters
            String url = getParameter("URL", 0);
            final String start = getParameter("Start", 1);
            
            if (url == null || url.trim().length() == 0)
                throw new Exception("You must provide a valid url.");
            
            try {
                url = resolveURL(url);
                handler.setURL(url);
                modified = false;
            } catch (MalformedURLException ex) {
                throw new Exception("You must provide a valid url.");
            }
            
            // Load the url
            URLLoader loader = getURLLoader(url, 0, 0);
            if (loader == null)
                throw new Exception("NanoGong is busy at the moment.");
            loader.start();
            modified = true;
            
            // Construct the response
            if (isXML()) {
                Response response = Response.newResponse("LoadFromURLResponse");
                response.setParameter("URL", url, false);
                setResponse(response);
            } else
                setResponse(url);
        }
        
        protected void getCurrentToken() throws Exception {
            throw new Exception("Script function not implemented.");
        }
        
        protected void getBoardName() throws Exception {
            throw new Exception("Script function not implemented.");
        }
        
        protected void getBoardData() throws Exception {
            throw new Exception("Script function not implemented.");
        }
        
        protected void getVersion() throws Exception {
            // Construct the response
            if (isXML()) {
                Response response = Response.newResponse("GetVersionResponse");
                response.setParameter("Version", VERSION_NUMBER, false);
                setResponse(response);
            } else
                setResponse(VERSION_NUMBER);
        }
        
    }
    
    /** This method is called from within the init() method to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        panPanel = new javax.swing.JPanel();
        btnPlay = new javax.swing.JButton();
        btnRecord = new javax.swing.JButton();
        btnStop = new javax.swing.JButton();
        slrAmplitude = new javax.swing.JSlider();
        panSpeed = new javax.swing.JPanel();
        btnSlow = new javax.swing.JButton();
        btnFast = new javax.swing.JButton();
        lblSpeed = new javax.swing.JLabel();
        btnSave = new javax.swing.JButton();
        slrTime = new javax.swing.JSlider();
        panWait = new javax.swing.JPanel();
        lblMessage = new javax.swing.JLabel();
        pbrWait = new javax.swing.JProgressBar();

        getContentPane().setLayout(new javax.swing.BoxLayout(getContentPane(), javax.swing.BoxLayout.X_AXIS));

        panPanel.setLayout(new java.awt.GridBagLayout());

        panPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        btnPlay.setToolTipText(bundle.getString("TOOLTIP_PLAY_BUTTON"));
        btnPlay.setMaximumSize(new java.awt.Dimension(25, 25));
        btnPlay.setMinimumSize(new java.awt.Dimension(25, 25));
        btnPlay.setPreferredSize(new java.awt.Dimension(25, 25));
        btnPlay.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPlayActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 0;
        panPanel.add(btnPlay, gridBagConstraints);

        btnRecord.setToolTipText(bundle.getString("TOOLTIP_RECORD_BUTTON"));
        btnRecord.setMaximumSize(new java.awt.Dimension(25, 25));
        btnRecord.setMinimumSize(new java.awt.Dimension(25, 25));
        btnRecord.setPreferredSize(new java.awt.Dimension(25, 25));
        btnRecord.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRecordActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        panPanel.add(btnRecord, gridBagConstraints);

        btnStop.setToolTipText(bundle.getString("TOOLTIP_STOP_BUTTON"));
        btnStop.setMaximumSize(new java.awt.Dimension(25, 25));
        btnStop.setMinimumSize(new java.awt.Dimension(25, 25));
        btnStop.setPreferredSize(new java.awt.Dimension(25, 25));
        btnStop.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnStopActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        panPanel.add(btnStop, gridBagConstraints);

        slrAmplitude.setValue(0);
        slrAmplitude.setMaximumSize(new java.awt.Dimension(15, 25));
        slrAmplitude.setMinimumSize(new java.awt.Dimension(15, 25));
        slrAmplitude.setPreferredSize(new java.awt.Dimension(15, 25));
        slrAmplitude.setForeground(java.awt.Color.GREEN);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        panPanel.add(slrAmplitude, gridBagConstraints);

        panSpeed.setLayout(new java.awt.GridBagLayout());

        panSpeed.setOpaque(false);
        btnSlow.setToolTipText(bundle.getString("TOOLTIP_SLOW_BUTTON"));
        btnSlow.setMaximumSize(new java.awt.Dimension(15, 15));
        btnSlow.setMinimumSize(new java.awt.Dimension(15, 15));
        btnSlow.setPreferredSize(new java.awt.Dimension(15, 15));
        btnSlow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSlowActionPerformed(evt);
            }
        });

        panSpeed.add(btnSlow, new java.awt.GridBagConstraints());

        btnFast.setToolTipText(bundle.getString("TOOLTIP_FAST_BUTTON"));
        btnFast.setMaximumSize(new java.awt.Dimension(15, 15));
        btnFast.setMinimumSize(new java.awt.Dimension(15, 15));
        btnFast.setPreferredSize(new java.awt.Dimension(15, 15));
        btnFast.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnFastActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        panSpeed.add(btnFast, gridBagConstraints);

        lblSpeed.setFont(new java.awt.Font("Dialog", 0, 10));
        lblSpeed.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblSpeed.setText("x1.0");
        lblSpeed.setMaximumSize(new java.awt.Dimension(30, 10));
        lblSpeed.setMinimumSize(new java.awt.Dimension(30, 10));
        lblSpeed.setPreferredSize(new java.awt.Dimension(30, 10));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridwidth = 2;
        panSpeed.add(lblSpeed, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        panPanel.add(panSpeed, gridBagConstraints);

        btnSave.setToolTipText(bundle.getString("TOOLTIP_SAVE_BUTTON"));
        btnSave.setMaximumSize(new java.awt.Dimension(25, 25));
        btnSave.setMinimumSize(new java.awt.Dimension(25, 25));
        btnSave.setPreferredSize(new java.awt.Dimension(25, 25));
        btnSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSaveActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        panPanel.add(btnSave, gridBagConstraints);

        slrTime.setValue(0);
        slrTime.setMaximumSize(new java.awt.Dimension(32767, 4));
        slrTime.setMinimumSize(new java.awt.Dimension(4, 4));
        slrTime.setPreferredSize(new java.awt.Dimension(4, 4));
        slrTime.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                slrTimeMouseEntered(evt);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                slrTimeMouseExited(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        panPanel.add(slrTime, gridBagConstraints);
        
        

        // Create the JLabel for time display
        lblTime = new javax.swing.JLabel();
        lblTime.setText("");
        lblTime.setBackground(new java.awt.Color(1.0f, 1.0f, 1.0f, 0.8f));
        lblTime.setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.WHITE));
        lblTime.setBounds((getWidth() - 100) / 2, 20, 100, 12);
        lblTime.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 10));
        lblTime.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblTime.setOpaque(true);
        lblTime.setPreferredSize(lblTime.getSize());
        
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        panPanel.add(lblTime, gridBagConstraints);

        getContentPane().add(panPanel);

        panWait.setLayout(new javax.swing.BoxLayout(panWait, javax.swing.BoxLayout.Y_AXIS));

        panWait.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        lblMessage.setFont(new java.awt.Font("Dialog", 0, 10));
        lblMessage.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblMessage.setAlignmentX(0.5F);
        lblMessage.setMaximumSize(new java.awt.Dimension(32768, 32768));
        panWait.add(lblMessage);

        pbrWait.setForeground(new java.awt.Color(64, 64, 255));
        pbrWait.setIndeterminate(true);
        pbrWait.setMaximumSize(new java.awt.Dimension(32767, 5));
        pbrWait.setMinimumSize(new java.awt.Dimension(10, 5));
        pbrWait.setPreferredSize(new java.awt.Dimension(100, 5));
        panWait.add(pbrWait);

        getContentPane().add(panWait);

    }// </editor-fold>//GEN-END:initComponents

    private void slrTimeMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_slrTimeMouseExited
        if (showTime) panGlassPane.setVisible(false);
    }//GEN-LAST:event_slrTimeMouseExited

    private void slrTimeMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_slrTimeMouseEntered
        if (showTime) panGlassPane.setVisible(true);
    }//GEN-LAST:event_slrTimeMouseEntered
    
    private void btnSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveActionPerformed
        // Execute the action
        ScriptHandler scriptHandler = getScriptHandler("SaveMessage", new String[] {});
        if (scriptHandler == null) {
            JOptionPane.showMessageDialog(this, "NanoGong is busy at the moment.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        scriptHandler.execute();

        // Return the fault if there is any
        Object fault = scriptHandler.getFault();
        if (fault != null) JOptionPane.showMessageDialog(this, fault.toString(), "Error", JOptionPane.ERROR_MESSAGE);

        clearScriptHandler();
    }//GEN-LAST:event_btnSaveActionPerformed
    
    private void setRate(float rate) {
        if (rate < 0.5f) rate = 0.5f;
        else if (rate > 1.5f) rate = 1.5f;
        
        rate = (int) (rate * 10) / 10f;
        lblSpeed.setText("x" + rate);
        handler.setRate(rate);
    }
    
    private void btnFastActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnFastActionPerformed
        setRate(handler.getRate() + 0.1f);
    }//GEN-LAST:event_btnFastActionPerformed
    
    private void btnSlowActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSlowActionPerformed
        setRate(handler.getRate() - 0.1f);
    }//GEN-LAST:event_btnSlowActionPerformed
    
    private void btnStopActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnStopActionPerformed
        // Execute the action
        ScriptHandler scriptHandler = getScriptHandler("StopMedia", new String[] {});
        if (scriptHandler == null) {
            JOptionPane.showMessageDialog(this, "NanoGong is busy at the moment.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        scriptHandler.execute();
        
        // Return the fault if there is any
        Object fault = scriptHandler.getFault();
        if (fault != null) JOptionPane.showMessageDialog(this, fault.toString(), "Error", JOptionPane.ERROR_MESSAGE);

        clearScriptHandler();
    }//GEN-LAST:event_btnStopActionPerformed
    
    private void btnRecordActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRecordActionPerformed
        ScriptHandler scriptHandler;
        if (handler.getStatus() == AudioHandler.RECORDING)
            scriptHandler = getScriptHandler("PauseMedia", new String[] {});
        else
            scriptHandler = getScriptHandler("RecordMedia", new String[] { "audio", String.valueOf(maxDuration) });

        // Execute the action
        if (scriptHandler == null) {
            JOptionPane.showMessageDialog(this, "NanoGong is busy at the moment.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        scriptHandler.execute();
        
        // Return the fault if there is any
        Object fault = scriptHandler.getFault();
        if (fault != null) JOptionPane.showMessageDialog(this, fault.toString(), "Error", JOptionPane.ERROR_MESSAGE);

        clearScriptHandler();
    }//GEN-LAST:event_btnRecordActionPerformed
    
    private void btnPlayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPlayActionPerformed
        ScriptHandler scriptHandler;
        if (handler.getStatus() == AudioHandler.PLAYING) {
            scriptHandler = getScriptHandler("PauseMedia", new String[] {});
        }
        else {
            String[] params;
            if (endTime >= 0)
                params = new String[] { "audio", String.valueOf(handler.getTime()), String.valueOf(endTime) };
            else
                params = new String[] { "audio", String.valueOf(handler.getTime()) };
            scriptHandler = getScriptHandler("PlayMedia", params);
        }

        // Execute the action
        if (scriptHandler == null) {
            JOptionPane.showMessageDialog(this, "NanoGong is busy at the moment.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        scriptHandler.execute();
        
        // Return the fault if there is any
        Object fault = scriptHandler.getFault();
        if (fault != null) JOptionPane.showMessageDialog(this, fault.toString(), "Error", JOptionPane.ERROR_MESSAGE);

        clearScriptHandler();
    }//GEN-LAST:event_btnPlayActionPerformed
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnFast;
    private javax.swing.JButton btnPlay;
    private javax.swing.JButton btnRecord;
    private javax.swing.JButton btnSave;
    private javax.swing.JButton btnSlow;
    private javax.swing.JButton btnStop;
    private javax.swing.JLabel lblMessage;
    private javax.swing.JLabel lblSpeed;
    private javax.swing.JPanel panPanel;
    private javax.swing.JPanel panSpeed;
    private javax.swing.JPanel panWait;
    private javax.swing.JProgressBar pbrWait;
    private javax.swing.JSlider slrAmplitude;
    private javax.swing.JSlider slrTime;
    // End of variables declaration//GEN-END:variables
    
}
