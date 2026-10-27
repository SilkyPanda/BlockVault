package io.github.silkypanda.blockvault.core.detect;

/**
 * A view of a string that aborts a regular-expression match once a time budget is spent, so a pattern with
 * catastrophic backtracking (or a hostile, very long message) can never stall the game thread.
 */
final class DeadlineCharSequence implements CharSequence {
    /** Thrown from {@code charAt} when the budget is exhausted. */
    static final class TimeoutException extends RuntimeException {
        TimeoutException() { super("Pattern matching exceeded its time budget", null, false, false); }
    }

    private final CharSequence text;
    private final long deadlineNanos;
    private int reads;

    DeadlineCharSequence(CharSequence text, long budgetNanos) {
        this.text = text;
        this.deadlineNanos = System.nanoTime() + budgetNanos;
    }

    @Override public int length() { return text.length(); }

    @Override
    public char charAt(int index) {
        if ((++reads & 0x3FF) == 0 && System.nanoTime() - deadlineNanos > 0) throw new TimeoutException();
        return text.charAt(index);
    }

    @Override public CharSequence subSequence(int start, int end) { return text.subSequence(start, end); }
    @Override public String toString() { return text.toString(); }
}
