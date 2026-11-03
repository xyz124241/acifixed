package de.aci.gui;

import de.aci.ItemEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Ui {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern CODE = Pattern.compile("&([0-9a-fA-FklmnorKLMNOR])");
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"), Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"), Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"), Map.entry('7', "<gray>"), Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"), Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"), Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"), Map.entry('k', "<obfuscated>"), Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"), Map.entry('n', "<underlined>"), Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>"));
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private Ui() {}

    /** Wandelt alte &-Farbcodes (&6, &l, &#ff8800) in MiniMessage-Tags um. */
    private static String legacyToMini(String s) {
        s = HEX.matcher(s).replaceAll("<#$1>");
        Matcher m = CODE.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String tag = LEGACY_TAGS.get(Character.toLowerCase(m.group(1).charAt(0)));
            m.appendReplacement(sb, Matcher.quoteReplacement(tag == null ? m.group() : tag));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Text mit MiniMessage (&lt;gradient:red:gold&gt;Name&lt;/gradient&gt;, &lt;#ff8800&gt;, &lt;bold&gt; ...)
     * und/oder alten &amp;-Farbcodes. Standardmaessig nicht kursiv.
     */
    public static Component text(String input) {
        return MINI.deserialize(legacyToMini(input))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String plain(String input) {
        return PlainTextComponentSerializer.plainText().serialize(MINI.deserialize(legacyToMini(input)));
    }

    public static ItemStack button(Material m, String name, String... lore) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(text(name));
        if (lore.length > 0) meta.lore(Arrays.stream(lore).map(Ui::text).toList());
        meta.addItemFlags(ItemFlag.values());
        s.setItemMeta(meta);
        return s;
    }

    public static ItemStack pane() {
        return button(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    /** Haengt Zeilen an die vorhandene Lore an. */
    public static ItemStack addLore(ItemStack s, String... lines) {
        ItemMeta meta = s.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        for (String l : lines) lore.add(text(l));
        meta.lore(lore);
        s.setItemMeta(meta);
        return s;
    }

    public static String roman(int level) {
        return level >= 1 && level < ROMAN.length ? ROMAN[level] : String.valueOf(level);
    }

    public static String fmtSeconds(int sec) {
        return (sec / 60) + ":" + String.format("%02d", sec % 60) + " min";
    }

    /** Lore-Zeile fuer einen Effekt, z.B. "Nebenhand: Staerke II". */
    public static Component effectLine(ItemEffect e) {
        Component line = Component.text(e.slot.label + ": ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.translatable(e.type.translationKey(), NamedTextColor.GREEN))
                .append(Component.text(" " + roman(e.level), NamedTextColor.GREEN));
        if (e.seconds > 0) line = line.append(Component.text(" (" + fmtSeconds(e.seconds) + ")", NamedTextColor.DARK_GRAY));
        return line;
    }
}
