package io.asv.mtgocr.ocrreader.model;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Card condition and conservative local estimate relative to the Near Mint market price. */
public final class CardCondition {
  public static final String MINT = "mint";
  public static final String NEAR_MINT = "near_mint";
  public static final String EXCELLENT = "excellent";
  public static final String GOOD = "good";
  public static final String LIGHT_PLAYED = "light_played";
  public static final String PLAYED = "played";
  public static final String POOR = "poor";

  private static final String[] CODES = {
      MINT, NEAR_MINT, EXCELLENT, GOOD, LIGHT_PLAYED, PLAYED, POOR
  };
  private static final Pattern FIRST_NUMBER = Pattern.compile("[-+]?\\d+(?:[.,]\\d+)?");

  private CardCondition() { }

  public static String normalize(String value) {
    if (value == null) return NEAR_MINT;
    String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    for (String code : CODES) if (code.equals(normalized)) return code;
    if ("nm".equals(normalized)) return NEAR_MINT;
    if ("lp".equals(normalized)) return LIGHT_PLAYED;
    return NEAR_MINT;
  }

  public static String[] codes() { return CODES.clone(); }

  public static int indexOf(String condition) {
    String normalized = normalize(condition);
    for (int index = 0; index < CODES.length; index++) {
      if (CODES[index].equals(normalized)) return index;
    }
    return 1;
  }

  public static double multiplier(String condition) {
    switch (normalize(condition)) {
      case MINT: return 1.05d;
      case EXCELLENT: return .90d;
      case GOOD: return .80d;
      case LIGHT_PLAYED: return .70d;
      case PLAYED: return .55d;
      case POOR: return .35d;
      default: return 1d;
    }
  }

  public static double adjustedAmount(double nearMintAmount, String condition) {
    return Math.max(0d, nearMintAmount * multiplier(condition));
  }

  public static String adjustedDisplay(String baseDisplay, String rawNearMintAmount, String condition) {
    String display = baseDisplay == null ? "" : baseDisplay;
    if (NEAR_MINT.equals(normalize(condition)) || rawNearMintAmount == null ||
        rawNearMintAmount.trim().isEmpty()) return display;
    try {
      double adjusted = adjustedAmount(Double.parseDouble(rawNearMintAmount.trim()), condition);
      String formatted = String.format(Locale.US, "%.2f", adjusted);
      Matcher matcher = FIRST_NUMBER.matcher(display);
      if (!matcher.find()) return formatted;
      return display.substring(0, matcher.start()) + formatted + display.substring(matcher.end());
    } catch (NumberFormatException ignored) {
      return display;
    }
  }
}
