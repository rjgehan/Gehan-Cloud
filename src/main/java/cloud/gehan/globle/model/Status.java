package cloud.gehan.globle.model;

/** Where a game stands. Only PLAYING accepts guesses, and only it hides the answer. */
public enum Status {
    PLAYING, WON, GAVE_UP
}
