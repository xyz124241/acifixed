package de.aci;

/**
 * Ein Rezept (3x3, geformt). Jede Zelle ist ein Token:
 * "aci:&lt;id&gt;" fuer ein Custom Item, "mc:&lt;MATERIAL&gt;" fuer ein normales Item, null = leer.
 */
public final class CustomRecipe {
    public final String[] grid = new String[9];
    public int amount = 1;

    public CustomRecipe copy() {
        CustomRecipe c = new CustomRecipe();
        System.arraycopy(grid, 0, c.grid, 0, 9);
        c.amount = amount;
        return c;
    }

    public boolean isEmpty() {
        for (String s : grid) if (s != null) return false;
        return true;
    }
}
