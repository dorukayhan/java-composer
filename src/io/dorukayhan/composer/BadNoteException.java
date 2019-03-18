package io.dorukayhan.composer;

/**
 * <p>A checked exception that indicates an error in {@link Composer#compileScore(String)}.
 * When {@link Composer#compileScoreButUnchecked(String)} runs into an error, it wraps
 * {@code compileScore}'s {@code BadNoteException} in an unchecked {@link IllegalArgumentException}.</p>
 * 
 * <p><sub>Did I ever mention that {@code IllegalArgumentException} is my favorite exception? It's so absurdly descriptive.</sub></p>
 */
public class BadNoteException extends Exception {
	private static final long serialVersionUID = 1;
	public BadNoteException(String note, Throwable cause) {
		super(note + " is an invalid note. Remember the format: [fraction][a-g in lowercase][optional hashtag for sharp notes][octave from 0 to 8]", cause);
	}
}
