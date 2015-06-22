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
 
package gong.audio;

/**
 * This represents the audio handler exception.
 * @version 4.2, 13/05/2011
 * @author Gibson Lam
 */
public class AudioHandlerException extends Exception {
    
    /**
     * Creates a new instance of the exception
     * @param message the error message
     */
    public AudioHandlerException(String message) {
        super(message);
    }

    public AudioHandlerException(String message, Exception cause) {
        super(message, cause);
    }
}
