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
 
package gong.event;

import gong.audio.AudioData;

/**
 * This interface defines a listener for the audio data.
 * @author Gibson Lam
 * @version 1.0, 26/09/2005
 * @version 4.2, 13/05/2011
 */
public interface AudioDataListener {
    
    /**
     * Received the updated available data
     * @param duration the duration of the available data
     */
    public void received(AudioData audioData, long duration);
    
    /**
     * Sent the audio data
     * @param duration the duration of the sent data
     */
    public void sent(AudioData audioData, long duration);

    /**
     * Indicates the download is finished for the data
     * @param duration the duration of the data
     */
    public void finish(AudioData audioData, long duration);
    
}
