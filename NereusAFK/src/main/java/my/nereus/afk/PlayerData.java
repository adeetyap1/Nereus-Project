package my.nereus.afk;

import java.util.HashMap;
import java.util.Map;

final class PlayerData {
    long fragments;
    long total;
    String day = "";
    int today;
    final Map<String, Integer> bought = new HashMap<>();
}
