package controllers.gestionposts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.scene.Scene;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.application.Platform;
import models.gestionposts.Post;
import netscape.javascript.JSObject;
import utils.GeoConfig;
import utils.NavigationManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carte Leaflet : tuiles CARTO Voyager (nettes, sans clé) ou Mapbox si token configuré.
 */
public final class PostsMapWebStage {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int SNIPPET_MAX = 320;

    private PostsMapWebStage() {
    }

    public static void show(List<Post> posts) {
        double centerLat = 20.0;
        double centerLng = 0.0;
        int zoom = 2;

        List<Map<String, Object>> markers = new ArrayList<>();
        Map<Long, Post> byId = new HashMap<>();
        for (Post p : posts) {
            if (p.getLatitude() == null || p.getLongitude() == null) {
                continue;
            }
            if (p.getId() != null) {
                byId.put(p.getId(), p);
            }
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("title", p.getTitre() != null ? p.getTitre() : "Post");
            m.put("lat", p.getLatitude());
            m.put("lng", p.getLongitude());
            m.put("location", p.getLocation() != null ? p.getLocation() : "");
            m.put("snippet", snippet(p.getContenu()));
            markers.add(m);
        }

        if (!markers.isEmpty()) {
            Map<String, Object> first = markers.get(0);
            centerLat = ((Number) first.get("lat")).doubleValue();
            centerLng = ((Number) first.get("lng")).doubleValue();
            zoom = markers.size() == 1 ? 6 : 4;
        }

        String markersJson;
        try {
            markersJson = MAPPER.writeValueAsString(markers);
        } catch (JsonProcessingException e) {
            markersJson = "[]";
        }

        String rawToken = GeoConfig.getMapboxAccessToken();
        boolean mapbox = GeoConfig.hasMapboxToken() && rawToken.startsWith("pk.");
        String tokenUrl = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        String html = buildPage(centerLat, centerLng, zoom, markersJson, mapbox, tokenUrl);

        WebView web = new WebView();
        web.setFontSmoothingType(FontSmoothingType.LCD);
        Stage st = new Stage();
        st.initModality(Modality.NONE);
        st.setTitle("SmartVoyage — Carte des recommandations");
        st.setScene(new Scene(web, 1080, 760));
        web.getEngine().loadContent(html);
        web.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                Platform.runLater(() -> {
                    try {
                        JSObject window = (JSObject) web.getEngine().executeScript("window");
                        window.setMember("smartvoyageBridge", new MapBridge(byId, st));
                        web.getEngine().executeScript(
                                "if(window.__svMap){window.__svMap.invalidateSize(true);} " +
                                "setTimeout(function(){if(window.__svMap){window.__svMap.invalidateSize(true);}},120);");
                    } catch (Exception ignored) {
                    }
                });
            }
        });
        st.show();
    }

    private static String snippet(String contenu) {
        if (contenu == null) {
            return "";
        }
        String t = contenu.replace('\n', ' ').replace('\r', ' ').trim();
        if (t.length() <= SNIPPET_MAX) {
            return t;
        }
        return t.substring(0, SNIPPET_MAX).trim() + "…";
    }

    private static String buildPage(double centerLat, double centerLng, int zoom, String markersJson,
                                    boolean mapbox, String tokenUrlEncoded) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>");
        sb.append("<script>window.L_DISABLE_3D=true;window.L_NO_TOUCH=true;window.L_PREFER_CANVAS=false;</script>");
        sb.append("<link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\" crossorigin=\"\"/>");
        sb.append("<script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\" crossorigin=\"\"></script>");
        sb.append("<style>");
        sb.append("html,body,#map{height:100%;width:100%;margin:0;padding:0;-webkit-font-smoothing:antialiased;}");
        sb.append("body{font-family:system-ui,Segoe UI,Roboto,sans-serif;font-size:14px;color:#0f172a;}");
        sb.append(".leaflet-tile-container img{image-rendering:auto;}");
        sb.append(".sv-popup{min-width:220px;max-width:340px;}");
        sb.append(".sv-popup h3{margin:0 0 6px 0;font-size:16px;line-height:1.25;color:#1e1b4b;}");
        sb.append(".sv-popup .loc{color:#5b21b6;font-weight:600;font-size:13px;margin-bottom:6px;}");
        sb.append(".sv-popup .snip{color:#334155;font-size:13px;line-height:1.45;max-height:200px;overflow-y:auto;}");
        sb.append(".sv-popup .meta{margin-top:8px;font-size:11px;color:#64748b;}");
        sb.append(".sv-popup .open{display:inline-block;margin-top:10px;color:#5b21b6;font-weight:700;text-decoration:none;}");
        sb.append("</style></head><body>");
        sb.append("<div id=\"map\"></div><script>");
        sb.append("const center = [").append(centerLat).append(",").append(centerLng).append("];");
        sb.append("const map = L.map('map',{zoomControl:true,preferCanvas:false,zoomAnimation:false,fadeAnimation:false,markerZoomAnimation:false,inertia:false}).setView(center, ").append(zoom).append(");");
        sb.append("window.__svMap = map;");
        if (mapbox) {
            sb.append("L.tileLayer('https://api.mapbox.com/styles/v1/mapbox/streets-v11/tiles/512/{z}/{x}/{y}@2x?access_token=")
                    .append(tokenUrlEncoded)
                    .append("',{tileSize:512,zoomOffset:-1,maxZoom:20,detectRetina:true,")
                    .append("attribution:\"&copy; Mapbox &copy; OpenStreetMap\"}).addTo(map);");
        } else {
            sb.append("L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png',");
            sb.append("{subdomains:'abcd',maxZoom:20,minZoom:2,detectRetina:true,");
            sb.append("attribution:\"&copy; OpenStreetMap &copy; CARTO\"}).addTo(map);");
        }
        sb.append("const markers = ").append(markersJson).append(";");
        sb.append("const group = L.featureGroup();");
        sb.append("group.addTo(map);");
        sb.append("const seen = new Map();");
        sb.append("markers.forEach(m => {");
        sb.append("  let lat = Number(m.lat), lng = Number(m.lng);");
        sb.append("  const key = lat.toFixed(5)+','+lng.toFixed(5);");
        sb.append("  const count = (seen.get(key) || 0) + 1;");
        sb.append("  seen.set(key, count);");
        sb.append("  if (count > 1) {");
        sb.append("    const angle = (count * 37) * Math.PI / 180.0;");
        sb.append("    const d = 0.00035 * Math.min(count, 10);");
        sb.append("    lat += Math.cos(angle) * d;");
        sb.append("    lng += Math.sin(angle) * d;");
        sb.append("  }");
        sb.append("  const mk = L.circleMarker([lat, lng],{");
        sb.append("    radius:8,weight:2,color:'#7c3aed',fillColor:'#a78bfa',fillOpacity:0.9");
        sb.append("  });");
        sb.append("  const loc = esc(m.location||'');");
        sb.append("  const sn = esc(m.snippet||'');");
        sb.append("  const html = '<div class=\"sv-popup\">' +");
        sb.append("    '<h3>' + esc(m.title) + '</h3>' +");
        sb.append("    (loc ? '<div class=\"loc\">&#128205; ' + loc + '</div>' : '') +");
        sb.append("    (sn ? '<div class=\"snip\">' + sn + '</div>' : '') +");
        sb.append("    '<div class=\"meta\">Post #' + m.id + '</div>' +");
        sb.append("    '<a class=\"open\" href=\"#\" onclick=\"openPost(' + m.id + ');return false;\">Voir détail du post</a>' +");
        sb.append("    '</div>';");
        sb.append("  mk.bindPopup(html,{maxWidth:360,minWidth:240,autoPanPadding:[24,24]});");
        sb.append("  mk.on('click',()=>{ mk.openPopup(); });");
        sb.append("  group.addLayer(mk);");
        sb.append("});");
        sb.append("if (group.getLayers().length > 0) {");
        sb.append("  const b = group.getBounds();");
        sb.append("  map.fitBounds(b.pad(0.14),{maxZoom:12});");
        sb.append("} else { map.setView([20,0],2); }");
        sb.append("function esc(t){ if(t==null) return ''; return String(t).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/\"/g,'&quot;'); }");
        sb.append("function openPost(id){");
        sb.append("  try { if(window.smartvoyageBridge){ window.smartvoyageBridge.openPost(String(id)); } } catch(e) {}");
        sb.append("}");
        sb.append("</script></body></html>");
        return sb.toString();
    }

    public static final class MapBridge {
        private final Map<Long, Post> byId;
        private final Stage stage;

        MapBridge(Map<Long, Post> byId, Stage stage) {
            this.byId = byId;
            this.stage = stage;
        }

        public void openPost(String idText) {
            try {
                long id = Long.parseLong(idText);
                Post post = byId.get(id);
                if (post == null) {
                    return;
                }
                Platform.runLater(() -> {
                    try {
                        if (stage != null) {
                            stage.close();
                        }
                    } catch (Exception ignored) {
                    }
                    NavigationManager.getInstance().showPostDetail(post, false);
                });
            } catch (Exception ignored) {
            }
        }
    }
}
