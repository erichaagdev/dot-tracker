package dev.erichaag.dottracker.book;

/**
 * The color family a product line's covers are drawn in, which the frontend turns into its {@code --l-} color token.
 * Related lines share a family, such as every Trace line.
 */
public enum LineColor {
  DBL,
  MINI,
  PREMIUM,
  REVEAL,
  TRACE,
  POP,
  GLOW,
  STAMP
}
