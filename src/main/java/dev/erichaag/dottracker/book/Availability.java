package dev.erichaag.dottracker.book;

/** Whether a book can be bought. A book nobody has checked on is {@link #UNKNOWN}. */
public enum Availability {
  AVAILABLE,
  COMING_SOON,
  RETIRED,
  UNKNOWN
}
