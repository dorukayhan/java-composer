/*
Java Composer - because music shouldn't be treated in terms of raw frequencies
Copyright (C) 2019  Doruk Ayhan

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
package io.dorukayhan.composer;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

/**
 * <p>A music "player" that converts tunes written in the format of the (in)famous Nokia composer
 * to a series of frequencies and durations. Can be used to play songs over speakers,
 * such as that of a Finch.</p>
 * 
 * <p><strong>Warning:</strong> This is the musical equivalent of Brainfuck. For the lack of
 * key signatures, everything is in quasi-absolute pitch, and you can rapidly lose your mind
 * as you try to tune the thing.</p>
 */
public class Composer {
	private int bpm;
	private static Map<String, Integer> notes = new HashMap<>();
	private static Object noteMapLock = new Object();
	private Object tempoLock = new Object();
	private static boolean notesInitialized = false;
	private final int a4Frequency;
	
	/**
	 * No Nokia composer clone is complete without a prefab rendition of the Nokia tune!
	 */
	// C sharp, F sharp, G sharp
	public static final String NOKIA_TUNE = "8e5 8d5 4f#4 4g#4 8c#5 8b4 4d4 4e4 8b4 8a4 4c#4 4e4 2a4";
	
	/**
	 * Initializes the player given a tempo. The player will use A440 tuning to
	 * turn notes to frequencies.
	 * 
	 * @param bpm quarter notes per minute
	 */
	public Composer(int bpm) {
		this(bpm, 440);
	}
	
	/**
	 * Initializes the player given a tempo and a frequency for middle A (A<sub>4</sub>).
	 * <strong>For music theorists only!</strong>
	 * 
	 * @param bpm quarter notes per minute
	 * @param a4Frequency A<sub>4</sub>'s frequency in Hz. If in doubt, use 440
	 */
	public Composer(int bpm, int a4Frequency) {
		this.bpm = bpm;
		this.a4Frequency = a4Frequency;
	}
	
	/**
	 * <p>The meat of this class. Converts a song written in the Nokia composer
	 * to a format that can be played over a speaker.</p>
	 * 
	 * <p>The format, as implemented here, specifies songs as a series of
	 * whitespace-separated notes. Each note looks like this:</p>
	 * 
	 * <pre><code>[fraction][a-g in lowercase][optional hashtag for sharp notes][octave]</code></pre>
	 * 
	 * <p>where {@code fraction} is the length of the note as a fraction of the whole note
	 * (e.g. 2 is a minim, 4 is a quarter, 8 is a quaver, 16 is a semiquaver, etc.),
	 * {@code a-g in lowercase} is the note itself, {@code optional hashtag for sharp notes}
	 * is for half tones, and {@code octave} is, well, the octave of the note from 0 to 8.</p>
	 * 
	 * <p>Note that:</p>
	 * <ul>
	 * <li><strong>You can't use flats!</strong> For instance, 4db4 is invalid - use 4c#4 instead.</li>
	 * <li><strong>Rests don't exist.</strong> Instead of doing something like 4-, split the score in
	 * two and use {@linkplain #fractionToDuration(int)} to get how long you need to wait.</li>
	 * <li><strong>There are more octaves than in the OG Nokia composer.</strong> Nokia goes from 1
	 * to 3 with 1 as the middle octave, while this goes from 0 to 8 with 4 in the middle.</li>
	 * <li><strong>Fractions can be arbitrary integers.</strong> For instance, something like 
	 * 3c4 3e4 3g4 would end up as a triplet about the length of a whole note.</li>
	 * <li><strong>Dots don't exist either.</strong> You can work around this using the fact that a
	 * dotted note just ties its undotted counterpart to a note half of that undotted counterpart's
	 * length - e.g. instead of 4.c4, use 4c4 8c4. (4..c4, then, would be 4c4 8c4 16c4.) Keep in mind
	 * that naively interpreting the frequency-duration pairs will slur everything, resulting in 
	 * 4c4 8c4 sounding like 4.c4.</li>
	 * </ul>
	 * @param score the song to be played
	 * @return a series of frequency-duration pairs as an {@code int[][]}. Each sub-array has two
	 * elements, the first being frequency and the second being duration.
	 * @throws BadNoteException if {@code score} is malformed
	 */
	public int[][] compileScore(String score) throws BadNoteException {
		if(!notesInitialized)
			initNoteMap();
		
		synchronized(tempoLock) {
			String[] notes = score.trim().split("\\s");
			ArrayList<int[]> freqsAndDurations = new ArrayList<>(notes.length);
			for(String note : notes) {
				try {
					String[] components = note.split("(?<=[0-9])(?=[a-g])"); // Matches the boundary between the fraction and the note
					int[] noteAsFreqAndTime = {noteToFrequency(components[1]), fractionToDuration(Integer.parseInt(components[0]))};
					freqsAndDurations.add(noteAsFreqAndTime);
				}catch(Exception e) {
					throw new BadNoteException(note, e);
				}
			}
			return freqsAndDurations.toArray(new int[][]{});
		}
	}
	
