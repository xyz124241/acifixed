package de.aci.gui;

import de.aci.AciPlugin;
import de.aci.CustomItem;
import de.aci.ItemAttribute;
import de.aci.ItemEffect;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Locale;

/** In-game per-item settings editor. Shift-click removes the last listed entry. */
public final class SettingsMenu extends Menu {
    private final CustomItem ci;
    public SettingsMenu(AciPlugin plugin, CustomItem ci) {
        super(plugin); this.ci=ci;
        inv=Bukkit.createInventory(this,27,Ui.text("&8Item settings: &0"+Ui.plain(ci.name)));
        for(int i=0;i<27;i++)inv.setItem(i,Ui.pane());
        inv.setItem(10,Ui.button(Material.CLOCK,"&eCooldown","&7Current: &f"+ci.cooldownSeconds+" seconds","&eClick: &7set cooldown seconds (0 = once on equip)"));
        inv.setItem(11,Ui.button(Material.SHIELD,ci.autoOffhand?"&aAuto offhand: ON":"&cAuto offhand: OFF","&7When enabled, the item activates","&7automatically while held in offhand."));
        inv.setItem(12,Ui.button(Material.POTION,"&dAbility effects","&7Configured: &f"+ci.abilityEffects.size(),"&7Type: RESISTANCE 4 10","&7(effect level duration-seconds)","&eClick: &7add effect","&eShift-click: &7remove last effect"));
        inv.setItem(13,Ui.button(Material.IRON_CHESTPLATE,"&bAttributes","&7Configured: &f"+ci.attributes.size(),"&7Type: MAX_HEALTH 4 ADD_NUMBER ANY","&eClick: &7add modifier","&eShift-click: &7remove last modifier"));
        inv.setItem(14,Ui.button(Material.ENCHANTED_BOOK,"&6Enchantments","&7Configured: &f"+ci.enchantments.size(),"&7Type: sharpness 5","&eClick: &7add/update enchant","&eShift-click: &7remove last enchant"));
        inv.setItem(15,Ui.button(Material.SHULKER_BOX,"&5Shulker capacity","&7Rows: &f"+ci.shulkerRows+" / 6","&eClick: &7cycle 1 to 6 rows"));
        inv.setItem(16,Ui.button(Material.HOPPER,ci.shulkerAutoPickup?"&aShulker break: Inventory":"&cShulker break: Drop","&7Auto pickup: &f"+(ci.shulkerAutoPickup?"ON":"OFF"),"&eClick: &7toggle pickup on break"));
        inv.setItem(18,Ui.button(Material.ARROW,"&cBack to item editor"));
    }
    @Override public void onClick(InventoryClickEvent e) {
        Player p=(Player)e.getWhoClicked();
        switch(e.getSlot()) {
            case 10 -> plugin.prompts().ask(p,"Cooldown in seconds (0 = once when equipped):",t->{
                try{ci.cooldownSeconds=Math.max(0,Math.min(3600,Integer.parseInt(t.trim())));plugin.items().changed();}
                catch(NumberFormatException ex){p.sendMessage(Ui.text("&cEnter a number between 0 and 3600."));}
                new SettingsMenu(plugin,ci).open(p);
            },()->new SettingsMenu(plugin,ci).open(p));
            case 11 -> {ci.autoOffhand=!ci.autoOffhand;plugin.items().changed();new SettingsMenu(plugin,ci).open(p);}
            case 12 -> {
                if(e.isShiftClick()){if(!ci.abilityEffects.isEmpty())ci.abilityEffects.remove(ci.abilityEffects.size()-1);plugin.items().changed();new SettingsMenu(plugin,ci).open(p);return;}
                plugin.prompts().ask(p,"Ability effect: EFFECT LEVEL SECONDS (example: RESISTANCE 4 10):",t->{
                    try{String[] a=t.trim().split("\\s+");if(a.length!=3)throw new IllegalArgumentException();
                        PotionEffectType type=Registry.EFFECT.get(NamespacedKey.minecraft(a[0].toLowerCase(Locale.ROOT)));
                        int level=Integer.parseInt(a[1]),seconds=Integer.parseInt(a[2]);if(type==null||level<1||level>255||seconds<1||seconds>3600)throw new IllegalArgumentException();
                        ci.abilityEffects.add(new ItemEffect(type,level,de.aci.EquipSlot.OFF_HAND,seconds));plugin.items().changed();
                    }catch(Exception ex){p.sendMessage(Ui.text("&cInvalid. Example: RESISTANCE 4 10"));}
                    new SettingsMenu(plugin,ci).open(p);
                },()->new SettingsMenu(plugin,ci).open(p));
            }
            case 13 -> {
                if(e.isShiftClick()){if(!ci.attributes.isEmpty())ci.attributes.remove(ci.attributes.size()-1);plugin.items().changed();new SettingsMenu(plugin,ci).open(p);return;}
                plugin.prompts().ask(p,"Attribute: ATTRIBUTE AMOUNT OPERATION SLOT (e.g. MAX_HEALTH 4 ADD_NUMBER ANY):",t->{
                    try{String[] a=t.trim().split("\\s+");if(a.length<2||a.length>4)throw new IllegalArgumentException();
                        String attribute=a[0].toUpperCase(Locale.ROOT);if(Registry.ATTRIBUTE.get(NamespacedKey.minecraft(attribute.toLowerCase(Locale.ROOT)))==null)throw new IllegalArgumentException("Unknown attribute");
                        double amount=Double.parseDouble(a[1]);String op=a.length>2?a[2].toUpperCase(Locale.ROOT):"ADD_NUMBER";
                        String slot=a.length>3?a[3].toUpperCase(Locale.ROOT):"ANY";
                        org.bukkit.attribute.AttributeModifier.Operation.valueOf(op);org.bukkit.inventory.EquipmentSlotGroup group=org.bukkit.inventory.EquipmentSlotGroup.getByName(slot.toLowerCase(Locale.ROOT).replace("_", ""));if(group==null)throw new IllegalArgumentException("Unknown slot group");
                        ci.attributes.add(new ItemAttribute(attribute,amount,op,slot));plugin.items().changed();
                    }catch(Exception ex){p.sendMessage(Ui.text("&cInvalid. Example: MAX_HEALTH 4 ADD_NUMBER ANY"));}
                    new SettingsMenu(plugin,ci).open(p);
                },()->new SettingsMenu(plugin,ci).open(p));
            }
            case 14 -> {
                if(e.isShiftClick()){if(!ci.enchantments.isEmpty()){String last=ci.enchantments.keySet().stream().reduce((a,b)->b).orElse(null);if(last!=null)ci.enchantments.remove(last);}plugin.items().changed();new SettingsMenu(plugin,ci).open(p);return;}
                plugin.prompts().ask(p,"Enchantment and level (example: sharpness 5):",t->{
                    try{String[] a=t.trim().split("\\s+");if(a.length!=2)throw new IllegalArgumentException();
                        String key=a[0].toLowerCase(Locale.ROOT).replace("minecraft:","");int level=Integer.parseInt(a[1]);
                        Enchantment en=Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));if(en==null||level<1||level>255)throw new IllegalArgumentException();
                        ci.enchantments.put(key,level);plugin.items().changed();
                    }catch(Exception ex){p.sendMessage(Ui.text("&cInvalid. Example: sharpness 5"));}
                    new SettingsMenu(plugin,ci).open(p);
                },()->new SettingsMenu(plugin,ci).open(p));
            }
            case 15 -> {ci.shulkerRows=ci.shulkerRows%6+1;plugin.items().changed();new SettingsMenu(plugin,ci).open(p);}
            case 16 -> {ci.shulkerAutoPickup=!ci.shulkerAutoPickup;plugin.items().changed();new SettingsMenu(plugin,ci).open(p);}
            case 18 -> new ItemEditorMenu(plugin,ci).open(p);
            default -> { }
        }
    }
}
