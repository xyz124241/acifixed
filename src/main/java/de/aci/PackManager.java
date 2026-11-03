package de.aci;

import de.aci.gui.Ui;
import org.bukkit.entity.Player;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Builds a Minecraft 26.2 resource pack using modern item model definitions. */
public final class PackManager {
    public static final String NAMESPACE = "aci";
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("aci-resourcepack".getBytes(StandardCharsets.UTF_8));
    private static final long FIXED_TIME = 315532800000L;
    private final AciPlugin plugin;
    private final File textureDir;
    private final File zipFile;
    private volatile byte[] zipBytes = new byte[0];
    private volatile byte[] sha1 = new byte[20];
    private volatile List<String> names = List.of();
    private HttpServer server;
    private ExecutorService executor;

    public PackManager(AciPlugin plugin) {
        this.plugin = plugin;
        this.textureDir = new File(plugin.getDataFolder(), "textures");
        this.zipFile = new File(plugin.getDataFolder(), "resourcepack.zip");
    }
    public List<String> textureNames() { return names; }

    public void rebuild() {
        Map<String, byte[]> textures = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        // Embedded crystal PNGs always win over stale copies left in plugins/ACI/textures.
        try (InputStream list = plugin.getResource("items.yml")) {
            if (list != null) {
                org.bukkit.configuration.file.YamlConfiguration defaults = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new InputStreamReader(list, StandardCharsets.UTF_8));
                var section = defaults.getConfigurationSection("items");
                if (section != null) for (String id : section.getKeys(false)) {
                    String texture = section.getString(id + ".texture", id);
                    if (texture == null || texture.isBlank() || order.contains(texture)) continue;
                    try (InputStream in = plugin.getResource("pack/textures/" + texture + ".png")) {
                        if (in != null) { textures.put(texture, in.readAllBytes()); order.add(texture); }
                    }
                }
            }
        } catch (IOException ex) { plugin.getLogger().warning("Could not read embedded crystal textures: " + ex.getMessage()); }
        File[] custom = textureDir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (custom != null) {
            Arrays.sort(custom);
            for (File f : custom) {
                String n = f.getName().substring(0, f.getName().length()-4).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
                // Do not let stale disk copies replace the embedded 38 standard crystals.
                if (textures.containsKey(n)) continue;
                try { textures.put(n, Files.readAllBytes(f.toPath())); order.add(n); }
                catch (IOException ex) { plugin.getLogger().warning("Texture " + f.getName() + " could not be read: " + ex.getMessage()); }
            }
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bos)) {
                put(zip, "pack.mcmeta", "{\"pack\":{\"description\":\"ACI Crystals 1.4.2 - Minecraft 26.2\",\"min_format\":[88,0],\"max_format\":88}}".getBytes(StandardCharsets.UTF_8));
                try (InputStream icon = plugin.getResource("pack/pack.png")) { if (icon != null) put(zip,"pack.png",icon.readAllBytes()); }
                for (String n : order) {
                    put(zip, "assets/aci/textures/item/"+n+".png", textures.get(n));
                    String model = "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"aci:item/"+n+"\"}}";
                    put(zip, "assets/aci/models/item/"+n+".json", model.getBytes(StandardCharsets.UTF_8));
                    String definition = "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"aci:item/"+n+"\"}}";
                    put(zip, "assets/aci/items/"+n+".json", definition.getBytes(StandardCharsets.UTF_8));
                }
            }
            byte[] bytes=bos.toByteArray(); Files.createDirectories(zipFile.toPath().getParent()); Files.write(zipFile.toPath(),bytes);
            sha1=MessageDigest.getInstance("SHA-1").digest(bytes); zipBytes=bytes; names=List.copyOf(order);
            plugin.getLogger().info("ACI 26.2 resource pack built: "+order.size()+" textures, pack_format 88.0, SHA-1 "+HexFormat.of().formatHex(sha1));
        } catch(Exception ex){ plugin.getLogger().severe("Resource pack could not be built: "+ex.getMessage()); }
    }
    private static void put(ZipOutputStream zip,String path,byte[] data)throws IOException{ZipEntry e=new ZipEntry(path);e.setTime(FIXED_TIME);zip.putNextEntry(e);zip.write(data);zip.closeEntry();}
    public void start(){
        stop(); var cfg=plugin.getConfig();
        if(!cfg.getString("resource-pack.url","").isBlank()||!cfg.getBoolean("resource-pack.host.enabled",true))return;
        int port=cfg.getInt("resource-pack.host.port",8123);
        try{
            executor=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"ACI-PackHost");t.setDaemon(true);return t;});
            server=HttpServer.create(new InetSocketAddress(port),0);
            server.createContext("/aci-pack.zip",ex->{byte[] b=zipBytes;ex.getResponseHeaders().add("Content-Type","application/zip");ex.getResponseHeaders().add("Cache-Control","no-cache");ex.sendResponseHeaders(200,b.length);try(OutputStream os=ex.getResponseBody()){os.write(b);}});
            server.setExecutor(executor);server.start(); plugin.getLogger().info("ACI pack host running on TCP port "+port);
        }catch(IOException ex){plugin.getLogger().severe("Pack web server could not start (port "+port+"): "+ex.getMessage());}
    }
    public void stop(){if(server!=null){server.stop(0);server=null;}if(executor!=null){executor.shutdownNow();executor=null;}}
    public String downloadUrl(){
        var cfg=plugin.getConfig();String ext=cfg.getString("resource-pack.url","");if(!ext.isBlank())return ext;
        if(server==null)return null;String addr=cfg.getString("resource-pack.host.public-address","");if(addr.isBlank())addr=plugin.getServer().getIp();if(addr.isBlank())return null;
        int port=cfg.getInt("resource-pack.host.port",8123);return "http://"+addr+":"+port+"/aci-pack.zip?v="+HexFormat.of().formatHex(sha1).substring(0,8);
    }
    public byte[] sha1(){return sha1.clone();}
    public void sendTo(Player p){String url=downloadUrl();if(url==null){plugin.getLogger().warning("ACI Resourcepack not sent: configure resource-pack.url (GitHub release) or resource-pack.host.public-address and open its TCP port.");return;}
        var cfg=plugin.getConfig();byte[] hash=sha1;
        if(!cfg.getString("resource-pack.url","").isBlank()){String hex=cfg.getString("resource-pack.sha1","").trim();hash=hex.matches("(?i)[0-9a-f]{40}")?HexFormat.of().parseHex(hex):null;}
        p.setResourcePack(PACK_ID,url,hash,Ui.text(cfg.getString("resource-pack.prompt","&6Bitte lade das ACI-Resource-Pack!")),cfg.getBoolean("resource-pack.required",false));
    }
}