	/**
	 * <p>Same as {@linkplain #compileScore(String)}, but throws
	 * {@code IllegalArgumentException}s instead of
	 * {@link BadNoteException}s.</p>
	 * 
	 * @param score the song to be played
	 * @return a series of frequency-duration pairs as an {@code int[][]}. Each sub-array has two
	 * elements, the first being frequency and the second being duration.
	 * @throws IllegalArgumentException if {@code score} is malformed
	 */
	public int[][] compileScoreButUnchecked(String score) {
		try {
			return compileScore(score);
		}catch(BadNoteException nope) {
			throw new IllegalArgumentException(nope);
		}
	}

	private static void initNoteMap() {
		if(notesInitialized)
			return;
		synchronized(noteMapLock) {
			if(notesInitialized)
				return; // Just let it go dude
			String[] keys = {
					"c0", "c#0", "d0", "d#0", "e0", "f0", "f#0", "g0", "g#0", "a0", "a#0", "b0",
					"c1", "c#1", "d1", "d#1", "e1", "f1", "f#1", "g1", "g#1", "a1", "a#1", "b1",
					"c2", "c#2", "d2", "d#2", "e2", "f2", "f#2", "g2", "g#2", "a2", "a#2", "b2",
					"c3", "c#3", "d3", "d#3", "e3", "f3", "f#3", "g3", "g#3", "a3", "a#3", "b3",
					"c4", "c#4", "d4", "d#4", "e4", "f4", "f#4", "g4", "g#4", "a4", "a#4", "b4",
					"c5", "c#5", "d5", "d#5", "e5", "f5", "f#5", "g5", "g#5", "a5", "a#5", "b5",
					"c6", "c#6", "d6", "d#6", "e6", "f6", "f#6", "g6", "g#6", "a6", "a#6", "b6",
					"c7", "c#7", "d7", "d#7", "e7", "f7", "f#7", "g7", "g#7", "a7", "a#7", "b7",
					"c8", "c#8", "d8", "d#8", "e8", "f8", "f#8", "g8", "g#8", "a8", "a#8", "b8"
			};
			for(int i = 0; i < keys.length; i++)
				notes.put(keys[i], i-57); // C0 is 57 half notes away from A4
			notesInitialized = true;
		}
	}
	
	/**
	 * <p>Converts a note of the form {@code [a-g in lowercase][optional hashtag for sharp notes][octave]}
	 * to the frequency it corresponds to in this Composer's tuning. The frequency is calculated using
	 * this formula and then rounding it to an integer:</p>
	 * 
	 * <blockquote>({@linkplain #Composer(int,int) A<sub>4</sub>}'s frequency) * 2<sup>(Semitones from A<sub>4</sub> to this note)/12</sup></blockquote>
	 * 
	 * <p>For instance, middle C (C<sub>4</sub>) is 9 semitones below A<sub>4</sub>, so in A440 its frequency
	 * would be 440 Hz * 2<sup>-9/12</sup> = 261.625565... Hz, which would be rounded to 262 Hz. Similarly,
	 * A<sub>4</sub> itself would be 440 Hz * 2<sup>0/12</sup> = 440 Hz.</p>
	 * @param note
	 * @return frequency of the note
	 */
	public int noteToFrequency(String note) {
		// Frequency of A4 * 2^(Half-step distance from A4 / 12)
		// noteToFrequency("a4") would return exactly 440 in A440 tuning
		return (int) Math.round(a4Frequency * Math.pow(2, notes.get(note) / 12.0));
	}
	
	/**
	 * <p>Converts a note fraction to its duration in milliseconds, like so:</p>
	 * 
	 * <blockquote>(1000 * 240) / (quarter beats per minute * fraction)</blockquote>
	 * 
	 * <p>where 240 is the BPM required to make a whole note last a second and 1000 converts from seconds to millis.</p>
	 * 
	 * <p>Since {@linkplain #compileScore(String)} doesn't support rests, this method can be used to find out
	 * how long you need to pause for a rest.</p>
	 * @param fraction
	 * @return duration in millis
	 */
	public int fractionToDuration(int fraction) {
		double whole = 240.0 / bpm; // BPM is based on quarters, so in 240 BPM whole notes like 1c4 would last exactly one second
		return (int)((whole / fraction) * 1000); // *1000 converts to milliseconds
	}
	
	/**
	 * <p>Changes the tempo on the fly* to satisfy your inner lunatic.
	 * Doesn't affect previously compiled scores; you can recompile them to use the new tempo.</p>
	 * 
	 * <p><sup>* Well, not literally. If you're using this thing across threads for some reason, it won't affect what {@link #compileScore(String)} is doing.</sup></p>
	 * @param bpm quarter beats per minute
	 */
	public void changeTempo(int bpm) {
		synchronized(tempoLock) {
			this.bpm = bpm;
		}
	}
	
	/**
	 * @return the amount of quarter beats (e.g. 4c4) this {@code Composer} crams into a minute
	 */
	public int tempo() {
		return bpm;
	}
	
	/**
	 * @return the frequency of middle A (A<sub>4</sub>) in Hz as used by {@code this} to convert notes into frequencies.
	 * {@linkplain #Composer(int, int) Not necessarily 440.}
	 */
	public int a4Frequency() {
		return a4Frequency;
	}
}
