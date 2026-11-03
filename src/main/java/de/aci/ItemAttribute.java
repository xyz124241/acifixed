package de.aci;

/** Attribute modifier configured for one custom item. */
public final class ItemAttribute {
    public String attribute;
    public double amount;
    public String operation;
    public String slotGroup;
    public ItemAttribute(String attribute, double amount, String operation, String slotGroup) {
        this.attribute = attribute; this.amount = amount; this.operation = operation; this.slotGroup = slotGroup;
    }
}
