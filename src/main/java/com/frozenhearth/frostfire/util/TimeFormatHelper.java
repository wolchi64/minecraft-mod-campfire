package com.frozenhearth.frostfire.util;

public final class TimeFormatHelper
{
    private TimeFormatHelper() {}

    public static String formatSeconds(double totalSeconds)
    {
        int seconds = Math.max(0, (int) Math.floor(totalSeconds));
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int remainder = seconds % 60;

        if (hours > 0)
        {
            return String.format("%dh %02dm", hours, minutes);
        }
        if (minutes > 0)
        {
            return String.format("%dm %02ds", minutes, remainder);
        }
        return remainder + "s";
    }
}
